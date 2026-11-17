package com.ergin.deepreader.navigation

import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

@Serializable
data object CurrentlyReadingRoute

@Serializable
data object CollectionRoute

@Serializable
data class ReaderRoute(val bookSha256: String)
