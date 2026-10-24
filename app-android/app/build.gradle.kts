import java.util.Properties
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Without keystore.properties the release build comes out unsigned.
val keystoreProperties = Properties().apply {
    val f = rootProject.file("../keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasSigningConfig = keystoreProperties.getProperty("storeFile") != null

// Invitations never choose an executable package. Only preview can target the pinned diagnostic client.
val oneTapPreviewNativePackage = project.findProperty("emufii.oneTapNativePackage")
    ?.toString()?.trim() ?: "com.mpds.online.singleowner.dev"
require(oneTapPreviewNativePackage in setOf(
    "com.mpds.online.singleowner.dev",
    "com.mpds.online.singleowner.emufii.metrics"
)) { "Unsupported One Tap native target" }


android {
    namespace = "eu.emufii.app"
    ndkVersion = "26.3.11579264"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "eu.emufii.app"
        minSdk = 33
        targetSdk = 36
        // Never reuse a published versionCode: the update check compares it alone.
        versionCode = 55
        versionName = "2.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "ONE_TAP_NATIVE_PACKAGE", "\"com.mpds.online.singleowner.dev\"")

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = rootProject.file("../${keystoreProperties.getProperty("storeFile")}")
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                enableV3Signing = true
            }
        }
    }

    val coordinatorUrl =
        project.findProperty("emufii.coordinatorUrl") ?: "https://coord.emufii.xyz"

    // Signs coordinator calls; empty in dev builds. Rotate it each release, it ships in the APK.
    val clientSecret = (project.findProperty("emufii.clientSecret") as String?)
        ?: System.getenv("EMUFII_CLIENT_SECRET")
        ?: ""

    // Dumps the a11y tree into Downloads. Never enable in a published build.
    val treeDump = project.findProperty("emufii.treeDump")?.toString() == "true"

    // Remain disabled until the adapter and integrated frontend have been deployed.
    val oneTapAdapterUrl = project.findProperty("emufii.oneTapAdapterUrl")?.toString()?.trim().orEmpty()
    val oneTapFrontendUrl = project.findProperty("emufii.oneTapFrontendUrl")?.toString()?.trim().orEmpty()
    fun validateOneTapUrl(value: String, path: String) {
        if (value.isEmpty()) return
        val uri = runCatching { URI(value) }.getOrNull()
        require(uri != null && uri.scheme == "https" && !uri.host.isNullOrEmpty() &&
            uri.rawUserInfo == null && uri.port == -1 && uri.rawQuery == null && uri.rawFragment == null &&
            uri.rawPath.orEmpty() == path && uri.toASCIIString() == value) {
            "One Tap configuration requires an HTTPS URL with path '$path' and no credentials/query/fragment"
        }
    }
    validateOneTapUrl(oneTapAdapterUrl, "")
    validateOneTapUrl(oneTapFrontendUrl, "/emufii/play/")

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            ndk {
                abiFilters += listOf("x86_64")
            }
            buildConfigField("String", "COORDINATOR_BASE_URL", "\"$coordinatorUrl\"")
            buildConfigField("String", "CLIENT_SECRET", "\"$clientSecret\"")
            buildConfigField("boolean", "TREE_DUMP", "$treeDump")
            buildConfigField("String", "ONE_TAP_ADAPTER_URL", "\"$oneTapAdapterUrl\"")
            buildConfigField("String", "ONE_TAP_FRONTEND_URL", "\"$oneTapFrontendUrl\"")
        }
        release {
            if (hasSigningConfig) signingConfig = signingConfigs.getByName("release")

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "COORDINATOR_BASE_URL", "\"$coordinatorUrl\"")
            buildConfigField("String", "CLIENT_SECRET", "\"$clientSecret\"")
            buildConfigField("boolean", "TREE_DUMP", "$treeDump")
            buildConfigField("String", "ONE_TAP_ADAPTER_URL", "\"$oneTapAdapterUrl\"")
            buildConfigField("String", "ONE_TAP_FRONTEND_URL", "\"$oneTapFrontendUrl\"")
        }
        create("preview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            buildConfigField("String", "ONE_TAP_NATIVE_PACKAGE", "\"$oneTapPreviewNativePackage\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Physical DS tests may use an app-local localhost CA generated outside
    // sources. This opt-in changes debug resources only; normal builds retain
    // their existing trust policy, and release/preview never contain the CA.
    if (providers.gradleProperty("emufii.dsDeviceFixture").orNull == "true") {
        val fixtureResources = rootProject.file("../build/device/tls/res")
        require(fixtureResources.resolve("raw/ds_device_fixture_ca.crt").isFile &&
            fixtureResources.resolve("xml/network_security_config.xml").isFile) {
            "Generate the private localhost device fixture before its debug build"
        }
        sourceSets.getByName("debug").res.setSrcDirs(listOf(fixtureResources))
    }
    packaging {
        jniLibs {
            keepDebugSymbols += listOf("**/libmelonjni.so", "**/libmpdsprobe.so", "**/libc++_shared.so")
            // Both runtimes use the byte-identical pinned Android C++ library.
            pickFirsts += "**/libc++_shared.so"
        }
    }
}

dependencies {
    implementation(project(":ds-hle-runtime"))
    implementation(project(":ds-nemul-runtime"))
    implementation(platform(libs.androidx.compose.bom))
    // Without it baseline-prof.txt ships but is never applied.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.haze)
    implementation(libs.backdrop)
    implementation(libs.haze.materials)
    implementation(libs.wireguard.tunnel)
    implementation(libs.xz)
    implementation(libs.aircompressor)
    testImplementation(libs.junit)
    // org.json is a throwing stub on the unit-test classpath.
    testImplementation("org.json:json:20260814")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
