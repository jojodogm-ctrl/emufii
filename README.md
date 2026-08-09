# Emufii

**Jouer à plusieurs, à distance, avec des émulateurs de consoles portables —
sans configurer un seul port ni une seule adresse.**

Emufii n'émule rien. Elle ouvre une session, partage un réseau entre vos
appareils, et laisse l'émulateur faire le reste : les consoles se voient comme
si elles étaient dans la même pièce.

[**→ Télécharger la dernière version**](https://github.com/jojodogm-ctrl/emufii/releases/latest)

---

## Ce que ça fait

Vous et vos amis avez chacun votre téléphone, votre émulateur et vos propres
jeux. Emufii vous met sur le même réseau le temps d'une partie :

1. L'hôte choisit un jeu et crée une session. Emufii affiche un code.
2. Les invités saisissent ce code, ou trouvent la partie dans la liste des
   sessions ouvertes.
3. Emufii ouvre l'émulateur sur son écran multijoueur et remplit l'adresse.
4. Vous jouez.

Il faut le même jeu de chaque côté.

## Consoles

| Console | Émulateur | Où le prendre |
|---|---|---|
| **3DS** | Azahar | <https://azahar-emu.org> — une **pre-release ≥ 2126.0-rc**, les stables antérieures n'ont pas d'écran multijoueur |
| **Switch** | Eden | <https://eden-emu.dev> |
| **PSP** | PPSSPP | <https://www.ppsspp.org> |
| **DS** | melonDS | <https://melonds.kuribo64.net> (ou melonDS DualS sur les appareils AYN) |

Emufii n'incorpore aucun code d'émulateur : ces applications sont installées
séparément et lancées par intent.

## Ce que ce projet ne distribue pas, et ne distribuera jamais

**Aucun jeu, aucun BIOS, aucune clé de console, aucun émulateur.** Vous
fournissez tout cela vous-même — vos propres sauvegardes de vos propres
cartouches et disques. C'est une règle du projet, pas une omission, et elle est
vérifiée sur le binaire à chaque version.

## Installation

L'app est signée mais distribuée hors boutique : Android préviendra qu'elle
vient d'une source inconnue.

### Vérifier que l'APK est bien la nôtre

Toutes les versions d'Emufii portent cette signature :

```
SHA-256 : 21:EF:2D:D6:11:E0:96:5A:70:8F:61:F6:00:77:DE:97:D4:0D:59:FD:56:2F:1D:C5:F6:EF:6C:87:77:5E:81:D5
```

Pour la contrôler, avec les build-tools d'Android :

```sh
apksigner verify --print-certs Emufii-<version>.apk
```

**Une copie qui porte une autre signature n'est pas la nôtre.** Android refusera
de toute façon de l'installer par-dessus une Emufii légitime.

### L'autorisation de remplissage, et pourquoi elle coince

Emufii peut saisir l'adresse de session dans l'écran multijoueur de
l'émulateur, pour vous éviter de la recopier. C'est un service
d'accessibilité, et Android le traite comme un **paramètre restreint** pour
toute app installée hors boutique : l'interrupteur reste grisé.

Pour le débloquer : **fiche de l'app → menu ⋮ en haut à droite → « Autoriser
les paramètres restreints »**, puis revenez activer Emufii dans
Réglages → Accessibilité.

Emufii n'observe que les écrans des émulateurs déclarés, et n'agit que sur une
session que vous avez lancée. **Refuser cette autorisation ne casse rien** :
vous saisirez l'adresse à la main, elle est affichée dans la session.

## Ce qu'il faut savoir avant de rejoindre des inconnus

Une session est un **réseau partagé entre ses joueurs** : c'est ce qui fait
marcher le multijoueur, et c'est ce que ça implique. Les sessions sont isolées
les unes des autres, mais à l'intérieur d'une session, les appareils
s'atteignent. Rejoindre un salon public revient à se brancher sur le même
réseau local qu'un inconnu — avec les mêmes précautions.

Les parties entre gens qui se connaissent passent par un code partagé de la
main à la main, sans passer par la liste publique.

## Vie privée

- Votre photo de profil **ne quitte jamais l'appareil**.
- Le serveur ne connaît que ce qu'il faut pour vous mettre en relation : un
  code de session, un pseudo, le titre du jeu, et une adresse valable le temps
  de la partie. Rien n'est conservé après.
- Votre liste d'amis reste sur votre téléphone.
- Aucune sauvegarde cloud : les clés que l'app détient ne sortent pas de
  l'appareil.
- Le tunnel ne transporte **que** le trafic de la session. Votre navigation,
  vos mises à jour, tout le reste ne passe jamais par nos machines — on ne le
  voit pas et on ne le paie pas.

## Mises à jour

L'app vous prévient quand une version plus récente existe, et sait s'installer
elle-même. Deux verrous : elle ne télécharge que depuis son propre serveur, et
elle **vérifie la signature du binaire avant de proposer quoi que ce soit** —
une version qui ne porte pas notre clé est refusée, et un retour à une version
antérieure aussi.

Rien ne s'installe sans que vous ayez appuyé sur « Installer ».

## Licence

**Apache-2.0** — voir [`LICENSE`](LICENSE) et [`NOTICE.md`](NOTICE.md).

La police Poppins est sous SIL OFL 1.1, dont le texte voyage dans l'APK
(`assets/POPPINS-OFL.txt`).

## Le code

Les sources ne sont pas publiées à ce jour. Ce dépôt sert à distribuer l'app,
à en publier les notes de version, et à porter ce que vous devez savoir avant
de l'installer.

---

**Page du projet :** <https://jojodogm-ctrl.github.io/emufii/>
