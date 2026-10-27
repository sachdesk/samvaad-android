package com.samvaads.widget.internal

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * Added to the WebView as `SamvaadAndroid`. The page calls `SamvaadAndroid.postMessage(json)`
 * on a WebView background thread; messages are parsed there and handed over on the main thread.
 */
internal class JsBridge(private val onMessage: (PageMessage) -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun postMessage(json: String?) {
        val msg = PageMessage.parse(json) ?: return
        main.post { onMessage(msg) }
    }
}
