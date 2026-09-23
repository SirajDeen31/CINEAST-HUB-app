package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ScreenplayProject
import com.example.export.ScreenplayPdfExporter
import com.example.ui.theme.CineastCyan
import com.example.ui.theme.CineastGold
import com.example.ui.theme.CineastGreen
import java.io.File

@Composable
fun ExportScreen(
    project: ScreenplayProject?,
    isExporting: Boolean,
    exportStatusMessage: String? = null,
    lastExportedFile: File?,
    lastExportSource: String? = "Cloud Firestore",
    lastExportPageCount: Int? = null,
    watermarkText: String = "",
    onWatermarkChange: (String) -> Unit = {},
    includeTitlePage: Boolean,
    includeSceneNumbers: Boolean,
    onToggleTitlePage: (Boolean) -> Unit,
    onToggleSceneNumbers: (Boolean) -> Unit,
    onExportScreenplay: () -> Unit,
    onExportStoryboard: () -> Unit,
    onExportCharacterBible: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("export_screen_list"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Cloud Firestore PDF Service Banner ---
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = CineastGold,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "FIRESTORE PDF EXPORT SERVICE",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = "Cloud-to-Print • WGA & AMPAS Courier Standard",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Firestore Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E293B)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = CineastCyan,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Firestore Sync",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CineastCyan
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 2. Live Export Status Progress Banner (if active) ---
        if (isExporting || exportStatusMessage != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CineastGold)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = CineastGold,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "EXPORTING FROM FIRESTORE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CineastGold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = exportStatusMessage ?: "Compiling screenplay layout...",
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // --- 3. Export Option 1: Standard Industry Screenplay Script PDF ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = CineastGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Industry Screenplay PDF",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "US LETTER 8.5x11",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = CineastGold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Generates industry standard US Letter screenplay formatted in Courier Monospace with accurate 1.5\" left margin for binding, 3.7\" character centerings, 2.5\" dialogue indents, and headers.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Formatting toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Include Title / Cover Page",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Centered script title, author, contact & draft date",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = includeTitlePage,
                            onCheckedChange = onToggleTitlePage,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = CineastGold)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Production Scene Numbers",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Print scene numbers on left & right margins",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = includeSceneNumbers,
                            onCheckedChange = onToggleSceneNumbers,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = CineastGold)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Watermark Input
                    OutlinedTextField(
                        value = watermarkText,
                        onValueChange = onWatermarkChange,
                        label = { Text("Draft Watermark (Optional)", fontSize = 11.sp) },
                        placeholder = { Text("e.g., CONFIDENTIAL DRAFT, WRITERS ROOM", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CineastGold,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_watermark_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onExportScreenplay,
                        enabled = !isExporting,
                        colors = ButtonDefaults.buttonColors(containerColor = CineastGold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_screenplay_pdf_btn")
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Exporting from Firestore...", color = Color.Black, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Screenplay PDF & Share", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- 4. Export Option 2: Visual Storyboard Deck PDF ---
        item {
            ExportFormatCard(
                icon = Icons.Default.Movie,
                title = "Storyboard Deck PDF",
                description = "Visual film deck featuring shot cards, framing angles, camera movements, lens specs, and action/dialogue tie-ins.",
                buttonText = "Export Storyboard Deck PDF",
                buttonTag = "export_storyboard_pdf_btn",
                isLoading = isExporting,
                onClick = onExportStoryboard
            )
        }

        // --- 5. Export Option 3: Character Bible PDF ---
        item {
            ExportFormatCard(
                icon = Icons.Default.People,
                title = "Character Bible & Cast Dossier PDF",
                description = "Complete profile document with external wants, internal needs, psychological traits, dialogue cadence, and casting suggestions.",
                buttonText = "Export Character Bible PDF",
                buttonTag = "export_characters_pdf_btn",
                isLoading = isExporting,
                onClick = onExportCharacterBible
            )
        }

        // --- 6. Last Exported File Preview / Quick Share ---
        lastExportedFile?.let { file ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CineastGreen)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = CineastGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "PDF Generated Successfully!",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CineastGreen
                                )
                            }

                            lastExportPageCount?.let { pages ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF064E3B)
                                ) {
                                    Text(
                                        text = "$pages PAGES",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF34D399),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = file.name,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Size: ${file.length() / 1024} KB • Cache Storage",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            lastExportSource?.let { src ->
                                Text(
                                    text = "Source: $src",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CineastCyan
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val shareIntent = ScreenplayPdfExporter.createShareIntent(context, file)
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Screenplay PDF"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CineastGold),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    val viewIntent = ScreenplayPdfExporter.createViewIntent(context, file)
                                    try {
                                        context.startActivity(viewIntent)
                                    } catch (e: Exception) {
                                        val shareIntent = ScreenplayPdfExporter.createShareIntent(context, file)
                                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Open PDF"))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("View", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExportFormatCard(
    icon: ImageVector,
    title: String,
    description: String,
    buttonText: String,
    buttonTag: String,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CineastGold,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onClick,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, CineastGold),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(buttonTag)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = CineastGold)
                Spacer(modifier = Modifier.width(6.dp))
                Text(buttonText, color = CineastGold, fontWeight = FontWeight.Bold)
            }
        }
    }
}
