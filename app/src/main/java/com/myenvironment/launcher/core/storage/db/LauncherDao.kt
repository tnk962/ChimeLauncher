package com.myenvironment.launcher.core.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LauncherDao {

    // --- User Pages ---
    @Query("SELECT * FROM user_pages ORDER BY sortOrder ASC")
    fun observeUserPages(): Flow<List<UserPageEntity>>

    @Query("SELECT * FROM user_pages ORDER BY sortOrder ASC")
    suspend fun getUserPagesSnapshot(): List<UserPageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserPage(page: UserPageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserPages(pages: List<UserPageEntity>)

    @Query("DELETE FROM user_pages WHERE id = :pageId")
    suspend fun deleteUserPageById(pageId: String)

    @Query("DELETE FROM user_pages")
    suspend fun clearAllUserPages()

    // --- Layout Items ---
    @Query("SELECT * FROM layout_items")
    fun observeLayoutItems(): Flow<List<LayoutItemEntity>>

    @Query("SELECT * FROM layout_items")
    suspend fun getLayoutItemsSnapshot(): List<LayoutItemEntity>

    @Query("SELECT * FROM layout_items WHERE id = :itemId LIMIT 1")
    suspend fun getLayoutItemById(itemId: String): LayoutItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLayoutItem(item: LayoutItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLayoutItems(items: List<LayoutItemEntity>)

    @Query("DELETE FROM layout_items WHERE id = :itemId")
    suspend fun deleteLayoutItemById(itemId: String)

    @Query("DELETE FROM layout_items WHERE pageId = :pageId")
    suspend fun deleteLayoutItemsByPageId(pageId: String)

    @Query("DELETE FROM layout_items")
    suspend fun clearAllLayoutItems()

    // --- Dock Items ---
    @Query("SELECT * FROM dock_items ORDER BY positionIndex ASC")
    fun observeDockItems(): Flow<List<DockItemEntity>>

    @Query("SELECT * FROM dock_items ORDER BY positionIndex ASC")
    suspend fun getDockItemsSnapshot(): List<DockItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDockItem(item: DockItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDockItems(items: List<DockItemEntity>)

    @Query("DELETE FROM dock_items WHERE id = :itemId")
    suspend fun deleteDockItemById(itemId: String)

    @Query("DELETE FROM dock_items")
    suspend fun clearAllDockItems()

    // --- Backup Snapshots ---
    @Query("SELECT * FROM backup_snapshots ORDER BY id DESC")
    fun observeBackupSnapshots(): Flow<List<BackupSnapshotEntity>>

    @Query("SELECT * FROM backup_snapshots WHERE id = :snapshotId LIMIT 1")
    suspend fun getBackupSnapshotById(snapshotId: Long): BackupSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupSnapshot(snapshot: BackupSnapshotEntity): Long

    @Query("DELETE FROM backup_snapshots WHERE id = :snapshotId")
    suspend fun deleteBackupSnapshotById(snapshotId: Long)

    // --- Transactions ---
    @Transaction
    suspend fun deletePageAndItsItems(pageId: String) {
        deleteLayoutItemsByPageId(pageId)
        deleteUserPageById(pageId)
    }

    @Transaction
    suspend fun replaceAllDock(items: List<DockItemEntity>) {
        clearAllDockItems()
        upsertDockItems(items)
    }

    @Transaction
    suspend fun replaceEntireLayout(
        pages: List<UserPageEntity>,
        items: List<LayoutItemEntity>,
        dock: List<DockItemEntity>
    ) {
        clearAllUserPages()
        clearAllLayoutItems()
        clearAllDockItems()
        upsertUserPages(pages)
        upsertLayoutItems(items)
        upsertDockItems(dock)
    }
}
