package news.piaz.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import news.piaz.shared.Brand
import news.piaz.shared.ChannelAvatar
import news.piaz.shared.LocalPalette
import news.piaz.shared.PiazPage
import news.piaz.shared.Piaz
import news.piaz.shared.Site
import news.piaz.shared.SitePage
import news.piaz.shared.Story
import news.piaz.shared.stage

@Composable
fun ExploreScreen() {
    val store = Piaz.store
    val nav = LocalNav.current
    val appearance = LocalAppearance.current
    val p = LocalPalette.current
    LazyColumn(Modifier.fillMaxSize().stage().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Column(Modifier.col().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Headline("Explore", 36); Gap(10)
                MenuRow("Search", Icons.Filled.Search, null) { nav.push(Route.Search) }
                MenuRow("Saved", Icons.Filled.Bookmarks, store.savedStories.size + store.savedVideos.size) { nav.push(Route.Saved) }
                MenuRow("YouTube channels", Icons.Filled.LiveTv, store.channels.size) { nav.push(Route.Manage) }
                Gap(20)
                Headline("Appearance", 24)
                val options = listOf("auto" to "Auto", "light" to "Light", "dark" to "Dark")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { i, (k, t) ->
                        SegmentedButton(appearance.value == k, { appearance.set(k) }, SegmentedButtonDefaults.itemShape(i, options.size)) { Text(t) }
                    }
                }
                Muted("Auto follows your phone's dark theme setting.", size = 13)
                Gap(20)
                SectionHeader("piaz.news", "Trackers and reports")
            }
        }
        items(Site.features.chunked(2)) { row ->
            Row(Modifier.col().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { pg ->
                    Box(Modifier.weight(1f).heightIn(min = 96.dp).clip(RoundedCornerShape(16.dp)).background(p.card).clickable { nav.push(Route.Page(pg.url)) }.padding(16.dp)) {
                        Headline(pg.title, 19)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { Gap(30); Column(Modifier.col()) { SectionHeader("piaz.news", "Sections"); Gap(8); Site.sections.forEach { LinkRow(it) } } }
        item { Gap(30); Column(Modifier.col()) { SectionHeader("The Piaz", "About"); Gap(8); Site.about.forEach { LinkRow(it) } } }
        item {
            val ctx = LocalContext.current
            Column(Modifier.col().padding(vertical = 24.dp)) {
                Muted("Memberships and the shop open in your browser. Nothing is sold in this app.", size = 13)
                TextButton({ news.piaz.shared.Privacy.openOutside(ctx, "https://piaz.news/support") }) { Text("Support The Piaz", fontWeight = FontWeight.SemiBold) }
                Muted("The Piaz 3.1 · IStudios Media LLC", size = 12)
            }
        }
    }
}

@Composable
private fun MenuRow(title: String, icon: ImageVector, count: Int?, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.card).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = p.accentInk)
        Text(title, Modifier.weight(1f), color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (count != null) Muted("$count", size = 14)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.muted)
    }
}

@Composable
private fun LinkRow(page: SitePage) {
    val nav = LocalNav.current
    Row(Modifier.fillMaxWidth().clickable { nav.push(Route.Page(page.url)) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(page.title, Modifier.weight(1f), color = LocalPalette.current.ink, fontSize = 16.sp)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = LocalPalette.current.muted)
    }
    HorizontalDivider(color = LocalPalette.current.rule)
}

@Composable
fun SearchScreen() {
    val store = Piaz.store
    var q by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val (stories, videos, episodes) = store.search(q)
    Column(Modifier.fillMaxSize().stage()) {
        BackBar("Search")
        OutlinedTextField(q, { q = it }, Modifier.col().focusRequester(focus), placeholder = { Text("Stories, videos and briefs") }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))
        LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (q.trim().length >= 2 && stories.isEmpty() && videos.isEmpty() && episodes.isEmpty()) item { Notice("Nothing found", "Try another word.") }
            if (stories.isNotEmpty()) { item { Gap(20); SectionHeader("Search", "Stories", Modifier.col()) }; items(stories, key = { "s" + it.id }) { Column(Modifier.col()) { StoryRow(it) } } }
            if (videos.isNotEmpty()) { item { Gap(20); SectionHeader("Search", "Videos", Modifier.col()); Gap(12) }; items(videos, key = { "v" + it.id }) { VideoCard(it, Modifier.col().padding(bottom = 20.dp)) } }
            if (episodes.isNotEmpty()) { item { Gap(20); SectionHeader("Search", "Briefs", Modifier.col()) }; items(episodes, key = { "e" + it.id }) { Column(Modifier.col()) { EpisodeRow(it) } } }
            item { Gap(30) }
        }
    }
}

@Composable
fun SavedScreen() {
    val store = Piaz.store
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().stage()) {
        BackBar("Saved")
        Row(Modifier.col(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PiazChip("Stories", tab == 0) { tab = 0 }; PiazChip("Videos", tab == 1) { tab = 1 }
        }
        LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (tab == 0) {
                if (store.savedStories.isEmpty()) item { Notice("Nothing saved yet", "Hold a story to save it for later.") }
                items(store.savedStories, key = { it.id }) { Column(Modifier.col()) { StoryRow(it) } }
            } else {
                if (store.savedVideos.isEmpty()) item { Notice("Nothing saved yet", "Hold a video to save it for later.") }
                items(store.savedVideos, key = { it.id }) { VideoCard(it, Modifier.col().padding(top = 20.dp)) }
            }
            item { Gap(30) }
        }
    }
}

@Composable
fun ManageScreen() {
    val store = Piaz.store
    val nav = LocalNav.current
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().stage()) {
        BackBar("YouTube channels") { TextButton({ nav.adding = AddMode.YouTube }) { Text("Add", fontWeight = FontWeight.SemiBold) } }
        LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Muted("Turn off a channel to hide it from Watch and Shorts.", Modifier.col().padding(bottom = 8.dp), size = 13) }
            items(store.allChannels, key = { it.id }) { c ->
                Row(Modifier.col().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ChannelAvatar(c, 44.dp)
                    Column(Modifier.weight(1f)) {
                        Text(c.name, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Muted(c.tagline, size = 12, maxLines = 1)
                    }
                    if (c.custom) TextButton({ store.remove(c) }) { Text("Remove", color = p.alertInk) }
                    else if (c.id != news.piaz.shared.Channel.piaz.id) Switch(c.id !in store.hidden, { store.setVisible(c, it) }, colors = SwitchDefaults.colors(checkedTrackColor = Brand.turquoise))
                }
            }
            item { Gap(30) }
        }
    }
}

@Composable
fun ReaderScreen(url: String) {
    val store = Piaz.store
    val ctx = LocalContext.current
    val p = LocalPalette.current
    var progress by remember(url) { mutableIntStateOf(0) }
    var title by remember(url) { mutableStateOf("") }
    var page by remember(url) { mutableStateOf(url) }
    var ready by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }
    var attempt by remember(url) { mutableIntStateOf(0) }
    // Feed story when we have it. Older articles are built from the page itself, so anything can be saved.
    val story = store.story(page) ?: if (page.contains("/article/")) {
        val slug = page.substringAfterLast("/").replace("-", " ").replaceFirstChar { it.uppercase() }
        Story(title.split(" | ")[0].ifEmpty { slug }, "", "The Piaz", page, null, System.currentTimeMillis())
    } else null
    LaunchedEffect(story?.id) { story?.let { store.markRead(it.id) } }
    Column(Modifier.fillMaxSize().background(p.page)) {
        BackBar {
            if (story != null) IconButton({ store.toggle(story) }) {
                Icon(if (store.isSaved(story)) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, if (store.isSaved(story)) "Remove from saved" else "Save story", tint = p.accentInk)
            }
            IconButton({ share(ctx, page, title) }) { Icon(Icons.Filled.Share, "Share", tint = p.accentInk) }
        }
        if (progress < 100 && !failed) LinearProgressIndicator(progress = { progress / 100f }, Modifier.fillMaxWidth(), color = Brand.turquoise, trackColor = Color.Transparent, drawStopIndicator = {})
        Box(Modifier.fillMaxSize()) {
            if (failed) Notice("This page did not load", "Check your connection and try again.") { failed = false; attempt++ }
            else androidx.compose.runtime.key(attempt) {
                PiazPage(url, p.dark, Modifier.fillMaxSize()) { pr, t, cur, r, f -> progress = pr; title = t; page = cur; ready = r; failed = f }
            }
            if (!ready && !failed) store.story(url)?.let { s ->
                Column(Modifier.fillMaxSize().background(p.page).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    news.piaz.shared.Kicker(s.source); Headline(s.title, 30); Muted(s.summary, size = 16)
                }
            }
        }
    }
}
