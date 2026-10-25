package com.openvault.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.openvault.app.domain.model.AccentColorChoice
import com.openvault.app.domain.model.FontScaleChoice
import com.openvault.app.domain.model.ThemeMode
import com.openvault.app.domain.model.UiDensityChoice
import com.openvault.app.domain.model.VaultSettings

val PlusJakartaFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

data class OpenVaultSpacing(
    val cardPadding: Dp = 16.dp,
    val itemSpacing: Dp = 12.dp,
    val screenHorizontal: Dp = 16.dp
)

val LocalOpenVaultSpacing = staticCompositionLocalOf { OpenVaultSpacing() }

val AmoledBlack = Color(0xFF000000)
val AmoledCardSurface = Color(0xFF0D0D11)
val AmoledElevatedSurface = Color(0xFF14141B)
val AmoledBorderOutline = Color(0xFF262633)

val StatusSuccessEmerald = Color(0xFF10B981)
val StatusWarningAmber = Color(0xFFF59E0B)
val StatusErrorRed = Color(0xFFEF4444)
val StatusInfoCyan = Color(0xFF06B6D4)

private fun buildColorScheme(
    themeMode: ThemeMode,
    accent: AccentColorChoice,
    systemDark: Boolean
): ColorScheme {
    val primary = Color(accent.primaryHex)
    val secondary = Color(accent.secondaryHex)

    val effectiveMode = when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDark) ThemeMode.AMOLED_BLACK else ThemeMode.LIGHT
        else -> themeMode
    }

    return when (effectiveMode) {
        ThemeMode.AMOLED_BLACK -> darkColorScheme(
            primary = primary,
            onPrimary = Color(0xFF001F14),
            primaryContainer = primary.copy(alpha = 0.18f),
            onPrimaryContainer = primary,
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFF161822),
            onSecondaryContainer = Color(0xFFE5E7EB),
            tertiary = StatusInfoCyan,
            background = AmoledBlack,
            onBackground = Color(0xFFF3F4F6),
            surface = AmoledBlack,
            onSurface = Color(0xFFF3F4F6),
            surfaceVariant = AmoledCardSurface,
            onSurfaceVariant = Color(0xFF9CA3AF),
            surfaceContainer = AmoledCardSurface,
            surfaceContainerHigh = AmoledElevatedSurface,
            outline = AmoledBorderOutline,
            outlineVariant = Color(0xFF1F2430),
            error = StatusErrorRed,
            onError = Color.White
        )

        ThemeMode.DARK_GRAY -> darkColorScheme(
            primary = primary,
            onPrimary = Color(0xFF001F14),
            primaryContainer = primary.copy(alpha = 0.2f),
            onPrimaryContainer = primary,
            secondary = secondary,
            onSecondary = Color.White,
            background = Color(0xFF0F1117),
            onBackground = Color(0xFFF3F4F6),
            surface = Color(0xFF131620),
            onSurface = Color(0xFFF3F4F6),
            surfaceVariant = Color(0xFF1C202E),
            onSurfaceVariant = Color(0xFF9CA3AF),
            outline = Color(0xFF2E3446),
            outlineVariant = Color(0xFF252A3A),
            error = StatusErrorRed
        )

        ThemeMode.LIGHT, ThemeMode.SYSTEM -> lightColorScheme(
            primary = secondary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.15f),
            onPrimaryContainer = secondary,
            secondary = primary,
            background = Color(0xFFF8FAFC),
            onBackground = Color(0xFF0F172A),
            surface = Color.White,
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFF1F5F9),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFCBD5E1),
            outlineVariant = Color(0xFFE2E8F0),
            error = StatusErrorRed
        )
    }
}

private fun buildTypography(scale: Float): Typography {
    return Typography(
        headlineLarge = TextStyle(
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (28 * scale).sp,
            lineHeight = (34 * scale).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (22 * scale).sp,
            lineHeight = (28 * scale).sp
        ),
        titleLarge = TextStyle(
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (19 * scale).sp,
            lineHeight = (25 * scale).sp
        ),
        titleMedium = TextStyle(
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (16 * scale).sp,
            lineHeight = (22 * scale).sp
        ),
        bodyLarge = TextStyle(
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (15 * scale).sp,
            lineHeight = (22 * scale).sp
        ),
        bodyMedium = TextStyle(
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (13.5f * scale).sp,
            lineHeight = (19 * scale).sp
        ),
        labelLarge = TextStyle(
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (13 * scale).sp
        ),
        labelSmall = TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (11 * scale).sp
        )
    )
}

@Composable
fun OpenVaultTheme(
    settings: VaultSettings = VaultSettings(),
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val colorScheme = buildColorScheme(
        themeMode = settings.themeMode,
        accent = settings.accentColor,
        systemDark = systemDark
    )
    val typography = buildTypography(settings.fontScale.scaleFactor)
    val spacing = when (settings.uiDensity) {
        UiDensityChoice.COMPACT -> OpenVaultSpacing(
            cardPadding = 12.dp,
            itemSpacing = 8.dp,
            screenHorizontal = 12.dp
        )
        UiDensityChoice.COMFORTABLE -> OpenVaultSpacing(
            cardPadding = 16.dp,
            itemSpacing = 12.dp,
            screenHorizontal = 16.dp
        )
    }

    CompositionLocalProvider(LocalOpenVaultSpacing provides spacing) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
