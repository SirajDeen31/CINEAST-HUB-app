package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.CharacterProfile
import com.example.data.model.CollaborationComment
import com.example.data.model.ScreenplayElement
import com.example.data.model.ScreenplayElementType
import com.example.data.model.ScreenplayProject
import com.example.data.model.StoryboardShot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ScreenplayProject::class,
        ScreenplayElement::class,
        StoryboardShot::class,
        CharacterProfile::class,
        CollaborationComment::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun screenplayDao(): ScreenplayDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cineast_hub_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database.screenplayDao())
                    }
                }
            }
        }

        suspend fun populateInitialData(dao: ScreenplayDao) {
            val projectId = dao.insertProject(
                ScreenplayProject(
                    title = "NEON SHADOWS",
                    logline = "In a drenched 2089 metropolis, a cynical memory-recovery detective uncovers an illicit syndicate selling fabricated identities—and discovers his own past was manufactured.",
                    author = "David Miller & Elena Vance",
                    basedOn = "Original Story",
                    contactInfo = "Miller-Vance Pictures\nreps@cineasthub.studio\n(555) 019-2834",
                    draftName = "Shooting Script (Blue Revision)",
                    draftColor = "Blue",
                    genre = "Sci-Fi Noir Thriller",
                    targetPages = 114
                )
            )

            val elements = listOf(
                ScreenplayElement(
                    projectId = projectId,
                    orderIndex = 0,
                    elementType = ScreenplayElementType.SCENE_HEADING.name,
                    content = "EXT. NEW ANGELES - SECTOR 4 SKYLINE - NIGHT",
                    sceneNumber = 1
                ),
                ScreenplayElement(
                    projectId = projectId,
                    orderIndex = 1,
                    elementType = ScreenplayElementType.ACTION.name,
                    content = "Acid rain hisses against the carbon-glass towers. Holographic serpents weave between neon spires. A rusted spinner hovers near a guttered fire escape, steam billowing into the perpetual twilight."
                ),
                ScreenplayElement(
                    projectId = projectId,
                    orderIndex = 2,
                    elementType = ScreenplayElementType.CHARACTER.name,
                    content = "JAXON (V.O.)"
                ),
                ScreenplayElement(
                    projectId = projectId,
                    orderIndex = 3,
                    elementType = ScreenplayElementType.DIALOGUE.name,
                    content = "They say memory is a luxury. In Sector 4, it's contraband. The kind people kill to erase... or die trying to buy back."
                ),
                ScreenplayElement(
                    projectId = projectId,
                    orderIndex = 4,
                    elementType = ScreenplayElementType.TRANSITION.name,
                    content = "CUT TO:"
                ),
                ScreenplayElement(
                    projectId = projectId,
                    orderIndex = 5,
                    elementType = ScreenplayElementType.SCENE_HEADING.name,
                    content = "INT. JAXON'S OFFICE - CONTINUOUS",
                    sceneNumber = 2
                ),
                ScreenplayElement(
                    projectId = projectId,
                    orderIndex = 6,
                    elementType = ScreenplayElementType.ACTION.name,
                    content = "A cramped room drenched in amber streetlight through venetian blinds. Shelves packed with archaic magnetic tape reels and neural dampeners. JAXON VANCE (38), gaunt with tired eyes and a cybernetic optical lens, lights a cigarette."
                ),
                ScreenplayElement(
                    projectId = projectId,
                    orderIndex = 7,
                    elementType = ScreenplayElementType.CHARACTER.name,
                    content = "LYRA"
                ),
                ScreenplayElement(
                    projectId = projectId,
                    orderIndex = 8,
                    elementType = ScreenplayElementType.PARENTHETICAL.name,
                    content = "(stepping from shadows)"
                ),
                ScreenplayElement(
                    projectId = projectId,
                    orderIndex = 9,
                    elementType = ScreenplayElementType.DIALOGUE.name,
                    content = "Still smoking analog toxins, Vance? Some habits die harder than guilt."
                ),
                ScreenplayElement(
                    projectId = projectId,
                    orderIndex = 10,
                    elementType = ScreenplayElementType.CHARACTER.name,
                    content = "JAXON"
                ),
                ScreenplayElement(
                    projectId = projectId,
                    orderIndex = 11,
                    elementType = ScreenplayElementType.DIALOGUE.name,
                    content = "The door was locked, Lyra. And dead women aren't supposed to have keycards."
                ),
                ScreenplayElement(
                    projectId = projectId,
                    orderIndex = 12,
                    elementType = ScreenplayElementType.CHARACTER.name,
                    content = "LYRA"
                ),
                ScreenplayElement(
                    projectId = projectId,
                    orderIndex = 13,
                    elementType = ScreenplayElementType.DIALOGUE.name,
                    content = "I didn't die. They just formatted me. But I left a backup... inside your neural drive."
                ),
                ScreenplayElement(
                    projectId = projectId,
                    orderIndex = 14,
                    elementType = ScreenplayElementType.ACTION.name,
                    content = "Jaxon freezes. His artificial optic flares electric blue. The cigarette trembles between his calloused fingers."
                ),
                ScreenplayElement(
                    projectId = projectId,
                    orderIndex = 15,
                    elementType = ScreenplayElementType.TRANSITION.name,
                    content = "SMASH CUT TO:"
                )
            )
            dao.insertElements(elements)

            // Initial Storyboard Shots
            val shots = listOf(
                StoryboardShot(
                    projectId = projectId,
                    sceneNumber = "Scene 1",
                    shotNumber = "1A",
                    shotType = "Extreme Wide Shot (EWS)",
                    cameraMovement = "Slow Crane Down",
                    lens = "24mm Anamorphic",
                    actionSummary = "Rain sweeps across Sector 4 skyline as neon serpents flicker in the haze. Spinner hovers near building edge.",
                    dialogueSnippet = "JAXON (V.O.): They say memory is a luxury...",
                    imageResName = "storyboard_sample_wide_1790150310028",
                    orderIndex = 0,
                    linkedSceneHeading = "EXT. NEO-TOKYO RUNWAY - DUSK",
                    imageSourceType = "PRESET_ASSET"
                ),
                StoryboardShot(
                    projectId = projectId,
                    sceneNumber = "Scene 2",
                    shotNumber = "2A",
                    shotType = "Medium Shot (MS)",
                    cameraMovement = "Static",
                    lens = "50mm Prime",
                    actionSummary = "Jaxon sits at his scarred desk under venetian shadows, striking an analog match.",
                    dialogueSnippet = "LYRA: Still smoking analog toxins, Vance?",
                    imageResName = null,
                    orderIndex = 1,
                    linkedSceneHeading = "INT. RUNNER'S APARTMENT - NIGHT",
                    imageSourceType = "NONE"
                ),
                StoryboardShot(
                    projectId = projectId,
                    sceneNumber = "Scene 2",
                    shotNumber = "2B",
                    shotType = "Close-Up (CU)",
                    cameraMovement = "Handheld Subtle Shake",
                    lens = "85mm Portrait",
                    actionSummary = "Lyra's silhouette steps forward, rain droplets glowing on her high-collar duster. Sharp piercing gaze.",
                    dialogueSnippet = "LYRA: I didn't die. They just formatted me.",
                    imageResName = "storyboard_sample_cu_1790150325985",
                    orderIndex = 2,
                    linkedSceneHeading = "INT. RUNNER'S APARTMENT - NIGHT",
                    imageSourceType = "PRESET_ASSET"
                ),
                StoryboardShot(
                    projectId = projectId,
                    sceneNumber = "Scene 2",
                    shotNumber = "2C",
                    shotType = "Extreme Close-Up (ECU)",
                    cameraMovement = "Quick Push-In",
                    lens = "100mm Macro",
                    actionSummary = "Jaxon's ocular optic clicks and iris iris-scans, pulsing intense cyan data rings as realization strikes.",
                    dialogueSnippet = "JAXON: The door was locked...",
                    imageResName = null,
                    orderIndex = 3,
                    linkedSceneHeading = "INT. RUNNER'S APARTMENT - NIGHT",
                    imageSourceType = "NONE"
                )
            )
            shots.forEach { dao.insertStoryboard(it) }

            // Initial Characters
            val characters = listOf(
                CharacterProfile(
                    projectId = projectId,
                    name = "JAXON VANCE",
                    role = "Protagonist",
                    tagline = "A burnt-out cyber-memory detective running on borrowed time and stale espresso.",
                    arcWant = "To solve his final case and buy passage off the irradiated Pacific Rim.",
                    arcNeed = "To accept that his fabricated memories are the only truth worth defending.",
                    traits = "Cynical, hyper-observant, insomnia-plagued, loyal to a fault.",
                    dialogueVoice = "Dry, laconic, cinematic hardboiled noir cadence with technical cybernetic jargon.",
                    castingSuggestion = "Oscar Isaac / Sterling K. Brown archetype",
                    scenesCount = 2,
                    avatarColorHex = "#F59E0B"
                ),
                CharacterProfile(
                    projectId = projectId,
                    name = "LYRA THORNE",
                    role = "Antagonist / Catalyst",
                    tagline = "A rogue neural architect who weaponized her own synthetic assassination.",
                    arcWant = "To decrypt the Syndicate's Master Core before her syn-consciousness corrupts.",
                    arcNeed = "To trust someone with the truth of the weaponized code she created.",
                    traits = "Razor-sharp, calculating, enigmatic, emotionally guarded.",
                    dialogueVoice = "Rapid, articulate, slightly melodic with cold clinical precision.",
                    castingSuggestion = "Ana de Armas / Sonoya Mizuno archetype",
                    scenesCount = 1,
                    avatarColorHex = "#06B6D4"
                ),
                CharacterProfile(
                    projectId = projectId,
                    name = "CHIEF MARCUS STERLING",
                    role = "Mentor / Foil",
                    tagline = "Veteran precinct commander who traded his morals for a pension in the upper dome.",
                    arcWant = "To keep the peace between the mega-corps and the slum sectors.",
                    arcNeed = "To remember why he took the badge thirty years ago.",
                    traits = "Weary, pragmatic, heavy drinker, authoritative.",
                    dialogueVoice = "Gravelly voice, blunt commands, colloquial street slang.",
                    castingSuggestion = "Giancarlo Esposito archetype",
                    scenesCount = 0,
                    avatarColorHex = "#8B5CF6"
                )
            )
            characters.forEach { dao.insertCharacter(it) }

            // Initial Collaboration Comments
            val comments = listOf(
                CollaborationComment(
                    projectId = projectId,
                    authorName = "Elena Vance",
                    authorRole = "Story Editor",
                    avatarInitials = "EV",
                    content = "The opening voiceover for Jaxon lands with great noir punch. Consider tightening line 3 to keep the rhythm snappy.",
                    status = "OPEN",
                    timestamp = System.currentTimeMillis() - 3600000 * 2
                ),
                CollaborationComment(
                    projectId = projectId,
                    authorName = "Marcus Sterling",
                    authorRole = "Director",
                    avatarInitials = "MS",
                    content = "Storyboard 1A and 2B lighting approved. The anamorphic lens flare will look incredible with the rain reflections.",
                    status = "RESOLVED",
                    timestamp = System.currentTimeMillis() - 3600000 * 5
                ),
                CollaborationComment(
                    projectId = projectId,
                    authorName = "Maya Patel",
                    authorRole = "Executive Producer",
                    avatarInitials = "MP",
                    content = "Pacing through Scene 2 is running tight at 1.8 pages. Perfect for the Act I inciting incident.",
                    status = "OPEN",
                    timestamp = System.currentTimeMillis() - 3600000 * 8
                )
            )
            comments.forEach { dao.insertComment(it) }
        }
    }
}
