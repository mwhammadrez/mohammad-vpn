package ir.mohammad.vpn.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ir.mohammad.vpn.data.PanelApi
import ir.mohammad.vpn.data.PanelProvisioner
import ir.mohammad.vpn.data.PanelStore
import ir.mohammad.vpn.ui.theme.Chakra
import ir.mohammad.vpn.ui.theme.ChakraSemi
import ir.mohammad.vpn.ui.theme.Orbitron
import ir.mohammad.vpn.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pre-filled token creation screen: Workers Scripts:Edit + D1:Edit + Account Settings:Read.
 * Opening this link is step one; the user copies the token it generates back into the app.
 */
private const val CLOUDFLARE_TOKEN_URL =
    "https://dash.cloudflare.com/profile/api-tokens?permissionGroupKeys=%5B%7B%22key%22%3A%22workers_scripts%22%2C%22type%22%3A%22edit%22%7D%2C%7B%22key%22%3A%22d1%22%2C%22type%22%3A%22edit%22%7D%2C%7B%22key%22%3A%22account_settings%22%2C%22type%22%3A%22read%22%7D%5D&accountId=*&zoneId=all&name=Mohammad"

/**
 * The Material default paints entered text with `onSurface`, which on this near-black card comes out
 * darker than the placeholder — the panel password was unreadable. These are explicit so a value
 * always wins over a hint.
 */
@Composable
private fun panelFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Palette.TextPrimary,
    unfocusedTextColor = Palette.TextPrimary,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedBorderColor = Palette.Mint,
    unfocusedBorderColor = Palette.BorderSubtle,
    focusedLabelColor = Palette.Mint,
    unfocusedLabelColor = Palette.TextMuted,
    cursorColor = Palette.Mint,
    focusedPlaceholderColor = Palette.TextMuted,
    unfocusedPlaceholderColor = Palette.TextMuted,
    focusedSupportingTextColor = Palette.TextMuted,
    unfocusedSupportingTextColor = Palette.TextMuted,
    focusedTrailingIconColor = Palette.Mint,
    unfocusedTrailingIconColor = Palette.TextSecondary,
)

private enum class Phase { FORM, RUNNING, PANEL }

/**
 * Mode 3 — the panel builder.
 *
 * Three stages, strictly in order:
 *  1. the user opens Cloudflare with the permissions already ticked and copies a token;
 *  2. the token goes straight to api.cloudflare.com and installs a panel and a proxy node in the
 *     user's own account; the progress list names each step as it actually completes;
 *  3. the form disappears and the panel itself is shown: create users, copy links, connect.
 */
@Composable
fun PanelScreen(
    onOpenUrl: (String) -> Unit,
    onConnect: (String) -> Boolean,
    topBar: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var account by remember { mutableStateOf(PanelStore.account(context)) }
    var session by remember { mutableStateOf(PanelStore.session(context)) }
    var phase by remember { mutableStateOf(if (account != null && session != null) Phase.PANEL else Phase.FORM) }

    var token by remember { mutableStateOf("") }
    var tokenVisible by remember { mutableStateOf(false) }
    var panelName by remember { mutableStateOf("mohammad") }
    var panelPassword by remember { mutableStateOf(PanelProvisioner.suggestPassword()) }
    var passwordVisible by remember { mutableStateOf(true) }
    var step by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    var users by remember { mutableStateOf<List<PanelApi.User>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var needPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    var showAdd by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var deleteToken by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    suspend fun refresh() {
        val acc = account ?: return
        val s = session ?: return
        loading = true
        try {
            users = withContext(Dispatchers.IO) { PanelApi.users(acc.baseUrl, s) }
            needPassword = false
        } catch (e: PanelApi.PanelException) {
            // A session lasts seven days. The stored password turns that expiry into a silent re-login
            // instead of a screen that asks for something the user may no longer remember.
            if (e.code == 401 && acc.password.isNotEmpty()) {
                try {
                    val renewed = withContext(Dispatchers.IO) { PanelApi.login(acc.baseUrl, acc.password) }
                    PanelStore.saveSession(context, renewed)
                    session = renewed
                    users = withContext(Dispatchers.IO) { PanelApi.users(acc.baseUrl, renewed) }
                    needPassword = false
                } catch (again: Exception) {
                    needPassword = true
                }
            } else if (e.code == 401) {
                needPassword = true
            } else {
                error = e.message
            }
        } catch (e: Exception) {
            error = e.message ?: "Network error"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(account, session) {
        if (account != null && session != null) refresh()
    }

    // Mode 1 and Mode 2 draw inside `systemBarsPadding()`. This screen must do the same: without it
    // the header — and the chevron that is the only way out of Mode 3 — sits under the status bar and
    // cannot be tapped, which traps the user in this mode.
    Box(Modifier.fillMaxSize().background(Palette.BgDeep).systemBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            topBar()
            Spacer(Modifier.height(12.dp))

            Box(Modifier.weight(1f)) {
                when {
            phase == Phase.FORM -> SetupForm(
                token = token,
                tokenVisible = tokenVisible,
                onToken = { token = it; error = null },
                onToggleVisible = { tokenVisible = !tokenVisible },
                name = panelName,
                onName = { panelName = it.filter { ch -> ch.isLetterOrDigit() || ch == '-' }; error = null },
                password = panelPassword,
                passwordVisible = passwordVisible,
                onPassword = { panelPassword = it; error = null },
                onTogglePassword = { passwordVisible = !passwordVisible },
                onOpenCloudflare = { onOpenUrl(CLOUDFLARE_TOKEN_URL) },
                onConnect = {
                    val trimmed = token.trim()
                    if (trimmed.length < 20) {
                        error = "That does not look like a Cloudflare token"
                    } else if (panelPassword.length < 6) {
                        error = "The panel password needs at least 6 characters"
                    } else {
                        error = null
                        step = 0
                        phase = Phase.RUNNING
                        scope.launch {
                            try {
                                val created = withContext(Dispatchers.IO) {
                                    PanelProvisioner(context.applicationContext, trimmed)
                                        .provision(panelName, panelPassword) { index -> step = index }
                                }
                                val signed = withContext(Dispatchers.IO) {
                                    PanelApi.login(created.account.baseUrl, created.account.password)
                                }
                                PanelStore.save(context, created.account, signed)
                                account = created.account
                                session = signed
                                token = ""
                                phase = Phase.PANEL
                                refresh()
                                notice = "Panel ready on ${created.accountName}"
                            } catch (e: PanelApi.PanelException) {
                                phase = Phase.FORM
                                error = e.message ?: "Could not create the panel"
                            } catch (e: Exception) {
                                phase = Phase.FORM
                                error = e.message ?: "Could not create the panel"
                            }
                        }
                    }
                },
                error = error,
            )

            phase == Phase.RUNNING -> ProgressCard(step = step, error = error)

            needPassword -> PasswordCard(
                panelUrl = account?.panelUrl.orEmpty(),
                password = password,
                onPassword = { password = it; error = null },
                error = error,
                onLogin = {
                    val acc = account ?: return@PasswordCard
                    scope.launch {
                        try {
                            val signed = withContext(Dispatchers.IO) { PanelApi.login(acc.baseUrl, password) }
                            PanelStore.saveSession(context, signed)
                            session = signed
                            password = ""
                            needPassword = false
                            error = null
                            refresh()
                        } catch (e: Exception) {
                            error = e.message ?: "Login failed"
                        }
                    }
                },
                onForget = {
                    PanelStore.clear(context)
                    account = null
                    session = null
                    users = emptyList()
                    phase = Phase.FORM
                },
            )

            else -> PanelDashboard(
                account = account,
                users = users,
                loading = loading,
                notice = notice,
                onRefresh = { scope.launch { refresh() } },
                onCopy = { value ->
                    clipboard.setText(AnnotatedString(value))
                    toast("Copied")
                },
                onOpenPanel = { account?.panelUrl?.let(onOpenUrl) },
                onAddUser = { showAdd = true },
                onToggle = { user ->
                    val acc = account ?: return@PanelDashboard
                    val s = session ?: return@PanelDashboard
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { PanelApi.setEnabled(acc.baseUrl, s, user.id, !user.enabled) }
                            refresh()
                        } catch (e: Exception) {
                            error = e.message
                        }
                    }
                },
                onReset = { user ->
                    val acc = account ?: return@PanelDashboard
                    val s = session ?: return@PanelDashboard
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { PanelApi.resetUsage(acc.baseUrl, s, user.id) }
                            refresh()
                        } catch (e: Exception) {
                            error = e.message
                        }
                    }
                },
                onDelete = { user ->
                    val acc = account ?: return@PanelDashboard
                    val s = session ?: return@PanelDashboard
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { PanelApi.deleteUser(acc.baseUrl, s, user.id) }
                            refresh()
                        } catch (e: Exception) {
                            error = e.message
                        }
                    }
                },
                onCopySub = { user ->
                    val acc = account ?: return@PanelDashboard
                    clipboard.setText(AnnotatedString(PanelApi.subLink(acc.baseUrl, user.subToken)))
                    toast("Subscription link copied")
                },
                onConnect = { user ->
                    val acc = account ?: return@PanelDashboard
                    val host = hostOf(acc.nodeUrl ?: acc.baseUrl)
                    val ok = onConnect(PanelApi.vlessLink(user, host))
                    toast(if (ok) "Connecting…" else "Could not import the profile")
                },
                onDeletePanel = { showDelete = true; deleteError = null },
                        error = error,
                    )
                }
            }
        }
    }

    if (showDelete) {
        DeletePanelDialog(
            worker = account?.worker.orEmpty(),
            token = deleteToken,
            onToken = { deleteToken = it; deleteError = null },
            busy = deleting,
            error = deleteError,
            onDelete = {
                val worker = account?.worker.orEmpty()
                val trimmed = deleteToken.trim()
                if (trimmed.length < 20) {
                    deleteError = "That does not look like a Cloudflare token"
                } else {
                    deleting = true
                    scope.launch {
                        try {
                            val message = withContext(Dispatchers.IO) {
                                PanelProvisioner(context.applicationContext, trimmed).delete(worker)
                            }
                            PanelStore.clear(context)
                            account = null
                            session = null
                            users = emptyList()
                            deleteToken = ""
                            showDelete = false
                            phase = Phase.FORM
                            notice = message
                        } catch (e: Exception) {
                            deleteError = e.message ?: "Could not delete the panel"
                        } finally {
                            deleting = false
                        }
                    }
                }
            },
            onForget = {
                PanelStore.clear(context)
                account = null
                session = null
                users = emptyList()
                deleteToken = ""
                showDelete = false
                phase = Phase.FORM
                notice = "Panel forgotten on this device"
            },
            onDismiss = { showDelete = false },
        )
    }

    if (showAdd) {
        AddUserDialog(
            onDismiss = { showAdd = false },
            onCreate = { name, gb, days ->
                showAdd = false
                val acc = account ?: return@AddUserDialog
                val s = session ?: return@AddUserDialog
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { PanelApi.addUser(acc.baseUrl, s, name, gb, days) }
                        refresh()
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            },
        )
    }
}

/** Strips the scheme and the path so the result can be used as the node host. */
private fun hostOf(url: String): String =
    url.removePrefix("https://").removePrefix("http://").trimEnd('/').substringBefore('/')

/* ───────────────────────── stage 1 ───────────────────────── */

@Composable
private fun SetupForm(
    token: String,
    tokenVisible: Boolean,
    onToken: (String) -> Unit,
    onToggleVisible: () -> Unit,
    name: String,
    onName: (String) -> Unit,
    password: String,
    passwordVisible: Boolean,
    onPassword: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onOpenCloudflare: () -> Unit,
    onConnect: () -> Unit,
    error: String?,
) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("YOUR OWN PANEL", "Run a VLESS/Trojan panel on your Cloudflare account — no server, no VPS.")

        Spacer(Modifier.height(16.dp))
        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepBadge("1")
                Spacer(Modifier.width(10.dp))
                Text("GET A TOKEN", fontFamily = ChakraSemi, fontSize = 12.sp, letterSpacing = 1.6.sp, color = Palette.Mint)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "The link below opens Cloudflare with the three permissions this needs already selected. " +
                        "Sign in, press Continue, then Create token and copy it.",
                fontSize = 12.sp, color = Palette.TextSecondary, lineHeight = 18.sp,
            )
            Spacer(Modifier.height(12.dp))
            PanelButton(label = "Open Cloudflare", accent = Palette.Mint, onClick = onOpenCloudflare)
            Spacer(Modifier.height(6.dp))
            Text(
                "Workers Scripts · Edit   +   D1 · Edit   +   Account Settings · Read",
                fontFamily = Chakra, fontSize = 10.sp, color = Palette.TextMuted,
            )
        }

        Spacer(Modifier.height(12.dp))
        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepBadge("2")
                Spacer(Modifier.width(10.dp))
                Text("PASTE THE TOKEN", fontFamily = ChakraSemi, fontSize = 12.sp, letterSpacing = 1.6.sp, color = Palette.Mint)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                            colors = panelFieldColors(),
                value = token,
                onValueChange = onToken,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API token") },
                singleLine = true,
                visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            val text = clipboard.getText()?.text?.trim().orEmpty()
                            if (text.isNotEmpty()) onToken(text)
                        }) {
                            Text("PASTE", fontFamily = ChakraSemi, fontSize = 9.sp, letterSpacing = 0.8.sp, color = Palette.Mint)
                        }
                        TextButton(onClick = onToggleVisible) {
                            Text(if (tokenVisible) "HIDE" else "SHOW", fontFamily = Chakra, fontSize = 9.sp)
                        }
                    }
                },
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "PANEL NAME",
                fontFamily = Chakra, fontSize = 10.sp, letterSpacing = 1.2.sp, color = Palette.TextMuted,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                colors = panelFieldColors(),
                value = name,
                onValueChange = onName,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("mohammad") },
                singleLine = true,
                supportingText = {
                    Text(
                        "Worker name on your account: $name",
                        fontFamily = Chakra, fontSize = 10.sp, color = Palette.TextMuted,
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
            )

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "PANEL PASSWORD",
                    fontFamily = Chakra, fontSize = 10.sp, letterSpacing = 1.2.sp, color = Palette.TextMuted,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { clipboard.setText(AnnotatedString(password)) }) {
                    Text("COPY", fontFamily = Chakra, fontSize = 10.sp)
                }
            }
            OutlinedTextField(
                            colors = panelFieldColors(),
                value = password,
                onValueChange = onPassword,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                supportingText = {
                    Text(
                        "Opens the web panel and re-signs this app in. Save it.",
                        fontFamily = Chakra, fontSize = 10.sp, color = Palette.TextMuted,
                    )
                },
                trailingIcon = {
                    TextButton(onClick = onTogglePassword) {
                        Text(if (passwordVisible) "HIDE" else "SHOW", fontFamily = Chakra, fontSize = 10.sp)
                    }
                },
            )

            Spacer(Modifier.height(14.dp))
            PanelButton(label = "CONNECT", accent = Palette.Mint, onClick = onConnect)
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Palette.Rose, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "The token goes from this phone straight to Cloudflare and is never stored. " +
                "The workers and the database are created inside your own account.",
            fontSize = 11.sp, color = Palette.TextMuted, lineHeight = 16.sp,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProgressCard(step: Int, error: String?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("CONNECTING TO CLOUDFLARE", "Creating your panel — this usually takes 10 to 30 seconds.")
        Spacer(Modifier.height(20.dp))
        PanelCard {
            val labels = PanelProvisioner.STEPS
            val current = step + 1
            labels.forEachIndexed { index, label ->
                val position = index + 1
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when {
                        position < current -> Text("✓", color = Palette.Mint, fontSize = 14.sp)
                        position == current -> CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Palette.Mint,
                        )
                        else -> Box(
                            Modifier.size(12.dp).clip(CircleShape).background(Color(0x22FFFFFF)),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        label,
                        fontSize = 13.sp,
                        color = when {
                            position < current -> Palette.TextPrimary
                            position == current -> Palette.Mint
                            else -> Palette.TextMuted
                        },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (current - 1).coerceIn(0, labels.size) / labels.size.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = Palette.Mint,
            )
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Palette.Rose, fontSize = 12.sp)
            }
        }
    }
}

/* ───────────────────────── stage 3 ───────────────────────── */

@Composable
private fun PanelDashboard(
    account: PanelApi.Account?,
    users: List<PanelApi.User>,
    loading: Boolean,
    notice: String?,
    onRefresh: () -> Unit,
    onCopy: (String) -> Unit,
    onOpenPanel: () -> Unit,
    onAddUser: () -> Unit,
    onToggle: (PanelApi.User) -> Unit,
    onReset: (PanelApi.User) -> Unit,
    onDelete: (PanelApi.User) -> Unit,
    onCopySub: (PanelApi.User) -> Unit,
    onConnect: (PanelApi.User) -> Unit,
    onDeletePanel: () -> Unit,
    error: String?,
) {
    val used = users.sumOf { it.usedBytes }
    val active = users.count { !it.blocked }

    Column(Modifier.fillMaxSize().padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("YOUR PANEL", fontFamily = Orbitron, fontSize = 13.sp, letterSpacing = 2.sp, color = Palette.TextPrimary)
                Text(
                    account?.panelUrl.orEmpty(),
                    fontFamily = Chakra, fontSize = 10.sp, color = Palette.TextMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onOpenPanel) { Text("OPEN", fontFamily = Chakra, fontSize = 10.sp) }
            TextButton(onClick = onDeletePanel) { Text("DELETE", fontFamily = Chakra, fontSize = 10.sp, color = Palette.Rose) }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("USERS", users.size.toString(), Palette.Mint, Modifier.weight(1f))
            StatTile("ACTIVE", active.toString(), Palette.Violet, Modifier.weight(1f))
            StatTile("TRAFFIC", formatBytes(used), Palette.Amber, Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelButton(label = "ADD USER", accent = Palette.Mint, onClick = onAddUser, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            val clipboard = LocalClipboardManager.current
            TextButton(onClick = { account?.let { clipboard.setText(AnnotatedString(it.nodeUrl ?: it.baseUrl)) } }) {
                Text("NODE", fontFamily = Chakra, fontSize = 10.sp)
            }
        }

        notice?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Palette.Mint, fontSize = 11.sp)
        }
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Palette.Rose, fontSize = 11.sp)
        }

        Spacer(Modifier.height(10.dp))
        if (loading && users.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Palette.Mint, strokeWidth = 2.dp)
            }
        } else if (users.isEmpty()) {
            PanelCard {
                Text("No users yet. Create one to get a config.", fontSize = 12.sp, color = Palette.TextSecondary)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(users, key = { it.id }) { user ->
                    UserCard(
                        user = user,
                        onConnect = { onConnect(user) },
                        onCopySub = { onCopySub(user) },
                        onToggle = { onToggle(user) },
                        onReset = { onReset(user) },
                        onDelete = { onDelete(user) },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun UserCard(
    user: PanelApi.User,
    onConnect: () -> Unit,
    onCopySub: () -> Unit,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
) {
    val fraction = if (user.limitBytes > 0) (user.usedBytes.toFloat() / user.limitBytes).coerceIn(0f, 1f) else 0f

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Card)
            .border(1.dp, Palette.BorderSubtle, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                user.name,
                fontFamily = ChakraSemi, fontSize = 13.sp, color = Palette.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (user.blocked) "EXPIRED" else "ACTIVE",
                fontFamily = Chakra, fontSize = 9.sp, letterSpacing = 1.sp,
                color = if (user.blocked) Palette.Rose else Palette.Mint,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${formatBytes(user.usedBytes)} of ${if (user.limitBytes > 0) formatBytes(user.limitBytes) else "∞"}",
            fontSize = 11.sp, color = Palette.TextSecondary,
        )
        if (user.limitBytes > 0) {
            Spacer(Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = if (fraction > 0.9f) Palette.Rose else Palette.Mint,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (user.expiresAt > 0) "expires ${formatDate(user.expiresAt)}" else "no expiry",
            fontSize = 10.sp, color = Palette.TextMuted,
        )

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallAction("CONNECT", Palette.Mint, onConnect)
            SmallAction("SUB", Palette.Violet, onCopySub)
            SmallAction(if (user.enabled) "OFF" else "ON", Palette.TextSecondary, onToggle)
            SmallAction("RESET", Palette.TextSecondary, onReset)
            SmallAction("DEL", Palette.Rose, onDelete)
        }
    }
}

/* ───────────────────────── pieces ───────────────────────── */

@Composable
private fun PasswordCard(
    panelUrl: String,
    password: String,
    onPassword: (String) -> Unit,
    error: String?,
    onLogin: () -> Unit,
    onForget: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("SESSION EXPIRED", "Sign in again to manage $panelUrl")
        Spacer(Modifier.height(16.dp))
        PanelCard {
            OutlinedTextField(
                colors = panelFieldColors(),
                value = password,
                onValueChange = onPassword,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Panel password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            )
            Spacer(Modifier.height(12.dp))
            PanelButton(label = "SIGN IN", accent = Palette.Mint, onClick = onLogin)
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onForget) {
                Text("Forget this panel", fontSize = 11.sp, color = Palette.TextMuted)
            }
            error?.let { Text(it, color = Palette.Rose, fontSize = 12.sp) }
        }
    }
}

/**
 * Deleting a panel removes two workers and a database from Cloudflare, so the token is asked for
 * again. "Forget" is the local-only option: the panel keeps running for its users, this device just
 * stops remembering it.
 */
@Composable
private fun DeletePanelDialog(
    worker: String,
    token: String,
    onToken: (String) -> Unit,
    busy: Boolean,
    error: String?,
    onDelete: () -> Unit,
    onForget: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Palette.Card)
                .border(1.dp, Palette.BorderSubtle, RoundedCornerShape(20.dp))
                .padding(18.dp),
        ) {
            Text("DELETE PANEL", fontFamily = ChakraSemi, fontSize = 12.sp, letterSpacing = 1.6.sp, color = Palette.Rose)
            Spacer(Modifier.height(8.dp))
            Text(
                "This removes \"$worker\", \"$worker-node\" and the database from your Cloudflare account. " +
                    "Paste the token again to confirm.",
                fontSize = 12.sp, color = Palette.TextSecondary, lineHeight = 18.sp,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                colors = panelFieldColors(),
                value = token,
                onValueChange = onToken,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = {
                        val text = clipboard.getText()?.text?.trim().orEmpty()
                        if (text.isNotEmpty()) onToken(text)
                    }) { Text("PASTE", fontFamily = ChakraSemi, fontSize = 9.sp, color = Palette.Mint) }
                },
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Palette.Rose, fontSize = 11.sp)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PanelButton(
                    label = if (busy) "DELETING…" else "DELETE",
                    accent = Palette.Rose,
                    onClick = { if (!busy) onDelete() },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { if (!busy) onForget() }) {
                    Text("FORGET", fontFamily = Chakra, fontSize = 10.sp, color = Palette.TextMuted)
                }
            }
        }
    }
}

@Composable
private fun AddUserDialog(onDismiss: () -> Unit, onCreate: (String, Double, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var gb by remember { mutableStateOf("50") }
    var days by remember { mutableStateOf("30") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Palette.CardInner)
                .border(1.dp, Palette.BorderSubtle, RoundedCornerShape(18.dp))
                .padding(16.dp),
        ) {
            Text("NEW USER", fontFamily = Orbitron, fontSize = 13.sp, letterSpacing = 2.sp, color = Palette.TextPrimary)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                colors = panelFieldColors(),
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = gb,
                    onValueChange = { gb = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("GB (0 = ∞)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                colors = panelFieldColors(),
                    value = days,
                    onValueChange = { days = it.filter { c -> c.isDigit() } },
                    label = { Text("Days") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelButton(
                    label = "CREATE",
                    accent = Palette.Mint,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val clean = name.trim()
                        if (clean.length >= 2) {
                            onCreate(clean, gb.toDoubleOrNull() ?: 0.0, days.toIntOrNull() ?: 0)
                        }
                    },
                )
                TextButton(onClick = onDismiss) { Text("CANCEL", fontFamily = Chakra, fontSize = 10.sp) }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 2.sp, color = Palette.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontSize = 12.sp, color = Palette.TextSecondary, lineHeight = 18.sp)
    }
}

@Composable
private fun PanelCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Card)
            .border(1.dp, Palette.BorderSubtle, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) { content() }
}

@Composable
private fun StepBadge(number: String) {
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color(0x1F34F5C5)),
        contentAlignment = Alignment.Center,
    ) {
        Text(number, fontFamily = ChakraSemi, fontSize = 11.sp, color = Palette.Mint)
    }
}

@Composable
private fun PanelButton(
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = ChakraSemi, fontSize = 11.sp, letterSpacing = 1.6.sp, color = accent)
    }
}

@Composable
private fun SmallAction(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(label, fontFamily = Chakra, fontSize = 9.sp, letterSpacing = 0.8.sp, color = color)
    }
}

@Composable
private fun StatTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Card)
            .border(1.dp, Palette.BorderSubtle, RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        Text(label, fontFamily = Chakra, fontSize = 9.sp, letterSpacing = 1.2.sp, color = Palette.TextMuted)
        Spacer(Modifier.height(3.dp))
        Text(value, fontFamily = ChakraSemi, fontSize = 14.sp, color = accent, maxLines = 1)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes} B" else String.format("%.1f %s", value, units[unit])
}

private fun formatDate(epochMillis: Long): String {
    val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    return format.format(java.util.Date(epochMillis))
}
