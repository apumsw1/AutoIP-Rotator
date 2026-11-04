
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.autoiprotator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.autoiprotator"
        minSdk = 25
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    implementation("com.wireguard.android:tunnel:1.0.20260102")
    implementation("me.proton.vpn:vpn-core-internal:2.2.1-1.0.44")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")
}
