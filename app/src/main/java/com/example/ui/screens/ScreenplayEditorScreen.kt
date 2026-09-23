package com.example.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CharacterProfile
import com.example.data.model.ScreenplayElement
import com.example.data.model.ScreenplayElementType
import com.example.data.model.ScreenplayProject
import com.example.ui.components.ScreenplayEditorComponent
import com.example.ui.theme.CineastGold
import com.example.ui.theme.CineastGoldBright
import com.example.ui.theme.CineastGreen
import com.example.ui.theme.ScriptCourierTextLight
import com.example.ui.theme.ScriptPaperDark
import java.util.Locale

@Composable
fun ScreenplayEditorScreen(
    project: ScreenplayProject?,
    elements: List<ScreenplayElement>,
    characters: List<CharacterProfile>,
    onAddElement: (ScreenplayElementType, String, Int?) -> Unit,
    onUpdateElement: (ScreenplayElement) -> Unit,
    onDeleteElement: (Long) -> Unit,
    onAddComment: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedElementTypeForAdd by remember { mutableStateOf(ScreenplayElementType.ACTION) }
    var editingElement by remember { mutableStateOf<ScreenplayElement?>(null) }
    var commentingElement by remember { mutableStateOf<ScreenplayElement?>(null) }

    // Analytics derived from current script
    val sceneCount by remember(elements) {
        derivedStateOf { elements.count { it.elementType == ScreenplayElementType.SCENE_HEADING.name } }
    }
    val wordCount by remember(elements) {
        derivedStateOf {
            elements.sumOf { elem ->
                elem.content.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
            }
        }
    }
    val pageCountEstimate by remember(wordCount) {
        derivedStateOf {
            // Industry standard: ~250 words per screenplay page
            val pages = (wordCount / 250f)
            if (pages < 1f) "1 Page" else "%.1f Pages".format(Locale.US, pages)
        }
    }
    val screenTimeEstimate by remember(wordCount) {
        derivedStateOf {
            // ~1 page = 1 minute screen time
            val minutes = (wordCount / 250f)
            val totalSeconds = (minutes * 60).toInt()
            val m = totalSeconds / 60
            val s = totalSeconds % 60
            "${m}m ${s}s"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- Live Industry Screenplay Header Stats Ribbon ---
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = CineastGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Est. Runtime: $screenTimeEstimate",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "$sceneCount Scenes",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$wordCount Words",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = pageCountEstimate,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CineastGold
                    )
                }
            }
        }

        // --- Industry-Standard Screenplay Editor Component ---
        ScreenplayEditorComponent(
            project = project,
            elements = elements,
            characters = characters,
            onAddElement = onAddElement,
            onUpdateElement = onUpdateElement,
            onDeleteElement = onDeleteElement,
            onElementComment = { commentingElement = it },
            modifier = Modifier.weight(1f)
        )
    }

    // --- Dialog: Add New Screenplay Element ---
    if (showAddDialog) {
        AddScreenplayElementDialog(
            initialType = selectedElementTypeForAdd,
            characterSuggestions = characters.map { it.name },
            onDismiss = { showAddDialog = false },
            onConfirm = { type, content, sceneNum ->
                onAddElement(type, content, sceneNum)
                showAddDialog = false
            }
        )
    }

    // --- Dialog: Edit Element ---
    editingElement?.let { elem ->
        EditScreenplayElementDialog(
            element = elem,
            onDismiss = { editingElement = null },
            onSave = { updated ->
                onUpdateElement(updated)
                editingElement = null
            },
            onDelete = {
                onDeleteElement(elem.id)
                editingElement = null
            }
        )
    }

    // --- Dialog: Add Collaborative Note/Comment to Element ---
    commentingElement?.let { elem ->
        AddElementCommentDialog(
            element = elem,
            onDismiss = { commentingElement = null },
            onAddComment = { commentText ->
                onAddComment("Note on ${elem.elementType}: \"$commentText\"")
                commentingElement = null
            }
        )
    }
}

@Composable
fun ScreenplayElementItem(
    element: ScreenplayElement,
    onClick: () -> Unit,
    onCommentClick: () -> Unit
) {
    val type = ScreenplayElementType.values().find { it.name == element.elementType }
        ?: ScreenplayElementType.ACTION

    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 3.dp)
            .testTag("element_${element.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            when (type) {
                ScreenplayElementType.SCENE_HEADING -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${element.sceneNumber ?: 1}.",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = CineastGold,
                            modifier = Modifier.width(28.dp)
                        )
                        Text(
                            text = element.content.uppercase(),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onCommentClick,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Comment,
                                contentDescription = "Add Scene Note",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                ScreenplayElementType.ACTION -> {
                    Text(
                        text = element.content,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = ScriptCourierTextLight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                ScreenplayElementType.CHARACTER -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = element.content.uppercase(),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = CineastGoldBright,
                            letterSpacing = 1.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                ScreenplayElementType.PARENTHETICAL -> {
                    val formatted = if (element.content.startsWith("(") && element.content.endsWith(")")) {
                        element.content
                    } else {
                        "(${element.content.trim()})"
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = formatted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                ScreenplayElementType.DIALOGUE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = element.content,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                ScreenplayElementType.TRANSITION -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = element.content.uppercase(),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = CineastGold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                ScreenplayElementType.SHOT -> {
                    Text(
                        text = element.content.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = CineastGold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AddScreenplayElementDialog(
    initialType: ScreenplayElementType,
    characterSuggestions: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (ScreenplayElementType, String, Int?) -> Unit
) {
    var selectedType by remember { mutableStateOf(initialType) }
    var textContent by remember { mutableStateOf("") }
    var sceneNumberText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Insert Screenplay Element",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Type selector chips
                Text(
                    text = "Element Type:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ScreenplayElementType.values().forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = {
                                selectedType = type
                                if (type == ScreenplayElementType.SCENE_HEADING && textContent.isBlank()) {
                                    textContent = "INT. "
                                } else if (type == ScreenplayElementType.TRANSITION && textContent.isBlank()) {
                                    textContent = "CUT TO:"
                                }
                            },
                            label = { Text(type.label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CineastGold,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Slugline Helpers
                if (selectedType == ScreenplayElementType.SCENE_HEADING) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("INT.", "EXT.", "DAY", "NIGHT", "CONTINUOUS").forEach { token ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clickable {
                                        textContent = if (textContent.isBlank()) "$token " else "$textContent $token"
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(token, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CineastGold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Quick Character suggestions
                if (selectedType == ScreenplayElementType.CHARACTER && characterSuggestions.isNotEmpty()) {
                    Text(
                        text = "Cast Cues:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        characterSuggestions.forEach { charName ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clickable { textContent = charName.uppercase() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(charName, fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = textContent,
                    onValueChange = { textContent = it },
                    label = { Text(selectedType.label) },
                    placeholder = {
                        Text(
                            when (selectedType) {
                                ScreenplayElementType.SCENE_HEADING -> "e.g. INT. DETECTIVE OFFICE - NIGHT"
                                ScreenplayElementType.CHARACTER -> "e.g. JAXON (V.O.)"
                                ScreenplayElementType.PARENTHETICAL -> "e.g. (whispering under breath)"
                                ScreenplayElementType.DIALOGUE -> "e.g. The truth isn't something you buy."
                                ScreenplayElementType.TRANSITION -> "e.g. DISSOLVE TO:"
                                else -> "Enter description..."
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("element_input_field"),
                    minLines = if (selectedType == ScreenplayElementType.ACTION || selectedType == ScreenplayElementType.DIALOGUE) 3 else 1,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CineastGold,
                        cursorColor = CineastGold
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (textContent.isNotBlank()) {
                        val parsedScene = sceneNumberText.toIntOrNull()
                        onConfirm(selectedType, textContent.trim(), parsedScene)
                    }
                },
                enabled = textContent.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CineastGold),
                modifier = Modifier.testTag("dialog_confirm_add")
            ) {
                Text("Insert", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditScreenplayElementDialog(
    element: ScreenplayElement,
    onDismiss: () -> Unit,
    onSave: (ScreenplayElement) -> Unit,
    onDelete: () -> Unit
) {
    var content by remember { mutableStateOf(element.content) }
    var selectedType by remember {
        mutableStateOf(
            ScreenplayElementType.values().find { it.name == element.elementType }
                ?: ScreenplayElementType.ACTION
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Element", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Change Element Type:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ScreenplayElementType.values().forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.shortCode, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CineastGold,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CineastGold,
                        cursorColor = CineastGold
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        element.copy(
                            content = content.trim(),
                            elementType = selectedType.name
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CineastGold)
            ) {
                Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Element",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun AddElementCommentDialog(
    element: ScreenplayElement,
    onDismiss: () -> Unit,
    onAddComment: (String) -> Unit
) {
    var noteText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Collaborative Script Note", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Referencing: \"${element.content.take(60)}...\"",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("e.g. Tighten this line for better punch, or check camera angle.") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (noteText.isNotBlank()) onAddComment(noteText.trim()) },
                enabled = noteText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CineastGold)
            ) {
                Text("Post Note", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
