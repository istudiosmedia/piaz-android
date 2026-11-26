package news.piaz.app

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import news.piaz.shared.Brand
import news.piaz.shared.ChannelAvatar
import news.piaz.shared.Episode
import news.piaz.shared.Kicker
import news.piaz.shared.LocalPalette
import news.piaz.shared.Piaz
import news.piaz.shared.R
import news.piaz.shared.RemoteImage
import news.piaz.shared.Story
import news.piaz.shared.Video
import news.piaz.shared.ago
import news.piaz.shared.clock
import news.piaz.shared.headline

@Composable
fun Headline(text: String, size: Int, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, color: Color = LocalPalette.current.ink) =
    Text(text, modifier, style = headline(size, color), maxLines = maxLines, overflow = TextOverflow.Ellipsis)

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, size: Int = 13, maxLines: Int = Int.MAX_VALUE) =
    Text(text, modifier, color = LocalPalette.current.muted, fontSize = size.sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis, lineHeight = (size * 1.35).sp)

@Composable
fun PiazChip(title: String, on: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.heightIn(min = 40.dp).clip(CircleShape).background(if (on) Brand.turquoise else p.card).clickable(onClick = onClick).padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center,
    ) { Text(title, color = if (on) Brand.deep else p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun SectionHeader(kicker: String, title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Kicker(kicker)
            Headline(title, 26)
        }
        if (action != null) TextButton(onClick = onAction) { Text(action, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun Notice(title: String, text: String, modifier: Modifier = Modifier, retry: (() -> Unit)? = null) {
    val p = LocalPalette.current
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Filled.WifiOff, null, tint = p.accentInk, modifier = Modifier.size(36.dp))
        Headline(title, 22)
        Muted(text, size = 14)
        if (retry != null) Button(onClick = retry, colors = ButtonDefaults.buttonColors(containerColor = Brand.turquoise, contentColor = Brand.deep)) { Text("Try again") }
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LocalPalette.current.accentInk) }

/** Sideways row. */
@Composable
fun Strip(content: LazyListScope.() -> Unit) =
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), content = content)

fun share(ctx: android.content.Context, url: String, title: String = "") {
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url).putExtra(Intent.EXTRA_SUBJECT, title), null))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StoryRow(story: Story) {
    val nav = LocalNav.current
    val store = Piaz.store
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().combinedClickable(onLongClick = { menu = true }) { nav.push(Route.Page(story.link)) }.padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(story.title, style = headline(19, if (story.id in store.read) p.muted else p.ink))
                Muted("${story.source}  ·  ${story.date.ago()}", size = 12)
            }
            RemoteImage(story.image, Modifier.size(104.dp, 84.dp).clip(RoundedCornerShape(10.dp)))
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text(if (store.isSaved(story)) "Remove from saved" else "Save") }, { store.toggle(story); menu = false })
            DropdownMenuItem({ Text("Share") }, { share(ctx, story.link, story.title); menu = false })
        }
    }
    HorizontalDivider(color = p.rule)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(video: Video, modifier: Modifier = Modifier, showChannel: Boolean = true) {
    val nav = LocalNav.current
    val p = LocalPalette.current
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(Modifier.combinedClickable(onLongClick = { menu = true }) { nav.video = video }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RemoteImage(video.thumb, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)), fallback = video.thumbFallback)
            if (showChannel) ChannelAvatar(video, 34.dp)
            Text(video.title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PlayArrow, null, tint = p.accentInk, modifier = Modifier.size(14.dp))
                Muted("  " + (if (showChannel) video.channel + "  ·  " else "") + video.date.ago(), size = 12, maxLines = 1)
            }
        }
        VideoMenu(video, menu) { menu = false }
    }
}

@Composable
fun VideoMenu(video: Video, open: Boolean, close: () -> Unit) {
    val store = Piaz.store
    val ctx = LocalContext.current
    DropdownMenu(open, close) {
        DropdownMenuItem({ Text(if (store.isSaved(video)) "Remove from saved" else "Save") }, { store.toggle(video); close() })
        DropdownMenuItem({ Text("Share") }, { share(ctx, video.link, video.title); close() })
        DropdownMenuItem({ Text("Open in YouTube") }, { news.piaz.shared.Privacy.openOutside(ctx, video.link); close() })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortCard(video: Video) {
    val nav = LocalNav.current
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.size(148.dp, 246.dp).clip(RoundedCornerShape(14.dp)).combinedClickable(onLongClick = { menu = true }) { nav.video = video }) {
        RemoteImage(video.thumb, Modifier.fillMaxSize(), fallback = video.thumbFallback)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Brand.deep.copy(alpha = 0.92f))))
        Text(video.title, Modifier.align(Alignment.BottomStart).padding(11.dp), color = Brand.paper, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = TextOverflow.Ellipsis)
        VideoMenu(video, menu) { menu = false }
    }
}

@Composable
fun PlayCircle(playing: Boolean, size: Int = 58, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Brand.turquoise).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play", tint = Brand.deep, modifier = Modifier.size((size * 0.48).dp))
    }
}

@Composable
fun BriefCard(episode: Episode, modifier: Modifier = Modifier) {
    val audio = Piaz.audio
    val nav = LocalNav.current
    val p = LocalPalette.current
    val on = audio.episode?.id == episode.id && audio.playing
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PlayCircle(on) { audio.play(episode) }
        Column(Modifier.weight(1f).clickable { if (audio.episode?.id != episode.id) audio.play(episode); nav.player = true }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Kicker(episode.kind)
            Headline(episode.headline, 20)
            Muted(episode.lead, size = 13, maxLines = 2)
        }
        Muted(episode.duration.clock(), size = 12)
    }
}

@Composable
fun MiniPlayer() {
    val audio = Piaz.audio
    val nav = LocalNav.current
    val p = LocalPalette.current
    val e = audio.episode ?: return
    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(p.card)) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.weight(1f).clickable { nav.player = true }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                news.piaz.shared.BrandMark(34.dp)
                Column {
                    Text(e.headline, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (audio.failed) "Could not play. Tap for options." else e.kind, color = if (audio.failed) p.alertInk else p.muted, fontSize = 12.sp, maxLines = 1)
                }
            }
            IconButton({ audio.toggle() }) { Icon(if (audio.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (audio.playing) "Pause" else "Play", tint = p.ink) }
            IconButton({ audio.close() }) { Icon(Icons.Filled.Close, "Close player", tint = p.muted) }
        }
        LinearProgressIndicator(
            progress = { if (audio.duration > 0) (audio.time / audio.duration).toFloat().coerceIn(0f, 1f) else 0f },
            Modifier.fillMaxWidth().height(2.dp), color = Brand.turquoise, trackColor = Color.Transparent, drawStopIndicator = {},
        )
    }
}

/** The Peel: the mark draws on from the base upward, then the wordmark tracks in. */
@Composable
fun Splash(done: () -> Unit) {
    val reveal = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val ease = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(600, easing = ease))
    }
    LaunchedEffect(Unit) { delay(350); word.animateTo(1f, tween(450, easing = ease)) }
    LaunchedEffect(Unit) { delay(1300); done() }
    Box(Modifier.fillMaxSize().background(Brand.navy).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Image(painterResource(R.drawable.mark), "The Piaz", Modifier.height(124.dp).graphicsLayer {
                clip = true
                shape = object : androidx.compose.ui.graphics.Shape {
                    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density) =
                        androidx.compose.ui.graphics.Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, size.height * (1 - reveal.value), size.width, size.height))
                }
            }, contentScale = ContentScale.Fit)
            Column(Modifier.alpha(word.value), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("THE PIAZ", style = headline(34, Brand.paper).copy(letterSpacing = (12 - 9 * word.value).sp))
                Kicker("Peeling back the headlines", Brand.turquoise)
            }
        }
    }
}

/** Screen title row used by tabs that have one. */
@Composable
fun TitleRow(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Headline(title, 36, Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.size(46.dp).clip(CircleShape).background(p.card).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = p.accentInk)
    }
}

@Composable fun Gap(h: Int) = Spacer(Modifier.height(h.dp))
@Composable fun HGap(w: Int) = Spacer(Modifier.width(w.dp))
