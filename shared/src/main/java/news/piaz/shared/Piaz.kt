package news.piaz.shared

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** The app's long-lived objects, created once in Application.onCreate. */
object Piaz {
    lateinit var store: Store; private set
    lateinit var lineup: LiveLineup; private set
    lateinit var audio: AudioPlayer; private set
    private var ready = false

    fun init(context: Context) {
        if (ready) return
        ready = true
        val app = context.applicationContext
        Privacy.forget() // anything an earlier session's WebViews left behind
        store = Store(app); lineup = LiveLineup(app); audio = AudioPlayer(app)
    }
}

/** A remote picture on navy, with the mark while it loads. Falls back to a second address if the first fails. */
@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier, fallback: String? = null, contentScale: ContentScale = ContentScale.Crop) {
    var src by remember(url) { mutableStateOf(url) }
    var loaded by remember(url) { mutableStateOf(false) }
    Box(modifier.background(Brand.navy), contentAlignment = Alignment.Center) {
        if (!loaded) Image(painterResource(R.drawable.mark), null, Modifier.size(48.dp).alpha(0.25f), contentScale = ContentScale.Fit)
        if (src != null) AsyncImage(
            model = src, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize(),
            onSuccess = { loaded = true },
            onError = { if (fallback != null && src != fallback) src = fallback },
        )
    }
}
