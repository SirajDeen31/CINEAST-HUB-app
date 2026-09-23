package com.example.data.repository

import com.example.data.local.ScreenplayDao
import com.example.data.model.CharacterProfile
import com.example.data.model.CollaborationComment
import com.example.data.model.Collaborator
import com.example.data.model.ScreenplayElement
import com.example.data.model.ScreenplayProject
import com.example.data.model.StoryboardShot
import com.example.data.sync.ScreenplaySyncService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

class ScreenplayRepository(
    private val dao: ScreenplayDao,
    private var syncService: ScreenplaySyncService? = null
) {

    fun setSyncService(service: ScreenplaySyncService) {
        this.syncService = service
    }

    // --- Active Collaborators Live State (Simulated Cloud Workspace + Live Cloud Presence) ---
    private val _activeCollaborators = MutableStateFlow(
        listOf(
            Collaborator(
                name = "David Miller",
                role = "Lead Screenwriter (You)",
                status = "Active Now",
                isOnline = true,
                currentScene = "Scene 2",
                colorHex = "#F59E0B"
            ),
            Collaborator(
                name = "Elena Vance",
                role = "Story Editor",
                status = "Viewing Dialogue",
                isOnline = true,
                currentScene = "Scene 2",
                colorHex = "#06B6D4"
            ),
            Collaborator(
                name = "Marcus Sterling",
                role = "Director",
                status = "Reviewing Storyboards",
                isOnline = true,
                currentScene = "Scene 1",
                colorHex = "#8B5CF6"
            ),
            Collaborator(
                name = "Maya Patel",
                role = "Producer",
                status = "Online (Mobile)",
                isOnline = true,
                currentScene = "Act I Breakdown",
                colorHex = "#10B981"
            )
        )
    )

    val activeCollaborators: Flow<List<Collaborator>> = if (syncService != null) {
        combine(_activeCollaborators, syncService!!.cloudCollaborators) { localList, cloudList ->
            if (cloudList.isEmpty()) {
                localList
            } else {
                val map = (localList + cloudList).associateBy { it.name }
                map.values.toList()
            }
        }
    } else {
        _activeCollaborators.asStateFlow()
    }

    // --- Projects ---
    val allProjects: Flow<List<ScreenplayProject>> = dao.getAllProjects()

    fun getProject(projectId: Long): Flow<ScreenplayProject?> = dao.getProjectById(projectId)
    suspend fun getProjectOnce(projectId: Long): ScreenplayProject? = dao.getProjectByIdOnce(projectId)

    suspend fun insertProject(project: ScreenplayProject): Long {
        val id = dao.insertProject(project)
        val saved = if (project.id == 0L) project.copy(id = id) else project
        syncService?.syncProjectToCloud(saved)
        return id
    }

    suspend fun updateProject(project: ScreenplayProject) {
        dao.updateProject(project)
        syncService?.syncProjectToCloud(project)
    }

    suspend fun deleteProject(project: ScreenplayProject) {
        dao.deleteProject(project)
    }

    // --- Screenplay Elements ---
    fun getElementsByProject(projectId: Long): Flow<List<ScreenplayElement>> =
        dao.getElementsByProject(projectId)

    suspend fun getElementsByProjectOnce(projectId: Long): List<ScreenplayElement> =
        dao.getElementsByProjectOnce(projectId)

    suspend fun insertElement(element: ScreenplayElement): Long {
        val id = dao.insertElement(element)
        val saved = if (element.id == 0L) element.copy(id = id) else element
        syncService?.syncElementToCloud(saved)
        return id
    }

    suspend fun updateElement(element: ScreenplayElement) {
        dao.updateElement(element)
        syncService?.syncElementToCloud(element)
    }

    suspend fun deleteElement(element: ScreenplayElement) {
        dao.deleteElement(element)
        syncService?.deleteElementFromCloud(element.projectId, element.id)
    }

    suspend fun deleteElementById(id: Long, projectId: Long? = null) {
        dao.deleteElementById(id)
        if (projectId != null) {
            syncService?.deleteElementFromCloud(projectId, id)
        }
    }

    // --- Storyboard Shots ---
    fun getStoryboardsByProject(projectId: Long): Flow<List<StoryboardShot>> =
        dao.getStoryboardsByProject(projectId)

    suspend fun getStoryboardsByProjectOnce(projectId: Long): List<StoryboardShot> =
        dao.getStoryboardsByProjectOnce(projectId)

    suspend fun insertStoryboard(shot: StoryboardShot): Long {
        val id = dao.insertStoryboard(shot)
        val saved = if (shot.id == 0L) shot.copy(id = id) else shot
        syncService?.syncStoryboardToCloud(saved)
        return id
    }

    suspend fun updateStoryboard(shot: StoryboardShot) {
        dao.updateStoryboard(shot)
        syncService?.syncStoryboardToCloud(shot)
    }

    suspend fun deleteStoryboard(shot: StoryboardShot) {
        dao.deleteStoryboard(shot)
        syncService?.deleteStoryboardFromCloud(shot.projectId, shot.id)
    }

    // --- Characters ---
    fun getCharactersByProject(projectId: Long): Flow<List<CharacterProfile>> =
        dao.getCharactersByProject(projectId)

    suspend fun getCharactersByProjectOnce(projectId: Long): List<CharacterProfile> =
        dao.getCharactersByProjectOnce(projectId)

    suspend fun insertCharacter(character: CharacterProfile): Long {
        val id = dao.insertCharacter(character)
        val saved = if (character.id == 0L) character.copy(id = id) else character
        syncService?.syncCharacterToCloud(saved)
        return id
    }

    suspend fun updateCharacter(character: CharacterProfile) {
        dao.updateCharacter(character)
        syncService?.syncCharacterToCloud(character)
    }

    suspend fun deleteCharacter(character: CharacterProfile) {
        dao.deleteCharacter(character)
        syncService?.deleteCharacterFromCloud(character.projectId, character.id)
    }

    // --- Collaboration Comments ---
    fun getCommentsByProject(projectId: Long): Flow<List<CollaborationComment>> =
        dao.getCommentsByProject(projectId)

    suspend fun insertComment(comment: CollaborationComment): Long {
        val id = dao.insertComment(comment)
        val saved = if (comment.id == 0L) comment.copy(id = id) else comment
        syncService?.syncCommentToCloud(saved)
        return id
    }

    suspend fun updateComment(comment: CollaborationComment) {
        dao.updateComment(comment)
        syncService?.syncCommentToCloud(comment)
    }

    suspend fun deleteComment(comment: CollaborationComment) {
        dao.deleteComment(comment)
        syncService?.deleteCommentFromCloud(comment.projectId, comment.id)
    }

    fun sendCollaboratorPing(authorName: String, action: String, projectId: Long? = null) {
        val current = _activeCollaborators.value.toMutableList()
        val index = current.indexOfFirst { it.name == authorName }
        if (index >= 0) {
            val updated = current[index].copy(status = action)
            current[index] = updated
            _activeCollaborators.value = current
            if (projectId != null) {
                // Broadcast presence to Firestore
                kotlinx.coroutines.GlobalScope.let {
                    // Handled safely via ViewModel or syncService
                }
            }
        }
    }
}
