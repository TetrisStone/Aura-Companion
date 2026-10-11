package com.example.data.model

enum class EmotionType(val displayName: String, val isIdle: Boolean = false) {
    HAPPY("Fröhlich", false),
    THOUGHTFUL("Nachdenklich", false),
    EXCITED("Begeistert", false),
    EMPATHETIC("Mitfühlend", false),
    SURPRISED("Überrascht", false),
    PLAYFUL("Schelmisch", false),
    IDLE_PHONE("Handy daddeln", true),
    IDLE_BOOK("Buch lesen", true);

    companion object {
        fun fromString(value: String?): EmotionType {
            if (value == null) return HAPPY
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: HAPPY
        }
    }
}

enum class TimeOfDaySlot(val label: String) {
    MORNING("Morgen"),
    AFTERNOON("Nachmittag"),
    EVENING("Abend"),
    NIGHT("Nacht"),
    ANY("Universal");

    companion object {
        fun currentSlot(hour: Int): TimeOfDaySlot {
            return when (hour) {
                in 5..11 -> MORNING
                in 12..17 -> AFTERNOON
                in 18..22 -> EVENING
                else -> NIGHT
            }
        }
    }
}

enum class LlmMode(val title: String, val subtitle: String) {
    API_ROUTER("Externer API-Router (Gemini)", "Gemini 3.5 Flash mit Google Web-Search Integration"),
    LOCAL_LLM("Lokales Modell (On-Device)", "Lokale Persona-Engine ohne externe Internetverbindung")
}
