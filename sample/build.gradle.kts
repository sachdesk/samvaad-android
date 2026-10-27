plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Push: add id("com.google.gms.google-services") here and your google-services.json
    // to this module. The sample builds without Firebase.
}

android {
    namespace = "in.yobruh.shop"
    compileSdk = 34

    defaultConfig {
        applicationId = "in.yobruh.shop"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":samvaad"))
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Push:
    // implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    // implementation("com.google.firebase:firebase-messaging")
}
