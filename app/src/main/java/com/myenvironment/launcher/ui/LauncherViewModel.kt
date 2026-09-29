package com.myenvironment.launcher.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myenvironment.launcher.AppContainer
import com.myenvironment.launcher.accessibility.NotificationShadeService
import com.myenvironment.launcher.core.model.AppInfo
import com.myenvironment.launcher.core.model.BackupSnapshotSummary
import com.myenvironment.launcher.core.model.DiscoverMode
import com.myenvironment.launcher.core.model.DockItem
import com.myenvironment.launcher.core.model.GridPosition
import com.myenvironment.launcher.core.model.ItemType
import com.myenvironment.launcher.core.model.LauncherAction
import com.myenvironment.launcher.core.model.LauncherPage
import com.myenvironment.launcher.core.model.LauncherSettings
import com.myenvironment.launcher.core.model.LayoutItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * アイテム追加ピッカーの追加先指定
 */
sealed interface ItemPickerTarget {
    data class HomePageCell(
        val pageId: String,
        val pageName: String,
        val preferredCell: GridPosition? = null,
        val initialTab: Int = 0
    ) : ItemPickerTarget

    data object Dock : ItemPickerTarget
}

/**
 * ViewModel内のローカルUI制御状態
 */
data class OverlayControlState(
    val isEditMode: Boolean = false,
    val isSearchOverlayOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isHomeEditSheetOpen: Boolean = false,
    val isPageManagerOpen: Boolean = false,
    val showLockedAlert: Boolean = false,
    val showAccessibilityOnboardingDialog: Boolean = false,
    val missingAppDialogTarget: LayoutItem? = null,
    val itemPickerTarget: ItemPickerTarget? = null,
    val jsonPreviewContent: String? = null,
    val statusMessage: String? = null
)

/**
 * Launcher全体の統合UIステート
 */
data class LauncherUiState(
    val installedApps: List<AppInfo> = emptyList(),
    val installedPackages: Set<String> = emptySet(),
    val pages: List<LauncherPage> = listOf(
        LauncherPage.FIXED_DISCOVER,
        LauncherPage.FIXED_ALL_APPS,
        LauncherPage.FIXED_HOME
    ),
    val userPages: List<LauncherPage> = emptyList(),
    val homePageIndex: Int = 2,
    val layoutItems: List<LayoutItem> = emptyList(),
    val dockItems: List<DockItem> = emptyList(),
    val settings: LauncherSettings = LauncherSettings(),
    val snapshots: List<BackupSnapshotSummary> = emptyList(),
    val overlay: OverlayControlState = OverlayControlState()
)

class LauncherViewModel(
    val container: AppContainer
) : ViewModel() {

    private val appDiscovery = container.appDiscoveryRepository
    private val appLauncher = container.appLauncher
    private val layoutRepository = container.layoutRepository
    private val settingsRepository = container.settingsRepository
    private val backupManager = container.backupManager
    val searchEngine = container.searchEngine
    val feedBridge = container.feedBridge

    private val overlayState = MutableStateFlow(OverlayControlState())

    // Pagerを指定ページIDへ移動させるイベントストリーム (Home Gesture復帰等で利用: 仕様 4)
    private val _pageNavigationEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val pageNavigationEvents: SharedFlow<String> = _pageNavigationEvents.asSharedFlow()

    val uiState: StateFlow<LauncherUiState> = combine(
        combine(
            appDiscovery.installedApps,
            appDiscovery.installedPackages,
            layoutRepository.userPages
        ) { apps, pkgs, uPages -> Triple(apps, pkgs, uPages) },
        combine(
            layoutRepository.layoutItems,
            layoutRepository.dockItems,
            settingsRepository.settings
        ) { items, dock, settings -> Triple(items, dock, settings) },
        combine(
            backupManager.savedSnapshots,
            overlayState
        ) { snaps, overlay -> snaps to overlay }
    ) { (apps, pkgs, uPages), (items, dock, settings), (snaps, overlay) ->
        val fixedLeftAndHome = buildList {
            if (settings.discoverMode != DiscoverMode.DISABLED) {
                add(LauncherPage.FIXED_DISCOVER)
            }
            add(LauncherPage.FIXED_ALL_APPS)
            add(LauncherPage.FIXED_HOME)
        }
        // 一番右にスワイプした時にマイランチャー設定ページが出るように末尾へ配置
        val allPages = fixedLeftAndHome + uPages.sortedBy { it.sortOrder } + LauncherPage.FIXED_SETTINGS
        val homeIdx = allPages.indexOfFirst { it.id == LauncherPage.PAGE_ID_HOME }.coerceAtLeast(0)

        LauncherUiState(
            installedApps = apps,
            installedPackages = pkgs,
            pages = allPages,
            userPages = uPages.sortedBy { it.sortOrder },
            homePageIndex = homeIdx,
            layoutItems = items,
            dockItems = dock,
            settings = settings,
            snapshots = snaps,
            overlay = overlay
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LauncherUiState()
    )

    init {
        viewModelScope.launch {
            layoutRepository.ensureInitialized()
        }
    }

    /**
     * AndroidのHome操作が実行された際、すべてのオーバーレイを閉じてHOMEページへ戻す (仕様 4)
     */
    fun onHomeGestureInvoked() {
        overlayState.update {
            it.copy(
                isEditMode = false,
                isSearchOverlayOpen = false,
                isSettingsOpen = false,
                isHomeEditSheetOpen = false,
                isPageManagerOpen = false,
                showLockedAlert = false,
                showAccessibilityOnboardingDialog = false,
                missingAppDialogTarget = null,
                itemPickerTarget = null,
                jsonPreviewContent = null
            )
        }
        _pageNavigationEvents.tryEmit(LauncherPage.PAGE_ID_HOME)
    }

    fun jumpToPage(pageId: String) {
        _pageNavigationEvents.tryEmit(pageId)
    }

    // --- レイアウトロック判定ヘルパー (仕様 15) ---
    private fun runIfUnlocked(action: () -> Unit) {
        if (uiState.value.settings.layoutLocked) {
            overlayState.update { it.copy(showLockedAlert = true) }
        } else {
            action()
        }
    }

    fun dismissLockedAlert() {
        overlayState.update { it.copy(showLockedAlert = false) }
    }

    fun unlockLayoutFromDialog() {
        viewModelScope.launch {
            settingsRepository.setLayoutLocked(false)
            overlayState.update { it.copy(showLockedAlert = false) }
        }
    }

    // --- ジェスチャー操作 (仕様 6, 7, 8) ---
    private fun canTriggerHomeSwipeGesture(): Boolean {
        val ov = overlayState.value
        return !ov.isSettingsOpen &&
            !ov.isSearchOverlayOpen &&
            !ov.isEditMode &&
            !ov.isHomeEditSheetOpen &&
            !ov.isPageManagerOpen &&
            !ov.showLockedAlert &&
            !ov.showAccessibilityOnboardingDialog &&
            ov.missingAppDialogTarget == null &&
            ov.itemPickerTarget == null &&
            ov.jsonPreviewContent == null
    }

    fun onSwipeUpSearch() {
        if (!canTriggerHomeSwipeGesture()) return
        overlayState.update { it.copy(isSearchOverlayOpen = true) }
    }

    fun openSearchOverlay() {
        if (overlayState.value.isSettingsOpen) return
        overlayState.update { it.copy(isSearchOverlayOpen = true) }
    }

    fun closeSearchOverlay() {
        overlayState.update { it.copy(isSearchOverlayOpen = false) }
    }

    fun onSwipeDownNotification(context: Context) {
        if (!canTriggerHomeSwipeGesture()) return
        val currentSettings = uiState.value.settings
        if (!currentSettings.swipeDownNotificationEnabled) return

        val expanded = NotificationShadeService.expandNotifications(context)
        if (!expanded) {
            // AccessibilityService も StatusBarManager も使えない場合のみオンボーディングを表示 (仕様 7.2)
            overlayState.update { it.copy(showAccessibilityOnboardingDialog = true) }
        }
    }

    fun dismissAccessibilityOnboarding() {
        overlayState.update { it.copy(showAccessibilityOnboardingDialog = false) }
    }

    fun openAccessibilitySettings() {
        overlayState.update { it.copy(showAccessibilityOnboardingDialog = false) }
        appLauncher.openAccessibilitySettings()
    }

    fun openDefaultHomeSettings() {
        appLauncher.openDefaultHomeSettings()
    }

    fun openAppInfo(packageName: String) {
        appLauncher.openAppDetailsSettings(packageName)
    }

    // --- アプリ / Shortcut / Action / Placeholder 起動 (仕様 12, 24, 25) ---
    fun onLayoutItemClicked(item: LayoutItem, isInstalled: Boolean) {
        when (item.type) {
            ItemType.APP -> {
                if (!isInstalled) {
                    // 未インストールPlaceholderの場合はPlayストア連携ダイアログを表示 (仕様 25)
                    overlayState.update { it.copy(missingAppDialogTarget = item) }
                } else {
                    val launched = appLauncher.launchApp(item.packageName, item.activityName)
                    if (!launched) {
                        overlayState.update { it.copy(missingAppDialogTarget = item) }
                    }
                }
            }
            ItemType.SHORTCUT -> {
                appLauncher.launchShortcutUri(item.targetUri)
            }
            ItemType.ACTION -> {
                val action = LauncherAction.fromActionId(item.targetUri)
                if (action != null) {
                    triggerLauncherAction(action)
                }
            }
        }
    }

    fun onDockItemClicked(item: DockItem, isInstalled: Boolean) {
        when (item.type) {
            ItemType.APP -> {
                if (!isInstalled) {
                    overlayState.update {
                        it.copy(
                            missingAppDialogTarget = LayoutItem(
                                id = item.id,
                                pageId = "dock",
                                type = item.type,
                                packageName = item.packageName,
                                activityName = item.activityName,
                                targetUri = item.targetUri,
                                label = item.label,
                                compact = GridPosition(0, 0)
                            )
                        )
                    }
                } else {
                    appLauncher.launchApp(item.packageName, item.activityName)
                }
            }
            ItemType.SHORTCUT -> {
                appLauncher.launchShortcutUri(item.targetUri)
            }
            ItemType.ACTION -> {
                val action = LauncherAction.fromActionId(item.targetUri)
                if (action != null) {
                    triggerLauncherAction(action)
                }
            }
        }
    }

    fun launchApp(app: AppInfo) {
        appLauncher.launchApp(app)
    }

    fun launchGoogleSearch(query: String) {
        appLauncher.launchGoogleSearch(query)
    }

    fun openPlayStoreForPackage(packageName: String) {
        appLauncher.openPlayStore(packageName)
    }

    fun dismissMissingAppDialog() {
        overlayState.update { it.copy(missingAppDialogTarget = null) }
    }

    fun triggerLauncherAction(action: LauncherAction) {
        when (action) {
            LauncherAction.SEARCH -> openSearchOverlay()
            LauncherAction.NOTIFICATION_SHADE -> {
                val ok = NotificationShadeService.expandNotifications(container.appContext)
                if (!ok) {
                    overlayState.update { it.copy(showAccessibilityOnboardingDialog = true) }
                }
            }
            LauncherAction.SETTINGS -> openSettings()
            LauncherAction.TOGGLE_LOCK -> {
                val nextLocked = !uiState.value.settings.layoutLocked
                setLayoutLocked(nextLocked)
            }
            LauncherAction.HATENA_FEED, LauncherAction.MY_NOTIFICATIONS -> {
                appLauncher.launchCompanionAppOrFallback(action)
            }
        }
    }

    // --- 編集シート & 編集モード制御 (仕様 13, 14, 15) ---
    fun openHomeEditSheet() {
        overlayState.update { it.copy(isHomeEditSheetOpen = true) }
    }

    fun closeHomeEditSheet() {
        overlayState.update { it.copy(isHomeEditSheetOpen = false) }
    }

    fun enterEditMode() = runIfUnlocked {
        overlayState.update { it.copy(isEditMode = true) }
    }

    fun toggleEditMode() {
        if (overlayState.value.isEditMode) {
            overlayState.update { it.copy(isEditMode = false) }
        } else {
            runIfUnlocked {
                overlayState.update { it.copy(isEditMode = true) }
            }
        }
    }

    fun exitEditMode() {
        overlayState.update { it.copy(isEditMode = false) }
    }

    fun openSettings() {
        _pageNavigationEvents.tryEmit(LauncherPage.PAGE_ID_SETTINGS)
    }

    fun closeSettings() {
        overlayState.update { it.copy(isSettingsOpen = false, statusMessage = null) }
        _pageNavigationEvents.tryEmit(LauncherPage.PAGE_ID_HOME)
    }

    // --- アイテム追加・移動・削除 (仕様 11, 13, 14, 20) ---
    fun requestAddItemToPage(
        page: LauncherPage,
        preferredCell: GridPosition? = null,
        initialTab: Int = 0
    ) = runIfUnlocked {
        val targetPage = if (
            page.id == LauncherPage.PAGE_ID_DISCOVER ||
            page.id == LauncherPage.PAGE_ID_ALL_APPS ||
            page.id == LauncherPage.PAGE_ID_SETTINGS
        ) {
            LauncherPage.FIXED_HOME
        } else {
            page
        }
        overlayState.update {
            it.copy(
                itemPickerTarget = ItemPickerTarget.HomePageCell(
                    pageId = targetPage.id,
                    pageName = targetPage.name,
                    preferredCell = preferredCell,
                    initialTab = initialTab
                )
            )
        }
    }

    fun requestAddItemToDock() = runIfUnlocked {
        overlayState.update {
            it.copy(itemPickerTarget = ItemPickerTarget.Dock)
        }
    }

    fun dismissItemPicker() {
        overlayState.update { it.copy(itemPickerTarget = null) }
    }

    fun addAppFromPicker(app: AppInfo, isExpandedMode: Boolean) = runIfUnlocked {
        val target = overlayState.value.itemPickerTarget ?: ItemPickerTarget.HomePageCell(
            pageId = LauncherPage.PAGE_ID_HOME,
            pageName = "HOME"
        )
        viewModelScope.launch {
            when (target) {
                is ItemPickerTarget.HomePageCell -> {
                    val pos = target.preferredCell ?: findFirstAvailableCell(target.pageId, isExpandedMode)
                    val newItem = LayoutItem(
                        id = UUID.randomUUID().toString(),
                        pageId = target.pageId,
                        type = ItemType.APP,
                        packageName = app.packageName,
                        activityName = app.activityName,
                        label = app.label,
                        compact = pos,
                        expanded = if (isExpandedMode) pos else null
                    )
                    layoutRepository.upsertLayoutItem(newItem)
                }
                is ItemPickerTarget.Dock -> {
                    val currentDock = uiState.value.dockItems
                    val newDock = DockItem(
                        id = UUID.randomUUID().toString(),
                        positionIndex = currentDock.size,
                        type = ItemType.APP,
                        packageName = app.packageName,
                        activityName = app.activityName,
                        label = app.label
                    )
                    layoutRepository.upsertDockItem(newDock)
                }
            }
        }
    }

    fun addActionFromPicker(action: LauncherAction, isExpandedMode: Boolean) = runIfUnlocked {
        val target = overlayState.value.itemPickerTarget ?: return@runIfUnlocked
        viewModelScope.launch {
            when (target) {
                is ItemPickerTarget.HomePageCell -> {
                    val pos = target.preferredCell ?: findFirstAvailableCell(target.pageId, isExpandedMode)
                    val newItem = LayoutItem(
                        id = UUID.randomUUID().toString(),
                        pageId = target.pageId,
                        type = ItemType.ACTION,
                        packageName = action.companionPackageName.orEmpty(),
                        targetUri = action.actionId,
                        label = action.title,
                        compact = pos,
                        expanded = if (isExpandedMode) pos else null
                    )
                    layoutRepository.upsertLayoutItem(newItem)
                }
                is ItemPickerTarget.Dock -> {
                    val currentDock = uiState.value.dockItems
                    val newDock = DockItem(
                        id = UUID.randomUUID().toString(),
                        positionIndex = currentDock.size,
                        type = ItemType.ACTION,
                        packageName = action.companionPackageName.orEmpty(),
                        targetUri = action.actionId,
                        label = action.title
                    )
                    layoutRepository.upsertDockItem(newDock)
                }
            }
        }
    }

    fun addShortcutFromPicker(label: String, uri: String, isExpandedMode: Boolean) = runIfUnlocked {
        val target = overlayState.value.itemPickerTarget ?: return@runIfUnlocked
        viewModelScope.launch {
            when (target) {
                is ItemPickerTarget.HomePageCell -> {
                    val pos = target.preferredCell ?: findFirstAvailableCell(target.pageId, isExpandedMode)
                    val newItem = LayoutItem(
                        id = UUID.randomUUID().toString(),
                        pageId = target.pageId,
                        type = ItemType.SHORTCUT,
                        packageName = "",
                        targetUri = uri,
                        label = label,
                        compact = pos,
                        expanded = if (isExpandedMode) pos else null
                    )
                    layoutRepository.upsertLayoutItem(newItem)
                }
                is ItemPickerTarget.Dock -> {
                    val currentDock = uiState.value.dockItems
                    val newDock = DockItem(
                        id = UUID.randomUUID().toString(),
                        positionIndex = currentDock.size,
                        type = ItemType.SHORTCUT,
                        packageName = "",
                        targetUri = uri,
                        label = label
                    )
                    layoutRepository.upsertDockItem(newDock)
                }
            }
        }
    }

    fun quickAddAppToHome(app: AppInfo, isExpandedMode: Boolean, context: Context) = runIfUnlocked {
        viewModelScope.launch {
            val pos = findFirstAvailableCell(LauncherPage.PAGE_ID_HOME, isExpandedMode)
            val newItem = LayoutItem(
                id = UUID.randomUUID().toString(),
                pageId = LauncherPage.PAGE_ID_HOME,
                type = ItemType.APP,
                packageName = app.packageName,
                activityName = app.activityName,
                label = app.label,
                compact = pos,
                expanded = if (isExpandedMode) pos else null
            )
            layoutRepository.upsertLayoutItem(newItem)
            Toast.makeText(context, "${app.label} をHOMEに追加しました", Toast.LENGTH_SHORT).show()
        }
    }

    fun quickAddAppToDock(app: AppInfo, context: Context) = runIfUnlocked {
        viewModelScope.launch {
            val currentDock = uiState.value.dockItems
            val newDock = DockItem(
                id = UUID.randomUUID().toString(),
                positionIndex = currentDock.size,
                type = ItemType.APP,
                packageName = app.packageName,
                activityName = app.activityName,
                label = app.label
            )
            layoutRepository.upsertDockItem(newDock)
            Toast.makeText(context, "${app.label} をDockに追加しました", Toast.LENGTH_SHORT).show()
        }
    }

    fun moveLayoutItem(item: LayoutItem, newPosition: GridPosition, isExpandedMode: Boolean) = runIfUnlocked {
        viewModelScope.launch {
            val state = uiState.value
            val cols = if (isExpandedMode) state.settings.expandedGridColumns else state.settings.compactGridColumns
            val rows = if (isExpandedMode) state.settings.expandedGridRows else state.settings.compactGridRows
            val currentPos = item.resolvePosition(isExpandedMode, cols, rows)

            // 移動先セルに既に別アイテムがある場合は位置をスワップする
            val occupyingItem = state.layoutItems
                .filter { it.pageId == item.pageId && it.id != item.id }
                .find { it.resolvePosition(isExpandedMode, cols, rows) == newPosition }

            if (occupyingItem != null) {
                layoutRepository.updateItemPosition(occupyingItem.id, currentPos, isExpandedMode)
            }
            layoutRepository.updateItemPosition(item.id, newPosition, isExpandedMode)
        }
    }

    fun deleteLayoutItem(item: LayoutItem) = runIfUnlocked {
        viewModelScope.launch {
            if (item.pageId == "dock") {
                layoutRepository.deleteDockItem(item.id)
            } else {
                layoutRepository.deleteLayoutItem(item.id)
            }
        }
    }

    fun removeDockItem(item: DockItem) = runIfUnlocked {
        viewModelScope.launch {
            layoutRepository.deleteDockItem(item.id)
        }
    }

    fun moveDockItem(item: DockItem, delta: Int) = runIfUnlocked {
        val current = uiState.value.dockItems.toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index == -1) return@runIfUnlocked
        val targetIndex = (index + delta).coerceIn(0, current.lastIndex)
        if (targetIndex == index) return@runIfUnlocked

        val removed = current.removeAt(index)
        current.add(targetIndex, removed)
        viewModelScope.launch {
            layoutRepository.replaceDockItems(current)
        }
    }

    // --- ページ管理 (仕様 16) ---
    fun openPageManager() = runIfUnlocked {
        overlayState.update { it.copy(isPageManagerOpen = true) }
    }

    fun closePageManager() {
        overlayState.update { it.copy(isPageManagerOpen = false) }
    }

    fun addUserPage(name: String) = runIfUnlocked {
        viewModelScope.launch {
            val newPage = layoutRepository.addUserPage(name)
            _pageNavigationEvents.tryEmit(newPage.id)
        }
    }

    fun renameUserPage(pageId: String, newName: String) = runIfUnlocked {
        viewModelScope.launch {
            layoutRepository.renameUserPage(pageId, newName)
        }
    }

    fun deleteUserPage(pageId: String) = runIfUnlocked {
        viewModelScope.launch {
            layoutRepository.deleteUserPage(pageId)
        }
    }

    fun moveUserPage(pageId: String, delta: Int) = runIfUnlocked {
        val pages = uiState.value.userPages.toMutableList()
        val idx = pages.indexOfFirst { it.id == pageId }
        if (idx == -1) return@runIfUnlocked
        val targetIdx = (idx + delta).coerceIn(0, pages.lastIndex)
        if (targetIdx == idx) return@runIfUnlocked

        val page = pages.removeAt(idx)
        pages.add(targetIdx, page)
        viewModelScope.launch {
            layoutRepository.reorderUserPages(pages.map { it.id })
        }
    }

    // --- 設定更新 ---
    fun setLayoutLocked(locked: Boolean) {
        viewModelScope.launch {
            settingsRepository.setLayoutLocked(locked)
            if (locked) {
                overlayState.update { it.copy(isEditMode = false) }
            }
        }
    }

    fun updateCompactGrid(columns: Int, rows: Int) {
        viewModelScope.launch {
            settingsRepository.setCompactGridSize(columns, rows)
        }
    }

    fun updateExpandedGrid(columns: Int, rows: Int) {
        viewModelScope.launch {
            settingsRepository.setExpandedGridSize(columns, rows)
        }
    }

    fun updateTinyIconsConfig(
        columnsCompact: Int,
        columnsExpanded: Int,
        sizeDp: Int,
        showLabels: Boolean
    ) {
        viewModelScope.launch {
            settingsRepository.setTinyIconsConfig(
                columnsCompact = columnsCompact,
                columnsExpanded = columnsExpanded,
                sizeDp = sizeDp,
                showLabels = showLabels
            )
        }
    }

    fun setSwipeDownNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSwipeDownNotificationEnabled(enabled)
        }
    }

    fun setDiscoverMode(mode: DiscoverMode) {
        viewModelScope.launch {
            settingsRepository.setDiscoverMode(mode)
        }
    }

    fun setExpandedPageLayoutMode(mode: com.myenvironment.launcher.core.model.ExpandedPageLayoutMode) {
        viewModelScope.launch {
            settingsRepository.setExpandedPageLayoutMode(mode)
        }
    }

    fun setAllAppsLeftOnlyInExpandedSingle(leftOnly: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAllAppsLeftOnlyInExpandedSingle(leftOnly)
        }
    }

    // --- Backup & Restore (仕様 22, 23, 24) ---
    fun saveSnapshot(customName: String?) {
        viewModelScope.launch {
            val summary = backupManager.saveInternalSnapshot(customName)
            overlayState.update {
                it.copy(statusMessage = "バックアップ「${summary.name}」を保存しました")
            }
        }
    }

    fun restoreSnapshot(snapshotId: Long) {
        viewModelScope.launch {
            val result = backupManager.restoreInternalSnapshot(snapshotId)
            overlayState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { payload -> "バックアップ (${payload.createdAt}) を復元しました" },
                        onFailure = { err -> "復元に失敗しました: ${err.message}" }
                    )
                )
            }
        }
    }

    fun deleteSnapshot(snapshotId: Long) {
        viewModelScope.launch {
            backupManager.deleteInternalSnapshot(snapshotId)
        }
    }

    fun exportBackupToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val jsonString = backupManager.exportToJsonString()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(jsonString.toByteArray(Charsets.UTF_8))
                    } ?: error("出力ストリームを開けませんでした")
                }
            }
            overlayState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { "JSONファイルへエクスポートしました" },
                        onFailure = { e -> "エクスポート失敗: ${e.message}" }
                    )
                )
            }
        }
    }

    fun importBackupFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val (bytes, displayName) = withContext(Dispatchers.IO) {
                    val fileBytes = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes()
                    } ?: error("ファイルを読み込めませんでした")
                    val name = resolveDisplayName(context, uri)
                    fileBytes to name
                }
                val isNova = com.myenvironment.launcher.core.backup.NovaBackupConverter
                    .isNovaBackupOrSqlite(bytes, displayName)
                val payload = backupManager.importFromBackupBytes(bytes, displayName).getOrThrow()
                payload to isNova
            }
            overlayState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { (payload, isNova) ->
                            val totalIcons = payload.pages.sumOf { p -> p.items.size }
                            val gridCols = payload.settings.compactGridColumns
                            val gridRows = payload.settings.compactGridRows
                            if (isNova) {
                                "Novaバックアップを変換・インポートしました (${payload.pages.size}ページ / ${totalIcons}アイコン / グリッド ${gridCols}列×${gridRows}行)"
                            } else {
                                "ファイルから復元しました (${payload.pages.size}ページ / ${totalIcons}アイテム / グリッド ${gridCols}列×${gridRows}行)"
                            }
                        },
                        onFailure = { e -> "インポート失敗: ${e.message}" }
                    )
                )
            }
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            } ?: uri.lastPathSegment
        }.getOrNull() ?: uri.lastPathSegment
    }

    fun openJsonBackupPreview() {
        viewModelScope.launch {
            val json = backupManager.exportToJsonString()
            overlayState.update { it.copy(jsonPreviewContent = json) }
        }
    }

    fun closeJsonBackupPreview() {
        overlayState.update { it.copy(jsonPreviewContent = null) }
    }

    fun restoreFromRawJson(jsonText: String) {
        viewModelScope.launch {
            val result = backupManager.restoreFromJsonString(jsonText)
            overlayState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { "JSONテキストからレイアウトを復元しました" },
                        onFailure = { e -> "JSON復元エラー: ${e.message}" }
                    )
                )
            }
        }
    }

    fun addDemoMissingAppPlaceholder(isExpandedMode: Boolean) {
        viewModelScope.launch {
            val pos = findFirstAvailableCell(LauncherPage.PAGE_ID_HOME, isExpandedMode)
            val missingItem = LayoutItem(
                id = UUID.randomUUID().toString(),
                pageId = LauncherPage.PAGE_ID_HOME,
                type = ItemType.APP,
                packageName = "com.spotify.music",
                activityName = "com.spotify.music.MainActivity",
                label = "Spotify",
                compact = pos,
                expanded = if (isExpandedMode) pos else null
            )
            layoutRepository.upsertLayoutItem(missingItem)
            overlayState.update {
                it.copy(statusMessage = "HOME画面に Spotify (Placeholder検証用) を追加しました")
            }
        }
    }

    fun clearStatusMessage() {
        overlayState.update { it.copy(statusMessage = null) }
    }

    private fun findFirstAvailableCell(pageId: String, isExpandedMode: Boolean): GridPosition {
        val state = uiState.value
        val cols = if (isExpandedMode) state.settings.expandedGridColumns else state.settings.compactGridColumns
        val rows = if (isExpandedMode) state.settings.expandedGridRows else state.settings.compactGridRows
        val occupied = state.layoutItems
            .filter { it.pageId == pageId }
            .map { it.resolvePosition(isExpandedMode, cols, rows) }
            .toSet()

        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val candidate = GridPosition(x, y)
                if (!occupied.contains(candidate)) {
                    return candidate
                }
            }
        }
        return GridPosition(0, 0)
    }

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LauncherViewModel(container) as T
                }
            }
    }
}
