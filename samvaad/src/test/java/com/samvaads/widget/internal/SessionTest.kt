package com.samvaads.widget.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {

    private class MapStore : KeyValueStore {
        val map = HashMap<String, Any?>()
        override fun getString(key: String) = map[key] as? String
        override fun getBoolean(key: String) = map[key] as? Boolean ?: false
        override fun getInt(key: String) = map[key] as? Int ?: 0
        override fun edit(changes: Map<String, Any?>) {
            for ((k, v) in changes) if (v == null) map.remove(k) else map[k] = v
        }
    }

    private val asha = Identity("asha@yobruh.in", "Asha", "hash1")
    private val store = MapStore()
    private val state = SdkState(store, "in.yobruh.shop")

    private fun types(scripts: List<String>) = scripts.map { (Json.parse(it) as Map<*, *>)["type"] }

    @Test
    fun queuesUntilReadyThenFlushesInOrder() {
        state.identify(asha)
        state.setPushToken("tok")
        val session = BridgeSession(state)
        session.onPageStarted()
        assertEquals(emptyList<String>(), session.sendMessage("Where is YB-1042?"))
        assertEquals(emptyList<String>(), session.sendMessage("second"))
        assertFalse(session.isReady)

        val out = session.onReady()
        assertTrue(session.isReady)
        assertEquals(listOf("identify", "pushToken", "message", "message"), types(out))
        assertEquals("Where is YB-1042?", (Json.parse(out[2]) as Map<*, *>)["text"])

        // After ready: straight out, and the queue is empty.
        assertEquals(listOf("message"), types(session.sendMessage("now")))
    }

    @Test
    fun identifyAndPushTokenAreSentOnEveryPageLoad() {
        state.identify(asha)
        state.setPushToken("tok")
        val session = BridgeSession(state)
        assertEquals(listOf("identify", "pushToken"), types(session.onReady()))
        session.onPageStarted() // the page reloaded
        assertEquals(listOf("identify", "pushToken"), types(session.onReady()))
    }

    @Test
    fun nothingToSendWhenNotIdentifiedAndNoToken() {
        assertEquals(emptyList<String>(), BridgeSession(state).onReady())
    }

    @Test
    fun blankPrefillIsIgnored() {
        val session = BridgeSession(state)
        assertEquals(emptyList<String>(), session.sendMessage("   "))
        assertEquals(emptyList<String>(), session.onReady())
    }

    @Test
    fun liveIdentifyAndTokenBeforeReadyAreNotDuplicated() {
        val session = BridgeSession(state)
        state.identify(asha)
        assertEquals(emptyList<String>(), session.sendIdentify(asha))
        state.setPushToken("tok")
        assertEquals(emptyList<String>(), session.sendPushToken("tok"))
        assertEquals(listOf("identify", "pushToken"), types(session.onReady()))
        assertEquals(listOf("identify"), types(session.sendIdentify(asha)))
        assertEquals(listOf("pushToken"), types(session.sendPushToken("tok2")))
    }

    @Test
    fun pushTokenCarriesAppId() {
        state.setPushToken("tok")
        val msg = Json.parse(BridgeSession(state).onReady().single()) as Map<*, *>
        assertEquals("in.yobruh.shop", msg["app_id"])
        assertEquals("fcm", msg["route"])
        assertEquals("android", msg["platform"])
    }

    @Test
    fun logoutWithChatClosedLeavesFlagAndResetGoesFirstOnNextLoad() {
        state.identify(asha)
        state.setPushToken("tok")
        state.logout(sentLive = false)

        assertNull(state.identity())
        assertEquals("tok", state.pushToken()) // the device token is kept
        assertTrue(state.resetPending)

        val session = BridgeSession(state)
        session.sendMessage("hello")
        assertEquals(listOf("reset", "pushToken", "message"), types(session.onReady()))
        assertFalse(state.resetPending)

        // The page reloads after reset: no second reset.
        session.onPageStarted()
        assertEquals(listOf("pushToken"), types(session.onReady()))
    }

    @Test
    fun logoutThenIdentifyAgainSendsResetBeforeTheNewIdentity() {
        state.identify(asha)
        state.logout(sentLive = false)
        state.identify(Identity("ravi@yobruh.in", null, "hash2"))
        val out = BridgeSession(state).onReady()
        assertEquals(listOf("reset", "identify"), types(out))
        assertTrue(out[1].contains("ravi@yobruh.in"))
    }

    @Test
    fun logoutWhileChatIsReadySendsResetNowWithoutAFlag() {
        state.identify(asha)
        val session = BridgeSession(state)
        session.onReady()

        val live = session.sendReset()
        assertEquals(listOf("reset"), types(live!!))
        state.logout(sentLive = true)
        assertFalse(state.resetPending)
        assertNull(state.identity())
    }

    @Test
    fun logoutWhileChatIsLoadingFallsBackToTheFlag() {
        val session = BridgeSession(state)
        session.onPageStarted()
        assertNull(session.sendReset())
        state.logout(sentLive = false)
        assertEquals(listOf("reset"), types(session.onReady()))
    }

    @Test
    fun configAndUnreadPersist() {
        state.saveConfig("6f1c2b8e-4a3d-4e2f-9b7a-1c5d8e0f2a34", "yobruh.samvaads.com")
        state.unreadCount = 4
        val again = SdkState(store, "in.yobruh.shop")
        assertEquals("6f1c2b8e-4a3d-4e2f-9b7a-1c5d8e0f2a34", again.tenantId)
        assertEquals("yobruh.samvaads.com", again.host)
        assertEquals(4, again.unreadCount)
        again.unreadCount = -1
        assertEquals(0, again.unreadCount)
    }
}
