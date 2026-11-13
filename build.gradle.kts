plugins {
    alias(libs.plugins.android.application) apply false
    // Declaring the Kotlin plugins here puts KGP 2.4.21 on the build classpath,
    // above the older Kotlin that AGP 9's built-in Kotlin depends on.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
