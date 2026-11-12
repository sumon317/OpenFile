package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.document.CellHorizontalAlignment
import com.example.core.document.DocxBlock
import com.example.core.document.DocxDocumentModel
import com.example.core.document.DocxParser
import com.example.core.document.DocxRun
import com.example.core.document.DocxTableCell
import com.example.core.document.DocxTableRow
import com.example.core.model.FileReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Desktop-grade Word Document (.docx) & OpenDocument Text (.odt) viewer.
 *
 * Implements:
 * - Reflowable paper document view with customizable typography scaling (80% to 200%)
 * - Full styling fidelity: Title, Subtitle, Headings (H1-H6), body, bold, italic, underline,
 *   strikethrough, superscript, subscript, highlight, and text colors
 * - Multi-column tables with borders, header shading, cell fills, and horizontal scroll
 * - Embedded inline/anchored images with memory-bounded LRU decoding and full-screen preview
 * - In-document search with match navigation, highlighting, and auto-scroll
 * - Document outline / table-of-contents jump list
 * - Word & reading time statistics
 * - Dark-mode intelligent contrast adaptation
 * - Copy and selection support via SelectionContainer
 */

data class DocxPage(
    val pageIndex: Int,
    val blocks: List<Pair<Int, DocxBlock>> // Pair of original block index to DocxBlock
)

fun paginateDocxBlocks(blocks: List<DocxBlock>): List<DocxPage> {
    if (blocks.isEmpty()) {
        return listOf(DocxPage(0, emptyList()))
    }
    val pages = mutableListOf<DocxPage>()
    var currentBlocks = mutableListOf<Pair<Int, DocxBlock>>()
    var currentWeight = 0
    val maxPageWeight = 10

    for ((index, block) in blocks.withIndex()) {
        if (block is DocxBlock.PageBreak) {
            if (currentBlocks.isNotEmpty()) {
                pages.add(DocxPage(pages.size, currentBlocks.toList()))
                currentBlocks = mutableListOf()
                currentWeight = 0
            }
            continue
        }

        val blockWeight = when (block) {
            is DocxBlock.Paragraph -> {
                if ((block.headingLevel == 1 || block.headingLevel == -1) && currentWeight >= 5) {
                    pages.add(DocxPage(pages.size, currentBlocks.toList()))
                    currentBlocks = mutableListOf()
                    currentWeight = 0
                }
                val charCount = block.fullText.length
                (charCount / 350 + 1).coerceIn(1, 5)
            }
            is DocxBlock.Table -> {
                (block.rows.size * 2).coerceIn(3, 8)
            }
            is DocxBlock.Image -> 4
            else -> 1
        }

        if (currentBlocks.isNotEmpty() && (currentWeight + blockWeight > maxPageWeight)) {
            pages.add(DocxPage(pages.size, currentBlocks.toList()))
            currentBlocks = mutableListOf()
            currentWeight = 0
        }

        currentBlocks.add(Pair(index, block))
        currentWeight += blockWeight
    }

    if (currentBlocks.isNotEmpty()) {
        pages.add(DocxPage(pages.size, currentBlocks.toList()))
    }

    return if (pages.isEmpty()) listOf(DocxPage(0, emptyList())) else pages
}

@Composable
fun DocxViewer(
    file: FileReference,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var localFileRef by remember { mutableStateOf<File?>(null) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var showOutlineDialog by remember { mutableStateOf(false) }
    var fullImagePreview by remember { mutableStateOf<Bitmap?>(null) }

    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatches by remember { mutableStateOf<List<Int>>(emptyList()) } // Block indices
    var currentMatchIndex by remember { mutableIntStateOf(0) }
    var showJumpDialog by remember { mutableStateOf(false) }

    // Load Document
    val docResult by produceState<Result<DocxDocumentModel>?>(initialValue = null, key1 = file.uriOrPath) {
        value = withContext(Dispatchers.IO) {
            val local = resolveOrCacheLocalFile(file, context)
            localFileRef = local
            if (local != null) {
                try {
                    Result.success(DocxParser.parse(local))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            } else {
                Result.failure(IllegalStateException("File could not be opened."))
            }
        }
    }

    if (docResult == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
        }
        return
    }

    val result = docResult!!
    if (result.isFailure) {
        val error = result.exceptionOrNull()
        val isLegacy = error is UnsupportedOperationException
        val isPassword = error is SecurityException

        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isPassword) Icons.Default.Lock else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isPassword) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = when {
                            isPassword -> "Password Protected"
                            isLegacy -> "Legacy Format (.doc) Not Supported"
                            else -> "Could Not Preview Document"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when {
                            isPassword -> "This document is encrypted or password-protected and cannot be previewed."
                            isLegacy -> "Legacy binary Word (.doc) files are not supported by the built-in desktop renderer. Please convert to .docx or .odt, or open in an external office app."
                            else -> error?.message ?: "An unexpected error occurred while parsing the document."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                try {
                                    val uri = Uri.parse(file.uriOrPath)
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/msword")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Open with..."))
                                } catch (_: Exception) {}
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open in External App")
                        }
                    }
                }
            }
        }
        return
    }

    val doc = result.getOrThrow()

    val pages = remember(doc) {
        paginateDocxBlocks(doc.blocks)
    }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pages.size })

    // Update search matches when query or document changes
    LaunchedEffect(searchQuery, doc) {
        if (searchQuery.isBlank()) {
            searchMatches = emptyList()
            currentMatchIndex = 0
        } else {
            val q = searchQuery.trim().lowercase()
            val matches = mutableListOf<Int>()
            doc.blocks.forEachIndexed { index, block ->
                val text = when (block) {
                    is DocxBlock.Paragraph -> block.fullText
                    is DocxBlock.Table -> block.rows.joinToString(" ") { r -> r.cells.joinToString(" ") { it.fullText } }
                    else -> ""
                }
                if (text.lowercase().contains(q)) {
                    matches.add(index)
                }
            }
            searchMatches = matches
            currentMatchIndex = 0
            if (matches.isNotEmpty()) {
                val matchBlockIdx = matches[0]
                val pageIdx = pages.indexOfFirst { p -> p.blocks.any { it.first == matchBlockIdx } }
                if (pageIdx != -1) {
                    pagerState.animateScrollToPage(pageIdx)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("docx_document_preview")
    ) {
        // -----------------------------------------------------------------------------------------
        // 1. Desktop Document Toolbar
        // -----------------------------------------------------------------------------------------
        Surface(
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                        Text(
                            text = file.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val wordCount = remember(doc) {
                            doc.plainTextSummary.split(Regex("\\s+")).count { it.isNotBlank() }
                        }
                        Text(
                            text = "$wordCount words • Page ${pagerState.currentPage + 1} of ${pages.size} • ${(zoomScale * 100).toInt()}% scale",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Zoom Out
                    IconButton(
                        onClick = { zoomScale = (zoomScale - 0.15f).coerceAtLeast(0.70f) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", modifier = Modifier.size(20.dp))
                    }

                    // Zoom In
                    IconButton(
                        onClick = { zoomScale = (zoomScale + 0.15f).coerceAtMost(2.0f) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", modifier = Modifier.size(20.dp))
                    }

                    // Table of Contents / Outline
                    IconButton(
                        onClick = { showOutlineDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Table of Contents", modifier = Modifier.size(20.dp))
                    }

                    // Search Toggle
                    IconButton(
                        onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) {
                                searchQuery = ""
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(20.dp))
                    }

                    // Copy Text
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(doc.plainTextSummary))
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy text", modifier = Modifier.size(20.dp))
                    }

                    // Share
                    IconButton(
                        onClick = {
                            try {
                                val uri = Uri.parse(file.uriOrPath)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Document"))
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(20.dp))
                    }
                }

                // In-document Search Bar
                AnimatedVisibility(
                    visible = isSearchActive,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Find in document...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        )

                        Text(
                            text = if (searchMatches.isEmpty()) {
                                if (searchQuery.isBlank()) "" else "0 of 0"
                            } else {
                                "${currentMatchIndex + 1} of ${searchMatches.size}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        IconButton(
                            onClick = {
                                if (searchMatches.isNotEmpty()) {
                                    currentMatchIndex = if (currentMatchIndex > 0) currentMatchIndex - 1 else searchMatches.size - 1
                                    val matchBlockIdx = searchMatches[currentMatchIndex]
                                    val pageIdx = pages.indexOfFirst { p -> p.blocks.any { it.first == matchBlockIdx } }
                                    if (pageIdx != -1) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pageIdx)
                                        }
                                    }
                                }
                            },
                            enabled = searchMatches.isNotEmpty(),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous")
                        }

                        IconButton(
                            onClick = {
                                if (searchMatches.isNotEmpty()) {
                                    currentMatchIndex = if (currentMatchIndex < searchMatches.size - 1) currentMatchIndex + 1 else 0
                                    val matchBlockIdx = searchMatches[currentMatchIndex]
                                    val pageIdx = pages.indexOfFirst { p -> p.blocks.any { it.first == matchBlockIdx } }
                                    if (pageIdx != -1) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pageIdx)
                                        }
                                    }
                                }
                            },
                            enabled = searchMatches.isNotEmpty(),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                        }
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // -----------------------------------------------------------------------------------------
        // 2. Horizontal Swipeable Document Page Viewport
        // -----------------------------------------------------------------------------------------
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("docx_horizontal_pager")
        ) { pageIndex ->
            val page = pages.getOrNull(pageIndex) ?: return@HorizontalPager
            val pageScrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp),
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 2.dp,
                    shadowElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(pageScrollState)
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            page.blocks.forEach { (origIndex, block) ->
                                val isHighlighted = isSearchActive && searchMatches.getOrNull(currentMatchIndex) == origIndex

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (isHighlighted) {
                                                Modifier
                                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                                    .padding(4.dp)
                                            } else Modifier
                                        )
                                ) {
                                    when (block) {
                                        is DocxBlock.Paragraph -> {
                                            DocxParagraphView(
                                                paragraph = block,
                                                zoomScale = zoomScale,
                                                searchHighlight = if (isSearchActive) searchQuery else null,
                                                context = context
                                            )
                                        }
                                        is DocxBlock.Table -> {
                                            DocxTableView(
                                                table = block,
                                                zoomScale = zoomScale,
                                                searchHighlight = if (isSearchActive) searchQuery else null
                                            )
                                        }
                                        is DocxBlock.Image -> {
                                            DocxImageView(
                                                image = block,
                                                localFile = localFileRef,
                                                onImageClick = { bmp -> fullImagePreview = bmp }
                                            )
                                        }
                                        is DocxBlock.PageBreak -> {
                                            DocxPageBreakView()
                                        }
                                        is DocxBlock.Placeholder -> {
                                            Text(
                                                text = block.label,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                            }

                            // Footnotes on the last page
                            if (pageIndex == pages.size - 1 && doc.footnotes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(24.dp))
                                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Footnotes",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                doc.footnotes.forEachIndexed { fIdx, note ->
                                    Text(
                                        text = "[${fIdx + 1}] $note",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // -----------------------------------------------------------------------------------------
        // 3. Bottom Navigation & Page Scrubber Bar
        // -----------------------------------------------------------------------------------------
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (pagerState.currentPage > 0) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                        },
                        enabled = pagerState.currentPage > 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Page")
                    }

                    val config = LocalConfiguration.current
                    val pageTitleStyle = if (config.screenWidthDp < 360) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium
                    TextButton(onClick = { showJumpDialog = true }) {
                        Text(
                            text = "Page ${pagerState.currentPage + 1} of ${pages.size} (Jump)",
                            style = pageTitleStyle,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = {
                            if (pagerState.currentPage < pages.size - 1) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        enabled = pagerState.currentPage < pages.size - 1
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Page")
                    }
                }

                if (pages.size > 1) {
                    var sliderPos by remember(pagerState.currentPage) { mutableFloatStateOf(pagerState.currentPage.toFloat()) }
                    Slider(
                        value = sliderPos,
                        onValueChange = { sliderPos = it },
                        onValueChangeFinished = {
                            coroutineScope.launch {
                                pagerState.scrollToPage(sliderPos.toInt().coerceIn(0, pages.size - 1))
                            }
                        },
                        valueRange = 0f..(pages.size - 1).toFloat(),
                        steps = (pages.size - 2).coerceAtLeast(0),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Jump to Page Dialog
    // ---------------------------------------------------------------------------------------------
    if (showJumpDialog) {
        var jumpInput by remember { mutableStateOf("${pagerState.currentPage + 1}") }
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to Page") },
            text = {
                OutlinedTextField(
                    value = jumpInput,
                    onValueChange = { jumpInput = it },
                    label = { Text("Page number (1 to ${pages.size})") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = (jumpInput.toIntOrNull() ?: (pagerState.currentPage + 1)) - 1
                    val safeTarget = target.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(safeTarget)
                    }
                    showJumpDialog = false
                }) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Outline / Table of Contents Dialog
    // ---------------------------------------------------------------------------------------------
    if (showOutlineDialog) {
        val headings = remember(doc) {
            doc.blocks.mapIndexedNotNull { index, b ->
                if (b is DocxBlock.Paragraph && (b.headingLevel != 0 || b.runs.any { it.fontSizePt >= 16f && it.isBold })) {
                    Triple(index, b.headingLevel, b.fullText)
                } else null
            }
        }

        AlertDialog(
            onDismissRequest = { showOutlineDialog = false },
            title = {
                Text("Table of Contents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            text = {
                if (headings.isEmpty()) {
                    Text("No headings found in this document.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(headings) { _, (blockIndex, level, text) ->
                            val indent = when (level) {
                                -1, 1 -> 0.dp
                                2 -> 16.dp
                                3 -> 32.dp
                                else -> 40.dp
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        showOutlineDialog = false
                                        val pageIdx = pages.indexOfFirst { p -> p.blocks.any { it.first == blockIndex } }
                                        if (pageIdx != -1) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(pageIdx)
                                            }
                                        }
                                    }
                                    .padding(start = indent, top = 8.dp, bottom = 8.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = text.ifBlank { "Untitled Section" },
                                    style = if (level <= 1) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodySmall,
                                    color = if (level <= 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOutlineDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Full Image Preview Dialog
    // ---------------------------------------------------------------------------------------------
    fullImagePreview?.let { bitmap ->
        Dialog(onDismissRequest = { fullImagePreview = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Full Image Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { fullImagePreview = null }) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

// =================================================================================================
// Sub-composables for Document Blocks
// =================================================================================================

@Composable
private fun DocxParagraphView(
    paragraph: DocxBlock.Paragraph,
    zoomScale: Float,
    searchHighlight: String?,
    context: Context
) {
    val isDark = MaterialTheme.colorScheme.surface.isDark()

    val baseFontSize = when (paragraph.headingLevel) {
        -1 -> 24f // Title
        -2 -> 16f // Subtitle
        1 -> 20f  // Heading 1
        2 -> 17f  // Heading 2
        3 -> 15f  // Heading 3
        4 -> 14f  // Heading 4
        else -> 13.5f // Normal body
    } * zoomScale

    val textAlign = when (paragraph.alignment) {
        CellHorizontalAlignment.CENTER -> TextAlign.Center
        CellHorizontalAlignment.RIGHT -> TextAlign.Right
        CellHorizontalAlignment.JUSTIFY -> TextAlign.Justify
        else -> TextAlign.Start
    }

    val annotated = remember(paragraph, zoomScale, searchHighlight, isDark) {
        buildAnnotatedString {
            paragraph.runs.forEach { run ->
                val runFontPt = if (paragraph.headingLevel != 0) baseFontSize else (run.fontSizePt * zoomScale)
                var spanColor = if (run.colorRgb != null) Color(run.colorRgb) else Color.Unspecified
                if (spanColor != Color.Unspecified && isDark && isColorTooDark(spanColor)) {
                    spanColor = Color.White
                }

                val bg = if (run.highlightColorRgb != null) Color(run.highlightColorRgb) else Color.Transparent

                val startIdx = length
                withStyle(
                    SpanStyle(
                        fontSize = runFontPt.sp,
                        fontWeight = if (run.isBold || paragraph.headingLevel != 0) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (run.isItalic) FontStyle.Italic else FontStyle.Normal,
                        textDecoration = when {
                            run.isUnderline && run.isStrike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                            run.isUnderline -> TextDecoration.Underline
                            run.isStrike -> TextDecoration.LineThrough
                            else -> TextDecoration.None
                        },
                        baselineShift = when {
                            run.isSuperScript -> androidx.compose.ui.text.style.BaselineShift.Superscript
                            run.isSubScript -> androidx.compose.ui.text.style.BaselineShift.Subscript
                            else -> androidx.compose.ui.text.style.BaselineShift.None
                        },
                        color = if (run.linkUrl != null) Color(0xFF1976D2) else spanColor,
                        background = bg
                    )
                ) {
                    append(run.text)
                }

                if (run.linkUrl != null) {
                    addStringAnnotation(
                        tag = "URL",
                        annotation = run.linkUrl,
                        start = startIdx,
                        end = length
                    )
                }
            }

            // Apply search query highlight
            if (!searchHighlight.isNullOrBlank()) {
                val fullStr = this.toAnnotatedString().text
                var matchIdx = fullStr.indexOf(searchHighlight, ignoreCase = true)
                while (matchIdx >= 0) {
                    addStyle(
                        style = SpanStyle(
                            background = Color(0xFFFFEB3B),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        ),
                        start = matchIdx,
                        end = matchIdx + searchHighlight.length
                    )
                    matchIdx = fullStr.indexOf(searchHighlight, matchIdx + searchHighlight.length, ignoreCase = true)
                }
            }
        }
    }

    val topPadding = when (paragraph.headingLevel) {
        -1, 1 -> 14.dp
        2 -> 10.dp
        3 -> 8.dp
        else -> 2.dp
    }

    val bottomPadding = when (paragraph.headingLevel) {
        -1, 1 -> 6.dp
        2, 3 -> 4.dp
        else -> 2.dp
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = bottomPadding)
    ) {
        if (paragraph.bulletPrefix != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (paragraph.indentLevel * 18).dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = paragraph.bulletPrefix,
                    style = TextStyle(
                        fontSize = baseFontSize.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(end = 6.dp)
                )
                ClickableText(
                    text = annotated,
                    style = TextStyle(
                        fontSize = baseFontSize.sp,
                        textAlign = textAlign,
                        lineHeight = (baseFontSize * 1.35f).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    onClick = { offset ->
                        annotated.getStringAnnotations(tag = "URL", start = offset, end = offset).firstOrNull()?.let { annotation ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    }
                )
            }
        } else {
            ClickableText(
                text = annotated,
                style = TextStyle(
                    fontSize = baseFontSize.sp,
                    textAlign = textAlign,
                    lineHeight = (baseFontSize * 1.35f).sp,
                    color = if (paragraph.headingLevel in 1..6 || paragraph.headingLevel == -1) {
                        MaterialTheme.colorScheme.primary
                    } else MaterialTheme.colorScheme.onSurface
                ),
                onClick = { offset ->
                    annotated.getStringAnnotations(tag = "URL", start = offset, end = offset).firstOrNull()?.let { annotation ->
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                }
            )
        }
    }
}

@Composable
private fun DocxTableView(
    table: DocxBlock.Table,
    zoomScale: Float,
    searchHighlight: String?
) {
    val scrollState = rememberScrollState()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(4.dp)
        ) {
            table.rows.forEachIndexed { rIdx, row ->
                val isHeader = row.isHeader || rIdx == 0
                val rowBg = if (isHeader) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else if (rIdx % 2 == 1) {
                    MaterialTheme.colorScheme.surfaceContainerLowest
                } else Color.Transparent

                Row(
                    modifier = Modifier
                        .background(rowBg)
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    row.cells.forEach { cell ->
                        val cellBg = if (cell.fillRgb != null) Color(cell.fillRgb) else Color.Transparent

                        Box(
                            modifier = Modifier
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                .background(cellBg)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .widthIn(min = (80 * zoomScale).dp, max = (280 * zoomScale).dp)
                        ) {
                            val cellText = cell.fullText
                            val isHighlighted = !searchHighlight.isNullOrBlank() && cellText.contains(searchHighlight, ignoreCase = true)

                            Text(
                                text = cellText,
                                style = TextStyle(
                                    fontSize = (12f * zoomScale).sp,
                                    fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    background = if (isHighlighted) Color(0xFFFFEB3B) else Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocxImageView(
    image: DocxBlock.Image,
    localFile: File?,
    onImageClick: (Bitmap) -> Unit
) {
    if (localFile == null) return

    val bitmapState by produceState<Bitmap?>(initialValue = null, key1 = image.mediaEntryPath) {
        value = withContext(Dispatchers.IO) {
            SlideMediaCache.getOrDecode(
                zipFile = localFile,
                entryPath = image.mediaEntryPath,
                targetWidth = 1080,
                targetHeight = 800
            )
        }
    }

    bitmapState?.let { bitmap ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = image.caption ?: "Document Image",
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .heightIn(max = 360.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .clickable { onImageClick(bitmap) }
            )
            if (!image.caption.isNullOrBlank()) {
                Text(
                    text = image.caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun DocxPageBreakView() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = "  Page Break  ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        HorizontalDivider(modifier = Modifier.weight(1f), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// =================================================================================================
// Color Utilities
// =================================================================================================

private fun Color.isDark(): Boolean {
    val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
    return luminance < 0.5f
}

private fun isColorTooDark(color: Color): Boolean {
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return luminance < 0.35f
}
