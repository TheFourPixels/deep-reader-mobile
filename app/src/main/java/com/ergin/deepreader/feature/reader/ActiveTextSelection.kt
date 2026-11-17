package com.ergin.deepreader.feature.reader

import androidx.compose.runtime.Immutable

@Immutable
data class ActiveTextSelection(
    val selectedText: String,
    val paragraphIndex: Int = 0,
    val startOffset: Int = 0,
    val endOffset: Int = 0
)
