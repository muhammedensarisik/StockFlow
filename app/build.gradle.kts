plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "com.stockflow.app"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.stockflow.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Emulator reaches the computer's localhost via 10.0.2.2. For a real phone pass
        // -Pstockflow.serverUrl=http://<computer-LAN-IP>:8000/ (or put it in gradle.properties).
        val serverUrl = providers.gradleProperty("stockflow.serverUrl").orElse("http://10.0.2.2:8000/").get()
        buildConfigField("String", "SYNC_BASE_URL", "\"$serverUrl\"")
    }
    buildTypes {
        // The local demo server speaks plain HTTP. Only debug builds may use it.
        debug { manifestPlaceholders["usesCleartextTraffic"] = "true" }
        release { manifestPlaceholders["usesCleartextTraffic"] = "false" }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Repository tests do not load application layouts, assets, or manifests.
    testOptions {
        unitTests.isIncludeAndroidResources = false
        // With a Turkish system locale "Windows".lowercase() becomes "wındows" (dotless ı) and
        // Robolectric cannot find its native library. Tests run with an English locale.
        unitTests.all { it.jvmArgs("-Duser.language=en", "-Duser.country=US") }
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
room { schemaDirectory("$projectDir/schemas") }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0")
    implementation("androidx.work:work-runtime-ktx:2.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
