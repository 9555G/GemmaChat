package com.example.gemmachat

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.ljcamargo.llamacpp.LlamaHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class GgufEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "GgufEngine"
    }

    private var helper: LlamaHelper? = null
    var isLoaded = false
        private set

    fun load(uriString: String, onDone: (Boolean, String?) -> Unit) {
        try {
            helper = LlamaHelper(
                contentResolver = context.contentResolver,
                coroutineScope  = scope
            )
            helper!!.load(
                path          = uriString,
                contextLength = 2048
            ) { id ->
                isLoaded = id >= 0
                if (isLoaded) {
                    Log.d(TAG, "GGUF loaded OK")
                    onDone(true, null)
                } else {
                    onDone(false, "Failed to load GGUF model")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "GGUF load error: ${e.message}", e)
            onDone(false, e.message ?: "GGUF load failed")
        }
    }

    fun generate(
        prompt: String,
        onToken: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        val h = helper ?: run { onError("GGUF model not loaded"); return }
        try {
            h.setOnToken { token -> onToken(token) }
            h.setOnComplete { onComplete() }
            h.predict(prompt)
        } catch (e: Exception) {
            onError(e.message ?: "GGUF generation failed")
        }
    }

    fun close() {
        helper?.stop()
        isLoaded = false
    }
}
