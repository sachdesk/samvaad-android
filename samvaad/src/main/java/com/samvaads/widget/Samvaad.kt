package com.samvaads.widget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import com.samvaads.widget.internal.Identity
import com.samvaads.widget.internal.PrefsStore
import com.samvaads.widget.internal.PushPayload
import com.samvaads.widget.internal.SdkState
import com.samvaads.widget.internal.WidgetOrigin
import java.lang.ref.WeakReference

/**
 * Samvaad chat for Android apps.
 *
 * ```
 * Samvaad.configure(applicationContext, tenantId = "yobruh", host = "yobruh.samvaads.com")
 * Samvaad.identify(email, name, userHash)   // userHash comes from YOUR backend
 * Samvaad.present(activity)
 * ```
 *
 * Call everything from the main thread.
 */
object Samvaad {

    /** SDK version. */
    const val VERSION = "0.1.0"

    private val main = Handler(Looper.getMainLooper())
    private var state: SdkState? = null
    private var origin: WidgetOrigin? = null
    private var unreadListener: UnreadListener? = null
    private var activeChat: WeakReference<SamvaadChatActivity>? = null

    /**
     * Set up the SDK. Call once, from `Application.onCreate`, before anything else.
     *
     * @param tenantId your Samvaad tenant id.
     * @param host the host serving your chat widget, e.g. `yobruh.samvaads.com`. Defaults to
     *   `<tenantId>.samvaads.com`. Must be https; `http://localhost:<port>` and
     *   `http://10.0.2.2:<port>` are accepted only in debuggable builds.
     * @throws IllegalArgumentException for an invalid tenant id or host.
     */
    @JvmStatic
    @JvmOverloads
    fun configure(context: Context, tenantId: String, host: String? = null) {
        require(tenantId.isNotBlank()) { "tenantId is required" }
        val app = context.applicationContext
        val resolved = WidgetOrigin.resolve(tenantId, host, allowLocalHttp = isDebuggable(app))
        val s = SdkState(PrefsStore(app), app.packageName)
        s.saveConfig(tenantId.trim(), host?.trim()?.takeIf { it.isNotEmpty() })
        state = s
        origin = resolved
    }

    /**
     * Tell the chat who the signed-in customer is.
     *
     * [userHash] is HMAC-SHA256(identity secret, lower-cased email), hex, computed on your
     * backend. Never put the identity secret in the app.
     */
    @JvmStatic
    fun identify(email: String, name: String?, userHash: String) {
        val e = email.trim()
        val h = userHash.trim()
        require(e.isNotEmpty()) { "email is required" }
        require(h.isNotEmpty()) { "userHash is required" }
        val identity = Identity(e.take(254), name?.trim()?.takeIf { it.isNotEmpty() }?.take(120), h.take(128))
        requireState().identify(identity)
        activeChat?.get()?.deliverIdentify(identity)
    }

    /**
     * Open the chat screen.
     *
     * @param message optional text to prefill in the message box (not sent).
     */
    @JvmStatic
    @JvmOverloads
    fun present(activity: Activity, message: String? = null) {
        requireState()
        activity.startActivity(chatIntent(activity, message))
    }

    /**
     * The customer signed out of your app: forget them in the chat. The device push token
     * is kept and registered again with the next chat.
     */
    @JvmStatic
    fun logout() {
        val s = requireState()
        val sentLive = activeChat?.get()?.deliverReset() == true
        s.logout(sentLive)
        setUnread(0)
    }

    /**
     * Listen for unread count changes (main thread). The listener is called right away with
     * the current count. Pass null to stop listening.
     */
    @JvmStatic
    fun onUnreadChange(listener: UnreadListener?) {
        unreadListener = listener
        listener?.onUnreadChange(unreadCount)
    }

    /** Agent replies the customer hasn't seen yet, as last reported by the chat. */
    @JvmStatic
    val unreadCount: Int
        get() = state?.unreadCount ?: 0

    /** Light / dark look of the chat screen. Default [SamvaadTheme.AUTO]: follows the app. */
    @JvmStatic
    var theme: SamvaadTheme = SamvaadTheme.AUTO

    /**
     * Give the SDK this device's FCM registration token (`FirebaseMessagingService.onNewToken`
     * or `FirebaseMessaging.getInstance().token`). The chat registers it for push.
     */
    @JvmStatic
    fun setPushToken(token: String) {
        val t = token.trim()
        require(t.isNotEmpty() && t.length <= 4096) { "Invalid push token" }
        requireState().setPushToken(t)
        activeChat?.get()?.deliverPushToken(t)
    }

    /**
     * Handle an FCM data payload (`RemoteMessage.data`). Returns false when it isn't a Samvaad
     * push (handle it yourself); otherwise opens the chat and returns true.
     */
    @JvmStatic
    fun handlePush(context: Context, data: Map<String, String>): Boolean {
        if (!PushPayload.isSamvaad(data)) return false
        ensureState(context)
        val intent = chatIntent(context, null)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    // ---- SDK internals ----

    internal fun chatIntent(context: Context, message: String?): Intent =
        Intent(context, SamvaadChatActivity::class.java).apply {
            if (!message.isNullOrBlank()) putExtra(SamvaadChatActivity.EXTRA_MESSAGE, message)
        }

    /** State for the chat screen; restores config from storage after process death. */
    internal fun ensureState(context: Context): SdkState {
        state?.let { return it }
        val app = context.applicationContext
        val s = SdkState(PrefsStore(app), app.packageName)
        val tenantId = s.tenantId ?: throw IllegalStateException("Call Samvaad.configure(...) first")
        origin = WidgetOrigin.resolve(tenantId, s.host, allowLocalHttp = isDebuggable(app))
        state = s
        return s
    }

    internal fun currentOrigin(): WidgetOrigin? = origin

    internal fun attach(chat: SamvaadChatActivity) {
        activeChat = WeakReference(chat)
    }

    internal fun detach(chat: SamvaadChatActivity) {
        if (activeChat?.get() === chat) activeChat = null
    }

    internal fun setUnread(count: Int) {
        val s = state ?: return
        val c = count.coerceAtLeast(0)
        if (s.unreadCount == c) return
        s.unreadCount = c
        val listener = unreadListener ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) listener.onUnreadChange(c) else main.post { listener.onUnreadChange(c) }
    }

    private fun requireState(): SdkState =
        state ?: throw IllegalStateException("Call Samvaad.configure(...) first")

    private fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
