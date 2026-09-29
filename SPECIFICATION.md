# 自作Android Launcher＋周辺アプリ 実装仕様書

**Document Version:** 0.1  
**作成日:** 2026-09-30  
**対象:** Androidスマートフォン／Foldable端末  
**主要ターゲット:** Galaxy Z Fold系、Pixel系  
**開発言語:** Kotlin  
**UI:** Jetpack Compose

---

# 1. プロジェクト概要

## 1.1 目的

既存のNova Launcherを置き換える、自分専用の軽量Android Launcherを開発する。

Nova Launcherの全機能を再現することは目的としない。

主目的は以下。

1. Galaxy / Pixel純正Launcherに固定表示されるGoogle検索バーを完全に排除する。
2. Google Discoverへのアクセス性を維持する。
3. インストール済みアプリをTiny Icons風に大量表示できる専用ページを持つ。
4. メインホーム画面を自由なグリッドとして編集できる。
5. Foldを閉じた場合と開いた場合でUIを最適化する。
6. 誤操作によるアイコン削除・移動を防止する。
7. レイアウトをバックアップ・復元できる。
8. 検索バーは置かず、ジェスチャーから強力な検索を呼び出す。
9. 将来的にはHatena Discover風フィードや通知履歴アプリと連携する。

Androidは `CATEGORY_HOME` を持つActivityをホームアプリとして扱えるため、本アプリを標準ホームアプリ候補として実装する。

---

# 2. システム全体構成

プロジェクト全体は以下の3アプリを基本単位とする。

```text
My Android Environment
│
├── My Launcher
│   ├── Google Discover
│   ├── All Apps / Tiny Icons
│   ├── Main Home
│   ├── Additional Home Pages
│   ├── Search
│   ├── Adaptive Dock
│   └── Backup / Restore
│
├── Hatena Feed
│   ├── 総合
│   ├── IT / Technology
│   ├── 記事閲覧
│   ├── はてブコメント
│   └── Wallabag連携
│
└── My Notifications
    ├── NotificationListener
    ├── 長期履歴
    ├── 検索
    └── アプリ別絞り込み
```

Launcherを最優先で開発する。

Hatena FeedとMy Notificationsは独立アプリとして開発し、Launcherから起動できるようにする。

---

# 3. Launcherの基本ページ構成

Launcherは横方向のページ構造を持つ。

```text
左                                                       右
←────────────────────────────────────────────────────────→

[ Google Discover ] [ All Apps ] [ HOME ] [ Page 2 ] [ Page 3 ] ...
                                  ↑
                             Default Page
```

## 3.1 固定ページ

以下の3ページはLauncherによって予約する。

### Page -2：Google Discover

Google Discover表示専用。

### Page -1：All Apps

Tiny Icons相当の全アプリ一覧。

### Page 0：HOME

メインホーム。

AndroidのHome操作を実行した際は必ずこのページを表示する。

---

## 3.2 ユーザーページ

HOMEより右側には任意のページを追加できる。

例：

```text
Discover
   ↓
All Apps
   ↓
HOME
   ↓
仕事
   ↓
ゲーム
   ↓
その他
```

ユーザー追加ページにはアプリまたはショートカットをグリッド配置できる。

WidgetはMVPでは配置対象としない。

---

# 4. HOME操作

本アプリを標準ホームアプリとして設定する。

AndroidのHomeジェスチャーまたはHome操作が発生した場合、

```text
任意のアプリ
      ↓
Home Gesture
      ↓
Launcher
      ↓
HOME Page
```

とする。

Launcher内で別ページを表示している場合も、

```text
All Apps
   ↓
Home
   ↓
HOME
```

に戻す。

Activityの再生成ではなく、可能な限り既存Launcher Activity内のPagerをHOME位置へ移動する。

---

# 5. システムジェスチャーとの役割分担

## 5.1 Android OSが担当する操作

画面最上部から下へスワイプした場合は、Galaxy / PixelそれぞれのSystem UIをそのまま使用する。

```text
画面上端
   ↓ swipe
通知
   ↓ further expand
Quick Settings
```

Launcher独自の通知UIやコントロールセンターは実装しない。

画面下端からのシステムHomeジェスチャーもAndroidに任せる。

Androidのジェスチャーナビゲーションでは、下端から上へのスワイプがHome操作としてOS側で処理される。

---

# 6. Launcher独自ジェスチャー

ホーム画面内部では以下のジェスチャーを実装する。

| 操作 | 動作 |
|---|---|
| 上端から下スワイプ | Android標準通知 |
| 空白部分を下スワイプ | 通知シェードを開く |
| 空白部分を上スワイプ | Launcher検索 |
| 左右スワイプ | ページ変更 |
| Home操作 | HOMEページへ戻る |
| 空白長押し | ホーム編集メニュー |
| アイコン長押し | アイコン編集 |
| アイコンタップ | アプリ／Action起動 |

---

# 7. 中央から下スワイプ → 通知

## 7.1 UX

ホーム画面の中央付近から下方向へスワイプした場合、

```text
HOME
 ↓ swipe

Android Notification Shade
```

を開く。

Galaxy / Nova等と同じ使い勝手を目指す。

Foldを開いた状態では特に重要な操作とする。

---

## 7.2 実装方針

通常アプリには通知シェードを直接操作する一般公開APIは用意されていないため、公開APIを利用する場合は `AccessibilityService` の

```text
GLOBAL_ACTION_NOTIFICATIONS
```

を利用する方法を第一候補とする。

AndroidのAccessibilityServiceには通知画面を開く `GLOBAL_ACTION_NOTIFICATIONS` が公式に定義されている。

### 注意

この機能にはAccessibility Serviceをユーザー自身が有効化する必要がある。

したがって、

```text
設定
└── ジェスチャー
    └── 下スワイプで通知を開く
        ├── ON
        └── Accessibility権限設定
```

というオンボーディングを用意する。

権限がない場合でもLauncher自体は正常動作させる。

Play Store一般公開を行う場合はAccessibility API利用ポリシーを別途確認する。

個人利用・サイドロードでは本方式を基本候補とする。

---

# 8. Swipe Up Search

Google検索バーはホーム画面には一切表示しない。

代わりに、

```text
HOME
  ↑
Swipe Up
  ↓

Search Overlay
```

を表示する。

---

## 8.1 検索開始時

検索画面表示直後に、

- TextFieldへFocus
- ソフトキーボード表示

を行う。

---

## 8.2 検索優先順位

検索結果は以下の順に表示する。

```text
1. インストール済みアプリ
2. Launcherショートカット
3. Launcher独自Action
4. Web検索
```

最重要用途は**端末内アプリ検索**。

例：

```text
🔍 cha

Apps
────────────────
ChatGPT
Chatwork
Chrome

Actions
────────────────
ChatGPT 新規チャット

Web
────────────────
Googleで「cha」を検索
```

Launcher用APIである `LauncherApps` から、現在ユーザーおよび表示可能なWork Profile等の起動可能Activityを取得できる。

---

# 9. Google検索

検索結果最下部に、

```text
Googleで「検索文字列」を検索
```

を表示する。

選択された場合はGoogleアプリまたはWebブラウザへIntentを発行する。

ホーム画面上に検索バーは常設しない。

**検索機能は残すが検索バーは排除する**ことが本Launcherの重要設計方針。

---

# 10. All Apps / Tiny Iconsページ

現在使用しているTiny Icons Widget相当の機能をLauncher内部に実装する。

## 10.1 目的

可能な限り多くのアプリを1ページ内に一覧表示する。

```text
┌────────────────────────┐
│ ● ● ● ● ● ● ● ● ● ● │
│ ● ● ● ● ● ● ● ● ● ● │
│ ● ● ● ● ● ● ● ● ● ● │
│ ● ● ● ● ● ● ● ● ● ● │
│ ● ● ● ● ● ● ● ● ● ● │
│ ● ● ● ● ● ● ● ● ● ● │
└────────────────────────┘
```

---

## 10.2 MVP仕様

- インストール済み起動可能アプリを取得
- 小型アイコンでGrid表示
- タップで起動
- 必要に応じて縦スクロール
- アプリ名表示ON/OFF
- アイコンサイズ変更
- 列数変更
- デフォルトはアルファベット／かな順

---

## 10.3 将来機能

後から以下を追加可能とする。

```text
使用頻度順
最近使った順
インストール日時順
非表示アプリ
カテゴリ分け
Work Profile表示
```

これらはMVP必須ではない。

---

# 11. HOME / ユーザーページのGrid

WidgetベースではなくLauncher自身のGridシステムを持つ。

例：

```text
┌────┬────┬────┬────┬────┐
│App │App │    │App │    │
├────┼────┼────┼────┼────┤
│    │App │App │    │App │
├────┼────┼────┼────┼────┤
│App │    │    │    │App │
└────┴────┴────┴────┴────┘
```

各Itemは、

```text
pageId
x
y
spanX
spanY
type
target
```

を持つ。

通常アプリアイコンは、

```text
spanX = 1
spanY = 1
```

とする。

---

# 12. 配置可能Item

MVPでは以下を扱う。

## APP

通常のアプリ。

```text
type = APP
packageName = com.example.app
activityName = ...
```

---

## SHORTCUT

Android ShortcutまたはDeep Link。

例：

```text
Keepの特定ノート
Wallabag
ChatGPT特定Action
```

---

## ACTION

Launcher独自Action。

例：

```text
Hatena Feed
通知履歴
Search
Settings
```

---

# 13. アイコン追加

空白部分長押し → 編集画面から追加する。

```text
空白長押し

ホーム画面を編集
────────────────
＋ アプリ
＋ ショートカット
＋ ページ
Dockを編集
レイアウトをロック
```

「アプリ」を選択すると検索可能なアプリ一覧を表示する。

---

# 14. 編集モード

誤操作を避けるため、通常操作と編集操作を明確に分離する。

## 通常モード

```text
タップ       → 起動
長押し       → コンテキストメニュー
ドラッグ     → 原則無効
```

---

## 編集モード

```text
アイコン移動
アイコン削除
追加
ページ追加
ページ削除
Dock編集
```

編集モードでのみドラッグ可能とする。

---

# 15. ホームレイアウトロック

誤操作対策として必須機能とする。

設定：

```text
ホーム画面
────────────────
☑ レイアウトをロック
```

ロック中は禁止する。

```text
アイコン移動
アイコン削除
ページ追加
ページ削除
Dock編集
```

変更操作を試みた場合、

```text
🔒 ホーム画面はロックされています

[キャンセル]
[ロック解除]
```

を表示する。

ポケット内での誤操作によるアイコン消失を防止することを主目的とする。

---

# 16. ページ管理

HOME右側にページを任意追加できる。

```text
Discover | All Apps | HOME | Page A | Page B | Page C
```

Discover / All Apps / HOMEは削除不可。

HOME右側のみ、

```text
追加
削除
並び替え
名前変更
```

可能。

---

# 17. Adaptive Dock

Fold開閉によってDock位置を変える。

## Compact / Fold Closed

```text
┌────────────────────┐
│                    │
│       HOME         │
│                    │
│                    │
├────────────────────┤
│ ●   ●   ●   ●   ●│
└────────────────────┘
       Bottom Dock
```

---

## Expanded / Fold Open

```text
┌──────────────────────────┬────┐
│                          │ ●  │
│                          │ ●  │
│          HOME            │ ●  │
│                          │ ●  │
│                          │ ●  │
└──────────────────────────┴────┘
                           Dock
```

---

# 18. Fold判定

Galaxy固有APIに依存しない。

基本判定は現在のWindow Widthを使用する。

Compose Material 3 Adaptive / Jetpack WindowManagerを利用し、

```text
Compact
Medium
Expanded
```

等のWindow Size Classに応じて切り替える。

Android公式でも折りたたみ・展開やマルチウィンドウによるWindowサイズ変更に応じてUIを適応させる方式が推奨されている。

基本ルール：

```text
Compact → Bottom Dock
Expanded → Right Dock
```

とする。

---

# 19. Dock内容

Dockはページとは独立して管理する。

例：

```text
ChatGPT
Chrome
Camera
Keep
Hatena Feed
```

アプリだけでなくShortcut / Actionも登録可能にする。

例：

```text
📚 読みたい
📰 Hatena
🔔 Notifications
🤖 ChatGPT
```

ページを変更してもDockは共通表示する。

他アプリ使用中には表示しない。

Samsung Edge PanelのようなOverlay機能は実装しない。

---

# 20. Fold開閉時のレイアウトデータ

ページ内容はCompact / Expandedで共有する。

ただしItem位置は必要に応じて個別保持可能な構造とする。

```text
LayoutItem
├── compactPosition
│   ├── x
│   └── y
│
└── expandedPosition
    ├── x
    └── y
```

Expanded位置未設定の場合、

```text
compactPosition
      ↓
自動変換
      ↓
expandedPosition
```

する。

これにより最初は1レイアウトだけ編集すれば利用でき、将来的にはFold開閉で別配置にもできる。

---

# 21. レイアウト保存

ホームレイアウトはDBに永続保存する。

推奨：

```text
Room
```

設定値については、

```text
DataStore
```

を利用する。

---

# 22. バックアップ

Launcher設定全体をJSONへシリアライズ可能にする。

例：

```json
{
  "schemaVersion": 1,
  "createdAt": "2026-09-30T00:00:00+09:00",
  "pages": [
    {
      "id": "home",
      "name": "HOME",
      "items": [
        {
          "type": "APP",
          "packageName": "com.openai.chatgpt",
          "label": "ChatGPT",
          "compact": {
            "x": 0,
            "y": 0
          },
          "expanded": {
            "x": 0,
            "y": 0
          }
        }
      ]
    }
  ],
  "dock": [],
  "settings": {
    "layoutLocked": true
  }
}
```

---

# 23. バックアップ機能

設定画面に以下を持つ。

```text
Backup & Restore
────────────────

現在のレイアウトを保存

バックアップ一覧

2026/09/30 00:41
2026/09/25 23:20
Fold Layout Test

ファイルへエクスポート
ファイルからインポート
```

---

# 24. アプリが存在しない場合

バックアップ復元時やアプリアンインストール時に、Itemを自動削除しない。

レイアウト位置を保持したまま、

```text
┌───────────┐
│     ?     │
│ Spotify   │
│未インストール│
└───────────┘
```

のPlaceholderを表示する。

---

# 25. Play Store連携

Placeholderをタップすると、

```text
このアプリはインストールされていません

[Playストアで開く]
[ホームから削除]
[キャンセル]
```

を表示する。

バックアップには必ず、

```text
packageName
label
```

を保存する。

Play Storeへの遷移にはPackage Nameを利用する。

Google PlayはPackage Nameを指定したStore Listing URLを公式にサポートしている。

例：

```text
https://play.google.com/store/apps/details?id=com.example.app
```

Play Storeアプリが利用可能ならPlay Storeで開き、なければBrowserへFallbackする。

---

# 26. アプリ再インストール時

`LauncherApps.Callback` 等でPackage変更を監視する。

PlaceholderのPackage Nameと、新しくインストールされたアプリが一致した場合、

```text
Placeholder
     ↓
App Installed
     ↓
自動的に通常アイコンへ復元
```

する。

ユーザーが再配置する必要はない。

`LauncherApps` はLauncher向けにPackage変更を監視するCallbackを提供している。

---

# 27. Google Discover

最左ページ。

```text
[Google Discover] [All Apps] [HOME]
```

Google DiscoverはLauncher本体と分離したBridge方式とする。

---

# 28. Discover実装方針

Google Discoverは一般アプリ向けの通常APIとして提供されている機能ではないため、MVP完成条件から外す。

参考実装として、

```text
Lawnchair
+
Lawnfeed
```

を調査する。

Lawnfeedは現在も「Google FeedをLawnchairへ追加する」ための独立したオープンソースプロジェクトとして公開されている。

構造：

```text
Launcher
   ↓
FeedBridge Interface
   ↓
GoogleFeedBridge
```

とする。

Google側仕様変更で壊れても、

```text
All Apps
HOME
Additional Pages
```

は正常動作し続けること。

---

# 29. Discover Fallback

Google Feed連携が使用できない場合、

```text
Discover Mode
────────────────

○ Native Bridge
○ Google Appを開く
○ 無効
```

を選択可能にする。

Google Appモードの場合、

左端ページへ移動しようとしたタイミングでGoogleアプリのDiscoverを起動する方式をFallbackとする。

---

# 30. Widget

MVPでは一般的なWidget自由配置は実装しない。

理由：

```text
AppWidgetHost
Widget Picker
Binding
Resize
Drag
Provider Configuration
```

等によりLauncher実装量が大きく増加するため。

AndroidではLauncherのようなアプリが `AppWidgetHost` を実装することで他アプリのWidgetを埋め込める。

将来的に必要性が高ければ、

```text
Google Calendar Widget
```

のみを最初のWidget対応候補とする。

**MVPでは必須ではない。**

---

# 31. Hatena Feedアプリ

Launcherとは独立したAndroidアプリとする。

名称仮：

```text
Hatena Discover
```

---

# 32. Hatena Feed UI

Google Discover風カードUI。

```text
[ 総合 ] [ IT ] [ AI ]

────────────────────────

AIエージェント時代の○○

example.com

🔥 386 users

[記事を読む] [コメントを見る]

────────────────────────

Androidの新機能について

example.jp

🔥 214 users

[記事を読む] [コメントを見る]
```

---

# 33. Hatena Feed操作

カード本体：

```text
元記事を開く
```

Bookmark Count / コメント：

```text
はてなブックマークコメントページ
```

追加Action：

```text
あとで読む
共有
Wallabagへ保存
既読
```

---

# 34. Hatenaデータ取得

データ取得レイヤーを抽象化する。

```text
HatenaFeedRepository
```

AndroidアプリからHatenaへ直接アクセスする実装と、

```text
Android
 ↓
Cloudflare Worker
 ↓
Hatena
```

の両方を差し替え可能にする。

はてなは公式Developer CenterでBookmark REST APIやEntry情報取得APIを提供している。

Hatena側仕様変更の影響をAndroidアプリから切り離すため、将来的にはCloudflare Worker経由を推奨。

---

# 35. Hatena Feed将来機能

```text
For You
```

モードを追加可能にする。

例：

```text
AI              +3
Android         +3
Cloudflare      +2
Apple           +2
ゲーム           +1
```

記事タイトル・カテゴリ等に簡易スコアリングを行い、自分用のランキングを生成する。

MVPには含めない。

---

# 36. My Notifications

Samsung NotiStar相当の機能をGalaxy以外でも利用可能にする独立アプリ。

Launcher本体には組み込まない。

---

# 37. 通知保存

構造：

```text
NotificationListenerService
       ↓
Notification
       ↓
Room Database
       ↓
Timeline UI
```

---

# 38. Notifications UI

```text
Notifications

[今日] [7日] [30日] [すべて]

🔍 Search

23:21 Gmail
────────────────
○○さん
件名……

22:58 X
────────────────
○○さんが投稿しました

22:31 YouTube
────────────────
新しい動画……
```

---

# 39. Notifications機能

将来的に以下を実装する。

```text
長期保存
全文検索
アプリ別絞り込み
日付絞り込み
お気に入り
削除
保存期間設定
```

LauncherのAction Shortcutから直接開けるようにする。

---

# 40. Androidプロジェクト構成案

```text
launcher/
│
├── app/
│
├── core-model/
├── core-storage/
├── core-launcher/
│
├── feature-home/
├── feature-allapps/
├── feature-search/
├── feature-dock/
├── feature-editor/
├── feature-backup/
├── feature-settings/
│
└── feature-feed-google/
```

規模が小さい初期段階ではSingle Moduleでも構わない。

ただし、

```text
Google Discover
Backup
Search
Storage
```

はInterface境界を明確にしておく。

---

# 41. 推奨技術

```text
Language
  Kotlin

UI
  Jetpack Compose

Architecture
  MVVM または UDF

DI
  Hilt / Koin
  ※小規模なら不要

DB
  Room

Preferences
  DataStore

Adaptive UI
  Compose Material 3 Adaptive
  Jetpack WindowManager

App discovery
  LauncherApps

Widget（将来）
  AppWidgetHost

Backup
  kotlinx.serialization

Async
  Kotlin Coroutines / Flow
```

`LauncherApps` はLauncher向けに起動可能Activityや複数User Profileの情報を取得する公式API。

---

# 42. Android Version方針

初期案：

```text
minSdk = 29
targetSdk = 最新Stable
compileSdk = 最新Stable
```

主対象が新しいGalaxy / Pixelであるため、古いAndroidとの互換性を優先しすぎない。

---

# 43. 権限方針

極力少ない権限で実装する。

MVP候補：

```text
Accessibility Service
  └ 下スワイプ通知用
```

その他は必要になった段階で追加する。

不必要なOverlay権限は取得しない。

Launcherは他アプリ使用中にUIを表示しない。

---

# 44. 非機能要件

Launcherのため、以下を特に重視する。

## 起動速度

端末起動直後でも高速表示する。

Feed通信完了を待ってHOMEを表示してはいけない。

---

## オフライン

ネットワークがなくても、

```text
HOME
All Apps
Search
Dock
```

は完全に動作する。

---

## Feed障害

Google Discoverが壊れてもLauncherは起動する。

---

## データ保護

アプリがアンインストールされても、ホーム上のItemを即座に削除しない。

Placeholderとして保持する。

---

## Process Death

Activity / ProcessがKillされても、

```text
ページ
レイアウト
Dock
ロック状態
```

を復元する。

---

# 45. MVP完成条件

Launcher v0.1は以下を満たした時点で完成とする。

```text
□ AndroidのDefault Homeに設定できる

□ Home GestureでHOMEページが表示される

□ 横スワイプでページ移動できる

□ All Appsページに全アプリが小型表示される

□ All Appsからアプリを起動できる

□ HOMEにアプリアイコンを配置できる

□ HOME右側にページを追加できる

□ アイコンを追加／移動／削除できる

□ レイアウトロックができる

□ Compactでは下Dock

□ Expandedでは右Dock

□ 上スワイプでアプリ検索

□ 下スワイプで通知を開ける
   ※Accessibility有効時

□ レイアウトを保存できる

□ JSONへExportできる

□ JSONからRestoreできる

□ 未インストールアプリをPlaceholder表示

□ PlaceholderからPlay Storeへ移動できる

□ アプリ再インストール後にPlaceholderが復活する
```

Google Discoverはv0.1の必須条件に含めない。

---

# 46. 開発フェーズ

## Phase 0 — Skeleton

```text
Android Project作成
CATEGORY_HOME対応
Default Launcher化
Compose画面表示
```

---

## Phase 1 — Core Launcher

```text
Horizontal Pager
All Apps
HOME
Home Gesture
App Launch
```

---

## Phase 2 — Editable Home

```text
Grid
App追加
Drag / Move
Delete
Page追加
Layout Lock
```

---

## Phase 3 — Adaptive Fold UI

```text
Window Size検出
Bottom Dock
Right Dock
Compact / Expanded配置
```

---

## Phase 4 — Search & Gestures

```text
Swipe Up Search
App Search
Google Search
Swipe Down Notification
```

---

## Phase 5 — Persistence

```text
Room
DataStore
Backup
Restore
Missing App
Play Store
```

---

## Phase 6 — Daily Driver

Galaxy Z Fold実機でNovaから一時的に切り替え、

```text
誤操作
Gesture
Fold開閉
アプリ起動
HOME復帰
Battery
Crash
```

を確認する。

---

## Phase 7 — Google Discover

```text
Lawnfeed調査
FeedBridge設計
Prototype
Google Discover統合
Fallback実装
```

---

## Phase 8 — Companion Apps

```text
Hatena Discover
My Notifications
```

---

# 47. 最重要設計原則

このLauncherはNova Launcherのクローンを目指さない。

必要な機能だけを持つ。

```text
Nova
────────────────────
何でもできるLauncher

My Launcher
────────────────────
自分が毎日使う操作だけが
最短距離でできるLauncher
```

特に以下を重視する。

```text
検索バーを置かない
↓
でも検索はすぐ呼べる

App Drawerを開かない
↓
All Appsを1ページで俯瞰できる

Foldを開いても
スマホUIを巨大化するだけにしない
↓
Dockを右へ移す

編集自由度を持つ
↓
普段はロックして誤操作させない

端末を変えても
↓
レイアウトを復元できる
```

---

# 48. 最終想定UX

## Galaxy Z Fold Closed

```text
      HOME

アプリアイコン Grid


───────────────
 ●  ●  ●  ●  ●
      Dock
```

横スワイプ：

```text
Discover ← All Apps ← HOME → Page 2 → Page 3
```

上スワイプ：

```text
Search
```

下スワイプ：

```text
Notifications
```

---

## Galaxy Z Fold Open

```text
┌────────────────────────────────┬────┐
│                                │ ●  │
│                                │ ●  │
│            HOME                │ ●  │
│                                │ ●  │
│                                │ ●  │
└────────────────────────────────┴────┘
                                    Dock
```

ページ構造、アイコン、ActionはClosed時と共通。

---

# 49. MVPで実装しないもの

以下は意図的に後回しにする。

```text
汎用Widget自由配置
Widget Resize
フォルダ
Icon Pack
Notification Badge
高度なGesture割当
Theme Marketplace
Launcher Cloud Sync
Edge Panel
他アプリ上へのOverlay Dock
AIによる自動配置
高度なUsage Ranking
```

必要になったものだけ後から追加する。

---

# 50. Definition of Done

v0.1が「完成」と呼べる状態は、

> Galaxy Z Fold上で本LauncherをDefault Homeとして1日通常使用し、Nova Launcherへ戻らなくても、アプリ起動・検索・ページ移動・Fold開閉・通知アクセス・レイアウト編集・バックアップの基本操作が問題なく行えること。

とする。

Google Discoverが未完成でもv0.1は完成扱いとする。

Google Discoverはv0.2以降の主要機能とする。
