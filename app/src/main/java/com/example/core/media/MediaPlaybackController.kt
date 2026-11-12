package com.example.core.media

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.media3.common.Player
import com.example.core.model.FileReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MediaPlaybackController(
    private val context: Context,
    private val scope: CoroutineScope
) : LifecycleObserver {

    private var connectionJob: Job? = null

    // Local state that mirrors the service state
    val isPlaying = MutableStateFlow(false)
    val currentPosition = MutableStateFlow(0L)
    val duration = MutableStateFlow(0L)
    val currentFile = MutableStateFlow<FileReference?>(null)
    val repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val shuffleMode = MutableStateFlow(0) // 0 = off, 1 = all
    val playbackSpeed = MutableStateFlow(1.0f)
    val playbackError = MutableStateFlow<String?>(null)

    val isPlayingState = isPlaying.asStateFlow()
    val currentPositionState = currentPosition.asStateFlow()
    val durationState = duration.asStateFlow()
    val currentFileState = currentFile.asStateFlow()
    val repeatModeState = repeatMode.asStateFlow()
    val shuffleModeState = shuffleMode.asStateFlow()
    val playbackSpeedState = playbackSpeed.asStateFlow()
    val playbackErrorState = playbackError.asStateFlow()

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun connect() {
        connectionJob?.cancel()
        connectionJob = scope.launch(Dispatchers.Main) {
            // Sync with service state by observing the service's static state flows
            MediaPlaybackStateHolder.isPlaying.collect { value ->
                isPlaying.value = value
            }
            MediaPlaybackStateHolder.currentPosition.collect { value ->
                currentPosition.value = value
            }
            MediaPlaybackStateHolder.duration.collect { value ->
                duration.value = value
            }
            MediaPlaybackStateHolder.currentFile.collect { value ->
                currentFile.value = value
            }
            MediaPlaybackStateHolder.repeatMode.collect { value ->
                repeatMode.value = value
            }
            MediaPlaybackStateHolder.shuffleMode.collect { value ->
                shuffleMode.value = value
            }
            MediaPlaybackStateHolder.playbackSpeed.collect { value ->
                playbackSpeed.value = value
            }
            MediaPlaybackStateHolder.playbackError.collect { value ->
                playbackError.value = value
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun disconnect() {
        connectionJob?.cancel()
    }

    fun play(file: FileReference, playlist: List<FileReference>, index: Int) {
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
        context.startForegroundService(intent)
        currentFile.value = file
    }

    fun playPause() {
        if (isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        val intent = Intent(context, MediaPlaybackForegroundService::class.java).apply {
            action = MediaPlaybackForegroundService.ACTION_PLAY
        }
        context.startForegroundService(intent)
    }

    fun pause() {
        val intent = Intent(context, MediaPlaybackForegroundService::class.java).apply {
            action = MediaPlaybackForegroundService.ACTION_PAUSE
        }
        context.startForegroundService(intent)
    }

    fun next() {
        val intent = Intent(context, MediaPlaybackForegroundService::class.java).apply {
            action = MediaPlaybackForegroundService.ACTION_NEXT
        }
        context.startForegroundService(intent)
    }

    fun previous() {
        val intent = Intent(context, MediaPlaybackForegroundService::class.java).apply {
            action = MediaPlaybackForegroundService.ACTION_PREVIOUS
        }
        context.startForegroundService(intent)
    }

    fun seekTo(position: Long) {
        val intent = Intent(context, MediaPlaybackForegroundService::class.java).apply {
            action = MediaPlaybackForegroundService.ACTION_SEEK
            putExtra(MediaPlaybackForegroundService.EXTRA_POSITION, position)
        }
        context.startForegroundService(intent)
        currentPosition.value = position
    }

    fun setRepeatMode(mode: Int) {
        repeatMode.value = mode
        val intent = Intent(context, MediaPlaybackForegroundService::class.java).apply {
            action = MediaPlaybackForegroundService.ACTION_SET_REPEAT_MODE
            putExtra(MediaPlaybackForegroundService.EXTRA_REPEAT_MODE, mode)
        }
        context.startForegroundService(intent)
    }

    fun setShuffleMode(shuffle: Boolean) {
        shuffleMode.value = if (shuffle) 1 else 0
        val intent = Intent(context, MediaPlaybackForegroundService::class.java).apply {
            action = MediaPlaybackForegroundService.ACTION_SET_SHUFFLE
            putExtra(MediaPlaybackForegroundService.EXTRA_SHUFFLE, shuffle)
        }
        context.startForegroundService(intent)
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed.value = speed
        val intent = Intent(context, MediaPlaybackForegroundService::class.java).apply {
            action = MediaPlaybackForegroundService.ACTION_SET_SPEED
            putExtra(MediaPlaybackForegroundService.EXTRA_SPEED, speed)
        }
        context.startForegroundService(intent)
    }

    fun release() {
        disconnect()
    }
}

// Singleton to hold shared state between service and UI
object MediaPlaybackStateHolder {
    private val _isPlaying = MutableStateFlow(false)
    private val _currentPosition = MutableStateFlow(0L)
    private val _duration = MutableStateFlow(0L)
    private val _currentFile = MutableStateFlow<FileReference?>(null)
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    private val _shuffleMode = MutableStateFlow(0)
    private val _playbackSpeed = MutableStateFlow(1.0f)
    private val _playbackError = MutableStateFlow<String?>(null)

    val isPlaying = _isPlaying.asStateFlow()
    val currentPosition = _currentPosition.asStateFlow()
    val duration = _duration.asStateFlow()
    val currentFile = _currentFile.asStateFlow()
    val repeatMode = _repeatMode.asStateFlow()
    val shuffleMode = _shuffleMode.asStateFlow()
    val playbackSpeed = _playbackSpeed.asStateFlow()
    val playbackError = _playbackError.asStateFlow()

    fun updateIsPlaying(value: Boolean) { _isPlaying.value = value }
    fun updateCurrentPosition(value: Long) { _currentPosition.value = value }
    fun updateDuration(value: Long) { _duration.value = value }
    fun updateCurrentFile(value: FileReference?) { _currentFile.value = value }
    fun updateRepeatMode(value: Int) { _repeatMode.value = value }
    fun updateShuffleMode(value: Int) { _shuffleMode.value = value }
    fun updatePlaybackSpeed(value: Float) { _playbackSpeed.value = value }
    fun updatePlaybackError(value: String?) { _playbackError.value = value }
}