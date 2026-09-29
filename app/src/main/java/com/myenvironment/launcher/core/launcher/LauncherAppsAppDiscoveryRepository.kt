package com.myenvironment.launcher.core.launcher

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.myenvironment.launcher.core.model.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/**
 * LauncherApps API を用いたインストール済みアプリ探索・パッケージ変更監視・高速ImageBitmapキャッシュ実装
 */
class LauncherAppsAppDiscoveryRepository(
    private val appContext: Context
) : AppDiscoveryRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val launcherApps = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager = appContext.getSystemService(Context.USER_SERVICE) as UserManager
    private val packageManager = appContext.packageManager

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    override val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _installedPackages = MutableStateFlow<Set<String>>(emptySet())
    override val installedPackages: StateFlow<Set<String>> = _installedPackages.asStateFlow()

    // Drawable キャッシュと、Compose で即座に描画できるプリレンダリング済み ImageBitmap キャッシュ (最大500アプリ分)
    private val iconCache = object : LruCache<String, Drawable>(300) {}
    private val bitmapCache = object : LruCache<String, ImageBitmap>(500) {}

    private val launcherAppsCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            invalidateCacheForPackage(packageName)
            triggerRefresh()
        }

        override fun onPackageAdded(packageName: String, user: UserHandle) {
            invalidateCacheForPackage(packageName)
            triggerRefresh()
        }

        override fun onPackageChanged(packageName: String, user: UserHandle) {
            invalidateCacheForPackage(packageName)
            triggerRefresh()
        }

        override fun onPackagesAvailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean
        ) {
            packageNames.forEach { invalidateCacheForPackage(it) }
            triggerRefresh()
        }

        override fun onPackagesUnavailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean
        ) {
            packageNames.forEach { invalidateCacheForPackage(it) }
            triggerRefresh()
        }
    }

    init {
        launcherApps.registerCallback(launcherAppsCallback, Handler(Looper.getMainLooper()))
        triggerRefresh()
    }

    private fun triggerRefresh() {
        scope.launch {
            refreshApps()
        }
    }

    private fun cacheKey(packageName: String, activityName: String): String {
        return if (activityName.isNotBlank()) "$packageName/$activityName" else packageName
    }

    private fun invalidateCacheForPackage(packageName: String) {
        synchronized(iconCache) {
            val keys = iconCache.snapshot().keys.filter {
                it == packageName || it.startsWith("$packageName/")
            }
            keys.forEach { iconCache.remove(it) }
        }
        synchronized(bitmapCache) {
            val keys = bitmapCache.snapshot().keys.filter {
                it == packageName || it.startsWith("$packageName/")
            }
            keys.forEach { bitmapCache.remove(it) }
        }
    }

    override suspend fun refreshApps() {
        withContext(Dispatchers.IO) {
            val collator = Collator.getInstance(Locale.JAPANESE).apply {
                strength = Collator.PRIMARY
            }
            val result = mutableListOf<AppInfo>()
            val profiles = userManager.userProfiles.ifEmpty {
                listOf(android.os.Process.myUserHandle())
            }

            val selfPackage = appContext.packageName

            for (profile in profiles) {
                val userSerial = userManager.getSerialNumberForUser(profile)
                val activities: List<LauncherActivityInfo> = try {
                    launcherApps.getActivityList(null, profile)
                } catch (_: Exception) {
                    emptyList()
                }

                for (info in activities) {
                    val pkg = info.applicationInfo.packageName
                    if (pkg == selfPackage) continue

                    val actName = info.componentName.className
                    val label = info.label?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: pkg
                    val firstInstall = try {
                        info.firstInstallTime
                    } catch (_: Exception) {
                        0L
                    }

                    result.add(
                        AppInfo(
                            packageName = pkg,
                            activityName = actName,
                            label = label,
                            userSerialNumber = userSerial,
                            firstInstallTime = firstInstall
                        )
                    )
                }
            }

            val sorted = result
                .distinctBy { "${it.packageName}/${it.activityName}/${it.userSerialNumber}" }
                .sortedWith { a, b ->
                    val cmp = collator.compare(a.label, b.label)
                    if (cmp != 0) cmp else a.packageName.compareTo(b.packageName)
                }

            val pkgSet = sorted.map { it.packageName }.toSet()
            _installedApps.value = sorted
            _installedPackages.value = pkgSet

            // バックグラウンドで全アプリのアイコンImageBitmapを事前プリロード（All Appsスクロールのカクつきを完全解消）
            scope.launch(Dispatchers.Default) {
                for (app in sorted) {
                    if (getCachedIconBitmap(app.packageName, app.activityName) == null) {
                        loadOrCreateIconBitmap(app.packageName, app.activityName)
                    }
                }
            }
        }
    }

    override fun getAppIcon(packageName: String, activityName: String): Drawable? {
        if (packageName.isBlank()) return null
        val key = cacheKey(packageName, activityName)
        synchronized(iconCache) {
            iconCache.get(key)?.let { return it }
        }

        val loaded: Drawable? = try {
            val profiles = userManager.userProfiles.ifEmpty {
                listOf(android.os.Process.myUserHandle())
            }
            var drawable: Drawable? = null
            for (profile in profiles) {
                val list = launcherApps.getActivityList(packageName, profile)
                val matched = if (activityName.isNotBlank()) {
                    list.find { it.componentName.className == activityName } ?: list.firstOrNull()
                } else {
                    list.firstOrNull()
                }
                if (matched != null) {
                    drawable = matched.getBadgedIcon(0)
                    break
                }
            }
            drawable ?: packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            null
        }

        if (loaded != null) {
            synchronized(iconCache) {
                iconCache.put(key, loaded)
            }
        }
        return loaded
    }

    override fun getCachedIconBitmap(packageName: String, activityName: String): ImageBitmap? {
        if (packageName.isBlank()) return null
        val key = cacheKey(packageName, activityName)
        synchronized(bitmapCache) {
            bitmapCache.get(key)?.let { return it }
            if (activityName.isNotBlank()) {
                bitmapCache.get(packageName)?.let { return it }
            }
        }
        return null
    }

    override fun loadOrCreateIconBitmap(packageName: String, activityName: String): ImageBitmap? {
        getCachedIconBitmap(packageName, activityName)?.let { return it }
        val drawable = getAppIcon(packageName, activityName) ?: return null

        val targetPx = 128 // Tiny Icons / ホームGridに最適な軽量固定解像度
        val bmp = try {
            val bitmap = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, targetPx, targetPx)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (_: Exception) {
            null
        }

        if (bmp != null) {
            val key = cacheKey(packageName, activityName)
            synchronized(bitmapCache) {
                bitmapCache.put(key, bmp)
                if (activityName.isNotBlank() && bitmapCache.get(packageName) == null) {
                    bitmapCache.put(packageName, bmp)
                }
            }
        }
        return bmp
    }

    override fun isPackageInstalled(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (_installedPackages.value.contains(packageName)) return true
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}
