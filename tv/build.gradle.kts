import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing comes from keystore.properties (never committed) or the environment.
// F-Droid builds unsigned and signs with its own key, so neither is needed there.
val keys = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun key(name: String): String? = keys.getProperty(name) ?: System.getenv("PIAZ_" + name.uppercase())

android {
    namespace = "news.piaz.tv"
    compileSdk = 35
    defaultConfig {
        // Its own ID, so app stores never offer the TV build to phones (F-Droid and IzzyOnDroid pick by version code).
        applicationId = "news.piaz.tv"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "3.1.1"
    }
    signingConfigs {
        if (key("storeFile") != null) create("release") {
            storeFile = rootProject.file(key("storeFile")!!)
            storePassword = key("storePassword"); keyAlias = key("keyAlias"); keyPassword = key("keyPassword")
        }
    }
    // Leave out Google's encrypted dependency report, which F-Droid and IzzyOnDroid reject.
    dependenciesInfo { includeInApk = false; includeInBundle = false }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.compose.material3:material3")
}
