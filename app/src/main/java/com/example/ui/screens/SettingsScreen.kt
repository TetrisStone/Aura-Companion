package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.BuildConfig
import com.example.data.local.PreferencesManager
import com.example.data.local.entity.CompanionSpriteEntity
import com.example.data.model.EmotionType
import com.example.data.model.LlmMode
import com.example.ui.components.CompanionAvatarShape
import com.example.ui.components.getEmotionColor
import com.example.ui.components.getEmotionIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userProfile: PreferencesManager.UserProfile,
    companionPersona: PreferencesManager.CompanionPersona,
    sprites: List<CompanionSpriteEntity>,
    llmMode: LlmMode,
    idleTimeoutSeconds: Int,
    onSaveUserProfile: (name: String, preferences: String, quirks: String, notes: String) -> Unit,
    onSavePersona: (name: String, personality: String, backstory: String, systemPrompt: String) -> Unit,
    onAssignSprite: (emotion: EmotionType, uri: String) -> Unit,
    onResetSprite: (emotion: EmotionType) -> Unit,
    onSetLlmMode: (LlmMode) -> Unit,
    onSetIdleTimeout: (Int) -> Unit,
    onClearChat: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Steckbrief", "Companion-Persona", "Emotions-Sprites", "KI-Router")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Einstellungen & Setup",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF100E26))
            )
        },
        containerColor = Color(0xFF0C0A1E),
        modifier = Modifier.testTag("settings_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF14122C),
                contentColor = Color(0xFF64FFDA),
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) Color(0xFF64FFDA) else Color(0xAAFFFFFF)
                            )
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when (selectedTab) {
                    0 -> UserProfileTab(userProfile = userProfile, onSave = onSaveUserProfile)
                    1 -> CompanionPersonaTab(persona = companionPersona, onSave = onSavePersona)
                    2 -> SpritesManagerTab(sprites = sprites, onAssignSprite = onAssignSprite, onResetSprite = onResetSprite)
                    3 -> LlmRouterTab(
                        llmMode = llmMode,
                        idleTimeout = idleTimeoutSeconds,
                        onSetLlmMode = onSetLlmMode,
                        onSetIdleTimeout = onSetIdleTimeout,
                        onClearChat = onClearChat
                    )
                }
            }
        }
    }
}

@Composable
fun UserProfileTab(
    userProfile: PreferencesManager.UserProfile,
    onSave: (name: String, preferences: String, quirks: String, notes: String) -> Unit
) {
    var name by remember(userProfile) { mutableStateOf(userProfile.name) }
    var preferences by remember(userProfile) { mutableStateOf(userProfile.preferences) }
    var quirks by remember(userProfile) { mutableStateOf(userProfile.quirks) }
    var notes by remember(userProfile) { mutableStateOf(userProfile.notes) }
    var savedNotice by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Dein persönlicher Steckbrief",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
        )
        Text(
            text = "Der Companion greift auf diese Daten zurück, um dich persönlich anzusprechen und deine Vorlieben zu kennen.",
            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xBBFFFFFF))
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Dein Name / Spitzname") },
            modifier = Modifier.fillMaxWidth(),
            colors = defaultFieldColors()
        )

        OutlinedTextField(
            value = preferences,
            onValueChange = { preferences = it },
            label = { Text("Feste Vorlieben (z.B. Kaffee, Sci-Fi, Spaziergänge)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            colors = defaultFieldColors()
        )

        OutlinedTextField(
            value = quirks,
            onValueChange = { quirks = it },
            label = { Text("Eigenheiten (z.B. Nachtmensch, denkt laut nach)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            colors = defaultFieldColors()
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Besondere Notizen (aktuelle Projekte, Ziele)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            colors = defaultFieldColors()
        )

        Button(
            onClick = {
                onSave(name, preferences, quirks, notes)
                savedNotice = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("save_user_profile_button")
        ) {
            Text("Steckbrief speichern", color = Color.White, fontWeight = FontWeight.Bold)
        }

        if (savedNotice) {
            Text(
                text = "✓ Steckbrief erfolgreich aktualisiert!",
                color = Color(0xFF64FFDA),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun CompanionPersonaTab(
    persona: PreferencesManager.CompanionPersona,
    onSave: (name: String, personality: String, backstory: String, systemPrompt: String) -> Unit
) {
    var name by remember(persona) { mutableStateOf(persona.name) }
    var personality by remember(persona) { mutableStateOf(persona.personality) }
    var backstory by remember(persona) { mutableStateOf(persona.backstory) }
    var systemPrompt by remember(persona) { mutableStateOf(persona.systemPrompt) }
    var savedNotice by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Companion-Identität & Tonalität",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name des Companions") },
            modifier = Modifier.fillMaxWidth(),
            colors = defaultFieldColors()
        )

        OutlinedTextField(
            value = personality,
            onValueChange = { personality = it },
            label = { Text("Persönlichkeit & Wesenszüge") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            colors = defaultFieldColors()
        )

        OutlinedTextField(
            value = backstory,
            onValueChange = { backstory = it },
            label = { Text("Hintergrundgeschichte") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            colors = defaultFieldColors()
        )

        OutlinedTextField(
            value = systemPrompt,
            onValueChange = { systemPrompt = it },
            label = { Text("Allgemeiner System-Prompt") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 6,
            colors = defaultFieldColors()
        )

        Button(
            onClick = {
                onSave(name, personality, backstory, systemPrompt)
                savedNotice = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("save_companion_persona_button")
        ) {
            Text("Persona speichern", color = Color.White, fontWeight = FontWeight.Bold)
        }

        if (savedNotice) {
            Text(
                text = "✓ Companion-Persona erfolgreich gespeichert!",
                color = Color(0xFF64FFDA),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SpritesManagerTab(
    sprites: List<CompanionSpriteEntity>,
    onAssignSprite: (emotion: EmotionType, uri: String) -> Unit,
    onResetSprite: (emotion: EmotionType) -> Unit
) {
    val context = LocalContext.current
    var targetEmotionToUpload by remember { mutableStateOf<EmotionType?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null && targetEmotionToUpload != null) {
                onAssignSprite(targetEmotionToUpload!!, uri.toString())
                targetEmotionToUpload = null
            }
        }
    )

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "Emotions- & Idle-Sprites zuordnen",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
        )
        Text(
            text = "Wähle für jede Emotion ein eigenes Bild oder nutze das dynamische Vektor-Shape.",
            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xBBFFFFFF))
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(EmotionType.entries) { emotion ->
                val spriteEntity = sprites.firstOrNull { it.id == emotion.name }
                val hasCustomImage = spriteEntity?.customImageUri != null
                val emotionColor = getEmotionColor(emotion)

                Surface(
                    color = Color(0x331E1B3F),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, emotionColor.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF161230)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasCustomImage) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(spriteEntity?.customImageUri).build(),
                                    contentDescription = emotion.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CompanionAvatarShape(
                                    emotion = emotion,
                                    isIdle = emotion.isIdle,
                                    pulseGlow = 0.8f,
                                    companionName = "Aura",
                                    modifier = Modifier.size(80.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = emotion.displayName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = if (hasCustomImage) "Eigenes Bild" else "Vektor-Shape",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = emotionColor,
                                fontSize = 10.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = Color(0xFF7C4DFF),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable {
                                    targetEmotionToUpload = emotion
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Bild", style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontSize = 10.sp))
                                }
                            }

                            if (hasCustomImage) {
                                Surface(
                                    color = Color(0x33FFFFFF),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable { onResetSprite(emotion) }
                                ) {
                                    Text(
                                        text = "Reset",
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontSize = 10.sp),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LlmRouterTab(
    llmMode: LlmMode,
    idleTimeout: Int,
    onSetLlmMode: (LlmMode) -> Unit,
    onSetIdleTimeout: (Int) -> Unit,
    onClearChat: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "KI-Router & Modell-Auswahl",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
        )
        Text(
            text = "Lege fest, welches Modell für die Gedanken und Antworten des Companions genutzt werden soll.",
            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xBBFFFFFF))
        )

        // Radio Option 1: Externer API-Router
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (llmMode == LlmMode.API_ROUTER) Color(0x337C4DFF) else Color(0x1F2A284D)
            ),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (llmMode == LlmMode.API_ROUTER) Color(0xFF64FFDA) else Color(0x33FFFFFF)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSetLlmMode(LlmMode.API_ROUTER) }
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = llmMode == LlmMode.API_ROUTER,
                    onClick = { onSetLlmMode(LlmMode.API_ROUTER) },
                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF64FFDA))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Externer API-Router (Gemini)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                    Text(
                        text = "Gemini 3.5 Flash mit Google Web-Search Integration für aktuelle Recherchen & Fakten.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xCCFFFFFF))
                    )
                }
            }
        }

        // Radio Option 2: Lokales On-Device Modell
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (llmMode == LlmMode.LOCAL_LLM) Color(0x337C4DFF) else Color(0x1F2A284D)
            ),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (llmMode == LlmMode.LOCAL_LLM) Color(0xFF64FFDA) else Color(0x33FFFFFF)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSetLlmMode(LlmMode.LOCAL_LLM) }
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = llmMode == LlmMode.LOCAL_LLM,
                    onClick = { onSetLlmMode(LlmMode.LOCAL_LLM) },
                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF64FFDA))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Lokales On-Device Modell (Offline)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                    Text(
                        text = "Direkt auf dem Smartphone ausgeführte Persona-Engine ohne externe Internetverbindung.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xCCFFFFFF))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Idle-Timeout Konfiguration
        Text(
            text = "Idle-Timeout (Inaktivitäts-Erkennung)",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
        )
        Text(
            text = "Zeitspanne ohne Nutzereingabe, nach der der Companion automatisch in die Idle-Pose (z.B. Handy daddeln oder Buch lesen) wechselt.",
            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xBBFFFFFF))
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(
                15 to "15 Sek (Test)",
                60 to "1 Minute",
                180 to "3 Min (Standard)"
            ).forEach { (seconds, label) ->
                val isSelected = idleTimeout == seconds
                Surface(
                    color = if (isSelected) Color(0xFF7C4DFF) else Color(0x22FFFFFF),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(0xFF64FFDA) else Color.Transparent),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSetIdleTimeout(seconds) }
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onClearChat,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0x44FF5252)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFFF8A80))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Chatverlauf zurücksetzen & Neu begrüßen", color = Color(0xFFFF8A80))
        }
    }
}

@Composable
fun defaultFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = Color(0x221E1B3F),
    unfocusedContainerColor = Color(0x181E1B3F),
    focusedBorderColor = Color(0xFF64FFDA),
    unfocusedBorderColor = Color(0x44FFFFFF),
    focusedLabelColor = Color(0xFF64FFDA),
    unfocusedLabelColor = Color(0xAAFFFFFF)
)
