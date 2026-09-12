package com.vabxsen.budgie.data

import android.content.Context
import android.util.AtomicFile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.vabxsen.budgie.domain.BudgieCollection
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Offline-first storage with a separate local cache and private Firestore path per account. */
class BudgieRepository(
    context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private val appContext = context.applicationContext
    private val filesDir = context.filesDir
    private val guestFile = AtomicFile(File(filesDir, "collection.json"))
    private val guestMetadataFile = AtomicFile(File(filesDir, "collection-sync.json"))
    private var activeFile = guestFile
    private var activeMetadataFile = guestMetadataFile
    private var activeUid: String? = null
    private var metadata = SyncMetadata()
    private var loaded = false

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow<Result<BudgieCollection>?>(null)
    val state = mutable.asStateFlow()
    private val mutableSync = MutableStateFlow(SyncState())
    val syncState = mutableSync.asStateFlow()

    private var subscriptionsRegistration: ListenerRegistration? = null
    private var preferencesRegistration: ListenerRegistration? = null
    private val pendingWrites = AtomicInteger(0)
    private val syncGeneration = AtomicInteger(0)
    private val profilePreferences =
        appContext.getSharedPreferences("budgie_account_storage", Context.MODE_PRIVATE)

    private val authListener = FirebaseAuth.AuthStateListener { firebase ->
        if (loaded && firebase.currentUser?.uid != activeUid) {
            scope.launch { switchProfile(firebase.currentUser?.uid) }
        }
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    suspend fun load() =
        withContext(Dispatchers.IO) {
            stopSync()
            val uid = auth.currentUser?.uid
            val loadedSuccessfully = mutex.withLock { loadProfileLocked(uid) }
            if (loadedSuccessfully && uid != null) startSync(uid)
        }

    suspend fun update(transform: (BudgieCollection) -> BudgieCollection) =
        withContext(Dispatchers.IO) {
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

                changed.forEach {
                    metadata.subscriptionTimes[it.id] = now
                    metadata.deletedTimes.remove(it.id)
                }
                removed.forEach {
                    metadata.deletedTimes[it] = now
                    metadata.subscriptionTimes.remove(it)
                }
                if (current.preferences != next.preferences) metadata.preferencesTime = now

                writeCollection(activeFile, next)
                writeMetadata(activeMetadataFile, metadata)
                mutable.value = Result.success(next)

                uid = activeUid
                if (uid != null) {
                    subscriptionWrites =
                        changed.map { it.id to it.toFirestore(metadata.subscriptionTimes.getValue(it.id)) } +
                            removed.map { it to deletedSubscription(metadata.deletedTimes.getValue(it)) }
                    if (current.preferences != next.preferences) {
                        preferencesWrite = next.preferences.toFirestore(metadata.preferencesTime)
                    }
                }
            }
            uid?.let { accountId ->
                subscriptionWrites.forEach { (id, value) ->
                    write(subscriptionRef(accountId, id), value, accountId)
                }
                preferencesWrite?.let { write(preferencesRef(accountId), it, accountId) }
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

                val collection =
                    if (uid != null && !exists(collectionFile) && !profilePreferences.getBoolean("guest_claimed", false)) {
                        val guest = readCollection(guestFile)
                        writeCollection(collectionFile, guest)
                        profilePreferences.edit().putBoolean("guest_claimed", true).apply()
                        guest
                    } else {
                        readCollection(collectionFile)
                    }

                metadata =
                    if (exists(metadataFile))
                        runCatching { readMetadata(metadataFile) }
                            .getOrElse { initialMetadata(collection, collectionFile.baseFile.lastModified()) }
                    else initialMetadata(collection, collectionFile.baseFile.lastModified())
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

    private fun profileFiles(uid: String?): Pair<AtomicFile, AtomicFile> {
        if (uid == null) return guestFile to guestMetadataFile
        val directory = File(filesDir, "accounts").apply { mkdirs() }
        val key =
            MessageDigest.getInstance("SHA-256")
                .digest(uid.toByteArray())
                .joinToString("") { "%02x".format(it) }
        return AtomicFile(File(directory, "$key.json")) to
            AtomicFile(File(directory, "$key-sync.json"))
    }

    private fun initialMetadata(collection: BudgieCollection, fileTime: Long): SyncMetadata {
        val time = fileTime.coerceAtLeast(1L)
        return SyncMetadata(
            subscriptionTimes = collection.subscriptions.associate { it.id to time }.toMutableMap(),
            preferencesTime = if (collection == BudgieCollection()) 0 else time,
        )
    }

    private fun startSync(uid: String) {
        if (uid != activeUid || auth.currentUser?.uid != uid) return
        mutableSync.value = SyncState(SyncStatus.CONNECTING)
        subscriptionsRegistration =
            subscriptionsRef(uid).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    syncError(uid, error)
                } else if (snapshot != null) {
                    scope.launch {
                        mergeSubscriptions(uid, snapshot.documents.associate { it.id to it.data.orEmpty() })
                    }
                }
            }
        preferencesRegistration =
            preferencesRef(uid).addSnapshotListener { snapshot, error ->
                if (error != null) syncError(uid, error)
                else scope.launch { mergePreferences(uid, snapshot?.data) }
            }
    }

    private suspend fun mergeSubscriptions(uid: String, documents: Map<String, Map<String, Any>>) {
        if (uid != activeUid || auth.currentUser?.uid != uid) return
        if (documents.size > 5000) {
            mutableSync.value =
                SyncState(SyncStatus.ERROR, message = "This cloud collection is too large to open safely.")
            return
        }
        val parsed = mutableMapOf<String, RemoteSubscription>()
        try {
            documents.forEach { (id, data) -> parsed[id] = remoteSubscription(id, data) }
        } catch (_: Exception) {
            mutableSync.value =
                SyncState(SyncStatus.ERROR, message = "Some cloud subscription data is invalid.")
            return
        }

        val uploads = mutableListOf<Pair<String, Map<String, Any>>>()
        mutex.withLock {
            if (uid != activeUid) return
            val current = mutable.value?.getOrNull() ?: return
            val local = current.subscriptions.associateBy { it.id }.toMutableMap()

            parsed.forEach { (id, remote) ->
                val localTime =
                    maxOf(metadata.subscriptionTimes[id] ?: 0, metadata.deletedTimes[id] ?: 0)
                if (remote.updatedAtMillis > localTime) {
                    if (remote.subscription == null) {
                        local.remove(id)
                        metadata.subscriptionTimes.remove(id)
                        metadata.deletedTimes[id] = remote.updatedAtMillis
                    } else {
                        local[id] = remote.subscription
                        metadata.deletedTimes.remove(id)
                        metadata.subscriptionTimes[id] = remote.updatedAtMillis
                    }
                } else if (localTime > remote.updatedAtMillis) {
                    val value =
                        local[id]?.toFirestore(localTime) ?: deletedSubscription(localTime)
                    uploads += id to value
                }
            }

            local.values.forEach { subscription ->
                if (subscription.id !in parsed) {
                    val time =
                        metadata.subscriptionTimes[subscription.id]
                            ?: nextTimestampLocked().also {
                                metadata.subscriptionTimes[subscription.id] = it
                            }
                    uploads += subscription.id to subscription.toFirestore(time)
                }
            }
            metadata.deletedTimes.forEach { (id, time) ->
                if (id !in parsed) uploads += id to deletedSubscription(time)
            }

            val next = current.copy(subscriptions = local.values.toList())
            if (next != current) {
                writeCollection(activeFile, next)
                mutable.value = Result.success(next)
            }
            writeMetadata(activeMetadataFile, metadata)
        }
        if (uploads.isEmpty()) markSynced(uid)
        else
            uploads.distinctBy { it.first }.forEach { (id, data) ->
                write(subscriptionRef(uid, id), data, uid)
            }
    }

    private suspend fun mergePreferences(uid: String, data: Map<String, Any>?) {
        if (uid != activeUid || auth.currentUser?.uid != uid) return
        var upload: Map<String, Any>? = null
        mutex.withLock {
            if (uid != activeUid) return
            val current = mutable.value?.getOrNull() ?: return
            if (data == null) {
                if (metadata.preferencesTime == 0L) metadata.preferencesTime = nextTimestampLocked()
                upload = current.preferences.toFirestore(metadata.preferencesTime)
            } else {
                val remotePair =
                    try {
                        remotePreferences(data)
                    } catch (_: Exception) {
                        mutableSync.value =
                            SyncState(SyncStatus.ERROR, message = "Cloud settings data is invalid.")
                        return
                    }
                val (remote, remoteTime) = remotePair
                if (remoteTime > metadata.preferencesTime) {
                    metadata.preferencesTime = remoteTime
                    val next = current.copy(preferences = remote)
                    writeCollection(activeFile, next)
                    mutable.value = Result.success(next)
                } else if (metadata.preferencesTime > remoteTime) {
                    upload = current.preferences.toFirestore(metadata.preferencesTime)
                }
            }
            writeMetadata(activeMetadataFile, metadata)
        }
        upload?.let { write(preferencesRef(uid), it, uid) } ?: markSynced(uid)
    }

    private fun write(reference: DocumentReference, data: Map<String, Any>, uid: String) {
        if (uid != activeUid || auth.currentUser?.uid != uid) return
        val generation = syncGeneration.get()
        pendingWrites.incrementAndGet()
        mutableSync.value = mutableSync.value.copy(status = SyncStatus.SYNCING, message = null)
        reference.set(data).addOnCompleteListener { task ->
            if (generation != syncGeneration.get()) return@addOnCompleteListener
            val remaining = pendingWrites.updateAndGet { (it - 1).coerceAtLeast(0) }
            if (uid != activeUid || auth.currentUser?.uid != uid) return@addOnCompleteListener
            if (task.isSuccessful && remaining == 0) markSynced(uid)
            else if (!task.isSuccessful) syncError(uid, task.exception)
        }
    }

    private fun markSynced(uid: String) {
        if (uid == activeUid && auth.currentUser?.uid == uid && pendingWrites.get() == 0) {
            mutableSync.value = SyncState(SyncStatus.SYNCED, System.currentTimeMillis())
        }
    }

    private fun syncError(uid: String, error: Throwable?) {
        if (uid == activeUid) {
            val offline = error?.message?.contains("network", ignoreCase = true) == true
            mutableSync.value =
                SyncState(
                    SyncStatus.ERROR,
                    mutableSync.value.lastSyncedAtMillis,
                    if (offline) "Offline changes will sync when your connection returns."
                    else "Sync paused. Your changes are safe on this device.",
                )
        }
    }

    private fun stopSync() {
        syncGeneration.incrementAndGet()
        subscriptionsRegistration?.remove()
        preferencesRegistration?.remove()
        subscriptionsRegistration = null
        preferencesRegistration = null
        pendingWrites.set(0)
        mutableSync.value = SyncState()
    }

    private fun subscriptionsRef(uid: String) =
        firestore.collection("users").document(uid).collection("subscriptions")

    private fun subscriptionRef(uid: String, id: String) = subscriptionsRef(uid).document(id)

    private fun preferencesRef(uid: String) =
        firestore.collection("users").document(uid).collection("settings").document("preferences")

    private fun nextTimestampLocked(): Long {
        val latest =
            maxOf(
                metadata.preferencesTime,
                metadata.subscriptionTimes.values.maxOrNull() ?: 0,
                metadata.deletedTimes.values.maxOrNull() ?: 0,
            )
        return maxOf(System.currentTimeMillis(), latest + 1)
    }

    private fun readCollection(file: AtomicFile): BudgieCollection =
        if (exists(file))
            CollectionCodec.decode(file.openRead().bufferedReader().use { it.readText() })
        else BudgieCollection()

    private fun readMetadata(file: AtomicFile): SyncMetadata =
        SyncMetadataCodec.decode(file.openRead().bufferedReader().use { it.readText() })

    private fun writeCollection(file: AtomicFile, collection: BudgieCollection) =
        writeAtomic(file, CollectionCodec.encode(collection).toByteArray(Charsets.UTF_8))

    private fun writeMetadata(file: AtomicFile, value: SyncMetadata) =
        writeAtomic(file, SyncMetadataCodec.encode(value).toByteArray(Charsets.UTF_8))

    private fun writeAtomic(file: AtomicFile, bytes: ByteArray) {
        file.baseFile.parentFile?.mkdirs()
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }

    private fun exists(file: AtomicFile) =
        file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
}
