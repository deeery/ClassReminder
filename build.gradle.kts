plugins {
    kotlin("jvm") version "1.9.20" apply false
    id("com.android.application") version "8.1.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
    // Add kapt plugin version so modules can apply kapt without specifying version
    id("org.jetbrains.kotlin.kapt") version "1.9.20" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

