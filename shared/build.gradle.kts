plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "news.piaz.shared"
    compileSdk = 35
    defaultConfig { minSdk = 24 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    api(bom)
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.material:material-icons-extended")
    api("androidx.activity:activity-compose:1.9.3")
    api("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("io.coil-kt:coil-compose:2.7.0")
    val media3 = "1.5.1"
    api("androidx.media3:media3-exoplayer:$media3")
    api("androidx.media3:media3-exoplayer-hls:$media3")
    api("androidx.media3:media3-session:$media3")
    api("androidx.media3:media3-ui:$media3")
    api("com.google.guava:guava:33.3.1-android")
}
