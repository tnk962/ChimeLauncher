package com.myenvironment.chimediscoverbridge

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.myenvironment.discoverprotocol.DiscoverContract

class CompanionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        layout.addView(TextView(this).apply {
            textSize = 22f
            text = "Chime Discover Companion"
        })
        layout.addView(TextView(this).apply {
            textSize = 16f
            val signed = packageManager.checkSignatures(packageName, DiscoverContract.LAUNCHER_PACKAGE) == PackageManager.SIGNATURE_MATCH
            val google = runCatching { packageManager.getApplicationInfo(DiscoverContract.GOOGLE_PACKAGE, 0).enabled }.getOrDefault(false)
            text = "\nLauncher: ${if (signed) "署名一致" else "未導入または署名不一致"}\nGoogle App: ${if (google) "有効" else "未導入または無効"}\n\nChime Launcherの設定で「独自フィード + Google Discover」を選び、Home → 全アプリ → 独自フィードと左へ進んだあと、さらに右方向へ指を動かしてください。\n\nGoogle Appの画面が連続して現れます。Google App更新によって利用できなくなる場合があります。\n\nこのプレビューはGoogleとの接続互換性のためdebuggableです。同じリリースの2つのAPKを組み合わせてください。"
        })
        layout.addView(Button(this).apply {
            text = "Chime Launcherを開く"
            setOnClickListener {
                packageManager.getLaunchIntentForPackage(DiscoverContract.LAUNCHER_PACKAGE)?.let { startActivity(it) }
            }
        })
        layout.addView(Button(this).apply {
            text = "標準のホームを選ぶ"
            setOnClickListener { startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS)) }
        })
        setContentView(layout)
    }
}
