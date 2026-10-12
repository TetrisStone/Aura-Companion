package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.LlmProvider
import com.example.data.model.LlmSettings
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object LlmClient {
    private const val TAG = "LlmClient"

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
            LlmProvider.GEMINI -> generateGemini(settings, systemPrompt, conversation, enableGoogleSearch)
            LlmProvider.OPENROUTER,
            LlmProvider.LOCAL_AI,
            LlmProvider.CUSTOM_OPENAI -> generateOpenAiCompatible(settings, systemPrompt, conversation)
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

        val modelName = settings.modelName.ifBlank { "gemini-2.0-flash" }
        val baseUrl = settings.baseUrl.trim().removeSuffix("/")
        val url = "$baseUrl/v1beta/models/$modelName:generateContent?key=$apiKey"

        val contents = conversation.map { msg ->
            val role = if (msg.role.equals("assistant", ignoreCase = true) || msg.role.equals("model", ignoreCase = true)) {
                "model"
            } else {
                "user"
            }
            GeminiContent(
                role = role,
                parts = listOf(GeminiPart(text = msg.content))
            )
        }

        val tools = if (enableGoogleSearch && settings.enableWebSearch) {
            listOf(mapOf("googleSearch" to emptyMap<String, Any>()))
        } else {
            null
        }

        val requestPayload = GeminiRequest(
            contents = contents,
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
            tools = tools
        )

        return try {
            val adapter = moshi.adapter(GeminiRequest::class.java)
            val jsonBody = adapter.toJson(requestPayload)

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.w(TAG, "Gemini API Fehler (${response.code}): $responseString")
                return Result.failure(
                    Exception("Gemini HTTP ${response.code}: ${parseErrorDetail(responseString)}")
                )
            }

            val responseAdapter = moshi.adapter(GeminiResponse::class.java)
            val parsed = responseAdapter.fromJson(responseString)
            val candidate = parsed?.candidates?.firstOrNull()
            val rawText = candidate?.content?.parts?.firstOrNull()?.text ?: ""

            if (rawText.isBlank()) {
                return Result.failure(Exception("Gemini hat eine leere Antwort geliefert."))
            }

            // Web search sources
            val grounding = candidate?.groundingMetadata
            val searchQueries = grounding?.webSearchQueries ?: emptyList()
            val sources = grounding?.groundingChunks?.mapNotNull { it.web?.title ?: it.web?.uri } ?: emptyList()
            val isWebSearch = searchQueries.isNotEmpty() || sources.isNotEmpty()
            val sourcesSummary = if (isWebSearch) {
                val qStr = if (searchQueries.isNotEmpty()) "Suchanfragen: ${searchQueries.joinToString(", ")}" else ""
                val srcStr = if (sources.isNotEmpty()) "Quellen: ${sources.take(3).joinToString("; ")}" else ""
                listOf(qStr, srcStr).filter { it.isNotBlank() }.joinToString("\n")
            } else {
                ""
            }

            val (cleanText, emotionTag) = parseEmotionTag(rawText)

            Result.success(
                GenerationResult(
                    text = cleanText,
                    emotionTag = emotionTag,
                    isWebSearchUsed = isWebSearch,
                    searchSources = sourcesSummary
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Request Exception", e)
            Result.failure(e)
        }
    }

    private fun generateOpenAiCompatible(
        settings: LlmSettings,
        systemPrompt: String,
        conversation: List<ChatMessageInput>
    ): Result<GenerationResult> {
        val model = settings.modelName.ifBlank {
            if (settings.provider == LlmProvider.LOCAL_AI) "llama3.2" else "meta-llama/llama-3.3-70b-instruct"
        }

        var baseUrl = settings.baseUrl.trim().removeSuffix("/")
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "http://$baseUrl"
        }

        val endpointUrl = if (baseUrl.endsWith("/chat/completions")) {
            baseUrl
        } else {
            "$baseUrl/chat/completions"
        }

        val messages = mutableListOf<OpenAiMessage>()
        if (systemPrompt.isNotBlank()) {
            messages.add(OpenAiMessage(role = "system", content = systemPrompt))
        }
        conversation.forEach { msg ->
            val role = if (msg.role.equals("model", ignoreCase = true)) "assistant" else msg.role
            messages.add(OpenAiMessage(role = role, content = msg.content))
        }

        val requestPayload = OpenAiChatRequest(
            model = model,
            messages = messages,
            temperature = 0.7f
        )

        return try {
            val adapter = moshi.adapter(OpenAiChatRequest::class.java)
            val jsonBody = adapter.toJson(requestPayload)

            val reqBuilder = Request.Builder()
                .url(endpointUrl)
                .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))

            if (settings.apiKey.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer ${settings.apiKey.trim()}")
            }

            if (settings.provider == LlmProvider.OPENROUTER) {
                reqBuilder.addHeader("HTTP-Referer", "https://aistudio.google.com")
                reqBuilder.addHeader("X-Title", "Aura Companion")
            }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.w(TAG, "OpenAI-kompatibler API Fehler (${response.code}): $responseString")
                val detail = parseErrorDetail(responseString)
                return Result.failure(
                    Exception("HTTP ${response.code} ($endpointUrl): $detail")
                )
            }

            val responseAdapter = moshi.adapter(OpenAiChatResponse::class.java)
            val parsed = responseAdapter.fromJson(responseString)
            val rawText = parsed?.choices?.firstOrNull()?.message?.content ?: ""

            if (rawText.isBlank()) {
                return Result.failure(
                    Exception("Modell '$model' hat keine Textantwort zurückgegeben.")
                )
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
