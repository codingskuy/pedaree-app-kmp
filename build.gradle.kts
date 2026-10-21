plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.kover) apply false
}

// `./gradlew uji`: uji unit :shared (JVM) + gerbang cakupan + kompilasi Android debug.
tasks.register("uji") {
    group = "verification"
    description = "Menjalankan uji unit :shared (JVM), gerbang cakupan, dan kompilasi Android composeApp."
    dependsOn(":shared:jvmTest", ":shared:koverVerify", ":composeApp:compileDebugKotlinAndroid")
}
