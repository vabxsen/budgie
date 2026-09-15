package com.vabxsen.budgie.data

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.vabxsen.budgie.domain.BudgieCollection
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Remembers that the signed-out collection was copied into an account on this device. */
internal interface GuestClaim {
    val claimed: Boolean

    fun markClaimed()
}

/** Offline-first storage with a separate local cache and private cloud path per account. */
class BudgieRepository
internal constructor(
    private val filesDir: File,
    private val guestClaim: GuestClaim,
    private val cloud: CloudBackend,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    constructor(
        context: Context
    ) : this(
        context.filesDir,
        PreferencesGuestClaim(
            context.applicationContext.getSharedPreferences("budgie_account_storage", Context.MODE_PRIVATE)
        ),
        FirebaseBackend(),
    )

    private val guestFile = AtomicStorageFile(File(filesDir, "collection.json"))
    private val guestMetadataFile = AtomicStorageFile(File(filesDir, "collection-sync.json"))
    @Volatile private var activeFile = guestFile
    private var activeMetadataFile = guestMetadataFile
    @Volatile private var activeUid: String? = null
    private var metadata = SyncMetadata()
    private var loaded = false

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutable = MutableStateFlow<Result<BudgieCollection>?>(null)
    val state = mutable.asStateFlow()
    private val mutableSync = MutableStateFlow(SyncState())
    val syncState = mutableSync.asStateFlow()

    @Volatile private var subscriptionsRegistration: SyncRegistration? = null
    @Volatile private var preferencesRegistration: SyncRegistration? = null
    @Volatile private var connectTimeoutJob: Job? = null
    @Volatile private var retryJob: Job? = null
    private val pendingWrites = AtomicInteger(0)
    private val syncGeneration = AtomicInteger(0)
    private val retryAttempts = AtomicInteger(0)
    @Volatile private var serverSynced = false
    @Volatile private var offline = false
    @Volatile private var failed = false
    @Volatile private var subscriptionsProblem: String? = null
    @Volatile private var preferencesProblem: String? = null

    init {
        cloud.addAccountListener { uid ->
            if (loaded && uid != activeUid) scope.launch { switchProfile(uid) }
        }
    }

    /** The file holding the collection that is currently shown. */
    @VisibleForTesting
    internal fun activeCollectionFile(): File = activeFile.baseFile

    suspend fun load() =
        withContext(ioDispatcher) {
            stopSync()
            val uid = cloud.currentUid
            val loadedSuccessfully = mutex.withLock { loadProfileLocked(uid) }
            if (loadedSuccessfully && uid != null) startSync(uid)
        }

    suspend fun update(transform: (BudgieCollection) -> BudgieCollection) =
        withContext(ioDispatcher) {
            var subscriptionWrites: List<Pair<String, Map<String, Any>>> = emptyList()
            var preferencesWrite: Map<String, Any>? = null
            var uid: String? = null
            mutex.withLock {
                val current =
                    mutable.value?.getOrThrow() ?: error("Your collection is still loading.")
                val next = transform(current)
                val now = nextTimestampLocked()
                val before = current.subscriptions.associateBy { it.id }
                val after = next.subscriptions.associateBy { it.id }
                val changed = after.values.filter { before[it.id] != it }
                val removed = before.keys - after.keys
                val preferencesChanged =
                    current.preferences.syncedFields() != next.preferences.syncedFields()

                changed.forEach {
                    metadata.subscriptionTimes[it.id] = now
                    metadata.deletedTimes.remove(it.id)
                }
                removed.forEach {
                    metadata.deletedTimes[it] = now
                    metadata.subscriptionTimes.remove(it)
                }
                if (preferencesChanged) metadata.preferencesTime = now

                writeCollection(activeFile, next)
                writeMetadata(activeMetadataFile, metadata)
                mutable.value = Result.success(next)

                uid = activeUid
                if (uid != null) {
                    subscriptionWrites =
                        changed.map { it.id to it.toFirestore(metadata.subscriptionTimes.getValue(it.id)) } +
                            removed.map { it to deletedSubscription(metadata.deletedTimes.getValue(it)) }
                    if (preferencesChanged) {
                        preferencesWrite = next.preferences.toFirestore(metadata.preferencesTime)
                    }
                }
            }
            uid?.let { accountId ->
                subscriptionWrites.forEach { (id, value) ->
                    write(accountId) { done -> cloud.writeSubscription(accountId, id, value, done) }
                }
                preferencesWrite?.let { value ->
                    write(accountId) { done -> cloud.writePreferences(accountId, value, done) }
                }
            }
        }

    private suspend fun switchProfile(uid: String?) {
        stopSync()
        val ok = mutex.withLock { loadProfileLocked(uid) }
        if (ok && uid != null) startSync(uid)
    }

    private fun loadProfileLocked(uid: String?): Boolean =
        runCatching {
                val (collectionFile, metadataFile) = profileFiles(uid)
                activeFile = collectionFile
                activeMetadataFile = metadataFile
                activeUid = uid

                val claimGuest = uid != null && !collectionFile.exists() && !guestClaim.claimed
                val collection =
                    if (claimGuest) {
                        val guest = readCollection(guestFile)
                        writeCollection(collectionFile, guest)
                        guestClaim.markClaimed()
                        guest
                    } else {
                        readCollection(collectionFile)
                    }

                metadata =
                    when {
                        // Claimed signed-out data has no sync times, so it can't outrank the cloud copy.
                        claimGuest -> SyncMetadata(pendingClaim = true)
                        metadataFile.exists() ->
                            runCatching { readMetadata(metadataFile) }
                                .getOrElse { initialMetadata(collection, collectionFile.baseFile.lastModified()) }
                        else -> initialMetadata(collection, collectionFile.baseFile.lastModified())
                    }
                writeMetadata(metadataFile, metadata)
                mutable.value = Result.success(collection)
                mutableSync.value =
                    if (uid == null) SyncState()
                    else SyncState(SyncStatus.CONNECTING)
                loaded = true
            }
            .onFailure {
                mutable.value = Result.failure(it)
                mutableSync.value =
                    SyncState(SyncStatus.ERROR, message = "Local account data could not be opened.")
                loaded = true
            }
            .isSuccess

    private fun profileFiles(uid: String?): Pair<AtomicStorageFile, AtomicStorageFile> {
        if (uid == null) return guestFile to guestMetadataFile
        val directory = File(filesDir, "accounts").apply { mkdirs() }
        val key =
            MessageDigest.getInstance("SHA-256")
                .digest(uid.toByteArray())
                .joinToString("") { "%02x".format(it) }
        return AtomicStorageFile(File(directory, "$key.json")) to
            AtomicStorageFile(File(directory, "$key-sync.json"))
    }

    private fun initialMetadata(collection: BudgieCollection, fileTime: Long): SyncMetadata {
        val time = fileTime.coerceAtLeast(1L)
        return SyncMetadata(
            subscriptionTimes = collection.subscriptions.associate { it.id to time }.toMutableMap(),
            preferencesTime = if (collection == BudgieCollection()) 0 else time,
        )
    }

    private fun startSync(uid: String) {
        if (uid != activeUid || cloud.currentUid != uid) return
        val generation = syncGeneration.get()
        mutableSync.value = SyncState(SyncStatus.CONNECTING)
        subscriptionsRegistration =
            cloud.listenSubscriptions(
                uid,
                { documents, fromCache -> scope.launch { mergeSubscriptions(uid, generation, documents, fromCache) } },
                { syncFailed(uid, generation) },
            )
        preferencesRegistration =
            cloud.listenPreferences(
                uid,
                { data, fromCache -> scope.launch { mergePreferences(uid, generation, data, fromCache) } },
                { syncFailed(uid, generation) },
            )
        connectTimeoutJob =
            scope.launch {
                delay(CONNECT_TIMEOUT_MILLIS)
                if (isCurrent(uid, generation) && !serverSynced) {
                    offline = true
                    publishStatus(uid)
                }
            }
    }

    private suspend fun mergeSubscriptions(
        uid: String,
        generation: Int,
        documents: Map<String, Map<String, Any>>,
        fromCache: Boolean,
    ) {
        if (!isCurrent(uid, generation)) return
        // Deletion records are tiny, so only live subscriptions count toward the safety limit.
        if (documents.values.count { it["deleted"] != true } > MAX_CLOUD_SUBSCRIPTIONS) {
            subscriptionsProblem = "This cloud collection is too large to open safely."
            publishStatus(uid)
            return
        }
        val remote =
            try {
                documents.mapValues { (id, data) -> remoteSubscription(id, data) }
            } catch (_: Exception) {
                subscriptionsProblem = "Some cloud subscription data is invalid."
                publishStatus(uid)
                return
            }
        subscriptionsProblem = null

        var uploads: List<SyncUpload> = emptyList()
        mutex.withLock {
            if (!isCurrent(uid, generation)) return
            val current = mutable.value?.getOrNull() ?: return
            val before = metadata.snapshot()
            val merge =
                SyncMerge.subscriptions(
                    current.subscriptions, remote, metadata, fromCache, ::nextTimestampLocked
                ) ?: return
            val next = current.copy(subscriptions = merge.subscriptions)
            if (next != current) {
                writeCollection(activeFile, next)
                mutable.value = Result.success(next)
            }
            if (metadata != before) writeMetadata(activeMetadataFile, metadata)
            uploads = merge.uploads
        }
        if (fromCache) {
            if (serverSynced) offline = true
        } else {
            serverSynced = true
            offline = false
            retryAttempts.set(0)
        }
        uploads.forEach { upload ->
            val data =
                upload.subscription?.toFirestore(upload.updatedAtMillis)
                    ?: deletedSubscription(upload.updatedAtMillis)
            write(uid) { done -> cloud.writeSubscription(uid, upload.id, data, done) }
        }
        publishStatus(uid)
    }

    private suspend fun mergePreferences(
        uid: String,
        generation: Int,
        data: Map<String, Any>?,
        fromCache: Boolean,
    ) {
        if (!isCurrent(uid, generation)) return
        val remote =
            try {
                data?.let(::remotePreferences)
            } catch (_: Exception) {
                preferencesProblem = "Cloud settings data is invalid."
                publishStatus(uid)
                return
            }
        preferencesProblem = null

        var upload: Map<String, Any>? = null
        mutex.withLock {
            if (!isCurrent(uid, generation)) return
            val current = mutable.value?.getOrNull() ?: return
            val before = metadata.snapshot()
            val merge =
                SyncMerge.preferences(current.preferences, remote, metadata, fromCache, ::nextTimestampLocked)
            merge.preferences?.takeIf { it != current.preferences }?.let {
                val next = current.copy(preferences = it)
                writeCollection(activeFile, next)
                mutable.value = Result.success(next)
            }
            if (metadata != before) writeMetadata(activeMetadataFile, metadata)
            upload = merge.uploadAtMillis?.let { current.preferences.toFirestore(it) }
        }
        upload?.let { value -> write(uid) { done -> cloud.writePreferences(uid, value, done) } }
        publishStatus(uid)
    }

    private fun write(uid: String, send: (onComplete: (success: Boolean) -> Unit) -> Unit) {
        if (uid != activeUid || cloud.currentUid != uid) return
        val generation = syncGeneration.get()
        pendingWrites.incrementAndGet()
        publishStatus(uid)
        send { success ->
            if (generation != syncGeneration.get()) return@send
            pendingWrites.updateAndGet { (it - 1).coerceAtLeast(0) }
            if (success) publishStatus(uid) else syncFailed(uid, generation)
        }
    }

    private fun publishStatus(uid: String) {
        if (uid != activeUid || cloud.currentUid != uid) return
        val current = mutableSync.value
        val problem = subscriptionsProblem ?: preferencesProblem
        mutableSync.value =
            when {
                failed ->
                    SyncState(
                        SyncStatus.ERROR,
                        current.lastSyncedAtMillis,
                        "Sync paused. Budgie will try again shortly, and your changes are safe on this device.",
                    )
                problem != null -> SyncState(SyncStatus.ERROR, current.lastSyncedAtMillis, problem)
                offline -> SyncState(SyncStatus.OFFLINE, current.lastSyncedAtMillis)
                !serverSynced -> SyncState(SyncStatus.CONNECTING, current.lastSyncedAtMillis)
                pendingWrites.get() > 0 -> SyncState(SyncStatus.SYNCING, current.lastSyncedAtMillis)
                current.status == SyncStatus.SYNCED -> current
                else -> SyncState(SyncStatus.SYNCED, clock())
            }
    }

    /** The cloud stops a listener after an error, so sync restarts itself after a growing delay. */
    private fun syncFailed(uid: String, generation: Int) {
        if (!isCurrent(uid, generation)) return
        failed = true
        publishStatus(uid)
        if (retryJob?.isActive == true) return
        val attempt = retryAttempts.getAndIncrement().coerceAtMost(MAX_RETRY_SHIFT)
        retryJob =
            scope.launch {
                delay(RETRY_BASE_MILLIS shl attempt)
                if (isCurrent(uid, generation)) {
                    detachListeners()
                    startSync(uid)
                }
            }
    }

    private fun stopSync() {
        retryJob?.cancel()
        retryJob = null
        retryAttempts.set(0)
        detachListeners()
    }

    private fun detachListeners() {
        syncGeneration.incrementAndGet()
        subscriptionsRegistration?.remove()
        preferencesRegistration?.remove()
        subscriptionsRegistration = null
        preferencesRegistration = null
        connectTimeoutJob?.cancel()
        connectTimeoutJob = null
        pendingWrites.set(0)
        serverSynced = false
        offline = false
        failed = false
        subscriptionsProblem = null
        preferencesProblem = null
        mutableSync.value = SyncState()
    }

    private fun isCurrent(uid: String, generation: Int) =
        generation == syncGeneration.get() && uid == activeUid && cloud.currentUid == uid

    private fun nextTimestampLocked(): Long {
        val latest =
            maxOf(
                metadata.preferencesTime,
                metadata.subscriptionTimes.values.maxOrNull() ?: 0,
                metadata.deletedTimes.values.maxOrNull() ?: 0,
            )
        return maxOf(clock(), latest + 1)
    }

    private fun SyncMetadata.snapshot() =
        copy(subscriptionTimes = subscriptionTimes.toMutableMap(), deletedTimes = deletedTimes.toMutableMap())

    private fun readCollection(file: AtomicStorageFile): BudgieCollection =
        if (file.exists()) CollectionCodec.decode(file.readText()) else BudgieCollection()

    private fun readMetadata(file: AtomicStorageFile): SyncMetadata = SyncMetadataCodec.decode(file.readText())

    private fun writeCollection(file: AtomicStorageFile, collection: BudgieCollection) =
        file.write(CollectionCodec.encode(collection).toByteArray(Charsets.UTF_8))

    private fun writeMetadata(file: AtomicStorageFile, value: SyncMetadata) =
        file.write(SyncMetadataCodec.encode(value).toByteArray(Charsets.UTF_8))

    private companion object {
        const val MAX_CLOUD_SUBSCRIPTIONS = 5000
        const val CONNECT_TIMEOUT_MILLIS = 10_000L
        const val RETRY_BASE_MILLIS = 15_000L
        const val MAX_RETRY_SHIFT = 4
    }
}

private class PreferencesGuestClaim(private val preferences: SharedPreferences) : GuestClaim {
    override val claimed: Boolean
        get() = preferences.getBoolean("guest_claimed", false)

    override fun markClaimed() = preferences.edit { putBoolean("guest_claimed", true) }
}
