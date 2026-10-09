# IcHomeLauncher

壁紙の上に「ワイヤーフレーム風のフローティングパネル」を自由に配置し、ターミナルとジェスチャーで操作する Android ホームアプリ（ランチャー）です。

- ホーム画面上の表示名：**IHL**
- パッケージ名：`com.ictools.ichomelauncher`
- バージョン：**v0.2.0**（ベータ版）
- 作者：IcTools
- 対応 OS：Android 12（API 31）以降／縦画面固定

詳細な仕様は [SPEC.md](SPEC.md) を参照してください。

## 機能

- OS の壁紙をそのまま表示し、ウィンドウ背後のぼかし（端末が対応している場合）とスモーク（暗さ）を重ねる
- フローティングパネル（Terminal / Apps / Settings / Media / Schedule / History / Memo）
  - タイトルバーのドラッグで移動、右下の三角ハンドルでリサイズ（最小 160×120dp）
  - タッチしたパネルが最前面になる
  - 位置・サイズ・開閉・重なり順を保存し、再起動後も復元
- 背景ジェスチャー（スワイプ・ダブルタップ・長押し）と画面全体のピンチにアクションを割り当て可能
- ターミナルからコマンドやアプリ名でアプリを起動
- アプリドロワー（検索付き、インストール／アンインストールを自動反映）
- メディアパネル：再生中の曲の表示と再生／一時停止・前後・シーク（要「通知へのアクセス」）
- スケジュールパネル：今日から数日分のカレンダーの予定（要カレンダー読み取り権限）
- ヒストリーパネル：アプリ起動履歴／コマンド履歴／通知履歴をタブで切り替え
- メモパネル：付箋のように 1 メモ＝1 パネルで何枚でも貼れる

## ターミナルコマンド

プロンプトは `ihl:` です。ソフトキーボードの送信（Enter）で実行します。左の「↑」で過去の入力（直近 50 件、再起動後も保持）を呼び出せます。

| コマンド | 動作 |
|---|---|
| `help` | コマンド一覧を表示 |
| `apps` / `drawer` | ドロワーパネルを開く |
| `open <アプリ名>` | 指定アプリを起動 |
| `<アプリ名>` | `open` を省略した起動（コマンド名と一致しない場合） |
| `settings` | 設定パネルを開く |
| `media` | メディアパネルを開く |
| `schedule` / `cal` | スケジュールパネルを開く |
| `history` | ヒストリーパネルを開く |
| `memo [テキスト]` | 新しいメモを作る |
| `memos` | メモ一覧（番号付き） |
| `memos <n>` | n 番のメモを開く |
| `memos rm <n>` | n 番のメモを削除 |
| `clear` | ログを消去 |
| `close` / `exit` | ターミナルを閉じる |
| `version` | バージョンを表示 |

アプリ名は大文字小文字を区別せず、完全一致 → 部分一致の順で探します。部分一致が複数ある場合は候補を表示し、起動しません。

## ジェスチャー

| ジェスチャー | 検出場所 | デフォルトのアクション |
|---|---|---|
| `swipe_up` | 背景 | `open_terminal` |
| `swipe_down` | 背景 | `none` |
| `swipe_left` | 背景 | `none` |
| `swipe_right` | 背景 | `none` |
| `double_tap` | 背景 | `none` |
| `long_press` | 背景 | `open_settings` |
| `pinch_in` | 画面全体 | `none` |
| `pinch_out` | 画面全体 | `close_focused` |

選べるアクション：`none` / `open_terminal` / `open_drawer` / `open_settings` / `open_media` / `open_schedule` / `open_history` / `new_memo` / `close_focused` / `close_all`

- 画面端のシステムジェスチャー領域（ホーム・戻る）で始まったスワイプは無視します
- 割り当てを変えて設定を開けなくなっても、ターミナルの `settings` コマンドから必ず開けます

## 権限

| 権限 | 用途 |
|---|---|
| 通知へのアクセス | メディアパネル・通知履歴 |
| カレンダーの読み取り | スケジュールパネル |

どちらも該当パネルを開いたときに許可ボタンが出ます（設定パネルの「権限」からも確認できます）。
GitHub からダウンロードした APK では「通知へのアクセス」がグレーアウトして許可できないことがあります。その場合は **アプリ情報 → 右上の︙ →「制限付き設定を許可」** を行ってから許可してください。

通知履歴・メモ・各履歴は端末内にのみ保存され、外部には送信しません。

## 使用ライブラリ

| ライブラリ | ライセンス |
|---|---|
| AndroidX（Core, Activity, Lifecycle） | Apache License 2.0 |
| Jetpack Compose | Apache License 2.0 |
| Jetpack DataStore | Apache License 2.0 |
| kotlinx.serialization | Apache License 2.0 |
| kotlinx.coroutines | Apache License 2.0 |
| Kotlin 標準ライブラリ | Apache License 2.0 |

Apache License 2.0 の全文は `app/src/main/assets/licenses/APACHE-2.0.txt` に同梱しており、アプリの設定パネル（情報）からも閲覧できます。

## ビルド方法

### GitHub Actions

`main` への push、または Actions 画面からの手動実行（workflow_dispatch）で `.github/workflows/build.yml` が動き、署名付き release APK が成果物 `IcHomeLauncher-apk` としてアップロードされます。

リポジトリの Secrets に以下を登録してください。

| Secret | 内容 |
|---|---|
| `IHL_KEYSTORE_BASE64` | キーストアファイルを Base64 にしたもの |
| `IHL_KEYSTORE_PASSWORD` | キーストアのパスワード |
| `IHL_KEY_ALIAS` | 鍵のエイリアス |
| `IHL_KEY_PASSWORD` | 鍵のパスワード |

キーストアの作成例：

```sh
keytool -genkeypair -v -keystore ihl-release.jks -keyalg RSA -keysize 4096 -validity 36500 -alias ihl
base64 -w 0 ihl-release.jks   # 出力を IHL_KEYSTORE_BASE64 に登録
```

上書きインストールでデータを引き継ぐため、**同じ鍵を使い続けてください**（`*.jks` はコミットしないこと）。

### ローカル

JDK 17 以上と Android SDK（API 36）を用意し：

```sh
./gradlew assembleRelease
```

署名用の環境変数（`IHL_KEYSTORE_PATH` / `IHL_KEYSTORE_PASSWORD` / `IHL_KEY_ALIAS` / `IHL_KEY_PASSWORD`）が無い場合は未署名の APK が `app/build/outputs/apk/release/` に出力されます。

## 使い方

1. APK をインストール
2. 設定パネルの「デフォルトのホームアプリを変更」、または端末の設定からホームアプリに **IHL** を選択

## ライセンス

[MIT License](LICENSE)
