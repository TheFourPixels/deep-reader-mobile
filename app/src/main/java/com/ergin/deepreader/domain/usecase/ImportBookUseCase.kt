package com.ergin.deepreader.domain.usecase

import com.ergin.deepreader.domain.repository.BookRepository
import com.ergin.deepreader.domain.repository.ImportBookResult
import java.io.InputStream
import javax.inject.Inject

class ImportBookUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    suspend operator fun invoke(inputStream: InputStream, fallbackFileName: String): ImportBookResult {
        return bookRepository.importBook(inputStream, fallbackFileName)
    }
}