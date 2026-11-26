package news.piaz.shared

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Several live feeds at once. One is heard at a time; each channel keeps its own volume.
 * A phone shows four at most. Small tiles get small renditions so all feeds fit on one connection.
 */
class LiveWall(context: Context, val sizes: List<Int> = listOf(1, 2, 4)) {
    val most = sizes.last()
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("wall", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var slots by mutableStateOf<List<LiveChannel>>(emptyList()); private set
    var heard by mutableIntStateOf(0); private set
    var muted by mutableStateOf(false); private set
    var full by mutableStateOf<Int?>(null); private set
    var failed by mutableStateOf<Set<Int>>(emptySet()); private set
    var running by mutableStateOf(false); private set
    private var volumes: Map<String, Float> = prefs.getString("volumes", null)?.let { runCatching { Json.decodeFromString<Map<String, Float>>(it) }.getOrNull() } ?: emptyMap()
    var volume by mutableStateOf(1f); private set
    private val retries = mutableMapOf<Int, Int>()
    private val players = mutableMapOf<Int, ExoPlayer>()

    @OptIn(UnstableApi::class)
    fun player(i: Int): ExoPlayer = players.getOrPut(i) {
        ExoPlayer.Builder(app)
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(4_000, 15_000, 1_500, 2_500).build())
            .build().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), i == 0)
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) { recover(i) }
                    override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) retries[i] = 0 }
                })
            }
    }

    fun start(size: Int, first: LiveChannel?, all: List<LiveChannel>) {
        if (all.isEmpty()) return
        running = true; muted = false; full = null; heard = 0
        var order = all
        if (first != null) order = listOf(first) + all.filter { it.id != first.id }
        else prefs.getString("order", null)?.split("\n")?.let { saved -> order = all.sortedBy { saved.indexOf(it.id).let { i -> if (i < 0) 99 else i } } }
        resize(size, order, keep = false)
    }

    fun resize(n: Int, all: List<LiveChannel>, keep: Boolean = true) {
        val size = minOf(n, most)
        full = null
        prefs.edit().putInt("size", size).apply()
        val next = (if (keep) slots.take(size) else emptyList()).toMutableList()
        for (c in all) if (next.size < size && next.none { it.id == c.id }) next += c
        val old = slots
        slots = next
        for (i in 0 until most) {
            if (i < next.size) { if (!keep || old.getOrNull(i)?.id != next[i].id) open(i) else quality(i) } else stop(i)
        }
        heard = heard.coerceIn(0, maxOf(0, next.size - 1))
        apply()
    }

    fun put(c: LiveChannel, i: Int) {
        if (i !in slots.indices) return
        val list = slots.toMutableList()
        val j = list.indexOfFirst { it.id == c.id }
        if (j >= 0) { list[j] = list[i]; list[i] = c; slots = list; open(j) } else { list[i] = c; slots = list }
        open(i); apply()
        prefs.edit().putString("order", slots.joinToString("\n") { it.id }).apply()
    }

    fun hear(i: Int) { heard = i; muted = false; apply() }
    fun toggleMute() { muted = !muted; apply() }
    fun setLevel(v: Float) {
        val c = slots.getOrNull(heard) ?: return
        volumes = volumes + (c.id to v.coerceIn(0f, 1f)); muted = false; apply()
        prefs.edit().putString("volumes", Json.encodeToString(volumes)).apply()
    }
    fun nudge(d: Float) = setLevel(((volume + d) * 10).let { Math.round(it) / 10f })
    fun expand(i: Int?) { full = i; if (i != null) hear(i); slots.indices.forEach(::quality) }
    fun retry(i: Int) { retries[i] = 0; open(i); apply() }
    fun stopAll() {
        running = false
        (0 until most).forEach(::stop)
        slots = emptyList()
    }
    fun suspend() = players.values.forEach { it.pause() }
    fun wake() { if (running) { slots.indices.forEach(::open); apply() } }
    fun release() { players.values.forEach { it.release() }; players.clear() }

    private fun open(i: Int) {
        failed = failed - i
        val p = player(i)
        p.setMediaItem(MediaItem.Builder().setUri(slots[i].stream).setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(8_000).build()).build())
        quality(i)
        p.prepare(); p.play()
    }

    /** Reopens a dropped feed a few times before asking the viewer. */
    private fun recover(i: Int) {
        if (!running || i !in slots.indices) return
        val n = (retries[i] ?: 0) + 1
        retries[i] = n
        if (n > 3) { failed = failed + i; return }
        scope.launch { delay(n * 2000L); if (running && i in slots.indices && retries[i] == n) { open(i); apply() } }
    }

    private fun stop(i: Int) { failed = failed - i; players[i]?.run { stop(); clearMediaItems() } }

    private fun quality(i: Int) {
        val p = players[i] ?: return
        val big = full == i || slots.size == 1
        val small = slots.size > 2
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().apply {
            if (big) clearVideoSizeConstraints().setMaxVideoBitrate(Int.MAX_VALUE)
            else if (small) setMaxVideoSize(960, 540).setMaxVideoBitrate(1_200_000)
            else setMaxVideoSize(1280, 720).setMaxVideoBitrate(3_000_000)
        }.build()
    }

    private fun apply() {
        volume = slots.getOrNull(heard)?.let { volumes[it.id] } ?: 1f
        for ((i, p) in players) {
            p.volume = if (muted || i != heard || i !in slots.indices) 0f else volumes[slots[i].id] ?: 1f
        }
    }
}

/** One live feed's picture. */
@OptIn(UnstableApi::class)
@Composable
fun LiveSurface(player: Player, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                setKeepContentOnPlayerReset(true)
                isFocusable = false
                this.player = player
            }
        },
        update = { if (it.player !== player) it.player = player },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}
