package com.myenvironment.launcher.core.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import com.myenvironment.launcher.core.model.LauncherAction

/**
 * Android Intent / LauncherApps を用いたアプリ・Shortcut・Web検索・Play Store起動の具象実装 (仕様 9, 12, 25)
 */
class AndroidAppLauncher(
    private val appContext: Context
) : AppLauncher {

    private val launcherApps = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val packageManager = appContext.packageManager

    override fun launchApp(packageName: String, activityName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            val user = Process.myUserHandle()
            val activities = launcherApps.getActivityList(packageName, user)
            val targetActivity = if (activityName.isNotBlank()) {
                activities.find { it.componentName.className == activityName } ?: activities.firstOrNull()
            } else {
                activities.firstOrNull()
            }

            if (targetActivity != null) {
                launcherApps.startMainActivity(targetActivity.componentName, user, null, null)
                true
            } else {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                } ?: return false
                appContext.startActivity(launchIntent)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun launchShortcutUri(uriString: String): Boolean {
        if (uriString.isBlank()) return false
        return try {
            val intent = if (uriString.startsWith("intent:")) {
                Intent.parseUri(uriString, Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(uriString))
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            true
        } catch (_: Exception) {
            Toast.makeText(appContext, "ショートカットを起動できませんでした", Toast.LENGTH_SHORT).show()
            false
        }
    }

    override fun launchGoogleSearch(query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return false

        // 1. Google App またはシステム Web Search Intent を試行
        try {
            val webSearchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra("query", trimmed)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(webSearchIntent)
            return true
        } catch (_: Exception) {
            // Fallback to browser URL
        }

        // 2. ブラウザで Google 検索 URL を開く (仕様 9)
        return try {
            val encoded = Uri.encode(trimmed)
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=$encoded")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(browserIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun openPlayStore(packageName: String) {
        if (packageName.isBlank()) return
        // 1. Play Store アプリで開く (market://details?id=...)
        try {
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$packageName")
            ).apply {
                setPackage("com.android.vending")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(marketIntent)
            return
        } catch (_: Exception) {
            // Play Store アプリがない場合は Browser へ Fallback (仕様 25)
        }

        // 2. 公式 Store Listing URL をブラウザで開く
        try {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(webIntent)
        } catch (_: Exception) {
            Toast.makeText(appContext, "Playストアを開けませんでした", Toast.LENGTH_SHORT).show()
        }
    }

    override fun searchPlayStore(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val encoded = Uri.encode(trimmed)

        // 1. Play Store アプリ内検索 (market://search?q=...&c=apps)
        try {
            val marketSearchIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://search?q=$encoded&c=apps")
            ).apply {
                setPackage("com.android.vending")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(marketSearchIntent)
            return
        } catch (_: Exception) {
            // Play Store アプリがない場合は Web Play Store 検索へ Fallback
        }

        // 2. ブラウザで Google Play ストア検索 URL を開く
        try {
            val webSearchIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/search?q=$encoded&c=apps")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(webSearchIntent)
        } catch (_: Exception) {
            Toast.makeText(appContext, "Playストア検索を開けませんでした", Toast.LENGTH_SHORT).show()
        }
    }

    override fun searchPlayStoreOnWeb(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val encoded = Uri.encode("$trimmed Google Play アプリ")
        try {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=$encoded")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(browserIntent)
        } catch (_: Exception) {
            Toast.makeText(appContext, "Web検索を開けませんでした", Toast.LENGTH_SHORT).show()
        }
    }

    override fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(appContext, "アクセシビリティ設定を開けませんでした", Toast.LENGTH_SHORT).show()
        }
    }

    override fun openDefaultHomeSettings() {
        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(fallback)
            } catch (_: Exception) {
                Toast.makeText(appContext, "ホームアプリ設定を開けませんでした", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun openAppDetailsSettings(packageName: String) {
        if (packageName.isBlank()) return
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(appContext, "アプリ情報を開けませんでした", Toast.LENGTH_SHORT).show()
        }
    }

    override fun launchCompanionAppOrFallback(action: LauncherAction): Boolean {
        val pkg = action.companionPackageName
        if (!pkg.isNullOrBlank() && launchApp(pkg)) {
            return true
        }
        return when (action) {
            LauncherAction.HATENA_FEED -> {
                // 公式はてなブックマークアプリがインストールされていれば優先起動し、未インストール時はWeb版へFallback
                if (launchApp("com.hatena.android.bookmark")) {
                    true
                } else {
                    launchShortcutUri("https://b.hatena.ne.jp/hotentry/all")
                }
            }
            LauncherAction.MY_NOTIFICATIONS -> {
                // Android標準の通知履歴設定画面を試行
                try {
                    val intent = Intent("android.settings.NOTIFICATION_HISTORY").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(intent)
                    true
                } catch (_: Exception) {
                    Toast.makeText(
                        appContext,
                        "My Notifications アプリが未インストールです（Phase 8 周辺アプリ連携枠）",
                        Toast.LENGTH_SHORT
                    ).show()
                    false
                }
            }
            else -> false
        }
    }
}
