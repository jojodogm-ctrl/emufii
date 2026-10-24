import java.security.MessageDigest

plugins { id("com.android.library") }

android {
    namespace = "eu.emufii.dsnemul"
    compileSdk { version = release(37) { minorApiLevel = 1 } }
    ndkVersion = "26.3.11579264"
    defaultConfig {
        minSdk = 33
        consumerProguardFiles("consumer-rules.pro")
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { arguments += "-DANDROID_STL=c++_static" } }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    externalNativeBuild.cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.22.1"
    }
}

dependencies {
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
}

val verifyPinnedNative by tasks.registering {
    val nativeDir = layout.projectDirectory.dir("src/main/jniLibs/arm64-v8a")
    inputs.files(nativeDir.file("libmpdsprobe.so"), nativeDir.file("libc++_shared.so"))
    doLast {
        val pins = mapOf(
            "libmpdsprobe.so" to "ca43f8efdeeccd19e0a17e4e801feb046b232ea277b20f5066d4ac8ae70a0fdf",
            "libc++_shared.so" to "83269b3124a287fac36b17c9fb64beb398c83dad6677b19ce63cf80d09966fd7",
        )
        pins.forEach { (name, expected) ->
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(nativeDir.file(name).asFile.readBytes()).joinToString("") { "%02x".format(it) }
            check(hash == expected) { "SILVER native pin mismatch: $name" }
        }
    }
}
tasks.named("preBuild").configure { dependsOn(verifyPinnedNative) }
