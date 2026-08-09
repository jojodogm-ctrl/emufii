# NOTICE d'Emufii

Copyright 2026 Emufii contributors

Emufii est distribuée sous **Apache License 2.0**. Le texte intégral est dans le
fichier [`LICENSE`](LICENSE).

## Apports tiers

### WireGuard, couche réseau des sessions

- Source : <https://github.com/WireGuard/wireguard-android>
- Artefact : `com.wireguard.android:tunnel`
- Licence : **Apache License 2.0**

Dépendance Maven ordinaire, utilisée par son backend userspace (`GoBackend`)
qui passe par le `VpnService` d'Android. Aucun binaire natif n'est vendoré.

### Bibliothèques Android

AndroidX et Jetpack Compose, Coil, Haze (`dev.chrisbanes.haze`), et la
bibliothèque standard Kotlin, toutes sous **Apache License 2.0**.

### Serveur

Express, sous **licence MIT**. L'agent du relais n'a aucune dépendance.

### Poppins

Poppins, par Indian Type Foundry, Jonny Pinhorn et Ninad Kale, sous **SIL Open
Font License 1.1**. Récupérée depuis Google Fonts
(`github.com/google/fonts/ofl/poppins`), pas depuis un binaire tiers.

La SIL OFL exige davantage qu'une attribution : son texte doit **accompagner le
logiciel de police**, dans toute copie et donc dans l'APK. Il y voyage à
`assets/POPPINS-OFL.txt`. C'est la seule obligation qu'un apport tiers impose à
Emufii, et elle est remplie par ce fichier.

## Ce qu'Emufii n'incorpore pas

Emufii **ne contient aucun code d'émulateur**. Azahar, Eden, PPSSPP et melonDS
sont lancés par intent, comme des applications tierces installées séparément.
Leur licence respective ne remonte donc pas jusqu'à ce dépôt.

De même, ni ROM, ni image de BIOS, ni clé de console : c'est un invariant du
projet, vérifié sur le binaire à chaque version.
