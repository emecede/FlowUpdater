import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
}

android {
    namespace = "com.family.flowupdater"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.family.flowupdater"
        minSdk = 22          // Fire OS 5 (Android 5.1) and newer
        targetSdk = 36
        versionCode = 1      // bump on every new release of the updater
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
