package dev.kyle.mctools.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

enum class FontChoice(val label: String) { MINECRAFT("Minecraft"), CLEAN("Clean") }
enum class ThemeChoice(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E9FB), onPrimaryContainer = Color(0xFF3730A3),
    background = Color(0xFFF6F7FB), onBackground = Color(0xFF1B1B2F),
    surface = Color.White, onSurface = Color(0xFF1B1B2F),
    surfaceVariant = Color(0xFFEEF0F8), onSurfaceVariant = Color(0xFF5A5F7A),
    outline = Color(0xFFD5D8EA), error = Color(0xFFC62828),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8B85FF), onPrimary = Color(0xFF14123D),
    primaryContainer = Color(0xFF2A2766), onPrimaryContainer = Color(0xFFDAD8FF),
    background = Color(0xFF0F1020), onBackground = Color(0xFFEDEEFA),
    surface = Color(0xFF181A30), onSurface = Color(0xFFEDEEFA),
    surfaceVariant = Color(0xFF22243F), onSurfaceVariant = Color(0xFFA9ADCB),
    outline = Color(0xFF34375A), error = Color(0xFFFF8A80),
)

val MinecraftFamily = FontFamily(Font(R.font.minecraft))

@Composable
fun KyleTheme(theme: ThemeChoice, font: FontChoice, content: @Composable () -> Unit) {
    val dark = when (theme) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    val family = if (font == FontChoice.MINECRAFT) MinecraftFamily else FontFamily.Default
    fun ts(size: Int, line: Int) = TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = line.sp)
    val typography = Typography(
        headlineLarge = ts(32, 36), headlineMedium = ts(28, 32), headlineSmall = ts(24, 28),
        titleLarge = ts(24, 28), titleMedium = ts(20, 24), titleSmall = ts(18, 22),
        bodyLarge = ts(16, 22), bodyMedium = ts(16, 22), bodySmall = ts(16, 20),
        labelLarge = ts(18, 22), labelMedium = ts(16, 20), labelSmall = ts(16, 20),
    )
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = typography, content = content)
}

@Composable
fun isDarkColors(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f
