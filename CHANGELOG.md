# Changelog

本プロジェクト（**Chime Launcher**）の変更履歴を記録します。

---

## [1.1.0] - 2026-09-30 (Build 11) — アプリ内自動アップデート & GitHub Actions CI 修正

### Added & Fixed
- **アプリ内新バージョン自動検知 & 1タップ自己アップデート (`AppUpdateManager` / `SettingsScreen`)**:
  - GitHub Releases API (`/repos/tnk962/ChimeLauncher/releases/latest`) と連携し、ホーム復帰時にバックグラウンド（3時間ごとスロットリング・通知なし）および設定画面の「アップデートを確認」ボタンで最新リリースを検知。
  - 新しいバージョンが公開されている場合、`Chime Launcher 設定` の最上部および最下部の「バージョン・ビルド情報 & アップデート」セクションに新バージョン名・リリースノート・ファイルサイズを表示。
  - **「ダウンロードしてアップデート」** ボタン1つで最新APKを進捗表示付きでキャッシュ取得し、`FileProvider` 経由で Android 標準のパッケージインストーラーを直接起動して上書きアップデート可能に（初回のみ「不明なアプリのインストール」許可画面への誘導ボタンも完備）。
- **GitHub Actions ワークフロー修正 & 署名キー統一**:
  - `gradle.properties` に残っていたローカル macOS 固有パス（`org.gradle.java.home=/opt/homebrew/...` 等）を削除し、GitHub Actions (`ubuntu-latest`) 上でのビルドエラーを解消。
  - ローカルビルドと GitHub Actions ビルドの双方で同一の署名証明書 (`app/keystore/chime-signing.keystore`) を使用するよう統一し、`v1.0.0` をインストール済みの端末でもアンインストール不要でそのまま上書きアップデートできるよう対応。

---

## [1.0.0] - 2026-09-30 (Build 10) — Chime Launcher 正式リリース

### Added & Changed
- **正式ブランド名称統一 (`Chime Launcher`)**:
  - アプリ名称・設定画面ヘッダー・アクセシビリティ表示名・バックアップファイル名（`chime_launcher_backup.json`）をすべて正式名称 **Chime Launcher** に統一。
  - ブランドコンセプト **"Chimeは通知しない。気づかせる。 (Chime Moments are ambient, not interruptive.)"** に基づくアンビエントUXを確立。
- **新アプリアイコン & Adaptive Icon 対応**:
  - 採用デザイン（深いブルーの空・水平な地平線と中央の朝焼けの光・Discover/Apps/Homeを象徴する中央が最も高い3枚の青〜シアン〜ミント系パネル・周囲を巡る軌道と暖色発光ドット）を正式アイコンとして組み込み。
  - Android 8.0+ Adaptive Icon (`mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml`, `ic_launcher_background.xml`, `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`)、全解像度ラスタPNG (`mipmap-mdpi`〜`xxxhdpi`)、および Google Play 用 512×512 PNG (`app/src/main/ic_launcher-playstore.png`) を完備。
- **Chime Moments (`First Chime` / `Return Chime` / `Time Chime`)**:
  - 通知やポップアップで操作を邪魔せず、ホーム画面下部の **ページインジケーターのみ** で節目を静かに表現する `ChimeController` を実装（優先順位：`First Chime > Return Chime > Time Chime`、同タイミング時は `First Chime` のみ再生）。
  - **First Chime**: その日 (`yyyy-MM-dd`) 初めてホーム画面を表示した際に1度だけ、現在位置のインジケーターから静かな二重の波紋 (`• ((●)) •`, 約1650ms) を広げてフェードアウト。
  - **Return Chime**: 一定時間以上（初期値 `1時間`、設定で `30分 / 1時間 / 3時間 / 6時間` に変更可能）離れてからホーム画面へ戻った際、インジケーター間の細い接続ラインとドット間隔の穏やかな呼吸 (`• ───── ● ───── •`, 約1750ms) で経過時間を表現。
  - **Time Chime**: 朝 (`05:00–10:59`)・昼 (`11:00–16:59`)・夕方 (`17:00–19:59`)・夜 (`20:00–23:59`)・深夜 (`00:00–04:59`) の時間帯に応じて、ページインジケーターのアクティブ色を微細に変化。
  - Android システムのアニメーション無効化 / 視差効果を減らす (`ANIMATOR_DURATION_SCALE == 0f`) 設定時は、拡大アニメーションを省略し短時間の明度変化へ自動フォールバック。
- **ページインジケーター 3スタイル切替 (`Dots` / `Icons` / `Text`)**:
  - `Chime Launcher 設定` → `表示 (Indicator Style)` から **Dots**（標準デフォルト：`•  •  ●  •  •`）、**Icons**（`Discover` / `All Apps` / `Home` / `Settings` アイコン）、**Text**（`Discover` / `Apps` / `1` / `2` / `Settings`）をいつでも切替可能に。高さ固定 (`30.dp`) によりモード切替やChime再生時も周囲レイアウトが一切ズレない構造を実現。
- **アプリ検索の Zero Query State 強化 & 利用頻度順ランキング**:
  - 検索オーバーレイを開いた直後（文字未入力時）に **Recently Used（最近使ったアプリ）**、**Frequently Used（よく使うアプリ）**、**Recently Installed（最近インストールしたアプリ）** の最大3セクション（1行4アプリアイコン、空セクションは自動非表示）を表示。
  - `UsageStatsManager`（権限未付与時はランチャー内起動履歴へ自動フォールバック）＋ `PackageInfo.firstInstallTime` を統合した `AppUsageRepository` を新設し、文字入力時の検索結果も「1. 完全一致 → 2. 前方一致 → 3. 部分一致 → 4. 利用頻度ボーナス」でソート。

---

## [0.9.0] - 2026-09-30 (Build 9)

### Added & Changed
- **別端末（Galaxy等）からのバックアップ復元時に発生する未インストールアプリの「違い診断」＆「しつこく検索・解決」機能 (`MissingAppResolver` / `MissingAppDialog`)**:
  - **なぜストアで直接ヒットしないかの原因診断表示**:
    - `GALAXY_STORE_EDITION`: Galaxy Store 版パッケージ（例: Galaxy 版 Kindle `com.amazon.kindleForSamsung` など、末尾に `ForSamsung` / `.samsung` 等が付く別パッケージID）
    - `SAMSUNG_SYSTEM_OR_EXCLUSIVE`: Galaxy (Samsung) 固有・標準アプリ（`com.sec.android.app.*` / `com.samsung.android.*`）
    - `CARRIER_CUSTOM`: ドコモ・au・ソフトバンク等のキャリア固有パッケージ
    - `GENERAL_APP`: 一般アプリ（地域限定・提供終了・パッケージID変更の可能性）
  - **Google Play 正規パッケージIDへの自動変換オープン**:
    - `com.amazon.kindleForSamsung` → `com.amazon.kindle` などの既知マッピングおよび `ForSamsung` / `.samsung` サフィックス除去ヒューリスティックにより、Google Play ストア上の正規パッケージID詳細ページを直接開けるボタンを追加。
  - **キーワード＆パッケージ単語による「しつこいPlayストア検索・Web検索」**:
    - パッケージID直接指定（`market://details?id=...`）でヒットしない場合でも、アプリ名やパッケージIDから抽出した検索キーワード候補チップ（編集可能）を使って **Playストア内キーワード検索 (`market://search?q=...&c=apps`)** および **Web (Google) 検索** をワンタップで実行可能に。
  - **Pixel（現端末）内のインストール済み同名・代替アプリとの自動照合＆1タップ／一括置き換え**:
    - Galaxy版 Kindle (`com.amazon.kindleForSamsung`) に対する Pixel 内の `Kindle (com.amazon.kindle)` や、Galaxy 標準カメラ・時計・電卓・ギャラリー等に対する Pixel 標準アプリ、または同名アプリが既に Pixel に入っている場合、ダイアログ上で **1タップでその位置のアイコンを端末内アプリに置き換え** 可能に。
    - さらに `My Launcher 設定` に **「未インストール枠を端末内アプリ(Kindle・標準等)と一括紐付け」** ボタンを追加し、ホーム画面・Dock上の該当プレースホルダーを一括でPixel内アプリへ自動変換できるよう対応。

---

## [0.8.0] - 2026-09-30 (Build 8)

### Fixed & Changed
- **スクロール可能なウィジェット（Google Keep等）での上下スクロール時に通知シェード・検索オーバーレイが誤発火しないよう改善**:
  - `LauncherAppWidgetHostView.hasVerticalScrollableContent()` を追加し、ウィジェット内部に縦スクロール可能なビュー（`AbsListView` / `ListView` / `GridView` / `ScrollView` / `AdapterViewAnimator` / `RecyclerView` 等）が含まれているかを自動判定。
  - **スクロール可能なウィジェット（Keepのメモ一覧、カレンダー予定リスト、Gmail等）**: All Apps ページ等と同様に、ウィジェット上での上下スワイプではランチャーの通知シェードや検索オーバーレイを一切発火させず、ウィジェット自身の縦スクロールのみが滑らかに反応するよう制御（`shouldIgnoreTouchAt` ＋ `requestDisallowInterceptTouchEvent(true)`）。また、`ListView` スクロール中に長押しメニューが誤発火しないよう `dispatchTouchEvent` で移動検知時に長押しタイマーを確実にキャンセル。
  - **スクロールしないウィジェット（時計など）・アプリアイコン・空白領域**: 従来通り上下スワイプで検索オーバーレイ／通知シェードが即座に反応。

---

## [0.7.0] - 2026-09-30 (Build 7)

### Added & Changed
- **ページを跨いだドラッグ＆ドロップ移動（端ホバーでの自動ページ遷移・見開き間ドラッグ・新規ページ作成）**:
  - **画面左右端ホバーでの自動ページ遷移**: ウィジェットまたはアプリアイコンをドラッグしたまま画面の左端／右端（端から `60dp` 以内）に **約0.65秒** 滞在すると、隣り合うホームページへ自動スクロール遷移し、そのまま任意のページ・セルへドロップ配置可能に。
  - **右端ホバーでの新規ページ自動作成**: 一番右のホームページでさらに右端にドラッグ保持すると、新しいユーザーページ（`Page N`）を自動作成して遷移し、そのまま配置可能。
  - **Foldable 見開き（Dual-Page）間のダイレクトドラッグ**: 折りたたみ端末を開いた2ページ見開き表示時は、ページスクロールなしで左ページ⇔右ページ間を直接ドラッグ＆ドロップで移動可能。
  - **長押しメニューからのページ移動**: ドラッグ操作だけでなく、ウィジェット／アイコンの長押しメニューにも「📄 別のページへ移動」および「➕ 新しいページを作成して移動」を追加。
  - **ドラッグ中のリアルタイムドロップ枠ハイライト＆フローティングプレビュー**: ページ遷移中も指に追従するフローティングプレビューと、移動先セルのハイライト枠をリアルタイム表示。
- **ウィジェットの「グリッドのキワキワまで」拡大表示＆高情報密度化**:
  - **内部余白・外周マージンの徹底排除**: `LauncherAppWidgetHostView` で Android 標準の `AppWidgetHostView` が自動付与するデフォルト余白（8dp四方）を `setPadding(0, 0, 0, 0)`・`clipToPadding = false`・`clipChildren = false` により完全撤廃。さらに `HomeGridPage` のグリッド外周余白を `2.dp`、ウィジェットセル間隔を `1.dp`（角丸 `6.dp`）まで極小化し、グリッドの境界キワキワまでウィジェット領域を拡大。
  - **仮想キャンバス拡大スケーリング（高情報密度化）**: `WIDGET_CONTENT_SCALE = 0.85f` により、ウィジェット内部の描画キャンバスを実セルサイズの約 **1.18倍（面積比 約1.38倍）** で測定・レイアウトしたうえでセル枠キワキワにフィット表示。さらに `updateAppWidgetSize` にも拡大後の仮想 dp サイズを通知することで、Google カレンダー等のレスポンシブウィジェットがより詳細で情報量の多いレイアウトを選択して多くの予定・項目を表示できるよう改善。

---

## [0.6.0] - 2026-09-30 (Build 6)

### Added
- **ホーム画面への Android AppWidget 配置・リサイズ・再バインド・永続化対応**:
  - `WidgetHostManager` (`LauncherAppWidgetHost` / `LauncherAppWidgetHostView`, `HOST_ID = 1024`) を新設し、Android 標準の `AppWidgetManager` と連携したフル機能のウィジェットホストを実装。
  - **Widget ピッカー統合**: ホーム画面空白長押しシート（`＋ ウィジェットを追加`）、編集モード上部バナー（`+ Widget`）、および空きセルタップ時の `ItemPickerDialog` に **「Widget」タブ** を追加。端末内の全 AppWidget プロバイダーをアプリ名・ウィジェット名でインクリメンタル検索し、推奨セルサイズ（`spanX×spanY`）とリサイズ可否バッジを確認して配置可能。
  - **権限バインド & Configuration Activity 対応**: `bindAppWidgetIdIfAllowed` によるサイレントバインド試行と、システム権限確認ダイアログ（`ACTION_APPWIDGET_BIND`）、およびウィジェット固有の初期設定画面（`startAppWidgetConfigureActivityForResult`）の起動に対応。
  - **マルチセル（`spanX × spanY`）グリッド占有とドラッグ移動**: `HomeGridPage` にて複数セルを跨ぐウィジェットの正確な矩形描画・セル占有判定・ドラッグ移動・空き領域探索（`findBestGridPlacementForSpan`）を実装。
  - **ウィジェットサイズ変更 (`WidgetResizeDialog`)**: 編集モードまたは長押しメニューの「📐 サイズを変更」から、列幅 (`spanX`)・行高 (`spanY`) をステッパーおよびプリセットボタンで自在にリサイズし、`updateAppWidgetSize` で即座に再レイアウト。
  - **バックアップ・復元と未バインド／未インストール Placeholder**: Room DB スキーマを `v2` へマイグレーション（`MIGRATION_1_2` で既存データを保持したまま `appWidgetId` カラムを追加）。JSON バックアップ復元時など `appWidgetId` が未バインド状態になった場合も、サイレント自動再バインドまたはワンタップの「タップしてウィジェットを有効化」カードから同じ位置・サイズで即座に復旧可能。アプリ未インストール時は専用 Placeholder を表示。

---

## [0.5.0] - 2026-09-30 (Build 5)

### Added
- **Discover ページ左端（行き止まり）への追加スワイプで Google アプリを起動する固定動作**:
  - 左端の `Discover` ページ表示中に、さらに左端の行き止まり方向（左→右ドラッグ）へスワイプすると、固定の動作として Google アプリ（Google Discover）が自動起動するよう対応（下部のジャンル選択チップバーの横スクロールや記事の縦スクロールとは干渉しないよう制御）。

---

## [0.4.0] - 2026-09-30 (Build 4)

### Changed
- **Foldable 展開時（左右2ページ見開き表示モード）の Discover ＆ 設定ページ全画面固定化**:
  - 折りたたみ端末を開いた状態で「左右2ページ見開き表示 (`DUAL_PAGE`)」を選択している際、`All Apps` や `HOME`・追加ページは従来通り左右2ページ見開きで表示しつつ、**左端の `Discover` ページ** と **右端の `My Launcher 設定` ページ** は常に **1ページ全画面表示固定** になるよう変更（`ExpandedPagerSlot.SingleFull` / `DualSpread`）。

---

## [0.3.0] - 2026-09-30 (Build 3)

### Added & Changed
- **Discover フィードから Googleニュース（新聞社ヘッドライン）を排除し、興味関心特化の6ジャンルへ拡充**:
  - 一般紙の政治・事件ヘッドライン（`news.google.com`）を廃止し、主にチェックしたい **AI・OpenAI（XenoSpectrum等）** や **リゼロ（Re:ゼロから始める異世界生活）・アニメ・ラノベ**、および **はてなブックマーク** の記事を直接収集・表示する6ジャンル構成に刷新：
    1. **おすすめ (AI・リゼロ)**: `xenospectrum.com`、はてブ「リゼロ / Re:ゼロ / 異世界 / アニメ」、はてブ「OpenAI / ChatGPT / Claude / Gemini / 生成AI」、`ITmedia AI+`、`アニメ！アニメ！`、`コミックナタリー`、`GIGAZINE` を横断統合
    2. **AI・OpenAI**: `xenospectrum.com`、はてブ「OpenAI / ChatGPT / LLM / 生成AI」新着、`ITmedia AI+`、`Zenn (OpenAI / AI トピック)`、`GIGAZINE`
    3. **リゼロ・アニメ**: はてブ「リゼロ / Re:ゼロ / 長月達平」人気・新着、`アニメ！アニメ！`、`コミックナタリー`、はてブ「アニメとゲーム」人気、`ねとらぼ`、`4Gamer.net`
    4. **はてブ 総合**: はてなブックマーク 総合ホットエントリー＆新着エントリー RSS
    5. **はてブ IT**: はてなブックマーク テクノロジー（IT）ホットエントリー＆新着エントリー RSS
    6. **ビジネス・政治**: はてなブックマーク 政治と経済・社会 ホットエントリー＆新着エントリー RSS
- **Atom / RDF / RSS 2.0 マルチフォーマット解析とメディア名表示の強化**:
  - `DefaultFeedBridge` にて Atom フィード（`Zenn`、`コミックナタリー` 等の `<entry>` / `<updated>` / `<content>` / `rel="enclosure"`）の解析を強化し、`xenospectrum.com`・`アニメ！アニメ！`・`コミックナタリー`・`ITmedia`・`Zenn`・`GIGAZINE` などの配信元名が記事タイトルを削らず正確に表示されるよう改善。
- **Google App 起動処理の改善**:
  - `openGoogleDiscoverApp()` のフォールバック先から `news.google.com` を除外し、Google アプリ本体（`com.google.android.googlequicksearchbox`）を優先起動するよう修正。

---

## [0.2.0] - 2026-09-30 (Build 2)

### Added
- **バージョン・ビルド情報表示**:
  - `My Launcher 設定` ページの最上部（タイトル直下）および最下部の「バージョン・ビルド情報」セクションに、現在のアプリバージョン（`v0.2.0`）、`versionCode`（`2`）、およびビルド日時（`BUILD_TIMESTAMP`）を表示。端末上でAPK更新が反映されたか一目で確認可能に改善。
- **Discover ページの4ジャンル刷新（Google Discover ＆ はてなブックマーク統合）**:
  - 旧4ジャンル（おすすめ・テクノロジー・ビジネス・サイエンス）を廃止し、以下の新4ジャンル構成へ刷新：
    1. **Google Discover**: Google News トップおすすめ記事（高解像度サムネイル・OGP本文要約付き）
    2. **はてブ 総合**: はてなブックマーク 総合ホットエントリー＆新着エントリー RSS（サムネイル・ブクマ数・要約付き）
    3. **はてブ テクノロジー**: はてなブックマーク テクノロジー（IT）ホットエントリー＆新着エントリー RSS
    4. **ビジネス・政治**: はてなブックマーク 政治と経済・社会 RSS ＋ ビジネス・国内政治ニュース RSS
- **Discover ヘッダーに「はてブ」起動ボタンを追加**:
  - 上部ヘッダーの `Google App` ボタンの隣に `はてブ` ボタンを配置し、公式はてなブックマークアプリ（未インストール時はWeb版）をワンタップで起動可能に。

### Fixed & Changed
- **Discover キャッシュの完全クリア機能**:
  - `FeedBridge.clearCache()` を追加し、Discoverページ初回表示時およびヘッダーの更新ボタン押下時にメモリ内の画像・OGP要約・記事リストキャッシュをすべてクリアして最新記事を取得するよう改善。
- **モバイル User-Agent での Google News 記事抽出修正**:
  - Android 端末からのアクセス時に返却されるモバイル版 Google News HTML 構造（`data-n-tid="29"`, `DY5T1d`, `wEwyrc`, `OGnjD`）に対応し、実記事URL・高解像度サムネイル画像が確実に抽出されるよう修正。

---

## [0.1.0] - 2026-09-30 (Build 1)

### Added
- **My Launcher MVP 初期構築**:
  - Google検索バーを排除した軽量ホーム画面（Jetpack Compose + Room + DataStore）。
  - 固定ページ構成（`Google Discover` / `All Apps (Tiny Icons)` / `HOME` / ユーザー追加ページ / 右端 `My Launcher 設定`）。
  - Foldable（Galaxy Z Fold等）の閉じた状態（Compact）と開いた状態（Expanded 2ペイン見開き・全画面切替）の最適化。
  - ホーム画面の誤操作防止レイアウトロック機能。
  - 中央下スワイプでの通知シェード展開（`StatusBarManager` リフレクション ＋ `AccessibilityService` 対応）および上スワイプでの Launcher 検索オーバーレイ起動。
  - JSON バックアップ・復元および Nova Launcher バックアップファイル（`.novabackup` / ZIP / SQLite / XML / CSV）のマルチフォーマットインポート対応。
