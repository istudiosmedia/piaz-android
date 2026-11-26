package news.piaz.shared

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class Story(
    val title: String, val summary: String, val source: String,
    val link: String, val image: String? = null, val date: Long, val body: String? = null,
) { val id get() = link }

/** Editorial order: United States, Iran, Palestine, Lebanon, then the rest. */
data class Topic(val name: String, val pattern: String) {
    private val regex = Regex(pattern)
    fun matches(s: Story) = regex.containsMatchIn(s.title + " " + s.summary)

    companion object {
        val all = listOf(
            Topic("United States", "\\bU\\.?S\\.?\\b|\\bUSA\\b|American|Washington|Trump|Pentagon|Congress|Senat|White House|Rubio|Hegseth|Supreme Court|Democrat|Republican|AIPAC"),
            Topic("Iran", "Iran|Tehran|Hormuz|IRGC|Khamenei"),
            Topic("Palestine", "Palestin|Gaza|West Bank|Rafah|Al-Aqsa|Hamas|Jenin|Nablus|Hebron"),
            Topic("Lebanon", "Leban|Beirut|Hezbollah"),
            Topic("Yemen", "Yemen|Houthi|Hodeida|Sana'a|Ansarallah"),
            Topic("Israel", "Israel|Netanyahu|Tel Aviv|Knesset"),
            Topic("Iraq and Syria", "Iraq|Baghdad|Syria|Damascus|Kurd"),
            Topic("Gulf", "Saudi|Riyadh|Qatar|Doha|Emirat|UAE|Kuwait|Bahrain|Oman\\b"),
            Topic("Turkey", "Turk|Ankara|Erdogan"),
            Topic("Europe", "Europe|Brussels|Britain|\\bUK\\b|France|German|Ukrain|NATO"),
            Topic("Asia Pacific", "China|Beijing|Korea|Japan|India|Pakistan|Philippine|Australia|Taiwan"),
        )

        /** One story appears once, under its first topic in editorial order. */
        fun sections(stories: List<Story>, each: Int): List<Pair<Topic, List<Story>>> {
            val used = mutableSetOf<String>()
            return all.mapNotNull { t ->
                val hit = stories.filter { it.id !in used && t.matches(it) }.take(each)
                hit.forEach { used += it.id }
                if (hit.isEmpty()) null else t to hit
            }
        }
    }
}

@Serializable
data class Video(
    val id: String, val title: String, val channelID: String, val channel: String,
    val about: String, val date: Long, val views: Int = 0, val isShort: Boolean,
) {
    val thumb get() = "https://i.ytimg.com/vi/$id/${if (isShort) "oar2" else "maxresdefault"}.jpg"
    /** Always exists. The sharper sizes above do not for every video. */
    val thumbFallback get() = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
    val link get() = if (isShort) "https://www.youtube.com/shorts/$id" else "https://www.youtube.com/watch?v=$id"
}

@Serializable
data class Episode(
    val id: String, val title: String, val notes: String, val audio: String,
    val date: Long, val duration: Double,
) {
    val isWeekend get() = title.startsWith("Weekend")
    val isOriginal get() = title.startsWith("The Piaz Original")
    val kind: String
        get() {
            if (isWeekend) return "Weekend Edition"
            if (isOriginal) return "The Piaz Original"
            for (e in listOf("Morning", "Afternoon", "Evening")) if (title.contains(": $e")) return "$e Brief"
            return "Daily Brief"
        }
    val headline: String
        get() {
            if ((isWeekend || isOriginal) && title.contains(": ")) return title.substringAfter(": ").split(" — ")[0]
            if (!title.startsWith("Daily Brief")) return title.replace(" — ", ": ")
            return SimpleDateFormat("EEEE, MMMM d", Locale.US).format(Date(date))
        }
    val lead: String
        get() {
            val parts = notes.split("\n\n")
            return (if (parts.size > 1) parts[1] else notes).trim()
        }
}

@Serializable
data class Channel(val id: String, val name: String, val tagline: String, val custom: Boolean = false) {
    companion object {
        val piaz = Channel("UCKB2d15DhTku7ik1_Of-Hvg", "The Piaz", "Our own reporting and analysis")
        val builtIn = listOf(
            piaz,
            Channel("UC8RrBpt3PF8RhauDOdp2G6g", "Jedaal English", "Debate and analysis from West Asia"),
            Channel("UCBMrOkjg3AbvLS5g1MhtlRQ", "Drop Site News", "Investigations by Ryan Grim and Jeremy Scahill"),
            Channel("UCEXR8pRTkE2vFeJePNe9UcQ", "The Grayzone", "Max Blumenthal and Aaron Mate"),
            Channel("UCkF-6h_Zgf9zXNUmUB-MzTw", "Dialogue Works", "Interviews with Nima Alkhorshid"),
            Channel("UCGttrUON87gWfU6dMWm1fcA", "Tucker Carlson", "Interviews and commentary"),
            Channel("UCL0u5uz7KZ9q-pe-VC8TY-w", "Candace Owens", "Commentary"),
            Channel("UCDkEYb-TXJVWLvOokshtlsw", "Judging Freedom", "Judge Andrew Napolitano"),
            Channel("UCZFCDIHTe9HGxtIuVDpBz7g", "Glenn Diesen", "Geopolitics and interviews"),
            Channel("UChzVhAwzGR7hV-4O8ZmBLHg", "Glenn Greenwald", "Journalism and commentary"),
            Channel("UC9jY5IcAA99wX8MZQ9K9r-Q", "The Electronic Intifada", "Reporting on Palestine"),
            Channel("UCzW1oJMWo5BpHys5QGbpJrA", "Al Mayadeen English", "Reporting from Beirut"),
            Channel("UCiinSjWS3E0vtAa3DCvp7iA", "BreakThrough News", "Independent news network"),
            Channel("UCc9UUSOBWC6VCkJUqnh-CLw", "MintPress News", "Independent investigative journalism"),
            Channel("UCzuqE7-t13O4NIDYJfakrhw", "Democracy Now!", "Daily independent news hour"),
            Channel("UCVG72F2Q5yCmLQfctNK6M2A", "Zeteo", "Mehdi Hasan and contributors"),
            Channel("UCR0fZh5SBxxMNYdg0VzRFkg", "Middle East Eye", "Reporting from West Asia and North Africa"),
            Channel("UCHdLVKdAeG6zAeZMGZh91bg", "Neutrality Studies", "Pascal Lottaz"),
            Channel("UCf_HItERkRB3vnkWt2RSOLg", "George Galloway", "Commentary and interviews"),
            Channel("UCeRYN0tYBQVrC2cKsxJjdow", "Double Down News", "Independent video journalism"),
            Channel("UCG29FnXZm4F5U8xpqs1cs1Q", "Empire Files", "Documentaries by Abby Martin"),
            Channel("UCOzMAa6IhV6uwYQATYG_2kg", "Novara Media", "UK independent media"),
            Channel("UCrmm_7RDZJeQzq2-wvmjueg", "The Real News", "News without corporate sponsors"),
            Channel("UCATGf8vE2nWDdnEtBtfcsZw", "Jedaal Farsi", "Debate and analysis, in Farsi"),
        )
    }
}

data class SitePage(val title: String, val path: String) { val url get() = "https://piaz.news/$path" }

object Site {
    val features = listOf(
        SitePage("Situation Room", "situation-room"), SitePage("Cost of War", "cost-of-war"),
        SitePage("AIPAC Tracker", "aipac-tracker"), SitePage("Propaganda Debunked", "propaganda-debunked"),
        SitePage("Gaza Report", "gaza-genocide-report"), SitePage("Gaza History", "gaza-history"),
        SitePage("Originals", "originals"), SitePage("News Wire", "news-wire"),
    )
    val sections = listOf(
        SitePage("United States", "section/us"), SitePage("West Asia", "section/west-asia"),
        SitePage("World", "section/world"), SitePage("All topics", "topics"), SitePage("Article archive", "archive"),
    )
    val about = listOf(
        SitePage("About The Piaz", "about"), SitePage("Editorial standards", "editorial-standards"),
        SitePage("Questions", "faq"), SitePage("Privacy", "privacy"), SitePage("Terms", "terms"),
    )
}

fun Long.ago(): String {
    val s = maxOf(0L, (System.currentTimeMillis() - this) / 1000)
    return when {
        s < 60 -> "Just now"
        s < 3600 -> "${s / 60}m ago"
        s < 86400 -> "${s / 3600}h ago"
        s < 604800 -> "${s / 86400}d ago"
        else -> SimpleDateFormat("MMM d", Locale.US).format(Date(this))
    }
}

fun Double.clock(): String {
    if (!isFinite() || this < 0) return "0:00"
    val t = toInt(); val h = t / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, t / 60 % 60, t % 60) else "%d:%02d".format(t / 60, t % 60)
}

fun todayLine(): String = SimpleDateFormat("EEEE, MMMM d", Locale.US).format(Date())
