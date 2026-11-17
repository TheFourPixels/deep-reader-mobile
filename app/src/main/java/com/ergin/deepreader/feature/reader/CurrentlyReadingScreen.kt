package com.ergin.deepreader.feature.reader

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ergin.deepreader.R
import com.ergin.deepreader.domain.model.Book
import com.ergin.deepreader.ui.components.DockScreen
import com.ergin.deepreader.ui.components.FloatingDock
import com.ergin.deepreader.ui.theme.DeepReaderTheme
import kotlin.math.abs

@Immutable
data class CurrentlyReadingBook(
    val id: String,
    val title: String,
    val author: String,
    val year: Int,
    val synopsis: String,
    val readPages: Int,
    val totalPages: Int,
    val coverPath: String? = null,
    val coverGradient: List<Color> = listOf(Color(0xFF1E293B), Color(0xFF0F172A)),
    val categoryBadge: String = "DEEP READER",
    val filePath: String = ""
) {
    val progressPercentage: Int
        get() = if (totalPages > 0) ((readPages.toFloat() / totalPages) * 100).toInt() else 0
}

fun Book.toCurrentlyReadingBook(
    unknownAuthor: String,
    defaultSynopsis: String
): CurrentlyReadingBook {
    val progress = progressPercentage.toInt().coerceIn(0, 100)
    return CurrentlyReadingBook(
        id = sha256,
        title = title,
        author = author ?: unknownAuthor,
        year = 2024,
        synopsis = defaultSynopsis,
        readPages = progress,
        totalPages = 100,
        coverPath = coverPath,
        filePath = filePath
    )
}

@Composable
fun CurrentlyReadingScreen(
    books: List<CurrentlyReadingBook>,
    currentDockScreen: DockScreen = DockScreen.READER,
    onDockScreenSelected: (DockScreen) -> Unit = {},
    onContinueClick: (CurrentlyReadingBook) -> Unit = {},
    onGoToLibraryClick: () -> Unit = { onDockScreenSelected(DockScreen.LIBRARY) },
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState { books.size }
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F7))
    ) {
        if (books.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.library_empty_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF141414),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.library_empty_subtitle),
                    fontSize = 14.sp,
                    color = Color(0xFF78716C),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onGoToLibraryClick) {
                    Text(stringResource(R.string.action_go_to_library))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(scrollState)
                    .padding(bottom = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD6D3D1))
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.currently_reading_title),
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF141414),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.currently_reading_books_count, books.size),
                        fontSize = 14.sp,
                        color = Color(0xFF78716C),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 96.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(290.dp)
                ) { page ->
                    val rawOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    val pageOffset = abs(rawOffset).coerceIn(0f, 1f)

                    val scale = 0.85f + (1f - pageOffset) * 0.15f
                    val alpha = 0.85f + (1f - pageOffset) * 0.15f

                    val book = books[page]

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        BookCover3DCard(
                            book = book,
                            scale = scale,
                            alpha = alpha,
                            elevationDp = (16 * (1f - pageOffset)).dp,
                            onClick = { onContinueClick(book) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(books.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 20.dp else 6.dp,
                            animationSpec = tween(durationMillis = 250),
                            label = "dotWidth"
                        )
                        val color by animateColorAsState(
                            targetValue = if (isSelected) Color(0xFF141414) else Color(0xFFD6D3D1),
                            animationSpec = tween(durationMillis = 250),
                            label = "dotColor"
                        )

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .height(6.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val activeBook = books.getOrNull(pagerState.currentPage) ?: books.first()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = activeBook.title.uppercase(),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF141414),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${activeBook.author} • ${activeBook.year}",
                        fontSize = 14.sp,
                        color = Color(0xFF78716C),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = activeBook.synopsis,
                        fontSize = 13.5.sp,
                        color = Color(0xFF57534E),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF141414))
                            )
                            Text(
                                text = stringResource(R.string.currently_reading_progress, activeBook.progressPercentage),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF141414)
                            )
                        }

                        Text(
                            text = stringResource(R.string.currently_reading_pages, activeBook.readPages, activeBook.totalPages),
                            fontSize = 13.sp,
                            color = Color(0xFF78716C)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE7E5E4))
                    ) {
                        val progressFraction = (activeBook.readPages.toFloat() / activeBook.totalPages.coerceAtLeast(1)).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = progressFraction)
                                .clip(CircleShape)
                                .background(Color(0xFF1C1C1E))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Surface(
                    onClick = { onContinueClick(activeBook) },
                    shape = CircleShape,
                    color = Color(0xFF141414),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(56.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.width(36.dp))

                        Text(
                            text = stringResource(R.string.currently_reading_continue),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.currently_reading_continue),
                                tint = Color(0xFF141414),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        FloatingDock(
            selectedScreen = currentDockScreen,
            onScreenSelected = onDockScreenSelected,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun BookCover3DCard(
    book: CurrentlyReadingBook,
    scale: Float,
    alpha: Float,
    elevationDp: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        shadowElevation = elevationDp,
        modifier = modifier
            .width(196.dp)
            .height(280.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!book.coverPath.isNullOrBlank()) {
                AsyncImage(
                    model = book.coverPath,
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = book.coverGradient
                            )
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = book.categoryBadge.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.6f),
                            letterSpacing = 1.5.sp,
                            textAlign = TextAlign.Center
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = book.title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 22.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .height(1.dp)
                                    .background(Color.White.copy(alpha = 0.4f))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = book.author,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = "DEEP READER • ${book.year}",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.5f),
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(20.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 3.dp)
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CurrentlyReadingScreenPreview() {
    DeepReaderTheme {
        CurrentlyReadingScreen(
            books = listOf(
                CurrentlyReadingBook(
                    id = "1",
                    title = "The Nickel Boys",
                    author = "Colson Whitehead",
                    year = 2023,
                    synopsis = "A dramatic story of survival and resilience.",
                    readPages = 168,
                    totalPages = 400
                )
            )
        )
    }
}
