package news.piaz.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import news.piaz.shared.Brand
import news.piaz.shared.Episode
import news.piaz.shared.Kicker
import news.piaz.shared.LocalPalette
import news.piaz.shared.Piaz
import news.piaz.shared.R
import news.piaz.shared.clock
import news.piaz.shared.stage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenScreen() {
    val store = Piaz.store
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable { mutableIntStateOf(0) }
    val list = store.episodes.filter {
        when (kind) { 1 -> !it.isWeekend && !it.isOriginal; 2 -> it.isWeekend; 3 -> it.isOriginal; else -> true }
    }
    PullToRefreshBox(store.loading, { scope.launch { store.refresh() } }, Modifier.fillMaxSize().stage()) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                Column(Modifier.col().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Headline("Listen", 36)
                    Muted("The Daily Brief, and the Weekend Edition when there is more to peel back.", size = 15)
                }
                Gap(20)
                store.episodes.firstOrNull()?.let { BriefCard(it, Modifier.col()) }
                Gap(20)
                Strip { items(listOf("All", "Daily Brief", "Weekend", "Originals").withIndex().toList()) { (i, t) -> PiazChip(t, kind == i) { kind = i } } }
                Gap(12)
                if (store.episodes.isEmpty()) {
                    if (store.episodesFailed) Notice("Episodes did not load", "Check your connection and try again.") { scope.launch { store.refresh() } } else Loading()
                } else if (list.isEmpty()) Notice("Nothing here yet", "No editions of this kind in the feed.")
            }
            items(list, key = { it.id }) { Column(Modifier.col()) { EpisodeRow(it) } }
            item { Gap(28) }
        }
    }
}

@Composable
fun EpisodeRow(episode: Episode) {
    val audio = Piaz.audio
    val p = LocalPalette.current
    val mine = audio.episode?.id == episode.id
    val state = when {
        episode.id in audio.finished -> "Played"
        (audio.positions[episode.id] ?: 0.0).let { it > 0 && episode.duration > it } -> "${(episode.duration - audio.positions.getValue(episode.id)).clock()} left"
        else -> episode.duration.clock()
    }
    Row(Modifier.fillMaxWidth().clickable { audio.play(episode) }.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(if (mine) Brand.turquoise else p.card), contentAlignment = Alignment.Center) {
            Icon(if (mine && audio.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = if (mine) Brand.deep else p.accentInk)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Headline(episode.headline, 18)
            Muted(episode.lead, size = 13, maxLines = 2)
            Muted("${episode.kind}  ·  $state", size = 12)
        }
    }
    HorizontalDivider(color = p.rule)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet() {
    val nav = LocalNav.current
    val audio = Piaz.audio
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val e = audio.episode
    var scrub by remember { mutableStateOf<Float?>(null) }
    ModalBottomSheet({ nav.player = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.page) {
        if (e == null) { Notice("Nothing playing", "Pick an edition in Listen."); return@ModalBottomSheet }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.size(250.dp).clip(RoundedCornerShape(20.dp)).background(Brand.navy), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.mark), null, Modifier.padding(54.dp).fillMaxSize(), contentScale = ContentScale.Fit)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Kicker(e.kind); Text(e.headline, style = news.piaz.shared.headline(28, p.ink), textAlign = TextAlign.Center)
            }
            Column {
                val d = maxOf(audio.duration, 1.0).toFloat()
                Slider(
                    value = scrub ?: audio.time.toFloat().coerceIn(0f, d), onValueChange = { scrub = it },
                    onValueChangeFinished = { scrub?.let { audio.seek(it.toDouble()) }; scrub = null }, valueRange = 0f..d,
                    colors = SliderDefaults.colors(thumbColor = Brand.turquoise, activeTrackColor = Brand.turquoise),
                )
                Row { Muted((scrub?.toDouble() ?: audio.time).clock(), size = 12); Box(Modifier.weight(1f)); Muted("-" + maxOf(0.0, audio.duration - audio.time).clock(), size = 12) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(34.dp)) {
                IconButton({ audio.skip(-15.0) }, Modifier.size(52.dp)) { Icon(Icons.Filled.Replay, "Back 15 seconds", tint = p.ink, modifier = Modifier.size(32.dp)) }
                PlayCircle(audio.playing, 82) { audio.toggle() }
                IconButton({ audio.skip(30.0) }, Modifier.size(52.dp)) { Icon(Icons.Filled.Forward30, "Forward 30 seconds", tint = p.ink, modifier = Modifier.size(32.dp)) }
            }
            if (audio.failed) Notice("This edition did not play", "Check your connection and try again.") { audio.retry() }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton({ audio.cycleRate() }) { Text("Speed ${audio.rate.toString().removeSuffix(".0")}x") }
                OutlinedButton({ share(ctx, "https://piaz.news/daily-brief", e.headline) }) { Text("Share") }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Headline("In this edition", 22)
                Muted(e.notes.split("\nSources:")[0].split("\nFull brief")[0], size = 14)
            }
        }
    }
}
