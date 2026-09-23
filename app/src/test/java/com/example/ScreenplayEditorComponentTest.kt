package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.data.model.CharacterProfile
import com.example.data.model.ScreenplayElement
import com.example.data.model.ScreenplayElementType
import com.example.data.model.ScreenplayProject
import com.example.ui.components.ScreenplayEditorComponent
import com.example.ui.components.getNextIndustrySuggestedType
import com.example.ui.components.getPlaceholderForType
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScreenplayEditorComponentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `test industry standard formatting auto-advancement progression`() {
        // Industry rule: Scene Heading -> Action
        assertEquals(ScreenplayElementType.ACTION, getNextIndustrySuggestedType(ScreenplayElementType.SCENE_HEADING))
        // Industry rule: Character -> Dialogue
        assertEquals(ScreenplayElementType.DIALOGUE, getNextIndustrySuggestedType(ScreenplayElementType.CHARACTER))
        // Industry rule: Parenthetical -> Dialogue
        assertEquals(ScreenplayElementType.DIALOGUE, getNextIndustrySuggestedType(ScreenplayElementType.PARENTHETICAL))
        // Industry rule: Dialogue -> Character
        assertEquals(ScreenplayElementType.CHARACTER, getNextIndustrySuggestedType(ScreenplayElementType.DIALOGUE))
        // Industry rule: Transition -> Scene Heading
        assertEquals(ScreenplayElementType.SCENE_HEADING, getNextIndustrySuggestedType(ScreenplayElementType.TRANSITION))
    }

    @Test
    fun `test placeholder texts match screenplay standards`() {
        val scenePlaceholder = getPlaceholderForType(ScreenplayElementType.SCENE_HEADING)
        assert(scenePlaceholder.contains("INT."))

        val parenPlaceholder = getPlaceholderForType(ScreenplayElementType.PARENTHETICAL)
        assert(parenPlaceholder.contains("whispering") || parenPlaceholder.contains("beat"))
    }

    @Test
    fun `test ScreenplayEditorComponent renders industry formatted beats`() {
        val sampleProject = ScreenplayProject(
            id = 1,
            title = "THE NEON CHRONICLES",
            author = "Alex Vance",
            genre = "Cyberpunk Neo-Noir"
        )

        val sampleElements = listOf(
            ScreenplayElement(
                id = 101,
                projectId = 1,
                elementType = ScreenplayElementType.SCENE_HEADING.name,
                content = "INT. DETECTIVE OFFICE - NIGHT",
                orderIndex = 1,
                sceneNumber = 1
            ),
            ScreenplayElement(
                id = 102,
                projectId = 1,
                elementType = ScreenplayElementType.ACTION.name,
                content = "Rain lashes against the grime-streaked window blinds.",
                orderIndex = 2
            ),
            ScreenplayElement(
                id = 103,
                projectId = 1,
                elementType = ScreenplayElementType.CHARACTER.name,
                content = "MARCUS (V.O.)",
                orderIndex = 3
            ),
            ScreenplayElement(
                id = 104,
                projectId = 1,
                elementType = ScreenplayElementType.PARENTHETICAL.name,
                content = "weary, taking a slow sip",
                orderIndex = 4
            ),
            ScreenplayElement(
                id = 105,
                projectId = 1,
                elementType = ScreenplayElementType.DIALOGUE.name,
                content = "In this city, memories are cheaper than clean water.",
                orderIndex = 5
            )
        )

        val characters = listOf(
            CharacterProfile(
                id = 1,
                projectId = 1,
                name = "MARCUS",
                role = "Protagonist",
                tagline = "A world-weary synth-crime investigator",
                arcWant = "Wants to solve the district blackouts",
                arcNeed = "Needs to confront his past",
                traits = "Cynical, observant, stoic",
                dialogueVoice = "Dry, laconic, direct",
                castingSuggestion = "Oscar Isaac"
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                ScreenplayEditorComponent(
                    project = sampleProject,
                    elements = sampleElements,
                    characters = characters,
                    onAddElement = { _, _, _ -> },
                    onUpdateElement = {},
                    onDeleteElement = {},
                    onElementComment = {}
                )
            }
        }

        // Verify canvas and industry format badge are present
        composeTestRule.onNodeWithTag("screenplay_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithText("INDUSTRY FORMAT").assertIsDisplayed()

        // Verify scene heading with number
        composeTestRule.onAllNodesWithText("1.").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("INT. DETECTIVE OFFICE - NIGHT").assertIsDisplayed()

        // Verify action beat
        composeTestRule.onNodeWithText("Rain lashes against the grime-streaked window blinds.").assertIsDisplayed()

        // Verify character cue
        composeTestRule.onNodeWithText("MARCUS (V.O.)").assertIsDisplayed()

        // Verify parenthetical formatting wrapped in parentheses
        composeTestRule.onNodeWithText("(weary, taking a slow sip)").assertIsDisplayed()

        // Verify dialogue line
        composeTestRule.onNodeWithText("In this city, memories are cheaper than clean water.").assertIsDisplayed()
    }
}
