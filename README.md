<div align="center">

# Emufii

<img src="logo.png" alt="Emufii" width="300">

**One code, and you're in the same room.**

Emufii puts your devices on a single network for as long as you're playing.
Your emulators find each other exactly as they would over local Wi-Fi.

<br>

[![Version](https://img.shields.io/github/v/release/jojodogm-ctrl/emufii?label=VERSION&labelColor=3d4048&color=2ea043&style=for-the-badge&cacheSeconds=300)](https://github.com/jojodogm-ctrl/emufii/releases/latest)
[![License](https://img.shields.io/badge/LICENSE-APACHE--2.0-9C6BF0?labelColor=3d4048&style=for-the-badge)](LICENSE)

[![Platform](https://img.shields.io/badge/ANDROID-13%2B-33C7A6?labelColor=3d4048&style=for-the-badge&logo=android&logoColor=white)](#install)
[![Consoles](https://img.shields.io/badge/CONSOLES-3DS%20%7C%20SWITCH%20%7C%20PSP%20%7C%20DS-1b1e24?labelColor=3d4048&style=for-the-badge)](#consoles)

[![Discord](https://img.shields.io/badge/DISCORD-JOIN-5865F2?labelColor=3d4048&style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/tvWcb28vBZ)
[![Patreon](https://img.shields.io/badge/PATREON-SUPPORT-FF424D?labelColor=3d4048&style=for-the-badge&logo=patreon&logoColor=white)](https://patreon.com/Emufii)

<br>

**[Download](https://github.com/jojodogm-ctrl/emufii/releases/latest)** •
[Install](#install) •
[Consoles](#consoles) •
[Privacy](#privacy) •
[Discord](https://discord.gg/tvWcb28vBZ)

</div>

---

## What it does

Playing an emulator with a friend across the country used to mean forwarding a
port, digging up your public IP, sending it over, typing it in on both ends, and
starting again when your ISP changed it.

Emufii replaces all of that with a six-character code.

| | |
|---|---|
| **1. The host opens a session** | Pick a game, get a code. |
| **2. The code gets around** | Say it, paste it, or let your friends find the game in the list of open sessions. |
| **3. The guest joins** | Emufii opens the emulator on its multiplayer screen and fills in the address for them. |
| **4. You play** | Both sides need the same game. |

Emufii emulates nothing and implements no netplay of its own. It opens a
session, shares a network, and lets the emulator do what it already knows how to
do.

## Consoles

These emulators are installed separately, from their own sites. Emufii launches
them and stays out of the way.

| Console | Emulator | Worth knowing |
|---|---|---|
| **3DS** | [Azahar](https://azahar-emu.org) | Grab a **pre-release ≥ 2126.0-rc**. Earlier stable builds have no multiplayer screen at all. |
| **Switch** | [Eden](https://eden-emu.dev) | The lobby runs on our server, so nobody has to host the game from their phone. |
| **PSP** | [PPSSPP](https://www.ppsspp.org) | The console's own ad hoc mode, between two devices that are no longer in the same room. |
| **DS** | [melonDS](https://melonds.kuribo64.net) | **Online** play through Kaeru WFC. On AYN handhelds the preinstalled build is melonDS DualS. |

<details>
<summary><b>What won't work, and why</b></summary>

<br>

None of these are missing features. They're facts that sit outside the project.

- **DS local wireless.** The DS radio is emulated down at the physical layer,
  timing and all. No tunnel gets underneath that, and the melonDS authors say so
  themselves. DS online play is unaffected.
- **Wii.** Wiimmfi needs a NAND dump from a real console. A NAND can't be
  shared: handing one around gets the original console banned.
- **GameCube.** Only three games ever shipped with LAN support. There was
  nothing to unlock.

</details>

## No games, no BIOS, no keys

Emufii ships none of them and never will. You bring your own dumps of your own
cartridges and discs. This is a rule of the project rather than an oversight,
and every release is checked against the binary before it goes out.

## Install

The app is signed but distributed outside any store, so Android will warn you
about an unknown source.

**1. Install the emulators** you plan to use, from the sites in the table above.

**2. [Download the latest release](https://github.com/jojodogm-ctrl/emufii/releases/latest)** and install it.

**3. Open Emufii and walk through setup:** your games folder, a nickname,
notifications, and the autofill permission.

### The autofill permission, and why it looks broken

Emufii can type the session address into the emulator's multiplayer screen so
you never have to copy it by hand. That runs as an accessibility service, and
Android treats those as a **restricted setting** for anything installed outside
a store. The toggle stays greyed out until you say otherwise.

> **App info → ⋮ menu, top right → "Allow restricted settings"**, then go back
> to Settings → Accessibility and switch Emufii on.

Turning it down costs you nothing but a few seconds of typing, since the address
is shown in the session anyway. Emufii only ever reads the screens of the
emulators it declares, and only while a session you started is running.

### Checking the APK is ours

Every build of Emufii carries this certificate:

```
21:EF:2D:D6:11:E0:96:5A:70:8F:61:F6:00:77:DE:97:D4:0D:59:FD:56:2F:1D:C5:F6:EF:6C:87:77:5E:81:D5
```

```sh
apksigner verify --print-certs Emufii-1.10.8.apk
```

A copy carrying any other signature isn't ours, and Android will refuse to
install it over a genuine Emufii regardless.

## Privacy

- Your profile picture never leaves the device. Neither does your friends list.
- The server holds a session code, a nickname, a game title, and an address that
  lives as long as the game does. Nothing survives the session.
- The tunnel carries session traffic and nothing else. Your browsing, your app
  updates, everything else: none of it passes through our machines.
- No cloud backup, and no device transfer. Console keys the app can read stay on
  the phone.

### Before you join strangers

A session is a network shared between its players. That's what makes the
multiplayer work, and it's worth understanding what it implies. Sessions are
walled off from one another, but inside a session the devices can reach each
other. Joining a public room is close to plugging into a stranger's home
network, and deserves the same caution.

Between people who already know each other, the code travels hand to hand and
the game never appears in any list.

## Updates

Emufii tells you when a newer build exists and can install it for you. It
downloads only from its own server, and it verifies the signature before
offering you anything: a build that doesn't carry our key is refused, and so is
a rollback to an older one.

Nothing installs until you tap Install.

## License

**Apache-2.0.** See [`LICENSE`](LICENSE) and [`NOTICE.md`](NOTICE.md).

Poppins is used under the SIL OFL 1.1, whose text ships inside the APK at
`assets/POPPINS-OFL.txt`.

The source is not published at this time. This repository exists to distribute
the app, publish its release notes, and tell you what you're installing.
