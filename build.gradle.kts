// Plugins are declared here once so that all modules share one Kotlin Gradle plugin version.
// AGP 9 compiles Kotlin itself (built-in Kotlin), so there is no kotlin-android plugin.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
