package com.myenvironment.launcher.ui.folder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myenvironment.launcher.core.launcher.AppDiscoveryRepository
import com.myenvironment.launcher.core.model.*
import com.myenvironment.launcher.ui.components.LauncherItemGraphic

@Composable
fun FolderDialog(
    folder: LayoutItem,
    installedApps: List<AppInfo>,
    repository: AppDiscoveryRepository,
    locked: Boolean,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onLaunch: (FolderApp) -> Unit,
    onAdd: (AppInfo) -> Unit,
    onExtract: (FolderApp, Boolean) -> Unit
) {
    var name by remember(folder.id) { mutableStateOf(folder.label) }
    var adding by remember(folder.id) { mutableStateOf(false) }
    var query by remember(folder.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (adding) adding = false else onDismiss() },
        title = { Text(if (adding) "フォルダにアプリを追加" else folder.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (adding) {
                    OutlinedTextField(query, { query = it }, label = { Text("アプリを検索") }, singleLine = true)
                    val candidates = installedApps.filter { app ->
                        app.label.contains(query, ignoreCase = true) && folder.folderApps.none {
                            it.packageName == app.packageName && it.activityName == app.activityName
                        }
                    }.sortedBy { it.label.lowercase() }
                    LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(candidates, key = { it.packageName + "/" + it.activityName }) { app ->
                            Row(Modifier.fillMaxWidth().clickable { onAdd(app); adding = false }.padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LauncherItemGraphic(ItemType.APP, app.packageName, app.activityName, "", app.label,
                                    true, repository, iconSize = 32.dp, showLabel = false)
                                Text(app.label, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    if (!locked) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(name, { name = it }, modifier = Modifier.weight(1f),
                                label = { Text("フォルダ名") }, singleLine = true)
                            TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank() && name.trim() != folder.label) {
                                Text("保存")
                            }
                        }
                    }
                    LazyVerticalGrid(GridCells.Fixed(3), Modifier.heightIn(max = 340.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(folder.folderApps, key = { it.id }) { app ->
                            var menu by remember { mutableStateOf(false) }
                            Column {
                                LauncherItemGraphic(ItemType.APP, app.packageName, app.activityName, "", app.label,
                                    repository.isPackageInstalled(app.packageName), repository,
                                    modifier = Modifier.fillMaxWidth().clickable { onLaunch(app) })
                                if (!locked) {
                                    Box {
                                        TextButton(onClick = { menu = true }) { Text("取り出す") }
                                        DropdownMenu(menu, { menu = false }) {
                                            DropdownMenuItem(text = { Text("ホームへ取り出す") }, onClick = { menu = false; onExtract(app, false) })
                                            DropdownMenuItem(text = { Text("Dockへ取り出す") }, onClick = { menu = false; onExtract(app, true) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text("${folder.folderApps.size}個のアプリ", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (adding) adding = false else onDismiss() }) { Text(if (adding) "戻る" else "閉じる") } },
        dismissButton = { if (!locked && !adding) TextButton(onClick = { adding = true }) { Text("アプリを追加") } }
    )
}
