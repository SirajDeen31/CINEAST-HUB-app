package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CharacterProfile
import com.example.data.model.ScreenplayElement
import com.example.data.model.ScreenplayElementType
import com.example.data.model.ScreenplayProject
import com.example.ui.theme.CineastCyan
import com.example.ui.theme.CineastGold
import com.example.ui.theme.CineastGoldBright
import com.example.ui.theme.CineastGreen
import com.example.ui.theme.CineastPurple
import com.example.ui.theme.CineastRed
import com.example.ui.theme.ScriptCourierTextLight
import com.example.ui.theme.ScriptPaperDark
import java.util.Locale

/**
 * ScreenplayEditorComponent
 *
 * Implements Hollywood/AMPAS standard screenplay formatting rules:
 * - Font: Courier 12pt (Monospace)
 * - Scene Heading: ALL CAPS, base margin, with scene number indicators
 * - Action: Sentence case, 1.5" left margin, standard script line spacing
 * - Character: ALL CAPS, indented 3.7" (~38% left offset), centered cue
 * - Dialogue: Indented 2.5" from left, 3.5" maximum column width
 * - Parenthetical: Indented 3.1", wrapped in (wryly), centered above dialogue
 * - Transition: ALL CAPS, right aligned (e.g., CUT TO:)
 * - Shot / Subheader: ALL CAPS, base margin
 */
@Composable
fun ScreenplayEditorComponent(
    project: ScreenplayProject?,
    elements: List<ScreenplayElement>,
    characters: List<CharacterProfile>,
    onAddElement: (ScreenplayElementType, String, Int?) -> Unit,
    onUpdateElement: (ScreenplayElement) -> Unit,
    onDeleteElement: (Long) -> Unit,
    onElementComment: (ScreenplayElement) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeQuickFormat by remember { mutableStateOf(ScreenplayElementType.ACTION) }
    var inlineEditingId by remember { mutableStateOf<Long?>(null) }
    var inlineEditContent by remember { mutableStateOf("") }
    var inlineEditType by remember { mutableStateOf(ScreenplayElementType.ACTION) }
    var inlineEditSceneNumber by remember { mutableStateOf<Int?>(null) }
    var showFormatGuideDialog by remember { mutableStateOf(false) }

    // Fast-append buffer state
    var showFastInputBar by remember { mutableStateOf(false) }
    var fastInputText by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    // Real-time word count tracking that dynamically updates as the user types
    val isTyping = fastInputText.isNotBlank() || (inlineEditingId != null && inlineEditContent.isNotBlank())

    val liveTotalWordCount by remember(elements, fastInputText, inlineEditContent, inlineEditingId) {
        derivedStateOf {
            var total = 0
            elements.forEach { elem ->
                val content = if (elem.id == inlineEditingId) inlineEditContent else elem.content
                total += countScreenplayWords(content)
            }
            if (inlineEditingId == null && fastInputText.isNotBlank()) {
                total += countScreenplayWords(fastInputText)
            }
            total
        }
    }

    val liveTotalCharCount by remember(elements, fastInputText, inlineEditContent, inlineEditingId) {
        derivedStateOf {
            var total = 0
            elements.forEach { elem ->
                val content = if (elem.id == inlineEditingId) inlineEditContent else elem.content
                total += content.length
            }
            if (inlineEditingId == null && fastInputText.isNotBlank()) {
                total += fastInputText.length
            }
            total
        }
    }

    val liveExactPages by remember(liveTotalWordCount) {
        derivedStateOf {
            val pages = liveTotalWordCount / 250f
            if (pages < 1f) "1 Page" else "%.1f Pages".format(Locale.US, pages)
        }
    }

    val liveEstimatedPages by remember(liveTotalWordCount) {
        derivedStateOf {
            val pages = liveTotalWordCount / 250f
            if (pages <= 1f) 1 else Math.ceil(pages.toDouble()).toInt()
        }
    }

    val liveSceneCount by remember(elements, inlineEditType, inlineEditingId) {
        derivedStateOf {
            elements.count { elem ->
                if (elem.id == inlineEditingId) {
                    inlineEditType == ScreenplayElementType.SCENE_HEADING
                } else {
                    elem.elementType == ScreenplayElementType.SCENE_HEADING.name
                }
            }
        }
    }

    val liveRuntimeEstimate by remember(liveTotalWordCount) {
        derivedStateOf {
            val minutes = liveTotalWordCount / 250f
            val totalSeconds = (minutes * 60).toInt()
            val m = totalSeconds / 60
            val s = totalSeconds % 60
            "${m}m ${s}s"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // =====================================================================
        // 1. Industry Screenplay Formatting Toolbar
        // =====================================================================
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Header row: Format label & standards compliance badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = CineastGold.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "INDUSTRY FORMAT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CineastGold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = "Courier 12pt • US Letter Standard",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Format guidelines popover button
                    IconButton(
                        onClick = { showFormatGuideDialog = !showFormatGuideDialog },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("format_guide_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Screenplay Formatting Rules",
                            tint = CineastGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable selector for industry screenplay elements
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ScreenplayElementType.values().forEach { type ->
                        val isSelected = activeQuickFormat == type
                        val chipColor = getElementTypeBadgeColor(type)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                activeQuickFormat = type
                                showFastInputBar = true
                            },
                            label = {
                                Text(
                                    text = type.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(chipColor)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = chipColor.copy(alpha = 0.25f),
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("format_chip_${type.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // =====================================================================
        // 2. Fast Script Beat Quick-Composer Bar
        // =====================================================================
        AnimatedVisibility(
            visible = showFastInputBar,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            FastBeatComposer(
                elementType = activeQuickFormat,
                inputText = fastInputText,
                onTextChanged = { fastInputText = it },
                onFormatChange = { activeQuickFormat = it },
                characterSuggestions = characters.map { it.name },
                onClose = {
                    showFastInputBar = false
                    fastInputText = ""
                },
                onSubmit = { content, sceneNum ->
                    if (content.isNotBlank()) {
                        onAddElement(activeQuickFormat, content.trim(), sceneNum)
                        fastInputText = ""
                        // Industry auto-progression workflow
                        activeQuickFormat = getNextIndustrySuggestedType(activeQuickFormat)
                    }
                }
            )
        }

        // =====================================================================
        // 3. Screenplay Page Canvas (Virtual Industry Page)
        // =====================================================================
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Surface(
                color = ScriptPaperDark,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(4.dp, RoundedCornerShape(8.dp))
                    .testTag("screenplay_canvas")
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 18.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Script Page Virtual Header
                    item {
                        ScriptPageHeader(project = project)
                    }

                    if (elements.isEmpty()) {
                        item {
                            EmptyScriptWatermark(
                                onStartWriting = {
                                    activeQuickFormat = ScreenplayElementType.SCENE_HEADING
                                    showFastInputBar = true
                                }
                            )
                        }
                    } else {
                        itemsIndexed(elements, key = { _, item -> item.id }) { index, element ->
                            val isEditingThis = inlineEditingId == element.id

                            if (isEditingThis) {
                                // Inline Editor Mode
                                InlineElementEditor(
                                    element = element,
                                    currentContent = inlineEditContent,
                                    currentType = inlineEditType,
                                    currentSceneNumber = inlineEditSceneNumber,
                                    onContentChange = { inlineEditContent = it },
                                    onTypeChange = { inlineEditType = it },
                                    onSceneNumChange = { inlineEditSceneNumber = it },
                                    onSave = {
                                        val updated = element.copy(
                                            content = inlineEditContent.trim(),
                                            elementType = inlineEditType.name,
                                            sceneNumber = inlineEditSceneNumber
                                        )
                                        onUpdateElement(updated)
                                        inlineEditingId = null
                                    },
                                    onCancel = { inlineEditingId = null },
                                    onDelete = {
                                        onDeleteElement(element.id)
                                        inlineEditingId = null
                                    }
                                )
                            } else {
                                // Industry-standard Formatted View
                                IndustryFormattedElement(
                                    element = element,
                                    onClick = {
                                        inlineEditingId = element.id
                                        inlineEditContent = element.content
                                        inlineEditType = ScreenplayElementType.values().find { it.name == element.elementType }
                                            ?: ScreenplayElementType.ACTION
                                        inlineEditSceneNumber = element.sceneNumber
                                    },
                                    onComment = { onElementComment(element) }
                                )
                            }
                        }
                    }

                    // Bottom script buffer & end tag
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (elements.isNotEmpty()) "— FADE OUT. —" else "",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                letterSpacing = 2.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            }

            // Quick Floating Add Action Button
            if (!showFastInputBar) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = CineastGold,
                    shadowElevation = 6.dp,
                    onClick = {
                        showFastInputBar = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .testTag("floating_quick_beat_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Script Beat",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "+ Write Beat",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }

        // =====================================================================
        // 4. Real-Time Screenplay Footer Status Bar
        // =====================================================================
        ScreenplayEditorFooter(
            totalWords = liveTotalWordCount,
            totalExactPages = liveExactPages,
            totalEstimatedPages = liveEstimatedPages,
            totalCharacters = liveTotalCharCount,
            totalScenes = liveSceneCount,
            estimatedRuntime = liveRuntimeEstimate,
            isTyping = isTyping,
            currentInputWords = if (inlineEditingId != null) countScreenplayWords(inlineEditContent) else countScreenplayWords(fastInputText),
            onQuickBeatClick = { showFastInputBar = true },
            isComposerVisible = showFastInputBar
        )
    }

    // =========================================================================
    // Format Guide Dialog
    // =========================================================================
    if (showFormatGuideDialog) {
        ScreenplayFormattingGuideDialog(onDismiss = { showFormatGuideDialog = false })
    }
}

/**
 * Renders individual screenplay elements with strict Hollywood standard rules:
 * - Scene Heading: All CAPS, scene number, slugline format
 * - Action: Regular Courier 12, full width
 * - Character: Indented 3.7", ALL CAPS
 * - Dialogue: Indented 2.5", 3.5" max column
 * - Parenthetical: Indented 3.1", in parentheses
 * - Transition: Right aligned, ALL CAPS
 */
@Composable
fun IndustryFormattedElement(
    element: ScreenplayElement,
    onClick: () -> Unit,
    onComment: () -> Unit,
    modifier: Modifier = Modifier
) {
    val type = ScreenplayElementType.values().find { it.name == element.elementType }
        ?: ScreenplayElementType.ACTION

    Surface(
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(vertical = getVerticalSpacingForType(type))
            .testTag("script_element_${element.id}")
    ) {
        when (type) {
            // -----------------------------------------------------------------
            // 1. SCENE HEADING (Slugline)
            // Rule: Base left margin, ALL CAPS, Scene # left & right
            // -----------------------------------------------------------------
            ScreenplayElementType.SCENE_HEADING -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sceneNum = element.sceneNumber ?: 1
                    Text(
                        text = "$sceneNum.",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = CineastGold,
                        modifier = Modifier.width(30.dp)
                    )
                    Text(
                        text = element.content.uppercase(Locale.US),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        letterSpacing = 0.5.sp
                    )
                    IconButton(
                        onClick = onComment,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Comment,
                            contentDescription = "Scene Note",
                            tint = if (element.hasComment) CineastGold else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // -----------------------------------------------------------------
            // 2. ACTION DESCRIPTION
            // Rule: Left margin 1.5", Right margin 1.0", Sentence case, Present tense
            // -----------------------------------------------------------------
            ScreenplayElementType.ACTION -> {
                Text(
                    text = element.content,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = ScriptCourierTextLight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                )
            }

            // -----------------------------------------------------------------
            // 3. CHARACTER CUE
            // Rule: Indented 3.7" from left, ALL CAPS, centered above dialogue
            // -----------------------------------------------------------------
            ScreenplayElementType.CHARACTER -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = element.content.uppercase(Locale.US),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = CineastGoldBright,
                        letterSpacing = 1.2.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // -----------------------------------------------------------------
            // 4. PARENTHETICAL (Actor Direction / Wryly)
            // Rule: Indented 3.1", ~3.0" width, enclosed in parentheses (lowercase)
            // -----------------------------------------------------------------
            ScreenplayElementType.PARENTHETICAL -> {
                val formatted = if (element.content.startsWith("(") && element.content.endsWith(")")) {
                    element.content
                } else {
                    "(${element.content.trim()})"
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = formatted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 240.dp)
                    )
                }
            }

            // -----------------------------------------------------------------
            // 5. DIALOGUE
            // Rule: Indented 2.5", 3.5" column width, centered block left-aligned text
            // -----------------------------------------------------------------
            ScreenplayElementType.DIALOGUE -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = element.content,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .widthIn(min = 200.dp, max = 300.dp)
                            .padding(horizontal = 6.dp)
                    )
                }
            }

            // -----------------------------------------------------------------
            // 6. TRANSITION
            // Rule: Indented to the right margin (~6.0"), ALL CAPS, ends with colon
            // -----------------------------------------------------------------
            ScreenplayElementType.TRANSITION -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = element.content.uppercase(Locale.US),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = CineastGold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // -----------------------------------------------------------------
            // 7. SHOT / SUBHEADER
            // Rule: Left aligned, ALL CAPS, e.g. "CLOSE UP ON:", "INSERT -"
            // -----------------------------------------------------------------
            ScreenplayElementType.SHOT -> {
                Text(
                    text = element.content.uppercase(Locale.US),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = CineastCyan,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Fast Beat Quick-Composer Bar
 * Lets the screenwriter type rapidly with quick format snippets and auto-suggestions.
 */
@Composable
fun FastBeatComposer(
    elementType: ScreenplayElementType,
    inputText: String,
    onTextChanged: (String) -> Unit,
    onFormatChange: (ScreenplayElementType) -> Unit,
    characterSuggestions: List<String>,
    onClose: () -> Unit,
    onSubmit: (String, Int?) -> Unit
) {
    var sceneNumberInput by remember { mutableStateOf<Int?>(null) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("fast_beat_composer")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Context header with active format indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(getElementTypeBadgeColor(elementType))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Writing ${elementType.label.uppercase()}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Composer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Snippets / Macros based on elementType
            when (elementType) {
                ScreenplayElementType.SCENE_HEADING -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("INT. ", "EXT. ", "INT./EXT. ", " - DAY", " - NIGHT", " - CONTINUOUS").forEach { snippet ->
                            AssistChip(
                                onClick = { onTextChanged(inputText + snippet) },
                                label = { Text(snippet, fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                ScreenplayElementType.CHARACTER -> {
                    if (characterSuggestions.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            characterSuggestions.forEach { name ->
                                AssistChip(
                                    onClick = { onTextChanged(name.uppercase()) },
                                    label = { Text(name, fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface)
                                )
                            }
                            listOf(" (V.O.)", " (O.S.)", " (CONT'D)").forEach { ext ->
                                AssistChip(
                                    onClick = { onTextChanged(inputText + ext) },
                                    label = { Text(ext, fontSize = 10.sp, color = CineastGold, fontFamily = FontFamily.Monospace) },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                ScreenplayElementType.PARENTHETICAL -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("beat", "whispering", "smiling", "overlapping", "to character", "sighs").forEach { wryly ->
                            AssistChip(
                                onClick = { onTextChanged("($wryly)") },
                                label = { Text("($wryly)", fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                ScreenplayElementType.TRANSITION -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("CUT TO:", "FADE OUT.", "DISSOLVE TO:", "SMASH CUT TO:", "MATCH CUT TO:").forEach { trans ->
                            AssistChip(
                                onClick = { onTextChanged(trans) },
                                label = { Text(trans, fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                else -> {}
            }

            // Input Text Field
            OutlinedTextField(
                value = inputText,
                onValueChange = onTextChanged,
                placeholder = {
                    Text(
                        text = getPlaceholderForType(elementType),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    color = Color.White
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = getElementTypeBadgeColor(elementType),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fast_beat_input_field")
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Action row: submit & next cue guidance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Next: ${getNextIndustrySuggestedType(elementType).label}",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )

                Button(
                    onClick = { onSubmit(inputText, sceneNumberInput) },
                    enabled = inputText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CineastGold),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("submit_fast_beat_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Insert Beat",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

/**
 * Inline Editor Component for editing any existing screenplay beat directly in place.
 */
@Composable
fun InlineElementEditor(
    element: ScreenplayElement,
    currentContent: String,
    currentType: ScreenplayElementType,
    currentSceneNumber: Int?,
    onContentChange: (String) -> Unit,
    onTypeChange: (ScreenplayElementType) -> Unit,
    onSceneNumChange: (Int?) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CineastGold),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("inline_editor_${element.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type Switcher inside inline editor
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ScreenplayElementType.values().forEach { type ->
                        val isSelected = currentType == type
                        FilterChip(
                            selected = isSelected,
                            onClick = { onTypeChange(type) },
                            label = { Text(type.shortCode, fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CineastGold,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Beat",
                        tint = CineastRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = currentContent,
                onValueChange = onContentChange,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color.White
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CineastGold,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) {
                    Text("Cancel", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColors(containerColor = CineastGold),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
    }
}

/**
 * Script Page Header showing Hollywood draft and page numbering rules
 */
@Composable
fun ScriptPageHeader(project: ScreenplayProject?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${project?.title?.uppercase() ?: "UNTITLED"} - ${project?.draftName?.uppercase() ?: "FIRST DRAFT"}",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                letterSpacing = 1.sp
            )
            Text(
                text = "1.",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 6.dp, bottom = 4.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        )
    }
}

@Composable
fun EmptyScriptWatermark(onStartWriting: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "FADE IN:",
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = CineastGold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your screenplay canvas is waiting.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onStartWriting,
            colors = ButtonDefaults.buttonColors(containerColor = CineastGold),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("Begin Scene 1", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 12.sp)
        }
    }
}

/**
 * Screenplay Formatting Guide dialog displaying official Hollywood industry standards.
 */
@Composable
fun ScreenplayFormattingGuideDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = CineastGold)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Screenplay Format Rules", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                Text(
                    text = "Hollywood & Academy Standard (AMPAS):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CineastGold
                )
                Spacer(modifier = Modifier.height(8.dp))

                FormatRuleItem("Scene Heading", "ALL CAPS. Left margin 1.5\". Specifies INT/EXT, location, time.", CineastGold)
                FormatRuleItem("Action", "Sentence case. Spans 1.5\" left to 1.0\" right margin. Present tense.", ScriptCourierTextLight)
                FormatRuleItem("Character Cue", "ALL CAPS. Indented 3.7\" from left (~38% offset). Centered cue.", CineastGoldBright)
                FormatRuleItem("Dialogue", "Sentence case. Indented 2.5\" from left. Max column 3.5\" width.", Color.White)
                FormatRuleItem("Parenthetical", "Indented 3.1\" from left. Actor direction enclosed in (wryly).", MaterialTheme.colorScheme.onSurfaceVariant)
                FormatRuleItem("Transition", "ALL CAPS. Right margin (~6.0\" indent). e.g. CUT TO:, FADE OUT.", CineastCyan)
                FormatRuleItem("Shot", "ALL CAPS. Specifies camera angle or subject, e.g. CLOSE UP ON:", CineastPurple)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got It", color = CineastGold, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun FormatRuleItem(title: String, desc: String, color: Color) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = color, fontFamily = FontFamily.Monospace)
        }
        Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp))
    }
}

// =============================================================================
// Helper Functions for Industry Screenplay Rules
// =============================================================================

fun getElementTypeBadgeColor(type: ScreenplayElementType): Color {
    return when (type) {
        ScreenplayElementType.SCENE_HEADING -> CineastGold
        ScreenplayElementType.ACTION -> ScriptCourierTextLight
        ScreenplayElementType.CHARACTER -> CineastGoldBright
        ScreenplayElementType.DIALOGUE -> CineastCyan
        ScreenplayElementType.PARENTHETICAL -> CineastPurple
        ScreenplayElementType.TRANSITION -> CineastGreen
        ScreenplayElementType.SHOT -> CineastRed
    }
}

fun getVerticalSpacingForType(type: ScreenplayElementType): androidx.compose.ui.unit.Dp {
    return when (type) {
        ScreenplayElementType.SCENE_HEADING -> 10.dp
        ScreenplayElementType.ACTION -> 4.dp
        ScreenplayElementType.CHARACTER -> 6.dp
        ScreenplayElementType.PARENTHETICAL -> 1.dp
        ScreenplayElementType.DIALOGUE -> 2.dp
        ScreenplayElementType.TRANSITION -> 8.dp
        ScreenplayElementType.SHOT -> 6.dp
    }
}

fun getPlaceholderForType(type: ScreenplayElementType): String {
    return when (type) {
        ScreenplayElementType.SCENE_HEADING -> "e.g. INT. APARTMENT 4B - NIGHT"
        ScreenplayElementType.ACTION -> "Describe visible actions, movements, sounds..."
        ScreenplayElementType.CHARACTER -> "CHARACTER NAME or EXTENSION (V.O.)"
        ScreenplayElementType.DIALOGUE -> "Spoken dialogue line..."
        ScreenplayElementType.PARENTHETICAL -> "e.g. (whispering) or (beat)"
        ScreenplayElementType.TRANSITION -> "e.g. CUT TO: or DISSOLVE TO:"
        ScreenplayElementType.SHOT -> "e.g. CLOSE UP ON DETECTIVE'S EYES"
    }
}

/**
 * Standard Hollywood auto-progression:
 * - After Scene Heading -> Action
 * - After Character -> Dialogue
 * - After Parenthetical -> Dialogue
 * - After Dialogue -> Character (for back-and-forth) or Action
 * - After Action -> Action (or Character)
 * - After Transition -> Scene Heading
 */
fun getNextIndustrySuggestedType(current: ScreenplayElementType): ScreenplayElementType {
    return when (current) {
        ScreenplayElementType.SCENE_HEADING -> ScreenplayElementType.ACTION
        ScreenplayElementType.CHARACTER -> ScreenplayElementType.DIALOGUE
        ScreenplayElementType.PARENTHETICAL -> ScreenplayElementType.DIALOGUE
        ScreenplayElementType.DIALOGUE -> ScreenplayElementType.CHARACTER
        ScreenplayElementType.ACTION -> ScreenplayElementType.ACTION
        ScreenplayElementType.TRANSITION -> ScreenplayElementType.SCENE_HEADING
        ScreenplayElementType.SHOT -> ScreenplayElementType.ACTION
    }
}

/**
 * Counts words accurately across whitespace, tabs, and linebreaks.
 */
fun countScreenplayWords(text: String): Int {
    if (text.isBlank()) return 0
    return text.trim().split("\\s+".toRegex()).count { it.isNotEmpty() }
}

/**
 * ScreenplayEditorFooter:
 * Real-time word count and page tracker footer anchored persistently at the bottom of the editor.
 * Displays live words, exact pages, scenes, screen runtime, and live typing indicators as user edits.
 */
@Composable
fun ScreenplayEditorFooter(
    totalWords: Int,
    totalExactPages: String,
    totalEstimatedPages: Int,
    totalCharacters: Int,
    totalScenes: Int,
    estimatedRuntime: String,
    isTyping: Boolean,
    currentInputWords: Int,
    onQuickBeatClick: () -> Unit,
    isComposerVisible: Boolean,
    modifier: Modifier = Modifier
) {
    var showDetailsDialog by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("screenplay_editor_footer")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Section: Interactive Word & Page Count Hub with Live Typing Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { showDetailsDialog = true }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .testTag("footer_word_count_tracker")
            ) {
                // Live status dot / icon
                if (isTyping) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(CineastGold)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Script Statistics",
                        tint = CineastGold,
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Word Count with real-time keystroke tracking
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$totalWords Words",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isTyping) CineastGoldBright else Color.White
                    )
                    if (isTyping && currentInputWords > 0) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "(+$currentInputWords)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = CineastGold
                        )
                    }
                }

                Text(
                    text = "•",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Page Count badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = CineastGold.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, CineastGold.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = totalExactPages,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = CineastGoldBright,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            // Right Section: Secondary screen metrics & fast beat trigger
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Compact runtime and scene counter chip
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showDetailsDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "$totalScenes Sc",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = estimatedRuntime,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = CineastCyan
                        )
                    }
                }

                // Quick Write Beat Button in Footer when composer is closed
                if (!isComposerVisible) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CineastGold,
                        onClick = onQuickBeatClick,
                        modifier = Modifier.testTag("footer_quick_beat_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Quick Beat",
                                tint = Color.Black,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Beat",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Details Dialog for Industry Analytics Breakdown
    if (showDetailsDialog) {
        ScreenplayStatsDetailsDialog(
            totalWords = totalWords,
            totalExactPages = totalExactPages,
            totalEstimatedPages = totalEstimatedPages,
            totalCharacters = totalCharacters,
            totalScenes = totalScenes,
            estimatedRuntime = estimatedRuntime,
            onDismiss = { showDetailsDialog = false }
        )
    }
}

/**
 * ScreenplayStatsDetailsDialog:
 * Complete Hollywood industry standard analytics modal.
 */
@Composable
fun ScreenplayStatsDetailsDialog(
    totalWords: Int,
    totalExactPages: String,
    totalEstimatedPages: Int,
    totalCharacters: Int,
    totalScenes: Int,
    estimatedRuntime: String,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = CineastGold,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Screenplay Analytics",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Hollywood Standard Formatting: Courier 12pt (~250 words / 55 lines per page).",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatMetricRow("Total Words", "$totalWords words", CineastGoldBright)
                        StatMetricRow("Page Count (Exact)", totalExactPages, CineastGold)
                        StatMetricRow("Estimated Print Pages", "$totalEstimatedPages pages", Color.White)
                        StatMetricRow("Estimated Runtime", estimatedRuntime, CineastCyan)
                        StatMetricRow("Total Scenes", "$totalScenes scenes", CineastGreen)
                        StatMetricRow("Total Characters", "$totalCharacters chars", MaterialTheme.colorScheme.onSurfaceVariant)
                        if (totalScenes > 0) {
                            StatMetricRow("Avg Words / Scene", "${totalWords / totalScenes} words", Color.White)
                        }
                    }
                }

                Surface(
                    color = CineastGold.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, CineastGold.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = CineastGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pacing rule of thumb: 1 screenplay page equals ~1 minute of cinematic screen time.",
                            fontSize = 10.5.sp,
                            color = CineastGoldBright,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = CineastGold, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun StatMetricRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = valueColor
        )
    }
}

