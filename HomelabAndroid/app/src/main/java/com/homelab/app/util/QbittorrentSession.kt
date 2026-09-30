package com.homelab.app.util

/**
 * qBittorrent WebUI session cookie handling.
 *
 * qBittorrent before 5.x names its session cookie `SID`; 5.x names it `QBT_SID_<port>`.
 * The stored token keeps the historical format (the bare value) for `SID`, and is stored as
 * `NAME=value` for any other cookie name, so existing instances keep working unchanged.
 */
object QbittorrentSession {
    private val cookiePattern = Regex("""(?:^|[;,]\s*)((?:QBT_)?SID(?:_\d+)?)=([^;,\s]+)""")

    /** Extracts the session token from the (possibly joined) Set-Cookie headers of a login response. */
    fun tokenFromSetCookie(setCookie: String): String? {
        val match = cookiePattern.find(setCookie) ?: return null
        val (name, value) = match.destructured
        if (value.isBlank()) return null
        return if (name == "SID") value else "$name=$value"
    }

    /** Builds the Cookie header value for a stored session token. */
    fun cookieHeader(token: String): String = if ('=' in token) token else "SID=$token"
}
