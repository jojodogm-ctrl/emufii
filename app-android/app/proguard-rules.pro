# R8 rules specific to Emufii.
#
# Deliberately short. Everything the libraries know how to protect themselves is
# already covered: Compose, Coil and OkHttp ship their rules with their artifact,
# and Android's own `proguard-android-optimize.txt` keeps native methods and the
# enum members reflection calls.
#
# What is left here are the two things none of those files can guess.
#
# One place, not two: AGP also combines every file under `src/main/keepRules/`
# and passes those to R8 as well. Android Studio leaves an empty `rules.keep`
# template there, which was removed on 2026-08-19 so there is no second, silent
# home for a rule nobody would think to look in.

# 1. Our own enum constants, because they are written to disk and read back
#    after an update.
#
# Five settings are persisted by name: the library's layout and sort order, the
# theme, and a netplay plan's role. The code reads them back by comparing
# `it.name` against the stored string, and R8 is free to rename a constant since
# it sees no reflection.
#
# It would still work within one version: that version writes and reads the same
# obfuscated name. The damage comes at the next update, where the name will have
# moved. The player would find their library back in grid view having left it as
# a list, with nothing to explain it. The code falls back to the default
# cleanly, so it would not crash; it would go wrong silently, which is worse to
# diagnose.
-keepclassmembers enum eu.emufii.app.** { *; }

# 2. The WireGuard library, which ships no rules at all.
#
# Checked inside the 1.0.20260102 AAR: not one `consumer-rules.pro`, and
# `GoBackend` declares six native methods (`wgTurnOn`, `wgTurnOff`,
# `wgGetConfig`, `wgGetSocketV4`, `wgGetSocketV6`, `wgVersion`). The `.so`
# resolves them by their fully qualified name, class included: a rename would
# give an `UnsatisfiedLinkError` when the tunnel comes up, which is the exact
# moment the player creates their session.
#
# `proguard-android-optimize.txt` already keeps native members, but it does not
# keep the *name of the class* carrying them. So we say it here, rather than
# depending on a file we do not control.
-keep class com.wireguard.android.backend.GoBackend { *; }
-keep class com.wireguard.android.backend.GoBackend$* { *; }
-keepclasseswithmembernames class com.wireguard.** { native <methods>; }
