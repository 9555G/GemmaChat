package com.example.gemmachat

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ModelDownloader {

    private const val TAG = "ModelDownloader"
    const val MODELS_DIR = "/sdcard/Download/"

    /**
     * Download a model file from HuggingFace.
     * Streams the file with progress updates.
     *
     * @param hfUrl      Full HuggingFace URL to the .task file
     * @param hfToken    HuggingFace access token (required for gated models like Gemma)
     * @param destName   Filename to save as e.g. "gemma4.task"
     * @param onProgress Called with 0-100 progress percentage
     * @param onComplete Called with the final file path when done
     * @param onError    Called with error message on failure
     */
    suspend fun download(
        hfUrl: String,
        hfToken: String,
        destName: String,
        onProgress: (Int) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {

        val destFile = File(MODELS_DIR + destName)
        val tempFile = File(MODELS_DIR + destName + ".tmp")

        try {
            // Build the direct download URL
            // HuggingFace format: /resolve/main/filename
            val downloadUrl = hfUrl
                .replace("/blob/", "/resolve/")
                .replace("huggingface.co", "huggingface.co")

            Log.d(TAG, "Downloading: $downloadUrl")

            val connection = URL(downloadUrl).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 30_000
                readTimeout = 60_000
                setRequestProperty("Authorization", "Bearer $hfToken")
                setRequestProperty("User-Agent", "GemmaChat/1.0")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val msg = when (responseCode) {
                    401 -> "Invalid HuggingFace token — check your token"
                    403 -> "Access denied — accept Gemma license on HuggingFace first"
                    404 -> "File not found at URL"
                    else -> "Server error $responseCode"
                }
                withContext(Dispatchers.Main) { onError(msg) }
                return@withContext
            }

            val totalBytes = connection.contentLengthLong
            Log.d(TAG, "File size: ${totalBytes / 1024 / 1024} MB")

            tempFile.parentFile?.mkdirs()
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (!isActive) {
                            // Coroutine cancelled — clean up
                            tempFile.delete()
                            withContext(Dispatchers.Main) { onError("Download cancelled") }
                            return@withContext
                        }
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        if (totalBytes > 0) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                            withContext(Dispatchers.Main) { onProgress(progress) }
                        }
                    }
                }
            }

            // Move temp → final file
            tempFile.renameTo(destFile)
            Log.d(TAG, "Download complete: ${destFile.absolutePath}")
            withContext(Dispatchers.Main) { onComplete(destFile.absolutePath) }

        } catch (e: Exception) {
            tempFile.delete()
            Log.e(TAG, "Download error: ${e.message}", e)
            withContext(Dispatchers.Main) { onError(e.message ?: "Download failed") }
        }
    }

    fun getDestPath(destName: String) = MODELS_DIR + destName

    fun isDownloaded(destName: String) = File(MODELS_DIR + destName).exists()

    fun fileSizeMB(destName: String): Long {
        val f = File(MODELS_DIR + destName)
        return if (f.exists()) f.length() / 1024 / 1024 else 0L
    }
}
