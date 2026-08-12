# NOTICE

Copyright 2026 Emufii contributors

Emufii is distributed under the **GNU Affero General Public License, version
3**. The full text is in [`LICENSE`](LICENSE).

Releases up to and including 1.10.8 went out under Apache-2.0. That grant stands
for those builds; everything from 1.10.9 onward is AGPL-3.0.

The name and the logo are trademarks of the project and are not covered by the
licence. The server side, which brokers sessions and runs the WireGuard relay,
is a separate program and is not published.

## Third-party components

### WireGuard, the session network layer

- Source: <https://github.com/WireGuard/wireguard-android>
- Artifact: `com.wireguard.android:tunnel`
- Licence: **Apache License 2.0**

An ordinary Maven dependency, used through its userspace backend (`GoBackend`)
on top of Android's `VpnService`. No native binary is vendored.

### Android libraries

AndroidX and Jetpack Compose, Coil, Haze (`dev.chrisbanes.haze`) and the Kotlin
standard library, all under the **Apache License 2.0**, which is compatible with
the AGPL-3.0 in this direction.

### Poppins

Poppins, by Indian Type Foundry, Jonny Pinhorn and Ninad Kale, under the **SIL
Open Font License 1.1**. Taken from Google Fonts
(`github.com/google/fonts/ofl/poppins`) rather than from a third-party binary.

The SIL OFL asks for more than attribution: its text must **travel with the font
software**, in every copy and therefore inside the APK. It ships at
`assets/POPPINS-OFL.txt`. That is the only obligation any third-party component
places on Emufii, and this file discharges it.

## What Emufii does not include

Emufii **contains no emulator code**. Azahar, Eden, PPSSPP and melonDS are
launched by intent, as third-party apps installed separately, so their
respective licences do not reach this repository.

Likewise no ROM, no BIOS image and no console key. That is a project invariant,
checked against the binary on every release.
