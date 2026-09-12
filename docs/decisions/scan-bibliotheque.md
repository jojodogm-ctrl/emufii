# Scanning the library: the cache, the decision chain, the identities

The narrative that lived in `library/RomsRepository.kt`, taken out of the code on
2026-08-24 (see `docs/STYLE_COMMENTAIRES.md`). See also
[`identite-disques.md`](identite-disques.md). Headings are anchors cited from the
code.

## The cache belongs to the process, not to the screen

The result of the last scan is kept so callers with a single thing to find do not
walk the whole tree again: joining from the finder did exactly that, a second
full walk to match one title id.

It is deliberately shared between instances rather than held per repository. A
repository is remembered per composition, so rotating the device built a new one
and rescanned from scratch, which with a 2 GB 3DS ROM in the folder took long
enough to cause an ANR, and several rotations queued several scans.

## Player-chosen names are applied on the way out, never into the cache

Renaming a game rescans nothing, it changes a preference, so a cache holding
renamed titles was a cache nothing invalidated: the rename only appeared at the
next cold start, which reads as "renaming does nothing".

The cache therefore keeps the titles read from the files, and chosen names are
applied on every read. Welcome side effect: clearing a name immediately gives the
original title back, where before the custom name stayed until a rescan.

Sorting belongs in the same place, for the same reason: a renamed game must go to
its new place in the alphabet, not stay where its old title put it.

Same logic for the title read from the file: every console has its own reading
path, and applying the chosen name per path would have given a library where
renaming works for 3DS and not for DS. One place, on the way out, for all of
them.

## Walking the tree

People file their ROMs in `3DS/`, `GameCube/`, `Jeux/`, and a flat scan found
nothing. So subfolders are walked, but not indefinitely: beyond a few levels you
are almost certainly somewhere you should not be, and each extra level costs one
query per directory.

`DocumentsContract` is queried directly, not `DocumentFile`: the latter issues
one query per entry to answer `isFile`/`name`, which on a library of a few
thousand files is a few thousand round trips. Here one query per directory
returns everything needed.

Breadth first, so shallow, well-named folders are visited before deep ones: that
matters when the file limit cuts the walk short.

## A decision chain, cheapest first

1. The folder name, when it names a console: the player sorted this file
   themselves.
2. The extension, when it belongs to one console only.
3. The bytes, for extensions three consoles share.

A shared extension no reading vouches for is a disc Emufii does not serve (a PS1,
a Dreamcast, an unreadable provider): it is not listed, rather than pointed at an
emulator that cannot open it.

Deliberate exception: Nintendo containers (`rvz`, `wia`) keep the extension's
answer when reading says nothing. Both play on Dolphin anyway, and a provider
hiccup must not empty a library.

Likewise, `.iso` and `.chd` say nothing about the console that burned them: the
PS2, the Xbox and a stack of arcade systems carry them too, and those games
landed in the grid under their filename while Emufii can do nothing with them.
For those two containers the file must prove it is a PSP game, a `PSP_GAME` in
its table of contents, failing which it is not listed. `.pbp` and `.cso` are
still admitted on extension alone: they belong to the PSP only.

## A zipped cartridge stays zipped

Only the DS. melonDS is the one backend of ours that takes an archive: its
`RomFileProcessorFactory` maps `zip` and `7z` to their own processors next to
`nds`, `dsi` and `ids`, it finds the cartridge inside on its own, extracts it to
its cache and boots that. Read off the 2.0.1 APK and confirmed in the source of
the WatermelonDS fork, 2026-09-12. So Emufii hands over the `.zip` untouched and
extracts nothing.

Azahar and Dolphin take no archive, so listing a zipped 3DS or GameCube game
would put a tile in the grid that no launch can honour. An archive is therefore
a DS game or it is not listed.

A zip belongs to no console by its name, which is why it is not in the extension
table: that table is a map with one owner per key, and a zip holds whatever
someone put in it. It is the first `nds`, `dsi` or `ids` entry inside that makes
it a DS game, the same entry melonDS would pick, and an archive holding none is
not listed.

`7z` is left out for now: reading its table of contents needs a library we do not
otherwise carry, and the zip covers what was asked for.

## What we open, and what we cannot open

3DS and DS are opened, because both carry their real title and their icon inside,
an SMDH for the 3DS and a banner for the DS, and both are cheap to reach.

PSP too: a UMD carries its icon and title in its filesystem, under `PSP_GAME`, a
few kilobytes to read on a disc weighing a million. The title comes from
`PARAM.SFO` and is clearly better than the filename, which usually drags its
region and revision along in brackets.

Disc images take their title from the filename, and that is accepted: an `.rvz`
is compressed, so no banner sits at a fixed offset, and you would have to
decompress a whole game to fetch its `opening.bnr`. Their identity is another
matter: an uncompressed image yields its six-character disc id for almost
nothing, and that is what the session guard compares. Without it the session
publishes nothing and the guest is told they do not have the game in front of
them, exactly the flaw fixed for the PSP in versionCode 12.

Decoded icons land in the cache directory under the game code, so a rescan does
not decode every banner again.

## `productCode` and `titleIdHex` do not play the same role

The disc id serves as a cache key, but not as a session identity: it goes into
`productCode`, as for the DS, and not into `titleIdHex`, which decides whether
two players really have the same game.

That is deliberate and holds for PSP as for GameCube/Wii: two regional dumps of
the same title carry two ids, and nothing yet says they refuse to play together.
`sessionId` can find a game by either, but the "this is not the same game" guard
only refuses on a title id. Refusing on a disc id would amount to forbidding a
game on a guess.

The PS2 follows the Nintendo disc path for the same reason: the title comes from
the filename, but the number is read from the disc, `SLES-50877` on
TimeSplitters 2, exactly what ARMSX2 shows.

## Switch keys: picked up, never supplied

A Switch dump says nothing about itself without them: no icon, no title. Emufii
ships no key and downloads none: it picks up a `prod.keys` the player already put
in their own ROM folder, which every Switch emulator asks them to do anyway. Held
in memory only.

Absent, Switch tiles keep their initials, exactly like an unrecognised file, and
here that is the common case, not the exception.

## What the player sees of the chosen folder

SAF returns a document id shaped like `primary:Roms/3DS`: the volume prefix means
nothing to anybody outside the framework, so only the part after it is shown.
Falls back to the last URI segment for providers whose id format we do not know.

## A name the file does not give is asked of the index

A ROM always says which game it is, since the title id, game code, disc id and
serial live in headers no encryption touches, but not always its title: no
console keys means no NACP on a Switch dump, no SMDH on a 3DS, and disc formats
carry no banner half the time. The grid then falls back on the filename, release
tags included.

So this is the one place a real name is asked for, by id, of the same public
indexes the compatibility tool resolves its own against. Every console is
covered: a tile naming its game on one console and its file on the next is the
mess this exists to avoid.

Served and cached like any other public document (`/compat`, `/meta`): handhelds
are offline half the time, and a real title vanishing without Wi-Fi would send the
grid back to scene jargon, which reads as a fault.

The overlay never replaces anything but a name derived from the file, and loses
against the two things above it: a title read from the file itself (the cartridge
speaks its own language) and the name the player chose.

## A cartridge's language is the app's

Every format Emufii reads carries its title several times, a DS banner in six
languages, a 3DS SMDH in twelve, a Switch control in sixteen, and every reader
picked from a list frozen at "French, then English, then Japanese". An app in
English therefore showed "Pokémon Version Blanche 2", with no way to ask for
anything else. The cartridge knows both names; the only question is which to
read, and the answer is the language the app itself speaks.

The app is bilingual, so each order names its own language first and the other
next, then keeps the old tail: a Japanese cartridge carrying neither French nor
English must still produce something, and a Japanese title beats a filename.

The language marker exists because titles are cached on disk. Two languages of
the same cartridge are two different strings under the same game code: the cache
key must therefore carry the language, or changing the app's language would show
the previous one until the next scan.
