package com.myenvironment.launcher.core.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.SizeF
import android.view.View
import android.widget.TextView
import kotlin.math.ceil

/**
 * 端末にインストールされている各 AppWidget Provider のカタログ情報
 */
data class WidgetProviderCatalogItem(
    val provider: ComponentName,
    val packageName: String,
    val providerClassName: String,
    val appLabel: String,
    val widgetLabel: String,
    val description: String,
    val defaultSpanX: Int,
    val defaultSpanY: Int,
    val minWidthDp: Int,
    val minHeightDp: Int,
    val resizeMode: Int,
    val hasConfigActivity: Boolean,
    val isConfigurationOptional: Boolean,
    val providerInfo: AppWidgetProviderInfo
)

/**
 * Jetpack Compose 内のグリッドセルに埋め込むカスタム [AppWidgetHostView]
 *
 * - 通常モード時でもウィジェット上の長押しを検出して Launcher のコンテキストメニューを開けるようにする。
 * - 編集モード時はウィジェット内部のクリック発火を防止し、ドラッグ移動やリサイズ操作を優先する。
 */
class LauncherAppWidgetHostView(context: Context) : AppWidgetHostView(context) {

    var isInEditModeOverlay: Boolean = false
    var onWidgetLongPress: (() -> Unit)? = null
    var contentScale: Float = WidgetHostManager.WIDGET_CONTENT_SCALE

    private var downX = 0f
    private var downY = 0f
    private var longPressTriggered = false
    private var childRequestedDisallowIntercept = false
    private var isScrollingVertically = false
    private var hasScrollableOnDown = false
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = android.view.ViewConfiguration.getLongPressTimeout().toLong()

    init {
        // Android 標準の AppWidgetHostView が自動付与する 8dp の内部余白を完全に排除し、グリッドのキワキワまで表示する
        setPadding(0, 0, 0, 0)
        clipToPadding = false
        clipChildren = false
    }

    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        super.setAppWidget(appWidgetId, info)
        setPadding(0, 0, 0, 0)
    }

    /**
     * このウィジェット内部に縦スクロール可能なView（Google Keepのメモ一覧、カレンダー予定、Gmail等のListView / ScrollView等）が
     * 含まれているかどうかを判定する。
     */
    fun hasVerticalScrollableContent(): Boolean {
        if (childRequestedDisallowIntercept || isScrollingVertically) {
            return true
        }
        if (appWidgetInfo?.autoAdvanceViewId != null && appWidgetInfo?.autoAdvanceViewId != View.NO_ID) {
            return true
        }
        return containsScrollableView(this)
    }

    private fun containsScrollableView(view: View): Boolean {
        if (view !== this) {
            if (view is android.widget.AbsListView ||
                view is android.widget.ScrollView ||
                view is android.widget.AdapterViewAnimator
            ) {
                return true
            }
            if (view is android.view.ViewGroup) {
                if (view.isScrollContainer ||
                    view.canScrollVertically(1) ||
                    view.canScrollVertically(-1)
                ) {
                    return true
                }
                val className = view.javaClass.name
                if (className.contains("ScrollView", ignoreCase = true) ||
                    className.contains("RecyclerView", ignoreCase = true) ||
                    className.contains("ListView", ignoreCase = true) ||
                    className.contains("GridView", ignoreCase = true) ||
                    className.contains("StackView", ignoreCase = true) ||
                    className.contains("ViewPager", ignoreCase = true)
                ) {
                    return true
                }
            }
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i) ?: continue
                if (containsScrollableView(child)) {
                    return true
                }
            }
        }
        return false
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept) {
            childRequestedDisallowIntercept = true
            handler?.removeCallbacks(longPressRunnable)
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (paddingLeft != 0 || paddingTop != 0 || paddingRight != 0 || paddingBottom != 0) {
            setPadding(0, 0, 0, 0)
        }
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)
        val scale = contentScale.coerceIn(0.6f, 1.0f)

        if (parentWidth > 0 && parentHeight > 0 && scale < 0.999f) {
            val virtualWidth = kotlin.math.round(parentWidth / scale).toInt()
            val virtualHeight = kotlin.math.round(parentHeight / scale).toInt()
            val childWidthSpec = MeasureSpec.makeMeasureSpec(virtualWidth, MeasureSpec.EXACTLY)
            val childHeightSpec = MeasureSpec.makeMeasureSpec(virtualHeight, MeasureSpec.EXACTLY)
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child.visibility != View.GONE) {
                    child.measure(childWidthSpec, childHeightSpec)
                }
            }
            setMeasuredDimension(parentWidth, parentHeight)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val parentWidth = right - left
        val parentHeight = bottom - top
        val scale = contentScale.coerceIn(0.6f, 1.0f)

        if (parentWidth > 0 && parentHeight > 0 && scale < 0.999f) {
            val virtualWidth = kotlin.math.round(parentWidth / scale).toInt()
            val virtualHeight = kotlin.math.round(parentHeight / scale).toInt()
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child.visibility != View.GONE) {
                    child.pivotX = 0f
                    child.pivotY = 0f
                    child.scaleX = scale
                    child.scaleY = scale
                    child.layout(0, 0, virtualWidth, virtualHeight)
                }
            }
        } else {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                child.scaleX = 1f
                child.scaleY = 1f
            }
            super.onLayout(changed, left, top, right, bottom)
        }
    }

    private val longPressRunnable = Runnable {
        if (!isInEditModeOverlay && !isScrollingVertically && onWidgetLongPress != null) {
            longPressTriggered = true
            // 子View（ListView等）の押下状態・クリック発火をキャンセルする
            val now = android.os.SystemClock.uptimeMillis()
            val cancelEvent = android.view.MotionEvent.obtain(
                now,
                now,
                android.view.MotionEvent.ACTION_CANCEL,
                downX,
                downY,
                0
            )
            super.dispatchTouchEvent(cancelEvent)
            cancelEvent.recycle()
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onWidgetLongPress?.invoke()
        }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (!isInEditModeOverlay) {
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = ev.x
                    downY = ev.y
                    longPressTriggered = false
                    childRequestedDisallowIntercept = false
                    isScrollingVertically = false
                    hasScrollableOnDown = hasVerticalScrollableContent()
                    handler?.removeCallbacks(longPressRunnable)
                    handler?.postDelayed(longPressRunnable, longPressTimeout)
                    if (hasScrollableOnDown) {
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = kotlin.math.abs(ev.x - downX)
                    val dy = kotlin.math.abs(ev.y - downY)
                    if (dx > touchSlop || dy > touchSlop) {
                        handler?.removeCallbacks(longPressRunnable)
                    }
                    if (hasScrollableOnDown || hasVerticalScrollableContent()) {
                        if (dy > touchSlop * 0.5f && dy >= dx * 0.8f) {
                            isScrollingVertically = true
                            parent?.requestDisallowInterceptTouchEvent(true)
                        } else if (!isScrollingVertically && dx > touchSlop && dx > dy * 1.25f) {
                            // 明確な横スワイプの場合は HorizontalPager のページ切り替えを許可する
                            parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    handler?.removeCallbacks(longPressRunnable)
                    isScrollingVertically = false
                    childRequestedDisallowIntercept = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    if (longPressTriggered) {
                        longPressTriggered = false
                        return true
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (isInEditModeOverlay) {
            // 編集モード中はウィジェット内部のRemoteViewsへタッチイベントを渡さない
            return true
        }
        return if (longPressTriggered) true else super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (isInEditModeOverlay) {
            return false
        }
        if (event.actionMasked == android.view.MotionEvent.ACTION_UP ||
            event.actionMasked == android.view.MotionEvent.ACTION_CANCEL
        ) {
            handler?.removeCallbacks(longPressRunnable)
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        handler?.removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }

    override fun getErrorView(): View {
        return TextView(context).apply {
            text = "ウィジェットを読み込めませんでした"
            textSize = 12f
            setTextColor(0xFFFFB74D.toInt())
            setPadding(8, 8, 8, 8)
        }
    }
}

/**
 * My Launcher 専用の [AppWidgetHost]
 */
class LauncherAppWidgetHost(
    context: Context,
    hostId: Int
) : AppWidgetHost(context, hostId) {

    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        return LauncherAppWidgetHostView(context)
    }
}

/**
 * Android 標準の [AppWidgetHost] および [AppWidgetManager] を管理するマネージャークラス (仕様 30, 41)
 */
class WidgetHostManager(context: Context) {

    private val appContext: Context = context.applicationContext
    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    val appWidgetHost: LauncherAppWidgetHost = LauncherAppWidgetHost(appContext, HOST_ID)
    private val activeHostViews = java.util.concurrent.ConcurrentHashMap<Int, LauncherAppWidgetHostView>()

    private var isListening = false

    /**
     * 指定された appWidgetId のウィジェットが内部に縦スクロール可能なコンテンツ（Keepのメモ一覧等）を持つかどうかを返す。
     */
    fun isWidgetScrollable(appWidgetId: Int): Boolean {
        if (appWidgetId <= 0) return false
        val hostView = activeHostViews[appWidgetId] ?: return false
        return hostView.hasVerticalScrollableContent()
    }

    /**
     * Activity の表示開始時に AppWidget の更新監視を開始する。
     */
    fun startListening() {
        if (isListening) return
        runCatching {
            appWidgetHost.startListening()
            isListening = true
        }
    }

    /**
     * Activity の停止時に AppWidget の更新監視を停止する。
     */
    fun stopListening() {
        if (!isListening) return
        runCatching {
            appWidgetHost.stopListening()
            isListening = false
        }
    }

    /**
     * 新しい appWidgetId を割り当てる。
     */
    fun allocateAppWidgetId(): Int {
        return runCatching {
            appWidgetHost.allocateAppWidgetId()
        }.getOrDefault(-1)
    }

    /**
     * 不要になった appWidgetId を解放する。
     */
    fun deleteAppWidgetId(appWidgetId: Int) {
        if (appWidgetId <= 0) return
        activeHostViews.remove(appWidgetId)
        runCatching {
            appWidgetHost.deleteAppWidgetId(appWidgetId)
        }
    }

    /**
     * 権限があれば即座に appWidgetId と Provider をバインドする。
     * @return バインドに成功した場合は true、ユーザーのシステム許可 (ACTION_APPWIDGET_BIND) が必要な場合は false
     */
    fun tryBindAppWidget(appWidgetId: Int, provider: ComponentName): Boolean {
        if (appWidgetId <= 0) return false
        return runCatching {
            appWidgetManager.bindAppWidgetIdIfAllowed(
                appWidgetId,
                Process.myUserHandle(),
                provider,
                null
            )
        }.getOrElse {
            runCatching {
                appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider)
            }.getOrDefault(false)
        }
    }

    /**
     * バインド済みの appWidgetId から [AppWidgetProviderInfo] を取得する。
     */
    fun getAppWidgetInfo(appWidgetId: Int): AppWidgetProviderInfo? {
        if (appWidgetId <= 0) return null
        return runCatching {
            appWidgetManager.getAppWidgetInfo(appWidgetId)
        }.getOrNull()
    }

    /**
     * パッケージ名とクラス名から現在インストールされている [AppWidgetProviderInfo] を検索する。
     * バックアップ復元後の再バインド等で使用する。
     */
    fun findProviderInfo(packageName: String, providerClassName: String): AppWidgetProviderInfo? {
        if (packageName.isBlank()) return null
        val normalizedClass = when {
            providerClassName.isBlank() -> ""
            providerClassName.startsWith(".") -> "$packageName$providerClassName"
            else -> providerClassName
        }

        val providers = runCatching {
            appWidgetManager.getInstalledProvidersForPackage(packageName, Process.myUserHandle())
        }.getOrElse {
            runCatching { appWidgetManager.installedProviders }.getOrDefault(emptyList())
                .filter { it.provider.packageName == packageName }
        }

        if (providers.isEmpty()) return null
        if (normalizedClass.isBlank()) return providers.firstOrNull()

        return providers.find {
            it.provider.className == normalizedClass ||
                it.provider.shortClassName == providerClassName
        } ?: providers.firstOrNull()
    }

    /**
     * 端末内の利用可能な全ウィジェット一覧を取得する。
     */
    fun loadInstalledWidgetCatalog(
        maxColumns: Int = 5,
        maxRows: Int = 6
    ): List<WidgetProviderCatalogItem> {
        val pm = appContext.packageManager
        val density = appContext.resources.displayMetrics.density.coerceAtLeast(1f)
        val rawProviders = runCatching {
            appWidgetManager.installedProviders
        }.getOrDefault(emptyList())

        return rawProviders.mapNotNull { info ->
            runCatching {
                val provider = info.provider ?: return@mapNotNull null
                val pkg = provider.packageName
                val cls = provider.className

                val appLabel = runCatching {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                }.getOrDefault(pkg.substringAfterLast('.'))

                val widgetLabel = runCatching {
                    info.loadLabel(pm)?.toString()?.trim()
                }.getOrNull().takeUnless { it.isNullOrEmpty() } ?: appLabel

                val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    runCatching {
                        info.loadDescription(appContext)?.toString()?.trim().orEmpty()
                    }.getOrDefault("")
                } else {
                    ""
                }

                // AndroidのAppWidgetProviderInfo.minWidth / minHeight は px 単位で返る端末と dp 相当があるが、
                // 標準仕様では dp または px (DisplayMetrics参照) のため、大きすぎる値は density で補正する
                val minWidthDp = normalizeDimensionToDp(info.minWidth, density)
                val minHeightDp = normalizeDimensionToDp(info.minHeight, density)

                val targetCellW = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    info.targetCellWidth
                } else {
                    0
                }
                val targetCellH = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    info.targetCellHeight
                } else {
                    0
                }

                val (spanX, spanY) = calculateDefaultSpan(
                    minWidthDp = minWidthDp,
                    minHeightDp = minHeightDp,
                    targetCellWidth = targetCellW,
                    targetCellHeight = targetCellH,
                    maxColumns = maxColumns,
                    maxRows = maxRows
                )

                val isConfigOptional = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val features = info.widgetFeatures
                    (features and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0 &&
                        (features and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0
                } else {
                    false
                }

                WidgetProviderCatalogItem(
                    provider = provider,
                    packageName = pkg,
                    providerClassName = cls,
                    appLabel = appLabel,
                    widgetLabel = widgetLabel,
                    description = description,
                    defaultSpanX = spanX,
                    defaultSpanY = spanY,
                    minWidthDp = minWidthDp,
                    minHeightDp = minHeightDp,
                    resizeMode = info.resizeMode,
                    hasConfigActivity = info.configure != null,
                    isConfigurationOptional = isConfigOptional,
                    providerInfo = info
                )
            }.getOrNull()
        }.sortedWith(
            compareBy<WidgetProviderCatalogItem>(
                { it.appLabel.lowercase() },
                { it.widgetLabel.lowercase() }
            )
        )
    }

    /**
     * [AppWidgetHostView] を生成し、指定サイズで初期化する。
     */
    fun createHostView(
        context: Context,
        appWidgetId: Int,
        providerInfo: AppWidgetProviderInfo,
        widthDp: Int,
        heightDp: Int
    ): AppWidgetHostView {
        val view = appWidgetHost.createView(context.applicationContext, appWidgetId, providerInfo)
        view.setAppWidget(appWidgetId, providerInfo)
        if (appWidgetId > 0 && view is LauncherAppWidgetHostView) {
            activeHostViews[appWidgetId] = view
        }
        updateHostViewSize(view, widthDp, heightDp)
        return view
    }

    /**
     * ウィジェットの表示サイズ（dp）を [AppWidgetHostView] に通知する。
     * 高密度スケール（[WIDGET_CONTENT_SCALE]）を考慮した仮想dpサイズを通知することで、
     * Google カレンダー等のレスポンシブ Widget がより情報量の多いレイアウトを選択するようにする。
     */
    fun updateHostViewSize(
        hostView: AppWidgetHostView,
        widthDp: Int,
        heightDp: Int
    ) {
        if (hostView.appWidgetId > 0 && hostView is LauncherAppWidgetHostView) {
            activeHostViews[hostView.appWidgetId] = hostView
        }
        val scale = (hostView as? LauncherAppWidgetHostView)?.contentScale ?: WIDGET_CONTENT_SCALE
        val virtualW = kotlin.math.round(widthDp.coerceAtLeast(40) / scale).toInt()
        val virtualH = kotlin.math.round(heightDp.coerceAtLeast(40) / scale).toInt()
        runCatching {
            hostView.setPadding(0, 0, 0, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                hostView.updateAppWidgetSize(
                    Bundle(),
                    listOf(SizeF(virtualW.toFloat(), virtualH.toFloat()))
                )
            } else {
                @Suppress("DEPRECATION")
                hostView.updateAppWidgetSize(
                    Bundle(),
                    virtualW,
                    virtualH,
                    virtualW,
                    virtualH
                )
            }
            hostView.requestLayout()
        }
    }

    companion object {
        const val HOST_ID = 1024

        /**
         * ウィジェット内部の表示スケール（0.85f = 同一グリッド枠内に約1.38倍の情報面積を表示）
         */
        const val WIDGET_CONTENT_SCALE = 0.85f

        internal fun normalizeDimensionToDp(rawValue: Int, density: Float): Int {
            if (rawValue <= 0) return 70
            // AppWidgetProviderInfo.minWidth はリソースからピクセル変換されて格納されているため density で割る
            val converted = (rawValue / density).toInt()
            return if (converted >= 24) converted else rawValue
        }

        /**
         * ウィジェットの最小dpサイズまたは targetCellWidth/Height から推奨グリッドセル数 (spanX, spanY) を算出する。
         */
        fun calculateDefaultSpan(
            minWidthDp: Int,
            minHeightDp: Int,
            targetCellWidth: Int = 0,
            targetCellHeight: Int = 0,
            maxColumns: Int = 5,
            maxRows: Int = 6
        ): Pair<Int, Int> {
            val safeMaxCols = maxColumns.coerceAtLeast(1)
            val safeMaxRows = maxRows.coerceAtLeast(1)

            val rawSpanX = if (targetCellWidth > 0) {
                targetCellWidth
            } else {
                dpToCells(minWidthDp)
            }

            val rawSpanY = if (targetCellHeight > 0) {
                targetCellHeight
            } else {
                dpToCells(minHeightDp)
            }

            return rawSpanX.coerceIn(1, safeMaxCols) to rawSpanY.coerceIn(1, safeMaxRows)
        }

        private fun dpToCells(dp: Int): Int {
            if (dp <= 0) return 1
            // Android 公式計算式: (dp + 30) / 70 相当
            return ceil((dp + 30).toDouble() / 70.0).toInt().coerceAtLeast(1)
        }
    }
}
