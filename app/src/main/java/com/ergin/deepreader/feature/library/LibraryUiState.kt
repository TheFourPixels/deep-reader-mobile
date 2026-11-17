package com.ergin.deepreader.feature.library

import androidx.compose.runtime.Immutable
import com.ergin.deepreader.domain.model.Book

enum class LibrarySortOrder {
    RECENT,
    TITLE_ASC,
    AUTHOR_ASC
}

@Immutable
data class LibraryUiState(
    val content: LibraryContentState = LibraryContentState.Empty,
    val dialog: LibraryDialogState = LibraryDialogState.None,
    val searchQuery: String = "",
    val sortOrder: LibrarySortOrder = LibrarySortOrder.RECENT
)

@Immutable
sealed interface LibraryContentState {
    object Empty : LibraryContentState
    object Importing : LibraryContentState

    @Immutable
    data class Content(
        val books: List<Book>,
        val filteredBooks: List<Book> = books,
        val warningMessage: String? = null
    ) : LibraryContentState

    data class Error(val message: String) : LibraryContentState
}

@Immutable
sealed interface LibraryDialogState {
    object None : LibraryDialogState
    data class DuplicateNotice(val book: Book) : LibraryDialogState
    data class MetadataCollisionNotice(val newBook: Book, val existingBook: Book) : LibraryDialogState
    data class FileMissingError(val bookTitle: String) : LibraryDialogState
}
