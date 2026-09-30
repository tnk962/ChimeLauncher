package com.myenvironment.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.myenvironment.launcher.ui.LauncherScreen
import com.myenvironment.launcher.ui.LauncherViewModel
import com.myenvironment.launcher.ui.theme.MyLauncherTheme

/**
 * My Launcher メインActivity (仕様 1.1, 4)
 *
 * - CATEGORY_HOME / CATEGORY_DEFAULT に対応
 * - singleTask で常駐し、Homeジェスチャー (onNewIntent) 発生時は
 *   Activityを再生成せず既存PagerをHOMEページ位置へ戻す。
 */
class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels {
        val app = application as LauncherApplication
        LauncherViewModel.provideFactory(app.container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MyLauncherTheme {
                LauncherScreen(viewModel = viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.widgetHostManager.startListening()
    }

    override fun onStop() {
        super.onStop()
        viewModel.widgetHostManager.stopListening()
    }

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
            viewModel.onHomeGestureInvoked()
        }
    }

    companion object {
        const val REQUEST_CODE_CONFIGURE_APPWIDGET = 2048
    }
}
