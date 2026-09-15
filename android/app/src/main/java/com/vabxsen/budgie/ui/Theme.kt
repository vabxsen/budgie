package com.vabxsen.budgie.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vabxsen.budgie.R
import com.vabxsen.budgie.domain.Appearance

// Both fonts are variable, so every weight the theme uses is declared with its own axis value.
val DisplayFont =
    FontFamily(
        weightedFont(R.font.bricolage, FontWeight.Normal),
        weightedFont(R.font.bricolage, FontWeight.Medium),
        weightedFont(R.font.bricolage, FontWeight.SemiBold),
    )
val BodyFont =
    FontFamily(
        weightedFont(R.font.dm_sans, FontWeight.Normal),
        weightedFont(R.font.dm_sans, FontWeight.Medium),
        weightedFont(R.font.dm_sans, FontWeight.SemiBold),
        weightedFont(R.font.dm_sans, FontWeight.Bold),
    )

@OptIn(ExperimentalTextApi::class)
private fun weightedFont(resId: Int, weight: FontWeight) =
    Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
val Butter = Color(0xFFF5E889)
val Pine = Color(0xFF292F27)
val Coral = Color(0xFFEE785B)

data class BudgieColors(
    val background: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val soft: Color,
    val dark: Boolean,
)

val LocalBudgieColors = staticCompositionLocalOf {
    BudgieColors(
        Color(0xFFF7F8F3),
        Color.White,
        Color(0xFF242722),
        Color(0xFF646B5E),
        Color(0xFFE5E7DE),
        Color(0xFFEEF0E8),
        false,
    )
}
val colors: BudgieColors
    @Composable get() = LocalBudgieColors.current

@Composable
fun BudgieTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val dark =
        when (appearance) {
            Appearance.SYSTEM -> isSystemInDarkTheme()
            Appearance.LIGHT -> false
            Appearance.DARK -> true
        }
    val palette =
        if (dark)
            BudgieColors(
                Color(0xFF1E231F),
                Color(0xFF272D28),
                Color(0xFFECEDE6),
                Color(0xFFA8B0A1),
                Color(0xFF3A4238),
                Color(0xFF343D32),
                true,
            )
        else LocalBudgieColors.current.copy(dark = false)
    val scheme =
        if (dark)
            darkColorScheme(
                primary = Butter,
                onPrimary = Pine,
                background = palette.background,
                surface = palette.surface,
                onBackground = palette.ink,
                onSurface = palette.ink,
                secondary = Coral,
                surfaceVariant = palette.soft,
                onSurfaceVariant = palette.muted,
                outline = palette.line,
            )
        else
            lightColorScheme(
                primary = Pine,
                onPrimary = Color.White,
                background = palette.background,
                surface = palette.surface,
                onBackground = palette.ink,
                onSurface = palette.ink,
                secondary = Coral,
                surfaceVariant = palette.soft,
                onSurfaceVariant = palette.muted,
                outline = palette.line,
            )
    CompositionLocalProvider(LocalBudgieColors provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography =
                Typography(
                    displayLarge =
                        TextStyle(
                            fontFamily = DisplayFont,
                            fontSize = 54.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-2).sp,
                        ),
                    headlineLarge =
                        TextStyle(
                            fontFamily = DisplayFont,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 38.sp,
                            letterSpacing = (-1).sp,
                        ),
                    headlineMedium =
                        TextStyle(
                            fontFamily = DisplayFont,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 30.sp,
                            letterSpacing = (-.5).sp,
                        ),
                    titleLarge =
                        TextStyle(
                            fontFamily = DisplayFont,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    titleMedium =
                        TextStyle(
                            fontFamily = BodyFont,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    bodyLarge =
                        TextStyle(fontFamily = BodyFont, fontSize = 16.sp, lineHeight = 24.sp),
                    bodyMedium =
                        TextStyle(fontFamily = BodyFont, fontSize = 14.sp, lineHeight = 21.sp),
                    bodySmall =
                        TextStyle(fontFamily = BodyFont, fontSize = 12.sp, lineHeight = 18.sp),
                    labelLarge =
                        TextStyle(
                            fontFamily = BodyFont,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    labelMedium = TextStyle(fontFamily = BodyFont, fontSize = 12.sp),
                    labelSmall =
                        TextStyle(fontFamily = BodyFont, fontSize = 10.sp, letterSpacing = .4.sp),
                ),
            content = content,
        )
    }
}
