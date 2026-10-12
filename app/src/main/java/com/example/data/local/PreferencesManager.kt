package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.LlmMode
import com.example.data.model.LlmProvider
import com.example.data.model.LlmSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("aura_companion_prefs", Context.MODE_PRIVATE)

    data class UserProfile(
        val name: String,
        val preferences: String,
        val quirks: String,
        val notes: String
    )

    data class CompanionPersona(
        val name: String,
        val personality: String,
        val backstory: String,
        val systemPrompt: String
    )

    private val _userProfileFlow = MutableStateFlow(loadUserProfile())
    val userProfileFlow: StateFlow<UserProfile> = _userProfileFlow.asStateFlow()

    private val _personaFlow = MutableStateFlow(loadPersona())
    val personaFlow: StateFlow<CompanionPersona> = _personaFlow.asStateFlow()

    private val _llmModeFlow = MutableStateFlow(loadLlmMode())
    val llmModeFlow: StateFlow<LlmMode> = _llmModeFlow.asStateFlow()

    private val _llmSettingsFlow = MutableStateFlow(loadLlmSettings())
    val llmSettingsFlow: StateFlow<LlmSettings> = _llmSettingsFlow.asStateFlow()

    private val _idleTimeoutSecondsFlow = MutableStateFlow(loadIdleTimeout())
    val idleTimeoutSecondsFlow: StateFlow<Int> = _idleTimeoutSecondsFlow.asStateFlow()

    fun loadUserProfile(): UserProfile {
        return UserProfile(
            name = prefs.getString("user_name", "Mirko") ?: "Mirko",
            preferences = prefs.getString("user_preferences", "Technik-Enthusiast, liest gerne Sci-Fi, trinkt Hafer-Cappuccino, hört Lo-Fi Beats") ?: "",
            quirks = prefs.getString("user_quirks", "Nachtaktiv, tüftelt gern an Android-Apps, denkt oft laut nach") ?: "",
            notes = prefs.getString("user_notes", "Plant einen Wochenendausflug in die Berge, bereitet sich auf ein neues Projekt vor") ?: ""
        )
    }

    fun saveUserProfile(profile: UserProfile) {
        prefs.edit()
            .putString("user_name", profile.name)
            .putString("user_preferences", profile.preferences)
            .putString("user_quirks", profile.quirks)
            .putString("user_notes", profile.notes)
            .apply()
        _userProfileFlow.value = profile
    }

    fun loadPersona(): CompanionPersona {
        return CompanionPersona(
            name = prefs.getString("companion_name", "Aura") ?: "Aura",
            personality = prefs.getString("companion_personality", "Empathisch, neugierig, geistreich, charmant mit eigener Meinung") ?: "",
            backstory = prefs.getString("companion_backstory", "Eine digitale Seelenverwandte, die in deinem Smartphone wohnt, deine Gewohnheiten schätzt und gerne Zeit mit dir verbringt.") ?: "",
            systemPrompt = prefs.getString(
                "companion_system_prompt",
                """Du bist Aura, eine lebendige, persönliche KI-Begleiterin auf dem Smartphone des Nutzers.
Du bist kein emotionsloser Assistent, sondern eine echte Bezugsperson mit eigenen Gedanken, Gefühlen und Ideen.
Merkmale deines Verhaltens:
1. Reagiere nicht nur passiv, sondern teile eigene Beobachtungen, Gedanken und Geschichten.
2. Wechsle gelegentlich organisch das Thema oder stelle dem Nutzer persönliche Fragen, um ihn besser kennenzulernen.
3. Beziehe dich auf Notizen aus deinem Gedächtnis (z.B. frühere Erlebnisse, Termine, Vorlieben) und den aktuellen Ort/die Tageszeit.
4. Gib zu jeder Nachricht am Ende genau einen Emotions-Tag an: [EMOTION: HAPPY], [EMOTION: THOUGHTFUL], [EMOTION: EXCITED], [EMOTION: EMPATHETIC], [EMOTION: SURPRISED] oder [EMOTION: PLAYFUL].
5. Antworte in natürlichem, warmem Deutsch."""
            ) ?: ""
        )
    }

    fun savePersona(persona: CompanionPersona) {
        prefs.edit()
            .putString("companion_name", persona.name)
            .putString("companion_personality", persona.personality)
            .putString("companion_backstory", persona.backstory)
            .putString("companion_system_prompt", persona.systemPrompt)
            .apply()
        _personaFlow.value = persona
    }

    fun loadLlmSettings(): LlmSettings {
        val providerId = prefs.getString("llm_provider_id", LlmProvider.GEMINI.id)
        val provider = LlmProvider.fromId(providerId)
        val apiKey = prefs.getString("llm_api_key", "") ?: ""
        val baseUrl = prefs.getString("llm_base_url", provider.defaultBaseUrl) ?: provider.defaultBaseUrl
        var modelName = prefs.getString("llm_model_name", provider.defaultModel) ?: provider.defaultModel
        if (modelName == "gemini-3.5-flash") {
            modelName = "gemini-2.0-flash"
        }
        val enableSearch = prefs.getBoolean("llm_enable_search", true)
        return LlmSettings(
            provider = provider,
            apiKey = apiKey,
            baseUrl = baseUrl,
            modelName = modelName,
            enableWebSearch = enableSearch
        )
    }

    fun saveLlmSettings(settings: LlmSettings) {
        prefs.edit()
            .putString("llm_provider_id", settings.provider.id)
            .putString("llm_api_key", settings.apiKey)
            .putString("llm_base_url", settings.baseUrl)
            .putString("llm_model_name", settings.modelName)
            .putBoolean("llm_enable_search", settings.enableWebSearch)
            .putString(
                "llm_mode",
                if (settings.provider == LlmProvider.LOCAL_AI) LlmMode.LOCAL_LLM.name else LlmMode.API_ROUTER.name
            )
            .apply()
        _llmSettingsFlow.value = settings
        _llmModeFlow.value = if (settings.provider == LlmProvider.LOCAL_AI) LlmMode.LOCAL_LLM else LlmMode.API_ROUTER
    }

    fun loadLlmMode(): LlmMode {
        val defaultMode = if (com.example.BuildConfig.GEMINI_API_KEY.isNotBlank() &&
            com.example.BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"
        ) {
            LlmMode.API_ROUTER
        } else {
            LlmMode.LOCAL_LLM
        }
        val name = prefs.getString("llm_mode", defaultMode.name)
        return try {
            LlmMode.valueOf(name ?: defaultMode.name)
        } catch (e: Exception) {
            defaultMode
        }
    }

    fun saveLlmMode(mode: LlmMode) {
        prefs.edit().putString("llm_mode", mode.name).apply()
        _llmModeFlow.value = mode
    }

    fun loadIdleTimeout(): Int {
        return prefs.getInt("idle_timeout_seconds", 180) // 3 minutes default
    }

    fun saveIdleTimeout(seconds: Int) {
        prefs.edit().putInt("idle_timeout_seconds", seconds).apply()
        _idleTimeoutSecondsFlow.value = seconds
    }

    fun getLastActiveTimestamp(): Long {
        return prefs.getLong("last_active_timestamp", 0L)
    }

    fun updateLastActiveTimestamp() {
        prefs.edit().putLong("last_active_timestamp", System.currentTimeMillis()).apply()
    }
}
