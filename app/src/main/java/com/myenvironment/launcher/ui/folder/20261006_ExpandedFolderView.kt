package com.myenvironment.launcher.ui.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myenvironment.launcher.core.model.*
import com.myenvironment.launcher.core.launcher.AppDiscoveryRepository
import com.myenvironment.launcher.ui.components.LauncherItemGraphic

@Composable
fun ExpandedFolderView(folder: LayoutItem, repository: AppDiscoveryRepository, onOpen: () -> Unit,
    onLaunch: (FolderApp) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Color(0xD9222936)).clickable(onClick = onOpen), contentAlignment = Alignment.Center) {
            val columns = (maxWidth.value / 48f).toInt().coerceAtLeast(2)
                .coerceAtMost(folder.folderApps.size.coerceAtLeast(1))
            val availableWidth = (maxWidth - 16.dp).coerceAtLeast(24.dp)
            val iconSize = (availableWidth / columns - 12.dp).coerceIn(16.dp, 32.dp)
            val rows = (folder.folderApps.size + columns - 1) / columns
            val gridHeight = (rows * (iconSize.value + 12f)).dp.coerceAtMost((maxHeight - 16.dp).coerceAtLeast(24.dp))
            LazyVerticalGrid(GridCells.Fixed(columns), Modifier.width(availableWidth).height(gridHeight),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(folder.folderApps, key = { it.id }) { app ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        // Only the icon launches an app. The dark space around it opens the folder.
                        LauncherItemGraphic(ItemType.APP, app.packageName, app.activityName, "", app.label,
                            repository.isPackageInstalled(app.packageName), repository, iconSize = iconSize, showLabel = false,
                            modifier = Modifier.size(iconSize + 8.dp).semantics { contentDescription = app.label }
                                .clickable { onLaunch(app) })
                    }
                }
            }
        }
        Text(folder.label, color = Color.White, fontSize = 11.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(top = 3.dp))
    }
}
