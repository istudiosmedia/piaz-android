package news.piaz.shared

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

/**
 * Privacy rules shared by every WebView in the app. Matches the tracker list in the iOS app's Web.swift:
 * advertising and analytics requests are answered with nothing, and no cookie or site storage outlives a view.
 */
object Privacy {
    private val blocked = listOf(
        "connect.facebook.net", "facebook.com/tr", "googlesyndication.com", "doubleclick.net",
        "googletagmanager.com", "google-analytics.com", "analytics.google.com", "daily_brief_engagement",
    )
    fun isBlocked(url: String) = blocked.any { url.contains(it) }
    fun empty() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    /** Accounts, payments and the shop belong in the browser, not inside the app. */
    fun external(url: Uri): Boolean {
        val first = url.pathSegments.firstOrNull() ?: ""
        return first in setOf("support", "members", "founding", "auth", "login", "signup", "account", "shop", "store", "advertise", "checkout", "studio", "admin")
    }

    fun isPiaz(url: Uri) = url.host == "piaz.news" || url.host == "www.piaz.news"

    /** The same as a non-persistent website data store: nothing a page stored survives the view. */
    fun forget() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }

    fun openOutside(ctx: Context, url: String) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: ActivityNotFoundException) {}
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(w: WebView, storage: Boolean) {
        if (w.context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = storage
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            setSupportMultipleWindows(true)
            @Suppress("DEPRECATION") saveFormData = false
            setGeolocationEnabled(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, false)
    }
}

/** A handle the TV app uses to drive the YouTube player with the remote. */
class YouTubeControl { var web: WebView? = null
    fun toggle() = web?.evaluateJavascript("window.p&&(p.getPlayerState()==1?p.pauseVideo():p.playVideo())", null)
    fun seek(by: Int) = web?.evaluateJavascript("window.p&&p.seekTo(p.getCurrentTime()+($by),true)", null)
}

/**
 * YouTube's own embedded player (IFrame API on youtube-nocookie.com), unmodified and unobstructed.
 * Runs in a WebView that keeps no cookies or storage once it closes.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayer(id: String, modifier: Modifier = Modifier, autoplay: Boolean = true, control: YouTubeControl? = null, focusable: Boolean = true, onError: () -> Unit = {}) {
    val ctx = LocalContext.current
    val latestError = rememberUpdatedState(onError)
    AndroidView(
        modifier = modifier,
        factory = { c ->
            WebView(c).apply {
                // A WRAP_CONTENT WebView lays pages out at their content height (0 here), which breaks 100% and 100vh.
                layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(0xFF000D1F.toInt())
                // On TV the remote drives the player through YouTubeControl, so the page never takes focus.
                isFocusable = focusable; isFocusableInTouchMode = focusable
                Privacy.configure(this, storage = true)
                addJavascriptInterface(object { @JavascriptInterface fun error(code: String) { post { latestError.value() } } }, "piaz")
                webChromeClient = object : WebChromeClient() {
                    private var custom: View? = null
                    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                        // Links inside the player (the YouTube logo, "Watch on YouTube") open outside the app.
                        val probe = WebView(view.context)
                        probe.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean { Privacy.openOutside(ctx, r.url.toString()); v.destroy(); return true }
                        }
                        (resultMsg?.obj as? WebView.WebViewTransport)?.webView = probe
                        resultMsg?.sendToTarget(); return true
                    }
                    override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) { custom = view }
                    override fun onHideCustomView() { custom = null }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? =
                        if (Privacy.isBlocked(r.url.toString())) Privacy.empty() else null
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                        if (r.isForMainFrame && r.hasGesture()) { Privacy.openOutside(ctx, r.url.toString()); return true }
                        return false
                    }
                }
                control?.web = this
            }
        },
        update = { w ->
            if (w.tag != id) {
                w.tag = id
                val html = """
                    <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="referrer" content="strict-origin-when-cross-origin">
                    <style>html,body{margin:0;height:100%;background:#000D1F;overflow:hidden}iframe,#p{position:absolute;inset:0;width:100%;height:100%;border:0}</style><div id="p"></div>
                    <script>var t=document.createElement('script');t.src='https://www.youtube.com/iframe_api';document.head.appendChild(t);
                    function onYouTubeIframeAPIReady(){window.p=new YT.Player('p',{host:'https://www.youtube-nocookie.com',videoId:'$id',width:'100%',height:'100%',playerVars:{playsinline:1,autoplay:${if (autoplay) 1 else 0},rel:0,origin:'https://piaz.news'},
                    events:{onReady:function(e){${if (autoplay) "e.target.playVideo()" else ""}},onError:function(e){piaz.error(String(e.data))}}})}</script>
                """.trimIndent()
                w.loadDataWithBaseURL("https://piaz.news", html, "text/html", "utf-8", null)
            }
        },
        onRelease = { w ->
            control?.web = null
            w.loadUrl("about:blank"); w.stopLoading(); w.removeJavascriptInterface("piaz"); w.destroy()
            Privacy.forget()
        },
    )
}

/** Removes site chrome the app already provides, and keeps the site in the app's theme. */
private fun themeScript(t: String) = """
(function(){window.__piaz='$t';
window.__piazTheme=function(){var R=document.documentElement,t=window.__piaz,o=t==='dark'?'light':'dark';
if(!R.classList.contains(t)||R.classList.contains(o)){R.classList.remove(o);R.classList.add(t)}
R.style.background=t==='dark'?'#000D1F':'#FFFFFF';};
try{localStorage.setItem('theme',window.__piaz)}catch(e){}
window.__piazTheme();new MutationObserver(window.__piazTheme).observe(document.documentElement,{attributes:true,attributeFilter:['class']});
if(!document.getElementById('piaz-css')){var s=document.createElement('style');s.id='piaz-css';
s.textContent='header.sticky,body>#root footer{display:none!important}.fixed.bottom-32.right-6,[class*="fixed"][class*="bottom-0"][class*="inset-x-0"],[class*="fixed"][class*="bottom-0"][class*="left-0"][class*="right-0"]{display:none!important}article>div.mt-10.pt-8.border-t,article>div.py-3.mb-6.border-y{display:none!important}html.dark body{background:#000D1F!important}main>div[class*="h-[calc(100vh-64px)]"]{height:auto!important;min-height:100vh;overflow:visible!important}main{padding-bottom:120px}';
(document.head||document.documentElement).appendChild(s);}})();
""".trimIndent()

/**
 * A piaz.news page. Other sites, accounts, payments and the shop open in the browser.
 * Trackers are blocked and the page keeps nothing after it closes.
 */
@Composable
fun PiazPage(url: String, dark: Boolean, modifier: Modifier = Modifier, onState: (progress: Int, title: String, current: String, ready: Boolean, failed: Boolean) -> Unit = { _, _, _, _, _ -> }) {
    val ctx = LocalContext.current
    val script = themeScript(if (dark) "dark" else "light")
    DisposableEffect(Unit) { onDispose { Privacy.forget() } }
    AndroidView(
        modifier = modifier,
        factory = { c ->
            WebView(c).apply {
                // A WRAP_CONTENT WebView lays pages out at their content height (0 here), which breaks 100% and 100vh.
                layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(if (dark) 0xFF000D1F.toInt() else 0xFFF3F6FA.toInt())
                Privacy.configure(this, storage = true)
                var progress = 0; var ready = false; var failed = false
                fun report() = onState(progress, title ?: "", this.url ?: url, ready, failed)
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, p: Int) { progress = p; if (p > 30) view.evaluateJavascript(script, null); report() }
                    override fun onReceivedTitle(view: WebView, t: String?) { report() }
                    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                        val href = view.hitTestResult.extra
                        if (href != null) { val u = Uri.parse(href); if (Privacy.isPiaz(u) && !Privacy.external(u)) view.loadUrl(href) else Privacy.openOutside(ctx, href) }
                        return false
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? =
                        if (Privacy.isBlocked(r.url.toString())) Privacy.empty() else null
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                        val u = r.url
                        if (!r.isForMainFrame) return false
                        if (u.scheme == "about" || u.scheme == "blob") return false
                        if (Privacy.isPiaz(u) && !Privacy.external(u)) return false
                        if (u.scheme in setOf("http", "https", "mailto", "tel")) Privacy.openOutside(ctx, u.toString())
                        return true
                    }
                    override fun onPageStarted(v: WebView, u: String?, favicon: Bitmap?) { v.evaluateJavascript(script, null) }
                    override fun doUpdateVisitedHistory(v: WebView, u: String?, isReload: Boolean) {
                        // The site navigates without loading a page; catch account and shop links here too.
                        val uri = Uri.parse(u ?: return)
                        if (Privacy.isPiaz(uri) && Privacy.external(uri)) { Privacy.openOutside(ctx, uri.toString()); if (v.canGoBack()) v.goBack() }
                        report()
                    }
                    override fun onPageFinished(v: WebView, u: String?) { v.evaluateJavascript(script, null); ready = true; report() }
                    override fun onReceivedError(v: WebView, r: WebResourceRequest, e: android.webkit.WebResourceError) {
                        if (r.isForMainFrame) { failed = true; report() }
                    }
                }
                setOnKeyListener { _, key, ev -> if (key == KeyEvent.KEYCODE_BACK && ev.action == KeyEvent.ACTION_UP && canGoBack()) { goBack(); true } else false }
                loadUrl(url)
            }
        },
        onRelease = { w -> w.stopLoading(); w.loadUrl("about:blank"); w.destroy() },
    )
}
