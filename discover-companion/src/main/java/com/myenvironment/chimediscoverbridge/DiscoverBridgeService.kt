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
import android.util.Log
import com.myenvironment.discoverprotocol.DiscoverContract
import com.myenvironment.discoverprotocol.IDiscoverBridge
import com.myenvironment.discoverprotocol.IDiscoverBridgeCallback

/** Chime-owned IPC boundary; Google wire transactions are forwarded without copying UI. */
class DiscoverBridgeService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var connection: ServiceConnection? = null
    private var callback: IDiscoverBridgeCallback? = null
    private var clientDeath: IBinder.DeathRecipient? = null
    private var googleBinder: IBinder? = null
    private var googleDeath: IBinder.DeathRecipient? = null

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
                .appendQueryParameter("cv", "10").build())
        val info = packageManager.resolveService(intent, PackageManager.GET_META_DATA)
        if (info == null) {
            reportError("Google AppのDiscoverサービスが見つかりません")
            return
        }
        val apiVersion = info.serviceInfo.metaData?.getInt("service.api.version", 1) ?: 1
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (connection !== this) return
                Log.d("ChimeDiscoverBridge", "Google overlay service connected")
                try {
                    if (name.packageName != DiscoverContract.GOOGLE_PACKAGE ||
                        service.interfaceDescriptor != DiscoverContract.OVERLAY_DESCRIPTOR
                    ) {
                        reportError("Google AppのDiscover接続仕様が対応形式と異なります")
                        return
                    }
                    val death = IBinder.DeathRecipient {
                        main.post { googleDisconnected(this) }
                    }
                    googleBinder = service
                    googleDeath = death
                    service.linkToDeath(death, 0)
                    cb.onConnected(ForwardingBinder(service, this), apiVersion)
                } catch (_: RemoteException) {
                    googleDisconnected(this)
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                googleDisconnected(this)
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

    private fun googleDisconnected(conn: ServiceConnection) {
        if (connection !== conn) return
        Log.w("ChimeDiscoverBridge", "Google overlay disconnected")
        runCatching { callback?.onDisconnected() }
        disconnectGoogle()
    }

    private inner class ForwardingBinder(
        private val google: IBinder,
        private val conn: ServiceConnection
    ) : Binder() {
        init {
            // This proxy exposes Google's wire protocol, without a local implementation.
            attachInterface(null, DiscoverContract.OVERLAY_DESCRIPTOR)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            enforceClient()
            if (code == IBinder.INTERFACE_TRANSACTION) {
                reply?.writeString(DiscoverContract.OVERLAY_DESCRIPTOR)
                return true
            }
            val identity = Binder.clearCallingIdentity()
            return try {
                val accepted = google.transact(code, data, reply, flags)
                Log.d("ChimeDiscoverBridge", "Forwarded transaction code=$code accepted=$accepted")
                accepted
            } catch (e: RemoteException) {
                Log.w("ChimeDiscoverBridge", "Google transaction failed; code=$code", e)
                main.post { googleDisconnected(conn) }
                // Binder cannot marshal RemoteException with Parcel.writeException.
                // One-way calls report the failure through the bridge callback instead.
                if (flags and IBinder.FLAG_ONEWAY != 0) return true
                throw IllegalStateException("Google Discover connection was lost", e)
            } finally {
                Binder.restoreCallingIdentity(identity)
            }
        }
    }

    private fun reportError(message: String) {
        Log.w("ChimeDiscoverBridge", message)
        runCatching { callback?.onError(message) }
        disconnectGoogle()
    }

    private fun disconnectGoogle() {
        val googleRecipient = googleDeath
        if (googleRecipient != null) {
            runCatching { googleBinder?.unlinkToDeath(googleRecipient, 0) }
        }
        googleBinder = null
        googleDeath = null
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
