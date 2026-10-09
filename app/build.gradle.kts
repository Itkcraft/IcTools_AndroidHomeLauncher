// アプリモジュールのビルド設定
// ※AGP 9 以降は Kotlin サポートが組み込みのため kotlin-android プラグインは不要
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 署名情報は環境変数から読む（GitHub Actions の Secrets から渡される）
val ihlKeystorePath: String? = System.getenv("IHL_KEYSTORE_PATH")
val ihlKeystorePassword: String? = System.getenv("IHL_KEYSTORE_PASSWORD")
val ihlKeyAlias: String? = System.getenv("IHL_KEY_ALIAS")
val ihlKeyPassword: String? = System.getenv("IHL_KEY_PASSWORD")

// 4つすべて揃っているときだけ release 署名を有効にする（ローカルビルドでは未署名のまま通す）
val hasReleaseSigning = listOf(ihlKeystorePath, ihlKeystorePassword, ihlKeyAlias, ihlKeyPassword)
    .all { !it.isNullOrBlank() }

android {
    namespace = "com.ictools.ichomelauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ictools.ichomelauncher"
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(ihlKeystorePath!!)
                storePassword = ihlKeystorePassword
                keyAlias = ihlKeyAlias
                keyPassword = ihlKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // v0.1.0 では難読化（R8 の minify）は OFF
            isMinifyEnabled = false
            isShrinkResources = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
}
