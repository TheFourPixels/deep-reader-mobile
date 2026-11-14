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

enum class ReaderThemeMode {
    LIGHT, SEPIA, DARK
}

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun DeepReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    readerThemeMode: ReaderThemeMode? = null,
    dynamicColor: Boolean = true,
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
            typography = Typography,
            content = content
        )
    }
}