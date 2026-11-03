package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.core.cloud.GoogleDriveOnDeviceAuth
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.viewinterop.AndroidView
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.cloud.CloudProviderKind
import com.example.core.cloud.PkceSession
import com.example.core.database.CloudAccountEntity
import com.example.core.database.NetworkProfileEntity
import com.example.core.datastore.AppSettingsState
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
    savedFtpPassword: String = "openfile123"
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var showAddProfileDialog by remember { mutableStateOf(false) }

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
                onUpdateFtpConfig = onUpdateFtpConfig
            )
        } else {
            RemoteClientsTab(
                profiles = profiles,
                discoveredHosts = discoveredHosts,
                isScanningLan = isScanningLan,
                onAddProfileClick = { showAddProfileDialog = true },
                onConnectProfile = onConnectProfile,
                onDeleteProfile = onDeleteProfile,
                onScanLan = onScanLan
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
    onUpdateFtpConfig: (Int, Int, Int, String, Boolean, Boolean, Boolean, Int, String) -> Unit
) {
    val resolvedLanIp by produceState(
        initialValue = ftpState.boundIpAddress.ifBlank { "127.0.0.1" },
        ftpState.isRunning
    ) {
        value = withContext(Dispatchers.IO) {
            FtpServerEngine.resolveLocalLanIpAddress()
        }
    }
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
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
                                        settings.ftpRootPath
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

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it },
                            label = { Text("Command Port") },
                            enabled = !ftpState.isRunning,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = pasvStartText,
                            onValueChange = { pasvStartText = it },
                            label = { Text("PASV Start") },
                            enabled = !ftpState.isRunning,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = pasvEndText,
                            onValueChange = { pasvEndText = it },
                            label = { Text("PASV End") },
                            enabled = !ftpState.isRunning,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            enabled = !ftpState.isRunning && !allowAnonymous,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
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
                            readOnly = ftpState.isRunning,
                            enabled = !allowAnonymous,
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
                        Text("Allow Read/Write Uploads (STOR/DELE/MKD)", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = allowWrite,
                            onCheckedChange = { allowWrite = it },
                            enabled = !ftpState.isRunning
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
                            enabled = !ftpState.isRunning
                        )
                    }

                    OutlinedTextField(
                        value = timeoutMinText,
                        onValueChange = { timeoutMinText = it },
                        label = { Text("Idle Auto-Shutdown Timeout (Minutes)") },
                        enabled = !ftpState.isRunning,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
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
    onScanLan: () -> Unit
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
            items(profiles, key = { it.id }) { profile ->
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${profile.protocol}://${profile.host}:${profile.port}${profile.remoteInitialPath} • User: ${if (profile.isAnonymous) "Anonymous" else profile.username}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!profile.expectedHostKeyFingerprint.isNullOrBlank()) {
                                Text(
                                    text = "Pinned SSH Key: ${profile.expectedHostKeyFingerprint.take(26)}...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ApkGreen
                                )
                            }
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
                Text("Discovered LAN Services", style = MaterialTheme.typography.titleSmall)
            }
            items(discoveredHosts, key = { "${it.ipAddress}:${it.port}" }) { host ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(host.bannerOrName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("${host.ipAddress}:${host.port}", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFamily)
                        }
                        Text(host.protocolGuess, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
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
 * Extensible Cloud Storage Integration Screen (Google Drive, Microsoft OneDrive, Dropbox)
 * with 1-tap direct webpage login & storage view (File Manager + style) and OAuth 2.0 PKCE support.
 */
@Composable
fun CloudStorageScreen(
    accounts: List<CloudAccountEntity>,
    activePkceSession: PkceSession?,
    onStartOAuth: (CloudProviderKind, String) -> Result<String>,
    onCompleteOAuth: (String, String, Boolean) -> Unit,
    onOpenCloudAccount: (CloudAccountEntity) -> Unit,
    onDisconnectAccount: (CloudAccountEntity) -> Unit,
    onShowMessage: (String) -> Unit,
    onGoogleDriveAuthorized: (String, String?) -> Unit = { _, _ -> },
    activeWebCloudProvider: CloudProviderKind? = null,
    activeWebCloudUrl: String? = null,
    onConnectProviderDirect: (CloudProviderKind) -> Unit = {},
    onWebCloudAuthenticated: (CloudProviderKind, String?) -> Unit = { _, _ -> },
    onOAuthRedirectUri: (Uri) -> Unit = {},
    onDownloadCloudWebFile: (String, String?, String?, String?, String?) -> Unit = { _, _, _, _, _ -> },
    onCloseWebCloudPortal: () -> Unit = {}
) {
    val context = LocalContext.current
    var configuringProvider by remember { mutableStateOf<CloudProviderKind?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("cloud_storage_screen")
    ) {
        item {
            Text("Cloud Storage Providers", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Tap Connect on any cloud provider to sign in using your default browser and access your cloud storage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (activeWebCloudProvider != null && !activeWebCloudUrl.isNullOrBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cloud_web_portal_view")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${activeWebCloudProvider.displayName} • Default Browser Sign-In",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Launched in your default browser. Complete sign-in there to access your cloud files.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(activeWebCloudUrl)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                        )
                                    }
                                }
                            ) {
                                Text("Re-open")
                            }
                            IconButton(
                                onClick = onCloseWebCloudPortal,
                                modifier = Modifier.testTag("cloud_web_portal_close")
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Dismiss browser sign-in banner")
                            }
                        }
                    }
                }
            }
        }

        if (accounts.isNotEmpty()) {
            item {
                HorizontalDivider()
                Text("Connected Accounts", style = MaterialTheme.typography.titleMedium)
            }
            items(accounts, key = { it.id }) { acc ->
                Card(
                    onClick = { onOpenCloudAccount(acc) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cloud_account_card_${acc.provider}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(acc.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(acc.accountEmail, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Open cloud storage",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(onClick = { onDisconnectAccount(acc) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Disconnect account", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            item {
                HorizontalDivider()
            }
        }

        // Provider Connector Cards
        items(CloudProviderKind.entries) { provider ->
            val isConnected = accounts.any { it.provider == provider.name }
            Card(
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(provider.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (isConnected) "Connected • Tap above or Connect to open" else "Default Browser Login + OAuth 2.0",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isConnected) ApkGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { configuringProvider = provider },
                            modifier = Modifier.testTag("configure_oauth_${provider.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Key,
                                contentDescription = "Custom OAuth API Key for ${provider.displayName}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { onConnectProviderDirect(provider) },
                            modifier = Modifier.testTag("connect_${provider.id}")
                        ) {
                            Text("Connect")
                        }
                    }
                }
            }
        }
    }

    if (configuringProvider != null) {
        val provider = configuringProvider!!
        var clientId by remember(provider) { mutableStateOf(provider.defaultClientIdFromBuildConfig()) }
        var accountEmail by remember {
            mutableStateOf(if (provider == CloudProviderKind.GOOGLE_DRIVE) "container6767@gmail.com" else "")
        }
        var authCodeOrToken by remember { mutableStateOf("") }
        var directTokenMode by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { configuringProvider = null },
            title = { Text("Custom OAuth Key: ${provider.displayName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = clientId,
                        onValueChange = { clientId = it },
                        label = { Text("OAuth 2.0 Client ID / App Key") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = accountEmail,
                        onValueChange = { accountEmail = it },
                        label = { Text("Account Label / Email") },
                        singleLine = true
                    )
                    OutlinedButton(
                        onClick = {
                            onStartOAuth(provider, clientId).fold(
                                onSuccess = { authUrl ->
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                        )
                                        configuringProvider = null
                                    }.onFailure {
                                        onShowMessage("No browser available to sign in: ${it.message}")
                                    }
                                },
                                onFailure = { onShowMessage(it.message ?: "Invalid Client ID") }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("1. Launch Default Browser OAuth 2.0 PKCE")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = directTokenMode, onCheckedChange = { directTokenMode = it })
                        Text("Use Direct OAuth Access Token", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = authCodeOrToken,
                        onValueChange = { authCodeOrToken = it },
                        label = {
                            Text(if (directTokenMode) "OAuth 2.0 Access Token" else "2. Authorization Code from Redirect")
                        },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (activePkceSession == null && clientId.isNotBlank()) {
                            onStartOAuth(provider, clientId)
                        }
                        if (authCodeOrToken.isNotBlank()) {
                            onCompleteOAuth(accountEmail, authCodeOrToken, directTokenMode)
                            configuringProvider = null
                        }
                    }
                ) {
                    Text("Verify & Save to Keystore")
                }
            },
            dismissButton = {
                TextButton(onClick = { configuringProvider = null }) { Text("Cancel") }
            }
        )
    }
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
    onClearPrivacyData: (Boolean) -> Unit
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
                        title = "Move Deleted Local/SAF Files to Trash by Default",
                        checked = settings.moveToTrashByDefault,
                        onCheckedChange = onMoveToTrashChange
                    )
                    SettingsToggleRow(
                        title = "Verify SHA-256 Checksum After Copy/Move",
                        checked = settings.verifyChecksumAfterTransfer,
                        onCheckedChange = onVerifyChecksumChange
                    )
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
            Text("About OpenFile", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("OpenFile v1.2.0 (com.openfile.manager)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Architecture: Capability-based VirtualFileSystemProvider • Room + DataStore • Apache Commons Compress & XZ • Apache Commons Net (FTP/FTPS) • JSch (SFTP w/ SHA-256 Host-Key Pinning) • SMBJ (SMB2/3) • OAuth 2.0 PKCE Cloud Engine.",
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
    testTag: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}
