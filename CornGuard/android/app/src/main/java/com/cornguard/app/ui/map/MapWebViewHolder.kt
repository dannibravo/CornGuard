package com.cornguard.app.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.cornguard.app.BuildConfig
import com.cornguard.app.data.model.MapOccurrence
import com.cornguard.app.data.model.NearbyReportSummary
import org.json.JSONObject

/**
 * Keeps the outbreak map's WebView (and the Leaflet page in it) alive between visits to the Map
 * tab. Rebuilding it on every tab switch meant a new Chromium view, re-parsing Leaflet and the 130KB
 * boundary file, and waiting for the stats again. Now a return to the tab re-attaches a ready page.
 *
 * The WebView is built on a [MutableContextWrapper] whose base is the current Activity while
 * attached and the application context while parked, so a parked map never holds a dead Activity.
 * If its renderer dies, the WebView is dropped and the next [attach] builds a fresh one.
 *
 * Also keeps the map tab's own state ([selectedArea], [diseaseFilter], [sheet]) so the screen comes
 * back as it was left. [clearState] on sign-out.
 */
object MapWebViewHolder {

    /** The map page's callbacks, delivered on the main thread to the attached [MapFragment]. */
    interface Listener {
        fun onPageReady()
        fun onAreaSelected(area: Pair<String, String>)
        fun onRendererGone()
    }

    /** The "Your area" sheet lists, cached per signed-in user. */
    data class SheetCache(
        val uid: String,
        val barangay: String,
        val municipality: String,
        val occurrences: List<MapOccurrence>,
        val nearby: List<NearbyReportSummary>
    )

    private const val BRIDGE_NAME = "CornGuardMap"
    private const val MAP_URL = "https://appassets.androidplatform.net/assets/map/outbreak-map.html"

    private val main = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var contextWrapper: MutableContextWrapper? = null
    private var listener: Listener? = null

    /** Leaflet and the boundaries are loaded in the current [webView]. */
    var pageReady = false
        private set

    /** The barangay in the sheet (tapped, else the farmer's own); survives tab switches. */
    var selectedArea: Pair<String, String>? = null
    /** null = all diseases; the page keeps the same filter, so the pills must match it. */
    var diseaseFilter: String? = null
    var sheet: SheetCache? = null

    /** Puts the map's WebView into [container], creating it (and loading the page) if needed. */
    fun attach(container: ViewGroup, newListener: Listener): WebView {
        val view = webView ?: create(container.context.applicationContext)
        contextWrapper?.baseContext = container.context
        (view.parent as? ViewGroup)?.removeView(view)
        container.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        view.onResume()
        listener = newListener
        return view
    }

    /** Parks the WebView (still loaded) when the map screen's view goes away. */
    fun detach(oldListener: Listener) {
        if (listener !== oldListener) return
        listener = null
        webView?.let { view ->
            view.onPause()
            (view.parent as? ViewGroup)?.removeView(view)
        }
        contextWrapper?.let { it.baseContext = it.applicationContext }
    }

    fun evaluate(script: String) {
        webView?.evaluateJavascript(script, null)
    }

    fun clearState() {
        selectedArea = null
        diseaseFilter = null
        sheet = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(appContext: Context): WebView {
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(appContext))
            // Inter for the page's controls and legend, from res/font (no network needed).
            .addPathHandler("/res/", WebViewAssetLoader.ResourcesPathHandler(appContext))
            .build()
        // Debug builds only: lets the map be inspected from Chrome (chrome://inspect).
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        val wrapper = MutableContextWrapper(appContext)
        val view = WebView(wrapper).apply {
            settings.javaScriptEnabled = true
            // localStorage: the page remembers the Map / Satellite choice (off by default in a WebView).
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClientCompat() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    assetLoader.shouldInterceptRequest(request.url)

                // The renderer can be killed (low memory, crash). A dead WebView can't be reused:
                // drop it so the next attach builds a fresh one, and tell the screen if it's open.
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    Log.w(BRIDGE_NAME, "Map renderer gone (crashed=${detail.didCrash()}), dropping the WebView")
                    if (view === webView) discard()
                    main.post { listener?.onRendererGone() }
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.d(BRIDGE_NAME, "${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                    return true
                }
            }
            addJavascriptInterface(Bridge(), BRIDGE_NAME)
            loadUrl(MAP_URL)
        }
        webView = view
        contextWrapper = wrapper
        pageReady = false
        return view
    }

    private fun discard() {
        val view = webView ?: return
        webView = null
        contextWrapper = null
        pageReady = false
        (view.parent as? ViewGroup)?.removeView(view)
        view.removeJavascriptInterface(BRIDGE_NAME)
        view.destroy()
    }

    /** Called from the page on a WebView thread; everything is posted to the main thread. */
    private class Bridge {
        @JavascriptInterface
        fun onReady() {
            main.post {
                pageReady = true
                listener?.onPageReady()
            }
        }

        @JavascriptInterface
        fun onAreaSelected(json: String) {
            val area = runCatching {
                JSONObject(json).let { it.getString("barangay") to it.getString("municipality") }
            }.getOrNull() ?: return
            main.post {
                selectedArea = area
                listener?.onAreaSelected(area)
            }
        }
    }
}
