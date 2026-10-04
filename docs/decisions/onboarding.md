# Onboarding: what the first run asks for, and in what order

Rebuilt on 2026-08-29. The headings are anchors cited from the code: do not
rename them lightly.

## The walkthrough has no fixed length

It had seven pages, the same for everybody: somebody who only plays DS crossed a
PPSSPP page and an ARMSX2 page that did not concern them, and nobody reads an
instruction that is not addressed to them.

The emulator pages are therefore drawn from what the player just answered on the
consoles page: hiding the PS2 removes the PS2 page, and the closing summary does
not invent a debt there. It is also what makes the consoles page genuinely
useful rather than decorative, since it cuts the rest to size.

The walkthrough is held by value rather than by index: hiding a console removes
a page, and an index would then point at the following one.

The autofill page appears only for consoles whose multiplayer goes through
driving the emulator: 3DS, Switch, GameCube, Wii, PS2. PSP goes through the
tunnel and DS through a DNS redirect, so offering an accessibility service to
somebody who plays only those two is asking a lot for nothing.

## Two columns, and they do not say the same thing

On the left the why: a mark, a title, a sentence, with no plate. It speaks over
the board like a screen title, which leaves the page's only plate to what gets
done. On the right the what to do: numbered steps, the state, the button that
works.

The previous walkthrough stacked everything in the middle of a narrow card, on a
screen 833 dp wide: two thirds of the Thor wasted, and a button that fell off the
bottom as soon as a page ran three lines long. In portrait the two columns become
a stack again, in the same order: you still read why before you do.

## The consoles page takes the full width

A third layout mode, used by one page only. `ConsoleGrid` is drawn to receive the
entire width; served in a column of 58% it fell back to three columns, so three
rows, so a page you scrolled through to answer a question that fits in a glance.

The why then becomes a banner, mark and title on one line with the sentence
below, instead of a column. It loses height, which is exactly what is wanted of
it there.

That was not enough: even at full width, seven full tiles make two rows, 300 dp
of card in a screen that leaves 322 to the whole page. Hence the short version of
the tile, see `reglages-ecran.md` under the short tile heading. Checked on
device: the seven labels sit at the same height, x=221 to x=1638, with 240 px
left before the buttons.

## The emulator rituals are the settings blocks, not copies

`PpssppBlock`, `Ps2Block` and `AutofillBlock` come from `settings/EmulatorsPage`
as they are: same steps, same state badge, same folder pickers, same error
messages. Redrawing them for the occasion would have produced two versions of one
procedure, the one you read while settling in and the one you re-read when it
does not work.

Deliberate consequence: everything is done from within onboarding, importing the
PS2 network profile into the memory card included.

Since 2026-10-01 the onboarding draws PPSSPP and ARMSX2 as fact sheets, not as the
settings blocks. What stays single is the procedure: the pickers, the preparation and
the error messages live in `settings/EmulatorSetups.kt` (`rememberPpssppSetup`,
`rememberPs2Setup`), and both drawings call them. Two drawings of one procedure, never
two procedures.

## Everything can be skipped, except the nickname

The folder can be chosen later, the notification permission can rightly be
refused, the emulator rituals can wait for the day you want to play that console.
An onboarding that holds somebody until they say yes is a trap, and this one has
to survive a no.

The nickname is the one exception, for a mechanical reason: it goes as it is into
the emulator's form, which refuses ones that are too short, and a refusal there
surfaces as a connection that never arrives. The field arrives pre-filled with a
valid value, so the block only affects somebody who actively cleared it.

Going back is the system button and the B key, never a third control at the foot
of the page: three things to read where there is a decision to make is too many.

## The summary names what was skipped

A list of congratulations teaches nothing. The closing summary says what is
missing and where to pick it up, one line per thing asked for, with its badge.
Lines that do not concern this player do not appear, same rule as the
walkthrough: a summary announcing "PS2: to do" to somebody who hid the PS2 would
invent a debt for them.

## Where you are, and what it is about

Dots alone said the length of the walkthrough and nothing else. Since this one
has no fixed length, they even said a length that changed under the player's eyes
on the consoles page. The step name answers the question they raised without
answering: where am I.

## French is re-read aloud, not word by word

Two correction passes were needed after the first draft, and the flaw was the
same each time: sentences that were correct but constructed, not sentences
anybody says. "Emufii monte le multijoueur a distance entre emulateurs", "les
autres quittent ta bibliotheque, et cette installation te fera grace des reglages
qui les concernent", "le scan demarre en fond", "un profil pointant vers le
tunnel".

The rule that comes out of it: English realigns on the revised meaning, never on
the French sentence, or it inherits the same stiffness the other way round. And a
badge label, a page title or a numbered step is read aloud before being written.

## Every page is a fact sheet that fits the screen

Rebuilt on 2026-10-01 in the session cards' format (`components/FactSheet.kt`): state
as tiles, numbered steps with the words that matter in the accent, one warning bar for
what must not be missed. In landscape nothing scrolls. The sheet gets the height left
between the rail and the buttons, and `FitColumn` drops its optional lines, in strict
order: once one gives way, every later one does too, so a short note never outlives the
warning above it.

Two traps paid for on the way. A line drawn outside its layout box (a negative offset,
a zero-height mark) is cut during the page change: the fade draws the page in a layer,
and the layer clips. Room is made inside the box instead, a spacer under the words as
tall as the mark above them, which keeps the words centred on the card. And the pages
are centred, not aligned to the top: the player asked for it after trying both.

## The welcome card answers "what does it do"

A list of facts ("7 consoles, 2 minutes, skippable") was rejected as filler. The card
shows the seven consoles by their library artwork, which exists whether the emulator is
installed or not (an emulator icon left holes on a fresh device), then a session in three
steps: a code, the friend types it, local multiplayer each at home.
