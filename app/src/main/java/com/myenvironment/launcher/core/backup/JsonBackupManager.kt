package com.myenvironment.launcher.core.backup

import android.content.Context
import com.myenvironment.launcher.core.launcher.AppDiscoveryRepository
import com.myenvironment.launcher.core.model.BackupLayoutItem
import com.myenvironment.launcher.core.model.BackupPage
import com.myenvironment.launcher.core.model.BackupPayload
import com.myenvironment.launcher.core.model.BackupSnapshotSummary
import com.myenvironment.launcher.core.model.LauncherPage
import com.myenvironment.launcher.core.model.LayoutItem
import com.myenvironment.launcher.core.storage.LayoutRepository
import com.myenvironment.launcher.core.storage.SettingsRepository
import com.myenvironment.launcher.core.storage.db.BackupSnapshotEntity
import com.myenvironment.launcher.core.storage.db.LauncherDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * kotlinx.serialization と Room スナップショットを用いた BackupManager 具象実装 (仕様 22, 23, 24)
 */
class JsonBackupManager(
    private val appContext: Context,
    private val dao: LauncherDao,
    private val layoutRepository: LayoutRepository,
    private val settingsRepository: SettingsRepository,
    private val appDiscoveryRepository: AppDiscoveryRepository
) : BackupManager {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val displayFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

    override val savedSnapshots: Flow<List<BackupSnapshotSummary>> =
        dao.observeBackupSnapshots().map { list ->
            list.map { it.toSummary() }
        }

    override suspend fun createCurrentPayload(): BackupPayload {
        val nowIso = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val userPages = layoutRepository.userPages.first()
        val allItems = layoutRepository.layoutItems.first()
        val dockItems = layoutRepository.dockItems.first()
        val currentSettings = settingsRepository.settings.first()

        // HOMEページ + ユーザー追加ページをパッケージ化
        val targetPages = listOf(LauncherPage.FIXED_HOME) + userPages.sortedBy { it.sortOrder }
        val backupPages = targetPages.map { page ->
            val pageItems = allItems
                .filter { it.pageId == page.id }
                .map { item ->
                    BackupLayoutItem(
                        id = item.id,
                        type = item.type,
                        packageName = item.packageName,
                        activityName = item.activityName,
                        targetUri = item.targetUri,
                        label = item.label,
                        compact = item.compact,
                        expanded = item.expanded,
                        spanX = item.spanX,
                        spanY = item.spanY,
                        appWidgetId = item.appWidgetId,
                        folderApps = item.folderApps
                    )
                }
            BackupPage(
                id = page.id,
                name = page.name,
                sortOrder = page.sortOrder,
                items = pageItems
            )
        }

        return BackupPayload(
            schemaVersion = BackupPayload.CURRENT_SCHEMA_VERSION,
            createdAt = nowIso,
            pages = backupPages,
            dock = dockItems,
            settings = currentSettings
        )
    }

    override suspend fun exportToJsonString(): String {
        val payload = createCurrentPayload()
        return json.encodeToString(payload)
    }

    override suspend fun restoreFromJsonString(jsonString: String): Result<BackupPayload> {
        return runCatching {
            val payload = json.decodeFromString<BackupPayload>(jsonString)
            applyPayload(payload)
            payload
        }
    }

    override suspend fun importFromNovaBackupBytes(
        bytes: ByteArray,
        fileName: String?
    ): Result<BackupPayload> {
        return runCatching {
            val installedApps = appDiscoveryRepository.installedApps.value
            val currentSettings = settingsRepository.settings.first()
            val currentDock = layoutRepository.dockItems.first()

            val payload = withContext(Dispatchers.IO) {
                NovaBackupConverter.convertFromBytes(
                    context = appContext,
                    bytes = bytes,
                    installedApps = installedApps,
                    currentSettings = currentSettings,
                    fallbackDock = currentDock
                )
            }
            applyPayload(payload)
            payload
        }
    }

    override suspend fun importFromBackupBytes(
        bytes: ByteArray,
        fileName: String?
    ): Result<BackupPayload> {
        if (bytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("バックアップファイルが空です"))
        }

        // 1. Nova Launcher バックアップ (.novabackup / ZIP / SQLite DB) の場合
        if (NovaBackupConverter.isNovaBackupOrSqlite(bytes, fileName)) {
            return importFromNovaBackupBytes(bytes, fileName)
        }

        // 2. JSON 文字列としてパースを試行し、独自フォーマット等の場合は汎用コンバーターへフォールバックする
        val text = runCatching { bytes.toString(Charsets.UTF_8).trimStart('\uFEFF').trim() }.getOrDefault("")
        if (text.startsWith("{") && text.contains("\"pages\"")) {
            val jsonResult = restoreFromJsonString(text)
            if (jsonResult.isSuccess) {
                return jsonResult
            }
        }

        return importFromNovaBackupBytes(bytes, fileName)
    }

    override suspend fun saveInternalSnapshot(customName: String?): BackupSnapshotSummary {
        val payload = createCurrentPayload()
        val jsonString = json.encodeToString(payload)
        val nowDisplay = OffsetDateTime.now().format(displayFormatter)
        val snapshotName = customName?.trim().takeUnless { it.isNullOrEmpty() } ?: nowDisplay
        val totalItems = payload.pages.sumOf { it.items.size } + payload.dock.size

        val entity = BackupSnapshotEntity(
            name = snapshotName,
            createdAt = nowDisplay,
            pageCount = payload.pages.size,
            itemCount = totalItems,
            jsonPayload = jsonString
        )
        val newId = dao.insertBackupSnapshot(entity)
        return entity.copy(id = newId).toSummary()
    }

    override suspend fun restoreInternalSnapshot(snapshotId: Long): Result<BackupPayload> {
        val entity = dao.getBackupSnapshotById(snapshotId)
            ?: return Result.failure(IllegalArgumentException("指定されたバックアップが見つかりません"))
        return restoreFromJsonString(entity.jsonPayload)
    }

    override suspend fun deleteInternalSnapshot(snapshotId: Long) {
        dao.deleteBackupSnapshotById(snapshotId)
    }

    private suspend fun applyPayload(payload: BackupPayload) {
        val restoredUserPages = mutableListOf<LauncherPage>()
        val restoredLayoutItems = mutableListOf<LayoutItem>()

        payload.pages.forEachIndexed { pageIndex, backupPage ->
            val isHome = backupPage.id == LauncherPage.PAGE_ID_HOME
            if (!isHome &&
                backupPage.id != LauncherPage.PAGE_ID_DISCOVER &&
                backupPage.id != LauncherPage.PAGE_ID_ALL_APPS &&
                backupPage.id != LauncherPage.PAGE_ID_SETTINGS
            ) {
                restoredUserPages.add(
                    LauncherPage(
                        id = backupPage.id,
                        name = backupPage.name,
                        sortOrder = if (backupPage.sortOrder > 0) backupPage.sortOrder else pageIndex,
                        isFixed = false
                    )
                )
            }

            // 未インストールアプリやWidgetも自動削除せずそのままLayoutItemとして復元する (仕様 24)
            backupPage.items.forEach { bItem ->
                restoredLayoutItems.add(
                    LayoutItem(
                        id = bItem.id.ifBlank { UUID.randomUUID().toString() },
                        pageId = backupPage.id,
                        type = bItem.type,
                        packageName = bItem.packageName,
                        activityName = bItem.activityName,
                        targetUri = bItem.targetUri,
                        label = bItem.label,
                        compact = bItem.compact,
                        expanded = bItem.expanded,
                        spanX = bItem.spanX.coerceAtLeast(1),
                        spanY = bItem.spanY.coerceAtLeast(1),
                        appWidgetId = bItem.appWidgetId,
                        folderApps = bItem.folderApps
                    )
                )
            }
        }

        layoutRepository.replaceAllLayoutData(
            userPages = restoredUserPages,
            items = restoredLayoutItems,
            dockItems = payload.dock
        )
        settingsRepository.replaceSettings(payload.settings)
    }
}
