import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// リリース署名の情報は環境変数から読む。KEYSTORE_BASE64 はキーストアファイルを
// base64 化したもので、ビルド時にデコードして一時ファイルへ書き出す。
// いずれかが未設定なら release 署名は適用しない（fork / 手元ビルドを壊さないため）。
val keystoreBase64: String? = System.getenv("KEYSTORE_BASE64")
val envKeystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")
val envKeyAlias: String? = System.getenv("KEY_ALIAS")
val envKeyPassword: String? = System.getenv("KEY_PASSWORD")
val hasReleaseSigning = !keystoreBase64.isNullOrBlank() &&
    !envKeystorePassword.isNullOrBlank() &&
    !envKeyAlias.isNullOrBlank() &&
    !envKeyPassword.isNullOrBlank()

val decodedKeystoreFile = if (hasReleaseSigning) {
    File(layout.buildDirectory.asFile.get(), "release-keystore/release.jks").apply {
        parentFile.mkdirs()
        writeBytes(Base64.getDecoder().decode(keystoreBase64))
    }
} else null

android {
    namespace = "com.example.voicetester"
    // インストール済みのプラットフォームが android-34 のみ、かつ cmdline-tools が無いため 34 で固定。
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.voicetester"
        // 26 にすると Adaptive Icon の XML だけで済み、PNG の launcher アセットが不要になる。
        minSdk = 26
        targetSdk = 34
        // CI からは -PreleaseVersionCode / -PreleaseVersionName でタグ由来の値を渡す。
        // 未指定時（手元ビルド等）はこれまでどおりの固定値を使う。
        versionCode = (project.findProperty("releaseVersionCode") as String?)?.toInt() ?: 2
        versionName = project.findProperty("releaseVersionName") as String? ?: "1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // VOICEVOX CORE / ONNX Runtime のネイティブライブラリがある ABI だけを対象にする。
        // x86_64 はエミュレータ確認用、arm64-v8a が実機用。
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = decodedKeystoreFile
                storePassword = envKeystorePassword
                keyAlias = envKeyAlias
                keyPassword = envKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 環境変数が揃っていないときは適用しない。fork した人や手元で試す人が
            // ビルドできなくなるのを避けるため、その場合は未署名/デバッグ署名のまま出す。
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
    }

    androidResources {
        // 辞書と音声モデルは実ファイルとして展開して使うので、
        // APK 内で再圧縮してもインストール後の容量は減らない。展開を速くする方を採る。
        noCompress += listOf("dic", "vvm", "bin", "def")
    }

    packaging {
        jniLibs {
            // .so は APK 内に非圧縮で置き、展開せず直接ロードさせる。
            useLegacyPackaging = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.voicevoxcore.android)

    testImplementation(libs.junit)
}
