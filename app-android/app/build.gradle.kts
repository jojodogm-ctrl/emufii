import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Signing credentials live outside the repo, in a git-ignored properties file
 * at the project root. Without it (a fresh clone, or CI) the release build still
 * works, it just comes out unsigned rather than failing.
 */
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
        // 34: the automatic setup finally reaches Eden's "Optimized" and legacy
        // variants. The accessibility service holds its own package list,
        // separate from `<queries>`: an emulator missing from it sends the
        // service no events at all, so the form does not get filled in, with no
        // error and no log, and the press simply looks like it does nothing. A
        // test now compares the two lists.
        //
        // 33: the wait for the host finally applies to Switch sessions. 31
        // excluded it as soon as a room was running on the VPS, on the grounds
        // that nobody hosts there, but the room exists as soon as the session is
        // created, whereas the game the guest looks for in Eden only exists once
        // the host has been through. Every Switch session having a room, the
        // safeguard had never once shown up.
        //
        // 32: Emufii recognises every Eden variant, not only `dev.eden.*`. The
        // emulator ships as a matrix of packages, and the "Optimized" one
        // presents itself under Genshin Impact's identity: it was therefore
        // invisible, and the player read "not installed" in front of an emulator
        // that was very much there. When several are present, the last installed
        // wins.
        //
        // 31: the guest no longer presses into the void. Their setup button
        // waits until the host has opened the room: before, pressing first led
        // to "no session found" in the emulator, which reads as a breakdown when
        // it is a matter of a minute. Plus an OLED theme, whose black really does
        // turn the pixels off, measured at ~190 mA less on the Thor.
        //
        // 26: the tunnel address finally carries the session's prefix instead of
        // a /32. Eden gave the game the interface's mask along with the room's
        // address: at /32 the Switch computed a broadcast to itself and the guest
        // never received anything. Routing does not depend on that prefix, LDN
        // does. See docs/M19_SWITCH_LDN.md.
        //
        // 25: the "private session" switch no longer shows up where no session
        // is created, DS games and the PSP in public ad hoc. It offered a choice
        // there that changed nothing.
        //
        // 24: the Switch no longer hosts itself on a phone. The app states its
        // console when creating the session, the coordinator raises an Eden room
        // on the VPS, and both players join it, so nothing waits on a tunnel
        // coming up any more. A session can also be private: it leaves the public
        // list, and only its code gets you in. And the tunnel declares its MTU,
        // which stopped Switch LDN from falling over.
        //
        // 4: the multiplayer autofill had never once worked, an extension shadowed
        // by the member method of the same name, so every write went into a setter
        // that fails. Nobody had seen it because Azahar's in-game menu has never
        // run on a device. 1.3 also brings the Switch: Eden sessions, icons and
        // titles read out of the dumps.
        //
        // 3: 1.1 threw the guest out with "the host closed the session" as soon
        // as their own network hiccupped, and tore down a tunnel whose two peers
        // were both still up. A 1.1 in circulation keeps that flaw, so it has to
        // be replaced.
        //
        // 2: 1.0 targeted the Fly coordinator, shut down on 2026-07-27. The URL
        // is frozen at build time, so 1.0 can no longer open a session and has to
        // be replaced, hence a versionCode Android sees going up.
        // 20: the PSP with two players. The host's ad hoc directory announced to
        // the others an address that led to nobody, fixed on the relay side. The
        // session screen gains a button that opens PPSSPP for its manual setup,
        // and the help card finally says what really decides how the game feels:
        // the distance to the Wi-Fi box.
        //
        // 17: the tunnel keeps the Wi-Fi radio awake during a session, and beats
        // every 10 s instead of 25. Measured between two Thors: 25 % loss at one
        // packet per second, 0 % at three.
        //
        // 16: the install button said "not downloadable here" to a transfer that
        // had stalled, read timeout raised to 60 s, and only a 404 now counts as
        // "nothing to fetch".
        //
        // 15: Eden receives the profile's nickname (two players with the same
        // nickname cannot share a room), and both help cards require the same
        // game version, DLC included.
        //
        // 14: the update banner can install, checking the APK's signature before
        // handing it back to Android. 13 was never published, it served as the
        // test bench for 14, which it installed itself on the Thor.
        //
        // 12: the library listed PS2 and Xbox games, the same `.iso` extension as
        // a UMD, and a PSP session told the guest they did not have the game they
        // had right there in front of them.
        // 35: the GameCube and the Wii, through Dolphin's Android Netplay, landed
        // upstream on 2026-06-28. The driver stands apart, Dolphin's screen being
        // in Compose with no view ids at all, and `.iso` does not change hands: it
        // stays with the PSP by extension, and only the first 128 bytes can move a
        // file over to Dolphin.
        // 36: Dolphin's room finally receives the session's game. It kept the
        // device's last choice, and two players set off on Brawl with "Resident
        // Evil 4" written above.
        //
        // And above all: 35 circulated as several different binaries, the first
        // without the guest-path fixes. The update channel is decided on
        // `version_code` alone, so whoever had the first one never saw anything
        // coming, no banner, no way to notice. A published binary freezes its
        // number: rebuilding it under the same code makes the fix invisible to
        // the very people who need it most.
        // 45: ARMSX2 receives its network and memory-card settings through the
        // native per-game INI before boot. ISO and CHD games carry PCSX2's ELF
        // XOR in the library cache, so PS2 sessions launch in one tap without
        // accessibility navigation; the old driver remains only as a codec
        // fallback. A PSP session is refused until the PPSSPP setup is done,
        // mirroring the PS2 gate.
        // 46: the coordinator answers on a name of our own, `coord.emufii.xyz`,
        // instead of an address that carried the machine's IP. This build is the
        // first one that can survive a change of server: from here on, moving
        // machines is a DNS record and nobody reinstalls anything. Builds up to
        // 45 keep calling the old address, which is why that machine stays up.
        // 48: 47 shipped four-letter, four-digit session codes and could not join its
        // own: the join keypad draws six boxes and refuses the seventh key. Back to
        // three and three, and 47 is withdrawn. 48 rather than reusing 47, so two
        // different binaries never answer to one number in the release archive.
        versionCode = 48
        versionName = "1.12.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // arm64 only, because it is the only machine that plays.
        //
        // The WireGuard library ships four ABIs, and `libwg-go.so` weighs 3.4 MB
        // in each. The Thor is arm64; x86_64 only ever existed for the AVDs, and
        // it is given back to them in the debug variant below. A release has no
        // business carrying 3.4 MB no player will ever execute.
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
                // v1 and v2 are deliberately left off: minSdk is 33, so every
                // device that can install this verifies v3. Asking for them
                // anyway is ignored by AGP and only makes the config lie.
                enableV3Signing = true
            }
        }
    }

    // One coordinator for every build: the hosted one. Debug used to point at
    // 10.0.2.2:8787, the host machine as seen from an emulator, which meant a
    // debug build was unusable unless a local server happened to be running, and
    // reported "coordinator unreachable" when it wasn't. It also meant debug and
    // release were never talking to the same thing, so a coordinator that had
    // fallen behind in production stayed invisible during development.
    //
    // Override with -Pemufii.coordinatorUrl=… to point a build somewhere else.
    //
    // A name of our own since 2026-08-30, in place of `85-215-52-3.sslip.io`.
    // That address carried the machine's IP, so moving machines changed it, and
    // every APK already installed kept calling the old one, with no way back:
    // the update they would need is served by the address they can no longer
    // reach. A domain makes the next move a DNS record.
    val coordinatorUrl =
        project.findProperty("emufii.coordinatorUrl") ?: "https://coord.emufii.xyz"

    // The key that signs calls to the coordinator, see `network/ClientAuth.kt`.
    //
    // It never lives in the repo: it comes from the environment, or from a
    // Gradle property. Empty by default, and that is intended: a dev build then
    // sends no signature at all, and the local coordinator does not require one.
    // Only release builds meant for production get one.
    //
    // To be rotated at every version: the secret is extractable from the binary
    // by construction, so its worth is its freshness.
    val clientSecret = (project.findProperty("emufii.clientSecret") as String?)
        ?: System.getenv("EMUFII_CLIENT_SECRET")
        ?: ""

    // Write the accessibility tree to a file when the Dolphin driver gives up,
    // see `dolphin/DolphinTreeDump.kt`.
    //
    // Off by default, and it must stay that way. A dump names the games in the
    // grid and drops a file into the player's Downloads: acceptable for a
    // diagnostic build handed over in person, never for production. Turned on on
    // demand, for a binary that does not get published:
    //
    //     ./gradlew :app:assembleRelease -Pemufii.treeDump=true
    //
    // It exists because the only test bench is here: when the automation fails
    // for a remote player with no PC, there is no way to read their logcat, and
    // two hypotheses have already been spent guessing on their behalf.
    val treeDump = project.findProperty("emufii.treeDump")?.toString() == "true"

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // x86_64 lives here and nowhere else: it is the AVDs' ABI, and they
            // now serve only to reproduce a doubt (see CLAUDE.md).
            ndk {
                abiFilters += listOf("x86_64")
            }
            buildConfigField("String", "COORDINATOR_BASE_URL", "\"$coordinatorUrl\"")
            buildConfigField("String", "CLIENT_SECRET", "\"$clientSecret\"")
            buildConfigField("boolean", "TREE_DUMP", "$treeDump")
        }
        release {
            if (hasSigningConfig) signingConfig = signingConfigs.getByName("release")

            // R8, switched on 2026-08-19. It had been off with no explanation,
            // and that is what gave a 31 MB APK carrying 24 MB of dex.
            //
            // Resource shrinking comes with it: the app's only two dynamic
            // lookups, in `NetplayLabels` and `NetplayUiSupport`, target the
            // resources *of the emulator opposite* and never our own, so nothing
            // here is looked up by name at runtime.
            //
            // What R8 cannot work out on its own is in `proguard-rules.pro`, and
            // comes down to two things: our persisted enums, and WireGuard's
            // JNI.
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
    // Installs the compilation profile on first launch. Without it,
    // `src/main/baseline-prof.txt` travels in the APK and is never read.
    // pourquoi : the profile, and what it changes, see `baseline-prof.txt`
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
    implementation(libs.haze.materials)
    implementation(libs.wireguard.tunnel)
    implementation(libs.xz)
    implementation(libs.aircompressor)
    testImplementation(libs.junit)
    // `org.json` is a stub on the unit-test classpath: every call throws
    // "not mocked". The real implementation is tiny and dependency-free, and
    // pulling it in here is what lets code that stores JSON be tested without
    // a device.
    testImplementation("org.json:json:20260814")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}