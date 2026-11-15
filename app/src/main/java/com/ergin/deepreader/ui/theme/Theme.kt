package com.ergin.deepreader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.ergin.deepreader.core.designsystem.theme.DarkExtendedReaderColors
import com.ergin.deepreader.core.designsystem.theme.LightExtendedReaderColors
import com.ergin.deepreader.core.designsystem.theme.LocalExtendedReaderColors
import com.ergin.deepreader.core.designsystem.theme.SepiaExtendedReaderColors
import com.ergin.deepreader.feature.reader.ReaderThemeMode

typealias ReaderThemeMode = com.ergin.deepreader.feature.reader.ReaderThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = FloatingDockPrimary,
    onPrimary = CardAndModalSurface,
    secondary = AiAssistantSparkle,
    onSecondary = CardAndModalSurface,
    tertiary = BookWarmPaper,
    background = TextPrimary,
    onBackground = ScreenBackground,
    surface = DockActiveIconBg,
    onSurface = ScreenBackground,
    surfaceVariant = DockActiveIconBg,
    onSurfaceVariant = TextSubtlePlaceholder,
    outline = BorderAndDividers
)

val LightColorScheme = lightColorScheme(
    primary = FloatingDockPrimary,
    onPrimary = CardAndModalSurface,
    secondary = AiAssistantSparkle,
    onSecondary = CardAndModalSurface,
    tertiary = BookWarmPaper,
    background = ScreenBackground,
    onBackground = TextPrimary,
    surface = CardAndModalSurface,
    onSurface = TextPrimary,
    surfaceVariant = SubSurfaceWell,
    onSurfaceVariant = TextSecondaryMuted,
    outline = BorderAndDividers,
    outlineVariant = QuoteHighlightGrey
)

@Composable
fun DeepReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    readerThemeMode: ReaderThemeMode? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val extendedColors = when (readerThemeMode) {
        ReaderThemeMode.LIGHT -> LightExtendedReaderColors
        ReaderThemeMode.SEPIA -> SepiaExtendedReaderColors
        ReaderThemeMode.DARK -> DarkExtendedReaderColors
        null -> if (darkTheme) DarkExtendedReaderColors else LightExtendedReaderColors
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(
        LocalExtendedReaderColors provides extendedColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
