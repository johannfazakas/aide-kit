package ro.jf.ai.assistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val lightScheme =
    lightColorScheme(
        primary = Color(0xFF4A5AB9),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDEE2F9),
        onPrimaryContainer = Color(0xFF1E2857),
        secondary = Color(0xFF5B6070),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE5E7EE),
        onSecondaryContainer = Color(0xFF23262F),
        tertiary = Color(0xFF6F5D00),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFE58F),
        onTertiaryContainer = Color(0xFF4A3B00),
        background = Color(0xFFFAFAFC),
        onBackground = Color(0xFF1B1C20),
        surface = Color(0xFFFAFAFC),
        onSurface = Color(0xFF1B1C20),
        surfaceVariant = Color(0xFFEEEFF3),
        onSurfaceVariant = Color(0xFF5D616C),
        outline = Color(0xFFC3C6CF),
        outlineVariant = Color(0xFFE2E4EA),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF5F5F8),
        surfaceContainer = Color(0xFFEFF0F4),
        surfaceContainerHigh = Color(0xFFE9EAEF),
        surfaceContainerHighest = Color(0xFFE3E4EA),
    )

private val darkScheme =
    darkColorScheme(
        primary = Color(0xFFB7C0F2),
        onPrimary = Color(0xFF20295E),
        primaryContainer = Color(0xFF3A4478),
        onPrimaryContainer = Color(0xFFDEE2F9),
        secondary = Color(0xFFB9BDC9),
        onSecondary = Color(0xFF23262F),
        secondaryContainer = Color(0xFF3A3E4A),
        onSecondaryContainer = Color(0xFFE5E7EE),
        tertiary = Color(0xFFE0C34C),
        onTertiary = Color(0xFF3A2E00),
        tertiaryContainer = Color(0xFF564A0E),
        onTertiaryContainer = Color(0xFFFFE58F),
        background = Color(0xFF131418),
        onBackground = Color(0xFFE3E4E9),
        surface = Color(0xFF131418),
        onSurface = Color(0xFFE3E4E9),
        surfaceVariant = Color(0xFF23252C),
        onSurfaceVariant = Color(0xFFA9ACB6),
        outline = Color(0xFF454854),
        outlineVariant = Color(0xFF2C2E36),
        surfaceContainerLowest = Color(0xFF0E0F12),
        surfaceContainerLow = Color(0xFF1B1C21),
        surfaceContainer = Color(0xFF1F2026),
        surfaceContainerHigh = Color(0xFF292A31),
        surfaceContainerHighest = Color(0xFF34353D),
    )

private val appTypography =
    Typography().let { base ->
        base.copy(
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            bodyLarge = base.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
            bodySmall = base.bodySmall.copy(fontSize = 12.sp),
            labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
        )
    }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkScheme else lightScheme,
        typography = appTypography,
        content = content,
    )
}
