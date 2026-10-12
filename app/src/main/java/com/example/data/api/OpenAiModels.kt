package com.example.data.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val temperature: Float = 0.7f
)

@JsonClass(generateAdapter = false)
data class OpenAiMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = false)
data class OpenAiChatResponse(
    val choices: List<OpenAiChoice>? = null,
    val error: OpenAiErrorPayload? = null
)

@JsonClass(generateAdapter = false)
data class OpenAiChoice(
    val message: OpenAiMessage? = null
)

@JsonClass(generateAdapter = false)
data class OpenAiErrorPayload(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)
