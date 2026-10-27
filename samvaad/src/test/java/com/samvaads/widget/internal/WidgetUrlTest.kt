package com.samvaads.widget.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetUrlTest {

    private val yobruh = WidgetOrigin("https", "yobruh.samvaads.com")

    @Test
    fun hostIsRequired() {
        assertThrows { WidgetOrigin.resolve("", allowLocalHttp = false) }
        assertThrows { WidgetOrigin.resolve("   ", allowLocalHttp = true) }
    }

    @Test
    fun acceptsBareHostOrHttpsUrl() {
        assertEquals(yobruh, WidgetOrigin.resolve("yobruh.samvaads.com", false))
        assertEquals(yobruh, WidgetOrigin.resolve("https://Yobruh.samvaads.com/", false))
        assertEquals(WidgetOrigin("https", "chat.yobruh.in"), WidgetOrigin.resolve("chat.yobruh.in", false))
        assertEquals(WidgetOrigin("https", "chat.yobruh.in", 8443), WidgetOrigin.resolve("chat.yobruh.in:8443", false))
    }

    @Test
    fun rejectsPathsQueriesAndOtherSchemes() {
        assertThrows { WidgetOrigin.resolve("https://yobruh.samvaads.com/embed", false) }
        assertThrows { WidgetOrigin.resolve("https://yobruh.samvaads.com?x=1", false) }
        assertThrows { WidgetOrigin.resolve("ftp://yobruh.samvaads.com", false) }
        assertThrows { WidgetOrigin.resolve("https://user@yobruh.samvaads.com", false) }
        assertThrows { WidgetOrigin.resolve("https://user:pass@yobruh.samvaads.com", false) }
        assertThrows { WidgetOrigin.resolve("yobruh.samvaads.com/embed/widget", false) }
        assertThrows { WidgetOrigin.resolve("https://yobruh.samvaads.com#x", false) }
    }

    @Test
    fun localHttpOnlyInDebugAndOnlyForLocalHosts() {
        assertThrows { WidgetOrigin.resolve("http://localhost:5173", allowLocalHttp = false) }
        assertEquals(WidgetOrigin("http", "localhost", 5173), WidgetOrigin.resolve("http://localhost:5173", true))
        assertEquals(WidgetOrigin("http", "10.0.2.2", 5173), WidgetOrigin.resolve("http://10.0.2.2:5173", true))
        assertEquals(WidgetOrigin("http", "127.0.0.1", 5173), WidgetOrigin.resolve("http://127.0.0.1:5173", true))
        assertThrows { WidgetOrigin.resolve("http://yobruh.samvaads.com", allowLocalHttp = true) }
    }

    @Test
    fun buildsPageUrl() {
        assertEquals(
            "https://yobruh.samvaads.com/embed/widget?tenant_id=6f1c2b8e-4a3d-4e2f-9b7a-1c5d8e0f2a34&mode=app&theme=dark",
            WidgetUrl.build(yobruh, "6f1c2b8e-4a3d-4e2f-9b7a-1c5d8e0f2a34", PageTheme.DARK),
        )
        assertEquals(
            "http://10.0.2.2:5173/embed/widget?tenant_id=a%20b%26c&mode=app",
            WidgetUrl.build(WidgetOrigin("http", "10.0.2.2", 5173), "a b&c", null),
        )
        assertEquals(
            "https://yobruh.samvaads.com/embed/widget?tenant_id=t&mode=app&theme=auto",
            WidgetUrl.build(yobruh, "t", PageTheme.AUTO),
        )
    }

    @Test
    fun originMatchesSameSchemeHostAndPortOnly() {
        assertTrue(yobruh.matches("https://yobruh.samvaads.com/embed/widget?x=1"))
        assertTrue(yobruh.matches("https://YOBRUH.samvaads.com:443/"))
        assertFalse(yobruh.matches("http://yobruh.samvaads.com/"))
        assertFalse(yobruh.matches("https://yobruh.samvaads.com.evil.example/"))
        assertFalse(yobruh.matches("https://evil.example/?https://yobruh.samvaads.com"))
        assertFalse(yobruh.matches("https://yobruh.samvaads.com:8443/"))
        assertFalse(yobruh.matches("not a url"))
    }

    @Test
    fun linkPolicy() {
        assertEquals(LinkAction.INSIDE, LinkPolicy.decide("https://yobruh.samvaads.com/embed/widget", yobruh))
        assertEquals(LinkAction.BROWSER, LinkPolicy.decide("https://yobruh.in/orders/YB-1042", yobruh))
        assertEquals(LinkAction.BROWSER, LinkPolicy.decide("http://yobruh.in/", yobruh))
        assertEquals(LinkAction.SYSTEM, LinkPolicy.decide("mailto:help@yobruh.in", yobruh))
        assertEquals(LinkAction.SYSTEM, LinkPolicy.decide("tel:+911234567890", yobruh))
        assertEquals(LinkAction.BLOCK, LinkPolicy.decide("javascript:alert(1)", yobruh))
        assertEquals(LinkAction.BLOCK, LinkPolicy.decide("intent://x#Intent;end", yobruh))
        assertEquals(LinkAction.BLOCK, LinkPolicy.decide("file:///sdcard/x", yobruh))
        assertEquals(LinkAction.BLOCK, LinkPolicy.decide("", yobruh))
        assertEquals(LinkAction.BLOCK, LinkPolicy.decide("https://", yobruh))
    }

    @Test
    fun navigateAlwaysOpensOutside() {
        assertEquals(LinkAction.BROWSER, LinkPolicy.decideNavigate("https://yobruh.samvaads.com/kb/returns", yobruh))
        assertEquals(LinkAction.BROWSER, LinkPolicy.decideNavigate("https://yobruh.in/", yobruh))
        assertEquals(LinkAction.BLOCK, LinkPolicy.decideNavigate("javascript:void(0)", yobruh))
    }

    private fun assertThrows(block: () -> Unit) {
        assertTrue(runCatching(block).exceptionOrNull() is IllegalArgumentException)
    }
}
