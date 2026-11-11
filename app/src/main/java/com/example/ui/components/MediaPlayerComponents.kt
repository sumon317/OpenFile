package com.example.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.model.FileReference
import com.example.core.model.ProviderType
import com.example.ui.theme.AudioPurple
import com.example.ui.theme.VideoCoral
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

data class AudioTrackInfo(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artwork: ImageBitmap? = null
)

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
        if (localBacking != null && localBacking.exists()) {
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
 * Built-in Modern Audio Player with embedded album art, seekable progress bar,
 * waveform animation, and playback controls.
 */
@Composable
fun AudioPlayerPreview(
    file: FileReference,
    onOpenExternal: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var trackInfo by remember(file) { mutableStateOf<AudioTrackInfo?>(null) }
    var isPrepared by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }

    var playerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    // Load track metadata off the main thread
    LaunchedEffect(file) {
        val info = withContext(Dispatchers.IO) {
            extractAudioTrackInfo(context, file)
        }
        trackInfo = info
        if (info.durationMs > 0) {
            durationMs = info.durationMs
        }
    }

    // Initialize MediaPlayer
    LaunchedEffect(file) {
        isPrepared = false
        isPlaying = false
        errorMessage = null
        currentPositionMs = 0L

        withContext(Dispatchers.IO) {
            runCatching {
                val mp = MediaPlayer()
                val localBacking = file.metadata["localBackingPath"]?.let { File(it) }
                    ?: if (file.providerType == ProviderType.LOCAL) File(file.uriOrPath) else null

                if (localBacking != null && localBacking.exists()) {
                    mp.setDataSource(localBacking.absolutePath)
                } else {
                    mp.setDataSource(context, Uri.parse(file.uriOrPath))
                }

                mp.setOnPreparedListener { player ->
                    playerRef = player
                    durationMs = player.duration.toLong().coerceAtLeast(0L)
                    isPrepared = true
                    player.start()
                    isPlaying = true
                }

                mp.setOnCompletionListener {
                    if (!isLooping) {
                        isPlaying = false
                        currentPositionMs = durationMs
                    }
                }

                mp.setOnErrorListener { _, what, extra ->
                    errorMessage = "Audio playback error ($what, $extra)"
                    isPlaying = false
                    true
                }

                mp.prepareAsync()
            }.onFailure { err ->
                errorMessage = err.message ?: "Unable to play audio file"
            }
        }
    }

    // Polling position updater
    LaunchedEffect(isPlaying, isPrepared, isUserSeeking) {
        while (isActive && isPlaying && !isUserSeeking) {
            playerRef?.let { mp ->
                if (runCatching { mp.isPlaying }.getOrDefault(false)) {
                    currentPositionMs = mp.currentPosition.toLong().coerceAtLeast(0L)
                }
            }
            delay(250)
        }
    }

    // Clean up on exit
    DisposableEffect(file) {
        onDispose {
            val mp = playerRef
            playerRef = null
            runCatching {
                if (mp?.isPlaying == true) mp.stop()
                mp?.release()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag("audio_player_preview"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (errorMessage != null) {
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
            return@Column
        }

        // Album Artwork or Animated Musical Icon
        val artwork = trackInfo?.artwork
        Card(
            modifier = Modifier
                .size(240.dp)
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
                            modifier = Modifier.size(96.dp)
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

        Spacer(modifier = Modifier.height(28.dp))

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
                file.extension.uppercase()
            ).joinToString(" • ").ifBlank { file.formattedSize },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

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
                val targetMs = (seekFraction * durationMs).toInt()
                playerRef?.seekTo(targetMs)
                currentPositionMs = targetMs.toLong()
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

        // Time labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatMediaDuration(currentPositionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                    playerRef?.isLooping = newLoop
                },
                enabled = isPrepared
            ) {
                Icon(
                    imageVector = if (isLooping) Icons.Filled.RepeatOn else Icons.Filled.Repeat,
                    contentDescription = "Loop Audio",
                    tint = if (isLooping) AudioPurple else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Rewind 10s
            IconButton(
                onClick = {
                    val newPos = (currentPositionMs - 10000L).coerceAtLeast(0L).toInt()
                    playerRef?.seekTo(newPos)
                    currentPositionMs = newPos.toLong()
                },
                enabled = isPrepared
            ) {
                Icon(
                    imageVector = Icons.Filled.Replay10,
                    contentDescription = "Rewind 10 seconds",
                    modifier = Modifier.size(32.dp)
                )
            }

            // Play / Pause Circle
            FilledIconButton(
                onClick = {
                    val mp = playerRef ?: return@FilledIconButton
                    if (isPlaying) {
                        mp.pause()
                        isPlaying = false
                    } else {
                        if (currentPositionMs >= durationMs && durationMs > 0L) {
                            mp.seekTo(0)
                            currentPositionMs = 0L
                        }
                        mp.start()
                        isPlaying = true
                    }
                },
                modifier = Modifier
                    .size(64.dp)
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
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Forward 10s
            IconButton(
                onClick = {
                    val newPos = (currentPositionMs + 10000L).coerceAtMost(durationMs).toInt()
                    playerRef?.seekTo(newPos)
                    currentPositionMs = newPos.toLong()
                },
                enabled = isPrepared
            ) {
                Icon(
                    imageVector = Icons.Filled.Forward10,
                    contentDescription = "Forward 10 seconds",
                    modifier = Modifier.size(32.dp)
                )
            }

            // Replay from start
            IconButton(
                onClick = {
                    playerRef?.seekTo(0)
                    currentPositionMs = 0L
                    if (!isPlaying) {
                        playerRef?.start()
                        isPlaying = true
                    }
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
 * Built-in Video Player using Android VideoView with custom overlay controls,
 * auto-hiding scrubbers, aspect ratio toggle, and full video playback.
 */
@Composable
fun VideoPlayerPreview(
    file: FileReference,
    onOpenExternal: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isPrepared by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }
    var fitAspectRatio by remember { mutableStateOf(true) }

    // Auto-hide controls after 3.5 seconds of user inactivity when playing
    LaunchedEffect(showControls, isPlaying, isUserSeeking) {
        if (showControls && isPlaying && !isUserSeeking) {
            delay(3500)
            showControls = false
        }
    }

    // Polling progress updater
    LaunchedEffect(isPlaying, isPrepared, isUserSeeking) {
        while (isActive && isPlaying && !isUserSeeking) {
            videoViewRef?.let { vv ->
                if (runCatching { vv.isPlaying }.getOrDefault(false)) {
                    currentPositionMs = vv.currentPosition.toLong().coerceAtLeast(0L)
                }
            }
            delay(250)
        }
    }

    // Clean up
    DisposableEffect(file) {
        onDispose {
            val vv = videoViewRef
            videoViewRef = null
            runCatching {
                vv?.stopPlayback()
            }
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

        // Native VideoView Container
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )

                    val localBacking = file.metadata["localBackingPath"]?.let { File(it) }
                        ?: if (file.providerType == ProviderType.LOCAL) File(file.uriOrPath) else null

                    if (localBacking != null && localBacking.exists()) {
                        setVideoPath(localBacking.absolutePath)
                    } else {
                        setVideoURI(Uri.parse(file.uriOrPath))
                    }

                    setOnPreparedListener { mp ->
                        videoViewRef = this
                        durationMs = mp.duration.toLong().coerceAtLeast(0L)
                        isPrepared = true
                        mp.isLooping = isLooping
                        start()
                        isPlaying = true
                    }

                    setOnCompletionListener {
                        if (!isLooping) {
                            isPlaying = false
                            currentPositionMs = durationMs
                            showControls = true
                        }
                    }

                    setOnErrorListener { _, what, extra ->
                        errorMessage = "Video format or codec error ($what, $extra)"
                        isPlaying = false
                        true
                    }
                }
            },
            update = { view ->
                videoViewRef = view
            }
        )

        // Loading spinner before video prepares
        if (!isPrepared && errorMessage == null) {
            CircularProgressIndicator(
                color = VideoCoral,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Controls Overlay
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
                // Top Overlay Bar: Title & External Open
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
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

                    if (onOpenExternal != null) {
                        IconButton(onClick = onOpenExternal) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open with external player",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Center Play / Pause & Skip 10s buttons
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 10s
                    FilledTonalIconButton(
                        onClick = {
                            val target = (currentPositionMs - 10000L).coerceAtLeast(0L).toInt()
                            videoViewRef?.seekTo(target)
                            currentPositionMs = target.toLong()
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
                            val vv = videoViewRef ?: return@FilledIconButton
                            if (isPlaying) {
                                vv.pause()
                                isPlaying = false
                            } else {
                                if (currentPositionMs >= durationMs && durationMs > 0L) {
                                    vv.seekTo(0)
                                    currentPositionMs = 0L
                                }
                                vv.start()
                                isPlaying = true
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
                            val target = (currentPositionMs + 10000L).coerceAtMost(durationMs).toInt()
                            videoViewRef?.seekTo(target)
                            currentPositionMs = target.toLong()
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
                            val targetMs = (seekFraction * durationMs).toInt()
                            videoViewRef?.seekTo(targetMs)
                            currentPositionMs = targetMs.toLong()
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

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Repeat / Loop button
                            IconButton(
                                onClick = {
                                    val newLoop = !isLooping
                                    isLooping = newLoop
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
