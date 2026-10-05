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
        versionCode = 3
        versionName = "1.2"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
