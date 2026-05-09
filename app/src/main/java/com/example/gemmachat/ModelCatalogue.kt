package com.example.gemmachat

/**
 * Catalogue of LiteRT-compatible on-device LLM models.
 * Sources: ai.google.dev/edge/mediapipe/solutions/genai/llm_inference
 *          huggingface.co/litert-community
 *          kaggle.com/models/google/gemma
 */

data class LiteRTModel(
    val id: String,
    val name: String,
    val variant: String,          // e.g. "INT4 GPU", "INT8 CPU"
    val sizeMB: Int,
    val format: String,           // ".task" or ".litertlm"
    val description: String,
    val features: List<String>,
    val kaggleUrl: String,
    val hfUrl: String,
    val isMTPDrafter: Boolean = false,
    val targetModelId: String? = null,  // if this is a drafter, which model it accelerates
    val speedupFactor: String? = null,
    val category: ModelCategory
)

enum class ModelCategory {
    GEMMA4, GEMMA3, GEMMA3N, COMMUNITY, DRAFTER
}

object ModelCatalogue {

    val all: List<LiteRTModel> = listOf(

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Gemma 4 Family
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        LiteRTModel(
            id = "gemma4-e4b",
            name = "Gemma 4 E4B",
            variant = "INT4 GPU",
            sizeMB = 3500,
            format = ".task",
            description = "Gemma 4's edge 4B model. Multimodal (text + image). Optimized for on-device inference with GPU acceleration via LiteRT-LM.",
            features = listOf("Multimodal", "GPU INT4", "MTP Drafter support", "LiteRT-LM"),
            kaggleUrl = "https://kaggle.com/models/google/gemma/frameworks/litert/variations/gemma-4-e4b-it-gpu-int4",
            hfUrl = "https://huggingface.co/google/gemma-4-E4B-it-litert",
            category = ModelCategory.GEMMA4
        ),
        LiteRTModel(
            id = "gemma4-e2b",
            name = "Gemma 4 E2B",
            variant = "INT4 GPU",
            sizeMB = 1800,
            format = ".task",
            description = "Smaller edge variant of Gemma 4. Supports audio input. Fastest Gemma 4 for resource-constrained devices.",
            features = listOf("Multimodal", "Audio input", "GPU INT4", "MTP Drafter support"),
            kaggleUrl = "https://kaggle.com/models/google/gemma/frameworks/litert/variations/gemma-4-e2b-it-gpu-int4",
            hfUrl = "https://huggingface.co/google/gemma-4-E2B-it-litert",
            category = ModelCategory.GEMMA4
        ),

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // MTP Drafters (Speculative Decoding)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        LiteRTModel(
            id = "gemma4-e4b-drafter",
            name = "Gemma 4 E4B MTP Drafter",
            variant = "4-layer draft model",
            sizeMB = 180,
            format = ".task",
            description = "Lightweight 4-layer speculative decoding drafter for Gemma 4 E4B. Predicts multiple tokens in parallel — the main model verifies them all in one forward pass. Shared KV cache and embeddings with target model.",
            features = listOf("Speculative decoding", "Shared KV cache", "Up to 3x speedup", "Zero quality loss"),
            kaggleUrl = "https://kaggle.com/models/google/gemma",
            hfUrl = "https://huggingface.co/google/gemma-4-E4B-it-assistant",
            isMTPDrafter = true,
            targetModelId = "gemma4-e4b",
            speedupFactor = "up to 3x",
            category = ModelCategory.DRAFTER
        ),
        LiteRTModel(
            id = "gemma4-e2b-drafter",
            name = "Gemma 4 E2B MTP Drafter",
            variant = "4-layer draft model",
            sizeMB = 100,
            format = ".task",
            description = "MTP drafter for Gemma 4 E2B. Uses an efficient clustering technique in the embedder to address final logit calculation bottlenecks — enabling faster generation on memory-constrained devices.",
            features = listOf("Speculative decoding", "Embedder clustering", "Memory-efficient", "Zero quality loss"),
            kaggleUrl = "https://kaggle.com/models/google/gemma",
            hfUrl = "https://huggingface.co/google/gemma-4-E2B-it-assistant",
            isMTPDrafter = true,
            targetModelId = "gemma4-e2b",
            speedupFactor = "up to 3x",
            category = ModelCategory.DRAFTER
        ),

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Gemma 3n Family
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        LiteRTModel(
            id = "gemma3n-e4b",
            name = "Gemma 3n E4B",
            variant = "INT4",
            sizeMB = 3000,
            format = ".litertlm",
            description = "Latest Gemma 3n edge model with MatMul-free architecture. Runs efficiently on mobile CPUs and GPUs.",
            features = listOf("MatMul-free", "CPU + GPU", "LiteRT-LM format"),
            kaggleUrl = "https://kaggle.com/models/google/gemma",
            hfUrl = "https://huggingface.co/google/gemma-3n-E4B-it-litert-preview",
            category = ModelCategory.GEMMA3N
        ),
        LiteRTModel(
            id = "gemma3n-e2b",
            name = "Gemma 3n E2B",
            variant = "INT4",
            sizeMB = 1500,
            format = ".litertlm",
            description = "Lightweight Gemma 3n. Smallest Gemma that still handles complex reasoning. Good for low-RAM devices (4 GB+).",
            features = listOf("MatMul-free", "4 GB RAM devices", "LiteRT-LM format"),
            kaggleUrl = "https://kaggle.com/models/google/gemma",
            hfUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-preview",
            category = ModelCategory.GEMMA3N
        ),

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Gemma 3 Family
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        LiteRTModel(
            id = "gemma3-1b",
            name = "Gemma 3 1B",
            variant = "INT4",
            sizeMB = 700,
            format = ".task",
            description = "Lightest Gemma model. Runs on almost any Android device with 3 GB+ RAM. Great for testing and basic chat.",
            features = listOf("3 GB RAM min", "Fast CPU inference", "700 MB download"),
            kaggleUrl = "https://kaggle.com/models/google/gemma/frameworks/litert/variations/gemma-3-1b-it-int4",
            hfUrl = "https://huggingface.co/google/gemma-3-1b-it-litert",
            category = ModelCategory.GEMMA3
        ),

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // LiteRT Community Models
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        LiteRTModel(
            id = "deepseek-r1-1.5b",
            name = "DeepSeek R1 Distill",
            variant = "1.5B INT8",
            sizeMB = 1600,
            format = ".task",
            description = "DeepSeek-R1-Distill-Qwen-1.5B adapted for LiteRT by the community. Strong reasoning with chain-of-thought output.",
            features = listOf("Chain-of-thought", "Strong reasoning", "LiteRT community"),
            kaggleUrl = "https://kaggle.com/models/litert-community/deepseek-r1-distill-qwen",
            hfUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
            category = ModelCategory.COMMUNITY
        ),
        LiteRTModel(
            id = "phi-2",
            name = "Phi-2",
            variant = "INT4",
            sizeMB = 1400,
            format = ".task",
            description = "Microsoft Phi-2 adapted for MediaPipe. Good for coding and instruction-following tasks on-device.",
            features = listOf("Code generation", "Instruction following", "Microsoft"),
            kaggleUrl = "https://kaggle.com/models/microsoft/phi",
            hfUrl = "https://huggingface.co/microsoft/phi-2",
            category = ModelCategory.COMMUNITY
        ),
        LiteRTModel(
            id = "qwen-1.5b",
            name = "Qwen 1.5B",
            variant = "INT4",
            sizeMB = 900,
            format = ".task",
            description = "Alibaba Qwen 1.5B adapted for LiteRT. Multilingual — strong in Chinese and English. Good for international apps.",
            features = listOf("Multilingual", "Chinese + English", "Alibaba"),
            kaggleUrl = "https://kaggle.com/models/litert-community/qwen",
            hfUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct",
            category = ModelCategory.COMMUNITY
        )
    )

    fun byCategory(cat: ModelCategory) = all.filter { it.category == cat }
    fun findById(id: String) = all.find { it.id == id }
}
