package com.myenvironment.launcher.ui.dock

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.myenvironment.launcher.core.launcher.AppDiscoveryRepository
import com.myenvironment.launcher.core.model.DockItem
import com.myenvironment.launcher.core.model.ItemType
import com.myenvironment.launcher.ui.adaptive.DockPlacement
import com.myenvironment.launcher.ui.components.LauncherItemGraphic

/**
 * Adaptive Dock コンポーネント (仕様 17, 18, 19)
 *
 * - Compact (Fold Closed) -> Bottom Dock (下部横並び)
 * - Expanded (Fold Open) -> 設定に応じて下・左・右に配置
 * ページを変更しても共通表示される。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AdaptiveDock(
    dockItems: List<DockItem>,
    installedPackages: Set<String>,
    placement: DockPlacement,
    iconCount: Int,
    isEditMode: Boolean,
    appDiscoveryRepository: AppDiscoveryRepository,
    onDockItemClick: (DockItem, Boolean) -> Unit,
    onRemoveDockItem: (DockItem) -> Unit,
    onMoveDockItem: (DockItem, Int) -> Unit,
    onRequestAddDockItem: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleItems = dockItems.take(iconCount.coerceIn(1, 12))
    BoxWithConstraints(modifier = modifier) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        when (placement) {
            DockPlacement.BOTTOM -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xAA16181E))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(26.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .horizontalScroll(rememberScrollState())
                        .widthIn(min = (availableWidth - 56.dp).coerceAtLeast(0.dp)),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    visibleItems.forEachIndexed { index, item ->
                        DockItemSlot(
                            item = item,
                            index = index,
                            totalCount = visibleItems.size,
                            installedPackages = installedPackages,
                            isEditMode = isEditMode,
                            isVertical = false,
                            appDiscoveryRepository = appDiscoveryRepository,
                            onClick = { isInstalled -> onDockItemClick(item, isInstalled) },
                            onRemove = { onRemoveDockItem(item) },
                            onMove = { delta -> onMoveDockItem(item, delta) }
                        )
                    }

                    if (isEditMode && dockItems.size < iconCount.coerceIn(1, 12)) {
                        IconButton(
                            onClick = onRequestAddDockItem,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Dockに追加",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            DockPlacement.RIGHT, DockPlacement.LEFT -> {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(84.dp)
                        .padding(start = if (placement == DockPlacement.LEFT) 12.dp else 0.dp, end = if (placement == DockPlacement.RIGHT) 12.dp else 0.dp, top = 16.dp, bottom = 16.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xAA16181E))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(26.dp)
                        )
                        .padding(vertical = 16.dp, horizontal = 8.dp)
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = (availableHeight - 64.dp).coerceAtLeast(0.dp)),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    visibleItems.forEachIndexed { index, item ->
                        DockItemSlot(
                            item = item,
                            index = index,
                            totalCount = visibleItems.size,
                            installedPackages = installedPackages,
                            isEditMode = isEditMode,
                            isVertical = true,
                            appDiscoveryRepository = appDiscoveryRepository,
                            onClick = { isInstalled -> onDockItemClick(item, isInstalled) },
                            onRemove = { onRemoveDockItem(item) },
                            onMove = { delta -> onMoveDockItem(item, delta) }
                        )
                    }

                    if (isEditMode && dockItems.size < iconCount.coerceIn(1, 12)) {
                        IconButton(
                            onClick = onRequestAddDockItem,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Dockに追加",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockItemSlot(
    item: DockItem,
    index: Int,
    totalCount: Int,
    installedPackages: Set<String>,
    isEditMode: Boolean,
    isVertical: Boolean,
    appDiscoveryRepository: AppDiscoveryRepository,
    onClick: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit
) {
    val isInstalled = when (item.type) {
        ItemType.APP -> installedPackages.contains(item.packageName) ||
            appDiscoveryRepository.isPackageInstalled(item.packageName)
        ItemType.SHORTCUT, ItemType.ACTION, ItemType.WIDGET -> true
    }

    var menuExpanded by remember { mutableStateOf(false) }

    Box(contentAlignment = Alignment.Center) {
        LauncherItemGraphic(
            type = item.type,
            packageName = item.packageName,
            activityName = item.activityName,
            targetUri = item.targetUri,
            label = item.label,
            isInstalled = isInstalled,
            appDiscoveryRepository = appDiscoveryRepository,
            iconSize = 46.dp,
            showLabel = false,
            isEditMode = isEditMode,
            modifier = Modifier
                .combinedClickable(
                    onClick = { onClick(isInstalled) },
                    onLongClick = { menuExpanded = true }
                )
                .padding(4.dp)
        )

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(item.label) },
                onClick = {
                    menuExpanded = false
                    onClick(isInstalled)
                }
            )
            if (index > 0) {
                DropdownMenuItem(
                    text = { Text(if (isVertical) "↑ 上へ移動" else "← 左へ移動") },
                    onClick = {
                        menuExpanded = false
                        onMove(-1)
                    }
                )
            }
            if (index < totalCount - 1) {
                DropdownMenuItem(
                    text = { Text(if (isVertical) "↓ 下へ移動" else "→ 右へ移動") },
                    onClick = {
                        menuExpanded = false
                        onMove(1)
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("🗑️ Dockから削除", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menuExpanded = false
                    onRemove()
                }
            )
        }
    }
}
