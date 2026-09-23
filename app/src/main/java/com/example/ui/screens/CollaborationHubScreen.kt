package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CollaborationComment
import com.example.data.model.Collaborator
import com.example.data.sync.SyncState
import com.example.data.sync.SyncStatus
import com.example.ui.theme.CineastCyan
import com.example.ui.theme.CineastGold
import com.example.ui.theme.CineastGreen
import com.example.ui.theme.CineastRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CollaborationHubScreen(
    collaborators: List<Collaborator>,
    comments: List<CollaborationComment>,
    onAddComment: (String, String) -> Unit,
    onDeleteComment: (CollaborationComment) -> Unit,
    onSimulateLiveActivity: () -> Unit,
    syncStatus: SyncStatus? = null,
    onTriggerSync: (() -> Unit)? = null
) {
    var newCommentText by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("Co-Writer") }
    var statusFilter by remember { mutableStateOf<String?>(null) }

    val roles = listOf("Lead Writer", "Story Editor", "Director", "Executive Producer")

    val filteredComments = remember(comments, statusFilter) {
        if (statusFilter == null) comments else comments.filter { it.status == statusFilter }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- Cloud Connected Room Status Banner ---
        val syncIcon = when (syncStatus?.state) {
            SyncState.SYNCING, SyncState.CONNECTING -> Icons.Default.CloudSync
            SyncState.SYNCED -> Icons.Default.CloudDone
            else -> Icons.Default.CloudDone
        }
        val syncColor = when (syncStatus?.state) {
            SyncState.SYNCING, SyncState.CONNECTING -> CineastGold
            SyncState.SYNCED -> CineastGreen
            SyncState.ERROR -> CineastRed
            else -> CineastGreen
        }
        val syncLabel = syncStatus?.message ?: "Cloud Sync: Real-Time Active"

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = syncIcon,
                        contentDescription = null,
                        tint = syncColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "FIRESTORE COLLAB ROOM",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = syncLabel,
                            fontSize = 10.5.sp,
                            color = syncColor,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onTriggerSync != null) {
                        Button(
                            onClick = onTriggerSync,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("trigger_cloud_sync_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Sync Cloud",
                                tint = CineastCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Sync Cloud",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CineastCyan
                            )
                        }
                    }

                    // Simulate incoming live collaborative activity
                    Button(
                        onClick = onSimulateLiveActivity,
                        colors = ButtonDefaults.buttonColors(containerColor = CineastGold),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("simulate_collab_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Live Ping",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }

        // --- Active Collaborators Strip ---
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        tint = CineastGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ACTIVE SCRIPTWRITING ROOM (${collaborators.size} ONLINE)",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    collaborators.forEach { collab ->
                        CollaboratorChip(collab = collab)
                    }
                }
            }
        }

        // --- Notes & Comments Feed Header & Filter ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SCRIPT REVIEW NOTES (${filteredComments.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = statusFilter == null,
                    onClick = { statusFilter = null },
                    label = { Text("All", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CineastGold,
                        selectedLabelColor = Color.Black
                    )
                )
                FilterChip(
                    selected = statusFilter == "OPEN",
                    onClick = { statusFilter = "OPEN" },
                    label = { Text("Open", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CineastGold,
                        selectedLabelColor = Color.Black
                    )
                )
                FilterChip(
                    selected = statusFilter == "RESOLVED",
                    onClick = { statusFilter = "RESOLVED" },
                    label = { Text("Resolved", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CineastGreen,
                        selectedLabelColor = Color.Black
                    )
                )
            }
        }

        // --- Comments List ---
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredComments, key = { it.id }) { comment ->
                CommentCard(
                    comment = comment,
                    onDelete = { onDeleteComment(comment) }
                )
            }
        }

        // --- Post New Collaborative Note Input Bar ---
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "As:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    roles.forEach { role ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedRole == role) CineastGold else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        ) {
                            Text(
                                text = role,
                                fontSize = 10.sp,
                                fontWeight = if (selectedRole == role) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedRole == role) Color.Black else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newCommentText,
                        onValueChange = { newCommentText = it },
                        placeholder = { Text("Write collaborative note or revision feedback...", fontSize = 12.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("collab_input_field"),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CineastGold,
                            cursorColor = CineastGold
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newCommentText.isNotBlank()) {
                                onAddComment(newCommentText.trim(), selectedRole)
                                newCommentText = ""
                            }
                        },
                        enabled = newCommentText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CineastGold),
                        modifier = Modifier.testTag("collab_send_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Post Note",
                            tint = Color.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CollaboratorChip(collab: Collaborator) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(CineastGold.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = collab.name.split(" ").map { it.take(1) }.joinToString(""),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CineastGold
                    )
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(CineastGreen)
                        .align(Alignment.BottomEnd)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Text(
                    text = collab.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = collab.status,
                    fontSize = 9.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CommentCard(
    comment: CollaborationComment,
    onDelete: () -> Unit
) {
    val statusColor = when (comment.status) {
        "RESOLVED" -> CineastGreen
        "URGENT" -> CineastRed
        else -> CineastGold
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("comment_card_${comment.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(CineastGold.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = comment.avatarInitials,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = CineastGold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = comment.authorName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${comment.authorRole}",
                            fontSize = 10.5.sp,
                            color = CineastGold
                        )
                    }
                    val dateFormatted = SimpleDateFormat("h:mm a • MMM d", Locale.getDefault()).format(Date(comment.timestamp))
                    Text(
                        text = dateFormatted,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = comment.status,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Note",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = comment.content,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                color = Color.White
            )
        }
    }
}
