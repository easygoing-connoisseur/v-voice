import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.voicetester"
    // インストール済みのプラットフォームが android-34 のみ、かつ cmdline-tools が無いため 34 で固定。
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.voicetester"
        // 26 にすると Adaptive Icon の XML だけで済み、PNG の launcher アセットが不要になる。
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // VOICEVOX CORE / ONNX Runtime のネイティブライブラリがある ABI だけを対象にする。
        // x86_64 はエミュレータ確認用、arm64-v8a が実機用。
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
