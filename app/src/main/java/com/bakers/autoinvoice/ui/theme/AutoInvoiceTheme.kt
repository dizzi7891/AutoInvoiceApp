package com.bakers.autoinvoice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = Maroon,
    secondary = MaroonLight,
    tertiary = AccentGold
)

private val LightColors = lightColorScheme(
    primary = Maroon,
    secondary = MaroonLight,
    tertiary = AccentGold
)

@Composable
fun AutoInvoiceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
