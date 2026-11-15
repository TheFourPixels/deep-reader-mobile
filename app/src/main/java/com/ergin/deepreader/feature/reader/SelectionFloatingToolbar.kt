package com.ergin.deepreader.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SelectionFloatingToolbar(
    selectedText: String,
    onExplainClick: () -> Unit,
    onTranslateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val length = selectedText.length
    val isAiDisabled = length > 500
    val isTranslateDisabled = length > 5000

    Surface(
        modifier = modifier
            .systemGestureExclusion()
            .padding(8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isAiDisabled && !isTranslateDisabled) {
                Text(
                    text = "Фрагмент велик ($length симв). AI доступен до 500 символов.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 6.dp, start = 8.dp, end = 8.dp)
                )
            } else if (isTranslateDisabled) {
                Text(
                    text = "Фрагмент слишком велик ($length символов).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 6.dp, start = 8.dp, end = 8.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Translate Button
                OutlinedButton(
                    onClick = onTranslateClick,
                    enabled = !isTranslateDisabled,
                    modifier = Modifier.size(width = 130.dp, height = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Перевод", style = MaterialTheme.typography.labelMedium)
                }

                // AI Explain Button
                Button(
                    onClick = onExplainClick,
                    enabled = !isAiDisabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.size(width = 140.dp, height = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AI-разбор", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}