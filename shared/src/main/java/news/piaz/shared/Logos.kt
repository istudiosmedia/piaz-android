package news.piaz.shared

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

/** Where a logo can come from, tried in order. */
sealed interface LogoSource {
    data class Image(val url: String) : LogoSource
    /** Channel ID; the avatar is read from the channel page. */
    data class YouTube(val id: String) : LogoSource
    /** Host name; its touch icon or favicon. */
    data class Site(val host: String) : LogoSource
}

object Logos {
    /** Avatars as published on each channel's own page. If one changes, the channel page is read instead. */
    private val youtube = mapOf(
        "UCKB2d15DhTku7ik1_Of-Hvg" to "cu94wWbMC5jpjGHU5b-K8G0RqCpHTXZBzIVfLhSjIpbmmtKlX6amZ67jtHg-mSad14UbAWrwTw",
        "UC8RrBpt3PF8RhauDOdp2G6g" to "9ANb6gOexFvfB2uKD48kCr04vLleu-j9Rcy-IuII-6fZPoHoQo0zVo0j64RBnBOOmp-rNnOI8w",
        "UCBMrOkjg3AbvLS5g1MhtlRQ" to "rQ1nLR8x_GBE2y32Lofpm0OnJQL4YIG9NhphWcbhXKSANXjltCuRnu3Mu9ck_ibX0KzNYwhLoA",
        "UCEXR8pRTkE2vFeJePNe9UcQ" to "8Hj2_AeWjUVcYLbNCRneAE_Y3QaNZTdCE3njZMK3kVF37HwGudPFe-lC4nNUyGPV8sjoYhMD",
        "UCkF-6h_Zgf9zXNUmUB-MzTw" to "kwSzQ3jlF7LzETwK3ZdqO3vPBn7856oCZOBExjHpbJkd0Z29Et2AlBzULAJyXakjm05VzVR3",
        "UCGttrUON87gWfU6dMWm1fcA" to "EgTy2Y6J2p0M58PAGljz6C473WNIv0v4JKya0-k7xDNDjR8q_J2yxnywMUQH7_cz-vDUk_rT",
        "UCL0u5uz7KZ9q-pe-VC8TY-w" to "pUH6VjIoyvYCoKRegNN_3kr_Q9qz7sXgOkKtlRV0BNQlgrjhYPn1zdAtFuEJsYTgHuGKE1E2",
        "UCDkEYb-TXJVWLvOokshtlsw" to "K7XNEgK1VRHxhtM1Mu-lhVoE4gnfPemt0EH8CoOYcdZ9ZMpiVXvVcnkJz9n_RnOsUjMZ9XxoDw",
        "UCZFCDIHTe9HGxtIuVDpBz7g" to "ytc/AIdro_kN0fAbTOVmWm7tmEZYDEOVXy-46gOjO8kUO6tBx3OBKbTe",
        "UChzVhAwzGR7hV-4O8ZmBLHg" to "fOgsm16Wrl9PWI30magv9EzS3JbWykm7vZJWQfRca_-paoHlL-2qABif3kZUqyCkWKO5i6GaL7k",
        "UC9jY5IcAA99wX8MZQ9K9r-Q" to "ytc/AIdro_mITbo4x_9lb6Wb_kPkjB4YCt1gqoZ4fBM6KUDlqaK6stk",
        "UCzW1oJMWo5BpHys5QGbpJrA" to "4XXrVFVDq2upRwD7ogNHZOlLz4nqUiedgkQaI4CkUCRyiWVA6gHI9BAymrxUhro1OLsVFiLon38",
        "UCiinSjWS3E0vtAa3DCvp7iA" to "rPE7_HlaOgYICv5PxeahutAygMa7pPzljDaKvxg00KR4wHMxyUFjqcCCHPhGB55Rij8t4LEyNjI",
        "UCc9UUSOBWC6VCkJUqnh-CLw" to "26ScX37az4Nnr3BKjnwIGC38s5rkdjc-B0NMSewDcHCIXXXY4Q2bjlqqz8abQk_edAhhhtPTqfc",
        "UCzuqE7-t13O4NIDYJfakrhw" to "ytc/AIdro_k13evyGyej3TcaRYOEUzR4OSYdCEHnHeQipbK0lJZOs5o",
        "UCVG72F2Q5yCmLQfctNK6M2A" to "S_CoUS5cot6jsNE67BsrPFjrnLkot1-CKyxGUXnsqC-iOGBBwYLweJIWtI9UE-j9Klqk46Bp",
        "UCR0fZh5SBxxMNYdg0VzRFkg" to "ytc/AIdro_lRzIchs74TuzdT7g_vmgeoD_KFN5kmLk_I3GlVmBl8Z_g",
        "UCHdLVKdAeG6zAeZMGZh91bg" to "nFQxZSJxDJ15zIw-NtKXrSwJO_JMa4IvP_K__y1FT4_w7_D9TzSBpTvMV2aKlpgtOVCPqoe2",
        "UCf_HItERkRB3vnkWt2RSOLg" to "HQ-6wgx8zL5OV2blT2mhrVb19lrNX13iyqantAAN4u3Tc_tjmnlBgtfYly0Dj6OCLQ87uyPfA48",
        "UCeRYN0tYBQVrC2cKsxJjdow" to "ytc/AIdro_kIawXO2Rrt6iwRAAUTaiwa0qmWVphZvs4SQk1P_DPtPeU",
        "UCG29FnXZm4F5U8xpqs1cs1Q" to "PLP8Vsv5eUkK1bJ-97a4OjPBGRsMiSUz-FY457vSBmhX1eY-L-qzJPHWjqG3QF9z4W5UiJEZ8Q",
        "UCOzMAa6IhV6uwYQATYG_2kg" to "ytc/AIdro_kC_W8W4jnGhKUI3Mg9-d_P5BVC1a6mxTYr98owrEDIIP8",
        "UCrmm_7RDZJeQzq2-wvmjueg" to "XykdDAUjGrcoHl_O8I2UX3uZeqgMbtwiotCG28xS1qZp57WOBdavgQIEBhzDKmeFqlyH0qoR5Q",
        "UCATGf8vE2nWDdnEtBtfcsZw" to "iicmoeYPiFGN1MWm5fGKx_V_Lx-4QTYsrQuQ8K6pM7V1do2388p9tqdHiiWp2fd7eDt5F52gFg",
    )
    private val live = mapOf(
        "aje" to "XsTga3Nsfc1E6ZgC6HfHfzTG_3zhuZleOnsKxSK2aILMjwkkIm-0vdALFaU-yt0Lw07iLtbSifk",
        "aja" to "76leFi_fmRjs0-azxyhbTCBryfo_WfYg7nlItMPBGM9ZTOUxWCli0F3_y4QYR24ec_KfcO_V7aw",
        "ajm" to "h0_bBdVgCAXIPJFnQ4hZtE87cDY_qO7rkDAue8qXdmOFxZ5NaO3AFD1uCUcst-EsBcGJ8zl8EQ",
        "mayadeen" to "wFkQlANf6dfxPwf1hgU1lzELS76BRiz179WVJWBwyLPfLg0NhD9DXedzaayXm_Iycmp2IrNQ",
        "telesur" to "kX_0THpPL-lVPTtUc0uwwwVXoIGSfjRcFezc_uT112hMlSLsaGyCFA0jkv_OTQIrqo3R3jjrlg",
        "cgtn" to "ZswtbrleL4K7ameGvmiWb-FTsBCPpWf9Awwt6rUTQtbKe-sf1MbdvxtCGAqcdp7rksGs0oi1",
        "trt" to "luLQWmGFG4iDC1U_2JlzZ1mquci_sUdfZfFl4eWgBkDpW6tvZT0MEA4c4JebJdi9hCo518q1",
    )
    private val sites = mapOf(
        "presstv" to "https://www.presstv.co.uk/custom/images/logo-app/LOGO_144x144.jpg",
        "rt" to "https://www.rt.com/static/block/touch-icon/apple-touch-icon-144x144-precomposed.png",
        "rtar" to "https://ar.russiatoday.com/static/projects/assets/assets/apple-touch-icon-180x180.png",
        "telesures" to "https://ikona.telesurenglish.net/content/uploads/2023/10/favicon.png.webp",
    )
    private fun avatar(key: String?) = key?.let { listOf(LogoSource.Image("https://yt3.googleusercontent.com/$it=s240-c-k-c0x00ffffff-no-rj")) } ?: emptyList()
    fun channel(id: String): List<LogoSource> = avatar(youtube[id]) + LogoSource.YouTube(id)
    /** A live channel: built-in logo, then the one named in a playlist, then the icon of the stream's site. */
    fun live(id: String, logo: String?, stream: String): List<LogoSource> {
        val out: MutableList<LogoSource> = (avatar(live[id]) + listOfNotNull(sites[id]?.let { LogoSource.Image(it) })).toMutableList()
        if (logo != null) out += LogoSource.Image(logo)
        if (out.isEmpty()) runCatching { URI(stream).host }.getOrNull()?.let { out += LogoSource.Site(it) }
        return out
    }
}

/** Finds, shrinks and keeps logos. They are remembered on disk, so they also show offline. */
object LogoLoader {
    private val memory = LruCache<String, Bitmap>(300)
    private val missed = mutableMapOf<String, Long>()
    private val locks = mutableMapOf<String, Mutex>()

    private fun file(ctx: Context, key: String): File {
        val dir = File(ctx.cacheDir, "logos").apply { mkdirs() }
        var h = -0x340d631b7bdddcdbL // FNV-1a, as on iOS
        for (b in key.toByteArray()) { h = (h xor (b.toLong() and 0xff)) * 1099511628211L }
        return File(dir, java.lang.Long.toHexString(h) + ".png")
    }

    fun cached(key: String): Bitmap? = memory.get(key)

    suspend fun image(ctx: Context, key: String, sources: List<LogoSource>): Bitmap? {
        memory.get(key)?.let { return it }
        val lock = synchronized(locks) { locks.getOrPut(key) { Mutex() } }
        return lock.withLock {
            memory.get(key)?.let { return@withLock it }
            synchronized(missed) { missed[key] }?.let { if (System.currentTimeMillis() - it < 120_000) return@withLock null }
            val img = withContext(Dispatchers.IO) {
                val f = file(ctx, key)
                val saved = if (f.exists()) decode(f.readBytes()) else null
                if (saved != null && System.currentTimeMillis() - f.lastModified() < 14 * 86_400_000L) return@withContext saved
                for (s in sources) {
                    val i = fetch(s)
                    if (i != null) { runCatching { f.outputStream().use { i.compress(Bitmap.CompressFormat.PNG, 100, it) } }; return@withContext i }
                }
                saved
            }
            if (img != null) memory.put(key, img) else synchronized(missed) { missed[key] = System.currentTimeMillis() }
            img
        }
    }

    private suspend fun fetch(s: LogoSource): Bitmap? = when (s) {
        is LogoSource.Image -> runCatching { decode(Net.bytes(s.url.replaceFirst("http://", "https://"))) }.getOrNull()
        is LogoSource.YouTube -> runCatching {
            val h = Net.text("https://www.youtube.com/channel/${s.id}", browser = true)
            val m = Regex("(https://yt3\\.(?:googleusercontent|ggpht)\\.com/[A-Za-z0-9_\\-/]+)=s\\d+").find(h)!!
            fetch(LogoSource.Image(m.groupValues[1] + "=s240-c-k-c0x00ffffff-no-rj"))
        }.getOrNull()
        is LogoSource.Site -> listOf("apple-touch-icon.png", "favicon.ico").firstNotNullOfOrNull { fetch(LogoSource.Image("https://${s.host}/$it")) }
    }

    private fun decode(d: ByteArray): Bitmap? {
        if (d.size > 8_000_000) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(d, 0, d.size, o)
        if (o.outWidth < 24 || o.outHeight < 24) return null
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= 240) sample *= 2
        return BitmapFactory.decodeByteArray(d, 0, d.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

private fun initials(name: String) = name.replace("The ", "").split(" ").filter { it.isNotEmpty() }.take(2).map { it.first() }.joinToString("").uppercase()

/** A source's logo, or its initials while the logo loads or when there is none. */
@Composable
fun SourceLogo(name: String, key: String, sources: List<LogoSource>, size: Dp = 64.dp, round: Boolean = true) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var image by remember(key) { mutableStateOf(LogoLoader.cached(key)) }
    LaunchedEffect(key) { if (image == null) image = LogoLoader.image(ctx.applicationContext, key, sources) }
    val shape = if (round) CircleShape else RoundedCornerShape(size * 0.2f)
    Box(
        Modifier.size(size).clip(shape).background(if (image == null) p.card else Color.White).border(1.dp, p.rule, shape),
        contentAlignment = Alignment.Center,
    ) {
        val i = image
        if (i != null) Image(i.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else BasicText(initials(name), style = TextStyle(fontFamily = Brand.playfair, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp, color = p.accentInk, textAlign = TextAlign.Center))
    }
}

@Composable
fun ChannelAvatar(id: String, name: String, size: Dp = 64.dp) {
    if (id == Channel.piaz.id) {
        Box(Modifier.size(size).clip(CircleShape).background(Brand.navy).border(1.dp, LocalPalette.current.rule, CircleShape), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.mark), null, Modifier.padding(size * 0.2f).fillMaxSize(), contentScale = ContentScale.Fit)
        }
    } else SourceLogo(name, id, Logos.channel(id), size)
}

@Composable fun ChannelAvatar(c: Channel, size: Dp = 64.dp) = ChannelAvatar(c.id, c.name, size)
@Composable fun ChannelAvatar(v: Video, size: Dp = 64.dp) = ChannelAvatar(v.channelID, v.channel, size)
@Composable fun LiveLogo(c: LiveChannel, size: Dp = 64.dp) = SourceLogo(c.name, c.id, c.logos, size, round = false)
@Composable fun FoundLogo(f: Directory.Found, size: Dp = 64.dp) =
    SourceLogo(f.name, f.id, listOfNotNull(f.logo?.let { LogoSource.Image(it) }) + Logos.channel(f.id), size)
