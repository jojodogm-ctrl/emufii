import java.util.Properties

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


android {
    namespace = "eu.emufii.app"
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
        versionCode = 54
        versionName = "2.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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
        }
        create("preview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
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
}

dependencies {
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