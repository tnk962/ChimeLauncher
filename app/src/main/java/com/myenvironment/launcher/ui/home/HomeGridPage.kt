package com.myenvironment.launcher.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.myenvironment.launcher.core.launcher.AppDiscoveryRepository
import com.myenvironment.launcher.core.model.GridPosition
import com.myenvironment.launcher.core.model.ItemType
import com.myenvironment.launcher.core.model.LauncherPage
import com.myenvironment.launcher.core.model.LayoutItem
import com.myenvironment.launcher.ui.components.LauncherItemGraphic
import com.myenvironment.launcher.ui.components.launcherVerticalSwipeGestures
import kotlin.math.roundToInt

/**
 * HOME および ユーザー追加ページの自由配置Grid画面 (仕様 11, 13, 14, 20, 24)
 *
 * - 通常モード: タップで起動、長押しでコンテキストメニュー、空白長押しで編集メニュー。ドラッグ移動は無効。
 * - 編集モード: ドラッグ＆ドロップまたはセル選択による移動、空きセルへのアイテム追加、アイテム削除が可能。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeGridPage(
    page: LauncherPage,
    items: List<LayoutItem>,
    installedPackages: Set<String>,
    columns: Int,
    rows: Int,
    isExpanded: Boolean,
    isEditMode: Boolean,
    appDiscoveryRepository: AppDiscoveryRepository,
    onItemClick: (LayoutItem, Boolean) -> Unit,
    onBlankLongPress: (GridPosition) -> Unit,
    onRequestAddAtCell: (GridPosition) -> Unit,
    onMoveItem: (LayoutItem, GridPosition) -> Unit,
    onDeleteItem: (LayoutItem) -> Unit,
    onEnterEditMode: () -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    isSwipeGestureEnabled: Boolean = !isEditMode,
    modifier: Modifier = Modifier
) {
    val safeCols = columns.coerceAtLeast(3)
    val safeRows = rows.coerceAtLeast(3)

    // 各アイテムの現在の表示座標を解決 (Compact / Expanded)
    val positionedItems = remember(items, isExpanded, safeCols, safeRows) {
        items.associateBy { item ->
            item.resolvePosition(
                isExpanded = isExpanded,
                expandedColumns = safeCols,
                expandedRows = safeRows
            )
        }
    }

    // タップ移動用の選択中アイテム（ドラッグに加えてタップ移動もサポート）
    var selectedItemForMove by remember(isEditMode) { mutableStateOf<LayoutItem?>(null) }
    var activeMenuItem by remember { mutableStateOf<LayoutItem?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .launcherVerticalSwipeGestures(
                enabled = isSwipeGestureEnabled && !isEditMode,
                onSwipeUp = onSwipeUp,
                onSwipeDown = onSwipeDown
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        val density = LocalDensity.current
        val cellWidthDp = maxWidth / safeCols
        val cellHeightDp = maxHeight / safeRows
        val cellWidthPx = with(density) { cellWidthDp.toPx() }
        val cellHeightPx = with(density) { cellHeightDp.toPx() }

        // 1. 背景セルグリッド（空白長押し検出 & 編集モード時の空きセル可視化）
        for (row in 0 until safeRows) {
            for (col in 0 until safeCols) {
                val cellPos = GridPosition(col, row)
                val existingItem = positionedItems[cellPos]

                if (existingItem == null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(width = cellWidthDp, height = cellHeightDp)
                            .offset(x = cellWidthDp * col, y = cellHeightDp * row)
                            .padding(4.dp)
                            .then(
                                if (isEditMode) {
                                    Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            width = 1.dp,
                                            color = if (selectedItemForMove != null) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                            } else {
                                                Color.White.copy(alpha = 0.18f)
                                            },
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .background(Color(0x22000000))
                                        .clickable {
                                            val moving = selectedItemForMove
                                            if (moving != null) {
                                                onMoveItem(moving, cellPos)
                                                selectedItemForMove = null
                                            } else {
                                                onRequestAddAtCell(cellPos)
                                            }
                                        }
                                } else {
                                    Modifier.combinedClickable(
                                        onClick = {},
                                        onLongClick = { onBlankLongPress(cellPos) }
                                    )
                                }
                            )
                    ) {
                        if (isEditMode) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "ここに追加",
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. 配置済みアイテムの描画
        items.forEach { item ->
            val pos = item.resolvePosition(
                isExpanded = isExpanded,
                expandedColumns = safeCols,
                expandedRows = safeRows
            )

            val isInstalled = when (item.type) {
                ItemType.APP -> installedPackages.contains(item.packageName) ||
                    appDiscoveryRepository.isPackageInstalled(item.packageName)
                ItemType.SHORTCUT, ItemType.ACTION -> true
            }

            var dragOffset by remember(item.id, pos, isEditMode) { mutableStateOf(Offset.Zero) }
            val isSelectedForMove = selectedItemForMove?.id == item.id

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = cellWidthDp * item.spanX, height = cellHeightDp * item.spanY)
                    .offset {
                        IntOffset(
                            x = (cellWidthPx * pos.x + dragOffset.x).roundToInt(),
                            y = (cellHeightPx * pos.y + dragOffset.y).roundToInt()
                        )
                    }
                    .padding(4.dp)
                    .then(
                        if (isSelectedForMove) {
                            Modifier
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                        } else {
                            Modifier
                        }
                    )
                    // 編集モードでのみドラッグ移動を有効化 (仕様 14)
                    .then(
                        if (isEditMode) {
                            Modifier.pointerInput(item.id, pos, safeCols, safeRows) {
                                detectDragGestures(
                                    onDragEnd = {
                                        val targetCol = ((pos.x * cellWidthPx + dragOffset.x + cellWidthPx / 2f) / cellWidthPx)
                                            .toInt()
                                            .coerceIn(0, safeCols - 1)
                                        val targetRow = ((pos.y * cellHeightPx + dragOffset.y + cellHeightPx / 2f) / cellHeightPx)
                                            .toInt()
                                            .coerceIn(0, safeRows - 1)
                                        dragOffset = Offset.Zero
                                        val targetPos = GridPosition(targetCol, targetRow)
                                        if (targetPos != pos) {
                                            onMoveItem(item, targetPos)
                                        }
                                    },
                                    onDragCancel = {
                                        dragOffset = Offset.Zero
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount
                                    }
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
                    .combinedClickable(
                        onClick = {
                            if (isEditMode) {
                                selectedItemForMove = if (isSelectedForMove) null else item
                            } else {
                                onItemClick(item, isInstalled)
                            }
                        },
                        onLongClick = {
                            activeMenuItem = item
                        }
                    )
            ) {
                LauncherItemGraphic(
                    type = item.type,
                    packageName = item.packageName,
                    activityName = item.activityName,
                    targetUri = item.targetUri,
                    label = item.label,
                    isInstalled = isInstalled,
                    appDiscoveryRepository = appDiscoveryRepository,
                    iconSize = 48.dp,
                    showLabel = true,
                    isEditMode = false
                )

                // 編集モード時のワンタップ削除バッジ
                if (isEditMode) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                            .clickable { onDeleteItem(item) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "アイテムを削除",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // アイコン長押し時のコンテキストメニュー (仕様 6, 14)
                DropdownMenu(
                    expanded = activeMenuItem?.id == item.id,
                    onDismissRequest = { activeMenuItem = null }
                ) {
                    DropdownMenuItem(
                        text = { Text(item.label, fontWeight = FontWeight.Bold) },
                        onClick = {
                            activeMenuItem = null
                            onItemClick(item, isInstalled)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("✏️ 位置を移動 / 編集モード") },
                        onClick = {
                            activeMenuItem = null
                            onEnterEditMode()
                            selectedItemForMove = item
                        }
                    )
                    if (item.type == ItemType.APP && item.packageName.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text("ℹ️ アプリ情報") },
                            onClick = {
                                activeMenuItem = null
                                onOpenAppInfo(item.packageName)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("🗑️ ホームから削除", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            activeMenuItem = null
                            onDeleteItem(item)
                        }
                    )
                }
            }
        }
    }
}
