package com.ergin.deepreader.feature.library

import com.ergin.deepreader.domain.model.Book

sealed interface LibraryUiState {
    object Empty : LibraryUiState
    object Importing : LibraryUiState
    data class Content(
        val books: List<Book>,
        val warningMessage: String? = null
    ) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

sealed interface LibraryDialogState {
    object None : LibraryDialogState
    data class DuplicateNotice(val book: Book) : LibraryDialogState
    data class MetadataCollisionNotice(val newBook: Book, val existingBook: Book) : LibraryDialogState
    data class FileMissingError(val bookTitle: String) : LibraryDialogState
}