package com.samvaads.widget.internal

import java.net.URI
import java.net.URLEncoder

/** Where the widget page lives: scheme + host (+ port for local testing). */
internal data class WidgetOrigin(val scheme: String, val host: String, val port: Int = -1) {

    /** `https://yobruh.samvaads.com` or `http://10.0.2.2:5173`. */
    override fun toString(): String = if (port > 0) "$scheme://$host:$port" else "$scheme://$host"

    /** True when [url] is on this exact origin (same scheme, host and port). */
    fun matches(url: String): Boolean {
        val uri = parseUri(url) ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host?.lowercase() ?: return false
        return scheme == this.scheme && host == this.host && effectivePort(scheme, uri.port) == effectivePort(this.scheme, port)
    }

    companion object {
        private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

        /**
         * Resolve the origin from `configure(tenantId, host)`.
         *
         * - `host` may be a bare host (`yobruh.samvaads.com`) or a URL (`https://yobruh.samvaads.com`).
         *   Any https host is accepted.
         * - `http://localhost:<port>`, `http://127.0.0.1:<port>` and `http://10.0.2.2:<port>` are
         *   accepted only when [allowLocalHttp] is true (debuggable app builds).
         * - The host is required: tenant ids are UUIDs, so no host can be derived from them.
         * - Paths, queries, fragments and credentials are rejected.
         */
        fun resolve(host: String, allowLocalHttp: Boolean): WidgetOrigin {
            val raw = host.trim()
            require(raw.isNotEmpty()) { "host is required, e.g. \"yobruh.samvaads.com\"" }
            val withScheme = if (raw.contains("://")) raw else "https://$raw"
            val uri = parseUri(withScheme) ?: throw IllegalArgumentException("Invalid host '$host'")
            val scheme = uri.scheme?.lowercase()
            val h = uri.host?.lowercase()
            require(!h.isNullOrEmpty()) { "Invalid host '$host'" }
            require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") { "Host must not include a path: '$host'" }
            require(uri.rawQuery == null && uri.rawFragment == null) { "Host must not include a query: '$host'" }
            require(uri.rawUserInfo == null) { "Host must not include credentials: '$host'" }
            return when (scheme) {
                "https" -> WidgetOrigin("https", h, if (uri.port == 443) -1 else uri.port)
                "http" -> {
                    require(allowLocalHttp && h in LOCAL_HOSTS) {
                        "http is only allowed for localhost / 10.0.2.2 in debuggable builds: '$host'"
                    }
                    WidgetOrigin("http", h, if (uri.port == 80) -1 else uri.port)
                }
                else -> throw IllegalArgumentException("Host must be https: '$host'")
            }
        }

        internal fun parseUri(url: String): URI? = try {
            URI(url.trim())
        } catch (e: Exception) {
            null
        }

        private fun effectivePort(scheme: String, port: Int): Int = when {
            port > 0 -> port
            scheme == "https" -> 443
            scheme == "http" -> 80
            else -> -1
        }
    }
}

/** Light / dark value passed to the page as `?theme=`. */
internal enum class PageTheme(val value: String) { LIGHT("light"), DARK("dark"), AUTO("auto") }

internal object WidgetUrl {
    /** `https://<host>/embed/widget?tenant_id=<tenantId>&mode=app&theme=<theme>` */
    fun build(origin: WidgetOrigin, tenantId: String, theme: PageTheme?): String {
        val sb = StringBuilder()
            .append(origin.toString())
            .append("/embed/widget?tenant_id=")
            .append(encode(tenantId.trim()))
            .append("&mode=app")
        if (theme != null) sb.append("&theme=").append(theme.value)
        return sb.toString()
    }

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}

/** What to do with a URL the page wants to open. */
internal enum class LinkAction {
    /** Load it in the chat WebView (same origin as the widget page). */
    INSIDE,

    /** http(s) elsewhere: Custom Tabs, else ACTION_VIEW. */
    BROWSER,

    /** mailto:, tel:, sms:, geo:, whatsapp: and similar: hand to the system (ACTION_VIEW). */
    SYSTEM,

    /** Never opened (javascript:, file:, content:, intent:, data: and garbage). */
    BLOCK,
}

internal object LinkPolicy {
    private val SYSTEM_SCHEMES = setOf("mailto", "tel", "sms", "smsto", "geo", "whatsapp", "market", "upi")

    fun decide(url: String?, origin: WidgetOrigin): LinkAction {
        if (url.isNullOrBlank()) return LinkAction.BLOCK
        val uri = WidgetOrigin.parseUri(url) ?: return LinkAction.BLOCK
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> when {
                uri.host.isNullOrEmpty() -> LinkAction.BLOCK
                origin.matches(url) -> LinkAction.INSIDE
                else -> LinkAction.BROWSER
            }
            in SYSTEM_SCHEMES -> LinkAction.SYSTEM
            else -> LinkAction.BLOCK
        }
    }

    /** `navigate` from the page always opens outside the WebView, even for same-origin links. */
    fun decideNavigate(url: String?, origin: WidgetOrigin): LinkAction =
        when (val action = decide(url, origin)) {
            LinkAction.INSIDE -> LinkAction.BROWSER
            else -> action
        }
}
