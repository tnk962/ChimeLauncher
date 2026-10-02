package com.myenvironment.launcher.ui.settings

import com.myenvironment.launcher.core.model.ExpandedDockPosition
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myenvironment.launcher.BuildConfig
import com.myenvironment.launcher.accessibility.NotificationShadeService
import com.myenvironment.launcher.core.model.BackupSnapshotSummary
import com.myenvironment.launcher.core.model.DiscoverMode
import com.myenvironment.launcher.core.model.ExpandedPageLayoutMode
import com.myenvironment.launcher.core.model.IndicatorStyle
import com.myenvironment.launcher.core.model.LauncherSettings
import com.myenvironment.launcher.core.model.ReturnChimeInterval
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
    onToggleAllAppsLeftOnlyInExpanded: (Boolean) -> Unit,
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
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val isAccessibilityEnabled = remember(settings) {
        NotificationShadeService.isServiceEnabled(context)
    }

    var snapshotNameInput by remember { mutableStateOf("") }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // トップバー
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isEmbeddedPage) {
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "戻る",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Column {
                        Text(
                            text = "Chime Launcher 設定",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE} • ${BuildConfig.BUILD_TIMESTAMP})",
                            color = Color(0xFF9AA0A6),
                            fontSize = 11.sp
                        )
                    }
                }

                if (isEmbeddedPage) {
                    FilledTonalButton(onClick = onClose) {
                        Icon(Icons.Default.Home, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("HOMEへ", fontSize = 12.sp)
                    }
                }
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
                        onCheckedChange = onToggleFirstChime
                    )
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
                        onCheckedChange = onToggleReturnChime
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
                                    onClick = { onSelectReturnChimeInterval(interval) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(interval.displayName, fontSize = 11.sp, maxLines = 1)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onSelectReturnChimeInterval(interval) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(interval.displayName, fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
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
                                text = mode.description,
                                color = Color(0xFF9AA0A6),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                if (settings.expandedPageLayoutMode == ExpandedPageLayoutMode.SINGLE_FULL) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "開いた時のAll Appsを左側半分だけに表示",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "OFFの場合は全画面幅で表示、ONの場合は左側半分に寄せて表示します",
                                color = Color(0xFF9AA0A6),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = settings.allAppsLeftOnlyInExpandedSingle,
                            onCheckedChange = onToggleAllAppsLeftOnlyInExpanded
                        )
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            // 5. Discover Mode 設定 (仕様 29)
            SettingsSectionCard(title = "Discover Mode") {
                DiscoverMode.entries.forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectDiscoverMode(mode) }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = settings.discoverMode == mode,
                            onClick = { onSelectDiscoverMode(mode) }
                        )
                        Column(modifier = Modifier.padding(start = 6.dp)) {
                            Text(text = mode.displayName, color = Color.White, fontSize = 14.sp)
                            Text(text = mode.description, color = Color(0xFF9AA0A6), fontSize = 11.sp)
                        }
                    }
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

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onAddDemoMissingAppPlaceholder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("未インストールPlaceholder (Spotify例) をHOMEに追加")
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
                        onCheckForUpdate = onCheckForUpdate,
                        onDownloadAndInstallUpdate = onDownloadAndInstallUpdate,
                        onInstallDownloadedApk = onInstallDownloadedApk,
                        onOpenUnknownSourcesSettings = onOpenUnknownSourcesSettings,
                        onOpenGitHubReleases = onOpenGitHubReleases,
                        showCheckButton = true
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = Color(0x33FFFFFF)
                    )
                    Text(
                        text = "最新更新 (v1.1.0 - Chime Launcher): GitHub Releases からの新バージョン自動検知と、設定画面から1タップで最新APKをダウンロード＆上書きインストールできるアプリ内アップデート機能を実装。GitHub Actions 自動ビルドおよび署名キー統一にも対応",
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

@Composable
private fun AppUpdateControlSection(
    updateState: AppUpdateState,
    onCheckForUpdate: () -> Unit,
    onDownloadAndInstallUpdate: (ReleaseUpdateInfo) -> Unit,
    onInstallDownloadedApk: (String) -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
    onOpenGitHubReleases: (String) -> Unit,
    showCheckButton: Boolean
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
                if (latestRelease?.apkDownloadUrl != null && showCheckButton) {
                    OutlinedButton(
                        onClick = { onDownloadAndInstallUpdate(latestRelease) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("最新リリース (${latestRelease.tagName}) のAPKを再インストール")
                    }
                }
            }

            is AppUpdateState.UpdateAvailable -> {
                val release = updateState.latestRelease
                val sizeMb = if (release.apkSizeBytes > 0L) {
                    String.format("%.1f MB", release.apkSizeBytes / (1024.0 * 1024.0))
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
                    Text("${release.tagName} をダウンロードしてアップデート ($sizeMb)")
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
                    text = "${release.tagName} のAPKをダウンロード中... ($progressText)",
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
                Button(
                    onClick = { onInstallDownloadedApk(updateState.apkFilePath) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SystemUpdateAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("パッケージインストーラーでアップデートを実行")
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
                modifier = Modifier.fillMaxWidth()
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
