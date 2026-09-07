import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val firebaseConfig = Properties().apply {
    val config = file("firebase.properties")
    if (config.exists()) config.inputStream().use { load(it) }
}
val useEmulator = providers.gradleProperty("firebaseEmulator").orNull == "true"
fun configValue(key: String, fallback: String = "") =
    firebaseConfig.getProperty(key, fallback).replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.example.home_garden_system"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.home_garden_system"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "environment"
    productFlavors {
        create("demo") {
            dimension = "environment"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            resValue("string", "app_name", "Adiwiyata Demo")
        }
        create("live") {
            dimension = "environment"
            resValue("string", "app_name", "Smart Garden Adiwiyata")
            buildConfigField("boolean", "FIREBASE_EMULATOR", useEmulator.toString())
            buildConfigField("String", "FIREBASE_API_KEY", "\"${if (useEmulator) "demo-api-key" else configValue("API_KEY")}\"")
            buildConfigField("String", "FIREBASE_APP_ID", "\"${if (useEmulator) "1:1234567890:android:0123456789abcdef" else configValue("APP_ID")}\"")
            buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${if (useEmulator) "demo-smart-garden" else configValue("PROJECT_ID")}\"")
            buildConfigField("String", "FIREBASE_DATABASE_URL", "\"${if (useEmulator) "https://demo-smart-garden-default-rtdb.firebaseio.com" else configValue("DATABASE_URL")}\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    "liveImplementation"(platform("com.google.firebase:firebase-bom:34.2.0"))
    "liveImplementation"("com.google.firebase:firebase-auth")
    "liveImplementation"("com.google.firebase:firebase-database")
    "liveImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.matching { it.name.startsWith("preLive") && it.name.endsWith("Build") }.configureEach {
    doFirst {
        check(useEmulator || listOf("API_KEY", "APP_ID", "PROJECT_ID", "DATABASE_URL").all {
            !firebaseConfig.getProperty(it).isNullOrBlank()
        }) { "Live memerlukan app/firebase.properties. Salin config/firebase.properties.example atau gunakan -PfirebaseEmulator=true untuk pengujian lokal." }
        check(!useEmulator || !name.contains("Release")) { "Firebase emulator hanya boleh digunakan untuk debug." }
    }
}
