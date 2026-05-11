package com.example.gemmachat

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GemmaEngine(private val context: Context) {

    companion object {
        private const val TAG = "GemmaEngine"
        const val DEFAULT_MODEL_PATH = ""
        const val DEFAULT_DRAFTER_PATH = ""
    }

    private var llmInference: LlmInference? = null
    private var session: LlmInferenceSession? = null
    var isLoaded = false
        private set
    var usingGpu = false
        private set

    suspend fun loadModel(modelPath: String, drafterPath: String? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (modelPath.lowercase().endsWith(".gguf")) {
                    return@withContext Result.failure(
                        Exception("GGUF not supported. Please use a .task file.")
                    )
                }

                val internalPath = getInternalPath(modelPath)
                    ?: return@withContext Result.failure(
                        Exception("Cannot read model file:\n$modelPath")
                    )

                // ✅ Try GPU first — ~4x faster prefill
                llmInference = tryLoadWithBackend(internalPath, LlmInference.Backend.GPU)
                if (llmInference != null) {
                    usingGpu = true
                    Log.d(TAG, "✅ GPU backend active")
                } else {
                    // Fallback to CPU if GPU not supported
                    llmInference = tryLoadWithBackend(internalPath, LlmInference.Backend.CPU)
                    usingGpu = false
                    Log.d(TAG, "⚠️ Fallback to CPU backend")
                }

                llmInference ?: return@withContext Result.failure(
                    Exception("Failed to initialize model on both GPU and CPU")
                )

                // ✅ Create session with TopK and Temperature
                startNewSession()
                isLoaded = true
                Result.success(Unit)

            } catch (e: Exception) {
                Log.e(TAG, "Load failed: ${e.message}", e)
                isLoaded = false
                Result.failure(e)
            }
        }

    private fun tryLoadWithBackend(
        modelPath: String,
        backend: LlmInference.Backend
    ): LlmInference? {
        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)           // ✅ Reduced from 2048 — faster
                .setPreferredBackend(backend)  // ✅ GPU or CPU
                .build()
            LlmInference.createFromOptions(context, options)
        } catch (e: Exception) {
            Log.w(TAG, "Backend $backend failed: ${e.message}")
            null
        }
    }

    fun startNewSession() {
        session?.close()
        session = null
        val inference = llmInference ?: return
        try {
            // ✅ TopK and Temperature go in SESSION options (not LlmInferenceOptions)
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(1)           // ✅ TopK=1 = greedy decoding = fastest + most coherent
                .setTemperature(0.1f) // ✅ Low temp = fast, focused responses
                .build()
            session = LlmInferenceSession.createFromOptions(inference, sessionOptions)
            Log.d(TAG, "New session started")
        } catch (e: Exception) {
            Log.w(TAG, "Session creation failed: ${e.message} — using direct inference")
        }
    }

    fun sendMessage(
        userMessage: String,
        onToken: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        val s = session
        val inference = llmInference

        if (s != null) {
            // ✅ Use session for multi-turn (reuses KV cache between turns)
            try {
                s.addQueryChunk(userMessage)
                s.generateResponseAsync { partial, done ->
                    partial?.let { onToken(it) }
                    if (done) onComplete()
                }
            } catch (e: Exception) {
                onError(e.message ?: "Generation error")
            }
        } else if (inference != null) {
            // Fallback to direct inference
            try {
                inference.generateResponseAsync(userMessage) { partial, done ->
                    partial?.let { onToken(it) }
                    if (done) onComplete()
                }
            } catch (e: Exception) {
                onError(e.message ?: "Generation error")
            }
        } else {
            onError("Model not loaded")
        }
    }

    fun startSession() = startNewSession()

    fun close() {
        session?.close()
        llmInference?.close()
        isLoaded = false
    }
}
