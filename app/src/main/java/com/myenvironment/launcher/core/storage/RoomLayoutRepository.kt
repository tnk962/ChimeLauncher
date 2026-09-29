package com.myenvironment.launcher.core.storage

import com.myenvironment.launcher.core.launcher.AppDiscoveryRepository
import com.myenvironment.launcher.core.model.DockItem
import com.myenvironment.launcher.core.model.GridPosition
import com.myenvironment.launcher.core.model.ItemType
import com.myenvironment.launcher.core.model.LauncherAction
import com.myenvironment.launcher.core.model.LauncherPage
import com.myenvironment.launcher.core.model.LayoutItem
import com.myenvironment.launcher.core.storage.db.DockItemEntity
import com.myenvironment.launcher.core.storage.db.LauncherDao
import com.myenvironment.launcher.core.storage.db.LayoutItemEntity
import com.myenvironment.launcher.core.storage.db.UserPageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Room Database を用いた LayoutRepository の具象実装 (仕様 11, 16, 19, 20, 21)
 */
class RoomLayoutRepository(
    private val dao: LauncherDao,
    private val settingsRepository: DataStoreSettingsRepository,
    private val appDiscoveryRepository: AppDiscoveryRepository
) : LayoutRepository {

    override val userPages: Flow<List<LauncherPage>> = dao.observeUserPages().map { entities ->
        entities.map { it.toDomain() }
    }

    override val layoutItems: Flow<List<LayoutItem>> = dao.observeLayoutItems().map { entities ->
        entities.map { it.toDomain() }
    }

    override val dockItems: Flow<List<DockItem>> = dao.observeDockItems().map { entities ->
        entities.map { it.toDomain() }
    }

    override suspend fun ensureInitialized() {
        val alreadySeeded = settingsRepository.isDefaultLayoutSeeded.first()
        if (alreadySeeded) return

        val existingItems = dao.getLayoutItemsSnapshot()
        val existingDock = dao.getDockItemsSnapshot()
        if (existingItems.isNotEmpty() || existingDock.isNotEmpty()) {
            settingsRepository.markDefaultLayoutSeeded()
            return
        }

        // 初回起動時：端末内の主要アプリまたは仕様書記載の定番アイテムで初期レイアウトを構築
        appDiscoveryRepository.refreshApps()
        val installed = appDiscoveryRepository.installedApps.value

        val defaultHomeItems = mutableListOf<LayoutItem>()
        // 端末に入っている先頭数個のアプリをHOME上段に配置
        installed.take(4).forEachIndexed { index, app ->
            defaultHomeItems.add(
                LayoutItem(
                    id = UUID.randomUUID().toString(),
                    pageId = LauncherPage.PAGE_ID_HOME,
                    type = ItemType.APP,
                    packageName = app.packageName,
                    activityName = app.activityName,
                    label = app.label,
                    compact = GridPosition(x = index, y = 0),
                    expanded = GridPosition(x = index, y = 0)
                )
            )
        }

        // Launcher独自Action（Hatena Discover / 通知履歴）もすぐ試せるよう初期配置
        defaultHomeItems.add(
            LayoutItem(
                id = UUID.randomUUID().toString(),
                pageId = LauncherPage.PAGE_ID_HOME,
                type = ItemType.ACTION,
                packageName = LauncherAction.HATENA_FEED.companionPackageName.orEmpty(),
                targetUri = LauncherAction.HATENA_FEED.actionId,
                label = LauncherAction.HATENA_FEED.title,
                compact = GridPosition(x = 0, y = 1),
                expanded = GridPosition(x = 0, y = 1)
            )
        )
        defaultHomeItems.add(
            LayoutItem(
                id = UUID.randomUUID().toString(),
                pageId = LauncherPage.PAGE_ID_HOME,
                type = ItemType.ACTION,
                packageName = LauncherAction.MY_NOTIFICATIONS.companionPackageName.orEmpty(),
                targetUri = LauncherAction.MY_NOTIFICATIONS.actionId,
                label = LauncherAction.MY_NOTIFICATIONS.title,
                compact = GridPosition(x = 1, y = 1),
                expanded = GridPosition(x = 1, y = 1)
            )
        )

        // Adaptive Dock の初期配置 (最大5個: 仕様19節の例に沿った構成)
        val defaultDockItems = mutableListOf<DockItem>()
        val dockApps = installed.take(3)
        dockApps.forEachIndexed { idx, app ->
            defaultDockItems.add(
                DockItem(
                    id = UUID.randomUUID().toString(),
                    positionIndex = idx,
                    type = ItemType.APP,
                    packageName = app.packageName,
                    activityName = app.activityName,
                    label = app.label
                )
            )
        }
        defaultDockItems.add(
            DockItem(
                id = UUID.randomUUID().toString(),
                positionIndex = defaultDockItems.size,
                type = ItemType.ACTION,
                packageName = "",
                targetUri = LauncherAction.SEARCH.actionId,
                label = "検索"
            )
        )
        defaultDockItems.add(
            DockItem(
                id = UUID.randomUUID().toString(),
                positionIndex = defaultDockItems.size,
                type = ItemType.ACTION,
                packageName = "",
                targetUri = LauncherAction.SETTINGS.actionId,
                label = "設定"
            )
        )

        dao.upsertLayoutItems(defaultHomeItems.map { LayoutItemEntity.fromDomain(it) })
        dao.upsertDockItems(defaultDockItems.map { DockItemEntity.fromDomain(it) })
        settingsRepository.markDefaultLayoutSeeded()
    }

    override suspend fun addUserPage(name: String): LauncherPage {
        val currentPages = dao.getUserPagesSnapshot()
        val nextSortOrder = (currentPages.maxOfOrNull { it.sortOrder } ?: 0) + 1
        val trimmedName = name.trim().ifEmpty { "Page $nextSortOrder" }
        val newPage = LauncherPage(
            id = "page_${UUID.randomUUID()}",
            name = trimmedName,
            sortOrder = nextSortOrder,
            isFixed = false
        )
        dao.upsertUserPage(UserPageEntity.fromDomain(newPage))
        return newPage
    }

    override suspend fun renameUserPage(pageId: String, newName: String) {
        if (isSystemFixedPage(pageId)) return
        val current = dao.getUserPagesSnapshot().find { it.id == pageId } ?: return
        val updated = current.copy(name = newName.trim().ifEmpty { current.name })
        dao.upsertUserPage(updated)
    }

    override suspend fun deleteUserPage(pageId: String) {
        // Discover / All Apps / HOME は削除不可 (仕様 16)
        if (isSystemFixedPage(pageId)) return
        dao.deletePageAndItsItems(pageId)
    }

    override suspend fun reorderUserPages(orderedPageIds: List<String>) {
        val currentMap = dao.getUserPagesSnapshot().associateBy { it.id }
        val updatedEntities = orderedPageIds.mapIndexedNotNull { index, pageId ->
            currentMap[pageId]?.copy(sortOrder = index + 1)
        }
        dao.upsertUserPages(updatedEntities)
    }

    override suspend fun upsertLayoutItem(item: LayoutItem) {
        dao.upsertLayoutItem(LayoutItemEntity.fromDomain(item))
    }

    override suspend fun updateItemPosition(
        itemId: String,
        newPosition: GridPosition,
        isExpandedMode: Boolean
    ) {
        val entity = dao.getLayoutItemById(itemId) ?: return
        val updated = if (isExpandedMode) {
            entity.copy(
                expandedX = newPosition.x,
                expandedY = newPosition.y
            )
        } else {
            entity.copy(
                compactX = newPosition.x,
                compactY = newPosition.y
            )
        }
        dao.upsertLayoutItem(updated)
    }

    override suspend fun deleteLayoutItem(itemId: String) {
        dao.deleteLayoutItemById(itemId)
    }

    override suspend fun upsertDockItem(item: DockItem) {
        dao.upsertDockItem(DockItemEntity.fromDomain(item))
    }

    override suspend fun deleteDockItem(itemId: String) {
        dao.deleteDockItemById(itemId)
        // インデックスを詰める
        val remaining = dao.getDockItemsSnapshot()
            .sortedBy { it.positionIndex }
            .mapIndexed { idx, entity -> entity.copy(positionIndex = idx) }
        dao.replaceAllDock(remaining)
    }

    override suspend fun replaceDockItems(items: List<DockItem>) {
        val entities = items.mapIndexed { index, item ->
            DockItemEntity.fromDomain(item.copy(positionIndex = index))
        }
        dao.replaceAllDock(entities)
    }

    override suspend fun replaceAllLayoutData(
        userPages: List<LauncherPage>,
        items: List<LayoutItem>,
        dockItems: List<DockItem>
    ) {
        val pageEntities = userPages
            .filterNot { isSystemFixedPage(it.id) }
            .mapIndexed { idx, page ->
                UserPageEntity(
                    id = page.id,
                    name = page.name,
                    sortOrder = if (page.sortOrder > 0) page.sortOrder else idx + 1
                )
            }
        val itemEntities = items.map { LayoutItemEntity.fromDomain(it) }
        val dockEntities = dockItems.mapIndexed { idx, dock ->
            DockItemEntity.fromDomain(dock.copy(positionIndex = idx))
        }
        dao.replaceEntireLayout(pageEntities, itemEntities, dockEntities)
        settingsRepository.markDefaultLayoutSeeded()
    }

    private fun isSystemFixedPage(pageId: String): Boolean {
        return pageId == LauncherPage.PAGE_ID_DISCOVER ||
            pageId == LauncherPage.PAGE_ID_ALL_APPS ||
            pageId == LauncherPage.PAGE_ID_HOME ||
            pageId == LauncherPage.PAGE_ID_SETTINGS
    }
}
