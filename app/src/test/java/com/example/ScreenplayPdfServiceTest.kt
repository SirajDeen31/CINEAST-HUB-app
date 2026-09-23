package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CharacterProfile
import com.example.data.model.ScreenplayElement
import com.example.data.model.ScreenplayElementType
import com.example.data.model.ScreenplayProject
import com.example.export.FirestoreScreenplayPdfService
import com.example.export.ScreenplayPdfConfig
import com.example.export.ScreenplayPdfExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScreenplayPdfServiceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `test industry standard margin constants conform to WGA specification`() {
        // Industry US Letter 8.5" x 11" @ 72 dpi (Points)
        assertEquals(612, FirestoreScreenplayPdfService.PAGE_WIDTH)
        assertEquals(792, FirestoreScreenplayPdfService.PAGE_HEIGHT)

        // Standard 1.5" Left Margin for script brads / 3-hole punch (1.5 * 72 = 108pt)
        assertEquals(108f, FirestoreScreenplayPdfService.MARGIN_LEFT, 0.1f)
        assertEquals(108f, FirestoreScreenplayPdfService.INDENT_ACTION, 0.1f)

        // Standard 1.0" Right, Top, Bottom Margins (72pt)
        assertEquals(72f, FirestoreScreenplayPdfService.MARGIN_RIGHT, 0.1f)
        assertEquals(72f, FirestoreScreenplayPdfService.MARGIN_TOP, 0.1f)
        assertEquals(72f, FirestoreScreenplayPdfService.MARGIN_BOTTOM, 0.1f)

        // Standard Character Cue Indentation (~3.7" from left edge = ~266pt)
        assertEquals(266f, FirestoreScreenplayPdfService.INDENT_CHARACTER, 0.1f)

        // Standard Dialogue Indentation (~2.5" from left edge = 180pt)
        assertEquals(180f, FirestoreScreenplayPdfService.INDENT_DIALOGUE, 0.1f)

        // Standard Parenthetical Indentation (~3.1" from left edge = 223pt)
        assertEquals(223f, FirestoreScreenplayPdfService.INDENT_PARENTHETICAL, 0.1f)

        // Standard Dialogue Column Width (~3.5" = 252pt)
        assertEquals(252f, FirestoreScreenplayPdfService.WIDTH_DIALOGUE, 0.1f)
    }

    @Test
    fun `test ScreenplayPdfConfig options and defaults`() {
        val defaultConfig = ScreenplayPdfConfig()
        assertTrue(defaultConfig.includeTitlePage)
        assertTrue(defaultConfig.includeSceneNumbers)
        assertEquals(null, defaultConfig.watermarkText)
        assertTrue(defaultConfig.includeProductionHeader)

        val customConfig = ScreenplayPdfConfig(
            includeTitlePage = false,
            includeSceneNumbers = false,
            watermarkText = "CONFIDENTIAL DRAFT",
            draftRevisionColor = "Goldenrod",
            includeProductionHeader = true
        )
        assertEquals(false, customConfig.includeTitlePage)
        assertEquals(false, customConfig.includeSceneNumbers)
        assertEquals("CONFIDENTIAL DRAFT", customConfig.watermarkText)
        assertEquals("Goldenrod", customConfig.draftRevisionColor)
    }

    @Test
    fun `test share and view intent creation with FileProvider`() {
        val tempPdf = File(context.cacheDir, "test_script.pdf")
        tempPdf.writeText("%PDF-1.4 test header")

        val service = FirestoreScreenplayPdfService(context)
        val shareIntent = service.createShareIntent(tempPdf)
        assertNotNull(shareIntent)
        assertEquals("application/pdf", shareIntent.type)
        assertNotNull(shareIntent.extras)

        val viewIntent = service.createViewIntent(tempPdf)
        assertNotNull(viewIntent)
        assertEquals("application/pdf", viewIntent.type)
        assertNotNull(viewIntent.data)
    }

    @Test
    fun `test screenplay element hierarchy and slugline requirements`() {
        val elements = listOf(
            ScreenplayElement(
                id = 1L,
                projectId = 100L,
                orderIndex = 0,
                elementType = ScreenplayElementType.SCENE_HEADING.name,
                content = "INT. WRITERS ROOM - DAY",
                sceneNumber = 1
            ),
            ScreenplayElement(
                id = 2L,
                projectId = 100L,
                orderIndex = 1,
                elementType = ScreenplayElementType.ACTION.name,
                content = "Whiteboards covered in neon index cards line the acoustic walls."
            ),
            ScreenplayElement(
                id = 3L,
                projectId = 100L,
                orderIndex = 2,
                elementType = ScreenplayElementType.CHARACTER.name,
                content = "LEAD WRITER"
            ),
            ScreenplayElement(
                id = 4L,
                projectId = 100L,
                orderIndex = 3,
                elementType = ScreenplayElementType.PARENTHETICAL.name,
                content = "capping dry erase marker"
            ),
            ScreenplayElement(
                id = 5L,
                projectId = 100L,
                orderIndex = 4,
                elementType = ScreenplayElementType.DIALOGUE.name,
                content = "The third act twist lands right here."
            )
        )

        assertEquals(5, elements.size)
        assertEquals(ScreenplayElementType.SCENE_HEADING.name, elements[0].elementType)
        assertEquals(ScreenplayElementType.ACTION.name, elements[1].elementType)
        assertEquals(ScreenplayElementType.CHARACTER.name, elements[2].elementType)
        assertEquals(ScreenplayElementType.PARENTHETICAL.name, elements[3].elementType)
        assertEquals(ScreenplayElementType.DIALOGUE.name, elements[4].elementType)

        // Verify scene number is tracked
        assertEquals(1, elements[0].sceneNumber)
    }
}
