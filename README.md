<div align="center">

# Emufii

<img src="logo.png" alt="Emufii" width="300">

**One code, and you're in the same room.**

Emufii puts your devices on a single network for as long as you're playing.
Your emulators find each other exactly as they would over local Wi-Fi.

**[Grab the latest build here](https://github.com/jojodogm-ctrl/emufii/releases/latest)**,
then come and say hello on Discord. That is where problems get reported and
fixed, and there will be problems.

<br>

[![Beta](https://img.shields.io/badge/BETA-EXPECT%20BUGS-FF7A18?labelColor=3d4048&style=for-the-badge)](#this-is-a-beta)
[![Version](https://img.shields.io/badge/VERSION-1.11.6-2ea043?labelColor=3d4048&style=for-the-badge)](https://github.com/jojodogm-ctrl/emufii/releases/latest)
[![License](https://img.shields.io/badge/LICENSE-AGPL--3.0-9C6BF0?labelColor=3d4048&style=for-the-badge)](LICENSE)

[![Platform](https://img.shields.io/badge/ANDROID-13%20ONLY-33C7A6?labelColor=3d4048&style=for-the-badge&logo=android&logoColor=white)](#install)
[![Consoles](https://img.shields.io/badge/CONSOLES-3DS%20%7C%20SWITCH%20%7C%20WII%20%7C%20GAMECUBE%20%7C%20PSP%20%7C%20DS-1b1e24?labelColor=3d4048&style=for-the-badge)](#consoles)

[![Discord](https://img.shields.io/badge/DISCORD-JOIN-5865F2?labelColor=3d4048&style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/tvWcb28vBZ)
[![Ko-fi](https://img.shields.io/badge/KO--FI-SUPPORT-13C3FF?labelColor=3d4048&style=for-the-badge&logo=kofi&logoColor=white)](https://ko-fi.com/emufii)

<br>

**[Download](https://github.com/jojodogm-ctrl/emufii/releases/latest)** •
[Install](#install) •
[Consoles](#consoles) •
[Privacy](#privacy) •
[Discord](https://discord.gg/tvWcb28vBZ)

</div>

---

## This is a beta

**Emufii is not stable yet, and you are going to run into plenty of problems.**
Sessions that don't come up, an emulator that ignores the autofill, a game that
desyncs for a reason nobody has pinned down yet. That is the honest state of it.

It is worth saying plainly rather than letting you find out: this is early
software, built against emulators that keep moving underneath it.

**Android 13, and one device.** Everything you read here was tested on an
**AYN Thor running Android 13**. That is the whole test bench, and there is no
second one. Android will happily install Emufii on 14, 15 or 16 because nothing
stops it, but none of those have been tried: if you run one, you are the first,
and we would genuinely like to hear how it went. Anything older than 13 will not
install at all.

**When something breaks, come and say so on
[Discord](https://discord.gg/tvWcb28vBZ).** Downloading takes one click and asks
nothing of you, so this is the part that actually matters: a bug nobody reports
is a bug that stays, and half of what works today was fixed because someone
described what they saw on their own device. Tell us the console, the emulator
build, and what you expected to happen.

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

Where the host has to open the room first, the guest's button says so and waits
instead of sending them into an emulator with nothing to find.

Emufii emulates nothing and implements no netplay of its own. It opens a
session, shares a network, and lets the emulator do what it already knows how to
do.

## Consoles

These emulators are installed separately, from their own sites. Emufii launches
them and stays out of the way.

**Not every console means the same thing by "multiplayer",** and that changes
which games work. Four of the five are the console's *local* mode, the one that
used to need everyone in the same room: those games work, and games that only
ever had online servers do not. The DS is the exception and goes to real
servers.

| Console | Emulator | What you're actually playing | Which games work |
|---|---|---|---|
| **3DS** | [Azahar](https://azahar-emu.org) | **Local wireless, over distance.** Azahar's own room system, the thing that stands in for two 3DS consoles sitting next to each other. Emufii puts you both in one room without either of you hosting a public server. | The games that had **local play / download play**. Nintendo Network games, no. |
| **Switch** | [Eden](https://eden-emu.dev) | **Local wireless (LDN), over distance.** Same idea, except the lobby runs on our server rather than on someone's phone, so nobody has to be the host and nobody's battery decides when the session ends. | The games with **local wireless play**. Nintendo Switch Online, no. |
| **Wii** and **GameCube** | [Dolphin](https://dolphin-emu.org) | **Netplay.** Different in kind: Dolphin runs both emulators in lockstep and sends your controller inputs across, so the game behaves as if you were both holding a pad on one console. | Anything **same-console multiplayer**, split-screen and versus included. That's most of the library. Not Nintendo's old servers. |
| **PSP** | [PPSSPP](https://www.ppsspp.org) | **Ad hoc, over distance.** The PSP's own system-link mode, between two devices that are no longer in the same room. | The **ad hoc** games. Infrastructure mode, no. |
| **DS** | [melonDS](https://melonds.kuribo64.net) | **Real online.** The only one here that isn't local play pretending: the game talks Nintendo Wi-Fi Connection, and Emufii points it at the [Kaeru WFC](https://kaeru.world) revival servers instead of the servers Nintendo shut down in 2014. | The **Nintendo WFC** games, with strangers as well as friends. DS local wireless, no, and it can't be done. |

Practical bits: Azahar needs a **pre-release ≥ 2126.0-rc**, since the stable
builds have no multiplayer screen at all. Dolphin needs the **Android build the
site lists as `2606a`**, which reports itself as `2606-302` once installed. Any
current Eden works, including the Optimized one that installs under another
name. On AYN handhelds the preinstalled melonDS is melonDS DualS.

**Both players need the same emulator build and the same dump of the same
game.**

<details>
<summary><b>What won't work, and why</b></summary>

<br>

None of these are missing features. They're facts that sit outside the project.

- **DS local wireless.** The DS radio is emulated down at the physical layer,
  timing and all. No tunnel gets underneath that, and the melonDS authors say so
  themselves. DS online play is unaffected.
- **Nintendo's own Wii servers.** Reaching them through Wiimmfi needs a NAND
  dump from a real console, and a NAND can't be shared: handing one around gets
  the original console banned. Playing Wii and GameCube games together does not
  go that way: it goes through Dolphin's netplay, which needs none of it.

</details>

## No games, no BIOS, no keys

Emufii ships none of them and never will. You bring your own dumps of your own
cartridges and discs. This is a rule of the project rather than an oversight,
and every release is checked against the binary before it goes out.

## Install

> **Android 13, on an AYN Thor.** That is the only combination Emufii has ever
> been tested on. Newer Android versions will let it install, and nobody has
> checked what happens next.

The app is signed but distributed outside any store, so Android will warn you
about an unknown source. Every build is published here, and announced on
[Discord](https://discord.gg/tvWcb28vBZ), which is also where you tell us what
broke, and the project still very much needs that.

**1. Install the emulators** you plan to use, from the sites in the table above.

**2. [Download the latest build](https://github.com/jojodogm-ctrl/emufii/releases/latest)** and install it.

**3. Open Emufii and walk through setup:** your games folder, a nickname,
notifications, and the autofill permission.

The app speaks English and French, and comes in light, dark and OLED black. All
three are in the profile page, along with the folder you scan for games.

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
apksigner verify --print-certs Emufii-1.11.6.apk
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

**AGPL-3.0.** See [`LICENSE`](LICENSE) and [`NOTICE.md`](NOTICE.md).

In plain terms: study it, change it, pass it on. Whatever you build from it
carries the same licence and ships its source, including when you only ever run
it as a service. Nobody gets to close this code and sell it back.

The Emufii name and the logo are not part of that grant. Forks are fine. Forks
calling themselves Emufii are not.

The server side, which brokers sessions and runs the relay, is a separate
program and is not published. Copyright stays with the project, so terms outside
the AGPL can be arranged: ask on [Discord](https://discord.gg/tvWcb28vBZ).

Poppins is used under the SIL OFL 1.1, whose text ships inside the APK at
`assets/POPPINS-OFL.txt`.

The app source is not published yet. The builds are: this page exists to say
what Emufii is and what it does with your data, before you install one.

---

## Security

Emufii has been through a security review covering the app, the session broker
and the relay: how clients are authenticated, how sessions are walled off from
one another, what the tunnel is allowed to carry, and how updates are verified
before they install.

Every flaw it turned up has been fixed, with two exceptions that are not flaws
so much as consequences of what the app is, and those are documented rather than
patched:

- **A session is a shared network.** That is the feature, and it is why the
  emulators find each other at all. It also means the devices in a session can
  reach each other, which is why [joining strangers](#before-you-join-strangers)
  deserves the same thought as plugging into someone's home network.
- **Public sessions are listed.** A room you open publicly shows a nickname, a
  game and a code, because that is what makes it findable. Sessions you keep
  private appear in no list, and the code alone gets people in.

That is a statement about what was looked for and found, not a promise that
nothing is left. No review catches everything, and this is beta software.

**If you find something, please report it on
[Discord](https://discord.gg/tvWcb28vBZ)** rather than posting it publicly, and
it will be looked at quickly. Sessions are shared networks between real people's
devices, so a flaw here is worth taking seriously.

## How it was built

Emufii is co-coded with [Claude Code](https://claude.com/claude-code). The
design decisions, the testing on real hardware and the calls about what ships
are the author's; a good deal of the code, the scouting of emulator internals
and the reviews were done alongside the model.

Said out loud because it is part of how this project is made, and you are
entitled to know what is behind the build you install.
