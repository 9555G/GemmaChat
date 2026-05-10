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
    var mtpDrafterEnabled = false
        private set

    suspend fun loadModel(modelPath: String, drafterPath: String? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // Check if GGUF - not supported yet
                if (modelPath.lowercase().endsWith(".gguf")) {
                    return@withContext Result.failure(
                        Exception("GGUF format is not yet supported.\n\nPlease use a .task file.\nDownload from: kaggle.com/models/google/gemma")
                    )
                }

                val internalPath = getInternalPath(modelPath)
                    ?: return@withContext Result.failure(
                        Exception("Cannot read model file.\nPath: $modelPath\n\nMake sure the file exists.")
                    )

                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(internalPath)
                    .setMaxTokens(2048)
                    .build()

                llmInference = LlmInference.createFromOptions(context, options)
                isLoaded = true
                Log.d(TAG, "Loaded: $internalPath")
                Result.success(Unit)
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

    fun close() { llmInference?.close(); isLoaded = false }
}
