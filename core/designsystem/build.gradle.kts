import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// What the app's interface is made of and other apps of ours share: shapes, spacing, type,
// motion and press feedback. Its package matches where these lived in the app.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
}

ktlint {
    version.set(libs.versions.ktlint)
}

android {
    namespace = "oolong.core.designsystem"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        targetCompatibility = JavaVersion.VERSION_11
        sourceCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.foundation)
    api(libs.compose.ui)
    api(libs.compose.animation)
    // The press veil takes Material's content colour, as everything in the app provides it
    api(libs.compose.material)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
