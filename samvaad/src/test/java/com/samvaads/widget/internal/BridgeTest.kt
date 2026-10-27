package com.samvaads.widget.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeTest {

    @Test
    fun parsesPageMessages() {
        assertEquals(PageMessage.Ready(true), PageMessage.parse("""{"v":1,"type":"ready","enabled":true}"""))
        assertEquals(PageMessage.Ready(false), PageMessage.parse("""{"v":1,"type":"ready","enabled":false}"""))
        assertEquals(PageMessage.Close, PageMessage.parse("""{"v":1,"type":"close"}"""))
        assertEquals(PageMessage.Unread(3), PageMessage.parse("""{"v":1,"type":"unread","count":3}"""))
        assertEquals(PageMessage.Unread(0), PageMessage.parse("""{"v":1,"type":"unread","count":-2}"""))
        assertEquals(
            PageMessage.Navigate("https://yobruh.in/orders/YB-1042"),
            PageMessage.parse("""{"v":1,"type":"navigate","url":"https://yobruh.in/orders/YB-1042"}"""),
        )
        assertEquals(PageMessage.Visibility(true), PageMessage.parse("""{"v":1,"type":"visibility","visible":true}"""))
    }

    @Test
    fun dropsUnknownOrMalformedPageMessages() {
        assertNull(PageMessage.parse(null))
        assertNull(PageMessage.parse(""))
        assertNull(PageMessage.parse("not json"))
        assertNull(PageMessage.parse("[1,2]"))
        assertNull(PageMessage.parse("""{"v":1,"type":"explode"}"""))
        assertNull(PageMessage.parse("""{"v":2,"type":"close"}"""))
        assertNull(PageMessage.parse("""{"v":1,"type":"unread","count":"3"}"""))
        assertNull(PageMessage.parse("""{"v":1,"type":"navigate"}"""))
        assertNull(PageMessage.parse("""{"v":1,"type":"navigate","url":"  "}"""))
    }

    @Test
    fun buildsAppMessages() {
        assertEquals(
            """{"type":"identify","identity":{"email":"asha@yobruh.in","name":"Asha","user_hash":"abc123"}}""",
            AppMessages.identify(Identity("asha@yobruh.in", "Asha", "abc123")),
        )
        assertEquals(
            """{"type":"identify","identity":{"email":"asha@yobruh.in","user_hash":"abc123"}}""",
            AppMessages.identify(Identity("asha@yobruh.in", null, "abc123")),
        )
        assertEquals("""{"type":"reset"}""", AppMessages.reset())
        assertEquals("""{"type":"message","text":"Where is YB-1042?"}""", AppMessages.message("Where is YB-1042?"))
    }

    @Test
    fun pushTokenFieldsAreTopLevel() {
        assertEquals(
            """{"type":"pushToken","token":"fcm-token","route":"fcm","platform":"android","app_id":"in.yobruh.shop"}""",
            AppMessages.pushToken("fcm-token", "in.yobruh.shop"),
        )
        assertEquals(
            """{"type":"pushToken","token":"t","route":"fcm","platform":"android"}""",
            AppMessages.pushToken("t", null),
        )
    }

    @Test
    fun scriptCallsTheBridgeWithAnEscapedLiteral() {
        val script = AppMessages.script(AppMessages.message("hi 'there'"))
        assertTrue(script.contains("b.receive({\"type\":\"message\",\"text\":\"hi\\u2028'there'\"});"))
        assertFalse(script.contains(' '))
    }

    @Test
    fun pushPayloadCheck() {
        assertTrue(PushPayload.isSamvaad(mapOf("samvaad" to "1", "tenant_id" to "6f1c2b8e-4a3d-4e2f-9b7a-1c5d8e0f2a34", "session_id" to "s1")))
        assertFalse(PushPayload.isSamvaad(mapOf("samvaad" to "0")))
        assertFalse(PushPayload.isSamvaad(mapOf("promo" to "YB-1042")))
        assertFalse(PushPayload.isSamvaad(emptyMap()))
        assertFalse(PushPayload.isSamvaad(null))
    }
}
