plugins {
    id("com.google.gms.google-services") version "4.5.0" apply false
    id("com.android.application") version "9.4.0" apply false
    // Not applied: AGP 9 compiles Kotlin itself. Declaring it pins the Kotlin Gradle plugin version.
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}