package com.example.gemmachat

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val isStreaming: Boolean = false
)

sealed class EngineState {
    object Idle : EngineState()
    object Loading : EngineState()
    data class Ready(
        val modelName: String,
        val mtpActive: Boolean,
        val speedupLabel: String
    ) : EngineState()
    data class Error(val message: String) : EngineState()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = GemmaEngine(application)

    val messages     = mutableStateListOf<ChatMessage>()
    val engineState  = mutableStateOf<EngineState>(EngineState.Idle)
    val isGenerating = mutableStateOf(false)
    val modelPath    = mutableStateOf(GemmaEngine.DEFAULT_MODEL_PATH)

    fun loadModel() {
        viewModelScope.launch {
            engineState.value = EngineState.Loading
            val result = engine.loadModel(modelPath.value)
            engineState.value = if (result.isSuccess) {
                EngineState.Ready(
                    modelName    = modelPath.value.substringAfterLast("/"),
                    mtpActive    = false,
                    speedupLabel = "On-device inference"
                )
            } else {
                EngineState.Error(result.exceptionOrNull()?.message ?: "Load failed")
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || isGenerating.value || !engine.isLoaded) return
        messages.add(ChatMessage(text = text, isUser = true))
        val idx = messages.size
        messages.add(ChatMessage(text = "", isUser = false, isStreaming = true))
        isGenerating.value = true
        var accumulated = ""
        engine.sendMessage(
            userMessage = text,
            onToken     = { tok ->
                accumulated += tok
                messages[idx] = ChatMessage(accumulated, false, true)
            },
            onComplete  = {
                messages[idx] = ChatMessage(accumulated, false, false)
                isGenerating.value = false
            },
            onError     = { err ->
                messages[idx] = ChatMessage("⚠️ $err", false, false)
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
