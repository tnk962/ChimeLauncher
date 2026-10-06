package com.myenvironment.launcher

import android.app.KeyguardManager
import android.os.PowerManager
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
import com.myenvironment.launcher.ui.DiscoverReturnTarget
import com.myenvironment.launcher.core.model.LauncherPage
import com.myenvironment.launcher.ui.theme.MyLauncherTheme

/**
 * Chime Launcher メインActivity (仕様 1.1, 4, 6〜10, 30)
 *
 * - CATEGORY_HOME / CATEGORY_DEFAULT に対応
 * - singleTask で常駐し、Homeジェスチャー (onNewIntent) 発生時は
 *   Activityを再生成せず、記事・アプリからの復帰では元のページを維持し、それ以外はHOMEへ戻す。
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

    override fun onResume() {
        super.onResume()
        googleOverlay.onResume()
        restoreDiscoverIfPending()
        // onNewIntentが先に処理したHOME復帰、または通常のBack復帰の記録を消す。
        viewModel.clearAppReturn()
    }
    override fun onPause() {
        val unlocked = !getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val screenOn = getSystemService(PowerManager::class.java).isInteractive
        if (googleOverlay.isOpen && unlocked && screenOn) viewModel.rememberGoogleDiscoverDeparture()
        googleOverlay.onPause()
        super.onPause()
    }

    private fun restoreDiscoverIfPending(): Boolean {
        return when (viewModel.consumeDiscoverReturn()) {
            DiscoverReturnTarget.CUSTOM -> {
                googleOverlay.close()
                viewModel.jumpToPage(LauncherPage.PAGE_ID_DISCOVER)
                true
            }
            DiscoverReturnTarget.GOOGLE -> {
                googleOverlay.restoreOnResume()
                true
            }
            null -> false
        }
    }
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
        // AndroidのHome操作（Home Gesture / Homeボタン）が発生した場合、記事・アプリからの復帰では元ページを維持する
        if (intent.action == Intent.ACTION_MAIN &&
            (intent.hasCategory(Intent.CATEGORY_HOME) || intent.hasCategory(Intent.CATEGORY_LAUNCHER))
        ) {
            if (!restoreDiscoverIfPending()) {
                googleOverlay.close()
                viewModel.onHomeGestureInvoked()
            }
        }
    }

    companion object {
        const val REQUEST_CODE_CONFIGURE_APPWIDGET = 2048
    }
}
