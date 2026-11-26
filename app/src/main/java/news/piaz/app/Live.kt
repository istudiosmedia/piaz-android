package news.piaz.app

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import news.piaz.shared.Brand
import news.piaz.shared.Directory
import news.piaz.shared.FoundLogo
import news.piaz.shared.Kicker
import news.piaz.shared.LiveChannel
import news.piaz.shared.LiveLogo
import news.piaz.shared.LiveSurface
import news.piaz.shared.LocalPalette
import news.piaz.shared.Palette
import news.piaz.shared.Piaz

/** "Live now" at the top of Watch: the lineup as logos, a four-at-once wall, and adding channels in place. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiveStrip() {
    val lineup = Piaz.lineup
    val wall = LocalWall.current
    val nav = LocalNav.current
    val p = LocalPalette.current
    fun open(n: Int, c: LiveChannel? = null) { Piaz.audio.pause(); wall.start(n, c, lineup.live); nav.wall = true }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Kicker("Live now", p.alertInk); Headline("Live TV", 26) }
            OutlinedButton({ open(wall.most) }, enabled = lineup.live.size >= 2) { Icon(Icons.Filled.GridView, null, Modifier.size(18.dp)); HGap(6); Text("Watch ${wall.most}") }
        }
        Strip {
            item {
                Column(Modifier.width(78.dp).clickable(onClickLabel = "Add a live channel") { nav.adding = AddMode.Live }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(13.dp)).background(p.card), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, null, tint = p.accentInk) }
                    Text("Add", color = p.ink, fontSize = 12.sp)
                }
            }
            items(lineup.live, key = { it.id }) { c ->
                var menu by remember { mutableStateOf(false) }
                Box {
                    Column(Modifier.width(78.dp).combinedClickable(onLongClick = { menu = true }) { open(1, c) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiveLogo(c, 64.dp)
                        Text(c.name, color = p.ink, fontSize = 12.sp, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(menu, { menu = false }) {
                        if (c.custom) DropdownMenuItem({ Text("Remove") }, { lineup.remove(c); menu = false })
                        else DropdownMenuItem({ Text("Hide") }, { lineup.toggle(c); menu = false })
                    }
                }
            }
        }
    }
}

/** Up to four live feeds. Tap one to hear it, double-tap to fill the screen, hold for more. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhoneWall() {
    val wall = LocalWall.current
    val nav = LocalNav.current
    val lineup = Piaz.lineup
    val ctx = LocalContext.current
    var picking by remember { mutableStateOf<Int?>(null) }
    BackHandler { if (picking != null) picking = null else nav.wall = false }
    DisposableEffect(Unit) {
        val w = (ctx as? Activity)?.window
        w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); wall.stopAll() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { wall.suspend() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { wall.wake() }
    androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides Palette.Dark) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).clickable(enabled = false) {}) {
            val wide = maxWidth > maxHeight
            val n = wall.slots.size
            val cols = if (n <= 1 || !wide) 1 else 2 // portrait stacks feeds full width
            val rows = maxOf(1, (n + cols - 1) / cols)
            val barH = if (wall.full == null) (if (wide) 64.dp else 150.dp) else 0.dp
            val gap = 8.dp
            val w = minOf((maxWidth - gap * (cols + 1)) / cols, (maxHeight - barH - gap * (rows + 1)) / rows * 16f / 9f)
            val h = w * 9f / 16f
            val full = wall.full
            if (full != null && full in wall.slots.indices) Tile(full, wall.slots[full], Modifier.fillMaxSize()) { picking = it }
            else Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    for (r in 0 until rows) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (c in 0 until cols) {
                            val i = r * cols + c
                            if (i in wall.slots.indices) Tile(i, wall.slots[i], Modifier.size(w, h)) { picking = it } else Spacer(Modifier.size(w, h))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Controls(wide, Modifier.height(barH)) { picking = it }
            }
        }
        picking?.let { slot ->
            ModalBottomSheet({ picking = null }, sheetState = rememberModalBottomSheetState()) {
                Text("Change channel", Modifier.padding(20.dp, 4.dp), fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                LazyColumn {
                    items(lineup.live, key = { it.id }) { c ->
                        Row(Modifier.fillMaxWidth().clickable { wall.put(c, slot); picking = null }.padding(20.dp, 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            LiveLogo(c, 40.dp)
                            Column { Text(c.name); Muted(c.about, size = 12) }
                        }
                    }
                    item { Gap(30) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(i: Int, c: LiveChannel, modifier: Modifier, pick: (Int) -> Unit) {
    val wall = LocalWall.current
    val heard = wall.heard == i
    val isFull = wall.full == i
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (isFull) 0.dp else 10.dp)
    Box(
        modifier.clip(shape).background(Brand.night)
            .border(3.dp, if (heard && wall.full == null && wall.slots.size > 1) Brand.turquoise else Color.Transparent, shape)
            .combinedClickable(
                onClickLabel = "Hear this channel",
                onDoubleClick = { wall.expand(if (wall.full == null) i else null) },
                onLongClick = { menu = true },
            ) { if (i in wall.failed) wall.retry(i) else if (wall.full != null) wall.expand(null) else wall.hear(i) },
    ) {
        LiveSurface(wall.player(i), Modifier.fillMaxSize())
        if (i in wall.failed) Column(Modifier.fillMaxSize().background(Brand.night), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("This channel stopped", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("Tap to try again", color = Color.White, fontSize = 12.sp)
        }
        Row(
            Modifier.align(Alignment.BottomStart).padding(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LiveLogo(c, 22.dp)
            if (heard) Icon(if (wall.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(14.dp), tint = if (wall.muted) Brand.coral else Brand.turquoise)
            Text(c.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text("Hear this channel") }, { wall.hear(i); menu = false })
            DropdownMenuItem({ Text("Full screen") }, { wall.expand(i); menu = false })
            DropdownMenuItem({ Text("Change channel") }, { pick(i); menu = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Controls(wide: Boolean, modifier: Modifier, pick: (Int) -> Unit) {
    val wall = LocalWall.current
    val nav = LocalNav.current
    val lineup = Piaz.lineup
    val top: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WallButton(Icons.Filled.Close, "Close") { nav.wall = false }
            SingleChoiceSegmentedButtonRow(Modifier.width(160.dp)) {
                wall.sizes.forEachIndexed { k, n ->
                    SegmentedButton(wall.slots.size == n, { wall.resize(n, lineup.live) }, SegmentedButtonDefaults.itemShape(k, wall.sizes.size), icon = {},
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = Brand.turquoise, activeContentColor = Brand.deep, inactiveContainerColor = Color.Transparent, inactiveContentColor = Color.White, activeBorderColor = Color.White.copy(alpha = 0.4f), inactiveBorderColor = Color.White.copy(alpha = 0.4f))) { Text("$n") }
                }
            }
            Spacer(Modifier.weight(1f))
            WallButton(Icons.Filled.Add, "Add a live channel") { nav.adding = AddMode.Live }
            WallButton(Icons.Filled.SwapHoriz, "Change channel") { if (wall.heard in wall.slots.indices) pick(wall.heard) }
        }
    }
    val bottom: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton({ wall.toggleMute() }) { Icon(if (wall.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, if (wall.muted) "Unmute" else "Mute", tint = if (wall.muted) Brand.coral else Color.White) }
            Slider(wall.volume, { wall.setLevel(it) }, Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = Brand.turquoise, activeTrackColor = Brand.turquoise))
        }
    }
    if (wide) Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.weight(1f)) { top() }; Box(Modifier.weight(1f)) { bottom() }
    } else Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) { top(); bottom() }
}

@Composable
private fun WallButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) =
    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White)
    }

/** Add a source by name: type a few letters (or nothing) and pick from what comes back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSourceSheet(start: AddMode) {
    val nav = LocalNav.current
    val store = Piaz.store
    val lineup = Piaz.lineup
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(start) }
    var query by remember { mutableStateOf("") }
    var channels by remember { mutableStateOf(Directory.suggestedChannels) }
    var live by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(setOf<String>()) }
    var address by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(mode, query) {
        delay(if (query.isEmpty()) 0 else 450) // waits for typing to pause
        searching = true
        if (mode == AddMode.YouTube) channels = if (query.isEmpty()) Directory.suggestedChannels else Directory.channels(query)
        else live = Directory.live(query)
        searching = false
    }
    ModalBottomSheet({ nav.adding = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.page) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Headline("Add a source", 26)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        AddMode.entries.forEachIndexed { i, m ->
                            SegmentedButton(mode == m, { mode = m }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(if (m == AddMode.YouTube) "YouTube" else "Live TV") }
                        }
                    }
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) },
                        placeholder = { Text(if (mode == AddMode.YouTube) "Channel name" else "Channel name, e.g. Al Jazeera") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Kicker(if (query.isEmpty()) "Suggested" else "Results", modifier = Modifier.weight(1f))
                        if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }
            if (mode == AddMode.YouTube) {
                if (!searching && channels.isEmpty()) item { Muted("No channels found. Try another name.", Modifier.padding(20.dp)) }
                items(channels, key = { it.id }) { f ->
                    val added = store.has(f.id)
                    SourceRow(f.name, f.detail, added, f.id in adding, { FoundLogo(f, 44.dp) }) {
                        adding = adding + f.id; scope.launch { store.add(f); adding = adding - f.id }
                    }
                }
            } else {
                if (!searching && live.isEmpty()) item { Muted("No live channels found. Try another name.", Modifier.padding(20.dp)) }
                items(live, key = { it.id }) { c -> SourceRow(c.name, "", lineup.contains(c), false, { LiveLogo(c, 44.dp) }) { lineup.add(c) } }
                item {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Muted("Streams belong to their broadcasters and come from the public iptv-org directory. Some may stop working.", size = 12)
                        Gap(8)
                        Kicker("Have a link?")
                        OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Stream or playlist address") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                        Button({ scope.launch { note = try { "Added ${lineup.add(address)}.".also { address = "" } } catch (e: Exception) { e.message } } }, enabled = address.isNotBlank()) { Text("Add address") }
                        note?.let { Muted(it, size = 13) }
                    }
                }
            }
            item { Gap(40) }
        }
    }
}

@Composable
private fun SourceRow(name: String, detail: String, added: Boolean, busy: Boolean, logo: @Composable () -> Unit, add: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().clickable(enabled = !added && !busy, onClickLabel = "Add $name", onClick = add).padding(20.dp, 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        logo()
        Column(Modifier.weight(1f)) {
            Text(name, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail.isNotEmpty()) Muted(detail, size = 12, maxLines = 1)
        }
        if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        else Icon(if (added) Icons.Filled.CheckCircle else Icons.Filled.AddCircle, if (added) "$name, added" else "Add $name", Modifier.size(28.dp), tint = if (added) p.muted else p.accentInk)
    }
    HorizontalDivider(Modifier.padding(start = 78.dp), color = p.rule)
}
