plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.autoiprotator"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.autoiprotator"
        minSdk = 25
        targetSdk = 35
        versionCode = 1
        versionName = "2.0.0"

        val defaultWorkerUrl = ((project.findProperty("DEFAULT_WORKER_URL") as String?)
            ?: System.getenv("DEFAULT_WORKER_URL")
            ?: "")
            .trim()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        val defaultWorkerToken = ((project.findProperty("DEFAULT_WORKER_TOKEN") as String?)
            ?: System.getenv("DEFAULT_WORKER_TOKEN")
            ?: "")
            .trim()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        buildConfigField("String", "DEFAULT_WORKER_URL", "\"$defaultWorkerUrl\"")
        buildConfigField("String", "DEFAULT_WORKER_TOKEN", "\"$defaultWorkerToken\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Single WireGuard VPN Engine (custom .conf / QR / Worker nodes)
    implementation("com.wireguard.android:tunnel:1.0.20260102")

    // Open-Source No-VPS Multi-Country Network (Tor Project + tun2socks)
    implementation("info.guardianproject:tor-android:0.4.8.16.1")
    implementation("com.ooimi.library:tun2socks:1.0.4")

    // Firebase Auth + Firestore & Jetpack Credential Manager (Google Sign-In)
    implementation(platform("com.google.firebase:firebase-bom:34.0.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // CameraX + ML Kit Barcode Scanning for WireGuard QR import
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.camera:camera-mlkit-vision:1.4.2")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")
}
