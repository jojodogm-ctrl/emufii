# Session: the host/guest order, the two panels, the per-console cards

The narrative that lived in `ui/screens/SessionScreen.kt`, taken out of the code
on 2026-08-24 (see `docs/STYLE_COMMENTAIRES.md`). Headings are anchors cited from
the code.

## Host then guest is not a comfort detail

A guest who settles in before the host finds nothing. The room does not exist
yet, the emulator answers "no session", and the player concludes the game is
broken when they simply arrived too early. Nothing on screen stated that order:
both players saw the same button, ready to be pressed.

The default state is true: that is what a coordinator ignorant of the question
will answer, and it is also what holds for the host, who waits for nobody.

An upstream room changes nothing about that order, and that was a reasoning error
corrected on 2026-08-10 after a two-player game. The first version excluded
sessions with an upstream room: nobody hosts there, both players join the same
room, so, I thought, no order to respect. The guard therefore never showed, since
every Switch session has one.

What that reasoning conflated: the room and the game are not the same thing. The
room exists from the session's creation, but what the guest looks for in Eden is
the game's LDN session, which only exists once the host has opened it from their
game. Arriving first means staring at an empty list, exactly the original
symptom.

The order therefore holds for any backend with a room to join, with or without a
relay on the VPS.

### Two proofs that a room exists, and the second is knowingly weaker

The automation only runs to the end with the accessibility service; a host who
refused it sets up by hand. With no second signal their guests would wait for a
"Done" that never comes, and a queue with no exit is worse than no queue at all.

The second signal is therefore the simple fact of having come back into Emufii
after opening the emulator. It is weaker, and that is accepted: at worst a guest
leaves a few seconds too early, which they did anyway before.

The "setup went as far as the room" flag is latched rather than read live off the
progress flow: starting the game disarms the plan, which returns that flow to
rest, and the setup would stop appearing done at the precise moment it starts
mattering. It is distinct from "the emulator was opened", which says only that.

Coming back from the emulator is also the moment to notice that the automation
never showed any sign of life (`NetplayAutomation.neverStarted`): that silence has
a cause the player can act on, and saying nothing reads as "the app is broken".

## Only a 404 proves a room is closed

A guest whose host has closed the room was left in front of a screen that looked
alive forever. So a few failures are tolerated, mobile networks losing requests,
and then it is said and we leave.

But only a coordinator that answers 404 proves the room has gone. A silent
coordinator proves one thing only: that we cannot reach it. On a Wi-Fi hiccup,
that announced "the host has closed the session" and tore down a tunnel whose two
peers were both still there. The app now says what it knows and leaves the session
alone: WireGuard redoes its handshake by itself as soon as the network returns.

Likewise, a coordinator gone silent is deliberately not an error: the tunnel is a
direct WireGuard pairing that no longer needs it once up, so a running game keeps
running. Only the list of who is there stops being trustworthy.

## The address shown is the one to type, never another

On PSP, the host's address is nobody's address on screen: the player types it
nowhere. What they set in PPSSPP is the sentinel, which the relay translates to
their session's host. Showing both put two different addresses on the same screen,
presented as the same host's, and the useless one was the one carrying the word
"address".

With a room on the VPS, the address to dial is the room's, and the host's is no
longer anybody's address: nobody hosts. Showing it anyway would put on screen,
under the word "host", an address the player must not enter, and that is precisely
the screen they are looking at when the automation fails and they type by hand.

The value is computed outside both columns: computed in one, it would only be true
on one side.

## Two panels, because stacked this screen does not fit

Stacked, this screen is eight full-width cards and three 56 dp buttons in a
scrolling column: on the Thor's 468 dp, the player never sees more than a third of
their own session, and the code, the one thing they read aloud to somebody, leaves
the screen as soon as they scroll.

On the left the state, which does not move: the code, who is there, the address.
On the right what is left to do, buttons pinned at the bottom. The rule that cost
dearly still holds, and for free this time: the answer to a press is under the
button that produced it, in a panel that does not scroll.

### The state panel does not scroll, so it has to fit

It had a `verticalScroll`, and a card ended up shifted out of the panel with
nothing asking for it: a state panel able to hide its state is not doing its job.
It fits because the code moved up into the header: at two cards, both keep their
full shape, where squeezing three in ended up clipping the address.

It is presence that gives way, never the address. The panel does not scroll and
its height is the screen's: what does not fit is clipped. Without weights, the two
cards were measured in order, presence took what it wanted and the address
inherited the rest; at two players the arrivals list grows and the rest stopped
being enough, the copy buttons' labels disappeared, then the port button itself
was cut off.

The weight inverts the measuring order: Compose measures unweighted children
first, so the address gets its natural height, whole, and presence makes do with
what is left, scrolling inside rather than being cut. Losing sight of the third
line of a player list costs nothing; losing the button that copies the port stops
you setting the game up.

What overflows the panel's foot fades out rather than being sliced mid-word: the
panel's height is the screen's and its content is a paragraph; sliced, the last
line read as a rendering fault, the same complaint as the board's half-rows.

### Down aims at the first button that answers

The "down" destination is the first button that exists and answers. It was
"Launch", the only one every backend shows, but that one is disabled until the
previous step is done, and a disabled button does not take focus: going down
failed and the cursor went off into the left column.

The panel fades, then a real gap, then the buttons. Without that gap the last line
dissolved straight into the top of the pill and the two read as one broken element
rather than as text continuing below the fold.

A greyed button stays reachable from the pad. A disabled `Button` stops being
focusable, and it is the column's only stop here: a waiting guest ended up on a
screen where the D-pad finds nothing at all, and so was stuck. Focus does not
promise a click will succeed, it says where you are.

## What is done by hand is said before the button, never after

The PSP card comes before everything else, right after the code: it is a thing to
do in another program, once. Leaving it at the foot of the column, under the
button that starts the game, meant showing it after the moment it was useful. The
other consoles keep their card at the end of the screen: they have nothing to set
before playing.

Same reasoning for the Azahar nickname: before the buttons, not after. It is the
one thing the player has to do by hand in the emulator, and Azahar refuses the
room for that reason while blaming the address; a prerequisite printed under the
button it applies to is read after the mistake, if it is read at all.

And the answer to a press is shown directly under the button that produced it, not
at the foot of the column: that column scrolls, and on a handheld in landscape its
bottom is off screen. Rendered last, the answer landed where the user could not see
it, and a launch refused for a good reason, an emulator with no multiplayer
interface for instance, was indistinguishable from a dead button.

## The per-console cards, and what each must prevent

- PPSSPP has no netplay to drive, no accessibility service can do it, but it has
  settings the player has to enter themselves and cannot guess. The button does
  not apply them: it opens the emulator, which is all Emufii can do, and says so
  plainly in its label rather than implying an automatic setup like Azahar's. The
  four settings are shown with the address already in the clipboard: copied on
  display rather than on click, because the player is about to leave Emufii for
  PPSSPP, and having to come back and press a button they did not see before
  leaving is exactly the round trip this card exists to avoid. The button stays,
  for whoever comes back later.
- Eden: its multiplayer is not in a game drawer but in the app's settings. The
  card therefore says where to go and, when autofill is active, that there is
  nothing left to type once there. The host is invited to Create and the guest to
  Join: unlike Azahar, this screen can open before a game is running, and saying
  the same thing to both would put both players on the same side of the room.
- Dolphin has no step 2. The game is not launched here and then joined: it is
  chosen in the room, by the host, once the room is up, and Dolphin cannot be
  handed a game from outside anyway. The card therefore never says "start your
  game", and saying so plainly beats falling back on "not supported yet", which
  was false and discouraging.
- PS2: ARMSX2 can do two unrelated multiplayers. Local mode (Local Link) is the
  one Emufii serves, for the sixty-odd games shipped with a LAN or System Link
  mode. Online mode goes through a revival server, over plain DNS, with no session
  and no tunnel: Emufii is of no use there, and implying otherwise would produce
  exactly the wrong expectation. Hence the warning at the head of the card, before
  anything else.

### The Dolphin prerequisite nobody checks

Both sides need the same dump, byte for byte: netplay hashes it and silently
refuses otherwise. That, the emulator only mentions once it is too late.

But the save is just as loud and more treacherous: the dump, Dolphin checks and
refuses; the save, nobody checks. Two players starting from different states join
the room, start the game, and each see a match that does not exist for the other,
with no message anywhere. Measured on Brawl on 2026-08-16: one was at the "create
a save" menu, the other had already passed it, and it took an evening to work that
out.

Desktop Dolphin has "Sync Save Data", which pushes the host's save and makes all
this invisible. This Android version does not expose it, and Emufii cannot take it
on: the saves live in Dolphin's private storage. So we warn, for want of being
able to act.

## What each backend receives at launch

Azahar: both roles point at the host's tunnel address, the guest to reach it, the
host because `netPlayCreateRoom` binds and joins itself on the same address (see
`PHASE0_AZAHAR.md`). Its own tunnel IP is the only value that works for both.

The nickname, for both roles: two players with the same nickname cannot share a
room, and every emulator ships the same one to everybody by default. Eden and
Dolphin always get the profile name; Azahar gets it only while its field still holds
the default, so a nickname the player chose there stays (`pilotes-emulateurs.md`,
the nickname section).

The session code doubles as the room code on PS2: ARMSX2 demands one, identical on
both sides, and negotiates nothing. It is the secret both players already share.
Useless elsewhere, the other emulators having no field to put it in, except for
VPS rooms, which carry their own.

Dolphin: a return, not a launch. The game is chosen and started in the Dolphin
room. This button has to bring the player back to where the game is waiting, after
a round trip through Emufii. The launch intent resumes the existing task instead of
opening a fresh one: the room is still on screen behind, and you land straight back
on it. If Dolphin has been killed meanwhile you land on its game grid, which is the
best available, `NetplayActivity` not being exported and therefore not targetable.
And above all: no armed plan. The room is already open; re-arming would send the
driver to fill the form over a running game.

PS2: a real launch, unlike Dolphin, ARMSX2's `MainActivity` being exported with a
VIEW filter on `content`, so the SAF ROM travels with the intent. No armed plan for
all that: the network was set at step one, and re-arming would there too send the
driver over a running game.

Finally, a PS2 image whose boot ELF is unreadable takes a separate branch: its
network shape can still go through the established accessibility driver, but the
prepared card first demands the single inherited global assignment that per-game
files avoid. We keep that extra navigation out of every supported ISO/CHD, and make
it explicit here rather than silently starting a game with no network profile.

## Back closes the session, and it says so

There were two controls for one gesture: the header's back button, which left
immediately, and "Close session" at the other end of the same bar. The first
promised to go up one screen; it cut the tunnel.

One remains, and it is the one you find without looking because it is there on
every other screen. It changes its mark, a cross, in shell red, and it asks before
acting. The red appears twice in the whole app; this is one of them, and that is
what makes it readable.

The question is not the same on both sides: the host closes the session for
everybody, the guest withdraws from it. And both wordings say what closing does
not do: a game already launched keeps running.

In portrait the bottom button stays: the page there is a column you go down, and
ending on the exit action is the natural order. It goes through the same question.

## The game is shown in the space the panel left

When the rear panel carries the address and the steps, the front screen's state
column ends with three hundred pixels of nothing, on a screen where you are
waiting for somebody, and where there is therefore nothing else to look at.

The artwork goes there, framed as on its tile: same plate, same outline, same glow
borrowed from its own colours. It is what the session is, and it is the screen's
only colour, coming from content, never from chrome.

Two points of plumbing:

- The session carries only a ROM reference, with no icon and no extracted colour.
  The artwork is found back in the library by its URI, in the already-warm cache,
  off the main thread, and never triggering a scan on its own. Nothing is shown if
  it is not found.
- Presence keeps priority. Both blocks are `weight(fill = false)`: the players'
  card takes what it needs, the image takes what is left, and a list longer than
  its half scrolls in its card as before. The image disappears rather than
  squeezing anything below 96 dp.

And the right column's fade only exists on a single screen, at both ends. It is
there so over-long text dissolves instead of being cut mid-word, which happens
when both controls live under it and take its height. With the panel lit they are
on the back, the column has the whole screen, and a gradient dimming the foot of a
full card then reads as a display fault.

Same rule, same reason, as the artwork: what the panel takes, the front gets back;
what the panel gives back, the front puts back. An in-between state where the front
keeps the habits of both works in neither case; the first version showed the
artwork in both, and on a single screen it capped the presence card at half a
column that was already carrying the address.

## Copying the address stopped making sense once Emufii fills it in

The connection card carried two chips, "copy address" and "copy port". They come
from a time when the player filled the emulator's form by hand.

Three reasons to remove them, and the third is enough:

- Emufii fills the form. It is the function the accessibility service exists to
  provide.
- When it cannot, Android having switched the service off, the explanation card
  above says what to type, and the value is shown right next to it. The clipboard,
  meanwhile, holds only one value at a time while the dialog asks for two: it was
  never the right answer to this problem.
- They cost 62 dp, and that is exactly what was missing for the panel to fit
  without scrolling.

The code is still copyable: it is what you send a friend in another application,
and nothing else carries it.

## What the rear panel carries, the front screen does not repeat

In session, the panel already shows the code large, the address and the port in
their sockets, and the game's name. While the front screen repeated them, the
player read the same thing twice and the connection card ate 150 dp.

When the panel is really lit, the setting on and a second screen present, the
connection card leaves the front screen. The setting alone is not enough to
decide: a device may have only one screen.

The buttons, though, do not move, and cannot. The panel's window carries
`FLAG_NOT_TOUCHABLE`: nothing on it is pressable, by construction, so it never
steals a press meant for the game. The panel reports, it does not command, which is
written in `second-ecran.md`, and it is also what the hardware imposes. Moving
steps 1 and 2 to the back would give two buttons nobody can press.

What moves instead, and gives back the room asked for: the state column goes from
272 to 220 dp when the panel is lit, only presence being left in it. The 52 dp
returned go to the right column, where the explanation then fits on fewer lines.

A divergence found along the way, and fixed. The front screen computed
`room?.host ?: hostIp` and the panel received a raw `hostIp`: an Eden session with
a room would have shown on the back an address the emulator does not expect. While
both were shown side by side it barely showed; the day the front stops repeating
it, it becomes the only value shown. The rule now lives once, on
`Session.shownAddress` / `shownPort`, with the PSP case inside it, its ad hoc
server having a fixed name and no port.

## This screen's drawing decisions

The code moved up into the header: it is what you read aloud to somebody, so it
has to stay permanently visible. The chip is the library's chips', the same
height, the same radius, the same shadow, so the header stays a row of floating
objects. A press copies: it is the gesture you want to make in front of a code.

The destructive button is a moulded chip, like everything you press. It was bare
red text floating on the board, the screen's only control made of nothing, and it
was precisely the destructive one. Red ink on a plate says the same thing without
pretending to be a link.

The tick is drawn rather than imported: two strokes cost less than pulling in the
whole material-icons artefact for one glyph, and unlike a "✓" character it sits
exactly where it is put, text glyphs being centred on their line box, not on their
ink.

The presence list is the whole point of the presence loop: hosting was a screen
with a code on it and no way to know whether anybody had come. Its internal
scrolling is active only in the panel, whose height is fixed. Elsewhere it is
false, and that is not a matter of taste: the single-column page already scrolls,
so it measures its children at infinite height, and Compose refuses, by throwing,
to measure scrolling content under an unbounded constraint.

The fade-out gradient is declared before the scroll, and the order is the whole
subject. Placed after, it works in the unrolled content's coordinates:
`size.height` there is the total height of the text and the gradient would land
below the fold, invisible. Placed before, it wraps the scrolling node, so it
measures the window and the fade stays stuck to the foot of the card. It becomes
opaque before reaching the edge, not only at the edge: a linear gradient running
to the bottom left the top of the last line under 40% coverage, therefore readable
and sliced, measured. Its height has to erase a whole line, leading included: at
28 dp the cut line stayed half readable.

The game's name is not shown in the panel, where its forty dp were exactly what
was clipping the copy buttons. The game's name is not a state you act on, the
player has just launched it, and the other panel already talks about its emulator,
whereas the address does get copied.

The important note is a hollow, and its mark is drawn. Two rounds to get there. It
started as an `errorContainer` filled at 55% under an "IMPORTANT" in spaced
capitals: an eyebrow, which the guidelines ban, on a saturated red field the size
of a paragraph that pulled attention away from the panel at the expense of the
buttons that are the real work. It became a hollow with a 3 dp red bar along the
reading edge, better, and still two things wrong: a coloured side border beyond a
hairline is refused on insets, and red is this product's danger colour, spent
exactly twice in the whole app; two warnings on one screen spent it four times,
which is what makes it lose all meaning when something really is wrong. So: the
board's hollow, ordinary ink, and the same warning bead the library already
carries on a game that half works. Drawn, neither typed nor filled: it says "read
this one" without claiming anything is broken.

The PSP card's comfort tip is at the end of the card, not in the middle: it is the
screen's longest paragraph, and slipped between the address and its "Copy" button
it separated the gesture from what it acts on. It is deliberately not an important
note, that block being reserved for what stops you playing, but not in the grey
voice of the notes above either, where it would read as filler. Hence a title, and
a body at full strength.

The list of who is present is put into a sentence by ICU ("Toi, Bibi et Théo" /
"You, Bibi and Théo"): writing it by hand was fine while the app spoke one
language, but the conjunction and the placing of the commas change with the
locale.

## One `focusRequester` per node, and it is the shell's

There were two stacked on the same node, the driver's and the shell's, and the
trace said the node never took focus: neither `focus=true`, nor even
`hasFocus=true`, not once. As a result, the request the header sends on Down fell
into the void, the key was consumed anyway, and a second one was needed to get back
to the panel, while Up, which asks nobody for anything, worked first time.

The driver receives keys without having focus, because it is near the root and a
key event bubbles up the chain. But receiving is not being designated: for the
cursor to be given back to it, it has to be a destination, and a destination has
only one address.

The driver then claims the cursor frame by frame. A single request after 150 ms
held before: Compose's initial focus designates the header, declared before the
content, and it won against it. The delay was a bet on timing; the loop is the same
answer as the shell's, ask again while the node is not placed, without checking
whether it worked, and bound it.

## Back closes the session, so it carries a cross and it asks

There were two controls for one gesture: the back button, which left immediately,
and "Close session" at the other end of the header. One remains, the one you find
without looking, because it is there on every other screen, and it stops promising
to go back: a red cross, and a question before cutting the tunnel.

## What the panel carries, the front screen gives back in space

Three places apply the same rule, and each gives its space back to something else:

- The code chip only appears on the front if the panel does not carry it: it shows
  it at 64 sp on the back, and repeating it at 19 sp serves nobody.
- The state pane is narrower with the panel lit, only presence being left, and the
  52 dp returned go to the right column, where the explanation then fits on fewer
  lines.
- The framed game is shown only in the space the panel left. Rendered in both
  cases, it broke the presence card on a single screen: two weighted children
  share the free space, so the players' card ended up capped at half a column.
  With no panel there is no space to fill, the panel being what creates it by
  taking the address.

And the bottom fade only exists on a single screen. It is there so over-long text
dissolves instead of being cut mid-word, which is the case when both controls live
under it and take its height. With the panel lit they are on the back, the column
has the whole screen, and a gradient dimming the foot of a full card then reads as
a display fault.

## Copying the address stopped making sense once Emufii fills it in

The "copy" buttons come from a time when the player filled the emulator's form by
hand. Emufii fills it for them, and when it cannot, Android having switched the
service off, the card above says what to type.

The clipboard could only ever carry half the thing anyway: it holds one value at a
time and the dialog wants two.

They cost 62 dp, and that is exactly what was missing for this panel to fit without
scrolling.
