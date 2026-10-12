package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LlmMode
import com.example.ui.components.CompanionVisualView
import com.example.ui.components.TransparentChatBox
import com.example.ui.viewmodel.CompanionScreen
import com.example.ui.viewmodel.CompanionViewModel

@Composable
fun MainCompanionScreen(
    viewModel: CompanionViewModel
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val currentBackground by viewModel.currentBackground.collectAsState()
    val currentEmotion by viewModel.currentEmotion.collectAsState()
    val isIdle by viewModel.isIdle.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val memories by viewModel.memories.collectAsState()
    val backgrounds by viewModel.backgrounds.collectAsState()
    val sprites by viewModel.sprites.collectAsState()
    val persona by viewModel.persona.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val llmMode by viewModel.llmMode.collectAsState()
    val llmSettings by viewModel.llmSettings.collectAsState()
    val idleTimeout by viewModel.idleTimeoutSeconds.collectAsState()
    val statusNotice by viewModel.statusNotice.collectAsState()

    var showBackgroundsDialog by remember { mutableStateOf(false) }
    var showOverlayUi by remember { mutableStateOf(true) }

    when (currentScreen) {
        CompanionScreen.SETTINGS -> {
            SettingsScreen(
                userProfile = userProfile,
                companionPersona = persona,
                sprites = sprites,
                llmMode = llmMode,
                llmSettings = llmSettings,
                idleTimeoutSeconds = idleTimeout,
                onSaveUserProfile = { name, pref, quirks, notes ->
                    viewModel.saveUserProfile(name, pref, quirks, notes)
                },
                onSavePersona = { name, personality, backstory, systemPrompt ->
                    viewModel.savePersona(name, personality, backstory, systemPrompt)
                },
                onAssignSprite = { emotion, uri ->
                    viewModel.assignCustomSprite(emotion, uri)
                },
                onResetSprite = { emotion ->
                    viewModel.resetSprite(emotion)
                },
                onSetLlmMode = { mode ->
                    viewModel.setLlmMode(mode)
                },
                onSaveLlmSettings = { settings ->
                    viewModel.saveLlmSettings(settings)
                },
                onTestLlmConnection = { settings, callback ->
                    viewModel.testLlmConnection(settings, callback)
                },
                onSetIdleTimeout = { sec ->
                    viewModel.setIdleTimeout(sec)
                },
                onClearChat = {
                    viewModel.clearChat()
                },
                onBack = { viewModel.navigateTo(CompanionScreen.STAGE) }
            )
        }

        CompanionScreen.MEMORIES -> {
            MemoryScreen(
                companionName = persona.name,
                memories = memories,
                onAddMemory = { title, note, cat ->
                    viewModel.addManualMemory(title, note, cat)
                },
                onDeleteMemory = { id ->
                    viewModel.deleteMemory(id)
                },
                onBack = { viewModel.navigateTo(CompanionScreen.STAGE) }
            )
        }

        CompanionScreen.STAGE, CompanionScreen.BACKGROUNDS_PICKER -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("main_companion_screen")
            ) {
                // Background & Animated Companion Sprite
                CompanionVisualView(
                    background = currentBackground,
                    emotion = currentEmotion,
                    isIdle = isIdle,
                    currentSpriteEntity = sprites.firstOrNull { it.id == currentEmotion.name },
                    companionName = persona.name,
                    onCompanionTap = {
                        if (isIdle) {
                            viewModel.wakeUpFromIdle()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Top Overlay Controls (Can be hidden on button tap for a completely uncluttered view)
                AnimatedVisibility(
                    visible = showOverlayUi,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // LLM Mode Badge & Version Indicator
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = Color(0x7716142E),
                                    shape = RoundedCornerShape(16.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                                    modifier = Modifier
                                        .clickable { viewModel.navigateTo(CompanionScreen.SETTINGS) }
                                        .testTag("llm_mode_indicator")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = when (llmSettings.provider) {
                                                com.example.data.model.LlmProvider.GEMINI -> Color(0xFF64FFDA)
                                                com.example.data.model.LlmProvider.OPENROUTER -> Color(0xFF82B1FF)
                                                com.example.data.model.LlmProvider.LOCAL_AI -> Color(0xFFFFD54F)
                                                com.example.data.model.LlmProvider.CUSTOM_OPENAI -> Color(0xFFFF80AB)
                                            },
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        val displayModel = llmSettings.modelName.split("/").last().take(12)
                                        Text(
                                            text = "${llmSettings.provider.title.split(" ").first()}: $displayModel",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Surface(
                                    color = Color(0x5500E676),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("app_version_tag")
                                ) {
                                    Text(
                                        text = "v1.3",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // Action Icons: Backgrounds, Memories, Settings, and Hide UI toggle
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Switch Background Button
                                IconButton(
                                    onClick = { showBackgroundsDialog = true },
                                    modifier = Modifier.testTag("switch_background_button")
                                ) {
                                    Surface(
                                        color = Color(0x7716142E),
                                        shape = CircleShape,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Image,
                                                contentDescription = "Hintergrund wechseln",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                // Memory Manager Button
                                IconButton(
                                    onClick = { viewModel.navigateTo(CompanionScreen.MEMORIES) },
                                    modifier = Modifier.testTag("open_memories_button")
                                ) {
                                    BadgedBox(
                                        badge = {
                                            if (memories.isNotEmpty()) {
                                                Badge(containerColor = Color(0xFF64FFDA), contentColor = Color(0xFF100E26)) {
                                                    Text(memories.size.toString())
                                                }
                                            }
                                        }
                                    ) {
                                        Surface(
                                            color = Color(0x7716142E),
                                            shape = CircleShape,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Psychology,
                                                    contentDescription = "Gedächtnis",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Settings Button
                                IconButton(
                                    onClick = { viewModel.navigateTo(CompanionScreen.SETTINGS) },
                                    modifier = Modifier.testTag("open_settings_button")
                                ) {
                                    Surface(
                                        color = Color(0x7716142E),
                                        shape = CircleShape,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Settings,
                                                contentDescription = "Einstellungen",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                // Button to Hide UI overlay
                                IconButton(
                                    onClick = { showOverlayUi = false },
                                    modifier = Modifier.testTag("hide_ui_button")
                                ) {
                                    Surface(
                                        color = Color(0x7716142E),
                                        shape = CircleShape,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.VisibilityOff,
                                                contentDescription = "UI ausblenden",
                                                tint = Color(0xDDFFFFFF),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Subtle Location Pill
                        if (currentBackground != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = Color(0x7716142E),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF)),
                                modifier = Modifier.clickable { showBackgroundsDialog = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Place,
                                        contentDescription = null,
                                        tint = Color(0xFF64FFDA),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = currentBackground!!.name,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // If UI overlays are hidden, show a subtle button to restore them
                if (!showOverlayUi) {
                    IconButton(
                        onClick = { showOverlayUi = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(16.dp)
                            .testTag("restore_ui_button")
                    ) {
                        Surface(
                            color = Color(0x6616142E),
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Visibility,
                                    contentDescription = "UI wieder einblenden",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Dynamic Notice Toast (e.g. Memory auto-saved)
                AnimatedVisibility(
                    visible = statusNotice != null,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 90.dp)
                ) {
                    Surface(
                        color = Color(0xEE1E1742),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF64FFDA)),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = statusNotice ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF64FFDA),
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                // Transparent Dialogue Box with "Weiter ❯" Button
                TransparentChatBox(
                    companionName = persona.name,
                    currentEmotion = currentEmotion,
                    isIdle = isIdle,
                    isGenerating = isGenerating,
                    messages = messages,
                    onSendMessage = { text -> viewModel.sendMessage(text) },
                    onContinueTalking = { viewModel.continueConversation() },
                    onWakeUp = { viewModel.wakeUpFromIdle() },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                // Backgrounds Dialog
                if (showBackgroundsDialog) {
                    BackgroundsDialog(
                        backgrounds = backgrounds,
                        currentBackground = currentBackground,
                        onSelectBackground = { bg ->
                            viewModel.setBackground(bg)
                            showBackgroundsDialog = false
                        },
                        onAddBackground = { name, uri, tags, slot ->
                            viewModel.addCustomBackground(name, uri, tags, slot)
                        },
                        onDeleteBackground = { bg ->
                            viewModel.deleteBackground(bg)
                        },
                        onDismiss = { showBackgroundsDialog = false }
                    )
                }
            }
        }
    }
}
