package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.example.core.document.CellRange
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
import com.example.core.document.SelectionStats
import com.example.core.document.SpreadsheetCell
import com.example.core.document.SpreadsheetDocument
import com.example.core.document.SpreadsheetParser
import com.example.core.document.SpreadsheetSheet
import com.example.core.document.SpreadsheetSheetModel
import com.example.core.document.SpreadsheetWorkbookModel
import com.example.core.model.FileReference
import com.example.core.model.ProviderType
import android.content.Intent
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
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
        val safeName = file.displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val cacheFile = File(context.cacheDir, "preview_${file.displayName.hashCode()}_$safeName")
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
    DocxViewer(file = file)
}

// =============================================================================================
// 2. PPTX & ODP Presentation Previewer
// =============================================================================================

@Composable
fun PptxDocumentPreview(file: FileReference) {
    PresentationViewer(file = file)
}

// =============================================================================================
// 3. Spreadsheet (XLSX, ODS, CSV) Previewer
// =============================================================================================

@Composable
fun SpreadsheetPreview(file: FileReference) {
    val context = LocalContext.current
    var reloadTrigger by remember { mutableIntStateOf(0) }

    val docResult by produceState<Result<SpreadsheetWorkbookModel>?>(initialValue = null, key1 = file.uriOrPath, key2 = reloadTrigger) {
        value = withContext(Dispatchers.IO) {
            val localFile = resolveOrCacheLocalFile(file, context)
            if (localFile != null) {
                try {
                    Result.success(SpreadsheetParser.parse(localFile))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            } else {
                Result.failure(IllegalStateException("File could not be opened."))
            }
        }
    }

    if (docResult == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
            modifier = Modifier
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
                            isLegacy -> "Legacy Format (.xls) Not Supported"
                            else -> "Could Not Preview Spreadsheet"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when {
                            isPassword -> "This spreadsheet is encrypted or password-protected and cannot be previewed."
                            isLegacy -> "Legacy binary Excel (.xls) files are not supported by the built-in desktop renderer. Please convert to .xlsx or .ods, or open in an external office app."
                            else -> error?.message ?: "An unexpected error occurred while parsing the spreadsheet."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val uri = Uri.parse(file.uriOrPath)
                                    val shareIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, file.mimeType.ifBlank { "application/vnd.ms-excel" })
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Open with"))
                                } catch (_: Exception) {}
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open in External App")
                        }
                        if (!isLegacy && !isPassword) {
                            OutlinedButton(onClick = { reloadTrigger++ }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
        return
    }

    val workbook = result.getOrThrow()
    val visibleSheets = remember(workbook) {
        workbook.sheets.filter { !it.view.isHidden }.ifEmpty { workbook.sheets }
    }
    var selectedSheetIndex by remember { mutableIntStateOf(0) }
    val currentSheet = visibleSheets.getOrNull(selectedSheetIndex) ?: visibleSheets.first()

    var selectedCellCoords by remember { mutableStateOf<Pair<Int, Int>?>(0 to 0) }
    var selectedRange by remember { mutableStateOf<CellRange?>(null) }
    var inspectCellDialog by remember { mutableStateOf<SpreadsheetCell?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val activeCell = remember(currentSheet, selectedCellCoords) {
        selectedCellCoords?.let { (r, c) -> currentSheet.getCell(r, c) }
    }

    // Compute live selection statistics asynchronously
    var stats by remember { mutableStateOf<SelectionStats?>(null) }
    LaunchedEffect(currentSheet, selectedCellCoords, selectedRange) {
        withContext(Dispatchers.Default) {
            val cellsToStat = mutableListOf<SpreadsheetCell>()
            if (selectedRange != null) {
                val r = selectedRange!!
                val rowLimit = (r.lastRow - r.firstRow + 1).coerceAtMost(500)
                val colLimit = (r.lastCol - r.firstCol + 1).coerceAtMost(50)
                for (row in r.firstRow until (r.firstRow + rowLimit)) {
                    for (col in r.firstCol until (r.firstCol + colLimit)) {
                        currentSheet.getCell(row, col)?.let { cellsToStat.add(it) }
                    }
                }
            } else if (selectedCellCoords != null) {
                currentSheet.getCell(selectedCellCoords!!.first, selectedCellCoords!!.second)?.let {
                    cellsToStat.add(it)
                }
            }

            if (cellsToStat.isNotEmpty()) {
                val numbers = cellsToStat.mapNotNull { it.rawValue?.toDoubleOrNull() ?: it.displayValue.toDoubleOrNull() }
                val count = cellsToStat.size
                val numCount = numbers.size
                val sum = if (numCount > 0) numbers.sum() else null
                val avg = if (numCount > 0) sum!! / numCount else null
                val min = if (numCount > 0) numbers.minOrNull() else null
                val max = if (numCount > 0) numbers.maxOrNull() else null
                stats = SelectionStats(count, numCount, sum, avg, min, max)
            } else {
                stats = null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("spreadsheet_preview")
    ) {
        // 1. Multiple Sheets Tab Row
        if (visibleSheets.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = selectedSheetIndex.coerceIn(0, visibleSheets.size - 1),
                edgePadding = 12.dp
            ) {
                visibleSheets.forEachIndexed { index, sheet ->
                    Tab(
                        selected = selectedSheetIndex == index,
                        onClick = {
                            selectedSheetIndex = index
                            selectedCellCoords = 0 to 0
                            selectedRange = null
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (sheet.view.tabColorRgb != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(sheet.view.tabColorRgb), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(sheet.name, maxLines = 1)
                            }
                        }
                    )
                }
            }
        }

        // 2. Formula Bar & Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Formula / Cell Reference Box
            val cellCoordText = selectedCellCoords?.let { (r, c) ->
                "${SpreadsheetParser.indexToColumnLetter(c)}${r + 1}"
            } ?: "A1"

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .weight(1f)
                    .clickable { inspectCellDialog = activeCell ?: SpreadsheetCell(selectedCellCoords?.first ?: 0, selectedCellCoords?.second ?: 0) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = cellCoordText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "fx",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = activeCell?.formula?.let { "=$it" }
                            ?: activeCell?.displayValue?.ifBlank { activeCell.rawValue }
                            ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val screenConfig = LocalConfiguration.current
            val isSmallScreen = screenConfig.screenWidthDp < 360
            val searchInputWidth = if (isSmallScreen) 100.dp else 130.dp

            Spacer(modifier = Modifier.width(6.dp))

            // Search input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Find...", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier
                    .width(searchInputWidth)
                    .height(44.dp)
            )
        }

        // 3. High-Performance Virtualized Canvas Grid
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            SpreadsheetCanvasGrid(
                sheet = currentSheet,
                selectedCell = selectedCellCoords,
                selectedRange = selectedRange,
                searchQuery = searchQuery,
                onCellClick = { row, col, _ ->
                    selectedCellCoords = row to col
                    selectedRange = null
                },
                onRangeSelected = { range ->
                    selectedRange = range
                }
            )
        }

        // 4. Quick Statistics Bar
        if (stats != null && stats!!.numericCount > 0) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Count: ${stats!!.totalCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (stats!!.sum != null) {
                        Text(
                            text = "Sum: ${String.format(java.util.Locale.US, "%,.2f", stats!!.sum)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (stats!!.average != null) {
                        Text(
                            text = "Average: ${String.format(java.util.Locale.US, "%,.2f", stats!!.average)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (stats!!.min != null && stats!!.max != null && stats!!.numericCount > 1) {
                        Text(
                            text = "Min: ${String.format(java.util.Locale.US, "%,.2f", stats!!.min)} | Max: ${String.format(java.util.Locale.US, "%,.2f", stats!!.max)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Cell Inspection & Copy Dialog
    if (inspectCellDialog != null) {
        val cell = inspectCellDialog!!
        val clipMgr = LocalClipboardManager.current
        val coordText = "${SpreadsheetParser.indexToColumnLetter(cell.colIndex)}${cell.rowIndex + 1}"

        AlertDialog(
            onDismissRequest = { inspectCellDialog = null },
            title = { Text("Cell $coordText") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!cell.formula.isNullOrBlank()) {
                        Text("Formula:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        SelectionContainer {
                            Text("=${cell.formula}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        HorizontalDivider()
                    }
                    Text("Display Value:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    SelectionContainer {
                        Text(cell.displayValue.ifBlank { "(empty)" }, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!cell.rawValue.isNullOrBlank() && cell.rawValue != cell.displayValue) {
                        HorizontalDivider()
                        Text("Raw Value:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SelectionContainer {
                            Text(cell.rawValue, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val toCopy = cell.formula?.let { "=$it" } ?: cell.displayValue.ifBlank { cell.rawValue ?: "" }
                    clipMgr.setText(AnnotatedString(toCopy))
                    inspectCellDialog = null
                }) {
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectCellDialog = null }) {
                    Text("Close")
                }
            }
        )
    }
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
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
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
                    itemsIndexed(
                        items = markdownDoc.nodes,
                        key = { index, _ -> index },
                        contentType = { _, node -> node::class.java.simpleName }
                    ) { _, node ->
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
                                val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
                                val linkColor = MaterialTheme.colorScheme.primary
                                val annotated = remember(node, codeBackground, linkColor) {
                                    buildAnnotatedString {
                                        node.inlines.forEach { inline ->
                                            val styles = SpanStyle(
                                                fontWeight = if (inline.isBold) FontWeight.Bold else FontWeight.Normal,
                                                fontStyle = if (inline.isItalic) FontStyle.Italic else FontStyle.Normal,
                                                fontFamily = if (inline.isCode) FontFamily.Monospace else FontFamily.Default,
                                                background = if (inline.isCode) codeBackground else Color.Transparent,
                                                color = if (inline.linkUrl != null) linkColor else Color.Unspecified,
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

// =============================================================================================
// 6. Enhanced Multi-Page Horizontal Scroll PDF Viewer
// =============================================================================================

object PdfPageCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtLeast(1024)

    private val lruCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun get(key: String): Bitmap? = lruCache.get(key)?.takeIf { !it.isRecycled }

    fun put(key: String, bitmap: Bitmap) {
        lruCache.put(key, bitmap)
    }

    fun clear() {
        lruCache.evictAll()
    }
}

@Composable
fun PdfDocumentViewer(file: FileReference) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pageCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showJumpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(file.uriOrPath) {
        isLoading = true
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
                    }
                }
            }.onFailure {
                errorText = it.message ?: "Unable to read PDF"
            }
            isLoading = false
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
        } else if (pageCount > 0) {
            val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })
            var isCurrentPageZoomed by remember { mutableStateOf(false) }

            LaunchedEffect(pagerState.currentPage) {
                isCurrentPageZoomed = false
            }

            // Horizontal Swipeable / Scrollable Page Viewport
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                userScrollEnabled = !isCurrentPageZoomed,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("pdf_horizontal_pager")
            ) { pageIndex ->
                PdfPageItem(
                    file = file,
                    pageIndex = pageIndex,
                    isCurrentPage = pagerState.currentPage == pageIndex,
                    onZoomChanged = { zoomed ->
                        if (pagerState.currentPage == pageIndex) {
                            isCurrentPageZoomed = zoomed
                        }
                    }
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
                                text = "Page ${pagerState.currentPage + 1} of $pageCount (Jump)",
                                style = pageTitleStyle,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = {
                                if (pagerState.currentPage < pageCount - 1) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            },
                            enabled = pagerState.currentPage < pageCount - 1
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Page")
                        }
                    }

                    if (pageCount > 1) {
                        var sliderPos by remember(pagerState.currentPage) { mutableFloatStateOf(pagerState.currentPage.toFloat()) }
                        Slider(
                            value = sliderPos,
                            onValueChange = { sliderPos = it },
                            onValueChangeFinished = {
                                coroutineScope.launch {
                                    pagerState.scrollToPage(sliderPos.toInt().coerceIn(0, pageCount - 1))
                                }
                            },
                            valueRange = 0f..(pageCount - 1).toFloat(),
                            steps = (pageCount - 2).coerceAtLeast(0),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Jump to Page Dialog
            if (showJumpDialog) {
                var jumpInput by remember { mutableStateOf("${pagerState.currentPage + 1}") }
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
                            val target = (jumpInput.toIntOrNull() ?: (pagerState.currentPage + 1)) - 1
                            val safeTarget = target.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
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
        } else if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This PDF document contains no pages.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PdfPageItem(
    file: FileReference,
    pageIndex: Int,
    isCurrentPage: Boolean,
    onZoomChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val cacheKey = "${file.uriOrPath}::$pageIndex"

    var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
    var offsetX by remember(pageIndex) { mutableFloatStateOf(0f) }
    var offsetY by remember(pageIndex) { mutableFloatStateOf(0f) }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage && scale != 1f) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
            onZoomChanged(false)
        }
    }

    val pageBitmap by produceState<Bitmap?>(initialValue = PdfPageCache.get(cacheKey), key1 = cacheKey) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val localFile = resolveOrCacheLocalFile(file, context)
                val pfd = if (localFile != null) {
                    ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
                } else {
                    context.contentResolver.openFileDescriptor(Uri.parse(file.uriOrPath), "r")
                } ?: return@runCatching null

                pfd.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        if (pageIndex in 0 until renderer.pageCount) {
                            renderer.openPage(pageIndex).use { page ->
                                val targetW = (page.width * 2).coerceIn(400, 2048)
                                val targetH = (page.height * 2).coerceIn(400, 2048)
                                val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                PdfPageCache.put(cacheKey, bmp)
                                bmp
                            }
                        } else null
                    }
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .pointerInput(pageIndex) {
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
            .pointerInput(pageIndex) {
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
                        } else if (pointerCount >= 2) {
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
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (pageBitmap != null) {
            Image(
                bitmap = pageBitmap!!.asImageBitmap(),
                contentDescription = "PDF Page ${pageIndex + 1}",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
        }
    }
}
