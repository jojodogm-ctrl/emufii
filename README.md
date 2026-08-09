<div align="center">

# Emufii

<img src="logo.png" alt="Emufii" width="300">

**Un code, et vous êtes dans la même pièce.**

Emufii met vos appareils sur le même réseau le temps d'une partie.
Vos émulateurs se voient comme s'ils étaient sur le même canapé.

<br>

[![Version](https://img.shields.io/github/v/release/jojodogm-ctrl/emufii?label=VERSION&labelColor=3d4048&color=2ea043&style=for-the-badge)](https://github.com/jojodogm-ctrl/emufii/releases/latest)
[![Téléchargements](https://img.shields.io/github/downloads/jojodogm-ctrl/emufii/total?label=T%C3%89L%C3%89CHARGEMENTS&labelColor=3d4048&color=2ea043&style=for-the-badge)](https://github.com/jojodogm-ctrl/emufii/releases)
[![Licence](https://img.shields.io/badge/LICENCE-APACHE--2.0-9C6BF0?labelColor=3d4048&style=for-the-badge)](LICENSE)

[![Plateforme](https://img.shields.io/badge/ANDROID-13%2B-33C7A6?labelColor=3d4048&style=for-the-badge&logo=android&logoColor=white)](#installation)
[![Consoles](https://img.shields.io/badge/CONSOLES-3DS%20%7C%20SWITCH%20%7C%20PSP%20%7C%20DS-1b1e24?labelColor=3d4048&style=for-the-badge)](#les-consoles)

<!-- Badges communauté : prêts, en attente des deux URL. Remplacer les deux
     marqueurs ci-dessous et retirer les balises de commentaire.

[![Discord](https://img.shields.io/badge/DISCORD-REJOINDRE-5865F2?labelColor=3d4048&style=for-the-badge&logo=discord&logoColor=white)](URL_DISCORD)
[![Patreon](https://img.shields.io/badge/PATREON-SOUTENIR-FF424D?labelColor=3d4048&style=for-the-badge&logo=patreon&logoColor=white)](URL_PATREON)
-->

<!-- FIN badges communauté -->

<br>

**[Télécharger](https://github.com/jojodogm-ctrl/emufii/releases/latest)** •
[Installation](#installation) •
[Les consoles](#les-consoles) •
[Vie privée](#vie-privée)

</div>

---

## Ce que ça fait

Vous et vos amis avez chacun votre téléphone, votre émulateur et vos propres
jeux. Jouer ensemble à distance demandait normalement d'ouvrir un port sur sa
box, de trouver son adresse IP publique, de la communiquer, de la saisir des
deux côtés, et de recommencer quand elle change.

Emufii supprime tout ça. Vous ne voyez qu'un code.

| | |
|---|---|
| **1. L'hôte crée** | Il choisit un jeu et ouvre une session. Emufii affiche un code. |
| **2. Le code circule** | Dites-le, écrivez-le. Ou laissez vos amis trouver la partie dans la liste des sessions ouvertes. |
| **3. L'invité rejoint** | Emufii ouvre l'émulateur sur son écran multijoueur et remplit l'adresse à sa place. |
| **4. Vous jouez** | Il faut le même jeu de chaque côté. |

**Emufii n'émule rien.** Elle ouvre une session, partage un réseau, et laisse
l'émulateur faire le reste.

## Les consoles

Emufii n'incorpore aucun code d'émulateur. Ces applications s'installent
séparément, depuis leurs sites officiels, et Emufii les lance.

| Console | Émulateur | À savoir |
|---|---|---|
| **3DS** | [Azahar](https://azahar-emu.org) | Prenez une **pre-release ≥ 2126.0-rc**. Les versions stables antérieures n'ont aucun écran multijoueur. |
| **Switch** | [Eden](https://eden-emu.dev) | Le salon est monté côté serveur : personne n'héberge la partie sur son téléphone. |
| **PSP** | [PPSSPP](https://www.ppsspp.org) | L'ad hoc de la console, entre deux appareils qui ne sont plus dans la même pièce. |
| **DS** | [melonDS](https://melonds.kuribo64.net) | Le multijoueur **en ligne**, via Kaeru WFC. Sur les appareils AYN, c'est melonDS DualS qui est préinstallé. |

<details>
<summary><b>Ce qui ne marchera pas, et pourquoi</b></summary>

<br>

Ce sont des faits extérieurs au projet, pas des fonctionnalités manquantes.

- **DS en sans-fil local.** Le sans-fil de la DS est émulé au niveau de la
  couche radio, avec ses échéances physiques. Aucun tunnel ne passe dessous, et
  les auteurs de melonDS le disent eux-mêmes. La DS reste jouable en ligne.
- **Wii.** Wiimmfi exige une sauvegarde NAND extraite d'une vraie console. Une
  NAND ne se partage pas : la diffuser fait bannir la console d'origine.
- **GameCube.** Le LAN GameCube n'existe que dans trois jeux. Il n'y avait rien
  à débloquer.

</details>

## Ce que ce projet ne distribue pas, et ne distribuera jamais

**Aucun jeu, aucun BIOS, aucune clé de console, aucun émulateur.** Vous
fournissez tout cela vous-même, à partir de vos propres cartouches et disques.
C'est une règle du projet, pas une omission, et elle est vérifiée sur le binaire
à chaque version.

## Installation

L'app est signée mais distribuée hors boutique : Android préviendra qu'elle
vient d'une source inconnue.

**1. Installez les émulateurs** que vous voulez utiliser, depuis les sites
officiels du tableau ci-dessus.

**2. [Téléchargez la dernière version](https://github.com/jojodogm-ctrl/emufii/releases/latest)** et installez-la.

**3. Ouvrez Emufii et suivez l'accueil** : dossier de vos jeux, pseudo,
notifications, autorisation de remplissage automatique.

### L'autorisation de remplissage, et pourquoi elle coince

Emufii peut saisir l'adresse de session dans l'écran multijoueur de
l'émulateur, pour vous éviter de la recopier. C'est un service d'accessibilité,
et Android le traite comme un **paramètre restreint** pour toute app installée
hors boutique : l'interrupteur reste grisé.

> Pour le débloquer : **fiche de l'app → menu ⋮ en haut à droite → « Autoriser
> les paramètres restreints »**, puis revenez l'activer dans
> Réglages → Accessibilité.

**Refuser ne casse rien.** Vous saisirez l'adresse à la main, elle est affichée
dans la session. Emufii n'observe que les écrans des émulateurs déclarés, et
n'agit que sur une session que vous avez lancée.

### Vérifier que l'APK est bien la nôtre

Toutes les versions d'Emufii portent cette signature :

```
21:EF:2D:D6:11:E0:96:5A:70:8F:61:F6:00:77:DE:97:D4:0D:59:FD:56:2F:1D:C5:F6:EF:6C:87:77:5E:81:D5
```

```sh
apksigner verify --print-certs Emufii-1.10.8.apk
```

Une copie qui porte une autre signature n'est pas la nôtre, et Android refusera
de l'installer par-dessus une Emufii légitime.

## Vie privée

- Votre **photo de profil** ne quitte jamais l'appareil.
- Votre **liste d'amis** reste sur votre téléphone.
- Le serveur ne connaît qu'un code de session, un pseudo, un titre de jeu et une
  adresse valable le temps de la partie. **Rien n'est conservé après.**
- Le tunnel ne transporte **que** le trafic de la session. Votre navigation, vos
  mises à jour, tout le reste ne passe jamais par nos machines.
- **Aucune sauvegarde cloud** : les clés que l'app détient ne sortent pas de
  l'appareil.

### Avant de rejoindre des inconnus

Une session est un **réseau partagé entre ses joueurs** : c'est ce qui fait
marcher le multijoueur, et c'est ce que ça implique. Les sessions sont isolées
les unes des autres, mais à l'intérieur d'une session, les appareils
s'atteignent. Rejoindre un salon public revient à se brancher sur le même réseau
local qu'un inconnu, avec les mêmes précautions.

Entre gens qui se connaissent, le code se passe de la main à la main et la
partie ne paraît dans aucune liste.

## Mises à jour

L'app vous prévient quand une version plus récente existe, et sait s'installer
elle-même. Elle ne télécharge que depuis son propre serveur, et **vérifie la
signature du binaire avant de proposer quoi que ce soit**. Une version qui ne
porte pas notre clé est refusée, un retour à une version antérieure aussi.

Rien ne s'installe sans que vous ayez appuyé sur « Installer ».

## Licence

**Apache-2.0**, voir [`LICENSE`](LICENSE) et [`NOTICE.md`](NOTICE.md).

La police Poppins est sous SIL OFL 1.1, dont le texte voyage dans l'APK
(`assets/POPPINS-OFL.txt`).

Les sources ne sont pas publiées à ce jour. Ce dépôt sert à distribuer l'app, à
en publier les notes de version, et à porter ce que vous devez savoir avant de
l'installer.
