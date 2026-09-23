package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.gemini.CinematicStylePreset
import com.example.data.model.ScreenplayElement
import com.example.data.model.ScreenplayElementType
import com.example.data.model.ScreenplayProject
import com.example.data.model.StoryboardShot
import com.example.ui.theme.CineastCyan
import com.example.ui.theme.CineastGold
import com.example.ui.theme.CineastGoldBright
import com.example.ui.theme.CineastGreen
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun StoryboardScreen(
    project: ScreenplayProject?,
    shots: List<StoryboardShot>,
    elements: List<ScreenplayElement> = emptyList(),
    isGeneratingImage: Boolean = false,
    generationStatus: String? = null,
    onAddShot: (StoryboardShot) -> Unit,
    onUpdateShot: (StoryboardShot) -> Unit,
    onDeleteShot: (StoryboardShot) -> Unit,
    onGenerateImage: (shot: StoryboardShot, prompt: String, stylePreset: CinematicStylePreset, forceOffline: Boolean, onComplete: (StoryboardShot) -> Unit) -> Unit,
    onUploadImage: (shot: StoryboardShot, uri: Uri, onComplete: (StoryboardShot) -> Unit) -> Unit,
    onCraftPrompt: suspend (sceneHeading: String, shotType: String, lens: String, cameraMovement: String, actionSummary: String, dialogue: String, preset: CinematicStylePreset) -> String,
    onNavigateToScriptScene: ((Long) -> Unit)? = null
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingShot by remember { mutableStateOf<StoryboardShot?>(null) }
    var geminiTargetShot by remember { mutableStateOf<StoryboardShot?>(null) }
    var inspectingShot by remember { mutableStateOf<StoryboardShot?>(null) }
    var selectedSceneFilter by remember { mutableStateOf<String?>(null) }

    // Target shot pending photo upload
    var shotPendingUpload by remember { mutableStateOf<StoryboardShot?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri != null && shotPendingUpload != null) {
                onUploadImage(shotPendingUpload!!, uri) { updatedShot ->
                    shotPendingUpload = null
                }
            } else {
                shotPendingUpload = null
            }
        }
    )

    // Extract script scene headings for linking
    val scriptScenes = remember(elements) {
        elements.filter { it.elementType == ScreenplayElementType.SCENE_HEADING.name }
    }

    // Filter scenes for quick tabs
    val sceneList = remember(shots, scriptScenes) {
        val shotScenes = shots.map { it.sceneNumber }
        val scriptSceneNumbers = scriptScenes.map { "Scene ${it.sceneNumber ?: 1}" }
        (shotScenes + scriptSceneNumbers).distinct().filter { it.isNotBlank() }
    }

    val filteredShots = remember(shots, selectedSceneFilter) {
        if (selectedSceneFilter == null) {
            shots
        } else {
            shots.filter { it.sceneNumber.equals(selectedSceneFilter, ignoreCase = true) }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = CineastGold,
                contentColor = Color.Black,
                modifier = Modifier.testTag("add_storyboard_shot_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Storyboard Shot")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header stats & Scene Filter Ribbon
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = CineastGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "VISUAL STORYBOARD DECK",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val visualCount = shots.count { !it.imageUri.isNullOrBlank() || !it.imageResName.isNullOrBlank() }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CineastGold.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CineastGold.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "$visualCount/${shots.size} Visualized",
                                    fontSize = 11.sp,
                                    color = CineastGoldBright,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Progress indicator if Gemini image generation is running
                    if (isGeneratingImage) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E1B4B),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF818CF8)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFA5B4FC)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = generationStatus ?: "Gemini is generating visual frame...",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Scene Filter Chips
                    if (sceneList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedSceneFilter == null,
                                onClick = { selectedSceneFilter = null },
                                label = { Text("All Shots (${shots.size})", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CineastGold,
                                    selectedLabelColor = Color.Black
                                )
                            )
                            sceneList.forEach { scene ->
                                val shotCountForScene = shots.count { it.sceneNumber.equals(scene, ignoreCase = true) }
                                FilterChip(
                                    selected = selectedSceneFilter == scene,
                                    onClick = { selectedSceneFilter = scene },
                                    label = { Text("$scene ($shotCountForScene)", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CineastGold,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (filteredShots.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = CineastGold,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (selectedSceneFilter != null) "No Shots for $selectedSceneFilter" else "No Storyboard Shots Yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Generate visual frames with Gemini AI, upload artwork, and link shots to script scenes.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CineastGold)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create First Shot Card", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(filteredShots, key = { it.id }) { shot ->
                        StoryboardShotCard(
                            shot = shot,
                            onEdit = { editingShot = shot },
                            onDelete = { onDeleteShot(shot) },
                            onOpenGemini = { geminiTargetShot = shot },
                            onUploadPhoto = {
                                shotPendingUpload = shot
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onInspectFrame = { inspectingShot = shot },
                            onNavigateToScene = {
                                if (shot.linkedSceneId != null && onNavigateToScriptScene != null) {
                                    onNavigateToScriptScene(shot.linkedSceneId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Add Shot Dialog
    if (showAddDialog) {
        AddEditStoryboardShotDialog(
            shot = null,
            scriptScenes = scriptScenes,
            allElements = elements,
            onDismiss = { showAddDialog = false },
            onSave = { newShot ->
                onAddShot(newShot)
                showAddDialog = false
            },
            onOpenGeminiForShot = { shotToGenerate ->
                geminiTargetShot = shotToGenerate
            }
        )
    }

    // Edit Shot Dialog
    editingShot?.let { shot ->
        AddEditStoryboardShotDialog(
            shot = shot,
            scriptScenes = scriptScenes,
            allElements = elements,
            onDismiss = { editingShot = null },
            onSave = { updated ->
                onUpdateShot(updated)
                editingShot = null
            },
            onOpenGeminiForShot = { shotToGenerate ->
                geminiTargetShot = shotToGenerate
            }
        )
    }

    // Gemini Image Generation Dialog
    geminiTargetShot?.let { shot ->
        GeminiImageGeneratorDialog(
            shot = shot,
            isGenerating = isGeneratingImage,
            onDismiss = { geminiTargetShot = null },
            onCraftPrompt = { preset ->
                onCraftPrompt(
                    shot.linkedSceneHeading ?: shot.sceneNumber,
                    shot.shotType,
                    shot.lens,
                    shot.cameraMovement,
                    shot.actionSummary,
                    shot.dialogueSnippet,
                    preset
                )
            },
            onGenerate = { prompt, preset, forceOffline ->
                onGenerateImage(shot, prompt, preset, forceOffline) { updated ->
                    geminiTargetShot = null
                }
            }
        )
    }

    // Fullscreen Visual Frame Inspector Dialog
    inspectingShot?.let { shot ->
        StoryboardFrameInspectorDialog(
            shot = shot,
            onDismiss = { inspectingShot = null },
            onEdit = {
                inspectingShot = null
                editingShot = shot
            },
            onOpenGemini = {
                inspectingShot = null
                geminiTargetShot = shot
            },
            onUploadPhoto = {
                inspectingShot = null
                shotPendingUpload = shot
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onNavigateToScriptScene = {
                if (shot.linkedSceneId != null && onNavigateToScriptScene != null) {
                    inspectingShot = null
                    onNavigateToScriptScene(shot.linkedSceneId)
                }
            }
        )
    }
}

@Composable
fun StoryboardShotCard(
    shot: StoryboardShot,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenGemini: () -> Unit,
    onUploadPhoto: () -> Unit,
    onInspectFrame: () -> Unit,
    onNavigateToScene: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("storyboard_card_${shot.shotNumber}")
    ) {
        Column {
            // Visual Frame Preview Box (16:9 ratio)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF0F1117))
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .clickable { onInspectFrame() }
            ) {
                val hasLocalFile = !shot.imageUri.isNullOrBlank() && File(shot.imageUri).exists()
                val context = LocalContext.current
                val drawableId = remember(shot.imageResName) {
                    if (!shot.imageResName.isNullOrBlank()) {
                        context.resources.getIdentifier(shot.imageResName, "drawable", context.packageName)
                    } else 0
                }

                if (hasLocalFile) {
                    AsyncImage(
                        model = File(shot.imageUri!!),
                        contentDescription = "Shot Frame Visual",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (drawableId != 0) {
                    Image(
                        painter = painterResource(id = drawableId),
                        contentDescription = "Shot Frame Visual",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Blueprint Framing Grid with Quick Action Buttons
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = CineastGold.copy(alpha = 0.5f),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "CINEMATIC 16:9 FRAME",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = shot.shotType,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Quick generation & upload buttons
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onOpenGemini,
                                    colors = ButtonDefaults.buttonColors(containerColor = CineastGold),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Gemini AI", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = onUploadPhoto,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Upload,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Upload", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // Top badges overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = "[SHOT ${shot.shotNumber}] • ${shot.sceneNumber.uppercase()}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = CineastGoldBright,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Source Badge (Gemini, Uploaded, Concept Sketch)
                        val sourceLabel = when (shot.imageSourceType) {
                            "GEMINI_GENERATED" -> "✨ Gemini AI"
                            "UPLOADED" -> "📷 Uploaded"
                            "CONCEPT_SKETCH" -> "🎨 Concept"
                            "PRESET_ASSET" -> "🎬 Asset"
                            else -> null
                        }

                        if (sourceLabel != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.Black.copy(alpha = 0.75f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, CineastCyan.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = sourceLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CineastCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Card Actions
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onOpenGemini,
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Generate with Gemini",
                                tint = CineastGold,
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        IconButton(
                            onClick = onUploadPhoto,
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = "Upload Photo",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Shot",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Shot",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // Shot Technical Specs & Action Description
            Column(modifier = Modifier.padding(14.dp)) {
                // Linked Script Scene Banner
                if (!shot.linkedSceneHeading.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToScene() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    tint = CineastGold,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "SCRIPT: ${shot.linkedSceneHeading}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Jump to Script Scene",
                                tint = CineastGold,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Specs Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SpecBadge(label = shot.shotType, color = CineastGold)
                    SpecBadge(label = shot.cameraMovement, color = CineastCyan)
                    SpecBadge(label = shot.lens, color = Color(0xFFA78BFA))
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "ACTION:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = shot.actionSummary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color.White,
                    modifier = Modifier.padding(top = 2.dp)
                )

                if (shot.dialogueSnippet.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Dialogue Cue: \"${shot.dialogueSnippet}\"",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            color = CineastGoldBright,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpecBadge(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditStoryboardShotDialog(
    shot: StoryboardShot?,
    scriptScenes: List<ScreenplayElement>,
    allElements: List<ScreenplayElement>,
    onDismiss: () -> Unit,
    onSave: (StoryboardShot) -> Unit,
    onOpenGeminiForShot: ((StoryboardShot) -> Unit)? = null
) {
    var sceneNumber by remember { mutableStateOf(shot?.sceneNumber ?: "Scene 1") }
    var shotNumber by remember { mutableStateOf(shot?.shotNumber ?: "1A") }
    var selectedShotType by remember { mutableStateOf(shot?.shotType ?: "Wide Shot (WS)") }
    var selectedCameraMovement by remember { mutableStateOf(shot?.cameraMovement ?: "Static") }
    var selectedLens by remember { mutableStateOf(shot?.lens ?: "35mm Cine") }
    var actionSummary by remember { mutableStateOf(shot?.actionSummary ?: "") }
    var dialogueSnippet by remember { mutableStateOf(shot?.dialogueSnippet ?: "") }
    var selectedImageRes by remember { mutableStateOf(shot?.imageResName) }

    var linkedSceneId by remember { mutableStateOf(shot?.linkedSceneId) }
    var linkedSceneHeading by remember { mutableStateOf(shot?.linkedSceneHeading) }

    var sceneDropdownExpanded by remember { mutableStateOf(false) }

    val shotTypes = listOf(
        "Extreme Wide Shot (EWS)",
        "Wide Shot (WS)",
        "Medium Shot (MS)",
        "Close-Up (CU)",
        "Extreme Close-Up (ECU)",
        "Over The Shoulder (OTS)",
        "Point of View (POV)",
        "Dutch Angle"
    )

    val movements = listOf(
        "Static",
        "Pan Left/Right",
        "Tilt Up/Down",
        "Slow Crane Down",
        "Dolly In/Out",
        "Tracking",
        "Handheld Subtle Shake",
        "Steadicam"
    )

    val lenses = listOf(
        "24mm Anamorphic",
        "35mm Cine",
        "50mm Prime",
        "85mm Portrait",
        "100mm Macro"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (shot == null) "New Storyboard Shot Card" else "Edit Shot Card ${shot.shotNumber}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Link to Script Scene Selector
                item {
                    Text(
                        text = "LINK TO SCRIPT SCENE:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CineastGold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (scriptScenes.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = sceneDropdownExpanded,
                            onExpandedChange = { sceneDropdownExpanded = !sceneDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = linkedSceneHeading ?: "Select Script Scene...",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sceneDropdownExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CineastGold,
                                    cursorColor = CineastGold
                                )
                            )

                            ExposedDropdownMenu(
                                expanded = sceneDropdownExpanded,
                                onDismissRequest = { sceneDropdownExpanded = false }
                            ) {
                                scriptScenes.forEach { sceneElem ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = "Scene ${sceneElem.sceneNumber ?: 1}: ${sceneElem.content}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        },
                                        onClick = {
                                            linkedSceneId = sceneElem.id
                                            linkedSceneHeading = sceneElem.content
                                            sceneNumber = "Scene ${sceneElem.sceneNumber ?: 1}"
                                            sceneDropdownExpanded = false

                                            // Auto populate action and dialogue if currently blank
                                            if (actionSummary.isBlank()) {
                                                val sceneIndex = allElements.indexOf(sceneElem)
                                                if (sceneIndex >= 0) {
                                                    val subsequent = allElements.drop(sceneIndex + 1)
                                                        .takeWhile { it.elementType != ScreenplayElementType.SCENE_HEADING.name }
                                                    val actionText = subsequent.firstOrNull { it.elementType == ScreenplayElementType.ACTION.name }?.content
                                                    val dialogueText = subsequent.firstOrNull { it.elementType == ScreenplayElementType.DIALOGUE.name }?.content
                                                    if (!actionText.isNullOrBlank()) {
                                                        actionSummary = actionText
                                                    }
                                                    if (!dialogueText.isNullOrBlank()) {
                                                        dialogueSnippet = dialogueText
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No scenes in script yet. You can still assign custom scene numbers.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = sceneNumber,
                            onValueChange = { sceneNumber = it },
                            label = { Text("Scene") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = shotNumber,
                            onValueChange = { shotNumber = it },
                            label = { Text("Shot #") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Shot Type
                item {
                    Text("Shot Type:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        shotTypes.forEach { type ->
                            FilterChip(
                                selected = selectedShotType == type,
                                onClick = { selectedShotType = type },
                                label = { Text(type, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CineastGold,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }
                }

                // Camera Movement
                item {
                    Text("Camera Movement:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        movements.forEach { move ->
                            FilterChip(
                                selected = selectedCameraMovement == move,
                                onClick = { selectedCameraMovement = move },
                                label = { Text(move, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CineastCyan,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }
                }

                // Lens
                item {
                    Text("Lens Choice:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        lenses.forEach { lens ->
                            FilterChip(
                                selected = selectedLens == lens,
                                onClick = { selectedLens = lens },
                                label = { Text(lens, fontSize = 10.sp) }
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = actionSummary,
                        onValueChange = { actionSummary = it },
                        label = { Text("Action & Framing Description") },
                        placeholder = { Text("Describe what the camera sees and character actions...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CineastGold,
                            cursorColor = CineastGold
                        )
                    )
                }

                item {
                    OutlinedTextField(
                        value = dialogueSnippet,
                        onValueChange = { dialogueSnippet = it },
                        label = { Text("Dialogue Tie-in Cue (Optional)") },
                        placeholder = { Text("e.g. CHARACTER: Key line spoken during shot...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (actionSummary.isNotBlank()) {
                        val base = shot ?: StoryboardShot(
                            projectId = 0,
                            sceneNumber = sceneNumber.trim(),
                            shotNumber = shotNumber.trim(),
                            shotType = selectedShotType,
                            cameraMovement = selectedCameraMovement,
                            lens = selectedLens,
                            actionSummary = actionSummary.trim(),
                            dialogueSnippet = dialogueSnippet.trim(),
                            imageResName = selectedImageRes,
                            linkedSceneId = linkedSceneId,
                            linkedSceneHeading = linkedSceneHeading
                        )
                        val updated = base.copy(
                            sceneNumber = sceneNumber.trim(),
                            shotNumber = shotNumber.trim(),
                            shotType = selectedShotType,
                            cameraMovement = selectedCameraMovement,
                            lens = selectedLens,
                            actionSummary = actionSummary.trim(),
                            dialogueSnippet = dialogueSnippet.trim(),
                            imageResName = selectedImageRes,
                            linkedSceneId = linkedSceneId,
                            linkedSceneHeading = linkedSceneHeading
                        )
                        onSave(updated)
                    }
                },
                enabled = actionSummary.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CineastGold),
                modifier = Modifier.testTag("dialog_save_shot")
            ) {
                Text("Save Shot", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun GeminiImageGeneratorDialog(
    shot: StoryboardShot,
    isGenerating: Boolean,
    onDismiss: () -> Unit,
    onCraftPrompt: suspend (CinematicStylePreset) -> String,
    onGenerate: (prompt: String, preset: CinematicStylePreset, forceOffline: Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedPreset by remember { mutableStateOf(CinematicStylePreset.FILM_NOIR) }
    var promptText by remember {
        mutableStateOf(
            shot.aiPromptUsed ?: "${shot.shotType}, ${shot.lens}. ${shot.actionSummary}"
        )
    }
    var isCraftingPrompt by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = CineastGold,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Gemini AI Visual Studio",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "[SHOT ${shot.shotNumber}] • ${shot.linkedSceneHeading ?: shot.sceneNumber}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Style Preset Selector
                item {
                    Text(
                        text = "CINEMATIC STYLE PRESET:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CineastGold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CinematicStylePreset.values().forEach { preset ->
                            FilterChip(
                                selected = selectedPreset == preset,
                                onClick = { selectedPreset = preset },
                                label = { Text(preset.displayName, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CineastGold,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }
                }

                // Auto-Craft Prompt Action
                item {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isCraftingPrompt = true
                                promptText = onCraftPrompt(selectedPreset)
                                isCraftingPrompt = false
                            }
                        },
                        enabled = !isGenerating && !isCraftingPrompt,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isCraftingPrompt) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Crafting Prompt with Gemini...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = CineastGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("✨ Auto-Craft Prompt from Scene Details", fontSize = 12.sp)
                        }
                    }
                }

                // Prompt Input
                item {
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        label = { Text("Cinematic Visual Prompt") },
                        placeholder = { Text("Lighting, subject, environment, lens mood...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CineastGold,
                            cursorColor = CineastGold
                        )
                    )
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Technical Specs Embedded:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CineastGold
                            )
                            Text(
                                text = "• Ratio: 16:9 Widescreen (1K)\n• Framing: ${shot.shotType}\n• Optics: ${shot.lens} | ${shot.cameraMovement}\n• Scene: ${shot.linkedSceneHeading ?: shot.sceneNumber}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Secondary offline generator option
                OutlinedButton(
                    onClick = {
                        onGenerate(promptText, selectedPreset, true)
                    },
                    enabled = !isGenerating && promptText.isNotBlank()
                ) {
                    Text("Concept Sketch", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        onGenerate(promptText, selectedPreset, false)
                    },
                    enabled = !isGenerating && promptText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CineastGold)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rendering...", color = Color.Black, fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate AI", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        },
        dismissButton = {
            if (!isGenerating) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
fun StoryboardFrameInspectorDialog(
    shot: StoryboardShot,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onOpenGemini: () -> Unit,
    onUploadPhoto: () -> Unit,
    onNavigateToScriptScene: () -> Unit
) {
    val context = LocalContext.current
    val hasLocalFile = !shot.imageUri.isNullOrBlank() && File(shot.imageUri).exists()
    val drawableId = remember(shot.imageResName) {
        if (!shot.imageResName.isNullOrBlank()) {
            context.resources.getIdentifier(shot.imageResName, "drawable", context.packageName)
        } else 0
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "[SHOT ${shot.shotNumber}] • ${shot.sceneNumber}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (!shot.linkedSceneHeading.isNullOrBlank()) {
                        Text(
                            text = shot.linkedSceneHeading,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = CineastGoldBright
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Large 16:9 Image Frame
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    if (hasLocalFile) {
                        AsyncImage(
                            model = File(shot.imageUri!!),
                            contentDescription = "Inspected Frame",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (drawableId != 0) {
                        Image(
                            painter = painterResource(id = drawableId),
                            contentDescription = "Inspected Frame",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No visual frame rendered yet.", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }

                // Specs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SpecBadge(label = shot.shotType, color = CineastGold)
                    SpecBadge(label = shot.cameraMovement, color = CineastCyan)
                    SpecBadge(label = shot.lens, color = Color(0xFFA78BFA))
                }

                Text(
                    text = "ACTION: ${shot.actionSummary}",
                    fontSize = 12.sp,
                    color = Color.White
                )

                if (shot.dialogueSnippet.isNotBlank()) {
                    Text(
                        text = "DIALOGUE: \"${shot.dialogueSnippet}\"",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = CineastGoldBright
                    )
                }

                if (!shot.aiPromptUsed.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Gemini Prompt Used:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CineastGold
                            )
                            Text(
                                text = shot.aiPromptUsed,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onUploadPhoto) {
                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Upload", fontSize = 12.sp)
                }
                Button(
                    onClick = onOpenGemini,
                    colors = ButtonDefaults.buttonColors(containerColor = CineastGold)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Regenerate", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onEdit) {
                Text("Edit Specs")
            }
        }
    )
}
