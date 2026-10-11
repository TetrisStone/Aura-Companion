package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CompanionDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.CompanionBackgroundEntity
import com.example.data.local.entity.CompanionMemoryEntity
import com.example.data.local.entity.CompanionSpriteEntity
import com.example.data.model.EmotionType
import com.example.data.model.LlmMode
import com.example.domain.CompanionEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class CompanionScreen {
    STAGE,
    SETTINGS,
    MEMORIES,
    BACKGROUNDS_PICKER
}

class CompanionViewModel(application: Application) : AndroidViewModel(application) {
    private val database = CompanionDatabase.getInstance(application)
    val prefs = PreferencesManager(application)
    val engine = CompanionEngine(application, database, prefs)

    private val _sessionMessages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val messages: StateFlow<List<ChatMessageEntity>> = _sessionMessages.asStateFlow()

    val memories = database.memoryDao().getAllMemoriesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backgrounds = database.backgroundDao().getAllBackgroundsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sprites = database.spriteDao().getAllSpritesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userProfile = prefs.userProfileFlow
    val persona = prefs.personaFlow
    val llmMode = prefs.llmModeFlow
    val idleTimeoutSeconds = prefs.idleTimeoutSecondsFlow

    private val _currentScreen = MutableStateFlow(CompanionScreen.STAGE)
    val currentScreen: StateFlow<CompanionScreen> = _currentScreen.asStateFlow()

    private val _currentBackground = MutableStateFlow<CompanionBackgroundEntity?>(null)
    val currentBackground: StateFlow<CompanionBackgroundEntity?> = _currentBackground.asStateFlow()

    private val _currentEmotion = MutableStateFlow(EmotionType.HAPPY)
    val currentEmotion: StateFlow<EmotionType> = _currentEmotion.asStateFlow()

    private val _isIdle = MutableStateFlow(false)
    val isIdle: StateFlow<Boolean> = _isIdle.asStateFlow()

    private val _idleEmotion = MutableStateFlow(EmotionType.IDLE_PHONE)
    val idleEmotion: StateFlow<EmotionType> = _idleEmotion.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _statusNotice = MutableStateFlow<String?>(null)
    val statusNotice: StateFlow<String?> = _statusNotice.asStateFlow()

    private var lastUserActivityTimestamp = System.currentTimeMillis()
    private var idleCheckJob: Job? = null

    init {
        initializeCompanion()
        startIdleWatcher()
    }

    private fun initializeCompanion() {
        viewModelScope.launch {
            // Select background strictly matching time of day on app launch
            val bg = engine.selectBackgroundForCurrentTime()
            _currentBackground.value = bg

            // Start with a clean context window for this session
            _sessionMessages.value = emptyList()
            prefs.updateLastActiveTimestamp()
        }
    }

    fun continueConversation() {
        recordActivity()
        wakeUpFromIdle()

        viewModelScope.launch {
            val currentBg = _currentBackground.value ?: engine.selectBackgroundForCurrentTime()
            _isGenerating.value = true
            val recent = _sessionMessages.value.takeLast(6)

            val reply = engine.continueConversation(recent, currentBg)
            _currentEmotion.value = reply.emotion

            val newMsg = ChatMessageEntity(
                sender = "COMPANION",
                text = reply.text,
                emotion = reply.emotion.name,
                backgroundId = currentBg.id,
                isWebSearch = reply.isWebSearchUsed,
                webSources = reply.searchSources
            )
            _sessionMessages.value = _sessionMessages.value + newMsg
            _isGenerating.value = false
            recordActivity()
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        recordActivity()
        wakeUpFromIdle()

        viewModelScope.launch {
            val currentBg = _currentBackground.value ?: engine.selectBackgroundForCurrentTime()

            val userMsg = ChatMessageEntity(
                sender = "USER",
                text = text.trim(),
                emotion = "HAPPY",
                backgroundId = currentBg.id
            )
            _sessionMessages.value = _sessionMessages.value + userMsg

            _isGenerating.value = true
            val recent = _sessionMessages.value.takeLast(6)

            val reply = engine.respondToUser(text, recent, currentBg)
            _currentEmotion.value = reply.emotion

            val compMsg = ChatMessageEntity(
                sender = "COMPANION",
                text = reply.text,
                emotion = reply.emotion.name,
                backgroundId = currentBg.id,
                isWebSearch = reply.isWebSearchUsed,
                webSources = reply.searchSources
            )
            _sessionMessages.value = _sessionMessages.value + compMsg

            if (reply.extractedMemoryTitle != null) {
                _statusNotice.value = "Gemerkt: ${reply.extractedMemoryTitle}"
                delay(3000)
                _statusNotice.value = null
            }

            _isGenerating.value = false
            recordActivity()
        }
    }

    fun recordActivity() {
        lastUserActivityTimestamp = System.currentTimeMillis()
        if (_isIdle.value) {
            wakeUpFromIdle()
        }
    }

    fun wakeUpFromIdle() {
        if (_isIdle.value) {
            _isIdle.value = false
            _currentEmotion.value = EmotionType.HAPPY
            lastUserActivityTimestamp = System.currentTimeMillis()
        }
    }

    fun triggerManualIdle() {
        _isIdle.value = true
        _idleEmotion.value = if (Random.nextBoolean()) EmotionType.IDLE_PHONE else EmotionType.IDLE_BOOK
        _currentEmotion.value = _idleEmotion.value
    }

    private fun startIdleWatcher() {
        idleCheckJob?.cancel()
        idleCheckJob = viewModelScope.launch {
            while (true) {
                delay(5000) // check every 5s
                val timeoutMs = (idleTimeoutSeconds.value * 1000).toLong()
                val elapsed = System.currentTimeMillis() - lastUserActivityTimestamp
                if (!_isIdle.value && !_isGenerating.value && elapsed >= timeoutMs) {
                    _isIdle.value = true
                    _idleEmotion.value = if (Random.nextBoolean()) EmotionType.IDLE_PHONE else EmotionType.IDLE_BOOK
                    _currentEmotion.value = _idleEmotion.value
                }
            }
        }
    }

    fun navigateTo(screen: CompanionScreen) {
        _currentScreen.value = screen
        recordActivity()
    }

    fun setBackground(bg: CompanionBackgroundEntity) {
        _currentBackground.value = bg
        recordActivity()
    }

    fun setEmotion(emotion: EmotionType) {
        _currentEmotion.value = emotion
        recordActivity()
    }

    fun addCustomBackground(name: String, uri: String, tags: String, timeSlot: String) {
        viewModelScope.launch {
            val newBg = CompanionBackgroundEntity(
                id = "bg_custom_${System.currentTimeMillis()}",
                name = name,
                drawableResName = null,
                customUri = uri,
                tags = tags,
                timeSlot = timeSlot,
                isCustom = true
            )
            database.backgroundDao().insert(newBg)
            _currentBackground.value = newBg
        }
    }

    fun deleteBackground(bg: CompanionBackgroundEntity) {
        viewModelScope.launch {
            database.backgroundDao().delete(bg)
            if (_currentBackground.value?.id == bg.id) {
                _currentBackground.value = backgrounds.value.firstOrNull { it.id != bg.id }
            }
        }
    }

    fun assignCustomSprite(emotion: EmotionType, uri: String) {
        viewModelScope.launch {
            database.spriteDao().insertOrUpdate(
                CompanionSpriteEntity(
                    id = emotion.name,
                    emotionName = emotion.name,
                    customImageUri = uri,
                    shapeVariant = "CUSTOM_IMAGE"
                )
            )
        }
    }

    fun resetSprite(emotion: EmotionType) {
        viewModelScope.launch {
            database.spriteDao().insertOrUpdate(
                CompanionSpriteEntity(
                    id = emotion.name,
                    emotionName = emotion.name,
                    customImageUri = null,
                    shapeVariant = "DEFAULT"
                )
            )
        }
    }

    fun saveUserProfile(name: String, preferences: String, quirks: String, notes: String) {
        prefs.saveUserProfile(
            PreferencesManager.UserProfile(name, preferences, quirks, notes)
        )
    }

    fun savePersona(name: String, personality: String, backstory: String, systemPrompt: String) {
        prefs.savePersona(
            PreferencesManager.CompanionPersona(name, personality, backstory, systemPrompt)
        )
    }

    fun setLlmMode(mode: LlmMode) {
        prefs.saveLlmMode(mode)
    }

    fun setIdleTimeout(seconds: Int) {
        prefs.saveIdleTimeout(seconds)
    }

    fun addManualMemory(title: String, note: String, category: String) {
        viewModelScope.launch {
            database.memoryDao().insertMemory(
                CompanionMemoryEntity(
                    title = title,
                    note = note,
                    category = category,
                    recordedDate = "Manuell"
                )
            )
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            database.memoryDao().deleteById(id)
        }
    }

    fun clearChat() {
        _sessionMessages.value = emptyList()
        viewModelScope.launch {
            database.chatDao().clearAll()
        }
    }
}
