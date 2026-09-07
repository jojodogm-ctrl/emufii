# Gamepad: the cursor, the ring, and what was taken back out

The narrative that lived in `ui/Gamepad.kt`, taken out of the code on 2026-08-24
(see `docs/STYLE_COMMENTAIRES.md`). The headings are anchors cited from the code:
do not rename them lightly.

Completes the gamepad navigation section of `CLAUDE.md`, which keeps the main
rule: a lazy grid holds its own cursor, Compose focus traversal cannot aim at
what is not composed yet.

## Two jobs, separated on purpose

The device targeted is a handheld console: D-pad, two sticks, face buttons.
Crossing the screen with a thumb to touch a tile is the wrong way to use it.

Compose already moves focus on the D-pad and already treats Enter and the D-pad
centre as a click. What it does not know is that `BUTTON_A` means the same thing:
that key code belongs to a gamepad, and Compose leaves gamepads to the
application.

Hence two jobs, deliberately separated: `gamepadClick` makes a thing pressable
from the pad, `focusRing` makes it obvious which one. A control that takes focus
without showing it is worse than an unreachable control: the player presses A and
something happens elsewhere.

`B` is deliberately absent from the confirm buttons: it means back.

## The cursor never lingers

The ring does not fade out on leaving. It used to fade over 70 ms, on the grounds
that a hard disappearance would flicker. On a held D-pad that is not what
happens: the cursor is already two cells further on while the previous one is
still lit, and the eye reads the remaining glow as a second selection trailing the
first. A cursor is a statement about now; it has no business where it no longer
is.

The arrival keeps its animation: that one the eye follows on purpose. The default
spring took the same time in both directions, which produced exactly those two
simultaneous selections.

Everything marking the selected cell, the ring, its glow, the cell's growth, must
move on one clock, or the cursor breaks into pieces that arrive separately.

## Three things at once, and the breath that was taken back

The cursor is a lit cyan outline. The board's cyan is spent here and on the
primary action, nowhere else: one colour, one meaning. It replaces the mint green
of the "glass" world, which had to be a third colour precisely because blue was
already taken by every button; with the palette reduced to a single accent, the
cursor can simply have it.

It needs an outline and a wide coloured glow, so the eye finds it across the
board. At 14 dp on light plates the colour was barely visible: the player had to
look for where they were, which is exactly what this ring exists to prevent.
Doubled, it becomes a glow, and the marker is found out of the corner of the eye.

The slow breath was tried and then removed. The intent was right, a selected
object on a console menu is never quite still, but a `shadow`'s elevation is not
a brightness knob: at every value it recomputes the shape's shadow, and an
animated elevation under a surface that is not perfectly opaque is seen through
the surface. The glow slipped inside the cursor, drifted, and left a moving hole
in the middle of the very element it was meant to point at. A cursor whose inside
moves is worse than a cursor that does not breathe.

## The ring surrounds, it does not clip

`focusRing` places its halo with `Modifier.shadow(elevation, shape)`, and Compose
defaults there to `clip = elevation > 0.dp`. The halo being animated from zero to
its full value, the ring therefore started clipping the control to its own shape
at the precise moment it lit up.

Invisible everywhere the ring's shape is the control's, which is to say
everywhere but one place. On the profile avatar the ring is a circle and the
pencil badge sits at the corner of a square box, therefore outside that circle:
it half vanished as soon as the cursor arrived on it, and that is exactly the
element that has to stand out.

`clip = false`, everywhere and unconditionally. A cursor signals, it does not
recut what it signals, and a control overflowing its focus shape is a legitimate
composition, not an error to trim away.

### What must stand out of the ring is declared after it

`clip = false` made the badge whole, but not visible: `Modifier.border` draws over
the content of the node carrying it, so the ring's stroke still went straight
through it. A child of the ringed box is under the ring, whatever you do.

On the profile avatar the ring is therefore placed on the photo's box alone, and
the pencil badge is declared after it, as a sibling rather than a child, and so
drawn on top. Focus stays on the outer box, which contains both: one cursor stop,
and the finger reaches the badge too. `controlRing` is kept there but silent
(`enabled = false`) for its `bringIntoView` alone; it is `focusRing` that draws,
underneath.

General rule: an element that has to cross the focus outline is not inside it.

## The ring keeps the same weight everywhere

`controlRing` is exactly `focusRing`, the tiles' one, at the same values and with
the control's own shape: not a shape rebuilt from a radius, not a larger frame,
no gap between the two.

The tiles always rendered correctly. Everything else that was tried, reserving a
few dp and drawing inside them, recomputing an outer radius, repainting the glow
by hand, amounted to reinventing what already worked, and every variant missed
the edge somewhere different.

The stroke and the glow were reduced for a while, on the grounds that a button is
smaller than a tile. That was a mistake: this drawing, at these values, is what
was approved on the tiles and on the back button, and weakening it gave a dull
cursor you have to hunt for. A ring must have the same weight everywhere, or it
stops reading as the same object.

The nuance that stays true: the default values are the tiles', 150 dp wide, a
stroke of 4 and a glow of 28. Applied as they are to a 46 dp button they visibly
overflow and read as a badly placed outline, which is what was seen on the
header's back button. Small controls therefore pass reduced values: it is the
same ring, at their size.

## The ring reads focus itself, and the order matters

`onFocusEvent` sees the focus of the nodes below it in the chain, so the
control's: no more threading a `MutableInteractionSource` down to a Material
`Button` that does not expose one. That is what lets a screen be fitted out
without rewriting it.

To be placed before the `clickable` or the `focusable`, never after. Placed
after, it sees nothing and stays dark while the cursor is very much there.

## Nothing must stop under the header

Content scrolls under the floating header, which is not a bar but a layer placed
over it. To Compose, a control slid underneath is "visible": scrolling therefore
stopped as soon as it reached that level, and going back to the first element of
a page never brought the top of that page back.

By asking for the whole band, the request overshoots the start of the content and
the scroll settles at zero. Hence the top inset at least equal to the header's
height, published as a `CompositionLocal` rather than a parameter: every control
would need it, and none has to know the value.

## The outermost control pulls the page to its edge

`bringIntoView` moves the least it can. That is right in the middle of a page and
wrong at either end: these columns finish on 24 dp of padding plus the navigation
bar's inset, more than the 28 dp the ring asks for, so the bottom control settled
against the edge with that strip still hidden and the page read as cut off. The
top does the same against the header.

The page therefore goes all the way when the control receiving focus is the
outermost one. Two things were got wrong before it worked:

- **Correcting afterwards shows the seam.** Asking for the usual `bringIntoView`
  and then finishing the travel lands in the right place in two animations, and
  the join between them is visible. The choice is made before anything moves, and
  the page travels once.
- **How far the page has left to travel says nothing.** It is a page-wide number.
  A settings page barely longer than the screen has little of it under every
  control alike, so going by it slammed each page to its end and held it there on
  the way back up. What is measured is past the *control*: where it would sit with
  the page pushed to that side, and how much content would be left beyond it.
  Only the outermost control has next to nothing beyond it.

A control also has to survive the move, whole and clear of both the header and the
bottom edge, or it is not the outermost one. A page barely longer than the screen
answers to both ends at once; the nearer edge wins, being the one walked towards.

The scrolling column reaches the ring as a `CompositionLocal`, empty on a page
that does not scroll. The library is not concerned: its grid is a
`LazyVerticalGrid` that scrolls itself, and a lazy list does not lay out what is
off-screen, which is what this measurement reads.

## One radius, named once

The radius of the large action buttons is named separately and shared: guessed at
each call site, it ended up no longer matching the button it surrounds. The ring
needs the number, not the shape: it draws its own, wider outline, and must be
able to add the gap between them.

## One rounded rectangle per path, never two

The ring's band was at first a filled ring: two concentric rounded rectangles in
`EVEN_ODD`, the large minus the small. The shape was right and the cost invisible
on reading.

Skia only recognises a path as a rounded rectangle if it contains exactly one,
and rasterises everything else on the CPU, into a mask it then uploads as a
texture. And the thickness animates as the cursor arrives: every frame gave a
shape never seen before, therefore a fresh mask. Measured on the Thor on
2026-08-29, scrolling fast down the grid: the software mask cache climbed to
27 MB across 372 entries and kept climbing, for one cursor.

The same surface is drawn with one stroke, the band's centre line, `band` thick.
Coverage identical to the pixel, the inner and outer edges falling exactly where
they fell, and the GPU draws it without going back through the CPU. After the
fix: 0.4 MB across 5 entries.

It is the same lesson as the halo, moved from a Gaussian blur to stacked strokes,
applied this time to the band itself. Do not reintroduce a path with several
subshapes into this file.

## `Modifier.alpha` clips, and that is what made the cursor square

`Modifier.alpha()` is not only an opacity: below 1 it sets `clip = true`, a
rectangular clip at the element's bounds. The ring surrounds the tile from
outside; during the tiles' arrival it was therefore cut square, and the cursor
looked rectangular.

It only showed on returning to the library, the one occasion where an
already-selected tile replays its arrival. And the gap lasts the last hundredth
of the spring: the ring lights as soon as the arrival passes 0.99, the clip only
lets go at exactly 1.00. Two different thresholds for two effects believed to be
linked.

Fix: `graphicsLayer { alpha = ... }`, whose `clip` defaults to false. What has to
be clipped to the tile's shape is clipped lower down, by the `clip(TileShape)`
that follows.

Two other places carry the same construction and are left as they are: the
carousel, where opacity only drops on an unselected card and therefore without a
cursor, and the launch dialog, where that clip is currently the only thing
holding the content in during the appearance.

## The selected control draws in front of its neighbours

The ring overflows its layout bounds, which is its definition. Between siblings
the last drawn wins: a row of tiles covered the right half of each other's ring,
and the last was the only one showing its own in full. Seen on the console grid,
in settings as in onboarding.

The library tile already had its `zIndex` set by hand for that exact reason. The
rule being the same everywhere, it now lives in `controlRing`.

It only holds between siblings: a grid with several rows must still raise the row
carrying the cursor, which `ConsoleGrid` does. The two together clear the ring in
all four directions.

## The cursor cannot draw in front of the top bar

Tried on 2026-08-29, in two variants, and abandoned both times.

The library's top bar floats above the grid: it therefore draws in front of
everything the grid draws, and the ring belongs to the tile, therefore to the
grid. A `zIndex` cannot help, since in Compose a child never draws in front of
its parent's sibling.

The attempt: take the ring out of the tile, publish the tile's rectangle in root
coordinates, and redraw it in a layer placed after the bar. It did draw in front,
and trailed a frame behind its tile as soon as you scrolled. Two variants, the
same lag twice: repositioning by recomposition first, then a deferred read in the
layout phase. A layer chasing coordinates always learns them after the layout
that produced them.

The ring is drawn with its tile, in the same pass: that is what makes it exact,
and that is what confines it to the grid. Tracking accuracy and drawing in front
of the bar are mutually exclusive. Passing behind the shelf is therefore settled
by spacing, see `bibliotheque.md` on the air under the bar.

## The four layers of the neon cursor

What the old ring did in two strokes, a 4 dp `border` plus a `shadow` diverted
into a halo, is done here in four layers, and it is the stack that produces the
effect, not the colour:

1. The glow: three concentric strokes from widest and palest to finest and
   densest, placed on the band's centre line. It is the profile of a blur,
   sampled at three points. It was a real `BlurMaskFilter`, and that is what made
   it right; it was removed because it has no GPU equivalent, so Android drew the
   path on the CPU every frame, permanently, the cursor always being on screen.
2. The band: one stroke at the centre line, `band` thick, carrying the flowing
   gradient. It was a filled `EVEN_ODD` ring, see the one-rounded-rectangle-per-
   path heading.
3. The outer rim: white, in a vertical gradient from 50% to 30%.
4. The inner rim: flat white at 40%.

The two rims are what make the band read as a glass object placed on the screen
rather than as a flat fill. They are fixed, never tinted: their role is to catch
light, not to say a colour.

Every measurement is a fraction of the band width, itself a fraction of the
control's size: the cursor grows with what it surrounds, instead of keeping a
thickness that crushes small controls and disappears on large ones.
