package com.example.gemmachat

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
            // Copy model to internal storage so MediaPipe can open it
            val internalPath = copyToInternal(modelPath)
                ?: return@withContext Result.failure(
                    Exception("Cannot read model file. Check file exists at:\n$modelPath")
                )

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(internalPath)
                .setMaxTokens(2048)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            isLoaded = true
            Log.d(TAG, "Loaded from: $internalPath")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed: ${e.message}", e)
            isLoaded = false
            Result.failure(e)
        }
    }

    /**
     * Copy model file to app's internal files dir.
     * MediaPipe can always read from internal storage.
     * Returns the internal path, or null if copy failed.
     */
    private fun copyToInternal(sourcePath: String): String? {
        return try {
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                Log.e(TAG, "Source file not found: $sourcePath")
                return null
            }

            val destDir  = context.getExternalFilesDir(null) ?: context.filesDir
            val destFile = File(destDir, sourceFile.name)

            // Skip copy if already there and same size
            if (destFile.exists() && destFile.length() == sourceFile.length()) {
                Log.d(TAG, "Already in internal: ${destFile.absolutePath}")
                return destFile.absolutePath
            }

            Log.d(TAG, "Copying ${sourceFile.length() / 1024 / 1024}MB to internal...")
            sourceFile.copyTo(destFile, overwrite = true)
            Log.d(TAG, "Copy done: ${destFile.absolutePath}")
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Copy failed: ${e.message}", e)
            null
        }
    }

    fun startSession() { }

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
