@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ui.components

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import com.example.core.model.FileReference
import com.example.core.model.ProviderType
import com.example.ui.theme.AudioPurple
import com.example.ui.theme.VideoCoral
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import kotlin.math.abs

/**
 * Formats duration in milliseconds to mm:ss or hh:mm:ss.
 */
fun formatMediaDuration(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

data class AudioTrackInfo(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artwork: ImageBitmap? = null,
)

private fun resolveMediaUri(file: FileReference): Uri {
    val localBacking = file.metadata["localBackingPath"]?.let { File(it) }
        ?: if (file.providerType == ProviderType.LOCAL) File(file.uriOrPath) else null

    return if (localBacking != null && localBacking.exists()) {
        Uri.fromFile(localBacking)
    } else {
        Uri.parse(file.uriOrPath)
    }
}

private fun extractAudioTrackInfo(context: Context, file: FileReference): AudioTrackInfo {
    var title = file.displayName.substringBeforeLast('.')
    var artist = "Unknown Artist"
    var album = ""
    var duration = 0L
    var artwork: ImageBitmap? = null
    try {
        val retriever = MediaMetadataRetriever()
        val localBacking = file.metadata["localBackingPath"]?.let { File(it) }
            ?: if (file.providerType == ProviderType.LOCAL) File(file.uriOrPath) else null
        if ((localBacking != null) && localBacking.exists()) {
            retriever.setDataSource(localBacking.absolutePath)
        } else {
            retriever.setDataSource(context, Uri.parse(file.uriOrPath))
        }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { if (it.isNotBlank()) title = it }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { if (it.isNotBlank()) artist = it }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { if (it.isNotBlank()) album = it }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { duration = it }
        val picture = retriever.embeddedPicture
        if (picture != null) {
            val bmp = BitmapFactory.decodeByteArray(picture, 0, picture.size)
            if (bmp != null) {
                artwork = bmp.asImageBitmap()
            }
        }
        retriever.release()
    } catch (_: Exception) {}
    return AudioTrackInfo(title, artist, album, duration, artwork)
}

/**
 * Built-in Modern Audio Player powered by Jetpack Media3 ExoPlayer.
 * Features: High-fidelity audio playback, embedded album art, seek scrubber,
 * waveform animation, playback speeds, and repeat loop modes.
 */
@Composable
fun AudioPlayerPreview(
    file: FileReference,
    onOpenExternal: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    hasPrevious: Boolean = false,
    hasNext: Boolean = false,
    onDismiss: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var trackInfo by remember(file.uriOrPath) { mutableStateOf<AudioTrackInfo?>(null) }
    var currentPositionMs by rememberSaveable(file.uriOrPath) { mutableLongStateOf(0L) }
    var durationMs by rememberSaveable(file.uriOrPath) { mutableLongStateOf(0L) }
    var wasPlaying by rememberSaveable(file.uriOrPath) { mutableStateOf(true) }
    var playbackSpeed by rememberSaveable(file.uriOrPath) { mutableFloatStateOf(1.0f) }
    var isLooping by rememberSaveable(file.uriOrPath) { mutableStateOf(false) }

    var isPrepared by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }

    val exoPlayer = remember(file.uriOrPath) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(resolveMediaUri(file))
            setMediaItem(mediaItem)
            if (currentPositionMs > 0L) {
                seekTo(currentPositionMs)
            }
            if (playbackSpeed != 1.0f) {
                setPlaybackSpeed(playbackSpeed)
            }
            repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = wasPlaying
        }
    }

    // Attach ExoPlayer Listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        isPrepared = true
                        val d = exoPlayer.duration
                        if (d > 0L) durationMs = d
                        if (currentPositionMs > 0L && Math.abs(exoPlayer.currentPosition - currentPositionMs) > 1000L) {
                            exoPlayer.seekTo(currentPositionMs)
                        }
                    }
                    Player.STATE_ENDED -> {
                        if (isLooping) {
                            exoPlayer.seekTo(0L)
                            exoPlayer.play()
                        } else if (hasNext && onNext != null) {
                            onNext()
                        } else {
                            isPlaying = false
                            wasPlaying = false
                            currentPositionMs = durationMs
                        }
                    }
                    Player.STATE_BUFFERING -> {}
                    Player.STATE_IDLE -> {}
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                wasPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = error.localizedMessage ?: "ExoPlayer error (${error.errorCode})"
                isPlaying = false
                wasPlaying = false
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            wasPlaying = exoPlayer.isPlaying
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Load track metadata off the main thread
    LaunchedEffect(file) {
        val info = withContext(Dispatchers.IO) {
            extractAudioTrackInfo(context, file)
        }
        trackInfo = info
        if (info.durationMs > 0 && durationMs == 0L) {
            durationMs = info.durationMs
        }
    }

    // Polling position updater
    LaunchedEffect(isPlaying, isPrepared, isUserSeeking) {
        while (isActive && isPlaying && !isUserSeeking) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            currentPositionMs = pos
            val dur = exoPlayer.duration
            if (dur > 0L) durationMs = dur
            delay(250)
        }
    }

    // Common Player Controls Composable
    val playerControls = @Composable {
        // Seek Bar (Slider)
        val progress = if (durationMs > 0L) {
            if (isUserSeeking) seekFraction else (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

        Slider(
            value = progress,
            onValueChange = { frac ->
                isUserSeeking = true
                seekFraction = frac
                currentPositionMs = (frac * durationMs).toLong()
            },
            onValueChangeFinished = {
                val targetMs = (seekFraction * durationMs).toLong()
                exoPlayer.seekTo(targetMs)
                currentPositionMs = targetMs
                isUserSeeking = false
            },
            enabled = isPrepared && durationMs > 0L,
            colors = SliderDefaults.colors(
                thumbColor = AudioPurple,
                activeTrackColor = AudioPurple,
                inactiveTrackColor = AudioPurple.copy(alpha = 0.24f)
            ),
            modifier = Modifier.fillMaxWidth().testTag("audio_seek_slider")
        )

        // Time labels & Speed Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatMediaDuration(currentPositionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Speed dropdown selector
            Box {
                TextButton(
                    onClick = { showSpeedMenu = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(14.dp), tint = AudioPurple)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "${playbackSpeed}x", style = MaterialTheme.typography.labelSmall, color = AudioPurple)
                }

                DropdownMenu(
                    expanded = showSpeedMenu,
                    onDismissRequest = { showSpeedMenu = false }
                ) {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                        DropdownMenuItem(
                            text = { Text("${speed}x") },
                            onClick = {
                                playbackSpeed = speed
                                exoPlayer.setPlaybackSpeed(speed)
                                showSpeedMenu = false
                            }
                        )
                    }
                }
            }

            Text(
                text = formatMediaDuration(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Loop toggle
            IconButton(
                onClick = {
                    val newLoop = !isLooping
                    isLooping = newLoop
                    exoPlayer.repeatMode = if (newLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                },
                enabled = isPrepared
            ) {
                Icon(
                    imageVector = if (isLooping) Icons.Filled.RepeatOn else Icons.Filled.Repeat,
                    contentDescription = "Loop Audio",
                    tint = if (isLooping) AudioPurple else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Previous Track Button
            IconButton(
                onClick = {
                    if (currentPositionMs > 3000L || !hasPrevious || onPrevious == null) {
                        exoPlayer.seekTo(0L)
                        currentPositionMs = 0L
                        exoPlayer.play()
                    } else {
                        onPrevious()
                    }
                },
                enabled = isPrepared
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous Track",
                    modifier = Modifier.size(32.dp),
                    tint = if (hasPrevious || currentPositionMs > 3000L) AudioPurple else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }

            // Rewind 10s
            IconButton(
                onClick = {
                    val newPos = (currentPositionMs - 10000L).coerceAtLeast(0L)
                    exoPlayer.seekTo(newPos)
                    currentPositionMs = newPos
                },
                enabled = isPrepared
            ) {
                Icon(
                    imageVector = Icons.Filled.Replay10,
                    contentDescription = "Rewind 10 seconds",
                    modifier = Modifier.size(30.dp)
                )
            }

            // Play / Pause Circle
            FilledIconButton(
                onClick = {
                    if (isPlaying) {
                        exoPlayer.pause()
                    } else {
                        if (currentPositionMs >= durationMs && durationMs > 0L) {
                            exoPlayer.seekTo(0L)
                            currentPositionMs = 0L
                        }
                        exoPlayer.play()
                    }
                },
                modifier = Modifier
                    .size(60.dp)
                    .testTag("audio_play_pause_button"),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = AudioPurple,
                    contentColor = Color.White
                ),
                enabled = isPrepared
            ) {
                if (!isPrepared) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            // Forward 10s
            IconButton(
                onClick = {
                    val newPos = (currentPositionMs + 10000L).coerceAtMost(durationMs)
                    exoPlayer.seekTo(newPos)
                    currentPositionMs = newPos
                },
                enabled = isPrepared
            ) {
                Icon(
                    imageVector = Icons.Filled.Forward10,
                    contentDescription = "Forward 10 seconds",
                    modifier = Modifier.size(30.dp)
                )
            }

            // Next Track Button
            IconButton(
                onClick = {
                    if (hasNext && onNext != null) {
                        onNext()
                    } else {
                        exoPlayer.seekTo(0L)
                        currentPositionMs = 0L
                        exoPlayer.play()
                    }
                },
                enabled = isPrepared
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next Track",
                    modifier = Modifier.size(32.dp),
                    tint = if (hasNext) AudioPurple else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }

            // Replay from start
            IconButton(
                onClick = {
                    exoPlayer.seekTo(0L)
                    currentPositionMs = 0L
                    exoPlayer.play()
                },
                enabled = isPrepared
            ) {
                Icon(
                    imageVector = Icons.Filled.Replay,
                    contentDescription = "Replay from Start"
                )
            }
        }

        if (onOpenExternal != null) {
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onOpenExternal) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open in External Player")
            }
        }
    }

    // Artwork Card Composable
    val artwork = trackInfo?.artwork
    val artworkCard = @Composable { sizeDp: Int ->
        Card(
            modifier = Modifier
                .size(sizeDp.dp)
                .testTag("audio_album_card"),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (artwork != null) {
                    Image(
                        bitmap = artwork,
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(AudioPurple.copy(alpha = 0.4f), MaterialTheme.colorScheme.surfaceContainerHighest)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Audiotrack,
                            contentDescription = null,
                            tint = AudioPurple,
                            modifier = Modifier.size((sizeDp * 0.4f).dp)
                        )
                    }
                }

                // Waveform bars overlay when playing
                if (isPlaying) {
                    AudioWaveformIndicator(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                    )
                }
            }
        }
    }

    if (errorMessage != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("audio_player_preview"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (onDismiss != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "Failed to play audio",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                    if (onOpenExternal != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = onOpenExternal) {
                            Text("Open With Another App")
                        }
                    }
                }
            }
        }
        return
    }

    if (isLandscape) {
        // Landscape Mode: Side-by-side artwork and controls
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .testTag("audio_player_preview"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(end = 24.dp)
            ) {
                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                artworkCard(180)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = trackInfo?.title ?: file.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().testTag("audio_track_title")
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = listOfNotNull(
                        trackInfo?.artist.takeIf { !it.isNullOrBlank() && it != "Unknown Artist" },
                        trackInfo?.album.takeIf { !it.isNullOrBlank() },
                        "ExoPlayer • ${file.extension.uppercase()} • ${file.formattedSize}"
                    ).joinToString(" • ").ifBlank { file.formattedSize },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))
                playerControls()
            }
        }
    } else {
        // Portrait Mode: Vertical layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
                .testTag("audio_player_preview"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (onDismiss != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            artworkCard(240)

            Spacer(modifier = Modifier.height(24.dp))

            // Title & Artist
            Text(
                text = trackInfo?.title ?: file.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag("audio_track_title")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = listOfNotNull(
                    trackInfo?.artist.takeIf { !it.isNullOrBlank() && it != "Unknown Artist" },
                    trackInfo?.album.takeIf { !it.isNullOrBlank() },
                    "ExoPlayer • ${file.extension.uppercase()} • ${file.formattedSize}"
                ).joinToString(" • ").ifBlank { file.formattedSize },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))
            playerControls()
        }
    }
}

/**
 * Animated audio waveform indicator showing rhythmic pulsing bars.
 */
@Composable
private fun AudioWaveformIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val heights = (0..5).map { index ->
        transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(400 + index * 100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$index"
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { anim ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((anim.value * 20).dp.coerceAtLeast(4.dp))
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White)
            )
        }
    }
}

enum class GestureHudType { VOLUME, BRIGHTNESS, SEEK }
enum class DoubleTapFeedbackType { REWIND, FORWARD }
enum class VideoPlayMode { PLAY_ALL, REPEAT_ONE, REPEAT_OFF }
enum class DecoderMode(val code: String, val title: String, val description: String) {
    HW(
        code = "HW",
        title = "HW (Hardware)",
        description = "Hardware acceleration via device GPU / MediaCodec chip. High-performance & power-efficient."
    ),
    HW_PLUS(
        code = "HW+",
        title = "HW+ (Hardware Plus)",
        description = "Hardware video decoding with custom software audio pipeline & loudness enhancement (200% volume boost)."
    ),
    SW(
        code = "SW",
        title = "SW (Software)",
        description = "Software CPU decoding. Maximum compatibility for rare or complex video codecs."
    )
}

data class OnlineSubtitleItem(
    val title: String,
    val language: String,
    val downloadUrl: String,
    val format: String = "srt"
)

object SubtitleManager {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun searchOnlineSubtitles(query: String): List<OnlineSubtitleItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<OnlineSubtitleItem>()
        if (query.isBlank()) return@withContext results

        val cleanQuery = query
            .replace(Regex("""(?i)\b(1080p|720p|480p|2160p|4k|bluray|web-dl|webrip|brrip|x264|x265|hevc|h264|aac|ac3|dts|hdrip|yify|yts)\b"""), "")
            .replace(Regex("""[._\-\[\]()]"""), " ")
            .trim()

        // 1. Query OpenSubtitles REST API
        try {
            val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
            val request = Request.Builder()
                .url("https://rest.opensubtitles.org/search/query-$encodedQuery")
                .header("User-Agent", "TemporaryUserAgent")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrBlank() && bodyString.startsWith("[")) {
                        val array = JSONArray(bodyString)
                        for (i in 0 until minOf(array.length(), 25)) {
                            val obj = array.getJSONObject(i)
                            val name = obj.optString("SubFileName", "Subtitle")
                            val lang = obj.optString("LanguageName", "English")
                            val dl = obj.optString("SubDownloadLink", "")
                            val format = obj.optString("SubFormat", "srt")
                            if (dl.isNotBlank()) {
                                results.add(OnlineSubtitleItem(title = name, language = lang, downloadUrl = dl, format = format))
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Query SubDL API if primary source returned no results
        if (results.isEmpty()) {
            try {
                val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
                val request = Request.Builder()
                    .url("https://api.subdl.com/api/v1/subtitles?film_name=$encodedQuery")
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string()
                        if (!bodyString.isNullOrBlank()) {
                            val json = JSONObject(bodyString)
                            val subs = json.optJSONArray("subtitles")
                            if (subs != null) {
                                for (i in 0 until minOf(subs.length(), 20)) {
                                    val sub = subs.getJSONObject(i)
                                    val releaseName = sub.optString("release_name", cleanQuery)
                                    val lang = sub.optString("language", "English")
                                    val link = sub.optString("url", "")
                                    if (link.isNotBlank()) {
                                        val fullUrl = if (link.startsWith("http")) link else "https://dl.subdl.com$link"
                                        results.add(OnlineSubtitleItem(title = releaseName, language = lang, downloadUrl = fullUrl, format = "srt"))
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        results
    }

    suspend fun downloadSubtitle(context: Context, downloadUrl: String, suggestedName: String): File? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "TemporaryUserAgent")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bytes = response.body?.bytes() ?: return@withContext null

                val subDir = File(context.cacheDir, "subtitles").apply { mkdirs() }
                val safeBaseName = suggestedName.replace(Regex("""[^a-zA-Z0-9._-]"""), "_")
                val targetFile = File(subDir, if (safeBaseName.endsWith(".srt", ignoreCase = true)) safeBaseName else "$safeBaseName.srt")

                if (bytes.size > 2 && (bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte())) {
                    GZIPInputStream(bytes.inputStream()).use { gzipIn ->
                        FileOutputStream(targetFile).use { out ->
                            gzipIn.copyTo(out)
                        }
                    }
                } else if (bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                    ZipInputStream(bytes.inputStream()).use { zipIn ->
                        var entry = zipIn.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && (entry.name.endsWith(".srt", ignoreCase = true) || entry.name.endsWith(".vtt", ignoreCase = true) || entry.name.endsWith(".ass", ignoreCase = true))) {
                                FileOutputStream(targetFile).use { out ->
                                    zipIn.copyTo(out)
                                }
                                break
                            }
                            entry = zipIn.nextEntry
                        }
                    }
                } else {
                    targetFile.writeBytes(bytes)
                }

                if (targetFile.exists() && targetFile.length() > 0) targetFile else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun findMatchingLocalSubtitles(file: FileReference): List<File> {
        val backingPath = file.metadata["localBackingPath"] ?: file.uriOrPath
        val videoFile = File(backingPath)
        if (!videoFile.exists() || !videoFile.isFile) return emptyList()
        val parent = videoFile.parentFile ?: return emptyList()
        val baseName = videoFile.nameWithoutExtension.lowercase()

        val subExtensions = setOf("srt", "vtt", "ass", "sub")
        return parent.listFiles { f ->
            f.isFile && f.extension.lowercase() in subExtensions &&
                (f.nameWithoutExtension.lowercase() == baseName || f.nameWithoutExtension.lowercase().startsWith(baseName))
        }?.toList() ?: emptyList()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun attachSubtitleToPlayer(exoPlayer: ExoPlayer, subUri: Uri, subLanguage: String = "en", subName: String = "Subtitle") {
        val currentItem = exoPlayer.currentMediaItem ?: return
        val nameLower = subName.lowercase()
        val mimeType = when {
            nameLower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            nameLower.endsWith(".ass") || nameLower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val subConfig = MediaItem.SubtitleConfiguration.Builder(subUri)
            .setMimeType(mimeType)
            .setLanguage(subLanguage)
            .setLabel(subName)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        val updatedItem = currentItem.buildUpon()
            .setSubtitleConfigurations(listOf(subConfig))
            .build()

        val pos = exoPlayer.currentPosition
        val isPlaying = exoPlayer.isPlaying
        exoPlayer.setMediaItem(updatedItem)
        exoPlayer.seekTo(pos)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = isPlaying
    }
}

/**
 * Built-in Modern Full-Featured Video Player powered by Jetpack Media3 ExoPlayer & PlayerView.
 * Features:
 * - Gesture swipe HUD controls: Left vertical = Brightness, Right vertical = Volume, Horizontal = Seek
 * - Progressive seekbar with buffered playback visualization
 * - Screen rotation toggle (Portrait <-> Sensor Landscape)
 * - Lock controls with floating unlock gesture
 * - Embedded, local file, and online subtitle search and download
 * - Audio track switcher and hardware acceleration (HW MediaCodec) badge
 * - Aspect ratio modes (Fit, Crop/Zoom, Stretch)
 * - Picture-in-Picture (PiP) support
 * - Option to play as audio with audio-only visualizer surface
 * - Video playlist / queue bottom sheet with "Play All" auto-advance
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerPreview(
    file: FileReference,
    playlist: List<FileReference> = emptyList(),
    onSelectMedia: ((FileReference) -> Unit)? = null,
    onOpenExternal: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    hasPrevious: Boolean = false,
    hasNext: Boolean = false,
    onDismiss: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var currentPositionMs by rememberSaveable(file.uriOrPath) { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by rememberSaveable(file.uriOrPath) { mutableLongStateOf(0L) }
    var wasPlaying by rememberSaveable(file.uriOrPath) { mutableStateOf(true) }
    var playbackSpeed by rememberSaveable(file.uriOrPath) { mutableFloatStateOf(1.0f) }
    var playMode by rememberSaveable(file.uriOrPath) { mutableStateOf(VideoPlayMode.PLAY_ALL) }

    var isPrepared by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }

    // Lock & Audio-only mode states
    var isControlsLocked by rememberSaveable { mutableStateOf(false) }
    var showUnlockButton by remember { mutableStateOf(false) }
    var isAudioOnlyMode by rememberSaveable(file.uriOrPath) { mutableStateOf(false) }

    // Aspect ratio modes: Fit, Zoom, Stretch
    val resizeModes = remember {
        listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit to Screen",
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Crop to Fill",
            AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch"
        )
    }
    var currentResizeModeIndex by rememberSaveable { mutableIntStateOf(0) }
    var aspectToastMessage by remember { mutableStateOf<String?>(null) }

    // Gesture HUD overlay states
    var activeGestureHud by remember { mutableStateOf<GestureHudType?>(null) }
    var hudValue by remember { mutableFloatStateOf(0f) }
    var seekDeltaMs by remember { mutableLongStateOf(0L) }
    var seekTargetMs by remember { mutableLongStateOf(0L) }
    var doubleTapFeedback by remember { mutableStateOf<DoubleTapFeedbackType?>(null) }

    // Sheets & Dialogs
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var showHwInfoDialog by remember { mutableStateOf(false) }

    // Decoder Mode: HW, HW+, SW
    var decoderMode by rememberSaveable(file.uriOrPath) { mutableStateOf(DecoderMode.HW) }

    val exoPlayer = remember(file.uriOrPath, decoderMode) {
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
            when (decoderMode) {
                DecoderMode.HW -> {
                    setMediaCodecSelector(MediaCodecSelector.DEFAULT)
                }
                DecoderMode.HW_PLUS -> {
                    setMediaCodecSelector(MediaCodecSelector.DEFAULT)
                    setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                }
                DecoderMode.SW -> {
                    setMediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
                        val decoders = MediaCodecUtil.getDecoderInfos(mimeType, requiresSecure, requiresTunneling)
                        decoders.sortedByDescending { it.softwareOnly || it.name.startsWith("c2.android") || it.name.startsWith("OMX.google") }
                    }
                }
            }
        }

        ExoPlayer.Builder(context, renderersFactory).build().apply {
            val mediaItem = MediaItem.fromUri(resolveMediaUri(file))
            setMediaItem(mediaItem)
            if (currentPositionMs > 0L) {
                seekTo(currentPositionMs)
            }
            if (playbackSpeed != 1.0f) {
                setPlaybackSpeed(playbackSpeed)
            }
            repeatMode = if (playMode == VideoPlayMode.REPEAT_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = wasPlaying
        }
    }

    // Subtitle document picker
    val subtitleFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Picked Subtitle"
            SubtitleManager.attachSubtitleToPlayer(
                exoPlayer = exoPlayer,
                subUri = uri,
                subName = displayName
            )
        }
    }

    // Attach ExoPlayer Listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        isPrepared = true
                        val d = exoPlayer.duration
                        if (d > 0L) durationMs = d
                        bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                        if (currentPositionMs > 0L && Math.abs(exoPlayer.currentPosition - currentPositionMs) > 1000L) {
                            exoPlayer.seekTo(currentPositionMs)
                        }
                    }
                    Player.STATE_ENDED -> {
                        when (playMode) {
                            VideoPlayMode.REPEAT_ONE -> {
                                exoPlayer.seekTo(0L)
                                exoPlayer.play()
                            }
                            VideoPlayMode.PLAY_ALL -> {
                                if (hasNext && onNext != null) {
                                    onNext()
                                } else {
                                    isPlaying = false
                                    wasPlaying = false
                                    currentPositionMs = durationMs
                                    showControls = true
                                }
                            }
                            VideoPlayMode.REPEAT_OFF -> {
                                isPlaying = false
                                wasPlaying = false
                                currentPositionMs = durationMs
                                showControls = true
                            }
                        }
                    }
                    Player.STATE_BUFFERING -> {}
                    Player.STATE_IDLE -> {}
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                wasPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = error.localizedMessage ?: "ExoPlayer video error (${error.errorCode})"
                isPlaying = false
                wasPlaying = false
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            wasPlaying = exoPlayer.isPlaying
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Completely Fullscreen Immersive Mode (No status bar)
    DisposableEffect(activity, showControls) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (!showControls) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.statusBars())
            }
        }
        onDispose {
            val window = activity?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Auto-hide controls after 4 seconds of inactivity
    LaunchedEffect(showControls, isPlaying, isUserSeeking, isControlsLocked) {
        if (showControls && isPlaying && !isUserSeeking && !isControlsLocked) {
            delay(4000)
            showControls = false
        }
    }

    // Polling progress updater (every 250ms)
    LaunchedEffect(isPlaying, isPrepared, isUserSeeking) {
        while (isActive && isPlaying && !isUserSeeking) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            currentPositionMs = pos
            bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration
            if (dur > 0L) durationMs = dur
            delay(250)
        }
    }

    // Aspect ratio toast auto-dismiss
    LaunchedEffect(aspectToastMessage) {
        if (aspectToastMessage != null) {
            delay(1800)
            aspectToastMessage = null
        }
    }

    // Double tap feedback auto-dismiss
    LaunchedEffect(doubleTapFeedback) {
        if (doubleTapFeedback != null) {
            delay(800)
            doubleTapFeedback = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("video_player_preview")
    ) {
        if (errorMessage != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = VideoCoral,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage ?: "Unable to play video",
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                if (onOpenExternal != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onOpenExternal) {
                        Text("Open in External Player")
                    }
                }
            }
            return@Box
        }

        // 1. Native Media3 PlayerView Container (Hidden in Audio-Only Mode)
        if (!isAudioOnlyMode) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = false
                        resizeMode = resizeModes[currentResizeModeIndex].first
                        player = exoPlayer
                    }
                },
                update = { view ->
                    if (view.player != exoPlayer) {
                        view.player = exoPlayer
                    }
                    view.resizeMode = resizeModes[currentResizeModeIndex].first
                }
            )
        } else {
            // Audio-Only Mode Surface
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF1E102F),
                                Color(0xFF0F0817),
                                Color.Black
                            )
                        )
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = VideoCoral.copy(alpha = 0.15f),
                    modifier = Modifier.size(130.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Headphones,
                            contentDescription = "Audio Mode",
                            tint = VideoCoral,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Playing as Audio • ${file.formattedSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (isPlaying) {
                    AudioWaveformIndicator()
                }
            }
        }

        // Loading spinner before video prepares
        if (!isPrepared && errorMessage == null) {
            CircularProgressIndicator(
                color = VideoCoral,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 2. Gesture Detector Layer (Swipe Brightness / Volume / Seek & Tap / Double-tap)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isControlsLocked) {
                    if (isControlsLocked) {
                        detectTapGestures(
                            onTap = {
                                showUnlockButton = true
                                coroutineScope.launch {
                                    delay(2800)
                                    showUnlockButton = false
                                }
                            }
                        )
                    } else {
                        var dragDirection = 0 // 0: none, 1: horizontal (seek), 2: vertical (brightness/volume)
                        var touchStartX = 0f
                        var touchStartY = 0f
                        var initialBrightness = 0.5f
                        var initialVolume = 0
                        var initialPosition = 0L

                        detectDragGestures(
                            onDragStart = { offset ->
                                dragDirection = 0
                                touchStartX = offset.x
                                touchStartY = offset.y
                                val winAttrs = activity?.window?.attributes
                                initialBrightness = winAttrs?.screenBrightness?.takeIf { it in 0.01f..1.0f } ?: 0.5f
                                initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                initialPosition = exoPlayer.currentPosition
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val totalDx = change.position.x - touchStartX
                                val totalDy = change.position.y - touchStartY

                                if (dragDirection == 0) {
                                    if (abs(totalDx) > 25f || abs(totalDy) > 25f) {
                                        dragDirection = if (abs(totalDx) > abs(totalDy)) 1 else 2
                                    }
                                }

                                if (dragDirection == 1) {
                                    // Horizontal Seek Gesture
                                    val screenWidth = size.width.toFloat().coerceAtLeast(1f)
                                    val maxScrub = (durationMs.coerceAtLeast(60_000L) / 2).coerceIn(30_000L, 240_000L)
                                    val seekDelta = ((totalDx / screenWidth) * maxScrub).toLong()
                                    val target = (initialPosition + seekDelta).coerceIn(0L, durationMs.coerceAtLeast(0L))
                                    seekDeltaMs = seekDelta
                                    seekTargetMs = target
                                    activeGestureHud = GestureHudType.SEEK
                                } else if (dragDirection == 2) {
                                    // Vertical Drag: Left half = Brightness, Right half = Volume
                                    val screenHeight = size.height.toFloat().coerceAtLeast(1f)
                                    val deltaFraction = -totalDy / screenHeight // upward drag increases
                                    if (touchStartX < size.width / 2) {
                                        // Brightness
                                        val newB = (initialBrightness + deltaFraction).coerceIn(0.01f, 1f)
                                        activity?.window?.attributes = activity?.window?.attributes?.apply {
                                            screenBrightness = newB
                                        }
                                        hudValue = newB
                                        activeGestureHud = GestureHudType.BRIGHTNESS
                                    } else {
                                        // Volume (with HW+ / SW 200% volume boost)
                                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        val isBoostSupported = decoderMode != DecoderMode.HW
                                        val totalScale = if (isBoostSupported) 2.0f else 1.0f
                                        val currentFraction = (initialVolume.toFloat() / maxVol.toFloat()) + (deltaFraction * totalScale)
                                        val clampedFraction = currentFraction.coerceIn(0f, totalScale)
                                        if (clampedFraction <= 1.0f) {
                                            val targetSysVol = (clampedFraction * maxVol).toInt().coerceIn(0, maxVol)
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetSysVol, 0)
                                            exoPlayer.volume = 1.0f
                                            hudValue = clampedFraction
                                        } else {
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)
                                            exoPlayer.volume = clampedFraction
                                            hudValue = clampedFraction
                                        }
                                        activeGestureHud = GestureHudType.VOLUME
                                    }
                                }
                            },
                            onDragEnd = {
                                if (dragDirection == 1 && activeGestureHud == GestureHudType.SEEK) {
                                    exoPlayer.seekTo(seekTargetMs)
                                    currentPositionMs = seekTargetMs
                                }
                                activeGestureHud = null
                            },
                            onDragCancel = {
                                activeGestureHud = null
                            }
                        )
                    }
                }
                .pointerInput(isControlsLocked) {
                    if (!isControlsLocked) {
                        detectTapGestures(
                            onTap = {
                                showControls = !showControls
                            },
                            onDoubleTap = { offset ->
                                if (offset.x < size.width / 2) {
                                    val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                    exoPlayer.seekTo(target)
                                    currentPositionMs = target
                                    doubleTapFeedback = DoubleTapFeedbackType.REWIND
                                } else {
                                    val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(durationMs)
                                    exoPlayer.seekTo(target)
                                    currentPositionMs = target
                                    doubleTapFeedback = DoubleTapFeedbackType.FORWARD
                                }
                            }
                        )
                    }
                }
        )

        // 3. Gesture HUD Overlays (Volume, Brightness, Seek, Double-Tap)
        activeGestureHud?.let { hud ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(
                        when (hud) {
                            GestureHudType.BRIGHTNESS -> Alignment.CenterStart
                            GestureHudType.VOLUME -> Alignment.CenterEnd
                            GestureHudType.SEEK -> Alignment.Center
                        }
                    )
                    .padding(32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (hud) {
                        GestureHudType.BRIGHTNESS -> {
                            Icon(Icons.Filled.BrightnessMedium, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("${(hudValue * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { hudValue },
                                modifier = Modifier.width(100.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFFFFD54F),
                                trackColor = Color.White.copy(alpha = 0.2f)
                            )
                        }
                        GestureHudType.VOLUME -> {
                            val isBoosted = hudValue > 1.0f
                            val volColor = if (isBoosted) VideoCoral else Color(0xFF4FC3F7)
                            Icon(
                                if (hudValue <= 0.02f) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = volColor,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val volPercent = (hudValue * 100).toInt()
                            Text(
                                if (isBoosted) "$volPercent% (Boost)" else "$volPercent%",
                                color = if (isBoosted) VideoCoral else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val progressFrac = if (decoderMode != DecoderMode.HW) (hudValue / 2.0f).coerceIn(0f, 1f) else hudValue.coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { progressFrac },
                                modifier = Modifier.width(100.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = volColor,
                                trackColor = Color.White.copy(alpha = 0.2f)
                            )
                        }
                        GestureHudType.SEEK -> {
                            val sign = if (seekDeltaMs >= 0) "+${seekDeltaMs / 1000}s" else "${seekDeltaMs / 1000}s"
                            Text("[$sign]", color = VideoCoral, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${formatMediaDuration(seekTargetMs)} / ${formatMediaDuration(durationMs)}", color = Color.White, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(8.dp))
                            val prog = if (durationMs > 0L) (seekTargetMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                            LinearProgressIndicator(
                                progress = { prog },
                                modifier = Modifier.width(160.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = VideoCoral,
                                trackColor = Color.White.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }
        }

        // Double-Tap Skip Feedback Ripple
        doubleTapFeedback?.let { fb ->
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(if (fb == DoubleTapFeedbackType.REWIND) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(48.dp)
                    .size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (fb == DoubleTapFeedbackType.REWIND) Icons.Filled.Replay10 else Icons.Filled.Forward10,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(if (fb == DoubleTapFeedbackType.REWIND) "-10s" else "+10s", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Aspect Ratio Toast Banner
        aspectToastMessage?.let { msg ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 70.dp)
            ) {
                Text(
                    text = msg,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // 4. Floating Unlock Button (When Locked)
        AnimatedVisibility(
            visible = isControlsLocked && showUnlockButton,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp)
        ) {
            FilledIconButton(
                onClick = {
                    isControlsLocked = false
                    showUnlockButton = false
                    showControls = true
                },
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = VideoCoral,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Filled.LockOpen, contentDescription = "Unlock Controls", modifier = Modifier.size(28.dp))
            }
        }

        // 5. Full On-Screen Controls Overlay
        AnimatedVisibility(
            visible = (!isControlsLocked) && (showControls || !isPlaying),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Top Overlay Bar: Back, Title, HW badge, Queue, Audio track, Subtitle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .displayCutoutPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onDismiss?.invoke() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${file.extension.uppercase()} • ${file.formattedSize}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.LightGray
                            )
                        }
                    }

                    // Top Action Icons matching Pic 1: HW Badge, Queue, Audio Track, Subtitle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // HW / HW+ / SW Decoder Mode Badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when (decoderMode) {
                                DecoderMode.HW -> Color.White.copy(alpha = 0.2f)
                                DecoderMode.HW_PLUS -> VideoCoral.copy(alpha = 0.85f)
                                DecoderMode.SW -> Color(0xFF7E57C2)
                            },
                            modifier = Modifier
                                .clickable { showHwInfoDialog = true }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = decoderMode.code,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }

                        // Queue / Playlist Icon
                        IconButton(onClick = { showPlaylistSheet = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                contentDescription = "Playlist Queue",
                                tint = Color.White
                            )
                        }

                        // Audio Track Selector
                        IconButton(onClick = { showAudioTrackDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.Audiotrack,
                                contentDescription = "Audio Tracks",
                                tint = Color.White
                            )
                        }

                        // Subtitles Selector
                        IconButton(onClick = { showSubtitleSheet = true }) {
                            Icon(
                                imageVector = Icons.Filled.Subtitles,
                                contentDescription = "Subtitles",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Center Play / Pause & Prev / Next Controls (matching Pic 1)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Skip Previous Video (|<<)
                    FilledTonalIconButton(
                        onClick = {
                            if (currentPositionMs > 3000L || !hasPrevious || onPrevious == null) {
                                exoPlayer.seekTo(0L)
                                currentPositionMs = 0L
                                exoPlayer.play()
                            } else {
                                onPrevious()
                            }
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous Video",
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Main Center Play / Pause
                    FilledIconButton(
                        onClick = {
                            if (isPlaying) {
                                exoPlayer.pause()
                            } else {
                                if (currentPositionMs >= durationMs && durationMs > 0L) {
                                    exoPlayer.seekTo(0L)
                                    currentPositionMs = 0L
                                }
                                exoPlayer.play()
                            }
                        },
                        modifier = Modifier
                            .size(76.dp)
                            .testTag("video_play_pause_button"),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = VideoCoral,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(46.dp)
                        )
                    }

                    // Skip Next Video (>>|)
                    FilledTonalIconButton(
                        onClick = {
                            if (hasNext && onNext != null) {
                                onNext()
                            } else {
                                exoPlayer.seekTo(0L)
                                currentPositionMs = 0L
                                exoPlayer.play()
                            }
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next Video",
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Bottom Controls: Time & Speed row, Progressive Seek Bar, Bottom Actions row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .displayCutoutPadding()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    // Time Label (Left) and Speed / Rotate Buttons (Right)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatMediaDuration(currentPositionMs)} / ${formatMediaDuration(durationMs)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Speed Chip Dropdown
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.clickable { showSpeedMenu = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "${playbackSpeed}x", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }

                                DropdownMenu(
                                    expanded = showSpeedMenu,
                                    onDismissRequest = { showSpeedMenu = false }
                                ) {
                                    listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                        DropdownMenuItem(
                                            text = { Text("${speed}x") },
                                            onClick = {
                                                playbackSpeed = speed
                                                exoPlayer.setPlaybackSpeed(speed)
                                                showSpeedMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Rotate Screen Button (Portrait <-> Sensor Landscape)
                            IconButton(
                                onClick = {
                                    val isLand = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                                    activity?.requestedOrientation = if (isLand) {
                                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    } else {
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ScreenRotation,
                                    contentDescription = "Rotate Screen",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Progressive Seek Bar (Progress + Buffer Visualization)
                    val progressFraction = if (durationMs > 0L) {
                        if (isUserSeeking) seekFraction else (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Buffered progress bar in background
                        val bufferFraction = if (durationMs > 0L) (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                        LinearProgressIndicator(
                            progress = { bufferFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color.White.copy(alpha = 0.45f),
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )

                        // Interactive Foreground Slider
                        Slider(
                            value = progressFraction,
                            onValueChange = { frac ->
                                isUserSeeking = true
                                seekFraction = frac
                                currentPositionMs = (frac * durationMs).toLong()
                            },
                            onValueChangeFinished = {
                                val targetMs = (seekFraction * durationMs).toLong()
                                exoPlayer.seekTo(targetMs)
                                currentPositionMs = targetMs
                                isUserSeeking = false
                            },
                            enabled = isPrepared && durationMs > 0L,
                            colors = SliderDefaults.colors(
                                thumbColor = VideoCoral,
                                activeTrackColor = VideoCoral,
                                inactiveTrackColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("video_seek_slider")
                        )
                    }

                    // Bottom Row Controls (matching Pic 1: Lock, Aspect Ratio, PiP, Headphones, Repeat/Play All, External)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Lock Button
                        IconButton(
                            onClick = {
                                isControlsLocked = true
                                showControls = false
                                showUnlockButton = true
                                coroutineScope.launch {
                                    delay(2500)
                                    showUnlockButton = false
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Lock Controls",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // 2. Aspect Ratio Button (Fit, Crop/Zoom, Stretch)
                        IconButton(
                            onClick = {
                                currentResizeModeIndex = (currentResizeModeIndex + 1) % resizeModes.size
                                aspectToastMessage = resizeModes[currentResizeModeIndex].second
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AspectRatio,
                                contentDescription = "Aspect Ratio",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // 3. Picture-in-Picture (PiP)
                        IconButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                                    runCatching {
                                        val pipBuilder = PictureInPictureParams.Builder()
                                        activity.enterPictureInPictureMode(pipBuilder.build())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PictureInPictureAlt,
                                contentDescription = "Picture in Picture",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // 4. Headphones (Play as Audio)
                        IconButton(
                            onClick = {
                                isAudioOnlyMode = !isAudioOnlyMode
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Headphones,
                                contentDescription = "Play as Audio",
                                tint = if (isAudioOnlyMode) VideoCoral else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // 5. Repeat / Play All Mode
                        IconButton(
                            onClick = {
                                playMode = when (playMode) {
                                    VideoPlayMode.PLAY_ALL -> VideoPlayMode.REPEAT_ONE
                                    VideoPlayMode.REPEAT_ONE -> VideoPlayMode.REPEAT_OFF
                                    VideoPlayMode.REPEAT_OFF -> VideoPlayMode.PLAY_ALL
                                }
                                exoPlayer.repeatMode = if (playMode == VideoPlayMode.REPEAT_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                                aspectToastMessage = when (playMode) {
                                    VideoPlayMode.PLAY_ALL -> "Play All"
                                    VideoPlayMode.REPEAT_ONE -> "Repeat One"
                                    VideoPlayMode.REPEAT_OFF -> "Repeat Off"
                                }
                            }
                        ) {
                            Icon(
                                imageVector = when (playMode) {
                                    VideoPlayMode.PLAY_ALL -> Icons.AutoMirrored.Filled.PlaylistPlay
                                    VideoPlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                                    VideoPlayMode.REPEAT_OFF -> Icons.Filled.Repeat
                                },
                                contentDescription = "Play Mode",
                                tint = if (playMode != VideoPlayMode.REPEAT_OFF) VideoCoral else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // 6. Open with External Player
                        if (onOpenExternal != null) {
                            IconButton(onClick = onOpenExternal) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open in External Player",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Subtitles Modal Bottom Sheet ---
    if (showSubtitleSheet) {
        VideoSubtitleSheet(
            file = file,
            exoPlayer = exoPlayer,
            onPickSubtitleFile = {
                subtitleFilePicker.launch(arrayOf("*/*"))
            },
            onDismiss = { showSubtitleSheet = false }
        )
    }

    // --- Playlist / Queue Modal Bottom Sheet ---
    if (showPlaylistSheet) {
        VideoPlaylistSheet(
            playlist = if (playlist.isNotEmpty()) playlist else listOf(file),
            currentFile = file,
            playMode = playMode,
            onTogglePlayMode = { newMode ->
                playMode = newMode
                exoPlayer.repeatMode = if (newMode == VideoPlayMode.REPEAT_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            },
            onSelect = { selected ->
                showPlaylistSheet = false
                onSelectMedia?.invoke(selected)
            },
            onDismiss = { showPlaylistSheet = false }
        )
    }

    // --- Audio Track Selector Dialog ---
    if (showAudioTrackDialog) {
        AudioTrackSelectorDialog(
            exoPlayer = exoPlayer,
            onDismiss = { showAudioTrackDialog = false }
        )
    }

    // --- Decoder Selector Dialog (HW, HW+, SW) ---
    if (showHwInfoDialog) {
        VideoDecoderSelectorDialog(
            currentMode = decoderMode,
            onSelectMode = { newMode ->
                if (newMode != decoderMode) {
                    currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                    wasPlaying = exoPlayer.isPlaying
                    decoderMode = newMode
                    aspectToastMessage = "Decoder: ${newMode.code}"
                }
            },
            exoPlayer = exoPlayer,
            file = file,
            onDismiss = { showHwInfoDialog = false }
        )
    }
}

/**
 * Subtitle Bottom Sheet with Embedded Tracks, Local Subtitles (.srt/.vtt/.ass), and Online Search & Download.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoSubtitleSheet(
    file: FileReference,
    exoPlayer: ExoPlayer,
    onPickSubtitleFile: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember {
        mutableStateOf(
            file.displayName.substringBeforeLast('.', file.displayName)
                .replace(Regex("""(?i)\b(1080p|720p|480p|2160p|4k|bluray|web-dl|webrip|brrip|x264|x265|hevc|h264|aac|ac3|dts|hdrip|yify|yts)\b"""), "")
                .replace(Regex("""[._\-\[\]()]"""), " ")
                .trim()
        )
    }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<OnlineSubtitleItem>>(emptyList()) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var downloadingUrl by remember { mutableStateOf<String?>(null) }

    val localSubtitles = remember(file.uriOrPath) {
        SubtitleManager.findMatchingLocalSubtitles(file)
    }

    // Query embedded subtitle tracks from ExoPlayer
    val embeddedTracks = remember(exoPlayer.currentTracks) {
        val tracksList = mutableListOf<Pair<String, () -> Unit>>()
        val trackGroups = exoPlayer.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        for (group in trackGroups) {
            val mg = group.mediaTrackGroup
            for (i in 0 until mg.length) {
                val format = mg.getFormat(i)
                val label = format.label?.takeIf { it.isNotBlank() }
                    ?: (format.language?.takeIf { it.isNotBlank() }?.let { "Track ${i + 1} ($it)" } ?: "Track ${i + 1}")
                tracksList.add(label to {
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(mg, i))
                        .build()
                })
            }
        }
        tracksList
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1E1E24)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Subtitles",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = VideoCoral
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Local & Embedded", color = if (selectedTab == 0) VideoCoral else Color.LightGray) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Online Search", color = if (selectedTab == 1) VideoCoral else Color.LightGray) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == 0) {
                // Embedded & Local Subtitles
                LazyColumn(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                    item {
                        Text(
                            text = "Actions",
                            style = MaterialTheme.typography.labelMedium,
                            color = VideoCoral,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                        .buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                        .build()
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Disable Subtitles (Off)", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onPickSubtitleFile()
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = VideoCoral, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Pick Subtitle from Storage (.srt, .vtt, .ass)", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    if (embeddedTracks.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Embedded Subtitle Tracks (${embeddedTracks.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = VideoCoral,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(embeddedTracks) { (label, onSelect) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelect()
                                        onDismiss()
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Subtitles, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    if (localSubtitles.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Auto-Detected in Folder (${localSubtitles.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = VideoCoral,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(localSubtitles) { subFile ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        SubtitleManager.attachSubtitleToPlayer(
                                            exoPlayer = exoPlayer,
                                            subUri = Uri.fromFile(subFile),
                                            subName = subFile.name
                                        )
                                        onDismiss()
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.FileDownload, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(subFile.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            } else {
                // Online Subtitle Search Tab
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Movie / Video Name", color = Color.LightGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = VideoCoral,
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (searchQuery.isNotBlank()) {
                                isSearching = true
                                searchMessage = null
                                coroutineScope.launch {
                                    val results = SubtitleManager.searchOnlineSubtitles(searchQuery)
                                    searchResults = results
                                    isSearching = false
                                    if (results.isEmpty()) {
                                        searchMessage = "No subtitles found. Try a different title."
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Search", tint = VideoCoral)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isSearching) {
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = VideoCoral)
                    }
                } else if (searchMessage != null) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text(searchMessage!!, color = Color.LightGray, textAlign = TextAlign.Center)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                        items(searchResults) { sub ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(sub.title, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${sub.language} • ${sub.format.uppercase()}", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                                }

                                if (downloadingUrl == sub.downloadUrl) {
                                    CircularProgressIndicator(color = VideoCoral, modifier = Modifier.size(24.dp))
                                } else {
                                    FilledTonalIconButton(
                                        onClick = {
                                            downloadingUrl = sub.downloadUrl
                                            coroutineScope.launch {
                                                val downloaded = SubtitleManager.downloadSubtitle(context, sub.downloadUrl, sub.title)
                                                downloadingUrl = null
                                                if (downloaded != null) {
                                                    SubtitleManager.attachSubtitleToPlayer(
                                                        exoPlayer = exoPlayer,
                                                        subUri = Uri.fromFile(downloaded),
                                                        subLanguage = sub.language,
                                                        subName = sub.title
                                                    )
                                                    onDismiss()
                                                }
                                            }
                                        },
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = VideoCoral.copy(alpha = 0.2f),
                                            contentColor = VideoCoral
                                        ),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Filled.CloudDownload, contentDescription = "Download Subtitle", modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Queue / Playlist Bottom Sheet with "Play All" switch and selectable video items.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoPlaylistSheet(
    playlist: List<FileReference>,
    currentFile: FileReference,
    playMode: VideoPlayMode,
    onTogglePlayMode: (VideoPlayMode) -> Unit,
    onSelect: (FileReference) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1E1E24)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Playlist Queue",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${playlist.size} videos in folder",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Play All", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = playMode == VideoPlayMode.PLAY_ALL,
                        onCheckedChange = { checked ->
                            onTogglePlayMode(if (checked) VideoPlayMode.PLAY_ALL else VideoPlayMode.REPEAT_OFF)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = VideoCoral,
                            checkedTrackColor = VideoCoral.copy(alpha = 0.4f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            ) {
                itemsIndexed(playlist) { idx, item ->
                    val isCurrent = item.uriOrPath == currentFile.uriOrPath
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCurrent) VideoCoral.copy(alpha = 0.18f) else Color.Transparent,
                        border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.dp, VideoCoral) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item) }
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isCurrent) VideoCoral else Color.White.copy(alpha = 0.1f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isCurrent) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    } else {
                                        Text("${idx + 1}", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.displayName,
                                    color = if (isCurrent) VideoCoral else Color.White,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.extension.uppercase()} • ${item.formattedSize}",
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Audio Track Switcher Dialog for multi-stream audio videos.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun AudioTrackSelectorDialog(
    exoPlayer: ExoPlayer,
    onDismiss: () -> Unit
) {
    val audioTracks = remember(exoPlayer.currentTracks) {
        val tracksList = mutableListOf<Pair<String, () -> Unit>>()
        val audioGroups = exoPlayer.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        for (group in audioGroups) {
            val mg = group.mediaTrackGroup
            for (i in 0 until mg.length) {
                val format = mg.getFormat(i)
                val label = format.label?.takeIf { it.isNotBlank() }
                    ?: (format.language?.takeIf { it.isNotBlank() }?.let { "Audio ($it)" } ?: "Audio Track ${i + 1}")
                val details = listOfNotNull(
                    format.sampleMimeType?.substringAfter('/'),
                    if (format.channelCount > 0) "${format.channelCount}ch" else null
                ).joinToString(" • ")
                val fullText = if (details.isNotBlank()) "$label ($details)" else label

                tracksList.add(fullText to {
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(TrackSelectionOverride(mg, i))
                        .build()
                })
            }
        }
        tracksList
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Audio Tracks", fontWeight = FontWeight.Bold, color = Color.White)
        },
        text = {
            if (audioTracks.isEmpty()) {
                Text("Default audio stream active.", color = Color.LightGray)
            } else {
                Column {
                    audioTracks.forEach { (label, onSelect) ->
                        TextButton(
                            onClick = {
                                onSelect()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Audiotrack, contentDescription = null, tint = VideoCoral, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = VideoCoral)
            }
        },
        containerColor = Color(0xFF22222A)
    )
}

/**
 * Decoder Selection & Hardware Acceleration Information Dialog (HW, HW+, SW).
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoDecoderSelectorDialog(
    currentMode: DecoderMode,
    onSelectMode: (DecoderMode) -> Unit,
    exoPlayer: ExoPlayer,
    file: FileReference,
    onDismiss: () -> Unit
) {
    val videoFormat = exoPlayer.videoFormat
    val resText = if (videoFormat != null && videoFormat.width > 0 && videoFormat.height > 0) {
        "${videoFormat.width} x ${videoFormat.height}"
    } else "Resolution: Native"
    val fpsText = if (videoFormat != null && videoFormat.frameRate > 0f) {
        "${videoFormat.frameRate.toInt()} fps"
    } else "Auto"
    val codecText = videoFormat?.sampleMimeType ?: "MediaCodec / Hardware Accelerated"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when (currentMode) {
                        DecoderMode.HW -> VideoCoral
                        DecoderMode.HW_PLUS -> Color(0xFFFFB300)
                        DecoderMode.SW -> Color(0xFF7E57C2)
                    },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        currentMode.code,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text("Select Decoder", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DecoderMode.entries.forEach { mode ->
                    val isSelected = mode == currentMode
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) VideoCoral.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, VideoCoral) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectMode(mode)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = when (mode) {
                                    DecoderMode.HW -> VideoCoral
                                    DecoderMode.HW_PLUS -> Color(0xFFFFB300)
                                    DecoderMode.SW -> Color(0xFF7E57C2)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        mode.code,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    mode.title,
                                    color = if (isSelected) VideoCoral else Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    mode.description,
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = VideoCoral,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                // Technical stream info
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Stream Information",
                        color = VideoCoral,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("Video Codec: $codecText", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    Text("Resolution: $resText", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    Text("Frame Rate: $fpsText", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    Text("Container: ${file.extension.uppercase()} (${file.formattedSize})", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = VideoCoral)
            }
        },
        containerColor = Color(0xFF22222A)
    )
}

