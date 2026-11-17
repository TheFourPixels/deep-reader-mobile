package com.ergin.deepreader.feature.analysis

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ergin.deepreader.R
import com.ergin.deepreader.core.common.copyTextToClipboard
import com.ergin.deepreader.core.designsystem.theme.LocalExtendedReaderColors
import com.ergin.deepreader.domain.model.DomainError
import com.ergin.deepreader.domain.model.ExplainAnalysis
import com.ergin.deepreader.domain.model.TranslationResult
import com.ergin.deepreader.ui.theme.DeepReaderTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisBottomSheet(
    viewModel: AnalysisViewModel,
    onDismissRequest: () -> Unit,
    onSwitchToTranslateClick: (() -> Unit)? = null,
    onRetryClick: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (uiState.sheetState !is AnalysisSheetState.Idle) {
        ModalBottomSheet(
            onDismissRequest = {
                viewModel.onDismissSheet()
                onDismissRequest()
            },
            sheetState = sheetState
        ) {
            AnalysisBottomSheetContent(
                sheetState = uiState.sheetState,
                onRetryClick = onRetryClick ?: { viewModel.retryLastRequest() },
                onSwitchToTranslateClick = onSwitchToTranslateClick
            )
        }
    }
}

@Composable
fun AnalysisBottomSheetContent(
    sheetState: AnalysisSheetState,
    modifier: Modifier = Modifier,
    onRetryClick: (() -> Unit)? = null,
    onSwitchToTranslateClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        when (sheetState) {
            is AnalysisSheetState.Loading -> {
                ShimmerLoadingState()
            }
            is AnalysisSheetState.SuccessExplain -> {
                ExplainSuccessContent(sheetState.analysis)
            }
            is AnalysisSheetState.SuccessTranslate -> {
                TranslateSuccessContent(sheetState.translation)
            }
            is AnalysisSheetState.Error -> {
                val error = sheetState.error
                val message = resolveDomainErrorMessage(error)
                val traceId = (error as? DomainError.ServerError)?.traceId
                val errorCode = (error as? DomainError.ServerError)?.errorCode
                ErrorContent(
                    errorCode = errorCode,
                    message = message,
                    traceId = traceId,
                    onRetryClick = onRetryClick,
                    onSwitchToTranslateClick = onSwitchToTranslateClick
                )
            }
            is AnalysisSheetState.Offline -> {
                val message = if (sheetState.action == "TRANSLATE") {
                    stringResource(R.string.error_offline_translate)
                } else {
                    stringResource(R.string.error_offline_explain)
                }
                OfflineContent(
                    message = message,
                    onRetryClick = onRetryClick
                )
            }
            AnalysisSheetState.Idle -> {}
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun resolveDomainErrorMessage(error: DomainError): String = when (error) {
    is DomainError.Offline -> if (error.action == "TRANSLATE") {
        stringResource(R.string.error_offline_translate)
    } else {
        stringResource(R.string.error_offline_explain)
    }
    is DomainError.Network -> if (error.action == "TRANSLATE") {
        stringResource(R.string.error_network_translate)
    } else {
        stringResource(R.string.error_network_explain)
    }
    is DomainError.EmptyResponse -> if (error.action == "TRANSLATE") {
        stringResource(R.string.error_empty_translate)
    } else {
        stringResource(R.string.error_empty_explain)
    }
    is DomainError.ServerError -> error.serverMessage?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.error_unknown)
    is DomainError.Unknown -> stringResource(R.string.error_unknown)
}

@Composable
private fun ShimmerLoadingState() {
    val transition = rememberInfiniteTransition(label = "ShimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerAnim"
    )

    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.3f),
        Color.LightGray.copy(alpha = 0.7f),
        Color.LightGray.copy(alpha = 0.3f)
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 200f, translateAnim - 200f),
        end = Offset(translateAnim, translateAnim)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.analysis_loading_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(
            modifier = Modifier
                .width(160.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExplainSuccessContent(analysis: ExplainAnalysis) {
    val readerColors = LocalExtendedReaderColors.current
    val badgeStyle = readerColors.getPhenomenonBadgeStyle(analysis.phenomenonType)

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
                text = stringResource(R.string.analysis_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (analysis.cached) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.analysis_cached_badge, analysis.processingTimeMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.analysis_quote_format, analysis.selectedText),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = analysis.contextualMeaning,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = badgeStyle.container,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = getPhenomenonLabel(analysis.phenomenonType),
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
                    text = stringResource(
                        R.string.analysis_style_prefix,
                        getStylisticLabel(analysis.stylisticColoring)
                    ),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = analysis.explanation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TranslateSuccessContent(translation: TranslationResult) {
    val sourceLang = translation.sourceLanguage?.uppercase()
        ?: stringResource(R.string.analysis_source_auto)
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
                text = stringResource(
                    R.string.analysis_translate_title,
                    sourceLang,
                    translation.targetLanguage.uppercase()
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = translation.translatedText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ErrorContent(
    errorCode: String?,
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
                    Text(stringResource(R.string.analysis_retry))
                }
            }

            if (errorCode == "LLM_PROVIDER_ERROR" && onSwitchToTranslateClick != null) {
                TextButton(
                    onClick = onSwitchToTranslateClick,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(stringResource(R.string.analysis_fallback_translate))
                }
            }
        }

        if (!traceId.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable {
                        with(context) {
                            copyTextToClipboard("Trace ID", traceId)
                        }
                    }
                    .padding(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.analysis_trace_id, traceId),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.cd_copy_trace_id),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun OfflineContent(
    message: String,
    onRetryClick: (() -> Unit)? = null
) {
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
        if (onRetryClick != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetryClick,
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.analysis_retry))
            }
        }
    }
}

@Composable
private fun getPhenomenonLabel(type: String): String = when (type.lowercase()) {
    "idiom" -> stringResource(R.string.phenomenon_idiom)
    "slang" -> stringResource(R.string.phenomenon_slang)
    "metaphor" -> stringResource(R.string.phenomenon_metaphor)
    "irony" -> stringResource(R.string.phenomenon_irony)
    "cultural_reference" -> stringResource(R.string.phenomenon_cultural_reference)
    "allusion" -> stringResource(R.string.phenomenon_allusion)
    "archaic" -> stringResource(R.string.phenomenon_archaic)
    "neologism" -> stringResource(R.string.phenomenon_neologism)
    else -> stringResource(R.string.phenomenon_literal)
}

@Composable
private fun getStylisticLabel(style: String): String = when (style.lowercase()) {
    "neutral" -> stringResource(R.string.style_neutral)
    "informal" -> stringResource(R.string.style_informal)
    "slang" -> stringResource(R.string.style_slang)
    "rude" -> stringResource(R.string.style_rude)
    "sarcastic" -> stringResource(R.string.style_sarcastic)
    "ironic" -> stringResource(R.string.style_ironic)
    "literary" -> stringResource(R.string.style_literary)
    "poetic" -> stringResource(R.string.style_poetic)
    "dramatic" -> stringResource(R.string.style_dramatic)
    else -> style
}

@Preview(showBackground = true)
@Composable
private fun AnalysisLoadingPreview() {
    DeepReaderTheme {
        AnalysisBottomSheetContent(sheetState = AnalysisSheetState.Loading)
    }
}

@Preview(showBackground = true)
@Composable
private fun AnalysisSuccessExplainPreview() {
    DeepReaderTheme {
        AnalysisBottomSheetContent(
            sheetState = AnalysisSheetState.SuccessExplain(
                ExplainAnalysis(
                    selectedText = "push your luck",
                    contextualMeaning = "испытывать судьбу / рисковать сверх меры",
                    phenomenonType = "idiom",
                    explanation = "Идиоматическое выражение, означающее продолжение рискованных действий после череды удач.",
                    stylisticColoring = "informal",
                    spoilerAlert = false,
                    cached = true,
                    processingTimeMs = 42L
                )
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AnalysisSuccessTranslatePreview() {
    DeepReaderTheme {
        AnalysisBottomSheetContent(
            sheetState = AnalysisSheetState.SuccessTranslate(
                TranslationResult(
                    translatedText = "Не искушай судьбу",
                    sourceLanguage = "en",
                    targetLanguage = "ru",
                    cached = false
                )
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AnalysisErrorPreview() {
    DeepReaderTheme {
        AnalysisBottomSheetContent(
            sheetState = AnalysisSheetState.Error(
                DomainError.ServerError(
                    errorCode = "LLM_PROVIDER_ERROR",
                    serverMessage = "Сервер ИИ перегружен. Попробуйте чуть позже.",
                    traceId = "req-9182-abcd"
                )
            ),
            onRetryClick = {},
            onSwitchToTranslateClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AnalysisOfflinePreview() {
    DeepReaderTheme {
        AnalysisBottomSheetContent(
            sheetState = AnalysisSheetState.Offline(action = "EXPLAIN"),
            onRetryClick = {}
        )
    }
}
