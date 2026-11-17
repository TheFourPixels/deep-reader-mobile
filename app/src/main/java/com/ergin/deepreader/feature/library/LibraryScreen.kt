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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ergin.deepreader.R
import com.ergin.deepreader.domain.model.Book
import com.ergin.deepreader.ui.components.DockScreen
import com.ergin.deepreader.ui.components.FloatingDock
import com.ergin.deepreader.ui.theme.DeepReaderTheme
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
    currentDockScreen: DockScreen = DockScreen.LIBRARY,
    onDockScreenSelected: (DockScreen) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LibraryScreenContent(
        uiState = uiState,
        onSearchQueryChange = viewModel::onSearchQueryChanged,
        onSortOrderChange = viewModel::onSortOrderChanged,
        onAddBookClick = onAddBookClick,
        onBookClick = onBookClick,
        onDismissDialog = viewModel::onDismissDialog,
        onDismissError = viewModel::onDismissError,
        currentDockScreen = currentDockScreen,
        onDockScreenSelected = onDockScreenSelected,
        modifier = modifier
    )
}

@Composable
fun LibraryScreenContent(
    uiState: LibraryUiState,
    onSearchQueryChange: (String) -> Unit,
    onSortOrderChange: (LibrarySortOrder) -> Unit,
    onAddBookClick: () -> Unit,
    onBookClick: (Book) -> Unit,
    onDismissDialog: () -> Unit,
    onDismissError: () -> Unit,
    currentDockScreen: DockScreen = DockScreen.LIBRARY,
    onDockScreenSelected: (DockScreen) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.library_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Surface(
                    onClick = onAddBookClick,
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.cd_add_book),
                            tint = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.library_search_placeholder),
                            color = TextSubtlePlaceholder,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.cd_search),
                            tint = TextSecondaryMuted
                        )
                    },
                    trailingIcon = if (uiState.searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.cd_clear_search),
                                    tint = TextSecondaryMuted
                                )
                            }
                        }
                    } else {
                        null
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

                Box {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier.size(52.dp)
                    ) {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = stringResource(R.string.cd_filter),
                                tint = TextPrimary
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortMenuOption(
                            label = stringResource(R.string.library_sort_recent),
                            selected = uiState.sortOrder == LibrarySortOrder.RECENT,
                            onClick = {
                                onSortOrderChange(LibrarySortOrder.RECENT)
                                showSortMenu = false
                            }
                        )
                        SortMenuOption(
                            label = stringResource(R.string.library_sort_title),
                            selected = uiState.sortOrder == LibrarySortOrder.TITLE_ASC,
                            onClick = {
                                onSortOrderChange(LibrarySortOrder.TITLE_ASC)
                                showSortMenu = false
                            }
                        )
                        SortMenuOption(
                            label = stringResource(R.string.library_sort_author),
                            selected = uiState.sortOrder == LibrarySortOrder.AUTHOR_ASC,
                            onClick = {
                                onSortOrderChange(LibrarySortOrder.AUTHOR_ASC)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.library_section_all),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (val contentState = uiState.content) {
                    is LibraryContentState.Empty -> {
                        EmptyLibraryContent(onAddBookClick = onAddBookClick)
                    }
                    is LibraryContentState.Importing -> {
                        ImportingContent()
                    }
                    is LibraryContentState.Content -> {
                        if (contentState.filteredBooks.isEmpty()) {
                            NoSearchResultsContent(
                                onResetSearchClick = { onSearchQueryChange("") }
                            )
                        } else {
                            BookGridContent(
                                books = contentState.filteredBooks,
                                onBookClick = onBookClick
                            )
                        }
                    }
                    is LibraryContentState.Error -> {
                        ErrorContent(
                            message = contentState.message,
                            onRetryClick = onAddBookClick,
                            onDismissClick = onDismissError
                        )
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

        when (val dialog = uiState.dialog) {
            is LibraryDialogState.FileMissingError -> {
                AlertDialog(
                    onDismissRequest = onDismissDialog,
                    icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                    title = { Text(stringResource(R.string.dialog_file_missing_title)) },
                    text = {
                        Text(stringResource(R.string.dialog_file_missing_message, dialog.bookTitle))
                    },
                    confirmButton = {
                        TextButton(onClick = onDismissDialog) {
                            Text(stringResource(R.string.action_ok_got_it))
                        }
                    }
                )
            }
            is LibraryDialogState.DuplicateNotice -> {
                AlertDialog(
                    onDismissRequest = onDismissDialog,
                    title = { Text(stringResource(R.string.dialog_duplicate_title)) },
                    text = {
                        Text(stringResource(R.string.dialog_duplicate_message, dialog.book.title))
                    },
                    confirmButton = {
                        Button(onClick = {
                            onDismissDialog()
                            onBookClick(dialog.book)
                        }) {
                            Text(stringResource(R.string.action_open_book))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = onDismissDialog) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }
            is LibraryDialogState.MetadataCollisionNotice -> {
                AlertDialog(
                    onDismissRequest = onDismissDialog,
                    title = { Text(stringResource(R.string.dialog_metadata_collision_title)) },
                    text = {
                        Text(
                            stringResource(
                                R.string.dialog_metadata_collision_message,
                                dialog.newBook.title
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = onDismissDialog) {
                            Text(stringResource(R.string.action_ok))
                        }
                    }
                )
            }
            LibraryDialogState.None -> {}
        }
    }
}

@Composable
private fun SortMenuOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
        onClick = onClick
    )
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
                    text = stringResource(R.string.library_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.library_empty_subtitle),
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
                            contentDescription = stringResource(R.string.cd_add_book),
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
private fun NoSearchResultsContent(onResetSearchClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = TextSecondaryMuted,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.library_search_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.library_search_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondaryMuted,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onResetSearchClick) {
            Text(stringResource(R.string.action_reset_search))
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
            text = stringResource(R.string.library_importing),
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
        contentPadding = PaddingValues(bottom = 112.dp),
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
private fun ErrorContent(
    message: String,
    onRetryClick: () -> Unit,
    onDismissClick: () -> Unit
) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismissClick) {
                Text(stringResource(R.string.library_back_to_library))
            }
            Button(onClick = onRetryClick) {
                Text(stringResource(R.string.library_choose_file))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenEmptyPreview() {
    DeepReaderTheme {
        LibraryScreenContent(
            uiState = LibraryUiState(content = LibraryContentState.Empty),
            onSearchQueryChange = {},
            onSortOrderChange = {},
            onAddBookClick = {},
            onBookClick = {},
            onDismissDialog = {},
            onDismissError = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenContentPreview() {
    val sampleBooks = listOf(
        Book("sha1", "The Great Gatsby", "F. Scott Fitzgerald", "/tmp/1.epub"),
        Book("sha2", "1984", "George Orwell", "/tmp/2.epub")
    )
    DeepReaderTheme {
        LibraryScreenContent(
            uiState = LibraryUiState(
                content = LibraryContentState.Content(books = sampleBooks)
            ),
            onSearchQueryChange = {},
            onSortOrderChange = {},
            onAddBookClick = {},
            onBookClick = {},
            onDismissDialog = {},
            onDismissError = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenErrorPreview() {
    DeepReaderTheme {
        LibraryScreenContent(
            uiState = LibraryUiState(
                content = LibraryContentState.Error("Повреждённый EPUB-файл")
            ),
            onSearchQueryChange = {},
            onSortOrderChange = {},
            onAddBookClick = {},
            onBookClick = {},
            onDismissDialog = {},
            onDismissError = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenDuplicateDialogPreview() {
    val duplicate = Book("sha1", "The Great Gatsby", "F. Scott Fitzgerald", "/tmp/1.epub")
    DeepReaderTheme {
        LibraryScreenContent(
            uiState = LibraryUiState(
                content = LibraryContentState.Content(books = listOf(duplicate)),
                dialog = LibraryDialogState.DuplicateNotice(duplicate)
            ),
            onSearchQueryChange = {},
            onSortOrderChange = {},
            onAddBookClick = {},
            onBookClick = {},
            onDismissDialog = {},
            onDismissError = {}
        )
    }
}
