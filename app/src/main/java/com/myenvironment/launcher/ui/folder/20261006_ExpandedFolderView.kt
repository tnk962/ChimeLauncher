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
            .background(Color(0xD9222936)).padding(4.dp)) {
            // Two columns also fit narrow 1×2 folders; app labels remain hidden on Home.
            val columns = (maxWidth.value / 44f).toInt().coerceAtLeast(2)
            val iconSize = (maxWidth / columns - 6.dp).coerceIn(20.dp, 40.dp)
            LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(folder.folderApps, key = { it.id }) { app ->
                    LauncherItemGraphic(ItemType.APP, app.packageName, app.activityName, "", app.label,
                        repository.isPackageInstalled(app.packageName), repository, iconSize = iconSize, showLabel = false,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = app.label }
                            .clickable { onLaunch(app) }.padding(vertical = 3.dp))
                }
            }
        }
        Text(folder.label, color = Color.White, fontSize = 11.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(top = 3.dp))
    }
}
