# My Launcher (v0.6.0)

Galaxy Z Fold系およびPixel系を主要ターゲットとした、自分専用の軽量・高速 Android Launcher です。
詳細な仕様は [SPECIFICATION.md](file:///Users/yohei/projects/TodoActions/AndroidLauncher/SPECIFICATION.md) を参照してください。

---

## 主な特徴

1. **Google検索バーの完全排除 & Swipe Up Search**
   - ホーム画面上に固定の検索バーは一切配置しません。
   - ホーム画面の空白部分を **上スワイプ** すると即座に `SearchOverlay` が開き、ソフトキーボードが自動起動します。
   - `1. インストール済みアプリ` → `2. ショートカット` → `3. Launcher Action` → `4. Google Web検索` の優先順で高速検索できます。
2. **All Apps / Tiny Icons ページ (`Page -1`)**
   - HOME の1つ左のページに、インストール済みアプリを小型アイコンで高密度に一覧表示します。
   - 列数・アイコンサイズ・アプリ名ラベルの ON/OFF をヘッダーメニューまたは設定画面から即座に切り替え可能です。
3. **Adaptive Fold UI (`Bottom Dock` ↔ `Right Dock` & 左右2ページ見開き)**
   - `WindowWidthSizeClass` に基づき、Fold を閉じた状態（Compact）では **下部 Dock + 1ページ表示**、Fold を開いた状態（Expanded）では **右端 Dock + 左右2ページ見開き表示 (`DUAL_PAGE`)**（Discover と 設定ページは1ページ全画面固定）または **1ページ全画面表示 (`SINGLE_FULL`)** に自動適応します。
   - アイコン・ウィジェット配置は `compactPosition` と `expandedPosition` を保持でき、未設定時は自動変換されます。
4. **ホーム画面 AppWidget 対応（配置・リサイズ・ドラッグ移動・再バインド）**
   - `AppWidgetHost` (`WidgetHostManager`) により、Google カレンダーや時計・天気など任意の Android ウィジェットを HOME および追加ページへ自由配置できます。
   - マルチセル（`spanX × spanY`）占有計算、ドラッグ移動、`WidgetResizeDialog` によるサイズ変更、バックアップ復元後のワンタップ再バインドに対応しています。
5. **誤操作防止（編集モード分離 & レイアウトロック）**
   - 通常時はドラッグ移動を無効化し、編集モードでのみ移動・リサイズ・削除・ページ追加を行えます。
   - **レイアウトロック** ON 時は一切の変更操作をブロックし、`🔒 ホーム画面はロックされています [キャンセル] [ロック解除]` ダイアログを表示します。
6. **下スワイプで通知シェード展開**
   - ホーム画面中央から **下スワイプ** すると、`StatusBarManager` リフレクションまたは `NotificationShadeService` (`AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS`) を介して Android 標準の通知シェードを開きます。
7. **JSON / Nova バックアップ・復元 & 未インストール Placeholder**
   - Room + DataStore に永続化されたレイアウト・ウィジェット・Dock・設定を `kotlinx.serialization` で JSON へシリアライズし、アプリ内スナップショット保存または外部 `.json` / `.novabackup` ファイルからインポートできます。
   - アンインストールされたアプリ・ウィジェットは自動削除せず、`? / 名前 / 未インストール` の **Placeholder** として位置とサイズを保持します。

---

## プロジェクト構成 (`:app` Single Module + 明確なInterface境界)

```text
app/src/main/java/com/myenvironment/launcher/
├── MainActivity.kt                  # CATEGORY_HOME, singleTask, AppWidgetHost lifecycle & Configure result
├── LauncherApplication.kt           # AppContainer (軽量DIコンテナ)
├── accessibility/
│   └── NotificationShadeService.kt  # GLOBAL_ACTION_NOTIFICATIONS による通知シェード展開
├── core/
│   ├── model/                       # AppInfo, LauncherPage, LayoutItem (WIDGET対応), DockItem, LauncherSettings, BackupPayload
│   ├── launcher/                    # [Interface] AppDiscoveryRepository, AppLauncher & 実装
│   ├── widget/                      # WidgetHostManager, LauncherAppWidgetHost, LauncherAppWidgetHostView
│   ├── storage/                     # [Interface] LayoutRepository, SettingsRepository & Room (v2) / DataStore実装
│   ├── search/                      # [Interface] SearchEngine & DefaultSearchEngine
│   ├── backup/                      # [Interface] BackupManager & JsonBackupManager, NovaBackupConverter
│   └── feed/                        # [Interface] FeedBridge & DefaultFeedBridge (6ジャンル RSS/Atom/OGP)
└── ui/
    ├── LauncherScreen.kt            # HorizontalPager (Single / DualSpread) + Adaptive Dock + Overlays
    ├── LauncherViewModel.kt         # UDF 状態管理 & AppWidget バインド・リサイズ制御
    ├── adaptive/                    # AdaptiveLayoutSpec (Compact / Expanded 判定)
    ├── components/                  # AppIconView (Placeholder対応), GestureContainer
    ├── discover/                    # Page -2: DiscoverPage (左端スワイプでGoogle App起動対応)
    ├── allapps/                     # Page -1: AllAppsTinyPage
    ├── home/                        # Page 0..N: HomeGridPage, WidgetItemView, MissingAppDialog
    ├── dock/                        # AdaptiveDock (Bottom / Right)
    ├── search/                      # SearchOverlay
    ├── editor/                      # HomeEditSheet, LockedAlertDialog, ItemPickerDialog, WidgetResizeDialog, PageManagerDialog
    └── settings/                    # SettingsScreen & Backup/Restore UI
```

---

## ビルドと実行方法

1. Android Studio で本プロジェクトディレクトリ (`/Users/yohei/projects/TodoActions/AndroidLauncher`) を開きます。
2. Gradle Sync を実行し、実機（Galaxy Z Fold / Pixel）または Foldable エミュレータへ `app` モジュールを実行します。
3. ホーム画面の空白部分を長押し → **「Launcher設定 & バックアップ」** から：
   - **「デフォルトのホームアプリを選択」** で `My Launcher` を標準ホームに設定します。
   - 下スワイプ通知を利用する場合は **「Accessibility権限設定を開く」** から `My Launcher 通知シェード操作` を ON にします。
