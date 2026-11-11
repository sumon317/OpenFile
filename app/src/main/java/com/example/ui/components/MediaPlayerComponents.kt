package com.example.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.core.model.FileReference
import com.example.core.model.ProviderType
import com.example.ui.theme.AudioPurple
import com.example.ui.theme.VideoCoral
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

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

/**
 * Built-in Modern Video Player powered by Jetpack Media3 ExoPlayer & PlayerView.
 * Features: High-performance hardware acceleration, MKV/MP4/WebM/TS playback,
 * auto-hiding gesture controls, playback speed selector, and loop mode.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerPreview(
    file: FileReference,
    onOpenExternal: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    hasPrevious: Boolean = false,
    hasNext: Boolean = false,
    onDismiss: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var currentPositionMs by rememberSaveable(file.uriOrPath) { mutableLongStateOf(0L) }
    var durationMs by rememberSaveable(file.uriOrPath) { mutableLongStateOf(0L) }
    var wasPlaying by rememberSaveable(file.uriOrPath) { mutableStateOf(true) }
    var playbackSpeed by rememberSaveable(file.uriOrPath) { mutableFloatStateOf(1.0f) }
    var isLooping by rememberSaveable(file.uriOrPath) { mutableStateOf(false) }

    var isPrepared by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
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
                            showControls = true
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
        }
    }

    // Auto-hide controls after 3.5 seconds of inactivity when playing
    LaunchedEffect(showControls, isPlaying, isUserSeeking) {
        if (showControls && isPlaying && !isUserSeeking) {
            delay(3500)
            showControls = false
        }
    }

    // Polling progress updater
    LaunchedEffect(isPlaying, isPrepared, isUserSeeking) {
        while (isActive && isPlaying && !isUserSeeking) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            currentPositionMs = pos
            val dur = exoPlayer.duration
            if (dur > 0L) durationMs = dur
            delay(250)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                showControls = !showControls
            }
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

        // Native Media3 PlayerView Container
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
                    useController = false // Custom Compose UI controller used
                    player = exoPlayer
                }
            },
            update = { view ->
                if (view.player != exoPlayer) {
                    view.player = exoPlayer
                }
            }
        )

        // Loading spinner before video prepares
        if (!isPrepared && errorMessage == null) {
            CircularProgressIndicator(
                color = VideoCoral,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Custom Compose Controls Overlay
        AnimatedVisibility(
            visible = showControls || !isPlaying,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Top Overlay Bar: Back, Title, Size & External Open
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onDismiss != null) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
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
                                text = "ExoPlayer • ${file.extension.uppercase()} • ${file.formattedSize}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.LightGray
                            )
                        }
                    }

                    if (onOpenExternal != null) {
                        IconButton(onClick = onOpenExternal) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open in External Player",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Center Play / Pause & Prev / Next buttons
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Video Button
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
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous Video",
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Rewind 10s
                    FilledTonalIconButton(
                        onClick = {
                            val target = (currentPositionMs - 10000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Replay10,
                            contentDescription = "Rewind 10s",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Main Big Play/Pause
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
                            .size(72.dp)
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
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    // Forward 10s
                    FilledTonalIconButton(
                        onClick = {
                            val target = (currentPositionMs + 10000L).coerceAtMost(durationMs)
                            exoPlayer.seekTo(target)
                            currentPositionMs = target
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Forward10,
                            contentDescription = "Forward 10s",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Next Video Button
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
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next Video",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Bottom Scrubber & Time Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
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
                            thumbColor = VideoCoral,
                            activeTrackColor = VideoCoral,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("video_seek_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatMediaDuration(currentPositionMs)} / ${formatMediaDuration(durationMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Speed selector dropdown
                            Box {
                                TextButton(
                                    onClick = { showSpeedMenu = true },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "${playbackSpeed}x", style = MaterialTheme.typography.labelSmall, color = Color.White)
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

                            // Repeat / Loop button
                            IconButton(
                                onClick = {
                                    val newLoop = !isLooping
                                    isLooping = newLoop
                                    exoPlayer.repeatMode = if (newLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                                }
                            ) {
                                Icon(
                                    imageVector = if (isLooping) Icons.Filled.RepeatOn else Icons.Filled.Repeat,
                                    contentDescription = "Loop Video",
                                    tint = if (isLooping) VideoCoral else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
