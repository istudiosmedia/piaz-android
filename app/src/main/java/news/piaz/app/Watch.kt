package news.piaz.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import news.piaz.shared.Brand
import news.piaz.shared.Channel
import news.piaz.shared.ChannelAvatar
import news.piaz.shared.Kicker
import news.piaz.shared.LocalPalette
import news.piaz.shared.Piaz
import news.piaz.shared.Privacy
import news.piaz.shared.RemoteImage
import news.piaz.shared.Video
import news.piaz.shared.YouTubePlayer
import news.piaz.shared.ago
import news.piaz.shared.stage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen() {
    val store = Piaz.store
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val long = store.piaz.filter { !it.isShort }
    val others = store.videos.filter { it.channelID != Channel.piaz.id && !it.isShort }.take(60)
    PullToRefreshBox(store.loading, { scope.launch { store.refresh() } }, Modifier.fillMaxSize().stage()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cols = maxOf(1, (minOf(maxWidth.value, 1180f) / 300f).toInt())
            LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                item {
                    TitleRow("Watch", Modifier.padding(top = 8.dp)) { RoundIcon(Icons.Filled.Add, "Add a channel") { nav.adding = AddMode.YouTube } }
                    Gap(24); LiveStrip(); Gap(24)
                    Strip {
                        items(store.channels, key = { it.id }) { c ->
                            Column(Modifier.width(78.dp).clickable { nav.push(Route.ChannelPage(c)) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChannelAvatar(c)
                                Text(c.name, color = LocalPalette.current.ink, fontSize = 12.sp, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (store.videos.isEmpty()) item { if (store.videosFailed) Notice("Videos did not load", "Check your connection and try again.") { scope.launch { store.refresh() } } else Loading() }
                long.firstOrNull()?.let { first ->
                    item {
                        Gap(30)
                        Column(Modifier.col(1180), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SectionHeader("The Piaz", "Our reporting", action = "All") { nav.push(Route.ChannelPage(Channel.piaz)) }
                            Column(Modifier.clickable { nav.video = first }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                RemoteImage(first.thumb, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)), fallback = first.thumbFallback)
                                Headline(first.title, 26)
                                Muted(first.date.ago(), size = 12)
                            }
                        }
                        Gap(14)
                        Strip { items(long.drop(1).take(10), key = { it.id }) { VideoCard(it, Modifier.width(260.dp), showChannel = false) } }
                    }
                }
                if (others.isNotEmpty()) {
                    item { Gap(30); SectionHeader("Independent media", "New from the channels", Modifier.col(1180)); Gap(16) }
                    items(others.chunked(cols)) { row ->
                        Row(Modifier.col(1180).padding(bottom = 26.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            row.forEach { VideoCard(it, Modifier.weight(1f)) }
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                item { Gap(28) }
            }
        }
    }
}

@Composable
fun BackBar(title: String = "", trailing: @Composable () -> Unit = {}) {
    val nav = LocalNav.current
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton({ nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = p.accentInk) }
        Text(title, Modifier.weight(1f), color = p.ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

@Composable
fun ChannelScreen(channel: Channel) {
    val store = Piaz.store
    val ctx = LocalContext.current
    val all = store.videosByChannel[channel.id] ?: emptyList()
    var shorts by remember(channel.id) { mutableStateOf(all.isNotEmpty() && all.none { !it.isShort }) }
    val list = all.filter { it.isShort == shorts }
    Column(Modifier.fillMaxSize().stage()) {
        BackBar()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cols = if (shorts) maxOf(2, (maxWidth.value / 160f).toInt()) else maxOf(1, (minOf(maxWidth.value, 1180f) / 300f).toInt())
            LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                item {
                    Column(Modifier.col(1180), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ChannelAvatar(channel, 76.dp)
                        Headline(channel.name, 28)
                        Muted(channel.tagline, size = 15)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PiazChip("Videos", !shorts) { shorts = false }
                            PiazChip("Shorts", shorts) { shorts = true }
                            Spacer(Modifier.weight(1f))
                            TextButton({ Privacy.openOutside(ctx, "https://www.youtube.com/channel/${channel.id}?sub_confirmation=1") }) { Text("Subscribe", fontWeight = FontWeight.SemiBold) }
                        }
                        if (list.isEmpty()) Notice("Nothing here yet", "No recent ${if (shorts) "Shorts" else "videos"} from this channel.")
                        Gap(6)
                    }
                }
                items(list.chunked(cols)) { row ->
                    Row(Modifier.col(1180).padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { if (shorts) Box(Modifier.weight(1f)) { ShortCard(it) } else VideoCard(it, Modifier.weight(1f), showChannel = false) }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayError(video: Video, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Column(modifier.background(LocalPalette.current.card).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
        Text("This video only plays on YouTube", color = LocalPalette.current.ink, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Button({ Privacy.openOutside(ctx, video.link) }, colors = ButtonDefaults.buttonColors(containerColor = Brand.turquoise, contentColor = Brand.deep)) { Text("Open in YouTube") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSheet(start: Video) {
    val nav = LocalNav.current
    val store = Piaz.store
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val video = nav.video ?: start
    var error by remember(video.id) { mutableStateOf(false) }
    val more = store.videos.filter { it.channelID == video.channelID && it.id != video.id && it.isShort == video.isShort }.take(8)
    ModalBottomSheet({ nav.video = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.page) {
        BoxWithConstraints {
            val w = minOf(maxWidth.value, 1100f)
            LazyColumn(horizontalAlignment = Alignment.CenterHorizontally) {
                item(key = "player") {
                    val mod = if (video.isShort) Modifier.height(minOf(560f, (w - 40) * 16 / 9).dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(16.dp))
                    else Modifier.width(w.dp).aspectRatio(16f / 9f)
                    Box(mod) { if (error) PlayError(video, Modifier.fillMaxSize()) else YouTubePlayer(video.id, Modifier.fillMaxSize(), onError = { error = true }) }
                }
                item {
                    Column(Modifier.col(760).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ChannelAvatar(video, 46.dp)
                        Kicker("${video.channel}  ·  ${video.date.ago()}")
                        Headline(video.title, 26)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton({ store.toggle(video) }) {
                                Icon(if (store.isSaved(video)) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, null, Modifier.size(18.dp)); HGap(6)
                                Text(if (store.isSaved(video)) "Saved" else "Save")
                            }
                            OutlinedButton({ share(ctx, video.link, video.title) }) { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); HGap(6); Text("Share") }
                            OutlinedButton({ Privacy.openOutside(ctx, video.link) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)); HGap(6); Text("YouTube") }
                        }
                        if (video.about.isNotEmpty()) Expandable(video.about)
                        if (more.isNotEmpty()) Headline("More from ${video.channel}", 22, Modifier.padding(top = 10.dp))
                    }
                }
                items(more, key = { it.id }) { v ->
                    Row(Modifier.col(760).clickable { nav.video = v }.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        RemoteImage(v.thumb, Modifier.size(128.dp, 72.dp).clip(RoundedCornerShape(10.dp)), fallback = v.thumbFallback)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(v.title, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Muted(v.date.ago(), size = 12)
                        }
                    }
                }
                item { Gap(30) }
            }
        }
    }
}

@Composable
fun Expandable(text: String) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Muted(text, size = 14, maxLines = if (open) Int.MAX_VALUE else 4)
        if (text.length > 160) TextButton({ open = !open }) { Text(if (open) "Show less" else "Show more", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun ShortsScreen() {
    val store = Piaz.store
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf(false) }
    val list = (if (all) store.videos else store.piaz).filter { it.isShort }
    val active = nav.tab == Tab.Shorts && nav.video == null && nav.stack.isEmpty() && !nav.wall
    LaunchedEffect(active) { if (active) Piaz.audio.pause() }
    Column(Modifier.fillMaxSize().stage().statusBarsPadding()) {
        Row(Modifier.padding(20.dp, 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PiazChip("The Piaz", !all) { all = false }
            PiazChip("All channels", all) { all = true }
        }
        if (list.isEmpty()) {
            if (store.loading) Loading() else Notice("No Shorts right now", "Check your connection and try again.") { scope.launch { store.refresh() } }
            return@Column
        }
        val pager = rememberPagerState { list.size }
        LaunchedEffect(all) { pager.scrollToPage(0) }
        VerticalPager(pager, Modifier.fillMaxSize(), key = { list[it].id }) { i ->
            ShortPage(list[i], active && pager.settledPage == i)
        }
    }
}

@Composable
private fun ShortPage(video: Video, active: Boolean) {
    val store = Piaz.store
    val ctx = LocalContext.current
    val p = LocalPalette.current
    var error by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxOf(140f, minOf(maxHeight.value - 112, (maxWidth.value - 24) * 16 / 9))
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.padding(top = 4.dp).height(h.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(18.dp))) {
                RemoteImage(video.thumb, Modifier.fillMaxSize(), fallback = video.thumbFallback)
                if (error) PlayError(video, Modifier.fillMaxSize()) else if (active) YouTubePlayer(video.id, Modifier.fillMaxSize(), onError = { error = true })
            }
            Row(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChannelAvatar(video, 42.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Kicker("${video.channel}  ·  ${video.date.ago()}")
                    Text(video.title, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                IconButton({ store.toggle(video) }) { Icon(if (store.isSaved(video)) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, if (store.isSaved(video)) "Remove from saved" else "Save", tint = p.accentInk) }
                IconButton({ share(ctx, video.link, video.title) }) { Icon(Icons.Filled.Share, "Share", tint = p.accentInk) }
            }
        }
    }
}
