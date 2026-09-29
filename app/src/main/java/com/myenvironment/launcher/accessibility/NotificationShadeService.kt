package com.myenvironment.launcher.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

/**
 * ホーム画面の下スワイプジェスチャーで通知シェードを開くための AccessibilityService (仕様 7.2)
 *
 * 画面内容の取得は一切行わず、GLOBAL_ACTION_NOTIFICATIONS の発行のみを担当する。
 */
class NotificationShadeService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 画面イベントの監視は不要
    }

    override fun onInterrupt() {
        // 中断時の処理なし
    }

    override fun onDestroy() {
        if (instanceRef?.get() === this) {
            instanceRef = null
        }
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instanceRef: WeakReference<NotificationShadeService>? = null

        /**
         * 通知シェードを展開する。
         * AccessibilityService または StatusBarManager (EXPAND_STATUS_BAR 権限) を利用して即座に開く。
         */
        fun expandNotifications(context: Context? = null): Boolean {
            // 1. AccessibilityService が接続中なら優先実行
            val service = instanceRef?.get()
            if (service != null && service.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)) {
                return true
            }

            // 2. EXPAND_STATUS_BAR 権限を用いた StatusBarManager.expandNotificationsPanel() フォールバック
            if (context != null) {
                val expandedByStatusBar = runCatching {
                    val statusBarService = context.applicationContext.getSystemService("statusbar")
                        ?: return@runCatching false
                    val statusBarManagerClass = Class.forName("android.app.StatusBarManager")
                    val expandMethod = statusBarManagerClass.getMethod("expandNotificationsPanel")
                    expandMethod.isAccessible = true
                    expandMethod.invoke(statusBarService)
                    true
                }.getOrDefault(false)

                if (expandedByStatusBar) {
                    return true
                }
            }

            return false
        }

        /**
         * システム設定上で本AccessibilityServiceが有効化されているか判定する
         */
        fun isServiceEnabled(context: Context): Boolean {
            if (instanceRef?.get() != null) return true

            val expectedComponentName = ComponentName(context, NotificationShadeService::class.java)
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)
            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                val enabledComponent = ComponentName.unflattenFromString(componentNameString)
                if (enabledComponent != null && enabledComponent == expectedComponentName) {
                    return true
                }
            }
            return false
        }
    }
}
