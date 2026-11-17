package com.ergin.deepreader.domain.usecase

import com.ergin.deepreader.domain.repository.BookRepository
import javax.inject.Inject

class UpdateLastOpenedUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    suspend operator fun invoke(sha256: String) {
        bookRepository.updateLastOpened(sha256)
    }
}
