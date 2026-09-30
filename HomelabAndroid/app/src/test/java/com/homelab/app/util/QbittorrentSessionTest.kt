package com.homelab.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QbittorrentSessionTest {

    @Test
    fun `legacy SID cookie keeps the bare value`() {
        assertEquals("abc123", QbittorrentSession.tokenFromSetCookie("SID=abc123; HttpOnly; path=/"))
        assertEquals("SID=abc123", QbittorrentSession.cookieHeader("abc123"))
    }

    @Test
    fun `qbittorrent 5 port-suffixed cookie keeps its name`() {
        val token = QbittorrentSession.tokenFromSetCookie(
            "QBT_SID_8112=xyz789; HttpOnly; SameSite=Lax; expires=Wed, 30-Sep-2026 12:05:42 GMT; path=/"
        )
        assertEquals("QBT_SID_8112=xyz789", token)
        assertEquals("QBT_SID_8112=xyz789", QbittorrentSession.cookieHeader(token!!))
    }

    @Test
    fun `cookie found among several joined set-cookie headers`() {
        assertEquals(
            "QBT_SID_8080=abc",
            QbittorrentSession.tokenFromSetCookie("other=1; path=/;QBT_SID_8080=abc; HttpOnly")
        )
    }

    @Test
    fun `unrelated cookies are ignored`() {
        assertNull(QbittorrentSession.tokenFromSetCookie("SESSIONID=nope; path=/"))
        assertNull(QbittorrentSession.tokenFromSetCookie(""))
    }
}
