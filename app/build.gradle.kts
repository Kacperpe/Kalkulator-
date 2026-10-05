plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "pl.kacper.kalkulator"
    compileSdk = 34

    defaultConfig {
        applicationId = "pl.kacper.kalkulator"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.3"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // Stały klucz podpisu, żeby kolejne wersje APK z GitHub Actions
    // dało się instalować jako aktualizację na wierzch poprzedniej.
    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = "kalkulator"
            keyAlias = "kalkulator"
            keyPassword = "kalkulator"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    androidResources {
        noCompress += listOf("onnx")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Testy jednostkowe działają na zwykłej JVM, więc używają desktopowej wersji ONNX Runtime.
configurations.matching { it.name.contains("UnitTest") }.all {
    exclude(group = "com.microsoft.onnxruntime", module = "onnxruntime-android")
}

dependencies {
    implementation("androidx.core:core:1.12.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.microsoft.onnxruntime:onnxruntime:1.19.2")
}
