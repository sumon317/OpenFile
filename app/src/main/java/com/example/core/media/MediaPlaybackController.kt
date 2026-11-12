package com.example.core.media

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.LifecycleObserver
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.example.core.model.FileReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MediaPlaybackController(
    private val context: Context,
    private val scope: CoroutineScope
) : LifecycleObserver {

    val isPlayingState: StateFlow<Boolean> = MediaPlaybackStateHolder.isPlaying
    val currentPositionState: StateFlow<Long> = MediaPlaybackStateHolder.currentPosition
    val durationState: StateFlow<Long> = MediaPlaybackStateHolder.duration
    val currentFileState: StateFlow<FileReference?> = MediaPlaybackStateHolder.currentFile
    val repeatModeState: StateFlow<Int> = MediaPlaybackStateHolder.repeatMode
    val shuffleModeState: StateFlow<Int> = MediaPlaybackStateHolder.shuffleMode
    val playbackSpeedState: StateFlow<Float> = MediaPlaybackStateHolder.playbackSpeed
    val playbackErrorState: StateFlow<String?> = MediaPlaybackStateHolder.playbackError

    val isPlaying: StateFlow<Boolean> get() = isPlayingState
    val currentPosition: StateFlow<Long> get() = currentPositionState
    val duration: StateFlow<Long> get() = durationState
    val currentFile: StateFlow<FileReference?> get() = currentFileState
    val repeatMode: StateFlow<Int> get() = repeatModeState
    val shuffleMode: StateFlow<Int> get() = shuffleModeState
    val playbackSpeed: StateFlow<Float> get() = playbackSpeedState
    val playbackError: StateFlow<String?> get() = playbackErrorState

    fun connect() {
        // State is directly exposed via MediaPlaybackStateHolder StateFlows
    }

    fun disconnect() {
    }

    fun play(file: FileReference, playlist: List<FileReference>, index: Int) {
        MediaPlaybackStateHolder.updateCurrentFile(file)
        MediaPlaybackStateHolder.updateCurrentPosition(0L)
        MediaPlaybackStateHolder.updateIsPlaying(true)
        val intent = Intent(context, MediaPlaybackForegroundService::class.java).apply {
            action = MediaPlaybackForegroundService.ACTION_SET_PLAYLIST
            putExtra(MediaPlaybackForegroundService.EXTRA_FILE_PATH, file.uriOrPath)
            putExtra(MediaPlaybackForegroundService.EXTRA_FILE_NAME, file.displayName)
            putExtra(MediaPlaybackForegroundService.EXTRA_FILE_MIME, file.mimeType)
            putExtra(MediaPlaybackForegroundService.EXTRA_FILE_SIZE, file.sizeBytes)
            putExtra(MediaPlaybackForegroundService.EXTRA_FILE_MODIFIED, file.lastModifiedEpochMs)
            putExtra(MediaPlaybackForegroundService.EXTRA_FILE_PROVIDER, file.providerType.name)
            putExtra(MediaPlaybackForegroundService.EXTRA_FILE_LOCAL_BACKING, file.metadata["localBackingPath"] ?: "")
            putExtra(MediaPlaybackForegroundService.EXTRA_FILE_PARENT, file.parentUriOrPath ?: "")
            putExtra(MediaPlaybackForegroundService.EXTRA_FILE_EXTENSION, file.extension)
            putExtra(MediaPlaybackForegroundService.EXTRA_FILE_IS_DIR, file.isDirectory)
            putExtra(MediaPlaybackForegroundService.EXTRA_PLAYLIST_PATHS, playlist.map { it.uriOrPath }.toTypedArray())
            putExtra(MediaPlaybackForegroundService.EXTRA_PLAYLIST_NAMES, playlist.map { it.displayName }.toTypedArray())
            putExtra(MediaPlaybackForegroundService.EXTRA_PLAYLIST_MIMES, playlist.map { it.mimeType }.toTypedArray())
            putExtra(MediaPlaybackForegroundService.EXTRA_PLAYLIST_SIZES, playlist.map { it.sizeBytes }.toLongArray())
            putExtra(MediaPlaybackForegroundService.EXTRA_PLAYLIST_MODIFIED, playlist.map { it.lastModifiedEpochMs }.toLongArray())
            putExtra(MediaPlaybackForegroundService.EXTRA_PLAYLIST_PROVIDERS, playlist.map { it.providerType.name }.toTypedArray())
            putExtra(MediaPlaybackForegroundService.EXTRA_PLAYLIST_LOCAL_BACKINGS, playlist.map { it.metadata["localBackingPath"] ?: "" }.toTypedArray())
            putExtra(MediaPlaybackForegroundService.EXTRA_PLAYLIST_PARENTS, playlist.map { it.parentUriOrPath ?: "" }.toTypedArray())
            putExtra(MediaPlaybackForegroundService.EXTRA_PLAYLIST_EXTENSIONS, playlist.map { it.extension }.toTypedArray())
            putExtra(MediaPlaybackForegroundService.EXTRA_PLAYLIST_IS_DIRS, playlist.map { it.isDirectory }.toBooleanArray())
            putExtra(MediaPlaybackForegroundService.EXTRA_INDEX, index)
        }
        startService(intent)
    }

    fun playPause() {
        if (isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        MediaPlaybackStateHolder.updateIsPlaying(true)
        val intent = Intent(context, MediaPlaybackForegroundService::class.java).apply {
            action = MediaPlaybackForegroundService.ACTION_PLAY
        }
        startService(intent)
    }

    fun pause() {
        MediaPlaybackStateHolder.updateIsPlaying(false)
        val intent = Intent(context, MediaPlaybackForegroundService::class.java).apply {
            action = MediaPlaybackForegroundService.ACTION_PAUSE
        }
        startService(intent)
    }

    fun next() {
        val intent = Intent(context, MediaPlaybackForegroundService::class.java).apply {
            action = MediaPlaybackForegroundService.ACTION_NEXT
        }
        startService(intent)
    }

    fun previous() {
        val intent = Intent(context, MediaPlaybackForegroundService::class.java).apply {
            action = MediaPlaybackForegroundService.ACTION_PREVIOUS
        }
        startService(intent)
    }

    fun seekTo(position: Long) {
        MediaPlaybackStateHolder.updateCurrentPosition(position)
        val intent = Intent(context, MediaPlaybackForegroundService::class.java).apply {
            action = MediaPlaybackForegroundService.ACTION_SEEK
            putExtra(MediaPlaybackForegroundService.EXTRA_POSITION, position)
        }
        startService(intent)
    }

    fun setRepeatMode(mode: Int) {
        MediaPlaybackStateHolder.updateRepeatMode(mode)
        val intent = Intent(context, MediaPlaybackForegroundService::class.java).apply {
            action = MediaPlaybackForegroundService.ACTION_SET_REPEAT_MODE
            putExtra(MediaPlaybackForegroundService.EXTRA_REPEAT_MODE, mode)
        }
        startService(intent)
    }

    fun setShuffleMode(shuffle: Boolean) {
        MediaPlaybackStateHolder.updateShuffleMode(if (shuffle) 1 else 0)
        val intent = Intent(context, MediaPlaybackForegroundService::class.java).apply {
            action = MediaPlaybackForegroundService.ACTION_SET_SHUFFLE
            putExtra(MediaPlaybackForegroundService.EXTRA_SHUFFLE, shuffle)
        }
        startService(intent)
    }

    fun setPlaybackSpeed(speed: Float) {
        MediaPlaybackStateHolder.updatePlaybackSpeed(speed)
        val intent = Intent(context, MediaPlaybackForegroundService::class.java).apply {
            action = MediaPlaybackForegroundService.ACTION_SET_SPEED
            putExtra(MediaPlaybackForegroundService.EXTRA_SPEED, speed)
        }
        startService(intent)
    }

    fun stop() {
        val intent = Intent(context, MediaPlaybackForegroundService::class.java).apply {
            action = MediaPlaybackForegroundService.ACTION_STOP
        }
        startService(intent)
        MediaPlaybackStateHolder.releasePlayer()
    }

    fun release() {
        disconnect()
    }

    private fun startService(intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {}
    }
}

// Singleton to hold shared state and ExoPlayer between service and UI
object MediaPlaybackStateHolder {
    private var _exoPlayer: ExoPlayer? = null

    val activePlayer: ExoPlayer?
        get() = _exoPlayer

    @androidx.annotation.OptIn(UnstableApi::class)
    fun getOrCreatePlayer(context: Context): ExoPlayer {
        val existing = _exoPlayer
        if (existing != null) return existing
        val renderersFactory = DefaultRenderersFactory(context.applicationContext).apply {
            setEnableDecoderFallback(true)
        }
        val player = ExoPlayer.Builder(context.applicationContext, renderersFactory).build().apply {
            setHandleAudioBecomingNoisy(true)
        }
        _exoPlayer = player
        return player
    }

    fun releasePlayer() {
        _exoPlayer?.run {
            stop()
            release()
        }
        _exoPlayer = null
        updateIsPlaying(false)
        updateCurrentPosition(0L)
        updateDuration(0L)
        updateCurrentFile(null)
    }

    private val _isPlaying = MutableStateFlow(false)
    private val _currentPosition = MutableStateFlow(0L)
    private val _duration = MutableStateFlow(0L)
    private val _currentFile = MutableStateFlow<FileReference?>(null)
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    private val _shuffleMode = MutableStateFlow(0)
    private val _playbackSpeed = MutableStateFlow(1.0f)
    private val _playbackError = MutableStateFlow<String?>(null)

    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    val duration: StateFlow<Long> = _duration.asStateFlow()
    val currentFile: StateFlow<FileReference?> = _currentFile.asStateFlow()
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()
    val shuffleMode: StateFlow<Int> = _shuffleMode.asStateFlow()
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    fun updateIsPlaying(value: Boolean) { _isPlaying.value = value }
    fun updateCurrentPosition(value: Long) { _currentPosition.value = value }
    fun updateDuration(value: Long) { _duration.value = value }
    fun updateCurrentFile(value: FileReference?) { _currentFile.value = value }
    fun updateRepeatMode(value: Int) { _repeatMode.value = value }
    fun updateShuffleMode(value: Int) { _shuffleMode.value = value }
    fun updatePlaybackSpeed(value: Float) { _playbackSpeed.value = value }
    fun updatePlaybackError(value: String?) { _playbackError.value = value }
}
