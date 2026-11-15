package com.ergin.deepreader.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ergin.deepreader.core.storage.FileStorageGateway
import com.ergin.deepreader.domain.model.Book
import com.ergin.deepreader.domain.repository.BookRepository
import com.ergin.deepreader.domain.repository.ImportBookResult
import com.ergin.deepreader.domain.usecase.ImportBookUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import javax.inject.Inject

sealed interface LibraryNavigationEvent {
    data class OpenReader(val bookSha256: String) : LibraryNavigationEvent
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val importBookUseCase: ImportBookUseCase,
    private val storageGateway: FileStorageGateway
) : ViewModel() {

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Empty)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _dialogState = MutableStateFlow<LibraryDialogState>(LibraryDialogState.None)
    val dialogState: StateFlow<LibraryDialogState> = _dialogState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<LibraryNavigationEvent>()
    val navigationEvent: SharedFlow<LibraryNavigationEvent> = _navigationEvent.asSharedFlow()

    private var isImporting = false

    init {
        observeBooks()
    }

    private fun observeBooks() {
        viewModelScope.launch {
            bookRepository.getBooks()
                .catch { e ->
                    _uiState.value = LibraryUiState.Error("Failed to load library: ${e.message}")
                }
                .collect { books ->
                    if (!isImporting) {
                        _uiState.value = if (books.isEmpty()) {
                            LibraryUiState.Empty
                        } else {
                            LibraryUiState.Content(books = books)
                        }
                    }
                }
        }
    }

    fun onImportFileSelected(inputStream: InputStream, fallbackFileName: String) {
        if (isImporting) return
        isImporting = true
        _uiState.value = LibraryUiState.Importing

        viewModelScope.launch {
            try {
                when (val result = importBookUseCase(inputStream, fallbackFileName)) {
                    is ImportBookResult.Success -> {
                        _uiState.value = LibraryUiState.Content(
                            books = listOf(result.book)
                        )
                    }
                    is ImportBookResult.ExactDuplicate -> {
                        _dialogState.value = LibraryDialogState.DuplicateNotice(result.existingBook)
                    }
                    is ImportBookResult.MetadataCollisionWarning -> {
                        _dialogState.value = LibraryDialogState.MetadataCollisionNotice(
                            newBook = result.importedBook,
                            existingBook = result.existingMatchingMetadataBook
                        )
                    }
                    is ImportBookResult.Error -> {
                        _uiState.value = LibraryUiState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = LibraryUiState.Error(e.message ?: "Failed to import file")
            } finally {
                isImporting = false
                observeBooks()
            }
        }
    }

    fun onBookClicked(book: Book) {
        val file = File(book.filePath)
        if (!storageGateway.fileExists(file)) {
            // FT-31: File not available on device error dialog without crashing
            _dialogState.value = LibraryDialogState.FileMissingError(book.title)
        } else {
            viewModelScope.launch {
                bookRepository.updateLastOpened(book.sha256)
                _navigationEvent.emit(LibraryNavigationEvent.OpenReader(book.sha256))
            }
        }
    }

    fun onDismissDialog() {
        _dialogState.value = LibraryDialogState.None
    }
}