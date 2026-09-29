package com.homelab.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.homelab.app.util.VpnProvider
import com.homelab.app.util.VpnStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Detects which VPN client is currently up (Tailscale/Headscale, NetBird, WireGuard, ZeroTier, IPsec IKEv2)
 * instead of assuming Tailscale.
 *
 * Android does not expose which app owns a VPN network, so the client is inferred from the VPN link
 * (interface name, addresses, platform VPN type) and from the known clients installed on the device.
 */
@Singleton
class VpnStatusRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val prefs = context.getSharedPreferences("vpn_status", Context.MODE_PRIVATE)

    private val _status = MutableStateFlow(VpnStatus.Inactive)
    val status: StateFlow<VpnStatus> = _status.asStateFlow()

    init {
        registerVpnCallback()
    }

    /** Re-evaluates the VPN state. Safe to call from any thread. */
    fun refresh() {
        val installed = installedProviders()
        val detected = try {
            detectActiveProvider(installed)
        } catch (_: Exception) {
            null
        }
        if (detected != null) {
            if (detected != VpnProvider.OTHER) {
                prefs.edit().putString(KEY_LAST_PROVIDER, detected.name).apply()
            }
            _status.value = VpnStatus(isActive = true, provider = detected)
        } else {
            _status.value = VpnStatus(isActive = false, provider = suggestedProvider(installed))
        }
    }

    private fun registerVpnCallback() {
        val cm = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        try {
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = refresh()
                override fun onLost(network: Network) = refresh()
                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = refresh()
            })
        } catch (_: Exception) {
            // Callback limits or restricted profiles: the on-resume refresh still keeps the state current.
        }
    }

    @Suppress("DEPRECATION")
    private fun detectActiveProvider(installed: Set<VpnProvider>): VpnProvider? {
        val cm = connectivityManager ?: return detectFromInterfaces(installed)
        val vpnNetwork = cm.allNetworks.firstOrNull { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        } ?: return detectFromInterfaces(installed)

        val link = cm.getLinkProperties(vpnNetwork)
        return classifyVpn(
            interfaceName = link?.interfaceName,
            addresses = link?.linkAddresses?.map { it.address }.orEmpty(),
            installed = installed
        )
    }

    /** Fallback when ConnectivityManager reports no VPN network (seen on some OEM builds). */
    private fun detectFromInterfaces(installed: Set<VpnProvider>): VpnProvider? {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return null
        for (networkInterface in interfaces) {
            if (!networkInterface.isUp || networkInterface.isLoopback) continue
            val name = networkInterface.name.orEmpty()
            if (!VPN_INTERFACE_PREFIXES.any { name.startsWith(it) }) continue
            val addresses = networkInterface.inetAddresses.toList().filterNot { it.isLinkLocalAddress }
            if (addresses.isEmpty()) continue
            return classifyVpn(name, addresses, installed = installed)
        }
        return null
    }

    private fun installedProviders(): Set<VpnProvider> {
        val pm = context.packageManager
        return VpnProvider.identifiable.filterTo(mutableSetOf()) { provider ->
            provider.packages.any { pkg -> pm.getLaunchIntentForPackage(pkg) != null }
        }
    }

    /** With no VPN up: the client last seen active, else the only known client installed. */
    private fun suggestedProvider(installed: Set<VpnProvider>): VpnProvider? {
        val last = prefs.getString(KEY_LAST_PROVIDER, null)
            ?.let { name -> VpnProvider.entries.firstOrNull { it.name == name } }
        if (last != null && (last in installed || last == VpnProvider.IPSEC)) return last
        return installed.singleOrNull()
    }

    companion object {
        private const val KEY_LAST_PROVIDER = "last_provider"
        private val VPN_INTERFACE_PREFIXES = listOf("tun", "wg", "ipsec", "ppp", "zt", "tailscale", "wt")

        /**
         * Infers the VPN client from what the VPN link looks like.
         *
         * - `ipsec*` / `xfrm*` links are IPsec: the built-in Android IKEv2 client creates them.
         * - `fd7a:115c:a1e0::/48` is Tailscale's own IPv6 range (Headscale uses it too).
         * - 100.64.0.0/10 without that IPv6 is NetBird or Tailscale, told apart by what is installed.
         * - ZeroTier's RFC 4193 addresses embed port 9993; `zt*` / `wg*` names are explicit.
         * - Otherwise the only matching installed client wins, or [VpnProvider.OTHER].
         */
        fun classifyVpn(
            interfaceName: String?,
            addresses: List<InetAddress>,
            installed: Set<VpnProvider>
        ): VpnProvider {
            val name = interfaceName.orEmpty().lowercase()
            if (name.startsWith("ipsec") || name.startsWith("xfrm")) return VpnProvider.IPSEC
            if (name.startsWith("tailscale")) return VpnProvider.TAILSCALE
            if (name.startsWith("wt")) return VpnProvider.NETBIRD
            if (name.startsWith("zt")) return VpnProvider.ZEROTIER
            if (name.startsWith("wg")) return VpnProvider.WIREGUARD

            if (addresses.any(::isTailscaleIpv6)) return VpnProvider.TAILSCALE
            if (addresses.any(::isCgnatIpv4)) {
                return when {
                    VpnProvider.NETBIRD in installed && VpnProvider.TAILSCALE !in installed -> VpnProvider.NETBIRD
                    VpnProvider.TAILSCALE in installed && VpnProvider.NETBIRD !in installed -> VpnProvider.TAILSCALE
                    // Both installed: Tailscale would also carry its IPv6 address, so this is NetBird.
                    VpnProvider.NETBIRD in installed -> VpnProvider.NETBIRD
                    else -> VpnProvider.TAILSCALE
                }
            }
            if (addresses.any(::isZeroTierIpv6)) return VpnProvider.ZEROTIER

            // Mesh clients always use the CGNAT range, so without it they are not the active VPN.
            val candidates = installed - VpnProvider.TAILSCALE - VpnProvider.NETBIRD
            return candidates.singleOrNull() ?: VpnProvider.OTHER
        }

        private fun isCgnatIpv4(address: InetAddress): Boolean {
            if (address !is Inet4Address) return false
            val bytes = address.address
            val first = bytes[0].toInt() and 0xFF
            val second = bytes[1].toInt() and 0xFF
            return first == 100 && second in 64..127
        }

        private fun isTailscaleIpv6(address: InetAddress): Boolean {
            if (address !is Inet6Address) return false
            val b = address.address
            return (b[0].toInt() and 0xFF) == 0xFD && (b[1].toInt() and 0xFF) == 0x7A &&
                (b[2].toInt() and 0xFF) == 0x11 && (b[3].toInt() and 0xFF) == 0x5C &&
                (b[4].toInt() and 0xFF) == 0xA1 && (b[5].toInt() and 0xFF) == 0xE0
        }

        private fun isZeroTierIpv6(address: InetAddress): Boolean {
            if (address !is Inet6Address) return false
            val b = address.address
            // RFC 4193 mode: fd + 64-bit network id + 0x9993 + node id.
            return (b[0].toInt() and 0xFF) == 0xFD && (b[9].toInt() and 0xFF) == 0x99 && (b[10].toInt() and 0xFF) == 0x93
        }
    }
}
