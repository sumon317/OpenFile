package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.core.database.NetworkProfileEntity
import com.example.core.datastore.AppSettingsState
import com.example.core.model.FileReference
import com.example.core.model.ViewMode
import com.example.core.model.formatByteSize
import com.example.core.network.DiscoveredLanHost
import com.example.core.network.FtpServerEngine
import com.example.core.network.FtpServerRuntimeState
import com.example.ui.SshHostKeyPrompt
import com.example.ui.theme.ApkGreen
import com.example.ui.theme.FolderAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ThemeMode

/**
 * Network Hub screen combining:
 * 1. Local Wi-Fi FTP / FTPS Server Control Center (Foreground Service, Passive Port Range, LAN IP binding, Live Activity Logs)
 * 2. Remote FTP / FTPS / SFTP (with strict SSH Host-Key verification) / SMB2/3 Client Connections & LAN Scanner
 */
@Composable
fun NetworkScreen(
    ftpState: FtpServerRuntimeState,
    settings: AppSettingsState,
    profiles: List<NetworkProfileEntity>,
    discoveredHosts: List<DiscoveredLanHost>,
    isScanningLan: Boolean,
    sshHostKeyPrompt: SshHostKeyPrompt?,
    onStartFtpServer: (String) -> Unit,
    onStopFtpServer: () -> Unit,
    onUpdateFtpConfig: (Int, Int, Int, String, Boolean, Boolean, Boolean, Int, String) -> Unit,
    onSaveProfile: (String, String, String, Int, String, String, String, String, Boolean, Boolean) -> Unit,
    onDeleteProfile: (NetworkProfileEntity) -> Unit,
    onConnectProfile: (NetworkProfileEntity) -> Unit,
    onScanLan: () -> Unit,
    onTrustSshHostKey: (SshHostKeyPrompt) -> Unit,
    onDismissSshPrompt: () -> Unit,
    initialTab: Int = 0,
    savedFtpPassword: String = "openfile123",
    onSaveFtpConfig: (Int, Int, Int, String, String, Boolean, Boolean, Boolean, Int, String) -> Unit =
        { port, pStart, pEnd, user, _, anon, write, tls, timeout, root ->
            onUpdateFtpConfig(port, pStart, pEnd, user, anon, write, tls, timeout, root)
        },
    onConnectDiscoveredHost: (String, String, String, Int, String, String, String, String, Boolean, Boolean) -> Unit =
        { name, proto, host, port, user, secret, remotePath, share, anon, pasv ->
            onSaveProfile(name, proto, host, port, user, secret, remotePath, share, anon, pasv)
        }
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var selectedDiscoveredHost by remember { mutableStateOf<DiscoveredLanHost?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("network_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                label = { Text("Wi-Fi FTP Server") },
                modifier = Modifier.testTag("network_tab_ftp_server")
            )
            FilterChip(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                label = { Text("Remote Connections (FTP / SFTP / SMB)") },
                modifier = Modifier.testTag("network_tab_remote_clients")
            )
        }
        HorizontalDivider()

        if (selectedTab == 0) {
            FtpServerControlTab(
                ftpState = ftpState,
                settings = settings,
                initialPassword = savedFtpPassword,
                onStartFtpServer = onStartFtpServer,
                onStopFtpServer = onStopFtpServer,
                onUpdateFtpConfig = onUpdateFtpConfig,
                onSaveFtpConfig = onSaveFtpConfig
            )
        } else {
            RemoteClientsTab(
                profiles = profiles,
                discoveredHosts = discoveredHosts,
                isScanningLan = isScanningLan,
                onAddProfileClick = { showAddProfileDialog = true },
                onConnectProfile = onConnectProfile,
                onDeleteProfile = onDeleteProfile,
                onScanLan = onScanLan,
                onSelectDiscoveredHost = { host ->
                    // One-tap reconnect: reuse stored credentials instead of prompting again.
                    val saved = profiles.firstOrNull {
                        it.host.equals(host.ipAddress, ignoreCase = true) && it.port == host.port
                    }
                    if (saved != null && (saved.isAnonymous || !saved.secretAlias.isNullOrBlank())) {
                        onConnectProfile(saved)
                    } else {
                        selectedDiscoveredHost = host
                    }
                },
                onEditDiscoveredHost = { selectedDiscoveredHost = it },
                onEditProfile = { p ->
                    selectedDiscoveredHost = DiscoveredLanHost(
                        ipAddress = p.host,
                        port = p.port,
                        protocolGuess = p.protocol,
                        bannerOrName = p.displayName,
                        protocol = p.protocol.uppercase()
                    )
                }
            )
        }
    }

    if (showAddProfileDialog) {
        AddNetworkProfileDialog(
            onDismiss = { showAddProfileDialog = false },
            onSave = { name, proto, host, port, user, secret, remotePath, share, anon, pasv ->
                onSaveProfile(name, proto, host, port, user, secret, remotePath, share, anon, pasv)
                showAddProfileDialog = false
            }
        )
    }

    val hostToConnect = selectedDiscoveredHost
    if (hostToConnect != null) {
        val existingProfile = remember(hostToConnect, profiles) {
            profiles.firstOrNull {
                it.host.equals(hostToConnect.ipAddress, ignoreCase = true) &&
                    it.port == hostToConnect.port
            }
        }
        ConnectDiscoveredHostDialog(
            host = hostToConnect,
            initialUsername = existingProfile?.username?.ifBlank { "openfile" } ?: "openfile",
            initialShareName = existingProfile?.shareName.orEmpty(),
            initialAnonymous = existingProfile?.isAnonymous ?: false,
            hasSavedPassword = existingProfile?.secretAlias != null,
            onDismiss = { selectedDiscoveredHost = null },
            onConnect = { username, password, shareName, isAnonymous ->
                val proto = hostToConnect.protocol.uppercase().let {
                    if (it in setOf("FTP", "FTPS", "SFTP", "SMB")) it else "FTP"
                }
                val displayName = existingProfile?.displayName
                    ?: hostToConnect.bannerOrName.ifBlank { "$proto://${hostToConnect.ipAddress}:${hostToConnect.port}" }
                onConnectDiscoveredHost(
                    displayName,
                    proto,
                    hostToConnect.ipAddress,
                    hostToConnect.port,
                    username,
                    password,
                    "/",
                    shareName,
                    isAnonymous,
                    true
                )
                selectedDiscoveredHost = null
            }
        )
    }

    if (sshHostKeyPrompt != null) {
        AlertDialog(
            onDismissRequest = onDismissSshPrompt,
            icon = {
                Icon(
                    imageVector = if (sshHostKeyPrompt.isMismatchWarning) Icons.Filled.Warning else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (sshHostKeyPrompt.isMismatchWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    if (sshHostKeyPrompt.isMismatchWarning) {
                        "SSH Host Key Changed Warning!"
                    } else {
                        "Verify SSH Host Key Fingerprint"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (sshHostKeyPrompt.isMismatchWarning) {
                            "The remote SFTP server (${sshHostKeyPrompt.profile.host}:${sshHostKeyPrompt.profile.port}) presented a different host key than previously pinned. This could indicate a Man-in-the-Middle attack or server reinstall."
                        } else {
                            "First SFTP connection to ${sshHostKeyPrompt.profile.host}:${sshHostKeyPrompt.profile.port}. Verify the server's SHA-256 fingerprint before trusting:"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = sshHostKeyPrompt.sha256Fingerprint,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFamily,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(onClick = { onTrustSshHostKey(sshHostKeyPrompt) }) {
                    Text("Trust & Pin Host Key")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissSshPrompt) {
                    Text("Abort Connection")
                }
            }
        )
    }
}

@Composable
private fun FtpServerControlTab(
    ftpState: FtpServerRuntimeState,
    settings: AppSettingsState,
    initialPassword: String = "openfile123",
    onStartFtpServer: (String) -> Unit,
    onStopFtpServer: () -> Unit,
    onUpdateFtpConfig: (Int, Int, Int, String, Boolean, Boolean, Boolean, Int, String) -> Unit,
    onSaveFtpConfig: (Int, Int, Int, String, String, Boolean, Boolean, Boolean, Int, String) -> Unit =
        { port, pStart, pEnd, user, _, anon, write, tls, timeout, root ->
            onUpdateFtpConfig(port, pStart, pEnd, user, anon, write, tls, timeout, root)
        }
) {
    val context = LocalContext.current
    val resolvedLanIp by produceState(
        initialValue = ftpState.boundIpAddress.ifBlank { "127.0.0.1" },
        ftpState.isRunning
    ) {
        value = withContext(Dispatchers.IO) {
            FtpServerEngine.resolveLocalLanIpAddress()
        }
    }
    val removableVolumes by produceState(
        initialValue = emptyList<Pair<String, java.io.File>>(),
        ftpState.isRunning
    ) {
        value = withContext(Dispatchers.IO) {
            com.example.core.filesystem.LocalFileSystemProvider(context).discoverRemovableStorageDirectories()
        }
    }
    val initialRootMode = remember(settings.ftpRootPath) {
        val raw = settings.ftpRootPath.trim()
        if (raw.isEmpty() || raw.equals("ALL", ignoreCase = true) || raw.startsWith("/storage/emulated")) {
            "ALL"
        } else {
            raw
        }
    }
    var selectedRootMode by remember(initialRootMode) { mutableStateOf(initialRootMode) }
    var portText by remember(settings.ftpPort) { mutableStateOf(settings.ftpPort.toString()) }
    var pasvStartText by remember(settings.ftpPassivePortStart) { mutableStateOf(settings.ftpPassivePortStart.toString()) }
    var pasvEndText by remember(settings.ftpPassivePortEnd) { mutableStateOf(settings.ftpPassivePortEnd.toString()) }
    var username by remember(settings.ftpUsername) { mutableStateOf(settings.ftpUsername) }
    var password by remember(initialPassword) { mutableStateOf(initialPassword) }
    var passwordVisible by remember { mutableStateOf(false) }
    var allowAnonymous by remember(settings.ftpAnonymousAllowed) { mutableStateOf(settings.ftpAnonymousAllowed) }
    var allowWrite by remember(settings.ftpAllowWrite) { mutableStateOf(settings.ftpAllowWrite) }
    var timeoutMinText by remember(settings.ftpIdleTimeoutMinutes) { mutableStateOf(settings.ftpIdleTimeoutMinutes.toString()) }
    var isNoticeExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Collapsible Unencrypted FTP Security Warning Banner (collapsed by default)
        item {
            Card(
                onClick = { isNoticeExpanded = !isNoticeExpanded },
                colors = CardDefaults.cardColors(
                    containerColor = FolderAmber.copy(alpha = 0.14f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ftp_security_notice_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = FolderAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Security Notice",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = FolderAmber
                            )
                        }
                        IconButton(
                            onClick = { isNoticeExpanded = !isNoticeExpanded },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("ftp_security_notice_toggle")
                        ) {
                            Icon(
                                imageVector = if (isNoticeExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (isNoticeExpanded) "Collapse security notice" else "Expand security notice",
                                tint = FolderAmber
                            )
                        }
                    }

                    AnimatedVisibility(visible = isNoticeExpanded) {
                        Column {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Standard FTP transmits credentials and files unencrypted over local Wi-Fi. Bound strictly to local LAN interface ($resolvedLanIp). Never expose to untrusted public networks.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Server Status & Primary Toggle Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (ftpState.isRunning) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (ftpState.isRunning) "FTP Server Running" else "FTP Server Stopped",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (ftpState.isRunning) {
                                ftpState.connectionUrl
                            } else {
                                "ftp://$resolvedLanIp:${portText.toIntOrNull() ?: 2121}/"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = JetBrainsMonoFamily
                        )
                    }

                    Box(modifier = Modifier.align(Alignment.End)) {
                        if (ftpState.isRunning) {
                            Button(
                                onClick = onStopFtpServer,
                                modifier = Modifier.testTag("ftp_stop_button")
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop Server")
                            }
                        } else {
                            Button(
                                onClick = {
                                    onUpdateFtpConfig(
                                        portText.toIntOrNull() ?: 2121,
                                        pasvStartText.toIntOrNull() ?: 50000,
                                        pasvEndText.toIntOrNull() ?: 50050,
                                        username,
                                        allowAnonymous,
                                        allowWrite,
                                        false,
                                        timeoutMinText.toIntOrNull() ?: 15,
                                        selectedRootMode
                                    )
                                    onStartFtpServer(password)
                                },
                                modifier = Modifier.testTag("ftp_start_button")
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start Server")
                            }
                        }
                    }

                    if (ftpState.isRunning) {
                        HorizontalDivider()
                        if (!allowAnonymous) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Username: $username",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Password: " + if (passwordVisible) password else "••••••••",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { passwordVisible = !passwordVisible },
                                            modifier = Modifier.testTag("ftp_running_password_toggle")
                                        ) {
                                            Icon(
                                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Anonymous Login Allowed (No password required)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (ftpState.mountedVolumesSummary.isNotBlank()) {
                            Text(
                                text = "Shared folders: ${ftpState.mountedVolumesSummary}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily
                            )
                        }
                        Text(
                            text = "Active sessions: ${ftpState.activeClientsCount} • Transferred: ${formatByteSize(ftpState.bytesTransferred)} • PASV Range: ${ftpState.passivePortRange}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        // Configuration Fields
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Server Configuration", style = MaterialTheme.typography.titleSmall)

                    // Shared Storage Root Selector (File Manager + style: All Storage /device & /sdcard vs Single Volume)
                    Text(
                        text = "Shared Storage Root (Access from PC / Other Device)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedRootMode == "ALL",
                            onClick = {
                                selectedRootMode = "ALL"
                                onUpdateFtpConfig(
                                    portText.toIntOrNull() ?: 2121,
                                    pasvStartText.toIntOrNull() ?: 50000,
                                    pasvEndText.toIntOrNull() ?: 50050,
                                    username,
                                    allowAnonymous,
                                    allowWrite,
                                    false,
                                    timeoutMinText.toIntOrNull() ?: 15,
                                    "ALL"
                                )
                            },
                            label = {
                                Text(
                                    if (removableVolumes.isNotEmpty()) {
                                        "All Storage (/device + /sdcard)"
                                    } else {
                                        "All Storage (/device)"
                                    }
                                )
                            },
                            modifier = Modifier.testTag("ftp_root_mode_all")
                        )
                        FilterChip(
                            selected = selectedRootMode == "INTERNAL",
                            onClick = {
                                selectedRootMode = "INTERNAL"
                                onUpdateFtpConfig(
                                    portText.toIntOrNull() ?: 2121,
                                    pasvStartText.toIntOrNull() ?: 50000,
                                    pasvEndText.toIntOrNull() ?: 50050,
                                    username,
                                    allowAnonymous,
                                    allowWrite,
                                    false,
                                    timeoutMinText.toIntOrNull() ?: 15,
                                    "INTERNAL"
                                )
                            },
                            label = { Text("Internal Storage Only") },
                            modifier = Modifier.testTag("ftp_root_mode_internal")
                        )
                        removableVolumes.forEachIndexed { index, (label, dir) ->
                            val sdPath = dir.absolutePath
                            FilterChip(
                                selected = selectedRootMode == sdPath,
                                onClick = {
                                    selectedRootMode = sdPath
                                    onUpdateFtpConfig(
                                        portText.toIntOrNull() ?: 2121,
                                        pasvStartText.toIntOrNull() ?: 50000,
                                        pasvEndText.toIntOrNull() ?: 50050,
                                        username,
                                        allowAnonymous,
                                        allowWrite,
                                        false,
                                        timeoutMinText.toIntOrNull() ?: 15,
                                        sdPath
                                    )
                                },
                                label = { Text("$label Only (${dir.name})") },
                                modifier = Modifier.testTag("ftp_root_mode_sdcard_$index")
                            )
                        }
                    }
                    Text(
                        text = when (selectedRootMode) {
                            "ALL" -> if (removableVolumes.isNotEmpty()) {
                                val sdList = removableVolumes.mapIndexed { idx, (_, d) ->
                                    val key = if (idx == 0) "sdcard" else "sdcard${idx + 1}"
                                    "/$key (${d.absolutePath})"
                                }.joinToString(", ")
                                "Exposes both /device (Internal Storage) and $sdList at the FTP root /."
                            } else {
                                "Exposes /device (Internal Storage) at /. Any inserted SD card or USB drive will automatically appear as /sdcard."
                            }
                            "INTERNAL" -> "Exposes Internal Shared Storage directly at the FTP root /."
                            else -> "Exposes SD Card ($selectedRootMode) directly at the FTP root /."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val screenConfig = LocalConfiguration.current
                    val isNarrowScreen = screenConfig.screenWidthDp < 420

                    if (isNarrowScreen) {
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it },
                            label = { Text("Command Port") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = pasvStartText,
                                onValueChange = { pasvStartText = it },
                                label = { Text("PASV Start") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = pasvEndText,
                                onValueChange = { pasvEndText = it },
                                label = { Text("PASV End") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = portText,
                                onValueChange = { portText = it },
                                label = { Text("Command Port") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = pasvStartText,
                                onValueChange = { pasvStartText = it },
                                label = { Text("PASV Start") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = pasvEndText,
                                onValueChange = { pasvEndText = it },
                                label = { Text("PASV End") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ftp_username_input")
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password (Keystore)") },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { passwordVisible = !passwordVisible },
                                    modifier = Modifier.testTag("ftp_password_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ftp_password_input")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Allow Read/Write Access", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = allowWrite,
                            onCheckedChange = { allowWrite = it },
                            modifier = Modifier.testTag("ftp_allow_write_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Allow Anonymous Login", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = allowAnonymous,
                            onCheckedChange = { allowAnonymous = it },
                            modifier = Modifier.testTag("ftp_allow_anonymous_switch")
                        )
                    }

                    OutlinedTextField(
                        value = timeoutMinText,
                        onValueChange = { timeoutMinText = it },
                        label = { Text("Idle Auto-Shutdown Timeout (Minutes)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            onSaveFtpConfig(
                                portText.toIntOrNull() ?: 2121,
                                pasvStartText.toIntOrNull() ?: 50000,
                                pasvEndText.toIntOrNull() ?: 50050,
                                username.trim().ifBlank { "openfile" },
                                password,
                                allowAnonymous,
                                allowWrite,
                                false,
                                timeoutMinText.toIntOrNull() ?: 15,
                                selectedRootMode
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ftp_save_config_button")
                    ) {
                        Text(if (ftpState.isRunning) "Save & Apply Configuration" else "Save Configuration")
                    }
                }
            }
        }

        // Live Server Activity Log
        if (ftpState.logs.isNotEmpty()) {
            item {
                Text("Server Activity Log", style = MaterialTheme.typography.titleSmall)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ftpState.logs.take(15).forEach { log ->
                            Text(
                                text = "[${log.clientAddress}] ${log.message}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = if (log.isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteClientsTab(
    profiles: List<NetworkProfileEntity>,
    discoveredHosts: List<DiscoveredLanHost>,
    isScanningLan: Boolean,
    onAddProfileClick: () -> Unit,
    onConnectProfile: (NetworkProfileEntity) -> Unit,
    onDeleteProfile: (NetworkProfileEntity) -> Unit,
    onScanLan: () -> Unit,
    onSelectDiscoveredHost: (DiscoveredLanHost) -> Unit = {},
    onEditDiscoveredHost: (DiscoveredLanHost) -> Unit = {},
    onEditProfile: (NetworkProfileEntity) -> Unit = {}
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (profiles.isNotEmpty()) "Saved Servers (${profiles.size})" else "Saved Servers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedButton(
                    onClick = onScanLan,
                    enabled = !isScanningLan,
                    modifier = Modifier.testTag("network_scan_lan_button")
                ) {
                    if (isScanningLan) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan LAN")
                }
            }
        }

        item {
            Card(
                onClick = onAddProfileClick,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("network_add_profile_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Add Server Connection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "FTP, FTPS, SFTP (SSH), or SMB2/3 network share",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (profiles.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lan,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No saved servers yet",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap 'Add Server Connection' above or scan your local network to discover open shares.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(
            items = profiles,
            key = { it.id },
            contentType = { "profile" }
        ) { profile ->
                Card(
                    onClick = { onConnectProfile(profile) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = profile.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${profile.protocol}://${profile.host}:${profile.port}${profile.remoteInitialPath} • User: ${if (profile.isAnonymous) "Anonymous" else profile.username}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!profile.expectedHostKeyFingerprint.isNullOrBlank()) {
                                Text(
                                    text = "Pinned SSH Key: ${profile.expectedHostKeyFingerprint.take(26)}...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ApkGreen,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = if (profile.isAnonymous || !profile.secretAlias.isNullOrBlank()) {
                                    "Credentials saved • tap to connect"
                                } else {
                                    "No saved password • tap edit to add one"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { onEditProfile(profile) },
                            modifier = Modifier.testTag("network_edit_profile_${profile.id}")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit credentials")
                        }
                        IconButton(onClick = { onDeleteProfile(profile) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete profile", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        if (discoveredHosts.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Discovered LAN Services (Tap to Connect)", style = MaterialTheme.typography.titleSmall)
            }
            items(
                items = discoveredHosts,
                key = { "${it.ipAddress}:${it.port}" },
                contentType = { "host" }
            ) { host ->
                Card(
                    onClick = { onSelectDiscoveredHost(host) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("discovered_lan_host_${host.ipAddress}_${host.port}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(host.bannerOrName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("${host.ipAddress}:${host.port}", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFamily)
                            val isSaved = profiles.any {
                                it.host.equals(host.ipAddress, ignoreCase = true) && it.port == host.port &&
                                    (it.isAnonymous || !it.secretAlias.isNullOrBlank())
                            }
                            if (isSaved) {
                                Text("Saved • one-tap connect", style = MaterialTheme.typography.labelSmall, color = ApkGreen)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(host.protocolGuess, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            if (profiles.any { it.host.equals(host.ipAddress, ignoreCase = true) && it.port == host.port }) {
                                IconButton(onClick = { onEditDiscoveredHost(host) }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit credentials for ${host.ipAddress}")
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Connect to ${host.ipAddress}",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectDiscoveredHostDialog(
    host: DiscoveredLanHost,
    initialUsername: String = "openfile",
    initialShareName: String = "",
    initialAnonymous: Boolean = false,
    hasSavedPassword: Boolean = false,
    onDismiss: () -> Unit,
    onConnect: (username: String, password: String, shareName: String, isAnonymous: Boolean) -> Unit
) {
    var username by remember(host) { mutableStateOf(initialUsername) }
    var password by remember(host) { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var shareName by remember(host) { mutableStateOf(initialShareName) }
    var isAnonymous by remember(host) { mutableStateOf(initialAnonymous) }
    val isSmb = host.protocol.equals("SMB", ignoreCase = true)
    val isSftp = host.protocol.equals("SFTP", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Connect to ${host.bannerOrName.ifBlank { host.ipAddress }}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${host.protocol} • ${host.ipAddress}:${host.port} • credentials are stored encrypted on this device",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFamily,
                    color = MaterialTheme.colorScheme.primary
                )
                if (isSmb) {
                    OutlinedTextField(
                        value = shareName,
                        onValueChange = { shareName = it },
                        label = { Text("SMB Share Name (e.g. Public)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isAnonymous,
                        onCheckedChange = { isAnonymous = it },
                        modifier = Modifier.testTag("discovered_host_anonymous_checkbox")
                    )
                    Text("Anonymous / Guest Login", style = MaterialTheme.typography.bodySmall)
                }
                if (!isAnonymous) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("discovered_host_username_input")
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = {
                            Text(
                                when {
                                    hasSavedPassword -> "Password (leave blank to keep saved)"
                                    isSftp -> "Password or PEM Private Key"
                                    else -> "Password"
                                }
                            )
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier.testTag("discovered_host_password_toggle")
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("discovered_host_password_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConnect(
                        username.trim().ifBlank { "openfile" },
                        password,
                        shareName.trim(),
                        isAnonymous
                    )
                },
                modifier = Modifier.testTag("discovered_host_connect_button")
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AddNetworkProfileDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, String, String, String, String, Boolean, Boolean) -> Unit
) {
    var displayName by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf("FTP") }
    var host by remember { mutableStateOf("127.0.0.1") }
    var portText by remember { mutableStateOf("2121") }
    var username by remember { mutableStateOf("openfile") }
    var secret by remember { mutableStateOf("") }
    var remotePath by remember { mutableStateOf("/") }
    var shareName by remember { mutableStateOf("") }
    var isAnonymous by remember { mutableStateOf(false) }
    var usePassive by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Network Connection") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("FTP" to 2121, "FTPS" to 990, "SFTP" to 22, "SMB" to 445).forEach { (proto, defaultPort) ->
                        FilterChip(
                            selected = protocol == proto,
                            onClick = {
                                protocol = proto
                                portText = defaultPort.toString()
                            },
                            label = { Text(proto) }
                        )
                    }
                }
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Profile Name (Optional)") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host / IP") },
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (protocol == "SMB") {
                    OutlinedTextField(
                        value = shareName,
                        onValueChange = { shareName = it },
                        label = { Text("SMB Share Name (e.g. Public)") },
                        singleLine = true
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isAnonymous, onCheckedChange = { isAnonymous = it })
                    Text("Anonymous / Guest Login", style = MaterialTheme.typography.bodySmall)
                }
                if (!isAnonymous) {
                    var secretVisible by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = secret,
                        onValueChange = { secret = it },
                        label = {
                            Text(
                                if (protocol == "SFTP") "Password or PEM Private Key" else "Password (Encrypted in Keystore)"
                            )
                        },
                        visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { secretVisible = !secretVisible },
                                modifier = Modifier.testTag("remote_password_toggle")
                            ) {
                                Icon(
                                    imageVector = if (secretVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (secretVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = remotePath,
                    onValueChange = { remotePath = it },
                    label = { Text("Initial Remote Directory") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (host.isNotBlank()) {
                        onSave(
                            displayName,
                            protocol,
                            host,
                            portText.toIntOrNull() ?: 21,
                            username,
                            secret,
                            remotePath,
                            shareName,
                            isAnonymous,
                            usePassive
                        )
                    }
                }
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Settings Screen covering Appearance, File Browser, Network Defaults, Privacy & Vault, and About.
 */
@Composable
fun SettingsScreen(
    settings: AppSettingsState,
    storedVaultSecretsCount: Int,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onViewModeChange: (ViewMode) -> Unit,
    onShowThumbnailsChange: (Boolean) -> Unit,
    onShowHiddenChange: (Boolean) -> Unit,
    onConfirmDeleteChange: (Boolean) -> Unit,
    onMoveToTrashChange: (Boolean) -> Unit,
    onVerifyChecksumChange: (Boolean) -> Unit,
    onClearPrivacyData: (Boolean) -> Unit,
    onMountSafFolder: () -> Unit,
    appUpdateState: com.example.core.update.AppUpdateUiState = com.example.core.update.AppUpdateUiState(),
    onAutoCheckUpdatesChange: (Boolean) -> Unit = {},
    onCheckForUpdatesNow: () -> Unit = {},
    onDownloadAndInstallUpdate: () -> Unit = {},
    onOpenReleasesInBrowser: () -> Unit = {}
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen")
    ) {
        item {
            Text("Appearance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Theme Mode", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                label = { Text(mode.name) },
                                modifier = Modifier.testTag("theme_chip_${mode.name}")
                            )
                        }
                    }
                    SettingsToggleRow(
                        title = "Dynamic Material You Colors (Android 12+)",
                        checked = settings.useDynamicColor,
                        onCheckedChange = onDynamicColorChange
                    )
                    SettingsToggleRow(
                        title = "Default Grid View",
                        checked = settings.defaultViewMode == ViewMode.GRID,
                        onCheckedChange = { onViewModeChange(if (it) ViewMode.GRID else ViewMode.LIST) }
                    )
                    SettingsToggleRow(
                        title = "Show Image Thumbnails",
                        checked = settings.showThumbnails,
                        onCheckedChange = onShowThumbnailsChange
                    )
                }
            }
        }

        item {
            Text("File Browser & Transfer Engine", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsToggleRow(
                        title = "Show Hidden Files (Dotfiles)",
                        checked = settings.showHiddenFiles,
                        onCheckedChange = onShowHiddenChange,
                        testTag = "settings_toggle_hidden"
                    )
                    SettingsToggleRow(
                        title = "Confirm Before Deleting Files",
                        checked = settings.confirmBeforeDelete,
                        onCheckedChange = onConfirmDeleteChange
                    )
                    SettingsToggleRow(
                        title = "Move Deleted Files to Recycle Bin by Default",
                        subtitle = if (settings.moveToTrashByDefault) {
                            "Files can be restored from Trash (turn off to skip Recycle Bin and delete directly)"
                        } else {
                            "Recycle Bin skipped: Files will be deleted directly and instantly"
                        },
                        checked = settings.moveToTrashByDefault,
                        onCheckedChange = onMoveToTrashChange,
                        testTag = "settings_toggle_move_to_trash"
                    )
                    SettingsToggleRow(
                        title = "Verify SHA-256 Checksum After Copy/Move",
                        checked = settings.verifyChecksumAfterTransfer,
                        onCheckedChange = onVerifyChecksumChange
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onMountSafFolder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Filled.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mount SAF Folder")
                    }
                }
            }
        }

        item {
            Text("Security & Privacy", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Android Keystore Credential Vault: $storedVaultSecretsCount encrypted secret(s) stored (AES-256-GCM).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Zero analytics, zero ad SDKs, and zero background network activity unless you explicitly start the FTP Server or browse a remote connection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onClearPrivacyData(false) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear Search & Recents")
                        }
                        OutlinedButton(
                            onClick = { onClearPrivacyData(true) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Purge Keystore Vault", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "App Updates (GitHub Releases)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_app_updates_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Installed Version: v${appUpdateState.currentVersionName} (Build ${appUpdateState.currentVersionCode})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = appUpdateState.statusMessage
                                    ?: "Direct self-update from github.com/sumon317/OpenFile/releases (no 3rd-party app needed)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (appUpdateState.errorMessage != null) {
                                    MaterialTheme.colorScheme.error
                                } else if (appUpdateState.updateAvailable) {
                                    ApkGreen
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    SettingsToggleRow(
                        title = "Auto-Check for Updates on Startup",
                        checked = settings.autoCheckUpdates,
                        onCheckedChange = onAutoCheckUpdatesChange,
                        testTag = "settings_toggle_auto_update"
                    )

                    if (appUpdateState.isDownloading) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { appUpdateState.downloadProgressFraction },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Downloading APK: ${(appUpdateState.downloadProgressFraction * 100).toInt()}% (${formatByteSize(appUpdateState.downloadedBytes)} / ${formatByteSize(appUpdateState.totalBytes)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (appUpdateState.updateAvailable && appUpdateState.latestRelease != null) {
                        val rel = appUpdateState.latestRelease
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "New Release: ${rel.releaseTitle} (${rel.tagName})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = rel.changelog,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 6,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Button(
                                    onClick = onDownloadAndInstallUpdate,
                                    enabled = !appUpdateState.isDownloading,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_download_install_update_button")
                                ) {
                                    Text(
                                        when {
                                            appUpdateState.isDownloading -> "Downloading..."
                                            appUpdateState.downloadedApkFile != null -> "Install Downloaded Update (${rel.tagName})"
                                            else -> "Download & Install ${rel.tagName} (${formatByteSize(rel.apkSizeBytes)})"
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onCheckForUpdatesNow,
                            enabled = !appUpdateState.isChecking && !appUpdateState.isDownloading,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_check_updates_button")
                        ) {
                            Text(if (appUpdateState.isChecking) "Checking..." else "Check for Updates")
                        }
                        OutlinedButton(
                            onClick = onOpenReleasesInBrowser,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_open_github_releases_button")
                        ) {
                            Text("GitHub Releases")
                        }
                    }
                }
            }
        }

        item {
            Text("About OpenFile", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "OpenFile v${appUpdateState.currentVersionName} (com.openfile.manager)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Architecture: Capability-based VirtualFileSystemProvider • Room + DataStore • Apache Commons Compress & XZ • Apache Commons Net (FTP/FTPS) • JSch (SFTP w/ SHA-256 Host-Key Pinning) • SMBJ (SMB2/3) • OAuth 2.0 PKCE Cloud Engine • Native GitHub Releases Self-Updater.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    testTag: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}
