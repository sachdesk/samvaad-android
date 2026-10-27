package com.samvaads.widget.internal

/** Bridge protocol version (`v`) the page sends and this SDK speaks. */
internal const val BRIDGE_VERSION = 1L

/** The signed-in customer, as given to `identify`. */
internal data class Identity(val email: String, val name: String?, val userHash: String)

/** Messages the page sends to the app. */
internal sealed class PageMessage {
    data class Ready(val enabled: Boolean) : PageMessage()
    object Close : PageMessage()
    data class Unread(val count: Int) : PageMessage()
    data class Navigate(val url: String) : PageMessage()
    data class Visibility(val visible: Boolean) : PageMessage()

    companion object {
        /** Parse one `SamvaadAndroid.postMessage(json)` call. Anything unknown or malformed is null. */
        fun parse(json: String?): PageMessage? {
            if (json.isNullOrEmpty() || json.length > 64 * 1024) return null
            val obj = try {
                Json.parse(json) as? Map<*, *>
            } catch (e: JsonException) {
                null
            } ?: return null
            val v = obj["v"]
            if (v != null && (v as? Number)?.toLong() != BRIDGE_VERSION) return null
            return when (obj["type"]) {
                "ready" -> Ready(enabled = obj["enabled"] != false)
                "close" -> Close
                "unread" -> {
                    val n = obj["count"] as? Number ?: return null
                    val count = n.toDouble()
                    if (count.isNaN()) return null
                    Unread(count.coerceIn(0.0, Int.MAX_VALUE.toDouble()).toInt())
                }
                "navigate" -> (obj["url"] as? String)?.takeIf { it.isNotBlank() }?.let { Navigate(it) }
                "visibility" -> Visibility(obj["visible"] == true)
                else -> null
            }
        }
    }
}

/** Builds the app -> page messages and the script that delivers them. */
internal object AppMessages {
    fun identify(identity: Identity): String {
        val id = LinkedHashMap<String, Any?>()
        id["email"] = identity.email
        if (!identity.name.isNullOrEmpty()) id["name"] = identity.name
        id["user_hash"] = identity.userHash
        return Json.encode(mapOf("type" to "identify", "identity" to id))
    }

    fun reset(): String = Json.encode(mapOf("type" to "reset"))

    fun message(text: String): String = Json.encode(mapOf("type" to "message", "text" to text))

    /** Push token fields are top level, not nested. */
    fun pushToken(token: String, appId: String?): String {
        val m = LinkedHashMap<String, Any?>()
        m["type"] = "pushToken"
        m["token"] = token
        m["route"] = "fcm"
        m["platform"] = "android"
        if (!appId.isNullOrEmpty()) m["app_id"] = appId
        return Json.encode(m)
    }

    /** The script passed to `evaluateJavascript` for one encoded message. */
    fun script(json: String): String =
        "(function(){var b=window.SamvaadBridge;if(b&&typeof b.receive==='function'){b.receive($json);}})();"
}

/** Push data from the server: `samvaad: "1"`, `tenant_id`, `session_id`. */
internal object PushPayload {
    fun isSamvaad(data: Map<String, String>?): Boolean = data?.get("samvaad") == "1"
}

/** What a page load needs from storage; implemented over SharedPreferences in the SDK. */
internal interface SessionState {
    fun identity(): Identity?
    fun pushToken(): String?
    fun appId(): String?

    /** Read and clear the "send reset on next load" flag left by `logout()`. */
    fun consumeResetFlag(): Boolean
}

/**
 * Order of app -> page messages for one page load.
 *
 * Before `ready`, outgoing messages queue. On `ready` the session sends, in order:
 * `reset` (if `logout()` left the flag), `identify` (if identified), `pushToken` (if set),
 * then anything queued (prefilled `message`). After `ready`, messages go straight out.
 * A new page load (the page reloads after `reset`) starts over.
 *
 * Plain Kotlin, no Android types: the Activity hands the returned scripts to the WebView.
 */
internal class BridgeSession(private val state: SessionState) {
    private val queue = ArrayList<String>()

    var isReady: Boolean = false
        private set

    /** The main frame started loading a page: nothing is listening until it says `ready`. */
    fun onPageStarted() {
        isReady = false
    }

    /** The page is listening: everything to deliver now, in order. */
    fun onReady(): List<String> {
        val out = ArrayList<String>()
        if (state.consumeResetFlag()) out.add(AppMessages.reset())
        state.identity()?.let { out.add(AppMessages.identify(it)) }
        state.pushToken()?.let { out.add(AppMessages.pushToken(it, state.appId())) }
        out.addAll(queue)
        queue.clear()
        isReady = true
        return out
    }

    /** Prefill the message box. Queued until `ready`. */
    fun sendMessage(text: String): List<String> {
        val t = text.trim()
        if (t.isEmpty()) return emptyList()
        return sendOrQueue(AppMessages.message(t))
    }

    /**
     * `identify` / `setPushToken` while the chat is open. Before `ready` nothing is queued:
     * [onReady] reads the latest value from storage anyway, so it is sent exactly once.
     */
    fun sendIdentify(identity: Identity): List<String> =
        if (isReady) listOf(AppMessages.identify(identity)) else emptyList()

    fun sendPushToken(token: String): List<String> =
        if (isReady) listOf(AppMessages.pushToken(token, state.appId())) else emptyList()

    /**
     * `logout()` while the chat is open. Ready: the `reset` to send now. Not ready yet: null, and
     * the caller persists the flag instead, so `reset` goes out first on `ready`.
     */
    fun sendReset(): List<String>? = if (isReady) listOf(AppMessages.reset()) else null

    private fun sendOrQueue(json: String): List<String> {
        if (isReady) return listOf(json)
        queue.add(json)
        return emptyList()
    }
}
