# Material and motion, taken from the trailer

Decided in September 2026 against `Emufii_trailer_v5_light.mp4` (timestamps below refer to
it). Replaces the MATERIAL section of `theme-duotone-shelves.md` for plates; the palette,
the font (M PLUS Rounded 1c), the shapes, the navigation and the screens' content do not
change. The headings are anchors cited from the code.

## Flat plates, dropped shadows

No 1 dp contour and no moulding on any plate. A surface is a face and the platform's own
shadow, cast by the render thread from the shape's outline: a wide spot shadow that lifts
it and a short ambient one that sets it down. The trailer's cards carry the CSS equivalent
of `0 30px 80px -30px`, 16% on light and 60% on dark; the app theme sets
`spotShadowAlpha` and `ambientShadowAlpha` to 1 so each shadow's colour carries its real
strength, the platform's default cap of 0.19 being out of reach of 60%. The shadows that
predate this keep their old strength through `LEGACY_SPOT` and `LEGACY_AMBIENT`.

`Modifier.dropShadow` was tried first and must not come back. It blurs a bitmap on the CPU
for every radius it meets, so a lift that animates (a press, a selection) re-rasterised on
every frame: that was the slowness. And it paints that bitmap through a rectangle, which
showed square under a blurred parent. `innerShadow` is the same mechanism; a hollow's lip
is a drawn gradient instead.

A plate is one layer: press scale, shadow and clip together, a node clipped to its outline
still casting that outline's shadow.

| Use | lift |
|---|---|
| Chips, small buttons, keypad keys | 2 dp |
| Cards and tiles at rest | 4 dp |
| Selected tile | 10 dp, shadow in the axis's hue |
| Launch card, dialogs, menus, toasts | 16 dp |

A press scales to 0.97 and divides the lift by three, both on `Motion.press()`. A hollow
(`socket`) keeps the plate's low tone and takes a soft inner shadow under its top lip.

The launch card and the waiting screens lost their animated coral-to-teal rim: the
trailer's cards have none, and the rule is "no contour". `WaitTrim.kt` is gone with it.

The background's two shelves lost their 2 dp outline too. A very diffuse shadow replaces
it, blurred in software inside the bitmap the tray already bakes once, so it costs nothing
per frame.

## OLED

A shadow on black is not seen, so on OLED `liftShadow` draws nothing and the plate is
lifted off black instead: `PlateOled` `#16131F`, low `#0F0D17`. No contour there either.

## Frosted header

The header's lens (refraction, chromatic aberration) split the covers passing underneath
into rainbow fringes right under "No friends online". It is frosted glass now: the
backdrop blurred at 16 dp behind the plate's face at 82%, under a lift of 6. The chips on
it are plain plates: blurring a pane that is already blurred costs a layer and shows
nothing.

## Springs, taken from the trailer

The trailer gives each spring as a pulsation `w` (rad/s) and a damping `z`; Compose takes
the same spring as `spring(dampingRatio = z, stiffness = w²)`. They live in `Motion`,
each passing through `spec()`, so animations off still means `snap()`.

| Name | Use | w, z |
|---|---|---|
| `morph` | a surface changing size, shape or place | 19, 0.80 |
| `enter` | opacity and blur appearing | 30, 0.99 |
| `exit` | opacity and blur leaving | 42, 0.99 |
| `rise` | rows, text, buttons coming up | 28, 0.99 |
| `pop` | avatar, badge, dot | 20, 0.70 |
| `snapIn` | a code character, a key | 32, 0.88 |
| `camera` | a whole view moving | 12, 0.88 |
| `cursor` | the focus ring travelling | 16, 0.90 |
| `tint` | any colour change | 18, 0.95 |
| `draw` | a tick drawing itself | 22, 0.99 |

## Screens bloom, they do not slide

`SCREEN_IN_MS`, `SCREEN_OUT_MS` and the 28 dp shift are gone. The arriving screen grows
from 96.5% as it fades in on `enter`; the leaving one does the reverse on `exit`, faster,
and the two overlap. A settings page does the same inside its screen. No blur at screen
size: the brief's 12 dp on two full-screen layers made every page change drag on the Thor.

## Blur is paid only in flight

A shadow takes only its own layer's opacity, never its parent's: a card fading in inside a
cascade drew its full shadow under a still transparent card, a black flash. Every plate
multiplies its shadow by `LocalShadowFade`; `Cascade` and `ShadowsFollow` provide it, which
is why an appearance is a container around what appears and not a modifier on it.

A fade must not go through an offscreen buffer when what fades has a shadow. The buffer is
the layer's own size, so everything spilling out of it, the shadow first, is cut square
until the fade ends: that was the "square backgrounds for a second". Every fade here is
`CompositingStrategy.ModulateAlpha`, applied per draw, which is also cheaper; only a
layer that actually blurs takes the buffer. Compose's `fadeIn` offers no such choice, so an
`AnimatedContent` whose child has a shadow uses `fadeInPlace` with `EnterTransition.None`.

A blur exists only while something is arriving or leaving: above 0.995 of its progress
the render effect is dropped. Never more than two blurred layers at once, the screen
arriving and the screen leaving; that is why a cascading list rises without blur, twelve
blurred rows being twelve offscreen layers. With animations off nothing blurs at all. If
an animation costs frames on the Thor, reduce the blur (radius, then layers), never the
springs.

## Recipes

One function each in `ui/TrailerMotion.kt`, never copied screen by screen.

- `bloom`: the appearance above, for any element.
- `cascade`: the children of a list or card rise one by one, 60 ms apart, the ninth and
  after arriving with the eighth. Only while the screen is opening: a row scrolled into
  view later arrives whole.
- `popIn`: scale from nothing on `pop`, opacity on `enter`. An avatar joining, the friend
  just added, a tick's disc. Not replayed on what was already there when the screen opened.
- `DrawnCheck`: the disc pops, the tick draws itself 30 ms later along its own path, with
  the confirm sound. A step already done when the screen opens is shown done, silently.
- `TrailerSpinner`: an arc at 420°/s whose length breathes, replacing every
  `CircularProgressIndicator`. It runs on its own frame clock, not the app's slow one: it
  lives only on waiting screens where it is the one thing moving, and at twelve steps a
  second it stutters.
- `EventToast`: rises 30 dp, holds two seconds, leaves on `exit`. "X joined your session"
  uses it, with the pop sound.

## Objects transform, screens do not replace each other

"Create a session" closes into a round pill holding the spinner (0:25), and opens back if
the start fails. Width and corners move together, a pill at every width, so the radius
never jumps.

Changing the library's layout moves the covers: the leaving layout lets go of each cover
and the arriving one catches it where it lands (0:17). Carousel neighbours shrink and keep
55% of their opacity.

Not done, and why: the tile-to-card-to-session chain and the header-chip-to-screen chain
as `sharedBounds` on the containers. The cover already flies between the grid and the card;
the containers crossing screens would need the session screen and the friends screen inside
the library's `SharedTransitionLayout`, a restructuring of the app's navigation that the
brief rules out.

## The session code writes itself

At creation the six characters appear one every 115 ms, each dropping 28 dp from a blur
of 8 dp onto `snapIn`, with the tick sound (0:10). Once per code and per process: coming
back to a session does not rewrite it. A character typed on the join keypad lands the same
way, without delay.

## The auto-setup button, host and guest

The step completes while the player is in the emulator, the app in the background: played
at once, the tick drew itself unseen. Every step moment waits for the app to be in front
again, then a beat (`awaitSeen`). The rear panel's step buttons carry the same looks and
moments, since with the panel live the front ones are not drawn.

Step 1 has two lives. The host presses it: the button shows a spinner and "Setting …
up" while the automation drives the emulator (`netplayBusy`, from the automation's own
progress, let go after a minute if it never reports), then the disc pops and the tick
draws itself with the confirm sound, the button turns green, and step 2 wakes with a pop.
The guest's is greyed with a quiet spinner while the host works, at 30 frames a second
since a guest may wait minutes; when the host finishes it wakes on its own, colour rising
on `tint`, a pop from 92%, and the pop sound. Only a change seen on screen plays: a
button already done or awake when the screen opens is shown as it is.

## One look for the code

The rear panel drew the code in teal while the main screen's chip was coral: one object
in two colours. Both are teal now, with every other accent (see below).

## The elastic indicator

An indicator that moves does not slide as a block: the edge in the direction of travel
leaves on a stiff spring (`0.80`, `1024`), the other follows on a soft one (`0.80`,
`256`), so it stretches and closes up (0:24, 1:14). Used on the switch thumb and under the
theme swatches. The app has no pagination dots and no settings tabs; the layout selector is
a menu, where a travelling indicator has nothing to travel between.

## One cursor colour

The cursor is teal everywhere, and so is every accent of the interface: the social screens'
coral (join, session code, settings' social entries, the rear panel's social faces) read as
a second cursor and a second accent. Coral is left to content only: avatars, the logo, the
background's shelf. The session's leave cross is neutral ink.

## Shadows take the game's colour

A game's shadow is its cover's colour, at rest as well as selected; a console folder's is
the console's; a cover shown anywhere else (the launch card, a session in the list, the
rear panel) carries the same.

## The focus ring

A tile being selected grows on `morph` and leaves on `exit`, one spring still driving its
three marks. Its shadow goes to lift 10 and turns to the axis's hue by the same amount.
The ring itself stays drawn by each tile: a ring travelling across the grid on `cursor`
would need the grid to draw it above its own lazy layout, a rewrite of the cursor that
`bibliotheque.md` explains was costly to get right.

## Colours

Every colour change runs on `tint`: the step buttons turning green, step 2 lighting up
once step 1 is done, the switch track. Never a hard cut.

## Library

The carousel's recession and dimming are applied inside the flying cover, never by the
card around it: from outside, the cover flew at full size and shrank once it had landed.

A title takes two lines and an ellipsis. The fade over a third line left "Spyro: Dawn of"
reading as a whole title, and cost an offscreen layer per overflowing tile.

A game rated broken is greyed out on its tile rather than carrying a red cross, which read
as "delete". The cross stays on the game's card, where the verdict is explained.

## What was left out

- The background's slow drift (±12 dp, 27 s and 33 s). The shelves are baked into a bitmap
  once, see `theme-duotone-shelves.md` § MATERIAL (background); moving them means redrawing
  them, which is the cost that bitmap exists to avoid.
- The arrival pulse (101.5% and back). Optional in the brief, and it lands at the same
  moment as the bloom's own scale.
- Folding the manual address and port into a "Manual setup" block, and folding the three
  launch steps after a first session: both change what a screen says, which this pass
  does not.

## Friends

One card says who you are and takes a friend's code; the list is a grid of short rows, a
status line only when there is one, Join or a small cross. The page's explanations, the
section title and the privacy footnote are gone, the count ("3 friends · 1 online")
replaces the title. With the panel, the front keeps the card and the count, and the back
carries the same grid.

## Judging smoothness

A debug build is interpreted and says nothing about smoothness (CLAUDE.md). The `preview`
build type is the release's code under the debug key and its own package, installed beside
the real one: that is what motion is judged on.

## Where the cursor goes

Up from the content's top goes to the header, from any control: only the first control
knew the way, and the others stayed stuck or jumped sideways. The scaffold moves the focus
up itself and falls back on the header when nothing is above. Where the geometry misleads,
a control names it with `upToHeader`: on the friends card, Share sits lower than the add
field in the next column, and spatial search sent Up into the field.

## Incompatible games

The launch card of a game rated broken shows a red notice in place of its buttons, and
neither the steps nor the privacy switch: a session for it would only fail, later and without saying why.

## Cover tones

Never a state created on read. A per-cover state made during a composition crashed the app
twice (2026-09-26): the lazy list composes ahead in its own snapshot, which cannot see a
state born after it. One counter, made at application start, says a tone arrived; the tone
is read in the shadow's draw lambda, so an arrival repaints and recomposes nothing.

In the library the glow shows under the cursor only; forty tinted halos at once made the
grid a wash. Everywhere else a cover always glows. The hue is the one covering the most of
the picture, each pixel weighing its saturation squared: weighing saturation times
brightness let a small vivid logo or a face win, and Luigi's Mansion glowed yellow.
Checked on the 48 icons cached on the Thor.

A cover request always asks for the same fixed size (`COVER_REQUEST_PX`): left to layout,
Coil reads its memory cache only once measured, and a tile landing after a flight showed
its white plate for a frame.

A cover's glow is read from the picture displayed (`artwork/CoverTone.kt`): only the icons
embedded in 3DS and DS files carried an accent, so most of a library had none. The tint
goes to the ambient shadow too, which surrounds the shape where the spot falls below it:
a halo of the cover's colour rather than a stain under it. One state per cover, never one
map, or every tile recomposes each time any tone arrives.

## Folders

Entering a console's folder zooms in, the root growing past you as the folder rises; leaving
zooms back out, in every layout. Each view is its own composition: a folder opens on its
first game and the root comes back on the folder you left. The cursor used to keep its
number across both, so the second console opened on its second game.

## Session, two screens

With the panel carrying the steps, the front's two columns centre in their height and the
text card takes a readable measure: pinned to the top, they left half the screen empty.

## The rear panel

The details page is a fixed head (cover, title, facts) and the summary taking all that is
left, ending on an ellipsis. The screenshots are one press away (the pad's X, printed Y on
the Thor, or a tap): a viewer over the whole panel shows one large in the middle and its
neighbours smaller and fainter at the edges, sliding on the morph spring, with elastic dots
and the pad's legend. Its state lives in `SecondScreen`, beside the page, because the pad
that drives it is the front screen's; while it is open it takes the pad. Squeezed into a
band under the text they either cut the summary or were too small to see; in fixed white
frames a DS or 3DS picture came out tiny.

A cover keeps the art last resolved for its game when a second place shows it: the launch
card started from the ROM's bare icon, which flashed white mid-flight.
