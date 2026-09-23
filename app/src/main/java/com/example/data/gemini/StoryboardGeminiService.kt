package com.example.data.gemini

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

enum class CinematicStylePreset(
    val displayName: String,
    val promptSuffix: String,
    val primaryColor: Int,
    val secondaryColor: Int
) {
    FILM_NOIR(
        "Film Noir",
        "cinematic film noir storyboard frame, dramatic high-contrast chiaroscuro lighting, venetian blind shadows, 35mm monochrome grain, moody mystery atmosphere, 16:9 widescreen composition",
        0xFF1A1A24.toInt(),
        0xFF0A0A0E.toInt()
    ),
    CYBERPUNK_NEON(
        "Cyberpunk Neon",
        "cyberpunk cinematic storyboard frame, rain-slicked wet pavement reflections, intense neon cyan and hot magenta lighting, futuristic atmospheric haze, 16:9 anamorphic cinematic aspect",
        0xFF0F172A.toInt(),
        0xFF050510.toInt()
    ),
    CINEMATIC_35MM(
        "35mm Panavision",
        "shot on 35mm Panavision anamorphic film, rich cinematic color grading, shallow depth of field, natural film grain, professional film production storyboard concept art, 16:9 widescreen",
        0xFF1C1917.toInt(),
        0xFF0C0A09.toInt()
    ),
    WARM_GOLDEN_HOUR(
        "Golden Hour",
        "magic hour golden sunset lighting, warm orange rim light, deep emotional shadows, anamorphic lens flare, cinematic prestige drama storyboard visual, 16:9",
        0xFF291E10.toInt(),
        0xFF0F0B05.toInt()
    ),
    CHARCOAL_SKETCH(
        "Charcoal Storyboard",
        "expressive charcoal and ink film production storyboard sketch, rough pencil gesture lines, architectural perspective drawing, dynamic composition, feature film previsualization, 16:9",
        0xFF1E2022.toInt(),
        0xFF121314.toInt()
    ),
    SCI_FI_ANAMORPHIC(
        "Sci-Fi Anamorphic",
        "hard sci-fi cinematic concept art frame, horizontal blue anamorphic streak flares, stark volumetric lighting, gritty industrial texture, high production value movie pre-vis, 16:9",
        0xFF0E1A24.toInt(),
        0xFF060B10.toInt()
    )
}

class StoryboardGeminiService(private val context: Context) {

    private val tag = "StoryboardGemini"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val storageDir: File by lazy {
        File(context.filesDir, "storyboard_images").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Checks if the Gemini API Key is configured and valid
     */
    fun hasValidApiKey(): Boolean {
        val key = getApiKey()
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY" && key != "YOUR_GEMINI_API_KEY"
    }

    private fun getApiKey(): String {
        return try {
            BuildConfig.GEMINI_API_KEY ?: ""
        } catch (e: Throwable) {
            ""
        }
    }

    /**
     * Generates a 16:9 visual storyboard image using Gemini (model gemini-2.5-flash-image).
     * If the API key is missing or call fails, falls back gracefully to generating a stylized
     * cinematic concept board sketch.
     */
    suspend fun generateStoryboardImage(
        prompt: String,
        sceneHeading: String? = null,
        shotType: String = "Wide Shot (WS)",
        lens: String = "35mm Cine",
        cameraMovement: String = "Static",
        stylePreset: CinematicStylePreset = CinematicStylePreset.FILM_NOIR,
        forceOfflineFallback: Boolean = false
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()

        if (forceOfflineFallback || apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.i(tag, "Generating offline concept storyboard sketch (API key not provided or fallback requested)")
            val localPath = generateOfflineConceptSketch(
                sceneHeading = sceneHeading ?: "SCENE SHOT",
                shotType = shotType,
                lens = lens,
                cameraMovement = cameraMovement,
                actionSummary = prompt,
                stylePreset = stylePreset
            )
            val info = if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                "Offline Concept Sketch (Add GEMINI_API_KEY in Secrets for AI Image Generation)"
            } else {
                "Offline Concept Sketch"
            }
            return@withContext Result.success(Pair(localPath, info))
        }

        try {
            val fullPrompt = buildString {
                append("A professional cinematic 16:9 widescreen film storyboard frame illustration: ")
                append(prompt.trim())
                if (!sceneHeading.isNullOrBlank()) {
                    append(". Screenplay Scene: ").append(sceneHeading.trim())
                }
                append(". Camera: ").append(shotType).append(", Lens: ").append(lens)
                append(", Movement: ").append(cameraMovement)
                append(". Style: ").append(stylePreset.promptSuffix)
                append(". High cinematic quality, storyboard previsualization art, 16:9 aspect ratio.")
            }

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", fullPrompt)
                            })
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)

                val generationConfig = JSONObject().apply {
                    val imageConfig = JSONObject().apply {
                        put("aspectRatio", "16:9")
                        put("imageSize", "1K")
                    }
                    put("imageConfig", imageConfig)
                    put("responseModalities", JSONArray().apply {
                        put("TEXT")
                        put("IMAGE")
                    })
                }
                put("generationConfig", generationConfig)
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent?key=$apiKey"
            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.w(tag, "Gemini image API returned code ${response.code}: $responseBody")
                // Fall back gracefully so the user is never blocked
                val localPath = generateOfflineConceptSketch(
                    sceneHeading = sceneHeading ?: "SCENE SHOT",
                    shotType = shotType,
                    lens = lens,
                    cameraMovement = cameraMovement,
                    actionSummary = prompt,
                    stylePreset = stylePreset
                )
                return@withContext Result.success(Pair(localPath, "Concept Sketch (Gemini API: HTTP ${response.code})"))
            }

            val responseJson = JSONObject(responseBody)
            val candidates = responseJson.optJSONArray("candidates")
            var base64Data: String? = null

            if (candidates != null && candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        val inlineData = part.optJSONObject("inlineData")
                        if (inlineData != null) {
                            val data = inlineData.optString("data")
                            if (!data.isNullOrBlank()) {
                                base64Data = data
                                break
                            }
                        }
                    }
                }
            }

            if (!base64Data.isNullOrBlank()) {
                val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

                val imageFile = File(storageDir, "gemini_shot_${System.currentTimeMillis()}.png")
                FileOutputStream(imageFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                }
                bitmap.recycle()
                Result.success(Pair(imageFile.absolutePath, "Generated via Gemini 2.5 Flash Image"))
            } else {
                Log.w(tag, "No inlineData found in Gemini response; creating concept sketch")
                val localPath = generateOfflineConceptSketch(
                    sceneHeading = sceneHeading ?: "SCENE SHOT",
                    shotType = shotType,
                    lens = lens,
                    cameraMovement = cameraMovement,
                    actionSummary = prompt,
                    stylePreset = stylePreset
                )
                Result.success(Pair(localPath, "Concept Sketch (No image part returned)"))
            }
        } catch (e: Exception) {
            Log.e(tag, "Error generating image with Gemini", e)
            val localPath = generateOfflineConceptSketch(
                sceneHeading = sceneHeading ?: "SCENE SHOT",
                shotType = shotType,
                lens = lens,
                cameraMovement = cameraMovement,
                actionSummary = prompt,
                stylePreset = stylePreset
            )
            Result.success(Pair(localPath, "Concept Sketch (${e.localizedMessage ?: "Offline fallback"})"))
        }
    }

    /**
     * Uses Gemini 3.5 Flash to craft a rich cinematic prompt based on script scene elements.
     */
    suspend fun craftCinematicPromptWithGemini(
        sceneHeading: String,
        shotType: String,
        lens: String,
        cameraMovement: String,
        actionSummary: String,
        dialogueSnippet: String = "",
        stylePreset: CinematicStylePreset = CinematicStylePreset.FILM_NOIR
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            // Return locally assembled cinematic prompt
            val localPrompt = buildString {
                append("${shotType} with ${lens}, ${cameraMovement.lowercase()}. ")
                if (actionSummary.isNotBlank()) append("${actionSummary.trim()}. ")
                if (dialogueSnippet.isNotBlank()) append("Character cue: \"${dialogueSnippet.trim()}\". ")
                append("Atmosphere: ${stylePreset.displayName} style, cinematic lighting and depth of field.")
            }
            return@withContext Result.success(localPrompt)
        }

        try {
            val userInstruction = buildString {
                append("You are an expert Hollywood cinematographer and storyboard director.\n")
                append("Create an evocative, concise visual prompt (under 50 words) for a 16:9 storyboard frame illustration.\n")
                append("Script Scene: $sceneHeading\n")
                append("Shot Framing: $shotType\n")
                append("Camera Lens: $lens\n")
                append("Camera Movement: $cameraMovement\n")
                append("Action / Subject: $actionSummary\n")
                if (dialogueSnippet.isNotBlank()) {
                    append("Dialogue Cue: $dialogueSnippet\n")
                }
                append("Visual Style: ${stylePreset.displayName} (${stylePreset.promptSuffix})\n")
                append("Output ONLY the visual art prompt description text, nothing else.")
            }

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", userInstruction)
                            })
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val responseJson = JSONObject(responseBody)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val text = parts?.optJSONObject(0)?.optString("text")
                    if (!text.isNullOrBlank()) {
                        return@withContext Result.success(text.trim())
                    }
                }
            }

            // Fallback if parsing or API returned non-200
            val fallbackPrompt = "${shotType} in ${sceneHeading}. ${actionSummary.trim()}. ${stylePreset.displayName} lighting with ${lens}."
            Result.success(fallbackPrompt)
        } catch (e: Exception) {
            Log.e(tag, "Error crafting prompt with Gemini", e)
            val fallbackPrompt = "${shotType} in ${sceneHeading}. ${actionSummary.trim()}. ${stylePreset.displayName} lighting with ${lens}."
            Result.success(fallbackPrompt)
        }
    }

    /**
     * Handles saving an image uploaded by the user from Photo Picker into app storage.
     */
    suspend fun saveUploadedImage(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open selected image URI"))

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Calculate sample size for max dimension 1920
            var sampleSize = 1
            val maxDim = maxOf(options.outWidth, options.outHeight)
            while (maxDim / sampleSize > 1920) {
                sampleSize *= 2
            }

            val decodeStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot re-open selected image URI"))

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val originalBitmap = BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
            decodeStream.close()

            if (originalBitmap == null) {
                return@withContext Result.failure(Exception("Failed to decode uploaded image"))
            }

            val destFile = File(storageDir, "upload_shot_${System.currentTimeMillis()}.jpg")
            FileOutputStream(destFile).use { out ->
                originalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            originalBitmap.recycle()

            Result.success(destFile.absolutePath)
        } catch (e: Exception) {
            Log.e(tag, "Failed to save uploaded image", e)
            Result.failure(e)
        }
    }

    /**
     * Renders a high-resolution 16:9 cinematic storyboard concept sketch frame.
     * Features composition guides, anamorphic letterboxing, stylistic lighting gradients,
     * camera specifications, and Director HUD stamps.
     */
    fun generateOfflineConceptSketch(
        sceneHeading: String,
        shotType: String,
        lens: String,
        cameraMovement: String,
        actionSummary: String,
        stylePreset: CinematicStylePreset
    ): String {
        val width = 1280
        val height = 720
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Cinematic Background Gradient
        val gradient = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            stylePreset.primaryColor,
            stylePreset.secondaryColor,
            Shader.TileMode.CLAMP
        )
        val bgPaint = Paint().apply {
            shader = gradient
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Anamorphic 2.39:1 Letterbox Bars or 16:9 Cinematic Border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        val safeMarginX = 64f
        val safeMarginY = 36f
        canvas.drawRect(safeMarginX, safeMarginY, width - safeMarginX, height - safeMarginY, borderPaint)

        // 3. Rule of Thirds Guides
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(35, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        }
        val thirdW = (width - 2 * safeMarginX) / 3f
        val thirdH = (height - 2 * safeMarginY) / 3f

        canvas.drawLine(safeMarginX + thirdW, safeMarginY, safeMarginX + thirdW, height - safeMarginY, gridPaint)
        canvas.drawLine(safeMarginX + 2 * thirdW, safeMarginY, safeMarginX + 2 * thirdW, height - safeMarginY, gridPaint)
        canvas.drawLine(safeMarginX, safeMarginY + thirdH, width - safeMarginX, safeMarginY + thirdH, gridPaint)
        canvas.drawLine(safeMarginX, safeMarginY + 2 * thirdH, width - safeMarginX, safeMarginY + 2 * thirdH, gridPaint)

        // 4. Center Framing Crosshair
        val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 245, 158, 11) // Cineast Gold
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val centerX = width / 2f
        val centerY = height / 2f
        canvas.drawLine(centerX - 24f, centerY, centerX + 24f, centerY, crossPaint)
        canvas.drawLine(centerX, centerY - 24f, centerX, centerY + 24f, crossPaint)
        canvas.drawCircle(centerX, centerY, 8f, crossPaint)

        // 5. Stylized Composition Graphic based on Shot Type
        val compPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 255, 255, 255)
            style = Paint.Style.FILL
        }

        if (shotType.contains("Close-Up", ignoreCase = true) || shotType.contains("CU", ignoreCase = true)) {
            // Silhouette portrait oval
            canvas.drawOval(RectF(centerX - 110f, centerY - 150f, centerX + 110f, centerY + 90f), compPaint)
            // Shoulders
            val shoulderPath = Path().apply {
                moveTo(centerX - 220f, height - safeMarginY)
                quadTo(centerX - 110f, centerY + 100f, centerX, centerY + 100f)
                quadTo(centerX + 110f, centerY + 100f, centerX + 220f, height - safeMarginY)
                close()
            }
            canvas.drawPath(shoulderPath, compPaint)
        } else if (shotType.contains("Extreme Wide", ignoreCase = true) || shotType.contains("Wide", ignoreCase = true)) {
            // Low Horizon & Mountain/City silhouettes
            val horizonY = centerY + 80f
            val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(50, 255, 255, 255)
                style = Paint.Style.FILL
            }
            val skylinePath = Path().apply {
                moveTo(safeMarginX, height - safeMarginY)
                lineTo(safeMarginX, horizonY)
                lineTo(safeMarginX + 180f, horizonY - 60f)
                lineTo(safeMarginX + 320f, horizonY - 30f)
                lineTo(safeMarginX + 480f, horizonY - 110f)
                lineTo(safeMarginX + 640f, horizonY - 40f)
                lineTo(safeMarginX + 800f, horizonY - 140f)
                lineTo(safeMarginX + 960f, horizonY - 50f)
                lineTo(width - safeMarginX, horizonY)
                lineTo(width - safeMarginX, height - safeMarginY)
                close()
            }
            canvas.drawPath(skylinePath, horizonPaint)
        } else {
            // Medium shot two-point framing box
            canvas.drawRoundRect(
                RectF(centerX - 160f, centerY - 110f, centerX + 160f, centerY + 120f),
                16f, 16f, compPaint
            )
        }

        // 6. Camera Director HUD Overlay Text
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 245, 158, 11) // Gold
            textSize = 22f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 255, 255, 255)
            textSize = 15f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        // Top Left: Scene slugline
        canvas.drawText("SCENE: ${sceneHeading.uppercase()}", safeMarginX + 24f, safeMarginY + 42f, headerPaint)

        // Top Right: Shot Specs
        val specsText = "$shotType | $lens | $cameraMovement"
        val specsWidth = captionPaint.measureText(specsText)
        canvas.drawText(specsText, width - safeMarginX - specsWidth - 24f, safeMarginY + 42f, captionPaint)

        // Bottom Left: Action Snippet
        val cleanAction = if (actionSummary.length > 90) actionSummary.take(87) + "..." else actionSummary
        canvas.drawText("ACTION: $cleanAction", safeMarginX + 24f, height - safeMarginY - 38f, subPaint)

        // Bottom Right: Style Preset & FPS Stamp
        val stampText = "CINEMATIC 16:9 • 24 FPS • ${stylePreset.displayName.uppercase()}"
        val stampWidth = captionPaint.measureText(stampText)
        canvas.drawText(stampText, width - safeMarginX - stampWidth - 24f, height - safeMarginY - 38f, captionPaint)

        // Save to file
        val destFile = File(storageDir, "concept_shot_${System.currentTimeMillis()}.png")
        FileOutputStream(destFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        bitmap.recycle()

        return destFile.absolutePath
    }
}
