package com.homelab.app.util

/**
 * VPN clients the app knows how to recognise and open.
 * [packages] must also be declared in the manifest `<queries>` block to be visible on Android 11+.
 */
enum class VpnProvider(
    val displayName: String,
    val packages: List<String>,
    val iconUrl: String?
) {
    TAILSCALE(
        displayName = "Tailscale",
        packages = listOf("com.tailscale.ipn", "com.tailscale.ipn.beta"),
        iconUrl = "https://cdn.jsdelivr.net/gh/selfhst/icons/png/tailscale.png"
    ),
    NETBIRD(
        displayName = "NetBird",
        packages = listOf("io.netbird.client"),
        iconUrl = "https://cdn.jsdelivr.net/gh/selfhst/icons/png/netbird.png"
    ),
    WIREGUARD(
        displayName = "WireGuard",
        packages = listOf("com.wireguard.android"),
        iconUrl = "https://cdn.jsdelivr.net/gh/selfhst/icons/png/wireguard.png"
    ),
    ZEROTIER(
        displayName = "ZeroTier",
        packages = listOf("com.zerotier.one"),
        iconUrl = "https://cdn.jsdelivr.net/gh/selfhst/icons/png/zerotier.png"
    ),
    /** IKEv2/IPsec: the built-in Android VPN client, or strongSwan. */
    IPSEC(
        displayName = "IPsec IKEv2",
        packages = listOf("org.strongswan.android"),
        iconUrl = null
    ),
    /** A VPN is up but its client could not be identified. */
    OTHER(
        displayName = "VPN",
        packages = emptyList(),
        iconUrl = null
    );

    companion object {
        val identifiable: List<VpnProvider> = entries.filter { it != OTHER }
    }
}

data class VpnStatus(
    /** True when the device routes traffic through a VPN network. */
    val isActive: Boolean,
    /** The detected client when active; otherwise the installed client to suggest, if any. */
    val provider: VpnProvider?
) {
    companion object {
        val Inactive = VpnStatus(isActive = false, provider = null)
    }
}
