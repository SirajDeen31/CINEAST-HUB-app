package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.gemini.CinematicStylePreset
import com.example.data.gemini.StoryboardGeminiService
import com.example.data.local.AppDatabase
import com.example.data.model.CharacterProfile
import com.example.data.model.CollaborationComment
import com.example.data.model.Collaborator
import com.example.data.model.ScreenplayElement
import com.example.data.model.ScreenplayElementType
import com.example.data.model.ScreenplayProject
import com.example.data.model.StoryboardShot
import com.example.data.repository.ScreenplayRepository
import com.example.data.sync.ScreenplaySyncService
import com.example.data.sync.SyncStatus
import com.example.export.FirestorePdfResult
import com.example.export.FirestoreScreenplayPdfService
import com.example.export.ScreenplayPdfConfig
import com.example.export.ScreenplayPdfExporter
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class CineastTab(val title: String) {
    SCREENPLAY("Screenplay"),
    STORYBOARD("Storyboard"),
    CHARACTERS("Characters"),
    COLLAB("Collab Hub"),
    FORMAT_TOOLS("Format Tools"),
    EXPORT("Export PDF")
}

sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
    data class SharePdf(val intent: Intent, val file: File) : UiEvent()
}

class ScreenplayViewModel(application: Application) : AndroidViewModel(application) {

    private val syncService: ScreenplaySyncService
    private val repository: ScreenplayRepository
    private val firestorePdfService: FirestoreScreenplayPdfService
    val geminiService: StoryboardGeminiService

    init {
        val db = AppDatabase.getDatabase(application, viewModelScope)
        val dao = db.screenplayDao()
        syncService = ScreenplaySyncService(application, dao)
        repository = ScreenplayRepository(dao, syncService)
        firestorePdfService = FirestoreScreenplayPdfService(application, dao)
        geminiService = StoryboardGeminiService(application)
    }

    val syncStatus: StateFlow<SyncStatus> = syncService.syncStatus

    private val _currentTab = MutableStateFlow(CineastTab.SCREENPLAY)
    val currentTab: StateFlow<CineastTab> = _currentTab.asStateFlow()

    val isGeneratingStoryboardImage = MutableStateFlow(false)
    val storyboardGenerationStatus = MutableStateFlow<String?>(null)
    val targetScrollSceneId = MutableStateFlow<Long?>(null)

    fun navigateToSceneInScript(sceneId: Long) {
        targetScrollSceneId.value = sceneId
        _currentTab.value = CineastTab.SCREENPLAY
    }

    fun clearTargetScrollScene() {
        targetScrollSceneId.value = null
    }

    private val _selectedProjectId = MutableStateFlow<Long?>(null)
    val selectedProjectId: StateFlow<Long?> = _selectedProjectId.asStateFlow()

    val allProjects: StateFlow<List<ScreenplayProject>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCollaborators: StateFlow<List<Collaborator>> = repository.activeCollaborators
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    // Export configuration states
    val includeTitlePage = MutableStateFlow(true)
    val includeSceneNumbers = MutableStateFlow(true)
    val watermarkText = MutableStateFlow("")
    val isExporting = MutableStateFlow(false)
    val exportStatusMessage = MutableStateFlow<String?>(null)
    val lastExportedFile = MutableStateFlow<File?>(null)
    val lastExportSource = MutableStateFlow<String?>("Cloud Firestore")
    val lastExportPageCount = MutableStateFlow<Int?>(null)

    // Elements for active project
    val elements: StateFlow<List<ScreenplayElement>> = _selectedProjectId.flatMapLatest { id ->
        if (id != null) repository.getElementsByProject(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Storyboard shots for active project
    val storyboards: StateFlow<List<StoryboardShot>> = _selectedProjectId.flatMapLatest { id ->
        if (id != null) repository.getStoryboardsByProject(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Characters for active project
    val characters: StateFlow<List<CharacterProfile>> = _selectedProjectId.flatMapLatest { id ->
        if (id != null) repository.getCharactersByProject(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Collaboration comments
    val comments: StateFlow<List<CollaborationComment>> = _selectedProjectId.flatMapLatest { id ->
        if (id != null) repository.getCommentsByProject(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Current active project
    val currentProject: StateFlow<ScreenplayProject?> = _selectedProjectId.flatMapLatest { id ->
        if (id != null) repository.getProject(id) else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // Auto-select first project when projects flow emits
        viewModelScope.launch {
            repository.allProjects.collect { list ->
                if (_selectedProjectId.value == null && list.isNotEmpty()) {
                    _selectedProjectId.value = list.first().id
                }
            }
        }

        // Start real-time Firestore sync whenever active project changes
        viewModelScope.launch {
            _selectedProjectId.collect { id ->
                if (id != null) {
                    syncService.startRealtimeSync(id, viewModelScope)
                    // Initial push ensures local and cloud are seeded together
                    syncService.fullPushToCloud(id)
                } else {
                    syncService.stopRealtimeSync()
                }
            }
        }
    }

    fun selectTab(tab: CineastTab) {
        _currentTab.value = tab
    }

    fun selectProject(projectId: Long) {
        _selectedProjectId.value = projectId
    }

    fun triggerCloudSync() {
        val projectId = _selectedProjectId.value ?: return
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowToast("Syncing with Firestore cloud..."))
            val success = syncService.fullPushToCloud(projectId)
            if (success) {
                _uiEvents.emit(UiEvent.ShowToast("Cloud synchronization complete!"))
            } else {
                _uiEvents.emit(UiEvent.ShowToast("Cloud sync failed (check connection)"))
            }
        }
    }

    fun broadcastPresence(status: String) {
        val projectId = _selectedProjectId.value ?: return
        val currentWriter = Collaborator(
            name = "David Miller",
            role = "Lead Screenwriter (You)",
            status = status,
            isOnline = true,
            currentScene = "Live Beat",
            colorHex = "#F59E0B"
        )
        viewModelScope.launch {
            syncService.broadcastPresence(projectId, currentWriter)
        }
    }

    // --- Screenplay Elements Operations ---
    fun addElement(
        type: ScreenplayElementType,
        content: String,
        sceneNumber: Int? = null
    ) {
        val projectId = _selectedProjectId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val currentElements = repository.getElementsByProjectOnce(projectId)
            val nextIndex = currentElements.size
            val finalSceneNum = if (type == ScreenplayElementType.SCENE_HEADING) {
                sceneNumber ?: (currentElements.count { it.elementType == ScreenplayElementType.SCENE_HEADING.name } + 1)
            } else null

            val newElement = ScreenplayElement(
                projectId = projectId,
                orderIndex = nextIndex,
                elementType = type.name,
                content = content,
                sceneNumber = finalSceneNum
            )
            repository.insertElement(newElement)
            _uiEvents.emit(UiEvent.ShowToast("Added ${type.label}"))
        }
    }

    fun updateElement(element: ScreenplayElement) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateElement(element)
        }
    }

    fun deleteElement(elementId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteElementById(elementId)
            _uiEvents.emit(UiEvent.ShowToast("Script element removed"))
        }
    }

    // --- Storyboard Operations ---
    fun addStoryboardShot(shot: StoryboardShot) {
        val projectId = _selectedProjectId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = repository.getStoryboardsByProjectOnce(projectId)
            val newShot = shot.copy(
                projectId = projectId,
                orderIndex = existing.size
            )
            repository.insertStoryboard(newShot)
            _uiEvents.emit(UiEvent.ShowToast("Shot ${newShot.shotNumber} added to Storyboard"))
        }
    }

    fun updateStoryboardShot(shot: StoryboardShot) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateStoryboard(shot)
            _uiEvents.emit(UiEvent.ShowToast("Updated Shot ${shot.shotNumber}"))
        }
    }

    fun deleteStoryboardShot(shot: StoryboardShot) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteStoryboard(shot)
            _uiEvents.emit(UiEvent.ShowToast("Deleted Shot ${shot.shotNumber}"))
        }
    }

    fun generateStoryboardImageForShot(
        shot: StoryboardShot,
        prompt: String,
        stylePreset: CinematicStylePreset = CinematicStylePreset.FILM_NOIR,
        forceOfflineFallback: Boolean = false,
        onComplete: ((StoryboardShot) -> Unit)? = null
    ) {
        viewModelScope.launch {
            isGeneratingStoryboardImage.value = true
            storyboardGenerationStatus.value = "Visualizing frame with Gemini..."
            try {
                val result = geminiService.generateStoryboardImage(
                    prompt = prompt,
                    sceneHeading = shot.linkedSceneHeading ?: shot.sceneNumber,
                    shotType = shot.shotType,
                    lens = shot.lens,
                    cameraMovement = shot.cameraMovement,
                    stylePreset = stylePreset,
                    forceOfflineFallback = forceOfflineFallback
                )

                result.onSuccess { (filePath, info) ->
                    val sourceType = if (info.contains("Gemini", ignoreCase = true)) {
                        "GEMINI_GENERATED"
                    } else {
                        "CONCEPT_SKETCH"
                    }
                    val updatedShot = shot.copy(
                        imageUri = filePath,
                        aiPromptUsed = prompt,
                        imageSourceType = sourceType
                    )
                    if (shot.id != 0L) {
                        repository.updateStoryboard(updatedShot)
                    }
                    _uiEvents.emit(UiEvent.ShowToast(info))
                    onComplete?.invoke(updatedShot)
                }.onFailure { err ->
                    _uiEvents.emit(UiEvent.ShowToast("Generation failed: ${err.localizedMessage ?: "Unknown error"}"))
                }
            } finally {
                isGeneratingStoryboardImage.value = false
                storyboardGenerationStatus.value = null
            }
        }
    }

    fun saveUploadedImageForShot(
        shot: StoryboardShot,
        uri: Uri,
        onComplete: ((StoryboardShot) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = geminiService.saveUploadedImage(uri)
            result.onSuccess { filePath ->
                val updatedShot = shot.copy(
                    imageUri = filePath,
                    imageSourceType = "UPLOADED"
                )
                if (shot.id != 0L) {
                    repository.updateStoryboard(updatedShot)
                }
                _uiEvents.emit(UiEvent.ShowToast("Attached image to Shot ${shot.shotNumber}"))
                onComplete?.invoke(updatedShot)
            }.onFailure { err ->
                _uiEvents.emit(UiEvent.ShowToast("Upload failed: ${err.localizedMessage ?: "Could not process image"}"))
            }
        }
    }

    suspend fun craftCinematicPrompt(
        sceneHeading: String,
        shotType: String,
        lens: String,
        cameraMovement: String,
        actionSummary: String,
        dialogueSnippet: String = "",
        stylePreset: CinematicStylePreset = CinematicStylePreset.FILM_NOIR
    ): String {
        val result = geminiService.craftCinematicPromptWithGemini(
            sceneHeading = sceneHeading,
            shotType = shotType,
            lens = lens,
            cameraMovement = cameraMovement,
            actionSummary = actionSummary,
            dialogueSnippet = dialogueSnippet,
            stylePreset = stylePreset
        )
        return result.getOrDefault("${shotType} in ${sceneHeading}. ${actionSummary.take(60)}")
    }

    // --- Character Operations ---
    fun addCharacter(character: CharacterProfile) {
        val projectId = _selectedProjectId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertCharacter(character.copy(projectId = projectId))
            _uiEvents.emit(UiEvent.ShowToast("Character ${character.name} added"))
        }
    }

    fun updateCharacter(character: CharacterProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateCharacter(character)
            _uiEvents.emit(UiEvent.ShowToast("Character updated"))
        }
    }

    fun deleteCharacter(character: CharacterProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCharacter(character)
            _uiEvents.emit(UiEvent.ShowToast("Character removed"))
        }
    }

    // --- Collaboration & Comments ---
    fun addComment(content: String, role: String = "Co-Writer") {
        val projectId = _selectedProjectId.value ?: return
        if (content.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val comment = CollaborationComment(
                projectId = projectId,
                authorName = "David Miller",
                authorRole = role,
                avatarInitials = "DM",
                content = content,
                status = "OPEN"
            )
            repository.insertComment(comment)
            repository.sendCollaboratorPing("David Miller", "Posted note: \"${content.take(20)}...\"")
            _uiEvents.emit(UiEvent.ShowToast("Comment posted to Collaboration Hub"))
        }
    }

    fun deleteComment(comment: CollaborationComment) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteComment(comment)
            _uiEvents.emit(UiEvent.ShowToast("Comment removed"))
        }
    }

    fun simulateLiveCollaboratorAction() {
        val sampleActions = listOf(
            "Elena Vance" to "Reviewed Scene 2 dialogue: 'Sharp delivery!'",
            "Marcus Sterling" to "Locked Shot 1A camera crane setup",
            "Maya Patel" to "Approved Blue Revision Draft"
        )
        val pick = sampleActions.random()
        val projectId = _selectedProjectId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val comment = CollaborationComment(
                projectId = projectId,
                authorName = pick.first,
                authorRole = if (pick.first.contains("Elena")) "Story Editor" else if (pick.first.contains("Marcus")) "Director" else "Producer",
                avatarInitials = pick.first.split(" ").map { it.take(1) }.joinToString(""),
                content = pick.second,
                status = "OPEN"
            )
            repository.insertComment(comment)
            repository.sendCollaboratorPing(pick.first, pick.second)
            _uiEvents.emit(UiEvent.ShowToast("${pick.first} updated the live script!"))
        }
    }

    // --- Project Settings & Formatting Tools ---
    fun updateProject(updated: ScreenplayProject) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateProject(updated.copy(updatedAt = System.currentTimeMillis()))
            _uiEvents.emit(UiEvent.ShowToast("Script details updated"))
        }
    }

    // --- Industry Standard PDF Export (Cloud Firestore Service) ---
    fun exportScreenplayPdf(context: Context) {
        val projectId = _selectedProjectId.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            isExporting.value = true
            exportStatusMessage.value = "Connecting to Firestore..."
            try {
                val config = ScreenplayPdfConfig(
                    includeTitlePage = includeTitlePage.value,
                    includeSceneNumbers = includeSceneNumbers.value,
                    watermarkText = watermarkText.value.takeIf { it.isNotBlank() }
                )
                val result = firestorePdfService.generateScreenplayPdfFromFirestore(
                    projectId = projectId,
                    config = config,
                    onProgress = { message ->
                        exportStatusMessage.value = message
                    }
                )

                when (result) {
                    is FirestorePdfResult.Success -> {
                        lastExportedFile.value = result.file
                        lastExportSource.value = result.source
                        lastExportPageCount.value = result.totalPages
                        val shareIntent = firestorePdfService.createShareIntent(result.file)
                        _uiEvents.emit(UiEvent.SharePdf(shareIntent, result.file))
                        _uiEvents.emit(UiEvent.ShowToast("Exported ${result.totalPages} pages via ${result.source}!"))
                    }
                    is FirestorePdfResult.Error -> {
                        _uiEvents.emit(UiEvent.ShowToast("Export failed: ${result.message}"))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(UiEvent.ShowToast("PDF Export failed: ${e.localizedMessage}"))
            } finally {
                isExporting.value = false
                exportStatusMessage.value = null
            }
        }
    }

    fun exportStoryboardPdf(context: Context) {
        val projectId = _selectedProjectId.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            isExporting.value = true
            exportStatusMessage.value = "Fetching shots from Firestore..."
            try {
                val result = firestorePdfService.generateStoryboardPdfFromFirestore(
                    projectId = projectId,
                    onProgress = { message ->
                        exportStatusMessage.value = message
                    }
                )

                when (result) {
                    is FirestorePdfResult.Success -> {
                        lastExportedFile.value = result.file
                        lastExportSource.value = result.source
                        lastExportPageCount.value = result.totalPages
                        val shareIntent = firestorePdfService.createShareIntent(result.file)
                        _uiEvents.emit(UiEvent.SharePdf(shareIntent, result.file))
                        _uiEvents.emit(UiEvent.ShowToast("Storyboard Deck exported (${result.totalPages} pages)!"))
                    }
                    is FirestorePdfResult.Error -> {
                        _uiEvents.emit(UiEvent.ShowToast("Storyboard Export failed: ${result.message}"))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(UiEvent.ShowToast("Storyboard Export failed: ${e.localizedMessage}"))
            } finally {
                isExporting.value = false
                exportStatusMessage.value = null
            }
        }
    }

    fun exportCharacterBiblePdf(context: Context) {
        val projectId = _selectedProjectId.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            isExporting.value = true
            exportStatusMessage.value = "Fetching characters from Firestore..."
            try {
                val result = firestorePdfService.generateCharacterBiblePdfFromFirestore(
                    projectId = projectId,
                    onProgress = { message ->
                        exportStatusMessage.value = message
                    }
                )

                when (result) {
                    is FirestorePdfResult.Success -> {
                        lastExportedFile.value = result.file
                        lastExportSource.value = result.source
                        lastExportPageCount.value = result.totalPages
                        val shareIntent = firestorePdfService.createShareIntent(result.file)
                        _uiEvents.emit(UiEvent.SharePdf(shareIntent, result.file))
                        _uiEvents.emit(UiEvent.ShowToast("Character Bible exported (${result.totalPages} pages)!"))
                    }
                    is FirestorePdfResult.Error -> {
                        _uiEvents.emit(UiEvent.ShowToast("Character Export failed: ${result.message}"))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(UiEvent.ShowToast("Character Export failed: ${e.localizedMessage}"))
            } finally {
                isExporting.value = false
                exportStatusMessage.value = null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        syncService.stopRealtimeSync()
    }
}
