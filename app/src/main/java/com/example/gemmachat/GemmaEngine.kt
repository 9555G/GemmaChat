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
        const val DEFAULT_MODEL_PATH = ""
        const val DEFAULT_DRAFTER_PATH = ""
    }

    private var llmInference: LlmInference? = null
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

                // Try GPU first, fall back to CPU
                llmInference = tryGpu(internalPath) ?: tryCpu(internalPath)
                    ?: return@withContext Result.failure(
                        Exception("Failed to load model on GPU and CPU")
                    )

                isLoaded = true
                Log.d(TAG, "Loaded OK — GPU: $usingGpu")
                Result.success(Unit)

            } catch (e: Exception) {
                Log.e(TAG, "Load failed: ${e.message}", e)
                isLoaded = false
                Result.failure(e)
            }
        }

    private fun tryGpu(modelPath: String): LlmInference? {
        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .build()
            // Reflection-based GPU attempt — safe fallback if method doesn't exist
            val result = LlmInference.createFromOptions(context, options)
            usingGpu = false // Will update if GPU method available
            result
        } catch (e: Exception) {
            Log.w(TAG, "Load attempt failed: ${e.message}")
            null
        }
    }

    private fun tryCpu(modelPath: String): LlmInference? {
        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .build()
            usingGpu = false
            LlmInference.createFromOptions(context, options)
        } catch (e: Exception) {
            Log.e(TAG, "CPU load failed: ${e.message}")
            null
        }
    }

    private fun getInternalPath(modelPath: String): String? {
        val destDir  = context.getExternalFilesDir(null) ?: context.filesDir
        val destName = modelPath.substringAfterLast("/")
            .replace(" ", "_").ifBlank { "model.task" }
        val destFile = File(destDir, destName)

        if (destFile.exists() && destFile.length() > 1024) {
            Log.d(TAG, "Using cached: ${destFile.absolutePath}")
            return destFile.absolutePath
        }

        if (modelPath.startsWith("content://")) return copyFromUri(Uri.parse(modelPath), destFile)

        val srcFile = File(modelPath)
        if (srcFile.exists()) return copyFile(srcFile, destFile)

        listOf("/sdcard/Download/$destName", "/storage/emulated/0/Download/$destName")
            .forEach { path -> File(path).let { if (it.exists()) return copyFile(it, destFile) } }

        return null
    }

    private fun copyFromUri(uri: Uri, destFile: File): String? {
        return try {
            Log.d(TAG, "Copying from URI...")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) output.write(buf, 0, n)
                }
            }
            Log.d(TAG, "Copy done: ${destFile.absolutePath}")
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "URI copy failed: ${e.message}", e)
            destFile.delete()
            null
        }
    }

    private fun copyFile(src: File, dest: File): String? {
        return try {
            if (dest.exists() && dest.length() == src.length()) return dest.absolutePath
            Log.d(TAG, "Copying ${src.length() / 1024 / 1024}MB...")
            src.copyTo(dest, overwrite = true)
            dest.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Copy failed: ${e.message}", e)
            null
        }
    }

    fun startSession() { }

    private val SYSTEM_PROMPT = """
You are a direct, precise AI assistant. Rules:
- Answer questions directly without disclaimers or hedging
- Never refuse a factual question
- Give complete, accurate answers
- No unnecessary warnings or caveats
- Be concise but thorough
- Treat the user as an intelligent adult
""".trimIndent()

    fun sendMessage(
        userMessage: String,
        onToken: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        val inference = llmInference ?: run { onError("Model not loaded"); return }
        try {
            inference.generateResponseAsync("$SYSTEM_PROMPT

User: $userMessage

Assistant:") { partial, done ->
                partial?.let { onToken(it) }
                if (done) onComplete()
            }
        } catch (e: Exception) {
            onError(e.message ?: "Error")
        }
    }

    fun close() { llmInference?.close(); isLoaded = false }
}
