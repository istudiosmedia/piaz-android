package news.piaz.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import news.piaz.shared.Brand
import news.piaz.shared.Channel
import news.piaz.shared.LiveWall
import news.piaz.shared.LocalPalette
import news.piaz.shared.Palette
import news.piaz.shared.Piaz
import news.piaz.shared.Privacy
import news.piaz.shared.Video
import kotlinx.coroutines.launch

class PiazApplication : Application() {
    lateinit var wall: LiveWall
    override fun onCreate() {
        super.onCreate()
        Piaz.init(this)
        wall = LiveWall(this, listOf(1, 2, 4)) // a phone shows four at most
    }
}

enum class Tab(val title: String, val icon: ImageVector) {
    Today("Today", Icons.Filled.Newspaper), Shorts("Shorts", Icons.Filled.Slideshow), Watch("Watch", Icons.Filled.LiveTv),
    Listen("Listen", Icons.Filled.Headphones), Explore("Explore", Icons.Filled.GridView),
}

sealed interface Route {
    data class Page(val url: String) : Route
    data class ChannelPage(val channel: Channel) : Route
    data object Saved : Route
    data object Search : Route
    data object Manage : Route
}

enum class AddMode { YouTube, Live }

/** Where the viewer is: one stack per tab, plus what is shown over everything. */
class Nav {
    var tab by mutableStateOf(Tab.Today)
    private val stacks = Tab.entries.associateWith { mutableStateListOf<Route>() }
    val stack get() = stacks.getValue(tab)
    var video by mutableStateOf<Video?>(null)
    var player by mutableStateOf(false)
    var adding by mutableStateOf<AddMode?>(null)
    var wall by mutableStateOf(false)
    fun push(r: Route) { video = null; player = false; stack.add(r) }
    fun pop(): Boolean {
        when {
            wall -> wall = false
            adding != null -> adding = null
            video != null -> video = null
            player -> player = false
            stack.isNotEmpty() -> stack.removeAt(stack.lastIndex)
            tab != Tab.Today -> tab = Tab.Today
            else -> return false
        }
        return true
    }
    /** piaz.news links stay in the app. Accounts and the shop open in the browser. */
    fun open(ctx: Context, url: String) {
        val u = Uri.parse(url)
        if (u.scheme == "piaz") {
            when (u.host) {
                "play" -> { tab = Tab.Listen; player = true }
                else -> Tab.entries.firstOrNull { it.name.equals(u.host, true) }?.let { tab = it }
            }
            return
        }
        if (Privacy.isPiaz(u) && !Privacy.external(u)) push(Route.Page(url)) else Privacy.openOutside(ctx, url)
    }
}

val LocalNav = staticCompositionLocalOf { Nav() }
val LocalWall = staticCompositionLocalOf<LiveWall> { error("No wall") }

class MainActivity : ComponentActivity() {
    private val nav = Nav()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(0, 0), navigationBarStyle = SystemBarStyle.auto(0, 0))
        handle(intent)
        val wall = (application as PiazApplication).wall
        setContent {
            val prefs = remember { getSharedPreferences("ui", MODE_PRIVATE) }
            var appearance by remember { mutableStateOf(prefs.getString("appearance", "auto")!!) }
            val dark = when (appearance) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
            val p = if (dark) Palette.Dark else Palette.Light
            val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
                primary = p.accentInk, onPrimary = Brand.deep, background = p.page, surface = p.card, onSurface = p.ink,
                onBackground = p.ink, surfaceVariant = p.card, onSurfaceVariant = p.muted, outline = p.rule,
                secondaryContainer = Brand.turquoise.copy(alpha = 0.22f), surfaceContainer = p.card,
            )
            CompositionLocalProvider(LocalPalette provides p, LocalNav provides nav, LocalWall provides wall, LocalAppearance provides Appearance(appearance) { appearance = it; prefs.edit().putString("appearance", it).apply() }) {
                MaterialTheme(colorScheme = scheme) { Root() }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }
    private fun handle(i: Intent?) { i?.data?.let { nav.open(this, it.toString()) } }
}

data class Appearance(val value: String, val set: (String) -> Unit)
val LocalAppearance = staticCompositionLocalOf { Appearance("auto") {} }

@Composable
fun Root() {
    val nav = LocalNav.current
    val store = Piaz.store
    val audio = Piaz.audio
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { store.refreshIfStale() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { store.scope.launch { store.refreshIfStale() } }
    BackHandler(enabled = nav.wall || nav.adding != null || nav.video != null || nav.player || nav.stack.isNotEmpty() || nav.tab != Tab.Today) { nav.pop() }
    LaunchedEffect(nav.video) { if (nav.video != null) audio.pause() }
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = p.page,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0), // screens pad for the status bar themselves
            bottomBar = {
                Column {
                    if (audio.episode != null && nav.tab != Tab.Shorts) MiniPlayer()
                    NavigationBar(containerColor = p.card) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = nav.tab == t,
                                onClick = { if (nav.tab == t) nav.stack.clear() else nav.tab = t },
                                icon = { Icon(t.icon, null) }, label = { Text(t.title) },
                                colors = NavigationBarItemDefaults.colors(selectedIconColor = p.accentInk, selectedTextColor = p.accentInk, indicatorColor = Brand.turquoise.copy(alpha = 0.18f)),
                            )
                        }
                    }
                }
            },
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when (val r = nav.stack.lastOrNull()) {
                    is Route.Page -> ReaderScreen(r.url)
                    is Route.ChannelPage -> ChannelScreen(r.channel)
                    Route.Saved -> SavedScreen()
                    Route.Search -> SearchScreen()
                    Route.Manage -> ManageScreen()
                    null -> when (nav.tab) {
                        Tab.Today -> TodayScreen()
                        Tab.Shorts -> ShortsScreen()
                        Tab.Watch -> WatchScreen()
                        Tab.Listen -> ListenScreen()
                        Tab.Explore -> ExploreScreen()
                    }
                }
            }
        }
        nav.video?.let { VideoSheet(it) }
        if (nav.player) PlayerSheet()
        nav.adding?.let { AddSourceSheet(it) }
        if (nav.wall) PhoneWall()
        AnimatedVisibility(splash, exit = fadeOut()) { Splash { splash = false } }
    }
}

