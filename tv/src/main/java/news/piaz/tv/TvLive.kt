package news.piaz.tv

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.view.KeyEvent as AndroidKey
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import news.piaz.shared.Brand
import news.piaz.shared.ChannelAvatar
import news.piaz.shared.Directory
import news.piaz.shared.FoundLogo
import news.piaz.shared.Kicker
import news.piaz.shared.LiveChannel
import news.piaz.shared.LiveLogo
import news.piaz.shared.LiveSurface
import news.piaz.shared.Piaz
import news.piaz.shared.headline

@Composable
fun TvLive() {
    val lineup = Piaz.lineup
    val wall = LocalTvWall.current
    val nav = LocalTvNav.current
    fun open(n: Int, c: LiveChannel? = null) { Piaz.audio.pause(); wall.start(n, c, lineup.live); nav.wall = true }
    Column(Modifier.fillMaxSize().padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.padding(horizontal = 58.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f)) {
                Kicker("Live now", Brand.coral, size = 13)
                Text("Live TV", style = headline(44, Brand.paper))
                Text("Watch up to ${wall.most} channels at once. The one you focus is the one you hear.", color = TvMuted, fontSize = 17.sp)
            }
            wall.sizes.drop(1).forEach { n -> Button({ open(n) }, enabled = lineup.live.size >= 2) { Text("Watch $n") } }
        }
        LazyVerticalGrid(GridCells.Adaptive(190.dp), contentPadding = PaddingValues(horizontal = 58.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            item {
                TvCard("Add a channel", "Suggestions and voice search", 190, onClick = { nav.finding = Finding.Live }) {
                    Box(Modifier.size(190.dp, 120.dp), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, null, Modifier.size(52.dp), tint = Brand.turquoise) }
                }
            }
            itemsIndexed(lineup.live, key = { _, c -> c.id }) { _, c ->
                TvCard(c.name, c.about, 190, onClick = { open(1, c) }, onLongClick = { if (c.custom) lineup.remove(c) else lineup.toggle(c) }) {
                    Box(Modifier.size(190.dp, 120.dp), contentAlignment = Alignment.Center) { LiveLogo(c, 88.dp) }
                }
            }
        }
    }
}

/** Up to four live feeds with the remote: move to a feed to hear it, press to fill the screen. */
@Composable
fun TvWall() {
    val wall = LocalTvWall.current
    val nav = LocalTvNav.current
    val lineup = Piaz.lineup
    val ctx = LocalContext.current
    var picking by remember { mutableStateOf<Int?>(null) }
    val first = remember { FocusRequester() }
    DisposableEffect(Unit) {
        val w = (ctx as? Activity)?.window
        w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); wall.stopAll() }
    }
    LaunchedEffect(Unit) { delay(100); runCatching { first.requestFocus() } }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val fullW = maxWidth; val fullH = maxHeight
        val n = wall.slots.size
        val cols = if (n <= 1) 1 else 2
        val rows = maxOf(1, (n + cols - 1) / cols)
        val gap = 12.dp
        val barH = if (wall.full == null) 80.dp else 0.dp
        val w = minOf((maxWidth - gap * (cols + 1)) / cols, (maxHeight - barH - gap * (rows + 1)) / rows * 16f / 9f)
        val h = w * 9f / 16f
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                for (r in 0 until rows) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (c in 0 until cols) {
                        val i = r * cols + c
                        if (i in wall.slots.indices) {
                            val full = wall.full
                            val m = if (full == null) Modifier.size(w, h) else if (full == i) Modifier.size(fullW, fullH) else Modifier.size(0.dp)
                            WallTile(i, wall.slots[i], m.then(if (i == 0) Modifier.focusRequester(first) else Modifier))
                        } else Spacer(Modifier.size(w, h))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (wall.full == null) Row(Modifier.padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                wall.sizes.forEach { k -> OutlinedButton({ wall.resize(k, lineup.live) }) { Text("$k", fontWeight = if (wall.slots.size == k) FontWeight.Black else FontWeight.Normal) } }
                Button({ wall.toggleMute() }) { Icon(if (wall.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, null); Text(if (wall.muted) "  Unmute" else "  Mute") }
                Button({ wall.nudge(-0.1f) }) { Text("Quieter") }
                Button({ wall.nudge(0.1f) }) { Text("Louder") }
                Button({ if (wall.heard in wall.slots.indices) picking = wall.heard }) { Text("Change channel") }
                Button({ nav.finding = Finding.Live }) { Text("Add") }
            }
        }
    }
    picking?.let { slot ->
        Dim {
            Column(Modifier.padding(60.dp)) {
                Text("Change channel", style = headline(44, Brand.paper))
                TvGap(20)
                LazyVerticalGrid(GridCells.Adaptive(190.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    itemsIndexed(lineup.live, key = { _, c -> c.id }) { _, c ->
                        TvCard(c.name, c.about, 190, onClick = { wall.put(c, slot); picking = null }) {
                            Box(Modifier.size(190.dp, 120.dp), contentAlignment = Alignment.Center) { LiveLogo(c, 88.dp) }
                        }
                    }
                }
            }
        }
        androidx.activity.compose.BackHandler { picking = null }
    }
}

@Composable
private fun WallTile(i: Int, c: LiveChannel, modifier: Modifier) {
    val wall = LocalTvWall.current
    val heard = wall.heard == i
    Surface(
        onClick = { if (i in wall.failed) wall.retry(i) else wall.expand(if (wall.full == null) i else null) },
        modifier = modifier.onFocusChanged { if (it.isFocused && wall.heard != i) wall.hear(i) },
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(if (wall.full == i) 0.dp else 10.dp)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = Brand.night, focusedContainerColor = Brand.night),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(4.dp, Brand.turquoise))),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(Modifier.fillMaxSize()) {
            LiveSurface(wall.player(i), Modifier.fillMaxSize())
            if (i in wall.failed) Column(Modifier.fillMaxSize().background(Brand.night), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("This channel stopped", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Press to try again", color = Color.White, fontSize = 13.sp)
            }
            Row(
                Modifier.align(Alignment.BottomStart).padding(12.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LiveLogo(c, 30.dp)
                if (heard) Icon(if (wall.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(18.dp), tint = if (wall.muted) Brand.coral else Brand.turquoise)
                Text(c.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

/** Everything the viewer follows, with one press to show or hide. Adding is the first thing on the screen. */
@Composable
fun TvSources() {
    // Adding sits on the right, under the "My sources" tab, so one press down reaches it.
    val store = Piaz.store
    val lineup = Piaz.lineup
    val nav = LocalTvNav.current
    Row(Modifier.fillMaxSize().padding(horizontal = 58.dp, vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(70.dp)) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Text("My sources", style = headline(36, Brand.paper)); Text("Press to show or hide. Hold to remove one you added.", color = TvMuted) }
            items(store.allChannels, key = { "y" + it.id }) { c ->
                val shown = c.id !in store.hidden
                ListItem(
                    selected = false,
                    onClick = { if (c.id != news.piaz.shared.Channel.piaz.id) store.setVisible(c, !shown) },
                    onLongClick = { if (c.custom) store.remove(c) },
                    headlineContent = { Text(c.name) },
                    supportingContent = { Text(if (c.custom) "YouTube, added by you" else "YouTube") },
                    leadingContent = { ChannelAvatar(c, 48.dp) },
                    trailingContent = { Icon(if (shown) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, if (shown) "Shown" else "Hidden") },
                )
            }
            items(lineup.all, key = { "l" + it.id }) { c ->
                val shown = c.id !in lineup.hidden
                ListItem(
                    selected = false, onClick = { lineup.toggle(c) }, onLongClick = { if (c.custom) lineup.remove(c) },
                    headlineContent = { Text(c.name) },
                    supportingContent = { Text(if (c.custom) "Live, added by you" else "Live") },
                    leadingContent = { LiveLogo(c, 48.dp) },
                    trailingContent = { Icon(if (shown) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, if (shown) "Shown" else "Hidden") },
                )
            }
        }
        Column(Modifier.width(460.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Add a source", style = headline(44, Brand.paper))
            Button({ nav.finding = Finding.YouTube }, Modifier.fillMaxWidth()) { Icon(Icons.Filled.Search, null); Text("  Find a YouTube channel") }
            Button({ nav.finding = Finding.Live }, Modifier.fillMaxWidth()) { Icon(Icons.Filled.Search, null); Text("  Find a live TV channel") }
            Text("Suggestions appear first, so most channels take one press. Hold the microphone button on your remote, or press the microphone on screen, to say a name.", color = TvMuted, fontSize = 16.sp)
        }
    }
}

/**
 * Add a source with the fewest presses: suggestions fill the screen before anything is typed and the first
 * one has focus, so adding a channel is one press. Voice search fills the query; results update as it changes.
 */
@Composable
fun TvAddSource(start: Finding) {
    val nav = LocalTvNav.current
    val store = Piaz.store
    val lineup = Piaz.lineup
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(start) }
    var query by remember { mutableStateOf("") }
    var channels by remember { mutableStateOf(Directory.suggestedChannels) }
    var live by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(setOf<String>()) }
    val firstCard = remember { FocusRequester() }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it }
    }
    val ctx = LocalContext.current
    val speech = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a channel name")
    }
    // Fire TV has no speech recognizer for apps (its mic button goes to Alexa), so the button is hidden there.
    val canListen = remember { speech.resolveActivity(ctx.packageManager) != null }
    fun listen() { if (canListen) voice.launch(speech) }
    LaunchedEffect(mode, query) {
        delay(if (query.isEmpty()) 0 else 500)
        searching = true
        if (mode == Finding.YouTube) channels = if (query.isEmpty()) Directory.suggestedChannels else Directory.channels(query)
        else live = Directory.live(query)
        searching = false
    }
    val count = if (mode == Finding.YouTube) channels.size else live.size
    LaunchedEffect(mode, count > 0) { if (count > 0) { delay(50); runCatching { firstCard.requestFocus() } } }
    Dim {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 60.dp, vertical = 36.dp).onPreviewKeyEvent { ev ->
                // The remote's search or voice key starts listening from anywhere on this screen.
                if (ev.type == KeyEventType.KeyDown && ev.key.nativeKeyCode in setOf(AndroidKey.KEYCODE_SEARCH, AndroidKey.KEYCODE_VOICE_ASSIST)) { listen(); true } else false
            },
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (mode == Finding.YouTube) "Add a YouTube channel" else "Add a live channel", style = headline(40, Brand.paper), modifier = Modifier.weight(1f))
                OutlinedButton({ mode = Finding.YouTube; query = "" }) { Text("YouTube", fontWeight = if (mode == Finding.YouTube) FontWeight.Bold else FontWeight.Normal) }
                OutlinedButton({ mode = Finding.Live; query = "" }) { Text("Live TV", fontWeight = if (mode == Finding.Live) FontWeight.Bold else FontWeight.Normal) }
                Button({ nav.finding = null }) { Text("Done") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (canListen) Button({ listen() }) { Icon(Icons.Filled.Mic, null); Text("  Say a name") }
                SearchField(query, { query = it }, when {
                    !canListen && mode == Finding.YouTube -> "Type a channel name"
                    !canListen -> "Type a name, e.g. Al Jazeera"
                    mode == Finding.YouTube -> "Or type a channel name"
                    else -> "Or type a name, e.g. Al Jazeera"
                }, Modifier.width(560.dp))
                if (searching) Text("Searching…", color = TvMuted)
            }
            Text(
                if (query.isEmpty()) "Suggested. Press one to add it." else "Results for “$query”. Press one to add it.",
                color = TvMuted, fontSize = 16.sp,
            )
            LazyVerticalGrid(GridCells.Adaptive(220.dp), Modifier.weight(1f), contentPadding = PaddingValues(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(30.dp), verticalArrangement = Arrangement.spacedBy(30.dp)) {
                if (mode == Finding.YouTube) itemsIndexed(channels, key = { _, f -> f.id }) { i, f ->
                    val added = store.has(f.id)
                    AddCard(f.name, if (f.id in adding) "Adding…" else if (added) "Added" else f.detail.ifEmpty { "Press to add" }, added, if (i == 0) Modifier.focusRequester(firstCard) else Modifier, { FoundLogo(f, 110.dp) }) {
                        if (!added && f.id !in adding) { adding = adding + f.id; scope.launch { store.add(f); adding = adding - f.id } }
                    }
                } else itemsIndexed(live, key = { _, c -> c.id }) { i, c ->
                    val added = lineup.contains(c)
                    AddCard(c.name, if (added) "Added" else "Live TV", added, if (i == 0) Modifier.focusRequester(firstCard) else Modifier, { LiveLogo(c, 110.dp) }) { if (!added) lineup.add(c) }
                }
            }
            if (!searching && count == 0) Text("Nothing found. Try another name.", color = TvMuted)
            if (mode == Finding.Live) Text("Streams belong to their broadcasters and come from the public iptv-org directory. Some may stop working.", color = TvMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AddCard(name: String, detail: String, added: Boolean, modifier: Modifier, logo: @Composable () -> Unit, add: () -> Unit) {
    Card(onClick = add, modifier = modifier.size(220.dp, 230.dp), shape = CardDefaults.shape(RoundedCornerShape(16.dp)), colors = CardDefaults.colors(containerColor = Brand.navy)) {
        Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
            Box(contentAlignment = Alignment.TopEnd) {
                logo()
                if (added) Box(Modifier.size(32.dp).clip(CircleShape).background(Brand.turquoise), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Check, "Added", tint = Brand.deep) }
            }
            Text(name, color = Brand.paper, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
            Text(detail, color = if (added) Brand.turquoise else TvMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value, onChange, modifier.onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(12.dp)).background(Brand.navy)
            .border(2.dp, if (focused) Brand.turquoise else Color.Transparent, RoundedCornerShape(12.dp)).padding(16.dp),
        singleLine = true, textStyle = TextStyle(color = Brand.paper, fontSize = 18.sp), cursorBrush = SolidColor(Brand.turquoise),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = {}),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, color = TvMuted, fontSize = 18.sp); inner() } },
    )
}
