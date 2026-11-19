package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.media.EqualizerBand
import com.example.core.media.EqualizerEngine
import com.example.core.media.EqualizerPreset
import com.example.core.media.EqualizerUiState
import com.example.core.media.LrcLine
import com.example.core.media.LrcParser
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerStudioSheet(
    onDismiss: () -> Unit
) {
    val eqState by EqualizerEngine.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF14161B),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            // Header: Title + Master Switch + Reset
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = if (eqState.enabled) Color(0xFF64B5F6) else Color.Gray,
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text(
                            text = "Studio Equalizer & DSP",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (eqState.enabled) "Active • ${eqState.selectedPreset}" else "Bypassed",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (eqState.enabled) Color(0xFF64B5F6) else Color.Gray
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { EqualizerEngine.resetToFlat() },
                        enabled = eqState.enabled
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset to Flat",
                            tint = if (eqState.enabled) Color(0xFFB0B0B0) else Color(0xFF505050)
                        )
                    }
                    Switch(
                        checked = eqState.enabled,
                        onCheckedChange = { EqualizerEngine.setEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF1976D2),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color(0xFF2C2F38)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Presets Horizontal Scroller
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(EqualizerUiState.BUILTIN_PRESETS) { preset ->
                    val isSelected = eqState.enabled && eqState.selectedPreset == preset.name
                    FilterChip(
                        selected = isSelected,
                        onClick = { EqualizerEngine.applyPreset(preset) },
                        label = { Text(preset.name, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0xFF20232B),
                            labelColor = Color(0xFFC0C0C0),
                            selectedContainerColor = Color(0xFF1976D2),
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) Color(0xFF64B5F6) else Color(0xFF333842)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5-Band Equalizer Sliders
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1F26)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "FREQUENCY BANDS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8E95A5),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        eqState.bands.forEach { band ->
                            VerticalEqBandColumn(
                                band = band,
                                enabled = eqState.enabled,
                                onLevelChanged = { newMb ->
                                    EqualizerEngine.setBandLevel(band.index, newMb)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hardware DSP Knobs: Bass Boost, Virtualizer, Preamp (Loudness Enhancer)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1F26)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "ACOUSTIC ENHANCEMENTS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8E95A5),
                        letterSpacing = 1.sp
                    )

                    // Bass Boost
                    DspEffectSliderRow(
                        icon = Icons.Default.SurroundSound,
                        name = "Bass Boost",
                        value = eqState.bassBoostStrength,
                        max = 1000,
                        unit = "%",
                        valueDisplay = "${eqState.bassBoostStrength / 10}%",
                        enabled = eqState.enabled,
                        onValueChange = { EqualizerEngine.setBassBoost(it) }
                    )

                    // 3D Virtualizer
                    DspEffectSliderRow(
                        icon = Icons.Default.SpatialAudio,
                        name = "3D Virtualizer",
                        value = eqState.virtualizerStrength,
                        max = 1000,
                        unit = "%",
                        valueDisplay = "${eqState.virtualizerStrength / 10}%",
                        enabled = eqState.enabled,
                        onValueChange = { EqualizerEngine.setVirtualizer(it) }
                    )

                    // Preamp / Loudness Enhancer
                    DspEffectSliderRow(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        name = "Preamp Boost",
                        value = eqState.loudnessGainMb,
                        max = 1500,
                        unit = "dB",
                        valueDisplay = "+${String.format(java.util.Locale.US, "%.1f", eqState.loudnessGainMb / 100f)} dB",
                        enabled = eqState.enabled,
                        onValueChange = { EqualizerEngine.setLoudnessGain(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun VerticalEqBandColumn(
    band: EqualizerBand,
    enabled: Boolean,
    onLevelChanged: (Int) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxHeight()
    ) {
        // Gain display in dB
        val gainText = if (band.levelDb > 0) "+${String.format(java.util.Locale.US, "%.1f", band.levelDb)}"
        else String.format(java.util.Locale.US, "%.1f", band.levelDb)
        Text(
            text = gainText,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled && band.levelMb != 0) Color(0xFF64B5F6) else Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )

        // Vertical Slider replacement using touch-friendly height layout
        Box(
            modifier = Modifier
                .weight(1f)
                .width(42.dp)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            // Slider range minLevelMb..maxLevelMb
            val currentFraction = ((band.levelMb - band.minLevelMb).toFloat() /
                (band.maxLevelMb - band.minLevelMb).coerceAtLeast(1)).coerceIn(0f, 1f)

            // Custom vertical bar track
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF2E333F))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(currentFraction)
                        .align(Alignment.BottomCenter)
                        .background(if (enabled) Color(0xFF1976D2) else Color.DarkGray)
                )
            }

            // Up / Down adjustment buttons
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF282D37),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(enabled = enabled) {
                            onLevelChanged((band.levelMb + 200).coerceAtMost(band.maxLevelMb))
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("+", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = Color(0xFF282D37),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(enabled = enabled) {
                            onLevelChanged((band.levelMb - 200).coerceAtLeast(band.minLevelMb))
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("-", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Center frequency & Role
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = band.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color.White else Color.Gray,
                fontSize = 11.sp
            )
            Text(
                text = band.roleLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8E95A5),
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun DspEffectSliderRow(
    icon: ImageVector,
    name: String,
    value: Int,
    max: Int,
    unit: String,
    valueDisplay: String,
    enabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = if (enabled && value > 0) Color(0xFF64B5F6) else Color.Gray, modifier = Modifier.size(18.dp))
                Text(name, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
            Text(
                text = valueDisplay,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled && value > 0) Color(0xFF64B5F6) else Color.Gray
            )
        }

        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..max.toFloat(),
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFF1976D2),
                inactiveTrackColor = Color(0xFF2C2F38)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SynchronizedLyricsSheet(
    lyrics: List<LrcLine>,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val currentLineIndex = remember(lyrics, currentPositionMs) {
        LrcParser.findCurrentLineIndex(lyrics, currentPositionMs)
    }

    // Auto-scroll to active lyric line
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0 && currentLineIndex < lyrics.size) {
            val target = (currentLineIndex - 2).coerceAtLeast(0)
            runCatching {
                listState.animateScrollToItem(target)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121418),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = null,
                        tint = Color(0xFF64B5F6),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Synchronized Lyrics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFF262A34))

            if (lyrics.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFF555B6A),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Synchronized Lyrics Found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFB0B0B0)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Place a matching .lrc file in the same folder with the same name as the song.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF707787),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(lyrics) { index, line ->
                        val isCurrent = index == currentLineIndex
                        val textColor by animateColorAsState(
                            targetValue = if (isCurrent) Color.White else Color(0xFF6C7383),
                            label = "lyric_color"
                        )
                        val scale by animateFloatAsState(
                            targetValue = if (isCurrent) 1.08f else 1.0f,
                            label = "lyric_scale"
                        )

                        Text(
                            text = line.text,
                            style = if (isCurrent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Normal,
                            color = textColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeekTo(line.timeMs) }
                                .padding(vertical = 4.dp, horizontal = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ABRepeatBar(
    pointA: Long?,
    pointB: Long?,
    currentPositionMs: Long,
    onSetPointA: () -> Unit,
    onSetPointB: () -> Unit,
    onClearLoop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF1E222B),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333845)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "A-B LOOP:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8E95A5)
            )

            // Point A Button
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (pointA != null) Color(0xFF1976D2) else Color(0xFF2B303C),
                modifier = Modifier.clickable { onSetPointA() }
            ) {
                Text(
                    text = if (pointA != null) "A: ${formatMediaDuration(pointA)}" else "Set A",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Point B Button
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (pointB != null) Color(0xFF1976D2) else Color(0xFF2B303C),
                modifier = Modifier.clickable { onSetPointB() }
            ) {
                Text(
                    text = if (pointB != null) "B: ${formatMediaDuration(pointB)}" else "Set B",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            if (pointA != null || pointB != null) {
                IconButton(
                    onClick = onClearLoop,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear A-B Loop",
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
