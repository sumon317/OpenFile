package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.document.CbzDocument
import com.example.core.document.CbzPage
import com.example.core.document.DocBlock
import com.example.core.document.DocxDocument
import com.example.core.document.DocumentReaderEngine
import com.example.core.document.MarkdownDocument
import com.example.core.document.MarkdownInline
import com.example.core.document.MarkdownNode
import com.example.core.document.PptSlide
import com.example.core.document.PptxDocument
import com.example.core.document.SpreadsheetDocument
import com.example.core.document.SpreadsheetSheet
import com.example.core.model.FileReference
import com.example.core.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------------------------------------------------------------------------------------------
// Helper: Resolve local File or extract from ContentResolver
// ---------------------------------------------------------------------------------------------

fun resolveOrCacheLocalFile(file: FileReference, context: Context): File? {
    file.metadata["localBackingPath"]?.takeIf { it.isNotBlank() }?.let { path ->
        val f = File(path)
        if (f.exists()) return f
    }
    if (file.providerType == ProviderType.LOCAL) {
        val f = File(file.uriOrPath)
        if (f.exists()) return f
    }
    return try {
        val uri = Uri.parse(file.uriOrPath)
        val cacheFile = File(context.cacheDir, "preview_${file.displayName.hashCode()}_${file.displayName}")
        if (cacheFile.exists() && cacheFile.length() > 0L) {
            return cacheFile
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (cacheFile.exists() && cacheFile.length() > 0L) cacheFile else null
    } catch (e: Exception) {
        null
    }
}

// =============================================================================================
// 1. DOCX & ODT Document Previewer
// =============================================================================================

@Composable
fun DocxDocumentPreview(file: FileReference) {
    val context = LocalContext.current
    val ext = file.extension.lowercase()
    val docState by produceState<DocxDocument?>(initialValue = null, key1 = file.uriOrPath) {
        value = withContext(Dispatchers.IO) {
            val localFile = resolveOrCacheLocalFile(file, context)
            if (localFile != null) {
                if (ext in setOf("odt", "odf")) {
                    DocumentReaderEngine.readOdt(localFile)
                } else {
                    DocumentReaderEngine.readDocx(localFile)
                }
            } else null
        }
    }

    if (docState == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
        }
        return
    }

    val doc = docState!!
    SelectionContainer {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("docx_document_preview"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(doc.blocks) { block ->
                when (block) {
                    is DocBlock.Paragraph -> {
                        val annotated = buildAnnotatedString {
                            block.runs.forEach { run ->
                                withStyle(
                                    SpanStyle(
                                        fontWeight = if (run.isBold || block.isHeading) FontWeight.Bold else FontWeight.Normal,
                                        fontStyle = if (run.isItalic) FontStyle.Italic else FontStyle.Normal,
                                        textDecoration = if (run.isUnderline) TextDecoration.Underline else TextDecoration.None
                                    )
                                ) {
                                    append(run.text)
                                }
                            }
                        }

                        if (block.isHeading) {
                            val fontSize = when (block.headingLevel) {
                                1 -> 22.sp
                                2 -> 19.sp
                                3 -> 17.sp
                                else -> 16.sp
                            }
                            Text(
                                text = annotated,
                                fontSize = fontSize,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                            )
                        } else if (block.isBullet) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text("• ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = annotated,
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 22.sp
                                )
                            }
                        } else {
                            Text(
                                text = annotated,
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 22.sp
                            )
                        }
                    }
                    is DocBlock.Table -> {
                        DocumentTableView(table = block)
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentTableView(table: DocBlock.Table) {
    val scrollState = rememberScrollState()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(8.dp)
        ) {
            table.rows.forEachIndexed { rIdx, row ->
                Row(
                    modifier = Modifier.background(
                        if (rIdx == 0) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
                    )
                ) {
                    row.forEach { cell ->
                        Box(
                            modifier = Modifier
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .widthIn(min = 90.dp, max = 220.dp)
                        ) {
                            Text(
                                text = cell,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (rIdx == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================================
// 2. PPTX & ODP Presentation Previewer
// =============================================================================================

@Composable
fun PptxDocumentPreview(file: FileReference) {
    val context = LocalContext.current
    val ext = file.extension.lowercase()
    val pptState by produceState<PptxDocument?>(initialValue = null, key1 = file.uriOrPath) {
        value = withContext(Dispatchers.IO) {
            val localFile = resolveOrCacheLocalFile(file, context)
            if (localFile != null) {
                if (ext == "odp") {
                    DocumentReaderEngine.readOdp(localFile)
                } else {
                    DocumentReaderEngine.readPptx(localFile)
                }
            } else null
        }
    }

    if (pptState == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
        }
        return
    }

    val doc = pptState!!
    var currentSlideIndex by remember { mutableIntStateOf(0) }
    val totalSlides = doc.slides.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("pptx_document_preview")
    ) {
        // Navigation bar: Previous / Slide X of Y / Next
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { if (currentSlideIndex > 0) currentSlideIndex-- },
                enabled = currentSlideIndex > 0
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Slide")
            }

            Text(
                text = "Slide ${currentSlideIndex + 1} of $totalSlides",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            IconButton(
                onClick = { if (currentSlideIndex < totalSlides - 1) currentSlideIndex++ },
                enabled = currentSlideIndex < totalSlides - 1
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Slide")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Slide Content Card
        val slide = doc.slides[currentSlideIndex.coerceIn(0, totalSlides - 1)]
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (!slide.title.isNullOrBlank()) {
                    Text(
                        text = slide.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(14.dp))
                }

                slide.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "• ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 24.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Slide scrubber slider
        if (totalSlides > 1) {
            Slider(
                value = currentSlideIndex.toFloat(),
                onValueChange = { currentSlideIndex = it.toInt() },
                valueRange = 0f..(totalSlides - 1).toFloat(),
                steps = totalSlides - 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// =============================================================================================
// 3. Spreadsheet (XLSX, ODS, CSV) Previewer
// =============================================================================================

@Composable
fun SpreadsheetPreview(file: FileReference) {
    val context = LocalContext.current
    val docState by produceState<SpreadsheetDocument?>(initialValue = null, key1 = file.uriOrPath) {
        value = withContext(Dispatchers.IO) {
            val localFile = resolveOrCacheLocalFile(file, context)
            if (localFile != null) {
                DocumentReaderEngine.readSpreadsheet(localFile)
            } else null
        }
    }

    if (docState == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
        }
        return
    }

    val doc = docState!!
    var selectedSheetIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCellContent by remember { mutableStateOf<String?>(null) }

    val currentSheet = doc.sheets.getOrNull(selectedSheetIndex) ?: doc.sheets.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("spreadsheet_preview")
    ) {
        // Multiple Sheets Tab Row
        if (doc.sheets.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = selectedSheetIndex,
                edgePadding = 12.dp
            ) {
                doc.sheets.forEachIndexed { index, sheet ->
                    Tab(
                        selected = selectedSheetIndex == index,
                        onClick = { selectedSheetIndex = index },
                        text = { Text(sheet.name, maxLines = 1) }
                    )
                }
            }
        }

        // Search Bar in Spreadsheet
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search spreadsheet...", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${currentSheet.rows.size} rows",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val horizontalScrollState = rememberScrollState()
        val numCols = currentSheet.columnCount.coerceIn(1, 100)

        // 2D Spreadsheet Grid
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .horizontalScroll(horizontalScrollState)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header Row (A, B, C, D...)
                item(key = "header_row") {
                    Row(
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        // Corner cell
                        Box(
                            modifier = Modifier
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                .size(width = 44.dp, height = 30.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("#", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        for (col in 0 until numCols) {
                            val colLetter = indexToColumnLetter(col)
                            Box(
                                modifier = Modifier
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                    .size(width = 110.dp, height = 30.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = colLetter,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Data Rows
                itemsIndexed(currentSheet.rows) { rIdx, row ->
                    val rowMatches = searchQuery.isBlank() || row.any { it.contains(searchQuery, ignoreCase = true) }
                    if (rowMatches) {
                        Row(
                            modifier = Modifier.background(
                                if (rIdx % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            // Row number
                            Box(
                                modifier = Modifier
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .size(width = 44.dp, height = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${rIdx + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            for (col in 0 until numCols) {
                                val cellVal = row.getOrNull(col) ?: ""
                                val isMatch = searchQuery.isNotBlank() && cellVal.contains(searchQuery, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                        .background(if (isMatch) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .size(width = 110.dp, height = 32.dp)
                                        .clickable { if (cellVal.isNotBlank()) selectedCellContent = cellVal }
                                        .padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = cellVal,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (isMatch) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Cell Inspection Dialog
        if (selectedCellContent != null) {
            val clipMgr = LocalClipboardManager.current
            AlertDialog(
                onDismissRequest = { selectedCellContent = null },
                title = { Text("Cell Contents") },
                text = {
                    SelectionContainer {
                        Text(selectedCellContent!!, style = MaterialTheme.typography.bodyMedium)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        clipMgr.setText(AnnotatedString(selectedCellContent!!))
                        selectedCellContent = null
                    }) {
                        Text("Copy")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedCellContent = null }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

private fun indexToColumnLetter(index: Int): String {
    var num = index
    val sb = StringBuilder()
    while (num >= 0) {
        sb.insert(0, ('A'.code + (num % 26)).toChar())
        num = (num / 26) - 1
    }
    return sb.toString()
}

// =============================================================================================
// 4. Comic Book Reader (CBZ, CBR) Previewer
// =============================================================================================

@Composable
fun CbzComicPreview(file: FileReference) {
    val context = LocalContext.current
    val cbzState by produceState<CbzDocument?>(initialValue = null, key1 = file.uriOrPath) {
        value = withContext(Dispatchers.IO) {
            val localFile = resolveOrCacheLocalFile(file, context)
            if (localFile != null) {
                DocumentReaderEngine.readCbz(localFile)
            } else null
        }
    }

    if (cbzState == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
        }
        return
    }

    val cbz = cbzState!!
    if (cbz.pages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No comic images found inside archive.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = 0) { cbz.pages.size }
    val coroutineScope = rememberCoroutineScope()
    var isCurrentPageZoomed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("cbz_comic_preview")
    ) {
        HorizontalPager(
            state = pagerState,
            key = { cbz.pages[it].entryName },
            userScrollEnabled = !isCurrentPageZoomed,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { pageIdx ->
            val page = cbz.pages[pageIdx]
            val pageFileState by produceState<File?>(initialValue = null, key1 = page.entryName) {
                value = withContext(Dispatchers.IO) {
                    val localArchive = resolveOrCacheLocalFile(file, context)
                    if (localArchive != null) {
                        DocumentReaderEngine.extractCbzPage(localArchive, page, context.cacheDir)
                    } else null
                }
            }

            if (pageFileState != null) {
                ZoomableComicImage(
                    imageFile = pageFileState!!,
                    onZoomChanged = { isZoomed ->
                        if (pagerState.currentPage == pageIdx) {
                            isCurrentPageZoomed = isZoomed
                        }
                    }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
                }
            }
        }

        // Top Page Badge (e.g. Page 1 of 40)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.65f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            Text(
                text = "Page ${pagerState.currentPage + 1} / ${cbz.pages.size}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        // Left Navigation Button
        if (pagerState.currentPage > 0) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(44.dp)
            ) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous Page",
                        tint = Color.White
                    )
                }
            }
        }

        // Right Navigation Button
        if (pagerState.currentPage < cbz.pages.size - 1) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(44.dp)
            ) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next Page",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomableComicImage(
    imageFile: File,
    onZoomChanged: (Boolean) -> Unit
) {
    var scale by remember(imageFile) { mutableFloatStateOf(1f) }
    var offsetX by remember(imageFile) { mutableFloatStateOf(0f) }
    var offsetY by remember(imageFile) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(imageFile) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                            onZoomChanged(false)
                        } else {
                            scale = 2.5f
                            onZoomChanged(true)
                        }
                    }
                )
            }
            .pointerInput(imageFile) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }

                        if (scale > 1.05f) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                            scale = newScale
                            if (newScale > 1.05f) {
                                val maxOffsetX = (size.width.toFloat() * (newScale - 1f) / 2f).coerceAtLeast(0f)
                                val maxOffsetY = (size.height.toFloat() * (newScale - 1f) / 2f).coerceAtLeast(0f)
                                offsetX = (offsetX + panChange.x).coerceIn(-maxOffsetX, maxOffsetX)
                                offsetY = (offsetY + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                                onZoomChanged(true)
                            } else {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                                onZoomChanged(false)
                            }
                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        } else {
                            if (pointerCount >= 2) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                if (newScale > 1.05f) {
                                    scale = newScale
                                    val maxOffsetX = (size.width.toFloat() * (newScale - 1f) / 2f).coerceAtLeast(0f)
                                    val maxOffsetY = (size.height.toFloat() * (newScale - 1f) / 2f).coerceAtLeast(0f)
                                    offsetX = (offsetX + panChange.x).coerceIn(-maxOffsetX, maxOffsetX)
                                    offsetY = (offsetY + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                                    onZoomChanged(true)
                                    event.changes.forEach { change ->
                                        if (change.positionChanged()) change.consume()
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageFile,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}

// =============================================================================================
// 5. Markdown Previewer & Editor
// =============================================================================================

@Composable
fun MarkdownDocumentPreview(
    file: FileReference,
    onSaveText: ((FileReference, String) -> Unit)? = null
) {
    val context = LocalContext.current
    var rawText by remember(file) { mutableStateOf("") }
    var isEditMode by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            val localFile = resolveOrCacheLocalFile(file, context)
            val content = localFile?.readText(Charsets.UTF_8) ?: ""
            rawText = content
            editText = content
        }
    }

    val markdownDoc = remember(rawText) {
        DocumentReaderEngine.parseMarkdown(rawText, file.displayName)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("markdown_document_preview")
    ) {
        // Toggle bar: Rendered vs Raw Edit
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !isEditMode,
                    onClick = { isEditMode = false },
                    label = { Text("Rendered") },
                    leadingIcon = { Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = isEditMode,
                    onClick = {
                        editText = rawText
                        isEditMode = true
                    },
                    label = { Text("Source Edit") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            if (isEditMode && onSaveText != null) {
                OutlinedButton(
                    onClick = {
                        rawText = editText
                        onSaveText(file, editText)
                        isEditMode = false
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save")
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (isEditMode) {
            OutlinedTextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )
        } else {
            SelectionContainer {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(markdownDoc.nodes) { node ->
                        when (node) {
                            is MarkdownNode.Header -> {
                                val (fontSize, weight) = when (node.level) {
                                    1 -> 24.sp to FontWeight.Bold
                                    2 -> 20.sp to FontWeight.Bold
                                    3 -> 18.sp to FontWeight.SemiBold
                                    4 -> 16.sp to FontWeight.SemiBold
                                    else -> 15.sp to FontWeight.Medium
                                }
                                Text(
                                    text = node.text,
                                    fontSize = fontSize,
                                    fontWeight = weight,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                                )
                            }
                            is MarkdownNode.CodeBlock -> {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val clipMgr = LocalClipboardManager.current
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        if (node.language.isNotBlank()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = node.language,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                IconButton(
                                                    onClick = { clipMgr.setText(AnnotatedString(node.code)) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy code", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }
                                        Text(
                                            text = node.code,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                            is MarkdownNode.Blockquote -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(24.dp)
                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = node.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontStyle = FontStyle.Italic
                                    )
                                }
                            }
                            is MarkdownNode.ChecklistItem -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = if (node.isChecked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                        contentDescription = null,
                                        tint = if (node.isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = node.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textDecoration = if (node.isChecked) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                }
                            }
                            is MarkdownNode.ListItem -> {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                                ) {
                                    Text(
                                        text = if (node.isOrdered) "${node.orderNumber}. " else "• ",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(text = node.text, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            is MarkdownNode.HorizontalRule -> {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            is MarkdownNode.Table -> {
                                MarkdownTableView(node)
                            }
                            is MarkdownNode.Paragraph -> {
                                val annotated = buildAnnotatedString {
                                    node.inlines.forEach { inline ->
                                        val styles = SpanStyle(
                                            fontWeight = if (inline.isBold) FontWeight.Bold else FontWeight.Normal,
                                            fontStyle = if (inline.isItalic) FontStyle.Italic else FontStyle.Normal,
                                            fontFamily = if (inline.isCode) FontFamily.Monospace else FontFamily.Default,
                                            background = if (inline.isCode) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                                            color = if (inline.linkUrl != null) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                            textDecoration = when {
                                                inline.isStrikethrough -> TextDecoration.LineThrough
                                                inline.linkUrl != null -> TextDecoration.Underline
                                                else -> TextDecoration.None
                                            }
                                        )
                                        withStyle(styles) {
                                            append(inline.text)
                                        }
                                    }
                                }
                                Text(
                                    text = annotated,
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownTableView(table: MarkdownNode.Table) {
    val scrollState = rememberScrollState()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(6.dp)
        ) {
            // Headers
            if (table.headers.isNotEmpty()) {
                Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    table.headers.forEach { header ->
                        Box(
                            modifier = Modifier
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .widthIn(min = 80.dp, max = 200.dp)
                        ) {
                            Text(header, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            // Rows
            table.rows.forEach { row ->
                Row {
                    row.forEach { cell ->
                        Box(
                            modifier = Modifier
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .widthIn(min = 80.dp, max = 200.dp)
                        ) {
                            Text(cell, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================================
// 6. Enhanced Multi-Page PDF Viewer
// =============================================================================================

@Composable
fun PdfDocumentViewer(file: FileReference) {
    val context = LocalContext.current
    var pageCount by remember { mutableIntStateOf(0) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showJumpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(file, currentPageIndex) {
        withContext(Dispatchers.IO) {
            runCatching {
                val localFile = resolveOrCacheLocalFile(file, context)
                val pfd = if (localFile != null) {
                    ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
                } else {
                    context.contentResolver.openFileDescriptor(Uri.parse(file.uriOrPath), "r")
                } ?: throw IllegalStateException("Cannot open file descriptor for PDF")

                pfd.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        pageCount = renderer.pageCount
                        if (renderer.pageCount > 0) {
                            val idx = currentPageIndex.coerceIn(0, renderer.pageCount - 1)
                            renderer.openPage(idx).use { page ->
                                val bmp = Bitmap.createBitmap(
                                    (page.width * 2).coerceAtMost(2048),
                                    (page.height * 2).coerceAtMost(2048),
                                    Bitmap.Config.ARGB_8888
                                )
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                pageBitmap = bmp
                            }
                        }
                    }
                }
            }.onFailure {
                errorText = it.message ?: "Unable to render PDF"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("pdf_document_viewer"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (errorText != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = errorText!!, color = MaterialTheme.colorScheme.error)
            }
        } else if (pageBitmap != null) {
            // Rendered Page Image
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = pageBitmap!!.asImageBitmap(),
                    contentDescription = "PDF Page ${currentPageIndex + 1}",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                )
            }

            // Bottom Navigation & Page Scrubber Bar
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
                            onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                            enabled = currentPageIndex > 0
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Page")
                        }

                        TextButton(onClick = { showJumpDialog = true }) {
                            Text(
                                text = "Page ${currentPageIndex + 1} of $pageCount (Jump)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        IconButton(
                            onClick = { if (currentPageIndex < pageCount - 1) currentPageIndex++ },
                            enabled = currentPageIndex < pageCount - 1
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Page")
                        }
                    }

                    if (pageCount > 1) {
                        Slider(
                            value = currentPageIndex.toFloat(),
                            onValueChange = { currentPageIndex = it.toInt() },
                            valueRange = 0f..(pageCount - 1).toFloat(),
                            steps = (pageCount - 2).coerceAtLeast(0),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
            }
        }

        // Jump to Page Dialog
        if (showJumpDialog) {
            var jumpInput by remember { mutableStateOf("${currentPageIndex + 1}") }
            AlertDialog(
                onDismissRequest = { showJumpDialog = false },
                title = { Text("Jump to Page") },
                text = {
                    OutlinedTextField(
                        value = jumpInput,
                        onValueChange = { jumpInput = it },
                        label = { Text("Page number (1 to $pageCount)") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val target = (jumpInput.toIntOrNull() ?: (currentPageIndex + 1)) - 1
                        currentPageIndex = target.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
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
    }
}
