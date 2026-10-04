# Friends, presence, and what we dare announce

Taken out of the code on 2026-08-29 (see `docs/STYLE_COMMENTAIRES.md`). The
headings are anchors cited from the code.

## What background watching can promise, and what it cannot

Emufii installs outside any store and has no push service behind it: nothing on
a server can wake this app. The only honest mechanism left is to ask, now and
then, from the device itself.

Android's floor for periodic work is fifteen minutes, and Doze stretches it
further on a phone in a pocket. An alert about a friend can therefore arrive a
quarter of an hour after they did, sometimes more, and a friend who plays for
ten minutes may never be announced at all.

That is a real limit, and it is written into the settings copy rather than
hidden. A feature that quietly delivers less than it promised teaches people to
distrust every notification the app will ever send. What it does deliver
reliably is slow news: a new version, and a friend settling in for an evening.

`JobScheduler` rather than WorkManager: WorkManager would bring a dependency, a
database and a hundred kilobytes for a periodic task with no chaining, no
constraint beyond the network, and no result to observe. The platform scheduler
does exactly this job.

## The announcement rules, each earned by picturing the notification it avoids

Comparing two polls is a pure function, and that is the point: the same function
serves the in-app alert and the background job, so what the two announce cannot
drift apart. It is also the only part of this feature that can be tested without
a device.

- A friend never seen before produces nothing. The first poll after adding one,
  or after the app was killed for a day, would otherwise announce the whole list
  at once as though everybody had just arrived.
- Coming online is announced once. If they are already in a game at that moment,
  the game is what gets announced, not both.
- Starting a game is announced even for someone already online. That is the case
  that really counts: they are there, and now there is something to join.
- A friend already in that same game produces nothing, however many polls go by.

## The page is a row and a card

Rebuilt on 2026-10-04, after a grid of sheets was judged a step back to the app's first
versions. The model is the 3DS friend list: a row of portraits to scroll through, and the
one under the cursor told large on a card.

- You are not in the row. Your code and the way to add someone live in the header (a
  coral pill that copies the code on press, and a `+` chip): putting yourself among your
  friends was judged pointless.
- The row does not scroll like a list. The selected face stays in the middle and the row
  slides under it on `Motion.cursor()`, the selected one rising like a library tile. A drag
  follows the finger and settles on the nearest face.
- The card is one plate: the photo as a large squircle on the left, name, status and code in
  the middle, the game on the right. A coral panel tilted behind it was tried the same day
  and judged to spill past the plate; a settle on the plate drew its shadow in a square
  layer. Both are gone. Changing friend slides the inside the way the row went, clipped to
  the plate's corners.
- The card keeps one height whatever the friend has to show: without a game, the picture
  frame stays, with "Nothing yet". Losing the frame made the page jump between two friends.
- The selection is a friend code held in `SecondScreen.friendsFocus`, process-wide: the
  panel's row and this page move the same cursor, and the card stays on the person when the
  order changes.
- Two screens: the front stays the same page, row and card, because the panel cannot take
  the cursor (a row on the back was empty and unreachable by pad). The back is a showcase of
  the selected friend: their game full-bleed, their photo and name laid on it.
- Pad: down from the header lands on the card's first button; down again is the row, and up
  from the row comes back to the card.
- A friend's picture is cached under its path and date: two pictures fetched in the same
  second shared a Coil key, and one friend wore another's face.

## The friend card shows the last game

Every route that starts a game (session, Kaeru WFC, PSP online) tells the coordinator, on
`/me` as `last_game: { title, title_id }`. `title_id` is the game's first compatibility key,
the one `meta.json` is indexed on, so a friend who does not own the game still gets its
picture: the first screenshot, cropped on the top screen (DS and 3DS snaps stack both).
In memory on the coordinator, like presence. A switch on the profile page ("Show my last game
to my friends") sends `null`, and the coordinator forgets it at once.

## Profile pictures travel, under the owner's key

Until 2026-10-04 a profile picture never left the phone. Now it goes up when it changes:
the app crops the centre square, scales to 256 px and encodes WebP under 40 KB.

- Ownership is trust on first use. Each install draws a 32-byte key (`AvatarSync`); the
  coordinator keeps its hash at the first upload, and only that key replaces or removes the
  picture after. Knowing a code shows the picture, as it shows the name; it does not let
  anyone change it. Removing a picture keeps the ownership recorded.
- The coordinator does not decode images. It checks the size (48 KB) and the WebP signature,
  serves the bytes as `image/webp` with `nosniff`, and paces uploads (20 an hour). The app
  reads a friend's picture's bounds before decoding it and refuses past 1024 px.
- Stored on disk (`AVATAR_DIR`, `avatars/` next to the code by default), unlike presence: a
  restart must not strip every face.
- No moderation: a picture is seen only by people holding your code, and you can remove it.
- Deletion goes through `POST /avatar/delete`: Android's `HttpURLConnection` will not send a
  body with DELETE, and the key travels in the body.
