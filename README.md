<div align="center">

<img src="logo.png" alt="Emufii" width="160">

# Emufii

**Play your emulators online with friends, using a six-character code.**<br>
No ports to open, no IP address to share. Built for Android handhelds.

[![Discord](https://img.shields.io/badge/Discord-Join_the_server-5865F2?logo=discord&logoColor=white)](https://discord.gg/tvWcb28vBZ)
[![Download](https://img.shields.io/badge/Download-APK-2b4c8c)](https://github.com/jojodogm-ctrl/emufii/releases/latest)
[![Games](https://img.shields.io/badge/Games-Compatible_list-2b4c8c)](COMPATIBLE-GAMES.md)
[![Version](https://img.shields.io/badge/Version-1.13-2ea043)](https://github.com/jojodogm-ctrl/emufii/releases/latest)
[![License](https://img.shields.io/badge/License-AGPL--3.0-0b7fbf)](LICENSE)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-Support-13C3FF?logo=kofi&logoColor=white)](https://ko-fi.com/emufii)

**[What it is](#what-emufii-is-and-isnt)** · **[How it works](#how-it-works)** · **[Consoles](#consoles)** · **[Install](#install)** · **[Privacy](#privacy)**

</div>

<br>

> [!IMPORTANT]
> **DS local wireless needs WatermelonDS Emufii Edition.**
> Starting with Emufii 2.0, DS local wireless games can be played online. This only works with [WatermelonDS Emufii Edition](https://github.com/jojodogm-ctrl/WatermelonDS/releases/latest), a fork of WatermelonDS that adds the netplay this needs.
>
> It installs next to the official WatermelonDS, so you keep your saves and settings. Once the official WatermelonDS includes the netplay, the fork won't be needed anymore. Online play through Kaeru WFC still works with any melonDS or WatermelonDS.

<br>

## What Emufii is (and isn't)

Emufii is an Android app that connects your emulators to your friends' emulators over the internet. The emulators think everyone is on the same Wi-Fi, so their own local multiplayer just works.

<table>
<tr>
<th width="50%">What it does</th>
<th width="50%">What it isn't</th>
</tr>
<tr>
<td valign="top">

**Connects the players.** A private network between the players for the length of a session, through Android's VPN feature. Only game traffic goes through it.

**Sets up the emulator.** Opens it on its multiplayer screen and fills in the connection details for you.

**Shows your games.** The games from your own ROM folder, with covers and a compatibility badge, to start a session from.

**Brings your friends.** Add them with a code, see who's online and what they're playing, and join their sessions.

</td>
<td valign="top">

**Not an emulator.** Every game runs in the emulator you already use. Emufii doesn't run any game itself.

**Not a frontend.** The game library is there to start multiplayer sessions. It doesn't replace your usual launcher.

**Not a downloader.** Emufii doesn't include, download or install games, BIOS files, keys or emulators. You install the emulators and use your own dumps.

**Not streaming.** Each player runs their own copy of the game on their own device.

**Not a way back to official servers.** Emufii never connects to Nintendo's or Sony's servers, so games that needed them don't work online. DS games through the Kaeru WFC revival server are the exception.

</td>
</tr>
</table>

<br>

## How it works

<table>
<tr>
<td align="center" width="25%" valign="top">
<h3>1. Host</h3>
Pick a game and share the six-character code.
</td>
<td align="center" width="25%" valign="top">
<h3>2. Join</h3>
Friends enter the code or pick a public session.
</td>
<td align="center" width="25%" valign="top">
<h3>3. Connect</h3>
Emufii opens the emulator and fills in the address.
</td>
<td align="center" width="25%" valign="top">
<h3>4. Play</h3>
Everyone plays as if on the same Wi-Fi.
</td>
</tr>
</table>

<br>

## Consoles

| Console | Emulator | Multiplayer mode | Version needed |
|---|---|---|---|
| **Switch** | [Eden](https://eden-emu.dev) | Local wireless | Any recent version |
| **3DS** | [Azahar](https://azahar-emu.org) | Local wireless | Pre-release 2126.0-rc or newer |
| **Wii / GameCube** | [Dolphin](https://dolphin-emu.org) | Netplay | Android build `2606a` |
| **PSP** | [PPSSPP](https://www.ppsspp.org) | Ad hoc | Any recent version |
| **PS2** | [ARMSX2](https://github.com/ARMSX2/ARMSX2) | System Link / LAN | Any recent version |
| **DS** | [WatermelonDS Emufii Edition](https://github.com/jojodogm-ctrl/WatermelonDS/releases/latest) | Local wireless | 0.8.0.rc2-emufii |
| **DS** | [melonDS](https://melonds.kuribo64.net) or WatermelonDS | Online via [Kaeru WFC](https://kaeru.world) | Any recent version |

> [!NOTE]
> **Which games work?** Games that had local multiplayer on the original console, and for the DS online mode, games that used Nintendo Wi-Fi Connection. See the [full list of compatible games](COMPATIBLE-GAMES.md).
>
> Every player needs the same emulator version and the same copy of the game.

<br>

## Install

> [!WARNING]
> **Beta.** Works on Android 13, 14 and 15. Expect bugs, and please report them on [Discord](https://discord.gg/tvWcb28vBZ).

1. Install the emulators you want to use.
2. [Download the latest APK](https://github.com/jojodogm-ctrl/emufii/releases/latest) and install it.
3. Open Emufii and follow the setup.

<details>
<summary><b>The autofill option is greyed out</b></summary>
<br>

Android blocks it for apps installed outside a store. Go to **App info → ⋮ → Allow restricted settings**, then turn Emufii on in **Settings → Accessibility**.

</details>

<details>
<summary><b>Updating</b></summary>
<br>

Download the new APK from the [releases page](https://github.com/jojodogm-ctrl/emufii/releases) and install it over the old one. Your settings are kept.

</details>

<details>
<summary><b>Checking the APK signature</b></summary>
<br>

```
21:EF:2D:D6:11:E0:96:5A:70:8F:61:F6:00:77:DE:97:D4:0D:59:FD:56:2F:1D:C5:F6:EF:6C:87:77:5E:81:D5
```

```sh
apksigner verify --print-certs Emufii-1.13.apk
```

</details>

<br>

## Privacy

| What | Where it goes |
|---|---|
| Session code, nickname, game | On the server for the length of the session, deleted when it ends |
| Profile picture | On the server, seen by your friends and the players in your sessions. Removing it in the app deletes it |
| Online status, last game | Seen by anyone who has your friend code |
| Friend code | Never shown to the other players of a session |
| Network traffic | Only game traffic goes through Emufii's network |
| Games, BIOS, keys | Never included. Use your own dumps |

> [!CAUTION]
> In a public session, other players' devices can reach yours, like on shared Wi-Fi. If you're playing with friends, keep the session private.

<br>

## License

[AGPL-3.0](LICENSE) (details in [NOTICE.md](NOTICE.md)). The app's source code is in this repository, but the session server and relay are private.

If you find a security issue, please report it privately on [Discord](https://discord.gg/tvWcb28vBZ).

<br>

## How it was built

**Emufii is built with [Claude Code](https://claude.com/claude-code), and I'd
rather tell you upfront.** About 90% of the code was written with the AI, along
with much of the research into how each emulator handles multiplayer.

That doesn't mean nobody is in charge. I decide the design and how the project
is built and tested, I review the code that goes in, and I test every feature on
real hardware before it ships.

**If you're not comfortable with software made this way, that's completely
fair.** Just know that the app and the servers have had a security review, with
every issue found fixed, and that experienced developers have started
contributing to the project.

If that sounds good to you,
**[grab the latest version](https://github.com/jojodogm-ctrl/emufii/releases/latest)**
and come say hi on [Discord](https://discord.gg/tvWcb28vBZ)!

<br>

## Credits

**Emulators.** Emufii only exists because of the people who build these emulators and their multiplayer: [Eden](https://eden-emu.dev), [Azahar](https://azahar-emu.org), [Dolphin](https://dolphin-emu.org), [PPSSPP](https://www.ppsspp.org), [ARMSX2](https://github.com/ARMSX2/ARMSX2), [melonDS](https://melonds.kuribo64.net) and its [Android port](https://github.com/rafaelvcaetano/melonDS-android) by rafaelvcaetano, and [WatermelonDS](https://github.com/SapphireRhodonite/WatermelonDS) by SapphireRhodonite.

**Online services.** [Kaeru WFC](https://kaeru.world), which keeps Nintendo Wi-Fi Connection alive for DS games.

**Contributors.** [@sofianeelhor](https://github.com/sofianeelhor) for the PPSSPP and ARMSX2 network automation, and [@BrianJr03](https://github.com/BrianJr03) for the manual session setup and uncropped covers.

**Libraries.** [WireGuard](https://www.wireguard.com) for the tunnel, [Coil](https://coil-kt.github.io/coil/) for images, and the [Rounded M+](https://github.com/google/fonts) font (OFL).

And everyone on the [Discord](https://discord.gg/tvWcb28vBZ) who tests, reports bugs and plays.
