package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.local.ScreenplayDao
import com.example.data.model.CharacterProfile
import com.example.data.model.ScreenplayElement
import com.example.data.model.ScreenplayElementType
import com.example.data.model.ScreenplayProject
import com.example.data.model.StoryboardShot
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result state for Firestore PDF Export operations
 */
sealed class FirestorePdfResult {
    data class Success(
        val file: File,
        val totalPages: Int,
        val elementsCount: Int,
        val source: String // "Cloud Firestore" or "Local Cache Fallback"
    ) : FirestorePdfResult()

    data class Error(val message: String, val throwable: Throwable? = null) : FirestorePdfResult()
}

/**
 * Options for industry-standard screenplay formatting
 */
data class ScreenplayPdfConfig(
    val includeTitlePage: Boolean = true,
    val includeSceneNumbers: Boolean = true,
    val watermarkText: String? = null,
    val draftRevisionColor: String? = null,
    val includeProductionHeader: Boolean = true
)

/**
 * FirestoreScreenplayPdfService
 *
 * Direct cloud-to-print service that queries screenplay projects, elements,
 * storyboards, and character bibles directly from Google Cloud Firestore
 * and renders an authentic, industry-standard (WGA & AMPAS specification)
 * Courier 12pt screenplay PDF document.
 */
class FirestoreScreenplayPdfService(
    private val context: Context,
    private val dao: ScreenplayDao? = null
) {
    companion object {
        private const val TAG = "FirestorePdfService"

        // Firestore collection paths
        private const val COLLECTION_PROJECTS = "screenplay_projects"
        private const val SUBCOLLECTION_ELEMENTS = "elements"
        private const val SUBCOLLECTION_STORYBOARDS = "storyboard_shots"
        private const val SUBCOLLECTION_CHARACTERS = "characters"

        // US Letter Standard Dimensions (8.5" x 11" @ 72 DPI points)
        const val PAGE_WIDTH = 612
        const val PAGE_HEIGHT = 792

        // Industry Standard Screenplay Margins (in 72 pt/inch)
        // Left margin: 1.5 inches for three-hole punch / script brad binding
        const val MARGIN_LEFT = 108f
        // Right margin: 1.0 inch
        const val MARGIN_RIGHT = 72f
        // Top margin: 1.0 inch
        const val MARGIN_TOP = 72f
        // Bottom margin: 1.0 inch
        const val MARGIN_BOTTOM = 72f

        // Industry Screenplay Indentations (from left page edge)
        const val INDENT_ACTION = 108f         // 1.5" from left edge
        const val INDENT_CHARACTER = 266f      // ~3.7" from left edge (~38% offset)
        const val INDENT_PARENTHETICAL = 223f  // ~3.1" from left edge
        const val INDENT_DIALOGUE = 180f       // ~2.5" from left edge

        // Standard Printable Column Widths
        const val WIDTH_ACTION = 432f          // 612 - 108 - 72 = 432 pt (6.0")
        const val WIDTH_DIALOGUE = 252f        // ~3.5 inches column width
        const val WIDTH_PARENTHETICAL = 210f   // ~2.9 inches
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
                FirebaseFirestore.getInstance()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falling back to default Firestore instance", e)
            FirebaseFirestore.getInstance()
        }
    }

    /**
     * Generates a standard industry screenplay PDF by querying screenplay data from Firestore.
     * If cloud data is not yet populated or offline, seamlessly falls back to local database.
     */
    suspend fun generateScreenplayPdfFromFirestore(
        projectId: Long,
        config: ScreenplayPdfConfig = ScreenplayPdfConfig(),
        onProgress: (String) -> Unit = {}
    ): FirestorePdfResult = withContext(Dispatchers.IO) {
        try {
            onProgress("Connecting to Firestore...")
            var dataSource = "Cloud Firestore"

            // 1. Query project metadata from Firestore
            var project = fetchProjectFromFirestore(projectId)
            if (project == null && dao != null) {
                onProgress("Using local project data...")
                project = dao.getProjectByIdOnce(projectId)
                dataSource = "Local Database (Fallback)"
            }

            if (project == null) {
                return@withContext FirestorePdfResult.Error("Screenplay project #$projectId not found in Firestore or local database.")
            }

            // 2. Query screenplay elements ordered by orderIndex from Firestore
            onProgress("Fetching script beats from Firestore...")
            var elements = fetchElementsFromFirestore(projectId)
            if (elements.isEmpty() && dao != null) {
                elements = dao.getElementsByProjectOnce(projectId)
                if (elements.isNotEmpty()) {
                    dataSource = "Local Database (Fallback)"
                }
            }

            if (elements.isEmpty()) {
                return@withContext FirestorePdfResult.Error("Screenplay contains no script beats. Add scenes or dialogue before exporting.")
            }

            onProgress("Formatting into Courier 12pt industry layout (${elements.size} beats)...")

            // 3. Compile into industry-standard PDF document
            val (file, totalPages) = renderScreenplayPdf(
                project = project,
                elements = elements,
                config = config,
                sourceBadge = dataSource
            )

            onProgress("Export complete! ($totalPages pages)")
            FirestorePdfResult.Success(
                file = file,
                totalPages = totalPages,
                elementsCount = elements.size,
                source = dataSource
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating screenplay PDF from Firestore", e)
            FirestorePdfResult.Error("Failed to export PDF: ${e.localizedMessage ?: "Unknown error"}", e)
        }
    }

    /**
     * Generates a visual Storyboard Deck PDF using shot lists stored in Firestore.
     */
    suspend fun generateStoryboardPdfFromFirestore(
        projectId: Long,
        onProgress: (String) -> Unit = {}
    ): FirestorePdfResult = withContext(Dispatchers.IO) {
        try {
            onProgress("Fetching storyboard shots from Firestore...")
            var dataSource = "Cloud Firestore"

            var project = fetchProjectFromFirestore(projectId)
            if (project == null && dao != null) {
                project = dao.getProjectByIdOnce(projectId)
                dataSource = "Local Database (Fallback)"
            }

            if (project == null) {
                return@withContext FirestorePdfResult.Error("Project #$projectId not found.")
            }

            var shots = fetchStoryboardsFromFirestore(projectId)
            if (shots.isEmpty() && dao != null) {
                shots = dao.getStoryboardsByProjectOnce(projectId)
                if (shots.isNotEmpty()) dataSource = "Local Database (Fallback)"
            }

            if (shots.isEmpty()) {
                return@withContext FirestorePdfResult.Error("No storyboard shots found in Firestore. Create shots before exporting.")
            }

            onProgress("Rendering ${shots.size} storyboard frame cards...")
            val (file, totalPages) = renderStoryboardPdf(project, shots, dataSource)

            onProgress("Storyboard Deck PDF generated!")
            FirestorePdfResult.Success(
                file = file,
                totalPages = totalPages,
                elementsCount = shots.size,
                source = dataSource
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating storyboard PDF from Firestore", e)
            FirestorePdfResult.Error("Storyboard export failed: ${e.localizedMessage}", e)
        }
    }

    /**
     * Generates a Character Bible & Casting Dossier PDF from Firestore.
     */
    suspend fun generateCharacterBiblePdfFromFirestore(
        projectId: Long,
        onProgress: (String) -> Unit = {}
    ): FirestorePdfResult = withContext(Dispatchers.IO) {
        try {
            onProgress("Fetching character bible from Firestore...")
            var dataSource = "Cloud Firestore"

            var project = fetchProjectFromFirestore(projectId)
            if (project == null && dao != null) {
                project = dao.getProjectByIdOnce(projectId)
                dataSource = "Local Database (Fallback)"
            }

            if (project == null) {
                return@withContext FirestorePdfResult.Error("Project #$projectId not found.")
            }

            var characters = fetchCharactersFromFirestore(projectId)
            if (characters.isEmpty() && dao != null) {
                characters = dao.getCharactersByProjectOnce(projectId)
                if (characters.isNotEmpty()) dataSource = "Local Database (Fallback)"
            }

            if (characters.isEmpty()) {
                return@withContext FirestorePdfResult.Error("No character profiles found in Firestore.")
            }

            onProgress("Rendering ${characters.size} character dossiers...")
            val (file, totalPages) = renderCharacterBiblePdf(project, characters, dataSource)

            onProgress("Character Dossier PDF ready!")
            FirestorePdfResult.Success(
                file = file,
                totalPages = totalPages,
                elementsCount = characters.size,
                source = dataSource
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating character dossier PDF from Firestore", e)
            FirestorePdfResult.Error("Character Dossier export failed: ${e.localizedMessage}", e)
        }
    }

    // =========================================================================
    // Firestore Query Implementations
    // =========================================================================

    private suspend fun fetchProjectFromFirestore(projectId: Long): ScreenplayProject? {
        return try {
            val doc = firestore.collection(COLLECTION_PROJECTS)
                .document(projectId.toString())
                .get()
                .await()

            if (doc.exists()) mapDocumentToProject(doc) else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch project #$projectId from Firestore", e)
            null
        }
    }

    private suspend fun fetchElementsFromFirestore(projectId: Long): List<ScreenplayElement> {
        return try {
            val snapshot = firestore.collection(COLLECTION_PROJECTS)
                .document(projectId.toString())
                .collection(SUBCOLLECTION_ELEMENTS)
                .orderBy("orderIndex", Query.Direction.ASCENDING)
                .get()
                .await()

            snapshot.documents.mapNotNull { mapDocumentToElement(it, projectId) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch elements for project #$projectId from Firestore", e)
            emptyList()
        }
    }

    private suspend fun fetchStoryboardsFromFirestore(projectId: Long): List<StoryboardShot> {
        return try {
            val snapshot = firestore.collection(COLLECTION_PROJECTS)
                .document(projectId.toString())
                .collection(SUBCOLLECTION_STORYBOARDS)
                .orderBy("orderIndex", Query.Direction.ASCENDING)
                .get()
                .await()

            snapshot.documents.mapNotNull { mapDocumentToStoryboard(it, projectId) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch storyboard shots from Firestore", e)
            emptyList()
        }
    }

    private suspend fun fetchCharactersFromFirestore(projectId: Long): List<CharacterProfile> {
        return try {
            val snapshot = firestore.collection(COLLECTION_PROJECTS)
                .document(projectId.toString())
                .collection(SUBCOLLECTION_CHARACTERS)
                .get()
                .await()

            snapshot.documents.mapNotNull { mapDocumentToCharacter(it, projectId) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch character profiles from Firestore", e)
            emptyList()
        }
    }

    // =========================================================================
    // Industry Layout Rendering Engine (US Letter, Courier 12pt Standard)
    // =========================================================================

    private fun renderScreenplayPdf(
        project: ScreenplayProject,
        elements: List<ScreenplayElement>,
        config: ScreenplayPdfConfig,
        sourceBadge: String
    ): Pair<File, Int> {
        val pdfDocument = PdfDocument()

        // Industry Standard Typography Paints
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = 10f // Standard Courier 12pt equivalent at 72dpi
            color = Color.BLACK
        }

        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 10f
            color = Color.BLACK
        }

        val headerPageNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = 9.5f
            color = Color.BLACK
            textAlign = Paint.Align.RIGHT
        }

        val headerDraftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = 8f
            color = Color.GRAY
            textAlign = Paint.Align.LEFT
        }

        val titlePageTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 22f
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
        }

        val titlePageAuthorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }

        val footerInfoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = 9.5f
            color = Color.BLACK
        }

        val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 34f
            color = Color.argb(28, 0, 0, 0) // Subtle watermark
            textAlign = Paint.Align.CENTER
        }

        val lineHeight = 13f
        var totalPages = 0
        var pageNumber = 1

        // ---------------------------------------------------------------------
        // 1. Standard Title Page (WGA / AMPAS format)
        // ---------------------------------------------------------------------
        if (config.includeTitlePage) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // Script Title in centered uppercase
            val titleY = 260f
            val formattedTitle = "\"${project.title.uppercase(Locale.ROOT)}\""
            canvas.drawText(formattedTitle, PAGE_WIDTH / 2f, titleY, titlePageTitlePaint)

            // "written by"
            canvas.drawText("written by", PAGE_WIDTH / 2f, titleY + 45f, titlePageAuthorPaint)

            // Author Name
            val authorPaint = Paint(titlePageAuthorPaint).apply {
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textSize = 13f
            }
            canvas.drawText(project.author, PAGE_WIDTH / 2f, titleY + 70f, authorPaint)

            // Based on (if applicable)
            if (project.basedOn.isNotBlank()) {
                canvas.drawText("based on", PAGE_WIDTH / 2f, titleY + 115f, titlePageAuthorPaint)
                canvas.drawText(project.basedOn, PAGE_WIDTH / 2f, titleY + 135f, titlePageAuthorPaint)
            }

            // Lower Left: Draft information & revision date
            val draftColor = config.draftRevisionColor ?: project.draftColor
            val draftText = "${project.draftName} ($draftColor Revision)"
            val dateStr = SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date(project.updatedAt))

            canvas.drawText(draftText, MARGIN_LEFT, PAGE_HEIGHT - 130f, footerInfoPaint)
            canvas.drawText(dateStr, MARGIN_LEFT, PAGE_HEIGHT - 115f, footerInfoPaint)
            canvas.drawText("Source: $sourceBadge", MARGIN_LEFT, PAGE_HEIGHT - 100f, Paint(footerInfoPaint).apply {
                textSize = 8f
                color = Color.DKGRAY
            })

            // Lower Right: Contact Info
            val contactLines = if (project.contactInfo.isNotBlank()) {
                project.contactInfo.split("\n")
            } else {
                listOf(project.author, "CINEAST CLOUD HUB")
            }

            var contactY = PAGE_HEIGHT - 130f
            val contactX = PAGE_WIDTH - MARGIN_RIGHT - 180f
            for (line in contactLines) {
                canvas.drawText(line, contactX, contactY, footerInfoPaint)
                contactY += 14f
            }

            pdfDocument.finishPage(page)
            totalPages++
            pageNumber++
        }

        // ---------------------------------------------------------------------
        // 2. Script Content Pages (Courier Monospace with Strict Margins)
        // ---------------------------------------------------------------------
        var currentScriptPageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var currentPage = pdfDocument.startPage(pageInfo)
        var canvas = currentPage.canvas
        var currentY = MARGIN_TOP + 15f

        fun drawWatermark(c: Canvas) {
            config.watermarkText?.let { wm ->
                c.save()
                c.rotate(-45f, PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f)
                c.drawText(wm.uppercase(Locale.ROOT), PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f, watermarkPaint)
                c.restore()
            }
        }

        fun startNewPage() {
            drawWatermark(canvas)
            pdfDocument.finishPage(currentPage)
            totalPages++
            pageNumber++
            currentScriptPageNum++

            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            currentPage = pdfDocument.startPage(pageInfo)
            canvas = currentPage.canvas
            currentY = MARGIN_TOP

            // Industry Rule: Top-Right header starting from page 2: "2."
            // Flush right with right margin, 0.5" (36pt) from top
            canvas.drawText("$currentScriptPageNum.", PAGE_WIDTH - MARGIN_RIGHT, 36f, headerPageNumPaint)

            if (config.includeProductionHeader) {
                val headerText = "${project.title.uppercase(Locale.ROOT)} • ${project.draftName}"
                canvas.drawText(headerText, MARGIN_LEFT, 36f, headerDraftPaint)
            }

            currentY += 10f
        }

        // Process script elements
        for (i in elements.indices) {
            val elem = elements[i]
            val type = ScreenplayElementType.values().find { it.name == elem.elementType }
                ?: ScreenplayElementType.ACTION

            when (type) {
                ScreenplayElementType.SCENE_HEADING -> {
                    // Double space before scene heading
                    currentY += lineHeight * 1.5f

                    // Orphan prevention: don't leave heading alone at bottom
                    if (currentY > PAGE_HEIGHT - MARGIN_BOTTOM - 55f) {
                        startNewPage()
                    }

                    val sceneNum = elem.sceneNumber ?: 1
                    if (config.includeSceneNumbers) {
                        // Left scene number (flush left outside left margin)
                        canvas.drawText("$sceneNum", MARGIN_LEFT - 35f, currentY, boldPaint)
                    }

                    // Scene slugline in uppercase bold
                    canvas.drawText(elem.content.uppercase(Locale.ROOT), INDENT_ACTION, currentY, boldPaint)

                    if (config.includeSceneNumbers) {
                        // Right scene number (flush right outside right margin)
                        canvas.drawText("$sceneNum", PAGE_WIDTH - MARGIN_RIGHT + 15f, currentY, boldPaint)
                    }

                    currentY += lineHeight * 1.3f
                }

                ScreenplayElementType.ACTION -> {
                    val wrappedLines = wrapText(elem.content, WIDTH_ACTION, textPaint)
                    if (currentY + (wrappedLines.size * lineHeight) > PAGE_HEIGHT - MARGIN_BOTTOM) {
                        startNewPage()
                    }
                    for (line in wrappedLines) {
                        canvas.drawText(line, INDENT_ACTION, currentY, textPaint)
                        currentY += lineHeight
                    }
                    currentY += lineHeight * 0.5f
                }

                ScreenplayElementType.CHARACTER -> {
                    currentY += lineHeight * 0.7f

                    // Orphan prevention: ensure character cue and at least one dialogue line fit
                    if (currentY > PAGE_HEIGHT - MARGIN_BOTTOM - 45f) {
                        startNewPage()
                    }

                    canvas.drawText(elem.content.uppercase(Locale.ROOT), INDENT_CHARACTER, currentY, boldPaint)
                    currentY += lineHeight
                }

                ScreenplayElementType.PARENTHETICAL -> {
                    if (currentY > PAGE_HEIGHT - MARGIN_BOTTOM - 30f) {
                        startNewPage()
                    }
                    val formatted = if (elem.content.startsWith("(") && elem.content.endsWith(")")) {
                        elem.content
                    } else {
                        "(${elem.content.trim()})"
                    }
                    val wrappedParen = wrapText(formatted, WIDTH_PARENTHETICAL, textPaint)
                    for (line in wrappedParen) {
                        canvas.drawText(line, INDENT_PARENTHETICAL, currentY, textPaint)
                        currentY += lineHeight
                    }
                }

                ScreenplayElementType.DIALOGUE -> {
                    val wrappedLines = wrapText(elem.content, WIDTH_DIALOGUE, textPaint)
                    if (currentY + (wrappedLines.size * lineHeight) > PAGE_HEIGHT - MARGIN_BOTTOM) {
                        startNewPage()
                    }
                    for (line in wrappedLines) {
                        canvas.drawText(line, INDENT_DIALOGUE, currentY, textPaint)
                        currentY += lineHeight
                    }
                    currentY += lineHeight * 0.6f
                }

                ScreenplayElementType.TRANSITION -> {
                    currentY += lineHeight * 0.8f
                    if (currentY > PAGE_HEIGHT - MARGIN_BOTTOM - 30f) {
                        startNewPage()
                    }
                    val transPaint = Paint(boldPaint).apply {
                        textAlign = Paint.Align.RIGHT
                    }
                    canvas.drawText(elem.content.uppercase(Locale.ROOT), PAGE_WIDTH - MARGIN_RIGHT, currentY, transPaint)
                    currentY += lineHeight * 1.2f
                }

                ScreenplayElementType.SHOT -> {
                    if (currentY > PAGE_HEIGHT - MARGIN_BOTTOM - 30f) {
                        startNewPage()
                    }
                    canvas.drawText(elem.content.uppercase(Locale.ROOT), INDENT_ACTION, currentY, boldPaint)
                    currentY += lineHeight * 1.2f
                }
            }
        }

        // Draw watermark on last page
        drawWatermark(canvas)
        pdfDocument.finishPage(currentPage)
        totalPages++

        // Save PDF file to cache directory
        val outputDir = File(context.cacheDir, "exports")
        if (!outputDir.exists()) outputDir.mkdirs()

        val safeTitle = project.title.replace("[^a-zA-Z0-9]".toRegex(), "_").lowercase(Locale.ROOT)
        val outputFile = File(outputDir, "${safeTitle}_screenplay.pdf")

        FileOutputStream(outputFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return Pair(outputFile, totalPages)
    }

    private fun renderStoryboardPdf(
        project: ScreenplayProject,
        shots: List<StoryboardShot>,
        sourceBadge: String
    ): Pair<File, Int> {
        val pdfDocument = PdfDocument()

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 18f
            color = Color.BLACK
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT
            textSize = 10f
            color = Color.DKGRAY
        }
        val cardTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 11.5f
            color = Color.BLACK
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT
            textSize = 9.5f
            color = Color.BLACK
        }
        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.5f
            color = Color.rgb(30, 64, 175)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val frameBoxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.LTGRAY
        }
        val frameFillPaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.rgb(245, 245, 247)
        }
        val frameLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f
            color = Color.GRAY
            textAlign = Paint.Align.CENTER
        }

        var pageIndex = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        canvas.drawText("${project.title.uppercase(Locale.ROOT)} - STORYBOARD DECK", MARGIN_RIGHT, 50f, titlePaint)
        canvas.drawText("Total Shots: ${shots.size} | Source: $sourceBadge", MARGIN_RIGHT, 68f, headerPaint)

        var cardY = 90f
        val cardHeight = 145f

        for (shot in shots) {
            if (cardY + cardHeight > PAGE_HEIGHT - 50f) {
                pdfDocument.finishPage(page)
                pageIndex++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                cardY = 50f
            }

            val cardLeft = 40f
            val cardRight = PAGE_WIDTH - 40f
            val cardBottom = cardY + cardHeight

            // 16:9 visual frame box
            val frameW = 160f
            val frameH = 90f
            val frameRect = Rect(cardLeft.toInt() + 10, cardY.toInt() + 10, (cardLeft + 10 + frameW).toInt(), (cardY + 10 + frameH).toInt())
            canvas.drawRect(frameRect, frameFillPaint)
            canvas.drawRect(frameRect, frameBoxPaint)

            canvas.drawText("VISUAL FRAME", cardLeft + 10 + frameW / 2f, cardY + 50f, frameLabelPaint)
            canvas.drawText(shot.shotType, cardLeft + 10 + frameW / 2f, cardY + 65f, frameLabelPaint)

            // Shot metadata on right
            val textLeft = cardLeft + 10 + frameW + 20f
            canvas.drawText("[SHOT ${shot.shotNumber}] - ${shot.sceneNumber}", textLeft, cardY + 25f, cardTitlePaint)
            canvas.drawText("${shot.shotType} | ${shot.cameraMovement} | ${shot.lens}", textLeft, cardY + 42f, tagPaint)

            val actionLines = wrapText("Action: ${shot.actionSummary}", (cardRight - textLeft - 10f), bodyPaint)
            var lineY = cardY + 60f
            for (line in actionLines.take(3)) {
                canvas.drawText(line, textLeft, lineY, bodyPaint)
                lineY += 13f
            }

            if (shot.dialogueSnippet.isNotBlank()) {
                val italicPaint = Paint(bodyPaint).apply {
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    color = Color.DKGRAY
                }
                val dialogueLines = wrapText("Cue: ${shot.dialogueSnippet}", (cardRight - textLeft - 10f), italicPaint)
                if (dialogueLines.isNotEmpty()) {
                    canvas.drawText(dialogueLines.first(), textLeft, lineY + 2f, italicPaint)
                }
            }

            val dividerPaint = Paint().apply {
                color = Color.rgb(230, 230, 235)
                strokeWidth = 1f
            }
            canvas.drawLine(cardLeft, cardBottom - 5f, cardRight, cardBottom - 5f, dividerPaint)
            cardY += cardHeight
        }

        pdfDocument.finishPage(page)

        val outputDir = File(context.cacheDir, "exports")
        if (!outputDir.exists()) outputDir.mkdirs()
        val safeTitle = project.title.replace("[^a-zA-Z0-9]".toRegex(), "_").lowercase(Locale.ROOT)
        val file = File(outputDir, "${safeTitle}_storyboard.pdf")
        FileOutputStream(file).use { out -> pdfDocument.writeTo(out) }
        pdfDocument.close()
        return Pair(file, pageIndex)
    }

    private fun renderCharacterBiblePdf(
        project: ScreenplayProject,
        characters: List<CharacterProfile>,
        sourceBadge: String
    ): Pair<File, Int> {
        val pdfDocument = PdfDocument()
        var pageIndex = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 18f
            color = Color.BLACK
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            color = Color.DKGRAY
        }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 13f
            color = Color.BLACK
        }
        val rolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 10f
            color = Color.rgb(180, 83, 9)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.5f
            color = Color.BLACK
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9.5f
            color = Color.DKGRAY
        }
        val dividerPaint = Paint().apply {
            color = Color.rgb(220, 220, 225)
            strokeWidth = 1f
        }

        canvas.drawText("${project.title.uppercase(Locale.ROOT)} - CHARACTER BIBLE", 45f, 50f, titlePaint)
        canvas.drawText("Cast Profiles, Dramatic Arcs & Casting Guide | Source: $sourceBadge", 45f, 68f, subPaint)

        var y = 100f

        for (char in characters) {
            if (y > PAGE_HEIGHT - 130f) {
                pdfDocument.finishPage(page)
                pageIndex++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 60f
            }

            canvas.drawText(char.name.uppercase(Locale.ROOT), 45f, y, namePaint)
            canvas.drawText("ROLE: ${char.role.uppercase(Locale.ROOT)}", 260f, y, rolePaint)
            y += 16f

            canvas.drawText(char.tagline, 45f, y, bodyPaint)
            y += 18f

            canvas.drawText("External Want:", 45f, y, labelPaint)
            canvas.drawText(char.arcWant, 140f, y, bodyPaint)
            y += 15f

            canvas.drawText("Internal Need:", 45f, y, labelPaint)
            canvas.drawText(char.arcNeed, 140f, y, bodyPaint)
            y += 15f

            canvas.drawText("Dialogue Voice:", 45f, y, labelPaint)
            val voiceLines = wrapText(char.dialogueVoice, 420f, bodyPaint)
            for (line in voiceLines) {
                canvas.drawText(line, 140f, y, bodyPaint)
                y += 13f
            }

            canvas.drawText("Casting Archetype:", 45f, y, labelPaint)
            canvas.drawText(char.castingSuggestion, 160f, y, bodyPaint)
            y += 18f

            canvas.drawLine(45f, y, PAGE_WIDTH - 45f, y, dividerPaint)
            y += 20f
        }

        pdfDocument.finishPage(page)

        val outputDir = File(context.cacheDir, "exports")
        if (!outputDir.exists()) outputDir.mkdirs()
        val safeTitle = project.title.replace("[^a-zA-Z0-9]".toRegex(), "_").lowercase(Locale.ROOT)
        val file = File(outputDir, "${safeTitle}_characters.pdf")
        FileOutputStream(file).use { out -> pdfDocument.writeTo(out) }
        pdfDocument.close()
        return Pair(file, pageIndex)
    }

    // =========================================================================
    // Document Mapping Utilities
    // =========================================================================

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
            orderIndex = doc.getLong("orderIndex")?.toInt() ?: 0
        )
    }

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

    private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
        val words = text.split("\\s+".toRegex())
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val potentialLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val textWidth = paint.measureText(potentialLine)
            if (textWidth <= maxWidth) {
                currentLine = StringBuilder(potentialLine)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return if (lines.isEmpty()) listOf("") else lines
    }

    /**
     * Creates an Intent to share the generated PDF file using FileProvider.
     */
    fun createShareIntent(pdfFile: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${pdfFile.nameWithoutExtension.replace("_", " ").uppercase(Locale.ROOT)} [CINEAST HUB]")
            putExtra(Intent.EXTRA_TEXT, "Here is the industry-standard Screenplay PDF exported from CINEAST HUB.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Creates an Intent to view the generated PDF file.
     */
    fun createViewIntent(pdfFile: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
