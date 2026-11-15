package com.ergin.deepreader.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ergin.deepreader.domain.model.Book
import com.ergin.deepreader.ui.components.DockScreen
import com.ergin.deepreader.ui.components.FloatingDock
import com.ergin.deepreader.ui.theme.ScreenBackground
import com.ergin.deepreader.ui.theme.SubSurfaceWell
import com.ergin.deepreader.ui.theme.TextPrimary
import com.ergin.deepreader.ui.theme.TextSecondaryMuted
import com.ergin.deepreader.ui.theme.TextSubtlePlaceholder

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onAddBookClick: () -> Unit,
    onBookClick: (Book) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val dialogState by viewModel.dialogState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedDockScreen by remember { mutableStateOf(DockScreen.LIBRARY) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // 1. Header "Мои книги"
            Text(
                text = "Мои книги",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Search / Filter row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Поиск книг...",
                            color = TextSubtlePlaceholder,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Поиск",
                            tint = TextSecondaryMuted
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier.size(52.dp)
                ) {
                    IconButton(onClick = { /* Filter action */ }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Фильтр",
                            tint = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Subheader "Все"
            Text(
                text = "Все",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Content Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (val state = uiState) {
                    is LibraryUiState.Empty -> {
                        EmptyLibraryContent(onAddBookClick = onAddBookClick)
                    }
                    is LibraryUiState.Importing -> {
                        ImportingContent()
                    }
                    is LibraryUiState.Content -> {
                        val filteredBooks = if (searchQuery.isBlank()) {
                            state.books
                        } else {
                            state.books.filter {
                                it.title.contains(searchQuery, ignoreCase = true) ||
                                        (it.author?.contains(searchQuery, ignoreCase = true) == true)
                            }
                        }

                        if (filteredBooks.isEmpty()) {
                            EmptyLibraryContent(onAddBookClick = onAddBookClick)
                        } else {
                            BookGridContent(
                                books = filteredBooks,
                                onBookClick = { book ->
                                    viewModel.onBookClicked(book)
                                    onBookClick(book)
                                }
                            )
                        }
                    }
                    is LibraryUiState.Error -> {
                        ErrorContent(message = state.message, onRetryClick = onAddBookClick)
                    }
                }
            }
        }

        // 5. Floating Dock at bottom center
        FloatingDock(
            selectedScreen = selectedDockScreen,
            onScreenSelected = { selectedDockScreen = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )

        // 6. Dialogs
        when (val dialog = dialogState) {
            is LibraryDialogState.FileMissingError -> {
                AlertDialog(
                    onDismissRequest = { viewModel.onDismissDialog() },
                    icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                    title = { Text("Книга недоступна") },
                    text = { Text("Не удалось открыть книгу «${dialog.bookTitle}». Файл недоступен на устройстве.") },
                    confirmButton = {
                        TextButton(onClick = { viewModel.onDismissDialog() }) {
                            Text("Понятно")
                        }
                    }
                )
            }
            is LibraryDialogState.DuplicateNotice -> {
                AlertDialog(
                    onDismissRequest = { viewModel.onDismissDialog() },
                    title = { Text("Книга уже добавлена") },
                    text = { Text("Книга «${dialog.book.title}» уже есть в вашей библиотеке.") },
                    confirmButton = {
                        Button(onClick = {
                            viewModel.onDismissDialog()
                            onBookClick(dialog.book)
                        }) {
                            Text("Открыть существующую")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.onDismissDialog() }) {
                            Text("Отмена")
                        }
                    }
                )
            }
            is LibraryDialogState.MetadataCollisionNotice -> {
                AlertDialog(
                    onDismissRequest = { viewModel.onDismissDialog() },
                    title = { Text("Книга добавлена") },
                    text = { Text("Добавлено новое издание книги «${dialog.newBook.title}». В библиотеке уже есть совпадающая книга.") },
                    confirmButton = {
                        TextButton(onClick = { viewModel.onDismissDialog() }) {
                            Text("ОК")
                        }
                    }
                )
            }
            LibraryDialogState.None -> {}
        }
    }
}

@Composable
private fun EmptyLibraryContent(onAddBookClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.5.dp, Color.White),
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF1F2F5),
                            Color(0xFFEDF0F4)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.AutoStories,
                            contentDescription = null,
                            tint = TextSecondaryMuted,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Библиотека пуста",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Добавьте первую книгу и выберите для неё группу",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                Surface(
                    onClick = onAddBookClick,
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Добавить книгу",
                            tint = TextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = TextPrimary,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Импорт книги и проверка формата...",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondaryMuted
        )
    }
}

@Composable
private fun BookGridContent(
    books: List<Book>,
    onBookClick: (Book) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(books, key = { it.sha256 }) { book ->
            BookGridCard(book = book, onClick = { onBookClick(book) })
        }
    }
}

@Composable
private fun BookGridCard(
    book: Book,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SubSurfaceWell),
                contentAlignment = Alignment.Center
            ) {
                if (!book.coverPath.isNullOrBlank()) {
                    AsyncImage(
                        model = book.coverPath,
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Book,
                            contentDescription = null,
                            tint = TextSecondaryMuted,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!book.author.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetryClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onRetryClick) {
            Text("Попробовать снова")
        }
    }
}
