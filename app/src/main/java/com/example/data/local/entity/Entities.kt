package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String, // "USER" or "COMPANION"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val emotion: String = "HAPPY",
    val backgroundId: String = "bg_kitchen",
    val isWebSearch: Boolean = false,
    val webSources: String = ""
)

@Entity(tableName = "companion_memories")
data class CompanionMemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val note: String,
    val category: String = "PERSÖNLICHES", // VORLIEBE, TERMIN, INTERESSE, FAKT
    val recordedDate: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "companion_backgrounds")
data class CompanionBackgroundEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val drawableResName: String? = null,
    val customUri: String? = null,
    val tags: String, // e.g. "Küche, Morgen, Kaffee"
    val timeSlot: String = "ANY", // MORNING, AFTERNOON, EVENING, NIGHT, ANY
    val isCustom: Boolean = false
)

@Entity(tableName = "companion_sprites")
data class CompanionSpriteEntity(
    @PrimaryKey
    val id: String, // EmotionType.name
    val emotionName: String,
    val customImageUri: String? = null,
    val colorHex: String = "#7C4DFF",
    val shapeVariant: String = "DEFAULT"
)
