# Third-Party Notices — application Android

Détail des apports tiers de l'APK. La licence d'Emufii elle-même, le copyright et
la liste complète des attributions sont dans le `NOTICE.md` et le `LICENSE` à
la racine du dépôt.

**Emufii est sous AGPL-3.0** depuis le 2026-08-09, en remplacement de
l'Apache-2.0 qui tenait depuis le 2026-07-28. Les versions jusqu'à la 1.10.8
incluse restent sous Apache-2.0.

## WireGuard (couche réseau des sessions)

- Source : https://github.com/WireGuard/wireguard-android
- Artefact : `com.wireguard.android:tunnel`
- Licence : **Apache License 2.0**

Utilisé via son backend userspace (`GoBackend`), qui passe par le `VpnService`
d'Android et ne demande donc pas le root. Aucun binaire natif n'est vendoré :
la bibliothèque est une dépendance Maven ordinaire.

## Interface

AndroidX et Jetpack Compose, Coil, Haze — toutes sous **Apache License 2.0**.

## Rounded M+ (M PLUS Rounded 1c)

Sous **SIL Open Font License 1.1**. Le texte de la licence voyage avec l'APK :
`assets/ROUNDED-MPLUS-OFL.txt`, comme l'OFL l'exige de toute copie du logiciel
de police. Attribution complète dans le `NOTICE.md` à la racine du dépôt.

---

## Historique — pourquoi la GPL v2 a été envisagée, puis abandonnée

Jusqu'au 2026-07-28, Emufii embarquait du code dérivé de **ZerotierFix** (kaaass,
GPL v2) et de **ZeroTier One** (BSL 1.1) : des stubs Java `com.zerotier.sdk.*`
et un `libZeroTierOneJNI.so`. C'est de ce lien que venait l'obligation de
distribuer Emufii sous GPL v2.

**Ce code a été entièrement retiré** lors de la bascule vers WireGuard — paquet
`zt/`, stubs `com.zerotier.sdk.*`, les deux `.so` (6,7 Mo) et les fichiers de
licence qui les accompagnaient.

La contrainte ayant disparu, la licence a été choisie librement : d'abord
Apache-2.0, puis AGPL-3.0 le 2026-08-09 pour rendre un fork hébergé sans intérêt
commercial. Le raisonnement complet, options écartées comprises, est dans
`NOTICE.md` à la racine et dans `docs/M10_LICENCES.md`.
