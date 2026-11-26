import com.android.build.api.variant.impl.VariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.androidx.baselineprofile)
}

fun loadProperties(name: String): Properties = Properties().apply {
    val file = rootProject.file(name)
    if (file.exists()) file.inputStream().use { load(it) }
}

val applicationProperties = loadProperties("application.properties")
val keystoreProperties = loadProperties("keystore.properties")
val keystoreFile = keystoreProperties.getProperty("keystore.file", "")

val sha: String? = System.getenv("GITHUB_SHA")
val isCI: String? = System.getenv("CI")
val isSelfBuild = isCI.isNullOrEmpty() || !isCI.equals("true", ignoreCase = true)
var applicationVersionName: String = applicationProperties.getProperty("versionName")
val isPreRelease = applicationProperties.getProperty("isPreRelease").toBoolean()
val preReleaseName: String = applicationProperties.getProperty("preReleaseName")
val preReleaseVer = applicationProperties.getProperty("preReleaseVer").toInt()

/**
 * The version code follows from the name, so releases always order as their names do: major ×
 * 1,000,000 + minor × 10,000 + patch × 100 + stage, where alpha.n is 10 + n, beta.n 30 + n, rc.n
 * 60 + n and the release itself 99. So 1.0.0-alpha.1 < 1.0.0-beta.2 < 1.0.0 < 1.0.1.
 */
val applicationVersionCode: Int = run {
    val (major, minor, patch) = applicationVersionName.split(".").map { it.toInt() }
    require(minor in 0..99 && patch in 0..99) { "minor and patch must be 0–99: $applicationVersionName" }
    val stage = if (!isPreRelease) {
        99
    } else {
        require(preReleaseVer in 1..19) { "preReleaseVer must be 1–19: $preReleaseVer" }
        val base = when (preReleaseName) {
            "alpha" -> 10
            "beta" -> 30
            "rc" -> 60
            else -> error("preReleaseName must be alpha, beta or rc: $preReleaseName")
        }
        base + preReleaseVer
    }
    major * 1_000_000 + minor * 10_000 + patch * 100 + stage
}
if (isPreRelease) {
    applicationVersionName += "-$preReleaseName.$preReleaseVer"
}
// The release notes are named for the version alone, without the build's commit
val releaseNotesVersion = applicationVersionName
if (!isSelfBuild && !sha.isNullOrEmpty()) {
    applicationVersionName += "+${sha.substring(0, 7)}"
}

android {
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }
    defaultConfig {
        applicationId = "oolong.client"
        minSdk = 24
        targetSdk = 37
        versionCode = applicationVersionCode
        versionName = applicationVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        // Robolectric needs the merged resources for screenshot tests
        unitTests.isIncludeAndroidResources = true
    }
    androidResources {
        // The app itself is Chinese-only; drop the translations dependencies bring in.
        // Libraries ship Chinese only under region qualifiers, so a bare "zh" keeps none of it.
        localeFilters += listOf("en", "zh-rCN", "zh-rTW", "zh-rHK")
    }
    signingConfigs {
        if (keystoreFile.isNotBlank()) {
            create("config") {
                storeFile = file(File(rootDir, keystoreFile))
                storePassword = keystoreProperties.getProperty("keystore.password")
                keyAlias = keystoreProperties.getProperty("keystore.key.alias")
                keyPassword = keystoreProperties.getProperty("keystore.key.password")
                // Android 7.0, the oldest the app runs on, checks v2 and later;
                // v1 only adds a digest of every file
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }
    buildTypes {
        all {
            signingConfig =
                if (signingConfigs.any { it.name == "config" })
                    signingConfigs.getByName("config")
                else signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isDebuggable = false
            isJniDebuggable = false
            multiDexEnabled = true
        }
    }
    compileOptions {
        targetCompatibility = JavaVersion.VERSION_11
        sourceCompatibility = JavaVersion.VERSION_11
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "DebugProbesKt.bin"
        }
        jniLibs {
            // LiteRT's runtime is downloaded the first time tap-to-hide is used, not shipped
            excludes += "**/libtensorflowlite_jni.so"
        }
    }
    lint {
        // Existing issues are recorded here so that only new ones fail the build
        baseline = file("lint-baseline.xml")
        // Material 2 components still serve under our own theme; moving to Material 3 is its own job
        disable += "UsingMaterialAndMaterial3Libraries"
    }
    namespace = "oolong.client"
}

/** Puts this version's release notes, when written, among the app's assets as release-notes.json. */
abstract class BundleReleaseNotes : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val notes: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun bundle() {
        val dir = outputDir.get().asFile
        dir.deleteRecursively()
        dir.mkdirs()
        notes.files.firstOrNull { it.isFile }?.copyTo(File(dir, "release-notes.json"))
    }
}

val bundleReleaseNotes = tasks.register<BundleReleaseNotes>("bundleReleaseNotes") {
    notes.from(rootProject.file("tools/release/notes/$releaseNotesVersion.json"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(bundleReleaseNotes, BundleReleaseNotes::outputDir)
        // The builds the baseline profile is generated on install beside the app itself, so
        // installing and clearing them never touches a device's own install
        if (variant.name == "nonMinifiedRelease" || variant.name == "benchmarkRelease") {
            variant.applicationId.set("oolong.client.bp")
        }
        variant.outputs.forEach { output ->
            (output as VariantOutputImpl).outputFileName.set(
                "${variant.buildType}-$applicationVersionName($applicationVersionCode).apk"
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

roborazzi {
    // Reference screenshots are committed; verifyRoborazziDebug compares against them
    outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
}

// -PskipScreenshots leaves the screenshot tests out, for CI's check that runs them on their own
if (providers.gradleProperty("skipScreenshots").isPresent) {
    tasks.withType<Test>().configureEach {
        filter.excludeTestsMatching("*ScreenshotTest")
    }
}

ktlint {
    version.set(libs.versions.ktlint)
}

composeCompiler {
    // Pass -PcomposeCompilerReports=true to generate stability reports and metrics
    if (providers.gradleProperty("composeCompilerReports").orNull.toBoolean()) {
        reportsDestination = layout.buildDirectory.dir("compose_metrics")
        metricsDestination = layout.buildDirectory.dir("compose_metrics")
    }
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_stability_configuration.txt"))
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Local Files
//    implementation fileTree(include: ["*.jar"], dir: "libs")

    implementation(libs.godaddy.color.picker)

    implementation(libs.lottie)
    implementation(libs.lottie.compose)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(project(":core:network"))
    implementation(project(":core:designsystem"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.accompanist.drawablepainter)

    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.haze.glass)

    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.exifinterface)
    // Its native runtime is left out of the APK, see packaging
    implementation(libs.litert)
    implementation(libs.zxing.core)
    implementation(libs.coil.gif)
    implementation(libs.zoomimage.compose.coil3)

    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))
    testImplementation(platform(libs.compose.bom))

    // Release builds strip the trace calls (see proguard-rules.pro), so only its initializers would remain
    debugRuntimeOnly(libs.compose.runtime.tracing)
    implementation(libs.compose.animation)
    implementation(libs.compose.animation.graphics)
    implementation(libs.compose.material)
    implementation(libs.compose.ui.util)
//    implementation "androidx.compose.material3:material3"

    // Android Studio Preview support
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // UI Tests
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugRuntimeOnly(libs.compose.ui.test.manifest)

    implementation(libs.androidx.constraintlayout.compose)

    implementation(libs.oaid)

    implementation(libs.jetbrains.annotations)

    implementation(libs.kotlin.stdlib)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // AndroidX
    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.appfunctions)
    ksp(libs.androidx.appfunctions.compiler)
    // Installs the baseline profile on sideloaded installs, which no store compiles ahead
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    implementation(libs.androidx.window)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestRuntimeOnly(libs.androidx.test.runner)

    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.wire)

    implementation(libs.xxpermissions)
}
