plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.ocrserver"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.ocrserver"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // ML Kit Text Recognition
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.0")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.0")
    implementation("com.google.mlkit:text-recognition-korean:16.0.0")
    implementation("com.google.mlkit:language-id:17.0.4")
    
    // Tesseract OCR for Russian
    implementation("com.rmtheis:tess-two:9.1.0")
    
    // Play Services Tasks (for coroutines support)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // HTTP Server
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    
    // WebSocket
    implementation("org.java-websocket:Java-WebSocket:1.5.4")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Jetpack
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    // JSON
    implementation("org.json:json:20231013")
}

