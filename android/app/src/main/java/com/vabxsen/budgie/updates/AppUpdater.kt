package com.vabxsen.budgie.updates

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vabxsen.budgie.BuildConfig
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val REPOSITORY_OWNER = "vabxsen"
private const val REPOSITORY_NAME = "budgie"
private const val RELEASES_API =
    "https://api.github.com/repos/$REPOSITORY_OWNER/$REPOSITORY_NAME/releases/latest"
private const val MAX_METADATA_BYTES = 1_048_576
private const val MAX_APK_BYTES = 100L * 1_024L * 1_024L
private const val MAX_REDIRECTS = 5
private const val INSTALL_ACTION = "com.vabxsen.budgie.UPDATE_INSTALL_STATUS"

data class UpdateRelease(
    val version: String,
    val tag: String,
    val assetName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val notes: String,
)

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class UpToDate(val version: String) : UpdateUiState
    data class Available(val release: UpdateRelease) : UpdateUiState
    data class Downloading(val release: UpdateRelease, val progress: Float) : UpdateUiState
    data class PermissionRequired(val release: UpdateRelease) : UpdateUiState
    data class Ready(val release: UpdateRelease) : UpdateUiState
    data class Installing(val release: UpdateRelease) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

internal object ReleaseRules {
    private val versionPattern = Regex("^v?((?:0|[1-9]\\d*))\\.((?:0|[1-9]\\d*))\\.((?:0|[1-9]\\d*))$")
    private val digestPattern = Regex("^[a-fA-F0-9]{64}$")

    fun normalizedVersion(value: String): String {
        val match = versionPattern.matchEntire(value.trim())
            ?: error("Release tag must use vMAJOR.MINOR.PATCH.")
        return match.groupValues.drop(1).joinToString(".")
    }

    fun compare(first: String, second: String): Int {
        val a = normalizedVersion(first).split('.').map { it.toLong() }
        val b = normalizedVersion(second).split('.').map { it.toLong() }
        return a.zip(b).firstOrNull { (left, right) -> left != right }
            ?.let { (left, right) -> left.compareTo(right) } ?: 0
    }

    fun parseLatestRelease(json: String): UpdateRelease {
        val root = JSONObject(json)
        check(!root.optBoolean("draft", true)) { "The latest release is still a draft." }
        check(!root.optBoolean("prerelease", true)) { "Pre-release builds are not installed automatically." }
        val tag = root.getString("tag_name").trim()
        val version = normalizedVersion(tag)
        val expectedName = "Budgie-$tag.apk"
        val assets = root.getJSONArray("assets")
        val asset = (0 until assets.length())
            .asSequence()
            .map(assets::getJSONObject)
            .singleOrNull { it.optString("name") == expectedName }
            ?: error("Release $tag does not contain $expectedName.")
        val contentType = asset.optString("content_type")
        check(contentType == "application/vnd.android.package-archive") {
            "The release asset is not an Android package."
        }
        val size = asset.getLong("size")
        check(size in 1..MAX_APK_BYTES) { "The release asset has an invalid size." }
        val digest = asset.optString("digest")
        check(digest.startsWith("sha256:", ignoreCase = true)) {
            "The release asset is missing its SHA-256 digest."
        }
        val sha256 = digest.substringAfter(':')
        check(digestPattern.matches(sha256)) { "The release asset digest is invalid." }
        val downloadUrl = asset.getString("browser_download_url")
        validateInitialDownloadUri(URI(downloadUrl), tag, expectedName)
        return UpdateRelease(
            version = version,
            tag = tag,
            assetName = expectedName,
            downloadUrl = downloadUrl,
            sizeBytes = size,
            sha256 = sha256.lowercase(),
            notes = root.optString("body").trim().take(800),
        )
    }

    fun validateInitialDownloadUri(uri: URI, tag: String, assetName: String) {
        validateHttpsUri(uri)
        check(uri.host.equals("github.com", ignoreCase = true)) { "Unexpected release download host." }
        check(uri.rawQuery == null && uri.rawFragment == null) { "Unexpected release download URL." }
        check(uri.path == "/$REPOSITORY_OWNER/$REPOSITORY_NAME/releases/download/$tag/$assetName") {
            "The release download URL does not belong to Budgie."
        }
    }

    fun validateRedirectUri(uri: URI) {
        validateHttpsUri(uri)
        check(
            uri.host.equals("github.com", ignoreCase = true) ||
                uri.host.equals("release-assets.githubusercontent.com", ignoreCase = true)
        ) { "Unexpected release redirect host." }
    }

    private fun validateHttpsUri(uri: URI) {
        check(uri.scheme.equals("https", ignoreCase = true)) { "Update downloads must use HTTPS." }
        check(uri.userInfo == null && uri.host != null) { "Invalid update URL." }
        check(uri.port == -1 || uri.port == 443) { "Unexpected update URL port." }
    }
}

private class GitHubUpdateClient(private val context: Context) {
    fun fetchLatest(): UpdateRelease {
        val connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Budgie-Android/${BuildConfig.VERSION_NAME}")
        }
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "GitHub returned HTTP ${connection.responseCode}."
            }
            return ReleaseRules.parseLatestRelease(readLimitedUtf8(connection, MAX_METADATA_BYTES))
        } finally {
            connection.disconnect()
        }
    }

    fun download(release: UpdateRelease, onProgress: (Float) -> Unit): File {
        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        updateDir.listFiles()?.forEach { it.delete() }
        val file =
            downloadRelease(release, updateDir, { it.toURL().openConnection() as HttpURLConnection }, onProgress)
        try {
            verifyDownloadedApk(context, file, release)
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
        return file
    }
}

/** Downloads the release asset, following GitHub's redirect, and checks its size and SHA-256 digest. */
internal fun downloadRelease(
    release: UpdateRelease,
    directory: File,
    open: (URI) -> HttpURLConnection,
    onProgress: (Float) -> Unit,
): File {
    val partial = File(directory, "${release.assetName}.part")
    val destination = File(directory, release.assetName)
    var uri = URI(release.downloadUrl)
    ReleaseRules.validateInitialDownloadUri(uri, release.tag, release.assetName)
    var redirects = 0
    while (true) {
        val connection = open(uri).apply {
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "Budgie-Android/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (connection.responseCode in 300..399) {
                check(redirects++ < MAX_REDIRECTS) { "Too many download redirects." }
                val location = connection.getHeaderField("Location")
                    ?: error("GitHub returned an invalid redirect.")
                uri = uri.resolve(location)
                ReleaseRules.validateRedirectUri(uri)
                continue
            }
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "GitHub returned HTTP ${connection.responseCode} while downloading."
            }
            val reportedLength = connection.contentLengthLong
            check(reportedLength == -1L || reportedLength == release.sizeBytes) {
                "The downloaded file size does not match the release."
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            connection.inputStream.use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        total += read
                        check(total <= release.sizeBytes && total <= MAX_APK_BYTES) {
                            "The downloaded file is larger than expected."
                        }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        onProgress((total.toFloat() / release.sizeBytes).coerceIn(0f, 1f))
                    }
                }
            }
            check(total == release.sizeBytes) { "The update download was incomplete." }
            val actualDigest = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            check(actualDigest == release.sha256) { "The update failed its integrity check." }
            check(partial.renameTo(destination)) { "The downloaded update could not be saved." }
            return destination
        } catch (error: Throwable) {
            partial.delete()
            destination.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }
}

@Suppress("DEPRECATION")
internal fun verifyDownloadedApk(context: Context, file: File, release: UpdateRelease) {
    val packageManager = context.packageManager
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        PackageManager.GET_SIGNATURES
    }
    val archive = packageManager.getPackageArchiveInfo(file.absolutePath, flags)
        ?: error("The downloaded file is not a valid Android package.")
    check(archive.packageName == context.packageName) { "The update belongs to a different app." }
    check(packageVersionCode(archive) > BuildConfig.VERSION_CODE.toLong()) {
        "The downloaded package is not newer than this version."
    }
    check(ReleaseRules.normalizedVersion(archive.versionName.orEmpty()) == release.version) {
        "The APK version does not match the GitHub release."
    }
    val installed = packageManager.getPackageInfo(context.packageName, flags)
    check(signingDigests(archive) == signingDigests(installed)) {
        "The update signature does not match the installed app."
    }
}

@Suppress("DEPRECATION")
private fun packageVersionCode(info: PackageInfo): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
    else info.versionCode.toLong()

@Suppress("DEPRECATION")
private fun signingDigests(info: PackageInfo): Set<String> {
    val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.signingInfo?.apkContentsSigners.orEmpty()
    } else {
        info.signatures.orEmpty()
    }
    check(signatures.isNotEmpty()) { "The APK has no signing certificate." }
    return signatures.mapTo(mutableSetOf()) { signature ->
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

private fun readLimitedUtf8(connection: HttpURLConnection, maxBytes: Int): String {
    val output = ByteArrayOutputStream()
    connection.inputStream.use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            check(total <= maxBytes) { "GitHub returned too much release data." }
            output.write(buffer, 0, count)
        }
    }
    return output.toString(Charsets.UTF_8.name())
}

private object UpdateInstaller {
    fun hasPermission(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun permissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri(),
        )

    fun install(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("Budgie-update.apk", 0, file.length()).use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                    session.fsync(output)
                }
                val statusIntent = Intent(context, UpdateInstallReceiver::class.java).apply {
                    action = INSTALL_ACTION
                    putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
                }
                val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val pending = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    statusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag,
                )
                session.commit(pending.intentSender)
            }
        } catch (error: Throwable) {
            runCatching { installer.abandonSession(sessionId) }
            throw error
        }
    }
}

private sealed interface InstallEvent {
    data object Success : InstallEvent
    data class Failure(val message: String) : InstallEvent
}

private object UpdateInstallEvents {
    private val mutableEvents = MutableSharedFlow<InstallEvent>(extraBufferCapacity = 1)
    val events = mutableEvents.asSharedFlow()
    fun emit(event: InstallEvent) = mutableEvents.tryEmit(event)
}

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != INSTALL_ACTION) return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?.let(context::startActivity)
                    ?: UpdateInstallEvents.emit(InstallEvent.Failure("Android could not open the installer."))
            }
            PackageInstaller.STATUS_SUCCESS -> {
                cleanupInstalledUpdateFiles(context)
                UpdateInstallEvents.emit(InstallEvent.Success)
            }
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                UpdateInstallEvents.emit(InstallEvent.Failure("Update installation was cancelled."))
            else -> UpdateInstallEvents.emit(
                InstallEvent.Failure(
                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        ?.take(160)
                        ?: "Android could not install the update."
                )
            )
        }
    }
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val client = GitHubUpdateClient(application)
    private val mutableState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state = mutableState.asStateFlow()
    private var downloadedFile: File? = null

    init {
        viewModelScope.launch {
            UpdateInstallEvents.events.collectLatest { event ->
                when (event) {
                    InstallEvent.Success -> mutableState.value = UpdateUiState.UpToDate(BuildConfig.VERSION_NAME)
                    is InstallEvent.Failure -> mutableState.value = UpdateUiState.Error(event.message)
                }
            }
        }
    }

    fun check() {
        if (mutableState.value == UpdateUiState.Checking || mutableState.value is UpdateUiState.Downloading) return
        viewModelScope.launch {
            mutableState.value = UpdateUiState.Checking
            mutableState.value = runCatching { withContext(Dispatchers.IO) { client.fetchLatest() } }
                .fold(
                    onSuccess = { release ->
                        if (ReleaseRules.compare(release.version, BuildConfig.VERSION_NAME) > 0)
                            UpdateUiState.Available(release)
                        else UpdateUiState.UpToDate(BuildConfig.VERSION_NAME)
                    },
                    onFailure = { UpdateUiState.Error(checkMessage(it)) },
                )
        }
    }

    fun download() {
        val release = (mutableState.value as? UpdateUiState.Available)?.release ?: return
        viewModelScope.launch {
            mutableState.value = UpdateUiState.Downloading(release, 0f)
            runCatching {
                withContext(Dispatchers.IO) {
                    client.download(release) { progress ->
                        mutableState.value = UpdateUiState.Downloading(release, progress)
                    }
                }
            }.fold(
                onSuccess = { file ->
                    downloadedFile = file
                    mutableState.value = UpdateUiState.Ready(release)
                },
                onFailure = { mutableState.value = UpdateUiState.Error(downloadMessage(it)) },
            )
        }
    }

    fun installReady(context: Context) {
        val release = when (val current = mutableState.value) {
            is UpdateUiState.Ready -> current.release
            is UpdateUiState.PermissionRequired -> current.release
            else -> return
        }
        val file = downloadedFile?.takeIf(File::isFile)
        if (file == null) {
            mutableState.value = UpdateUiState.Error("The downloaded update is no longer available. Check again to retry.")
            return
        }
        if (!UpdateInstaller.hasPermission(context)) {
            mutableState.value = UpdateUiState.PermissionRequired(release)
            context.startActivity(UpdateInstaller.permissionIntent(context))
            return
        }
        mutableState.value = UpdateUiState.Installing(release)
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { UpdateInstaller.install(context.applicationContext, file) } }
                .onFailure { mutableState.value = UpdateUiState.Error("Android could not prepare the installer. Please try again.") }
        }
    }

    fun openInstallPermission(context: Context) {
        if (mutableState.value is UpdateUiState.PermissionRequired) {
            context.startActivity(UpdateInstaller.permissionIntent(context))
        }
    }

    fun continueInstallIfAllowed(context: Context) {
        if (mutableState.value is UpdateUiState.PermissionRequired && UpdateInstaller.hasPermission(context)) {
            installReady(context)
        }
    }

    private fun checkMessage(error: Throwable): String = when {
        error.message?.contains("HTTP 403") == true -> "GitHub’s update check is temporarily rate limited. Try again later."
        else -> "Couldn’t check GitHub Releases. Check your connection and try again."
    }

    private fun downloadMessage(error: Throwable): String = when {
        error.message?.contains("integrity", ignoreCase = true) == true ||
            error.message?.contains("signature", ignoreCase = true) == true ||
            error.message?.contains("different app", ignoreCase = true) == true ->
            "Budgie rejected this update because it could not verify the APK."
        else -> "Couldn’t download the update. Check your connection and try again."
    }
}

fun cleanupInstalledUpdateFiles(context: Context) {
    val updateDir = File(context.cacheDir, "updates")
    updateDir.listFiles()?.forEach { file ->
        if (file.extension == "part") {
            file.delete()
            return@forEach
        }
        val info = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        }.getOrNull()
        if (info == null || packageVersionCode(info) <= BuildConfig.VERSION_CODE.toLong()) file.delete()
    }
}
