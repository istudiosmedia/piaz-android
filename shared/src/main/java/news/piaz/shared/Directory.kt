package news.piaz.shared

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer

/**
 * Finds sources by name, so nobody has to type a link. YouTube channels come from YouTube's own search page
 * (no API key); live channels from the public iptv-org directory of free streams (news, Persian and Arabic lists).
 */
object Directory {
    data class Found(val id: String, val name: String, val detail: String, val logo: String? = null)

    /** Picks shown before anyone types, so most people add a channel with one press. */
    val suggestedChannels = listOf(
        Found("UCDRIjKy6eZOvKtOELtTdeUA", "Breaking Points", "Independent news show"),
        Found("UCJX8Wn6opMsBng3QWh0dK6g", "Middle East Monitor", "News from West Asia"),
        Found("UCB1u_wJThc3_e5J4VVj7hQQ", "Richard Medhurst", "Independent journalist"),
        Found("UCwlvSJdcMc7iGdR-aducSog", "Geopolitical Economy Report", "Ben Norton"),
        Found("UCoJTOwZxbvq8Al8Qat2zgTA", "Kim Iversen", "Commentary"),
        Found("UCSYCo8uRGF39qDCxF870K5Q", "Owen Jones", "UK journalist"),
        Found("UCmuTmpLY35O3csvhyA6vrkg", "teleSUR English", "Caracas"),
        Found("UCgrNz-aDmcr2uuto8_DL2jg", "CGTN", "Beijing"),
        Found("UCZCFHCU-2eGF7V5ciMkoPHw", "Al Mayadeen", "Beirut, in Arabic"),
        Found("UCfiwzLy-8yKzIbsmZTzxDgw", "Al Jazeera Arabic", "Doha, in Arabic"),
        Found("UCHZk9MrT3DGWmVqdsj5y0EA", "BBC Persian", "In Farsi"),
        Found("UCat6bC0Wrqq9Bcq7EkH_yQw", "Iran International", "In Farsi"),
    )

    // YouTube

    /** Channels whose name matches, most relevant first. Handles and links still work too. */
    suspend fun channels(query: String): List<Found> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        if (q.contains("youtube.com") || q.startsWith("@") || Regex("UC[A-Za-z0-9_-]{22}").containsMatchIn(q)) {
            runCatching { Feeds.resolve(q) }.getOrNull()?.let { return listOf(Found(it.id, it.name, "YouTube channel")) }
        }
        // sp=EgIQAg== limits results to channels.
        val url = "https://www.youtube.com/results?search_query=${URLEncoder.encode(q, "UTF-8")}&sp=EgIQAg%3D%3D"
        val html = runCatching { Net.text(url, browser = true) }.getOrNull() ?: return emptyList()
        val start = html.indexOf("var ytInitialData = ").takeIf { it >= 0 } ?: return emptyList()
        val from = start + "var ytInitialData = ".length
        val end = html.indexOf(";</script>", from).takeIf { it > 0 } ?: return emptyList()
        val root = runCatching { JSONObject(html.substring(from, end)) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<Found>()
        fun text(o: Any?): String {
            val d = o as? JSONObject ?: return ""
            d.optString("simpleText").takeIf { it.isNotEmpty() }?.let { return it }
            val runs = d.optJSONArray("runs") ?: return ""
            return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text") ?: "" }
        }
        fun walk(o: Any?) {
            when (o) {
                is JSONObject -> {
                    o.optJSONObject("channelRenderer")?.let { c ->
                        val id = c.optString("channelId")
                        if (id.isNotEmpty() && out.none { it.id == id }) {
                            val thumbs = c.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                            val raw = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")
                                ?.let { if (it.startsWith("//")) "https:$it" else it }
                            val subs = listOf(text(c.opt("videoCountText")), text(c.opt("subscriberCountText"))).firstOrNull { it.contains("subscriber") } ?: ""
                            out += Found(id, text(c.opt("title")), subs, raw)
                        }
                    }
                    o.keys().forEach { walk(o.opt(it)) }
                }
                is JSONArray -> for (i in 0 until o.length()) walk(o.opt(i))
            }
        }
        walk(root)
        return out.take(20)
    }

    // Live TV

    private val lists = listOf("categories/news", "languages/fas", "languages/ara").map { "https://iptv-org.github.io/iptv/$it.m3u" }
    private val lock = Mutex()
    private var cache: List<LiveChannel> = emptyList()
    private var cachedAt = 0L

    private fun fold(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

    /** The whole directory, fetched once a day. Names are tidied ("Al Jazeera (1080p)" becomes "Al Jazeera"). */
    suspend fun liveDirectory(): List<LiveChannel> = lock.withLock {
        if (cache.isNotEmpty() && System.currentTimeMillis() - cachedAt < 86_400_000) return cache
        val all = coroutineScope {
            lists.map { u -> async { runCatching { LiveChannel.playlist(Net.text(u), limit = 5000) }.getOrDefault(emptyList()) } }.awaitAll().flatten()
        }
        val seen = mutableSetOf<String>()
        val out = all.mapNotNull { c ->
            val name = c.name.replace(Regex("\\s*[(\\[][^)\\]]*[)\\]]"), "").trim()
            if (name.isEmpty() || !seen.add(fold(name))) null
            else LiveChannel(c.stream, name, "Live TV", c.stream, custom = true, logo = c.logo)
        }
        if (out.isNotEmpty()) { cache = out; cachedAt = System.currentTimeMillis() }
        out
    }

    suspend fun live(query: String): List<LiveChannel> {
        val all = liveDirectory()
        val q = fold(query.trim())
        if (q.isEmpty()) return LiveChannels.suggested.mapNotNull { s ->
            all.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: all.firstOrNull { it.name.contains(s, ignoreCase = true) }
        }
        return all.filter { fold(it.name).contains(q) }
            .sortedWith(compareBy<LiveChannel> { !fold(it.name).startsWith(q) }.thenBy { it.name.length })
            .take(40)
    }
}
