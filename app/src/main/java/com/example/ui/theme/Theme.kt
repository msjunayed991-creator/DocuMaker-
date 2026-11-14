package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.domain.model.ThemeMode

private val DocuLightColorScheme = lightColorScheme(
    primary = DocuRed,
    onPrimary = PureWhite,
    primaryContainer = DocuRedSoft,
    onPrimaryContainer = DocuRedDark,
    secondary = InkBlack,
    onSecondary = PureWhite,
    secondaryContainer = PaperSurfaceVariant,
    onSecondaryContainer = InkBlack,
    tertiary = DocuRedDark,
    onTertiary = PureWhite,
    background = PaperOffWhite,
    onBackground = TextPrimaryLight,
    surface = PureWhite,
    onSurface = TextPrimaryLight,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderLight,
    outlineVariant = BorderLight,
    error = DocuRed,
    onError = PureWhite
)

private val DocuDarkColorScheme = darkColorScheme(
    primary = DocuRed,
    onPrimary = PureWhite,
    primaryContainer = DocuRedSoftDark,
    onPrimaryContainer = PureWhite,
    secondary = PureWhite,
    onSecondary = InkBlack,
    secondaryContainer = CharcoalElevated,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = DocuRed,
    onTertiary = PureWhite,
    background = DeepObsidian,
    onBackground = TextPrimaryDark,
    surface = CharcoalSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = CharcoalElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDark,
    outlineVariant = BorderDark,
    error = DocuRed,
    onError = PureWhite
)

val DocuMakerShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DocuMakerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DocuDarkColorScheme else DocuLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DocuMakerTypography,
        shapes = DocuMakerShapes,
        content = content
    )
}
