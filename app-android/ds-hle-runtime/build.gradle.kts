import java.net.URI

plugins {
    id("com.android.library")
}

// Application build configuration owns origins. No Intent/invitation may
// override these endpoints. Default remains the pinned production contract.
fun endpoint(property:String,fallback:String,scheme:String):String {
    val value=providers.gradleProperty(property).orElse(fallback).get().trim()
    val uri=runCatching {URI(value)}.getOrNull()
    require(uri!=null&&uri.scheme==scheme&&!uri.host.isNullOrEmpty()&&uri.rawUserInfo==null&&
        uri.rawQuery==null&&uri.rawFragment==null&&uri.toASCIIString()==value&&
        !uri.rawPath.orEmpty().endsWith("/")&&uri.rawPath.orEmpty().startsWith("/")) {
        "Embedded GOLD endpoint must be an application-owned secure URL"
    }
    return value
}
val coordinatorOrigin=providers.gradleProperty("emufii.coordinatorUrl").orElse("https://coord.emufii.xyz").get().trim().removeSuffix("/")
val coordinatorUri=URI(coordinatorOrigin)
require(coordinatorUri.scheme=="https"&&!coordinatorUri.host.isNullOrEmpty()&&coordinatorUri.rawUserInfo==null&&
    coordinatorUri.rawPath.isNullOrEmpty()&&coordinatorUri.rawQuery==null&&coordinatorUri.rawFragment==null)
val directoryEndpoint=endpoint("emufii.dsGoldDirectoryUrl","$coordinatorOrigin/ds/nifi2/rooms","https")
val gatewayEndpoint=endpoint("emufii.dsGoldGatewayUrl","${coordinatorOrigin.replaceFirst("https://","wss://")}/ds/nifi2/wire","wss")
val gatewayUri=URI(gatewayEndpoint)
val gatewayOrigin=URI("https",gatewayUri.rawAuthority,null,null,null).toASCIIString()

android {
    // The pinned native library exports Java_com_mpds_online_NativeBridge_*.
    namespace = "com.mpds.online"
    compileSdk { version = release(37) { minorApiLevel = 1 } }
    ndkVersion = "26.3.11579264"
    defaultConfig {
        minSdk = 33
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("boolean", "SINGLE_OWNER", "true")
        buildConfigField("boolean", "SDK_BLOCKS", "true")
        buildConfigField("boolean", "AUTHORITY_CHECKPOINTS", "true")
        buildConfigField("String","DIRECTORY_ENDPOINT","\"$directoryEndpoint\"")
        buildConfigField("String","GATEWAY_ENDPOINT","\"$gatewayEndpoint\"")
        buildConfigField("String","GATEWAY_ORIGIN","\"$gatewayOrigin\"")
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild.cmake.arguments += "-DANDROID_STL=c++_static"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
    externalNativeBuild.cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.22.1"
    }
    packaging.jniLibs.keepDebugSymbols += listOf("**/libmelonjni.so", "**/libc++_shared.so")
    // The GOLD native runtime is prebuilt and pin-verified, never rebuilt here.
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation(libs.junit)
}
