package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ImportProgress
import com.example.data.local.LocalGgufModel
import com.example.data.local.ModelManager
import com.example.data.local.OnDeviceEngine
import com.example.data.model.AccelerationMode
import com.example.data.model.LlmProvider
import com.example.data.model.LlmSettings
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalModelsScreen(
    llmSettings: LlmSettings,
    onSaveLlmSettings: (LlmSettings) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val modelManager = remember { ModelManager.getInstance(context) }
    val onDeviceEngine = remember { OnDeviceEngine.getInstance(context) }
    val modelsList by modelManager.modelsListFlow.collectAsState()
    val importProgress by modelManager.importProgress.collectAsState()
    val scope = rememberCoroutineScope()

    var activePath by remember(llmSettings) { mutableStateOf(llmSettings.activeLocalModelPath) }
    var accelMode by remember(llmSettings) { mutableStateOf(llmSettings.accelerationMode) }
    var contextLength by remember(llmSettings) { mutableIntStateOf(llmSettings.contextLength) }
    var cpuThreads by remember(llmSettings) { mutableIntStateOf(llmSettings.cpuThreads) }
    var temperature by remember(llmSettings) { mutableFloatStateOf(llmSettings.temperature) }
    var topP by remember(llmSettings) { mutableFloatStateOf(llmSettings.topP) }
    var maxTokens by remember(llmSettings) { mutableIntStateOf(llmSettings.maxTokens) }

    var latestDiagnostics by remember { mutableStateOf(onDeviceEngine.getLastDiagnostics()) }

    // SAF Document Picker (ACTION_OPEN_DOCUMENT) - no broad storage permission required
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val res = modelManager.importModelFromUri(uri)
                if (res.isSuccess) {
                    val imported = res.getOrThrow()
                    activePath = imported.file.absolutePath
                    val updated = llmSettings.copy(
                        provider = LlmProvider.ON_DEVICE,
                        activeLocalModelPath = imported.file.absolutePath,
                        modelName = imported.name,
                        accelerationMode = accelMode,
                        contextLength = contextLength,
                        cpuThreads = cpuThreads,
                        temperature = temperature,
                        topP = topP,
                        maxTokens = maxTokens
                    )
                    onSaveLlmSettings(updated)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Lokale Modelle (GGUF)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "100% Offline via llama.cpp & Adreno GPU",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64FFDA))
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("local_models_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        modelManager.refreshModels()
                        latestDiagnostics = onDeviceEngine.getLastDiagnostics()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF16142E))
            )
        },
        containerColor = Color(0xFF0C0A1E)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // Action: Import GGUF File via SAF
            Button(
                onClick = {
                    modelManager.resetImportProgress()
                    filePickerLauncher.launch(arrayOf("*/*"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("import_model_button")
            ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GGUF-Modell importieren (Dateimanager)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            // Import Progress / Status
            when (val prog = importProgress) {
                is ImportProgress.Copying -> {
                    Surface(
                        color = Color(0x337C4DFF),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Kopiere Modell nach App-Speicher: ${prog.progressPercent}%",
                                style = MaterialTheme.typography.labelMedium.copy(color = Color.White)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { prog.progressPercent / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF64FFDA),
                                trackColor = Color(0x33FFFFFF)
                            )
                        }
                    }
                }
                is ImportProgress.Success -> {
                    Surface(
                        color = Color(0x3300E676),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00E676))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Modell '${prog.model.name}' erfolgreich importiert und aktiviert!",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White)
                            )
                        }
                    }
                }
                is ImportProgress.Error -> {
                    Surface(
                        color = Color(0x33FF5252),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF5252))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = prog.message,
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White)
                            )
                        }
                    }
                }
                ImportProgress.Idle -> Unit
            }

            // List of Imported Models
            Text(
                text = "Importierte Modelle (${modelsList.size})",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )

            if (modelsList.isEmpty()) {
                Surface(
                    color = Color(0x1F2A284D),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Noch keine lokalen GGUF-Modelle vorhanden.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xAAFFFFFF))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tipp: Lade z.B. 'Qwen2.5-7B-Instruct-Q4_K_M.gguf' herunter und importiere die Datei oben.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF80CBC4),
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            } else {
                modelsList.forEach { model ->
                    val isModelActive = activePath == model.file.absolutePath && llmSettings.provider == LlmProvider.ON_DEVICE

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isModelActive) Color(0x337C4DFF) else Color(0x1F2A284D)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isModelActive) Color(0xFF64FFDA) else Color(0x22FFFFFF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isModelActive,
                                onClick = {
                                    activePath = model.file.absolutePath
                                    val updated = llmSettings.copy(
                                        provider = LlmProvider.ON_DEVICE,
                                        activeLocalModelPath = model.file.absolutePath,
                                        modelName = model.name,
                                        accelerationMode = accelMode,
                                        contextLength = contextLength,
                                        cpuThreads = cpuThreads,
                                        temperature = temperature,
                                        topP = topP,
                                        maxTokens = maxTokens
                                    )
                                    onSaveLlmSettings(updated)
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF64FFDA))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = model.sizeFormatted,
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64FFDA))
                                    )
                                    if (model.isHeaderValid) {
                                        Text(
                                            text = "• GGUF Gültig",
                                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF00E676))
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = {
                                modelManager.deleteModel(model.file)
                                if (activePath == model.file.absolutePath) {
                                    activePath = ""
                                    onSaveLlmSettings(llmSettings.copy(activeLocalModelPath = ""))
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = Color(0xFFFF5252))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Hardware Settings (Acceleration, Threads, Context, etc.)
            Surface(
                color = Color(0x221A1838),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3364FFDA)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Hardware- & Inferenz-Parameter",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64FFDA)
                        )
                    )

                    // Beschleunigung: CPU oder GPU (OpenCL)
                    Text(
                        text = "Beschleunigung:",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xCCFFFFFF))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AccelerationMode.entries.forEach { mode ->
                            val isSel = accelMode == mode
                            Surface(
                                color = if (isSel) Color(0xFF7C4DFF) else Color(0x22FFFFFF),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSel) Color(0xFF64FFDA) else Color(0x33FFFFFF)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        accelMode = mode
                                        val updated = llmSettings.copy(
                                            accelerationMode = mode,
                                            activeLocalModelPath = activePath
                                        )
                                        onSaveLlmSettings(updated)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color.White,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                    Text(
                                        text = if (mode == AccelerationMode.GPU_OPENCL) "n_gpu_layers = 99" else "n_gpu_layers = 0",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isSel) Color(0xFF64FFDA) else Color(0xAAFFFFFF),
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // CPU Threads (1..8)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "CPU-Threads: $cpuThreads",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White)
                            )
                            Text(
                                text = "Standard: 6 (Snapdragon 8 Elite)",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xAAFFFFFF))
                            )
                        }
                        Slider(
                            value = cpuThreads.toFloat(),
                            onValueChange = { cpuThreads = it.toInt() },
                            onValueChangeFinished = {
                                onSaveLlmSettings(llmSettings.copy(cpuThreads = cpuThreads))
                            },
                            valueRange = 1f..8f,
                            steps = 6,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF64FFDA),
                                activeTrackColor = Color(0xFF7C4DFF)
                            )
                        )
                    }

                    // Kontextlänge (Standard 4096)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Kontextlänge: $contextLength Tokens",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White)
                            )
                            Text(
                                text = "Standard: 4096",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xAAFFFFFF))
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(2048, 4096, 8192).forEach { ctx ->
                                val isCur = contextLength == ctx
                                Surface(
                                    color = if (isCur) Color(0xFF7C4DFF) else Color(0x22FFFFFF),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isCur) Color(0xFF64FFDA) else Color(0x33FFFFFF)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            contextLength = ctx
                                            onSaveLlmSettings(llmSettings.copy(contextLength = ctx))
                                        }
                                ) {
                                    Text(
                                        text = "$ctx",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = Color.White,
                                            fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Temperatur & Top-P
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Temperatur: ${String.format(java.util.Locale.US, "%.2f", temperature)}",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White)
                            )
                            Slider(
                                value = temperature,
                                onValueChange = { temperature = it },
                                onValueChangeFinished = {
                                    onSaveLlmSettings(llmSettings.copy(temperature = temperature))
                                },
                                valueRange = 0.0f..1.5f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF64FFDA),
                                    activeTrackColor = Color(0xFF7C4DFF)
                                )
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Top-P: ${String.format(java.util.Locale.US, "%.2f", topP)}",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White)
                            )
                            Slider(
                                value = topP,
                                onValueChange = { topP = it },
                                onValueChangeFinished = {
                                    onSaveLlmSettings(llmSettings.copy(topP = topP))
                                },
                                valueRange = 0.1f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF64FFDA),
                                    activeTrackColor = Color(0xFF7C4DFF)
                                )
                            )
                        }
                    }
                }
            }

            // Diagnose: Letzte Messung
            Surface(
                color = Color(0x1F2A284D),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = Color(0xFF64FFDA), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Letzte Messung & Diagnose",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (latestDiagnostics.isNotBlank()) latestDiagnostics else "Noch keine Inferenz ausgeführt.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xDDFFFFFF),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
