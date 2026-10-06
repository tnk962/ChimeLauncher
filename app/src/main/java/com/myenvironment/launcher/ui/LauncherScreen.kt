package com.myenvironment.launcher.ui

import com.myenvironment.launcher.core.model.asLayoutItem
import com.myenvironment.launcher.ui.folder.FolderDialog
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pages
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myenvironment.launcher.MainActivity
import com.myenvironment.launcher.core.feed.overlay.OverlayDragSession
import com.myenvironment.launcher.core.model.DiscoverMode
import com.myenvironment.launcher.core.model.ExpandedPageLayoutMode
import com.myenvironment.launcher.core.model.GridPosition
import com.myenvironment.launcher.core.model.ItemType
import com.myenvironment.launcher.core.model.LauncherPage
import com.myenvironment.launcher.ui.adaptive.AdaptiveLayoutSpec
import com.myenvironment.launcher.ui.adaptive.DockPlacement
import com.myenvironment.launcher.ui.adaptive.rememberAdaptiveLayoutSpec
import com.myenvironment.launcher.ui.allapps.AllAppsTinyPage
import com.myenvironment.launcher.ui.components.LauncherItemGraphic
import com.myenvironment.launcher.ui.discover.DiscoverPage
import com.myenvironment.launcher.ui.discover.LocalGoogleOverlayClient
import com.myenvironment.launcher.ui.dock.AdaptiveDock
import com.myenvironment.launcher.ui.editor.HomeEditSheet
import com.myenvironment.launcher.ui.editor.ItemPickerDialog
import com.myenvironment.launcher.ui.editor.LockedAlertDialog
import com.myenvironment.launcher.ui.editor.PageManagerDialog
import com.myenvironment.launcher.ui.editor.WidgetResizeDialog
import com.myenvironment.launcher.ui.components.LauncherDragController
import com.myenvironment.launcher.ui.components.LocalLauncherDragController
import com.myenvironment.launcher.ui.components.launcherDragHost
import com.myenvironment.launcher.ui.home.DragOrigin
import com.myenvironment.launcher.ui.home.CrossPageDragState
import com.myenvironment.launcher.ui.home.HomeGridPage
import com.myenvironment.launcher.ui.home.HomePageGridMetrics
import com.myenvironment.launcher.ui.home.MissingAppDialog
import com.myenvironment.launcher.ui.indicator.PageIndicatorBar
import com.myenvironment.launcher.ui.search.SearchOverlay
import com.myenvironment.launcher.ui.settings.JsonBackupPreviewDialog
import com.myenvironment.launcher.ui.settings.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 固定特殊ページ（Discover / All Apps / 設定）以外の、アイコン・ウィジェットを自由配置できるホームページかどうか
 */
internal fun LauncherPage.isEditableHomePage(): Boolean {
    return id != LauncherPage.PAGE_ID_DISCOVER &&
        id != LauncherPage.PAGE_ID_ALL_APPS &&
        id != LauncherPage.PAGE_ID_SETTINGS
}

/**
 * Fold展開時（左右2ページ見開きモード）のページャースロットモデル
 *
 * - Discover / All Apps / 設定は常に1ページ全画面表示固定 (SingleFull)
 * - HOME / ユーザー追加ページは左右2ページ見開き表示 (DualSpread)。HOMEのみならSingleFull
 */
internal sealed interface ExpandedPagerSlot {
    val primaryPage: LauncherPage
    val visiblePageIds: Set<String>

    data class SingleFull(
        val page: LauncherPage
    ) : ExpandedPagerSlot {
        override val primaryPage: LauncherPage get() = page
        override val visiblePageIds: Set<String> get() = setOf(page.id)
    }

    data class DualSpread(
        val leftPage: LauncherPage,
        val rightPage: LauncherPage
    ) : ExpandedPagerSlot {
        override val primaryPage: LauncherPage get() = rightPage
        override val visiblePageIds: Set<String> get() = setOf(leftPage.id, rightPage.id)
    }
}

internal fun buildExpandedDualSlots(pages: List<LauncherPage>): List<ExpandedPagerSlot> {
    if (pages.isEmpty()) {
        return listOf(ExpandedPagerSlot.SingleFull(LauncherPage.FIXED_HOME))
    }

    val slots = mutableListOf<ExpandedPagerSlot>()

    // 1. Discover ページは常に 1ページ全画面表示固定
    pages.filter { it.id == LauncherPage.PAGE_ID_DISCOVER }.forEach { discoverPage ->
        slots.add(ExpandedPagerSlot.SingleFull(discoverPage))
    }

    // 2. All Appsは見開きに含めず、ページ領域全体に表示する。
    pages.filter { it.id == LauncherPage.PAGE_ID_ALL_APPS }.forEach { appsPage ->
        slots.add(ExpandedPagerSlot.SingleFull(appsPage))
    }

    // 3. HOME / ユーザー追加ページは左右2ページ見開きペアにする
    val middlePages = pages.filter {
        it.id != LauncherPage.PAGE_ID_DISCOVER && it.id != LauncherPage.PAGE_ID_ALL_APPS &&
            it.id != LauncherPage.PAGE_ID_SETTINGS
    }
    if (middlePages.size == 1) {
        slots.add(ExpandedPagerSlot.SingleFull(middlePages[0]))
    } else if (middlePages.size >= 2) {
        for (i in 0 until middlePages.size - 1) {
            slots.add(
                ExpandedPagerSlot.DualSpread(
                    leftPage = middlePages[i],
                    rightPage = middlePages[i + 1]
                )
            )
        }
    }

    // 4. 設定ページは常に 1ページ全画面表示固定
    pages.filter { it.id == LauncherPage.PAGE_ID_SETTINGS }.forEach { settingsPage ->
        slots.add(ExpandedPagerSlot.SingleFull(settingsPage))
    }

    return slots
}

internal fun resolveExpandedDualSlotIndex(
    slots: List<ExpandedPagerSlot>,
    targetPageId: String
): Int {
    if (slots.isEmpty()) return 0
    val primaryMatch = slots.indexOfFirst { it.primaryPage.id == targetPageId }
    if (primaryMatch >= 0) return primaryMatch
    val visibleMatch = slots.indexOfFirst { it.visiblePageIds.contains(targetPageId) }
    if (visibleMatch >= 0) return visibleMatch
    val homeMatch = slots.indexOfFirst { it.visiblePageIds.contains(LauncherPage.PAGE_ID_HOME) }
    return if (homeMatch >= 0) homeMatch else 0
}

/** All Appsの表示幅ではなく、登録先HOMEの実際のグリッドを選ぶ。 */
internal fun shouldUseExpandedHomeGrid(
    isExpanded: Boolean,
    mode: ExpandedPageLayoutMode,
    hasAdditionalHomePages: Boolean
): Boolean = isExpanded && (mode == ExpandedPageLayoutMode.SINGLE_FULL || !hasAdditionalHomePages)

/**
 * Chime Launcher ルート画面
 *
 * - ページ構成: [Discover] [All Apps] [HOME] [Page 2...] [設定 (一番右)]
 * - Fold閉 (Compact): 1ページ表示 + 下部 Bottom Dock
 * - Fold開 (Expanded):
 *   - DUAL_PAGE (デフォルト):
 *     - Discover・All Apps・設定は1ページ全画面表示固定
 *     - HOME / 追加ページは左右2ページ見開き同時表示。Dockは設定位置に配置
 *   - SINGLE_FULL: 開いた時も全ページを1ページ全画面表示。Dockは設定位置に配置
 */
@Composable
fun LauncherScreen(
    viewModel: LauncherViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val googleOverlay = LocalGoogleOverlayClient.current
    val overlayState = googleOverlay?.state?.collectAsStateWithLifecycle()?.value
    LaunchedEffect(uiState.settings.discoverMode, googleOverlay) {
        googleOverlay?.setEnabled(uiState.settings.discoverMode.usesGoogleOverlay)
    }
    val context = LocalContext.current
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // AppWidget バインド許可ダイアログ用 ActivityResultLauncher
    val widgetBindLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onWidgetBindActivityResult(result.resultCode == Activity.RESULT_OK)
    }

    // AppWidget バインド・設定 Activity 起動イベント購読
    LaunchedEffect(viewModel, context) {
        viewModel.widgetSystemEvents.collect { event ->
            when (event) {
                is WidgetSystemEvent.RequestBindAppWidget -> {
                    val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, event.appWidgetId)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, event.provider)
                    }
                    runCatching {
                        widgetBindLauncher.launch(intent)
                    }.onFailure {
                        viewModel.onWidgetBindActivityResult(false)
                    }
                }

                is WidgetSystemEvent.RequestConfigureAppWidget -> {
                    val activity = context as? Activity
                    if (activity != null) {
                        runCatching {
                            viewModel.widgetHostManager.appWidgetHost.startAppWidgetConfigureActivityForResult(
                                activity,
                                event.appWidgetId,
                                0,
                                MainActivity.REQUEST_CODE_CONFIGURE_APPWIDGET,
                                null
                            )
                        }.onFailure {
                            // 設定 Activity の起動に失敗した場合もウィジェット配置自体は継続できるようにする
                            viewModel.onWidgetConfigureActivityResult(true)
                        }
                    } else {
                        viewModel.onWidgetConfigureActivityResult(true)
                    }
                }
            }
        }
    }

    val adaptiveSpec = rememberAdaptiveLayoutSpec(uiState.settings)
    val embeddedSettingsScrollState = rememberScrollState()
    val pages = uiState.pages
    val editableHomePages = remember(pages) { pages.filter { it.isEditableHomePage() } }
    val homePageIndex = uiState.homePageIndex

    // Fold展開時に2ページ見開き表示を行うかどうか
    val isDualPageMode = adaptiveSpec.isExpanded &&
        uiState.settings.expandedPageLayoutMode == ExpandedPageLayoutMode.DUAL_PAGE &&
        pages.size >= 2

    // 現在フォーカスしているメインページのID（閉じた時の表示ページ ＝ 開いた時のメインページ）
    var focusedPageId by rememberSaveable { mutableStateOf(LauncherPage.PAGE_ID_HOME) }

    val focusedPageIndex = remember(pages, focusedPageId, homePageIndex) {
        pages.indexOfFirst { it.id == focusedPageId }.takeIf { it >= 0 } ?: homePageIndex
    }

    // 1ページ表示用 PagerState
    val singlePagerState = rememberPagerState(
        initialPage = focusedPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
        pageCount = { pages.size }
    )

    // 2ページ見開き表示用スロット一覧（Discover・All Apps・設定は全面、HOME・追加ページは見開き）
    val dualSlots = remember(pages) { buildExpandedDualSlots(pages) }
    val initialDualSlotIndex = remember(dualSlots, focusedPageId) {
        resolveExpandedDualSlotIndex(dualSlots, focusedPageId)
    }
    val dualPagerState = rememberPagerState(
        initialPage = initialDualSlotIndex.coerceIn(0, (dualSlots.size - 1).coerceAtLeast(0)),
        pageCount = { dualSlots.size }
    )

    // --- ページ跨ぎドラッグ＆ドロップ状態と各ページの画面座標メトリクス ---
    var activeDragState by remember { mutableStateOf<CrossPageDragState?>(null) }
    val pageMetricsMap = remember { mutableStateMapOf<String, HomePageGridMetrics>() }
    var rootBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var pagerBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var dockBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    val dockSlotBounds = remember { mutableStateMapOf<Int, Rect>() }
    val dragController = remember { LauncherDragController() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var isSecondFingerPaging by remember { mutableStateOf(false) }
    var dragStartedInEditMode by remember { mutableStateOf(false) }
    var edgePagingInProgress by remember { mutableStateOf(false) }
    // Layout changes invalidate both geometry and the drag's original grab offset.
    LaunchedEffect(adaptiveSpec, uiState.settings.expandedPageLayoutMode, uiState.settings.layoutLocked) {
        activeDragState = null
    }
    LaunchedEffect(uiState.overlay.isEditMode) {
        if (!uiState.overlay.isEditMode) activeDragState = null
    }

    /**
     * 現在ドラッグ中のアイテムがドロップされる対象ページID・メトリクス・セル座標を算出する。
     */
    fun resolveDropTarget(drag: CrossPageDragState): Triple<String, HomePageGridMetrics, GridPosition>? {
        val sourceMetrics = pageMetricsMap[drag.sourcePageId]
        if (drag.isAppAddition || drag.origin == DragOrigin.DOCK) {
            val visibleIds = if (isDualPageMode) {
                dualSlots.getOrNull(dualPagerState.currentPage)?.visiblePageIds.orEmpty()
            } else listOfNotNull(pages.getOrNull(singlePagerState.currentPage)?.id)
            val targetId = visibleIds.firstOrNull { id ->
                pages.any { it.id == id && it.isEditableHomePage() } &&
                    pageMetricsMap[id]?.boundsInRoot?.contains(drag.fingerInRoot) == true
            } ?: return null
            val metrics = pageMetricsMap[targetId] ?: return null
            val centered = drag.fingerInRoot - androidx.compose.ui.geometry.Offset(metrics.cellWidthPx / 2, metrics.cellHeightPx / 2)
            return Triple(targetId, metrics, metrics.resolveDropCell(centered, 1, 1))
        }
        if (isDualPageMode) {
            val slot = dualSlots.getOrNull(dualPagerState.currentPage.coerceIn(0, dualSlots.lastIndex))
            val candidatePageIds = slot?.visiblePageIds.orEmpty().filter { pageId ->
                pages.find { it.id == pageId }?.isEditableHomePage() == true
            }
            val centerX = drag.topLeftInRoot.x + drag.itemWidthPx / 2f
            val targetId = candidatePageIds.minByOrNull { pageId ->
                val b = pageMetricsMap[pageId]?.boundsInRoot
                if (b == null) {
                    Float.MAX_VALUE
                } else if (centerX in b.left..b.right) {
                    0f
                } else {
                    min(abs(centerX - b.left), abs(centerX - b.right))
                }
            } ?: candidatePageIds.firstOrNull() ?: drag.sourcePageId

            val metrics = pageMetricsMap[targetId] ?: sourceMetrics ?: return null
            val cell = metrics.resolveDropCell(drag.topLeftInRoot, drag.spanX, drag.spanY)
            return Triple(targetId, metrics, cell)
        } else {
            val currentP = pages.getOrNull(singlePagerState.currentPage.coerceIn(0, pages.lastIndex))
            val targetId = if (currentP != null && currentP.isEditableHomePage()) {
                currentP.id
            } else {
                drag.sourcePageId
            }
            val rawMetrics = pageMetricsMap[targetId] ?: sourceMetrics ?: return null
            // ページ遷移直後で boundsInRoot がスクロール前の座標だった場合は pagerBoundsInRoot を基準に補正
            val effectiveMetrics = if (pagerBoundsInRoot.width > 0f) {
                rawMetrics.copy(boundsInRoot = pagerBoundsInRoot)
            } else {
                rawMetrics
            }
            val cell = effectiveMetrics.resolveDropCell(drag.topLeftInRoot, drag.spanX, drag.spanY)
            return Triple(targetId, effectiveMetrics, cell)
        }
    }

    val currentDropTarget = remember(
        activeDragState,
        isDualPageMode,
        singlePagerState.currentPage,
        dualPagerState.currentPage,
        pagerBoundsInRoot
    ) {
        activeDragState?.let { resolveDropTarget(it) }
    }

    // --- ドラッグ中の画面左右端ホバーによるページ自動遷移 (約0.65秒キープで隣接ページへ遷移) ---
    val edgeHoverZonePx = with(density) { 54.dp.toPx() }
    val currentDrag = activeDragState
    val edgeHoverDirection: Int = when {
        currentDrag == null || currentDrag.origin != DragOrigin.HOME || isSecondFingerPaging ||
            ((singlePagerState.isScrollInProgress || dualPagerState.isScrollInProgress) && !edgePagingInProgress) || pagerBoundsInRoot.width <= 0f -> 0
        currentDrag.fingerInRoot.x <= pagerBoundsInRoot.left + edgeHoverZonePx -> -1
        currentDrag.fingerInRoot.x >= pagerBoundsInRoot.right - edgeHoverZonePx -> 1
        else -> 0
    }

    // 現在のホバー方向で遷移可能なページがあるか（または右端で新規ページ作成可能か）を判定
    val edgeTransitionHint: String? = remember(
        edgeHoverDirection,
        isDualPageMode,
        singlePagerState.currentPage,
        dualPagerState.currentPage,
        pages,
        dualSlots
    ) {
        if (edgeHoverDirection == 0) {
            null
        } else if (isDualPageMode) {
            val curSlotIdx = dualPagerState.currentPage
            if (edgeHoverDirection < 0) {
                val prevSlot = dualSlots.getOrNull(curSlotIdx - 1)
                val hasHome = prevSlot?.visiblePageIds?.any { id ->
                    pages.find { it.id == id }?.isEditableHomePage() == true
                } == true
                if (hasHome) "◀ そのままキープで左のページへ" else null
            } else {
                val nextSlot = dualSlots.getOrNull(curSlotIdx + 1)
                val hasHome = nextSlot?.visiblePageIds?.any { id ->
                    pages.find { it.id == id }?.isEditableHomePage() == true
                } == true
                if (hasHome) "そのままキープで右のページへ ▶" else "＋ そのままキープで新規ページ作成 ▶"
            }
        } else {
            val curIdx = singlePagerState.currentPage
            if (edgeHoverDirection < 0) {
                val prevPage = pages.getOrNull(curIdx - 1)?.takeIf { it.isEditableHomePage() }
                if (prevPage != null) "◀ そのままキープで「${prevPage.name}」へ" else null
            } else {
                val nextPage = pages.getOrNull(curIdx + 1)
                if (nextPage != null && nextPage.isEditableHomePage()) {
                    "そのままキープで「${nextPage.name}」へ ▶"
                } else {
                    "＋ そのままキープで新規ページ作成 ▶"
                }
            }
        }
    }

    LaunchedEffect(
        activeDragState != null,
        edgeHoverDirection,
        isDualPageMode
    ) {
        if (activeDragState == null || edgeHoverDirection == 0 || edgeTransitionHint == null) {
            return@LaunchedEffect
        }
        // 画面端で約0.65秒ホバーし続けたらページを遷移する
        delay(650L)
        if (activeDragState == null) return@LaunchedEffect

        // Scrolling/page changes caused by this animation must not cancel its own effect.
        edgePagingInProgress = true
        try {
            if (isDualPageMode) {
                val curSlotIdx = dualPagerState.currentPage
                if (edgeHoverDirection < 0) {
                    val targetSlotIdx = curSlotIdx - 1
                    val prevSlot = dualSlots.getOrNull(targetSlotIdx)
                    val hasHome = prevSlot?.visiblePageIds?.any { id ->
                        pages.find { it.id == id }?.isEditableHomePage() == true
                    } == true
                    if (hasHome) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        dualPagerState.animateScrollToPage(targetSlotIdx)
                    }
                } else {
                    val targetSlotIdx = curSlotIdx + 1
                    val nextSlot = dualSlots.getOrNull(targetSlotIdx)
                    val hasHome = nextSlot?.visiblePageIds?.any { id ->
                        pages.find { it.id == id }?.isEditableHomePage() == true
                    } == true
                    if (hasHome) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        dualPagerState.animateScrollToPage(targetSlotIdx)
                    } else {
                        // 右端の最終ページでさらに右端ホバーした場合は新規ページを作成して遷移
                        val created = viewModel.createUserPageForDrag()
                        if (created != null) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            focusedPageId = created.id
                            delay(80L)
                            val updatedSlots = buildExpandedDualSlots(viewModel.uiState.value.pages)
                            val newSlotIdx = resolveExpandedDualSlotIndex(updatedSlots, created.id)
                            dualPagerState.animateScrollToPage(newSlotIdx)
                        }
                    }
                }
            } else {
                val curIdx = singlePagerState.currentPage
                if (edgeHoverDirection < 0) {
                    val targetIdx = curIdx - 1
                    val prevPage = pages.getOrNull(targetIdx)?.takeIf { it.isEditableHomePage() }
                    if (prevPage != null) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        singlePagerState.animateScrollToPage(targetIdx)
                    }
                } else {
                    val targetIdx = curIdx + 1
                    val nextPage = pages.getOrNull(targetIdx)
                    if (nextPage != null && nextPage.isEditableHomePage()) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        singlePagerState.animateScrollToPage(targetIdx)
                    } else {
                        // 右端の最終ホームページでさらに右端ホバーした場合は新規ページを作成して遷移
                        val created = viewModel.createUserPageForDrag()
                        if (created != null) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            focusedPageId = created.id
                            delay(80L)
                            val updatedPages = viewModel.uiState.value.pages
                            val newIdx = updatedPages.indexOfFirst { it.id == created.id }
                            if (newIdx >= 0) {
                                singlePagerState.animateScrollToPage(newIdx)
                            }
                        }
                    }
                }
            }
        } finally {
            edgePagingInProgress = false
        }
    }

    // Compact (1ページ) <-> Expanded (2ページ見開き) 切替時に、見ていたページへ即座に同期
    LaunchedEffect(isDualPageMode, pages.size, dualSlots.size) {
        val retainedPageId = focusedPageId.takeIf { id -> pages.any { it.id == id } }
            ?: LauncherPage.PAGE_ID_HOME
        focusedPageId = retainedPageId
        val currentFocusedIdx = pages.indexOfFirst { it.id == retainedPageId }
            .takeIf { it >= 0 } ?: homePageIndex

        if (isDualPageMode) {
            val targetSlot = resolveExpandedDualSlotIndex(dualSlots, retainedPageId)
                .coerceIn(0, (dualSlots.size - 1).coerceAtLeast(0))
            if (dualPagerState.currentPage != targetSlot) {
                dualPagerState.scrollToPage(targetSlot)
            }
        } else {
            val targetSingle = currentFocusedIdx.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
            if (singlePagerState.currentPage != targetSingle) {
                singlePagerState.scrollToPage(targetSingle)
            }
        }
    }

    // ユーザーが1ページPagerをスワイプした際に focusedPageId を更新
    LaunchedEffect(singlePagerState.settledPage, isDualPageMode, pages) {
        if (!isDualPageMode) {
            pages.getOrNull(singlePagerState.settledPage)?.let { page ->
                focusedPageId = page.id
            }
        }
    }

    // ユーザーが2ページ見開きPagerをスワイプした際に、スロットのメインページを focusedPageId として記録
    LaunchedEffect(dualPagerState.settledPage, isDualPageMode, dualSlots) {
        if (isDualPageMode) {
            dualSlots.getOrNull(dualPagerState.settledPage)?.let { slot ->
                // 表示中のペインに指定ページが残っていれば、Foldを閉じる際の復帰先を維持する。
                if (focusedPageId !in slot.visiblePageIds) focusedPageId = slot.primaryPage.id
            }
        }
    }

    // Homeジェスチャーまたはページジャンプ要求時に指定ページへスクロール (仕様 4)
    LaunchedEffect(viewModel, pages, dualSlots, isDualPageMode) {
        viewModel.pageNavigationEvents.collectPageNavigationRequests { targetPageId ->
            val targetIdx = pages.indexOfFirst { it.id == targetPageId }
            if (targetIdx >= 0) {
                focusedPageId = targetPageId
                if (isDualPageMode) {
                    val targetSlot = resolveExpandedDualSlotIndex(dualSlots, targetPageId)
                        .coerceIn(0, (dualSlots.size - 1).coerceAtLeast(0))
                    dualPagerState.animateScrollToPage(targetSlot)
                } else if (targetIdx < singlePagerState.pageCount) {
                    singlePagerState.animateScrollToPage(targetIdx)
                }
            }
        }
    }

    // Overlayの起点になる左端ページを確認する。
    val leftmostActivePageId = if (isDualPageMode) {
        val slot = dualSlots.getOrNull(dualPagerState.settledPage)
        when (slot) {
            is ExpandedPagerSlot.SingleFull -> slot.page.id
            is ExpandedPagerSlot.DualSpread -> slot.leftPage.id
            null -> null
        }
    } else {
        pages.getOrNull(singlePagerState.settledPage)?.id
    }
    // 現在編集や追加のターゲットとなるメインページ
    val currentPage = if (isDualPageMode) {
        val currentSlot = dualSlots.getOrNull(dualPagerState.currentPage.coerceIn(0, dualSlots.lastIndex))
        currentSlot?.primaryPage ?: LauncherPage.FIXED_HOME
    } else {
        pages.getOrNull(singlePagerState.currentPage.coerceIn(0, pages.lastIndex)) ?: LauncherPage.FIXED_HOME
    }
    val visiblePageIndices: Set<Int> = if (isDualPageMode) {
        val currentSlot = dualSlots.getOrNull(dualPagerState.currentPage.coerceIn(0, dualSlots.lastIndex))
        val visibleIds = currentSlot?.visiblePageIds.orEmpty()
        pages.mapIndexedNotNull { idx, page -> if (page.id in visibleIds) idx else null }.toSet()
    } else {
        setOf(singlePagerState.currentPage.coerceIn(0, pages.lastIndex))
    }

    // Discoverページに到達して静止しているか（左端行き止まりからの追加スワイプでGoogleアプリを起動するため）
    val isSettledOnDiscover = if (isDualPageMode) {
        !dualPagerState.isScrollInProgress &&
            dualSlots.getOrNull(dualPagerState.settledPage)?.primaryPage?.id == LauncherPage.PAGE_ID_DISCOVER
    } else {
        !singlePagerState.isScrollInProgress &&
            pages.getOrNull(singlePagerState.settledPage)?.id == LauncherPage.PAGE_ID_DISCOVER
    }

    val isSettingsPageActive = leftmostActivePageId == LauncherPage.PAGE_ID_SETTINGS &&
        !(if (isDualPageMode) dualPagerState.isScrollInProgress else singlePagerState.isScrollInProgress)

    // Google-only mode reveals from the leftmost visible page, including HOME when All Apps is hidden.
    val canRevealGoogleFromAllApps = uiState.settings.discoverMode == DiscoverMode.GOOGLE_ONLY &&
        activeDragState == null && !uiState.overlay.isEditMode &&
        leftmostActivePageId == pages.firstOrNull()?.id &&
        !(if (isDualPageMode) dualPagerState.isScrollInProgress else singlePagerState.isScrollInProgress)
    val googleOnlyEdgeModifier = Modifier.pointerInput(canRevealGoogleFromAllApps, googleOverlay) {
        if (!canRevealGoogleFromAllApps) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val drag = OverlayDragSession(viewConfiguration.touchSlop,
                googleOverlay?.revealWidth ?: size.width.toFloat())
            var overlayDragging = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed || event.changes.any { it.id != down.id && it.pressed }) break
                    val progress = drag.move(
                        change.position.x - change.previousPosition.x,
                        change.position.y - change.previousPosition.y
                    ) { googleOverlay?.beginScroll() == true }
                    if (progress != null) {
                        overlayDragging = true
                        change.consume()
                        googleOverlay?.scroll(progress)
                    }
                }
            } finally {
                if (overlayDragging) googleOverlay?.endScroll()
            }
        }
    }

    // Always consume Back: dismiss active UI, return other pages to HOME, and do nothing on HOME.
    // Let dialogs keep their own dismissal handlers instead of finishing the launcher Activity.
    BackHandler {
        when {
            uiState.overlay.activeFolderId != null -> viewModel.closeFolder()
            activeDragState != null -> activeDragState = null
            (overlayState?.progress ?: 0f) > 0f -> googleOverlay?.close()
            uiState.overlay.resizingWidgetTarget != null -> viewModel.dismissResizeWidgetDialog()
            uiState.overlay.isSearchOverlayOpen -> {
                activeDragState = null
                viewModel.closeSearchOverlay()
            }
            uiState.overlay.isSettingsOpen -> viewModel.closeSettings()
            uiState.overlay.isEditMode -> viewModel.exitEditMode()
            currentPage.id != LauncherPage.PAGE_ID_HOME -> {
                viewModel.jumpToPage(LauncherPage.PAGE_ID_HOME)
            }
        }
    }

    val handleDragUpdate: (CrossPageDragState) -> Unit = { state ->
        activeDragState = state
    }
    val handleDragCancel: () -> Unit = {
        activeDragState = null
    }
    fun dockInsertionIndex(finger: androidx.compose.ui.geometry.Offset): Int {
        val vertical = adaptiveSpec.dockPlacement != DockPlacement.BOTTOM
        return dockSlotBounds.entries.filter { it.key < uiState.dockItems.size }.sortedBy { it.key }
            .firstOrNull { (_, bounds) -> if (vertical) finger.y < bounds.center.y else finger.x < bounds.center.x }
            ?.key ?: uiState.dockItems.size
    }
    fun folderTarget(drag: CrossPageDragState): String? {
        if (drag.item.type != ItemType.APP) return null
        if (dockBoundsInRoot.contains(drag.fingerInRoot)) {
            val visibleCount = minOf(uiState.dockItems.size, uiState.settings.effectiveDockIconCount)
            val slot = dockSlotBounds.entries.filter { it.key < visibleCount }.firstOrNull { (_, bounds) ->
                kotlin.math.abs(drag.fingerInRoot.x - bounds.center.x) < bounds.width * 0.28f &&
                    kotlin.math.abs(drag.fingerInRoot.y - bounds.center.y) < bounds.height * 0.28f
            } ?: return null
            return uiState.dockItems.getOrNull(slot.key)?.takeIf {
                it.id != drag.item.id && it.type in setOf(ItemType.APP, ItemType.FOLDER)
            }?.id
        }
        val target = resolveDropTarget(drag) ?: return null
        val metrics = target.second
        val centerX = metrics.boundsInRoot.left + (target.third.x + 0.5f) * metrics.cellWidthPx
        val centerY = metrics.boundsInRoot.top + (target.third.y + 0.5f) * metrics.cellHeightPx
        if (kotlin.math.abs(drag.fingerInRoot.x - centerX) > metrics.cellWidthPx * 0.28f ||
            kotlin.math.abs(drag.fingerInRoot.y - centerY) > metrics.cellHeightPx * 0.28f) return null
        return uiState.layoutItems.firstOrNull {
            it.pageId == target.first && it.id != drag.item.id && it.type in setOf(ItemType.APP, ItemType.FOLDER) &&
                it.resolveClampedPosition(metrics.isExpanded, metrics.columns, metrics.rows) == target.third
        }?.id
    }

    val handleDragEnd: (CrossPageDragState) -> Unit = { finalState ->
        if (activeDragState != null) {
            val folderId = folderTarget(finalState)
            if (folderId != null) {
                viewModel.groupAppIntoFolder(finalState.item, finalState.origin == DragOrigin.DOCK, finalState.isAppAddition, folderId)
            } else if (finalState.origin == DragOrigin.DOCK) {
                if (dockBoundsInRoot.contains(finalState.fingerInRoot)) {
                    viewModel.reorderDockItemByDrop(finalState.item.id, dockInsertionIndex(finalState.fingerInRoot))
                } else {
                    val target = resolveDropTarget(finalState)
                    if (target != null && target.second.boundsInRoot.contains(finalState.fingerInRoot)) {
                        viewModel.moveDockItemToHome(finalState.item.id, target.first, target.third, target.second.isExpanded)
                    }
                }
            } else if (finalState.isAppAddition) {
                val app = uiState.installedApps.find {
                    it.packageName == finalState.item.packageName && it.activityName == finalState.item.activityName
                }
                if (app != null && dockBoundsInRoot.contains(finalState.fingerInRoot)) {
                    viewModel.addSearchAppToDock(app, dockInsertionIndex(finalState.fingerInRoot))
                } else if (app != null) {
                    val target = resolveDropTarget(finalState)
                    if (target != null && target.second.boundsInRoot.contains(finalState.fingerInRoot)) {
                        viewModel.addSearchAppToHome(app, target.first, target.third, target.second.isExpanded)
                    }
                }
            } else if (dockBoundsInRoot.contains(finalState.fingerInRoot) &&
                finalState.item.type in setOf(ItemType.APP, ItemType.FOLDER)) {
                viewModel.moveHomeItemToDock(finalState.item.id, dockInsertionIndex(finalState.fingerInRoot))
            } else {
                val resolved = resolveDropTarget(finalState)
                if (resolved != null && resolved.second.boundsInRoot.contains(finalState.fingerInRoot) &&
                    pages.any { it.id == resolved.first && it.isEditableHomePage() }) {
                    viewModel.moveLayoutItem(finalState.item, resolved.third, resolved.second.isExpanded, resolved.first)
                }
            }
        }
        activeDragState = null
    }

    CompositionLocalProvider(LocalLauncherDragController provides dragController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = (overlayState?.progress ?: 0f) * size.width
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .onGloballyPositioned { coords ->
                rootBoundsInRoot = coords.boundsInRoot()
            }
            .launcherDragHost(
                controller = dragController,
                activeDrag = { activeDragState },
                canStart = { origin ->
                    !uiState.overlay.isSettingsOpen && uiState.overlay.resizingWidgetTarget == null &&
                        if (uiState.overlay.isSearchOverlayOpen) origin == DragOrigin.SEARCH else origin != DragOrigin.SEARCH
                },
                pagerBounds = { pagerBoundsInRoot },
                onStart = { state ->
                    if (uiState.settings.layoutLocked) {
                        viewModel.enterEditMode()
                        false
                    } else {
                        dragStartedInEditMode = uiState.overlay.isEditMode
                        viewModel.enterEditMode()
                        activeDragState = state
                        if (state.isAppAddition && !currentPage.isEditableHomePage()) {
                            viewModel.jumpToPage(LauncherPage.PAGE_ID_HOME)
                        }
                        if (state.origin == DragOrigin.SEARCH) {
                            keyboardController?.hide()
                            viewModel.closeSearchOverlay()
                        }
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        true
                    }
                },
                onUpdate = handleDragUpdate, onEnd = handleDragEnd, onCancel = handleDragCancel,
                onSecondaryActive = { isSecondFingerPaging = it },
                onPageSwipe = { direction ->
                    coroutineScope.launch {
                        if (isDualPageMode) {
                            val target = dualPagerState.currentPage + direction
                            if (dualSlots.getOrNull(target)?.visiblePageIds?.any { id ->
                                    pages.any { it.id == id && it.isEditableHomePage() }
                                } == true) dualPagerState.animateScrollToPage(target)
                        } else {
                            val target = singlePagerState.currentPage + direction
                            if (pages.getOrNull(target)?.isEditableHomePage() == true) singlePagerState.animateScrollToPage(target)
                        }
                    }
                }
            )
    ) {
        val mainArea: @Composable (Modifier) -> Unit = { areaModifier ->
            Column(modifier = areaModifier.then(googleOnlyEdgeModifier)) {
                EditModeBanner(
                    visible = uiState.overlay.isEditMode && (activeDragState == null || dragStartedInEditMode),
                    currentPage = currentPage,
                    onAddApp = { viewModel.requestAddItemToPage(currentPage, initialTab = 0) },
                    onAddWidget = { viewModel.requestAddItemToPage(currentPage, initialTab = 1) },
                    onManagePages = { viewModel.openPageManager() },
                    onFinishEdit = { viewModel.exitEditMode() },
                    undoCount = uiState.overlay.undoCount,
                    canUndo = !uiState.settings.layoutLocked && uiState.overlay.undoCount > 0 &&
                        !uiState.overlay.isLayoutOperationInProgress && !uiState.overlay.isWidgetPlacementPending &&
                        activeDragState == null,
                    onUndo = { viewModel.undoLastLayoutEdit() }
                )

                if (isDualPageMode) {
                    // 左右2ページ見開きモード（Discover・All Apps・設定は1ページ全画面固定、他は左右2ページ見開き）
                    HorizontalPager(
                        state = dualPagerState,
                        key = { index -> dualSlots[index].visiblePageIds.sorted().joinToString("|") },
                        beyondViewportPageCount = dualSlots.size.coerceAtLeast(1),
                        userScrollEnabled = activeDragState == null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                pagerBoundsInRoot = coords.boundsInRoot()
                            }
                    ) { slotIndex ->
                        val slot = dualSlots.getOrNull(slotIndex)
                            ?: ExpandedPagerSlot.SingleFull(LauncherPage.FIXED_HOME)

                        when (slot) {
                            is ExpandedPagerSlot.SingleFull -> {
                                LauncherPageContent(
                                    page = slot.page,
                                    uiState = uiState,
                                    adaptiveSpec = adaptiveSpec,
                                    isHalfPaneInDualMode = false,
                                    isSettledOnDiscover = isSettledOnDiscover,
                                    settingsScrollState = embeddedSettingsScrollState,
                                    isSettingsPageActive = isSettingsPageActive,
                                    activeDragState = activeDragState,
                                    highlightedDropCell = if (currentDropTarget?.first == slot.page.id) currentDropTarget.third else null,
                                    editableHomePages = editableHomePages,
                                    onGridMetricsChanged = { pageMetricsMap[it.pageId] = it },
                                    viewModel = viewModel
                                )
                            }

                            is ExpandedPagerSlot.DualSpread -> {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    ) {
                                        LauncherPageContent(
                                            page = slot.leftPage,
                                            uiState = uiState,
                                            adaptiveSpec = adaptiveSpec,
                                            isHalfPaneInDualMode = true,
                                            isSettledOnDiscover = isSettledOnDiscover,
                                            settingsScrollState = embeddedSettingsScrollState,
                                            isSettingsPageActive = isSettingsPageActive,
                                            activeDragState = activeDragState,
                                            highlightedDropCell = if (currentDropTarget?.first == slot.leftPage.id) currentDropTarget.third else null,
                                            editableHomePages = editableHomePages,
                                            onGridMetricsChanged = { pageMetricsMap[it.pageId] = it },
                                            viewModel = viewModel
                                        )
                                    }

                                    VerticalDivider(
                                        color = Color.White.copy(alpha = 0.12f),
                                        thickness = 1.dp,
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .padding(vertical = 12.dp)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    ) {
                                        LauncherPageContent(
                                            page = slot.rightPage,
                                            uiState = uiState,
                                            adaptiveSpec = adaptiveSpec,
                                            isHalfPaneInDualMode = true,
                                            isSettledOnDiscover = isSettledOnDiscover,
                                            settingsScrollState = embeddedSettingsScrollState,
                                            isSettingsPageActive = isSettingsPageActive,
                                            activeDragState = activeDragState,
                                            highlightedDropCell = if (currentDropTarget?.first == slot.rightPage.id) currentDropTarget.third else null,
                                            editableHomePages = editableHomePages,
                                            onGridMetricsChanged = { pageMetricsMap[it.pageId] = it },
                                            viewModel = viewModel
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Expanded 1ページ全画面表示モード
                    HorizontalPager(
                        state = singlePagerState,
                        key = { index -> pages[index].id },
                        beyondViewportPageCount = pages.size.coerceAtLeast(1),
                        userScrollEnabled = activeDragState == null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                pagerBoundsInRoot = coords.boundsInRoot()
                            }
                    ) { pageIndex ->
                        val page = pages.getOrNull(pageIndex) ?: LauncherPage.FIXED_HOME
                        LauncherPageContent(
                            page = page,
                            uiState = uiState,
                            adaptiveSpec = adaptiveSpec,
                            isHalfPaneInDualMode = false,
                            isSettledOnDiscover = isSettledOnDiscover,
                            settingsScrollState = embeddedSettingsScrollState,
                            isSettingsPageActive = isSettingsPageActive,
                            activeDragState = activeDragState,
                            highlightedDropCell = if (currentDropTarget?.first == page.id) currentDropTarget.third else null,
                            editableHomePages = editableHomePages,
                            onGridMetricsChanged = { pageMetricsMap[it.pageId] = it },
                            viewModel = viewModel
                        )
                    }
                }

                PageIndicatorBar(
                    pages = pages,
                    visiblePageIndices = visiblePageIndices,
                    indicatorStyle = uiState.settings.indicatorStyle,
                    isLayoutLocked = uiState.settings.layoutLocked,
                    timeSegment = uiState.overlay.currentTimeSegment,
                    timeChimeEnabled = uiState.settings.timeChimeEnabled,
                    activeChimeEvent = uiState.overlay.activeChimeEvent,
                    onSelectPage = { idx ->
                        val targetPage = pages.getOrNull(idx) ?: return@PageIndicatorBar
                        if (isDualPageMode) {
                            val targetSlot = resolveExpandedDualSlotIndex(dualSlots, targetPage.id)
                                .coerceIn(0, (dualSlots.size - 1).coerceAtLeast(0))
                            coroutineScope.launch {
                                dualPagerState.animateScrollToPage(targetSlot)
                            }
                        } else {
                            coroutineScope.launch {
                                singlePagerState.animateScrollToPage(idx)
                            }
                        }
                    },
                    onLongPressIndicator = { viewModel.openHomeEditSheet() },
                    onChimeAnimationFinished = { viewModel.onChimeAnimationFinished() }
                )
            }
        }
        val dock: @Composable () -> Unit = {
            AdaptiveDock(
                dockItems = uiState.dockItems,
                installedPackages = uiState.installedPackages,
                placement = adaptiveSpec.dockPlacement,
                iconCount = uiState.settings.effectiveDockIconCount,
                isEditMode = uiState.overlay.isEditMode && (activeDragState == null || dragStartedInEditMode),
                appDiscoveryRepository = viewModel.container.appDiscoveryRepository,
                onDockItemClick = { item, isInstalled ->
                    viewModel.onDockItemClicked(item, isInstalled)
                },
                onRemoveDockItem = { viewModel.removeDockItem(it) },
                onMoveDockItem = { item, delta -> viewModel.moveDockItem(item, delta) },
                onRequestAddDockItem = { viewModel.requestAddItemToDock() },
                onSlotBounds = { index, bounds -> dockSlotBounds[index] = bounds },
                isDropHovered = activeDragState?.let { it.item.type in setOf(ItemType.APP, ItemType.FOLDER) && dockBoundsInRoot.contains(it.fingerInRoot) } == true,
                modifier = Modifier.onGloballyPositioned { dockBoundsInRoot = it.boundsInRoot() }
            )
        }
        if (adaptiveSpec.dockPlacement == DockPlacement.BOTTOM) {
            Column(Modifier.fillMaxSize()) {
                mainArea(Modifier.weight(1f).fillMaxWidth())
                dock()
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                if (adaptiveSpec.dockPlacement == DockPlacement.LEFT) dock()
                mainArea(Modifier.weight(1f).fillMaxHeight())
                if (adaptiveSpec.dockPlacement == DockPlacement.RIGHT) dock()
            }
        }

        // --- ページ跨ぎドラッグ中のフローティングプレビュー & 左右端ホバーインジケーター ---
        val drag = activeDragState
        if (drag != null) {
            // 1. 画面左右端のページ遷移ゾーン視覚ガイド
            if (edgeHoverDirection < 0 && edgeTransitionHint != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight(0.75f)
                        .width(44.dp)
                        .clip(RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else if (edgeHoverDirection > 0 && edgeTransitionHint != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(0.75f)
                        .width(44.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // 上部中央に現在のドロップ予定ページとホバー案内を表示
            val targetPageName = pages.find { it.id == currentDropTarget?.first }?.name ?: currentPage.name
            Surface(
                color = Color(0xEE101622),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
            ) {
                Text(
                    text = edgeTransitionHint
                        ?: if (folderTarget(drag) != null) "フォルダを作成 / アプリを追加"
                        else if (drag.item.type in setOf(ItemType.APP, ItemType.FOLDER) && dockBoundsInRoot.contains(drag.fingerInRoot)) {
                            if (drag.origin == DragOrigin.DOCK) {
                                val reordered = reorderDockByInsertion(uiState.dockItems, drag.item.id, dockInsertionIndex(drag.fingerInRoot))
                                "Dock: ${reordered.indexOfFirst { it.id == drag.item.id } + 1}番目へ移動"
                            } else if (uiState.dockItems.size >= uiState.settings.effectiveDockIconCount) "Dockが満杯です"
                            else "Dock: ${dockInsertionIndex(drag.fingerInRoot) + 1}番目に追加"
                        } else if ((drag.isAppAddition || drag.origin == DragOrigin.DOCK) && currentDropTarget == null) "領域外: 離すとキャンセル"
                        else "移動先: $targetPageName ${
                            currentDropTarget?.third?.let { "(列${it.x + 1}, 行${it.y + 1})" } ?: ""
                        }",
                    color = if (edgeTransitionHint != null) MaterialTheme.colorScheme.primary else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            // 2. 指に追従するドラッグ中アイテム／ウィジェットのフローティングプレビュー
            val relX = (drag.topLeftInRoot.x - rootBoundsInRoot.left).roundToInt()
            val relY = (drag.topLeftInRoot.y - rootBoundsInRoot.top).roundToInt()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset { IntOffset(relX, relY) }
                    .size(width = drag.itemWidthDp, height = drag.itemHeightDp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xCC1A2232))
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp)
                    )
            ) {
                if (drag.item.type == ItemType.WIDGET) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = drag.item.label,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "${drag.spanX}×${drag.spanY} ウィジェットを移動中",
                            color = Color(0xFFB0BEC5),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                } else {
                    LauncherItemGraphic(
                        type = drag.item.type,
                        folderApps = drag.item.folderApps,
                        packageName = drag.item.packageName,
                        activityName = drag.item.activityName,
                        targetUri = drag.item.targetUri,
                        label = drag.item.label,
                        isInstalled = true,
                        appDiscoveryRepository = viewModel.container.appDiscoveryRepository,
                        iconSize = 48.dp,
                        showLabel = true,
                        isEditMode = false
                    )
                }
            }
        }

        // --- Overlays & Dialogs ---

        // 1. Swipe Up Search Overlay (仕様 8, 9, 21〜27, 37)
        AnimatedVisibility(
            visible = uiState.overlay.isSearchOverlayOpen,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 }
        ) {
            SearchOverlay(
                installedApps = uiState.installedApps,
                configuredShortcuts = uiState.layoutItems,
                usageMap = uiState.usageMetrics,
                hasUsageAccessPermission = uiState.hasUsageAccessPermission,
                searchEngine = viewModel.searchEngine,
                appDiscoveryRepository = viewModel.container.appDiscoveryRepository,
                onLaunchApp = { viewModel.launchApp(it) },
                onLaunchShortcut = { viewModel.onLayoutItemClicked(it, true) },
                onTriggerAction = { viewModel.triggerLauncherAction(it) },
                onGoogleSearch = { viewModel.launchGoogleSearch(it) },
                onRequestUsageAccess = { viewModel.openUsageAccessSettings() },
                onDismiss = { viewModel.closeSearchOverlay() }
            )
        }

        // 2. Settings Overlay (万が一オーバーレイ指定された場合のフォールバック)
        AnimatedVisibility(
            visible = uiState.overlay.isSettingsOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SettingsScreen(
                settings = uiState.settings,
                snapshots = uiState.snapshots,
                statusMessage = uiState.overlay.statusMessage,
                isEmbeddedPage = false,
                hasUsageAccessPermission = uiState.hasUsageAccessPermission,
                updateState = uiState.updateState,
                onClearStatusMessage = { viewModel.clearStatusMessage() },
                onToggleLayoutLock = { viewModel.setLayoutLocked(it) },
                onUpdateCompactGrid = { c, r -> viewModel.updateCompactGrid(c, r) },
                onUpdateExpandedGrid = { c, r -> viewModel.updateExpandedGrid(c, r) },
                onUpdateTinyIcons = { cc, ce, size, labels ->
                    viewModel.updateTinyIconsConfig(cc, ce, size, labels)
                },
                onSetDockIconCount = { viewModel.setDockIconCount(it) },
                onSetExpandedDockPosition = { viewModel.setExpandedDockPosition(it) },
                onSelectExpandedLayoutMode = { viewModel.setExpandedPageLayoutMode(it) },
                onSelectIndicatorStyle = { viewModel.setIndicatorStyle(it) },
                onToggleFirstChime = { viewModel.setFirstChimeEnabled(it) },
                onToggleReturnChime = { viewModel.setReturnChimeEnabled(it) },
                onSelectReturnChimeInterval = { viewModel.setReturnChimeInterval(it) },
                onToggleTimeChime = { viewModel.setTimeChimeEnabled(it) },
                onToggleSwipeDownNotification = { viewModel.setSwipeDownNotificationEnabled(it) },
                onSetAllAppsPageEnabled = { viewModel.setAllAppsPageEnabled(it) },
                onSetFeedCategoryEnabled = { category, enabled -> viewModel.setFeedCategoryEnabled(category, enabled) },
                onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                onOpenUsageAccessSettings = { viewModel.openUsageAccessSettings() },
                onOpenDefaultHomeSettings = { viewModel.openDefaultHomeSettings() },
                onSelectDiscoverMode = { viewModel.setDiscoverMode(it) },
                onSaveSnapshot = { viewModel.saveSnapshot(it) },
                onRestoreSnapshot = { viewModel.restoreSnapshot(it) },
                onDeleteSnapshot = { viewModel.deleteSnapshot(it) },
                onExportBackupToUri = { uri -> viewModel.exportBackupToUri(context, uri) },
                onImportBackupFromUri = { uri -> viewModel.importBackupFromUri(context, uri) },
                onShowJsonPreview = { viewModel.openJsonBackupPreview() },
                onAutoBindMissingApps = { viewModel.autoBindMissingAppsToInstalledApps() },
                onAddDemoMissingAppPlaceholder = {
                    viewModel.addDemoMissingAppPlaceholder(adaptiveSpec.isExpanded && !isDualPageMode)
                },
                onCheckForUpdate = { viewModel.checkForAppUpdate() },
                onDownloadAndInstallUpdate = { viewModel.downloadAndInstallAppUpdate(it) },
                onInstallDownloadedApk = { viewModel.installDownloadedApk(it) },
                onOpenUnknownSourcesSettings = { viewModel.openUnknownAppSourcesSettings() },
                onOpenGitHubReleases = { viewModel.openGitHubReleasesPage(it) },
                onClose = { viewModel.closeSettings() }
            )
        }

        // 3. 空白長押し「ホーム画面を編集」シート (仕様 13)
        val activeFolder = uiState.overlay.activeFolderId?.let { id ->
            uiState.layoutItems.find { it.id == id && it.type == ItemType.FOLDER }
                ?: uiState.dockItems.find { it.id == id && it.type == ItemType.FOLDER }?.asLayoutItem()
        }
        LaunchedEffect(uiState.overlay.activeFolderId, activeFolder?.id) {
            if (uiState.overlay.activeFolderId != null && activeFolder == null) viewModel.closeFolder()
        }
        if (activeFolder != null) {
            FolderDialog(
                folder = activeFolder, installedApps = uiState.installedApps,
                repository = viewModel.container.appDiscoveryRepository, locked = uiState.settings.layoutLocked,
                onDismiss = viewModel::closeFolder,
                onRename = { viewModel.renameFolder(activeFolder.id, it) },
                onLaunch = { viewModel.launchFolderApp(activeFolder.id, it) },
                onAdd = { viewModel.addAppToFolder(activeFolder.id, it) },
                onExtract = { app, toDock -> viewModel.extractFolderApp(activeFolder.id, app.id, toDock) }
            )
        }

        if (uiState.overlay.isHomeEditSheetOpen) {
            HomeEditSheet(
                isLayoutLocked = uiState.settings.layoutLocked,
                isEditMode = uiState.overlay.isEditMode,
                onAddAppClick = {
                    viewModel.requestAddItemToPage(currentPage, initialTab = 0)
                },
                onAddWidgetClick = {
                    viewModel.requestAddItemToPage(currentPage, initialTab = 1)
                },
                onAddShortcutOrActionClick = {
                    viewModel.requestAddItemToPage(currentPage, initialTab = 2)
                },
                onManagePagesClick = { viewModel.openPageManager() },
                onToggleEditModeClick = { viewModel.toggleEditMode() },
                onToggleLayoutLockClick = {
                    viewModel.setLayoutLocked(!uiState.settings.layoutLocked)
                },
                onOpenSettingsClick = { viewModel.openSettings() },
                onDismiss = { viewModel.closeHomeEditSheet() }
            )
        }

        // 4. ロック中の変更操作警告ダイアログ (仕様 15)
        if (uiState.overlay.showLockedAlert) {
            LockedAlertDialog(
                onUnlock = { viewModel.unlockLayoutFromDialog() },
                onDismiss = { viewModel.dismissLockedAlert() }
            )
        }

        // 5. アイテム追加ピッカーダイアログ (仕様 12, 13)
        val pickerTarget = uiState.overlay.itemPickerTarget
        if (pickerTarget != null) {
            val desc = when (pickerTarget) {
                is ItemPickerTarget.HomePageCell ->
                    "配置先: ${pickerTarget.pageName}" +
                        (pickerTarget.preferredCell?.let { " (列${it.x + 1}, 行${it.y + 1})" } ?: "")
                is ItemPickerTarget.Dock -> "配置先: Adaptive Dock"
            }
            val initialTab = (pickerTarget as? ItemPickerTarget.HomePageCell)?.initialTab ?: 0
            val useExpandedCoord = adaptiveSpec.isExpanded && !isDualPageMode
            ItemPickerDialog(
                initialTab = initialTab,
                targetDescription = desc,
                installedApps = uiState.installedApps,
                availableWidgets = uiState.availableWidgets,
                allowWidgets = pickerTarget is ItemPickerTarget.HomePageCell,
                appDiscoveryRepository = viewModel.container.appDiscoveryRepository,
                onSelectApp = { app ->
                    viewModel.addAppFromPicker(app, useExpandedCoord)
                },
                onSelectWidget = { widget ->
                    viewModel.addWidgetFromPicker(widget, useExpandedCoord)
                },
                onSelectAction = { action ->
                    viewModel.addActionFromPicker(action, useExpandedCoord)
                },
                onCreateShortcut = { label, uri ->
                    viewModel.addShortcutFromPicker(label, uri, useExpandedCoord)
                },
                onDismiss = { viewModel.dismissItemPicker() }
            )
        }

        // 6. ウィジェットサイズ変更ダイアログ
        val resizingWidget = uiState.overlay.resizingWidgetTarget
        if (resizingWidget != null) {
            val useExpandedFullGrid = adaptiveSpec.isExpanded && !isDualPageMode
            val maxCols = if (useExpandedFullGrid) {
                uiState.settings.expandedGridColumns
            } else {
                uiState.settings.compactGridColumns
            }
            val maxRows = if (useExpandedFullGrid) {
                uiState.settings.expandedGridRows
            } else {
                uiState.settings.compactGridRows
            }
            WidgetResizeDialog(
                item = resizingWidget,
                maxColumns = maxCols,
                maxRows = maxRows,
                onConfirmResize = { newSpanX, newSpanY ->
                    viewModel.resizeWidgetItem(
                        item = resizingWidget,
                        newSpanX = newSpanX,
                        newSpanY = newSpanY
                    )
                },
                onDismiss = { viewModel.dismissResizeWidgetDialog() }
            )
        }

        // 7. ページ管理ダイアログ (仕様 16)
        if (uiState.overlay.isPageManagerOpen) {
            PageManagerDialog(
                userPages = uiState.userPages,
                onAddPage = { viewModel.addUserPage(it) },
                onRenamePage = { id, name -> viewModel.renameUserPage(id, name) },
                onDeletePage = { viewModel.deleteUserPage(it) },
                onMovePage = { id, delta -> viewModel.moveUserPage(id, delta) },
                onJumpToPage = { viewModel.jumpToPage(it) },
                onDismiss = { viewModel.closePageManager() }
            )
        }

        // 8. 未インストールアプリ・ウィジェット Placeholder ダイアログ (仕様 25)
        val missingTarget = uiState.overlay.missingAppDialogTarget
        if (missingTarget != null) {
            MissingAppDialog(
                item = missingTarget,
                installedApps = uiState.installedApps,
                onOpenPlayStore = { pkg -> viewModel.openPlayStoreForPackage(pkg) },
                onSearchPlayStore = { query -> viewModel.searchPlayStoreForQuery(query) },
                onSearchPlayStoreWeb = { query -> viewModel.searchPlayStoreOnWebForQuery(query) },
                onReplaceWithInstalledApp = { item, targetApp ->
                    viewModel.replaceMissingItemWithInstalledApp(item, targetApp)
                },
                onRemoveFromHome = { item -> viewModel.deleteLayoutItem(item) },
                onDismiss = { viewModel.dismissMissingAppDialog() }
            )
        }

        // 9. 下スワイプ通知 Accessibility オンボーディングダイアログ (仕様 7.2)
        if (uiState.overlay.showAccessibilityOnboardingDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissAccessibilityOnboarding() },
                title = {
                    Text("下スワイプで通知を開く", fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "ホーム画面の中央から下スワイプでAndroid標準の通知シェードを開くには、" +
                            "アクセシビリティ設定で「Chime Launcher 通知シェード操作」を有効にしてください。\n\n" +
                            "※画面内容の読み取りは一切行わず、通知パネル展開のみに使用します。"
                    )
                },
                confirmButton = {
                    Button(onClick = { viewModel.openAccessibilitySettings() }) {
                        Text("Accessibility権限設定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissAccessibilityOnboarding() }) {
                        Text("キャンセル")
                    }
                }
            )
        }

        // 10. JSONプレビュー・直接復元ダイアログ
        val jsonPreview = uiState.overlay.jsonPreviewContent
        if (jsonPreview != null) {
            JsonBackupPreviewDialog(
                initialJson = jsonPreview,
                onRestoreFromJsonText = { viewModel.restoreFromRawJson(it) },
                onDismiss = { viewModel.closeJsonBackupPreview() }
            )
        }
    }
    }
}

@Composable
private fun LauncherPageContent(
    page: LauncherPage,
    uiState: LauncherUiState,
    adaptiveSpec: AdaptiveLayoutSpec,
    isHalfPaneInDualMode: Boolean,
    isSettledOnDiscover: Boolean,
    settingsScrollState: ScrollState,
    isSettingsPageActive: Boolean,
    activeDragState: CrossPageDragState?,
    highlightedDropCell: GridPosition?,
    editableHomePages: List<LauncherPage>,
    onGridMetricsChanged: (HomePageGridMetrics) -> Unit,
    viewModel: LauncherViewModel
) {
    val context = LocalContext.current
    // 見開き2ページ表示の片側ペインでは、Compact用の列数・座標を使って閉じた時のレイアウトをそのまま綺麗に収める
    val useExpandedFullGrid = adaptiveSpec.isExpanded && !isHalfPaneInDualMode

    when (page.id) {
        LauncherPage.PAGE_ID_DISCOVER -> {
            DiscoverPage(
                settings = uiState.settings,
                onOpenSettings = { viewModel.jumpToPage(LauncherPage.PAGE_ID_SETTINGS) },
                feedBridge = viewModel.feedBridge,
                onOpenArticle = viewModel::openDiscoverArticle,
                isSettledOnDiscover = isSettledOnDiscover,
                onSelectDiscoverMode = { viewModel.setDiscoverMode(it) },
                onOpenGoogleApp = {
                    val opened = viewModel.feedBridge.openGoogleDiscoverApp()
                    if (!opened) {
                        viewModel.launchGoogleSearch("Google Discover")
                    }
                },
                onTriggerAction = { viewModel.triggerLauncherAction(it) }
            )
        }

        LauncherPage.PAGE_ID_ALL_APPS -> {
            val tinyCols = if (useExpandedFullGrid) {
                uiState.settings.tinyIconsColumnsExpanded
            } else {
                uiState.settings.tinyIconsColumnsCompact
            }

            AllAppsTinyPage(
                apps = uiState.installedApps,
                columns = tinyCols,
                iconSizeDp = uiState.settings.tinyIconsSizeDp,
                showLabels = uiState.settings.tinyIconsShowLabels,
                appDiscoveryRepository = viewModel.container.appDiscoveryRepository,
                onLaunchApp = { viewModel.launchApp(it) },
                onAddAppToHome = { app ->
                    viewModel.quickAddAppToHome(
                        app,
                        shouldUseExpandedHomeGrid(adaptiveSpec.isExpanded, uiState.settings.expandedPageLayoutMode, uiState.userPages.isNotEmpty()),
                        context
                    )
                },
                onAddAppToDock = { app ->
                    viewModel.quickAddAppToDock(app, context)
                },
                onOpenAppInfo = { app -> viewModel.openAppInfo(app.packageName) },
                onOpenSearch = { viewModel.onSwipeUpSearch() },
                onToggleLabels = {
                    viewModel.updateTinyIconsConfig(
                        columnsCompact = uiState.settings.tinyIconsColumnsCompact,
                        columnsExpanded = uiState.settings.tinyIconsColumnsExpanded,
                        sizeDp = uiState.settings.tinyIconsSizeDp,
                        showLabels = !uiState.settings.tinyIconsShowLabels
                    )
                },
                onChangeColumns = { newCols ->
                    if (useExpandedFullGrid) {
                        viewModel.updateTinyIconsConfig(
                            columnsCompact = uiState.settings.tinyIconsColumnsCompact,
                            columnsExpanded = newCols,
                            sizeDp = uiState.settings.tinyIconsSizeDp,
                            showLabels = uiState.settings.tinyIconsShowLabels
                        )
                    } else {
                        viewModel.updateTinyIconsConfig(
                            columnsCompact = newCols,
                            columnsExpanded = uiState.settings.tinyIconsColumnsExpanded,
                            sizeDp = uiState.settings.tinyIconsSizeDp,
                            showLabels = uiState.settings.tinyIconsShowLabels
                        )
                    }
                },
                onChangeIconSize = { newSize ->
                    viewModel.updateTinyIconsConfig(
                        columnsCompact = uiState.settings.tinyIconsColumnsCompact,
                        columnsExpanded = uiState.settings.tinyIconsColumnsExpanded,
                        sizeDp = newSize,
                        showLabels = uiState.settings.tinyIconsShowLabels
                    )
                }
            )
        }

        LauncherPage.PAGE_ID_SETTINGS -> {
            // 一番右端のChime Launcher設定ページ
            SettingsScreen(
                settings = uiState.settings,
                snapshots = uiState.snapshots,
                statusMessage = uiState.overlay.statusMessage,
                isEmbeddedPage = true,
                isActive = isSettingsPageActive,
                scrollState = settingsScrollState,
                hasUsageAccessPermission = uiState.hasUsageAccessPermission,
                updateState = uiState.updateState,
                onClearStatusMessage = { viewModel.clearStatusMessage() },
                onToggleLayoutLock = { viewModel.setLayoutLocked(it) },
                onUpdateCompactGrid = { c, r -> viewModel.updateCompactGrid(c, r) },
                onUpdateExpandedGrid = { c, r -> viewModel.updateExpandedGrid(c, r) },
                onUpdateTinyIcons = { cc, ce, size, labels ->
                    viewModel.updateTinyIconsConfig(cc, ce, size, labels)
                },
                onSetDockIconCount = { viewModel.setDockIconCount(it) },
                onSetExpandedDockPosition = { viewModel.setExpandedDockPosition(it) },
                onSelectExpandedLayoutMode = { viewModel.setExpandedPageLayoutMode(it) },
                onSelectIndicatorStyle = { viewModel.setIndicatorStyle(it) },
                onToggleFirstChime = { viewModel.setFirstChimeEnabled(it) },
                onToggleReturnChime = { viewModel.setReturnChimeEnabled(it) },
                onSelectReturnChimeInterval = { viewModel.setReturnChimeInterval(it) },
                onToggleTimeChime = { viewModel.setTimeChimeEnabled(it) },
                onToggleSwipeDownNotification = { viewModel.setSwipeDownNotificationEnabled(it) },
                onSetAllAppsPageEnabled = { viewModel.setAllAppsPageEnabled(it) },
                onSetFeedCategoryEnabled = { category, enabled -> viewModel.setFeedCategoryEnabled(category, enabled) },
                onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                onOpenUsageAccessSettings = { viewModel.openUsageAccessSettings() },
                onOpenDefaultHomeSettings = { viewModel.openDefaultHomeSettings() },
                onSelectDiscoverMode = { viewModel.setDiscoverMode(it) },
                onSaveSnapshot = { viewModel.saveSnapshot(it) },
                onRestoreSnapshot = { viewModel.restoreSnapshot(it) },
                onDeleteSnapshot = { viewModel.deleteSnapshot(it) },
                onExportBackupToUri = { uri -> viewModel.exportBackupToUri(context, uri) },
                onImportBackupFromUri = { uri -> viewModel.importBackupFromUri(context, uri) },
                onShowJsonPreview = { viewModel.openJsonBackupPreview() },
                onAutoBindMissingApps = { viewModel.autoBindMissingAppsToInstalledApps() },
                onAddDemoMissingAppPlaceholder = {
                    viewModel.addDemoMissingAppPlaceholder(useExpandedFullGrid)
                },
                onCheckForUpdate = { viewModel.checkForAppUpdate() },
                onDownloadAndInstallUpdate = { viewModel.downloadAndInstallAppUpdate(it) },
                onInstallDownloadedApk = { viewModel.installDownloadedApk(it) },
                onOpenUnknownSourcesSettings = { viewModel.openUnknownAppSourcesSettings() },
                onOpenGitHubReleases = { viewModel.openGitHubReleasesPage(it) },
                onClose = { viewModel.jumpToPage(LauncherPage.PAGE_ID_HOME) }
            )
        }

        else -> {
            // Page 0 (HOME) および HOME右側のユーザー追加ページ
            val pageItems = uiState.layoutItems.filter { it.pageId == page.id }
            val cols = if (useExpandedFullGrid) uiState.settings.expandedGridColumns else uiState.settings.compactGridColumns
            val rows = if (useExpandedFullGrid) uiState.settings.expandedGridRows else uiState.settings.compactGridRows

            HomeGridPage(
                page = page,
                items = pageItems,
                installedPackages = uiState.installedPackages,
                columns = cols,
                rows = rows,
                isExpanded = useExpandedFullGrid,
                isEditMode = uiState.overlay.isEditMode,
                appDiscoveryRepository = viewModel.container.appDiscoveryRepository,
                widgetHostManager = viewModel.widgetHostManager,
                onItemClick = { item, isInstalled ->
                    viewModel.onLayoutItemClicked(item, isInstalled)
                },
                onBlankLongPress = { viewModel.openHomeEditSheet() },
                onRequestAddAtCell = { cellPos ->
                    viewModel.requestAddItemToPage(page, preferredCell = cellPos)
                },
                onMoveItem = { item, targetPos ->
                    viewModel.moveLayoutItem(item, targetPos, useExpandedFullGrid)
                },
                onResizeWidgetRequest = { item ->
                    viewModel.openResizeWidgetDialog(item)
                },
                onRebindWidgetRequest = { item ->
                    viewModel.requestRebindExistingWidget(item, useExpandedFullGrid)
                },
                onConfigureWidgetRequest = { item ->
                    viewModel.requestConfigureExistingWidget(item)
                },
                onSilentAutoRebindWidget = { item ->
                    viewModel.trySilentAutoRebindWidget(item)
                },
                onDeleteItem = { item -> viewModel.deleteLayoutItem(item) },
                onEnterEditMode = { viewModel.enterEditMode() },
                onOpenAppInfo = { pkg -> viewModel.openAppInfo(pkg) },
                onSwipeUp = { viewModel.onSwipeUpSearch() },
                onSwipeDown = { viewModel.onSwipeDownNotification(context) },
                activeDragState = activeDragState,
                highlightedDropCell = highlightedDropCell,
                availableHomePages = editableHomePages,
                onGridMetricsChanged = onGridMetricsChanged,
                onMoveItemToAnotherPage = { item, destPageId ->
                    viewModel.moveItemToAnotherPage(item, destPageId, useExpandedFullGrid)
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditModeBanner(
    visible: Boolean,
    currentPage: LauncherPage,
    onAddApp: () -> Unit,
    onAddWidget: () -> Unit,
    onManagePages: () -> Unit,
    onFinishEdit: () -> Unit,
    undoCount: Int,
    canUndo: Boolean,
    onUndo: () -> Unit
) {
    AnimatedVisibility(visible = visible) {
        Surface(
            color = Color(0xDD1A1D24),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "編集: ${currentPage.name}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(onClick = onUndo, enabled = canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("元に戻す ($undoCount)", fontSize = 12.sp)
                    }
                    FilledTonalButton(onClick = onAddApp) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("追加", fontSize = 12.sp)
                    }
                    FilledTonalButton(onClick = onAddWidget) {
                        Icon(Icons.Default.Widgets, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Widget", fontSize = 12.sp)
                    }
                    FilledTonalButton(onClick = onManagePages) {
                        Icon(Icons.Default.Pages, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ページ", fontSize = 12.sp)
                    }
                    Button(onClick = onFinishEdit) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("完了", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
