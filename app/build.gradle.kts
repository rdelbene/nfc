plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rdelbene.lectornfc"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rdelbene.lectornfc"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("fija") {
            storeFile = rootProject.file("keystore/lectornfc.jks")
            storePassword = "lectornfc"
            keyAlias = "lectornfc"
            keyPassword = "lectornfc"
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("fija")
            isMinifyEnabled = false
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
