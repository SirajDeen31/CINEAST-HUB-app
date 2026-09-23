package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.CineastNavigationBar
import com.example.ui.components.CineastTopBar
import com.example.ui.screens.CharactersScreen
import com.example.ui.screens.CollaborationHubScreen
import com.example.ui.screens.ExportScreen
import com.example.ui.screens.FormatToolsScreen
import com.example.ui.screens.ScreenplayEditorScreen
import com.example.ui.screens.StoryboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.CineastTab
import com.example.ui.viewmodel.ScreenplayViewModel
import com.example.ui.viewmodel.UiEvent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CineastApp()
            }
        }
    }
}

@Composable
fun CineastApp(viewModel: ScreenplayViewModel = viewModel()) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val project by viewModel.currentProject.collectAsStateWithLifecycle()
    val elements by viewModel.elements.collectAsStateWithLifecycle()
    val storyboards by viewModel.storyboards.collectAsStateWithLifecycle()
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val collaborators by viewModel.activeCollaborators.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val exportStatusMessage by viewModel.exportStatusMessage.collectAsStateWithLifecycle()
    val lastExportedFile by viewModel.lastExportedFile.collectAsStateWithLifecycle()
    val lastExportSource by viewModel.lastExportSource.collectAsStateWithLifecycle()
    val lastExportPageCount by viewModel.lastExportPageCount.collectAsStateWithLifecycle()
    val watermarkText by viewModel.watermarkText.collectAsStateWithLifecycle()
    val includeTitlePage by viewModel.includeTitlePage.collectAsStateWithLifecycle()
    val includeSceneNumbers by viewModel.includeSceneNumbers.collectAsStateWithLifecycle()
    val isGeneratingImage by viewModel.isGeneratingStoryboardImage.collectAsStateWithLifecycle()
    val storyboardGenStatus by viewModel.storyboardGenerationStatus.collectAsStateWithLifecycle()

    // Listen for UI events (Toasts & Share Intent)
    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is UiEvent.SharePdf -> {
                    try {
                        val chooser = Intent.createChooser(event.intent, "Share Industry PDF")
                        context.startActivity(chooser)
                    } catch (e: Exception) {
                        Toast.makeText(context, "PDF saved: ${event.file.name}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CineastTopBar(
                project = project,
                activeCollabCount = collaborators.count { it.isOnline },
                onCollabClick = { viewModel.selectTab(CineastTab.COLLAB) },
                syncStatus = syncStatus,
                onSyncClick = { viewModel.triggerCloudSync() }
            )
        },
        bottomBar = {
            CineastNavigationBar(
                currentTab = currentTab,
                onTabSelected = { viewModel.selectTab(it) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentTab) {
                CineastTab.SCREENPLAY -> {
                    ScreenplayEditorScreen(
                        project = project,
                        elements = elements,
                        characters = characters,
                        onAddElement = { type, content, sceneNum ->
                            viewModel.addElement(type, content, sceneNum)
                        },
                        onUpdateElement = { elem ->
                            viewModel.updateElement(elem)
                        },
                        onDeleteElement = { id ->
                            viewModel.deleteElement(id)
                        },
                        onAddComment = { note ->
                            viewModel.addComment(note)
                        }
                    )
                }

                CineastTab.STORYBOARD -> {
                    StoryboardScreen(
                        project = project,
                        shots = storyboards,
                        elements = elements,
                        isGeneratingImage = isGeneratingImage,
                        generationStatus = storyboardGenStatus,
                        onAddShot = { viewModel.addStoryboardShot(it) },
                        onUpdateShot = { viewModel.updateStoryboardShot(it) },
                        onDeleteShot = { viewModel.deleteStoryboardShot(it) },
                        onGenerateImage = { shot, prompt, preset, forceOffline, onComplete ->
                            viewModel.generateStoryboardImageForShot(shot, prompt, preset, forceOffline, onComplete)
                        },
                        onUploadImage = { shot, uri, onComplete ->
                            viewModel.saveUploadedImageForShot(shot, uri, onComplete)
                        },
                        onCraftPrompt = { heading, shotType, lens, mov, action, dial, preset ->
                            viewModel.craftCinematicPrompt(heading, shotType, lens, mov, action, dial, preset)
                        },
                        onNavigateToScriptScene = { sceneId ->
                            viewModel.navigateToSceneInScript(sceneId)
                        }
                    )
                }

                CineastTab.CHARACTERS -> {
                    CharactersScreen(
                        characters = characters,
                        onAddCharacter = { viewModel.addCharacter(it) },
                        onUpdateCharacter = { viewModel.updateCharacter(it) },
                        onDeleteCharacter = { viewModel.deleteCharacter(it) }
                    )
                }

                CineastTab.COLLAB -> {
                    CollaborationHubScreen(
                        collaborators = collaborators,
                        comments = comments,
                        onAddComment = { content, role ->
                            viewModel.addComment(content, role)
                        },
                        onDeleteComment = { comment ->
                            viewModel.deleteComment(comment)
                        },
                        onSimulateLiveActivity = {
                            viewModel.simulateLiveCollaboratorAction()
                        },
                        syncStatus = syncStatus,
                        onTriggerSync = {
                            viewModel.triggerCloudSync()
                        }
                    )
                }

                CineastTab.FORMAT_TOOLS -> {
                    FormatToolsScreen(
                        project = project,
                        elements = elements,
                        onUpdateProject = { updated ->
                            viewModel.updateProject(updated)
                        }
                    )
                }

                CineastTab.EXPORT -> {
                    ExportScreen(
                        project = project,
                        isExporting = isExporting,
                        exportStatusMessage = exportStatusMessage,
                        lastExportedFile = lastExportedFile,
                        lastExportSource = lastExportSource,
                        lastExportPageCount = lastExportPageCount,
                        watermarkText = watermarkText,
                        onWatermarkChange = { viewModel.watermarkText.value = it },
                        includeTitlePage = includeTitlePage,
                        includeSceneNumbers = includeSceneNumbers,
                        onToggleTitlePage = { viewModel.includeTitlePage.value = it },
                        onToggleSceneNumbers = { viewModel.includeSceneNumbers.value = it },
                        onExportScreenplay = { viewModel.exportScreenplayPdf(context) },
                        onExportStoryboard = { viewModel.exportStoryboardPdf(context) },
                        onExportCharacterBible = { viewModel.exportCharacterBiblePdf(context) }
                    )
                }
            }
        }
    }
}
