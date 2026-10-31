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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import ir.mohammad.vpn.data.ImportBatch
import ir.mohammad.vpn.data.LogStore
import ir.mohammad.vpn.data.ProfileStore
import ir.mohammad.vpn.data.ProxyState
import ir.mohammad.vpn.proxy.DnsProxyService
import ir.mohammad.vpn.net.RealDelay
import ir.mohammad.vpn.ui.AppMode
import ir.mohammad.vpn.ui.DnsMode
import ir.mohammad.vpn.ui.DnsScreen
import ir.mohammad.vpn.ui.ProfilePing
import ir.mohammad.vpn.ui.TopBar
import ir.mohammad.vpn.data.OperatorBundleLoader
import ir.mohammad.vpn.data.Profile
import ir.mohammad.vpn.data.Tunnel
import ir.mohammad.vpn.data.TunnelError
import ir.mohammad.vpn.data.TunnelState
import ir.mohammad.vpn.surfaces.TunnelController
import ir.mohammad.vpn.diagnostics.CrashRecorder
import ir.mohammad.vpn.ui.MohammadScreen
import ir.mohammad.vpn.vpn.MohammadVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalClipboardManager
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class MainActivity : ComponentActivity() {

    internal var lastCrash: String? = null

    /** Bundle profiles only; imported ones are re-read from the store. */
    private var operatorProfiles: List<Profile> = emptyList()

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
                operatorProfiles = bundle.profiles
                Tunnel.profiles = operatorProfiles + ProfileStore.load(applicationContext)
                val savedId = prefs().getString(KEY_SELECTED, null)
                LogStore.add(
                    "I", "APP",
                    "imported profiles: ${Tunnel.profiles.size - bundle.profiles.size}",
                )
                Tunnel.selected = Tunnel.profiles.firstOrNull { it.id == savedId }
                    ?: Tunnel.profiles.firstOrNull { it.id == bundle.defaultProfileId }
                    ?: Tunnel.profiles.firstOrNull()
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
        prefs().edit { putString(KEY_SELECTED, profile.id) }
        LogStore.add("I", "APP", "selected profile ${profile.id}")
    }

    /**
     * Imports every share link in [text]. A clipboard usually holds one link, but "select all and
     * copy" from a channel holds several; both work.
     *
     * Returns a message for the UI and the number of profiles imported.
     */
    fun importLinks(text: String): Pair<String, Int> {
        val outcome = ImportBatch.parse(text)
        outcome.profiles.forEach { profile ->
            ProfileStore.add(applicationContext, profile)
            LogStore.add("I", "IMPORT", "imported ${profile.protocol} ${profile.id}")
        }
        if (outcome.profiles.isNotEmpty()) {
            Tunnel.profiles = operatorProfiles + ProfileStore.load(applicationContext)
            outcome.profiles.lastOrNull()?.let { selectProfile(it) }
        }
        outcome.failures.forEach { LogStore.add("W", "IMPORT", it) }
        return outcome.message to outcome.profiles.size
    }

    fun requestConnect() {
        if (!TunnelController.hasProfile()) {
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

    /**
     * The dial, the widget and the tile all enter the tunnel through [TunnelController]; this is the
     * only place an Activity adds anything (the consent dialog).
     */
    private fun startTunnel() {
        if (TunnelController.connect(this) == TunnelController.Outcome.NEEDS_PERMISSION) {
            VpnService.prepare(this)?.let { vpnPermissionLauncher.launch(it) }
        }
    }

    fun requestDisconnect() {
        TunnelController.disconnect(this)
    }

    /** First DNS profile, preferring the current selection. */
    internal fun dnsProfile(): Profile? =
        Tunnel.selected?.takeIf { it.core == "DNS" }
            ?: Tunnel.profiles.firstOrNull { it.core == "DNS" }

    /**
     * Mode 2 connect. Proxy mode never touches the VpnService: the engine listens on loopback and
     * the user points apps at it. Full tunnel uses exactly the same path as Mode 1.
     */
    fun startDns() {
        val profile = dnsProfile()
        if (profile == null) {
            LogStore.add("E", "APP", "no DNS profile to connect")
            return
        }
        if (Tunnel.selected?.id != profile.id) selectProfile(profile)
        when (DnsMode.from(prefs().getString("dns_mode", null))) {
            DnsMode.PROXY -> {
                // Never two cores: a live VPN is stopped before the proxy starts.
                if (Tunnel.state.value !is TunnelState.Disconnected) {
                    LogStore.add("I", "APP", "stopping the tunnel before starting the proxy")
                    requestDisconnect()
                }
                LogStore.add("I", "APP", "starting DNS proxy for ${profile.name}")
                DnsProxyService.start(this, profile)
            }
            DnsMode.VPN -> requestDisconnectIfProxy()
        }
    }

    /** Stops a running DNS proxy so a VPN connect cannot end up with two cores. */
    private fun requestDisconnectIfProxy() {
        val proxy = ProxyState.state.value
        if (proxy.running || proxy.starting) {
            LogStore.add("I", "APP", "stopping the DNS proxy before the tunnel starts")
            DnsProxyService.stop(this)
        }
        requestConnect()
    }

    fun stopDns() {
        val proxy = ProxyState.state.value
        if (proxy.running || proxy.starting) DnsProxyService.stop(this)
        if (Tunnel.state.value !is TunnelState.Disconnected) requestDisconnect()
    }

    fun stopDnsProxy() {
        val proxy = ProxyState.state.value
        if (proxy.running || proxy.starting) DnsProxyService.stop(this)
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
    var profiles by remember { mutableStateOf(Tunnel.profiles) }
    var pingMs by remember { mutableStateOf<Long?>(null) }
    var pingBusy by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var pingProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var appMode by remember { mutableStateOf(AppMode.from(activity.prefs().getString("app_mode", null))) }
    var dnsMode by remember { mutableStateOf(DnsMode.from(activity.prefs().getString("dns_mode", null))) }
    var modeMenuOpen by remember { mutableStateOf(false) }
    val proxy by ProxyState.state.collectAsStateWithLifecycle()
    val pingByProfile = remember { mutableStateMapOf<String, ProfilePing>() }
    val clipboard = LocalClipboardManager.current
    var crash by remember { mutableStateOf(activity.lastCrash) }
    var fragmentOn by remember { mutableStateOf(activity.prefs().getBoolean("fragment", false)) }
    var fragmentMode by remember { mutableStateOf(activity.prefs().getString("fragmentMode", "tlshello") ?: "tlshello") }
    var muxOn by remember { mutableStateOf(activity.prefs().getBoolean("mux", false)) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Tunnel.profiles) {
        profiles = Tunnel.profiles
        selected = Tunnel.selected
    }

    val topBar: @Composable () -> Unit = {
        TopBar(
            mode = appMode,
            version = "v" + (runCatching {
                activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
            }.getOrNull() ?: ""),
            menuOpen = modeMenuOpen,
            onToggleMenu = { modeMenuOpen = !modeMenuOpen },
            onPickMode = { picked ->
                modeMenuOpen = false
                if (picked != appMode) {
                    // Never two cores: leaving a mode stops whatever it started.
                    val sessionLive = proxy.running || proxy.starting ||
                        state is TunnelState.Connected || state is TunnelState.Connecting
                    if (sessionLive) {
                        activity.stopDns()
                        LogStore.add("I", "APP", "mode switch stopped the running session")
                    }
                    appMode = picked
                    activity.prefs().edit { putString("app_mode", picked.storageKey) }
                    LogStore.add("I", "APP", "mode ${picked.label}")
                }
            },
        )
    }

    if (appMode == AppMode.DNS) {
        DnsScreen(
            dnsProfiles = profiles.filter { it.core == "DNS" },
            selected = profiles.firstOrNull { it.id == selected?.id && it.core == "DNS" }
                ?: profiles.firstOrNull { it.core == "DNS" },
            dnsMode = dnsMode,
            tunnelState = state,
            proxy = proxy,
            logs = logs.filter { it.tag == "DNS" || it.tag == "DNS-PROXY" },
            onPickDnsMode = { picked ->
                if (picked != dnsMode) {
                    if (proxy.running || proxy.starting) activity.stopDnsProxy()
                    if (state is TunnelState.Connected) activity.requestDisconnect()
                    dnsMode = picked
                    activity.prefs().edit { putString("dns_mode", picked.storageKey) }
                    LogStore.add("I", "APP", "dns mode ${picked.label}")
                }
            },
            onSelectProfile = { profile ->
                activity.selectProfile(profile)
                selected = profile
            },
            onToggle = {
                val running = proxy.running || proxy.starting || state is TunnelState.Connected ||
                    state is TunnelState.Connecting
                if (running) activity.stopDns() else activity.startDns()
            },
            onCopyAddress = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(proxy.address))
                LogStore.add("I", "APP", "proxy address copied")
            },
            onPaste = {
                val raw = clipboard.getText()?.text
                if (raw.isNullOrBlank()) {
                    importMessage = "Clipboard is empty"
                } else {
                    val (message, count) = activity.importLinks(raw)
                    importMessage = message
                    if (count > 0) profiles = Tunnel.profiles
                }
            },
            importMessage = importMessage,
            topBar = topBar,
        )
        return@MohammadApp
    }

    MohammadScreen(
        state = state,
        traffic = traffic,
        logs = logs,
        profiles = profiles.filter { it.core != "DNS" },
        selected = selected,
        pingMs = pingMs,
        pingBusy = pingBusy,
        fragmentOn = fragmentOn,
        fragmentMode = fragmentMode,
        muxOn = muxOn,
        onFragment = {
            fragmentOn = it
            activity.prefs().edit { putBoolean("fragment", it) }
            if (state is TunnelState.Connected) LogStore.add("I", "APP", "fragment change applies on next connect")
        },
        onFragmentMode = {
            fragmentMode = it
            activity.prefs().edit { putString("fragmentMode", it) }
        },
        onMux = {
            muxOn = it
            activity.prefs().edit { putBoolean("mux", it) }
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
            if (pingBusy || profiles.isEmpty()) return@MohammadScreen
            pingBusy = true
            pingMs = null
            pingProgress = 0 to profiles.size
            profiles.forEach { pingByProfile[it.id] = ProfilePing(busy = true) }
            scope.launch {
                val snapshot = profiles
                RealDelay.measureAll(activity, snapshot) { index, total, profile, result ->
                    pingByProfile[profile.id] = if (result.ok) {
                        ProfilePing(ms = result.ms)
                    } else {
                        ProfilePing(error = result.reason)
                    }
                    if (result.ok) {
                        pingMs = result.ms
                    } else if (result.reason != RealDelay.SKIPPED) {
                        LogStore.add("W", "APP", "delay test ${profile.name}: ${result.reason}")
                    }
                    pingProgress = (index + 1) to total
                }
                LogStore.add("I", "APP", "delay test finished for ${snapshot.size} profile(s)")
                pingBusy = false
                pingProgress = null
            }
        },
        onPingProfile = { profile ->
            pingByProfile[profile.id] = ProfilePing(busy = true)
            scope.launch {
                val result = RealDelay.measure(activity, profile)
                pingByProfile[profile.id] = if (result.ok) {
                    ProfilePing(ms = result.ms)
                } else {
                    ProfilePing(error = result.reason)
                }
                if (!result.ok) {
                    LogStore.add("W", "APP", "delay test ${profile.id}: ${result.reason}")
                }
                pingMs = result.ms
            }
        },
        onPaste = {
            val raw = clipboard.getText()?.text
            if (raw.isNullOrBlank()) {
                importMessage = "Clipboard is empty"
            } else {
                val (message, count) = activity.importLinks(raw)
                importMessage = message
                if (count > 0) {
                    profiles = Tunnel.profiles
                    selected = Tunnel.selected
                    pingMs = null
                    if (Tunnel.selected?.core == "DNS") {
                        appMode = AppMode.DNS
                        activity.prefs().edit { putString("app_mode", AppMode.DNS.storageKey) }
                        LogStore.add("I", "APP", "dns profile imported; switched to Mode 2")
                    }
                }
            }
        },
        pingByProfile = pingByProfile,
        importMessage = importMessage,
        pingProgress = pingProgress,
        topBar = topBar,
    )
}
