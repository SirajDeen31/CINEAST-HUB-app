package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ScreenplayProject
import com.example.data.sync.SyncState
import com.example.data.sync.SyncStatus
import com.example.ui.theme.CineastGold
import com.example.ui.theme.CineastGreen
import com.example.ui.viewmodel.CineastTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CineastTopBar(
    project: ScreenplayProject?,
    activeCollabCount: Int,
    onCollabClick: () -> Unit,
    syncStatus: SyncStatus? = null,
    onSyncClick: (() -> Unit)? = null
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Clapperboard brand indicator
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = CineastGold,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "Cineast Logo",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                androidx.compose.foundation.layout.Column {
                    Text(
                        text = project?.title?.uppercase() ?: "CINEAST HUB",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 1.sp,
                        maxLines = 1
                    )
                    Text(
                        text = "${project?.draftName ?: "First Draft"} • ${project?.genre ?: "Screenplay"}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            // Live Firestore Cloud Sync indicator
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                onClick = { onSyncClick?.invoke() ?: onCollabClick() },
                modifier = Modifier
                    .padding(end = 6.dp)
                    .testTag("topbar_sync_status")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    val isSyncing = syncStatus?.state == SyncState.SYNCING || syncStatus?.state == SyncState.CONNECTING
                    Icon(
                        imageVector = if (isSyncing) Icons.Default.CloudSync else Icons.Default.CloudDone,
                        contentDescription = "Cloud Status",
                        tint = if (isSyncing) CineastGold else CineastGreen,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isSyncing) "Syncing" else "Cloud",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Live Collaboration badge
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                onClick = onCollabClick,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .testTag("collab_presence_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(CineastGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$activeCollabCount Live",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    )
}

@Composable
fun CineastNavigationBar(
    currentTab: CineastTab,
    onTabSelected: (CineastTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 4.dp
    ) {
        val items = listOf(
            Triple(CineastTab.SCREENPLAY, Icons.Default.Description, "Script"),
            Triple(CineastTab.STORYBOARD, Icons.Default.Movie, "Storyboard"),
            Triple(CineastTab.CHARACTERS, Icons.Default.People, "Characters"),
            Triple(CineastTab.COLLAB, Icons.Default.Group, "Collab"),
            Triple(CineastTab.FORMAT_TOOLS, Icons.Default.Tune, "Format"),
            Triple(CineastTab.EXPORT, Icons.Default.PictureAsPdf, "PDF Export")
        )

        items.forEach { (tab, icon, label) ->
            val selected = currentTab == tab
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Black,
                    selectedTextColor = CineastGold,
                    indicatorColor = CineastGold,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
            )
        }
    }
}
