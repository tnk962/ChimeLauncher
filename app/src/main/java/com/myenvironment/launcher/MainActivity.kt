package com.myenvironment.launcher

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import com.myenvironment.launcher.core.feed.overlay.GoogleOverlayClient
import com.myenvironment.launcher.ui.discover.LocalGoogleOverlayClient
import com.myenvironment.launcher.ui.LauncherScreen
import com.myenvironment.launcher.ui.LauncherViewModel
import com.myenvironment.launcher.ui.theme.MyLauncherTheme

/**
 * Chime Launcher メインActivity (仕様 1.1, 4, 6〜10, 30)
 *
 * - CATEGORY_HOME / CATEGORY_DEFAULT に対応
 * - singleTask で常駐し、Homeジェスチャー (onNewIntent) 発生時は
 *   Activityを再生成せず既存PagerをHOMEページ位置へ戻す。
 * - フォアグラウンド復帰・離脱時に Chime Moments (First / Return / Time Chime) を評価する。
 */
class MainActivity : ComponentActivity() {
    private lateinit var googleOverlay: GoogleOverlayClient

    private val viewModel: LauncherViewModel by viewModels {
        val app = application as LauncherApplication
        LauncherViewModel.provideFactory(app.container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        googleOverlay = GoogleOverlayClient(this)

        setContent {
            MyLauncherTheme {
                CompositionLocalProvider(LocalGoogleOverlayClient provides googleOverlay) {
                    LauncherScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        googleOverlay.onStart()
        viewModel.widgetHostManager.startListening()
        viewModel.onLauncherResumed()
    }

    override fun onStop() {
        googleOverlay.onStop()
        super.onStop()
        viewModel.widgetHostManager.stopListening()
        viewModel.onLauncherPaused()
    }

    override fun onResume() { super.onResume(); googleOverlay.onResume() }
    override fun onPause() { googleOverlay.onPause(); super.onPause() }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); googleOverlay.onAttachedToWindow() }
    override fun onDetachedFromWindow() { googleOverlay.onDetachedFromWindow(); super.onDetachedFromWindow() }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        googleOverlay.onConfigurationChanged()
    }
    override fun onDestroy() { googleOverlay.onDestroy(); super.onDestroy() }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CONFIGURE_APPWIDGET) {
            viewModel.onWidgetConfigureActivityResult(resultCode == RESULT_OK)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // AndroidのHome操作（Home Gesture / Homeボタン）が発生した場合、必ずHOMEページを表示する (仕様 4)
        if (intent.action == Intent.ACTION_MAIN &&
            (intent.hasCategory(Intent.CATEGORY_HOME) || intent.hasCategory(Intent.CATEGORY_LAUNCHER))
        ) {
            googleOverlay.close()
            viewModel.onHomeGestureInvoked()
        }
    }

    companion object {
        const val REQUEST_CODE_CONFIGURE_APPWIDGET = 2048
    }
}
