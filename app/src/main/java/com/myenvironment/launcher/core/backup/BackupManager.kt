package com.myenvironment.launcher.core.backup

import com.myenvironment.launcher.core.model.BackupPayload
import com.myenvironment.launcher.core.model.BackupSnapshotSummary
import kotlinx.coroutines.flow.Flow

/**
 * レイアウトおよび設定のバックアップ・復元を担う境界インターフェース (仕様 22, 23, 40)
 */
interface BackupManager {
    /** アプリ内DBに保存されたバックアップ一覧（新しい順） */
    val savedSnapshots: Flow<List<BackupSnapshotSummary>>

    /** 現在の状態から BackupPayload を生成する */
    suspend fun createCurrentPayload(): BackupPayload

    /** 現在の状態を JSON 文字列へシリアライズする */
    suspend fun exportToJsonString(): String

    /** JSON 文字列から状態を復元する */
    suspend fun restoreFromJsonString(jsonString: String): Result<BackupPayload>

    /** Nova Launcher バックアップ (.novabackup / SQLite DB) のバイト列から変換して復元する */
    suspend fun importFromNovaBackupBytes(bytes: ByteArray, fileName: String? = null): Result<BackupPayload>

    /** ファイルのバイト列 (.json または .novabackup / .db) を自動判別して復元する */
    suspend fun importFromBackupBytes(bytes: ByteArray, fileName: String? = null): Result<BackupPayload>

    /** アプリ内バックアップ一覧に現在のレイアウトを名前付きで保存する */
    suspend fun saveInternalSnapshot(customName: String? = null): BackupSnapshotSummary

    /** アプリ内バックアップIDを指定して復元する */
    suspend fun restoreInternalSnapshot(snapshotId: Long): Result<BackupPayload>

    /** アプリ内バックアップを削除する */
    suspend fun deleteInternalSnapshot(snapshotId: Long)
}
