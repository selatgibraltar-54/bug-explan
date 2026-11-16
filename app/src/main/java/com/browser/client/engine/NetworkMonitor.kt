package com.browser.client.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionType {
    WIFI,
    CELLULAR,
    OTHER,
    NONE
}

data class NetworkStatus(
    val isConnected: Boolean,
    val connectionType: ConnectionType
)

class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkStatus = MutableStateFlow(readCurrentStatus())
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _networkStatus.value = readCurrentStatus()
        }

        override fun onLost(network: Network) {
            _networkStatus.value = readCurrentStatus()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            _networkStatus.value = readCurrentStatus()
        }
    }

    init {
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }
    }

    fun isOnlineNow(): Boolean {
        val status = readCurrentStatus()
        _networkStatus.value = status
        return status.isConnected
    }

    fun readCurrentStatus(): NetworkStatus {
        return runCatching {
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
                if (caps != null) {
                    return@runCatching evaluateCapabilities(caps)
                }
            }
            @Suppress("DEPRECATION")
            val legacyInfo = connectivityManager.activeNetworkInfo
            if (legacyInfo != null && legacyInfo.isConnected) {
                val type = when (legacyInfo.type) {
                    ConnectivityManager.TYPE_WIFI -> ConnectionType.WIFI
                    ConnectivityManager.TYPE_MOBILE -> ConnectionType.CELLULAR
                    else -> ConnectionType.OTHER
                }
                return@runCatching NetworkStatus(isConnected = true, connectionType = type)
            }
            NetworkStatus(isConnected = false, connectionType = ConnectionType.NONE)
        }.getOrDefault(NetworkStatus(isConnected = true, connectionType = ConnectionType.OTHER))
    }

    private fun evaluateCapabilities(caps: NetworkCapabilities): NetworkStatus {
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (!hasInternet) {
            return NetworkStatus(isConnected = false, connectionType = ConnectionType.NONE)
        }

        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR
            else -> ConnectionType.OTHER
        }
        return NetworkStatus(isConnected = true, connectionType = type)
    }

    fun unregister() {
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }
}
