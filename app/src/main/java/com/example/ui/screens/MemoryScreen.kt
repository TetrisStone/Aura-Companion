package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CompanionMemoryEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    companionName: String,
    memories: List<CompanionMemoryEntity>,
    onAddMemory: (title: String, note: String, category: String) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Schlankes Gedächtnis",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "Was $companionName über dich und frühere Tage weiß",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xAAFFFFFF))
                        )
                    }
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
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("add_memory_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Notiz hinzufügen", tint = Color(0xFF64FFDA))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF100E26))
            )
        },
        containerColor = Color(0xFF0C0A1E),
        modifier = Modifier.testTag("memory_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Explanation Banner
            Surface(
                color = Color(0x337C4DFF),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x557C4DFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        tint = Color(0xFFB388FF),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "$companionName speichert automatisch wichtige Vorlieben, Termine und Erlebnisse aus deinen Chats und nutzt diese bei Begrüßungen am nächsten Tag.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xEEFFFFFF),
                            lineHeight = 18.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (memories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Noch keine Notizen im Gedächtnis gespeichert.\nErzähle $companionName etwas über dich!",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0x88FFFFFF),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(memories) { memory ->
                        MemoryItemCard(
                            memory = memory,
                            onDelete = { onDeleteMemory(memory.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddMemoryDialog(
            onDismiss = { showAddDialog = false },
            onSave = { title, note, cat ->
                onAddMemory(title, note, cat)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun MemoryItemCard(
    memory: CompanionMemoryEntity,
    onDelete: () -> Unit
) {
    val categoryColor = when (memory.category.uppercase()) {
        "VORLIEBE" -> Color(0xFFFFD54F)
        "TERMIN" -> Color(0xFFFF80AB)
        "INTERESSE" -> Color(0xFF64FFDA)
        "PROJEKT" -> Color(0xFF82B1FF)
        else -> Color(0xFFB388FF)
    }

    Surface(
        color = Color(0x2A201C46),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = categoryColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = memory.category,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = categoryColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (memory.recordedDate.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• ${memory.recordedDate}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0x88FFFFFF),
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = memory.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = memory.note,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xDDFFFFFF),
                        lineHeight = 19.sp
                    )
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Löschen",
                    tint = Color(0x88FF5252),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, note: String, category: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("VORLIEBE") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gedächtniseintrag hinzufügen", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titel (z.B. Kaffee-Vorliebe, Klausur)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Details / Notiz") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("VORLIEBE", "TERMIN", "INTERESSE", "PROJEKT").forEach { cat ->
                        Surface(
                            color = if (category == cat) Color(0xFF7C4DFF) else Color(0x22FFFFFF),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { category = cat }
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && note.isNotBlank()) {
                        onSave(title, note, category)
                    }
                },
                enabled = title.isNotBlank() && note.isNotBlank()
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)) {
                Text("Abbrechen", color = Color.White)
            }
        },
        containerColor = Color(0xFF1E1A38)
    )
}
