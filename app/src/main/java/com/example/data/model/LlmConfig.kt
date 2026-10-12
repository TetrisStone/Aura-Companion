package com.example.data.model

enum class LlmProvider(
    val id: String,
    val title: String,
    val subtitle: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val isKeyRequired: Boolean
) {
    GEMINI(
        id = "GEMINI",
        title = "Google Gemini",
        subtitle = "Google API (gemini-2.0-flash, gemini-2.5-flash)",
        defaultBaseUrl = "https://generativelanguage.googleapis.com",
        defaultModel = "gemini-2.0-flash",
        isKeyRequired = true
    ),
    OPENROUTER(
        id = "OPENROUTER",
        title = "OpenRouter",
        subtitle = "Multi-Modell API (Llama 3.3, DeepSeek, Claude, GPT-4o)",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "meta-llama/llama-3.3-70b-instruct",
        isKeyRequired = true
    ),
    LOCAL_AI(
        id = "LOCAL_AI",
        title = "Lokale KI (Ollama / LM Studio)",
        subtitle = "Auf dem PC/Server im WLAN ohne Cloud",
        defaultBaseUrl = "http://10.0.2.2:11434/v1",
        defaultModel = "llama3.2",
        isKeyRequired = false
    ),
    CUSTOM_OPENAI(
        id = "CUSTOM_OPENAI",
        title = "Eigener OpenAI-Endpunkt",
        subtitle = "Kompatible API (Groq, Together AI, vLLM)",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        isKeyRequired = true
    );

    companion object {
        fun fromId(id: String?): LlmProvider =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GEMINI
    }
}

data class LlmSettings(
    val provider: LlmProvider = LlmProvider.GEMINI,
    val apiKey: String = "",
    val baseUrl: String = LlmProvider.GEMINI.defaultBaseUrl,
    val modelName: String = LlmProvider.GEMINI.defaultModel,
    val enableWebSearch: Boolean = true
)
