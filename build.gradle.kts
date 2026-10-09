// ルートプロジェクト：プラグインのバージョンだけ宣言し、各モジュールで適用する
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
