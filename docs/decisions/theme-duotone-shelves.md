# "DUOTONE SHELVES" theme, direction contract

Replaces "HOME MENU" (moulded plastic on an engraved board). Decided on
2026-08-27 from the v3 logo (`emufii_logo_v3.png`), approved by the user. This
document is the contract: screens are audited against it.

Revision of 2026-08-28, the world is not flat. The first pass had read "flat
layers" as "no relief": no bevel, no sunken notch, a background of two blurred
halos. On screen it did not hold. A white plate four points of luminance above a
cream shell, with a 24% edge and a 14% shadow, does not lift off its background:
every screen read as a single sheet. And two halos overlapping at 12% alpha are
no longer coral and turquoise, they are a dirty pink wash, and the signature
staircase was visible nowhere. Two corrections, decided by the user: the
staircase becomes literal (tiles, with an outline) and relief comes back (lit
moulding, sunken notch). What does not come back from the HOME MENU world: the
hard offset drop shadow, the engraved board, and the single accent. The sections
below carry those corrections.

## THESIS

The logo becomes the interface's grammar. Three rounded tiles in a diagonal
staircase: coral top left, cream in the foreground with the glyph, turquoise
bottom right. The app moves from a monochrome-moulded-plus-one-accent world to a
two-colour world in flat layers: two colour axes crossing, a warm neutral tile at
the centre. Refused: the grey plastic console menu with engraved relief, and the
single cyan accent that did not distinguish "play" from "connect".

## THE WORLD

- Layered surfaces overlapping, like the logo's tiles, but layers with a
  thickness. One tile = a vertical micro-gradient (<=3% lightness), a 1 dp edge, a
  moulding (a lit rim on the upper inner edge, shaded at the bottom), and an
  ambient shadow growing with logical elevation. One light source for the whole
  app: high, slightly to the left. Still forbidden: the engraved grid and the
  hard offset shadow of the HOME MENU world.

### The diagonal staircase

The signature motif: watermark halos in the background, a selected card that
rises and slides diagonally, dialogs overlapping a slanted panel behind them.

### Two semantic axes

Not one accent:

- Coral = the social: sessions, friends, joining, presence. Making a link.
- Turquoise = play and system: launching, confirming, navigating, the library.
- The warm cream neutral = the foreground (the glyph's tile).

### Hollows become notches

Sunken notches: the plate's low tone and an inverted moulding, shaded at the top
under the lip, lit at the bottom where the light reaches the floor. It is the
same source striking a hollow instead of a bump; once the light is set, that is
the only honest way to say "pushed in".

## PALETTE (numbered contract)

### Axes (three cuts each: bright / deep / ink; soft = bright at 20%)

| Axis | bright | deep (light ground, white text) | ink (text on light ground) | dark bright |
|---|---|---|---|---|
| Coral | `#EE6FA3` | `#C24B7E` | `#5A1D3E` | `#F793BC` |
| Turquoise | `#3FCFC0` | `#0E9C8F` | `#0A4A44` | `#5CE0D2` |

Depth violet (links, sheen, bottom of the logo gradient): `#6B72E0` (dark
`#8E93EC`). Glyph/warm ink: `#221B26`.

### Warm neutrals (light), the cream tile extended

- Shell: `#F1EFEA` / low `#E2DFD7`, moved twice on 2026-08-28, in opposite
  directions, and rightly both times. It started at `#F5F1E8`, four points below
  the white plate: a card had no floor. Deepened to `#EDE6D6` it got one, but the
  whole app yellowed: ten points of saturation, full screen, is no longer a
  neutral, it is a colour. The value kept holds the gap the moulding and the
  shadow need while taking most of the yellow out.
- Plate: `#FFFFFF` / low `#F7F5F1`
- Ink: `#221B26`, muted `#6E6475`
- Edges: warm black `#241610` at alpha (replaces the blue-black)

### Purpled neutrals (dark), the bottom of the turquoise gradient

- Shell: `#120F1D` / low `#090711`
- Plate: `#272238` / low `#1C1929`
- Ink: `#F0EAF5`, muted `#9B93AC`

### OLED

Shell `#000000`, plate `#16131F` / low `#0F0D17`; background halos at reduced
alpha to stay black.

### Semantics (centralised)

Never again duplicated by hand.

- Good/green pulled towards turquoise: `#1FA98B` (dark `#3BC4A6`)
- Warning/amber: `#C98A12` (dark `#E3A83C`)
- Error pulled towards coral: `#E5604F` (dark `#F0796A`)
- Info/blue: `#5A8FD8` (dark `#82AFE6`)

### Colour strategy

Committed duo: the two axes carry the structure (rings, primary actions,
domains), the background and surfaces stay warm neutrals. Colour is never
decorative: it encodes play or link.

## MATERIAL (replaces Plastic.kt)

Superseded for plates, hollows, the header and the shelves' outline by
`matiere-et-mouvement-trailer.md` (September 2026): flat plates, two drop shadows, no
contour, no moulding. What follows is kept as the record of what it replaced.


- `plate()`: micro-gradient plus 1 dp edge plus moulding plus ambient shadow. The
  lip widens with elevation (1.5 dp, 2 dp beyond 10 dp of lift): a dialog does not
  carry the same rim as a chip. `pressed`: the tile sinks (scale 0.98, shadow at a
  third) and flips its light.
- `socket()`: the plate's low tone plus an inverted moulding. The name stays,
  twenty callers still say it.
- `engravedGrid` stays empty: the background already carries its own relief, and
  two motifs on the same floor would fight.
- The background (TrayBackdrop): warm neutral plus two enormous squircle tiles,
  coral top left and turquoise bottom right, each with a body gradient (bright at
  the top, deep at the bottom) and a 2 dp outline. It is the corner and the edge
  that say "tile", not the hue: without an outline you fall back on the wash. Big
  enough to run off two sides each. The third tile, the foreground cream, is the
  app's plates themselves: the background draws only the two behind. Then a
  diagonal violet sheen and a vignette, frozen if animations are off.
- Overlap: a foreground element (a dialog, a selected tile) may run diagonally
  over what is behind it.

## GAMEPAD FOCUS

The ring (4 dp outline plus halo, 140 ms in, 0 out, bring-into-view) is kept as
it is but becomes turquoise by default and coral on social areas (session/friend
chips, friend lists, join). The cursor's colour says the area. The selected
tile's shadow takes the axis's hue.

## TYPOGRAPHY

M PLUS Rounded 1c kept (its curves match the logo's squircles). Weight contrast
owned: titles Black/ExtraBold, body Regular. No gradient text, no two-colour
titles.

## SHAPES

The logo's squircles: library tile 16 -> 20 dp, artwork 13 -> 16 dp, cards
22 -> 28 dp (the foreground is rounder), inset 14 dp, actions 18 dp, pills 50%.
"One language of corners" kept.

## PER-SCREEN TREATMENT

- Splash: coral -> turquoise LEDs (resampled from the v3 logo). Option: the three
  tiles assembling diagonally, then the glyph appearing.
- Library: cream squircle tiles on a darker cream shell; focus: scale plus
  turquoise ring plus tinted shadow. Console badge as a pill; compat keeps the
  semantic code. Floating header kept (never a bar): flat pills on notches,
  Sessions/Friends/Profile chips coral when active, library controls
  neutral/turquoise.

### Game card (dialog)

A large foreground card, with the two-axis rim on its outline, its colour
drifting from one axis to the other (7 s, back and forth, frozen mid-course if
system animations are off). It is the one screen where both are true at once, the
card offering to create a session (coral) and to launch (turquoise), and a frozen
hue there would take a side the screen does not take. Launch = solid turquoise
pill; private/session = coral.

A tile placed behind was tried three times, then removed. It was the logo's third
tile taken literally: a turquoise plate overflowing the card, first in
`matchParentSize` (so the size of the whole screen), then at the card's size and
tilted 3 degrees, then straight with an even 6 dp margin. All three had the same
underlying flaw: to say "there is a layer underneath", they added one more object
to a screen that already has two, the card, and the darkened library behind it.
The outline says the same thing without adding anything, and it carries the drift
just as well.

A form trap, paid for twice: the rim is a `drawWithContent`, so it takes the size
of what it wraps. Placed at the head of the chain it also wrapped the outer
padding and drew an outline 48 dp wider than the card, floating around it. It
goes below the size bounds, and below `scale`/`alpha` so it arrives with the card
instead of staying put while it grows.

### Session / Join, coral domain

Session, Join, Friends, Finder, Wfc, PspOnline: the coral domain, codes,
presence, joining, alerts. Code slots as cream notches, coral caret. Leaving =
error.

### Settings

A neutral hub, cream cards, icons in notches tinted by domain (turquoise for
system, coral for profile/social). The configurable accent is removed: two
semantic colours replace it (SYSTEM/Material You can stay as an option tinting
both axes).

### Onboarding / Preparing

Neutral cards with the coral-to-turquoise rim around the whole outline, for
waiting and connecting flows. It lives in `ui/components/WaitTrim.kt`.

Redone three times on 2026-08-28. What had been written was not a rim on an edge
but a chord: a straight segment joining a point on the left edge to a point on
the top edge, so a slanted bar laid across the face. First flaw, it was not
clipped to the silhouette, so both its ends came out of the plate through the
rounded corner and floated on the background. Clipped, the second appeared: it
was still a 6 dp gash across the upper left quarter, at full saturation, and on a
screen that fades in the card is still transparent while the band is already
solid, so for a moment it is all you see. Brought back onto the edge, it held
only one corner of it, which read as a drawing accident.

The stroke is the card's own outline, all the way round: 3 dp, the gradient
running along the logo's diagonal, so each side carries its corner's colour. It
borders instead of barring.

- Second screen (SecondScreenHost): same theme, same tokens.

### Avatars

The avatar takes the social axis's hue, coral, and never the chosen accent: it is
a person, not a setting. Its pencil badge is declared after the cursor ring, or
the ring's stroke passes straight through it.

## KEPT AS IS

Gamepad navigation (Gamepad.kt behaviour), floating header plus WallpaperVeil,
screen structure (sealed Screen), 2-4 column grid, in-house keyboard,
PadDialog/PadTextField, "console menu" density: few elements, large, spaced out.

## CONSTRAINTS (no hard-coded hex)

- Contrast: solid turquoise on cream is borderline as text, so reserved for
  button grounds (dark or white ink depending on the cut) and for rings, never
  for body text. Muted takes its surface's hue, never neutral grey.
- Every colour lives in the theme (Color.kt / semantic object); no hard-coded hex
  left in the screens (badges, statuses, avatars, splash, scrims).
- Avatars: gradients remixed from the logo's two axes.

## ANTI-REFERENCES

The single cyan accent, the engraved grid and the warm offset shadow of the HOME
MENU world: replaced, not softened. "Liquid Glass" (blur, translucency, iOS
halos) stays forbidden as before. The bevel and the sunken notch are no longer
anti-references since 2026-08-28: that was the one point of the contract which,
applied, emptied the interface of its material.

## The lustre is gone

The wide band of light crossing the board every nineteen seconds was removed on
2026-08-29. It read as a semi-transparent veil sweeping the screen, which a
background must not do.

And on the rear panel, where the board is frozen, it was painted still: its clip
box then left a hard edge at the bottom left, a large square nothing explained.
The same flaw on both sides, invisible on one and glaring on the other.

The two shelves, their waves and the vignette remain. Do not reintroduce it: it
is the one thing in the background whose movement got noticed.

## MATERIAL (background)

### Two shelves, and a movement budget

The two shelves are the logo's staircase at screen scale, coral top left and
turquoise bottom right. They are rounded squares large enough to run off two
edges: only the corner facing the middle stays visible, and that corner is the
whole motif. It is the corner and the edge that say "tile", not the hue; two
blurred halos at 12% gave only a dirty pink wash, with no edge, no staircase, no
logo.

The colour encodes the two axes and nothing else. With artwork on top, two
palettes fight: the shelves therefore stay under the content and never under
text.

The movement budget is the subject of this background. It held half a CPU
repainting at 120 Hz with nothing happening on screen, and it did it a second
time: 85% of a core, measured on a still library, against 3.7% for the launcher
next door. Two causes, both fixed, both not to be reintroduced:

1. Everything was redrawn every frame, including what does not move, a dozen
   gradients rebuilt thirty times a second for an identical result. What is still
   is baked into a half-resolution bitmap, recorded once; only the waves redraw.
2. The Gaussian blur fell back to software rendering. `BlurMaskFilter` has no GPU
   equivalent: Android drew those paths on the CPU, into an intermediate image,
   twelve times per frame. The waves' glow is done with stacked strokes, which the
   GPU draws without thinking. Same lesson as the cursor, see
   `navigation-manette.md` on the four layers of the neon cursor.

## The two-axis rim borders, it does not bar

The contract said "a diagonal rim on an edge", and what was written was a chord:
a straight segment joining a point on the left edge to a point on the top edge,
so a slanted bar laid across the face. Two flaws, the second appearing only once
the first was fixed:

1. It was not clipped to the silhouette, so both its ends came out of the plate
   through the rounded corner and floated on the background.
2. Clipped, it was still a 6 dp gash across the upper left quarter, at full
   saturation. On a screen that fades in, the card is still transparent while the
   band is already solid: for a moment, it is all you see.

The stroke is the card's own outline, and it goes all the way round: the gradient
runs along the logo's diagonal, coral top left, turquoise bottom right, so each
side carries its corner's colour. It borders instead of barring.

The phase drifts the gradient along that diagonal. At zero it does not move,
which is what the waiting screens want, where the only thing that should turn is
the progress disc. The session card makes it breathe from one axis to the other:
it is the one screen where both axes are true at once, and a frozen hue there
would take a side the screen does not take.

## The compatibility badge is the documented exception to the single accent

Three marks, one per verdict, and a game nobody has rated shows none. That last
distinction is the one that counts: an untested game and a game known to work
must not look alike, or the badge stops being information and becomes decoration.
A tick means somebody checked.

It was built the other way round first, nothing drawn for a game that works, on
the grounds that a library is mostly made of games that work and marking them all
would put a mark on nearly every tile. That reasoning holds for density and was
wrong about meaning: silence already means "unknown" here, so spending it on
"verified" too made the two indistinguishable.

On the colours: the chrome stays achromatic and the only accent is reserved for
the cursor. This is the documented exception, and it is narrow, a mark appearing
only on games somebody actually judged, in three fixed colours, none of which is
the accent. Fixed rather than following the chosen accent, by design: a verdict is
the same fact for every player, and a badge that changed colour with a personal
setting would say something about the setting instead of about the game.
