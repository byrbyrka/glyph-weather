plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Fail fast and loudly (instead of silently producing app-release-unsigned.apk) if
// someone runs assembleRelease without a signing key configured. A debug-signed or
// unsigned "release" APK is exactly the artifact that trips AV heuristics.
//
// Two ways to provide a signing key are recognized:
//  1. ~/.gradle/gradle.properties: RELEASE_STORE_FILE / RELEASE_STORE_PASSWORD / etc.
//  2. Android Studio's "Generate Signed Bundle / APK" wizard, which injects
//     android.injected.signing.store.file (and friends) as project properties instead.
tasks.matching { it.name == "assembleRelease" || it.name.startsWith("packageRelease") }.configureEach {
    doFirst {
        val hasManualConfig = project.findProperty("RELEASE_STORE_FILE") != null
        val hasStudioWizardConfig = project.findProperty("android.injected.signing.store.file") != null
        check(hasManualConfig || hasStudioWizardConfig) {
            "No release signing key configured. Either:\n" +
                "  1) Use Android Studio's Build > Generate Signed Bundle / APK wizard, or\n" +
                "  2) Set RELEASE_STORE_FILE / RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS / " +
                "RELEASE_KEY_PASSWORD in ~/.gradle/gradle.properties " +
                "(see app/build.gradle.kts signingConfigs for details)."
        }
    }
}

android {
    namespace = "com.glyphweather"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.glyphweather"
        minSdk = 33          // Glyph Matrix SDK requires version 33 or higher
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            // Set these in ~/.gradle/gradle.properties (NOT this file, and NOT committed):
            //   RELEASE_STORE_FILE=/absolute/path/to/release.keystore
            //   RELEASE_STORE_PASSWORD=...
            //   RELEASE_KEY_ALIAS=...
            //   RELEASE_KEY_PASSWORD=...
            val storeFilePath = findProperty("RELEASE_STORE_FILE") as String?
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = findProperty("RELEASE_STORE_PASSWORD") as String?
                keyAlias = findProperty("RELEASE_KEY_ALIAS") as String?
                keyPassword = findProperty("RELEASE_KEY_PASSWORD") as String?
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Falls back to no signing config (build fails at packaging) if the
            // gradle.properties keys above aren't set — that's intentional: we never
            // want a release build to silently fall back to the debug keystore, since
            // shipping a debug-signed APK is what triggers AV heuristics in the first place.
            if (findProperty("RELEASE_STORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    androidResources {
        // Disable baseline profile to avoid INSTALL_BASELINE_PROFILE_FAILED on some devices
        generateLocaleConfig = false
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/baseline-prof.txt"
            excludes += "/baseline.prof"
            excludes += "/baseline.profm"
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
        viewBinding = true
    }
}

dependencies {
    // Nothing Glyph Matrix SDK — положите glyph-matrix-sdk-2.0.aar в app/libs/
    // (см. app/libs/README.txt). Все .aar из libs подключаются автоматически.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Жизненный цикл / сервис
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")

    // Фоновая работа
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Геолокация
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Корутины
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
