package oolong.client.repository

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import oolong.client.BuildConfig
import oolong.client.arch.ApplicationScope
import oolong.client.arch.IoDispatcher
import oolong.client.dataStore
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days

/** Where releases are published: each channel's manifest under `update/`, the APKs under `apk/`. Crash reports go to `crash`. */
internal const val UpdateHost = "https://oolong.huanchengfly.top"

/** A release as a channel's manifest describes it. */
@Serializable
data class Release(
    val versionCode: Int,
    val versionName: String,
    val publishedAt: String,
    val url: String,
    val size: Long,
    val sha256: String,
    /**
     * What changed, by language and then by group (新增, 修复…) in the order shown. An item may be a
     * title and, after a line break, what it does for the reader.
     */
    val notes: Map<String, Map<String, List<String>>> = emptyMap(),
    /** Versions below this one no longer work, and may not skip this release. */
    val minSupported: Int = 0,
) {
    val isNewer: Boolean get() = versionCode > BuildConfig.VERSION_CODE
    val required: Boolean get() = BuildConfig.VERSION_CODE < minSupported

    /** The notes in the language the app is shown in, else Chinese. */
    fun notesFor(locale: Locale): Map<String, List<String>> = notes.notesFor(locale)
}

/** Release notes by language: those in [locale]'s language, else Chinese. */
fun Map<String, Map<String, List<String>>>.notesFor(locale: Locale): Map<String, List<String>> =
    this[locale.language] ?: this["zh"] ?: values.firstOrNull().orEmpty()

/**
 * Stable has releases only; beta has the alpha, beta and rc builds too, and always offers the newest
 * of everything, a release included.
 */
enum class UpdateChannel(val key: String) {
    Stable("stable"),
    Beta("beta"),
    ;

    companion object {
        /** A pre-release build (named 1.0.0-alpha.1 and the like) is on beta until the user says otherwise. */
        val default: UpdateChannel get() = if (InstalledIsPreRelease) Beta else Stable
    }
}

/** Whether the build installed is a pre-release, which stable releases may be older than. */
val InstalledIsPreRelease: Boolean = BuildConfig.VERSION_NAME.substringBefore('+').contains('-')

sealed interface UpdateCheck {
    data object Idle : UpdateCheck

    data object Checking : UpdateCheck

    data class Failed(val error: Throwable) : UpdateCheck

    /** The channel's [latest] release, which may be the one installed. */
    data class Done(val latest: Release) : UpdateCheck
}

sealed interface UpdateDownload {
    data object Idle : UpdateDownload

    data class Running(val release: Release, val progress: Float) : UpdateDownload

    data class Failed(val release: Release, val error: Throwable) : UpdateDownload

    /** Downloaded and checked: [file] is [release], signed as the app installed is. */
    data class Ready(val release: Release, val file: File) : UpdateDownload
}

/** Thrown when a downloaded package is not the release, or not signed as the app installed is. */
class UpdateVerificationException(message: String) : IOException(message)

/**
 * Checks the chosen channel for a newer release, downloads it and hands it to the system installer.
 * Downloads go on in the application's scope, so leaving the update page doesn't stop them.
 */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) {
    // Its own client: the app's clients carry Tieba's cookies and parameters, which have no
    // business going to the release host
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private val _check = MutableStateFlow<UpdateCheck>(UpdateCheck.Idle)
    val check: StateFlow<UpdateCheck> = _check.asStateFlow()

    private val _download = MutableStateFlow<UpdateDownload>(UpdateDownload.Idle)
    val download: StateFlow<UpdateDownload> = _download.asStateFlow()

    private val _installing = MutableStateFlow(false)

    /** Whether an install session is on, from handing it over until it fails or replaces the app. */
    val installing: StateFlow<Boolean> = _installing.asStateFlow()

    private var checkJob: Job? = null

    /** What changed in the version installed, from the release notes it was built with; empty when none were written. */
    suspend fun installedNotes(): Map<String, Map<String, List<String>>> = withContext(io) {
        try {
            context.assets.open("release-notes.json").use { json.decodeFromString(it.readBytes().decodeToString()) }
        } catch (_: IOException) {
            emptyMap()
        }
    }
    private var downloadJob: Job? = null

    private val prefs = context.dataStore
    val autoCheck: Flow<Boolean> = prefs.data.map { it[AutoCheckKey] ?: true }
    val channel: Flow<UpdateChannel> = prefs.data.map { prefs ->
        UpdateChannel.entries.firstOrNull { it.key == prefs[ChannelKey] } ?: UpdateChannel.default
    }

    /** A newer release the user hasn't skipped, for the About page's row and the launch hint. */
    val available: Flow<Release?> = combine(check, prefs.data) { check, prefs ->
        (check as? UpdateCheck.Done)?.latest
            ?.takeIf { it.isNewer && (it.required || it.versionCode != prefs[SkippedKey]) }
    }

    /** Whether the launch hint for [release] has been shown, which happens once per release. */
    suspend fun hinted(release: Release): Boolean = prefs.data.first()[HintedKey] == release.versionCode

    suspend fun markHinted(release: Release) {
        prefs.edit { it[HintedKey] = release.versionCode }
    }

    suspend fun setAutoCheck(enabled: Boolean) {
        prefs.edit { it[AutoCheckKey] = enabled }
    }

    suspend fun setChannel(channel: UpdateChannel) {
        prefs.edit { it[ChannelKey] = channel.key }
        checkNow()
    }

    suspend fun skip(release: Release) {
        prefs.edit { it[SkippedKey] = release.versionCode }
    }

    /** Checks unless a check is running. */
    fun checkNow() {
        if (checkJob?.isActive == true) return
        checkJob = scope.launch {
            _check.value = UpdateCheck.Checking
            _check.value = try {
                UpdateCheck.Done(fetch(channel.first())).also {
                    prefs.edit { it[LastCheckKey] = System.currentTimeMillis() }
                }
            } catch (e: Exception) {
                UpdateCheck.Failed(e)
            }
        }
    }

    /** Checks if checking by itself is on and the last check was a day or more ago. */
    fun checkIfDue() {
        scope.launch {
            if (!autoCheck.first()) return@launch
            val last = prefs.data.first()[LastCheckKey] ?: 0L
            if (System.currentTimeMillis() - last >= 1.days.inWholeMilliseconds) checkNow()
        }
    }

    /** The channel's latest release; on beta, the newer of beta's and stable's. */
    private suspend fun fetch(channel: UpdateChannel): Release = when (channel) {
        UpdateChannel.Stable -> fetchManifest(UpdateChannel.Stable)
        UpdateChannel.Beta -> {
            val found = UpdateChannel.entries.map { runCatching { fetchManifest(it) } }
            // Either manifest may be missing, as stable is before the first release
            found.mapNotNull { it.getOrNull() }.maxByOrNull { it.versionCode }
                ?: throw found.first().exceptionOrNull()!!
        }
    }

    private suspend fun fetchManifest(channel: UpdateChannel): Release = withContext(io) {
        val request = Request.Builder()
            .url("$UpdateHost/update/${channel.key}.json")
            // The manifest changes in place: never take it from a cache on the way
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            json.decodeFromString<Release>(response.body.string())
        }
    }

    /** Downloads [release], unless it is downloading or downloaded already. */
    fun download(release: Release) {
        val current = _download.value
        if (current is UpdateDownload.Running || (current is UpdateDownload.Ready && current.release == release)) return
        downloadJob?.cancel()
        downloadJob = scope.launch {
            _download.value = UpdateDownload.Running(release, 0f)
            _download.value = try {
                UpdateDownload.Ready(release, fetchApk(release))
            } catch (e: Exception) {
                UpdateDownload.Failed(release, e)
            }
        }
    }

    private suspend fun fetchApk(release: Release): File = withContext(io) {
        val dir = File(context.cacheDir, UpdateDir).apply { mkdirs() }
        // Only the release being fetched is kept
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "oolong-${release.versionName}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        val request = Request.Builder().url(release.url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 } ?: release.size
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    var shown = 0f
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        val progress = (read.toFloat() / total).coerceIn(0f, 1f)
                        if (progress - shown >= 0.005f) {
                            shown = progress
                            _download.value = UpdateDownload.Running(release, progress)
                        }
                    }
                }
            }
        }
        try {
            verify(file, release, digest.digest().toHex())
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        file
    }

    /** The package must be the release, the same app, and signed by the key the installed app is. */
    private fun verify(file: File, release: Release, sha256: String) {
        if (!sha256.equals(release.sha256, ignoreCase = true)) throw UpdateVerificationException("checksum")
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.path, SignatureFlags)
            ?: throw UpdateVerificationException("unreadable")
        if (archive.packageName != context.packageName) throw UpdateVerificationException("package")
        val installed = pm.getPackageInfo(context.packageName, SignatureFlags)
        val theirs = archive.signerDigests()
        if (theirs.isEmpty() || theirs.none { it in installed.signerDigests() }) {
            throw UpdateVerificationException("signature")
        }
    }

    /**
     * Installs [file] in a session. From Android 12 an app updating itself, once it is its own
     * installer, needs no confirmation; the first time, before Android 12, or without leave to
     * install apps, the system asks, which [UpdateInstallReceiver] passes on.
     */
    fun install(download: UpdateDownload.Ready) {
        if (_installing.value) return
        _installing.value = true
        val file = download.file
        scope.launch(io) {
            // For the notice once the app is replaced: this update is the app's own doing
            prefs.edit { it[PendingUpdateKey] = download.release.versionName }
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(file.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) setRequestUpdateOwnership(true)
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("oolong.apk", 0, file.length()).use { output ->
                    file.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                val intent = Intent(context, UpdateInstallReceiver::class.java)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
            }
        }
    }

    /**
     * Hands the downloaded package to the system's installer, for when the install session can't
     * ask the user itself. True when the installer opened; the app no longer hears how it went.
     */
    fun installWithSystem(): Boolean {
        val file = (_download.value as? UpdateDownload.Ready)?.file?.takeIf(File::exists) ?: return false
        val uri = FileProvider.getUriForFile(context, context.packageName + ".share.FileProvider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (!context.tryStart(intent)) return false
        // The notice after the update still comes, from the package being replaced
        _installing.value = false
        return true
    }

    /** The install session ended without replacing the app: it failed or was turned down. */
    fun installEnded() {
        _installing.value = false
        scope.launch { prefs.edit { it.remove(PendingUpdateKey) } }
    }

    /** The version the app updated itself to, once, if this start follows an update it installed. */
    suspend fun takeUpdated(): String? {
        val version = prefs.data.first()[PendingUpdateKey] ?: return null
        prefs.edit { it.remove(PendingUpdateKey) }
        return version
    }

    private fun PackageInfo.signerDigests(): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = signingInfo ?: return emptySet()
            if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            this.signatures
        }
        return signatures.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toHex() }.toSet()
    }

    companion object {
        /** Under the cache, where the package being downloaded is kept. */
        const val UpdateDir = "update"

        @Suppress("DEPRECATION")
        private val SignatureFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

        private val AutoCheckKey = booleanPreferencesKey("update_auto_check")

        // Read by UsageStats too, which sends the channel along
        internal val ChannelKey = stringPreferencesKey("update_channel")
        private val SkippedKey = intPreferencesKey("update_skipped_version")
        private val HintedKey = intPreferencesKey("update_hinted_version")
        private val LastCheckKey = longPreferencesKey("update_last_check")
        private val PendingUpdateKey = stringPreferencesKey("update_pending_version")
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UpdateEntryPoint {
    fun updateRepository(): UpdateRepository
}

/** For the update receivers, which can't be injected. */
internal val Context.updateRepository: UpdateRepository
    get() = EntryPointAccessors.fromApplication(applicationContext, UpdateEntryPoint::class.java).updateRepository()
