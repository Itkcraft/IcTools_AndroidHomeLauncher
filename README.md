# IcHomeLauncher

壁紙の上に「ワイヤーフレーム風のフローティングパネル」を自由に配置し、ターミナルとジェスチャーで操作する Android ホームアプリ（ランチャー）です。

- ホーム画面上の表示名：**IHL**
- パッケージ名：`com.ictools.ichomelauncher`
- バージョン：**v0.3.0**（ベータ版）
- 作者：IcTools
- 対応 OS：Android 12（API 31）以降／縦画面固定

詳細な仕様は [SPEC.md](SPEC.md) を参照してください。

## 機能

- OS の壁紙をそのまま表示し、ウィンドウ背後のぼかし（端末が対応している場合）とスモーク（暗さ）を重ねる
- フローティングパネル（Terminal / Apps / Settings / Media / Schedule / History / Memo / Clock / Calendar / Status / Network / Favorites）
  - タイトルバーのドラッグで移動、右下の三角ハンドルでリサイズ（最小 160×120dp）
  - タッチしたパネルが最前面になる
  - 位置・サイズ・開閉・重なり順を保存し、再起動後も復元
- 背景ジェスチャー（スワイプ・ダブルタップ・長押し）と画面全体のピンチにアクションを割り当て可能
- ターミナルからコマンドやアプリ名でアプリを起動
- アプリドロワー（検索付き、インストール／アンインストールを自動反映）
- メディアパネル：曲情報（停止後・再起動後も表示）、再生／一時停止・前後・シーク、キュー表示、EQ、音楽波形（要「通知へのアクセス」）
- スケジュールパネル：今日から数日分のカレンダーの予定（要カレンダー読み取り権限）
- ヒストリーパネル：アプリ起動履歴／コマンド履歴／通知履歴をタブで切り替え
- メモパネル：付箋のように 1 メモ＝1 パネルで何枚でも貼れる
- 時計パネル／カレンダーパネル（月表示）
- 稼働状況パネル：RAM・バッテリー・発熱・ストレージ・稼働時間・最近使ったアプリ
- 通信パネル：Wi-Fi／モバイルの接続状態・電波強度・リンク速度・SSID
- お気に入りパネル：よく使うアプリを並び替えて置いておける
- ターミナルの入力補完（グレーのゴーストテキスト、→ ボタンか Tab で確定）とファイル操作
- 見た目のカスタマイズ（ワイヤー色・パネル背景・パネルの形）

## ターミナルコマンド

プロンプトは `ihl:` です。ソフトキーボードの送信（Enter）で実行します。左の「↑」で過去の入力（直近 50 件、再起動後も保持）を呼び出せます。入力中はグレーで補完候補が出るので、右端の「→」（外付けキーボードなら Tab）で確定できます。

| コマンド | 動作 |
|---|---|
| `help` | コマンド一覧を表示 |
| `apps` / `drawer` | ドロワーパネルを開く |
| `open <ファイル｜アプリ名>` | 今いるディレクトリのファイル／パスに一致すればファイルを開き、そうでなければアプリを起動 |
| `open -a <アプリ名>` | アプリとして起動 |
| `<アプリ名>` | `open` を省略した起動（コマンド名と一致しない場合） |
| `settings` | 設定パネルを開く |
| `media` | メディアパネルを開く |
| `schedule` / `cal` | スケジュールパネルを開く |
| `history` | ヒストリーパネルを開く |
| `memo [テキスト]` | 新しいメモを作る |
| `memos` | メモ一覧（番号付き） |
| `memos <n>` | n 番のメモを開く |
| `memos rm <n>` | n 番のメモを削除 |
| `clock` / `calendar` / `status` / `network`（`net`）/ `favorites`（`favs`） | 各パネルを開く |
| `camera` | カメラアプリを起動 |
| `light` / `light on` / `light off` | ライトの切り替え／点灯／消灯 |
| `fav` / `fav add <アプリ名>` / `fav rm <アプリ名>` | お気に入りの一覧／登録／解除 |
| `pwd` | 現在のディレクトリ |
| `ls [-a] [path]` | ファイル一覧 |
| `cd [path]` | ディレクトリ移動（`~` は内部ストレージ直下） |
| `mkdir <path>` | ディレクトリ作成 |
| `cp <src> <dst>` / `mv <src> <dst>` | コピー／移動（既存のものは上書きしない） |
| `rm <path>` | 削除（`(y/N)` の確認あり） |
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

選べるアクション：`none` / `open_terminal` / `open_drawer` / `open_settings` / `open_media` / `open_schedule` / `open_history` / `open_clock` / `open_calendar` / `open_status` / `open_network` / `open_favorites` / `new_memo` / `close_focused` / `close_all`

- 画面端のシステムジェスチャー領域（ホーム・戻る）で始まったスワイプは無視します
- 割り当てを変えて設定を開けなくなっても、ターミナルの `settings` コマンドから必ず開けます

## 権限

| 権限 | 用途 |
|---|---|
| 通知へのアクセス | メディアパネル・通知履歴 |
| カレンダーの読み取り | スケジュール・カレンダーパネル |
| マイク | 音楽波形（端末の音声出力を解析するだけで、録音はしません） |
| すべてのファイルへのアクセス | ターミナルのファイル操作 |
| 使用状況へのアクセス | 稼働状況の「最近使ったアプリ」 |
| 正確な位置情報 | 通信パネルの SSID 表示（端末の位置情報サービスも ON にする必要あり） |
| 電話 | 通信パネルの回線種別（5G/LTE）表示 |

どれも、使う機能のパネル（ファイル操作はターミナル）に理由と許可ボタンが出ます。設定パネルの「権限」からも確認できます。未許可でもその項目が表示されないだけで、アプリは動作します。
GitHub からダウンロードした APK では「通知へのアクセス」「使用状況へのアクセス」がグレーアウトして許可できないことがあります。その場合は **アプリ情報 → 右上の︙ →「制限付き設定を許可」** を行ってから許可してください。

通知履歴・メモ・各履歴は端末内にのみ保存され、外部には送信しません。

### Android の仕様による制限

- **EQ**：再生アプリが再生開始時に通知を送る場合のみ対応します。IHL が起動する前に再生を始めた曲には、次の曲・再生し直しまで効きません。端末内蔵のサウンド機能と効果が重なる場合があります。
- **音楽波形**：ハードウェアで直接処理する再生方式（オフロード再生）では平らなままになることがあります。
- **停止後の操作**：再生アプリが終了した後のボタン操作は、OS が最後に再生していたアプリへ届けます（再開に対応していないアプリは反応しません）。キューは最後に取得したものを表示します。
- **ファイル操作**：`Android/data`・`Android/obb` は参照できません。
- **稼働状況**：CPU 温度、他アプリのプロセス一覧・CPU 使用率・強制終了は取得・操作できません。

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
