package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.model.EmotionType

@Composable
fun TransparentChatBox(
    companionName: String,
    currentEmotion: EmotionType,
    isIdle: Boolean,
    isGenerating: Boolean,
    messages: List<ChatMessageEntity>,
    onSendMessage: (String) -> Unit,
    onContinueTalking: () -> Unit,
    onWakeUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showWebSourcesForMessage by remember { mutableStateOf<ChatMessageEntity?>(null) }
    val listState = rememberLazyListState()

    val lastMessage = messages.lastOrNull()
    val emotionColor = getEmotionColor(currentEmotion)

    // Scroll to latest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Surface(
        color = Color(0xCC0E0D24), // Frosted glassmorphism dark violet
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.verticalGradient(
                listOf(
                    emotionColor.copy(alpha = 0.5f),
                    Color(0x22FFFFFF)
                )
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("transparent_chat_box")
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // --- Top Bar: Clean Companion Name & History Toggle ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (isIdle) Color(0xFFFFB74D) else Color(0xFF00E676))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = companionName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                // History button (only icon, uncluttered)
                IconButton(
                    onClick = { showHistoryDialog = !showHistoryDialog },
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("chat_history_toggle")
                ) {
                    Icon(
                        imageVector = if (showHistoryDialog) Icons.Default.ExpandMore else Icons.Default.History,
                        contentDescription = "Verlauf einblenden",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // --- Chat View (Either Compact Single Dialogue or Expanded History) ---
            if (showHistoryDialog) {
                // Expanded Conversation History
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(vertical = 4.dp)
                ) {
                    items(messages) { msg ->
                        ChatMessageItem(
                            message = msg,
                            companionName = companionName,
                            onShowSources = { showWebSourcesForMessage = msg }
                        )
                    }
                }
            } else {
                // Compact Visual Novel Dialogue
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x551E1C38))
                        .padding(14.dp)
                        .testTag("companion_dialogue_card")
                ) {
                    if (isGenerating) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = emotionColor,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "$companionName denkt nach...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xDDFFFFFF),
                                    fontSize = 14.sp
                                )
                            )
                        }
                    } else if (isIdle) {
                        Text(
                            text = when (currentEmotion) {
                                EmotionType.IDLE_PHONE -> "($companionName scrollt leise auf ihrem Smartphone und wartet geduldig.)"
                                EmotionType.IDLE_BOOK -> "($companionName vertieft sich in ein Buch und entspannt sich.)"
                                else -> "($companionName ruht sich aus.)"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFFAAAAAA),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                fontSize = 14.sp
                            )
                        )
                    } else if (lastMessage != null) {
                        Column {
                            if (lastMessage.sender == "USER") {
                                Text(
                                    text = "Du: ${lastMessage.text}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xAAFFFFFF)
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            val companionText = if (lastMessage.sender == "COMPANION") {
                                lastMessage.text
                            } else {
                                messages.findLast { it.sender == "COMPANION" }?.text ?: "$companionName hört dir zu..."
                            }

                            Text(
                                text = companionText,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color.White,
                                    lineHeight = 21.sp,
                                    fontSize = 15.sp
                                )
                            )

                            val lastCompMsg = if (lastMessage.sender == "COMPANION") lastMessage else messages.findLast { it.sender == "COMPANION" }
                            if (lastCompMsg?.isWebSearch == true) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = Color(0x4464FFDA),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable { showWebSourcesForMessage = lastCompMsg }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = "Websuche",
                                            tint = Color(0xFF64FFDA),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Recherchiert mit Websuche",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color(0xFF64FFDA),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "$companionName ist bereit. Schreib etwas oder tippe auf 'Weiter ❯', um sie plaudern zu lassen.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xCCFFFFFF),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        )
                    }
                }
            }

            // Web Search Sources info popup
            if (showWebSourcesForMessage != null) {
                Surface(
                    color = Color(0xF0181530),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF64FFDA)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🌐 Verwendete Internetquellen:",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color(0xFF64FFDA),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Schließen",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White),
                                modifier = Modifier.clickable { showWebSourcesForMessage = null }
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = showWebSourcesForMessage?.webSources?.ifBlank { "Google Search Grounding (Live-Daten)" } ?: "Live-Suche",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xDDFFFFFF))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Action Bar: "Weiter ❯" Button + Quick Topic Suggestions ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                // Der geforderte "Weiter"-Button (LLM plappert von sich aus weiter)
                Surface(
                    color = Color(0xFF7C4DFF),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB388FF)),
                    modifier = Modifier
                        .clickable(enabled = !isGenerating) {
                            onWakeUp()
                            onContinueTalking()
                        }
                        .testTag("continue_talking_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Weiter",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Weiter ❯",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                // 2 dezente Themenvorschläge
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuickPromptChip("Erzähl mir was") { onSendMessage("Erzähl mir deine Gedanken! Woran hast du gerade gedacht?") }
                    QuickPromptChip("Recherche 🔍") { onSendMessage("Was gibt es heute Neues auf der Welt? Recherchier das mal für mich.") }
                }
            }

            // --- Text Input Row ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        onWakeUp()
                    },
                    placeholder = {
                        Text(
                            text = if (isIdle) "Tippe, um $companionName anzusprechen..." else "Nachricht an $companionName...",
                            color = Color(0x88FFFFFF),
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("companion_message_input"),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0x441F1D3D),
                        unfocusedContainerColor = Color(0x331F1D3D),
                        focusedBorderColor = emotionColor,
                        unfocusedBorderColor = Color(0x44FFFFFF)
                    ),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank() && !isGenerating) {
                                onSendMessage(inputText)
                                inputText = ""
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = if (inputText.isNotBlank()) emotionColor else Color(0x33FFFFFF),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(enabled = inputText.isNotBlank() && !isGenerating) {
                            onSendMessage(inputText)
                            inputText = ""
                        }
                        .testTag("companion_send_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Senden",
                            tint = if (inputText.isNotBlank()) Color(0xFF100E26) else Color(0x88FFFFFF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickPromptChip(text: String, onClick: () -> Unit) {
    Surface(
        color = Color(0x28FFFFFF),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0x33FFFFFF)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color(0xEEFFFFFF),
                fontSize = 11.sp
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessageEntity,
    companionName: String,
    onShowSources: () -> Unit
) {
    val isUser = message.sender == "USER"
    val align = if (isUser) Alignment.End else Alignment.Start

    Column(
        horizontalAlignment = align,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = if (isUser) "Du" else companionName,
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (isUser) Color(0xFF64FFDA) else Color(0xFFFFD54F),
                fontWeight = FontWeight.Bold
            )
        )
        Surface(
            color = if (isUser) Color(0x5500B4D8) else Color(0x553C1F75),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White)
                )
                if (message.isWebSearch) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "🌐 Websuche genutzt [Tippen für Quellen]",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF64FFDA),
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.clickable { onShowSources() }
                    )
                }
            }
        }
    }
}
