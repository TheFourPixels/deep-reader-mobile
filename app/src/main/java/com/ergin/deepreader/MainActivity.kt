package com.ergin.deepreader

import android.net.Uri
import android.os.Bundle
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.ergin.deepreader.feature.analysis.AnalysisBottomSheet
import com.ergin.deepreader.feature.analysis.AnalysisViewModel
import com.ergin.deepreader.feature.library.LibraryNavigationEvent
import com.ergin.deepreader.feature.library.LibraryScreen
import com.ergin.deepreader.feature.library.LibraryViewModel
import com.ergin.deepreader.feature.reader.ReaderScreen
import com.ergin.deepreader.feature.reader.ReaderViewModel
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
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val readerViewModel: ReaderViewModel = hiltViewModel()
    val analysisViewModel: AnalysisViewModel = hiltViewModel()

    var activeBookSha256 by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val fileName = uri.lastPathSegment ?: "imported.epub"
                if (inputStream != null) {
                    libraryViewModel.onImportFileSelected(inputStream, fileName)
                }
            } catch (e: Exception) {
                // Handled in ViewModel
            }
        }
    }

    LaunchedEffect(Unit) {
        libraryViewModel.navigationEvent.collect { event ->
            when (event) {
                is LibraryNavigationEvent.OpenReader -> {
                    activeBookSha256 = event.bookSha256
                }
            }
        }
    }

    val currentSha256 = activeBookSha256
    if (currentSha256 == null) {
        LibraryScreen(
            viewModel = libraryViewModel,
            onAddBookClick = {
                launcher.launch(arrayOf("application/epub+zip", "application/octet-stream", "*/*"))
            },
            onBookClick = { book ->
                activeBookSha256 = book.sha256
            }
        )
    } else {
        ReaderScreen(
            viewModel = readerViewModel,
            bookSha256 = currentSha256,
            onBackClick = {
                activeBookSha256 = null
            }
        )
    }

    AnalysisBottomSheet(
        viewModel = analysisViewModel,
        onDismissRequest = {
            analysisViewModel.onDismissSheet()
        }
    )
}