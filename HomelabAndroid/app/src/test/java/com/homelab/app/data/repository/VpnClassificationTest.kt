package com.homelab.app.data.repository

import com.homelab.app.data.repository.VpnStatusRepository.Companion.classifyVpn
import com.homelab.app.util.VpnProvider
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Test

class VpnClassificationTest {

    private fun ips(vararg values: String) = values.map { InetAddress.getByName(it) }

    @Test
    fun `ipsec interface is detected as IPsec`() {
        assertEquals(VpnProvider.IPSEC, classifyVpn("ipsec0", ips("10.10.10.2"), installed = emptySet()))
        assertEquals(VpnProvider.IPSEC, classifyVpn("xfrm1", ips("10.10.10.2"), installed = setOf(VpnProvider.WIREGUARD)))
    }

    @Test
    fun `tailscale ipv6 range wins even with netbird installed`() {
        val result = classifyVpn(
            "tun0",
            ips("100.101.102.103", "fd7a:115c:a1e0::1234"),
            installed = setOf(VpnProvider.TAILSCALE, VpnProvider.NETBIRD)
        )
        assertEquals(VpnProvider.TAILSCALE, result)
    }

    @Test
    fun `cgnat without tailscale ipv6 is netbird when netbird is installed`() {
        assertEquals(
            VpnProvider.NETBIRD,
            classifyVpn("tun0", ips("100.80.1.2"), installed = setOf(VpnProvider.NETBIRD, VpnProvider.TAILSCALE))
        )
        assertEquals(
            VpnProvider.NETBIRD,
            classifyVpn("tun0", ips("100.80.1.2"), installed = setOf(VpnProvider.NETBIRD))
        )
    }

    @Test
    fun `cgnat defaults to tailscale for headscale setups`() {
        assertEquals(VpnProvider.TAILSCALE, classifyVpn("tun0", ips("100.64.0.5"), installed = emptySet()))
    }

    @Test
    fun `address outside cgnat is not tailscale`() {
        // 100.128.x.x is public space, not 100.64.0.0/10.
        assertEquals(
            VpnProvider.WIREGUARD,
            classifyVpn("tun0", ips("100.128.0.1"), installed = setOf(VpnProvider.TAILSCALE, VpnProvider.WIREGUARD))
        )
    }

    @Test
    fun `plain tunnel resolves to the only non-mesh client installed`() {
        assertEquals(
            VpnProvider.WIREGUARD,
            classifyVpn("tun0", ips("10.8.0.2"), installed = setOf(VpnProvider.WIREGUARD, VpnProvider.TAILSCALE))
        )
    }

    @Test
    fun `zerotier rfc4193 address is detected`() {
        assertEquals(
            VpnProvider.ZEROTIER,
            classifyVpn("tun0", ips("10.147.17.5", "fd12:3456:789a:bcde:f099:9312:3456:789a"), installed = setOf(VpnProvider.WIREGUARD))
        )
    }

    @Test
    fun `ambiguous tunnel is reported as other`() {
        assertEquals(
            VpnProvider.OTHER,
            classifyVpn("tun0", ips("10.8.0.2"), installed = setOf(VpnProvider.WIREGUARD, VpnProvider.ZEROTIER))
        )
        assertEquals(VpnProvider.OTHER, classifyVpn("tun0", ips("10.8.0.2"), installed = emptySet()))
    }

    @Test
    fun `explicit interface names are trusted`() {
        assertEquals(VpnProvider.WIREGUARD, classifyVpn("wg0", ips("10.8.0.2"), installed = emptySet()))
        assertEquals(VpnProvider.ZEROTIER, classifyVpn("zt5u4y", ips("10.147.17.5"), installed = emptySet()))
    }

    @Test
    fun `autobrr release status prefers push result over filter status`() {
        assertEquals(AutobrrReleaseStatus.APPROVED, AutobrrReleaseStatus.from("PUSH_APPROVED", "FILTER_APPROVED"))
        assertEquals(AutobrrReleaseStatus.ERROR, AutobrrReleaseStatus.from("push_error", null))
        assertEquals(AutobrrReleaseStatus.FILTER_REJECTED, AutobrrReleaseStatus.from(null, "FILTER_REJECTED"))
        assertEquals(AutobrrReleaseStatus.PENDING, AutobrrReleaseStatus.from("", "FILTER_APPROVED"))
    }
}
