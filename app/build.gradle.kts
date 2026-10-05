plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.myenvironment.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.myenvironment.launcher"
        minSdk = 29
        targetSdk = 35
        versionCode = 36
        versionName = "1.3.7-preview.2"
        buildConfigField("String", "BUILD_TIMESTAMP", "\"2026-10-05\"")
        buildConfigField("String", "UPDATE_SUMMARY", "\"Discoverの記事から戻る際、元の独自フィード・Google Discoverへ一度だけ復帰。HOME・戻る操作の受信継続を修正。All Appsページの表示を切り替え可能に。フィード設定画面で6種類の独自フィードを個別にON・OFFでき、再起動やバックアップ復元後も設定を保持します。\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("keystore/chime-signing.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("distribution") {
            storeFile = file(providers.environmentVariable("CHIME_KEYSTORE_PATH").orElse("${rootProject.projectDir}/.signing/chime.keystore").get())
            storePassword = providers.environmentVariable("CHIME_KEYSTORE_PASSWORD").orElse("android").get()
            keyAlias = providers.environmentVariable("CHIME_KEY_ALIAS").orElse("androiddebugkey").get()
            keyPassword = providers.environmentVariable("CHIME_KEY_PASSWORD").orElse("android").get()
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = if (providers.environmentVariable("CHIME_KEYSTORE_PATH").isPresent) signingConfigs.getByName("distribution") else signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":discover-protocol"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.window)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
