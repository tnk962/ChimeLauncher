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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myenvironment.launcher.core.model.*
import com.myenvironment.launcher.core.launcher.AppDiscoveryRepository
import com.myenvironment.launcher.ui.components.LauncherItemGraphic

@Composable
fun ExpandedFolderView(folder: LayoutItem, repository: AppDiscoveryRepository, onOpen: () -> Unit,
    onLaunch: (FolderApp) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xD9222936)).padding(6.dp)) {
        Text("${folder.label}  ⋯", color = Color.White, fontSize = 12.sp, maxLines = 1,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(bottom = 4.dp))
        LazyVerticalGrid(GridCells.Adaptive(64.dp), Modifier.weight(1f)) {
            items(folder.folderApps, key = { it.id }) { app ->
                LauncherItemGraphic(ItemType.APP, app.packageName, app.activityName, "", app.label,
                    repository.isPackageInstalled(app.packageName), repository, iconSize = 40.dp,
                    modifier = Modifier.fillMaxWidth().clickable { onLaunch(app) }.padding(2.dp))
            }
        }
    }
}
