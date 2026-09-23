package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.CharacterProfile
import com.example.data.model.ScreenplayElement
import com.example.data.model.ScreenplayElementType
import com.example.data.model.ScreenplayProject
import com.example.data.model.StoryboardShot
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ScreenplayPdfExporter {

    private const val PAGE_WIDTH = 612 // 8.5 inches * 72 points
    private const val PAGE_HEIGHT = 792 // 11 inches * 72 points

    // Industry Standard Margins in points (72 pt = 1 inch)
    private const val MARGIN_LEFT = 108f // 1.5 inches left margin for hole-punch/binding
    private const val MARGIN_RIGHT = 72f // 1.0 inch right margin
    private const val MARGIN_TOP = 72f // 1.0 inch top margin
    private const val MARGIN_BOTTOM = 72f // 1.0 inch bottom margin

    // Screenplay Standard Indents from Page Left
    private const val INDENT_ACTION = 108f
    private const val INDENT_CHARACTER = 260f // ~3.7 inches from page left
    private const val INDENT_DIALOGUE = 180f // ~2.5 inches from page left
    private const val INDENT_PARENTHETICAL = 220f // ~3.0 inches from page left

    // Printable widths
    private const val WIDTH_ACTION = 432f // 612 - 108 - 72
    private const val WIDTH_DIALOGUE = 260f // ~3.6 inches
    private const val WIDTH_PARENTHETICAL = 210f

    /**
     * Generates an industry-standard Screenplay PDF with Cover Page, Courier typography,
     * header page counts, scene numbers, and proper indentation.
     */
    fun exportScreenplayToPdf(
        context: Context,
        project: ScreenplayProject,
        elements: List<ScreenplayElement>,
        includeTitlePage: Boolean = true,
        includeSceneNumbers: Boolean = true
    ): File {
        val pdfDocument = PdfDocument()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = 10f
            color = Color.BLACK
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 10f
            color = Color.BLACK
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 20f
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }
        val subTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = 9f
            color = Color.DKGRAY
            textAlign = Paint.Align.RIGHT
        }

        var pageNumber = 1

        // --- 1. Title / Cover Page ---
        if (includeTitlePage) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // Script Title in uppercase
            canvas.drawText(project.title.uppercase(Locale.ROOT), PAGE_WIDTH / 2f, 260f, titlePaint)

            // Written by
            canvas.drawText("written by", PAGE_WIDTH / 2f, 310f, subTitlePaint)
            canvas.drawText(project.author, PAGE_WIDTH / 2f, 335f, subTitlePaint)

            if (project.basedOn.isNotBlank()) {
                canvas.drawText("based on", PAGE_WIDTH / 2f, 375f, subTitlePaint)
                canvas.drawText(project.basedOn, PAGE_WIDTH / 2f, 395f, subTitlePaint)
            }

            // Draft info & contact at bottom
            val bottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.MONOSPACE
                textSize = 9.5f
                color = Color.BLACK
            }

            val dateStr = SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date(project.updatedAt))
            canvas.drawText("Draft: ${project.draftName} (${project.draftColor})", MARGIN_LEFT, PAGE_HEIGHT - 130f, bottomPaint)
            canvas.drawText("Date: $dateStr", MARGIN_LEFT, PAGE_HEIGHT - 115f, bottomPaint)

            val contactLines = project.contactInfo.split("\n")
            var contactY = PAGE_HEIGHT - 130f
            for (line in contactLines) {
                canvas.drawText(line, PAGE_WIDTH - MARGIN_RIGHT - 180f, contactY, bottomPaint)
                contactY += 14f
            }

            pdfDocument.finishPage(page)
            pageNumber++
        }

        // --- 2. Screenplay Script Pages ---
        var currentScriptPageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var currentPage = pdfDocument.startPage(pageInfo)
        var canvas = currentPage.canvas
        var currentY = MARGIN_TOP + 15f

        val lineHeight = 13f

        fun startNewPage() {
            pdfDocument.finishPage(currentPage)
            pageNumber++
            currentScriptPageNum++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            currentPage = pdfDocument.startPage(pageInfo)
            canvas = currentPage.canvas
            currentY = MARGIN_TOP

            // Draw Top-Right Page Header: "2."
            canvas.drawText("$currentScriptPageNum.", PAGE_WIDTH - MARGIN_RIGHT, MARGIN_TOP - 20f, headerPaint)
            currentY += 10f
        }

        for (elem in elements) {
            when (elem.elementType) {
                ScreenplayElementType.SCENE_HEADING.name -> {
                    // Double space before scene heading
                    currentY += lineHeight * 1.5f
                    if (currentY > PAGE_HEIGHT - MARGIN_BOTTOM - 40f) {
                        startNewPage()
                    }

                    val sceneNum = elem.sceneNumber ?: 1
                    if (includeSceneNumbers) {
                        // Left scene number
                        canvas.drawText("$sceneNum", MARGIN_LEFT - 35f, currentY, boldPaint)
                    }

                    canvas.drawText(elem.content.uppercase(Locale.ROOT), INDENT_ACTION, currentY, boldPaint)

                    if (includeSceneNumbers) {
                        // Right scene number
                        canvas.drawText("$sceneNum", PAGE_WIDTH - MARGIN_RIGHT + 15f, currentY, boldPaint)
                    }

                    currentY += lineHeight * 1.3f
                }

                ScreenplayElementType.ACTION.name -> {
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

                ScreenplayElementType.CHARACTER.name -> {
                    currentY += lineHeight * 0.7f
                    if (currentY > PAGE_HEIGHT - MARGIN_BOTTOM - 35f) {
                        startNewPage()
                    }
                    canvas.drawText(elem.content.uppercase(Locale.ROOT), INDENT_CHARACTER, currentY, boldPaint)
                    currentY += lineHeight
                }

                ScreenplayElementType.PARENTHETICAL.name -> {
                    if (currentY > PAGE_HEIGHT - MARGIN_BOTTOM - 25f) {
                        startNewPage()
                    }
                    val formattedParen = if (elem.content.startsWith("(") && elem.content.endsWith(")")) {
                        elem.content
                    } else {
                        "(${elem.content.trim()})"
                    }
                    canvas.drawText(formattedParen, INDENT_PARENTHETICAL, currentY, textPaint)
                    currentY += lineHeight
                }

                ScreenplayElementType.DIALOGUE.name -> {
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

                ScreenplayElementType.TRANSITION.name -> {
                    currentY += lineHeight * 0.8f
                    if (currentY > PAGE_HEIGHT - MARGIN_BOTTOM - 25f) {
                        startNewPage()
                    }
                    val transPaint = Paint(boldPaint).apply {
                        textAlign = Paint.Align.RIGHT
                    }
                    canvas.drawText(elem.content.uppercase(Locale.ROOT), PAGE_WIDTH - MARGIN_RIGHT, currentY, transPaint)
                    currentY += lineHeight * 1.2f
                }

                else -> {
                    // SHOT or other
                    if (currentY > PAGE_HEIGHT - MARGIN_BOTTOM - 25f) {
                        startNewPage()
                    }
                    canvas.drawText(elem.content.uppercase(Locale.ROOT), INDENT_ACTION, currentY, boldPaint)
                    currentY += lineHeight * 1.2f
                }
            }
        }

        pdfDocument.finishPage(currentPage)

        // Save PDF to cache dir
        val outputDir = File(context.cacheDir, "exports")
        if (!outputDir.exists()) outputDir.mkdirs()
        val safeTitle = project.title.replace("\\s+".toRegex(), "_").lowercase(Locale.ROOT)
        val file = File(outputDir, "${safeTitle}_screenplay.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return file
    }

    /**
     * Generates a Storyboard Deck PDF listing all visual shots, camera specs, and action descriptions.
     */
    fun exportStoryboardToPdf(
        context: Context,
        project: ScreenplayProject,
        shots: List<StoryboardShot>
    ): File {
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
            textSize = 12f
            color = Color.BLACK
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT
            textSize = 9.5f
            color = Color.BLACK
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

        var pageIndex = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        // Header
        canvas.drawText("${project.title.uppercase(Locale.ROOT)} - STORYBOARD DECK", MARGIN_RIGHT, 50f, titlePaint)
        canvas.drawText("Total Shots: ${shots.size} | Director & Cinematography Breakdown", MARGIN_RIGHT, 68f, headerPaint)

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

            // Draw Card Container
            val cardLeft = 40f
            val cardRight = PAGE_WIDTH - 40f
            val cardBottom = cardY + cardHeight

            // Visual Frame Preview Box (16:9 ratio box)
            val frameW = 160f
            val frameH = 90f
            val frameRect = Rect(cardLeft.toInt() + 10, cardY.toInt() + 10, (cardLeft + 10 + frameW).toInt(), (cardY + 10 + frameH).toInt())

            var imageDrawn = false
            if (!shot.imageUri.isNullOrBlank()) {
                val imgFile = File(shot.imageUri)
                if (imgFile.exists()) {
                    try {
                        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                        val bmp = BitmapFactory.decodeFile(imgFile.absolutePath, options)
                        if (bmp != null) {
                            canvas.drawBitmap(bmp, null, frameRect, null)
                            bmp.recycle()
                            imageDrawn = true
                        }
                    } catch (e: Exception) {
                        // ignore and fall back to sketch placeholder
                    }
                }
            }

            if (!imageDrawn) {
                canvas.drawRect(frameRect, frameFillPaint)
                canvas.drawRect(frameRect, frameBoxPaint)

                val frameLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 9f
                    color = Color.GRAY
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("VISUAL FRAME (16:9)", cardLeft + 10 + frameW / 2f, cardY + 50f, frameLabelPaint)
                canvas.drawText(shot.shotType, cardLeft + 10 + frameW / 2f, cardY + 65f, frameLabelPaint)
            } else {
                canvas.drawRect(frameRect, frameBoxPaint)
            }

            // Details on the right
            val textLeft = cardLeft + 10 + frameW + 20f
            canvas.drawText("[SHOT ${shot.shotNumber}] - ${shot.sceneNumber}", textLeft, cardY + 25f, cardTitlePaint)

            val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 9.5f
                color = Color.rgb(30, 64, 175)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
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

            // Divider line
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
        val file = File(outputDir, "${project.title.replace(" ", "_").lowercase(Locale.ROOT)}_storyboard.pdf")
        FileOutputStream(file).use { out -> pdfDocument.writeTo(out) }
        pdfDocument.close()
        return file
    }

    /**
     * Generates a Character Dossier / Bible PDF with psychological profiles, arcs, and dialogue notes.
     */
    fun exportCharacterBibleToPdf(
        context: Context,
        project: ScreenplayProject,
        characters: List<CharacterProfile>
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

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
            color = Color.rgb(180, 83, 9) // Amber/Gold
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

        canvas.drawText("${project.title.uppercase(Locale.ROOT)} - CHARACTER BIBLE", 45f, 50f, titlePaint)
        canvas.drawText("Character Arcs, Motivations, Dialogue Cadence & Casting Guide", 45f, 68f, subPaint)

        var y = 100f
        val dividerPaint = Paint().apply {
            color = Color.rgb(220, 220, 225)
            strokeWidth = 1f
        }

        for (char in characters) {
            if (y > PAGE_HEIGHT - 120f) break

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
        val file = File(outputDir, "${project.title.replace(" ", "_").lowercase(Locale.ROOT)}_characters.pdf")
        FileOutputStream(file).use { out -> pdfDocument.writeTo(out) }
        pdfDocument.close()
        return file
    }

    /**
     * Creates an Intent to share or open the generated PDF file.
     */
    fun createShareIntent(context: Context, pdfFile: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, pdfFile.nameWithoutExtension.replace("_", " ").uppercase(Locale.ROOT))
            putExtra(Intent.EXTRA_TEXT, "Here is the industry-standard PDF export from CINEAST HUB.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createViewIntent(context: Context, pdfFile: File): Intent {
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
}
