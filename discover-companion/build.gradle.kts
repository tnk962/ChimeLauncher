plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.myenvironment.chimediscoverbridge"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.myenvironment.chimediscoverbridge"
        minSdk = 29
        targetSdk = 35
        versionCode = 6
        versionName = "1.3.1"
    }
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("app/keystore/chime-signing.keystore")
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
            // Google overlay compatibility requires a debuggable connecting process.
            isDebuggable = true
            signingConfig = if (providers.environmentVariable("CHIME_KEYSTORE_PATH").isPresent) signingConfigs.getByName("distribution") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies { implementation(project(":discover-protocol")) }
