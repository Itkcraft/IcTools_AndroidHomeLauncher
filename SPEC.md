# IcHomeLauncher 仕様書（v0.2.0）

> この文書は Claude Code に実装を依頼するための仕様書。
> 作者表記は **IcTools**。実装は Claude Code、ビルドは GitHub Actions で行う。

---

## 1. 概要

- アプリ名：**IcHomeLauncher**
- ホーム画面上のラベル（アイコン下の表示）：**IHL**
- パッケージ名：`com.ictools.ichomelauncher`
- 種別：Android ホームアプリ（ランチャー）
- コンセプト：壁紙の上に「ワイヤーフレーム風のフローティングパネル」を自由に配置し、ターミナルとジェスチャーで操作するランチャー
- バージョン：`versionName = "0.2.0"`, `versionCode = 2`（0.x.x はベータ版）

## 2. 技術スタック

| 項目 | 内容 |
|---|---|
| 言語 | Kotlin |
| UI | Jetpack Compose（Material3 は使ってもよいが、見た目は独自スタイル） |
| minSdk | 31（Android 12） |
| targetSdk / compileSdk | 36（Android 16） |
| ビルド | Gradle（Kotlin DSL）＋ Version Catalog（`gradle/libs.versions.toml`） |
| 保存 | Jetpack DataStore（Preferences） |
| シリアライズ | kotlinx.serialization（JSON） |
| 構成 | 単一 Activity ＋ ViewModel ＋ Repository |

- AGP・Kotlin・Compose BOM などのバージョンは、**実装時点の安定版を確認して使う**こと（この仕様書では固定しない）
- Gradle Wrapper（`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle-wrapper.properties`）を必ずコミットすること。GitHub Actions は `./gradlew` でビルドする

### 使用ライブラリとライセンス

| ライブラリ | ライセンス |
|---|---|
| AndroidX（Core, Activity, Lifecycle 等） | Apache License 2.0 |
| Jetpack Compose | Apache License 2.0 |
| Jetpack DataStore | Apache License 2.0 |
| kotlinx.serialization | Apache License 2.0 |
| kotlinx.coroutines | Apache License 2.0 |
| Kotlin 標準ライブラリ | Apache License 2.0 |

- 上記以外のライブラリを追加する場合は、追加前にライセンスを確認し、この表と設定パネルの「使用ライブラリ」に追記すること
- Apache License 2.0 の全文を `app/src/main/assets/licenses/APACHE-2.0.txt` に同梱し、設定パネルから閲覧できるようにする

## 3. ホームアプリとしての登録

`AndroidManifest.xml` のメイン Activity に以下を設定する。

```xml
<!-- ホームアプリとして認識させる設定 -->
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:label="IHL"
    android:launchMode="singleTask"
    android:stateNotNeeded="true"
    android:clearTaskOnLaunch="true"
    android:excludeFromRecents="true"
    android:screenOrientation="portrait"
    android:theme="@style/Theme.IcHomeLauncher">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <!-- ホームアプリ候補として表示される -->
        <category android:name="android.intent.category.HOME" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

- `android:resumeWhilePaused` は公開 SDK に存在しない非公開属性（ビルドエラーになる）のため指定しない
- `application` の `android:label` も `IHL` にする
- インストール済みアプリ一覧を取得するため、パッケージ可視性の `<queries>` を追加する

```xml
<!-- ランチャーに表示されるアプリ（MAIN + LAUNCHER）を取得可能にする -->
<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent>
</queries>
```

- 画面の向きは **縦固定**（v0.1.0）
- ホームボタンで再度 `onNewIntent` が来た場合、v0.1.0 では何もしない

## 4. 画面構成

```
┌──────────────────────────┐
│ 壁紙（OS 設定のもの）              │ ← レイヤー0：システム壁紙
│ ＋ ぼかし／スモーク                │ ← レイヤー1：背景エフェクト
│ ＋ ジェスチャー受付レイヤー         │ ← レイヤー2：背景タップ・スワイプ検出
│   ┌──────┐                   │
│   │Panel │  ┌──────┐         │ ← レイヤー3：フローティングパネル群
│   └──────┘  │Panel │         │
│              └──────┘         │
└──────────────────────────┘
```

- ステータスバー・ナビゲーションバーの裏まで描画する（edge-to-edge）。バー部分は透過

## 5. 背景（壁紙＋ぼかし＋スモーク）

### 5.1 壁紙表示

- テーマで `android:windowShowWallpaper="true"`、`android:windowIsTranslucent="true"`、`android:windowBackground="@android:color/transparent"` を設定し、OS の壁紙をそのまま表示する
- 壁紙画像そのものは読み込まない（追加権限が必要になるため）

### 5.2 ぼかし

- `Window.setBackgroundBlurRadius(px)` を使ってウィンドウ背後（＝壁紙）をぼかす
- `WindowManager.isCrossWindowBlurEnabled` を確認し、`addCrossWindowBlurEnabledListener` で有効／無効の変化を監視する
  - ぼかしが使えない状態（端末非対応・省電力モード等）では、ぼかしを適用せずスモークのみにする
  - その場合、設定パネルのぼかし項目に「この端末／状態ではぼかしが無効です」と表示する
- 実機（ROG Phone 8 / Android 16）で効くかは未検証。効かない場合は報告すること

### 5.3 スモーク（暗さ）

- 背景全面に黒の半透明レイヤーを重ねる。透明度を設定で変更できる

### 5.4 デフォルト値

| 設定 | デフォルト | 範囲 |
|---|---|---|
| ぼかし ON/OFF | ON | — |
| ぼかし半径 | 30dp 相当 | 0〜100 |
| スモーク（暗さ） | 0% | 0〜90% |

## 6. フローティングパネル基盤

### 6.1 パネル共通仕様

- 外観（ワイヤーフレーム風）
  - 背景：黒の半透明（不透明度 70% 程度）
  - 枠線：1dp の白〜グレー系の細線
  - 角：右上と左下を斜めにカットした形（`GenericShape` 等で実装）
  - フォント：等幅（`FontFamily.Monospace`）
- タイトルバー
  - 左：パネル名（例：`Terminal`）
  - 右：閉じるボタン（✕）
  - タイトルバーをドラッグすると移動
- リサイズ
  - 右下に小さな三角のハンドルを置き、ドラッグでサイズ変更
  - 最小サイズ：幅 160dp × 高さ 120dp
- 画面外への移動
  - 自由に動かせるが、**タイトルバーが最低 48dp は画面内に残る**ように制限する（見失い防止）
- タッチの扱い
  - パネル内をタッチしたら、そのパネルがフォーカス（最前面）になる
  - パネル上のタッチは背景のジェスチャーレイヤーに渡さない（ピンチを除く。7章参照）

### 6.2 重なり順（Z オーダー）

- パネルはアクティブ化（開く・タッチ）された順に積み重なる
- フォーカス中のパネルが最前面
- パネルを閉じたら、次に新しくアクティブ化されたパネルにフォーカスを移す

### 6.3 パネルの種類

| ID | 名前 | 内容 |
|---|---|---|
| `terminal` | Terminal | 8章 |
| `drawer` | Apps | 9章 |
| `settings` | Settings | 10章 |
| `media` | Media | 16章（v0.2.0） |
| `schedule` | Schedule | 17章（v0.2.0） |
| `history` | History | 18章（v0.2.0） |
| `memo:<メモID>` | Memo | 19章（v0.2.0） |

- メモ以外は **各パネル 1 つずつ**。すでに開いている場合は最前面に出すだけ
- メモは 1 メモ＝1 パネルで、複数同時に開ける（パネルIDは `memo:<メモID>`）

### 6.4 状態の保存（必須）

保存する情報（パネルごと）：

```kotlin
// パネル1枚分の保存データ
@Serializable
data class PanelState(
    val id: String,        // パネル種別ID（terminal / drawer / settings）
    val xDp: Float,        // 左上X座標（dp）
    val yDp: Float,        // 左上Y座標（dp）
    val widthDp: Float,    // 幅（dp）
    val heightDp: Float,   // 高さ（dp）
    val isOpen: Boolean,   // 開いているか
    val zOrder: Int        // 重なり順（大きいほど前面）
)
```

- 移動・リサイズ・開閉・フォーカス変更のたびに DataStore へ保存する（ドラッグ中は終了時のみ保存）
- アプリ再起動・端末再起動後も同じ配置で復元する
- 復元時、画面サイズが変わっていて 6.1 の制限から外れる場合は画面内に収まるよう補正する
- 初回起動時の初期配置：ターミナルのみ画面中央下寄りに開いた状態

## 7. ジェスチャー

### 7.1 ジェスチャーの種類

| ID | 内容 | 検出場所 |
|---|---|---|
| `swipe_up` | 1本指で下→上スワイプ | 背景のみ |
| `swipe_down` | 1本指で上→下スワイプ | 背景のみ |
| `swipe_left` | 1本指で右→左スワイプ | 背景のみ |
| `swipe_right` | 1本指で左→右スワイプ | 背景のみ |
| `double_tap` | 1本指ダブルタップ | 背景のみ |
| `long_press` | 1本指長押し | 背景のみ |
| `pinch_in` | 2本指でつまむ（指を近づける） | 画面全体（パネル上も含む） |
| `pinch_out` | 2本指で広げる（指を離す） | 画面全体（パネル上も含む） |

- ピンチは画面全体で検出する。パネル上でもパネル側の 1 本指操作を邪魔しないよう、ルートで `PointerEventPass.Initial` を使って 2 本指の距離変化だけを監視する
- ピンチ判定は 2 本指の距離が開始時から ±30% 以上変化したら確定する

### 7.2 システムジェスチャーとの衝突回避

- ジェスチャーナビゲーションでは、画面最下端からの上スワイプは OS（ホームへ戻る）に取られる
- `WindowInsets.systemGestures` を取得し、**システムジェスチャー領域の中で開始したスワイプは無視**する
- 同様に左右端のシステム「戻る」領域で開始したスワイプも無視する

### 7.3 実行できるアクション

| ID | 内容 |
|---|---|
| `none` | 何もしない |
| `open_terminal` | ターミナルを開く（開いていれば前面に） |
| `open_drawer` | ドロワーを開く |
| `open_settings` | 設定を開く |
| `open_media` | メディアパネルを開く（v0.2.0） |
| `open_schedule` | スケジュールパネルを開く（v0.2.0） |
| `open_history` | ヒストリーパネルを開く（v0.2.0） |
| `new_memo` | 新しいメモを作る（v0.2.0） |
| `close_focused` | フォーカス中のパネルを閉じる |
| `close_all` | すべてのパネルを閉じる |

### 7.4 割り当て（設定で変更可能）

- ジェスチャー → アクションの対応表を DataStore に保存し、設定パネルから変更できる
- デフォルト：

| ジェスチャー | アクション |
|---|---|
| `swipe_up` | `open_terminal` |
| `pinch_out` | `close_focused` |
| `long_press` | `open_settings` |
| その他 | `none` |

- 設定パネルが開けなくなる事態を防ぐため、ターミナルの `settings` コマンドからは常に設定を開ける

## 8. ターミナルパネル

### 8.1 見た目

- 上部：出力ログ（スクロール可能、新しい行が下）
- 下部：プロンプト＋入力欄
- プロンプト：`ihl:`
- 入力したコマンドはログに `ihl: <入力内容>` として残す
- 日本語入力（IME）に対応する（アプリ名の日本語入力のため）
- 実行はソフトキーボードの Enter（`ImeAction.Done` / `Send`）で行う

### 8.2 コマンド

| コマンド | 動作 |
|---|---|
| `help` | コマンド一覧を表示 |
| `apps` / `drawer` | ドロワーパネルを開く |
| `open <アプリ名>` | 指定アプリを起動 |
| `<アプリ名>` | `open` を省略した起動（コマンド名と一致しない場合） |
| `settings` | 設定パネルを開く |
| `media` | メディアパネルを開く（v0.2.0） |
| `schedule` / `cal` | スケジュールパネルを開く（v0.2.0） |
| `history` | ヒストリーパネルを開く（v0.2.0） |
| `memo [テキスト]` | 新しいメモを作る（v0.2.0） |
| `memos` | メモ一覧を番号付きで表示（更新が新しい順、v0.2.0） |
| `memos <n>` | n 番のメモを開く（v0.2.0） |
| `memos rm <n>` | n 番のメモを削除（v0.2.0） |
| `clear` | ログを消去 |
| `close` / `exit` | ターミナルを閉じる |
| `version` | `IcHomeLauncher v0.1.0` を表示 |

### 8.3 アプリ名での起動ルール

1. アプリのラベルと**完全一致**（大文字小文字無視）するものがあれば起動
2. なければ**部分一致**で検索
   - 1件 → 起動
   - 複数件 → 候補一覧を表示して起動しない
   - 0件 → `command not found: <入力>` を表示
- 起動時は `Opening <アプリ名>` をログに表示

### 8.4 その他

- コマンド履歴：入力欄の左に「↑」ボタンを置き、過去の入力を呼び出せる（直近 50 件。v0.2.0 から DataStore に保存し再起動後も残す。同じコマンドの連続は 1 件にまとめる）
- ログは最大 500 行、超えたら古い行から削除

## 9. ドロワーパネル（Apps）

- インストール済みアプリ（MAIN + LAUNCHER を持つもの）をラベル順に一覧表示
- 各行：アイコン＋アプリ名
- 上部に検索欄（部分一致で絞り込み）
- タップで起動
- アプリのインストール／アンインストールを検知して一覧を更新する（`LauncherApps.Callback` か `PACKAGE_ADDED` / `PACKAGE_REMOVED` の受信）
- 自分自身（IHL）は一覧から除外する

## 10. 設定パネル（Settings）

### 10.1 項目

- **背景**
  - ぼかし ON/OFF
  - ぼかし半径（スライダー）
  - スモーク（暗さ）（スライダー）
  - 変更は即時反映
- **ジェスチャー**
  - 7.1 の各ジェスチャーに対し、7.3 のアクションを選択（ドロップダウン）
  - 「初期値に戻す」ボタン
- **パネル**
  - 「パネル配置をリセット」ボタン（初回起動時の配置に戻す）
- **情報**
  - アプリ名・バージョン（`IcHomeLauncher v0.1.0`）・作者（IcTools）
  - 使用ライブラリ一覧（名前・ライセンス）と、ライセンス全文の表示
  - 「デフォルトのホームアプリを変更」ボタン（`Settings.ACTION_HOME_SETTINGS` を開く）

## 11. アプリアイコン

- アダプティブアイコン（`mipmap-anydpi-v26`）、ベクター（VectorDrawable）で作成
- デザイン：黒背景に、細い白線の枠（角を一部カット）と、等幅風の文字「IHL」
- モノクロアイコン（`monochrome`）も同じデザインで用意する

## 12. パッケージ構成（目安）

```
com.ictools.ichomelauncher
├─ MainActivity.kt          // ホームActivity、背景ぼかし制御
├─ ui/
│  ├─ LauncherScreen.kt     // 背景＋ジェスチャー＋パネル群のルート
│  ├─ theme/                // 色・フォント・パネル形状
│  ├─ panel/                // フローティングパネル共通部品
│  ├─ terminal/             // ターミナルパネル
│  ├─ drawer/               // ドロワーパネル
│  └─ settings/             // 設定パネル
├─ gesture/                 // ジェスチャー検出・割り当て
├─ data/
│  ├─ PanelRepository.kt    // パネル状態の保存・復元
│  ├─ SettingsRepository.kt // 背景・ジェスチャー設定
│  └─ AppRepository.kt      // インストール済みアプリ取得・起動
└─ LauncherViewModel.kt
```

## 13. ビルド・署名

- GitHub Actions（`.github/workflows/build.yml`）で `assembleRelease` を実行し、APK を成果物としてアップロードする
- 署名は**固定の鍵**を使う（毎回違う鍵だと上書きインストールできず、保存データが消えるため）
- `app/build.gradle.kts` の署名設定は環境変数から読む：

| 環境変数 | 内容 |
|---|---|
| `IHL_KEYSTORE_PATH` | キーストアファイルのパス |
| `IHL_KEYSTORE_PASSWORD` | キーストアのパスワード |
| `IHL_KEY_ALIAS` | 鍵のエイリアス |
| `IHL_KEY_PASSWORD` | 鍵のパスワード |

- 環境変数が無い場合（ローカルビルド等）は release の署名設定を適用しない（ビルドは通す）
- v0.1.0 では難読化（R8 の minify）は **OFF**
- `.gitignore` に `*.jks`, `*.keystore`, `local.properties` を入れる

## 14. ドキュメント

- `README.md`：概要、機能一覧、ターミナルコマンド、ジェスチャー一覧、使用ライブラリ（名前・ライセンス）、ビルド方法
- `CHANGELOG.md`：バージョンごとの変更履歴（v0.1.0 から記載）
- 修正・機能追加のたびに `versionName` / `versionCode` を更新し、README・CHANGELOG・アプリ内表示も合わせて更新する
  - バグ修正：パッチ（0.1.0 → 0.1.1）
  - 機能追加：マイナー（0.1.x → 0.2.0）
  - ベータ期間（0.x.x）中は互換性が壊れる変更でもメジャーは上げない
- ソースコードのコメントは日本語で、機能の説明を書く

## 15. 今後の予定

- 横画面対応

（v0.1.0 で予定していたメディア・スケジュール・メモ・同種パネルの複数起動は v0.2.0 で実装。Toolkit は予定から外した）

## 16. メディアパネル（Media、v0.2.0）

- 再生中（無ければ直近）のメディアセッションの曲名・アーティスト・アプリ名・アートワークを表示
- 再生位置バー（長さが分かる場合）。タップした位置へシーク
- 操作ボタン：`|<`（前）／`>`・`||`（再生・一時停止）／`>|`（次）
- 曲情報をタップすると再生中のアプリを開く
- `MediaSessionManager.getActiveSessions` を使うため、通知リスナー（`NotificationListenerService`）を用意し「通知へのアクセス」の許可を必要とする
- 未許可の場合はパネル内に許可ボタンを表示。サイドロードした APK は「制限付き設定」により許可できないことがあるため、アプリ情報を開くボタンと手順を併記する

## 17. スケジュールパネル（Schedule、v0.2.0）

- `READ_CALENDAR` 権限で端末のカレンダーを読む（読み取りのみ）
- 今日から N 日分（設定で 1／3／7／14 日、初期値 7 日）の予定を日付ごとに表示。日付見出しに today／tomorrow を付ける
- 各行：カレンダー色の縦線、タイトル、時刻（終日は `all day`）、場所。終わった予定は薄く表示
- タップでカレンダーアプリの該当予定を開く
- カレンダーの変更を監視して自動更新。日付が変わったら読み直す
- 未許可の場合はパネル内に許可ボタンを表示

## 18. ヒストリーパネル（History、v0.2.0）

タブで切り替える。各タブ右上の `clear` は 2 回押しで消去。

| タブ | 内容 | タップ時 |
|---|---|---|
| `Apps` | IHL から起動したアプリ（新しい順、同じアプリは最新 1 件、最大 50 件） | アプリを起動 |
| `Cmds` | ターミナルのコマンド履歴（新しい順、最大 50 件） | ターミナルを開いて再実行 |
| `Notif` | 届いた通知（最大 200 件。常駐通知・グループのまとめ通知・本文の無い通知・同じ内容の連続は記録しない） | 元の通知の操作で開く（使えなければアプリを起動） |

- 通知履歴は「通知へのアクセス」許可中のみ記録される。履歴は端末内（DataStore）にのみ保存する
- 通知を開く操作（PendingIntent）は保存できないため、IHL のプロセスが終了した後はアプリの起動で代用する

## 19. メモパネル（Memo、v0.2.0）

- 1 メモ＝1 パネル。複数のメモを同時に画面に貼れる
- タイトルバーは `Memo: <1行目>`
- 本文は入力のたびに 0.5 秒待ってから保存
- 右下に更新時刻と `del`（2 回押しで削除）
- ✕で閉じてもメモは残る（ターミナルの `memos` から再表示）。空のまま閉じたメモは削除する
- 新規作成：ターミナルの `memo [テキスト]`、またはジェスチャーの `new_memo`
- 「パネル配置をリセット」ではメモは閉じた状態で残す

## 20. 権限（v0.2.0）

| 権限 | 用途 | 未許可時 |
|---|---|---|
| 通知へのアクセス | メディアパネル・通知履歴 | 該当パネルに許可ボタンを表示 |
| カレンダーの読み取り（`READ_CALENDAR`） | スケジュールパネル | パネルに許可ボタンを表示 |

- 設定パネルに「権限」セクションを設け、状態と設定画面へのボタンを表示する
- 設定画面から戻ったとき（`onResume`）に権限を確認し直す
