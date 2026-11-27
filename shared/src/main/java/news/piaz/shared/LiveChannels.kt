package news.piaz.shared

/**
 * The built-in live TV lineup, shown on phone and TV before the viewer adds anything.
 *
 * Edit this list to change which channels ship with the app. Each entry needs a short stable `id`
 * (logos in Logos.kt are keyed by it), a display name, one line of detail and a public HLS address.
 * Leaving the list empty is fine: viewers can still add channels from the iptv-org directory.
 *
 * Rights: Google Play can ask for proof that the app may carry each stream. Press TV and RT are under
 * US sanctions, so they are left out of the build until legal sign-off. Their entries stay below; to ship
 * them, set `piaz.shipSanctioned=true` in gradle.properties (or pass -Ppiaz.shipSanctioned=true for one build).
 */
object LiveChannels {
    val SHIP_SANCTIONED = BuildConfig.SHIP_SANCTIONED
    private val sanctioned = setOf("presstv", "rt", "rtar")

    private val all = listOf(
        LiveChannel("presstv", "Press TV", "Tehran, in English", "https://live.presstv.co.uk/hls/presstv.m3u8"),
        LiveChannel("rt", "RT", "Moscow, in English", "https://rt-glb.rttv.com/dvr/rtnews/playlist.m3u8"),
        LiveChannel("aje", "Al Jazeera English", "Doha, in English", "https://live-hls-apps-aje-fa.getaj.net/AJE/index.m3u8"),
        LiveChannel("mayadeen", "Al Mayadeen", "Beirut, in Arabic", "https://mdnlv.cdn.octivid.com/almdn/smil:mpegts.stream.smil/playlist.m3u8"),
        LiveChannel("telesur", "teleSUR English", "Caracas, in English", "https://mblenmain01.telesur.ultrabase.net/mblivev3/hd/playlist.m3u8"),
        LiveChannel("cgtn", "CGTN", "Beijing, in English", "https://english-livebkali.cgtn.com/live/encgtn.m3u8"),
        LiveChannel("trt", "TRT World", "Istanbul, in English", "https://tv-trtworld.medya.trt.com.tr/master.m3u8"),
        LiveChannel("aja", "Al Jazeera", "Doha, in Arabic", "https://live-hls-web-aja.getaj.net/AJA/index.m3u8"),
        LiveChannel("ajm", "Al Jazeera Mubasher", "Doha, in Arabic", "https://live-hls-web-ajm.getaj.net/AJM/index.m3u8"),
        LiveChannel("rtar", "RT Arabic", "Moscow, in Arabic", "https://rt-arb.rttv.com/live/rtarab/playlist.m3u8"),
        LiveChannel("telesures", "teleSUR", "Caracas, in Spanish", "https://mblesmain01.telesur.ultrabase.net/mbliveMain/hd/playlist.m3u8"),
    )

    val builtIn: List<LiveChannel> = if (SHIP_SANCTIONED) all else all.filter { it.id !in sanctioned }

    /** Live channels suggested before anyone types in Add, matched by name in the iptv-org directory. */
    val suggested = listOf(
        "Al Jazeera", "Al Mayadeen", "CGTN", "TRT World", "teleSUR", "France 24", "DW", "Euronews",
        "IRINN", "Iran International", "Manoto", "Al Arabiya", "Sky News Arabia", "NHK World", "Al Araby",
    )
}
