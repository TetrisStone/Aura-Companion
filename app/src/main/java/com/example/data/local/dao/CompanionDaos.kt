package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.CompanionBackgroundEntity
import com.example.data.local.entity.CompanionMemoryEntity
import com.example.data.local.entity.CompanionSpriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessagesFlow(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM companion_memories ORDER BY timestamp DESC")
    fun getAllMemoriesFlow(): Flow<List<CompanionMemoryEntity>>

    @Query("SELECT * FROM companion_memories ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMemories(limit: Int): List<CompanionMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: CompanionMemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: CompanionMemoryEntity)

    @Delete
    suspend fun deleteMemory(memory: CompanionMemoryEntity)

    @Query("DELETE FROM companion_memories WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface BackgroundDao {
    @Query("SELECT * FROM companion_backgrounds")
    fun getAllBackgroundsFlow(): Flow<List<CompanionBackgroundEntity>>

    @Query("SELECT * FROM companion_backgrounds")
    suspend fun getAllBackgrounds(): List<CompanionBackgroundEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(background: CompanionBackgroundEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(backgrounds: List<CompanionBackgroundEntity>)

    @Delete
    suspend fun delete(background: CompanionBackgroundEntity)

    @Query("DELETE FROM companion_backgrounds WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface SpriteDao {
    @Query("SELECT * FROM companion_sprites")
    fun getAllSpritesFlow(): Flow<List<CompanionSpriteEntity>>

    @Query("SELECT * FROM companion_sprites WHERE id = :emotionId LIMIT 1")
    suspend fun getSprite(emotionId: String): CompanionSpriteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(sprite: CompanionSpriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sprites: List<CompanionSpriteEntity>)
}
