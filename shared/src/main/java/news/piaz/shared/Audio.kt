package news.piaz.shared

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Plays the Daily Brief in the background. Media3 shows the notification and lock-screen controls,
 * handles headphones being unplugged and audio focus, and answers Bluetooth and Assistant commands.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(15_000).setSeekForwardIncrementMs(30_000)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        session = MediaSession.Builder(this, player).apply {
            if (launch != null) setSessionActivity(android.app.PendingIntent.getActivity(this@PlaybackService, 0, launch, android.app.PendingIntent.FLAG_IMMUTABLE))
        }.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}

/** The app's view of the podcast player. Talks to PlaybackService through a MediaController. */
class AudioPlayer(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("audio", Context.MODE_PRIVATE)
    private val json = Json
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private var pending: (() -> Unit)? = null

    var episode by mutableStateOf<Episode?>(null); private set
    var playing by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    var duration by mutableStateOf(0.0); private set
    var time by mutableStateOf(0.0); private set
    var rate by mutableFloatStateOf(1f); private set
    var positions by mutableStateOf(prefs.getString("positions", null)?.let { runCatching { json.decodeFromString<Map<String, Double>>(it) }.getOrNull() } ?: emptyMap()); private set
    var finished by mutableStateOf(prefs.getStringSet("finished", emptySet())!!.toSet()); private set

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying; if (!isPlaying) remember() }
                override fun onPlayerError(error: PlaybackException) { failed = true; playing = false }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY && c.duration > 0) duration = c.duration / 1000.0
                    if (state == Player.STATE_ENDED) ended()
                }
            })
            pending?.invoke(); pending = null
        }, MoreExecutors.directExecutor())
        scope.launch {
            while (isActive) {
                controller?.let { c -> if (c.isPlaying) { time = c.currentPosition / 1000.0; if (time.toInt() % 10 == 0) remember() } }
                delay(500)
            }
        }
    }

    private fun withController(f: (MediaController) -> Unit) {
        val c = controller
        if (c != null) f(c) else pending = { controller?.let(f) }
    }

    fun play(e: Episode) {
        if (episode?.id == e.id) { toggle(); return }
        remember()
        episode = e; failed = false; duration = e.duration
        val start = if (e.id in finished) 0.0 else positions[e.id] ?: 0.0
        finished = finished - e.id; saveFinished()
        time = start
        val item = MediaItem.Builder().setMediaId(e.id).setUri(Uri.parse(e.audio))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(e.headline).setArtist("The Piaz").setAlbumTitle(e.kind)
                .setArtworkUri(Uri.parse("android.resource://${app.packageName}/${R.drawable.mark}")).build())
            .build()
        withController { c ->
            c.setMediaItem(item, if (start > 2) (start * 1000).toLong() else 0L)
            c.setPlaybackSpeed(rate)
            c.prepare(); c.play()
        }
    }

    fun retry() { episode?.let { episode = null; play(it) } }
    fun resume() = withController { it.play() }
    fun pause() = withController { it.pause(); remember() }
    fun toggle() { if (failed) retry() else if (playing) pause() else resume() }
    fun skip(s: Double) = seek(time + s)
    fun seek(t: Double) {
        val v = t.coerceIn(0.0, maxOf(duration - 0.5, 0.0))
        time = v
        withController { it.seekTo((v * 1000).toLong()) }
    }
    fun cycleRate() {
        val rates = listOf(1f, 1.25f, 1.5f, 1.75f, 2f, 0.8f)
        rate = rates[(rates.indexOf(rate) + 1) % rates.size]
        withController { it.setPlaybackSpeed(rate) }
    }
    fun close() { pause(); withController { it.stop(); it.clearMediaItems() }; episode = null }

    private fun ended() {
        val e = episode ?: return
        playing = false; positions = positions - e.id; finished = finished + e.id; time = 0.0
        saveFinished(); prefs.edit().putString("positions", json.encodeToString(positions)).apply()
    }
    private fun remember() {
        val e = episode ?: return
        if (time <= 2) return
        positions = positions + (e.id to time)
        prefs.edit().putString("positions", json.encodeToString(positions)).apply()
    }
    private fun saveFinished() = prefs.edit().putStringSet("finished", finished).apply()
}
