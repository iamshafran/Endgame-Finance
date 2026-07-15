package com.endgamefinance.data.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Thin wrapper over LiteRT-LM. Loads the local .litertlm model and runs text
 * generation fully on-device (no network). Engine init is slow (~seconds), so
 * it is created lazily and reused; access is serialized (one inference at a
 * time) to keep memory bounded on-device.
 */
object GemmaEngine {

    @Volatile
    private var engine: Engine? = null

    // Set when a GPU/NPU inference failed at runtime (e.g. missing OpenCL) so
    // we reload on CPU without changing the user's saved preference.
    @Volatile
    private var forceCpu: Boolean = false
    private val mutex = Mutex()

    val isLoaded: Boolean get() = engine != null

    private const val TAG = "GemmaEngine"

    /** The backend the loaded engine is actually using (may differ from the
     *  preference if we fell back). Null until the first successful load. */
    private val _activeBackend = MutableStateFlow<String?>(null)
    val activeBackend: StateFlow<String?> = _activeBackend.asStateFlow()

    /** Loads the model if needed. Safe to call repeatedly. Honors the backend
     *  preference, falling back to CPU if GPU/NPU initialization fails. */
    private suspend fun ensureEngine(context: Context): Engine = mutex.withLock {
        engine ?: run {
            require(AiModel.isReady(context)) { "The AI model isn't downloaded yet." }
            val path = AiModel.modelFile(context).absolutePath
            val pref = if (forceCpu) AiPrefs.CPU else AiPrefs.backendValue(context)

            // The NPU backend must be told where the QNN runtime libraries live
            // (libLiteRtDispatch_Qualcomm.so + libQnnHtp*/libQnnSystem, bundled in
            // jniLibs and extracted to the app's native lib dir). Passing no dir
            // leaves it empty and the Qualcomm dispatch lib is never found.
            val nativeLibDir = context.applicationInfo.nativeLibraryDir

            fun backendFor(name: String): Backend = when (name) {
                AiPrefs.CPU -> Backend.CPU()
                AiPrefs.NPU -> Backend.NPU(nativeLibDir)
                else -> Backend.GPU()
            }

            fun tryStart(name: String): Engine =
                Engine(EngineConfig(modelPath = path, backend = backendFor(name)))
                    .also {
                        it.initialize()
                        _activeBackend.value = name
                        Log.i(TAG, "Engine initialized on backend=$name")
                    }

            val started = try {
                tryStart(pref)
            } catch (e: Throwable) {
                if (pref == AiPrefs.CPU) throw e
                Log.w(TAG, "Backend '$pref' init failed; falling back to CPU: ${e.message}")
                tryStart(AiPrefs.CPU)
            }
            engine = started
            started
        }
    }

    /** Generates a completion for [prompt]. Returns the model's text response.
     *  Falls back to CPU if a GPU/NPU inference fails at runtime. */
    suspend fun generate(context: Context, prompt: String): String =
        withContext(Dispatchers.Default) {
            try {
                runOnce(context, prompt)
            } catch (e: Throwable) {
                if (_activeBackend.value != AiPrefs.CPU && !forceCpu) {
                    Log.w(TAG, "Inference on '${_activeBackend.value}' failed; retrying on CPU: ${e.message}")
                    forceCpu = true
                    close()
                    runOnce(context, prompt)
                } else {
                    throw e
                }
            }
        }

    private suspend fun runOnce(context: Context, prompt: String): String {
        val eng = ensureEngine(context)
        return mutex.withLock {
            val conversation = eng.createConversation()
            conversation.sendMessage(prompt).toString().trim()
        }
    }

    fun close() {
        engine = null
        _activeBackend.value = null
    }

    /** Called when the user explicitly changes the backend preference. */
    fun resetBackendChoice() {
        forceCpu = false
        close()
    }
}
