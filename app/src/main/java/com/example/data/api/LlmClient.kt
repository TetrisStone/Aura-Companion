package com.example.data.api

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.OnDeviceEngine
import com.example.data.model.LlmProvider
import com.example.data.model.LlmSettings
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object LlmClient {
    private const val TAG = "LlmClient"

    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    data class GenerationResult(
        val text: String,
        val emotionTag: String,
        val isWebSearchUsed: Boolean,
        val searchSources: String
    )

    data class ChatMessageInput(
        val role: String, // "user", "assistant" / "model", "system"
        val content: String
    )

    suspend fun generateResponse(
        settings: LlmSettings,
        systemPrompt: String,
        conversation: List<ChatMessageInput>,
        enableGoogleSearch: Boolean = false
    ): Result<GenerationResult> = withContext(Dispatchers.IO) {
        when (settings.provider) {
            LlmProvider.ON_DEVICE -> generateOnDevice(settings, systemPrompt, conversation)
            LlmProvider.GEMINI -> generateGemini(settings, systemPrompt, conversation, enableGoogleSearch)
            LlmProvider.OPENROUTER,
            LlmProvider.LOCAL_AI,
            LlmProvider.CUSTOM_OPENAI -> generateOpenAiCompatible(settings, systemPrompt, conversation)
        }
    }

    private suspend fun generateOnDevice(
        settings: LlmSettings,
        systemPrompt: String,
        conversation: List<ChatMessageInput>
    ): Result<GenerationResult> {
        val context = appContext ?: return Result.failure(
            IllegalStateException("App-Kontext für On-Device Engine nicht initialisiert.")
        )

        val onDeviceEngine = OnDeviceEngine.getInstance(context)

        // 1. Ensure model is loaded with current settings (acceleration, context, threads)
        val loadResult = onDeviceEngine.ensureModelLoaded(settings)
        if (loadResult.isFailure) {
            return Result.failure(loadResult.exceptionOrNull() ?: Exception("Fehler beim Laden des Modells"))
        }

        // 2. Set system prompt if needed
        val sysPromptResult = onDeviceEngine.setSystemPromptIfNeeded(systemPrompt)
        if (sysPromptResult.isFailure) {
            return Result.failure(sysPromptResult.exceptionOrNull() ?: Exception("Fehler beim Setzen des System-Prompts"))
        }

        // 3. User message
        val latestUserMessage = conversation.lastOrNull { it.role == "user" }?.content
            ?: return Result.failure(IllegalArgumentException("Keine Benutzernachricht übergeben."))

        return try {
            val tokenList = mutableListOf<String>()
            val maxTokens = if (settings.maxTokens > 0) settings.maxTokens else 1024

            // Stream tokens and collect
            onDeviceEngine.generate(latestUserMessage, maxTokens).collect { token ->
                tokenList.add(token)
            }

            val rawText = tokenList.joinToString("").trim()
            val (cleanText, emotionTag) = parseEmotionTag(rawText)

            // Diagnostic logging
            val diag = onDeviceEngine.getLastDiagnostics()
            Log.i(TAG, "ON_DEVICE Generation complete:\n$diag")

            Result.success(
                GenerationResult(
                    text = cleanText,
                    emotionTag = emotionTag,
                    isWebSearchUsed = false,
                    searchSources = ""
                )
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "On-device generation failed", e)
            Result.failure(Exception("On-Device Fehler: ${e.localizedMessage ?: e.message}", e))
        }
    }

    suspend fun testConnection(settings: LlmSettings): Result<String> = withContext(Dispatchers.IO) {
        val testPrompt = "Sag mir in einem kurzen Satz auf Deutsch, dass du online und einsatzbereit bist."
        val testMessages = listOf(ChatMessageInput("user", testPrompt))
        val result = generateResponse(
            settings = settings,
            systemPrompt = "Du bist ein KI-Modell für einen Verbindungstest. Antworte in genau einem freundlichen Satz.",
            conversation = testMessages,
            enableGoogleSearch = false
        )
        if (result.isSuccess) {
            Result.success(result.getOrThrow().text)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Unbekannter Fehler"))
        }
    }

    private fun resolveGeminiApiKey(configuredKey: String): String {
        if (configuredKey.isNotBlank()) return configuredKey.trim()
        val envKey = BuildConfig.GEMINI_API_KEY
        if (envKey.isNotBlank() && envKey != "MY_GEMINI_API_KEY") {
            return envKey.trim()
        }
        return ""
    }

    private fun generateGemini(
        settings: LlmSettings,
        systemPrompt: String,
        conversation: List<ChatMessageInput>,
        enableGoogleSearch: Boolean
    ): Result<GenerationResult> {
        val apiKey = resolveGeminiApiKey(settings.apiKey)
        if (apiKey.isBlank()) {
            return Result.failure(
                IllegalStateException("Gemini API-Key fehlt. Bitte trage deinen Key in den Einstellungen (KI-Router) ein oder konfiguriere das Secrets-Panel.")
            )
        }

        val baseUrl = settings.baseUrl.ifBlank { "https://generativelanguage.googleapis.com" }.trimEnd('/')
        val modelName = settings.modelName.ifBlank { "gemini-2.0-flash" }

        val endpointUrl = "$baseUrl/v1beta/models/$modelName:generateContent?key=$apiKey"

        val contents = conversation.map { msg ->
            val role = if (msg.role == "assistant") "model" else "user"
            GeminiContent(
                role = role,
                parts = listOf(GeminiPart(text = msg.content))
            )
        }

        val systemInstruction = GeminiContent(
            parts = listOf(GeminiPart(text = systemPrompt))
        )

        val tools = if (enableGoogleSearch && settings.enableWebSearch) {
            listOf(mapOf("google_search" to emptyMap<String, String>()))
        } else {
            null
        }

        val requestPayload = GeminiRequest(
            contents = contents,
            systemInstruction = systemInstruction,
            tools = tools
        )

        val jsonAdapter = moshi.adapter(GeminiRequest::class.java)
        val requestBodyString = jsonAdapter.toJson(requestPayload)

        val request = Request.Builder()
            .url(endpointUrl)
            .addHeader("Content-Type", "application/json")
            .post(requestBodyString.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini HTTP-Fehler: ${response.code} -> $responseBody")
                val errorMsg = parseErrorDetail(responseBody)
                return Result.failure(
                    Exception("Gemini API Fehler (${response.code}): $errorMsg")
                )
            }

            val responseAdapter = moshi.adapter(GeminiResponse::class.java)
            val parsedResponse = responseAdapter.fromJson(responseBody)

            val candidate = parsedResponse?.candidates?.firstOrNull()
            val rawText = candidate?.content?.parts?.firstOrNull { !it.text.isNullOrBlank() }?.text
                ?: return Result.failure(Exception("Keine Antwort von Gemini erhalten."))

            val hasSearch = candidate.groundingMetadata?.webSearchQueries?.isNotEmpty() == true
            val sources = candidate.groundingMetadata?.groundingChunks
                ?.mapNotNull { it.web?.title ?: it.web?.uri }
                ?.distinct()
                ?.joinToString(", ") ?: ""

            val (cleanText, emotionTag) = parseEmotionTag(rawText)

            Result.success(
                GenerationResult(
                    text = cleanText,
                    emotionTag = emotionTag,
                    isWebSearchUsed = hasSearch,
                    searchSources = sources
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Request Exception", e)
            Result.failure(Exception("Gemini Verbindungsfehler: ${e.localizedMessage}", e))
        }
    }

    private fun generateOpenAiCompatible(
        settings: LlmSettings,
        systemPrompt: String,
        conversation: List<ChatMessageInput>
    ): Result<GenerationResult> {
        val baseUrl = settings.baseUrl.ifBlank { settings.provider.defaultBaseUrl }.trimEnd('/')
        val endpointUrl = if (baseUrl.endsWith("/chat/completions")) {
            baseUrl
        } else {
            "$baseUrl/chat/completions"
        }

        val messages = mutableListOf<OpenAiMessage>()
        messages.add(OpenAiMessage(role = "system", content = systemPrompt))
        conversation.forEach { msg ->
            messages.add(OpenAiMessage(role = msg.role, content = msg.content))
        }

        val modelName = settings.modelName.ifBlank { settings.provider.defaultModel }

        val requestPayload = OpenAiChatRequest(
            model = modelName,
            messages = messages,
            temperature = 0.7f
        )

        val jsonAdapter = moshi.adapter(OpenAiChatRequest::class.java)
        val requestBodyString = jsonAdapter.toJson(requestPayload)

        val requestBuilder = Request.Builder()
            .url(endpointUrl)
            .addHeader("Content-Type", "application/json")

        if (settings.apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${settings.apiKey.trim()}")
        }

        if (settings.provider == LlmProvider.OPENROUTER) {
            requestBuilder.addHeader("HTTP-Referer", "https://github.com/aistudio/aura-companion")
            requestBuilder.addHeader("X-Title", "Aura Companion")
        }

        val request = requestBuilder
            .post(requestBodyString.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "OpenAI-kompatibler HTTP-Fehler: ${response.code} von $endpointUrl -> $responseBody")
                val errorMsg = parseErrorDetail(responseBody)
                return Result.failure(
                    Exception("Server-Fehler (${response.code}) von $modelName: $errorMsg")
                )
            }

            val responseAdapter = moshi.adapter(OpenAiChatResponse::class.java)
            val parsedResponse = responseAdapter.fromJson(responseBody)

            val rawText = parsedResponse?.choices?.firstOrNull()?.message?.content
            if (rawText.isNullOrBlank()) {
                return Result.failure(Exception("Leere Antwort von $modelName erhalten."))
            }

            val (cleanText, emotionTag) = parseEmotionTag(rawText)

            Result.success(
                GenerationResult(
                    text = cleanText,
                    emotionTag = emotionTag,
                    isWebSearchUsed = false,
                    searchSources = ""
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "OpenAI-kompatible Request Exception für $endpointUrl", e)
            val userFriendlyMessage = when {
                e is java.net.ConnectException ->
                    "Verbindung verweigert ($endpointUrl). Läuft dein lokales Modell (z.B. Ollama)? Bei lokalem Netzwerk OLLAMA_HOST=0.0.0.0 beachten!"
                e is java.net.SocketTimeoutException ->
                    "Zeitüberschreitung (Timeout). Das Modell benötigt zu lange für eine Antwort."
                e is java.net.UnknownHostException ->
                    "Host nicht gefunden: ${e.message}. Überprüfe die IP-Adresse oder URL."
                else -> e.localizedMessage ?: "Verbindungsfehler"
            }
            Result.failure(Exception(userFriendlyMessage, e))
        }
    }

    private fun parseEmotionTag(text: String): Pair<String, String> {
        val emotionRegex = Regex("\\[EMOTION:\\s*([A-Za-z_]+)\\]", RegexOption.IGNORE_CASE)
        val match = emotionRegex.find(text)
        val emotionTag = match?.groupValues?.getOrNull(1)?.uppercase() ?: "HAPPY"
        val cleanText = text.replace(emotionRegex, "").trim()
        return Pair(cleanText, emotionTag)
    }

    private fun parseErrorDetail(jsonString: String): String {
        return try {
            if (jsonString.contains("\"message\":")) {
                val match = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(jsonString)
                match?.groupValues?.getOrNull(1) ?: jsonString.take(150)
            } else {
                jsonString.take(150)
            }
        } catch (e: Exception) {
            jsonString.take(150)
        }
    }
}
