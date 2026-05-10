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

    suspend fun loadModel(
        modelPath: String = DEFAULT_MODEL_PATH,
        drafterPath: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Get internal path - copy via URI or direct file
            val internalPath = getInternalPath(modelPath)
                ?: return@withContext Result.failure(
                    Exception("Cannot read model file.\nPath: $modelPath\n\nMake sure you selected the correct file.")
                )

            Log.d(TAG, "Loading from: $internalPath")

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(internalPath)
                .setMaxTokens(2048)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            isLoaded = true
            Log.d(TAG, "Model loaded OK")
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
            .replace(" ", "_")
            .ifBlank { "model.task" }
        val destFile = File(destDir, destName)

        // 1. Already copied before - same size shortcut
        if (destFile.exists() && destFile.length() > 1024) {
            Log.d(TAG, "Using cached: ${destFile.absolutePath}")
            return destFile.absolutePath
        }

        // 2. Try as content URI
        if (modelPath.startsWith("content://")) {
            return copyFromUri(Uri.parse(modelPath), destFile)
        }

        // 3. Try as direct file path
        val srcFile = File(modelPath)
        if (srcFile.exists()) {
            return copyFile(srcFile, destFile)
        }

        // 4. Try common locations
        val candidates = listOf(
            "/sdcard/Download/$destName",
            "/storage/emulated/0/Download/$destName",
            "/sdcard/$destName"
        )
        for (path in candidates) {
            val f = File(path)
            if (f.exists()) {
                Log.d(TAG, "Found at: $path")
                return copyFile(f, destFile)
            }
        }

        Log.e(TAG, "File not found: $modelPath")
        return null
    }

    private fun copyFromUri(uri: Uri, destFile: File): String? {
        return try {
            Log.d(TAG, "Copying from URI to ${destFile.absolutePath}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    var n: Int
                    var total = 0L
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        total += n
                    }
                    Log.d(TAG, "Copied ${total / 1024 / 1024}MB from URI")
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "URI copy failed: ${e.message}", e)
            destFile.delete()
            null
        }
    }

    private fun copyFile(src: File, dest: File): String? {
        return try {
            if (dest.exists() && dest.length() == src.length()) {
                return dest.absolutePath
            }
            Log.d(TAG, "Copying ${src.length() / 1024 / 1024}MB...")
            src.copyTo(dest, overwrite = true)
            dest.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "File copy failed: ${e.message}", e)
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
