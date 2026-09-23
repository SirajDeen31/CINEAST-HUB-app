package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CharacterProfile
import com.example.ui.theme.CineastCyan
import com.example.ui.theme.CineastGold
import com.example.ui.theme.CineastGoldBright
import com.example.ui.theme.CineastGreen
import com.example.ui.theme.CineastPurple

@Composable
fun CharactersScreen(
    characters: List<CharacterProfile>,
    onAddCharacter: (CharacterProfile) -> Unit,
    onUpdateCharacter: (CharacterProfile) -> Unit,
    onDeleteCharacter: (CharacterProfile) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCharacter by remember { mutableStateOf<CharacterProfile?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = CineastGold,
                contentColor = Color.Black,
                modifier = Modifier.testTag("add_character_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Character")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header stats
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            tint = CineastGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CHARACTER BIBLE & NOTES",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = "${characters.size} Profiles",
                        fontSize = 12.sp,
                        color = CineastGold,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (characters.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.TheaterComedy,
                            contentDescription = null,
                            tint = CineastGold,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Characters Defined Yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Build arcs, motivations (Want vs Need), dialogue cadence, and casting guides.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CineastGold)
                        ) {
                            Text("Create Character Profile", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(characters, key = { it.id }) { character ->
                        CharacterDossierCard(
                            character = character,
                            onEdit = { editingCharacter = character },
                            onDelete = { onDeleteCharacter(character) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditCharacterDialog(
            character = null,
            onDismiss = { showAddDialog = false },
            onSave = { newChar ->
                onAddCharacter(newChar)
                showAddDialog = false
            }
        )
    }

    editingCharacter?.let { char ->
        AddEditCharacterDialog(
            character = char,
            onDismiss = { editingCharacter = null },
            onSave = { updated ->
                onUpdateCharacter(updated)
                editingCharacter = null
            }
        )
    }
}

@Composable
fun CharacterDossierCard(
    character: CharacterProfile,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val roleColor = when (character.role) {
        "Protagonist" -> CineastGold
        "Antagonist", "Antagonist / Catalyst" -> CineastCyan
        "Mentor", "Mentor / Foil" -> CineastPurple
        else -> CineastGreen
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("character_card_${character.name.lowercase().replace(" ", "_")}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Header: Avatar + Name + Role Badge + Edit/Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Monogram avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(roleColor.copy(alpha = 0.2f))
                        .border(1.5.dp, roleColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = character.name.split(" ").map { it.take(1) }.joinToString(""),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = roleColor
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = character.name.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = roleColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = character.role.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = roleColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Character",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Character",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tagline / Essence
            Text(
                text = character.tagline,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Arc Breakdown: Want vs Need (The Golden Rule of Screenwriting)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = CineastGold,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PSYCHOLOGICAL ARC",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CineastGold,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "External Want: ",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = character.arcWant,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Internal Need: ",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CineastGoldBright
                        )
                        Text(
                            text = character.arcNeed,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dialogue Voice Cadence
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = CineastCyan,
                    modifier = Modifier.size(15.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "DIALOGUE CADENCE & VOICE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CineastCyan,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = character.dialogueVoice,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Casting Suggestion
            if (character.castingSuggestion.isNotBlank()) {
                Text(
                    text = "Casting Archetype: ${character.castingSuggestion}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AddEditCharacterDialog(
    character: CharacterProfile?,
    onDismiss: () -> Unit,
    onSave: (CharacterProfile) -> Unit
) {
    var name by remember { mutableStateOf(character?.name ?: "") }
    var selectedRole by remember { mutableStateOf(character?.role ?: "Protagonist") }
    var tagline by remember { mutableStateOf(character?.tagline ?: "") }
    var arcWant by remember { mutableStateOf(character?.arcWant ?: "") }
    var arcNeed by remember { mutableStateOf(character?.arcNeed ?: "") }
    var dialogueVoice by remember { mutableStateOf(character?.dialogueVoice ?: "") }
    var castingSuggestion by remember { mutableStateOf(character?.castingSuggestion ?: "") }

    val roles = listOf("Protagonist", "Antagonist", "Deuteragonist", "Mentor", "Foil", "Supporting")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (character == null) "New Character Profile" else "Edit Character Profile",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Character Name") },
                        placeholder = { Text("e.g. JAXON VANCE") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Text("Dramatic Role:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        roles.forEach { r ->
                            FilterChip(
                                selected = selectedRole == r,
                                onClick = { selectedRole = r },
                                label = { Text(r, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CineastGold,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = tagline,
                        onValueChange = { tagline = it },
                        label = { Text("Logline / Archetype Essence") },
                        placeholder = { Text("e.g. A cynical detective haunted by erased memories.") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }

                item {
                    OutlinedTextField(
                        value = arcWant,
                        onValueChange = { arcWant = it },
                        label = { Text("External Want (The Goal)") },
                        placeholder = { Text("What they think they are chasing...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = arcNeed,
                        onValueChange = { arcNeed = it },
                        label = { Text("Internal Need (Transformation)") },
                        placeholder = { Text("The emotional truth they must learn...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = dialogueVoice,
                        onValueChange = { dialogueVoice = it },
                        label = { Text("Dialogue Voice Cadence") },
                        placeholder = { Text("e.g. Speaks in dry, laconic sentences with technical slang.") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }

                item {
                    OutlinedTextField(
                        value = castingSuggestion,
                        onValueChange = { castingSuggestion = it },
                        label = { Text("Casting Suggestion Archetype") },
                        placeholder = { Text("e.g. Oscar Isaac / Sterling K. Brown") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val profile = (character ?: CharacterProfile(
                            projectId = 0,
                            name = name.trim().uppercase(),
                            role = selectedRole,
                            tagline = tagline.trim(),
                            arcWant = arcWant.trim(),
                            arcNeed = arcNeed.trim(),
                            traits = "",
                            dialogueVoice = dialogueVoice.trim(),
                            castingSuggestion = castingSuggestion.trim()
                        )).copy(
                            name = name.trim().uppercase(),
                            role = selectedRole,
                            tagline = tagline.trim(),
                            arcWant = arcWant.trim(),
                            arcNeed = arcNeed.trim(),
                            dialogueVoice = dialogueVoice.trim(),
                            castingSuggestion = castingSuggestion.trim()
                        )
                        onSave(profile)
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CineastGold),
                modifier = Modifier.testTag("dialog_save_character")
            ) {
                Text("Save Profile", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
