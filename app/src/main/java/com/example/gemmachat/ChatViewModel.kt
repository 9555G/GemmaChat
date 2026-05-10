package com.example.gemmachat

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class ChatMessage(val text: String, val isUser: Boolean, val isStreaming: Boolean = false)

sealed class EngineState {
    object Idle : EngineState()
    object Loading : EngineState()
    data class Ready(val modelName: String, val mtpActive: Boolean, val speedupLabel: String) : EngineState()
    data class Error(val message: String) : EngineState()
}

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val modelId: String, val progress: Int) : DownloadState()
    data class Done(val modelId: String, val path: String) : DownloadState()
    data class Failed(val modelId: String, val error: String) : DownloadState()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = GemmaEngine(application)

    val messages      = mutableStateListOf<ChatMessage>()
    val engineState   = mutableStateOf<EngineState>(EngineState.Idle)
    val isGenerating  = mutableStateOf(false)
    val modelPath     = mutableStateOf(GemmaEngine.DEFAULT_MODEL_PATH)
    val drafterPath   = mutableStateOf(GemmaEngine.DEFAULT_DRAFTER_PATH)
    val useDrafter    = mutableStateOf(false)
    val downloadState = mutableStateOf<DownloadState>(DownloadState.Idle)
    val hfToken       = mutableStateOf("")
    private var downloadJob: Job? = null

    fun loadModel() {
        viewModelScope.launch {
            engineState.value = EngineState.Loading
            val result = engine.loadModel(modelPath.value)
            engineState.value = if (result.isSuccess) {
                EngineState.Ready(
                    modelName    = modelPath.value.substringAfterLast("/"),
                    mtpActive    = false,
                    speedupLabel = "Gemma 4 E4B • On-device"
                )
            } else {
                EngineState.Error(result.exceptionOrNull()?.message ?: "Load failed")
            }
        }
    }

    fun loadFromCatalogue(model: LiteRTModel) {
        modelPath.value = ModelDownloader.getDestPath("${model.id}.task")
        loadModel()
    }

    fun downloadModel(model: LiteRTModel) {
        if (hfToken.value.isBlank()) {
            downloadState.value = DownloadState.Failed(model.id, "Enter your HuggingFace token first")
            return
        }
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            downloadState.value = DownloadState.Downloading(model.id, 0)
            val fileMap = mapOf(
                "gemma4-e4b"         to "google/gemma-4-E4B-it-litert-preview/resolve/main/gemma4-E4B-it-Q8_0.task",
                "gemma4-e2b"         to "google/gemma-4-E2B-it-litert-preview/resolve/main/gemma4-E2B-it-Q8_0.task",
                "gemma4-e4b-drafter" to "google/gemma-4-E4B-it-assistant/resolve/main/gemma-4-e4b-it-assistant.task",
                "gemma4-e2b-drafter" to "google/gemma-4-E2B-it-assistant/resolve/main/gemma-4-e2b-it-assistant.task",
                "gemma3n-e4b"        to "google/gemma-3n-E4B-it-litert-preview/resolve/main/gemma-3n-e4b-it-int4.litertlm",
                "gemma3n-e2b"        to "google/gemma-3n-E2B-it-litert-preview/resolve/main/gemma-3n-e2b-it-int4.litertlm",
                "gemma3-1b"          to "google/gemma-3-1b-it-litert/resolve/main/gemma-3-1b-it-int4.task",
                "deepseek-r1-1.5b"   to "litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/model.task",
                "phi-2"              to "litert-community/Phi-2/resolve/main/model.task",
                "qwen-1.5b"          to "litert-community/Qwen2.5-1.5B-Instruct/resolve/main/model.task"
            )
            val path = fileMap[model.id] ?: "${model.hfUrl.removePrefix("https://huggingface.co/")}/resolve/main/model.task"
            val downloadUrl = "https://huggingface.co/$path"
            val fileName = "${model.id}.task"
            ModelDownloader.download(
                hfUrl      = downloadUrl,
                hfToken    = hfToken.value.trim(),
                destName   = fileName,
                onProgress = { pct -> downloadState.value = DownloadState.Downloading(model.id, pct) },
                onComplete = { filePath -> downloadState.value = DownloadState.Done(model.id, filePath) },
                onError    = { err -> downloadState.value = DownloadState.Failed(model.id, err) }
            )
        }
    }

    fun cancelDownload() { downloadJob?.cancel(); downloadState.value = DownloadState.Idle }
    fun resetDownload()  { downloadState.value = DownloadState.Idle }

    fun sendMessage(text: String) {
        if (text.isBlank() || isGenerating.value || !engine.isLoaded) return
        messages.add(ChatMessage(text = text, isUser = true))
        val idx = messages.size
        messages.add(ChatMessage(text = "", isUser = false, isStreaming = true))
        isGenerating.value = true
        var accumulated = ""
        engine.sendMessage(
            userMessage = text,
            onToken     = { tok -> accumulated += tok; messages[idx] = ChatMessage(accumulated, false, true) },
            onComplete  = { messages[idx] = ChatMessage(accumulated, false, false); isGenerating.value = false },
            onError     = { err -> messages[idx] = ChatMessage("⚠️ $err", false, false); isGenerating.value = false }
        )
    }

    fun newChat() { messages.clear(); engine.startSession() }
    override fun onCleared() { super.onCleared(); engine.close() }
}
