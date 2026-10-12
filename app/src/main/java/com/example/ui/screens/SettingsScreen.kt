package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.BuildConfig
import com.example.data.local.PreferencesManager
import com.example.data.local.entity.CompanionSpriteEntity
import com.example.data.model.EmotionType
import com.example.data.model.LlmMode
import com.example.data.model.LlmProvider
import com.example.data.model.LlmSettings
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
    llmSettings: LlmSettings,
    idleTimeoutSeconds: Int,
    onSaveUserProfile: (name: String, preferences: String, quirks: String, notes: String) -> Unit,
    onSavePersona: (name: String, personality: String, backstory: String, systemPrompt: String) -> Unit,
    onAssignSprite: (emotion: EmotionType, uri: String) -> Unit,
    onResetSprite: (emotion: EmotionType) -> Unit,
    onSetLlmMode: (LlmMode) -> Unit,
    onSaveLlmSettings: (LlmSettings) -> Unit,
    onTestLlmConnection: (LlmSettings, (Boolean, String) -> Unit) -> Unit,
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
                        llmSettings = llmSettings,
                        idleTimeout = idleTimeoutSeconds,
                        onSaveLlmSettings = onSaveLlmSettings,
                        onTestLlmConnection = onTestLlmConnection,
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
    llmSettings: LlmSettings,
    idleTimeout: Int,
    onSaveLlmSettings: (LlmSettings) -> Unit,
    onTestLlmConnection: (LlmSettings, (Boolean, String) -> Unit) -> Unit,
    onSetIdleTimeout: (Int) -> Unit,
    onClearChat: () -> Unit
) {
    var selectedProvider by remember(llmSettings) { mutableStateOf(llmSettings.provider) }
    var apiKey by remember(llmSettings) { mutableStateOf(llmSettings.apiKey) }
    var baseUrl by remember(llmSettings) { mutableStateOf(llmSettings.baseUrl) }
    var modelName by remember(llmSettings) { mutableStateOf(llmSettings.modelName) }
    var enableWebSearch by remember(llmSettings) { mutableStateOf(llmSettings.enableWebSearch) }
    var isKeyVisible by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showSavedNotice by remember { mutableStateOf(false) }

    fun currentDraftSettings() = LlmSettings(
        provider = selectedProvider,
        apiKey = apiKey.trim(),
        baseUrl = baseUrl.trim(),
        modelName = modelName.trim(),
        enableWebSearch = enableWebSearch
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "KI-Schnittstellen & Modell-Router",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
        )
        Text(
            text = "Wähle deine bevorzugte KI-Quelle. Alle Antworten des Companions werden direkt und ausschließlich von diesem Modell erzeugt.",
            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xBBFFFFFF))
        )

        // Provider Selection Cards
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LlmProvider.entries.forEach { providerOption ->
                val isSelected = selectedProvider == providerOption
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0x337C4DFF) else Color(0x1F2A284D)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFF64FFDA) else Color(0x22FFFFFF)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedProvider = providerOption
                            baseUrl = providerOption.defaultBaseUrl
                            modelName = providerOption.defaultModel
                            testResult = null
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                selectedProvider = providerOption
                                baseUrl = providerOption.defaultBaseUrl
                                modelName = providerOption.defaultModel
                                testResult = null
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF64FFDA))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = providerOption.title,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                                if (providerOption == LlmProvider.LOCAL_AI) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = Color(0x4400E676),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Offline / Kein Cloud-Zwang",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color(0xFF69F0AE),
                                                fontSize = 9.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = providerOption.subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xCCFFFFFF))
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Configuration Form for the selected provider
        Surface(
            color = Color(0x221A1838),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3364FFDA)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Konfiguration für ${selectedProvider.title}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF64FFDA))
                )

                // API Key Field (if needed)
                if (selectedProvider.isKeyRequired || selectedProvider == LlmProvider.GEMINI) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; testResult = null },
                        label = { Text("API-Schlüssel") },
                        placeholder = {
                            if (selectedProvider == LlmProvider.GEMINI) {
                                Text("Optional (standardmäßig aus Secrets)")
                            } else {
                                Text("z.B. sk-or-v1-...")
                            }
                        },
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Passwort anzeigen",
                                    tint = Color.White
                                )
                            }
                        },
                        colors = defaultFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (selectedProvider == LlmProvider.GEMINI && apiKey.isBlank()) {
                        Text(
                            text = "💡 Tipp: Für Gemini wird automatisch der in AI Studio hinterlegte API-Schlüssel verwendet, sofern kein eigener Schlüssel eingetragen ist.",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xAAFFFFFF))
                        )
                    }
                }

                // Base URL / Server Endpoint
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; testResult = null },
                    label = { Text("Server-Adresse / Endpunkt") },
                    colors = defaultFieldColors(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick preset buttons for Local AI
                if (selectedProvider == LlmProvider.LOCAL_AI) {
                    Text(
                        text = "Schnellauswahl für Endpunkt:",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xAAFFFFFF))
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            "http://10.0.2.2:11434/v1" to "Emulator (10.0.2.2)",
                            "http://192.168.178.50:11434/v1" to "WLAN (Ollama)",
                            "http://192.168.178.50:1234/v1" to "LM Studio"
                        ).forEach { (url, label) ->
                            Surface(
                                color = Color(0x22FFFFFF),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { baseUrl = url; testResult = null }
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontSize = 10.sp),
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    Text(
                        text = "📱 Hinweis für echtes Handy: Wenn du die App auf deinem Smartphone installierst, ersetze '192.168.178.50' durch die lokale IP-Adresse deines PCs im Heimnetzwerk. Starte Ollama auf dem PC mit 'OLLAMA_HOST=0.0.0.0 ollama serve', damit Verbindungen erlaubt sind.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF80CBC4), fontSize = 11.sp)
                    )
                }

                // Model Name
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it; testResult = null },
                    label = { Text("Modellname") },
                    colors = defaultFieldColors(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Model Suggestion Chips
                val modelSuggestions = when (selectedProvider) {
                    LlmProvider.GEMINI -> listOf(
                        "gemini-2.0-flash",
                        "gemini-2.5-flash",
                        "gemini-3.5-flash",
                        "gemini-3.1-pro-preview"
                    )
                    LlmProvider.OPENROUTER -> listOf(
                        "google/gemini-2.0-flash-001",
                        "meta-llama/llama-3.3-70b-instruct",
                        "deepseek/deepseek-chat",
                        "anthropic/claude-3.5-haiku",
                        "google/gemini-2.5-flash"
                    )
                    LlmProvider.LOCAL_AI -> listOf("llama3.2", "mistral", "qwen2.5", "phi3")
                    LlmProvider.CUSTOM_OPENAI -> listOf("gpt-4o-mini", "llama-3.3-70b-versatile", "mistral-large")
                }

                Text(
                    text = "Schnellauswahl für Modell (antippen):",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xAAFFFFFF))
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    modelSuggestions.forEach { suggestion ->
                        val isCurrent = modelName.trim().equals(suggestion.trim(), ignoreCase = true)
                        Surface(
                            color = if (isCurrent) Color(0xFF7C4DFF) else Color(0x22FFFFFF),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isCurrent) Color(0xFF64FFDA) else Color(0x33FFFFFF)
                            ),
                            modifier = Modifier.clickable {
                                modelName = suggestion
                                testResult = null
                            }
                        ) {
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isCurrent) Color.White else Color(0xDDFFFFFF),
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 10.dp)
                            )
                        }
                    }
                }

                // Web Search Grounding switch (for Gemini)
                if (selectedProvider == LlmProvider.GEMINI) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Google Websuche aktivieren",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Ermöglicht dem Companion, bei Fragen zu aktuellen Themen live im Web zu recherchieren.",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xAAFFFFFF))
                            )
                        }
                        Switch(
                            checked = enableWebSearch,
                            onCheckedChange = { enableWebSearch = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF64FFDA),
                                checkedTrackColor = Color(0xFF7C4DFF)
                            )
                        )
                    }
                }

                // Test Connection & Save Action Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            isTesting = true
                            testResult = null
                            onTestLlmConnection(currentDraftSettings()) { success, msg ->
                                isTesting = false
                                testResult = Pair(success, msg)
                            }
                        },
                        enabled = !isTesting,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A284D)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF64FFDA), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Prüfe...", color = Color.White, fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF64FFDA), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Verbindung testen", color = Color.White, fontSize = 12.sp)
                        }
                    }

                    Button(
                        onClick = {
                            onSaveLlmSettings(currentDraftSettings())
                            showSavedNotice = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Speichern", color = Color.White, fontSize = 12.sp)
                    }
                }

                // Test Result Feedback Banner
                if (testResult != null) {
                    val (success, message) = testResult!!
                    Surface(
                        color = if (success) Color(0x3300E676) else Color(0x33FF5252),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (success) Color(0xFF00E676) else Color(0xFFFF5252)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (success) Color(0xFF00E676) else Color(0xFFFF5252),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (success) "Verbindung erfolgreich!" else "Verbindung fehlgeschlagen:",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (success) Color(0xFF00E676) else Color(0xFFFF8A80)
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontSize = 12.sp)
                                )
                            }
                        }
                    }
                }

                if (showSavedNotice) {
                    Surface(
                        color = Color(0x4400E676),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✅ Einstellungen gespeichert und aktiv!",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF69F0AE),
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Idle-Timeout Konfiguration
        Text(
            text = "Inaktivitäts-Erkennung (Idle-Modus)",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
        )
        Text(
            text = "Nach wie vielen Minuten ohne Eingabe soll der Companion automatisch in eine Lese- oder Smartphone-Pose wechseln?",
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

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onClearChat,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0x44FF5252)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFFF8A80))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Aktuellen Chatverlauf leeren", color = Color(0xFFFF8A80))
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
