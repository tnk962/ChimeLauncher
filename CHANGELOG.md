# Changelog

本プロジェクト（My Launcher）の変更履歴を記録します。

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
