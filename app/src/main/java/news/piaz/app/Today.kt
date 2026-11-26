package news.piaz.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import news.piaz.shared.BrandMark
import news.piaz.shared.Kicker
import news.piaz.shared.LocalPalette
import news.piaz.shared.Piaz
import news.piaz.shared.RemoteImage
import news.piaz.shared.Story
import news.piaz.shared.Topic
import news.piaz.shared.ago
import news.piaz.shared.stage
import news.piaz.shared.todayLine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen() {
    val store = Piaz.store
    val nav = LocalNav.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var topicName by rememberSaveable { mutableStateOf<String?>(null) }
    val topic = Topic.all.firstOrNull { it.name == topicName }
    val stories = store.stories
    val available = Topic.all.filter { t -> stories.any(t::matches) }
    val filtered = topic?.let { t -> stories.filter(t::matches) } ?: stories
    PullToRefreshBox(store.loading, { scope.launch { store.refresh() } }, Modifier.fillMaxSize().stage()) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                Row(Modifier.widthIn(max = 820.dp).fillMaxWidth().padding(20.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(42.dp); HGap(12)
                    Column(Modifier.weight(1f)) { Headline("The Piaz", 28); Kicker(todayLine()) }
                    RoundIcon(Icons.Filled.Search, "Search") { nav.push(Route.Search) }
                }
            }
            if (stories.isEmpty()) {
                item { if (store.storiesFailed) Notice("Stories did not load", "Check your connection and try again.") { scope.launch { store.refresh() } } else Loading() }
                return@LazyColumn
            }
            if (store.storiesFailed) item { Text("Offline. Showing saved stories.", Modifier.padding(20.dp, 4.dp), color = p.alertInk, fontSize = 13.sp) }
            item {
                Gap(12)
                Strip {
                    item { PiazChip("Top stories", topic == null) { topicName = null } }
                    items(available, key = { it.name }) { t -> PiazChip(t.name, topic == t) { topicName = t.name } }
                }
                Gap(20)
            }
            filtered.firstOrNull()?.let { first -> item { Column(Modifier.col()) { Hero(first) } } }
            if (topic == null) {
                store.episodes.firstOrNull()?.let { e -> item { Gap(24); BriefCard(e, Modifier.col()) } }
                val sections = Topic.sections(filtered.drop(1), 4)
                sections.forEachIndexed { i, (t, list) ->
                    item(key = "h" + t.name) { Gap(30); SectionHeader("Top stories", t.name, Modifier.col(), "See all") { topicName = t.name } }
                    items(list, key = { "s" + it.id }) { Column(Modifier.col()) { StoryRow(it) } }
                    if (i == minOf(1, sections.size - 1)) item { ShortsRail() }
                    if (i == minOf(3, sections.size - 1)) item { Featured() }
                }
                val shown = sections.flatMap { s -> s.second.map { it.id } }.toSet() + (filtered.firstOrNull()?.id ?: "")
                val rest = filtered.filter { it.id !in shown }
                if (rest.isNotEmpty()) {
                    item { Gap(30); SectionHeader("Latest", "More stories", Modifier.col()) }
                    items(rest, key = { "r" + it.id }) { Column(Modifier.col()) { StoryRow(it) } }
                }
            } else items(filtered.drop(1), key = { "f" + it.id }) { Column(Modifier.col()) { StoryRow(it) } }
            item { Gap(28) }
        }
    }
}

fun Modifier.col(max: Int = 820) = this.widthIn(max = max.dp).fillMaxWidth().padding(horizontal = 20.dp)

@Composable
fun Hero(story: Story) {
    val nav = LocalNav.current
    val p = LocalPalette.current
    Column(Modifier.clickable { nav.push(Route.Page(story.link)) }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RemoteImage(story.image, Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(16.dp)))
        Kicker("${story.source}  ·  ${story.date.ago()}")
        Headline(story.title, 32)
        Muted(story.summary, size = 16, maxLines = 3)
    }
}

@Composable
private fun ShortsRail() {
    val nav = LocalNav.current
    val shorts = Piaz.store.piaz.filter { it.isShort }
    if (shorts.isEmpty()) return
    Column {
        Gap(30)
        SectionHeader("The Piaz", "Shorts", Modifier.col(), "See all") { nav.tab = Tab.Shorts }
        Gap(14)
        Strip { items(shorts.take(10), key = { it.id }) { ShortCard(it) } }
    }
}

@Composable
private fun Featured() {
    val nav = LocalNav.current
    val v = Piaz.store.piaz.filter { !it.isShort }
    if (v.isEmpty()) return
    Column {
        Gap(30)
        SectionHeader("The Piaz", "Interviews and explainers", Modifier.col(), "Watch") { nav.tab = Tab.Watch }
        Gap(14)
        Strip { items(v.take(8), key = { it.id }) { VideoCard(it, Modifier.width(280.dp), showChannel = false) } }
    }
}
