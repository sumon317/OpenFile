package com.example.core.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.core.filesystem.LocalFileSystemProvider
import java.io.File

/**
 * Foreground Service managing the lifecycle of [FtpServerEngine].
 * - Displays an ongoing notification with the bound Wi-Fi IP:Port and a direct "Stop Server" button.
 * - Monitors [ConnectivityManager.NetworkCallback] to stop the server automatically if Wi-Fi drops.
 * - Cleans up sockets on task removal or service destruction so it never runs silently in the background.
 */
class FtpServerForegroundService : Service() {

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        const val ACTION_START = "com.openfile.manager.action.START_FTP_SERVER"
        const val ACTION_STOP = "com.openfile.manager.action.STOP_FTP_SERVER"
        private const val CHANNEL_ID = "openfile_ftp_server_channel"
        private const val NOTIFICATION_ID = 4201

        const val EXTRA_PORT = "extra_port"
        const val EXTRA_PASV_START = "extra_pasv_start"
        const val EXTRA_PASV_END = "extra_pasv_end"
        const val EXTRA_ROOT_PATH = "extra_root_path"
        const val EXTRA_USERNAME = "extra_username"
        const val EXTRA_PASSWORD = "extra_password"
        const val EXTRA_ANONYMOUS = "extra_anonymous"
        const val EXTRA_ALLOW_WRITE = "extra_allow_write"
        const val EXTRA_USE_TLS = "extra_use_tls"
        const val EXTRA_TIMEOUT_MIN = "extra_timeout_min"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                FtpServerEngine.appendLog("System", "Stopped by user")
                stopServerAndService()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val lanIp = FtpServerEngine.resolveLocalLanIpAddress()
                val port = intent.getIntExtra(EXTRA_PORT, 2121)
                val pasvStart = intent.getIntExtra(EXTRA_PASV_START, 50000)
                val pasvEnd = intent.getIntExtra(EXTRA_PASV_END, 50050)
                val rootPath = intent.getStringExtra(EXTRA_ROOT_PATH) ?: "ALL"
                val username = intent.getStringExtra(EXTRA_USERNAME) ?: "openfile"
                val password = intent.getStringExtra(EXTRA_PASSWORD) ?: ""
                val anonymous = intent.getBooleanExtra(EXTRA_ANONYMOUS, false)
                val allowWrite = intent.getBooleanExtra(EXTRA_ALLOW_WRITE, true)
                val useTls = intent.getBooleanExtra(EXTRA_USE_TLS, false)
                val timeoutMin = intent.getIntExtra(EXTRA_TIMEOUT_MIN, 15)

                createNotificationChannel()
                val notification = buildNotification(lanIp, port, allowWrite)
                startForeground(NOTIFICATION_ID, notification)

                val localProvider = LocalFileSystemProvider(applicationContext)
                val (resolvedRootDir, virtualMounts) = localProvider.resolveFtpServerRootAndMounts(rootPath)

                val config = FtpServerConfig(
                    bindAddress = lanIp,
                    port = port,
                    passivePortStart = pasvStart,
                    passivePortEnd = pasvEnd,
                    rootDirectory = resolvedRootDir,
                    virtualMounts = virtualMounts,
                    username = username,
                    password = password,
                    allowAnonymous = anonymous,
                    allowWrite = allowWrite,
                    useExplicitTls = useTls,
                    idleTimeoutMinutes = timeoutMin
                )

                val startResult = FtpServerEngine.startServer(
                    config = config,
                    onIdleTimeoutStop = {
                        stopServerAndService()
                    }
                )
                if (startResult.isFailure) {
                    FtpServerEngine.appendLog(
                        "Error",
                        "Failed to bind ${config.bindAddress}:${config.port} — ${startResult.exceptionOrNull()?.message}",
                        isWarning = true
                    )
                    stopServerAndService()
                } else {
                    registerWifiMonitor()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun registerWifiMonitor() {
        unregisterWifiMonitor()
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        connectivityManager = cm
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                FtpServerEngine.appendLog(
                    "Network",
                    "Wi-Fi / LAN connection lost. Automatically stopping FTP server for security.",
                    isWarning = true
                )
                stopServerAndService()
            }
        }
        networkCallback = callback
        runCatching { cm.registerNetworkCallback(request, callback) }
    }

    private fun unregisterWifiMonitor() {
        val cb = networkCallback ?: return
        runCatching { connectivityManager?.unregisterNetworkCallback(cb) }
        networkCallback = null
    }

    private fun stopServerAndService() {
        unregisterWifiMonitor()
        FtpServerEngine.stopServer()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopServerAndService()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopServerAndService()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.ftp_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active local network FTP server status"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(ip: String, port: Int, allowWrite: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FtpServerForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val modeText = if (allowWrite) "Read/Write" else "Read-Only"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(getString(R.string.ftp_notification_title))
            .setContentText("ftp://$ip:$port/ ($modeText)")
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.ftp_stop_server),
                stopPending
            )
            .build()
    }
}
