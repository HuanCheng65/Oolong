import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// The last version of the old app: under its package and key it replaces whatever version of
// it is installed, keeping that version's data, and only hands the data over to Oolong.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
}

ktlint {
    version.set(libs.versions.ktlint)
}

// The old app's release key, kept outside the repository (migration/keystore.properties)
val keystoreProperties = Properties().apply {
    val file = project.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val keystoreFile: String = keystoreProperties.getProperty("keystore.file", "")

android {
    namespace = "oolong.migration"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }
    defaultConfig {
        applicationId = "com.huanchengfly.tieba.post"
        minSdk = 24
        targetSdk = 37
        // Above every version of the old app (its last was 390102)
        versionCode = 390200
        versionName = "4.0.0-migration"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    signingConfigs {
        if (keystoreFile.isNotBlank()) {
            create("old") {
                storeFile = file(keystoreFile)
                storePassword = keystoreProperties.getProperty("keystore.password")
                keyAlias = keystoreProperties.getProperty("keystore.key.alias")
                keyPassword = keystoreProperties.getProperty("keystore.key.password")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (signingConfigs.any { it.name == "old" }) signingConfig = signingConfigs.getByName("old")
        }
    }
    compileOptions {
        targetCompatibility = JavaVersion.VERSION_11
        sourceCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    // The app's own shapes, spacing, type, motion and press feedback, so the dialog is the app's
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.activity.compose)
}
