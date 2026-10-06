package com.myenvironment.launcher.core.model

import kotlinx.serialization.Serializable

/** App shortcuts owned by one folder, independent of Home and Dock placement. */
@Serializable
data class FolderApp(
    val id: String,
    val packageName: String,
    val activityName: String = "",
    val label: String
)

fun LayoutItem.asFolderApp() = FolderApp(id, packageName, activityName, label)
fun DockItem.asLayoutItem(pageId: String = LauncherPage.PAGE_ID_HOME) = LayoutItem(
    id, pageId, type, packageName, activityName, targetUri, label, GridPosition(0, 0),
    folderApps = folderApps
)
