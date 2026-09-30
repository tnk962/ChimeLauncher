package com.myenvironment.chimediscoverbridge

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.Process
import android.os.RemoteException
import com.myenvironment.discoverprotocol.DiscoverContract
import com.myenvironment.discoverprotocol.IDiscoverBridge
import com.myenvironment.discoverprotocol.IDiscoverBridgeCallback

/** Chime-owned IPC boundary; Google wire transactions are forwarded without copying UI. */
class DiscoverBridgeService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var connection: ServiceConnection? = null
    private var callback: IDiscoverBridgeCallback? = null
    private var clientDeath: IBinder.DeathRecipient? = null

    private fun enforceClient() {
        val uid = Binder.getCallingUid()
        val packages = packageManager.getPackagesForUid(uid).orEmpty()
        if (DiscoverContract.LAUNCHER_PACKAGE !in packages ||
            packageManager.checkSignatures(uid, Process.myUid()) != PackageManager.SIGNATURE_MATCH
        ) throw SecurityException("Only the same-signed Chime Launcher may connect")
    }

    private val bridge = object : IDiscoverBridge.Stub() {
        override fun connect(cb: IDiscoverBridgeCallback) {
            enforceClient()
            main.post { connectGoogle(cb) }
        }

        override fun disconnect() {
            enforceClient()
            main.post { disconnectGoogle() }
        }
    }

    override fun onBind(intent: Intent): IBinder = bridge

    private fun connectGoogle(cb: IDiscoverBridgeCallback) {
        disconnectGoogle()
        callback = cb
        val death = IBinder.DeathRecipient { main.post { disconnectGoogle() } }
        clientDeath = death
        try {
            cb.asBinder().linkToDeath(death, 0)
        } catch (_: RemoteException) {
            disconnectGoogle()
            return
        }
        val intent = Intent(DiscoverContract.OVERLAY_ACTION)
            .setPackage(DiscoverContract.GOOGLE_PACKAGE)
            .setData(Uri.parse("app://$packageName:${Process.myUid()}")
                .buildUpon().appendQueryParameter("v", "7")
                .appendQueryParameter("cv", "9").build())
        val info = packageManager.resolveService(intent, PackageManager.GET_META_DATA)
        if (info == null) {
            reportError("Google AppのDiscoverサービスが見つかりません")
            return
        }
        val apiVersion = info.serviceInfo.metaData?.getInt("service.api.version", 1) ?: 1
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (connection !== this) return
                try {
                    if (name.packageName != DiscoverContract.GOOGLE_PACKAGE ||
                        service.interfaceDescriptor != DiscoverContract.OVERLAY_DESCRIPTOR
                    ) {
                        reportError("Google AppのDiscover接続仕様が対応形式と異なります")
                        return
                    }
                    cb.onConnected(ForwardingBinder(service), apiVersion)
                } catch (_: RemoteException) {
                    disconnectGoogle()
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                if (connection === this) {
                    runCatching { cb.onDisconnected() }
                    disconnectGoogle()
                }
            }

            override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
            override fun onNullBinding(name: ComponentName) {
                if (connection === this) reportError("Google AppがDiscover接続を受け付けませんでした")
            }
        }
        try {
            connection = conn
            if (!bindService(intent, conn, BIND_AUTO_CREATE or BIND_WAIVE_PRIORITY)) {
                connection = null
                reportError("Google Appへの接続に失敗しました")
            }
        } catch (e: SecurityException) {
            connection = null
            reportError("Google Appに接続を拒否されました")
        } catch (e: RuntimeException) {
            connection = null
            reportError("Google Appへの接続に失敗しました: ${e.javaClass.simpleName}")
        }
    }

    private inner class ForwardingBinder(private val google: IBinder) : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            enforceClient()
            val identity = Binder.clearCallingIdentity()
            return try {
                google.transact(code, data, reply, flags)
            } finally {
                Binder.restoreCallingIdentity(identity)
            }
        }
    }

    private fun reportError(message: String) {
        runCatching { callback?.onError(message) }
        disconnectGoogle()
    }

    private fun disconnectGoogle() {
        connection?.let { runCatching { unbindService(it) } }
        connection = null
        val death = clientDeath
        if (death != null) runCatching { callback?.asBinder()?.unlinkToDeath(death, 0) }
        clientDeath = null
        callback = null
    }

    override fun onUnbind(intent: Intent): Boolean {
        disconnectGoogle()
        return false
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        disconnectGoogle()
        super.onDestroy()
    }
}
