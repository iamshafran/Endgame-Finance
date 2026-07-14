package com.endgamefinance.data.ai

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

sealed interface ModelState {
    data object Absent : ModelState
    data class Downloading(val bytes: Long, val total: Long) : ModelState
    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

/**
 * The on-device AI model file: resumable download (the app's ONLY network use),
 * local storage, and lifecycle. Uses the JDK's HttpURLConnection — no
 * networking library. Inference (Milestone 7.3) reads this file locally with
 * no further network.
 */
object AiModel {

    const val BUILD_GENERIC = "generic"
    const val BUILD_NPU_SM8750 = "npu_sm8750"

    private const val GENERIC_URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
    private const val NPU_SM8750_URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it_qualcomm_sm8750.litertlm"

    /** Kept for the settings "custom URL" default. */
    const val DEFAULT_URL = GENERIC_URL

    fun urlForBuild(build: String): String =
        if (build == BUILD_NPU_SM8750) NPU_SM8750_URL else GENERIC_URL

    /** The build a URL corresponds to, so disk_build records what was actually
     *  fetched — not merely what was selected. A custom URL keeps [fallback]. */
    private fun buildForUrl(url: String, fallback: String): String = when (url) {
        GENERIC_URL -> BUILD_GENERIC
        NPU_SM8750_URL -> BUILD_NPU_SM8750
        else -> fallback
    }

    private const val PREFS = "ai_model_prefs"
    private const val KEY_SELECTED = "selected_build"
    private const val KEY_DISK = "disk_build"

    private const val MODEL_NAME = "gemma-4-E2B-it.litertlm"
    private const val MIN_VALID_BYTES = 1_000_000_000L // a real model is >1GB; guards partials
    private const val BUFFER = 1 shl 20 // 1 MiB

    private val _state = MutableStateFlow<ModelState>(ModelState.Absent)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    // App-scoped so a download survives leaving the Settings screen.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Which build the user wants (generic vs NPU). */
    fun selectedBuild(context: Context): String =
        prefs(context).getString(KEY_SELECTED, BUILD_GENERIC) ?: BUILD_GENERIC

    /** Which build is actually on disk (null if none). */
    fun diskBuild(context: Context): String? = prefs(context).getString(KEY_DISK, null)

    /**
     * Switch the desired build. If it differs from what's on disk, the current
     * model is deleted so the correct build re-downloads.
     */
    fun setSelectedBuild(context: Context, build: String) {
        prefs(context).edit().putString(KEY_SELECTED, build).apply()
        if (build != diskBuild(context) && modelFile(context).exists()) {
            delete(context)
        } else {
            refresh(context)
        }
    }

    /** Idempotent: starts a download of the selected build if none is running. */
    fun startDownload(context: Context, url: String? = null) {
        if (job?.isActive == true) return
        val appContext = context.applicationContext
        val effectiveUrl = url ?: urlForBuild(selectedBuild(appContext))
        val build = buildForUrl(effectiveUrl, selectedBuild(appContext))
        job = scope.launch { download(appContext, effectiveUrl, build) }
    }

    fun cancelDownload() {
        job?.cancel()
        job = null
    }

    fun modelFile(context: Context): File =
        File(File(context.filesDir, "models").apply { mkdirs() }, MODEL_NAME)

    private fun partFile(context: Context): File =
        File(File(context.filesDir, "models").apply { mkdirs() }, "$MODEL_NAME.part")

    /** Call on startup to reflect any already-downloaded model. */
    fun refresh(context: Context) {
        _state.value =
            if (isReady(context)) ModelState.Ready else ModelState.Absent
    }

    fun isReady(context: Context): Boolean {
        val f = modelFile(context)
        return f.exists() && f.length() >= MIN_VALID_BYTES
    }

    fun delete(context: Context) {
        modelFile(context).delete()
        partFile(context).delete()
        _state.value = ModelState.Absent
    }

    /**
     * Downloads [url] to app storage, resuming a prior partial if present.
     * Cancellable via the calling coroutine. Updates [state] throughout.
     */
    suspend fun download(
        context: Context,
        url: String = DEFAULT_URL,
        build: String = buildForUrl(url, selectedBuild(context)),
    ) {
        withContext(Dispatchers.IO) {
            val target = modelFile(context)
            if (target.exists() && target.length() >= MIN_VALID_BYTES) {
                _state.value = ModelState.Ready
                return@withContext
            }
            val part = partFile(context)
            try {
                var existing = if (part.exists()) part.length() else 0L
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 30_000
                    readTimeout = 30_000
                    if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
                }
                conn.connect()
                val code = conn.responseCode
                if (code !in 200..299) {
                    _state.value = ModelState.Failed("Server returned HTTP $code")
                    return@withContext
                }
                // If the server ignored our Range (200 not 206), restart cleanly.
                val resuming = existing > 0 && code == HttpURLConnection.HTTP_PARTIAL
                if (!resuming) {
                    existing = 0L
                    part.delete()
                }
                val remaining = conn.contentLengthLong
                val total = if (remaining >= 0) existing + remaining else -1L

                var downloaded = existing
                conn.inputStream.use { input ->
                    FileOutputStream(part, resuming).use { out ->
                        val buf = ByteArray(BUFFER)
                        var lastEmit = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            downloaded += n
                            if (downloaded - lastEmit >= BUFFER * 4) {
                                _state.value = ModelState.Downloading(downloaded, total)
                                lastEmit = downloaded
                            }
                        }
                        out.flush()
                    }
                }
                if (downloaded < MIN_VALID_BYTES) {
                    _state.value = ModelState.Failed("Download incomplete — try again to resume")
                    return@withContext
                }
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
                prefs(context).edit().putString(KEY_DISK, build).apply()
                _state.value = ModelState.Ready
            } catch (e: CancellationException) {
                // Keep the .part file so the next attempt resumes; reflect stopped state
                _state.value = if (isReady(context)) ModelState.Ready else ModelState.Absent
                throw e
            } catch (e: Exception) {
                _state.value = ModelState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }
}
