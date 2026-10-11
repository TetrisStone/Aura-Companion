package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.BackgroundDao
import com.example.data.local.dao.ChatDao
import com.example.data.local.dao.MemoryDao
import com.example.data.local.dao.SpriteDao
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.CompanionBackgroundEntity
import com.example.data.local.entity.CompanionMemoryEntity
import com.example.data.local.entity.CompanionSpriteEntity
import com.example.data.model.EmotionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ChatMessageEntity::class,
        CompanionMemoryEntity::class,
        CompanionBackgroundEntity::class,
        CompanionSpriteEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CompanionDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun memoryDao(): MemoryDao
    abstract fun backgroundDao(): BackgroundDao
    abstract fun spriteDao(): SpriteDao

    companion object {
        @Volatile
        private var INSTANCE: CompanionDatabase? = null

        fun getInstance(context: Context): CompanionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CompanionDatabase::class.java,
                    "aura_companion.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun seedDefaults(database: CompanionDatabase) {
            val existing = database.backgroundDao().getAllBackgrounds()
            if (existing.isNotEmpty()) return
            val defaultBackgrounds = listOf(
                CompanionBackgroundEntity(
                    id = "bg_kitchen",
                    name = "Küche am Morgen",
                    drawableResName = "img_bg_kitchen",
                    customUri = null,
                    tags = "Küche, Morgen, Kaffee, Frühstück",
                    timeSlot = "MORNING",
                    isCustom = false
                ),
                CompanionBackgroundEntity(
                    id = "bg_livingroom",
                    name = "Wohnzimmer am Abend",
                    drawableResName = "img_bg_livingroom",
                    customUri = null,
                    tags = "Wohnzimmer, Couch, Abend, Gemütlich",
                    timeSlot = "EVENING",
                    isCustom = false
                ),
                CompanionBackgroundEntity(
                    id = "bg_park",
                    name = "Sonniger Stadtpark",
                    drawableResName = "img_bg_park",
                    customUri = null,
                    tags = "Park, Nachmittag, Natur, Spaziergang",
                    timeSlot = "AFTERNOON",
                    isCustom = false
                ),
                CompanionBackgroundEntity(
                    id = "bg_bedroom",
                    name = "Ruhiges Schlafzimmer",
                    drawableResName = "img_bg_bedroom",
                    customUri = null,
                    tags = "Schlafzimmer, Nacht, Sterne, Ruhe",
                    timeSlot = "NIGHT",
                    isCustom = false
                )
            )
            database.backgroundDao().insertAll(defaultBackgrounds)

            val defaultMemories = listOf(
                CompanionMemoryEntity(
                    title = "Kaffee-Vorliebe",
                    note = "Trinkt morgens am liebsten Hafer-Cappuccino ohne Zucker.",
                    category = "VORLIEBE",
                    recordedDate = "Gestern"
                ),
                CompanionMemoryEntity(
                    title = "Tech-Begeisterung",
                    note = "Liebt innovative Gadgets und baut moderne Android-Apps.",
                    category = "INTERESSE",
                    recordedDate = "Gestern"
                ),
                CompanionMemoryEntity(
                    title = "Entspannung",
                    note = "Mag abends ruhige Musik und gute Gespräche zum Abschalten.",
                    category = "PERSÖNLICHES",
                    recordedDate = "Vor 2 Tagen"
                )
            )
            defaultMemories.forEach { database.memoryDao().insertMemory(it) }

            val defaultSprites = EmotionType.entries.map { emotion ->
                val hex = when (emotion) {
                    EmotionType.HAPPY -> "#FFD54F"
                    EmotionType.THOUGHTFUL -> "#64B5F6"
                    EmotionType.EXCITED -> "#FF4081"
                    EmotionType.EMPATHETIC -> "#81C784"
                    EmotionType.SURPRISED -> "#FFB74D"
                    EmotionType.PLAYFUL -> "#BA68C8"
                    EmotionType.IDLE_PHONE -> "#4DD0E1"
                    EmotionType.IDLE_BOOK -> "#A1887F"
                }
                CompanionSpriteEntity(
                    id = emotion.name,
                    emotionName = emotion.name,
                    customImageUri = null,
                    colorHex = hex,
                    shapeVariant = "VECTOR_AURA"
                )
            }
            database.spriteDao().insertAll(defaultSprites)
        }
    }
}
