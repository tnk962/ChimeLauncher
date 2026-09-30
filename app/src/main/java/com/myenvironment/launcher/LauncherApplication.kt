package com.myenvironment.launcher

import android.app.Application
import android.content.Context
import com.myenvironment.launcher.core.backup.BackupManager
import com.myenvironment.launcher.core.backup.JsonBackupManager
import com.myenvironment.launcher.core.chime.ChimeController
import com.myenvironment.launcher.core.feed.DefaultFeedBridge
import com.myenvironment.launcher.core.feed.FeedBridge
import com.myenvironment.launcher.core.launcher.AndroidAppLauncher
import com.myenvironment.launcher.core.launcher.AppDiscoveryRepository
import com.myenvironment.launcher.core.launcher.AppLauncher
import com.myenvironment.launcher.core.launcher.LauncherAppsAppDiscoveryRepository
import com.myenvironment.launcher.core.search.AppUsageRepository
import com.myenvironment.launcher.core.search.DefaultSearchEngine
import com.myenvironment.launcher.core.search.SearchEngine
import com.myenvironment.launcher.core.storage.DataStoreSettingsRepository
import com.myenvironment.launcher.core.storage.LayoutRepository
import com.myenvironment.launcher.core.storage.RoomLayoutRepository
import com.myenvironment.launcher.core.storage.SettingsRepository
import com.myenvironment.launcher.core.storage.db.LauncherDatabase
import com.myenvironment.launcher.core.update.AppUpdateManager
import com.myenvironment.launcher.core.widget.WidgetHostManager

/**
 * Chime Launcher 全体の依存オブジェクトを保持する軽量DIコンテナ (仕様 40, 41)
 *
 * 各機能はInterface境界で公開され、将来的なHilt/Koin移行やMulti-Module分割を容易にする。
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    private val database = LauncherDatabase.getInstance(appContext)
    private val dao = database.launcherDao()

    val appDiscoveryRepository: AppDiscoveryRepository =
        LauncherAppsAppDiscoveryRepository(appContext)

    val appLauncher: AppLauncher =
        AndroidAppLauncher(appContext)

    val widgetHostManager: WidgetHostManager =
        WidgetHostManager(appContext)

    private val dataStoreSettingsRepository =
        DataStoreSettingsRepository(appContext)

    val settingsRepository: SettingsRepository =
        dataStoreSettingsRepository

    val layoutRepository: LayoutRepository =
        RoomLayoutRepository(
            dao = dao,
            settingsRepository = dataStoreSettingsRepository,
            appDiscoveryRepository = appDiscoveryRepository
        )

    val searchEngine: SearchEngine =
        DefaultSearchEngine()

    val appUsageRepository: AppUsageRepository =
        AppUsageRepository(appContext)

    val chimeController: ChimeController =
        ChimeController()

    val backupManager: BackupManager =
        JsonBackupManager(
            appContext = appContext,
            dao = dao,
            layoutRepository = layoutRepository,
            settingsRepository = settingsRepository,
            appDiscoveryRepository = appDiscoveryRepository
        )

    val feedBridge: FeedBridge =
        DefaultFeedBridge(appContext)

    val appUpdateManager: AppUpdateManager =
        AppUpdateManager(appContext)
}

class LauncherApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
