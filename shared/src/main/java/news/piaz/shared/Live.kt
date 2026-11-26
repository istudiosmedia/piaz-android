package news.piaz.shared

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI

@Serializable
data class LiveChannel(
    val id: String, val name: String, val about: String, val stream: String,
    val custom: Boolean = false, val logo: String? = null,
) {
    val logos get() = Logos.live(id, logo, stream)

    companion object {
        /** Reads "#EXTINF:-1 ...,Name" followed by an address. Capped so one list cannot flood the app. */
        fun playlist(text: String, limit: Int = 60): List<LiveChannel> {
            val out = mutableListOf<LiveChannel>()
            var title = ""; var logo: String? = null
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.startsWith("#EXTINF")) {
                    logo = Regex("tvg-logo=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                    title = line.substringAfterLast(",", "").trim()
                } else if (line.isNotEmpty() && !line.startsWith("#") && (line.startsWith("http://") || line.startsWith("https://"))) {
                    if (out.none { it.id == line }) {
                        val host = runCatching { URI(line).host }.getOrNull()
                        out += LiveChannel(line, title.ifEmpty { host ?: "Channel" }, "Added by you", line, custom = true, logo = logo)
                    }
                    title = ""; logo = null
                    if (out.size == limit) break
                }
            }
            return out
        }

        /** Opens the address for real: refuses pages that merely look like a stream. */
        suspend fun plays(url: String): Boolean = runCatching {
            val head = Net.text(url).take(4000)
            head.contains("#EXTM3U")
        }.getOrDefault(false)
    }
}

class Problem(text: String) : Exception(text)

private val json = Json { ignoreUnknownKeys = true }

/** Live channels on phone and TV: the built-in lineup plus any the viewer adds. Kept in SharedPreferences. */
class LiveLineup(context: Context) {
    private val prefs = context.getSharedPreferences("live", Context.MODE_PRIVATE)
    var custom by mutableStateOf(load<List<LiveChannel>>("custom") ?: emptyList()); private set
    var hidden by mutableStateOf(load<Set<String>>("hidden") ?: emptySet()); private set
    val all get() = custom + LiveChannels.builtIn
    val live get() = all.filter { it.id !in hidden }

    fun contains(c: LiveChannel) = live.any { it.id == c.id || it.name.equals(c.name, ignoreCase = true) }
    fun add(c: LiveChannel) {
        hidden = hidden - c.id; save("hidden", hidden)
        if (all.any { it.id == c.id }) return
        custom = listOf(c) + custom; save("custom", custom)
    }

    /** A stream or playlist address pasted by hand. Returns what was added. */
    suspend fun add(address: String): String {
        val a = address.trim().let { if (it.contains("://")) it else "https://$it" }
        if (!(a.startsWith("http://") || a.startsWith("https://"))) throw Problem("Use a full stream address.")
        val text = runCatching { Net.text(a) }.getOrNull()
        if (text != null && text.contains("#EXTINF") && !text.contains("#EXT-X-")) {
            val found = LiveChannel.playlist(text).filter { c -> all.none { it.id == c.id } }
            if (found.isEmpty()) throw Problem("No new channels were found in that playlist.")
            found.reversed().forEach(::add)
            return if (found.size == 1) found[0].name else "${found.size} channels"
        }
        if (text == null || !text.contains("#EXTM3U")) throw Problem("That address is not a live stream this device can play.")
        val c = LiveChannel(a, runCatching { URI(a).host }.getOrNull() ?: "Live stream", "Added by you", a, custom = true)
        add(c); return c.name
    }

    fun toggle(c: LiveChannel) { hidden = if (c.id in hidden) hidden - c.id else hidden + c.id; save("hidden", hidden) }
    fun remove(c: LiveChannel) { custom = custom.filter { it.id != c.id }; save("custom", custom) }
    fun removeAllCustom() { custom = emptyList(); save("custom", custom) }

    private inline fun <reified T> save(k: String, v: T) = prefs.edit().putString(k, json.encodeToString(v)).apply()
    private inline fun <reified T> load(k: String): T? = prefs.getString(k, null)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }
}
