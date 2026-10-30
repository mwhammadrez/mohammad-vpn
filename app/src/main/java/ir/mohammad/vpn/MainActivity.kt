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
import androidx.activity.enableEdgeToEdge
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
import ir.mohammad.vpn.diagnostics.CrashRecorder
import ir.mohammad.vpn.diagnostics.SelfTest
import ir.mohammad.vpn.ui.MohammadScreen
import ir.mohammad.vpn.vpn.MohammadVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.net.Socket

class MainActivity : ComponentActivity() {

    internal var lastCrash: String? = null

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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        CrashRecorder.install(this)
        lastCrash = CrashRecorder.take(this)
        requestNotificationPermission()
        loadProfiles()
        captureBaselineEgress()
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
                Tunnel.ipv6Enabled = bundle.tunIpv6
                Tunnel.tunIndex = bundle.tunAddressIndex
                Tunnel.geositeUrl = bundle.geositeUrl
                Tunnel.geoipUrl = bundle.geoipUrl
                if (!ir.mohammad.vpn.core.GeoAssets.isReady(applicationContext)) {
                    LogStore.add("I", "GEO", "rule sets not cached; will download in background")
                }
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

    internal fun prefs() = getSharedPreferences("mohammad", Context.MODE_PRIVATE)

    /**
     * Records the carrier egress IP while disconnected. The self-test compares it
     * with the IP seen while connected, which is the only proof that traffic
     * really leaves through the server.
     */
    private fun captureBaselineEgress() {
        if (Tunnel.baselineEgressIp != null) return
        lifecycleScope.launch {
            val ip = withContext(Dispatchers.IO) {
                runCatching {
                    val connection = (java.net.URL("https://ifconfig.co/json").openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 8_000
                        readTimeout = 8_000
                        setRequestProperty("User-Agent", "Mohammad-SelfTest")
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    connection.disconnect()
                    org.json.JSONObject(body).optString("ip").takeIf { it.isNotBlank() }
                }.getOrNull()
            }
            if (ip != null) {
                Tunnel.baselineEgressIp = ip
                LogStore.add("I", "TEST", "baseline egress captured")
            } else {
                LogStore.add("W", "TEST", "baseline egress unavailable")
            }
        }
    }

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
    val traffic by Tunnel.traffic.collectAsStateWithLifecycle()

    var selected by remember { mutableStateOf(Tunnel.selected) }
    var pingMs by remember { mutableStateOf<Long?>(null) }
    var pingBusy by remember { mutableStateOf(false) }
    var testRunning by remember { mutableStateOf(false) }
    var testReport by remember { mutableStateOf<String?>(null) }
    var crash by remember { mutableStateOf(activity.lastCrash) }
    var fragmentOn by remember { mutableStateOf(activity.prefs().getBoolean("fragment", false)) }
    var fragmentMode by remember { mutableStateOf(activity.prefs().getString("fragmentMode", "tlshello") ?: "tlshello") }
    var muxOn by remember { mutableStateOf(activity.prefs().getBoolean("mux", false)) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Tunnel.profiles) { selected = Tunnel.selected }

    MohammadScreen(
        state = state,
        traffic = traffic,
        logs = logs,
        profiles = Tunnel.profiles,
        selected = selected,
        pingMs = pingMs,
        pingBusy = pingBusy,
        testRunning = testRunning,
        testReport = testReport,
        fragmentOn = fragmentOn,
        fragmentMode = fragmentMode,
        muxOn = muxOn,
        onFragment = {
            fragmentOn = it
            activity.prefs().edit().putBoolean("fragment", it).apply()
            if (state is TunnelState.Connected) LogStore.add("I", "APP", "fragment change applies on next connect")
        },
        onFragmentMode = {
            fragmentMode = it
            activity.prefs().edit().putString("fragmentMode", it).apply()
        },
        onMux = {
            muxOn = it
            activity.prefs().edit().putBoolean("mux", it).apply()
            if (state is TunnelState.Connected) LogStore.add("I", "APP", "mux change applies on next connect")
        },
        crashReport = crash,
        onDismissCrash = { crash = null },
        onToggle = {
            when (state) {
                is TunnelState.Connected, is TunnelState.Connecting, is TunnelState.Disconnecting ->
                    activity.requestDisconnect()
                else -> activity.requestConnect()
            }
        },
        onSelect = { profile ->
            activity.selectProfile(profile)
            selected = profile
            pingMs = null
        },
        onPing = {
            val profile = selected ?: return@MohammadScreen
            pingBusy = true
            pingMs = null
            scope.launch {
                val ms = measure(profile, 5_000)
                pingMs = ms
                if (ms < 0) LogStore.add("W", "APP", "ping failed or timed out")
                pingBusy = false
            }
        },
        onSelfTest = {
            testRunning = true
            testReport = null
            scope.launch {
                testReport = runCatching { SelfTest.run(context.applicationContext) }
                    .getOrElse { "self-test failed: ${it.javaClass.simpleName}: ${it.message}" }
                testRunning = false
                runCatching {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Mohammad self-test", testReport ?: ""))
                }
            }
        },
    )
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
