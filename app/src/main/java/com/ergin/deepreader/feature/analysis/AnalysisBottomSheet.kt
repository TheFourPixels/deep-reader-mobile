package com.ergin.deepreader.feature.analysis

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ergin.deepreader.core.designsystem.theme.LocalExtendedReaderColors
import com.ergin.deepreader.core.network.model.ExplainResponseDto
import com.ergin.deepreader.core.network.model.TranslateResponseDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisBottomSheet(
    viewModel: AnalysisViewModel,
    onDismissRequest: () -> Unit,
    onSwitchToTranslateClick: (() -> Unit)? = null,
    onRetryClick: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (uiState !is AnalysisUiState.Idle) {
        ModalBottomSheet(
            onDismissRequest = {
                viewModel.onDismissSheet()
                onDismissRequest()
            },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                when (val state = uiState) {
                    is AnalysisUiState.Loading -> {
                        ShimmerLoadingState()
                    }
                    is AnalysisUiState.SuccessExplain -> {
                        ExplainSuccessContent(state.response)
                    }
                    is AnalysisUiState.SuccessTranslate -> {
                        TranslateSuccessContent(state.response)
                    }
                    is AnalysisUiState.Error -> {
                        ErrorContent(
                            errorCode = state.errorCode,
                            message = state.message,
                            traceId = state.traceId,
                            onRetryClick = onRetryClick,
                            onSwitchToTranslateClick = onSwitchToTranslateClick
                        )
                    }
                    is AnalysisUiState.Offline -> {
                        OfflineContent(message = state.message)
                    }
                    AnalysisUiState.Idle -> {}
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ShimmerLoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "AI-анализатор изучает литературный контекст...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExplainSuccessContent(response: ExplainResponseDto) {
    val readerColors = LocalExtendedReaderColors.current
    val badgeStyle = readerColors.getPhenomenonBadgeStyle(response.phenomenonType)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "AI-разбор",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (response.cached) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Из кэша (${response.processingTimeMs}мс)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "«${response.selectedText}»",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = response.contextualMeaning,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(12.dp))

        // M3 Badges wrapped in FlowRow for 200% font scaling support (/mobile-design)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = badgeStyle.container,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = getPhenomenonLabel(response.phenomenonType),
                    color = badgeStyle.content,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Стиль: ${getStylisticLabel(response.stylisticColoring)}",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = response.explanation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TranslateSuccessContent(response: TranslateResponseDto) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Translate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Перевод (${response.sourceLanguage?.uppercase() ?: "AUTO"} ➔ ${response.targetLanguage.uppercase()})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = response.translatedText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ErrorContent(
    errorCode: String,
    message: String,
    traceId: String?,
    onRetryClick: (() -> Unit)?,
    onSwitchToTranslateClick: (() -> Unit)?
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onRetryClick != null) {
                Button(
                    onClick = onRetryClick,
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Попробовать снова")
                }
            }

            if (errorCode == "LLM_PROVIDER_ERROR" && onSwitchToTranslateClick != null) {
                TextButton(
                    onClick = onSwitchToTranslateClick,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Перевод DeepL")
                }
            }
        }

        if (!traceId.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Trace ID", traceId))
                    }
                    .padding(8.dp)
            ) {
                Text(
                    text = "ID ошибки: $traceId",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Скопировать ID ошибки",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun OfflineContent(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun getPhenomenonLabel(type: String): String = when (type.lowercase()) {
    "idiom" -> "🧩 Идиома"
    "slang" -> "🗣 Сленг / Жаргон"
    "metaphor" -> "🎭 Метафора"
    "irony" -> "🙃 Ирония"
    "cultural_reference" -> "🏛 Культурный код"
    "allusion" -> "📖 Аллюзия"
    "archaic" -> "📜 Архаизм"
    "neologism" -> "✨ Неологизм"
    else -> "💬 Прямой смысл"
}

private fun getStylisticLabel(style: String): String = when (style.lowercase()) {
    "neutral" -> "Нейтральный"
    "informal" -> "Разговорный"
    "slang" -> "Сленговый"
    "rude" -> "Грубый"
    "sarcastic" -> "Саркастический"
    "ironic" -> "Ироничный"
    "literary" -> "Высокий стиль"
    "poetic" -> "Поэтический"
    "dramatic" -> "Драматический"
    else -> style
}