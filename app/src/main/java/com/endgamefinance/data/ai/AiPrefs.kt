package com.endgamefinance.data.ai

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** AI runtime preferences. Backend default is GPU (fast on modern devices) with
 *  automatic CPU fallback in GemmaEngine if GPU/NPU init fails. */
object AiPrefs {

    const val GPU = "gpu"
    const val CPU = "cpu"
    const val NPU = "npu"

    private const val PREFS = "ai_prefs"
    private const val KEY_BACKEND = "backend"

    private var flow: MutableStateFlow<String>? = null

    fun backend(context: Context): StateFlow<String> {
        flow?.let { return it }
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return MutableStateFlow(prefs.getString(KEY_BACKEND, GPU) ?: GPU)
            .also { flow = it }
    }

    fun backendValue(context: Context): String = backend(context).value

    fun setBackend(context: Context, value: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_BACKEND, value).apply()
        flow?.value = value
        // Force a reload with the new backend on next query (clears any CPU fallback).
        GemmaEngine.resetBackendChoice()
    }
}
