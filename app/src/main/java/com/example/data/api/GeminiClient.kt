package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client: OkHttpClient = OkHttpClient.Builder()
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

    suspend fun generateResponse(
        systemPrompt: String,
        chatHistory: List<GeminiContent>,
        enableGoogleSearch: Boolean = true
    ): Result<GenerationResult> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Gemini API Key ist nicht konfiguriert. Bitte im AI Studio Secrets-Panel eintragen oder das lokale Modell nutzen.")
            )
        }

        try {
            val url = "$BASE_URL?key=$apiKey"
            val tools = if (enableGoogleSearch) {
                listOf(mapOf("googleSearch" to emptyMap<String, Any>()))
            } else {
                null
            }

            val requestPayload = GeminiRequest(
                contents = chatHistory,
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = systemPrompt))
                ),
                tools = tools
            )

            val adapter = moshi.adapter(GeminiRequest::class.java)
            val jsonBody = adapter.toJson(requestPayload)

            val httpRequest = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val httpResponse = client.newCall(httpRequest).execute()
            val responseString = httpResponse.body?.string() ?: ""

            if (!httpResponse.isSuccessful) {
                Log.w(TAG, "API Response: ${httpResponse.code} - $responseString")
                return@withContext Result.failure(
                    Exception("API-Fehler (${httpResponse.code}): $responseString")
                )
            }

            val responseAdapter = moshi.adapter(GeminiResponse::class.java)
            val parsedResponse = responseAdapter.fromJson(responseString)

            val candidate = parsedResponse?.candidates?.firstOrNull()
            val rawText = candidate?.content?.parts?.firstOrNull()?.text ?: "Keine Antwort erhalten."

            // Check if web search grounding was used
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

            // Extract emotion tag like [EMOTION: HAPPY]
            val emotionRegex = Regex("\\[EMOTION:\\s*([A-Za-z_]+)\\]", RegexOption.IGNORE_CASE)
            val match = emotionRegex.find(rawText)
            val emotionTag = match?.groupValues?.getOrNull(1)?.uppercase() ?: "HAPPY"
            val cleanText = rawText.replace(emotionRegex, "").trim()

            Result.success(
                GenerationResult(
                    text = cleanText,
                    emotionTag = emotionTag,
                    isWebSearchUsed = isWebSearch,
                    searchSources = sourcesSummary
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception during generation", e)
            Result.failure(e)
        }
    }
}
