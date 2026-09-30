# Google Discover Companion プレビュー

このブランチでは、Home → 全アプリ → 独自フィードというページ順を保ち、独自フィードのさらに左にGoogle AppのDiscover overlayを接続します。右へ指を動かすと、指の移動量をGoogle Appへ渡して画面を開きます。外部のGoogle App Activityを自動起動する処理とは別です。

## 導入

同じリリースの次の2つのAPKをインストールしてください。

- `ChimeLauncher-v1.2.0-discover-preview.1.apk`
- `ChimeDiscoverCompanion-v1.2.0-discover-preview.1.apk`

v1.0.0・v1.1.0公開APKと同じ証明書で本体を署名しています。既存公開版の上に更新できます。違う署名の手元ビルドを使っている場合は、先にJSONバックアップを保存してから更新方法を判断してください。署名違いのAPKで既存アプリを上書きすることはできません。

1. Google Appをインストール・有効化します。
2. 2つのAPKをインストールします。インストール順は問いません。
3. Chime Launcherを標準のホームに設定します。
4. Launcher設定のDiscoverモードを「独自フィード + Google Discover」にします。既存のフィード表示設定もこのモードとして引き継ぎます。
5. Homeから左へ2ページ進んで独自フィードを開きます。さらに右方向へ指を動かすとGoogle Discoverを開きます。

Companion不在、署名不一致、Google Appの接続拒否時は独自フィードの上部に状態を表示します。このモードで外部Google Appを勝手に起動しません。Google Appを別画面で開くには、フィード上のGoogleボタンを使うか、「Google Appを自動で開く」を選びます。

## 実装

- `discover-protocol`: 独自Bridge AIDLとGoogle overlayのwire契約。AIDLの宣言順はtransaction IDなので変更しないでください。
- `discover-companion`: debuggable helper。Google Appの `WINDOW_OVERLAY` にhelper自身のpackage/UIDで接続し、Binder transactionを中継します。
- Launcher `GoogleOverlayClient`: 署名検証、Window attach、Activity状態、Binder切断、再接続、package更新、Fold構成変更を扱います。
- `OverlayDragSession`: 縦スクロール、通常Pager、Googleへの横ドラッグを1回のgesture単位で判定します。

CompanionのServiceはsignature権限で保護し、IPCごとにUIDがChime Launcherに属するか、同一証明書かを検証します。LauncherもCompanionの証明書を検証します。Companionにインストーラ権限、外部ストレージ権限、アカウント読取権限はありません。

Nova/Lawnfeedのコード、素材、UIは取り込んでいません。相互運用上のメソッド順、descriptor、Intent action、Bundleキーを契約として定義し、中継とUI接続はChime側で実装しています。

## ビルドと署名

JDK 17、Android SDK 35、Gradle wrapperを使用します。

```zsh
./gradlew :app:testDebugUnitTest :app:assembleRelease :discover-companion:assembleRelease
```

既定では2つとも既存リポジトリの開発証明書を使用します。別の配布鍵を利用するときは次の環境変数を渡し、同じ保管済み鍵で両方を署名します。

```zsh
export CHIME_KEYSTORE_PATH='/absolute/path/to/chime.keystore'
export CHIME_KEYSTORE_PASSWORD='your-store-password'
export CHIME_KEY_ALIAS='your-key-alias'
export CHIME_KEY_PASSWORD='your-key-password'
./gradlew :app:testDebugUnitTest :app:assembleRelease :discover-companion:assembleRelease
```

出力は `app/build/outputs/apk/release/app-release.apk` と `discover-companion/build/outputs/apk/release/discover-companion-release.apk` です。本体は非debuggable、Companionのみdebuggableです。鍵はGitへコミットしないでください。

CIは2つのAPKとテストをビルドします。別途管理する鍵で自動リリースする場合はRepository Secret `CHIME_KEYSTORE_BASE64`、必要に応じて `CHIME_KEYSTORE_PASSWORD`、`CHIME_KEY_ALIAS`、`CHIME_KEY_PASSWORD` を設定してください。既定のCI成果物も既存リポジトリの開発証明書を使用します。配布鍵SecretなしのCIはGitHub Releaseを作成・上書きしません。今回の公開APKはローカルの既存配布鍵を使用します。

## 検証範囲

ビルド、既存ユニットテスト、overlayドラッグ判定テスト、APKの署名・Manifestを確認して公開するプレビューです。Google Appをインストールした実機が接続されていないため、Google Discoverの表示、スワイプの追従、戻る操作、Fold開閉の実機動作は未検証です。Nova/Pixelとの完全一致を保証するリリースではありません。

実機で確認する項目: 2APK導入、標準HOME指定、接続status、開閉・途中で戻す操作、記事タップ後のHOME復帰、戻る・Home、画面消灯・復帰、回転・Fold開閉、Google/Companionの更新・force-stop、CompanionなしでもRSSページが利用できること。

Googleの公開SDKではなくGoogle Appの非公開Binderに依存します。Google App更新で接続不能になる場合があります。Google Appが画面と記事内容を提供するため、Chime側で記事を取得・コピーしたりアカウントにログインしたりしません。
