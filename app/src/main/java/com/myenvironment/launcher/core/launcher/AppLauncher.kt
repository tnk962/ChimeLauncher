package com.myenvironment.launcher.core.launcher

import com.myenvironment.launcher.core.model.AppInfo
import com.myenvironment.launcher.core.model.LauncherAction

/**
 * アプリ・Shortcut・Launcher Action・Web検索・Play Store遷移を実行する境界インターフェース (仕様 9, 12, 25)
 */
interface AppLauncher {
    /**
     * 通常アプリを起動する
     * @return 起動成功時は true、未インストール等で起動できない場合は false
     */
    fun launchApp(packageName: String, activityName: String = ""): Boolean

    /**
     * AppInfoから直接起動する
     */
    fun launchApp(appInfo: AppInfo): Boolean = launchApp(appInfo.packageName, appInfo.activityName)

    /**
     * Deep Link または Intent URI のショートカットを起動する
     */
    fun launchShortcutUri(uriString: String): Boolean

    /**
     * Googleアプリまたはデフォルトブラウザで指定クエリをWeb検索する (仕様 9)
     */
    fun launchGoogleSearch(query: String): Boolean

    /**
     * Play Store のアプリ詳細ページを開く。Play Storeが無い場合はブラウザへFallbackする (仕様 25)
     */
    fun openPlayStore(packageName: String)

    /**
     * Play Store アプリ内で指定キーワードのアプリ検索を開く (market://search?q=...&c=apps)
     */
    fun searchPlayStore(query: String)

    /**
     * ブラウザ (Google 検索) で指定キーワードの Google Play ストアページをしつこく検索する
     */
    fun searchPlayStoreOnWeb(query: String)

    /**
     * システムのAccessibility設定画面を開く (仕様 7.2)
     */
    fun openAccessibilitySettings()

    /**
     * システムのデフォルトホームアプリ選択画面を開く (仕様 4, 45)
     */
    fun openDefaultHomeSettings()

    /**
     * システムのアプリ情報画面を開く
     */
    fun openAppDetailsSettings(packageName: String)

    /**
     * 周辺アプリ（Hatena Discover / My Notifications等）を起動する
     */
    fun launchCompanionAppOrFallback(action: LauncherAction): Boolean
}
