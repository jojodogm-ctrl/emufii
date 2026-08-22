# NOTICE — Emufii

> **Emufii s'appelait EOEA jusqu'au 2026-08-01.** L'ancien nom subsiste dans
> l'historique git, dans les noms d'unités et de chemins déjà déployés sur le
> serveur, et dans le nom du fichier de signature — tous conservés à dessein.

Copyright 2026 Emufii contributors

Ce produit est distribué sous **GNU Affero General Public License v3** depuis le
2026-08-09. Le texte intégral est dans `LICENSE`, à la racine du dépôt.

Les versions jusqu'à la 1.10.8 incluse sont sorties sous Apache-2.0, et cette
concession vaut pour ces binaires-là ; tout ce qui part de la 1.10.9 est en
AGPL-3.0.

> Le titulaire du copyright ci-dessus est une formulation d'attente : y mettre
> le nom sous lequel l'auteur souhaite être identifié.

## Décision de licence

**AGPL-3.0, arrêté le 2026-08-09**, en remplacement de l'Apache-2.0 du
2026-07-28. Ce qui a fait bouger la décision : l'inquiétude qu'un fork vive sur
le VPS et fasse disparaître le Patreon au passage.

Ce qu'il faut avoir en tête, dans l'ordre où ça compte vraiment :

- **La licence n'est pas ce qui protège l'infrastructure.** Un texte juridique
  se plaide, il ne filtre pas. Ce qui protège le VPS, c'est le contrôle d'accès
  du coordinator (`coordinator/client-auth.js`) et le fait de **ne jamais
  publier `coordinator/` ni `relay/`** : un fork se retrouve avec un client
  sans réseau, à charge pour lui de monter et payer le sien.
- **L'AGPL n'interdit pas le fork, elle le rend sans intérêt commercial.**
  Quiconque forke doit republier ses sources sous la même licence, y compris en
  exploitation comme service réseau. Personne ne peut fermer ce code et le
  revendre.
- **Le nom et le logo ne sont couverts par aucune licence de code.** Un fork
  légal doit changer de nom. C'était déjà vrai sous Apache-2.0.
- **Le copyright reste au projet**, donc des licences commerciales hors AGPL
  peuvent se négocier au cas par cas.

Ce qui a été écarté : le source-available (type PolyForm), qui aurait exigé un
accord écrit pour toute modification. C'est ce qui avait été demandé au départ,
mais ce n'est pas de l'open source et ne peut pas être appelé ainsi.

Le dossier d'origine, avec les options pesées en juillet :
`docs/M10_LICENCES.md`.

## Apports tiers

### WireGuard — couche réseau des sessions

- Source : https://github.com/WireGuard/wireguard-android
- Artefact : `com.wireguard.android:tunnel`
- Licence : **Apache License 2.0**

Dépendance Maven ordinaire, utilisée par son backend userspace (`GoBackend`)
qui passe par le `VpnService` d'Android. Aucun binaire natif n'est vendoré.

### Bibliothèques Android

AndroidX et Jetpack Compose, Coil, Haze (`dev.chrisbanes.haze`), et la
bibliothèque standard Kotlin — toutes sous **Apache License 2.0**.

### Coordinator

Express — **licence MIT**. Le relais (`relay/`) n'a aucune dépendance.

### Rounded M+ (M PLUS Rounded 1c)

`app-android/app/src/main/res/font/rounded_*.ttf` — Rounded M+ 1c, par le
Rounded M+ Project (`github.com/coz-m/MPLUS_FONTS`), sous **SIL Open Font
License 1.1**. Récupérée depuis Google Fonts, pas depuis un binaire tiers, puis
**réduite au latin** : la famille couvre tout le japonais, l'app parle français
et anglais, et l'intégrale pesait 3,4 Mo par graisse.

C'est une linéale à terminaisons arrondies, la voix des menus de consoles
portables, et c'est ce que la direction visuelle de l'app demande. Elle remplace
Poppins depuis le 2026-08-22 ; les versions jusqu'à la 1.12.1 embarquaient
Poppins (Indian Type Foundry, Jonny Pinhorn, Ninad Kale), également sous OFL.

La SIL OFL exige davantage qu'une attribution : son texte doit **accompagner le
logiciel de police**, dans toute copie et donc dans l'APK. Il est donc à deux
endroits — `licenses/ROUNDED-MPLUS-OFL.txt` pour qui lit le dépôt, et
`app-android/app/src/main/assets/ROUNDED-MPLUS-OFL.txt` pour qui n'a que le
binaire.
C'est la seule obligation qu'un apport tiers impose à Emufii, et elle est
remplie par ces deux fichiers.

## Ce qu'Emufii n'incorpore pas

Emufii **ne contient aucun code d'émulateur**. Azahar, Dolphin et melonDS sont
lancés par intent, comme des applications tierces installées séparément. Leur
licence respective ne remonte donc pas jusqu'à ce dépôt.

De même : ni ROM, ni BIOS, ni clés — c'est un invariant du projet.

## La configuration réseau PS2 embarquée

Depuis le 2026-08-20, Emufii embarque une carte mémoire PlayStation 2 ne contenant
qu'une sauvegarde `BWNETCNF` : la configuration réseau de la console. Cette carte
n'a pas été fabriquée ici — elle a été **créée par ARMSX2, formatée par la PS2
émulée, puis écrite par l'utilitaire réseau de Midnight Club 3**, et reprise
telle quelle. C'est ce qui garantit qu'elle est valide sans avoir à réimplémenter
le contrôle d'erreur des cartes PS2.

C'est une redistribution assumée, et le raisonnement tient en trois points. Sans
cette donnée, **aucun jeu LAN de PS2 n'ouvre son menu local** : ce n'est pas un
réglage, c'est une sauvegarde, et la plupart des jeux l'attendent sans savoir la
créer. L'utilitaire qui l'écrit n'est embarqué que dans une poignée de titres, si
bien qu'un joueur qui n'en possède aucun n'a aucune porte d'entrée. Et ce qui est
repris est une configuration d'interface réseau, pas un BIOS ni du code : on peut
la lire en clair pour l'essentiel.

**L'icône Sony de la sauvegarde (33 Ko) est incluse**, faute de pouvoir l'enlever :
le jeu lit la configuration sans elle, c'est mesuré, mais la retirer supposerait de
réécrire la carte, donc de recalculer un ECC que ce choix vise justement à ne pas
avoir à implémenter.

Si ce choix devait être revu, le point d'entrée est `Ps2NetworkProfile` et l'asset
`assets/ps2/emufii-ps2-net.ps2` ; le retrait laisserait la fonction en place et
priverait seulement le joueur de la configuration toute faite.
