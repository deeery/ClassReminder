plugins {
    id("com.android.application")
    kotlin("android")
    // Enable kapt for Room annotation processor
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.example.classreminder"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.classreminder"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.01.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Navigation (simple)
    implementation("androidx.navigation:navigation-compose:2.6.0")

    // Notifications
    implementation("androidx.core:core-ktx:1.12.0")
}

