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

// 版本号的**唯一来源**。绝不要在代码里手写版本字符串 ——
// 更新检查拿 `BuildConfig.VERSION_NAME` 去和 GitHub Release 的 tag 比，
// 一旦和这里对不上，用户要么收不到更新，要么被反复提示同一个版本。
//
// `-PstumateVersionName=1.5` / `-PstumateVersionCode=6` 是**只在构建时**生效的
// 验收开关（默认值就是下面那两个正式值）：装一个「自称旧版」的包，去对真实的
// v1.6 Release 走一遍「发现新版本 → 下载 APK → 拉起系统安装器 → 系统覆盖安装」。
// 桌面端对应的开关是 `-Dstumate.currentVersion`（那边是运行时系统属性，
// 安卓只能在构建期注入）。不传参数时产物与正式包**逐字节同源**。
//
// ⚠️ 验收包必须也用 **release 签名**（`assembleRelease`）：debug 签名和正式签名
// 不同，系统安装器会直接 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` ——
// 那样测的就不是更新链路，而是「签名不匹配」。
val appVersionName = providers.gradleProperty("stumateVersionName").getOrElse("1.5")
val appVersionCode = providers.gradleProperty("stumateVersionCode").getOrElse("6").toInt()

android {
    namespace = "com.example.classreminder"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.classreminder"
        minSdk = 21
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
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
        // AGP 8 起 BuildConfig 默认不生成。更新检查要读 VERSION_NAME
        // （版本比较的唯一来源，绝不能手写字符串常量），所以显式打开。
        buildConfig = true
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

