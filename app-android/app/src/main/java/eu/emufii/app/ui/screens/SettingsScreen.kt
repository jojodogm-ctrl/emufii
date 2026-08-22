package eu.emufii.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.positionInRoot
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.plateColors
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.emufii.app.library.HiddenRoms
import eu.emufii.app.BuildConfig
import eu.emufii.app.R
import eu.emufii.app.azahar.AzaharLauncher
import eu.emufii.app.library.ConsoleKeysStore
import eu.emufii.app.library.Console
import eu.emufii.app.profile.FriendStore
import eu.emufii.app.profile.Profile
import eu.emufii.app.profile.ProfileStore
import eu.emufii.app.profile.playerDisplayName
import eu.emufii.app.settings.AppLanguage
import eu.emufii.app.settings.AppAccent
import eu.emufii.app.settings.AppTheme
import eu.emufii.app.settings.SettingsStore
import eu.emufii.app.ui.controlRing
import eu.emufii.app.ui.theme.AccentCuts
import eu.emufii.app.ui.theme.CardCorner
import eu.emufii.app.ui.theme.accentCuts
import eu.emufii.app.ui.components.ConsoleGrid
import eu.emufii.app.ps2.Ps2NetworkProfile
import eu.emufii.app.ui.theme.CardShape
import eu.emufii.app.ui.components.Avatar
import eu.emufii.app.ui.components.EmufiiScaffold
import eu.emufii.app.ui.components.GhostButton
import eu.emufii.app.ui.components.PadTextField
import eu.emufii.app.ui.components.SectionHeader
import eu.emufii.app.ui.components.labelRes
import eu.emufii.app.ui.components.ThemeDialog
import eu.emufii.app.ui.components.PadDialogText
import eu.emufii.app.ui.components.PadDialog
import eu.emufii.app.ui.components.ChevronRight
import eu.emufii.app.ui.components.SoftCard
import eu.emufii.app.ui.components.padEntry
import eu.emufii.app.ui.components.SteamGridDbMark
import eu.emufii.app.wg.WgKeys
import eu.emufii.app.ui.theme.ShellRed

/**
 * The settings, as four sections of rows that unfold.
 *
 * This screen was called "Profile" and carried eight cards of equal weight: the
 * nickname, the ROM folder, the console keys, the artwork key, the language, the
 * theme, the about box and the reset. Each laid out a heading, a paragraph of
 * explanation and its buttons, all the time, whether you had come to see it or
 * not. On the Thor the library card took up a whole screen for three lines, the
 * two columns ended up staggered, and finding a setting meant searching a wall
 * of text.
 *
 * Here every setting is a row: what it is on the left, where it stands on the
 * right. The explanatory text and the buttons only exist once the row is open,
 * that is, at the precise moment they were asked for. The value shown on the
 * right is what replaces the paragraph: "Set", "ROMS", "French" already answer
 * the question you came to ask.
 *
 * One row open at a time. Two unfolded sections rebuild exactly the screen we
 * just took apart, and on a handheld the page would go back to scrolling at the
 * first setting touched.
 *
 * The name is saved as it is typed rather than behind a button: there is
 * nothing to validate or send, storage is local, and a button that only means
 * "yes, really" is a button nobody needs.
 */
@Composable
fun SettingsScreen(
    profile: Profile,
    profileStore: ProfileStore,
    friendStore: FriendStore,
    settingsStore: SettingsStore,
    libraryFolder: String?,
    libraryScanning: Boolean,
    libraryCount: Int?,
    onFolderPicked: (Uri) -> Unit,
    onRescan: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember(profile.id) {
        mutableStateOf(profile.name.takeIf { profile.isNamed } ?: "")
    }
    var photoError by remember { mutableStateOf<String?>(null) }
    var confirmingReset by remember { mutableStateOf(false) }

    /** The unfolded row, if there is one. */
    var open by remember { mutableStateOf<SettingsRowId?>(null) }

    val language by settingsStore.language.collectAsState()
    val theme by settingsStore.theme.collectAsState()
    val accent by settingsStore.accent.collectAsState()
    var themePanel by remember { mutableStateOf(false) }
    val artworkKey by settingsStore.steamGridDbKey.collectAsState()
    val context = LocalContext.current
    // Le joueur l'a-t-il importee dans ARMSX2 ? Rien ici ne peut le verifier,
    // et c'est ce drapeau qui autorise une session PS2.
    var ps2ProfileReady by remember { mutableStateOf(Ps2NetworkProfile.isReady(context)) }
    var hiddenCount by remember { mutableStateOf(HiddenRoms(context).count()) }

    // Whether Emufii's accessibility service is on, re-read while this screen is
    // up rather than once.
    //
    // Leaving for Android's settings is a trip out of the app, not a dialog with
    // a result: the answer only exists on the way back. Polled like the
    // onboarding step does, which is cheaper than pulling in a lifecycle observer
    // for one boolean, and it is what makes the row turn green under the
    // player's eyes when they return.
    val hiddenConsoles by settingsStore.hiddenConsoles.collectAsState()
    val autofillLauncher = remember { AzaharLauncher(context) }
    var autofillOn by remember { mutableStateOf(autofillLauncher.isNetplayAutomationEnabled()) }
    LaunchedEffect(Unit) {
        while (true) {
            autofillOn = autofillLauncher.isNetplayAutomationEnabled()
            delay(700)
        }
    }


    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            photoError = profileStore.setAvatar(uri).exceptionOrNull()?.message
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> if (uri != null) onFolderPicked(uri) }

    // Console keys. A Switch dump says nothing about itself without them, and
    // until now they were only found if they happened to sit in the ROM folder
    // - true for some players, a silent dead end for the rest.
    val keysStore = remember { ConsoleKeysStore(context) }
    var hasKeys by remember { mutableStateOf(keysStore.hasKeys) }
    var keysRejected by remember { mutableStateOf(false) }
    val keysPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val ok = keysStore.import(uri)
            hasKeys = keysStore.hasKeys
            keysRejected = !ok
            // Keys change what a scan can read, so the library is worth walking
            // again, otherwise the tiles stay blank until something else
            // triggers a rescan and the setting looks inert.
            if (ok) onRescan()
        }
    }

    // An unfolded row is a sub-level: B closes it before leaving the screen.
    // Without that, opening "Console keys" then wanting to go back returned to
    // the library, skipping the step just opened, and back did not undo the
    // last gesture.
    BackHandler(enabled = open != null) { open = null }

    EmufiiScaffold(
        // "Settings" and not "Profile": the profile is the first of the four
        // sections, not the subject of the page. The title announced an eighth
        // of the content.
        title = stringResource(R.string.settings_title),
        onBack = {
            profileStore.setName(name)
            onBack()
        },
        modifier = modifier
    ) { topPadding ->
        val toggle = { id: SettingsRowId -> open = if (open == id) null else id }

        val you = @Composable {
            SettingsSection(stringResource(R.string.settings_sec_you)) {
                SettingsRow(
                    label = stringResource(R.string.settings_row_identity),
                    value = playerDisplayName(name.ifBlank { Profile.DEFAULT_NAME }),
                    expanded = open == SettingsRowId.IDENTITY,
                    onToggle = { toggle(SettingsRowId.IDENTITY) },
                    divider = false,
                    last = true,
                    // First control on the page: the gamepad comes down to it
                    // from the header, and goes back up to it.
                    entry = true,
                    // The avatar serves as the value as much as the nickname
                    // does: it is the only row whose state is a picture.
                    leading = {
                        Avatar(
                            name = playerDisplayName(name.ifBlank { Profile.DEFAULT_NAME }),
                            imageFile = profile.avatarFile,
                            size = 34.dp
                        )
                    }
                ) {
                    IdentityDetail(
                        profile = profile,
                        name = name,
                        onNameChange = { name = it },
                        onPickPhoto = {
                            picker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        onClearPhoto = { profileStore.clearAvatar() },
                        photoError = photoError
                    )
                }
            }
        }

        val library = @Composable {
            SettingsSection(stringResource(R.string.settings_sec_library)) {
                SettingsRow(
                    label = stringResource(R.string.settings_row_folder),
                    value = libraryFolder ?: stringResource(R.string.settings_value_none),
                    expanded = open == SettingsRowId.FOLDER,
                    onToggle = { toggle(SettingsRowId.FOLDER) },
                    divider = false
                ) {
                    FolderDetail(
                        folder = libraryFolder,
                        scanning = libraryScanning,
                        count = libraryCount,
                        onPickFolder = { folderPicker.launch(null) },
                        onRescan = onRescan
                    )
                }
                // One row, not two. There was an "Emulators" row above this
                // one, an inventory to read, and it said exactly what the tiles
                // below now say: same seven machines, same icons, same versions.
                // The onboarding merged its own pair on the same day and for the
                // same reason.
                SettingsRow(
                    label = stringResource(R.string.settings_row_consoles),
                    value =
                        if (hiddenConsoles.isEmpty()) stringResource(R.string.settings_value_consoles_all)
                        else stringResource(
                            R.string.settings_value_consoles_some,
                            Console.entries.size - hiddenConsoles.size,
                            Console.entries.size
                        ),
                    expanded = open == SettingsRowId.CONSOLES,
                    onToggle = { toggle(SettingsRowId.CONSOLES) }
                ) {
                    Text(
                        stringResource(R.string.consoles_pick_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ConsoleGrid(
                        hidden = hiddenConsoles,
                        onSetVisible = { console, visible ->
                            settingsStore.setConsoleVisible(console, visible)
                        }
                    )
                    Text(
                        stringResource(R.string.consoles_pick_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SettingsRow(
                    label = stringResource(R.string.settings_row_keys),
                    value = stringResource(
                        if (hasKeys) R.string.settings_value_keys_ok
                        else R.string.settings_value_keys_none
                    ),
                    expanded = open == SettingsRowId.KEYS,
                    onToggle = { toggle(SettingsRowId.KEYS) }
                ) {
                    KeysDetail(
                        hasKeys = hasKeys,
                        rejected = keysRejected,
                        onPick = {
                            keysRejected = false
                            // Key files have no registered type; anything else
                            // hides them.
                            keysPicker.launch(arrayOf("*/*"))
                        },
                        onForget = {
                            keysStore.clear()
                            hasKeys = false
                            keysRejected = false
                        }
                    )
                }
                SettingsRow(
                    label = stringResource(R.string.settings_row_artwork),
                    value = stringResource(
                        if (artworkKey.isNotBlank()) R.string.settings_value_artwork_on
                        else R.string.settings_value_artwork_off
                    ),
                    expanded = open == SettingsRowId.ARTWORK,
                    onToggle = { toggle(SettingsRowId.ARTWORK) }
                ) {
                    ArtworkDetail(
                        key = artworkKey,
                        onKeyChange = { settingsStore.setSteamGridDbKey(it) }
                    )
                }
                // Last of the library rows: it is about what the grid shows, so
                // it belongs beside the folder that fills it rather than next to
                // the app's own settings.
                SettingsRow(
                    label = stringResource(R.string.settings_row_hidden),
                    value =
                        if (hiddenCount == 0) stringResource(R.string.settings_value_hidden_none)
                        else stringResource(R.string.settings_value_hidden_some, hiddenCount),
                    expanded = open == SettingsRowId.HIDDEN,
                    onToggle = { toggle(SettingsRowId.HIDDEN) },
                    last = true
                ) {
                    HiddenRomsDetail(
                        count = hiddenCount,
                        onRestore = {
                            HiddenRoms(context).clear()
                            hiddenCount = 0
                        }
                    )
                }
            }
        }

        val app = @Composable {
            SettingsSection(stringResource(R.string.settings_sec_app)) {
                SettingsRow(
                    label = stringResource(R.string.settings_language),
                    value = stringResource(language.labelRes),
                    expanded = open == SettingsRowId.LANGUAGE,
                    onToggle = { toggle(SettingsRowId.LANGUAGE) },
                    divider = false
                ) {
                    AppLanguage.entries.forEach { option ->
                        ChoiceRow(
                            label = stringResource(option.labelRes),
                            selected = option == language,
                            onClick = { settingsStore.setLanguage(option) }
                        )
                    }
                }
                // Not an unfolding row: this one opens the panel.
                //
                // The look of the app is not a detail of a settings line. It was
                // nine labelled choices stacked inside the card, pushing
                // everything below them off the screen, and asking the reader to
                // picture what each name looked like. Its value still names both
                // halves of the choice, so the row says where things stand
                // without being opened.
                SettingsRow(
                    label = stringResource(R.string.settings_theme),
                    value = stringResource(theme.labelRes) + " · " +
                        stringResource(accent.labelRes),
                    expanded = false,
                    onToggle = { themePanel = true }
                )
                SettingsRow(
                    label = stringResource(R.string.settings_row_autofill),
                    value = stringResource(
                        if (autofillOn) R.string.settings_value_autofill_on
                        else R.string.settings_value_autofill_off
                    ),
                    expanded = open == SettingsRowId.AUTOFILL,
                    onToggle = { toggle(SettingsRowId.AUTOFILL) }
                ) {
                    AutofillDetail(
                        enabled = autofillOn,
                        onOpen = { autofillLauncher.openAccessibilitySettings() }
                    )
                }
                SettingsRow(
                    label = stringResource(R.string.settings_row_ps2_profile),
                    value = stringResource(
                        if (ps2ProfileReady) R.string.settings_value_ps2_profile_on
                        else R.string.settings_value_ps2_profile_off
                    ),
                    expanded = open == SettingsRowId.PS2_PROFILE,
                    onToggle = { toggle(SettingsRowId.PS2_PROFILE) }
                ) {
                    Ps2ProfileDetail(
                        ready = ps2ProfileReady,
                        onReadyChange = {
                            Ps2NetworkProfile.setReady(context, it)
                            ps2ProfileReady = it
                        }
                    )
                }
                SettingsRow(
                    label = stringResource(R.string.settings_row_about),
                    value = BuildConfig.VERSION_NAME,
                    expanded = open == SettingsRowId.ABOUT,
                    onToggle = { toggle(SettingsRowId.ABOUT) },
                    last = true
                ) {
                    Text(
                        stringResource(R.string.settings_about_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        val danger = @Composable {
            SettingsSection(stringResource(R.string.settings_sec_danger)) {
                // A row, like the others, but with no chevron and no unfolding:
                // there is nothing to reveal, and the confirmation that follows
                // already carries the warning. The old card laid out two
                // sentences and a button permanently for a gesture you make once
                // in the life of the app.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        // Alone in its card: it takes the card's whole shape,
                        // which is also what made it show the glow inside.
                        .controlRing(CardShape)
                        .cardSliceFill(CardShape)
                        .clickable { confirmingReset = true }
                        .padding(horizontal = ROW_TEXT_INSET, vertical = 12.dp)
                ) {
                    Text(
                        stringResource(R.string.profile_reset),
                        style = MaterialTheme.typography.bodyLarge,
                        color = DANGER
                    )
                    Spacer(Modifier.weight(1f))
                    ChevronRight(size = 18.dp, color = DANGER.copy(alpha = 0.6f))
                }
            }
        }

        /**
         * A single column, centred and bounded in width.
         *
         * Two columns looked like the right answer to a wide screen, and they
         * are not: four sections of different lengths never split evenly, so one
         * ends up shorter than the other and leaves a three-hundred-pixel hole
         * that nothing fills. The problem is not the pairing, it is structural,
         * and a row that unfolds changes height, which reopens the hole on every
         * opening even when the balance was right at rest.
         *
         * Bounded, because a single column stretched over 1920 px puts the label
         * and its value at opposite edges of the screen, and the pair stops
         * being readable. Centred, because a bounded block pinned left would
         * leave the very emptiness we just removed, merely moved.
         */
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = topPadding, bottom = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = SETTINGS_MAX_WIDTH).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                you()
                library()
                app()
                danger()
            }
        }
    }

    if (confirmingReset) {
        val done = stringResource(R.string.profile_reset_done)
        PadDialog(
            title = stringResource(R.string.profile_reset),
            onDismiss = { confirmingReset = false },
            actions = {
                GhostButton(
                    label = stringResource(R.string.friends_cancel),
                    onClick = { confirmingReset = false }
                )
                GhostButton(
                    label = stringResource(R.string.profile_reset),
                    onClick = {
                        // Both, always: the friends list is keyed on an identity
                        // that no longer exists, and leaving it would show rows
                        // that can never come online again.
                        friendStore.clear()
                        profileStore.reset()
                        // The WireGuard public key is a stable identifier the
                        // coordinator sees; leaving it would outlive the profile
                        // it went with.
                        WgKeys.reset(context)
                        name = ""
                        confirmingReset = false
                        Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
                    },
                    tint = DANGER
                )
            }
        ) {
            PadDialogText(stringResource(R.string.profile_reset_confirm))
        }
    }

    if (themePanel) {
        ThemeDialog(
            theme = theme,
            accent = accent,
            onTheme = settingsStore::setTheme,
            onAccent = settingsStore::setAccent,
            onDismiss = { themePanel = false }
        )
    }
}

/**
 * The width past which a settings row stops reading as one thing.
 *
 * The label is on the left, its value on the right: stretched across a handheld
 * screen in landscape, the two sit at opposite edges and the eye no longer pairs
 * them.
 */
private val SETTINGS_MAX_WIDTH = 620.dp

/**
 * How long a row is given to finish opening before we go and fetch it into view.
 * Matched to the unfold animation: any shorter and we bring in a height that is
 * not the right one yet.
 */
private const val EXPAND_SETTLE_MS = 220L

/**
 * A row's corner radius, and therefore its ring's, being the same shape, since
 * the cursor traces the row's outline.
 *
 * Small: at 52 dp tall, a large radius gives a capsule sitting inside a card
 * with far sharper corners.
 */
private val ROW_CORNER = 12.dp

/**
 * How far a row is inset from the edge of its card.
 *
 * This is the width of a settings row: the separators draw it, and the ring has
 * to land on it. One constant for both, otherwise they drift apart at the first
 * adjustment.
 */
private val ROW_INSET = 18.dp

/** The radius of [CardShape], which the end rows inherit. */
private val CARD_CORNER = CardCorner

/**
 * How long the corners of an opening row take to morph. Matched to the unfolding
 * of the detail: any snappier and the shape changes before the content moves.
 */
private const val CORNER_MS = 180

/**
 * How far the text is indented from the card's edge. The separators align on it:
 * they mark the text column, not the row's width, which runs edge to edge.
 */
private val ROW_TEXT_INSET = ROW_INSET

/** The shape of a choice in an unfolded list: a plate laid inside the detail. */
private val ROW_SHAPE = RoundedCornerShape(14.dp)

/** Where a settings card is and how tall it is, in the window's coordinates. */
private data class CardBounds(val top: Float, val height: Float)

/**
 * The card the caller is drawing inside.
 *
 * Anything focusable laid on a card has to fill itself opaquely — see
 * [cardSliceFill] — and the only fill that does not show is the card's own
 * gradient, taken at the right offset. Root coordinates rather than a parent's,
 * because the things that need this are at different depths: a row is a direct
 * child of the card, a choice inside an unfolded detail is three levels down.
 */
private val LocalCardBounds = compositionLocalOf { CardBounds(0f, 0f) }

/**
 * An opaque fill that is, pixel for pixel, what the card was already painting
 * here — plus [tint] laid over it.
 *
 * It exists for the cursor, not for the look. The glow is a drop shadow of the
 * control's outline, and a shadow cast by a layer that is not opaque is drawn
 * *through* it: a focused row filled with a wash of the accent, bright along its
 * rounded edges and hollow in the middle. Nothing cuts a shadow out of its own
 * outline; the only thing that hides it is opaque content on top.
 *
 * A flat colour would have done that job and broken another: each row would
 * freeze the gradient at its own top, and a five-row card would become five
 * bands. Slicing the card's gradient costs the same and shows nothing.
 */
@Composable
private fun Modifier.cardSliceFill(shape: Shape, tint: Color = Color.Transparent): Modifier {
    val card = LocalCardBounds.current
    val colors = plateColors(
        dark = LocalEmufiiDarkTheme.current,
        oled = LocalEmufiiOledTheme.current
    )
    var top by remember { mutableFloatStateOf(Float.NaN) }
    return this
        .onGloballyPositioned { top = it.positionInRoot().y }
        .background(
            // Before either has been measured there is no slice to take, and a
            // zero-height gradient renders as its last colour. The plate's top
            // colour is the honest stand-in for the one frame it lasts.
            brush = if (card.height <= 0f || top.isNaN()) SolidColor(colors.first())
            else Brush.verticalGradient(
                colors = colors,
                startY = card.top - top,
                endY = card.top - top + card.height
            ),
            shape = shape
        )
        .then(if (tint == Color.Transparent) Modifier else Modifier.background(tint, shape))
}

/** The red of gestures that cannot be undone. */
private val DANGER = ShellRed

/**
 * What identifies an unfoldable row.
 *
 * An enum and not the row's index: the order of the sections rearranges between
 * portrait and landscape, and an index would have opened the wrong row when the
 * device was turned.
 */
private enum class SettingsRowId {
    IDENTITY, FOLDER, CONSOLES, KEYS, ARTWORK, HIDDEN, PS2_PROFILE, LANGUAGE, THEME, AUTOFILL, ABOUT
}

/** A section: a heading, then its rows inside a single card. */
/**
 * The PS2 network profile: hand over the card, then take the player's word.
 *
 * Laid out as what it is, a short procedure: prepare, then four steps inside
 * ARMSX2, the warning that carries the one nobody must skip, then the
 * confirmation. The steps are shown before the file is prepared
 * as well as after, because a player deciding whether to bother deserves to see
 * what it will cost them.
 *
 * The confirmation is a real switch and not a formality. Nothing here can look
 * inside ARMSX2 to check the import happened, so this flag is what a PS2 launch
 * is gated on, and flipping it without importing is the one way to get the dead
 * local menu back.
 */
@Composable
private fun Ps2ProfileDetail(ready: Boolean, onReadyChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var exported by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    Text(
        stringResource(R.string.settings_ps2_profile_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    // A GhostButton and not a TextButton: this is the project's focusable pill,
    // the one that carries the focus ring. A bare TextButton sat there unreachable
    // to anyone driving the app with a pad, which on a handheld is everyone.
    GhostButton(
        label = stringResource(R.string.hint_ps2_profile_button),
        onClick = {
            val name = Ps2NetworkProfile.export(context)
            exported = name
            failed = name == null
        },
        fillWidth = true
    )
    exported?.let {
        Text(
            stringResource(R.string.settings_ps2_profile_created, it),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
    if (failed) {
        Text(
            stringResource(R.string.hint_ps2_profile_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    Text(
        stringResource(R.string.settings_ps2_profile_steps_title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
    SettingsStep(1, stringResource(R.string.settings_ps2_profile_step1))
    SettingsStep(2, stringResource(R.string.settings_ps2_profile_step2))
    SettingsStep(3, stringResource(R.string.settings_ps2_profile_step3))
    SettingsStep(4, stringResource(R.string.settings_ps2_profile_step4))
    // Spelled out rather than left as a fifth step: a player who stops after the
    // import has a card that boots and a game that still refuses, which is the
    // exact dead end this whole row exists to avoid.
    Text(
        stringResource(R.string.settings_ps2_profile_warning),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
    ChoiceRow(
        label = stringResource(R.string.settings_ps2_profile_confirm),
        selected = ready,
        onClick = { onReadyChange(!ready) }
    )
}

/**
 * The way back from a game removed in a long-press menu.
 *
 * Restoring is all-or-nothing on purpose. A list of removed games would need
 * their titles and icons, which are read from files this screen never scans, so
 * it would show paths — and a player who removed three regional copies of one
 * game gains nothing from picking among three identical lines. Bringing them all
 * back costs one more removal to redo, and it always works.
 */
@Composable
private fun HiddenRomsDetail(count: Int, onRestore: () -> Unit) {
    Text(
        stringResource(R.string.settings_hidden_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    if (count > 0) {
        GhostButton(
            label = stringResource(R.string.settings_hidden_restore),
            onClick = onRestore,
            fillWidth = true
        )
    }
}

/** A numbered step, for the procedures a settings row has to spell out. */
@Composable
private fun SettingsStep(number: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number.toString(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    var bounds by remember { mutableStateOf(CardBounds(0f, 0f)) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title, modifier = Modifier.padding(start = 4.dp))
        SoftCard {
            // No padding at all. A row takes up the whole card, edge to edge:
            // that is what you see, and leaving even a few dp around it produced
            // a white band between the cursor and the edge, at which point the
            // cursor was the size of nothing. In exchange every row takes the
            // exact shape of the space it occupies, card corners included; the
            // ring's stroke is drawn inside its bounds, so clipping the card
            // does not shave it.
            CompositionLocalProvider(LocalCardBounds provides bounds) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned {
                            bounds = CardBounds(
                                top = it.positionInRoot().y,
                                height = it.size.height.toFloat()
                            )
                        }
                ) { content() }
            }
        }
    }
}



/**
 * A settings row: what it is, where it stands, and the detail underneath when
 * asked for.
 *
 * The value on the right does the work the paragraphs used to do: "Set",
 * "French", "ROMS" answer the question without anything being opened. It fades
 * to grey so as not to compete with the label, which stays the thing the eye
 * scans.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsRow(
    label: String,
    value: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    /**
     * True for the first row on the page: it becomes the "down" destination from
     * the header.
     *
     * A flag rather than a modifier passed in from outside: this row's
     * `Modifier` applies to the column holding the row *and* its detail, and a
     * `FocusRequester` placed there targets a node that is not focusable, so the
     * request fails silently. The clickable row is what must carry it, and that
     * is private.
     */
    entry: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    /**
     * False on the first row of a section.
     *
     * The separator is drawn above and not below: an unfolded row pushes its
     * detail downwards, and a line placed after it would end up separating the
     * detail from the next row instead of separating two rows. Above, it stays
     * where it means something.
     */
    divider: Boolean = true,
    /**
     * True for the last row in a card.
     *
     * Used for drawing, not for logic: together with [divider] it says which of
     * the row's corners are the card's, and therefore what shape the cursor has
     * to take.
     */
    last: Boolean = false,
    /**
     * Null for a row that leads somewhere instead of unfolding — the theme,
     * which opens its own panel. Its chevron then never turns, which is the
     * difference being drawn: a turned chevron says "it is below", a still one
     * says "it is elsewhere".
     */
    detail: (@Composable () -> Unit)? = null
) {
    // The shape of the space this row occupies in the card. A middle row is a
    // plain rectangle; the end ones inherit the card's corners. That is what the
    // ring encloses.
    //
    // Open, it rounds off everywhere. Closed, a row is a slice: it only takes
    // rounding from the card corners it touches. Open, it detaches from the
    // stack, it becomes the header of what it has just revealed, and a sharp
    // corner reads there as a cut, at the top against the previous row as much
    // as at the bottom against its own detail. Both ends therefore follow the
    // same rule, each on its side, and morph rather than snap: otherwise the
    // shape jumps at the precise moment the content is unrolling.
    val top by animateDpAsState(
        targetValue = if (!divider || expanded) CARD_CORNER else 0.dp,
        animationSpec = tween(CORNER_MS),
        label = "settings-row-corner-top"
    )
    val bottom by animateDpAsState(
        targetValue = if (last || expanded) CARD_CORNER else 0.dp,
        animationSpec = tween(CORNER_MS),
        label = "settings-row-corner-bottom"
    )
    val shape = RoundedCornerShape(
        topStart = top,
        topEnd = top,
        bottomStart = bottom,
        bottomEnd = bottom
    )
    val turn by animateFloatAsState(if (expanded) 90f else 0f, label = "settings-chevron")


    // A row reached with no visible marker is a vanished cursor, and the rule
    // holds here as it does in the grid. And it is the green ring, as
    // everywhere else: a plain grey background read as a disabled state rather
    // than as a selection, and it had nothing in common with the cursor on the
    // other screens.
    val interaction = remember { MutableInteractionSource() }

    // Unfolding brings the detail on screen. The cursor stays on the row:
    // nothing moves as far as focus is concerned, so automatic scrolling has no
    // reason to fire, and the content just asked for opened below the fold. So
    // we ask for it explicitly, and on the whole column, row *and* detail,
    // otherwise we only bring in the row, which was already visible.
    //
    // After the opening animation, not during: the column still measures its
    // previous height at the moment the state changes.
    val reveal = remember { BringIntoViewRequester() }
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(EXPAND_SETTLE_MS)
            runCatching { reveal.bringIntoView() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(reveal)
    ) {
        if (divider) {
            Box(
                Modifier
                    .padding(horizontal = ROW_TEXT_INSET)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                // No inset here. The row the eye sees runs from one edge of the
                // card to the other; the ring has to enclose *that*. Shrinking
                // it by [ROW_INSET] to line the outline up with the separators
                // enclosed a box smaller than the row, and the stroke ran
                // through the middle of the label. The inset belongs to the
                // text, so it moved inside, further down.
                .controlRing(shape)
                // Opaque, so the cursor's glow stays outside it. Only the rows
                // with rounded corners — the first and last of every card —
                // showed the flaw: on a plain rectangle the shadow's silhouette
                // coincides with the row exactly and there is no rim to give it
                // away.
                .cardSliceFill(shape)
                // Before the `clickable`, and the order is not cosmetic: a
                // `focusRequester` placed after it no longer targets the focus
                // node the clickable has just created, and the request fails
                // silently, leaving the cursor on the back button.
                .then(if (entry) Modifier.padEntry() else Modifier)
                .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
                .padding(horizontal = ROW_TEXT_INSET, vertical = 12.dp)
        ) {
            leading?.invoke()
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                // Bounded: a long folder name or nickname would otherwise push
                // the label off the row, and the label is what gets read
                // first.
                modifier = Modifier.widthIn(max = 180.dp)
            )
            // The chevron rotates downwards instead of being swapped out: that
            // is what says the detail comes from here, and not somewhere else.
            ChevronRight(
                size = 18.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(turn)
            )
        }

        AnimatedVisibility(
            visible = expanded && detail != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                detail?.invoke()
            }
        }
    }
}

/** Photo, nickname, and what the others see of them. */
@Composable
private fun IdentityDetail(
    profile: Profile,
    name: String,
    onNameChange: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onClearPhoto: () -> Unit,
    photoError: String?
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // A fixed width and not a weight: the photo column contains free text,
        // so unbounded it claims the whole row and the nickname field drops to
        // zero, its label spelling itself out one letter per line.
        Column(
            // 164 and not 150: the ring now reserves a few dp on each side of
            // the pill, and at 150 "Remove photo" went back to two lines.
            modifier = Modifier.width(164.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // One cursor stop, not two: the photo and the pencil trigger the
            // same thing, and two focusable nodes for one gesture meant two
            // presses of a direction with nothing changing on screen. The click
            // is therefore carried by the shared frame.
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier
                    .controlRing(CircleShape)
                    .clickable(onClick = onPickPhoto)
            ) {
                Avatar(
                    name = playerDisplayName(name.ifBlank { Profile.DEFAULT_NAME }),
                    imageFile = profile.avatarFile,
                    size = 88.dp
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("✎", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
            photoError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = DANGER)
            }
            if (profile.avatarFile != null) {
                GhostButton(
                    label = stringResource(R.string.profile_remove_photo),
                    onClick = onClearPhoto,
                    tint = DANGER
                )
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PadTextField(
                value = name,
                onValueChange = { if (it.length <= Profile.MAX_NAME_LENGTH) onNameChange(it) },
                placeholder = stringResource(R.string.profile_default_name),
                label = stringResource(R.string.profile_name_label),
                // Same floor as the onboarding step. Without it here, the pseudo
                // could be shortened afterwards from this screen and land back
                // in the emulator's form as one it refuses, the rule has to
                // hold wherever the name is edited.
                isError = name.trim().length < Profile.MIN_NAME_LENGTH,
                supportingText = {
                    Text(
                        if (name.trim().length < Profile.MIN_NAME_LENGTH) {
                            stringResource(R.string.onb_name_too_short, Profile.MIN_NAME_LENGTH)
                        } else {
                            stringResource(R.string.profile_name_hint)
                        },
                        color = if (name.trim().length < Profile.MIN_NAME_LENGTH) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                stringResource(R.string.profile_photo_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Where the ROMs are, and the button to walk them again.
 *
 * These two buttons used to be pills in the library dock, permanently in front
 * of someone who had picked their folder months earlier. Plumbing you set once
 * belongs in the settings.
 */
@Composable
private fun FolderDetail(
    folder: String?,
    scanning: Boolean,
    count: Int?,
    onPickFolder: () -> Unit,
    onRescan: () -> Unit
) {
    Text(
        stringResource(R.string.settings_library_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GhostButton(
            label = stringResource(
                if (folder == null) R.string.lib_choose_folder
                else R.string.settings_library_change
            ),
            onClick = onPickFolder
        )
        // Nothing to walk until a folder is picked, and picking one scans on
        // its own.
        if (folder != null) {
            GhostButton(
                label = stringResource(R.string.settings_library_rescan),
                onClick = onRescan
            )
        }
    }
    // A scan of a real library takes seconds; without a word here the buttons
    // look like they did nothing.
    when {
        scanning -> Text(
            stringResource(R.string.lib_scanning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )

        count != null -> Text(
            pluralStringResource(R.plurals.settings_library_found, count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The autofill, and the one reason this row exists: Android can switch it off.
 *
 * An accessibility service is not a permission the app holds; it is a system
 * setting the player granted, and the system drops it on its own. An update, a
 * restore onto a new device, a battery optimiser: the service goes quiet and
 * nothing in Emufii says so. The session screen used to carry the way back, as a
 * button that only appeared once the automation was already off, at the bottom
 * of a card nobody scrolls to.
 *
 * It belongs here instead. A switch you flick once in the life of the app is
 * plumbing, and plumbing lives in the settings, next to the ROM folder and the
 * keys. The row shows the state whether it is on or off, which is what makes it
 * findable *before* something is wrong rather than after.
 */
@Composable
private fun AutofillDetail(enabled: Boolean, onOpen: () -> Unit) {
    Text(
        stringResource(if (enabled) R.string.settings_autofill_on else R.string.settings_autofill_off),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        stringResource(R.string.settings_autofill_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    GhostButton(
        label = stringResource(R.string.settings_autofill_open),
        onClick = onOpen,
        fillWidth = true
    )
}

/**
 * Where the player's console keys come from.
 *
 * Deliberately says what Emufii does *not* do, supply keys, download any, send
 * the file anywhere, because asking for a key file with no explanation is how an
 * app gets uninstalled.
 */
@Composable
private fun KeysDetail(
    hasKeys: Boolean,
    rejected: Boolean,
    onPick: () -> Unit,
    onForget: () -> Unit
) {
    Text(
        stringResource(if (hasKeys) R.string.settings_keys_ok else R.string.settings_keys_none),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        stringResource(R.string.settings_keys_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (rejected) {
        Text(
            stringResource(R.string.settings_keys_bad),
            style = MaterialTheme.typography.bodySmall,
            color = DANGER
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GhostButton(
            label = stringResource(
                if (hasKeys) R.string.settings_keys_replace else R.string.settings_keys_pick
            ),
            onClick = onPick
        )
        if (hasKeys) {
            GhostButton(label = stringResource(R.string.settings_keys_forget), onClick = onForget)
        }
    }
}

/**
 * The player's SteamGridDB key, which replaces the tiny ROM icons with real game
 * artwork.
 *
 * The field is in the clear and not masked: this is not a password, it only
 * opens a catalogue of public images, read-only. Masking it would mostly get in
 * the way of spotting a typo, which is the one likely incident, a wrong key says
 * nothing, it simply brings nothing back.
 */
@Composable
private fun ArtworkDetail(key: String, onKeyChange: (String) -> Unit) {
    SteamGridDbMark()
    Text(
        stringResource(R.string.settings_artwork_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    PadTextField(
        value = key,
        onValueChange = onKeyChange,
        label = stringResource(R.string.settings_artwork_field),
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        stringResource(R.string.settings_artwork_where),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private val AppLanguage.labelRes: Int
    get() = when (this) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.FRENCH -> R.string.settings_language_fr
        AppLanguage.ENGLISH -> R.string.settings_language_en
    }

/**
 * A row that reads as chosen or not chosen at a glance, without a radio button.
 * The filled dot and the tinted background do the work; Material's radio in a
 * glass card looks like a form.
 */
@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val emphasis by animateFloatAsState(if (selected) 1f else 0f, label = "choice-row")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .controlRing(ROW_SHAPE)
            // The selection tint is translucent by design, so on its own it
            // never made the row opaque and the glow came through here too.
            .cardSliceFill(
                ROW_SHAPE,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f * emphasis)
            )
            .clip(ROW_SHAPE)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
