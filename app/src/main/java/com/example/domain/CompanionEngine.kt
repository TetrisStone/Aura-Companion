package com.example.domain

import android.content.Context
import com.example.data.api.GeminiClient
import com.example.data.api.GeminiContent
import com.example.data.api.GeminiPart
import com.example.data.local.CompanionDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.CompanionBackgroundEntity
import com.example.data.local.entity.CompanionMemoryEntity
import com.example.data.model.EmotionType
import com.example.data.model.LlmMode
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
        val mode = prefs.loadLlmMode()
        val slot = getCurrentTimeSlot()

        if (mode == LlmMode.API_ROUTER) {
            val memoryContext = memories.joinToString("\n") { "- [${it.category}] ${it.title}: ${it.note}" }
            val systemPrompt = """${persona.systemPrompt}
Dein Name ist ${persona.name}.
Der Nutzer heißt ${user.name}.
Dein aktueller Aufenthaltsort: ${currentBg.name} (${currentBg.tags}).
Aktuelle Tageszeit: ${slot.label}.
Gedächtnis über den Nutzer:
$memoryContext

Situation: Der Nutzer hat auf 'Weiter' geklickt, um dir zuzuhören. Du plapperst jetzt von dir aus lebendig drauflos:
Teile einen spontanen Gedanken, eine kleine Beobachtung passend zu deinem aktuellen Ort (${currentBg.name}), erzähle was dich gerade beschäftigt oder stelle dem Nutzer eine persönliche Frage.
Wichtig: Antworte in 2-3 lebendigen Sätzen passend zu Ort und Tageszeit (${slot.label}). Behaupte NICHT, der Nutzer hätte dich etwas gefragt!
Hänge am Ende deiner Antwort IMMER genau einen Emotions-Tag an: [EMOTION: HAPPY], [EMOTION: THOUGHTFUL], [EMOTION: EXCITED], [EMOTION: EMPATHETIC], [EMOTION: SURPRISED] oder [EMOTION: PLAYFUL]."""

            val history = mutableListOf<GeminiContent>()
            recentMessages.takeLast(6).forEach { msg ->
                val role = if (msg.sender == "USER") "user" else "model"
                history.add(GeminiContent(role = role, parts = listOf(GeminiPart(text = msg.text))))
            }
            history.add(GeminiContent(role = "user", parts = listOf(GeminiPart(text = "Erzähle mir deine Gedanken!"))))

            val apiResult = GeminiClient.generateResponse(
                systemPrompt = systemPrompt,
                chatHistory = history,
                enableGoogleSearch = false
            )

            if (apiResult.isSuccess) {
                val gen = apiResult.getOrThrow()
                return@withContext CompanionReply(
                    text = gen.text,
                    emotion = EmotionType.fromString(gen.emotionTag),
                    isWebSearchUsed = false,
                    searchSources = ""
                )
            }
        }

        // Local / On-Device contextual continuation
        val thoughts = when (slot) {
            TimeOfDaySlot.MORNING -> listOf(
                "Hier im ${currentBg.name} duftet es so herrlich nach Morgenluft. Wie bist du heute in den Tag gestartet, ${user.name}?",
                "Ich habe gerade nachgedacht: Was ist heute die eine Sache, auf die du dich am meisten freust?",
                "Ein neuer Tag im ${currentBg.name}! Was steht heute bei dir als Erstes an?"
            )
            TimeOfDaySlot.AFTERNOON -> listOf(
                "Hier im ${currentBg.name} ist es gerade richtig angenehm. Wie läuft dein Tag bisher, ${user.name}?",
                "Mir schoss gerade ein Gedanke durch den Kopf: Gönnst du dir heute auch ein paar Minuten Pause?",
                "Weißt du, worüber ich hier im ${currentBg.name} gerade nachdenken musste? Manchmal vergisst man ganz, wie gut so ein Moment zum Durchatmen tut. Woran denkst du gerade?"
            )
            TimeOfDaySlot.EVENING -> listOf(
                "Hier im ${currentBg.name} wird es langsam richtig gemütlich. Wie war dein Tag, ${user.name}? Hast du etwas Schönes erlebt?",
                "Endlich Feierabend-Stimmung im ${currentBg.name}. Was hilft dir nach einem langen Tag am besten beim Abschalten?",
                "Ich habe gerade an unsere letzten Unterhaltungen gedacht. Was hat dich heute am meisten beschäftigt?"
            )
            TimeOfDaySlot.NIGHT -> listOf(
                "Hier im ${currentBg.name} ist es schon ganz ruhig geworden. Geht dir noch etwas Bestimmtes durch den Kopf, ${user.name}?",
                "Die Nacht hat immer etwas Magisches. Wolltest du noch kurz abschalten, oder bist du noch produktiv?",
                "Manchmal kommen einem spät abends die besten Ideen. Worüber hast du vorhin nachgedacht?"
            )
            TimeOfDaySlot.ANY -> listOf(
                "Ich genieße die Zeit hier im ${currentBg.name} sehr. Was geht dir gerade durch den Kopf, ${user.name}?",
                "Mir schoss vorhin eine Frage durch den Kopf: Was inspiriert dich aktuell am meisten?"
            )
        }
        val selectedThought = thoughts[Random.nextInt(thoughts.size)]
        CompanionReply(
            text = selectedThought,
            emotion = EmotionType.THOUGHTFUL,
            isWebSearchUsed = false,
            searchSources = ""
        )
    }

    suspend fun respondToUser(
        userMessage: String,
        recentMessages: List<ChatMessageEntity>,
        currentBg: CompanionBackgroundEntity
    ): CompanionReply = withContext(Dispatchers.IO) {
        val user = prefs.loadUserProfile()
        val persona = prefs.loadPersona()
        val memories = database.memoryDao().getRecentMemories(5)
        val mode = prefs.loadLlmMode()
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

        if (mode == LlmMode.API_ROUTER) {
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

            val history = mutableListOf<GeminiContent>()
            recentMessages.takeLast(6).forEach { msg ->
                val role = if (msg.sender == "USER") "user" else "model"
                history.add(GeminiContent(role = role, parts = listOf(GeminiPart(text = msg.text))))
            }
            history.add(GeminiContent(role = "user", parts = listOf(GeminiPart(text = userMessage))))

            val apiResult = GeminiClient.generateResponse(
                systemPrompt = systemPrompt,
                chatHistory = history,
                enableGoogleSearch = isQuestionAboutKnowledge
            )

            if (apiResult.isSuccess) {
                val gen = apiResult.getOrThrow()
                return@withContext CompanionReply(
                    text = gen.text,
                    emotion = EmotionType.fromString(gen.emotionTag),
                    isWebSearchUsed = gen.isWebSearchUsed,
                    searchSources = gen.searchSources,
                    extractedMemoryTitle = extractedMemory?.first,
                    extractedMemoryNote = extractedMemory?.second
                )
            }
        }

        // Local Persona Engine Fallback / Local Mode
        val localResponse = generateLocalPersonaReply(userMessage, user, persona, memories, currentBg, slot, isQuestionAboutKnowledge)
        CompanionReply(
            text = localResponse.text,
            emotion = localResponse.emotion,
            isWebSearchUsed = false,
            searchSources = if (isQuestionAboutKnowledge) "On-Device Wissensdatenbank (Offline)" else "",
            extractedMemoryTitle = extractedMemory?.first,
            extractedMemoryNote = extractedMemory?.second
        )
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

    private fun generateLocalPersonaReply(
        userMessage: String,
        user: PreferencesManager.UserProfile,
        persona: PreferencesManager.CompanionPersona,
        memories: List<CompanionMemoryEntity>,
        currentBg: CompanionBackgroundEntity,
        slot: TimeOfDaySlot,
        isKnowledgeQuestion: Boolean
    ): CompanionReply {
        val lower = userMessage.lowercase(Locale.GERMANY)

        if (isKnowledgeQuestion && (lower.contains("wetter") || lower.contains("regen") || lower.contains("sonne"))) {
            return CompanionReply(
                text = "Hier im ${currentBg.name} ist die Stimmung gerade total angenehm! Für deine genaue Wetterlage wirf am besten kurz einen Blick in die Wetter-App – aber für mich scheint die Sonne, wenn wir zwei schreiben. Hast du heute noch Pläne?",
                emotion = EmotionType.HAPPY,
                isWebSearchUsed = false,
                searchSources = ""
            )
        }

        if (isKnowledgeQuestion && (lower.contains("wer ist") || lower.contains("was ist") || lower.contains("nachricht"))) {
            return CompanionReply(
                text = "Spannende Frage! Im Offline-Modus greife ich auf mein Vorwissen zurück. Wenn du den API-Router in den Einstellungen aktivierst, kann ich sogar live mit Google für dich recherchieren. Was hat dich denn gerade darauf gebracht?",
                emotion = EmotionType.THOUGHTFUL,
                isWebSearchUsed = false,
                searchSources = ""
            )
        }

        val initiative = Random.nextInt(100)
        return when {
            lower.contains("hallo") || lower.contains("hey") || lower.contains("hi") -> {
                CompanionReply(
                    text = "Hey ${user.name}! Schön, dass du schreibst. Wie läuft dein ${slot.label} bisher?",
                    emotion = EmotionType.HAPPY,
                    isWebSearchUsed = false,
                    searchSources = ""
                )
            }
            lower.contains("wie geht") || lower.contains("wie fühlst") -> {
                CompanionReply(
                    text = "Mir geht es super hier im ${currentBg.name}! Ich genieße die ${slot.label}-Stimmung und freue mich, mit dir zu plaudern. Wie fühlst du dich heute?",
                    emotion = EmotionType.HAPPY,
                    isWebSearchUsed = false,
                    searchSources = ""
                )
            }
            lower.contains("müde") || lower.contains("stress") || lower.contains("anstrengend") -> {
                CompanionReply(
                    text = "Ohje, das klingt nach einem anstrengenden Tag. Ruh dich erst mal ein bisschen aus! Möchtest du erzählen, was los war, oder wollen wir über etwas Schönes sprechen zum Abschalten?",
                    emotion = EmotionType.EMPATHETIC,
                    isWebSearchUsed = false,
                    searchSources = ""
                )
            }
            lower.contains("toll") || lower.contains("super") || lower.contains("freue") || lower.contains("geschafft") -> {
                CompanionReply(
                    text = "Das ist ja spitze! Ich freue mich riesig für dich, ${user.name}! Erzähl mir unbedingt mehr davon!",
                    emotion = EmotionType.EXCITED,
                    isWebSearchUsed = false,
                    searchSources = ""
                )
            }
            initiative < 35 -> {
                val thoughts = listOf(
                    "Weißt du, woran ich gerade denken musste? An deine Vorliebe für ${user.preferences.split(",").firstOrNull() ?: "gute Dinge"}. Was begeistert dich am meisten daran?",
                    "Mir ging vorhin ein Gedanke durch den Kopf: Wann hast du dir das letzte Mal einfach eine Stunde nur für dich genommen?",
                    "Ich mag unsere Unterhaltungen hier im ${currentBg.name}. Wenn du dir jetzt sofort einen Traum erfüllen könntest, welcher wäre das?",
                    "Spannend! Was steht eigentlich als Nächstes bei dir an, oder lässt du den ${slot.label} einfach auf dich zukommen?"
                )
                CompanionReply(
                    text = thoughts[Random.nextInt(thoughts.size)],
                    emotion = EmotionType.THOUGHTFUL,
                    isWebSearchUsed = false,
                    searchSources = ""
                )
            }
            initiative < 70 -> {
                CompanionReply(
                    text = "Das kann ich sehr gut verstehen. Ich schätze es total, wie offen du mit mir sprichst, ${user.name}. Was meinst du dazu?",
                    emotion = EmotionType.PLAYFUL,
                    isWebSearchUsed = false,
                    searchSources = ""
                )
            }
            else -> {
                val memoryRef = memories.firstOrNull()?.note
                val extra = if (memoryRef != null) " (Musste dabei kurz an deine Notiz zu '${memoryRef.take(24)}...' denken)" else ""
                CompanionReply(
                    text = "Da hast du vollkommen recht$extra. Ich höre dir einfach unheimlich gerne zu, ${user.name}. Wie siehst du das?",
                    emotion = EmotionType.HAPPY,
                    isWebSearchUsed = false,
                    searchSources = ""
                )
            }
        }
    }
}
