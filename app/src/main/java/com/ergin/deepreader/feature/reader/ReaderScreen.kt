package com.ergin.deepreader.feature.reader

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ergin.deepreader.core.designsystem.theme.LocalExtendedReaderColors
import com.ergin.deepreader.domain.model.ParagraphBlock
import com.ergin.deepreader.ui.theme.DeepReaderTheme
import com.ergin.deepreader.ui.theme.ReaderThemeMode

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    bookSha256: String,
    onBackClick: () -> Unit
) {
    LaunchedEffect(bookSha256) {
        viewModel.loadBook(bookSha256)
    }

    val uiState by viewModel.uiState.collectAsState()

    val currentThemeMode = (uiState as? ReaderUiState.Success)?.readerThemeMode

    DeepReaderTheme(readerThemeMode = currentThemeMode) {
        ReaderScreenContent(
            uiState = uiState,
            onBackClick = onBackClick,
            onPreviousChapter = { viewModel.onPreviousChapter() },
            onNextChapter = { viewModel.onNextChapter() },
            onFontSizeChanged = { viewModel.onFontSizeChanged(it) },
            onThemeModeChanged = { viewModel.onThemeModeChanged(it) },
            onScrollPositionChanged = { pIndex, charOffset ->
                viewModel.onScrollPositionChanged(pIndex, charOffset)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScreenContent(
    uiState: ReaderUiState,
    onBackClick: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onFontSizeChanged: (Int) -> Unit,
    onThemeModeChanged: (ReaderThemeMode) -> Unit,
    onScrollPositionChanged: (Int, Int) -> Unit
) {
    val readerColors = LocalExtendedReaderColors.current
    var showFontControls by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (uiState as? ReaderUiState.Success)?.book?.title ?: "Чтение"
                    Text(title, maxLines = 1)
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showFontControls = !showFontControls },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.FormatSize, contentDescription = "Настройка шрифта")
                    }
                }
            )
        },
        bottomBar = {
            if (uiState is ReaderUiState.Success) {
                BottomAppBar(
                    modifier = Modifier.height(64.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onPreviousChapter,
                            enabled = !uiState.isFirstChapter,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Предыдущая глава")
                        }

                        Text(
                            text = uiState.chapter.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = onNextChapter,
                            enabled = !uiState.isLastChapter,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Следующая глава")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(readerColors.readerBackground)
        ) {
            when (uiState) {
                is ReaderUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    }
                }
                is ReaderUiState.Success -> {
                    val lazyListState = rememberLazyListState(
                        initialFirstVisibleItemIndex = uiState.currentPosition.paragraphIndex
                    )

                    LaunchedEffect(lazyListState.firstVisibleItemIndex) {
                        onScrollPositionChanged(lazyListState.firstVisibleItemIndex, 0)
                    }

                    SelectionContainer {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            items(
                                items = uiState.chapter.paragraphs,
                                key = { it.paragraphIndex }
                            ) { paragraph ->
                                ParagraphItem(
                                    paragraph = paragraph,
                                    fontSp = uiState.fontSp,
                                    textColor = readerColors.readerContent
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }

                    if (showFontControls) {
                        FontAndThemeControlOverlay(
                            fontSp = uiState.fontSp,
                            currentMode = uiState.readerThemeMode,
                            onFontSizeChanged = onFontSizeChanged,
                            onThemeModeChanged = onThemeModeChanged,
                            onDismiss = { showFontControls = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                        )
                    }
                }
                is ReaderUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = uiState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParagraphItem(
    paragraph: ParagraphBlock,
    fontSp: Int,
    textColor: androidx.compose.ui.graphics.Color
) {
    Text(
        text = paragraph.text,
        fontSize = fontSp.sp,
        lineHeight = (fontSp * 1.5).sp,
        color = textColor,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FontAndThemeControlOverlay(
    fontSp: Int,
    currentMode: ReaderThemeMode,
    onFontSizeChanged: (Int) -> Unit,
    onThemeModeChanged: (ReaderThemeMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Размер шрифта: $fontSp sp", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onFontSizeChanged(fontSp - 2) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Text("-", style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(
                    onClick = { onFontSizeChanged(fontSp + 2) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Text("+", style = MaterialTheme.typography.headlineSmall)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { onThemeModeChanged(ReaderThemeMode.LIGHT) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = "Светлая тема", tint = androidx.compose.ui.graphics.Color.LightGray)
                }
                IconButton(
                    onClick = { onThemeModeChanged(ReaderThemeMode.SEPIA) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = "Сепия", tint = androidx.compose.ui.graphics.Color(0xFF8B4513))
                }
                IconButton(
                    onClick = { onThemeModeChanged(ReaderThemeMode.DARK) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = "Тёмная тема", tint = androidx.compose.ui.graphics.Color.Black)
                }
            }
        }
    }
}