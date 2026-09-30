package com.myenvironment.launcher.core.search

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import com.myenvironment.launcher.core.model.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ln

/**
 * アプリごとの利用統計メトリクス (仕様 22, 23, 27, 28)
 */
data class AppUsageMetric(
    val packageName: String,
    val lastTimeUsedMillis: Long,
    val recent7dLaunchScore: Double,
    val recent30dLaunchScore: Double
) {
    /**
     * 直近7日間の利用を優先しつつ直近30日間も加味した利用頻度スコア (仕様 23)
     */
    val frequencyScore: Double
        get() = recent7dLaunchScore * 2.5 + recent30dLaunchScore * 1.0

    /**
     * 検索ランキングの同一マッチ度ティア内で加算する控えめな利用頻度ボーナス (0..90点: 仕様 27)
     * 名前一致度 (Exact=1000, Prefix=800, Partial=600, Package=400) を逆転しない範囲に制限する。
     */
    val searchRankingBonus: Int
        get() {
            if (frequencyScore <= 0.0) return 0
            return (ln(1.0 + frequencyScore) * 18.0).toInt().coerceIn(1, 90)
        }
}

/**
 * 検索の Zero Query State で表示する3セクション (仕様 21〜25)
 */
data class ZeroQueryAppSections(
    val recentlyUsed: List<AppInfo> = emptyList(),
    val frequentlyUsed: List<AppInfo> = emptyList(),
    val recentlyInstalled: List<AppInfo> = emptyList()
) {
    val isEmpty: Boolean
        get() = recentlyUsed.isEmpty() && frequentlyUsed.isEmpty() && recentlyInstalled.isEmpty()
}

/**
 * Recently Used / Frequently Used / Recently Installed の純粋計算ロジック (仕様 22〜25, 38)
 */
object AppUsageAnalyzer {
    const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    const val WINDOW_7_DAYS_MILLIS = 7L * DAY_MILLIS
    const val WINDOW_30_DAYS_MILLIS = 30L * DAY_MILLIS
    const val DEFAULT_MAX_ITEMS_PER_SECTION = 4

    /**
     * 内部起動タイムスタンプ履歴とシステム UsageStats を統合して [AppUsageMetric] マップを構築する
     */
    fun buildUsageMetrics(
        internalLaunchEvents: List<Pair<String, Long>>,
        systemUsageRecords: List<SystemUsageRecord> = emptyList(),
        nowMillis: Long
    ): Map<String, AppUsageMetric> {
        val cutoff7d = nowMillis - WINDOW_7_DAYS_MILLIS
        val cutoff30d = nowMillis - WINDOW_30_DAYS_MILLIS

        val lastUsedMap = HashMap<String, Long>()
        val score7dMap = HashMap<String, Double>()
        val score30dMap = HashMap<String, Double>()

        // 1. ランチャー内部の起動履歴を集計（直近7日・30日）
        for ((pkg, timestamp) in internalLaunchEvents) {
            if (pkg.isBlank() || timestamp <= 0L) continue
            val prevLast = lastUsedMap[pkg] ?: 0L
            if (timestamp > prevLast) {
                lastUsedMap[pkg] = timestamp
            }
            if (timestamp >= cutoff30d) {
                score30dMap[pkg] = (score30dMap[pkg] ?: 0.0) + 1.0
                if (timestamp >= cutoff7d) {
                    score7dMap[pkg] = (score7dMap[pkg] ?: 0.0) + 1.0
                }
            }
        }

        // 2. Android UsageStatsManager の統計があれば統合
        for (record in systemUsageRecords) {
            val pkg = record.packageName
            if (pkg.isBlank() || record.lastTimeUsedMillis <= 0L) continue
            val prevLast = lastUsedMap[pkg] ?: 0L
            if (record.lastTimeUsedMillis > prevLast) {
                lastUsedMap[pkg] = record.lastTimeUsedMillis
            }
            if (record.lastTimeUsedMillis >= cutoff30d && record.totalTimeInForegroundMillis > 0L) {
                // フォアグラウンド利用分数を軽いスコアに変換（1分あたり0.35ポイント、上限30ポイント/日相当）
                val minutes = (record.totalTimeInForegroundMillis / 60_000.0).coerceAtLeast(0.2)
                val contribution = (minutes * 0.35).coerceAtMost(40.0)
                score30dMap[pkg] = (score30dMap[pkg] ?: 0.0) + contribution
                if (record.lastTimeUsedMillis >= cutoff7d) {
                    score7dMap[pkg] = (score7dMap[pkg] ?: 0.0) + contribution
                }
            }
        }

        val allPackages = (lastUsedMap.keys + score30dMap.keys + score7dMap.keys).toSet()
        val result = HashMap<String, AppUsageMetric>(allPackages.size)
        for (pkg in allPackages) {
            result[pkg] = AppUsageMetric(
                packageName = pkg,
                lastTimeUsedMillis = lastUsedMap[pkg] ?: 0L,
                recent7dLaunchScore = score7dMap[pkg] ?: 0.0,
                recent30dLaunchScore = score30dMap[pkg] ?: 0.0
            )
        }
        return result
    }

    /**
     * Zero Query State の 3 セクション（Recently Used / Frequently Used / Recently Installed）を算出する
     */
    fun computeZeroQuerySections(
        installedApps: List<AppInfo>,
        usageMap: Map<String, AppUsageMetric>,
        nowMillis: Long,
        maxItemsPerSection: Int = DEFAULT_MAX_ITEMS_PER_SECTION,
        recentInstallWindowMillis: Long = WINDOW_30_DAYS_MILLIS
    ): ZeroQueryAppSections {
        if (installedApps.isEmpty()) return ZeroQueryAppSections()
        val limit = maxItemsPerSection.coerceIn(1, 8)

        // パッケージごとの代表 AppInfo（同一パッケージに複数Activityがある場合は先頭）
        val uniqueAppsByPkg = installedApps.distinctBy { it.packageName }

        // 1. Recently Used: 最新利用日時の降順 (仕様 22)
        val recentlyUsed = uniqueAppsByPkg
            .mapNotNull { app ->
                val lastUsed = usageMap[app.packageName]?.lastTimeUsedMillis ?: 0L
                if (lastUsed > 0L) app to lastUsed else null
            }
            .sortedWith(
                compareByDescending<Pair<AppInfo, Long>> { it.second }
                    .thenBy { it.first.label }
            )
            .take(limit)
            .map { it.first }

        // 2. Frequently Used: 直近7日〜30日の利用頻度スコア降順 (仕様 23)
        val frequentlyUsed = uniqueAppsByPkg
            .mapNotNull { app ->
                val metric = usageMap[app.packageName] ?: return@mapNotNull null
                if (metric.frequencyScore > 0.0) app to metric else null
            }
            .sortedWith(
                compareByDescending<Pair<AppInfo, AppUsageMetric>> { it.second.frequencyScore }
                    .thenByDescending { it.second.lastTimeUsedMillis }
                    .thenBy { it.first.label }
            )
            .take(limit)
            .map { it.first }

        // 3. Recently Installed: firstInstallTime が直近30日以内のアプリを新しい順に表示 (仕様 24)
        val installCutoff = (nowMillis - recentInstallWindowMillis).coerceAtLeast(1L)
        val recentlyInstalled = uniqueAppsByPkg
            .filter { app ->
                app.firstInstallTime in installCutoff..nowMillis
            }
            .sortedWith(
                compareByDescending<AppInfo> { it.firstInstallTime }
                    .thenBy { it.label }
            )
            .take(limit)

        return ZeroQueryAppSections(
            recentlyUsed = recentlyUsed,
            frequentlyUsed = frequentlyUsed,
            recentlyInstalled = recentlyInstalled
        )
    }
}

data class SystemUsageRecord(
    val packageName: String,
    val lastTimeUsedMillis: Long,
    val totalTimeInForegroundMillis: Long
)

/**
 * 検索用のアプリ利用履歴（UsageStats + ランチャー内起動履歴フォールバック）を管理・キャッシュするリポジトリ (仕様 22, 23, 28, 37)
 */
class AppUsageRepository(
    private val appContext: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _usageMetrics = MutableStateFlow<Map<String, AppUsageMetric>>(emptyMap())
    val usageMetrics: StateFlow<Map<String, AppUsageMetric>> = _usageMetrics.asStateFlow()

    private val _hasUsageAccessPermission = MutableStateFlow(false)
    val hasUsageAccessPermission: StateFlow<Boolean> = _hasUsageAccessPermission.asStateFlow()

    @Volatile
    private var lastSystemQueryTimestamp: Long = 0L

    init {
        refreshUsageStatsAsync(force = true)
    }

    /**
     * Android の「使用状況へのアクセス」権限が許可されているか確認する
     */
    fun checkUsageStatsPermission(): Boolean {
        return try {
            val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * システムの「使用状況へのアクセス」設定画面を開く (仕様 22)
     */
    fun openUsageAccessSettings() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        } catch (_: Exception) {
            // Ignore if unavailable on device
        }
    }

    /**
     * ランチャーからアプリを起動した際に内部履歴へ記録し、即座にキャッシュを更新する
     */
    fun recordAppLaunch(packageName: String, timestampMillis: Long = System.currentTimeMillis()) {
        if (packageName.isBlank() || packageName == appContext.packageName) return
        scope.launch {
            val currentEvents = loadInternalLaunchEvents(nowMillis = timestampMillis).toMutableList()
            currentEvents.add(packageName to timestampMillis)
            val trimmed = currentEvents
                .sortedByDescending { it.second }
                .take(MAX_STORED_LAUNCH_EVENTS)
            saveInternalLaunchEvents(trimmed)
            rebuildMetrics(nowMillis = timestampMillis, forceSystemQuery = false)
        }
    }

    /**
     * バックグラウンドで UsageStats と内部履歴を再集計する（キャッシュTTL: 60秒、仕様 37）
     */
    fun refreshUsageStatsAsync(force: Boolean = false) {
        scope.launch {
            rebuildMetrics(nowMillis = System.currentTimeMillis(), forceSystemQuery = force)
        }
    }

    private var cachedSystemRecords: List<SystemUsageRecord> = emptyList()

    private suspend fun rebuildMetrics(nowMillis: Long, forceSystemQuery: Boolean) {
        withContext(Dispatchers.IO) {
            val permitted = checkUsageStatsPermission()
            _hasUsageAccessPermission.value = permitted

            val internalEvents = loadInternalLaunchEvents(nowMillis)
            if (permitted && (forceSystemQuery || nowMillis - lastSystemQueryTimestamp > SYSTEM_CACHE_TTL_MILLIS)) {
                cachedSystemRecords = querySystemUsageStats(nowMillis)
                lastSystemQueryTimestamp = nowMillis
            } else if (!permitted) {
                cachedSystemRecords = emptyList()
            }

            val merged = AppUsageAnalyzer.buildUsageMetrics(
                internalLaunchEvents = internalEvents,
                systemUsageRecords = cachedSystemRecords,
                nowMillis = nowMillis
            )
            _usageMetrics.value = merged
        }
    }

    private fun querySystemUsageStats(nowMillis: Long): List<SystemUsageRecord> {
        return try {
            val usageStatsManager =
                appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                    ?: return emptyList()
            val startTime = nowMillis - AppUsageAnalyzer.WINDOW_30_DAYS_MILLIS
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                nowMillis
            ).orEmpty()
            val selfPkg = appContext.packageName
            stats.mapNotNull { stat ->
                val pkg = stat.packageName
                if (pkg.isNullOrBlank() || pkg == selfPkg || stat.lastTimeUsed <= 0L) {
                    null
                } else {
                    SystemUsageRecord(
                        packageName = pkg,
                        lastTimeUsedMillis = stat.lastTimeUsed,
                        totalTimeInForegroundMillis = stat.totalTimeInForeground
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadInternalLaunchEvents(nowMillis: Long): List<Pair<String, Long>> {
        val raw = prefs.getString(KEY_LAUNCH_EVENTS, null) ?: return emptyList()
        val cutoff = nowMillis - AppUsageAnalyzer.WINDOW_30_DAYS_MILLIS
        return raw.lineSequence()
            .mapNotNull { line ->
                val idx = line.lastIndexOf('|')
                if (idx <= 0) return@mapNotNull null
                val pkg = line.substring(0, idx).trim()
                val ts = line.substring(idx + 1).trim().toLongOrNull() ?: return@mapNotNull null
                if (pkg.isNotEmpty() && ts >= cutoff) pkg to ts else null
            }
            .toList()
    }

    private fun saveInternalLaunchEvents(events: List<Pair<String, Long>>) {
        val serialized = events.joinToString("\n") { (pkg, ts) -> "$pkg|$ts" }
        prefs.edit().putString(KEY_LAUNCH_EVENTS, serialized).apply()
    }

    companion object {
        private const val PREFS_NAME = "chime_app_usage_store"
        private const val KEY_LAUNCH_EVENTS = "launch_events_v1"
        private const val MAX_STORED_LAUNCH_EVENTS = 500
        private const val SYSTEM_CACHE_TTL_MILLIS = 60_000L
    }
}
