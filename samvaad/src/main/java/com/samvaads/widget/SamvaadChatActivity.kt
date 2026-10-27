package com.samvaads.widget

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.samvaads.widget.internal.AppMessages
import com.samvaads.widget.internal.BridgeSession
import com.samvaads.widget.internal.Identity
import com.samvaads.widget.internal.JsBridge
import com.samvaads.widget.internal.LinkAction
import com.samvaads.widget.internal.LinkPolicy
import com.samvaads.widget.internal.PageMessage
import com.samvaads.widget.internal.PageTheme
import com.samvaads.widget.internal.WidgetOrigin
import com.samvaads.widget.internal.WidgetUrl

/**
 * Full-screen chat: hosts the Samvaad widget page in a WebView and bridges messages.
 * Open it with [Samvaad.present]; it is not meant to be started directly.
 */
class SamvaadChatActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var session: BridgeSession
    private lateinit var origin: WidgetOrigin
    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooser =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = pendingFileCallback
            pendingFileCallback = null
            callback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val state = try {
            Samvaad.ensureState(this)
        } catch (e: IllegalStateException) {
            finish()
            return
        }
        origin = Samvaad.currentOrigin() ?: run {
            finish()
            return
        }
        session = BridgeSession(state)

        val dark = isDark()
        val background = if (dark) Color.parseColor("#111418") else Color.WHITE

        // Draw edge to edge and pad the content by the system bars, cutout and keyboard,
        // so the page (which has no notch handling) stays inside the safe area.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        val root = FrameLayout(this).apply { setBackgroundColor(background) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }

        webView = WebView(this).apply {
            setBackgroundColor(background)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true // localStorage holds the chat session across restarts
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false) // target=_blank / window.open go through shouldOverrideUrlLoading
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
        }
        webView.addJavascriptInterface(JsBridge(::onPageMessage), "SamvaadAndroid")
        webView.webViewClient = ChatWebViewClient()
        webView.webChromeClient = ChatChromeClient()

        root.addView(webView)
        setContentView(root)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) webView.goBack() else finish()
                }
            },
        )

        intent?.getStringExtra(EXTRA_MESSAGE)?.let { deliver(session.sendMessage(it)) }

        Samvaad.attach(this)
        val tenantId = state.tenantId ?: run {
            finish()
            return
        }
        webView.loadUrl(WidgetUrl.build(origin, tenantId, pageTheme(dark)))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // present(message) while the chat is already open: prefill the new text.
        intent.getStringExtra(EXTRA_MESSAGE)?.let { deliver(session.sendMessage(it)) }
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onPause() {
        if (::webView.isInitialized) webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        Samvaad.detach(this)
        pendingFileCallback?.onReceiveValue(null)
        pendingFileCallback = null
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.removeJavascriptInterface("SamvaadAndroid")
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    // ---- Called by Samvaad on the main thread ----

    internal fun deliverIdentify(identity: Identity) = deliver(session.sendIdentify(identity))

    internal fun deliverPushToken(token: String) = deliver(session.sendPushToken(token))

    /** True when `reset` went to a listening page now; false means the caller must persist the flag. */
    internal fun deliverReset(): Boolean {
        val scripts = session.sendReset() ?: return false
        deliver(scripts)
        return true
    }

    // ---- Bridge ----

    private fun onPageMessage(msg: PageMessage) {
        if (isFinishing || isDestroyed) return
        // Only the widget page itself may talk to the app.
        if (!origin.matches(webView.url ?: "")) return
        when (msg) {
            is PageMessage.Ready -> deliver(session.onReady()) // enabled=false: the page says the chat is off
            PageMessage.Close -> finish()
            is PageMessage.Unread -> Samvaad.setUnread(msg.count)
            is PageMessage.Navigate -> openOutside(msg.url, LinkPolicy.decideNavigate(msg.url, origin))
            is PageMessage.Visibility -> Unit // website only
        }
    }

    private fun deliver(messages: List<String>) {
        if (!::webView.isInitialized) return
        for (json in messages) webView.evaluateJavascript(AppMessages.script(json), null)
    }

    private fun openOutside(url: String, action: LinkAction) {
        val uri = Uri.parse(url)
        when (action) {
            LinkAction.BROWSER -> {
                try {
                    CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, uri)
                } catch (e: ActivityNotFoundException) {
                    viewExternally(uri)
                }
            }
            LinkAction.SYSTEM -> viewExternally(uri)
            LinkAction.INSIDE, LinkAction.BLOCK -> Unit
        }
    }

    private fun viewExternally(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (e: ActivityNotFoundException) {
            // Nothing on the device can open it.
        }
    }

    private fun isDark(): Boolean = when (Samvaad.theme) {
        SamvaadTheme.LIGHT -> false
        SamvaadTheme.DARK -> true
        SamvaadTheme.AUTO ->
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    /** AUTO is resolved to the app's current mode, so the page matches the app. */
    private fun pageTheme(dark: Boolean): PageTheme = if (dark) PageTheme.DARK else PageTheme.LIGHT

    private inner class ChatWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // Sub-frames (e.g. embedded media) load as the page asks; the top frame stays on our host.
            if (!request.isForMainFrame) return false
            val url = request.url.toString()
            return when (val action = LinkPolicy.decide(url, origin)) {
                LinkAction.INSIDE -> false
                else -> {
                    openOutside(url, action)
                    true
                }
            }
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            session.onPageStarted()
        }
    }

    private inner class ChatChromeClient : WebChromeClient() {
        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = filePathCallback
            return try {
                fileChooser.launch(fileChooserParams.createIntent())
                true
            } catch (e: ActivityNotFoundException) {
                pendingFileCallback = null
                filePathCallback.onReceiveValue(null)
                true
            }
        }
    }

    internal companion object {
        const val EXTRA_MESSAGE = "com.samvaads.widget.MESSAGE"
    }
}
