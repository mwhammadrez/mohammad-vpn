package ir.mohammad.vpn

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import ir.mohammad.vpn.data.LogStore
import ir.mohammad.vpn.data.OperatorBundleLoader
import ir.mohammad.vpn.data.Profile
import ir.mohammad.vpn.data.Tunnel
import ir.mohammad.vpn.data.TunnelError
import ir.mohammad.vpn.data.TunnelState
import ir.mohammad.vpn.vpn.MohammadVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.net.Socket

class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Tunnel.setPermissionDenied(false)
            startTunnel()
        } else {
            LogStore.add("E", "APP", "VPN permission denied")
            Tunnel.setPermissionDenied(true)
            Tunnel.update(TunnelState.Disconnected)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        LogStore.add("D", "APP", "notification permission granted=$granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        loadProfiles()
        setContent { MohammadApp() }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun loadProfiles() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    withTimeout(5_000) { OperatorBundleLoader.load(applicationContext) }
                }
            }
            result.onSuccess { bundle ->
                Tunnel.profiles = bundle.profiles
                Tunnel.defaultDns = bundle.dnsServers
                Tunnel.defaultMtu = bundle.mtu
                val savedId = prefs().getString(KEY_SELECTED, null)
                Tunnel.selected = bundle.profiles.firstOrNull { it.id == savedId }
                    ?: bundle.profiles.firstOrNull { it.id == bundle.defaultProfileId }
                    ?: bundle.profiles.firstOrNull()
                LogStore.add(
                    "I", "APP",
                    "bundle loaded: ${bundle.profiles.size} profile(s), mtu=${bundle.mtu}, dns=${bundle.dnsServers.size}",
                )
            }.onFailure { e ->
                LogStore.add("E", "APP", "configuration unavailable: ${e.javaClass.simpleName}: ${e.message}")
                Tunnel.update(TunnelState.Failed(TunnelError.InvalidProfile))
            }
        }
    }

    private fun prefs() = getSharedPreferences("mohammad", Context.MODE_PRIVATE)

    fun selectProfile(profile: Profile) {
        Tunnel.selected = profile
        prefs().edit().putString(KEY_SELECTED, profile.id).apply()
        LogStore.add("I", "APP", "selected profile ${profile.id}")
    }

    fun requestConnect() {
        if (Tunnel.selected == null) {
            Tunnel.update(TunnelState.Failed(TunnelError.NoProfileSelected))
            return
        }
        Tunnel.setPermissionDenied(false)
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startTunnel()
        }
    }

    private fun startTunnel() {
        val serviceIntent = Intent(this, MohammadVpnService::class.java).apply {
            action = MohammadVpnService.ACTION_CONNECT
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    fun requestDisconnect() {
        val serviceIntent = Intent(this, MohammadVpnService::class.java).apply {
            action = MohammadVpnService.ACTION_DISCONNECT
        }
        startService(serviceIntent)
    }

    private companion object {
        const val KEY_SELECTED = "selected_profile"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MohammadApp() {
    val context = LocalContext.current
    val activity = context as MainActivity
    val state by Tunnel.state.collectAsStateWithLifecycle()
    val logs by LogStore.entries.collectAsStateWithLifecycle()
    val permissionDenied by Tunnel.permissionDenied.collectAsStateWithLifecycle()
    val traffic by Tunnel.traffic.collectAsStateWithLifecycle()

    var selected by remember { mutableStateOf(Tunnel.selected) }
    var menuOpen by remember { mutableStateOf(false) }
    var pingText by remember { mutableStateOf("") }
    var pingEnabled by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Tunnel.profiles) { selected = Tunnel.selected }
    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.scrollToItem(logs.size - 1) }

    Scaffold(topBar = { TopAppBar(title = { Text("Mohammad") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(statusText(state), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(statusDetail(state, permissionDenied), style = MaterialTheme.typography.bodySmall)
                    if (state is TunnelState.Connected) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "↓ ${humanBytes(traffic.second)}   ↑ ${humanBytes(traffic.first)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            val connected = state is TunnelState.Connected
            val busy = state is TunnelState.Connecting || state is TunnelState.Disconnecting
            Button(
                onClick = {
                    when {
                        connected || busy -> activity.requestDisconnect()
                        else -> activity.requestConnect()
                    }
                },
                enabled = !(state is TunnelState.Disconnecting) && (selected != null || connected),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(buttonText(state))
            }

            Box {
                OutlinedButton(
                    onClick = { menuOpen = true },
                    enabled = !connected && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(selected?.let { "${it.name} · ${it.protocol}" } ?: "Select a profile to connect.")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    Tunnel.profiles.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text("${profile.name} · ${profile.protocol} · ${profile.core}") },
                            onClick = {
                                activity.selectProfile(profile)
                                selected = profile
                                menuOpen = false
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        val profile = selected ?: return@OutlinedButton
                        pingEnabled = false
                        pingText = "Pinging…"
                        scope.launch {
                            val ms = measure(profile, 5_000)
                            pingText = if (ms >= 0) "Ping: $ms ms" else "Ping failed"
                            if (ms < 0) LogStore.add("W", "APP", "ping failed or timed out")
                            pingEnabled = true
                        }
                    },
                    enabled = pingEnabled && selected != null && !busy,
                ) { Text("Test ping") }
                Text(pingText, style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Connection log (${logs.size})", fontWeight = FontWeight.SemiBold)
                Row {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Mohammad log", LogStore.dump()))
                        LogStore.add("I", "APP", "log copied to clipboard")
                    }) { Text("Copy") }
                    TextButton(onClick = { LogStore.clear() }) { Text("Clear log") }
                }
            }

            Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    items(logs) { entry ->
                        Text(
                            text = "${entry.time} ${entry.level}/${entry.tag}  ${entry.message}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

private suspend fun measure(profile: Profile, timeoutMs: Int): Long = withContext(Dispatchers.IO) {
    val started = System.nanoTime()
    try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(profile.host, profile.port), timeoutMs)
        }
        (System.nanoTime() - started) / 1_000_000
    } catch (e: Exception) {
        -1L
    }
}

private fun humanBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb)
    return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
}

private fun statusText(state: TunnelState): String = when (state) {
    TunnelState.Disconnected -> "Disconnected"
    TunnelState.Connecting -> "Connecting…"
    TunnelState.Connected -> "Connected"
    TunnelState.Disconnecting -> "Disconnecting…"
    is TunnelState.Failed -> "Connection failed"
}

private fun statusDetail(state: TunnelState, permissionDenied: Boolean): String = when {
    permissionDenied && state is TunnelState.Disconnected -> "Allow VPN permission to connect."
    state is TunnelState.Failed -> when (state.reason) {
        TunnelError.NoProfileSelected -> "Select a profile to connect."
        TunnelError.VpnPermissionDenied -> "Allow VPN permission to connect."
        TunnelError.InvalidProfile -> "Configuration is unavailable. Contact the app operator."
        TunnelError.TunInterfaceFailed -> "Could not create the VPN interface."
        TunnelError.CoreStartFailed -> "Could not start the VPN engine."
        TunnelError.CoreCrashed -> "The VPN engine stopped unexpectedly."
        else -> "Connection failed."
    }
    state is TunnelState.Connected -> Tunnel.selected?.name ?: ""
    else -> "Ready"
}

private fun buttonText(state: TunnelState): String = when (state) {
    TunnelState.Disconnected -> "Connect"
    TunnelState.Connecting -> "Cancel"
    TunnelState.Connected -> "Disconnect"
    TunnelState.Disconnecting -> "Disconnecting…"
    is TunnelState.Failed -> "Retry"
}
