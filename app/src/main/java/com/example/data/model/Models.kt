package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "screenplay_projects")
data class ScreenplayProject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val logline: String = "",
    val author: String = "David Miller",
    val basedOn: String = "Original Screenplay",
    val contactInfo: String = "contact@cineasthub.studio",
    val draftName: String = "First Draft",
    val draftColor: String = "White", // White, Blue, Pink, Yellow, Green
    val genre: String = "Sci-Fi Noir",
    val targetPages: Int = 110,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class ScreenplayElementType(val label: String, val shortCode: String) {
    SCENE_HEADING("Scene Heading", "SCENE"),
    ACTION("Action Description", "ACTION"),
    CHARACTER("Character Cue", "CHAR"),
    DIALOGUE("Dialogue", "DIALOG"),
    PARENTHETICAL("Parenthetical", "PAREN"),
    TRANSITION("Transition", "TRANS"),
    SHOT("Shot / Subheader", "SHOT")
}

@Entity(tableName = "screenplay_elements")
data class ScreenplayElement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val orderIndex: Int,
    val elementType: String, // from ScreenplayElementType
    val content: String,
    val sceneNumber: Int? = null,
    val revisionNote: String? = null,
    val hasComment: Boolean = false
)

@Entity(tableName = "storyboard_shots")
data class StoryboardShot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val sceneNumber: String, // e.g. "Scene 1"
    val shotNumber: String,  // e.g. "1A", "1B"
    val shotType: String,    // "Extreme Wide Shot (EWS)", "Wide Shot (WS)", "Close-Up (CU)", etc.
    val cameraMovement: String, // "Static", "Pan Left", "Dolly In", "Handheld"
    val lens: String,        // "24mm Anamorphic", "35mm Cine", "50mm Prime", "85mm Portrait"
    val actionSummary: String,
    val dialogueSnippet: String = "",
    val imageResName: String? = null,
    val orderIndex: Int = 0,
    val imageUri: String? = null,              // File path or content URI of uploaded/generated image
    val linkedSceneId: Long? = null,          // ScreenplayElement.id of the linked script scene heading
    val linkedSceneHeading: String? = null,    // e.g. "INT. RUNNER'S APARTMENT - NIGHT"
    val aiPromptUsed: String? = null,         // Gemini prompt used for generation
    val imageSourceType: String = "NONE"      // "UPLOADED", "GEMINI_GENERATED", "PRESET_ASSET", "NONE"
)

@Entity(tableName = "character_profiles")
data class CharacterProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    val role: String, // "Protagonist", "Antagonist", "Deuteragonist", "Mentor", "Supporting"
    val tagline: String,
    val arcWant: String,
    val arcNeed: String,
    val traits: String,
    val dialogueVoice: String,
    val castingSuggestion: String,
    val scenesCount: Int = 1,
    val avatarColorHex: String = "#F59E0B"
)

@Entity(tableName = "collaboration_comments")
data class CollaborationComment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val elementId: Long? = null,
    val authorName: String,
    val authorRole: String, // "Story Editor", "Director", "Producer", "Co-Writer"
    val avatarInitials: String,
    val content: String,
    val status: String = "OPEN", // "OPEN", "RESOLVED", "URGENT"
    val timestamp: Long = System.currentTimeMillis()
)

data class Collaborator(
    val name: String,
    val role: String,
    val status: String,
    val isOnline: Boolean,
    val currentScene: String,
    val colorHex: String
)
