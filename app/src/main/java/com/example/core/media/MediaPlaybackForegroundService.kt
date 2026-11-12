package com.example.core.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import com.example.MainActivity
import com.example.R
import com.example.core.model.FileReference
import com.example.core.model.ProviderType
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
class MediaPlaybackForegroundService : Service() {

    private var exoPlayer: ExoPlayer? = null
    private var currentFile: FileReference? = null
    private var playlist: List<FileReference> = emptyList()
    private var currentIndex = 0
    private var wasPlaying = true
    private var notificationJob: Job? = null
    private val mainScope = MainScope()

    companion object {
        const val ACTION_PLAY = "com.openfile.manager.media.ACTION_PLAY"
        const val ACTION_PAUSE = "com.openfile.manager.media.ACTION_PAUSE"
        const val ACTION_NEXT = "com.openfile.manager.media.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.openfile.manager.media.ACTION_PREVIOUS"
        const val ACTION_SEEK = "com.openfile.manager.media.ACTION_SEEK"
        const val ACTION_SET_PLAYLIST = "com.openfile.manager.media.ACTION_SET_PLAYLIST"
        const val ACTION_SET_REPEAT_MODE = "com.openfile.manager.media.ACTION_SET_REPEAT_MODE"
        const val ACTION_SET_SHUFFLE = "com.openfile.manager.media.ACTION_SET_SHUFFLE"
        const val ACTION_SET_SPEED = "com.openfile.manager.media.ACTION_SET_SPEED"
        const val ACTION_STOP = "com.openfile.manager.media.ACTION_STOP"

        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_FILE_MIME = "extra_file_mime"
        const val EXTRA_FILE_SIZE = "extra_file_size"
        const val EXTRA_FILE_MODIFIED = "extra_file_modified"
        const val EXTRA_FILE_PROVIDER = "extra_file_provider"
        const val EXTRA_FILE_LOCAL_BACKING = "extra_file_local_backing"
        const val EXTRA_FILE_PARENT = "extra_file_parent"
        const val EXTRA_FILE_EXTENSION = "extra_file_extension"
        const val EXTRA_FILE_IS_DIR = "extra_file_is_dir"
        
        const val EXTRA_PLAYLIST_PATHS = "extra_playlist_paths"
        const val EXTRA_PLAYLIST_NAMES = "extra_playlist_names"
        const val EXTRA_PLAYLIST_MIMES = "extra_playlist_mimes"
        const val EXTRA_PLAYLIST_SIZES = "extra_playlist_sizes"
        const val EXTRA_PLAYLIST_MODIFIED = "extra_playlist_modified"
        const val EXTRA_PLAYLIST_PROVIDERS = "extra_playlist_providers"
        const val EXTRA_PLAYLIST_LOCAL_BACKINGS = "extra_playlist_local_backings"
        const val EXTRA_PLAYLIST_PARENTS = "extra_playlist_parents"
        const val EXTRA_PLAYLIST_EXTENSIONS = "extra_playlist_extensions"
        const val EXTRA_PLAYLIST_IS_DIRS = "extra_playlist_is_dirs"
        
        const val EXTRA_INDEX = "extra_index"
        const val EXTRA_POSITION = "extra_position"
        const val EXTRA_REPEAT_MODE = "extra_repeat_mode"
        const val EXTRA_SHUFFLE = "extra_shuffle"
        const val EXTRA_SPEED = "extra_speed"

        private const val CHANNEL_ID = "openfile_media_playback_channel"
        private const val NOTIFICATION_ID = 4202
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        val renderersFactory = DefaultRenderersFactory(this).apply {
            setEnableDecoderFallback(true)
        }
        exoPlayer = ExoPlayer.Builder(this, renderersFactory).build().apply {
            setHandleAudioBecomingNoisy(true)
        }

        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> handlePlaybackEnded()
                    Player.STATE_READY -> updateNotification()
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                wasPlaying = playing
                MediaPlaybackStateHolder.updateIsPlaying(playing)
                updateNotification()
            }

            override fun onPlayerError(error: PlaybackException) {
                MediaPlaybackStateHolder.updatePlaybackError(error.localizedMessage ?: "Playback error")
                updateNotification()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateNotification()
            }

            override fun onPositionDiscontinuity(reason: Int) {
                // Position changed
            }
        })

        // Start notification update job
        notificationJob = mainScope.launch {
            while (true) {
                updateNotification()
                delay(1000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_SET_PLAYLIST -> {
                    val fileRef = buildFileReferenceFromIntent(intent)
                    val playlistExtra = buildPlaylistFromIntent(intent)
                    val index = intent.getIntExtra(EXTRA_INDEX, 0)
                    if (fileRef != null) {
                        val pl = playlistExtra?.toList() ?: listOf(fileRef)
                        setPlaylist(pl, index)
                    }
                }
                ACTION_PLAY -> exoPlayer?.playWhenReady = true
                ACTION_PAUSE -> exoPlayer?.playWhenReady = false
                ACTION_NEXT -> playNext()
                ACTION_PREVIOUS -> playPrevious()
                ACTION_SEEK -> {
                    val position = intent.getLongExtra(EXTRA_POSITION, 0L)
                    exoPlayer?.seekTo(position)
                }
                ACTION_SET_REPEAT_MODE -> {
                    val mode = intent.getIntExtra(EXTRA_REPEAT_MODE, Player.REPEAT_MODE_OFF)
                    exoPlayer?.repeatMode = mode
                }
                ACTION_SET_SHUFFLE -> {
                    val shuffle = intent.getBooleanExtra(EXTRA_SHUFFLE, false)
                    // shuffleMode and playbackSpeed are not available in this Media3 version
                    MediaPlaybackStateHolder.updateShuffleMode(if (shuffle) 1 else 0)
                }
                ACTION_SET_SPEED -> {
                    val speed = intent.getFloatExtra(EXTRA_SPEED, 1.0f)
                    MediaPlaybackStateHolder.updatePlaybackSpeed(speed)
                }
                ACTION_STOP -> {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun buildFileReferenceFromIntent(intent: Intent): FileReference? {
        val path = intent.getStringExtra(EXTRA_FILE_PATH) ?: return null
        val name = intent.getStringExtra(EXTRA_FILE_NAME) ?: ""
        val mime = intent.getStringExtra(EXTRA_FILE_MIME) ?: ""
        val size = intent.getLongExtra(EXTRA_FILE_SIZE, 0L)
        val modified = intent.getLongExtra(EXTRA_FILE_MODIFIED, 0L)
        val providerStr = intent.getStringExtra(EXTRA_FILE_PROVIDER) ?: "LOCAL"
        val localBacking = intent.getStringExtra(EXTRA_FILE_LOCAL_BACKING) ?: ""
        val parent = intent.getStringExtra(EXTRA_FILE_PARENT) ?: ""
        val extension = intent.getStringExtra(EXTRA_FILE_EXTENSION) ?: ""
        val isDir = intent.getBooleanExtra(EXTRA_FILE_IS_DIR, false)
        
        val provider = ProviderType.valueOf(providerStr)
        val metadata = mutableMapOf<String, String>()
        if (localBacking.isNotBlank()) metadata["localBackingPath"] = localBacking
        
        return FileReference(
            providerType = provider,
            uriOrPath = path,
            displayName = name,
            parentUriOrPath = parent,
            isDirectory = isDir,
            sizeBytes = size,
            lastModifiedEpochMs = modified,
            mimeType = mime,
            metadata = metadata
        )
    }

    private fun buildPlaylistFromIntent(intent: Intent): List<FileReference>? {
        val paths = intent.getStringArrayExtra(EXTRA_PLAYLIST_PATHS) ?: return null
        val names = intent.getStringArrayExtra(EXTRA_PLAYLIST_NAMES) ?: return null
        val mimes = intent.getStringArrayExtra(EXTRA_PLAYLIST_MIMES) ?: return null
        val sizes = intent.getLongArrayExtra(EXTRA_PLAYLIST_SIZES) ?: return null
        val modified = intent.getLongArrayExtra(EXTRA_PLAYLIST_MODIFIED) ?: return null
        val providers = intent.getStringArrayExtra(EXTRA_PLAYLIST_PROVIDERS) ?: return null
        val localBackings = intent.getStringArrayExtra(EXTRA_PLAYLIST_LOCAL_BACKINGS) ?: return null
        val parents = intent.getStringArrayExtra(EXTRA_PLAYLIST_PARENTS) ?: return null
        val extensions = intent.getStringArrayExtra(EXTRA_PLAYLIST_EXTENSIONS) ?: return null
        val isDirs = intent.getBooleanArrayExtra(EXTRA_PLAYLIST_IS_DIRS) ?: return null
        
        if (paths.size != names.size) return null
        
        return paths.indices.map { i ->
            val provider = ProviderType.valueOf(providers[i])
            val metadata = mutableMapOf<String, String>()
            if (localBackings[i].isNotBlank()) metadata["localBackingPath"] = localBackings[i]
            
            FileReference(
                providerType = provider,
                uriOrPath = paths[i],
                displayName = names[i],
                parentUriOrPath = parents[i],
                isDirectory = isDirs[i],
                sizeBytes = sizes[i],
                lastModifiedEpochMs = modified[i],
                mimeType = mimes[i],
                metadata = metadata
            )
        }
    }

    private fun setPlaylist(newPlaylist: List<FileReference>, index: Int) {
        playlist = newPlaylist
        currentIndex = index.coerceIn(0, playlist.size - 1)
        currentFile = playlist[currentIndex]
        MediaPlaybackStateHolder.updateCurrentFile(currentFile)
        val mediaItem = createMediaItem(currentFile!!)
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = wasPlaying
        updateNotification()
    }

    private fun createMediaItem(file: FileReference): MediaItem {
        val uri = resolveMediaUri(file)
        return MediaItem.fromUri(uri)
    }

    private fun resolveMediaUri(file: FileReference): Uri {
        val localBacking = file.metadata["localBackingPath"]?.let { File(it) }
            ?: if (file.providerType == ProviderType.LOCAL) File(file.uriOrPath) else null
        return if (localBacking != null && localBacking.exists()) {
            Uri.fromFile(localBacking)
        } else {
            Uri.parse(file.uriOrPath)
        }
    }

    private fun playNext() {
        if (playlist.isEmpty()) return
        var nextIndex = currentIndex + 1
        val repeatModeVal = exoPlayer?.repeatMode ?: Player.REPEAT_MODE_OFF
        val shuffleModeVal = MediaPlaybackStateHolder.shuffleMode.value

        when {
            shuffleModeVal == 1 && playlist.size > 1 -> {
                val candidates = playlist.filterIndexed { i, _ -> i != currentIndex }
                if (candidates.isNotEmpty()) {
                    nextIndex = playlist.indexOf(candidates.random())
                }
            }
            nextIndex >= playlist.size -> {
                if (repeatModeVal == Player.REPEAT_MODE_ALL) {
                    nextIndex = 0
                } else {
                    return
                }
            }
        }
        if (nextIndex in playlist.indices) {
            currentIndex = nextIndex
            currentFile = playlist[currentIndex]
            MediaPlaybackStateHolder.updateCurrentFile(currentFile)
            val mediaItem = createMediaItem(currentFile!!)
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
            exoPlayer?.playWhenReady = true
            updateNotification()
        }
    }

    private fun playPrevious() {
        if (playlist.isEmpty()) return
        if ((exoPlayer?.currentPosition ?: 0L) > 3000) {
            exoPlayer?.seekTo(0L)
            return
        }
        var prevIndex = currentIndex - 1
        if (prevIndex < 0) {
            val repeatModeVal = exoPlayer?.repeatMode ?: Player.REPEAT_MODE_OFF
            if (repeatModeVal == Player.REPEAT_MODE_ALL) {
                prevIndex = playlist.size - 1
            } else {
                return
            }
        }
        if (prevIndex in playlist.indices) {
            currentIndex = prevIndex
            currentFile = playlist[currentIndex]
            MediaPlaybackStateHolder.updateCurrentFile(currentFile)
            val mediaItem = createMediaItem(currentFile!!)
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
            exoPlayer?.playWhenReady = true
            updateNotification()
        }
    }

    private fun handlePlaybackEnded() {
        val repeatModeVal = exoPlayer?.repeatMode ?: Player.REPEAT_MODE_OFF
        val shuffleModeVal = MediaPlaybackStateHolder.shuffleMode.value

        when {
            repeatModeVal == Player.REPEAT_MODE_ONE -> {
                exoPlayer?.seekTo(0L)
                exoPlayer?.playWhenReady = true
            }
            repeatModeVal == Player.REPEAT_MODE_ALL || shuffleModeVal == 1 -> {
                playNext()
            }
            else -> {
                wasPlaying = false
                MediaPlaybackStateHolder.updateIsPlaying(false)
                updateNotification()
            }
        }
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        
// Update foreground service notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(NOTIFICATION_ID, notification, 2) // FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK = 2
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for audio and video playback"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val file = currentFile ?: return buildEmptyNotification()

        val title = file.displayName.substringBeforeLast('.')
        val artist = extractArtist(file)
        val artwork = extractArtwork(file)

        val playPauseAction = if (exoPlayer?.isPlaying == true) {
            NotificationCompat.Action(
                R.drawable.ic_pause,
                "Pause",
                createPendingIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_play,
                "Play",
                createPendingIntent(ACTION_PLAY)
            )
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_media_notification)
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(artwork ?: getDefaultArtwork())
            .setOngoing(exoPlayer?.isPlaying == true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setStyle(
                MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_previous,
                    "Previous",
                    createPendingIntent(ACTION_PREVIOUS)
                )
            )
            .addAction(playPauseAction)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_next,
                    "Next",
                    createPendingIntent(ACTION_NEXT)
                )
            )
            .setContentIntent(createContentIntent())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    private fun buildEmptyNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_media_notification)
            .setContentTitle("OpenFile Media Player")
            .setContentText("No media playing")
            .setOngoing(false)
            .build()
    }

    private fun extractArtist(file: FileReference): String {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            val localBacking = file.metadata["localBackingPath"]?.let { File(it) }
                ?: if (file.providerType == ProviderType.LOCAL) File(file.uriOrPath) else null
            if (localBacking != null && localBacking.exists()) {
                retriever.setDataSource(localBacking.absolutePath)
            } else {
                retriever.setDataSource(this, Uri.parse(file.uriOrPath))
            }
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
        } catch (_: Exception) {
            "Unknown Artist"
        }
    }

    private fun extractArtwork(file: FileReference): Bitmap? {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            val localBacking = file.metadata["localBackingPath"]?.let { File(it) }
                ?: if (file.providerType == ProviderType.LOCAL) File(file.uriOrPath) else null
            if (localBacking != null && localBacking.exists()) {
                retriever.setDataSource(localBacking.absolutePath)
            } else {
                retriever.setDataSource(this, Uri.parse(file.uriOrPath))
            }
            val picture = retriever.embeddedPicture
            if (picture != null) {
                BitmapFactory.decodeByteArray(picture, 0, picture.size)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getDefaultArtwork(): Bitmap? {
        return BitmapFactory.decodeResource(resources, R.drawable.ic_audio_placeholder)
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MediaPlaybackForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onDestroy() {
        notificationJob?.cancel()
        exoPlayer?.run {
            removeListener(object : Player.Listener {})
            stop()
            release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}