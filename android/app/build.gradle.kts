import java.io.File
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.compose")
}

data class ReleaseSigning(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

// Private release key settings from BUDGIE_KEYSTORE_PROPERTIES or ../.release/keystore.properties.
val releaseSigning: ReleaseSigning? =
    (System.getenv("BUDGIE_KEYSTORE_PROPERTIES")?.let(::File)
            ?: rootProject.file("../.release/keystore.properties"))
        .takeIf { it.isFile }
        ?.let { propertiesFile ->
            // Values are read literally: java.util.Properties would strip the backslashes from a
            // hand-written Windows path.
            val values =
                propertiesFile.readLines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("!") && "=" in it }
                    .associate { it.substringBefore("=").trim() to it.substringAfter("=").trim() }
            fun required(name: String) = values[name] ?: error("$name is missing from $propertiesFile")
            val recorded = File(required("storeFile"))
                .let { if (it.isAbsolute) it else File(propertiesFile.parentFile, it.path) }
            // Use a keystore kept next to the properties file when the recorded path no longer exists.
            val keystore =
                listOf(recorded, File(propertiesFile.parentFile, recorded.name)).firstOrNull { it.isFile }
                    ?: error("Release keystore not found at $recorded")
            ReleaseSigning(keystore, required("storePassword"), required("keyAlias"), required("keyPassword"))
        }

android {
    namespace = "com.vabxsen.budgie"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.vabxsen.budgie"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    signingConfigs {
        releaseSigning?.let { signing ->
            create("release") {
                storeFile = signing.storeFile
                storePassword = signing.storePassword
                keyAlias = signing.keyAlias
                keyPassword = signing.keyPassword
            }
        }
    }
    buildTypes {
        release {
            // Signed when the private release key is available; otherwise the APK stays unsigned.
            if (releaseSigning != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
