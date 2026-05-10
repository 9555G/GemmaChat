package com.example.gemmachat

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.CoroutineScope
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
    private var ggufEngine: GgufEngine? = null
    private var isGguf = false

    var isLoaded = false
        private set
    var mtpDrafterEnabled = false
        private set

    suspend fun loadModel(
        modelPath: String,
        drafterPath: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            isGguf = modelPath.lowercase().endsWith(".gguf")

            if (isGguf) {
                // Route to llama.cpp for GGUF files
                var loadError: String? = null
                var loaded = false
                val scope = CoroutineScope(Dispatchers.IO)
                val engine = GgufEngine(context, scope)
                engine.load(modelPath) { success, error ->
                    loaded = success
                    loadError = error
                }
                // Wait briefly for load callback
                kotlinx.coroutines.delay(5000)
                if (loaded) {
                    ggufEngine = engine
                    isLoaded = true
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(loadError ?: "GGUF load failed"))
                }
            } else {
                // Route to MediaPipe for .task files
                val internalPath = getInternalPath(modelPath)
                    ?: return@withContext Result.failure(
                        Exception("Cannot read model file.\nPath: $modelPath")
                    )

                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(internalPath)
                    .setMaxTokens(2048)
                    .build()

                llmInference = LlmInference.createFromOptions(context, options)
                isLoaded = true
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Load failed: ${e.message}", e)
            isLoaded = false
            Result.failure(e)
        }
    }

    private fun getInternalPath(modelPath: String): String? {
        val destDir  = context.getExternalFilesDir(null) ?: context.filesDir
        val destName = modelPath.substringAfterLast("/")
            .replace(" ", "_").ifBlank { "model.task" }
        val destFile = File(destDir, destName)

        if (destFile.exists() && destFile.length() > 1024) return destFile.absolutePath

        if (modelPath.startsWith("content://")) {
            return copyFromUri(Uri.parse(modelPath), destFile)
        }

        val srcFile = File(modelPath)
        if (srcFile.exists()) return copyFile(srcFile, destFile)

        listOf(
            "/sdcard/Download/$destName",
            "/storage/emulated/0/Download/$destName"
        ).forEach { path ->
            val f = File(path)
            if (f.exists()) return copyFile(f, destFile)
        }
        return null
    }

    private fun copyFromUri(uri: Uri, destFile: File): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) output.write(buf, 0, n)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            destFile.delete(); null
        }
    }

    private fun copyFile(src: File, dest: File): String? {
        return try {
            if (dest.exists() && dest.length() == src.length()) return dest.absolutePath
            src.copyTo(dest, overwrite = true)
            dest.absolutePath
        } catch (e: Exception) { null }
    }

    fun startSession() { }

    fun sendMessage(
        userMessage: String,
        onToken: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (isGguf) {
            ggufEngine?.generate(userMessage, onToken, onComplete, onError)
                ?: onError("GGUF engine not loaded")
        } else {
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
    }

    fun close() {
        llmInference?.close()
        ggufEngine?.close()
        isLoaded = false
    }
}
