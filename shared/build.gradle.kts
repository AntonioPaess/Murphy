plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    explicitApi()

    androidTarget()
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.murphy.shared"
    compileSdk = 35
}
