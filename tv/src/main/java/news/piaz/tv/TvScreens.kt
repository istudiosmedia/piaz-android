package news.piaz.tv

import android.view.KeyEvent as AndroidKey
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import news.piaz.shared.Brand
import news.piaz.shared.ChannelAvatar
import news.piaz.shared.Kicker
import news.piaz.shared.Piaz
import news.piaz.shared.RemoteImage
import news.piaz.shared.Story
import news.piaz.shared.Video
import news.piaz.shared.YouTubeControl
import news.piaz.shared.YouTubePlayer
import news.piaz.shared.ago
import news.piaz.shared.clock
import news.piaz.shared.headline

@Composable
fun TvToday() {
    val store = Piaz.store
    val nav = LocalTvNav.current
    if (store.stories.isEmpty()) { TvStatus(if (store.storiesFailed) "The Piaz did not load. Check this TV's connection." else "Loading…"); return }
    LazyColumn(contentPadding = PaddingValues(vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(30.dp)) {
        item {
            TvRow("Top stories", "Today") {
                items(store.stories.take(20), key = { it.id }) { s ->
                    TvCard(s.title, "${s.source}  ·  ${s.date.ago()}", 380, onClick = { nav.story = s }) {
                        RemoteImage(s.image, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    }
                }
            }
        }
        if (store.piaz.isNotEmpty()) item {
            TvRow("Latest videos", "The Piaz") {
                items(store.piaz.filter { !it.isShort }.take(15), key = { it.id }) { v -> VideoTile(v) }
            }
        }
        store.episodes.firstOrNull()?.let { e ->
            item {
                TvRow("Listen", "Daily Brief") {
                    items(store.episodes.take(10), key = { it.id }) { ep ->
                        TvCard(ep.headline, "${ep.kind}  ·  ${ep.duration.clock()}", 300, onClick = { Piaz.audio.play(ep); nav.nowPlaying = true }) {
                            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Brand.navy), contentAlignment = Alignment.Center) {
                                news.piaz.shared.BrandMark(72.dp, tile = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoTile(v: Video, showChannel: Boolean = false) {
    val nav = LocalTvNav.current
    TvCard(v.title, (if (showChannel) v.channel + "  ·  " else "") + v.date.ago(), 340, onClick = { Piaz.audio.pause(); nav.video = v }) {
        RemoteImage(v.thumb, Modifier.fillMaxWidth().aspectRatio(16f / 9f), fallback = v.thumbFallback)
    }
}

@Composable
fun TvWatch() {
    val store = Piaz.store
    val nav = LocalTvNav.current
    LazyColumn(contentPadding = PaddingValues(vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(30.dp)) {
        item {
            TvRow("Channels", "Watch") {
                item {
                    TvCard("Add a channel", "Suggestions and voice search", 160, onClick = { nav.finding = Finding.YouTube }) {
                        Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, null, Modifier.size(56.dp), tint = Brand.turquoise) }
                    }
                }
                items(store.channels, key = { it.id }) { c ->
                    TvCard(c.name, c.tagline, 160, onClick = { (store.videosByChannel[c.id] ?: emptyList()).firstOrNull()?.let { nav.video = it } }) {
                        Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) { ChannelAvatar(c, 120.dp) }
                    }
                }
            }
        }
        if (store.videos.isEmpty()) item { TvStatus(if (store.videosFailed) "Videos did not load." else "Loading…") }
        store.channels.forEach { c ->
            val list = (store.videosByChannel[c.id] ?: emptyList()).filter { !it.isShort }.take(12)
            if (list.isNotEmpty()) item(key = c.id) { TvRow(c.name, c.tagline) { items(list, key = { it.id }) { VideoTile(it) } } }
        }
    }
}

@Composable
fun TvListen() {
    val store = Piaz.store
    val audio = Piaz.audio
    val nav = LocalTvNav.current
    Row(Modifier.fillMaxSize().padding(horizontal = 58.dp, vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(60.dp)) {
        Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Listen", style = headline(52, Brand.paper))
            Text("The Daily Brief, and the Weekend Edition when there is more to peel back.", color = TvMuted, fontSize = 18.sp)
            audio.episode?.let { Button({ nav.nowPlaying = true }) { Text("Now playing: ${it.headline}", maxLines = 1) } }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (store.episodes.isEmpty()) item { Text(if (store.episodesFailed) "Episodes did not load." else "Loading…", color = TvMuted) }
            items(store.episodes, key = { it.id }) { e ->
                val mine = audio.episode?.id == e.id
                ListItem(
                    selected = mine, onClick = { audio.play(e); nav.nowPlaying = true },
                    headlineContent = { Text(e.headline, style = headline(20, Brand.paper)) },
                    supportingContent = { Text("${e.kind}  ·  ${e.duration.clock()}") },
                    leadingContent = { Icon(if (mine && audio.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null) },
                )
            }
        }
    }
}

@Composable
fun TvNowPlaying() {
    val audio = Piaz.audio
    val e = audio.episode ?: return
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Dim {
        Column(Modifier.fillMaxSize().padding(80.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterVertically)) {
            Box(Modifier.size(260.dp).clip(RoundedCornerShape(24.dp)).background(Brand.navy), contentAlignment = Alignment.Center) { news.piaz.shared.BrandMark(150.dp, tile = false) }
            Kicker(e.kind, size = 14)
            Text(e.headline, style = headline(40, Brand.paper))
            Row(Modifier.width(800.dp)) {
                Text(audio.time.clock(), color = TvMuted); Box(Modifier.weight(1f).padding(horizontal = 16.dp).height(4.dp).align(Alignment.CenterVertically).background(Brand.paper.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(if (audio.duration > 0) (audio.time / audio.duration).toFloat().coerceIn(0f, 1f) else 0f).background(Brand.turquoise))
                }
                Text("-" + maxOf(0.0, audio.duration - audio.time).clock(), color = TvMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(30.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ audio.skip(-15.0) }) { Icon(Icons.Filled.Replay, "Back 15 seconds") }
                IconButton({ audio.toggle() }, Modifier.size(80.dp).focusRequester(focus), colors = ButtonDefaults.colors()) {
                    Icon(if (audio.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (audio.playing) "Pause" else "Play", Modifier.size(44.dp))
                }
                IconButton({ audio.skip(30.0) }) { Icon(Icons.Filled.Forward30, "Forward 30 seconds") }
            }
            if (audio.failed) Text("This edition did not play. Press play to try again.", color = Brand.coral)
        }
    }
}

/** A story, read natively with the remote: the text scrolls with up and down. */
@Composable
fun TvStory(story: Story) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Piaz.store.markRead(story.id)
    Dim {
        Row(Modifier.fillMaxSize().padding(horizontal = 80.dp, vertical = 50.dp), horizontalArrangement = Arrangement.spacedBy(60.dp)) {
            Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                RemoteImage(story.image, Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(16.dp)))
                Kicker("${story.source}  ·  ${story.date.ago()}", size = 14)
                Text("Full story with sources at piaz.news", color = TvMuted, fontSize = 15.sp)
            }
            val paragraphs = (story.body ?: story.summary).split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            LazyColumn(Modifier.focusRequester(focus).focusable(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item { Text(story.title, style = headline(40, Brand.paper)) }
                items(paragraphs) { Text(it, color = Brand.paper, fontSize = 21.sp, lineHeight = 32.sp) }
            }
        }
    }
}

/**
 * YouTube's own player, in the app (Android TV has a WebView, unlike tvOS).
 * Center or play/pause toggles; left and right skip ten seconds; back closes.
 */
@Composable
fun TvVideo(video: Video) {
    val control = remember(video.id) { YouTubeControl() }
    var error by remember(video.id) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(video.id) { runCatching { focus.requestFocus() } }
    Box(
        Modifier.fillMaxSize().background(Brand.deep).focusRequester(focus).focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key.nativeKeyCode) {
                    AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER, AndroidKey.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKey.KEYCODE_MEDIA_PLAY, AndroidKey.KEYCODE_MEDIA_PAUSE -> { control.toggle(); true }
                    AndroidKey.KEYCODE_DPAD_RIGHT, AndroidKey.KEYCODE_MEDIA_FAST_FORWARD -> { control.seek(10); true }
                    AndroidKey.KEYCODE_DPAD_LEFT, AndroidKey.KEYCODE_MEDIA_REWIND -> { control.seek(-10); true }
                    else -> false
                }
            },
    ) {
        if (error) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("This video only plays on YouTube", style = headline(32, Brand.paper))
            Text(video.link, color = TvMuted)
        } else YouTubePlayer(video.id, Modifier.fillMaxSize(), control = control, focusable = false, onError = { error = true })
        Row(Modifier.align(Alignment.TopStart).padding(40.dp).clip(CircleShape).background(Brand.deep.copy(alpha = 0.7f)).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChannelAvatar(video, 36.dp)
            Text(video.channel, color = Brand.paper, fontSize = 15.sp)
        }
    }
}
