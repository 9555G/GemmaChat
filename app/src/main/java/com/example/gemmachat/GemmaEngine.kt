package com.example.gemmachat

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GemmaEngine(private val context: Context) {

    companion object {
        private const val TAG = "GemmaEngine"
        const val DEFAULT_MODEL_PATH = "/sdcard/Download/gemma4.task"
        const val DEFAULT_DRAFTER_PATH = "/sdcard/Download/gemma4-drafter.task"
    }

    private var llmInference: LlmInference? = null
    var isLoaded = false
        private set
    var mtpDrafterEnabled = false
        private set

    suspend fun loadModel(
        modelPath: String = DEFAULT_MODEL_PATH,
        drafterPath: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(2048)
                .setTopK(40)
                .setTemperature(0.8f)
                .setRandomSeed(101)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
            isLoaded = true
            Log.d(TAG, "Loaded: $modelPath")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed: ${e.message}", e)
            isLoaded = false
            Result.failure(e)
        }
    }

    fun startSession() { /* no-op */ }

    fun sendMessage(
        userMessage: String,
        onToken: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        val inference = llmInference ?: run { onError("Model not loaded"); return }
        try {
            inference.generateResponseAsync(userMessage) { partial, done ->
                partial?.let { onToken(it) }
                if (done) onComplete()
            }
        } catch (e: Exception) {
            onError(e.message ?: "Error")
        }
    }

    fun close() {
        llmInference?.close()
        isLoaded = false
    }
}
