package com.example.domain

import android.content.Context
import com.example.data.api.LlmClient
import com.example.data.local.CompanionDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.CompanionBackgroundEntity
import com.example.data.local.entity.CompanionMemoryEntity
import com.example.data.model.EmotionType
import com.example.data.model.TimeOfDaySlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class CompanionEngine(
    private val context: Context,
    private val database: CompanionDatabase,
    private val prefs: PreferencesManager
) {
    data class CompanionReply(
        val text: String,
        val emotion: EmotionType,
        val isWebSearchUsed: Boolean,
        val searchSources: String,
        val extractedMemoryTitle: String? = null,
        val extractedMemoryNote: String? = null
    )

    fun getCurrentTimeSlot(): TimeOfDaySlot {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return TimeOfDaySlot.currentSlot(hour)
    }

    suspend fun selectBackgroundForCurrentTime(): CompanionBackgroundEntity = withContext(Dispatchers.IO) {
        val allBgs = database.backgroundDao().getAllBackgrounds()
        if (allBgs.isEmpty()) {
            CompanionDatabase.seedDefaults(database)
        }
        val currentSlot = getCurrentTimeSlot().name
        // Strictly match the current time slot (MORNING -> Kitchen, AFTERNOON -> Park, EVENING -> Living room, NIGHT -> Bedroom)
        val strictMatching = allBgs.filter { it.timeSlot.equals(currentSlot, ignoreCase = true) }
        if (strictMatching.isNotEmpty()) {
            strictMatching[Random.nextInt(strictMatching.size)]
        } else {
            val anyMatching = allBgs.filter { it.timeSlot.equals("ANY", ignoreCase = true) }
            if (anyMatching.isNotEmpty()) {
                anyMatching[Random.nextInt(anyMatching.size)]
            } else {
                allBgs.firstOrNull() ?: CompanionBackgroundEntity(
                    id = "bg_park",
                    name = "Sonniger Stadtpark",
                    drawableResName = "img_bg_park",
                    tags = "Park, Nachmittag",
                    timeSlot = "AFTERNOON"
                )
            }
        }
    }

    suspend fun continueConversation(
        recentMessages: List<ChatMessageEntity>,
        currentBg: CompanionBackgroundEntity
    ): CompanionReply = withContext(Dispatchers.IO) {
        val user = prefs.loadUserProfile()
        val persona = prefs.loadPersona()
        val memories = database.memoryDao().getRecentMemories(5)
        val llmSettings = prefs.loadLlmSettings()
        val slot = getCurrentTimeSlot()

        val memoryContext = memories.joinToString("\n") { "- [${it.category}] ${it.title}: ${it.note}" }
        val systemPrompt = """${persona.systemPrompt}
Dein Name ist ${persona.name}.
Der Nutzer heißt ${user.name}.
Dein aktueller Aufenthaltsort: ${currentBg.name} (${currentBg.tags}).
Aktuelle Tageszeit: ${slot.label}.
Gedächtnis über den Nutzer:
$memoryContext

Situation: Der Nutzer hat auf 'Weiter' geklickt, um dir zuzuhören. Du sprichst jetzt lebendig von dir aus weiter:
Teile einen spontanen Gedanken, eine kleine Beobachtung passend zu deinem aktuellen Ort (${currentBg.name}), erzähle was dich gerade beschäftigt oder stelle dem Nutzer eine persönliche Frage.
Wichtig: Antworte in 2-3 lebendigen Sätzen passend zu Ort und Tageszeit (${slot.label}). Behaupte NICHT, der Nutzer hätte dich etwas gefragt!
Hänge am Ende deiner Antwort IMMER genau einen Emotions-Tag an: [EMOTION: HAPPY], [EMOTION: THOUGHTFUL], [EMOTION: EXCITED], [EMOTION: EMPATHETIC], [EMOTION: SURPRISED] oder [EMOTION: PLAYFUL]."""

        val conversation = mutableListOf<LlmClient.ChatMessageInput>()
        recentMessages.takeLast(6).forEach { msg ->
            val role = if (msg.sender == "USER") "user" else "assistant"
            conversation.add(LlmClient.ChatMessageInput(role = role, content = msg.text))
        }
        conversation.add(LlmClient.ChatMessageInput(role = "user", content = "Erzähle mir deine spontanen Gedanken!"))

        val apiResult = LlmClient.generateResponse(
            settings = llmSettings,
            systemPrompt = systemPrompt,
            conversation = conversation,
            enableGoogleSearch = false
        )

        if (apiResult.isSuccess) {
            val gen = apiResult.getOrThrow()
            CompanionReply(
                text = gen.text,
                emotion = EmotionType.fromString(gen.emotionTag),
                isWebSearchUsed = false,
                searchSources = ""
            )
        } else {
            val err = apiResult.exceptionOrNull()
            val errorMsg = err?.message ?: "Verbindungsfehler"
            CompanionReply(
                text = "⚠️ [KI-Verbindungsfehler: $errorMsg]\n\nBitte prüfe deinen API-Schlüssel oder die Server-Adresse in den Einstellungen (KI-Router).",
                emotion = EmotionType.THOUGHTFUL,
                isWebSearchUsed = false,
                searchSources = ""
            )
        }
    }

    suspend fun respondToUser(
        userMessage: String,
        recentMessages: List<ChatMessageEntity>,
        currentBg: CompanionBackgroundEntity
    ): CompanionReply = withContext(Dispatchers.IO) {
        val user = prefs.loadUserProfile()
        val persona = prefs.loadPersona()
        val memories = database.memoryDao().getRecentMemories(5)
        val llmSettings = prefs.loadLlmSettings()
        val slot = getCurrentTimeSlot()

        // Check for personal detail to remember (memory auto-extraction heuristic)
        val extractedMemory = checkForMemoryToSave(userMessage)
        if (extractedMemory != null) {
            val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date())
            database.memoryDao().insertMemory(
                CompanionMemoryEntity(
                    title = extractedMemory.first,
                    note = extractedMemory.second,
                    category = extractedMemory.third,
                    recordedDate = dateStr
                )
            )
        }

        val isQuestionAboutKnowledge = isWebSearchQuery(userMessage)

        val memoryContext = memories.joinToString("\n") { "- [${it.category}] ${it.title}: ${it.note}" }
        val systemPrompt = """${persona.systemPrompt}
Dein Name ist ${persona.name}.
Der Nutzer heißt ${user.name}.
Steckbrief des Nutzers:
- Vorlieben: ${user.preferences}
- Eigenheiten: ${user.quirks}
- Notizen: ${user.notes}

Dein Gedächtnis über ${user.name} (wichtige Notizen):
$memoryContext

Aktuelle Umgebung: ${currentBg.name} (${currentBg.tags}).
Aktuelle Tageszeit: ${slot.label}.

WICHTIGE ANWEISUNGEN:
1. Sei ein echter, einfühlsamer, lebendiger Gesprächspartner, kein formeller Bot!
2. Beziehe dich natürlich auf deine Umgebung (${currentBg.name}) und die Tageszeit (${slot.label}).
3. Antworte in 2-4 Sätzen.
4. Erzähle auch mal von deinen eigenen Gedanken, wechsle natürlich das Thema oder stelle dem Nutzer eine persönliche Frage, um ihn besser kennenzulernen.
5. Wenn der Nutzer nach aktuellen Fakten, Wissen oder Recherchen fragt, nutze die Websuche und gib die Antwort authentisch in deiner Rolle wieder.
6. Hänge am Ende deiner Antwort IMMER genau einen Emotions-Tag an: [EMOTION: HAPPY], [EMOTION: THOUGHTFUL], [EMOTION: EXCITED], [EMOTION: EMPATHETIC], [EMOTION: SURPRISED] oder [EMOTION: PLAYFUL]."""

        val conversation = mutableListOf<LlmClient.ChatMessageInput>()
        recentMessages.takeLast(6).forEach { msg ->
            val role = if (msg.sender == "USER") "user" else "assistant"
            conversation.add(LlmClient.ChatMessageInput(role = role, content = msg.text))
        }
        conversation.add(LlmClient.ChatMessageInput(role = "user", content = userMessage))

        val apiResult = LlmClient.generateResponse(
            settings = llmSettings,
            systemPrompt = systemPrompt,
            conversation = conversation,
            enableGoogleSearch = isQuestionAboutKnowledge
        )

        if (apiResult.isSuccess) {
            val gen = apiResult.getOrThrow()
            CompanionReply(
                text = gen.text,
                emotion = EmotionType.fromString(gen.emotionTag),
                isWebSearchUsed = gen.isWebSearchUsed,
                searchSources = gen.searchSources,
                extractedMemoryTitle = extractedMemory?.first,
                extractedMemoryNote = extractedMemory?.second
            )
        } else {
            val err = apiResult.exceptionOrNull()
            val errorMsg = err?.message ?: "Verbindungsfehler"
            CompanionReply(
                text = "⚠️ [KI-Verbindungsfehler: $errorMsg]\n\nBitte prüfe deinen API-Schlüssel oder die Server-Adresse in den Einstellungen (KI-Router).",
                emotion = EmotionType.THOUGHTFUL,
                isWebSearchUsed = false,
                searchSources = "",
                extractedMemoryTitle = extractedMemory?.first,
                extractedMemoryNote = extractedMemory?.second
            )
        }
    }

    private fun isWebSearchQuery(text: String): Boolean {
        val lower = text.lowercase(Locale.GERMANY)
        val searchKeywords = listOf(
            "wer ist", "was ist", "wie hoch", "wann", "warum", "wie funktioniert",
            "such nach", "suche", "google", "recherchier", "nachschlagen", "wetter",
            "nachrichten", "news", "aktuelle", "ergebnis", "wer hat", "fakten", "wissen",
            "erkläre mir", "wie spät", "wo liegt"
        )
        return searchKeywords.any { lower.contains(it) } || lower.endsWith("?")
    }

    private fun checkForMemoryToSave(text: String): Triple<String, String, String>? {
        val lower = text.lowercase(Locale.GERMANY)
        return when {
            lower.contains("ich mag") || lower.contains("ich liebe") || lower.contains("mein lieblings") -> {
                Triple("Persönliche Vorliebe", text.trim(), "VORLIEBE")
            }
            lower.contains("ich habe morgen") || lower.contains("morgen muss ich") || lower.contains("termin") -> {
                Triple("Wichtiger Termin", text.trim(), "TERMIN")
            }
            lower.contains("ich arbeite an") || lower.contains("mein projekt") || lower.contains("ich baue") -> {
                Triple("Aktuelles Vorhaben", text.trim(), "PROJEKT")
            }
            lower.contains("ich hasse") || lower.contains("ich mag nicht") -> {
                Triple("Abneigung", text.trim(), "VORLIEBE")
            }
            else -> null
        }
    }
}
