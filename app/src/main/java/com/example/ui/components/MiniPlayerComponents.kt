@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.example.core.media.MediaPlaybackController
import com.example.core.model.FileReference
import com.example.core.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MiniPlayer(
    controller: MediaPlaybackController,
    onExpand: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isPlaying by controller.isPlayingState.collectAsStateWithLifecycle()
    val currentPosition by controller.currentPositionState.collectAsStateWithLifecycle()
    val duration by controller.durationState.collectAsStateWithLifecycle()
    val currentFile by controller.currentFileState.collectAsStateWithLifecycle()
    val playbackError by controller.playbackErrorState.collectAsStateWithLifecycle()

    var artworkBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var showMiniPlayer by remember { mutableStateOf(false) }

    val progress = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    // Show mini player when there's a current file and (playing or was playing)
    LaunchedEffect(currentFile, isPlaying) {
        showMiniPlayer = currentFile != null && (isPlaying || currentPosition > 0)
    }

    // Load artwork
    LaunchedEffect(currentFile) {
        val currentFileVal = currentFile
        if (currentFileVal != null) {
            val bitmap = withContext(Dispatchers.IO) {
                extractArtwork(context, currentFileVal)
            }
            artworkBitmap = bitmap
        } else {
            artworkBitmap = null
        }
    }

    AnimatedVisibility(
        visible = showMiniPlayer,
        enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
        exit = shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeOut(),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clickable(onClick = onExpand)
                .height(72.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Progress bar at top
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Artwork
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        if (artworkBitmap != null) {
                            Image(
                                bitmap = artworkBitmap!!,
                                contentDescription = "Album Art",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Image(
                                painter = painterResource(id = com.example.R.drawable.ic_audio_placeholder),
                                contentDescription = "Audio Placeholder",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }

                    // Title and Artist
                    val currentFileVal = currentFile
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = currentFileVal?.displayName?.substringBeforeLast('.') ?: "Unknown",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = extractArtist(context, currentFileVal!!) ?: "Unknown Artist",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = { controller.previous() }) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = "Previous",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = { controller.playPause() }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        IconButton(onClick = { controller.next() }) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Next",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun extractArtwork(context: Context, file: FileReference): ImageBitmap? {
    return try {
        val retriever = MediaMetadataRetriever()
        val localBacking = file.metadata["localBackingPath"]?.let { java.io.File(it) }
            ?: if (file.providerType == ProviderType.LOCAL) java.io.File(file.uriOrPath) else null
        if (localBacking != null && localBacking.exists()) {
            retriever.setDataSource(localBacking.absolutePath)
        } else {
            retriever.setDataSource(context, Uri.parse(file.uriOrPath))
        }
        val picture = retriever.embeddedPicture
        if (picture != null) {
            BitmapFactory.decodeByteArray(picture, 0, picture.size)?.let { it.asImageBitmap() }
        } else null
    } catch (_: Exception) {
        null
    }
}

private fun extractArtist(context: Context, file: FileReference): String? {
    return try {
        val retriever = MediaMetadataRetriever()
        val localBacking = file.metadata["localBackingPath"]?.let { java.io.File(it) }
            ?: if (file.providerType == ProviderType.LOCAL) java.io.File(file.uriOrPath) else null
        if (localBacking != null && localBacking.exists()) {
            retriever.setDataSource(localBacking.absolutePath)
        } else {
            retriever.setDataSource(context, Uri.parse(file.uriOrPath))
        }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}