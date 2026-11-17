package com.ergin.deepreader

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ergin.deepreader.domain.model.TranslateRequest
import com.ergin.deepreader.feature.analysis.AnalysisBottomSheet
import com.ergin.deepreader.feature.analysis.AnalysisViewModel
import com.ergin.deepreader.feature.collection.CollectionScreen
import com.ergin.deepreader.feature.library.LibraryContentState
import com.ergin.deepreader.feature.library.LibraryNavigationEvent
import com.ergin.deepreader.feature.library.LibraryScreen
import com.ergin.deepreader.feature.library.LibraryViewModel
import com.ergin.deepreader.feature.reader.CurrentlyReadingScreen
import com.ergin.deepreader.feature.reader.ReaderScreen
import com.ergin.deepreader.feature.reader.ReaderViewModel
import com.ergin.deepreader.feature.reader.toCurrentlyReadingBook
import com.ergin.deepreader.navigation.CollectionRoute
import com.ergin.deepreader.navigation.CurrentlyReadingRoute
import com.ergin.deepreader.navigation.LibraryRoute
import com.ergin.deepreader.navigation.ReaderRoute
import com.ergin.deepreader.ui.components.DockScreen
import com.ergin.deepreader.ui.theme.DeepReaderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DeepReaderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DeepReaderMainApp()
                }
            }
        }
    }
}

@Composable
fun DeepReaderMainApp() {
    val navController = rememberNavController()
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val analysisViewModel: AnalysisViewModel = hiltViewModel()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                var fileName: String? = null
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            fileName = cursor.getString(index)
                        }
                    }
                }
                val finalFileName = fileName ?: uri.lastPathSegment ?: "imported.epub"
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    libraryViewModel.onImportFileSelected(inputStream, finalFileName)
                }
            } catch (_: Exception) {
                // Handled in ViewModel
            }
        }
    }

    LaunchedEffect(libraryViewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            libraryViewModel.navigationEvent.collect { event ->
                when (event) {
                    is LibraryNavigationEvent.OpenReader -> {
                        navController.navigate(ReaderRoute(event.bookSha256)) {
                            launchSingleTop = true
                        }
                    }
                }
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentDockScreen = when {
        currentDestination?.hasRoute<CurrentlyReadingRoute>() == true -> DockScreen.READER
        currentDestination?.hasRoute<CollectionRoute>() == true -> DockScreen.SETTINGS
        else -> DockScreen.LIBRARY
    }

    val navigateToDockTab: (DockScreen) -> Unit = { screen ->
        val route: Any = when (screen) {
            DockScreen.LIBRARY -> LibraryRoute
            DockScreen.READER -> CurrentlyReadingRoute
            DockScreen.SETTINGS -> CollectionRoute
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val libraryUiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val analysisUiState by analysisViewModel.uiState.collectAsStateWithLifecycle()
    val contentState = libraryUiState.content as? LibraryContentState.Content
    val realBooks = contentState?.books.orEmpty()
    val filteredBooks = contentState?.filteredBooks.orEmpty()

    NavHost(
        navController = navController,
        startDestination = LibraryRoute
    ) {
        composable<LibraryRoute> {
            LibraryScreen(
                viewModel = libraryViewModel,
                onAddBookClick = {
                    launcher.launch(
                        arrayOf("application/epub+zip", "application/octet-stream", "*/*")
                    )
                },
                onBookClick = libraryViewModel::onBookClicked,
                currentDockScreen = currentDockScreen,
                onDockScreenSelected = navigateToDockTab
            )
        }

        composable<CurrentlyReadingRoute> {
            val unknownAuthor = stringResource(R.string.currently_reading_unknown_author)
            val defaultSynopsis = stringResource(R.string.currently_reading_default_synopsis)
            val currentlyReadingList = realBooks.map {
                it.toCurrentlyReadingBook(unknownAuthor, defaultSynopsis)
            }
            CurrentlyReadingScreen(
                books = currentlyReadingList,
                currentDockScreen = currentDockScreen,
                onDockScreenSelected = navigateToDockTab,
                onContinueClick = { readingBook ->
                    if (readingBook.id.isNotBlank()) {
                        val book = realBooks.find { it.sha256 == readingBook.id }
                        if (book != null) {
                            libraryViewModel.onBookClicked(book)
                        } else {
                            navController.navigate(ReaderRoute(readingBook.id)) {
                                launchSingleTop = true
                            }
                        }
                    }
                },
                onGoToLibraryClick = { navigateToDockTab(DockScreen.LIBRARY) }
            )
        }

        composable<CollectionRoute> {
            CollectionScreen(
                collectionTitle = stringResource(R.string.collection_all_books),
                books = filteredBooks,
                searchQuery = libraryUiState.searchQuery,
                onSearchQueryChange = libraryViewModel::onSearchQueryChanged,
                onBookClick = libraryViewModel::onBookClicked,
                onDismiss = { navigateToDockTab(DockScreen.LIBRARY) },
                currentDockScreen = currentDockScreen,
                onDockScreenSelected = navigateToDockTab
            )
        }

        composable<ReaderRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ReaderRoute>()
            val readerViewModel: ReaderViewModel = hiltViewModel()

            ReaderScreen(
                viewModel = readerViewModel,
                bookSha256 = route.bookSha256,
                hasLastAiResult = analysisUiState.hasLastSuccessfulResult,
                onBackClick = {
                    navController.popBackStack<ReaderRoute>(inclusive = true)
                },
                onTranslateRequested = { selectedText ->
                    analysisViewModel.translate(
                        request = TranslateRequest(text = selectedText, targetLanguage = "ru")
                    )
                },
                onAiExplainRequested = { explainRequest ->
                    analysisViewModel.explain(explainRequest)
                },
                onReopenAiResult = {
                    analysisViewModel.onReopenLastResult()
                }
            )
        }
    }

    AnalysisBottomSheet(
        viewModel = analysisViewModel,
        onDismissRequest = {
            analysisViewModel.onDismissSheet()
        },
        onSwitchToTranslateClick = analysisViewModel::fallbackToTranslate
    )
}
