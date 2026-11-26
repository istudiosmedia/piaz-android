package news.piaz.tv

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import news.piaz.shared.Brand
import news.piaz.shared.BrandMark
import news.piaz.shared.Kicker
import news.piaz.shared.LiveChannel
import news.piaz.shared.LiveWall
import news.piaz.shared.LocalPalette
import news.piaz.shared.Palette
import news.piaz.shared.Piaz
import news.piaz.shared.Story
import news.piaz.shared.Video
import news.piaz.shared.headline
import news.piaz.shared.stage

class PiazTvApplication : Application() {
    lateinit var wall: LiveWall
    override fun onCreate() {
        super.onCreate()
        Piaz.init(this)
        // Four at once. TV boxes have few hardware video decoders, so the Apple TV's six would drop feeds here.
        wall = LiveWall(this, listOf(1, 2, 4))
    }
}

enum class TvTab(val title: String) { Today("Today"), Live("Live"), Watch("Watch"), Listen("Listen"), Sources("My sources") }
enum class Finding { YouTube, Live }

/** What is shown over the tabs. Back closes the top one. */
class TvNav {
    var tab by mutableStateOf(TvTab.Today)
    var story by mutableStateOf<Story?>(null)
    var video by mutableStateOf<Video?>(null)
    var wall by mutableStateOf(false)
    var finding by mutableStateOf<Finding?>(null)
    var nowPlaying by mutableStateOf(false)
    val covered get() = story != null || video != null || wall || finding != null || nowPlaying
    fun back() {
        when {
            video != null -> video = null
            finding != null -> finding = null
            wall -> wall = false
            story != null -> story = null
            nowPlaying -> nowPlaying = false
        }
    }
}

val LocalTvNav = staticCompositionLocalOf { TvNav() }
val LocalTvWall = staticCompositionLocalOf<LiveWall> { error("No wall") }

class TvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wall = (application as PiazTvApplication).wall
        setContent {
            val nav = remember { TvNav() }
            val p = Palette.Dark
            CompositionLocalProvider(LocalPalette provides p, LocalTvNav provides nav, LocalTvWall provides wall) {
                MaterialTheme(colorScheme = darkColorScheme(primary = Brand.turquoise, onPrimary = Brand.deep, surface = Brand.navy, onSurface = Brand.paper, background = Brand.deep)) {
                    TvRoot()
                }
            }
        }
    }
}

@Composable
fun TvRoot() {
    val nav = LocalTvNav.current
    val store = Piaz.store
    LaunchedEffect(Unit) { store.refreshIfStale() }
    BackHandler(nav.covered) { nav.back() }
    Box(Modifier.fillMaxSize().stage()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 58.dp, end = 58.dp, top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                BrandMark(40.dp, tile = false)
                Text("  The Piaz", style = headline(26, Brand.paper))
                Box(Modifier.weight(1f))
                val tabFocus = remember { TvTab.entries.associateWith { FocusRequester() } }
                // Closing an overlay hands focus back to the first tab, and tabs switch on focus.
                // Remember the tab under the overlay and return to it.
                var under by remember { mutableStateOf(nav.tab) }
                LaunchedEffect(nav.covered) {
                    if (nav.covered) under = nav.tab
                    else { nav.tab = under; kotlinx.coroutines.delay(50); runCatching { tabFocus.getValue(under).requestFocus() } }
                }
                TabRow(selectedTabIndex = nav.tab.ordinal) {
                    TvTab.entries.forEach { t ->
                        Tab(selected = nav.tab == t, onFocus = { if (!nav.covered) nav.tab = t }, onClick = { nav.tab = t }, modifier = Modifier.focusRequester(tabFocus.getValue(t))) {
                            Text(t.title, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 16.sp)
                        }
                    }
                }
            }
            Box(Modifier.fillMaxSize()) {
                when (nav.tab) {
                    TvTab.Today -> TvToday()
                    TvTab.Live -> TvLive()
                    TvTab.Watch -> TvWatch()
                    TvTab.Listen -> TvListen()
                    TvTab.Sources -> TvSources()
                }
            }
        }
        nav.story?.let { TvStory(it) }
        if (nav.nowPlaying) TvNowPlaying()
        if (nav.wall) TvWall()
        nav.finding?.let { TvAddSource(it) }
        nav.video?.let { TvVideo(it) }
    }
}

// Shared TV pieces

@Composable
fun TvRow(title: String, kicker: String? = null, content: LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.padding(horizontal = 58.dp)) {
            if (kicker != null) Kicker(kicker, size = 13)
            Text(title, style = headline(28, Brand.paper))
        }
        LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 58.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), content = content)
    }
}

@Composable
fun TvCard(
    title: String, detail: String, width: Int = 300, onClick: () -> Unit, onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier, media: @Composable () -> Unit,
) {
    Column(Modifier.width(width.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(onClick = onClick, onLongClick = onLongClick, modifier = modifier, shape = CardDefaults.shape(RoundedCornerShape(14.dp)),
            colors = CardDefaults.colors(containerColor = Brand.navy)) { media() }
        Text(title, color = Brand.paper, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (detail.isNotEmpty()) Text(detail, color = Brand.paper.copy(alpha = 0.64f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TvStatus(text: String) = Box(Modifier.fillMaxSize().padding(58.dp), contentAlignment = Alignment.TopStart) {
    Text(text, style = headline(34, Brand.paper.copy(alpha = 0.8f)))
}

@Composable
fun Dim(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize().background(Brand.deep)) { content() }

fun LiveChannel.short() = name
val TvMuted = Color(0xFFF3F6FA).copy(alpha = 0.64f)
@Composable fun TvGap(h: Int) = Box(Modifier.size(1.dp, h.dp))
