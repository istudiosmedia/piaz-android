package news.piaz.shared

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** JSON files in the app's private storage. Writes happen off the main thread. */
class Disk(context: Context) {
    val dir: File = File(context.filesDir, "piaz").apply { mkdirs() }
    val json = Json { ignoreUnknownKeys = true }
    inline fun <reified T> load(name: String): T? =
        runCatching { json.decodeFromString<T>(File(dir, "$name.json").readText()) }.getOrNull()
    inline fun <reified T> save(scope: CoroutineScope, name: String, v: T) {
        val text = json.encodeToString(v)
        scope.launch(Dispatchers.IO) {
            val f = File(dir, "$name.json"); val tmp = File(dir, "$name.tmp")
            tmp.writeText(text); tmp.renameTo(f)
        }
    }
}

/** Feeds, saved items and the viewer's channel choices. One per process. */
class Store(context: Context) {
    val disk = Disk(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var stories by mutableStateOf<List<Story>>(emptyList()); private set
    var episodes by mutableStateOf<List<Episode>>(emptyList()); private set
    var videosByChannel by mutableStateOf<Map<String, List<Video>>>(emptyMap()); private set
    var customChannels by mutableStateOf(disk.load<List<Channel>>("channels") ?: emptyList()); private set
    var hidden by mutableStateOf(disk.load<Set<String>>("hidden") ?: emptySet()); private set
    var savedStories by mutableStateOf(disk.load<List<Story>>("savedStories") ?: emptyList()); private set
    var savedVideos by mutableStateOf(disk.load<List<Video>>("savedVideos") ?: emptyList()); private set
    var read by mutableStateOf(disk.load<Set<String>>("read") ?: emptySet()); private set
    var loading by mutableStateOf(false); private set
    var storiesFailed by mutableStateOf(false); private set
    var episodesFailed by mutableStateOf(false); private set
    var videosFailed by mutableStateOf(false); private set
    private var updated = 0L
    private var restored = false

    val allChannels get() = Channel.builtIn + customChannels
    val channels get() = allChannels.filter { it.id !in hidden }
    val videos: List<Video> get() = channels.flatMap { videosByChannel[it.id] ?: emptyList() }.sortedByDescending { it.date }
    val piaz get() = videosByChannel[Channel.piaz.id] ?: emptyList()

    suspend fun refresh() {
        if (loading) return
        loading = true
        kotlinx.coroutines.coroutineScope {
            listOf(async { loadStories() }, async { loadEpisodes() }, async { loadVideos() }).awaitAll()
        }
        loading = false
        if (!storiesFailed) updated = System.currentTimeMillis()
    }

    /** Cached feeds load off the main thread so launch stays smooth. */
    private suspend fun restore() {
        if (restored) return
        restored = true
        val (s, e, v) = withContext(Dispatchers.IO) {
            Triple(disk.load<List<Story>>("stories"), disk.load<List<Episode>>("episodes"), disk.load<Map<String, List<Video>>>("videos"))
        }
        if (stories.isEmpty()) stories = s ?: emptyList()
        if (episodes.isEmpty()) episodes = e ?: emptyList()
        if (videosByChannel.isEmpty()) videosByChannel = v ?: emptyMap()
    }

    suspend fun refreshIfStale() {
        restore()
        if (System.currentTimeMillis() - updated > 300_000) {
            refresh()
            // The network can still be coming up right after launch or wake. Try once more before showing an error.
            if (storiesFailed && videosFailed) { kotlinx.coroutines.delay(3_000); refresh() }
        }
    }

    private suspend fun loadStories() {
        val s = runCatching { Feeds.stories() }.getOrNull()
        if (!s.isNullOrEmpty()) { stories = s; storiesFailed = false; disk.save(scope, "stories", s) } else storiesFailed = true
    }
    private suspend fun loadEpisodes() {
        val e = runCatching { Feeds.episodes() }.getOrNull()
        if (!e.isNullOrEmpty()) { episodes = e; episodesFailed = false; disk.save(scope, "episodes", e) } else episodesFailed = true
    }
    private suspend fun loadVideos() {
        val gate = Semaphore(8)
        val results = withContext(Dispatchers.IO) {
            channels.map { c -> async { gate.withPermit { c.id to runCatching { Feeds.videos(c) }.getOrNull() } } }.awaitAll()
        }
        var ok = false
        val next = videosByChannel.toMutableMap()
        for ((id, v) in results) if (!v.isNullOrEmpty()) { next[id] = v; ok = true }
        videosByChannel = next
        videosFailed = !ok
        disk.save(scope, "videos", next)
    }

    /** A channel typed as a handle, ID or link. */
    suspend fun add(input: String): Channel {
        val c = Feeds.resolve(input)
        add(c)
        return c
    }

    /** A channel picked from search results. */
    suspend fun add(f: Directory.Found) = add(Channel(f.id, f.name, f.detail.ifEmpty { "Added by you" }, custom = true))

    private suspend fun add(c: Channel) {
        if (c.id in hidden) { hidden = hidden - c.id; disk.save(scope, "hidden", hidden) }
        if (allChannels.none { it.id == c.id }) { customChannels = listOf(c) + customChannels; disk.save(scope, "channels", customChannels) }
        runCatching { Feeds.videos(c) }.getOrNull()?.let { videosByChannel = videosByChannel + (c.id to it); disk.save(scope, "videos", videosByChannel) }
    }

    fun has(id: String) = channels.any { it.id == id }
    fun remove(c: Channel) {
        customChannels = customChannels.filter { it.id != c.id }; videosByChannel = videosByChannel - c.id
        disk.save(scope, "channels", customChannels); disk.save(scope, "videos", videosByChannel)
    }
    fun setVisible(c: Channel, on: Boolean) {
        hidden = if (on) hidden - c.id else hidden + c.id
        disk.save(scope, "hidden", hidden)
        if (on && videosByChannel[c.id] == null) scope.launch {
            runCatching { Feeds.videos(c) }.getOrNull()?.let { videosByChannel = videosByChannel + (c.id to it) }
        }
    }

    fun isSaved(s: Story) = savedStories.any { it.id == s.id }
    fun isSaved(v: Video) = savedVideos.any { it.id == v.id }
    fun toggle(s: Story) {
        savedStories = if (isSaved(s)) savedStories.filter { it.id != s.id } else listOf(s) + savedStories
        disk.save(scope, "savedStories", savedStories)
    }
    fun toggle(v: Video) {
        savedVideos = if (isSaved(v)) savedVideos.filter { it.id != v.id } else listOf(v) + savedVideos
        disk.save(scope, "savedVideos", savedVideos)
    }
    fun markRead(id: String) { if (id !in read) { read = read + id; disk.save(scope, "read", read) } }
    fun story(url: String): Story? {
        val path = url.substringAfter("piaz.news").substringBefore("?")
        return (stories + savedStories).firstOrNull { it.link.substringAfter("piaz.news").substringBefore("?") == path }
    }

    fun search(q: String): Triple<List<Story>, List<Video>, List<Episode>> {
        val t = q.trim()
        if (t.length < 2) return Triple(emptyList(), emptyList(), emptyList())
        fun hit(vararg s: String) = s.any { it.contains(t, ignoreCase = true) }
        return Triple(
            (stories + savedStories).distinctBy { it.id }.filter { hit(it.title, it.summary, it.source) }.take(40),
            videos.filter { hit(it.title, it.channel) }.take(40),
            episodes.filter { hit(it.title, it.notes) }.take(20),
        )
    }
}
