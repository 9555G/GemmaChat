package com.example.gemmachat

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.io.File

// ─── Message model ────────────────────────────────────────────────────────────

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val isStreaming: Boolean = false
)

// ─── Load state ───────────────────────────────────────────────────────────────

sealed class EngineState {
    object Idle : EngineState()
    object Loading : EngineState()
    data class Ready(
        val modelName: String,
        val mtpActive: Boolean,
        val speedupLabel: String   // e.g. "up to 3x faster"
    ) : EngineState()
    data class Error(val message: String) : EngineState()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = GemmaEngine(application)

    val messages    = mutableStateListOf<ChatMessage>()
    val engineState = mutableStateOf<EngineState>(EngineState.Idle)
    val isGenerating = mutableStateOf(false)

    // For the load dialog
    val modelPath    = mutableStateOf(GemmaEngine.DEFAULT_MODEL_PATH)
    val drafterPath  = mutableStateOf(GemmaEngine.DEFAULT_DRAFTER_PATH)
    val useDrafter   = mutableStateOf(true)

    // ─── Load ────────────────────────────────────────────────────

    fun loadModel() {
        viewModelScope.launch {
            engineState.value = EngineState.Loading

            val mPath = modelPath.value
            val dPath = if (useDrafter.value) drafterPath.value else null

            val result = engine.loadModel(
                modelPath   = mPath,
                drafterPath = dPath
            )

            if (result.isSuccess) {
                engine.startSession()
                engineState.value = EngineState.Ready(
                    modelName    = File(mPath).nameWithoutExtension,
                    mtpActive    = engine.mtpDrafterEnabled,
                    speedupLabel = if (engine.mtpDrafterEnabled) "MTP Drafter ON • up to 3x faster" else "Standard inference"
                )
            } else {
                engineState.value = EngineState.Error(
                    result.exceptionOrNull()?.message ?: "Load failed"
                )
            }
        }
    }

    // Quick-load from model catalogue (user tapped "Use This Model")
    fun loadFromCatalogue(model: LiteRTModel, drafter: LiteRTModel? = null) {
        modelPath.value  = "/sdcard/Download/${model.id}.task"
        drafterPath.value = drafter?.let { "/sdcard/Download/${it.id}.task" }
            ?: GemmaEngine.DEFAULT_DRAFTER_PATH
        useDrafter.value = drafter != null && File(drafterPath.value).exists()
        loadModel()
    }

    // ─── Chat ────────────────────────────────────────────────────

    fun sendMessage(text: String) {
        if (text.isBlank() || isGenerating.value || !engine.isLoaded) return

        messages.add(ChatMessage(text = text, isUser = true))

        val idx = messages.size
        messages.add(ChatMessage(text = "", isUser = false, isStreaming = true))
        isGenerating.value = true

        var accumulated = ""

        engine.sendMessage(
            userMessage = text,
            onToken = { tok ->
                accumulated += tok
                messages[idx] = ChatMessage(text = accumulated, isUser = false, isStreaming = true)
            },
            onComplete = {
                messages[idx] = ChatMessage(text = accumulated, isUser = false, isStreaming = false)
                isGenerating.value = false
            },
            onError = { err ->
                messages[idx] = ChatMessage(text = "⚠️ $err", isUser = false, isStreaming = false)
                isGenerating.value = false
            }
        )
    }

    fun newChat() {
        messages.clear()
        engine.startSession()
    }

    override fun onCleared() {
        super.onCleared()
        engine.close()
    }
}
