package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.CharacterProfile
import com.example.data.model.CollaborationComment
import com.example.data.model.ScreenplayElement
import com.example.data.model.ScreenplayProject
import com.example.data.model.StoryboardShot
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenplayDao {

    // --- Projects ---
    @Query("SELECT * FROM screenplay_projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ScreenplayProject>>

    @Query("SELECT * FROM screenplay_projects WHERE id = :projectId")
    fun getProjectById(projectId: Long): Flow<ScreenplayProject?>

    @Query("SELECT * FROM screenplay_projects WHERE id = :projectId")
    suspend fun getProjectByIdOnce(projectId: Long): ScreenplayProject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ScreenplayProject): Long

    @Update
    suspend fun updateProject(project: ScreenplayProject)

    @Delete
    suspend fun deleteProject(project: ScreenplayProject)

    // --- Screenplay Elements ---
    @Query("SELECT * FROM screenplay_elements WHERE projectId = :projectId ORDER BY orderIndex ASC")
    fun getElementsByProject(projectId: Long): Flow<List<ScreenplayElement>>

    @Query("SELECT * FROM screenplay_elements WHERE projectId = :projectId ORDER BY orderIndex ASC")
    suspend fun getElementsByProjectOnce(projectId: Long): List<ScreenplayElement>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertElement(element: ScreenplayElement): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertElements(elements: List<ScreenplayElement>)

    @Update
    suspend fun updateElement(element: ScreenplayElement)

    @Delete
    suspend fun deleteElement(element: ScreenplayElement)

    @Query("DELETE FROM screenplay_elements WHERE id = :id")
    suspend fun deleteElementById(id: Long)

    @Query("DELETE FROM screenplay_elements WHERE projectId = :projectId")
    suspend fun deleteElementsByProject(projectId: Long)

    // --- Storyboard Shots ---
    @Query("SELECT * FROM storyboard_shots WHERE projectId = :projectId ORDER BY orderIndex ASC")
    fun getStoryboardsByProject(projectId: Long): Flow<List<StoryboardShot>>

    @Query("SELECT * FROM storyboard_shots WHERE projectId = :projectId ORDER BY orderIndex ASC")
    suspend fun getStoryboardsByProjectOnce(projectId: Long): List<StoryboardShot>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStoryboard(shot: StoryboardShot): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStoryboards(shots: List<StoryboardShot>)

    @Update
    suspend fun updateStoryboard(shot: StoryboardShot)

    @Delete
    suspend fun deleteStoryboard(shot: StoryboardShot)

    @Query("DELETE FROM storyboard_shots WHERE id = :id")
    suspend fun deleteStoryboardById(id: Long)

    @Query("DELETE FROM storyboard_shots WHERE projectId = :projectId")
    suspend fun deleteStoryboardsByProject(projectId: Long)

    // --- Character Profiles ---
    @Query("SELECT * FROM character_profiles WHERE projectId = :projectId ORDER BY name ASC")
    fun getCharactersByProject(projectId: Long): Flow<List<CharacterProfile>>

    @Query("SELECT * FROM character_profiles WHERE projectId = :projectId ORDER BY name ASC")
    suspend fun getCharactersByProjectOnce(projectId: Long): List<CharacterProfile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacter(character: CharacterProfile): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacters(characters: List<CharacterProfile>)

    @Update
    suspend fun updateCharacter(character: CharacterProfile)

    @Delete
    suspend fun deleteCharacter(character: CharacterProfile)

    @Query("DELETE FROM character_profiles WHERE id = :id")
    suspend fun deleteCharacterById(id: Long)

    @Query("DELETE FROM character_profiles WHERE projectId = :projectId")
    suspend fun deleteCharactersByProject(projectId: Long)

    // --- Collaboration Comments ---
    @Query("SELECT * FROM collaboration_comments WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getCommentsByProject(projectId: Long): Flow<List<CollaborationComment>>

    @Query("SELECT * FROM collaboration_comments WHERE projectId = :projectId ORDER BY timestamp DESC")
    suspend fun getCommentsByProjectOnce(projectId: Long): List<CollaborationComment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CollaborationComment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComments(comments: List<CollaborationComment>)

    @Update
    suspend fun updateComment(comment: CollaborationComment)

    @Delete
    suspend fun deleteComment(comment: CollaborationComment)

    @Query("DELETE FROM collaboration_comments WHERE id = :id")
    suspend fun deleteCommentById(id: Long)

    @Query("DELETE FROM collaboration_comments WHERE projectId = :projectId")
    suspend fun deleteCommentsByProject(projectId: Long)
}
