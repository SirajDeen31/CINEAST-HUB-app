package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.ScreenplayDao
import com.example.data.model.CharacterProfile
import com.example.data.model.CollaborationComment
import com.example.data.model.Collaborator
import com.example.data.model.ScreenplayElement
import com.example.data.model.ScreenplayProject
import com.example.data.model.StoryboardShot
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

enum class SyncState {
    IDLE,
    CONNECTING,
    SYNCING,
    SYNCED,
    OFFLINE,
    ERROR
}

data class SyncStatus(
    val state: SyncState = SyncState.IDLE,
    val lastSyncTime: Long? = null,
    val itemsSyncedCount: Int = 0,
    val message: String = "Cloud Ready",
    val isRealtimeActive: Boolean = false
)

class ScreenplaySyncService(
    private val context: Context,
    private val dao: ScreenplayDao
) {
    companion object {
        private const val TAG = "ScreenplaySyncService"
        private const val COLLECTION_PROJECTS = "screenplay_projects"
        private const val SUBCOLLECTION_ELEMENTS = "elements"
        private const val SUBCOLLECTION_STORYBOARDS = "storyboard_shots"
        private const val SUBCOLLECTION_CHARACTERS = "characters"
        private const val SUBCOLLECTION_COMMENTS = "collaboration_comments"
        private const val SUBCOLLECTION_PRESENCE = "presence"
    }

    private val firestore: FirebaseFirestore by lazy {
        try {
            val app = FirebaseApp.getInstance()
            val dbId = try {
                context.getString(R.string.firestore_database_id)
            } catch (e: Exception) {
                null
            }

            if (!dbId.isNullOrBlank()) {
                Log.d(TAG, "Initializing Firestore with configured databaseId: $dbId")
                FirebaseFirestore.getInstance(app, dbId)
            } else {
                Log.d(TAG, "Initializing Firestore with default database")
                FirebaseFirestore.getInstance()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firestore with custom id, falling back to default", e)
            FirebaseFirestore.getInstance()
        }
    }

    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _cloudCollaborators = MutableStateFlow<List<Collaborator>>(emptyList())
    val cloudCollaborators: StateFlow<List<Collaborator>> = _cloudCollaborators.asStateFlow()

    // Listener registrations for real-time Firestore synchronization
    private val activeListeners = mutableListOf<ListenerRegistration>()
    private var currentlyObservedProjectId: Long? = null

    // Guard against echo-loops (remote updates trigger Room -> don't re-upload to cloud)
    @Volatile
    private var isApplyingRemoteUpdate = false

    // =========================================================================
    // Real-Time Listeners (Firestore -> Room)
    // =========================================================================

    fun startRealtimeSync(projectId: Long, scope: CoroutineScope) {
        if (currentlyObservedProjectId == projectId && activeListeners.isNotEmpty()) {
            Log.d(TAG, "Realtime sync already active for project $projectId")
            return
        }

        stopRealtimeSync()
        currentlyObservedProjectId = projectId

        _syncStatus.value = _syncStatus.value.copy(
            state = SyncState.CONNECTING,
            message = "Connecting to Firestore...",
            isRealtimeActive = true
        )

        val projectRef = firestore.collection(COLLECTION_PROJECTS).document(projectId.toString())

        // 1. Listen to Project Metadata
        val projectListener = projectRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Project snapshot listener error", error)
                _syncStatus.value = _syncStatus.value.copy(
                    state = SyncState.OFFLINE,
                    message = "Offline: ${error.localizedMessage ?: "Sync error"}"
                )
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists() && !snapshot.metadata.hasPendingWrites()) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val remoteProject = mapDocumentToProject(snapshot)
                        if (remoteProject != null) {
                            isApplyingRemoteUpdate = true
                            dao.updateProject(remoteProject)
                            isApplyingRemoteUpdate = false
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error applying remote project", e)
                    }
                }
            }
        }
        activeListeners.add(projectListener)

        // 2. Listen to Elements Subcollection (Live Screenplay text edits)
        val elementsListener = projectRef.collection(SUBCOLLECTION_ELEMENTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Elements snapshot error", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val remoteElements = snapshot.documents.mapNotNull { doc ->
                                mapDocumentToElement(doc, projectId)
                            }
                            if (remoteElements.isNotEmpty()) {
                                isApplyingRemoteUpdate = true
                                dao.insertElements(remoteElements)
                                isApplyingRemoteUpdate = false

                                _syncStatus.value = _syncStatus.value.copy(
                                    state = SyncState.SYNCED,
                                    lastSyncTime = System.currentTimeMillis(),
                                    itemsSyncedCount = remoteElements.size,
                                    message = "Live Synced (${remoteElements.size} script beats)"
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error applying remote elements", e)
                        }
                    }
                }
            }
        activeListeners.add(elementsListener)

        // 3. Listen to Storyboard Shots
        val storyboardListener = projectRef.collection(SUBCOLLECTION_STORYBOARDS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val shots = snapshot.documents.mapNotNull { mapDocumentToStoryboard(it, projectId) }
                            if (shots.isNotEmpty()) {
                                isApplyingRemoteUpdate = true
                                dao.insertStoryboards(shots)
                                isApplyingRemoteUpdate = false
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error applying remote storyboards", e)
                        }
                    }
                }
            }
        activeListeners.add(storyboardListener)

        // 4. Listen to Character Profiles
        val charactersListener = projectRef.collection(SUBCOLLECTION_CHARACTERS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val chars = snapshot.documents.mapNotNull { mapDocumentToCharacter(it, projectId) }
                            if (chars.isNotEmpty()) {
                                isApplyingRemoteUpdate = true
                                dao.insertCharacters(chars)
                                isApplyingRemoteUpdate = false
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error applying remote characters", e)
                        }
                    }
                }
            }
        activeListeners.add(charactersListener)

        // 5. Listen to Collaboration Comments
        val commentsListener = projectRef.collection(SUBCOLLECTION_COMMENTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val comments = snapshot.documents.mapNotNull { mapDocumentToComment(it, projectId) }
                            if (comments.isNotEmpty()) {
                                isApplyingRemoteUpdate = true
                                dao.insertComments(comments)
                                isApplyingRemoteUpdate = false
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error applying remote comments", e)
                        }
                    }
                }
            }
        activeListeners.add(commentsListener)

        // 6. Listen to Real-time Presence in the Screenplay Room
        val presenceListener = projectRef.collection(SUBCOLLECTION_PRESENCE)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val collaborators = snapshot.documents.mapNotNull { doc ->
                        try {
                            Collaborator(
                                name = doc.getString("name") ?: "Writer",
                                role = doc.getString("role") ?: "Collaborator",
                                status = doc.getString("status") ?: "Active",
                                isOnline = doc.getBoolean("isOnline") ?: true,
                                currentScene = doc.getString("currentScene") ?: "Scene 1",
                                colorHex = doc.getString("colorHex") ?: "#F59E0B"
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (collaborators.isNotEmpty()) {
                        _cloudCollaborators.value = collaborators
                    }
                }
            }
        activeListeners.add(presenceListener)

        _syncStatus.value = _syncStatus.value.copy(
            state = SyncState.SYNCED,
            lastSyncTime = System.currentTimeMillis(),
            message = "Real-Time Room Connected",
            isRealtimeActive = true
        )
    }

    fun stopRealtimeSync() {
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
        currentlyObservedProjectId = null
        _syncStatus.value = _syncStatus.value.copy(isRealtimeActive = false)
    }

    // =========================================================================
    // Room -> Firestore Push Operations
    // =========================================================================

    suspend fun fullPushToCloud(projectId: Long): Boolean = withContext(Dispatchers.IO) {
        if (isApplyingRemoteUpdate) return@withContext true

        try {
            _syncStatus.value = _syncStatus.value.copy(
                state = SyncState.SYNCING,
                message = "Pushing local data to Firestore..."
            )

            val project = dao.getProjectByIdOnce(projectId) ?: return@withContext false
            val elements = dao.getElementsByProjectOnce(projectId)
            val storyboards = dao.getStoryboardsByProjectOnce(projectId)
            val characters = dao.getCharactersByProjectOnce(projectId)
            val comments = dao.getCommentsByProjectOnce(projectId)

            val projectRef = firestore.collection(COLLECTION_PROJECTS).document(projectId.toString())

            // Push project
            val projectData = mapProjectToMap(project)
            projectRef.set(projectData, SetOptions.merge()).await()

            // Push elements in batch
            val batch = firestore.batch()
            elements.forEach { elem ->
                val doc = projectRef.collection(SUBCOLLECTION_ELEMENTS).document(elem.id.toString())
                batch.set(doc, mapElementToMap(elem), SetOptions.merge())
            }
            storyboards.forEach { shot ->
                val doc = projectRef.collection(SUBCOLLECTION_STORYBOARDS).document(shot.id.toString())
                batch.set(doc, mapStoryboardToMap(shot), SetOptions.merge())
            }
            characters.forEach { char ->
                val doc = projectRef.collection(SUBCOLLECTION_CHARACTERS).document(char.id.toString())
                batch.set(doc, mapCharacterToMap(char), SetOptions.merge())
            }
            comments.forEach { comment ->
                val doc = projectRef.collection(SUBCOLLECTION_COMMENTS).document(comment.id.toString())
                batch.set(doc, mapCommentToMap(comment), SetOptions.merge())
            }

            batch.commit().await()

            val totalItems = elements.size + storyboards.size + characters.size + comments.size
            _syncStatus.value = SyncStatus(
                state = SyncState.SYNCED,
                lastSyncTime = System.currentTimeMillis(),
                itemsSyncedCount = totalItems,
                message = "Cloud Backup & Room Synced ($totalItems items)",
                isRealtimeActive = true
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed full cloud push", e)
            _syncStatus.value = _syncStatus.value.copy(
                state = SyncState.ERROR,
                message = "Push failed: ${e.localizedMessage ?: "Network error"}"
            )
            false
        }
    }

    suspend fun syncProjectToCloud(project: ScreenplayProject) = withContext(Dispatchers.IO) {
        if (isApplyingRemoteUpdate) return@withContext
        try {
            firestore.collection(COLLECTION_PROJECTS)
                .document(project.id.toString())
                .set(mapProjectToMap(project), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Sync project error", e)
        }
    }

    suspend fun syncElementToCloud(element: ScreenplayElement) = withContext(Dispatchers.IO) {
        if (isApplyingRemoteUpdate) return@withContext
        try {
            firestore.collection(COLLECTION_PROJECTS)
                .document(element.projectId.toString())
                .collection(SUBCOLLECTION_ELEMENTS)
                .document(element.id.toString())
                .set(mapElementToMap(element), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Sync element error", e)
        }
    }

    suspend fun deleteElementFromCloud(projectId: Long, elementId: Long) = withContext(Dispatchers.IO) {
        if (isApplyingRemoteUpdate) return@withContext
        try {
            firestore.collection(COLLECTION_PROJECTS)
                .document(projectId.toString())
                .collection(SUBCOLLECTION_ELEMENTS)
                .document(elementId.toString())
                .delete()
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Delete element error", e)
        }
    }

    suspend fun syncStoryboardToCloud(shot: StoryboardShot) = withContext(Dispatchers.IO) {
        if (isApplyingRemoteUpdate) return@withContext
        try {
            firestore.collection(COLLECTION_PROJECTS)
                .document(shot.projectId.toString())
                .collection(SUBCOLLECTION_STORYBOARDS)
                .document(shot.id.toString())
                .set(mapStoryboardToMap(shot), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Sync storyboard error", e)
        }
    }

    suspend fun deleteStoryboardFromCloud(projectId: Long, shotId: Long) = withContext(Dispatchers.IO) {
        if (isApplyingRemoteUpdate) return@withContext
        try {
            firestore.collection(COLLECTION_PROJECTS)
                .document(projectId.toString())
                .collection(SUBCOLLECTION_STORYBOARDS)
                .document(shotId.toString())
                .delete()
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Delete storyboard error", e)
        }
    }

    suspend fun syncCharacterToCloud(character: CharacterProfile) = withContext(Dispatchers.IO) {
        if (isApplyingRemoteUpdate) return@withContext
        try {
            firestore.collection(COLLECTION_PROJECTS)
                .document(character.projectId.toString())
                .collection(SUBCOLLECTION_CHARACTERS)
                .document(character.id.toString())
                .set(mapCharacterToMap(character), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Sync character error", e)
        }
    }

    suspend fun deleteCharacterFromCloud(projectId: Long, characterId: Long) = withContext(Dispatchers.IO) {
        if (isApplyingRemoteUpdate) return@withContext
        try {
            firestore.collection(COLLECTION_PROJECTS)
                .document(projectId.toString())
                .collection(SUBCOLLECTION_CHARACTERS)
                .document(characterId.toString())
                .delete()
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Delete character error", e)
        }
    }

    suspend fun syncCommentToCloud(comment: CollaborationComment) = withContext(Dispatchers.IO) {
        if (isApplyingRemoteUpdate) return@withContext
        try {
            firestore.collection(COLLECTION_PROJECTS)
                .document(comment.projectId.toString())
                .collection(SUBCOLLECTION_COMMENTS)
                .document(comment.id.toString())
                .set(mapCommentToMap(comment), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Sync comment error", e)
        }
    }

    suspend fun deleteCommentFromCloud(projectId: Long, commentId: Long) = withContext(Dispatchers.IO) {
        if (isApplyingRemoteUpdate) return@withContext
        try {
            firestore.collection(COLLECTION_PROJECTS)
                .document(projectId.toString())
                .collection(SUBCOLLECTION_COMMENTS)
                .document(commentId.toString())
                .delete()
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Delete comment error", e)
        }
    }

    suspend fun broadcastPresence(projectId: Long, collaborator: Collaborator) = withContext(Dispatchers.IO) {
        try {
            val safeId = collaborator.name.replace(" ", "_").lowercase()
            val data = mapOf(
                "name" to collaborator.name,
                "role" to collaborator.role,
                "status" to collaborator.status,
                "isOnline" to collaborator.isOnline,
                "currentScene" to collaborator.currentScene,
                "colorHex" to collaborator.colorHex,
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection(COLLECTION_PROJECTS)
                .document(projectId.toString())
                .collection(SUBCOLLECTION_PRESENCE)
                .document(safeId)
                .set(data, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Broadcast presence error", e)
        }
    }

    // =========================================================================
    // Model Mappers
    // =========================================================================

    private fun mapProjectToMap(project: ScreenplayProject): Map<String, Any?> = mapOf(
        "id" to project.id,
        "title" to project.title,
        "logline" to project.logline,
        "author" to project.author,
        "basedOn" to project.basedOn,
        "contactInfo" to project.contactInfo,
        "draftName" to project.draftName,
        "draftColor" to project.draftColor,
        "genre" to project.genre,
        "targetPages" to project.targetPages,
        "createdAt" to project.createdAt,
        "updatedAt" to System.currentTimeMillis()
    )

    private fun mapDocumentToProject(doc: DocumentSnapshot): ScreenplayProject? {
        val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: return null
        return ScreenplayProject(
            id = id,
            title = doc.getString("title") ?: "Untitled",
            logline = doc.getString("logline") ?: "",
            author = doc.getString("author") ?: "David Miller",
            basedOn = doc.getString("basedOn") ?: "Original",
            contactInfo = doc.getString("contactInfo") ?: "",
            draftName = doc.getString("draftName") ?: "First Draft",
            draftColor = doc.getString("draftColor") ?: "White",
            genre = doc.getString("genre") ?: "Drama",
            targetPages = doc.getLong("targetPages")?.toInt() ?: 110,
            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
        )
    }

    private fun mapElementToMap(element: ScreenplayElement): Map<String, Any?> = mapOf(
        "id" to element.id,
        "projectId" to element.projectId,
        "orderIndex" to element.orderIndex,
        "elementType" to element.elementType,
        "content" to element.content,
        "sceneNumber" to element.sceneNumber,
        "revisionNote" to element.revisionNote,
        "hasComment" to element.hasComment
    )

    private fun mapDocumentToElement(doc: DocumentSnapshot, defaultProjectId: Long): ScreenplayElement? {
        val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: return null
        return ScreenplayElement(
            id = id,
            projectId = doc.getLong("projectId") ?: defaultProjectId,
            orderIndex = doc.getLong("orderIndex")?.toInt() ?: 0,
            elementType = doc.getString("elementType") ?: "ACTION",
            content = doc.getString("content") ?: "",
            sceneNumber = doc.getLong("sceneNumber")?.toInt(),
            revisionNote = doc.getString("revisionNote"),
            hasComment = doc.getBoolean("hasComment") ?: false
        )
    }

    private fun mapStoryboardToMap(shot: StoryboardShot): Map<String, Any?> = mapOf(
        "id" to shot.id,
        "projectId" to shot.projectId,
        "sceneNumber" to shot.sceneNumber,
        "shotNumber" to shot.shotNumber,
        "shotType" to shot.shotType,
        "cameraMovement" to shot.cameraMovement,
        "lens" to shot.lens,
        "actionSummary" to shot.actionSummary,
        "dialogueSnippet" to shot.dialogueSnippet,
        "imageResName" to shot.imageResName,
        "orderIndex" to shot.orderIndex,
        "imageUri" to shot.imageUri,
        "linkedSceneId" to shot.linkedSceneId,
        "linkedSceneHeading" to shot.linkedSceneHeading,
        "aiPromptUsed" to shot.aiPromptUsed,
        "imageSourceType" to shot.imageSourceType
    )

    private fun mapDocumentToStoryboard(doc: DocumentSnapshot, defaultProjectId: Long): StoryboardShot? {
        val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: return null
        return StoryboardShot(
            id = id,
            projectId = doc.getLong("projectId") ?: defaultProjectId,
            sceneNumber = doc.getString("sceneNumber") ?: "Scene 1",
            shotNumber = doc.getString("shotNumber") ?: "1A",
            shotType = doc.getString("shotType") ?: "Wide Shot (WS)",
            cameraMovement = doc.getString("cameraMovement") ?: "Static",
            lens = doc.getString("lens") ?: "35mm Cine",
            actionSummary = doc.getString("actionSummary") ?: "",
            dialogueSnippet = doc.getString("dialogueSnippet") ?: "",
            imageResName = doc.getString("imageResName"),
            orderIndex = doc.getLong("orderIndex")?.toInt() ?: 0,
            imageUri = doc.getString("imageUri"),
            linkedSceneId = doc.getLong("linkedSceneId"),
            linkedSceneHeading = doc.getString("linkedSceneHeading"),
            aiPromptUsed = doc.getString("aiPromptUsed"),
            imageSourceType = doc.getString("imageSourceType") ?: "NONE"
        )
    }

    private fun mapCharacterToMap(character: CharacterProfile): Map<String, Any?> = mapOf(
        "id" to character.id,
        "projectId" to character.projectId,
        "name" to character.name,
        "role" to character.role,
        "tagline" to character.tagline,
        "arcWant" to character.arcWant,
        "arcNeed" to character.arcNeed,
        "traits" to character.traits,
        "dialogueVoice" to character.dialogueVoice,
        "castingSuggestion" to character.castingSuggestion,
        "scenesCount" to character.scenesCount,
        "avatarColorHex" to character.avatarColorHex
    )

    private fun mapDocumentToCharacter(doc: DocumentSnapshot, defaultProjectId: Long): CharacterProfile? {
        val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: return null
        return CharacterProfile(
            id = id,
            projectId = doc.getLong("projectId") ?: defaultProjectId,
            name = doc.getString("name") ?: "Character",
            role = doc.getString("role") ?: "Supporting",
            tagline = doc.getString("tagline") ?: "",
            arcWant = doc.getString("arcWant") ?: "",
            arcNeed = doc.getString("arcNeed") ?: "",
            traits = doc.getString("traits") ?: "",
            dialogueVoice = doc.getString("dialogueVoice") ?: "",
            castingSuggestion = doc.getString("castingSuggestion") ?: "",
            scenesCount = doc.getLong("scenesCount")?.toInt() ?: 1,
            avatarColorHex = doc.getString("avatarColorHex") ?: "#F59E0B"
        )
    }

    private fun mapCommentToMap(comment: CollaborationComment): Map<String, Any?> = mapOf(
        "id" to comment.id,
        "projectId" to comment.projectId,
        "elementId" to comment.elementId,
        "authorName" to comment.authorName,
        "authorRole" to comment.authorRole,
        "avatarInitials" to comment.avatarInitials,
        "content" to comment.content,
        "status" to comment.status,
        "timestamp" to comment.timestamp
    )

    private fun mapDocumentToComment(doc: DocumentSnapshot, defaultProjectId: Long): CollaborationComment? {
        val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: return null
        return CollaborationComment(
            id = id,
            projectId = doc.getLong("projectId") ?: defaultProjectId,
            elementId = doc.getLong("elementId"),
            authorName = doc.getString("authorName") ?: "Collaborator",
            authorRole = doc.getString("authorRole") ?: "Writer",
            avatarInitials = doc.getString("avatarInitials") ?: "CW",
            content = doc.getString("content") ?: "",
            status = doc.getString("status") ?: "OPEN",
            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
        )
    }
}
