package com.example.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.local.entity.CompanionBackgroundEntity

@Composable
fun BackgroundsDialog(
    backgrounds: List<CompanionBackgroundEntity>,
    currentBackground: CompanionBackgroundEntity?,
    onSelectBackground: (CompanionBackgroundEntity) -> Unit,
    onAddBackground: (name: String, uri: String, tags: String, timeSlot: String) -> Unit,
    onDeleteBackground: (CompanionBackgroundEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xF213112B),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FFFFFF)),
            modifier = Modifier
                .fillMaxWidth()
                .height(580.dp)
                .testTag("backgrounds_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "Hintergründe & Orte",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "Wähle oder lade neue Umgebungen hoch",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xAAFFFFFF))
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Backgrounds List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(backgrounds) { bg ->
                        val isSelected = currentBackground?.id == bg.id
                        val drawableId = remember(bg.drawableResName) {
                            if (bg.drawableResName != null) {
                                context.resources.getIdentifier(bg.drawableResName, "drawable", context.packageName)
                            } else 0
                        }

                        Surface(
                            color = if (isSelected) Color(0x447C4DFF) else Color(0x22FFFFFF),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) Color(0xFF64FFDA) else Color(0x22FFFFFF)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectBackground(bg) }
                                .testTag("background_item_${bg.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                // Thumbnail
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF201B42))
                                ) {
                                    if (!bg.customUri.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context).data(bg.customUri).build(),
                                            contentDescription = bg.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        androidx.compose.foundation.Image(
                                            painter = androidx.compose.ui.res.painterResource(
                                                id = if (drawableId != 0) drawableId else R.drawable.img_bg_kitchen
                                            ),
                                            contentDescription = bg.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bg.name,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "🏷️ ${bg.tags}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xCC64FFDA),
                                            fontSize = 11.sp
                                        )
                                    )
                                    Text(
                                        text = "Tageszeit: ${bg.timeSlot}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0x88FFFFFF),
                                            fontSize = 10.sp
                                        )
                                    )
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF64FFDA)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Aktiv",
                                            tint = Color(0xFF100E26),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else if (bg.isCustom) {
                                    IconButton(onClick = { onDeleteBackground(bg) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Löschen",
                                            tint = Color(0xFFFF5252)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Add Custom Background Button
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_custom_background_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Neuen Hintergrund hinzufügen", color = Color.White)
                }
            }
        }
    }

    if (showAddDialog) {
        AddBackgroundSubDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, uri, tags, slot ->
                onAddBackground(name, uri, tags, slot)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun AddBackgroundSubDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, uri: String, tags: String, slot: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("Wohnzimmer, Gemütlich, Abend") }
    var selectedSlot by remember { mutableStateOf("ANY") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> selectedImageUri = uri }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neuen Ort/Hintergrund anlegen", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F3D6B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (selectedImageUri != null) "Bild ausgewählt ✓" else "Bild aus Galerie wählen")
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (z.B. Dachterrasse, Café)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Stichworte/Tags (2-3 Beschreibungen)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val uriStr = selectedImageUri?.toString() ?: ""
                        onSave(name, uriStr, tags, selectedSlot)
                    }
                },
                enabled = name.isNotBlank()
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
