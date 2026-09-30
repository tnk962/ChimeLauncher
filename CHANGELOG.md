# Changelog

本プロジェクト（My Launcher）の変更履歴を記録します。

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
