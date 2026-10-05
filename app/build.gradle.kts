// ⚠️ `Properties` 必须走 import，**不能写 `java.util.Properties()`** ——
// 在 Kotlin DSL 脚本里 `java` 会被解析成 JavaPluginExtension 那个隐式访问器，
// 于是 `java.util` 报 `Unresolved reference: util`，整个构建脚本编译不过。
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    // Enable kapt for Room annotation processor
    id("org.jetbrains.kotlin.kapt")
}

// 发布签名：密钥路径与口令放在仓库根目录的 keystore.properties（已 gitignore）。
// 文件不存在时**不报错** —— 否则别人 clone 下来连 debug 都构建不了。
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.example.classreminder"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.classreminder"
        minSdk = 21
        targetSdk = 34
        versionCode = 6
        versionName = "1.5"
        // 应用名走占位符：debug 变体可以额外带 -test 后缀
        manifestPlaceholders["appLabel"] = "StuMate"
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // 独立包名 + 独立应用名：装到手机上会和已安装的正式版共存，
            // 不会覆盖它的数据、设置和正在运行的 Service
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            manifestPlaceholders["appLabel"] = "StuMate-test"
        }
        release {
            // R8 压缩。这个 App **自身没有任何反射**（已全仓 grep
            // Class.forName / getDeclaredField / getDeclaredMethod 确认过），
            // Room / Compose / 协程都自带 consumer rules，裁剪是安全的。
            isMinifyEnabled = true
            // 刻意不开 shrinkResources：体积大头在代码侧，而资源裁剪对
            // 「运行时按名字取资源」的写法很敏感，收益不值当这个风险。
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 没配签名时留空：会产出 app-release-unsigned.apk（装不上，但至少能编译）
            signingConfig = signingConfigs.findByName("release")
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

