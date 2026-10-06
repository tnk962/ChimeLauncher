package com.myenvironment.launcher.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.myenvironment.launcher.core.launcher.AppDiscoveryRepository
import com.myenvironment.launcher.core.model.GridPosition
import com.myenvironment.launcher.core.model.ItemType
import com.myenvironment.launcher.core.model.LauncherPage
import com.myenvironment.launcher.core.model.LayoutItem
import com.myenvironment.launcher.core.widget.WidgetHostManager
import com.myenvironment.launcher.ui.components.launcherDragSource
import com.myenvironment.launcher.ui.components.LauncherItemGraphic
import com.myenvironment.launcher.ui.components.launcherVerticalSwipeGestures
import kotlin.math.roundToInt

/**
 * ページ内およびページを跨ぐドラッグ＆ドロップ状態
 */
enum class DragOrigin { HOME, SEARCH, ALL_APPS, DOCK }

data class CrossPageDragState(
    val item: LayoutItem,
    val sourcePageId: String,
    val spanX: Int,
    val spanY: Int,
    val itemWidthDp: Dp,
    val itemHeightDp: Dp,
    val itemWidthPx: Float,
    val itemHeightPx: Float,
    val topLeftInRoot: Offset,
    val fingerInRoot: Offset,
    val origin: DragOrigin = DragOrigin.HOME
) {
    val isAppAddition: Boolean get() = origin == DragOrigin.SEARCH || origin == DragOrigin.ALL_APPS
}

/**
 * 各 [HomeGridPage] の画面ルート上の描画領域およびセル寸法
 */
data class HomePageGridMetrics(
    val pageId: String,
    val boundsInRoot: Rect,
    val columns: Int,
    val rows: Int,
    val cellWidthPx: Float,
    val cellHeightPx: Float,
    val isExpanded: Boolean
) {
    /**
     * ルート座標上のアイテム左上位置 ([topLeftInRoot]) から、このページ上のドロップ先セル座標を算出する。
     */
    fun resolveDropCell(topLeftInRoot: Offset, spanX: Int, spanY: Int): GridPosition {
        val safeCols = columns.coerceAtLeast(1)
        val safeRows = rows.coerceAtLeast(1)
        val maxCol = (safeCols - spanX.coerceIn(1, safeCols)).coerceAtLeast(0)
        val maxRow = (safeRows - spanY.coerceIn(1, safeRows)).coerceAtLeast(0)
        val localX = topLeftInRoot.x - boundsInRoot.left
        val localY = topLeftInRoot.y - boundsInRoot.top
        val col = ((localX + cellWidthPx / 2f) / cellWidthPx).toInt().coerceIn(0, maxCol)
        val row = ((localY + cellHeightPx / 2f) / cellHeightPx).toInt().coerceIn(0, maxRow)
        return GridPosition(col, row)
    }
}

/**
 * HOME および ユーザー追加ページの自由配置Grid画面 (仕様 11, 13, 14, 20, 24, 30)
 *
 * - 通常モード: タップで起動（Widgetは内部操作）、長押しでコンテキストメニュー、空白長押しで編集メニュー。ドラッグ移動は無効。
 * - 編集モード: ページ内・ページ跨ぎのドラッグ＆ドロップ移動（画面左右端ホバーでページ遷移）、Widgetリサイズ、空きセルへのアイテム追加、削除が可能。
 * - Widget表示: グリッドのキワキワ（余白1dp）まで大きく広がり、高密度スケールで多くの情報量を表示。
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
    widgetHostManager: WidgetHostManager,
    onItemClick: (LayoutItem, Boolean) -> Unit,
    onBlankLongPress: (GridPosition) -> Unit,
    onRequestAddAtCell: (GridPosition) -> Unit,
    onMoveItem: (LayoutItem, GridPosition) -> Unit,
    onResizeWidgetRequest: (LayoutItem) -> Unit,
    onRebindWidgetRequest: (LayoutItem) -> Unit,
    onConfigureWidgetRequest: (LayoutItem) -> Unit,
    onSilentAutoRebindWidget: (LayoutItem) -> Unit,
    onDeleteItem: (LayoutItem) -> Unit,
    onEnterEditMode: () -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    activeDragState: CrossPageDragState? = null,
    highlightedDropCell: GridPosition? = null,
    availableHomePages: List<LauncherPage> = emptyList(),
    onGridMetricsChanged: (HomePageGridMetrics) -> Unit = {},
    onMoveItemToAnotherPage: (LayoutItem, String?) -> Unit = { _, _ -> },
    isSwipeGestureEnabled: Boolean = !isEditMode,
    modifier: Modifier = Modifier
) {
    val safeCols = columns.coerceAtLeast(3)
    val safeRows = rows.coerceAtLeast(3)

    // マルチセル（Widgetの spanX × spanY を含む）の占有済みセル座標セットを計算
    val occupiedCells = remember(items, isExpanded, safeCols, safeRows) {
        val set = HashSet<GridPosition>()
        items.forEach { item ->
            set.addAll(
                item.occupiedCells(
                    isExpanded = isExpanded,
                    columns = safeCols,
                    rows = safeRows
                )
            )
        }
        set
    }

    // タップ移動用の選択中アイテム（ドラッグに加えてタップ移動もサポート）
    var selectedItemForMove by remember(isEditMode) { mutableStateOf<LayoutItem?>(null) }
    var activeMenuItem by remember { mutableStateOf<LayoutItem?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            // ウィジェットを画面のキワキワまで大きく表示できるよう外側余白を最小化
            .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        val density = LocalDensity.current
        val cellWidthDp = maxWidth / safeCols
        val cellHeightDp = maxHeight / safeRows
        val cellWidthPx = with(density) { cellWidthDp.toPx() }
        val cellHeightPx = with(density) { cellHeightDp.toPx() }

        val shouldIgnoreVerticalSwipeAt = remember(
            items,
            isExpanded,
            safeCols,
            safeRows,
            cellWidthPx,
            cellHeightPx,
            widgetHostManager
        ) {
            { touchOffset: Offset ->
                isTouchInsideScrollableWidget(
                    touchOffset = touchOffset,
                    items = items,
                    isExpanded = isExpanded,
                    columns = safeCols,
                    rows = safeRows,
                    cellWidthPx = cellWidthPx,
                    cellHeightPx = cellHeightPx,
                    isWidgetScrollable = { widgetItem ->
                        widgetHostManager.isWidgetScrollable(widgetItem.appWidgetId)
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .launcherVerticalSwipeGestures(
                    enabled = isSwipeGestureEnabled && !isEditMode,
                    shouldIgnoreTouchAt = shouldIgnoreVerticalSwipeAt,
                    onSwipeUp = onSwipeUp,
                    onSwipeDown = onSwipeDown
                )
                .onGloballyPositioned { coords ->
                    onGridMetricsChanged(
                        HomePageGridMetrics(
                            pageId = page.id,
                            boundsInRoot = coords.boundsInRoot(),
                            columns = safeCols,
                            rows = safeRows,
                            cellWidthPx = cellWidthPx,
                            cellHeightPx = cellHeightPx,
                            isExpanded = isExpanded
                        )
                    )
                }
        ) {
            // 1. 背景セルグリッド（空白長押し検出 & 編集モード時の空きセル可視化）
            for (row in 0 until safeRows) {
                for (col in 0 until safeCols) {
                    val cellPos = GridPosition(col, row)
                    val isCellOccupied = occupiedCells.contains(cellPos)

                    // 移動元アイテムが選択されている場合、編集モード中は任意のセルをタップして移動先指定できるようにする
                    if (!isCellOccupied || (isEditMode && selectedItemForMove != null)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(width = cellWidthDp, height = cellHeightDp)
                                .offset(x = cellWidthDp * col, y = cellHeightDp * row)
                                .padding(3.dp)
                                .then(
                                    if (isEditMode) {
                                        Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(
                                                width = 1.dp,
                                                color = if (selectedItemForMove != null) {
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                                } else {
                                                    Color.White.copy(alpha = 0.18f)
                                                },
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .background(Color(0x22000000))
                                            .clickable {
                                                val moving = selectedItemForMove
                                                if (moving != null) {
                                                    val maxCol = (safeCols - moving.resolveSpanX(safeCols)).coerceAtLeast(0)
                                                    val maxRow = (safeRows - moving.resolveSpanY(safeRows)).coerceAtLeast(0)
                                                    val clampedTarget = GridPosition(
                                                        x = cellPos.x.coerceIn(0, maxCol),
                                                        y = cellPos.y.coerceIn(0, maxRow)
                                                    )
                                                    onMoveItem(moving, clampedTarget)
                                                    selectedItemForMove = null
                                                } else if (!isCellOccupied) {
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
                            if (isEditMode && !isCellOccupied) {
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

            // 1.5. ドラッグ中のドロップ予定セル範囲ハイライト（ページ内・ページ跨ぎ共通）
            if (activeDragState != null && highlightedDropCell != null) {
                val dropColor = if (activeDragState.isAppAddition && items.any {
                    highlightedDropCell in it.occupiedCells(isExpanded, safeCols, safeRows) &&
                        it.type !in setOf(ItemType.APP, ItemType.FOLDER)
                }) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                val dropSpanX = activeDragState.spanX.coerceIn(1, safeCols)
                val dropSpanY = activeDragState.spanY.coerceIn(1, safeRows)
                Box(
                    modifier = Modifier
                        .size(
                            width = cellWidthDp * dropSpanX,
                            height = cellHeightDp * dropSpanY
                        )
                        .offset(
                            x = cellWidthDp * highlightedDropCell.x,
                            y = cellHeightDp * highlightedDropCell.y
                        )
                        .padding(2.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(dropColor.copy(alpha = 0.28f))
                        .border(
                            width = 2.dp,
                            color = dropColor,
                            shape = RoundedCornerShape(10.dp)
                        )
                )
            }

            // 2. 配置済みアイテム（App / Shortcut / Action / Widget）の描画
            items.forEach { item ->
                val effectiveSpanX = item.resolveSpanX(safeCols)
                val effectiveSpanY = item.resolveSpanY(safeRows)
                val pos = item.resolveClampedPosition(
                    isExpanded = isExpanded,
                    columns = safeCols,
                    rows = safeRows
                )

                val isInstalled = when (item.type) {
                    ItemType.APP, ItemType.WIDGET -> installedPackages.contains(item.packageName) ||
                        appDiscoveryRepository.isPackageInstalled(item.packageName)
                    ItemType.SHORTCUT, ItemType.ACTION, ItemType.FOLDER -> true
                }

                val isBeingDragged = activeDragState?.item?.id == item.id
                val isSelectedForMove = selectedItemForMove?.id == item.id
                val itemWidthDp = cellWidthDp * effectiveSpanX
                val itemHeightDp = cellHeightDp * effectiveSpanY
                // ウィジェットはグリッド境界のキワキワ（1.dp）まで広げ、通常アイコンは 3.dp とする
                val itemPaddingDp = if (item.type == ItemType.WIDGET) 1.dp else 3.dp

                var itemBoxCoords by remember(item.id) { mutableStateOf<LayoutCoordinates?>(null) }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = itemWidthDp, height = itemHeightDp)
                        .offset {
                            IntOffset(
                                x = (cellWidthPx * pos.x).roundToInt(),
                                y = (cellHeightPx * pos.y).roundToInt()
                            )
                        }
                        .onGloballyPositioned { coords ->
                            itemBoxCoords = coords
                        }
                        .alpha(if (isBeingDragged) 0.25f else 1f)
                        .padding(itemPaddingDp)
                        .then(
                            if (isSelectedForMove) {
                                Modifier
                                    .border(
                                        width = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                            } else {
                                Modifier
                            }
                        )
                        .launcherDragSource(
                            key = "home:${page.id}:${item.id}", origin = DragOrigin.HOME,
                            enabled = isEditMode || item.type != ItemType.WIDGET, afterLongPress = !isEditMode,
                            onLongPressRelease = { activeMenuItem = item },
                            createState = { finger ->
                                val coords = itemBoxCoords
                                if (coords == null || !coords.isAttached) null else CrossPageDragState(
                                    item = item, sourcePageId = page.id,
                                    spanX = effectiveSpanX, spanY = effectiveSpanY,
                                    itemWidthDp = itemWidthDp, itemHeightDp = itemHeightDp,
                                    itemWidthPx = cellWidthPx * effectiveSpanX,
                                    itemHeightPx = cellHeightPx * effectiveSpanY,
                                    topLeftInRoot = coords.boundsInRoot().topLeft, fingerInRoot = finger
                                )
                            }
                        )
                        .then(
                            if (item.type != ItemType.WIDGET || isEditMode) {
                                Modifier.combinedClickable(
                                    onClick = {
                                        if (isEditMode && item.type != ItemType.FOLDER) {
                                            selectedItemForMove = if (isSelectedForMove) null else item
                                        } else {
                                            onItemClick(item, isInstalled)
                                        }
                                    },
                                    // The root drag host opens the menu on release without movement.
                                    // A child long-click timer would open a popup before dragging can begin.
                                    onLongClick = null
                                ).semantics {
                                    onLongClick("ホームのメニュー") { activeMenuItem = item; true }
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    if (item.type == ItemType.WIDGET) {
                        WidgetItemView(
                            item = item,
                            spanX = effectiveSpanX,
                            spanY = effectiveSpanY,
                            widthDp = (itemWidthDp - 2.dp).coerceAtLeast(40.dp),
                            heightDp = (itemHeightDp - 2.dp).coerceAtLeast(40.dp),
                            isPackageInstalled = isInstalled,
                            isEditMode = isEditMode,
                            widgetHostManager = widgetHostManager,
                            onLongPressWidget = { activeMenuItem = item },
                            onRequestResize = { onResizeWidgetRequest(item) },
                            onRequestRebindWidget = { onRebindWidgetRequest(it) },
                            onMissingWidgetClick = { onItemClick(it, false) },
                            onSilentAutoRebindAttempt = { onSilentAutoRebindWidget(it) }
                        )
                    } else {
                        LauncherItemGraphic(
                            type = item.type,
                            folderApps = item.folderApps,
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
                    }

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

                    // アイコン / ウィジェット長押し時のコンテキストメニュー (仕様 6, 14)
                    DropdownMenu(
                        expanded = activeMenuItem?.id == item.id,
                        onDismissRequest = { activeMenuItem = null }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (item.type == ItemType.WIDGET) {
                                        "🧩 ${item.label} (${effectiveSpanX}×${effectiveSpanY})"
                                    } else {
                                        item.label
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            onClick = {
                                activeMenuItem = null
                                if (item.type == ItemType.WIDGET) {
                                    onResizeWidgetRequest(item)
                                } else {
                                    onItemClick(item, isInstalled)
                                }
                            }
                        )
                        if (item.type == ItemType.WIDGET) {
                            DropdownMenuItem(
                                text = { Text("📐 サイズを変更 (${effectiveSpanX}×${effectiveSpanY})") },
                                onClick = {
                                    activeMenuItem = null
                                    onResizeWidgetRequest(item)
                                }
                            )
                            val boundInfo = widgetHostManager.getAppWidgetInfo(item.appWidgetId)
                            if (boundInfo?.configure != null) {
                                DropdownMenuItem(
                                    text = { Text("⚙️ ウィジェットの設定") },
                                    onClick = {
                                        activeMenuItem = null
                                        onConfigureWidgetRequest(item)
                                    }
                                )
                            } else if (boundInfo == null && isInstalled) {
                                DropdownMenuItem(
                                    text = { Text("🔄 ウィジェットを再バインド") },
                                    onClick = {
                                        activeMenuItem = null
                                        onRebindWidgetRequest(item)
                                    }
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text("✏️ 位置を移動 / 編集モード") },
                            onClick = {
                                activeMenuItem = null
                                onEnterEditMode()
                                selectedItemForMove = item
                            }
                        )

                        // 別ページへの直接移動メニュー
                        val otherPages = availableHomePages.filter { it.id != page.id }
                        if (otherPages.isNotEmpty()) {
                            HorizontalDivider()
                            otherPages.forEach { targetPage ->
                                DropdownMenuItem(
                                    text = { Text("📄 「${targetPage.name}」へ移動") },
                                    onClick = {
                                        activeMenuItem = null
                                        onMoveItemToAnotherPage(item, targetPage.id)
                                    }
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text("➕ 新しいページを作成して移動") },
                            onClick = {
                                activeMenuItem = null
                                onMoveItemToAnotherPage(item, null)
                            }
                        )
                        HorizontalDivider()

                        if ((item.type == ItemType.APP || item.type == ItemType.WIDGET) &&
                            item.packageName.isNotBlank()
                        ) {
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
}

/**
 * タッチ位置 ([touchOffset]) が縦スクロール可能なウィジェットの領域内にあるかどうかを判定する。
 *
 * - スクロール可能なウィジェット（Google Keepのメモ一覧、カレンダー予定リスト、Gmail等）の上での上下操作は
 *   通知シェードや検索オーバーレイを発火させず、ウィジェット自身のスクロールのみを反応させる。
 * - スクロールしないウィジェット（時計など）や通常アプリアイコン・空白領域の上では false を返し、
 *   従来通り上下スワイプで検索・通知シェードを呼び出せるようにする。
 */
internal fun isTouchInsideScrollableWidget(
    touchOffset: Offset,
    items: List<LayoutItem>,
    isExpanded: Boolean,
    columns: Int,
    rows: Int,
    cellWidthPx: Float,
    cellHeightPx: Float,
    isWidgetScrollable: (LayoutItem) -> Boolean
): Boolean {
    if (cellWidthPx <= 0f || cellHeightPx <= 0f) return false
    val safeCols = columns.coerceAtLeast(1)
    val safeRows = rows.coerceAtLeast(1)

    for (item in items) {
        if (item.type != ItemType.WIDGET) continue
        val spanX = item.resolveSpanX(safeCols)
        val spanY = item.resolveSpanY(safeRows)
        val pos = item.resolveClampedPosition(
            isExpanded = isExpanded,
            columns = safeCols,
            rows = safeRows
        )
        val left = pos.x * cellWidthPx
        val top = pos.y * cellHeightPx
        val right = (pos.x + spanX) * cellWidthPx
        val bottom = (pos.y + spanY) * cellHeightPx

        if (touchOffset.x in left..right && touchOffset.y in top..bottom) {
            if (isWidgetScrollable(item)) {
                return true
            }
        }
    }
    return false
}

