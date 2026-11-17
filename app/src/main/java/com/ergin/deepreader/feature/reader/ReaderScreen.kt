package com.ergin.deepreader.feature.reader

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.ergin.deepreader.R
import com.ergin.deepreader.core.designsystem.theme.LocalExtendedReaderColors
import com.ergin.deepreader.domain.model.Book
import com.ergin.deepreader.domain.model.ChapterContent
import com.ergin.deepreader.domain.model.ExplainRequest
import com.ergin.deepreader.domain.model.ParagraphBlock
import com.ergin.deepreader.domain.model.ParagraphLayoutInfo
import com.ergin.deepreader.domain.model.ReadingPosition
import com.ergin.deepreader.domain.model.SavedQuote
import com.ergin.deepreader.domain.model.SentenceLayoutInfo
import com.ergin.deepreader.domain.model.SentenceSpan
import com.ergin.deepreader.domain.model.ViewportLayoutSnapshot
import com.ergin.deepreader.ui.theme.DeepReaderTheme
import com.ergin.deepreader.ui.theme.ReaderThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private class ReaderSelectionMenuSession(
    val dataProvider: TextContextMenuDataProvider
) : TextContextMenuSession {
    private val closeSignal = CompletableDeferred<Unit>()

    override fun close() {
        closeSignal.complete(Unit)
    }

    suspend fun awaitClose() {
        closeSignal.await()
    }
}

private class SelectionClipboardCapture {
    var captureOnly: Boolean = false
    var capturedText: String? = null
}

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    bookSha256: String,
    hasLastAiResult: Boolean = false,
    onBackClick: () -> Unit,
    onTranslateRequested: (String) -> Unit = {},
    onAiExplainRequested: (ExplainRequest) -> Unit = {},
    onReopenAiResult: () -> Unit = {}
) {
    LaunchedEffect(bookSha256) {
        viewModel.loadBook(bookSha256)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.flushReadingPositionImmediately()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushReadingPositionImmediately()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is ReaderUiEvent.ShowMessage -> {
                        snackbarHostState.showSnackbar(resources.getString(event.messageResId))
                    }
                    is ReaderUiEvent.RequestTranslation -> {
                        onTranslateRequested(event.text)
                    }
                    is ReaderUiEvent.RequestAiExplain -> {
                        onAiExplainRequested(event.request)
                    }
                }
            }
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentThemeMode = (uiState as? ReaderUiState.Success)?.readerThemeMode

    DeepReaderTheme(readerThemeMode = currentThemeMode) {
        ReaderScreenContent(
            uiState = uiState,
            snackbarHostState = snackbarHostState,
            hasLastAiResult = hasLastAiResult,
            onBackClick = onBackClick,
            onPreviousChapter = { openAtEnd -> viewModel.onPreviousChapter(openAtEnd) },
            onNextChapter = viewModel::onNextChapter,
            onFontSizeChanged = viewModel::onFontSizeChanged,
            onThemeModeChanged = viewModel::onThemeModeChanged,
            onScrollPositionChanged = viewModel::onScrollPositionChanged,
            onTranslateSelected = viewModel::onTranslateSelection,
            onAiExplainSelected = viewModel::onAiExplainSelection,
            onBookmarksClick = { viewModel.onBookmarksSheetToggled(true) },
            onDismissBookmarks = { viewModel.onBookmarksSheetToggled(false) },
            onSaveQuote = viewModel::onSaveQuote,
            onJumpToQuote = viewModel::onJumpToQuote,
            onDeleteQuote = viewModel::onDeleteQuote,
            onPageTargetConsumed = viewModel::onPageTargetConsumed,
            onReopenAiResult = onReopenAiResult
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreenContent(
    uiState: ReaderUiState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    hasLastAiResult: Boolean = false,
    onBackClick: () -> Unit,
    onPreviousChapter: (openAtEnd: Boolean) -> Unit,
    onNextChapter: () -> Unit,
    onFontSizeChanged: (Int) -> Unit,
    onThemeModeChanged: (ReaderThemeMode) -> Unit,
    onScrollPositionChanged: (Int, Int, Float?) -> Unit,
    onTranslateSelected: (String) -> Unit,
    onAiExplainSelected: (String, Int, ViewportLayoutSnapshot) -> Unit,
    onBookmarksClick: () -> Unit,
    onDismissBookmarks: () -> Unit,
    onSaveQuote: (String, Int, Int) -> Unit,
    onJumpToQuote: (SavedQuote) -> Unit = {},
    onDeleteQuote: (Long) -> Unit = {},
    onPageTargetConsumed: () -> Unit = {},
    onReopenAiResult: () -> Unit
) {
    val readerColors = LocalExtendedReaderColors.current
    var showFontControls by remember { mutableStateOf(false) }
    var quoteDialogText by remember { mutableStateOf<String?>(null) }
    var pendingQuoteParagraphIndex by remember { mutableIntStateOf(0) }
    var pendingQuoteCharOffset by remember { mutableIntStateOf(0) }

    var highlightedQuoteText by remember { mutableStateOf<String?>(null) }
    var highlightVersion by remember { mutableIntStateOf(0) }

    val baseClipboard = LocalClipboard.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    var activeMenuSession by remember { mutableStateOf<ReaderSelectionMenuSession?>(null) }
    var lastTouchedParagraphIndex by remember { mutableIntStateOf(0) }
    val textLayoutResults = remember { mutableStateMapOf<Int, TextLayoutResult>() }
    val clipboardCapture = remember { SelectionClipboardCapture() }

    LaunchedEffect(highlightVersion) {
        if (highlightedQuoteText != null) {
            delay(2600L)
            highlightedQuoteText = null
        }
    }

    val interceptingClipboard = remember(baseClipboard, clipboardCapture) {
        object : Clipboard {
            override val nativeClipboard: NativeClipboard
                get() = baseClipboard.nativeClipboard

            override suspend fun getClipEntry(): ClipEntry? = baseClipboard.getClipEntry()

            override suspend fun setClipEntry(clipEntry: ClipEntry?) {
                val item = clipEntry?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)
                clipboardCapture.capturedText = item?.text?.toString()
                if (!clipboardCapture.captureOnly) {
                    baseClipboard.setClipEntry(clipEntry)
                }
            }
        }
    }

    val contextMenuToolbarProvider = remember {
        object : TextContextMenuProvider {
            override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
                val hasCopyItem = dataProvider.data().components.any {
                    it is TextContextMenuItem && it.key == TextContextMenuKeys.CopyKey
                }
                if (!hasCopyItem) return

                val session = ReaderSelectionMenuSession(dataProvider)
                activeMenuSession = session
                try {
                    session.awaitClose()
                } finally {
                    if (activeMenuSession === session) {
                        activeMenuSession = null
                    }
                }
            }
        }
    }

    fun consumeSelectedText(
        session: ReaderSelectionMenuSession,
        copyToSystemClipboard: Boolean
    ): String? {
        val copyItem = session.dataProvider.data().components
            .filterIsInstance<TextContextMenuItem>()
            .firstOrNull { it.key == TextContextMenuKeys.CopyKey }

        if (copyItem == null) {
            session.close()
            if (activeMenuSession === session) {
                activeMenuSession = null
            }
            return null
        }

        clipboardCapture.capturedText = null
        clipboardCapture.captureOnly = !copyToSystemClipboard
        try {
            with(copyItem) {
                session.onClick()
            }
        } finally {
            clipboardCapture.captureOnly = false
        }
        if (activeMenuSession === session) {
            activeMenuSession = null
        }
        return clipboardCapture.capturedText
    }

    BackHandler(enabled = showFontControls || activeMenuSession != null || quoteDialogText != null) {
        when {
            quoteDialogText != null -> quoteDialogText = null
            showFontControls -> showFontControls = false
            activeMenuSession != null -> {
                activeMenuSession?.close()
                activeMenuSession = null
            }
        }
    }

    CompositionLocalProvider(
        LocalClipboard provides interceptingClipboard,
        LocalTextContextMenuToolbarProvider provides contextMenuToolbarProvider
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(readerColors.readerBackground)
        ) {
            when (val safeState = uiState) {
                is ReaderUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    }
                }
                is ReaderUiState.Success -> {
                    val density = LocalDensity.current
                    val textMeasurer = rememberTextMeasurer()
                    val pagerCoroutineScope = rememberCoroutineScope()

                    var pages by remember { mutableStateOf<List<ReaderPage>>(emptyList()) }
                    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })

                    val latestActiveMenuSession by rememberUpdatedState(activeMenuSession)
                    val latestShowFontControls by rememberUpdatedState(showFontControls)
                    val latestQuoteDialogOpen by rememberUpdatedState(quoteDialogText != null)
                    val latestBookmarksOpen by rememberUpdatedState(safeState.isBookmarksSheetVisible)
                    val latestIsFirstChapter by rememberUpdatedState(safeState.isFirstChapter)
                    val latestIsLastChapter by rememberUpdatedState(safeState.isLastChapter)
                    val latestPages by rememberUpdatedState(pages)

                    // 1. Horizontal Paged Reader Area
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(top = 64.dp, bottom = 76.dp)
                            .pointerInput(safeState.chapter.chapterIndex) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val selectionActiveAtDown = latestActiveMenuSession != null
                                    val overlayOpenAtDown = latestShowFontControls ||
                                        latestQuoteDialogOpen ||
                                        latestBookmarksOpen
                                    val downTime = System.currentTimeMillis()
                                    val downPos = down.position
                                    val up = waitForUpOrCancellation()
                                    if (up != null &&
                                        !up.isConsumed &&
                                        !selectionActiveAtDown &&
                                        !overlayOpenAtDown
                                    ) {
                                        val elapsedMs = System.currentTimeMillis() - downTime
                                        val distance = (up.position - downPos).getDistance()
                                        if (elapsedMs < viewConfiguration.longPressTimeoutMillis &&
                                            distance <= viewConfiguration.touchSlop
                                        ) {
                                            val tapX = up.position.x
                                            val leftThreshold = size.width * 0.35f
                                            val rightThreshold = size.width * 0.65f
                                            val currentPages = latestPages
                                            when {
                                                tapX < leftThreshold -> {
                                                    pagerCoroutineScope.launch {
                                                        if (pagerState.currentPage > 0) {
                                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                        } else if (!latestIsFirstChapter) {
                                                            onPreviousChapter(true)
                                                        }
                                                    }
                                                }
                                                tapX > rightThreshold -> {
                                                    pagerCoroutineScope.launch {
                                                        if (pagerState.currentPage < currentPages.lastIndex) {
                                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                        } else if (!latestIsLastChapter) {
                                                            onNextChapter()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        val pageWidthPx = (constraints.maxWidth - with(density) { 48.dp.roundToPx() })
                            .coerceAtLeast(1)
                        val pageHeightPx = (constraints.maxHeight - with(density) { 28.dp.roundToPx() })
                            .coerceAtLeast(1)

                        val measuredPages = remember(
                            safeState.chapter.chapterIndex,
                            safeState.chapter.paragraphs,
                            safeState.chapter.title,
                            safeState.fontSp,
                            pageWidthPx,
                            pageHeightPx
                        ) {
                            paginateChapter(
                                chapter = safeState.chapter,
                                fontSp = safeState.fontSp,
                                textMeasurer = textMeasurer,
                                density = density,
                                pageWidthPx = pageWidthPx,
                                pageHeightPx = pageHeightPx
                            )
                        }

                        LaunchedEffect(measuredPages) {
                            pages = measuredPages
                            textLayoutResults.clear()
                        }

                        LaunchedEffect(safeState.chapter.chapterIndex, measuredPages, safeState.pendingPageTarget) {
                            val target = safeState.pendingPageTarget ?: return@LaunchedEffect
                            if (measuredPages.isEmpty()) return@LaunchedEffect

                            val targetPage = when (target) {
                                is PageNavigationTarget.LastPageOfChapter -> measuredPages.lastIndex
                                is PageNavigationTarget.Position -> findPageIndexForPosition(
                                    pages = measuredPages,
                                    paragraphIndex = target.paragraphIndex,
                                    charOffset = target.charOffset
                                )
                            }.coerceIn(0, measuredPages.lastIndex)

                            if (pagerState.currentPage != targetPage) {
                                pagerState.scrollToPage(targetPage)
                            }

                            if (target is PageNavigationTarget.Position && !target.highlightText.isNullOrBlank()) {
                                highlightedQuoteText = target.highlightText
                                highlightVersion++
                            }
                            onPageTargetConsumed()
                        }

                        val isNavigationPending = safeState.pendingPageTarget != null
                        LaunchedEffect(pagerState, measuredPages, safeState.chapter.chapterIndex, isNavigationPending) {
                            if (isNavigationPending || measuredPages.isEmpty()) return@LaunchedEffect
                            snapshotFlow { pagerState.settledPage }
                                .distinctUntilChanged()
                                .collect { pageIdx ->
                                    val page = measuredPages.getOrNull(pageIdx) ?: return@collect
                                    val firstItem = page.items.firstOrNull()
                                    val pIndex = firstItem?.paragraphIndex ?: 0
                                    val cOffset = firstItem?.startCharOffset ?: 0
                                    val chapterFraction = if (measuredPages.size <= 1) {
                                        0f
                                    } else {
                                        pageIdx.toFloat() / measuredPages.size.toFloat()
                                    }
                                    onScrollPositionChanged(pIndex, cOffset, chapterFraction)
                                }
                        }

                        if (measuredPages.isNotEmpty()) {
                            HorizontalPager(
                                state = pagerState,
                                key = { pageIdx ->
                                    "${safeState.chapter.chapterIndex}_${safeState.fontSp}_$pageIdx"
                                },
                                beyondViewportPageCount = 1,
                                modifier = Modifier.fillMaxSize()
                            ) { pageIdx ->
                                val page = measuredPages.getOrNull(pageIdx)
                                if (page != null) {
                                    SelectionContainer(modifier = Modifier.fillMaxSize()) {
                                        ReaderPageContent(
                                            chapterTitle = safeState.chapter.title,
                                            page = page,
                                            fontSp = safeState.fontSp,
                                            textColor = readerColors.readerContent,
                                            highlightedQuoteText = highlightedQuoteText,
                                            onParagraphTouched = { touchedIndex ->
                                                lastTouchedParagraphIndex = touchedIndex
                                            },
                                            onTextLayout = { paragraphIndex, layoutResult ->
                                                if (pageIdx == pagerState.currentPage) {
                                                    textLayoutResults[paragraphIndex] = layoutResult
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Top Header (rendered ABOVE HorizontalPager/SelectionContainer so clicks always work)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .size(40.dp)
                                .background(readerColors.readerContent.copy(alpha = 0.08f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                                tint = readerColors.readerContent
                            )
                        }

                        Text(
                            text = safeState.book.title.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.sp,
                            color = readerColors.readerContent.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            textAlign = TextAlign.Center
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = {
                                    if (hasLastAiResult) {
                                        onReopenAiResult()
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                resources.getString(R.string.reader_ai_hint)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .alpha(if (hasLastAiResult) 1f else 0.6f)
                                    .background(readerColors.readerContent.copy(alpha = 0.08f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = stringResource(R.string.cd_ai_assistant),
                                    tint = readerColors.readerContent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(readerColors.readerContent.copy(alpha = 0.08f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.cd_close),
                                    tint = readerColors.readerContent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // 3. Footer Navigation
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                            .align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBookmarksClick,
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFF161618), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = stringResource(R.string.cd_bookmarks),
                                tint = Color.White
                            )
                        }

                        val progressPercent = safeState.currentPosition.scrollProgressPercentage
                            .toInt()
                            .coerceIn(0, 100)
                        val progressFraction = (safeState.currentPosition.scrollProgressPercentage / 100f)
                            .coerceIn(0f, 1f)
                        val chapterIndexText = stringResource(
                            R.string.reader_chapter_progress,
                            safeState.chapter.chapterIndex + 1,
                            safeState.totalChapters
                        )

                        Row(
                            modifier = Modifier
                                .background(Color(0xFF161618), RoundedCornerShape(24.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { onPreviousChapter(false) },
                                enabled = !safeState.isFirstChapter,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.cd_prev_chapter),
                                    tint = if (!safeState.isFirstChapter) {
                                        Color.White.copy(alpha = 0.85f)
                                    } else {
                                        Color.White.copy(alpha = 0.3f)
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(
                                    modifier = Modifier.width(110.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "$progressPercent%",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = chapterIndexText,
                                        fontSize = 9.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .width(110.dp)
                                        .height(4.dp)
                                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(progressFraction)
                                            .background(Color.White, CircleShape)
                                    )
                                }
                            }

                            IconButton(
                                onClick = onNextChapter,
                                enabled = !safeState.isLastChapter,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = stringResource(R.string.cd_next_chapter),
                                    tint = if (!safeState.isLastChapter) {
                                        Color.White.copy(alpha = 0.85f)
                                    } else {
                                        Color.White.copy(alpha = 0.3f)
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { showFontControls = !showFontControls },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFF161618), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = stringResource(R.string.cd_settings),
                                tint = Color.White
                            )
                        }
                    }

                    // 4. Font & Theme Control Overlay + Tap-Outside Scrim
                    if (showFontControls) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    showFontControls = false
                                }
                        )
                        FontAndThemeControlOverlay(
                            fontSp = safeState.fontSp,
                            currentMode = safeState.readerThemeMode,
                            onFontSizeChanged = onFontSizeChanged,
                            onThemeModeChanged = onThemeModeChanged,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .navigationBarsPadding()
                                .padding(bottom = 72.dp, end = 24.dp)
                        )
                    }

                    // 5. Floating Selection Toolbar
                    val currentMenuSession = activeMenuSession
                    if (currentMenuSession != null) {
                        SelectionFloatingToolbar(
                            onTranslateClick = {
                                val text = consumeSelectedText(
                                    session = currentMenuSession,
                                    copyToSystemClipboard = false
                                )
                                if (!text.isNullOrBlank()) {
                                    onTranslateSelected(text)
                                }
                            },
                            onAiClick = {
                                val text = consumeSelectedText(
                                    session = currentMenuSession,
                                    copyToSystemClipboard = false
                                )
                                if (!text.isNullOrBlank()) {
                                    val activePage = pages.getOrNull(pagerState.currentPage)
                                    val snapshot = buildPageViewportLayoutSnapshot(
                                        chapterTitle = safeState.chapter.title,
                                        page = activePage,
                                        textLayoutResults = textLayoutResults
                                    )
                                    onAiExplainSelected(text, lastTouchedParagraphIndex, snapshot)
                                }
                            },
                            onCopyClick = {
                                val text = consumeSelectedText(
                                    session = currentMenuSession,
                                    copyToSystemClipboard = true
                                )
                                if (!text.isNullOrBlank()) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            resources.getString(R.string.reader_copied)
                                        )
                                    }
                                }
                            },
                            onQuoteClick = {
                                val text = consumeSelectedText(
                                    session = currentMenuSession,
                                    copyToSystemClipboard = false
                                )
                                if (!text.isNullOrBlank()) {
                                    val activePage = pages.getOrNull(pagerState.currentPage)
                                    val (pIdx, cOff) = if (activePage != null) {
                                        findQuoteLocationInPage(
                                            quoteText = text,
                                            page = activePage,
                                            preferredParagraphIndex = lastTouchedParagraphIndex
                                        )
                                    } else {
                                        lastTouchedParagraphIndex to 0
                                    }
                                    pendingQuoteParagraphIndex = pIdx
                                    pendingQuoteCharOffset = cOff
                                    quoteDialogText = text
                                }
                            },
                            onShareClick = {
                                val text = consumeSelectedText(
                                    session = currentMenuSession,
                                    copyToSystemClipboard = false
                                )
                                if (!text.isNullOrBlank()) {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    val chooser = Intent.createChooser(
                                        sendIntent,
                                        resources.getString(R.string.cd_share)
                                    )
                                    context.startActivity(chooser)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 80.dp)
                        )
                    }

                    val pendingQuote = quoteDialogText
                    if (pendingQuote != null) {
                        QuoteCardModal(
                            quoteText = pendingQuote,
                            author = safeState.book.author
                                ?: stringResource(R.string.currently_reading_unknown_author),
                            source = safeState.book.title,
                            onSaveClick = {
                                onSaveQuote(
                                    pendingQuote,
                                    pendingQuoteParagraphIndex,
                                    pendingQuoteCharOffset
                                )
                                quoteDialogText = null
                            },
                            onAskAiClick = {
                                val activePage = pages.getOrNull(pagerState.currentPage)
                                val snapshot = buildPageViewportLayoutSnapshot(
                                    chapterTitle = safeState.chapter.title,
                                    page = activePage,
                                    textLayoutResults = textLayoutResults
                                )
                                onAiExplainSelected(
                                    pendingQuote,
                                    pendingQuoteParagraphIndex,
                                    snapshot
                                )
                                quoteDialogText = null
                            },
                            onDismiss = { quoteDialogText = null }
                        )
                    }

                    if (safeState.isBookmarksSheetVisible) {
                        ModalBottomSheet(onDismissRequest = onDismissBookmarks) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 16.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.reader_bookmarks_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                if (safeState.savedQuotes.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.reader_bookmarks_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(
                                            items = safeState.savedQuotes,
                                            key = { quote -> "${quote.id}_${quote.chapterIndex}_${quote.paragraphIndex}_${quote.quoteText.hashCode()}" }
                                        ) { quote ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onJumpToQuote(quote) }
                                                    .padding(vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = quote.quoteText,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        maxLines = 3,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = stringResource(
                                                            R.string.reader_quote_location,
                                                            quote.chapterIndex + 1,
                                                            quote.chapterTitle
                                                        ),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                IconButton(onClick = { onDeleteQuote(quote.id) }) {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteOutline,
                                                        contentDescription = stringResource(R.string.cd_delete_quote),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            HorizontalDivider()
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                }
                is ReaderUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = safeState.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = onBackClick) {
                                Text(stringResource(R.string.reader_return_to_library))
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun ReaderPageContent(
    chapterTitle: String,
    page: ReaderPage,
    fontSp: Int,
    textColor: Color,
    highlightedQuoteText: String?,
    onParagraphTouched: (Int) -> Unit,
    onTextLayout: (Int, TextLayoutResult) -> Unit,
    fontFamily: FontFamily = FontFamily.Serif
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        if (page.showChapterHeader) {
            Text(
                text = chapterTitle,
                style = readerChapterTitleTextStyle().copy(color = textColor),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(1.dp)
                        .background(textColor.copy(alpha = 0.4f))
                )
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(16.dp),
                    tint = textColor.copy(alpha = 0.4f)
                )
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(1.dp)
                        .background(textColor.copy(alpha = 0.4f))
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        val headerStyle = remember(fontSp, fontFamily, textColor) {
            readerParagraphHeaderTextStyle(fontSp, fontFamily).copy(color = textColor)
        }
        val bodyStyle = remember(fontSp, fontFamily, textColor) {
            readerBodyTextStyle(fontSp, fontFamily).copy(color = textColor)
        }

        page.items.forEachIndexed { idx, item ->
            val touchTrackerModifier = Modifier.pointerInput(item.paragraphIndex) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed }) {
                            onParagraphTouched(item.paragraphIndex)
                        }
                    }
                }
            }

            val styledText = remember(item.text, highlightedQuoteText) {
                buildHighlightedText(item.text, highlightedQuoteText)
            }

            if (item.isHeader) {
                Text(
                    text = styledText,
                    style = headerStyle,
                    onTextLayout = { layoutResult -> onTextLayout(item.paragraphIndex, layoutResult) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(touchTrackerModifier)
                        .padding(vertical = 4.dp)
                )
            } else {
                Text(
                    text = styledText,
                    style = bodyStyle,
                    onTextLayout = { layoutResult -> onTextLayout(item.paragraphIndex, layoutResult) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(touchTrackerModifier)
                )
            }

            if (idx < page.items.lastIndex) {
                Spacer(modifier = Modifier.height(18.dp))
            }
        }

        if (page.showEndOfChapter) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.reader_end_of_chapter),
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                color = textColor.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun buildHighlightedText(
    text: String,
    highlightTarget: String?
): AnnotatedString {
    if (highlightTarget.isNullOrBlank()) return AnnotatedString(text)

    val trimmed = highlightTarget.trim()
    val candidates = buildList {
        add(trimmed)
        trimmed.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { add(it) }
    }

    var matchStart = -1
    var matchLength = 0
    for (candidate in candidates) {
        val idx = text.indexOf(candidate, ignoreCase = true)
        if (idx != -1) {
            matchStart = idx
            matchLength = candidate.length
            break
        }
    }

    if (matchStart == -1 || matchLength <= 0) return AnnotatedString(text)

    return buildAnnotatedString {
        append(text)
        addStyle(
            style = SpanStyle(background = Color(0x66FACC15)),
            start = matchStart,
            end = (matchStart + matchLength).coerceAtMost(text.length)
        )
    }
}

private fun buildPageViewportLayoutSnapshot(
    chapterTitle: String?,
    page: ReaderPage?,
    textLayoutResults: Map<Int, TextLayoutResult>
): ViewportLayoutSnapshot {
    if (page == null) {
        return ViewportLayoutSnapshot(
            viewportTopY = 0f,
            viewportBottomY = 1000f,
            chapterTitle = chapterTitle,
            paragraphs = emptyList()
        )
    }

    var runningTopY = 0f
    val paragraphLayouts = mutableListOf<ParagraphLayoutInfo>()

    for (item in page.items) {
        val textLayout = textLayoutResults[item.paragraphIndex]
        val itemHeight = textLayout?.size?.height?.toFloat() ?: 100f
        val itemTopY = runningTopY
        val itemBottomY = itemTopY + itemHeight

        val visibleSentences = item.originalParagraph.sentences.filter { sentence ->
            sentence.startOffset < item.endCharOffset && sentence.endOffset > item.startCharOffset
        }

        val sentenceLayouts = visibleSentences.map { sentence ->
            if (textLayout != null) {
                val localStart = (sentence.startOffset - item.startCharOffset)
                    .coerceIn(0, textLayout.layoutInput.text.length)
                val localEnd = (sentence.endOffset - item.startCharOffset)
                    .coerceIn(0, textLayout.layoutInput.text.length)
                val startLine = textLayout.getLineForOffset(localStart)
                val endLine = textLayout.getLineForOffset((localEnd - 1).coerceAtLeast(localStart))
                SentenceLayoutInfo(
                    sentence = sentence,
                    topY = itemTopY + textLayout.getLineTop(startLine),
                    bottomY = itemTopY + textLayout.getLineBottom(endLine)
                )
            } else {
                SentenceLayoutInfo(
                    sentence = sentence,
                    topY = itemTopY,
                    bottomY = itemBottomY
                )
            }
        }

        paragraphLayouts.add(
            ParagraphLayoutInfo(
                paragraphIndex = item.paragraphIndex,
                text = item.originalParagraph.text,
                topY = itemTopY,
                bottomY = itemBottomY,
                sentences = sentenceLayouts
            )
        )
        runningTopY = itemBottomY + 24f
    }

    return ViewportLayoutSnapshot(
        viewportTopY = 0f,
        viewportBottomY = runningTopY.coerceAtLeast(1f),
        chapterTitle = chapterTitle,
        paragraphs = paragraphLayouts
    )
}

@Composable
private fun FontAndThemeControlOverlay(
    fontSp: Int,
    currentMode: ReaderThemeMode,
    onFontSizeChanged: (Int) -> Unit,
    onThemeModeChanged: (ReaderThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.reader_font_size, fontSp),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onFontSizeChanged(fontSp - 2) },
                    enabled = fontSp > 12,
                    modifier = Modifier.size(48.dp)
                ) {
                    Text(
                        text = stringResource(R.string.reader_font_decrease),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(
                    onClick = { onFontSizeChanged(fontSp + 2) },
                    enabled = fontSp < 32,
                    modifier = Modifier.size(48.dp)
                ) {
                    Text(
                        text = stringResource(R.string.reader_font_increase),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeSwatchButton(
                    selected = currentMode == ReaderThemeMode.LIGHT,
                    tint = Color.LightGray,
                    contentDescription = stringResource(R.string.cd_theme_light),
                    onClick = { onThemeModeChanged(ReaderThemeMode.LIGHT) }
                )
                ThemeSwatchButton(
                    selected = currentMode == ReaderThemeMode.SEPIA,
                    tint = Color(0xFF8B4513),
                    contentDescription = stringResource(R.string.cd_theme_sepia),
                    onClick = { onThemeModeChanged(ReaderThemeMode.SEPIA) }
                )
                ThemeSwatchButton(
                    selected = currentMode == ReaderThemeMode.GREY,
                    tint = Color.Gray,
                    contentDescription = stringResource(R.string.cd_theme_grey),
                    onClick = { onThemeModeChanged(ReaderThemeMode.GREY) }
                )
                ThemeSwatchButton(
                    selected = currentMode == ReaderThemeMode.DARK,
                    tint = Color.Black,
                    contentDescription = stringResource(R.string.cd_theme_dark),
                    onClick = { onThemeModeChanged(ReaderThemeMode.DARK) }
                )
            }
        }
    }
}

@Composable
private fun ThemeSwatchButton(
    selected: Boolean,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        color = Color.Transparent,
        modifier = Modifier.size(48.dp)
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = contentDescription,
                tint = tint
            )
        }
    }
}

private fun sampleReaderSuccessState(themeMode: ReaderThemeMode): ReaderUiState.Success {
    val p1Text = "In my younger and more vulnerable years my father gave me some advice that I've been turning over in my mind ever since."
    val p2Text = "Whenever you feel like criticizing any one, he told me, just remember that all the people in this world haven't had the advantages that you've had."
    return ReaderUiState.Success(
        book = Book("sha1", "The Great Gatsby", "F. Scott Fitzgerald", "/tmp/gatsby.epub"),
        chapter = ChapterContent(
            chapterIndex = 0,
            title = "Chapter 1",
            paragraphs = listOf(
                ParagraphBlock(
                    paragraphIndex = 0,
                    xhtmlId = "p-0",
                    text = p1Text,
                    sentences = listOf(SentenceSpan(0, p1Text, 0, p1Text.length))
                ),
                ParagraphBlock(
                    paragraphIndex = 1,
                    xhtmlId = "p-1",
                    text = p2Text,
                    sentences = listOf(SentenceSpan(0, p2Text, 0, p2Text.length))
                )
            )
        ),
        currentPosition = ReadingPosition("sha1", 0, "/6/2!/4/2/1:0", 0, 0, 25f),
        totalChapters = 9,
        fontSp = 16,
        readerThemeMode = themeMode,
        isFirstChapter = true,
        isLastChapter = false
    )
}

@Composable
private fun ReaderThemePreviewTemplate(themeMode: ReaderThemeMode) {
    DeepReaderTheme(readerThemeMode = themeMode) {
        ReaderScreenContent(
            uiState = sampleReaderSuccessState(themeMode),
            hasLastAiResult = true,
            onBackClick = {},
            onPreviousChapter = {},
            onNextChapter = {},
            onFontSizeChanged = {},
            onThemeModeChanged = {},
            onScrollPositionChanged = { _, _, _ -> },
            onTranslateSelected = {},
            onAiExplainSelected = { _, _, _ -> },
            onBookmarksClick = {},
            onDismissBookmarks = {},
            onSaveQuote = { _, _, _ -> },
            onReopenAiResult = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReaderScreenLightPreview() {
    ReaderThemePreviewTemplate(ReaderThemeMode.LIGHT)
}

@Preview(showBackground = true)
@Composable
private fun ReaderScreenSepiaPreview() {
    ReaderThemePreviewTemplate(ReaderThemeMode.SEPIA)
}
