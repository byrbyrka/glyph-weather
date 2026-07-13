plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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

    buildTypes {
        release {
            isMinifyEnabled = false
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
