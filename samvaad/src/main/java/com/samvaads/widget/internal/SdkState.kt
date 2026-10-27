package com.samvaads.widget.internal

/** Minimal key-value storage; SharedPreferences in the SDK, a map in tests. */
internal interface KeyValueStore {
    fun getString(key: String): String?
    fun getBoolean(key: String): Boolean
    fun getInt(key: String): Int

    /** Apply all [changes] together. A null value removes the key. */
    fun edit(changes: Map<String, Any?>)
}

/**
 * What the SDK remembers across app restarts: config, identity, the logout flag,
 * the device push token and the last unread count.
 */
internal class SdkState(private val store: KeyValueStore, private val appId: String?) : SessionState {

    val tenantId: String? get() = store.getString(K_TENANT)

    val host: String? get() = store.getString(K_HOST)

    fun saveConfig(tenantId: String, host: String?) {
        store.edit(mapOf(K_TENANT to tenantId, K_HOST to host))
    }

    fun identify(identity: Identity) {
        store.edit(
            mapOf(
                K_EMAIL to identity.email,
                K_NAME to identity.name,
                K_HASH to identity.userHash,
            ),
        )
    }

    /**
     * Forget the identity. Keep the push token (it belongs to the device). When the open
     * chat could not take `reset` right now ([sentLive] false), leave a flag so the next
     * page load sends it first.
     */
    fun logout(sentLive: Boolean) {
        store.edit(
            mapOf(
                K_EMAIL to null,
                K_NAME to null,
                K_HASH to null,
                K_RESET to if (sentLive) null else true,
            ),
        )
    }

    val resetPending: Boolean get() = store.getBoolean(K_RESET)

    fun setPushToken(token: String) {
        store.edit(mapOf(K_PUSH to token))
    }

    var unreadCount: Int
        get() = store.getInt(K_UNREAD)
        set(value) = store.edit(mapOf(K_UNREAD to value.coerceAtLeast(0)))

    override fun identity(): Identity? {
        val email = store.getString(K_EMAIL) ?: return null
        val hash = store.getString(K_HASH) ?: return null
        return Identity(email, store.getString(K_NAME), hash)
    }

    override fun pushToken(): String? = store.getString(K_PUSH)

    override fun appId(): String? = appId

    override fun consumeResetFlag(): Boolean {
        if (!store.getBoolean(K_RESET)) return false
        store.edit(mapOf(K_RESET to null))
        return true
    }

    companion object {
        const val K_TENANT = "tenant_id"
        const val K_HOST = "host"
        const val K_EMAIL = "identity_email"
        const val K_NAME = "identity_name"
        const val K_HASH = "identity_user_hash"
        const val K_RESET = "reset_pending"
        const val K_PUSH = "push_token"
        const val K_UNREAD = "unread_count"
    }
}
