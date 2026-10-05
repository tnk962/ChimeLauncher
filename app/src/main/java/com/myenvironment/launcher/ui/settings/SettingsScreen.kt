package com.myenvironment.launcher.ui.settings

import com.myenvironment.launcher.core.model.ExpandedDockPosition
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveable
import com.myenvironment.launcher.core.feed.FeedCategory
import androidx.compose.material.icons.filled.ChevronRight
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myenvironment.launcher.core.chime.ChimeEvent
import com.myenvironment.launcher.core.chime.TimeChimeProvider
import com.myenvironment.launcher.core.model.LauncherPage
import com.myenvironment.launcher.ui.indicator.PageIndicatorBar
import com.myenvironment.launcher.BuildConfig
import com.myenvironment.launcher.accessibility.NotificationShadeService
import com.myenvironment.launcher.core.model.BackupSnapshotSummary
import com.myenvironment.launcher.core.model.DiscoverMode
import com.myenvironment.launcher.core.model.ExpandedPageLayoutMode
import com.myenvironment.launcher.core.model.IndicatorStyle
import com.myenvironment.launcher.core.model.LauncherSettings
import com.myenvironment.launcher.core.model.ReturnChimeInterval
import com.myenvironment.launcher.core.update.AppUpdateParser
import com.myenvironment.launcher.core.update.AppUpdateState
import com.myenvironment.launcher.core.update.ReleaseUpdateInfo

/**
 * Chime Launcher 設定および Backup & Restore 画面 (仕様 2, 6〜20, 33)
 *
 * 一番右端のページ (Page Settings) として常設表示されるほか、オーバーレイとしても表示可能。
 */
@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    snapshots: List<BackupSnapshotSummary>,
    statusMessage: String?,
    isEmbeddedPage: Boolean = false,
    hasUsageAccessPermission: Boolean = false,
    updateState: AppUpdateState = AppUpdateState.Idle,
    onClearStatusMessage: () -> Unit,
    onToggleLayoutLock: (Boolean) -> Unit,
    onUpdateCompactGrid: (Int, Int) -> Unit,
    onUpdateExpandedGrid: (Int, Int) -> Unit,
    onUpdateTinyIcons: (Int, Int, Int, Boolean) -> Unit,
    onSetDockIconCount: (Int) -> Unit,
    onSetExpandedDockPosition: (ExpandedDockPosition) -> Unit,
    onSelectExpandedLayoutMode: (ExpandedPageLayoutMode) -> Unit,
    onSelectIndicatorStyle: (IndicatorStyle) -> Unit = {},
    onToggleFirstChime: (Boolean) -> Unit = {},
    onToggleReturnChime: (Boolean) -> Unit = {},
    onSelectReturnChimeInterval: (ReturnChimeInterval) -> Unit = {},
    onToggleTimeChime: (Boolean) -> Unit = {},
    onToggleSwipeDownNotification: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit = {},
    onOpenDefaultHomeSettings: () -> Unit,
    onSelectDiscoverMode: (DiscoverMode) -> Unit,
    onSetAllAppsPageEnabled: (Boolean) -> Unit,
    onSetFeedCategoryEnabled: (FeedCategory, Boolean) -> Unit,
    isActive: Boolean = true,
    onSaveSnapshot: (String?) -> Unit,
    onRestoreSnapshot: (Long) -> Unit,
    onDeleteSnapshot: (Long) -> Unit,
    onExportBackupToUri: (Uri) -> Unit,
    onImportBackupFromUri: (Uri) -> Unit,
    onShowJsonPreview: () -> Unit,
    onAutoBindMissingApps: () -> Unit = {},
    onAddDemoMissingAppPlaceholder: () -> Unit,
    onCheckForUpdate: () -> Unit = {},
    onDownloadAndInstallUpdate: (ReleaseUpdateInfo) -> Unit = {},
    onInstallDownloadedApk: (String) -> Unit = {},
    onOpenUnknownSourcesSettings: () -> Unit = {},
    onOpenGitHubReleases: (String) -> Unit = {},
    scrollState: ScrollState = rememberScrollState(),
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var showFeedSettings by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = isActive && showFeedSettings) { showFeedSettings = false }
    LaunchedEffect(isActive) { if (!isActive) showFeedSettings = false }
    val density = LocalDensity.current
    var contentHeight by remember { mutableIntStateOf(0) }
    var reservedContentHeight by remember { mutableIntStateOf(0) }
    var checkButtonY by remember { mutableStateOf<Int?>(null) }
    var checkAnchorY by remember { mutableStateOf<Int?>(null) }
    val checkKeepingPosition = {
        reservedContentHeight = maxOf(reservedContentHeight, contentHeight)
        checkAnchorY = checkButtonY
        onCheckForUpdate()
    }
    // Keep the user's pressed button at the same window coordinate even when a banner
    // above it changes. Retain the pre-check extent so a temporary shrink cannot clamp scroll.
    LaunchedEffect(scrollState) {
        snapshotFlow { Triple(checkAnchorY, checkButtonY, scrollState.maxValue) }
            .collect { (anchor, current, _) ->
                if (anchor != null && current != null && anchor != current) {
                    scrollState.scrollTo((scrollState.value + current - anchor).coerceIn(0, scrollState.maxValue))
                }
            }
    }
    LaunchedEffect(scrollState) {
        scrollState.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) checkAnchorY = null
        }
    }
    LaunchedEffect(updateState) {
        if (updateState !is AppUpdateState.Checking && checkAnchorY != null) {
            repeat(3) { withFrameNanos { } }
            checkAnchorY = null
        }
    }
    val isAccessibilityEnabled = remember(settings) {
        NotificationShadeService.isServiceEnabled(context)
    }

    var snapshotNameInput by remember { mutableStateOf("") }
    var firstPreviewId by remember { mutableStateOf(0) }
    var returnPreviewId by remember { mutableStateOf(0) }
    var firstPreview by remember { mutableStateOf<ChimeEvent?>(null) }
    var returnPreview by remember { mutableStateOf<ChimeEvent?>(null) }

    // SAF ファイルエクスポート (.json)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            onExportBackupToUri(uri)
        }
    }

    // SAF ファイルインポート (.json)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportBackupFromUri(uri)
        }
    }

    AnimatedContent(
        targetState = showFeedSettings,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (targetState) {
                slideInHorizontally(tween(250)) { it } togetherWith slideOutHorizontally(tween(250)) { -it }
            } else {
                slideInHorizontally(tween(250)) { -it } togetherWith slideOutHorizontally(tween(250)) { it }
            }
        },
        label = "feedSettingsNavigation"
    ) { feedSettings ->
        if (feedSettings) {
            FeedSettingsScreen(
                settings = settings,
                isEmbeddedPage = isEmbeddedPage,
                onSetFeedCategoryEnabled = onSetFeedCategoryEnabled,
                onBack = { showFeedSettings = false }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isEmbeddedPage) Color(0x66101218) else Color(0xF5111318)
                    )
                    .then(if (isEmbeddedPage) Modifier else Modifier.statusBarsPadding())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .heightIn(min = with(density) { reservedContentHeight.toDp() })
                        .onSizeChanged {
                            contentHeight = it.height
                            if (checkAnchorY != null) reservedContentHeight = maxOf(reservedContentHeight, it.height)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // タイトルとHOMEボタンを上段、ビルド情報を下段に配置する。
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (!isEmbeddedPage) {
                                IconButton(onClick = onClose) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "戻る",
                                        tint = Color.White
                                    )
                                }
                            }
                            Text(
                                text = "Chime Launcher 設定",
                                style = MaterialTheme.typography.titleLarge,
                                fontSize = 18.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isEmbeddedPage) {
                                FilledTonalButton(
                                    onClick = onClose,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Home, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("HOMEへ", fontSize = 12.sp, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE} • ${BuildConfig.BUILD_TIMESTAMP})",
                            color = Color(0xFF9AA0A6),
                            fontSize = 11.sp
                        )
                        Text(
                            text = BuildConfig.UPDATE_SUMMARY,
                            color = Color(0xFF9AA0A6),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }

                    if (!statusMessage.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onClearStatusMessage() }
                        ) {
                            Text(
                                text = statusMessage,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // 新しいバージョンが検知されている場合、またはダウンロード中・インストール待ちの場合は最上部にハイライト表示
                    if (updateState is AppUpdateState.UpdateAvailable ||
                        updateState is AppUpdateState.Downloading ||
                        updateState is AppUpdateState.ReadyToInstall
                    ) {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xEE18283C)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "🚀 Chime Launcher の新しいアップデートがあります",
                                    color = Color(0xFF7FD7FF),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                AppUpdateControlSection(
                                    updateState = updateState,
                                    onCheckForUpdate = onCheckForUpdate,
                                    onDownloadAndInstallUpdate = onDownloadAndInstallUpdate,
                                    onInstallDownloadedApk = onInstallDownloadedApk,
                                    onOpenUnknownSourcesSettings = onOpenUnknownSourcesSettings,
                                    onOpenGitHubReleases = onOpenGitHubReleases,
                                    showCheckButton = false
                                )
                            }
                        }
                    }

                    // 0-A. 表示 / Indicator Style (仕様 12〜18, 33)
                    SettingsSectionCard(title = "表示 (Indicator Style)") {
                        Text(
                            text = "ホーム画面下部のページインジケーターの表示スタイルを選択します。",
                            color = Color(0xFFBDC1C6),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        IndicatorStyle.entries.forEach { style ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectIndicatorStyle(style) }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = settings.indicatorStyle == style,
                                    onClick = { onSelectIndicatorStyle(style) }
                                )
                                Column(modifier = Modifier.padding(start = 6.dp)) {
                                    Text(
                                        text = style.displayName,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = style.description,
                                        color = Color(0xFF9AA0A6),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // 0-B. Chime Moments 設定 (仕様 3, 6〜11, 33)
                    SettingsSectionCard(title = "Chime Moments") {
                        Text(
                            text = "Chimeは通知しない。気づかせる。 (Chime Moments are ambient, not interruptive.)",
                            color = Color(0xFF9AD4EE),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "ポップアップや通知を出さず、ページインジケーターの微細な変化だけで1日の節目を静かに伝えます。",
                            color = Color(0xFF9AA0A6),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )

                        // First Chime
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "First Chime",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "その日初めてホーム画面を表示した際、現在位置に短い波紋を1度だけ表示します",
                                    fontSize = 11.sp,
                                    color = Color(0xFF9AA0A6)
                                )
                            }
                            Switch(
                                checked = settings.firstChimeEnabled,
                                onCheckedChange = { enabled ->
                                    onToggleFirstChime(enabled)
                                    firstPreviewId++
                                    firstPreview = if (enabled) ChimeEvent.First else null
                                }
                            )
                        }

                        if (firstPreview != null) {
                            key(firstPreviewId) {
                                ChimeSettingsPreview(settings, firstPreview) { firstPreview = null }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0x22FFFFFF))

                        // Return Chime
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Return Chime",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "一定時間以上離れてからホームへ戻った際、インジケーターの伸縮で時間の経過を表現します",
                                    fontSize = 11.sp,
                                    color = Color(0xFF9AA0A6)
                                )
                            }
                            Switch(
                                checked = settings.returnChimeEnabled,
                                onCheckedChange = { enabled ->
                                    onToggleReturnChime(enabled)
                                    returnPreviewId++
                                    returnPreview = if (enabled) ChimeEvent.Return(settings.returnChimeInterval.durationMillis) else null
                                }
                            )
                        }

                        if (settings.returnChimeEnabled) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Returnまでの時間: ${settings.returnChimeInterval.displayName}",
                                color = Color(0xFFD5DCE6),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                            ) {
                                ReturnChimeInterval.entries.forEach { interval ->
                                    val isSelected = settings.returnChimeInterval == interval
                                    if (isSelected) {
                                        FilledTonalButton(
                                            onClick = {
                                                onSelectReturnChimeInterval(interval)
                                                if (interval != settings.returnChimeInterval) {
                                                    returnPreviewId++
                                                    returnPreview = ChimeEvent.Return(interval.durationMillis)
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(interval.displayName, fontSize = 11.sp, maxLines = 1)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                onSelectReturnChimeInterval(interval)
                                                if (interval != settings.returnChimeInterval) {
                                                    returnPreviewId++
                                                    returnPreview = ChimeEvent.Return(interval.durationMillis)
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(interval.displayName, fontSize = 11.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }

                        if (returnPreview != null) {
                            key(returnPreviewId) {
                                ChimeSettingsPreview(settings, returnPreview) { returnPreview = null }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0x22FFFFFF))

                        // Time Chime
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Time Chime",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "朝・昼・夕方・夜・深夜の時間帯に応じてインジケーターの灯りを微細に変化させます",
                                    fontSize = 11.sp,
                                    color = Color(0xFF9AA0A6)
                                )
                            }
                            Switch(
                                checked = settings.timeChimeEnabled,
                                onCheckedChange = onToggleTimeChime
                            )
                        }
                    }

                    // 1. Fold展開時 (開いた画面) の表示モード設定
                    SettingsSectionCard(title = "Fold展開時 (開いた画面) の表示設定") {
                        ExpandedPageLayoutMode.entries.forEach { mode ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectExpandedLayoutMode(mode) }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = settings.expandedPageLayoutMode == mode,
                                    onClick = { onSelectExpandedLayoutMode(mode) }
                                )
                                Column(modifier = Modifier.padding(start = 6.dp)) {
                                    Text(
                                        text = mode.displayName,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (mode == ExpandedPageLayoutMode.DUAL_PAGE) {
                                            "HOMEと追加ページを左右2ページで表示します。Discover・All Apps・設定は全面表示です"
                                        } else mode.description,
                                        color = Color(0xFF9AA0A6),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                    }

                    // 2. ホーム画面とレイアウトロック (仕様 11, 15, 20)
                    SettingsSectionCard(title = "ホーム画面 & レイアウトロック") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "レイアウトをロック",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "アイコン移動・削除・ページ変更・Dock編集を禁止し誤操作を防ぎます",
                                    fontSize = 12.sp,
                                    color = Color(0xFF9AA0A6)
                                )
                            }
                            Switch(
                                checked = settings.layoutLocked,
                                onCheckedChange = onToggleLayoutLock
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        Text("Dockのアイコン数: ${settings.effectiveDockIconCount}個", color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onSetDockIconCount(settings.effectiveDockIconCount - 1) }, enabled = settings.effectiveDockIconCount > 1) { Text("−") }
                            OutlinedButton(onClick = { onSetDockIconCount(settings.effectiveDockIconCount + 1) }, enabled = settings.effectiveDockIconCount < 12) { Text("＋") }
                        }
                        Text("1〜12個。枠を超えた登録アイコンは保持され、数を増やすと再表示されます。横・縦にスクロールできます。", color = Color(0xFF9AA0A6), fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("Fold展開時のDock位置", color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExpandedDockPosition.entries.forEach { position ->
                                OutlinedButton(onClick = { onSetExpandedDockPosition(position) }, enabled = settings.expandedDockPosition != position) { Text(position.displayName) }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        Text(
                            text = "標準/見開き片側グリッド: ${settings.compactGridColumns}列 × ${settings.compactGridRows}行",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onUpdateCompactGrid(settings.compactGridColumns - 1, settings.compactGridRows)
                                }
                            ) { Text("列 -") }
                            OutlinedButton(
                                onClick = {
                                    onUpdateCompactGrid(settings.compactGridColumns + 1, settings.compactGridRows)
                                }
                            ) { Text("列 +") }
                            OutlinedButton(
                                onClick = {
                                    onUpdateCompactGrid(settings.compactGridColumns, settings.compactGridRows - 1)
                                }
                            ) { Text("行 -") }
                            OutlinedButton(
                                onClick = {
                                    onUpdateCompactGrid(settings.compactGridColumns, settings.compactGridRows + 1)
                                }
                            ) { Text("行 +") }
                        }

                        if (settings.expandedPageLayoutMode == ExpandedPageLayoutMode.SINGLE_FULL) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Expanded全画面時グリッド: ${settings.expandedGridColumns}列 × ${settings.expandedGridRows}行",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onUpdateExpandedGrid(settings.expandedGridColumns - 1, settings.expandedGridRows)
                                    }
                                ) { Text("列 -") }
                                OutlinedButton(
                                    onClick = {
                                        onUpdateExpandedGrid(settings.expandedGridColumns + 1, settings.expandedGridRows)
                                    }
                                ) { Text("列 +") }
                                OutlinedButton(
                                    onClick = {
                                        onUpdateExpandedGrid(settings.expandedGridColumns, settings.expandedGridRows - 1)
                                    }
                                ) { Text("行 -") }
                                OutlinedButton(
                                    onClick = {
                                        onUpdateExpandedGrid(settings.expandedGridColumns, settings.expandedGridRows + 1)
                                    }
                                ) { Text("行 +") }
                            }
                        }
                    }

                    // 3. All Apps / Tiny Icons 設定 (仕様 10.2)
                    SettingsSectionCard(title = "All Apps / Tiny Icons 設定") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("アプリ名ラベルを表示", color = Color.White)
                            Switch(
                                checked = settings.tinyIconsShowLabels,
                                onCheckedChange = { show ->
                                    onUpdateTinyIcons(
                                        settings.tinyIconsColumnsCompact,
                                        settings.tinyIconsColumnsExpanded,
                                        settings.tinyIconsSizeDp,
                                        show
                                    )
                                }
                            )
                        }

                        Text(
                            text = "列数 (片側/閉: ${settings.tinyIconsColumnsCompact}列 / 全画面開: ${settings.tinyIconsColumnsExpanded}列) ・ サイズ: ${settings.tinyIconsSizeDp}dp",
                            color = Color(0xFFBDC1C6),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onUpdateTinyIcons(
                                        settings.tinyIconsColumnsCompact - 1,
                                        settings.tinyIconsColumnsExpanded - 1,
                                        settings.tinyIconsSizeDp,
                                        settings.tinyIconsShowLabels
                                    )
                                }
                            ) { Text("列数 -") }
                            OutlinedButton(
                                onClick = {
                                    onUpdateTinyIcons(
                                        settings.tinyIconsColumnsCompact + 1,
                                        settings.tinyIconsColumnsExpanded + 1,
                                        settings.tinyIconsSizeDp,
                                        settings.tinyIconsShowLabels
                                    )
                                }
                            ) { Text("列数 +") }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onUpdateTinyIcons(
                                        settings.tinyIconsColumnsCompact,
                                        settings.tinyIconsColumnsExpanded,
                                        settings.tinyIconsSizeDp - 4,
                                        settings.tinyIconsShowLabels
                                    )
                                }
                            ) { Text("サイズ -") }
                            OutlinedButton(
                                onClick = {
                                    onUpdateTinyIcons(
                                        settings.tinyIconsColumnsCompact,
                                        settings.tinyIconsColumnsExpanded,
                                        settings.tinyIconsSizeDp + 4,
                                        settings.tinyIconsShowLabels
                                    )
                                }
                            ) { Text("サイズ +") }
                        }
                    }

                    // 4. ジェスチャー & Accessibility 通知シェード設定 (仕様 7.2)
                    SettingsSectionCard(title = "ジェスチャー") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "下スワイプで通知を開く",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isAccessibilityEnabled) {
                                        "✓ Accessibility権限: 有効"
                                    } else {
                                        "※ 通知シェードを直接開くにはAccessibility権限設定が必要です"
                                    },
                                    fontSize = 12.sp,
                                    color = if (isAccessibilityEnabled) Color(0xFF81C995) else Color(0xFFFFB74D)
                                )
                            }
                            Switch(
                                checked = settings.swipeDownNotificationEnabled,
                                onCheckedChange = onToggleSwipeDownNotification
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        FilledTonalButton(
                            onClick = onOpenAccessibilitySettings,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Accessibility権限設定を開く")
                        }
                    }

                    // 5. Independent feed visibility toggles
                    SettingsSectionCard(title = "ページ・フィード表示") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text("All Apps", color = Color.White, modifier = Modifier.weight(1f))
                            Switch(checked = settings.allAppsPageEnabled, onCheckedChange = onSetAllAppsPageEnabled)
                        }
                        Text("All AppsをOFFにしても、上スワイプのアプリ検索は使えます。", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        listOf("Google Discover" to true, "独自フィード" to false).forEach { (label, isGoogle) ->
                            val checked = if (isGoogle) settings.discoverMode.usesGoogleOverlay else settings.discoverMode.showsCustomFeed
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Text(label, color = Color.White, modifier = Modifier.weight(1f))
                                Switch(checked = checked, onCheckedChange = { enabled ->
                                    onSelectDiscoverMode(DiscoverMode.fromVisibility(
                                        google = if (isGoogle) enabled else settings.discoverMode.usesGoogleOverlay,
                                        feed = if (isGoogle) settings.discoverMode.showsCustomFeed else enabled
                                    ))
                                })
                            }
                        }
                        Text("Google DiscoverにはChime Discover Companionが必要です。", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("左へ移動する順序: HOME" +
                            (if (settings.allAppsPageEnabled) " → All Apps" else "") +
                            (if (settings.discoverMode.showsCustomFeed) " → 独自フィード" else "") +
                            (if (settings.discoverMode.usesGoogleOverlay) " → Google Discover" else ""),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { showFeedSettings = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("フィード設定", modifier = Modifier.weight(1f))
                            Text("${settings.enabledFeedCategories.size} / ${FeedCategory.entries.size}")
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }

                    // 6. Backup & Restore (仕様 22, 23)
                    SettingsSectionCard(title = "Backup & Restore (Novaバックアップ対応)") {
                        Text(
                            text = "JSON形式での保存・復元のほか、Nova Launcherのバックアップファイル (.novabackup / .db / .zip) やCSV・XMLなどあらゆる拡張子のファイルを直接インポートできます。",
                            fontSize = 12.sp,
                            color = Color(0xFFBDC1C6)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = snapshotNameInput,
                                onValueChange = { snapshotNameInput = it },
                                placeholder = { Text("バックアップ名 (省略時は日時)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    onSaveSnapshot(snapshotNameInput.takeIf { it.isNotBlank() })
                                    snapshotNameInput = ""
                                }
                            ) {
                                Icon(Icons.Default.Backup, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("保存")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    exportLauncher.launch("chime_launcher_backup.json")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("エクスポート")
                            }

                            OutlinedButton(
                                onClick = {
                                    // .novabackup 等の独自拡張子もすべて選択できるよう "*/*" を指定
                                    importLauncher.launch(arrayOf("*/*"))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("インポート (全形式)")
                            }
                        }

                        TextButton(
                            onClick = onShowJsonPreview,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("現在のバックアップJSONを確認 / 直接編集復元")
                        }

                        FilledTonalButton(
                            onClick = onAutoBindMissingApps,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("未インストール枠を端末内アプリ(Kindle・標準等)と一括紐付け")
                        }
                        Text(
                            text = "※ Galaxy版パッケージ (com.amazon.kindleForSamsung 等) やSamsung固有アプリ枠を、Pixel内のKindleや標準アプリへ自動変換して紐付けます。",
                            color = Color(0xFF9AA0A6),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Text(
                            text = "バックアップ一覧 (${snapshots.size}件)",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )

                        if (snapshots.isEmpty()) {
                            Text(
                                text = "保存されたバックアップはまだありません。",
                                color = Color(0xFF9AA0A6),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                snapshots.forEach { snap ->
                                    Surface(
                                        color = Color(0xFF232732),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = snap.name,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    text = "${snap.createdAt} • ${snap.pageCount}ページ / ${snap.itemCount}アイテム",
                                                    color = Color(0xFF9AA0A6),
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Row {
                                                FilledTonalButton(
                                                    onClick = { onRestoreSnapshot(snap.id) }
                                                ) {
                                                    Icon(Icons.Default.Restore, contentDescription = null)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("復元")
                                                }
                                                IconButton(
                                                    onClick = { onDeleteSnapshot(snap.id) }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "削除",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 7. 標準ホームアプリ & システム連携ツール
                    SettingsSectionCard(title = "システム設定 & ツール") {
                        Button(
                            onClick = onOpenDefaultHomeSettings,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("デフォルトのホームアプリを選択")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        FilledTonalButton(
                            onClick = onOpenUsageAccessSettings,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (hasUsageAccessPermission) {
                                    "✓ 使用状況へのアクセス (検索 Recently/Frequently 連携): 有効"
                                } else {
                                    "使用状況へのアクセスを許可 (検索の最近・高頻度精度を向上)"
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        FilledTonalButton(
                            onClick = onAutoBindMissingApps,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("未インストール枠を端末内アプリと一括自動紐付け")
                        }
                    }

                    // 8. バージョン・ビルド情報 & 自動アップデート（最下部）
                    SettingsSectionCard(title = "バージョン・ビルド情報 & アップデート") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("アプリバージョン", color = Color(0xFFBDC1C6), fontSize = 13.sp)
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME} (Code: ${BuildConfig.VERSION_CODE})",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("ビルド日時", color = Color(0xFFBDC1C6), fontSize = 13.sp)
                                Text(
                                    text = BuildConfig.BUILD_TIMESTAMP,
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = Color(0x33FFFFFF)
                            )

                            AppUpdateControlSection(
                                updateState = updateState,
                                onCheckForUpdate = checkKeepingPosition,
                                onDownloadAndInstallUpdate = onDownloadAndInstallUpdate,
                                onInstallDownloadedApk = onInstallDownloadedApk,
                                onOpenUnknownSourcesSettings = onOpenUnknownSourcesSettings,
                                onOpenGitHubReleases = onOpenGitHubReleases,
                                checkButtonModifier = Modifier.onGloballyPositioned {
                                    checkButtonY = it.boundsInRoot().top.roundToInt()
                                },
                                showCheckButton = true
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = Color(0x33FFFFFF)
                            )
                            Text(
                                text = "最新更新 (v${BuildConfig.VERSION_NAME}): ${BuildConfig.UPDATE_SUMMARY}",
                                color = Color(0xFF9AA0A6),
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ChimeSettingsPreview(
    settings: LauncherSettings,
    event: ChimeEvent?,
    onFinished: () -> Unit
) {
    Text("動きのプレビュー", color = Color(0xFF9AA0A6), fontSize = 11.sp)
    PageIndicatorBar(
        pages = listOf(LauncherPage.FIXED_DISCOVER, LauncherPage.FIXED_ALL_APPS, LauncherPage.FIXED_HOME, LauncherPage.FIXED_SETTINGS),
        visiblePageIndices = setOf(2),
        indicatorStyle = settings.indicatorStyle,
        timeSegment = TimeChimeProvider.resolveTimeSegment(java.time.LocalTime.now().hour),
        timeChimeEnabled = settings.timeChimeEnabled,
        activeChimeEvent = event,
        onChimeAnimationFinished = onFinished,
        isLayoutLocked = true,
        onSelectPage = {},
        onLongPressIndicator = {},
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AppUpdateControlSection(
    updateState: AppUpdateState,
    onCheckForUpdate: () -> Unit,
    onDownloadAndInstallUpdate: (ReleaseUpdateInfo) -> Unit,
    onInstallDownloadedApk: (String) -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
    onOpenGitHubReleases: (String) -> Unit,
    showCheckButton: Boolean,
    checkButtonModifier: Modifier = Modifier
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (updateState) {
            is AppUpdateState.Idle -> {
                Text(
                    text = "GitHub Releases から最新バージョンを確認し、アプリ内で直接アップデートできます。",
                    color = Color(0xFFBDC1C6),
                    fontSize = 12.sp
                )
            }

            is AppUpdateState.Checking -> {
                Text(
                    text = "GitHub Releases に最新バージョンを確認しています...",
                    color = Color(0xFF9AD4EE),
                    fontSize = 12.sp
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            is AppUpdateState.UpToDate -> {
                Text(
                    text = "✅ 最新バージョンを使用中です (v${updateState.currentVersion} • 確認: ${updateState.checkedAtText})",
                    color = Color(0xFF81C995),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
                val latestRelease = updateState.latestRelease
                if (latestRelease != null && showCheckButton &&
                    AppUpdateParser.canInstallRelease(updateState.currentVersion, latestRelease) &&
                    AppUpdateParser.compareVersions(latestRelease.versionName, updateState.currentVersion) == 0) {
                    OutlinedButton(
                        onClick = { onDownloadAndInstallUpdate(latestRelease) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${latestRelease.tagName} の本体${if (latestRelease.companionDownloadUrl != null) "・Companion" else ""}を再インストール")
                    }
                }
            }

            is AppUpdateState.InstalledAhead -> {
                Text(
                    text = "インストール済みの v${updateState.currentVersion} は、公開版 ${updateState.latestRelease.tagName} より新しいバージョンです。",
                    color = Color(0xFF81C995), fontWeight = FontWeight.Medium, fontSize = 13.sp
                )
                Text(
                    text = "古い公開版へのインストールは行いません。（確認: ${updateState.checkedAtText}）",
                    color = Color(0xFFBDC1C6), fontSize = 12.sp
                )
            }

            is AppUpdateState.ReleaseUnavailable -> {
                Text(
                    text = "公開版 ${updateState.latestRelease.tagName} のAPKは準備中です（現在: v${updateState.currentVersion}）。時間をおいて再確認してください。",
                    color = Color(0xFFFDD663), fontSize = 13.sp
                )
            }

            is AppUpdateState.UpdateAvailable -> {
                val release = updateState.latestRelease
                val sizeMb = if (release.apkSizeBytes + release.companionSizeBytes > 0L) {
                    String.format("%.1f MB", (release.apkSizeBytes + release.companionSizeBytes) / (1024.0 * 1024.0))
                } else {
                    "APK"
                }
                Text(
                    text = "新しいバージョン ${release.tagName} が公開されています（現在: v${updateState.currentVersion}）",
                    color = Color(0xFF7FD7FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                if (release.releaseNotes.isNotBlank()) {
                    Surface(
                        color = Color(0x55000000),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = release.releaseNotes.take(360),
                            color = Color(0xFFDADCE0),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                Button(
                    onClick = { onDownloadAndInstallUpdate(release) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SystemUpdateAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${release.tagName} の本体${if (release.companionDownloadUrl != null) "・Companion" else ""}を更新 ($sizeMb)")
                }
            }

            is AppUpdateState.Downloading -> {
                val release = updateState.latestRelease
                val progressText = if (updateState.progressPercent >= 0) {
                    "${updateState.progressPercent}%"
                } else {
                    "${updateState.downloadedBytes / 1024} KB"
                }
                Text(
                    text = "${release.tagName} のAPK${if (release.companionDownloadUrl != null) "（本体・Companion）" else ""}をダウンロード中... ($progressText)",
                    color = Color(0xFF9AD4EE),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
                if (updateState.progressPercent in 0..100) {
                    LinearProgressIndicator(
                        progress = { updateState.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            is AppUpdateState.ReadyToInstall -> {
                val release = updateState.latestRelease
                Text(
                    text = "✅ ${release.tagName} のダウンロードが完了しました。",
                    color = Color(0xFF81C995),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                if (updateState.requiresInstallPermission) {
                    Text(
                        text = "※ 初回のみ「不明なアプリのインストール」で Chime Launcher を許可してから、下の「インストールを実行」を押してください。",
                        color = Color(0xFFFDD663),
                        fontSize = 12.sp
                    )
                    OutlinedButton(
                        onClick = onOpenUnknownSourcesSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("1. 不明なアプリのインストール許可を開く")
                    }
                }
                if (updateState.companionFilePath != null) {
                    Text(
                        text = "Companionを先にインストールし、設定画面に戻って本体を更新してください。Androidの確認はそれぞれ必要です。",
                        color = Color(0xFFBDC1C6),
                        fontSize = 12.sp
                    )
                    OutlinedButton(
                        onClick = { onInstallDownloadedApk(updateState.companionFilePath) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("1. Companionをインストール")
                    }
                }
                Button(
                    onClick = { onInstallDownloadedApk(updateState.apkFilePath) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SystemUpdateAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (updateState.companionFilePath != null) "2. 本体をインストール" else "本体をインストール")
                }
            }

            is AppUpdateState.Error -> {
                Text(
                    text = "⚠️ ${updateState.message}",
                    color = Color(0xFFF28B82),
                    fontSize = 12.sp
                )
            }
        }

        if (showCheckButton) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().then(checkButtonModifier)
            ) {
                FilledTonalButton(
                    onClick = onCheckForUpdate,
                    enabled = updateState !is AppUpdateState.Checking &&
                        updateState !is AppUpdateState.Downloading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("アップデートを確認", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        onOpenGitHubReleases(AppUpdateState.GITHUB_RELEASES_PAGE_URL)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("GitHub Releases", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xDD1A1D24)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}

/**
 * JSON文字列を直接確認・コピー・貼り付け復元できるダイアログ (仕様 22)
 */
@Composable
fun JsonBackupPreviewDialog(
    initialJson: String,
    onRestoreFromJsonText: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var jsonText by remember(initialJson) { mutableStateOf(initialJson) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("バックアップ JSON (Schema v1)", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "現在のレイアウトJSONの確認、またはバックアップJSONを貼り付けて直接復元できます。",
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onRestoreFromJsonText(jsonText)
                    onDismiss()
                }
            ) {
                Text("このJSONから復元")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )
}
