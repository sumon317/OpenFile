package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.core.document.PresentationDocumentModel
import com.example.core.document.PresentationParser
import com.example.core.document.PresentationSlideModel
import com.example.core.model.FileReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Desktop-grade PowerPoint & OpenDocument presentation viewer.
 *
 * Implements:
 * - True slide geometry rendering with shapes, theme colors, tables, and images
 * - HorizontalPager swiping with gesture isolation for pinch zoom
 * - Fullscreen immersive slideshow mode
 * - Bottom slide filmstrip with auto-centering
 * - Scrubber slider
 * - Collapsible speaker notes panel
 * - Outline / Text View toggle for accessibility and copying
 * - In-presentation search with slide jump
 */
@Composable
fun PresentationViewer(
    file: FileReference,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipMgr = LocalClipboardManager.current

    // Parse presentation asynchronously
    val presState by produceState<Result<PresentationDocumentModel>?>(initialValue = null, key1 = file.uriOrPath) {
        value = withContext(Dispatchers.IO) {
            val localFile = resolveOrCacheLocalFile(file, context)
            if (localFile != null) {
                try {
                    Result.success(PresentationParser.parse(localFile))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            } else {
                Result.failure(IllegalStateException("Presentation file could not be resolved."))
            }
        }
    }

    if (presState == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
        }
        return
    }

    val presResult = presState!!
    if (presResult.isFailure) {
        val error = presResult.exceptionOrNull()
        val isLegacy = error is UnsupportedOperationException
        val isPassword = error is SecurityException

        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isPassword) "Password Protected" else if (isLegacy) "Legacy Format (.ppt)" else "Could Not Load Presentation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error?.message ?: "Failed to open presentation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    val presentation = presResult.getOrThrow()
    val totalSlides = presentation.slides.size
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { totalSlides })
    val filmstripListState = rememberLazyListState()

    var isFullscreenSlideshow by remember { mutableStateOf(false) }
    var isOutlineView by remember { mutableStateOf(false) }
    var isNotesExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    val localFile = remember(file) { resolveOrCacheLocalFile(file, context) }

    // Synchronize filmstrip when slide changes
    LaunchedEffect(pagerState.currentPage) {
        filmstripListState.animateScrollToItem(pagerState.currentPage)
    }

    // Fullscreen slideshow system insets handling
    val activity = context.findActivity()
    DisposableEffect(isFullscreenSlideshow) {
        if (isFullscreenSlideshow && activity != null) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val windowInsetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (activity != null) {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val windowInsetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Fullscreen Slideshow UI
    if (isFullscreenSlideshow) {
        FullscreenSlideshowMode(
            presentation = presentation,
            currentPage = pagerState.currentPage,
            presentationFile = localFile,
            onPageChange = { target ->
                scope.launch { pagerState.scrollToPage(target.coerceIn(0, totalSlides - 1)) }
            },
            onExit = { isFullscreenSlideshow = false }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("presentation_viewer")
    ) {
        // 1. Top Control Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        enabled = pagerState.currentPage > 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Slide")
                    }

                    Text(
                        text = "${pagerState.currentPage + 1} / $totalSlides",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )

                    IconButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        enabled = pagerState.currentPage < totalSlides - 1
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Slide")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Search toggle
                    IconButton(onClick = { isSearching = !isSearching }) {
                        Icon(Icons.Default.Search, contentDescription = "Search Presentation")
                    }

                    // Outline / Slide view toggle
                    IconButton(onClick = { isOutlineView = !isOutlineView }) {
                        Icon(
                            imageVector = if (isOutlineView) Icons.Default.Slideshow else Icons.Default.Description,
                            contentDescription = if (isOutlineView) "Slide View" else "Outline View"
                        )
                    }

                    // Slideshow Fullscreen Button
                    IconButton(onClick = { isFullscreenSlideshow = true }) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Start Slideshow")
                    }
                }
            }
        }

        // Live In-Presentation Search Bar
        if (isSearching) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search text across all slides...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Search Results List (if query is active)
        if (isSearching && searchQuery.isNotBlank()) {
            val matchingSlides = remember(searchQuery, presentation) {
                presentation.slides.filter { it.plainTextSummary.contains(searchQuery, ignoreCase = true) }
            }
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(matchingSlides) { _, slide ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .clickable {
                                scope.launch {
                                    pagerState.scrollToPage(slide.slideNumber - 1)
                                    isSearching = false
                                }
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Slide ${slide.slideNumber}: ${slide.title ?: "Untitled"}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = slide.plainTextSummary.take(150),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        } else if (isOutlineView) {
            // Accessible Text Outline View
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp)
            ) {
                itemsIndexed(presentation.slides) { _, slide ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Slide ${slide.slideNumber}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(onClick = {
                                    clipMgr.setText(AnnotatedString(slide.plainTextSummary))
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy text", modifier = Modifier.size(18.dp))
                                }
                            }
                            if (!slide.title.isNullOrBlank()) {
                                Text(
                                    text = slide.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            SelectionContainer {
                                Text(
                                    text = slide.plainTextSummary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // 2. Interactive Slide Canvas Pager with Zoom
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = true,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val slide = presentation.slides[pageIndex]

                    var zoomScale by remember { mutableFloatStateOf(1f) }
                    var panOffset by remember { mutableStateOf(Offset.Zero) }

                    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                        zoomScale = (zoomScale * zoomChange).coerceIn(1f, 3f)
                        if (zoomScale > 1f) {
                            panOffset += panChange
                        } else {
                            panOffset = Offset.Zero
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = panOffset.x
                                translationY = panOffset.y
                            }
                            .transformable(state = transformState)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (zoomScale > 1.2f) {
                                            zoomScale = 1f
                                            panOffset = Offset.Zero
                                        } else {
                                            zoomScale = 2f
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        SlideCanvas(
                            slide = slide,
                            slideWidthEmu = presentation.slideWidthEmu,
                            slideHeightEmu = presentation.slideHeightEmu,
                            presentationFile = localFile
                        )
                    }
                }
            }
        }

        // 3. Collapsible Speaker Notes Panel
        val currentSlide = presentation.slides.getOrNull(pagerState.currentPage)
        val speakerNotes = currentSlide?.speakerNotes
        if (!speakerNotes.isNullOrBlank() && !isFullscreenSlideshow) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { isNotesExpanded = !isNotesExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Speaker Notes", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Icon(
                            imageVector = if (isNotesExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = null
                        )
                    }
                    if (isNotesExpanded) {
                        Text(
                            text = speakerNotes,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 4.dp).verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }

        // 4. Scrubber Slider & Filmstrip (Thumbnails)
        if (!isOutlineView && !isSearching) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(vertical = 4.dp)
            ) {
                // Slider
                if (totalSlides > 1) {
                    Slider(
                        value = pagerState.currentPage.toFloat(),
                        onValueChange = { target ->
                            scope.launch { pagerState.scrollToPage(target.toInt()) }
                        },
                        valueRange = 0f..(totalSlides - 1).toFloat(),
                        steps = (totalSlides - 2).coerceAtLeast(0),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(24.dp)
                    )
                }

                // Thumbnail Filmstrip
                LazyRow(
                    state = filmstripListState,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(68.dp)
                ) {
                    itemsIndexed(presentation.slides) { idx, slide ->
                        val isSelected = idx == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .width(88.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(6.dp))
                                .border(
                                    width = if (isSelected) 2.5.dp else 0.5.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    scope.launch { pagerState.animateScrollToPage(idx) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            SlideCanvas(
                                slide = slide,
                                slideWidthEmu = presentation.slideWidthEmu,
                                slideHeightEmu = presentation.slideHeightEmu,
                                presentationFile = localFile
                            )
                            // Slide index pill
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(2.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
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
 * Fullscreen Immersive Slideshow Mode
 */
@Composable
private fun FullscreenSlideshowMode(
    presentation: PresentationDocumentModel,
    currentPage: Int,
    presentationFile: File?,
    onPageChange: (Int) -> Unit,
    onExit: () -> Unit
) {
    var showControls by remember { mutableStateOf(false) }
    val total = presentation.slides.size

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val w = size.width
                        if (offset.x < w * 0.25f) {
                            // Left edge tap: Previous
                            onPageChange(currentPage - 1)
                        } else if (offset.x > w * 0.75f) {
                            // Right edge tap: Next
                            onPageChange(currentPage + 1)
                        } else {
                            // Center tap: Toggle controls
                            showControls = !showControls
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val currentSlide = presentation.slides.getOrNull(currentPage) ?: presentation.slides.first()

        SlideCanvas(
            slide = currentSlide,
            slideWidthEmu = presentation.slideWidthEmu,
            slideHeightEmu = presentation.slideHeightEmu,
            presentationFile = presentationFile,
            modifier = Modifier.fillMaxSize()
        )

        // Overlay Controls
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onExit) {
                        Icon(Icons.Default.Close, contentDescription = "Exit Slideshow", tint = Color.White)
                    }
                    Text(
                        text = "Slide ${currentPage + 1} of $total",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onExit) {
                        Icon(Icons.Default.FullscreenExit, contentDescription = "Exit", tint = Color.White)
                    }
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
