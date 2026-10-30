package ir.mohammad.vpn.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import ir.mohammad.vpn.data.LogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Observes system connectivity and exposes a single online flag. */
class NetMonitor(context: Context) {

    private val manager = context.getSystemService(ConnectivityManager::class.java)

    private val _online = MutableStateFlow(isCurrentlyOnline())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (!_online.value) LogStore.add("I", "NET", "network available")
            _online.value = true
        }

        override fun onLost(network: Network) {
            val stillOnline = isCurrentlyOnline()
            if (_online.value && !stillOnline) LogStore.add("W", "NET", "network lost")
            _online.value = stillOnline
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (validated && !_online.value) {
                _online.value = true
                LogStore.add("I", "NET", "network validated")
            }
        }
    }

    private var started = false

    /** The network that currently carries internet traffic, if any. */
    fun currentNetwork(): Network? {
        val active = manager.activeNetwork ?: return null
        val caps = manager.getNetworkCapabilities(active) ?: return null
        return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) active else null
    }

    fun start() {
        if (started) return
        started = true
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { manager.registerNetworkCallback(request, callback) }
            .onFailure { LogStore.add("W", "NET", "callback registration failed: ${it.message}") }
        LogStore.add("D", "NET", "monitor started online=${_online.value}")
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { manager.unregisterNetworkCallback(callback) }
    }

    private fun isCurrentlyOnline(): Boolean {
        val active = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
