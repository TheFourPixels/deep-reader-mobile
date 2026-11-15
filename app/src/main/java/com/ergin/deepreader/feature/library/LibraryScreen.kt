package com.ergin.deepreader.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ergin.deepreader.domain.model.Book

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onAddBookClick: () -> Unit,
    onBookClick: (Book) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val dialogState by viewModel.dialogState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Библиотека DeepReader") }
            )
        },
        floatingActionButton = {
            if (uiState !is LibraryUiState.Empty && uiState !is LibraryUiState.Importing) {
                FloatingActionButton(
                    onClick = onAddBookClick,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Добавить книгу")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is LibraryUiState.Empty -> {
                    EmptyLibraryContent(onAddBookClick = onAddBookClick)
                }
                is LibraryUiState.Importing -> {
                    ImportingContent()
                }
                is LibraryUiState.Content -> {
                    BookGridContent(
                        books = state.books,
                        onBookClick = { book ->
                            viewModel.onBookClicked(book)
                            onBookClick(book)
                        }
                    )
                }
                is LibraryUiState.Error -> {
                    ErrorContent(message = state.message, onRetryClick = onAddBookClick)
                }
            }
        }

        // Dialogs
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Book,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Ваша библиотека пуста",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Добавьте книги в формате EPUB для чтения и AI-анализа",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAddBookClick,
            modifier = Modifier.height(56.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("Добавить книгу EPUB", style = MaterialTheme.typography.titleMedium)
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
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Импорт книги и проверка формата...",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun BookGridContent(
    books: List<Book>,
    onBookClick: (Book) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(books, key = { it.sha256 }) { book ->
            BookItemCard(book = book, onClick = { onBookClick(book) })
        }
    }
}

@Composable
private fun BookItemCard(
    book: Book,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Book,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!book.author.isNullOrBlank()) {
                    Text(
                        text = book.author.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetryClick) {
            Text("Попробовать снова")
        }
    }
}