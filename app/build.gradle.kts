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
        versionCode = 3
        versionName = "1.2"
        // 应用名走占位符：debug 变体可以额外带 -test 后缀
        manifestPlaceholders["appLabel"] = "StuMate"
    }

    buildTypes {
        debug {
            // 独立包名 + 独立应用名：装到手机上会和已安装的正式版共存，
            // 不会覆盖它的数据、设置和正在运行的 Service
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            manifestPlaceholders["appLabel"] = "StuMate-test"
        }
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
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
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

    // 课表 PDF 解析的单元测试（纯 JVM，不需要设备）
    testImplementation("junit:junit:4.13.2")
}

