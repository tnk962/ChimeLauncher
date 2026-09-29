package com.myenvironment.launcher.core.launcher

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import com.myenvironment.launcher.core.model.AppInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * インストール済みアプリの取得・パッケージ変更監視・アイコン取得の境界インターフェース (仕様 8.2, 10.2, 26, 40)
 */
interface AppDiscoveryRepository {
    /**
     * 現在インストールされている起動可能アプリのリスト（かな/アルファベット順ソート済み）
     */
    val installedApps: StateFlow<List<AppInfo>>

    /**
     * 現在インストールされているパッケージ名のセット（Placeholder判定用）
     */
    val installedPackages: StateFlow<Set<String>>

    /**
     * アプリ一覧を手動で再スキャンする
     */
    suspend fun refreshApps()

    /**
     * パッケージ名（および任意でActivity名）からアイコンDrawableを取得する
     */
    fun getAppIcon(packageName: String, activityName: String = ""): Drawable?

    /**
     * メモリキャッシュ済みの ImageBitmap があれば同期的に即座に返す（スクロール時のカクつき防止用）
     */
    fun getCachedIconBitmap(packageName: String, activityName: String = ""): ImageBitmap?

    /**
     * ImageBitmap を取得または生成してキャッシュに格納し返す（IOスレッド呼び出し用）
     */
    fun loadOrCreateIconBitmap(packageName: String, activityName: String = ""): ImageBitmap?

    /**
     * 指定したパッケージが現在端末にインストールされているか判定する
     */
    fun isPackageInstalled(packageName: String): Boolean
}
