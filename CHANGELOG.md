# Changelog

本プロジェクト（My Launcher）の変更履歴を記録します。

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
