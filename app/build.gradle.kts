plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.family.flowupdater"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.family.flowupdater"
        minSdk = 22          // Fire OS 5 (Android 5.1) and newer
        targetSdk = 33
        versionCode = 1      // bump on every new release of the updater
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
