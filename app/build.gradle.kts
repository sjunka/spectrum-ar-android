import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.sjunka.spectrumar"
    // SceneView 4.x AARs declare minCompileSdk = 37; targetSdk stays at 36.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sjunka.spectrumar"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Real phones and the arm64 emulator only: keeps the APK small enough for git.
        ndk { abiFilters += setOf("arm64-v8a") }
    }

    buildTypes {
        release {
            // Release-signed with the debug keystore so it installs without a store key (performance parity).
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures { compose = true }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*")
        jniLibs.useLegacyPackaging = false
    }
    androidResources { noCompress += setOf("bin", "json") }

    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    implementation("io.github.sceneview:sceneview:4.65.0")
    implementation("io.github.sceneview:arsceneview:4.65.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
