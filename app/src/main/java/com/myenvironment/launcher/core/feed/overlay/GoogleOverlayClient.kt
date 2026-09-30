package com.myenvironment.launcher.core.feed.overlay

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.google.android.libraries.launcherclient.ILauncherOverlay
import com.google.android.libraries.launcherclient.ILauncherOverlayCallback
import com.myenvironment.discoverprotocol.DiscoverContract
import com.myenvironment.discoverprotocol.IDiscoverBridge
import com.myenvironment.discoverprotocol.IDiscoverBridgeCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GoogleOverlayState(
    val ready: Boolean = false,
    val progress: Float = 0f,
    val message: String = "Chime Discover Companionをインストールしてください"
)

/** Activity-owned client: never retains an Activity through the application container. */
class GoogleOverlayClient(private val activity: Activity) {
    private val main = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(GoogleOverlayState())
    val state = mutableState.asStateFlow()
    val revealWidth: Float
        get() = activity.window.decorView.width.takeIf { it > 0 }?.toFloat()
            ?: activity.resources.displayMetrics.widthPixels.toFloat()
    private var enabled = true
    private var started = false
    private var resumed = false
    private var attached = false
    private var destroyed = false
    private var connection: ServiceConnection? = null
    private var bridge: IDiscoverBridge? = null
    private var overlay: ILauncherOverlay? = null
    private var callback: ILauncherOverlayCallback? = null
    private var death: IBinder.DeathRecipient? = null
    private var apiVersion = 1
    private var generation = 0
    private var retryDelay = 1000L
    private val reconnect = Runnable { connect() }
    private val timeout = Runnable {
        if (started && enabled && !state.value.ready) {
            fail("Google Discoverの応答がありません。Google Appを更新・有効化してください", retry = false)
        }
    }
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.data?.schemeSpecificPart in setOf(DiscoverContract.COMPANION_PACKAGE, DiscoverContract.GOOGLE_PACKAGE)) {
                disconnect()
                if (started && enabled) connect()
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(activity, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        disconnect()
        if (enabled && started) connect()
    }

    fun onStart() { started = true; connect(); sendActivityState() }
    fun onResume() { resumed = true; connect(); sendActivityState() }
    fun onPause() { resumed = false; sendActivityState() }
    fun onStop() { started = false; sendActivityState(); disconnect() }
    fun onAttachedToWindow() { attached = true; attachWindow() }
    fun onDetachedFromWindow() {
        attached = false
        call { windowDetached(activity.isChangingConfigurations) }
        mutableState.value = state.value.copy(ready = false, progress = 0f)
    }
    fun onConfigurationChanged() {
        // configChanges keeps this Activity alive on Fold open/close and rotation.
        close()
        if (attached) attachWindow()
    }
    fun onDestroy() {
        destroyed = true
        disconnect()
        activity.unregisterReceiver(packageReceiver)
        main.removeCallbacksAndMessages(null)
    }

    private fun connect() {
        if (!enabled || !started || destroyed || connection != null) return
        val pm = activity.packageManager
        val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
        if (home?.activityInfo?.packageName != activity.packageName) {
            mutableState.value = GoogleOverlayState(message = "Chime Launcherを標準のホームに設定してください")
            return
        }
        val installed = runCatching { pm.getApplicationInfo(DiscoverContract.COMPANION_PACKAGE, 0).enabled }.getOrDefault(false)
        if (!installed) {
            mutableState.value = GoogleOverlayState()
            return
        }
        if (pm.checkSignatures(activity.packageName, DiscoverContract.COMPANION_PACKAGE) != PackageManager.SIGNATURE_MATCH) {
            mutableState.value = GoogleOverlayState(message = "Companionの署名が一致しません。同じリリースの2つのAPKを導入してください")
            return
        }
        mutableState.value = GoogleOverlayState(message = "Google Discoverに接続中…")
        val session = ++generation
        val bridgeCallback = object : IDiscoverBridgeCallback.Stub() {
            private fun trusted(): Boolean = pm.getPackagesForUid(Binder.getCallingUid()).orEmpty()
                .contains(DiscoverContract.COMPANION_PACKAGE) &&
                pm.checkSignatures(Binder.getCallingUid(), android.os.Process.myUid()) == PackageManager.SIGNATURE_MATCH

            override fun onConnected(binder: IBinder, version: Int) {
                if (!trusted()) return
                main.post {
                    if (session != generation) return@post
                    val descriptor = runCatching { binder.interfaceDescriptor }.getOrNull()
                    if (descriptor != DiscoverContract.OVERLAY_DESCRIPTOR) {
                        fail("Companionのプロトコルが一致しません", false)
                        return@post
                    }
                    overlay = ILauncherOverlay.Stub.asInterface(binder)
                    apiVersion = version
                    callback = object : ILauncherOverlayCallback.Stub() {
                        override fun overlayScrollChanged(progress: Float) {
                            main.post {
                                if (session == generation && progress.isFinite()) {
                                    mutableState.value = state.value.copy(progress = progress.coerceIn(0f, 1f))
                                }
                            }
                        }
                        override fun overlayStatusChanged(status: Int) {
                            main.post {
                                if (session == generation) {
                                    val ready = status and 1 != 0 && attached
                                    mutableState.value = state.value.copy(ready = ready,
                                        progress = if (ready) state.value.progress else 0f,
                                        message = if (ready) "さらに右へスワイプするとGoogle Discoverを表示します" else "Google Discoverは現在利用できません")
                                    if (ready) { main.removeCallbacks(timeout); retryDelay = 1000L }
                                }
                            }
                        }
                    }
                    attachWindow()
                }
            }
            override fun onDisconnected() {
                if (trusted()) main.post { if (session == generation) fail("Google Discoverとの接続が切れました", true) }
            }
            override fun onError(message: String) {
                if (trusted()) main.post { if (session == generation) fail(message, false) }
            }
        }
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (session != generation) return
                try {
                    if (binder.interfaceDescriptor != DiscoverContract.BRIDGE_DESCRIPTOR) {
                        fail("Companionのバージョンが対応していません", false)
                        return
                    }
                    bridge = IDiscoverBridge.Stub.asInterface(binder)
                    val recipient = IBinder.DeathRecipient {
                        main.post { if (session == generation) fail("Companionとの接続が切れました", true) }
                    }
                    death = recipient
                    binder.linkToDeath(recipient, 0)
                    bridge?.connect(bridgeCallback)
                } catch (_: Exception) { fail("Companionへの接続に失敗しました", true) }
            }
            override fun onServiceDisconnected(name: ComponentName) {
                if (session == generation) fail("Companionとの接続が切れました", true)
            }
            override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
            override fun onNullBinding(name: ComponentName) {
                if (session == generation) fail("Companionが接続を受け付けませんでした", false)
            }
        }
        try {
            connection = conn
            val intent = Intent().setComponent(ComponentName(DiscoverContract.COMPANION_PACKAGE, DiscoverContract.COMPANION_SERVICE))
            if (!activity.bindService(intent, conn, Context.BIND_AUTO_CREATE or Context.BIND_ADJUST_WITH_ACTIVITY)) {
                connection = null
                fail("Companionへ接続できませんでした", false)
            } else {
                main.postDelayed(timeout, 10000L)
            }
        } catch (_: Exception) {
            connection = null
            fail("Companionへの接続が拒否されました。同じリリースのAPKを導入してください", false)
        }
    }

    private fun attachWindow() {
        if (!attached || overlay == null || callback == null) return
        val attrs = WindowManager.LayoutParams().apply { copyFrom(activity.window.attributes) }
        // Google creates an application-level window (type 4), not a child window.
        // View.windowToken identifies ViewRoot's window and is rejected as BadToken.
        attrs.token = activity.window.attributes.token
            ?: activity.window.decorView.applicationWindowToken
            ?: return
        val cb = callback!!
        call {
            if (apiVersion >= 3) {
                windowAttached2(Bundle().apply {
                    putParcelable("layout_params", attrs)
                    putParcelable("configuration", activity.resources.configuration)
                    putInt("client_options", 1 or 2 or 4 or 8)
                }, cb)
            } else { windowAttached(attrs, cb, 1 or 2 or 4 or 8) }
        }
        sendActivityState()
    }

    private fun sendActivityState() {
        call {
            if (apiVersion >= 4) setActivityState((if (started) 1 else 0) or (if (resumed) 2 else 0))
            else if (resumed) onResume() else onPause()
        }
    }

    fun beginScroll(): Boolean {
        if (!state.value.ready) return false
        return call { startScroll() }
    }
    fun scroll(progress: Float) {
        if (progress.isFinite()) call { onScroll(progress.coerceIn(0f, 1f)) }
    }
    fun endScroll() { call { endScroll() } }
    fun close() {
        call { closeOverlay(1) }
        mutableState.value = state.value.copy(progress = 0f)
    }

    private fun call(operation: ILauncherOverlay.() -> Unit): Boolean {
        val target = overlay ?: return false
        return try { target.operation(); true } catch (_: Exception) {
            fail("Google Discoverとの通信に失敗しました", true)
            false
        }
    }

    private fun fail(message: String, retry: Boolean) {
        disconnect()
        mutableState.value = GoogleOverlayState(message = message)
        if (retry && started && enabled && !destroyed) {
            main.postDelayed(reconnect, retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(30000L)
        }
    }

    private fun disconnect() {
        generation++
        main.removeCallbacks(reconnect)
        main.removeCallbacks(timeout)
        runCatching { overlay?.windowDetached(activity.isChangingConfigurations) }
        runCatching { bridge?.disconnect() }
        val recipient = death
        if (recipient != null) runCatching { bridge?.asBinder()?.unlinkToDeath(recipient, 0) }
        connection?.let { runCatching { activity.unbindService(it) } }
        connection = null
        overlay = null
        callback = null
        bridge = null
        death = null
        mutableState.value = state.value.copy(ready = false, progress = 0f)
    }
}
