package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
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
import androidx.compose.material3.RadioButton
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
import com.example.core.datastore.CategoryFolderViewMode
import com.example.core.datastore.FileOpenAction
import com.example.core.datastore.MusicPlayerCustomSettings
import com.example.core.datastore.ProgressBarPreset
import com.example.core.datastore.PlayButtonShape
import com.example.core.datastore.PlayerThemePreset
import com.example.core.datastore.PlayerFontStyle
import com.example.core.datastore.PlayerFontSize
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
    onOpenReleasesInBrowser: () -> Unit = {},
    onFileOpenActionChange: (FileOpenAction) -> Unit = {},
    onCategoryFolderViewModeChange: (CategoryFolderViewMode) -> Unit = {},
    onMusicPlayerSettingsChange: ((MusicPlayerCustomSettings) -> MusicPlayerCustomSettings) -> Unit = {}
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
                        title = "Show Media Thumbnails",
                        checked = settings.showThumbnails,
                        onCheckedChange = onShowThumbnailsChange
                    )
                }
            }
        }

        item {
            Text(
                "Music Player & Studio Customization",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_music_player_card")
            ) {
                val music = settings.musicPlayerSettings
                val activeTheme = music.playerTheme
                val accentColor = Color(activeTheme.accentColor)
                val themeBgStart = Color(activeTheme.backgroundStart)
                val themeBgEnd = Color(activeTheme.backgroundEnd)
                val themeSurface = Color(activeTheme.cardSurface)

                val previewFontFamily = when (music.fontStyle) {
                    PlayerFontStyle.DEFAULT -> FontFamily.Default
                    PlayerFontStyle.MONOSPACE -> FontFamily.Monospace
                    PlayerFontStyle.SERIF -> FontFamily.Serif
                    PlayerFontStyle.CURSIVE -> FontFamily.Cursive
                }
                val previewFontScale = music.fontSize.scaleFactor

                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Live Interactive Player Preview Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.verticalGradient(listOf(themeBgStart, themeBgEnd)))
                            .padding(16.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LIVE IN-PLAYER PREVIEW",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp
                                    ),
                                    color = accentColor
                                )
                                Text(
                                    text = activeTheme.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.LightGray
                                )
                            }

                            // Album Art Preview with dynamic shape, border radius, and rotation if unlocked
                            val infiniteTransition = rememberInfiniteTransition(label = "preview_vinyl_rot")
                            val previewAngle by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(durationMillis = 6000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "preview_art_angle"
                            )
                            val artRotationModifier = if (music.isAlbumArtCircle && music.rotateIfCircle) {
                                Modifier.graphicsLayer(rotationZ = previewAngle)
                            } else Modifier

                            val previewShape = if (music.isAlbumArtCircle) CircleShape else RoundedCornerShape(music.albumArtCornerRadiusDp.dp)

                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .then(artRotationModifier)
                                    .clip(previewShape)
                                    .background(themeSurface)
                                    .border(2.dp, accentColor.copy(alpha = 0.5f), previewShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (music.isAlbumArtCircle) {
                                    // Circular Vinyl Disc Stylings
                                    Box(modifier = Modifier.fillMaxSize(0.85f).border(1.dp, Color(0xFF282A36), CircleShape))
                                    Box(modifier = Modifier.fillMaxSize(0.65f).border(1.dp, Color(0xFF282A36), CircleShape))
                                    Surface(shape = CircleShape, color = accentColor, modifier = Modifier.size(36.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Filled.Album, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Surface(shape = CircleShape, color = Color.Black, modifier = Modifier.size(8.dp)) {}
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Audiotrack,
                                        contentDescription = null,
                                        tint = accentColor.copy(alpha = 0.85f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }

                            // Moving / Static Title Preview
                            val titleModifier = if (music.songTitleMarquee) {
                                Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE)
                            } else {
                                Modifier.fillMaxWidth()
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Midnight City Dreams (Lossless Remaster)",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = previewFontFamily,
                                        fontSize = (16 * previewFontScale).sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    ),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = titleModifier
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Aether Soundworks • Studio FLAC 96kHz/24bit",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = previewFontFamily,
                                        fontSize = (12 * previewFontScale).sp,
                                        textAlign = TextAlign.Center
                                    ),
                                    color = Color(0xFFA0A5B5),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Progress Bar Preview (matching ProgressBarPreset)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                when (music.progressBarStyle) {
                                    ProgressBarPreset.STANDARD -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(18.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(Color.White.copy(alpha = 0.2f))
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.45f)
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(accentColor)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .padding(start = 120.dp)
                                                    .size(14.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White)
                                                    .border(2.dp, accentColor, CircleShape)
                                            )
                                        }
                                    }
                                    ProgressBarPreset.WAVE_PILL -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(20.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(10.dp)
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(Color.White.copy(alpha = 0.15f))
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.45f)
                                                    .height(10.dp)
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            listOf(accentColor.copy(alpha = 0.7f), accentColor)
                                                        )
                                                    )
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .padding(start = 118.dp)
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White)
                                                    .border(2.dp, accentColor, CircleShape)
                                            )
                                        }
                                    }
                                    ProgressBarPreset.SEGMENTED -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(14.dp),
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val totalSegments = 24
                                            val activeCount = 11
                                            for (seg in 0 until totalSegments) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(if (seg == activeCount - 1) 12.dp else 6.dp)
                                                        .clip(RoundedCornerShape(1.dp))
                                                        .background(if (seg < activeCount) accentColor else Color.White.copy(alpha = 0.2f))
                                                )
                                            }
                                        }
                                    }
                                    ProgressBarPreset.MINIMAL_THIN -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(12.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(2.dp)
                                                    .background(Color.White.copy(alpha = 0.15f))
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.45f)
                                                    .height(2.dp)
                                                    .background(accentColor)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .padding(start = 124.dp)
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White)
                                            )
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("01:24", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Text("03:45", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }

                            // Play Button Shape Preview
                            val previewPlayShape = if (music.playButtonShape == PlayButtonShape.CIRCLE) {
                                CircleShape
                            } else {
                                RoundedCornerShape(music.playButtonCornerRadiusDp.dp)
                            }
                            Surface(
                                shape = previewPlayShape,
                                color = Color.White,
                                shadowElevation = 4.dp,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = Color(0xFF0F172A),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }

                    // --- 2. Album Art Shape & Border Radius Controls ---
                    Text("Album Art Shape & Radius", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !music.isAlbumArtCircle,
                            onClick = {
                                onMusicPlayerSettingsChange { it.copy(isAlbumArtCircle = false) }
                            },
                            label = { Text("Rounded Card") },
                            modifier = Modifier.testTag("art_shape_rounded_chip")
                        )
                        FilterChip(
                            selected = music.isAlbumArtCircle,
                            onClick = {
                                onMusicPlayerSettingsChange { it.copy(isAlbumArtCircle = true) }
                            },
                            label = { Text("Circle (Disc)") },
                            modifier = Modifier.testTag("art_shape_circle_chip")
                        )
                    }

                    if (!music.isAlbumArtCircle) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Corner Radius", style = MaterialTheme.typography.bodySmall)
                                Text("${music.albumArtCornerRadiusDp} dp", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = music.albumArtCornerRadiusDp.toFloat(),
                                onValueChange = { radius ->
                                    onMusicPlayerSettingsChange { it.copy(albumArtCornerRadiusDp = radius.toInt()) }
                                },
                                valueRange = 0f..48f,
                                steps = 47,
                                modifier = Modifier.fillMaxWidth().testTag("art_radius_slider")
                            )
                        }
                    } else {
                        Text(
                            text = "Artwork is clipped into a full disc circle. Spinning vinyl mode is now unlocked below!",
                            style = MaterialTheme.typography.bodySmall,
                            color = ApkGreen
                        )
                    }

                    // --- 3. Rotate if Circle Toggle (Unlocked ONLY if Circle) ---
                    SettingsToggleRow(
                        title = "Spinning Disc on Play",
                        subtitle = if (music.isAlbumArtCircle) {
                            "Disc smoothly rotates like a vinyl turntable while song is playing"
                        } else {
                            "🔒 Locked: Select 'Circle (Disc)' above to unlock vinyl rotation"
                        },
                        checked = if (music.isAlbumArtCircle) music.rotateIfCircle else false,
                        enabled = music.isAlbumArtCircle,
                        onCheckedChange = { rotate ->
                            onMusicPlayerSettingsChange { it.copy(rotateIfCircle = rotate) }
                        },
                        testTag = "toggle_rotate_circle"
                    )

                    // --- 4. Moving Song Title Toggle ---
                    SettingsToggleRow(
                        title = "Marquee Scrolling Song Title",
                        subtitle = "Smoothly auto-scrolls long song and track titles across the screen",
                        checked = music.songTitleMarquee,
                        onCheckedChange = { marquee ->
                            onMusicPlayerSettingsChange { it.copy(songTitleMarquee = marquee) }
                        },
                        testTag = "toggle_marquee_title"
                    )

                    HorizontalDivider()

                    // --- 5. Progress Bar Style Presets ---
                    Text("Progress Bar Style", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProgressBarPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = music.progressBarStyle == preset,
                                onClick = {
                                    onMusicPlayerSettingsChange { it.copy(progressBarStyle = preset) }
                                },
                                label = { Text(preset.label) },
                                modifier = Modifier.testTag("progress_style_${preset.name}")
                            )
                        }
                    }
                    Text(
                        text = music.progressBarStyle.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    // --- 6. Play Button Shape & Radius ---
                    Text("Play Button Shape & Radius", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PlayButtonShape.entries.forEach { shape ->
                            FilterChip(
                                selected = music.playButtonShape == shape,
                                onClick = {
                                    onMusicPlayerSettingsChange { it.copy(playButtonShape = shape) }
                                },
                                label = { Text(shape.label) },
                                modifier = Modifier.testTag("play_button_shape_${shape.name}")
                            )
                        }
                    }

                    if (music.playButtonShape == PlayButtonShape.ROUNDED) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Button Corner Radius", style = MaterialTheme.typography.bodySmall)
                                Text("${music.playButtonCornerRadiusDp} dp (0 = Sharp Square, 12 = Squircle, 36 = Pill)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = music.playButtonCornerRadiusDp.toFloat(),
                                onValueChange = { radius ->
                                    onMusicPlayerSettingsChange { it.copy(playButtonCornerRadiusDp = radius.toInt()) }
                                },
                                valueRange = 0f..36f,
                                steps = 35,
                                modifier = Modifier.fillMaxWidth().testTag("play_button_radius_slider")
                            )
                        }
                    }

                    HorizontalDivider()

                    // --- 7. In-Player Theme & Style Presets ---
                    Text("In-Player Theme Presets", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PlayerThemePreset.entries.forEach { themePreset ->
                            FilterChip(
                                selected = music.playerTheme == themePreset,
                                onClick = {
                                    onMusicPlayerSettingsChange { it.copy(playerTheme = themePreset) }
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(Color(themePreset.accentColor))
                                    )
                                },
                                label = { Text(themePreset.label) },
                                modifier = Modifier.testTag("theme_preset_${themePreset.name}")
                            )
                        }
                    }

                    HorizontalDivider()

                    // --- 8. Font Style & Font Size ---
                    Text("In-Player Font Style", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PlayerFontStyle.entries.forEach { fontStyle ->
                            FilterChip(
                                selected = music.fontStyle == fontStyle,
                                onClick = {
                                    onMusicPlayerSettingsChange { it.copy(fontStyle = fontStyle) }
                                },
                                label = { Text(fontStyle.label) },
                                modifier = Modifier.testTag("font_style_${fontStyle.name}")
                            )
                        }
                    }

                    Text("In-Player Font Size", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PlayerFontSize.entries.forEach { fontSize ->
                            FilterChip(
                                selected = music.fontSize == fontSize,
                                onClick = {
                                    onMusicPlayerSettingsChange { it.copy(fontSize = fontSize) }
                                },
                                label = { Text("${fontSize.label} (${(fontSize.scaleFactor * 100).toInt()}%)") },
                                modifier = Modifier.testTag("font_size_${fontSize.name}")
                            )
                        }
                    }
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
                    Text("File Opening Behavior", style = MaterialTheme.typography.titleSmall)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FileOpenAction.entries.forEach { action ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onFileOpenActionChange(action) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.fileOpenAction == action,
                                    onClick = { onFileOpenActionChange(action) },
                                    modifier = Modifier.testTag("setting_file_action_${action.name}")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(text = action.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(text = action.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Category Quick Tiles View Mode", style = MaterialTheme.typography.titleSmall)
                    Text("Toggle default presentation for Songs, Photos, Videos, and Docs (list everything vs grouped by parent folders)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryFolderViewMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.categoryFolderViewMode == mode,
                                onClick = { onCategoryFolderViewModeChange(mode) },
                                label = { Text(mode.label) },
                                leadingIcon = {
                                    Icon(
                                        if (mode == CategoryFolderViewMode.ALL_FILES) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("category_mode_chip_${mode.name}")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(6.dp))
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
    enabled: Boolean = true,
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
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}
