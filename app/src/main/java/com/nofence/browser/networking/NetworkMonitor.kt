package com.nofence.browser.networking

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceNetworkStatus(
    val isConnected: Boolean = true,
    val isWifi: Boolean = false,
    val isCellular: Boolean = false,
    val isVpnActive: Boolean = false,
    val interfaceSummary: String = "Checking network..."
)

/**
 * Monitors Android device network transitions (`ConnectivityManager.NetworkCallback`)
 * so Tor and Proxy managers can detect network drops, Wi-Fi/Cellular handoffs, or VPN conflicts
 * and re-verify tunnels immediately.
 */
class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkStatus = MutableStateFlow(readCurrentStatus())
    val networkStatus: StateFlow<DeviceNetworkStatus> = _networkStatus.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _networkStatus.value = readCurrentStatus()
        }

        override fun onLost(network: Network) {
            _networkStatus.value = readCurrentStatus()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            _networkStatus.value = readCurrentStatus()
        }
    }

    fun startMonitoring() {
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }
    }

    fun stopMonitoring() {
        runCatching {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    private fun readCurrentStatus(): DeviceNetworkStatus {
        return try {
            val activeNetwork = connectivityManager.activeNetwork ?: return DeviceNetworkStatus(
                isConnected = false,
                interfaceSummary = "Offline (No active network)"
            )
            val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return DeviceNetworkStatus(
                isConnected = false,
                interfaceSummary = "No network capabilities"
            )
            val isConnected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

            val summary = buildList {
                if (isWifi) add("Wi-Fi")
                if (isCellular) add("Cellular")
                if (isVpn) add("System VPN")
                if (isEmpty() && isConnected) add("Connected")
            }.joinToString(" + ").ifEmpty { "Offline" }

            DeviceNetworkStatus(
                isConnected = isConnected,
                isWifi = isWifi,
                isCellular = isCellular,
                isVpnActive = isVpn,
                interfaceSummary = summary
            )
        } catch (_: Exception) {
            DeviceNetworkStatus(isConnected = true, interfaceSummary = "Unknown network")
        }
    }
}
