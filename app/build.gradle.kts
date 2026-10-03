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
        versionCode = 19
        versionName = "1.3.3"
        buildConfigField("String", "BUILD_TIMESTAMP", "\"2026-10-03\"")
        buildConfigField("String", "UPDATE_SUMMARY", "\"検索・All Appsからホーム／Dockへドラッグ登録、Dockのドラッグ並べ替え、別の指によるページ送りに対応。長押しメニュー・レイアウトロック・Undoも維持。\"")

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
