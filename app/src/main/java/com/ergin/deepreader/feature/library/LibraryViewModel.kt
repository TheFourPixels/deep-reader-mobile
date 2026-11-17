package com.ergin.deepreader.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ergin.deepreader.core.storage.FileStorageGateway
import com.ergin.deepreader.di.IoDispatcher
import com.ergin.deepreader.domain.model.Book
import com.ergin.deepreader.domain.repository.BookRepository
import com.ergin.deepreader.domain.repository.ImportBookResult
import com.ergin.deepreader.domain.usecase.ImportBookUseCase
import com.ergin.deepreader.domain.usecase.UpdateLastOpenedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val updateLastOpenedUseCase: UpdateLastOpenedUseCase,
    private val storageGateway: FileStorageGateway,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _navigationChannel = Channel<LibraryNavigationEvent>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val navigationEvent = _navigationChannel.receiveAsFlow()

    private var isImporting = false
    private var latestBooks: List<Book> = emptyList()

    init {
        observeBooks()
    }

    private fun observeBooks() {
        viewModelScope.launch {
            bookRepository.getBooks()
                .catch { e ->
                    if (e is CancellationException) throw e
                    _uiState.update {
                        it.copy(content = LibraryContentState.Error(e.message ?: "Ошибка загрузки библиотеки"))
                    }
                }
                .collect { books ->
                    latestBooks = books
                    if (!isImporting && _uiState.value.content !is LibraryContentState.Error) {
                        _uiState.update { state ->
                            state.copy(
                                content = buildContentState(books, state.searchQuery, state.sortOrder)
                            )
                        }
                    }
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            val updatedContent = if (state.content is LibraryContentState.Content) {
                buildContentState(latestBooks, query, state.sortOrder)
            } else {
                state.content
            }
            state.copy(searchQuery = query, content = updatedContent)
        }
    }

    fun onSortOrderChanged(sortOrder: LibrarySortOrder) {
        _uiState.update { state ->
            val updatedContent = if (state.content is LibraryContentState.Content) {
                buildContentState(latestBooks, state.searchQuery, sortOrder)
            } else {
                state.content
            }
            state.copy(sortOrder = sortOrder, content = updatedContent)
        }
    }

    fun onImportFileSelected(inputStream: InputStream, fallbackFileName: String) {
        if (isImporting) return
        isImporting = true
        _uiState.update { it.copy(content = LibraryContentState.Importing) }

        viewModelScope.launch {
            try {
                when (val result = importBookUseCase(inputStream, fallbackFileName)) {
                    is ImportBookResult.Success -> {
                        // DB emission refreshes observeBooks()
                    }
                    is ImportBookResult.ExactDuplicate -> {
                        _uiState.update { it.copy(dialog = LibraryDialogState.DuplicateNotice(result.existingBook)) }
                    }
                    is ImportBookResult.MetadataCollisionWarning -> {
                        _uiState.update {
                            it.copy(
                                dialog = LibraryDialogState.MetadataCollisionNotice(
                                    newBook = result.importedBook,
                                    existingBook = result.existingMatchingMetadataBook
                                )
                            )
                        }
                    }
                    is ImportBookResult.Error -> {
                        _uiState.update { it.copy(content = LibraryContentState.Error(result.message)) }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(content = LibraryContentState.Error(e.message ?: "Ошибка импорта файла"))
                }
            } finally {
                isImporting = false
                _uiState.update { state ->
                    if (state.content is LibraryContentState.Importing) {
                        state.copy(
                            content = buildContentState(latestBooks, state.searchQuery, state.sortOrder)
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun onBookClicked(book: Book) {
        viewModelScope.launch {
            val exists = withContext(ioDispatcher) {
                storageGateway.fileExists(File(book.filePath))
            }
            if (!exists) {
                _uiState.update {
                    it.copy(dialog = LibraryDialogState.FileMissingError(book.title))
                }
            } else {
                updateLastOpenedUseCase(book.sha256)
                _navigationChannel.send(LibraryNavigationEvent.OpenReader(book.sha256))
            }
        }
    }

    fun onDismissDialog() {
        _uiState.update { it.copy(dialog = LibraryDialogState.None) }
    }

    fun onDismissError() {
        _uiState.update { state ->
            state.copy(content = buildContentState(latestBooks, state.searchQuery, state.sortOrder))
        }
    }

    private fun buildContentState(
        books: List<Book>,
        query: String,
        sortOrder: LibrarySortOrder
    ): LibraryContentState {
        if (books.isEmpty()) return LibraryContentState.Empty
        val sorted = when (sortOrder) {
            LibrarySortOrder.RECENT -> books
            LibrarySortOrder.TITLE_ASC -> books.sortedBy { it.title.lowercase() }
            LibrarySortOrder.AUTHOR_ASC -> books.sortedBy { (it.author ?: "").lowercase() }
        }
        val filtered = if (query.isBlank()) {
            sorted
        } else {
            sorted.filter {
                it.title.contains(query, ignoreCase = true) ||
                    (it.author?.contains(query, ignoreCase = true) == true)
            }
        }
        return LibraryContentState.Content(books = sorted, filteredBooks = filtered)
    }
}
