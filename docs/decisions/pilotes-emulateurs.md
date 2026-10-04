# Driving the emulators: the accessibility service, and what it cost

The narrative that lived in `azahar/AzaharNetplayService.kt`, taken out of the
code on 2026-08-24 (see `docs/STYLE_COMMENTAIRES.md`). Headings are anchors cited
from the code.

## Why an accessibility service, and not something else

None of the emulators exposes IPC for multiplayer. Azahar's manifest exports only
`MainActivity` and `EmulationActivity`, and netplay lives behind JNI
(`netPlayCreateRoom` / `netPlayJoinRoom`) with no intent extras at all. Writing
the SharedPreferences would need root or `run-as`. Driving the interface is the
only path that works on an unmodified, sideloaded build.

The service is inert while no plan is armed: it does nothing of its own accord,
and never touches anything but the targeted packages.

It is best-effort by design. Azahar is a moving target: when a resource id moves,
we stop rather than click anywhere, and the interface falls back on showing the
address to type by hand.

## The class keeps its Azahar name, and that is not carelessness

It serves Eden too now. Renaming it would change the `ComponentName`, and it is
the `ComponentName` Android records when the user enables the service: a rename
would therefore switch the automation off, silently, for everybody who had already
enabled it. An inaccurate name is the cheaper of the two costs.

## Three screen families, three drivers, one standard

Azahar and Eden share a walk based on resource ids.

Dolphin cannot join it: its netplay screen is Compose and exposes no id, so
nothing about it can be expressed in that walk. It has its own object, which
guarantees the 3DS and Switch paths cannot be reached, let alone modified, by
anything Dolphin does. It also caches the emulator's labels, so it has to outlive
a single event.

The PS2 is the third screen shape, and it gets the means to re-read the tree:
entering a value in ARMSX2 means a dozen clicks in a row on its own keyboard, and
the screen redraws at every key. The other two drivers do not need it, writing
their fields with a single `ACTION_SET_TEXT`.

A package none of the three knows leaves with nothing touched.

## Acting on events is not enough: you have to look again

Acting on events alone assumes a screen's last event arrives after that screen is
usable. Azahar's multiplayer sheet proves otherwise: it slides in, all the events
fire while its buttons are still off screen, and then nothing more arrives, so a
flow that had just opened the sheet correctly was left contemplating it. Seen on
the Thor, and indistinguishable from "the automation never ran".

A few spaced re-reads cost nothing when there is nothing to do, and they are the
only things that catch a view that arrived late.

A pass that made progress gets its re-read budget back. Without that, the number
of looks following an event caps the length of the route, and the PS2 goes well
past that cap: menu, settings, tab, mode, two scrolls, then a field opened,
cleared, typed letter by letter, confirmed. Azahar and Dolphin fit in three or
four screens and had never hit it. The driver therefore stopped halfway, after a
scroll, without a word: from outside, "automatic setup does not work". Renewing
the budget does not open an endless loop, each driver having its own caps.

## An armed plan must not make the game unusable

The navigation steps, the in-game drawer entry, the settings tab, the Multiplayer
card, are the only ones firing on a screen the player opened for their own
reasons.

An armed plan that never reaches a room form therefore re-clicked Multiplayer
every time the in-game drawer appeared, which is to say exactly when the player is
trying to reach Quit: the drawer became unusable. Reported from the Thor.

A cap turns "forever" into "two or three tries, then get out of the way". Filling
a form already on screen stays unlimited: that one only fires where the player is
doing what they were asked.

## Visibility is the right filter for locating yourself, the wrong one for acting

Visibility distinguishes "this screen is in front" from "this screen exists
somewhere in the hierarchy": it therefore governs every decision about where we
are.

But it is the wrong filter as soon as it comes to acting on a form we have already
decided is in front. Two real failures, the same cause:

- The OK button is usually below the fold. The form is a scrolling bottom sheet,
  and on Azahar's "create" box, one field more than "join", on a landscape screen,
  OK starts off screen. A search filtered on visibility found nothing, and Emufii
  fell back on "the fields are filled, press OK yourself": that is the famous "the
  click does not take" this project chased, and there had never been a click.
- The host's button could not be found. The sheet stacks Lobby / Join / Create,
  and a bottom sheet on a landscape screen cuts off the last. The host's button
  was therefore the one the search never found, while the guest's showed
  comfortably: the automation looked as though it had a broken idea of who hosts,
  when it simply had the same filtered search already hiding `btn_confirm` and
  `room_name` on that device.

The remedy is the same in both cases: bring the node on screen, then press. And
the sheet is "in front" as soon as any one of its three buttons is visible, three
rather than one, because only the top one is reliably in view, and which one that
is belongs to the emulator's layout, not to us.

## A click that does not take is not a success

Eden's OK button declares itself enabled and clickable and ignores the action
anyway, seen on a device, box left open. Believing our own request would tell the
player everything is done while the room was never created.

So we say what actually happened: the fields are filled, the last press is theirs.

## The host creates, the guest joins, and it is logged

A guest who creates their own room joins nothing; a host who joins looks for a
room nobody opened. Logged, because both failures are identical from outside, a
box that fills then refuses, and the only way to tell them apart is knowing which
button was taken.

## The nickname replaces a default, never a choice

Two players with the same nickname cannot share a room, and every emulator ships
the same one to everybody by default: without this, two Emufii players introduce
themselves as the same person and the second is refused.

Eden and Dolphin get the profile name every time. Azahar gets it only over its
default: an empty field, one under Azahar's five-character floor, or the shipped
names (`AZAHAR`, and `Citra` / `Lime3DS` from older builds). A nickname the player
set in Azahar stays. Two earlier rules each failed one way: writing the profile name
unconditionally replaced a valid nickname and once put a two-letter one in, which the
form refused as "Invalid address or name is too short!"; writing nothing left two
fresh installs both called `AZAHAR`, refused on 2026-10-01 in a session between two
AVDs. The profile name is padded to five characters with dots, as for Eden.

## Azahar's update prompt is dismissed, never accepted

Azahar asks about its own updates on opening, in a dialog that sits over every
screen the driver looks for: nothing moves until someone answers, and the session
looks frozen. The driver recognises it by Azahar's own `update_available` string, in
every language Azahar ships, and presses the dialog's negative button. It never
accepts: updating an emulator is the player's call, and a download mid-session would
lose the room.

A refused click is also looked at again rather than taken as final: a sheet still
rising refuses `ACTION_CLICK`, seen on a portrait phone where it travels further. Six
looks, three seconds, then the driver gives up as before.

## A recycling list does not contain what you have not seen yet

The settings hub's rows all carry the same ids, so the row is found by its text,
the emulator's own, read from its own resources, so it works whatever its
language.

Two things to survive, both seen on the Thor with Eden:

- the row may be scrolled below the bottom of the list, in which case it is in the
  tree but not visible, or not in the tree at all, a recycling list holding only
  what it has drawn. Hence one scroll per pass, counted as a click so it cannot
  loop;
- the label may be either of two strings, because the hub shows a title and a
  description, and only one of the two is what upstream calls "multiplayer" in a
  given build.

Failing either way was identical from outside: the emulator opened on its game
grid and nothing else happened.

## We bring the player home

They asked for a setup step, not a journey into another app: they tapped a button
in Emufii and the next thing they need is the button just below it. Leaving them
in the emulator's settings made them find their own way back before they could
start the game.

Delayed, because the emulator is still acting on the click we just made: returning
instantly would race its own "joined" message. Best-effort: if the platform
refuses the launch, the flow is over anyway and the player comes back by hand.

## `typeText` and not `setText`: a year of a green test proving nothing

`AccessibilityNodeInfo` already has a member named `setText`, and in Kotlin a
member always beats an extension. The extension was therefore never called: every
fill went to the platform setter, which throws `Cannot perform this action on a
sealed instance` on any node obtained from a query, which is to say all of them.

The automation had been failing on its very first field since it was written.
Nobody saw it because Azahar's in-game menu had never run on a device (M16), and
because the emulator pre-fills its own address, which, for a host, happens to be
the right answer. A green test that proved nothing.

---

# The PS2 driver (ARMSX2)

Taken out of `ps2/Ps2NetplayDriver.kt`.

## Two peculiarities that exist nowhere else

1. There is no text field at all. Opening a row brings up ARMSX2's own keyboard,
   and entry is done key by key. `ACTION_SET_TEXT` has nothing to aim at, and
   injected key events are ignored (measured).
2. That keyboard has no full stop key, so the guest cannot write an IPv4 address.
   They write a name, `emufii`, which the tunnel's DNS resolves to the relay's
   sentinel (`relay/dns.js`).

The screen is read by rows, label on the left, value on the right, and it is the
container that takes the click.

## The order of the settings is not cosmetic

Changing the mode redraws the bottom half of the screen, and the host's fields are
not the guest's: a value written before would be lost. One setting per pass, in the
order they depend on each other.

We go down the screen and never back up, hence a memory of the steps already set:
once past the DEV9 switch it is no longer in the tree, and without a memory the
driver would conclude it still had to set it. For the same reason, an absent label
does not mean "no switch" but "we have already passed it".

The current mode cannot be read off the button: measured, none of the three
carries `selected` or `checked` in the tree. It is inferred from the fields
present, as the Dolphin driver tells its tabs apart by the absence of the address
field.

One click, never two. The marker confirming the mode is lower than the button:
until we have scrolled it is absent from the tree, and the driver concluded it had
not clicked. Seen for real on 2026-08-17: eight clicks in a row on "Host local
game" before the screen moved enough to correct it.

The Network screen is recognised by any one of its markers, and not by the DEV9
switch alone: it is taller than the device, it has to be scrolled, and an
accessibility tree contains only what is actually drawn. Latching onto the first
label meant losing sight of the screen at the very first scroll.

## A screen mid-animation is an unknown screen

Just after a click, ARMSX2's tree drops to a handful of nodes while the next page
draws. Going back at that instant undoes the click we just made, and the driver
goes round in circles: menu, settings, back, menu, up to the cap. Seen for real on
2026-08-17, on a tree of 18 nodes.

So we only insist after several lost passes in a row, which leaves plenty of time
for a transition, and the counter drops to zero as soon as we recognise something.

## Two caps, two failures avoided

Entries are capped: a screen that does not read back the value just written to it
would make the driver start over endlessly. It happened: without that counter it
would have rewritten the room code until the player closed the app.

Scrolls too: if scrolling never brings up what we are looking for, we hand back
control saying what to set, rather than scrolling the screen indefinitely under
the player's thumb. It is the same trap as Azahar's OK button in landscape, in
another form: there you had to search without the visibility filter, here you have
to bring the row on screen.

The PS2 route is longer than the others, library, menu, settings, tab, so its
navigation cap is higher: four were enough for Dolphin, not here, and a cap set
too low reads as "setup does not work".

## Entry is done in one pass

Clear, type, confirm, with the tree re-read between keys. Splitting it into one
pass per character would have looked safer: it would have made it depend on one
accessibility event per key, while nothing guarantees ARMSX2 emits one for each.

The room code is cut to ARMSX2's bounds. The session code is already the two
players' shared secret, and it is alphanumeric and therefore typeable. Too short,
we do not invent one: better to leave ARMSX2's, identical on both sides only if
the players copy it to each other, than to set one the other will not have.

The system back gesture is used to leave a screen we cannot read: an already-open
ARMSX2 comes back to the foreground where the player left it, and there is no
other path from an unknown screen to the settings.

---

# The Dolphin driver

Taken out of `dolphin/DolphinNetplayDriver.kt` and `dolphin/DolphinScreen.kt`.

## Reading a Compose form with no id at all

Everything there is geometry and text. The reading is expressed on an in-house
node rather than on `AccessibilityNodeInfo`, because those rules are this
backend's whole risk and the platform type cannot be constructed in a unit test.

In-house bounds rather than `android.graphics.Rect`, for the same reason: a JVM
test gets the stubbed `android.jar`, where every `Rect` method silently returns
zero. The containment rule would have been false everywhere and the test would
have stayed green while proving nothing, a shape this project has already paid for
once, on the accessibility setter that never ran.

## Containment, not parentage, not position

A label belongs to the field whose bounds contain it. Compose's
`OutlinedTextField` draws its label inside the field's border: the "Port" caption
sits at the top left corner of the box containing "2626". The two are therefore
not siblings to be counted in order, they are nested in space.

That is deliberately the anchor. Pairing by position in the form would break the
day upstream adds a field, and upstream is still moving: three netplay PRs were
open the day this was written. Containment survives a reorganisation, an inserted
row and a screen rotation, and it is the same test whatever the label's language.

Parentage was the first rule, and it is false on the real tree. Recorded on the
Thor: the confirm button and its own caption come out as siblings, at the same
depth, a `Button` at `[1698,859][1883,988]` with no text, next to a `TextView`
"Host" at `[1756,900][1826,947]`. Walking up from the text therefore found no
button: the driver filled the whole form and then stopped one press short of
opening the room. The box always contains the caption, so containment answers
where parentage could not, and the ancestor case is kept in case a future build
nests them.

The tab and the button carry the same text. The button is wrapped in an
`android.widget.Button`, the tab is a bare row at the top of the screen. Clicking
the wrong one is not harmless: pressing "Host" while the Connect tab is showing
would start hosting when Emufii meant to join.

## The overflow button is found by its shape

The first attempt asked appcompat for its own description
(`abc_action_menu_overflow_description`). It resolves nowhere: not in Dolphin's
resources, not in ours, Emufii being all-Compose and not embedding appcompat.
Measured twice on the Thor; the driver sat on the game grid saying `desc=0`.

What the node does have is a shape nothing else in that bar shares. Dolphin's own
buttons all carry a resource id, from its menu resource; the overflow is added by
the framework and carries none, while staying clickable and describing itself for
screen readers. So: in the top band, the clickable node with no id but with a
description, furthest to the right. Independent of any language, which is the
point, and of the menu gaining or losing entries.

Likewise, the menu's Netplay row is found by its text, not by its id: the resource
names the entry `menu_netplay`, but appcompat renders each row's title in a view
carrying `id/title`, so the entry's id never reaches the accessibility tree and
the search found nothing, silently. Exactly like Azahar's settings cards.

## The order of the screens, and the two traps it avoids

The room first, before everything else, because it is the last screen: the form is
behind us and must not be touched again.

The room's game list is told apart from the startup grid by `lobbyClicks > 0`, and
that is not a detail: without it, the step fired on Dolphin's startup grid, which
has no more a text field than the room's list and shows the same titles. The
driver therefore launched the game on the very first pass instead of opening
netplay.

The connection type is set before any entry. Changing it rebuilds the form, the
port field appearing and disappearing with it, and a value written into a field
about to be recreated is lost. It is also a single setting shared by both tabs, so
set once for both roles.

Direct connection, never traversal: traversal would send the session through
Dolphin's STUN server, which this app exists precisely to make unnecessary, both
players already being on the same WireGuard network and the host answering at a
simple address. It would also remove the port field, which only exists in direct
mode: there would be nothing left to point anywhere.

And above all, no `Done` on submitting the form. A submitted form is no longer the
end of the road: the room opens behind it, and the game still has to be chosen
there. And `report(Done)` clears the plan, that being its whole purpose, so
declaring victory there would disarm the driver just before the screen it has left
to handle, and the game picker would stay on the device's last choice.

We never click "Start". Starting the game is the host's decision, not ours: the
guest may not be ready, and a game launched under the player's thumb is exactly
the kind of initiative this driver forbids itself everywhere else.

## Matching a game when the two sides do not name it the same

Strict equality cannot work: Emufii starts from the filename and cuts at the first
bracket, giving "Super Smash Bros. Brawl"; Dolphin reads the title stamped in the
disc header and shows "Smash Bros. Brawl". Neither is wrong, and neither can
change, the first being our library, the second being the disc.

The rule is therefore containment on normalised strings, both ways: the disc's
title is often shorter than ours, sometimes the other way round when our filename
is abbreviated. Punctuation is stripped because that is precisely where the two
diverge ("Smash Bros. Brawl" against "Smash Bros Brawl").

The longest wins, and a tie cancels everything. Two entries matching equally well
is a library containing "Mario Kart Wii" and "Mario Kart Wii (disc 2)": choosing
at random would launch the wrong game, which is worse than doing nothing. We
return `null`, and the player chooses themselves.

## The labels are resolved once

Resolving the labels costs some thirty string lookups: the same resource is
resolved in every language Dolphin might run in, because there is no way to ask
which one it actually uses. Six labels, re-read at each of the six re-reads per
screen, would make a thousand lookups to fill one form. They cannot change while
the emulator is running.

## The navigation cap is the moment to photograph

Below the bar where the driver takes the player to a screen they did not ask for,
everything is capped: an armed plan that never arrives must not reopen the
overflow menu under the player's thumb.

Reaching the cap is the moment this driver gives up silently: it clicked, the
screen did not change as expected, and it hands back control without telling the
player anything. That is exactly the instant to photograph; earlier, we would
capture the grid before the menu opened, which proves nothing.

## ARMSX2: two sibling `TextView`s, paired by their horizontal band

ARMSX2's Settings > Network screen looks like neither of the other two, measured
with `uiautomator` on the Thor.

On Dolphin the label is inside the field (Compose), so we search by nesting. Here,
label and value are two sibling `TextView`s on one line, label on the left, value
on the right, and neither is clickable: it is the row that is. No `EditText` is
visible until the row has been opened.

One measured example, in host mode:

```
"Local Link port"  TextView  [69,809][306,867]
"19072"            TextView  [1761,809][1851,867]
```

Hence the pairing rule: the horizontal band, not the node order. A screen that
gains a row, or that reorders itself, does not break that; counting nodes would
have broken at the first upstream addition.

`Node` and `Bounds` are borrowed from the Dolphin side rather than redeclared:
they are inert data, with nothing emulator-specific about them, and two copies
would drift.

## ARMSX2 has no editable field, and that is a wall

Measured on the Thor on 2026-08-17: touching a row opens no `EditText`. ARMSX2
draws its own keyboard, 42 keys, each a clickable view carrying its character in a
`TextView`. Recorded: 44 views, 42 labels, and not a single
`android.widget.EditText` in the whole tree.

Two consequences, and the second is a wall:

1. `ACTION_SET_TEXT` has nothing to aim at. Entry is done key by key, like a
   player's. `input text` over ADB does not get through either: that keyboard
   ignores injected key events, tried and confirmed.
2. The keyboard has no full stop. Digits, letters, shift, space, backspace,
   `Clear`, `Done`, and nothing else. Shift only changes case, and the field does
   not add the dots by itself: typing `10671` shows `10671`. An IPv4 address is
   therefore impossible to enter, by us as by the player. It is an upstream flaw,
   see `docs/PHASE1_SCOUT_PS2_ARMSX2.md`.

## ARMSX2 is launched by named component, never by filtering

Unlike Dolphin, it can be handed the ROM, but not the way you would imagine. Its
activity is exported with a `VIEW` filter on the `content` and `file` schemes,
with no MIME type at all, and that is the trap: for a `content://`, Android infers
the type from the provider, and a filter declaring none then matches nothing.

A SAF URI can therefore never be resolved by filtering. Measured on the Thor on
2026-08-17: the intent went out, `ActivityTaskManager` logged it, and no activity
started, even with ARMSX2 stopped beforehand.

Hence the explicitly named component: an intent that names its target does not go
through filtering. It is exactly what `AzaharLauncher` does with
`EmulationActivity`, and for the same reason.

Preparation must always be done before the game starts: the Network screen is in
the app's settings, not in a running game, and the DEV9 adapter initialises at
game start (`Local Link host ready on port 19072`). A port or a code set
afterwards would not be re-read.

## A build is chosen, it is no longer guessed

The case is ordinary, not exotic: Azahar installs under three `applicationId`s
depending on the channel, Eden comes in mainline / Optimized / legacy, each
doubled by a nightly, and nothing stops somebody having three at once. Until now
each launcher decided by itself, with two different heuristics: first in the list
for five of them, most recently updated for Eden.

Those heuristics stay, being the default, and the right one: with no explicit
choice, the most recently installed build is the one you meant to open. But they
stop being the only possible answer. Eden's comment had written it in black and
white: "the day this becomes a nuisance, what is needed is an explicit setting,
not a finer heuristic."

A choice does not survive the uninstallation of what it names. A preference
pointing at an absent package must never make a console unplayable: it is ignored,
the default takes over, and the choice is cleared on first reading so it does not
come back to life if the package returns one day unasked.

## Azahar has not finished changing its id

It comes from Lime3DS, which came from Citra, and the rename stopped halfway: its
classes are still `org.citra.citra_emu.*`, and some of its channels still publish
under Lime3DS's `applicationId`, `io.github.lime3ds.android`.

Observed on the Thor on 2026-08-26: the installed build (`263745c1d-vanilla`)
carries that name, does expose `btn_create`, `btn_join`, `ip_address`,
`btn_confirm` and `menu_multiplayer`, and does launch
`org.citra.citra_emu.activities.EmulationActivity`: it is Azahar in every respect
but the package name. Emufii only looked for `org.azahar_emu.*` and therefore
announced "not installed" in front of a perfectly drivable emulator.

Never replace one name with another: all three coexist depending on where the
install came from, and a player may have two. The order is one of preference, the
Azahar name first, the legacy one last.

What really decides whether a build is drivable is not its name but the probe
under the heading on asking the resources rather than the version number. Adding a
name here therefore risks nothing.

## Ask the resources, not the version number

Emufii drives netplay by filling the emulator's dialog through the accessibility
service. That only works if the dialog exists, and it is not always the case.
Azahar 2125.1.3-vanilla, an official release signed by the Lime3DS team and
shipped on the AYN Thor, carries the whole network engine in its native library,
`Network::RoomMember`, ENet, wifi packet handling, but none of the Android views
that reach it. Its 36,765 resources contain neither `menu_multiplayer`, nor
`btn_join`, nor `ip_address`; the only occurrence of the word "multiplayer" is the
description of an unrelated LLE setting.

Armed against such a build, the automation waits for a screen that will never
come, and the failure looks like Emufii doing nothing at all. Hence this probe,
run before arming.

It asks the emulator for its resources rather than comparing version numbers. A
version threshold would need a magic constant per distribution channel and would
be wrong for any fork; asking whether the view id resolves is the same question
the accessibility service will ask at runtime, so it cannot disagree with it.

## Eden: the most recently installed wins

Eden ships as a matrix of packages, mainline, "Optimized" under Genshin Impact's
identity, legacy, each doubled by a nightly, and nothing stops somebody having
three at once. A hard-coded order then always chose the same one, while the one
you have just installed is precisely the one you meant to use: on the Thor, last
week's stable beat the Optimized installed moments earlier, and Emufii opened the
emulator the player had not chosen.

`lastUpdateTime` rather than `firstInstallTime`: reinstalling or updating a
variant is as deliberate a gesture as installing it the first time. The drawback
is accepted: updating a forgotten variant can take over without saying so. That is
what led to the explicit setting, under the heading on a build being chosen rather
than guessed.

On equal dates, the order of `NetplayTarget.EDEN.packages` decides, which keeps
our fork in front, it being the only one that lets the network interface be
chosen.

## Dolphin gets no ROM, and it does not mind

Dolphin cannot be told to start a specific file from outside: its
`AppLinkActivity` takes a filesystem path through `AutoStartFile`, and a SAF
`content:` URI is exactly what a path cannot be. Emufii has known that since the
tapserver days.

Netplay makes the point moot: the game is not chosen at launch, it is chosen in
the room, by the host, in Dolphin's library, and each client is told which one it
is. The journey Emufii needs is therefore the one Dolphin already offers: open the
app, land in the room, choose the game there. What the other backends do in two
steps, this one does in one, and the ROM the session carries only ever serves to
name the game on our own screens.

The consequence to keep in mind: both players must already have that game in
Dolphin, with identical content. Netplay checks it by a hash and says so out loud
when they differ.

## Total silence is not a failure

A driver that ran and gave up reports a failure and says what to type instead. The
case here is silence, and it has a cause worth naming: the accessibility service
is still listed and still bound, but receives not a single event any more, which
is what reinstalling the app over itself leaves behind.

Measured on the Thor on 2026-08-23 after an `install -r`: every launch opened the
emulator and did nothing, with no error, no progress and no log line, and turning
the service off and on again in Android's settings brought it straight back.
Players hit the same reinstall through the update channel.

The question is asked when the player comes back into Emufii: it is the only
moment we know both that the emulator has had its turn and that we are alive to
say so. The silence threshold is what separates "never started" from "still
opening the menu", so a player who comes straight back is not told something is
wrong.
