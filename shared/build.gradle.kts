plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(17)

    androidTarget()
    iosArm64()
    iosSimulatorArm64()
    // Target JVM HANYA sebagai inang uji cepat (bukan target rilis; tidak ada desktop). Lihat ADR-0002.
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime) // tipe publik (LocalDate, Instant) muncul di model
            api(libs.ktor.client.core)
            api(libs.sqldelight.runtime) // SqlDriver muncul di LapisanData (dipakai entri platform)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.driver.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.driver.native)
        }
        jvmMain.dependencies {
            // Hanya untuk inang uji (ADR-0002): driver SQLite di JVM (HTTP diuji dengan MockEngine).
            implementation(libs.sqldelight.driver.jvm)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

android {
    namespace = "com.pedaree.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("PedareeDb") {
            packageName.set("com.pedaree.shared.db")
        }
    }
}

kover {
    reports {
        filters { excludes { packages("com.pedaree.shared.db") } }
        verify { rule { minBound(80) } }
    }
}
