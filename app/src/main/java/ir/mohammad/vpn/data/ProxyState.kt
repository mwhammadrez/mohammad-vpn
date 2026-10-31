package ir.mohammad.vpn.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State of Mode 2's proxy: the DNS engine running without a VpnService.
 *
 * Separate from [Tunnel] because it is a different thing: no interface, no routes, no permission
 * dialog — just a listener on 127.0.0.1 that apps can point at.
 */
object ProxyState {

    data class Proxy(
        val running: Boolean = false,
        val starting: Boolean = false,
        val port: Int = 0,
        val profileId: String = "",
        val profileName: String = "",
        val error: String? = null,
    ) {
        val address: String get() = if (port > 0) "127.0.0.1:$port" else ""
    }

    private val _state = MutableStateFlow(Proxy())
    val state: StateFlow<Proxy> = _state.asStateFlow()

    fun starting(profileId: String, profileName: String, port: Int) {
        _state.value = Proxy(starting = true, port = port, profileId = profileId, profileName = profileName)
    }

    fun running(profileId: String, profileName: String, port: Int) {
        _state.value = Proxy(running = true, port = port, profileId = profileId, profileName = profileName)
    }

    fun stopped() {
        _state.value = Proxy()
    }

    fun failed(reason: String) {
        _state.value = Proxy(error = reason)
    }
}
