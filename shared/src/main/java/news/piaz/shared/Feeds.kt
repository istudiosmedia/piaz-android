package news.piaz.shared

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/** One HTTP client for the app. No cookies are kept, so nothing identifies the viewer between requests. */
object Net {
    const val BROWSER_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15"
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true).build()

    suspend fun bytes(url: String, browser: Boolean = false): ByteArray = withContext(Dispatchers.IO) {
        val r = Request.Builder().url(url).apply {
            if (browser) header("User-Agent", BROWSER_UA).header("Accept-Language", "en")
        }.build()
        client.newCall(r).execute().use { resp ->
            if (resp.code != 200) throw IOException("HTTP ${resp.code}")
            resp.body?.bytes() ?: throw IOException("Empty")
        }
    }

    suspend fun text(url: String, browser: Boolean = false) = String(bytes(url, browser), Charsets.UTF_8)
}

/** Minimal parser for RSS and Atom. Each item keeps the first text value and first attributes per element. */
class FeedParser private constructor() {
    class Item { val text = mutableMapOf<String, String>(); val attrs = mutableMapOf<String, Map<String, String>>() }
    val items = mutableListOf<Item>()
    var feedTitle = ""; private set
    var feedImage: String? = null; private set

    companion object {
        fun parse(xml: String): FeedParser {
            val f = FeedParser()
            val p = Xml.newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            p.setInput(xml.reader())
            var current: Item? = null
            val buffer = StringBuilder()
            var e = p.eventType
            while (e != XmlPullParser.END_DOCUMENT) {
                when (e) {
                    XmlPullParser.START_TAG -> {
                        buffer.setLength(0)
                        val n = p.name
                        val a = (0 until p.attributeCount).associate { p.getAttributeName(it) to p.getAttributeValue(it) }
                        if (n == "item" || n == "entry") current = Item()
                        else if (current != null) { if (n !in current.attrs && a.isNotEmpty()) current.attrs[n] = a }
                        else if (n == "itunes:image") a["href"]?.let { f.feedImage = it }
                    }
                    XmlPullParser.TEXT, XmlPullParser.CDSECT -> buffer.append(p.text)
                    XmlPullParser.END_TAG -> {
                        val n = p.name
                        val t = buffer.toString().trim()
                        buffer.setLength(0)
                        if (n == "item" || n == "entry") { current?.let { f.items += it }; current = null }
                        else if (current != null) { if (n !in current.text && t.isNotEmpty()) current.text[n] = t }
                        else if (n == "title" && f.feedTitle.isEmpty()) f.feedTitle = t
                        else if (n == "url" && f.feedImage == null) f.feedImage = t
                    }
                }
                e = p.next() // merges text, CDATA and entities
            }
            return f
        }
    }
}

object Feeds {
    private const val BASE = "https://uoaatvrighkyjjrfyjia.supabase.co/functions/v1/"
    private fun rfc(s: String?): Long? = s?.let {
        listOf("EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, dd MMM yyyy HH:mm:ss Z").firstNotNullOfOrNull { f ->
            runCatching { SimpleDateFormat(f, Locale.US).parse(it)?.time }.getOrNull()
        }
    }
    private fun iso(s: String?): Long? = s?.let { runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(it)?.time }.getOrNull() }
    fun clean(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
    fun plain(html: String) = clean(
        html.replace("</p>", "\n\n").replace(Regex("<br[^>]*>"), "\n").replace(Regex("<[^>]+>"), "")
    ).trim()
    private fun seconds(s: String?) = (s ?: "").split(":").fold(0.0) { acc, p -> acc * 60 + (p.toDoubleOrNull() ?: 0.0) }

    suspend fun stories(): List<Story> {
        val xml = FeedParser.parse(Net.text(BASE + "rss-feed"))
        return xml.items.mapNotNull { i ->
            val t = i.text["title"] ?: return@mapNotNull null
            val l = i.text["link"] ?: return@mapNotNull null
            val d = rfc(i.text["pubDate"]) ?: return@mapNotNull null
            Story(
                title = clean(t), summary = clean(i.text["description"] ?: ""),
                source = (i.text["source"] ?: "The Piaz").split(" - ")[0], link = l,
                image = i.attrs["enclosure"]?.get("url")?.let(::clean), date = d,
                body = i.text["content:encoded"]?.let(::plain),
            )
        }
    }

    suspend fun episodes(): List<Episode> {
        val xml = FeedParser.parse(Net.text(BASE + "podcast-rss"))
        return xml.items.mapNotNull { i ->
            val t = i.text["title"] ?: return@mapNotNull null
            val a = i.attrs["enclosure"]?.get("url") ?: return@mapNotNull null
            val d = rfc(i.text["pubDate"]) ?: return@mapNotNull null
            Episode(i.text["guid"] ?: a, t, i.text["description"] ?: "", a, d, seconds(i.text["itunes:duration"]))
        }
    }

    /** YouTube keeps separate long-form (UULF) and Shorts (UUSH) playlists. The mixed feed is the fallback. */
    suspend fun videos(c: Channel): List<Video> = coroutineScope {
        val suffix = c.id.drop(2)
        val long = async { runCatching { playlist("playlist_id=UULF$suffix", c, false) }.getOrNull() }
        val short = async { runCatching { playlist("playlist_id=UUSH$suffix", c, true) }.getOrNull() }
        val l = long.await(); val s = short.await()
        if (l == null && s == null) playlist("channel_id=${c.id}", c, null)
        else ((l ?: emptyList()) + (s ?: emptyList())).sortedByDescending { it.date }
    }

    private suspend fun playlist(q: String, c: Channel, short: Boolean?): List<Video> {
        val xml = FeedParser.parse(Net.text("https://www.youtube.com/feeds/videos.xml?$q"))
        return xml.items.mapNotNull { i ->
            val id = i.text["yt:videoId"] ?: return@mapNotNull null
            val t = i.text["title"] ?: return@mapNotNull null
            val d = iso(i.text["published"]) ?: return@mapNotNull null
            Video(
                id, clean(t), c.id, c.name, i.text["media:description"] ?: "", d,
                i.attrs["media:statistics"]?.get("views")?.toIntOrNull() ?: 0,
                short ?: (i.attrs["link"]?.get("href")?.contains("/shorts/") == true),
            )
        }
    }

    private val idPattern = Regex("UC[A-Za-z0-9_-]{22}")

    /** Accepts a channel ID, @handle or any YouTube channel link. */
    suspend fun resolve(input: String): Channel {
        val s = input.trim()
        val id = idPattern.find(s)?.value ?: run {
            val handle = Regex("@[\\w.\\-]+").find(s)?.value
                ?: if (Regex("^[A-Za-z0-9._-]{3,30}$").matches(s)) "@$s" else throw IOException("Not a channel")
            val html = Net.text("https://www.youtube.com/$handle", browser = true)
            Regex("(?:channel_id=|\"externalId\":\")(UC[A-Za-z0-9_-]{22})").find(html)?.groupValues?.get(1)
        } ?: throw IOException("Channel not found")
        val feed = FeedParser.parse(Net.text("https://www.youtube.com/feeds/videos.xml?channel_id=$id"))
        return Channel(id, feed.feedTitle.ifEmpty { "Channel" }, "Added by you", custom = true)
    }
}
