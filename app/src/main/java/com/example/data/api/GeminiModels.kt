package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val tools: List<Map<String, Any>>? = null
)

@JsonClass(generateAdapter = false)
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = false)
data class GeminiPart(
    val text: String? = null
)

@JsonClass(generateAdapter = false)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null
)

@JsonClass(generateAdapter = false)
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val groundingMetadata: GroundingMetadata? = null
)

@JsonClass(generateAdapter = false)
data class GroundingMetadata(
    val webSearchQueries: List<String>? = null,
    val groundingChunks: List<GroundingChunk>? = null
)

@JsonClass(generateAdapter = false)
data class GroundingChunk(
    val web: WebChunk? = null
)

@JsonClass(generateAdapter = false)
data class WebChunk(
    val uri: String? = null,
    val title: String? = null
)
