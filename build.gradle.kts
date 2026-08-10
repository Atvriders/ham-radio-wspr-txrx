plugins {
    alias(libs.plugins.android.application) apply false
    // No kotlin-android plugin: AGP 9 compiles Kotlin itself (built-in Kotlin) and
    // applying org.jetbrains.kotlin.android alongside it fails with "Cannot add
    // extension with name 'kotlin'". The Kotlin *compiler plugins* below are still
    // applied normally, and — because the parcelize plugin marker depends on the full
    // kotlin-gradle-plugin — declaring them here is what pins the KGP on the buildscript
    // classpath to libs.versions.toml's `kotlin`, above the older KGP that AGP bundles.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
