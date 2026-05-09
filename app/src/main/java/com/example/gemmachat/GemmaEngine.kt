package com.example.gemmachat

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GemmaEngine — MediaPipe LiteRT-LM wrapper with MTP Drafter support.
 *
 * ══════════════════════════════════════════════════════════
 * MTP DRAFTER (Multi-Token Prediction / Speculative Decoding)
 * ══════════════════════════════════════════════════════════
 * How it works:
 *   1. The lightweight drafter model (4 layers) predicts N future tokens fast.
 *   2. The main Gemma 4 E4B model verifies all N tokens in ONE forward pass.
 *   3. If it agrees → output the whole sequence at once.
 *   4. Result: up to 3x tokens/second with ZERO quality degradation.
 *
 * Drafter architecture advantages:
 *   • Shared KV cache with the target model (no redundant context recalculation)
 *   • Shared input embedding table
 *   • Embedder clustering for edge E2B/E4B models (addresses logit bottleneck)
 *   • Each rejected token still produces a correct token — nothing is wasted
 *
 * To use MTP Drafter: provide both modelPath AND drafterModelPath.
 * ══════════════════════════════════════════════════════════
 */
class GemmaEngine(private val context: Context) {

    companion object {
        private const val TAG = "GemmaEngine"

        const val DEFAULT_MODEL_PATH   = "/sdcard/Download/gemma4.task"
        const val DEFAULT_DRAFTER_PATH = "/sdcard/Download/gemma4-drafter.task"

        const val MAX_TOKENS    = 2048
        const val TOP_K         = 40
        const val TEMPERATURE   = 0.8f
        const val NUM_DRAFT_TOKENS = 5  // how many tokens drafter proposes each step
    }

    private var llmInference: LlmInference? = null
    private var session: LlmInferenceSession? = null

    var isLoaded = false
        private set

    var mtpDrafterEnabled = false
        private set

    var loadedModelPath: String? = null
        private set

    var loadedDrafterPath: String? = null
        private set

    /**
     * Load the main model.
     * Optionally supply drafterPath to enable MTP speculative decoding.
     *
     * @param modelPath     Path to gemma4-e4b.task on device storage
     * @param drafterPath   Path to gemma4-e4b-drafter.task (null = no MTP)
     */
    suspend fun loadModel(
        modelPath: String = DEFAULT_MODEL_PATH,
        drafterPath: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val builder = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(MAX_TOKENS)
                .setTopK(TOP_K)
                .setTemperature(TEMPERATURE)
                .setRandomSeed(101)

            // ✅ MTP Drafter: configure speculative decoding if drafter path provided
            if (drafterPath != null && java.io.File(drafterPath).exists()) {
                builder
                    .setDraftModelPath(drafterPath)          // lightweight 4-layer drafter
                    .setNumDraftTokens(NUM_DRAFT_TOKENS)     // tokens drafter proposes per step
                Log.d(TAG, "MTP Drafter enabled: $drafterPath (drafts $NUM_DRAFT_TOKENS tokens)")
                mtpDrafterEnabled = true
                loadedDrafterPath = drafterPath
            } else {
                mtpDrafterEnabled = false
                if (drafterPath != null) {
                    Log.w(TAG, "Drafter file not found at $drafterPath — running without MTP")
                }
            }

            llmInference = LlmInference.createFromOptions(context, builder.build())
            isLoaded = true
            loadedModelPath = modelPath

            Log.d(TAG, "Model loaded: $modelPath | MTP Drafter: $mtpDrafterEnabled")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Load failed: ${e.message}", e)
            isLoaded = false
            Result.failure(e)
        }
    }

    /**
     * Start a fresh inference session.
     * Each new session creates a clean KV cache.
     * Call on "New Chat" or after loading a model.
     */
    fun startSession() {
        session?.close()
        session = null

        val inference = llmInference ?: return

        val sessionOpts = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(TOP_K)
            .setTemperature(TEMPERATURE)
            .build()

        session = LlmInferenceSession.createFromLlmInference(inference, sessionOpts)
        Log.d(TAG, "New session started (MTP Drafter: $mtpDrafterEnabled)")
    }

    /**
     * Send a message and stream the response.
     *
     * With MTP Drafter ON:
     *   → Drafter proposes NUM_DRAFT_TOKENS tokens per step
     *   → Main model verifies all in one forward pass
     *   → Up to 3x faster token generation
     *
     * With MTP Drafter OFF:
     *   → Standard autoregressive decoding (one token per forward pass)
     */
    fun sendMessage(
        userMessage: String,
        onToken: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        val s = session ?: run {
            startSession()
            session!!
        }

        try {
            s.addQueryChunk(userMessage)
            s.generateResponseAsync { partial, done ->
                partial?.let { onToken(it) }
                if (done) onComplete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Generation error: ${e.message}", e)
            onError(e.message ?: "Generation failed")
        }
    }

    fun close() {
        session?.close()
        llmInference?.close()
        isLoaded = false
        Log.d(TAG, "Engine closed")
    }
}
