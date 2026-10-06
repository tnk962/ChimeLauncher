package com.myenvironment.launcher.core.model

import kotlinx.serialization.Serializable

/**
 * Adaptive Dock に配置されるアイテム (仕様 17, 19)
 * ページとは独立して管理され、全ページで共通表示される。
 */
@Serializable
data class DockItem(
    val id: String,
    val positionIndex: Int,
    val type: ItemType,
    val packageName: String,
    val activityName: String = "",
    val targetUri: String = "",
    val label: String,
    val folderApps: List<FolderApp> = emptyList()
)
