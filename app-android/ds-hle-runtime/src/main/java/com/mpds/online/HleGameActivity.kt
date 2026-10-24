package com.mpds.online

import android.app.Activity
import android.content.pm.ActivityInfo
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.KeyEvent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.os.Bundle
import android.os.PowerManager
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.io.File
import eu.emufii.dshle.DsHleRuntime
import eu.emufii.dshle.GuestRequest

/**
 * Emufii's internal GOLD guest screen. The pinned single-owner engine runs in
 * :round; this activity owns display, controls, audio and the authenticated lobby.
 */
class HleGameActivity : ComponentActivity() {

    private lateinit var dsView: DsView
    private lateinit var statusText: TextView
    private lateinit var noticeBanner:RoomNoticeBanner
    private val updateNotices=object:Runnable {
        override fun run(){
            if(isDestroyed)return
            noticeBanner.refresh(GameLink.notices.text().takeIf{lobbyRoot.visibility!=View.VISIBLE})
            ui.postDelayed(this,150)
        }
    }
    private lateinit var lobbyRoot: View
    private lateinit var nameInput: EditText
    private lateinit var codeInput: EditText
    private lateinit var joinButton: Button
    private lateinit var lobbyStatus: TextView
    private var wantDualScreen = false     // set once a game starts; drives the second-panel launch + landscape
    private var dualScreenActive = false   // the bottom-panel activity has been launched
    private var joining = false            // JOIN pressed; the lobby poll loop is running
    private var singleOwnerSeat = 0
    private var roomSession: RoomGuestSession? = null
    private var hostSession: RoomHostSession? = null
    private var guestAccess:DsHleRuntime.GuestAccess?=null
    private var roomRetiring=false
    private var applicationCloseRequested=false
    private val retirementReported=java.util.concurrent.atomic.AtomicBoolean(false)
    private var roomConnection: RoomGuestConnection? = null
    private var savePromptGeneration = -1L
    private var savePromptConnection:RoomGuestConnection?=null
    private var audioThread: Thread? = null
    @Volatile private var audioRunning = false
    @Volatile private var audioGain = 0f
    private var audioFocus: AudioFocusRequest? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())

    // Second (bottom) display appears/changes ASYNCHRONOUSLY on AYN Thor-class clamshells (the panel powers on when
    // the game starts; that is the "open & close Settings to make the external screen work" quirk seen in the wild).
    // A one-shot check at launch misses it -> re-run the attach on every display event AND in onStart. (Pattern +
    // selector taken from the rafaelvcaetano/melonDS-android fork, the reference dual-screen DS emulator for the Thor.)
    private val displayListener = object : android.hardware.display.DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int)   { runOnUiThread { applyDisplayMode() } }
        override fun onDisplayRemoved(displayId: Int) { runOnUiThread { applyDisplayMode() } }
        override fun onDisplayChanged(displayId: Int) { runOnUiThread { applyDisplayMode() } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val host=DsHleRuntime.takeHost(intent.getStringExtra(DsHleRuntime.EXTRA_HOST))
        guestAccess=DsHleRuntime.takeGuest(intent.getStringExtra(DsHleRuntime.EXTRA_GUEST))
        if (intent.hasExtra(DsHleRuntime.EXTRA_HOST)&&host==null ||
            intent.hasExtra(DsHleRuntime.EXTRA_GUEST)&&guestAccess==null ||
            host!=null&&guestAccess!=null || host==null&&
            GuestRequest.parse(intent?.getStringExtra(DsHleRuntime.EXTRA_ROOM),
                intent?.getStringExtra(DsHleRuntime.EXTRA_NAME)) == null) {
            reportRuntimeRetirement()
            finish()
            return
        }
        DsHleRuntime.opened()
        volumeControlStream = AudioManager.STREAM_MUSIC
        setContentView(R.layout.activity_main)

        // Hold a SUSTAINABLE SoC clock from the start (no boost-then-throttle) so locked-60 survives a long session,
        // not just the first minutes -- directly targets the post-warmup thermal cliff. No-op if the SoC lacks it.
        getSystemService(PowerManager::class.java)?.let { if (it.isSustainedPerformanceModeSupported) window.setSustainedPerformanceMode(true) }

        dsView = findViewById(R.id.dsView)
        statusText = findViewById(R.id.statusText)
        lobbyRoot = findViewById(R.id.lobbyRoot)
        nameInput = findViewById(R.id.nameInput)
        codeInput = findViewById(R.id.codeInput)
        joinButton = findViewById(R.id.joinButton)
        lobbyStatus = findViewById(R.id.lobbyStatus)
        noticeBanner=RoomNoticeBanner(this)
        (dsView.parent as android.widget.LinearLayout).addView(noticeBanner,1,
            android.widget.LinearLayout.LayoutParams(-1,-2))
        ui.post(updateNotices)
        if(BuildConfig.SINGLE_OWNER) {
            val container=lobbyStatus.parent as android.widget.LinearLayout
            container.removeView(lobbyStatus);container.addView(lobbyStatus,4)
            val savesButton=Button(this).apply {
                text="MES SAUVEGARDES";setBackgroundResource(R.drawable.lobby_field)
                setTextColor(android.graphics.Color.WHITE)
                visibility=View.GONE
                setOnClickListener { showSavedCartridges() }
            }
            val optionsButton=Button(this).apply {
                text="OPTIONS";setBackgroundResource(R.drawable.lobby_field)
                setTextColor(android.graphics.Color.WHITE)
                setOnClickListener {
                    val expanded=savesButton.visibility!=View.VISIBLE
                    savesButton.visibility=if(expanded)View.VISIBLE else View.GONE
                    text=if(expanded)"FERMER LES OPTIONS" else "OPTIONS"
                }
            }
            container.addView(optionsButton)
            container.addView(savesButton)
            PixelUi.style(findViewById(android.R.id.content))
        }
        dsView.onFps = { f -> runOnUiThread {
            val seat = if (BuildConfig.SINGLE_OWNER) singleOwnerSeat else NativeBridge.nativeMySeat()
            statusText.text = "P$seat · %.0f fps".format(f)
        } }
        dsView.onError = { runOnUiThread {
            if(BuildConfig.SINGLE_OWNER) {
                if(hostSession!=null)retireHost("La partie s’est interrompue.")
                else retireSingleOwner("La partie s’est interrompue. Rejoignez le salon pour réessayer.")
            }
        } }

        joinButton.setOnClickListener {
            if(hostSession!=null) {
                hostSession?.start();joinButton.isEnabled=false
            } else if(BuildConfig.SINGLE_OWNER && roomSession!=null)
                retireSingleOwner("Vous avez quitté le salon.")
            else onJoinPressed()
        }
        codeInput.setOnEditorActionListener { _, _, _ -> onJoinPressed(); true }

        wireControls()

        // Orientation FIRST, independent of any game: on a Thor-class dual-screen device the top panel is mounted
        // LANDSCAPE, so a portrait activity renders sideways. Apply the correct display mode immediately.
        orientationForDual = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        applyDisplayMode()

        // The host app provides only a validated room code and display name.
        if(host!=null)startHost(host)
        else if(intent.hasExtra(DsHleRuntime.EXTRA_HOST))finish()
        else handleGuestIntent(intent)
        DsHleRuntime.attachCloser {ui.post {closeFromApplication()}}

    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // A current owner is immutable; no later Intent replaces its credentials.
        if(roomSession!=null||hostSession!=null||joining)return
        if(intent.hasExtra(DsHleRuntime.EXTRA_HOST)){finish();return}
        val next=DsHleRuntime.takeGuest(intent.getStringExtra(DsHleRuntime.EXTRA_GUEST))
        if(intent.hasExtra(DsHleRuntime.EXTRA_GUEST)&&next==null){finish();return}
        runCatching {guestAccess?.close()};guestAccess=next
        setIntent(intent);handleGuestIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        getSystemService(DisplayManager::class.java)?.registerDisplayListener(displayListener, null)
        applyDisplayMode()   // re-apply orientation + re-attach the bottom panel on return-from-background
    }
    override fun onPause()   { super.onPause();   android.util.Log.i("MPDS-life", "onPause") }
    override fun onStop()    { super.onStop();    getSystemService(DisplayManager::class.java)?.unregisterDisplayListener(displayListener); android.util.Log.i("MPDS-life", "onStop") }
    override fun onDestroy() {
        ui.removeCallbacks(updateNotices)
        ui.removeCallbacks(lobbyPoll)
        val retiring = roomSession
        val accessRetiring=guestAccess;guestAccess=null
        roomSession=null;roomConnection=null
        val hostRetiring=hostSession;hostSession=null
        if(hostRetiring!=null)hostRetiring.closeAsync().whenComplete {_,error->if(error==null)reportRuntimeRetirement()}
        else if (retiring == null) {runCatching {accessRetiring?.close()};reportRuntimeRetirement()}
        else retiring.closeAsync().whenComplete { _, error ->
            runCatching {accessRetiring?.close()}
            // A failed retirement keeps the native reservation unavailable.
            if (error == null) reportRuntimeRetirement()
        }
        super.onDestroy(); audioRunning=false; GameLink.bottomShouldRun = false
        runCatching { wifiLock?.release() }; wifiLock = null
    }

    // Only validated, app-owned selection is accepted by this private activity.
    private fun handleGuestIntent(intent: android.content.Intent?) {
        if (joining || roomSession != null || hostSession != null) return
        val request = GuestRequest.parse(intent?.getStringExtra(DsHleRuntime.EXTRA_ROOM),
            intent?.getStringExtra(DsHleRuntime.EXTRA_NAME)) ?: return
        nameInput.setText(request.playerName)
        codeInput.setText(request.roomCode)
        lobbyStatus.text = "Connexion au salon ${request.roomCode}…"
        ui.post { if (!isFinishing && !isDestroyed) onJoinPressed() }
    }

    private fun onJoinPressed() {
        if (joining) return
        val name = nameInput.text.toString().trim()
        val code = RoomCode.parse(codeInput.text.toString())
        if (name.isEmpty()) { nameInput.requestFocus(); lobbyStatus.text = "Entrez votre nom."; return }
        if (code == null) { codeInput.requestFocus(); lobbyStatus.text = "Entrez le code de quatre caractères partagé par l’hôte."; return }
        hideKeyboard()
        joinRoom(name, code)
    }

    private fun joinRoom(name: String, code: String) = joinSingleOwner(name, code)

    private fun reportRuntimeRetirement():Boolean {
        // finish() schedules onDestroy later. Once this Activity released its
        // owner, that later callback must not clear a newly opened round.
        if(RoomEngine.hasReservedLease())return false
        if(retirementReported.compareAndSet(false,true))DsHleRuntime.retired()
        return true
    }

    private fun closeFromApplication() {
        if(isFinishing||isDestroyed)return
        applicationCloseRequested=true
        if(hostSession!=null){retireHost("Le salon a été fermé.");return}
        val retiring=roomSession
        if(retiring==null){finish();return}
        roomRetiring=true;joinButton.isEnabled=false
        retiring.closeAsync().whenComplete {_,error->runOnUiThread {
            if(roomSession===retiring&&!isDestroyed) {
                if(error==null){roomSession=null;roomConnection=null;reportRuntimeRetirement();finish()}
                else lobbyStatus.text="Impossible de fermer le moteur. Relancez l’application."
            }
        }}
    }

    private fun startHost(registration:DsHleRuntime.HostRegistration) {
        val request=registration.request
        nameInput.setText(request.playerName);codeInput.setText(request.roomCode)
        nameInput.isEnabled=false;codeInput.isEnabled=false;joinButton.text="DÉMARRER LA PARTIE";joinButton.isEnabled=false
        val root=File(cacheDir.canonicalFile,"gold-hle-rounds").apply {mkdirs()}
        lateinit var session:RoomHostSession
        session=RoomHostSession(applicationContext,registration,root,onStatus={text,ready->runOnUiThread {
            if(hostSession===session&&!isDestroyed){
                lobbyStatus.text=text
                if(lobbyRoot.visibility==View.VISIBLE)GameLink.bottomLobby=text
                joinButton.isEnabled=ready
            }
        }},onReady={seat,abort->
            val attached=java.util.concurrent.CompletableFuture<Unit>()
            runOnUiThread {
                try {
                    check(hostSession===session&&!isDestroyed&&!attached.isCancelled)
                    dsView.onError={runOnUiThread {if(hostSession===session)retireHost("Partie interrompue.")}}
                    singleOwnerSeat=seat;lobbyRoot.visibility=View.GONE;GameLink.bottomLobby=null
                    statusText.text="P$seat";dsView.startEmu();startAudio();wantDualScreen=true;applyDisplayMode()
                    attached.complete(Unit)
                }catch(e:Exception){attached.completeExceptionally(e)}
            }
            try{attached.get(10,java.util.concurrent.TimeUnit.SECONDS)}
            catch(e:Exception){attached.cancel(false);runCatching {abort()};dsView.stopEmuAndDrain();stopAudio();throw e}
            java.io.Closeable {
                dsView.stopEmuAndDrain();stopAudio()
                runOnUiThread {if(hostSession===session&&!isDestroyed){wantDualScreen=false;applyDisplayMode();lobbyRoot.visibility=View.VISIBLE}}
            }
        })
        hostSession=session
        // Internal prepare/network/game failures retire the host themselves.
        // Observe their result as well as an explicit Quit, before work starts.
        session.onTermination {error->runOnUiThread {
            if(hostSession===session&&!isDestroyed) {
                roomRetiring=true;joinButton.isEnabled=false
                if(error==null&&reportRuntimeRetirement()) {
                    hostSession=null
                    finish()
                } else {
                    lobbyRoot.visibility=View.VISIBLE
                    lobbyStatus.text="Impossible de fermer le moteur. Relancez l’application."
                }
            }
        }}
        session.open()
    }
    private fun retireHost(message:String) {
        val retiring=hostSession ?: return
        if(roomRetiring)return
        roomRetiring=true;joinButton.isEnabled=false;lobbyRoot.visibility=View.VISIBLE;lobbyStatus.text="Fermeture de la partie…"
        retiring.closeAsync()
    }

    private fun joinSingleOwner(name:String,code:String) {
        try { RoomDirectory.name(name) } catch(_:Exception) {
            lobbyStatus.text="Nom invalide : utilisez 1 à 10 caractères.";return
        }
        GameLink.notices.reset()
        joining=true;joinButton.isEnabled=false;nameInput.isEnabled=false;codeInput.isEnabled=false
        joinButton.text="CONNEXION EN COURS"
        lobbyStatus.text="Connexion au salon $code…"
        // Android may expose its own app storage through a user-directory
        // alias in the app's mount namespace. Resolve ONLY this trusted
        // Context-owned base, before creating any incoming files. Native
        // admission still rejects links in the round and its asset paths.
        val root=File(cacheDir.canonicalFile,"gold-hle-rounds").apply { mkdirs() }
        lateinit var session:RoomGuestSession
        fun show(message:String) { runOnUiThread {
            if(roomSession===session && !isDestroyed) {
                lobbyStatus.text=message
                if(lobbyRoot.visibility==View.VISIBLE)GameLink.bottomLobby=message
            }
        } }
        fun disconnected(player:RoomDirectory.Player) { runOnUiThread {
            if(roomSession===session && !isDestroyed) {
                GameLink.notices.disconnected(player)
            }
        } }
        val directory=guestAccess?.let {access->RoomDirectory(RoomDirectory.Transport {room,operation,token,body->
            val response=access.post(room,operation,token,body)
            require(response.size<=65536)
            RoomDirectory.Response(200,"application/json",null,response)
        })} ?: RoomDirectory()
        session=RoomGuestSession(directory=directory,prefetchRoot=root,startRound={ round ->
            GameLink.notices.reset()
            val connected=java.util.concurrent.CompletableFuture<RoomGuestConnection>()
            lateinit var connection:RoomGuestConnection
            connection=RoomGuestConnection.open(round,name,startGame={ request ->
                RoomNativeGame.start(applicationContext,round,request,root,progress={ phase,received,total ->
                    show(when(phase) {
                        "download"->"Réception du jeu : ${if(total>0)received*100/total else 0}%"
                        "preparation"->"Préparation de votre console…"
                        "waiting-for-host"->"En attente du départ collectif…"
                        "starting"->"Démarrage du jeu…"
                        else->"Partie en cours"
                    })
                },onReady={ seat,abortStartup ->
                    val ready=java.util.concurrent.CompletableFuture<Unit>()
                    runOnUiThread {
                        try {
                            check(!ready.isCancelled && roomSession===session && !isDestroyed);request.checkCurrent()
                            dsView.onError={ runOnUiThread {
                                if(roomSession===session && !isDestroyed && !request.stopping && request.admitted.isCurrent())
                                    retireSingleOwner("La partie s’est interrompue. Rejoignez le salon pour réessayer.")
                            } }
                            singleOwnerSeat=seat;lobbyRoot.visibility=View.GONE;GameLink.bottomLobby=null
                            statusText.text="P$seat";dsView.startEmu();startAudio()
                            wantDualScreen=true;applyDisplayMode();ready.complete(Unit)
                        } catch(e:Exception) { ready.completeExceptionally(e) }
                    }
                    try { ready.get(10,java.util.concurrent.TimeUnit.SECONDS) }
                    catch(e:Exception) {
                        ready.cancel(false)
                        // Rendering may already be inside a network-blocked frame.
                        // Release that wait before joining the renderer/audio.
                        runCatching { abortStartup() }
                        dsView.stopEmuAndDrain();stopAudio();throw e
                    }
                    java.io.Closeable {
                        dsView.stopEmuAndDrain();stopAudio()
                        runOnUiThread {
                            if(roomSession===session && !isDestroyed) {
                                wantDualScreen=false;GameLink.bottomLobby="DANS LE SALON\nEN ATTENTE DE L HOTE"
                                lobbyRoot.visibility=View.VISIBLE;statusText.text=""
                                lobbyStatus.text="Dans le salon — en attente de l’hôte."
                            }
                        }
                    }
                })
            },validateSave={ game,bytes -> RoomLobbySavePolicy.validateSize(bytes.size,game) },
                onChange={ status -> connected.thenAccept { currentConnection -> runOnUiThread {
                    if(roomSession===session && !isDestroyed) {
                        GameLink.notices.diagnostic(status.playerDiagnostic,status.roster)
                        if(status.phase=="waiting")lobbyStatus.text="Dans le salon $code — en attente de l’hôte."
                        if(status.saveRequested && (savePromptConnection!==currentConnection || savePromptGeneration!=status.generation)) {
                            savePromptGeneration=status.generation;savePromptConnection=currentConnection
                            promptSave(currentConnection)
                        }
                    }
                } } },onDisconnected=::disconnected,onError={ code -> runOnUiThread {
                    android.util.Log.w("Wantap-room", "Inner session failed: $code")
                    if(roomSession===session)retireSingleOwner("Connexion interrompue ($code). Rejoignez le salon.")
                } },authorityCheckpoints=BuildConfig.AUTHORITY_CHECKPOINTS)
            connected.complete(connection)
            runOnUiThread { if(roomSession===session)roomConnection=connection }
            connection
        },onSnapshot={ snapshot ->
            if(snapshot.phase==RoomDirectory.Phase.WAITING) {
                show("En attente du départ par l’hôte.")
                runOnUiThread { if(roomSession===session && !isDestroyed && !roomRetiring) {
                    showJoinedLobby(snapshot)
                    joinButton.text="QUITTER LE SALON";joinButton.isEnabled=true
                } }
            }
        },onDisconnected=::disconnected,onError={
            runOnUiThread { if(roomSession===session) {
                retireSingleOwner("Connexion impossible. Vérifiez le salon et réessayez.")
            } }
        })
        roomSession=session;session.join(code,name)
    }

    private fun retireSingleOwner(message:String) {
        val session=roomSession ?: return
        if(roomRetiring)return
        roomRetiring=true
        joinButton.isEnabled=false
        dsView.stopEmu();audioRunning=false
        lobbyRoot.visibility=View.VISIBLE;lobbyStatus.text="Fermeture de la partie…"
        session.closeAsync().whenComplete { _,error -> runOnUiThread {
            if(roomSession===session && !isDestroyed) {
                roomSession=null;roomConnection=null;pendingSave=null;savePromptConnection=null
                wantDualScreen=false;GameLink.bottomLobby=message
                if(error==null) {
                    roomRetiring=false
                    if(applicationCloseRequested){reportRuntimeRetirement();finish()} else resetLobby(message)
                }
                else { lobbyStatus.text="Impossible de fermer le moteur. Relancez l’application.";joinButton.isEnabled=false }
            }
        } }
    }

    private var pendingSave:RoomGuestConnection?=null
    private var saveToExport:File?=null
    private val exportSavePicker=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val source=saveToExport;saveToExport=null
        if(uri!=null && source!=null)Thread {
            val success=runCatching {
                check(source.parentFile==File(filesDir,"gold-hle/saves") && source.isFile)
                source.inputStream().use { input ->
                    checkNotNull(contentResolver.openOutputStream(uri,"w")).use { output -> input.copyTo(output) }
                }
            }.isSuccess
            runOnUiThread { toast(if(success)"Sauvegarde exportée." else "Export impossible. La copie interne est conservée.") }
        }.start()
    }
    private fun showSavedCartridges() {
        val saves=File(filesDir,"gold-hle/saves").listFiles()?.filter { it.isFile && it.extension=="sav" }?.sortedByDescending { it.lastModified() }?:emptyList()
        if(saves.isEmpty()) { toast("Les sauvegardes sont conservées à la fermeture de la partie.");return }
        val labels=saves.map { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.lastModified()))+" - ${it.length()/1024} Ko" }.toTypedArray()
        android.app.AlertDialog.Builder(this).setTitle("Exporter une sauvegarde")
            .setItems(labels) { _,index -> saveToExport=saves[index];exportSavePicker.launch(saves[index].name) }
            .setNegativeButton("ANNULER",null).show()
    }
    private fun promptSave(connection:RoomGuestConnection) {
        android.app.AlertDialog.Builder(this).setTitle("Sauvegarde personnelle")
            .setMessage("L’hôte demande une sauvegarde. Vous pouvez jouer sans en fournir.")
            .setPositiveButton("Choisir un fichier") { _,_->
                pendingSave=connection;savePicker.launch(arrayOf("*/*"))
            }
            .setNegativeButton("Sans sauvegarde") { _,_-> connection.lobby.submitSave(null) }
            .setCancelable(false).show()
    }
    private val savePicker=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        val connection=pendingSave;pendingSave=null
        if(connection!=null && uri!=null)Thread {
            var bytes:ByteArray?=null
            try {
                bytes=contentResolver.openInputStream(uri)!!.use { input ->
                    val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(65536)
                    while(true) { val n=input.read(buffer);if(n<0)break
                        require(output.size().toLong()+n<=67108864);output.write(buffer,0,n) }
                    output.toByteArray()
                }
                connection.lobby.submitSave(bytes).get(10,java.util.concurrent.TimeUnit.SECONDS)
            } catch(_:Exception) { runOnUiThread {
                if(!isDestroyed && roomConnection===connection) {
                    toast("Sauvegarde refusée. Réessayez ou choisissez sans sauvegarde.");promptSave(connection)
                }
            } } finally { bytes?.fill(0) }
        }.start()
        else if(connection!=null && !isDestroyed && roomConnection===connection)promptSave(connection)
    }

    // Poll the native lobby state (uiMode / rom-fetch %) and flip to the game the moment the core starts running.
    private val lobbyPoll = object : Runnable {
        override fun run() {
            if (!joining) return
            if (NativeBridge.nativeMpRunning() == 1) { enterGame(); return }
            val pct = NativeBridge.nativeRomFetchPct()
            lobbyStatus.text = when {
                pct in 0..100 -> "Downloading the game from the host… $pct%"
                else -> when (NativeBridge.nativeUiMode()) {
                    2 -> "Reconnecting…"
                    3, 1 -> "Waiting for the host to start…"
                    5 -> "Starting…"
                    else -> "In the room — waiting for players…"
                }
            }
            ui.postDelayed(this, 250)
        }
    }
    private fun startLobbyPoll() { ui.removeCallbacks(lobbyPoll); ui.post(lobbyPoll) }

    private fun enterGame() {
        joining = false
        ui.removeCallbacks(lobbyPoll)
        lobbyRoot.visibility = View.GONE
        val seat = NativeBridge.nativeMySeat().coerceIn(1, 4)
        statusText.text = "P$seat"
        // DIAG bisection marker: `nodsview` in the external dir -> join + run the core WITHOUT the render/audio
        // threads (isolates the Java-side loops from the lockstep; watch [emu] fps in logcat).
        val noDsView = runCatching { File(getExternalFilesDir(null), "nodsview").exists() }.getOrDefault(false)
        if (noDsView) { android.util.Log.i("MPDS-life", "entered game as P$seat (nodsview DIAG: no render/audio threads)"); return }
        dsView.startMp(seat)
        startAudio()
        wantDualScreen = true
        applyDisplayMode()
        android.util.Log.i("MPDS-life", "entered game as P$seat")
    }

    private fun showJoinedLobby(snapshot:RoomDirectory.Snapshot) {
        val code=findViewById<TextView>(R.id.joinedRoomCode)
        val first=code.visibility!=View.VISIBLE
        code.text=snapshot.code;code.visibility=View.VISIBLE
        findViewById<TextView>(R.id.joinedRoster).apply {
            text=snapshot.roster.joinToString("\n") { "P${it.seat}  ${it.name}  -  EN LIGNE" }
            visibility=View.VISIBLE
        }
        for(id in listOf(R.id.nameLabel,R.id.nameInput,R.id.codeLabel,R.id.codeInput))
            findViewById<View>(id).visibility=View.GONE
        if(first) {
            hideKeyboard()
            (lobbyRoot as android.widget.ScrollView).scrollTo(0,0)
        }
    }

    private fun resetLobby(msg: String) {
        joining = false
        ui.removeCallbacks(lobbyPoll)
        joinButton.isEnabled = true; nameInput.isEnabled = true; codeInput.isEnabled = true
        if(BuildConfig.SINGLE_OWNER) {
            joinButton.text="REJOINDRE"
            findViewById<View>(R.id.joinedRoomCode).visibility=View.GONE
            findViewById<View>(R.id.joinedRoster).visibility=View.GONE
            for(id in listOf(R.id.nameLabel,R.id.nameInput,R.id.codeLabel,R.id.codeInput))
                findViewById<View>(id).visibility=View.VISIBLE
        }
        lobbyRoot.visibility = View.VISIBLE
        lobbyStatus.text = msg
    }

    private fun hideKeyboard() {
        val imm = getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(joinButton.windowToken, 0)
    }

    // Audio: a dedicated thread pulls MY seat's SPU samples from the core ring into a streaming AudioTrack.
    private fun startAudio() {
        if (audioRunning) return
        val manager = getSystemService(AudioManager::class.java)
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener({ change ->
                audioGain = when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> 1f
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> 0.2f
                    else -> 0f
                }
                android.util.Log.i("MPDS-audio", "focus=$change gain=$audioGain")
            }, ui).build()
        audioFocus = request
        val focusResult = manager.requestAudioFocus(request)
        audioGain = if (focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) 1f else 0f
        android.util.Log.i("MPDS-audio", "focus-request=$focusResult")
        audioRunning = true
        audioThread = Thread {
            var track: AudioTrack? = null
            try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            val rate = 32823   // melonDS SPU output rate (Hz); AudioTrack resamples to the device rate
            val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            val output = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(maxOf(minBuf, 16384))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)   // minSdk 26: faster path, smaller buffer
                .build()
            track = output
            output.setVolume(audioGain)
            output.play()
            var appliedGain = audioGain
            val buf = ShortArray(1024 * 2)
            var acc = 0L; var peak = 0; var lastLog = System.nanoTime()
            while (audioRunning) {
                val pairs = if (BuildConfig.SINGLE_OWNER) RoomEngine.nativeReadSingleOwnerAudio(buf, 1024)
                    else NativeBridge.nativeReadAudio(buf, 1024)
                val gain = audioGain
                if (gain != appliedGain) { output.setVolume(gain); appliedGain = gain }
                if (pairs > 0) {
                    for (i in 0 until pairs * 2) peak = maxOf(peak, kotlin.math.abs(buf[i].toInt()))
                    var offset = 0
                    while (audioRunning && offset < pairs * 2) {
                        val written = output.write(buf, offset, pairs * 2 - offset)
                        check(written > 0) { "audio-write-$written" }
                        offset += written
                    }
                    acc += offset / 2
                } else Thread.sleep(3)
                val now = System.nanoTime()
                if (now - lastLog >= 1_000_000_000L) { android.util.Log.i("MPDS-audio", "wrote $acc pairs/s (rate=$rate) peak=$peak gain=$audioGain"); acc = 0; peak = 0; lastLog = now }
            }
            } catch (error:Exception) {
                android.util.Log.e("MPDS-audio", "audio output failed", error)
                audioRunning=false
            } finally {
                try { track?.stop() } catch (_: Exception) {}
                track?.release()
                manager.abandonAudioFocusRequest(request)
            }
        }.also { it.start() }
    }
    private fun stopAudio() {
        audioRunning = false
        val thread=audioThread;thread?.join(3000)
        check(thread?.isAlive!=true) { "audio-retirement-timeout" }
        audioThread=null
        audioFocus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        audioFocus=null; audioGain=0f
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---- DUAL-SCREEN (AYN Thor-class clamshells): DS top screen on THIS panel, DS bottom + touch on the lower panel ----
    // The user's Thor runs the iiSU launcher + AYN Dual Screen Assistant, which own the lower panel and keep it OUT of
    // the presentation-display category (so a Presentation never attaches). We instead LAUNCH our own activity directly
    // onto that display by ID (ActivityOptions.setLaunchDisplayId) -- this force-takes the panel regardless of iiSU and
    // wakes it (confirmed: other native dual-screen emulators light this panel on this device).
    private companion object {
        val EXCLUDED_DISPLAYS = listOf("HiddenDisplay", "WebRTC_ScreenCapture")
    }

    /** The real second display, or null on a single-screen phone (S23 etc. -> stays portrait). ALL categories: iiSU
     *  keeps the panel out of the PRESENTATION list, so we can't filter by that -- pick any non-default real display. */
    private fun secondaryDisplay(): Display? {
        val cur = windowManager.defaultDisplay?.displayId ?: 0
        return getSystemService(DisplayManager::class.java)
            ?.displays
            ?.firstOrNull { it.displayId != cur && it.name !in EXCLUDED_DISPLAYS && it.isValid }
    }

    // Called from onCreate, onStart, every display event, and on game start. Two independent jobs:
    //  (1) ORIENTATION + layout: on a dual-screen device -> landscape + DS-top-only; else -> portrait stacked.
    //      This is NOT gated on a game, so the top screen is oriented correctly the moment the app is on the Thor.
    //  (2) BOTTOM PANEL: once a game is running (wantDualScreen), launch the bottom activity onto the 2nd display.
    private var orientationForDual: Int = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE   // tunable (see cycleTopOrientation)
    private fun applyDisplayMode() {
        val all = getSystemService(DisplayManager::class.java)?.displays?.joinToString { "${it.displayId}:${it.name}" }
        val sd = secondaryDisplay()
        android.util.Log.i("MPDS-life", "applyDisplayMode dualDevice=${sd != null} want=$wantDualScreen active=$dualScreenActive displays=[$all]")
        if (sd != null) {
            requestedOrientation = orientationForDual
            dsView.dualMode = true
            findViewById<View>(R.id.controlsRoot)?.visibility = View.GONE   // real DS layout: physical buttons drive it
            if ((wantDualScreen || BuildConfig.SINGLE_OWNER) && !dualScreenActive) launchBottomScreen(sd)
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT   // single-screen phone: keep the stacked layout
            dsView.dualMode = false
            findViewById<View>(R.id.controlsRoot)?.visibility = View.VISIBLE
        }
    }

    private fun launchBottomScreen(sd: Display) {
        try {
            dualScreenActive = true
            GameLink.bottomShouldRun = true
            val opts = android.app.ActivityOptions.makeBasic().setLaunchDisplayId(sd.displayId)
            val intent = android.content.Intent(this, BottomScreenActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            startActivity(intent, opts.toBundle())
            android.util.Log.i("MPDS-life", "bottom panel: launched activity on display ${sd.displayId} '${sd.name}'")
        } catch (e: Exception) { dualScreenActive = false; android.util.Log.w("MPDS-life", "bottom panel launch failed: " + e.message) }
    }

    // adb-tunable landscape direction, in case SCREEN_ORIENTATION_LANDSCAPE faces the wrong way on this panel:
    //   adb shell am start -n com.mpds.online/.MainActivity --ei toporient <0=landscape|1=reverse|2=user|3=sensor>
    private fun orientationConst(i: Int) = when (i) {
        1 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        2 -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        3 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    // ---- physical gamepad -> GameLink.padMain (merged with overlay + the bottom activity's pad) ----
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val bit = dsBitFor(event.keyCode)
        if (bit != 0) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> GameLink.padMain = GameLink.padMain or bit
                KeyEvent.ACTION_UP   -> GameLink.padMain = GameLink.padMain and bit.inv()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
    private fun dsBitFor(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> NativeBridge.Keys.A
        KeyEvent.KEYCODE_BUTTON_B -> NativeBridge.Keys.B
        KeyEvent.KEYCODE_BUTTON_X -> NativeBridge.Keys.X
        KeyEvent.KEYCODE_BUTTON_Y -> NativeBridge.Keys.Y
        KeyEvent.KEYCODE_BUTTON_L1 -> NativeBridge.Keys.L
        KeyEvent.KEYCODE_BUTTON_R1 -> NativeBridge.Keys.R
        KeyEvent.KEYCODE_BUTTON_START -> NativeBridge.Keys.START
        KeyEvent.KEYCODE_BUTTON_SELECT -> NativeBridge.Keys.SELECT
        KeyEvent.KEYCODE_DPAD_UP -> NativeBridge.Keys.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> NativeBridge.Keys.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> NativeBridge.Keys.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> NativeBridge.Keys.RIGHT
        else -> 0
    }
    override fun onGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        // d-pads that report as HAT axes + the left stick as a d-pad fallback (threshold 0.5)
        val hx = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_X) + event.getAxisValue(android.view.MotionEvent.AXIS_X)
        val hy = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_Y) + event.getAxisValue(android.view.MotionEvent.AXIS_Y)
        var m = GameLink.padMain and (NativeBridge.Keys.UP or NativeBridge.Keys.DOWN or NativeBridge.Keys.LEFT or NativeBridge.Keys.RIGHT).inv()
        if (hx < -0.5f) m = m or NativeBridge.Keys.LEFT
        if (hx >  0.5f) m = m or NativeBridge.Keys.RIGHT
        if (hy < -0.5f) m = m or NativeBridge.Keys.UP
        if (hy >  0.5f) m = m or NativeBridge.Keys.DOWN
        GameLink.padMain = m
        return true
    }

    // ---- on-screen controls -> DS key bitmask ----
    private fun wireControls() {
        bindKey(R.id.btnA,      NativeBridge.Keys.A)
        bindKey(R.id.btnB,      NativeBridge.Keys.B)
        bindKey(R.id.btnX,      NativeBridge.Keys.X)
        bindKey(R.id.btnY,      NativeBridge.Keys.Y)
        bindKey(R.id.btnL,      NativeBridge.Keys.L)
        bindKey(R.id.btnR,      NativeBridge.Keys.R)
        bindKey(R.id.btnStart,  NativeBridge.Keys.START)
        bindKey(R.id.btnSelect, NativeBridge.Keys.SELECT)
        bindKey(R.id.btnUp,     NativeBridge.Keys.UP)
        bindKey(R.id.btnDown,   NativeBridge.Keys.DOWN)
        bindKey(R.id.btnLeft,   NativeBridge.Keys.LEFT)
        bindKey(R.id.btnRight,  NativeBridge.Keys.RIGHT)
    }

    @Suppress("ClickableViewAccessibility")
    private fun bindKey(id: Int, bit: Int) {
        findViewById<View>(id)?.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { GameLink.overlay = GameLink.overlay or bit; v.isPressed = true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { GameLink.overlay = GameLink.overlay and bit.inv(); v.isPressed = false }
            }
            true
        }
    }
}
