package com.myenvironment.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pages
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myenvironment.launcher.core.model.DiscoverMode
import com.myenvironment.launcher.core.model.ExpandedPageLayoutMode
import com.myenvironment.launcher.core.model.LauncherPage
import com.myenvironment.launcher.ui.adaptive.AdaptiveLayoutSpec
import com.myenvironment.launcher.ui.adaptive.DockPlacement
import com.myenvironment.launcher.ui.adaptive.rememberAdaptiveLayoutSpec
import com.myenvironment.launcher.ui.allapps.AllAppsTinyPage
import com.myenvironment.launcher.ui.discover.DiscoverPage
import com.myenvironment.launcher.ui.dock.AdaptiveDock
import com.myenvironment.launcher.ui.editor.HomeEditSheet
import com.myenvironment.launcher.ui.editor.ItemPickerDialog
import com.myenvironment.launcher.ui.editor.LockedAlertDialog
import com.myenvironment.launcher.ui.editor.PageManagerDialog
import com.myenvironment.launcher.ui.home.HomeGridPage
import com.myenvironment.launcher.ui.home.MissingAppDialog
import com.myenvironment.launcher.ui.search.SearchOverlay
import com.myenvironment.launcher.ui.settings.JsonBackupPreviewDialog
import com.myenvironment.launcher.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

/**
 * My Launcher ルート画面
 *
 * - ページ構成: [Discover] [All Apps] [HOME] [Page 2...] [設定 (一番右)]
 * - Fold閉 (Compact): 1ページ表示 + 下部 Bottom Dock
 * - Fold開 (Expanded):
 *   - DUAL_PAGE (デフォルト): 閉じた時に見ていたページを「右側」に寄せ、「左側」に1つ前のページを並べて2ページ同時表示 + 右端 Right Dock
 *     (例: HOME表示中に開くと 左=All Apps / 右=HOME、All Apps表示中に開くと 左=Discover / 右=All Apps)
 *   - SINGLE_FULL: 開いた時も1ページ全画面表示 (All Appsを左半分だけに寄せるオプション付き) + 右端 Right Dock
 */
@Composable
fun LauncherScreen(
    viewModel: LauncherViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val adaptiveSpec = rememberAdaptiveLayoutSpec(uiState.settings)
    val pages = uiState.pages
    val homePageIndex = uiState.homePageIndex

    // Fold展開時に2ページ見開き表示を行うかどうか
    val isDualPageMode = adaptiveSpec.isExpanded &&
        uiState.settings.expandedPageLayoutMode == ExpandedPageLayoutMode.DUAL_PAGE &&
        pages.size >= 2

    // 現在フォーカスしているメインページのID（閉じた時の表示ページ ＝ 開いた時の右側ページ）
    var focusedPageId by rememberSaveable { mutableStateOf(LauncherPage.PAGE_ID_HOME) }

    val focusedPageIndex = remember(pages, focusedPageId, homePageIndex) {
        pages.indexOfFirst { it.id == focusedPageId }.takeIf { it >= 0 } ?: homePageIndex
    }

    // 1ページ表示用 PagerState
    val singlePagerState = rememberPagerState(
        initialPage = focusedPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
        pageCount = { pages.size }
    )

    // 2ページ見開き表示用 PagerState (pairIndex = 右側ページのインデックス - 1)
    val dualPageCount = (pages.size - 1).coerceAtLeast(1)
    val initialPairIndex = (focusedPageIndex - 1).coerceIn(0, dualPageCount - 1)
    val dualPagerState = rememberPagerState(
        initialPage = initialPairIndex,
        pageCount = { dualPageCount }
    )

    // Compact (1ページ) <-> Expanded (2ページ見開き) 切替時に、見ていたページが右側に来るよう即座に同期
    LaunchedEffect(isDualPageMode, pages.size) {
        val currentFocusedIdx = pages.indexOfFirst { it.id == focusedPageId }
            .takeIf { it >= 0 } ?: homePageIndex

        if (isDualPageMode) {
            // 閉じた時のページ (currentFocusedIdx) が右側に来るペア = currentFocusedIdx - 1
            val targetPair = (currentFocusedIdx - 1).coerceIn(0, (pages.size - 2).coerceAtLeast(0))
            if (dualPagerState.currentPage != targetPair) {
                dualPagerState.scrollToPage(targetPair)
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

    // ユーザーが2ページ見開きPagerをスワイプした際に、右側ペインのページを focusedPageId として記録
    LaunchedEffect(dualPagerState.settledPage, isDualPageMode, pages) {
        if (isDualPageMode) {
            val rightIdx = (dualPagerState.settledPage + 1).coerceIn(0, pages.lastIndex)
            pages.getOrNull(rightIdx)?.let { rightPage ->
                focusedPageId = rightPage.id
            }
        }
    }

    // Homeジェスチャーまたはページジャンプ要求時に指定ページへスクロール (仕様 4)
    LaunchedEffect(viewModel, pages, isDualPageMode) {
        viewModel.pageNavigationEvents.collect { targetPageId ->
            val targetIdx = pages.indexOfFirst { it.id == targetPageId }
            if (targetIdx >= 0) {
                focusedPageId = targetPageId
                if (isDualPageMode) {
                    val targetPair = (targetIdx - 1).coerceIn(0, (pages.size - 2).coerceAtLeast(0))
                    dualPagerState.animateScrollToPage(targetPair)
                } else if (targetIdx < singlePagerState.pageCount) {
                    singlePagerState.animateScrollToPage(targetIdx)
                }
            }
        }
    }

    // DiscoverMode が GOOGLE_APP の場合、左端のDiscoverページへ到達したタイミングでGoogleアプリを起動する (仕様 29)
    val leftmostActivePageId = if (isDualPageMode) {
        pages.getOrNull(dualPagerState.settledPage)?.id
    } else {
        pages.getOrNull(singlePagerState.settledPage)?.id
    }
    var lastLeftmostPageId by remember { mutableStateOf(LauncherPage.PAGE_ID_HOME) }
    LaunchedEffect(leftmostActivePageId, uiState.settings.discoverMode) {
        val currentId = leftmostActivePageId ?: return@LaunchedEffect
        if (currentId == LauncherPage.PAGE_ID_DISCOVER &&
            lastLeftmostPageId != LauncherPage.PAGE_ID_DISCOVER &&
            uiState.settings.discoverMode == DiscoverMode.GOOGLE_APP
        ) {
            viewModel.feedBridge.openGoogleDiscoverApp()
        }
        lastLeftmostPageId = currentId
    }

    // 現在編集や追加のターゲットとなるメインページ（見開き時は右側ページ）
    val activeMainPageIndex = if (isDualPageMode) {
        (dualPagerState.currentPage + 1).coerceIn(0, pages.lastIndex)
    } else {
        singlePagerState.currentPage.coerceIn(0, pages.lastIndex)
    }
    val currentPage = pages.getOrNull(activeMainPageIndex) ?: LauncherPage.FIXED_HOME
    val visiblePageIndices: Set<Int> = if (isDualPageMode) {
        val left = dualPagerState.currentPage.coerceIn(0, pages.lastIndex)
        val right = (dualPagerState.currentPage + 1).coerceIn(0, pages.lastIndex)
        setOf(left, right)
    } else {
        setOf(singlePagerState.currentPage.coerceIn(0, pages.lastIndex))
    }

    // Backボタン押下時：オーバーレイや編集モードを閉じ、HOME以外のページにいる場合はHOMEへ戻す (仕様 4)
    val shouldInterceptBack = uiState.overlay.isSearchOverlayOpen ||
        uiState.overlay.isSettingsOpen ||
        uiState.overlay.isEditMode ||
        currentPage.id != LauncherPage.PAGE_ID_HOME

    BackHandler(enabled = shouldInterceptBack) {
        when {
            uiState.overlay.isSearchOverlayOpen -> viewModel.closeSearchOverlay()
            uiState.overlay.isSettingsOpen -> viewModel.closeSettings()
            uiState.overlay.isEditMode -> viewModel.exitEditMode()
            currentPage.id != LauncherPage.PAGE_ID_HOME -> {
                viewModel.jumpToPage(LauncherPage.PAGE_ID_HOME)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (adaptiveSpec.dockPlacement == DockPlacement.BOTTOM) {
            // Compact (Fold Closed) -> [Pager + Bottom Dock]
            Column(modifier = Modifier.fillMaxSize()) {
                EditModeBanner(
                    visible = uiState.overlay.isEditMode,
                    currentPage = currentPage,
                    onAddApp = { viewModel.requestAddItemToPage(currentPage) },
                    onManagePages = { viewModel.openPageManager() },
                    onFinishEdit = { viewModel.exitEditMode() }
                )

                HorizontalPager(
                    state = singlePagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { pageIndex ->
                    val page = pages.getOrNull(pageIndex) ?: LauncherPage.FIXED_HOME
                    LauncherPageContent(
                        page = page,
                        uiState = uiState,
                        adaptiveSpec = adaptiveSpec,
                        isHalfPaneInDualMode = false,
                        viewModel = viewModel
                    )
                }

                PageIndicatorBar(
                    pages = pages,
                    visiblePageIndices = visiblePageIndices,
                    isLayoutLocked = uiState.settings.layoutLocked,
                    onSelectPage = { idx ->
                        pages.getOrNull(idx)?.let { viewModel.jumpToPage(it.id) }
                    },
                    onLongPressIndicator = { viewModel.openHomeEditSheet() }
                )

                AdaptiveDock(
                    dockItems = uiState.dockItems,
                    installedPackages = uiState.installedPackages,
                    placement = DockPlacement.BOTTOM,
                    isEditMode = uiState.overlay.isEditMode,
                    appDiscoveryRepository = viewModel.container.appDiscoveryRepository,
                    onDockItemClick = { item, isInstalled ->
                        viewModel.onDockItemClicked(item, isInstalled)
                    },
                    onRemoveDockItem = { viewModel.removeDockItem(it) },
                    onMoveDockItem = { item, delta -> viewModel.moveDockItem(item, delta) },
                    onRequestAddDockItem = { viewModel.requestAddItemToDock() }
                )
            }
        } else {
            // Expanded (Fold Open) -> [Main Area (Dual Page or Single Full) + Right Dock]
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    EditModeBanner(
                        visible = uiState.overlay.isEditMode,
                        currentPage = currentPage,
                        onAddApp = { viewModel.requestAddItemToPage(currentPage) },
                        onManagePages = { viewModel.openPageManager() },
                        onFinishEdit = { viewModel.exitEditMode() }
                    )

                    if (isDualPageMode) {
                        // 左右2ページ見開き表示: 左=1つ前のページ (pairIndex), 右=閉じた時のページ (pairIndex + 1)
                        HorizontalPager(
                            state = dualPagerState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) { pairIndex ->
                            val leftPage = pages.getOrNull(pairIndex) ?: LauncherPage.FIXED_ALL_APPS
                            val rightPage = pages.getOrNull(pairIndex + 1) ?: LauncherPage.FIXED_HOME

                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    LauncherPageContent(
                                        page = leftPage,
                                        uiState = uiState,
                                        adaptiveSpec = adaptiveSpec,
                                        isHalfPaneInDualMode = true,
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
                                        page = rightPage,
                                        uiState = uiState,
                                        adaptiveSpec = adaptiveSpec,
                                        isHalfPaneInDualMode = true,
                                        viewModel = viewModel
                                    )
                                }
                            }
                        }
                    } else {
                        // Expanded 1ページ全画面表示モード
                        HorizontalPager(
                            state = singlePagerState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) { pageIndex ->
                            val page = pages.getOrNull(pageIndex) ?: LauncherPage.FIXED_HOME
                            LauncherPageContent(
                                page = page,
                                uiState = uiState,
                                adaptiveSpec = adaptiveSpec,
                                isHalfPaneInDualMode = false,
                                viewModel = viewModel
                            )
                        }
                    }

                    PageIndicatorBar(
                        pages = pages,
                        visiblePageIndices = visiblePageIndices,
                        isLayoutLocked = uiState.settings.layoutLocked,
                        onSelectPage = { idx ->
                            if (isDualPageMode) {
                                val targetPair = if (idx == 0) 0 else (idx - 1).coerceIn(0, dualPageCount - 1)
                                coroutineScope.launch {
                                    dualPagerState.animateScrollToPage(targetPair)
                                }
                            } else {
                                coroutineScope.launch {
                                    singlePagerState.animateScrollToPage(idx)
                                }
                            }
                        },
                        onLongPressIndicator = { viewModel.openHomeEditSheet() }
                    )
                }

                // Right Dock (Expanded / Fold Open)
                AdaptiveDock(
                    dockItems = uiState.dockItems,
                    installedPackages = uiState.installedPackages,
                    placement = DockPlacement.RIGHT,
                    isEditMode = uiState.overlay.isEditMode,
                    appDiscoveryRepository = viewModel.container.appDiscoveryRepository,
                    onDockItemClick = { item, isInstalled ->
                        viewModel.onDockItemClicked(item, isInstalled)
                    },
                    onRemoveDockItem = { viewModel.removeDockItem(it) },
                    onMoveDockItem = { item, delta -> viewModel.moveDockItem(item, delta) },
                    onRequestAddDockItem = { viewModel.requestAddItemToDock() }
                )
            }
        }

        // --- Overlays & Dialogs ---

        // 1. Swipe Up Search Overlay (仕様 8, 9)
        AnimatedVisibility(
            visible = uiState.overlay.isSearchOverlayOpen,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 }
        ) {
            SearchOverlay(
                installedApps = uiState.installedApps,
                configuredShortcuts = uiState.layoutItems,
                searchEngine = viewModel.searchEngine,
                appDiscoveryRepository = viewModel.container.appDiscoveryRepository,
                onLaunchApp = { viewModel.launchApp(it) },
                onLaunchShortcut = { viewModel.onLayoutItemClicked(it, true) },
                onTriggerAction = { viewModel.triggerLauncherAction(it) },
                onGoogleSearch = { viewModel.launchGoogleSearch(it) },
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
                onClearStatusMessage = { viewModel.clearStatusMessage() },
                onToggleLayoutLock = { viewModel.setLayoutLocked(it) },
                onUpdateCompactGrid = { c, r -> viewModel.updateCompactGrid(c, r) },
                onUpdateExpandedGrid = { c, r -> viewModel.updateExpandedGrid(c, r) },
                onUpdateTinyIcons = { cc, ce, size, labels ->
                    viewModel.updateTinyIconsConfig(cc, ce, size, labels)
                },
                onSelectExpandedLayoutMode = { viewModel.setExpandedPageLayoutMode(it) },
                onToggleAllAppsLeftOnlyInExpanded = { viewModel.setAllAppsLeftOnlyInExpandedSingle(it) },
                onToggleSwipeDownNotification = { viewModel.setSwipeDownNotificationEnabled(it) },
                onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                onOpenDefaultHomeSettings = { viewModel.openDefaultHomeSettings() },
                onSelectDiscoverMode = { viewModel.setDiscoverMode(it) },
                onSaveSnapshot = { viewModel.saveSnapshot(it) },
                onRestoreSnapshot = { viewModel.restoreSnapshot(it) },
                onDeleteSnapshot = { viewModel.deleteSnapshot(it) },
                onExportBackupToUri = { uri -> viewModel.exportBackupToUri(context, uri) },
                onImportBackupFromUri = { uri -> viewModel.importBackupFromUri(context, uri) },
                onShowJsonPreview = { viewModel.openJsonBackupPreview() },
                onAddDemoMissingAppPlaceholder = {
                    viewModel.addDemoMissingAppPlaceholder(adaptiveSpec.isExpanded && !isDualPageMode)
                },
                onClose = { viewModel.closeSettings() }
            )
        }

        // 3. 空白長押し「ホーム画面を編集」シート (仕様 13)
        if (uiState.overlay.isHomeEditSheetOpen) {
            HomeEditSheet(
                isLayoutLocked = uiState.settings.layoutLocked,
                isEditMode = uiState.overlay.isEditMode,
                onAddAppClick = {
                    viewModel.requestAddItemToPage(currentPage, initialTab = 0)
                },
                onAddShortcutOrActionClick = {
                    viewModel.requestAddItemToPage(currentPage, initialTab = 1)
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
                appDiscoveryRepository = viewModel.container.appDiscoveryRepository,
                onSelectApp = { app ->
                    viewModel.addAppFromPicker(app, useExpandedCoord)
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

        // 6. ページ管理ダイアログ (仕様 16)
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

        // 7. 未インストールアプリ Placeholder ダイアログ (仕様 25)
        val missingTarget = uiState.overlay.missingAppDialogTarget
        if (missingTarget != null) {
            MissingAppDialog(
                item = missingTarget,
                onOpenPlayStore = { pkg -> viewModel.openPlayStoreForPackage(pkg) },
                onRemoveFromHome = { item -> viewModel.deleteLayoutItem(item) },
                onDismiss = { viewModel.dismissMissingAppDialog() }
            )
        }

        // 8. 下スワイプ通知 Accessibility オンボーディングダイアログ (仕様 7.2)
        if (uiState.overlay.showAccessibilityOnboardingDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissAccessibilityOnboarding() },
                title = {
                    Text("下スワイプで通知を開く", fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "ホーム画面の中央から下スワイプでAndroid標準の通知シェードを開くには、" +
                            "アクセシビリティ設定で「My Launcher 通知シェード操作」を有効にしてください。\n\n" +
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

        // 9. JSONプレビュー・直接復元ダイアログ
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

@Composable
private fun LauncherPageContent(
    page: LauncherPage,
    uiState: LauncherUiState,
    adaptiveSpec: AdaptiveLayoutSpec,
    isHalfPaneInDualMode: Boolean,
    viewModel: LauncherViewModel
) {
    val context = LocalContext.current
    // 見開き2ページ表示の片側ペインでは、Compact用の列数・座標を使って閉じた時のレイアウトをそのまま綺麗に収める
    val useExpandedFullGrid = adaptiveSpec.isExpanded && !isHalfPaneInDualMode

    when (page.id) {
        LauncherPage.PAGE_ID_DISCOVER -> {
            DiscoverPage(
                discoverMode = uiState.settings.discoverMode,
                feedBridge = viewModel.feedBridge,
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
                isExpandedSinglePage = useExpandedFullGrid,
                restrictToHalfWidthInExpanded = uiState.settings.allAppsLeftOnlyInExpandedSingle,
                appDiscoveryRepository = viewModel.container.appDiscoveryRepository,
                onLaunchApp = { viewModel.launchApp(it) },
                onAddAppToHome = { app ->
                    viewModel.quickAddAppToHome(app, useExpandedFullGrid, context)
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
                },
                onToggleHalfWidthInExpanded = {
                    viewModel.setAllAppsLeftOnlyInExpandedSingle(
                        !uiState.settings.allAppsLeftOnlyInExpandedSingle
                    )
                }
            )
        }

        LauncherPage.PAGE_ID_SETTINGS -> {
            // 一番右端のマイランチャー設定ページ
            SettingsScreen(
                settings = uiState.settings,
                snapshots = uiState.snapshots,
                statusMessage = uiState.overlay.statusMessage,
                isEmbeddedPage = true,
                onClearStatusMessage = { viewModel.clearStatusMessage() },
                onToggleLayoutLock = { viewModel.setLayoutLocked(it) },
                onUpdateCompactGrid = { c, r -> viewModel.updateCompactGrid(c, r) },
                onUpdateExpandedGrid = { c, r -> viewModel.updateExpandedGrid(c, r) },
                onUpdateTinyIcons = { cc, ce, size, labels ->
                    viewModel.updateTinyIconsConfig(cc, ce, size, labels)
                },
                onSelectExpandedLayoutMode = { viewModel.setExpandedPageLayoutMode(it) },
                onToggleAllAppsLeftOnlyInExpanded = { viewModel.setAllAppsLeftOnlyInExpandedSingle(it) },
                onToggleSwipeDownNotification = { viewModel.setSwipeDownNotificationEnabled(it) },
                onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                onOpenDefaultHomeSettings = { viewModel.openDefaultHomeSettings() },
                onSelectDiscoverMode = { viewModel.setDiscoverMode(it) },
                onSaveSnapshot = { viewModel.saveSnapshot(it) },
                onRestoreSnapshot = { viewModel.restoreSnapshot(it) },
                onDeleteSnapshot = { viewModel.deleteSnapshot(it) },
                onExportBackupToUri = { uri -> viewModel.exportBackupToUri(context, uri) },
                onImportBackupFromUri = { uri -> viewModel.importBackupFromUri(context, uri) },
                onShowJsonPreview = { viewModel.openJsonBackupPreview() },
                onAddDemoMissingAppPlaceholder = {
                    viewModel.addDemoMissingAppPlaceholder(useExpandedFullGrid)
                },
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
                onDeleteItem = { item -> viewModel.deleteLayoutItem(item) },
                onEnterEditMode = { viewModel.enterEditMode() },
                onOpenAppInfo = { pkg -> viewModel.openAppInfo(pkg) },
                onSwipeUp = { viewModel.onSwipeUpSearch() },
                onSwipeDown = { viewModel.onSwipeDownNotification(context) }
            )
        }
    }
}

@Composable
private fun EditModeBanner(
    visible: Boolean,
    currentPage: LauncherPage,
    onAddApp: () -> Unit,
    onManagePages: () -> Unit,
    onFinishEdit: () -> Unit
) {
    AnimatedVisibility(visible = visible) {
        Surface(
            color = Color(0xDD1A1D24),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "編集: ${currentPage.name}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onAddApp) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("追加", fontSize = 12.sp)
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

@Composable
private fun PageIndicatorBar(
    pages: List<LauncherPage>,
    visiblePageIndices: Set<Int>,
    isLayoutLocked: Boolean,
    onSelectPage: (Int) -> Unit,
    onLongPressIndicator: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x55101218))
                .clickable { onLongPressIndicator() }
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            pages.forEachIndexed { index, page ->
                val isSelected = visiblePageIndices.contains(index)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.3f)
                        )
                        .clickable { onSelectPage(index) }
                        .padding(horizontal = if (isSelected) 8.dp else 4.dp, vertical = 3.dp)
                ) {
                    if (isSelected) {
                        Text(
                            text = page.name,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Spacer(modifier = Modifier.size(4.dp))
                    }
                }
            }

            if (isLayoutLocked) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "レイアウトロック中",
                    tint = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
