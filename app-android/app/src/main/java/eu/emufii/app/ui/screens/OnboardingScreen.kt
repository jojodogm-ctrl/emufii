package eu.emufii.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.emufii.app.ui.controlRing
import eu.emufii.app.R
import eu.emufii.app.profile.Profile
import eu.emufii.app.settings.SettingsStore
import eu.emufii.app.azahar.AzaharLauncher
import eu.emufii.app.ui.components.PadTextField
import eu.emufii.app.ui.components.SoftCard
import eu.emufii.app.ui.components.ConsoleGrid
import eu.emufii.app.ui.components.SteamGridDbMark
import eu.emufii.app.ui.theme.PillShape
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.wallpaper.TrayBackdrop
import kotlinx.coroutines.delay

/**
 * First run: a welcome, then the two things the app genuinely cannot do without,
 * then out of the way.
 *
 * Both steps are skippable on purpose. The folder can be picked later from
 * settings and the notification is a permission the user is entitled to refuse,
 * an onboarding that traps someone until they say yes is a dark pattern, and this
 * one has to survive being said no to.
 */
@Composable
fun OnboardingScreen(
    initialName: String,
    onSetName: (String) -> Unit,
    onPickFolder: (Uri) -> Unit,
    onSetArtworkKey: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = LocalEmufiiDarkTheme.current
    val context = LocalContext.current
    var step by remember { mutableStateOf(0) }
    var folderPicked by remember { mutableStateOf(false) }
    val settingsStore = remember { SettingsStore.get(context) }
    val hiddenConsoles by settingsStore.hiddenConsoles.collectAsState()
    var name by remember { mutableStateOf(initialName) }
    var artworkKey by remember { mutableStateOf("") }
    val nameTooShort = name.trim().length < Profile.MIN_NAME_LENGTH

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            onPickFolder(uri)
            folderPicked = true
        }
    }

    // Seeded from the real permission, not from "has the user pressed it yet".
    // Installing with `adb install -g`, or simply having allowed it on a past
    // install, grants it before this screen is ever seen, and the step then
    // offered a button that did nothing at all when pressed, because Android
    // returns "already granted" without showing anything.
    var notificationsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var notificationsRefused by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // A refusal used to show the same ✓ as a grant, which was a lie, and it
        // matters here, because after two refusals Android stops showing the
        // dialog at all and the button would silently do nothing for ever.
        notificationsGranted = granted
        notificationsRefused = !granted
    }

    // Not a permission dialog but a trip to Android's settings, so there is no
    // result to wait on, the answer is only visible on the way back. Polled
    // while this step is on screen rather than pulling in a lifecycle observer
    // for one boolean.
    val launcher = remember { AzaharLauncher(context) }
    var autofillEnabled by remember { mutableStateOf(launcher.isNetplayAutomationEnabled()) }
    LaunchedEffect(step) {
        if (step == STEP_AUTOFILL) {
            while (true) {
                autofillEnabled = launcher.isNetplayAutomationEnabled()
                delay(700)
            }
        }
    }

    // What weight alone cannot save: the fixed elements themselves. Over 468 dp
    // of height, the dots, the button, the "Later" link, their three gaps and the
    // margins consumed more than the room available, and it was the link at the
    // bottom that got clipped. The panel could scroll all it liked, there was
    // nothing left to take from it. So the fixed parts tighten up too, on the same
    // threshold as the launch card.
    val configuration = LocalConfiguration.current
    val shortScreen = configuration.screenHeightDp < 520
    val gap = if (shortScreen) 12.dp else 20.dp
    val edge = if (shortScreen) 12.dp else 20.dp
    val actionHeight = if (shortScreen) 48.dp else 56.dp

    Box(modifier = modifier.fillMaxSize()) {
        TrayBackdrop(modifier = Modifier.fillMaxSize(), dark = dark)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = edge),
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StepDots(current = step, total = STEPS)

            // The panel scrolls, the button stays.
            //
            // This column filled the screen without scrolling, and centred its
            // content. In landscape on the Thor, 468 dp tall, the SteamGridDB step
            // is the largest of them all (wordmark, title, text, field, then the
            // address where the key is obtained): it pushed the "Next" button off
            // the screen, where it was simply clipped. Nothing said so, and with no
            // button the onboarding has no exit.
            //
            // Weight gives the panel the room left once the button has been
            // served, never the other way round. It is the same lesson as the
            // message rendered at the bottom of a scrolling column, in 1.10.3: on
            // this screen, what overflows cannot be seen overflowing.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn())
                        .togetherWith(
                            slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut()
                        )
                },
                label = "onboarding-step"
            ) { current ->
                when (current) {
                    0 -> Panel(
                        title = stringResource(R.string.onb_welcome_title),
                        body = stringResource(R.string.onb_welcome_body)
                    )

                    STEP_NAME -> NamePanel(
                        name = name,
                        onNameChange = { name = it.take(Profile.MAX_NAME_LENGTH) },
                        tooShort = nameTooShort
                    )

                    2 -> Panel(
                        title = stringResource(R.string.onb_folder_title),
                        body = stringResource(R.string.onb_folder_body),
                        confirmation = if (folderPicked) stringResource(R.string.onb_folder_done) else null,
                        action = if (folderPicked) null else stringResource(R.string.lib_choose_folder),
                        onAction = { folderPicker.launch(null) }
                    )

                    STEP_CONSOLES -> ListPanel(
                        title = stringResource(R.string.consoles_pick_title),
                        body = stringResource(R.string.consoles_pick_body),
                        note = stringResource(R.string.consoles_pick_note)
                    ) {
                        ConsoleGrid(
                            hidden = hiddenConsoles,
                            onSetVisible = { console, visible ->
                                settingsStore.setConsoleVisible(console, visible)
                            }
                        )
                    }

                    STEP_ARTWORK -> ArtworkPanel(
                        key = artworkKey,
                        onKeyChange = { artworkKey = it }
                    )

                    STEP_NOTIF -> Panel(
                        title = stringResource(R.string.onb_notif_title),
                        body = stringResource(R.string.onb_notif_body),
                        confirmation =
                            if (notificationsGranted) stringResource(R.string.onb_notif_done) else null,
                        note =
                            if (notificationsRefused) stringResource(R.string.onb_notif_refused) else null,
                        action =
                            if (notificationsGranted || notificationsRefused) null
                            else stringResource(R.string.onb_notif_enable),
                        onAction = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    )

                    else -> Panel(
                        title = stringResource(R.string.onb_fill_title),
                        body = stringResource(R.string.onb_fill_body),
                        confirmation =
                            if (autofillEnabled) stringResource(R.string.onb_fill_done) else null,
                        action =
                            if (autofillEnabled) null else stringResource(R.string.onb_fill_enable),
                        onAction = { launcher.openAccessibilitySettings() }
                    )
                }
            }
            }

            // Both actions on one line, not stacked.
            //
            // Stacked, they cost the panel some 50 dp of height plus a gap, on a
            // screen 468 dp tall where the consoles page has to fit seven tiles
            // and a line of text without scrolling. Side by side they cost one
            // row, and "Skip" sitting next to "Next" also reads better than
            // hanging under it: they are two ways out of the same step.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Button(
                onClick = {
                    if (step == STEP_NAME) onSetName(name.trim())
                    if (step == STEP_ARTWORK) onSetArtworkKey(artworkKey.trim())
                    if (step < STEPS - 1) step++ else onDone()
                },
                // The one step that can't be waved through: the pseudo goes
                // straight into the emulator's netplay form, which rejects a
                // short one, and a name refused there surfaces as a connection
                // that just won't happen. It starts pre-filled with a valid
                // default, so this only blocks someone who has actively emptied
                // it, not a wall in front of a first run.
                enabled = step != STEP_NAME || !nameTooShort,
                shape = PillShape,
                modifier = Modifier.weight(1f).height(actionHeight).controlRing(PillShape)
            ) {
                Text(
                    stringResource(
                        when (step) {
                            0 -> R.string.onb_start
                            STEPS - 1 -> R.string.onb_finish
                            else -> R.string.onb_next
                        }
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Only offered on the steps that ask for something, so the welcome
            // screen doesn't invite you to skip a page that asks nothing.
            // Not offered on the name step: skipping it would mean going on with
            // a pseudo the emulator won't take, which is the failure this step
            // exists to prevent. Keeping the default is already the one-tap way
            // through.
            if (step in 1 until STEPS && step != STEP_NAME) {
                // The ring, because this link takes focus like anything else.
                //
                // It did not have one, so pressing down from the main button
                // moved the cursor onto a control that showed nothing: the
                // screen looked frozen with no way to tell that a press would
                // now skip the step. Everything that takes focus has to show it,
                // which is the rule the rest of the app already follows.
                TextButton(
                    onClick = { if (step < STEPS - 1) step++ else onDone() },
                    modifier = Modifier.height(actionHeight).controlRing(PillShape)
                ) {
                    Text(stringResource(R.string.onb_skip))
                }
            }
            }
        }
    }
}

private const val STEPS = 7

/** Asked first, right after the welcome: everything social hangs off it. */
private const val STEP_NAME = 1

/**
 * The consoles, with the emulators that play them, on one page.
 *
 * Two pages until 2026-08-19: an inventory of emulators to read, then a list of
 * switches. Both were about the same seven machines, and the second answered a
 * question the first had just raised, so they are one grid of tiles now. What is
 * left is a single screen that fits without scrolling.
 *
 * Placed after the ROM folder rather than before it, because the answer only
 * means something once there are games to hide: asked first, it would be seven
 * switches about consoles the player may not own a single dump for.
 */
private const val STEP_CONSOLES = 3

/**
 * Offered right after the consoles: we have just been talking about the
 * library, and that is the moment "what if it looked good" makes sense.
 * Skippable like the others; an app that demands a third-party account before it
 * will open does not deserve to be installed.
 */
private const val STEP_ARTWORK = 4

private const val STEP_NOTIF = 5

/** The last step, and the only one whose answer arrives from another app. */
private const val STEP_AUTOFILL = 6

@Composable
private fun Panel(
    title: String,
    body: String,
    confirmation: String? = null,
    /** A plain outcome, for the ones a ✓ would misrepresent. */
    note: String? = null,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    SoftCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (confirmation != null) {
                Text(
                    "✓ $confirmation",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else if (action != null) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onAction,
                    shape = PillShape,
                    modifier = Modifier.controlRing(PillShape)
                ) { Text(action) }
            }
        }
    }
}

/**
 * A [Panel] whose content is a list rather than a sentence.
 *
 * It does *not* scroll, and that is the whole point of this note. The first
 * version gave it a `verticalScroll` of its own, reasoning that seven emulator
 * rows are taller than a landscape handheld. They are, but the panel already
 * sits inside a `Box` that scrolls and carries the weight, with the button
 * pinned below it: adding a second scroll nests two scrolling containers, so the
 * outer one measures this card at infinite height and Compose refuses, by
 * throwing, the moment the step is drawn.
 *
 * It crashed on the Thor at the page after the ROM folder. `SessionScreen`
 * already carries the same warning about its presence card, in almost these
 * words, which is where this should have been read before it was written.
 *
 * So the card simply grows, and the scroll that was always there takes care of
 * it.
 */
@Composable
private fun ListPanel(
    title: String,
    body: String,
    /** A line under the content, for what qualifies it rather than introduces it. */
    note: String? = null,
    content: @Composable () -> Unit
) {
    SoftCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            content()
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * The pseudo step. A [Panel] with a field instead of a button, kept as its own
 * composable rather than another optional parameter on [Panel], which already
 * carries four.
 */
@Composable
private fun NamePanel(
    name: String,
    onNameChange: (String) -> Unit,
    tooShort: Boolean
) {
    SoftCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.onb_name_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.onb_name_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            PadTextField(
                value = name,
                onValueChange = onNameChange,
                isError = tooShort,
                shape = PillShape,
                supportingText = {
                    if (tooShort) {
                        Text(
                            stringResource(
                                R.string.onb_name_too_short,
                                Profile.MIN_NAME_LENGTH
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * The SteamGridDB key step.
 *
 * The field starts empty and staying empty is a valid answer: the library then
 * keeps the ROMs' icons. It is the only onboarding screen that mentions a
 * third-party service, hence the address written out in full; an onboarding that
 * says "go and get a key" without saying where is an onboarding people skip.
 */
@Composable
private fun ArtworkPanel(key: String, onKeyChange: (String) -> Unit) {
    SoftCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SteamGridDbMark()
            Text(
                stringResource(R.string.onb_artwork_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.onb_artwork_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            PadTextField(
                value = key,
                onValueChange = onKeyChange,
                shape = PillShape,
                label = stringResource(R.string.settings_artwork_field),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                stringResource(R.string.onb_artwork_where),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Where you are in the walkthrough, without a number to read. */
@Composable
private fun StepDots(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { index ->
            val active = index == current
            Box(
                Modifier
                    .height(8.dp)
                    .width(if (active) 22.dp else 8.dp)
                    .clip(if (active) PillShape else CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                    )
            )
        }
    }
}
