package com.myenvironment.launcher.core.storage

import com.myenvironment.launcher.core.model.LayoutSnapshot
import com.myenvironment.launcher.core.model.DockItem
import com.myenvironment.launcher.core.model.GridPosition
import com.myenvironment.launcher.core.model.LauncherPage
import com.myenvironment.launcher.core.model.LayoutItem
import kotlinx.coroutines.flow.Flow

/**
 * ホーム画面のページ、配置アイテム、Dockアイテムを永続化する境界インターフェース (仕様 21, 40)
 */
interface LayoutRepository {
    /** ユーザー追加ページ一覧（sortOrder順） */
    val userPages: Flow<List<LauncherPage>>

    /** 全ページの配置アイテム一覧 */
    val layoutItems: Flow<List<LayoutItem>>

    /** Dockアイテム一覧（positionIndex順） */
    val dockItems: Flow<List<DockItem>>

    /** Undo用に3テーブルを一貫した状態で読み込む。 */
    suspend fun getLayoutSnapshot(): LayoutSnapshot

    /** Undo用スナップショットを設定に触れずトランザクションで復元する。 */
    suspend fun restoreLayoutSnapshot(snapshot: LayoutSnapshot)

    /** 初回起動時にデフォルトのHOMEアイテム＆Dockを初期投入する */
    suspend fun ensureInitialized()

    /** HOME右側に新しいユーザーページを追加する */
    suspend fun addUserPage(name: String): LauncherPage

    /** ユーザーページの名前を変更する（固定ページは変更不可） */
    suspend fun renameUserPage(pageId: String, newName: String)

    /** ユーザーページを削除する（Discover / All Apps / HOME は削除不可） */
    suspend fun deleteUserPage(pageId: String)

    /** ユーザーページの並び順を入れ替える */
    suspend fun reorderUserPages(orderedPageIds: List<String>)

    /** ページに新しいLayoutItemを追加または上書きする */
    suspend fun upsertLayoutItem(item: LayoutItem)

    /** アイテムの座標を更新する（CompactまたはExpanded） */
    suspend fun updateItemPosition(
        itemId: String,
        newPosition: GridPosition,
        isExpandedMode: Boolean
    )

    /** ページを跨いでアイテムの所属ページ（pageId）と座標を更新する */
    suspend fun updateItemPageAndPosition(
        itemId: String,
        targetPageId: String,
        newPosition: GridPosition,
        isExpandedMode: Boolean
    )

    /** アイテム（主にWidget）のセルサイズ（spanX × spanY）および必要に応じて調整後の座標を更新する */
    suspend fun updateItemSpan(
        itemId: String,
        spanX: Int,
        spanY: Int,
        adjustedCompactPosition: GridPosition? = null,
        adjustedExpandedPosition: GridPosition? = null
    )

    /** Widgetアイテムの appWidgetId を更新する（復元後の再バインド時など） */
    suspend fun updateWidgetId(
        itemId: String,
        appWidgetId: Int
    )

    /** アイテムをホーム画面から削除する */
    suspend fun deleteLayoutItem(itemId: String)

    /** Dockにアイテムを追加または更新する */
    suspend fun upsertDockItem(item: DockItem)

    /** Dockからアイテムを削除する */
    suspend fun deleteDockItem(itemId: String)

    /** Dockの並び順を更新する */
    suspend fun replaceDockItems(items: List<DockItem>)

    /** バックアップ復元用に全ページ・全アイテム・全Dockをトランザクションで一括置換する */
    suspend fun replaceAllLayoutData(
        userPages: List<LauncherPage>,
        items: List<LayoutItem>,
        dockItems: List<DockItem>
    )
}
