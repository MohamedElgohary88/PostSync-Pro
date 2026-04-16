package com.postsyncpro.presentation.theme

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.postsyncpro.presentation.i18n.*

// ─── RTL Helpers ───────────────────────────────────────────────
val LocalIsRtl = staticCompositionLocalOf { false }

@Composable
fun isRtl(): Boolean = LocalLayoutDirection.current == LayoutDirection.Rtl

// ─── Brand Colors ───────────────────────────────────────────────
object BrandColors {
    val Orange = Color(0xFF4F46E5) // using Indigo to avoid Postman look
    val OrangeLight = Color(0xFF818CF8)
    val OrangeDark = Color(0xFF3730A3)
    val Purple = Color(0xFF6B47ED)
    val PurpleLight = Color(0xFF8B6FFF)
    val Blue = Color(0xFF42A5F5)
    val BlueLight = Color(0xFF64B5F6)
    val BlueDark = Color(0xFF1E88E5)
    val Red = Color(0xFFEF5350)
    val RedLight = Color(0xFFFF7676)
    val RedDark = Color(0xFFD32F2F)
    val Green = Color(0xFF4CAF50)
    val GreenLight = Color(0xFF66BB6A)
    val GreenDark = Color(0xFF388E3C)
    val Teal = Color(0xFF26A69A)
}

object HttpMethodColors {
    val GET = Color(0xFF00C853)
    val POST = Color(0xFFFFB74D)
    val PUT = Color(0xFF42A5F5)
    val DELETE = Color(0xFFEF5350)
    val PATCH = Color(0xFFAB47BC)
    val HEAD = Color(0xFF26C6DA)
    val OPTIONS = Color(0xFF78909C)

    fun forMethod(method: String): Color = when (method.uppercase()) {
        "GET" -> GET
        "POST" -> POST
        "PUT" -> PUT
        "DELETE" -> DELETE
        "PATCH" -> PATCH
        "HEAD" -> HEAD
        "OPTIONS" -> OPTIONS
        else -> Color(0xFF9E9E9E)
    }
}

object StatusColors {
    val Success = Color(0xFF00C853)
    val SuccessBackground = Color(0xFF0D2818)
    val Error = Color(0xFFEF5350)
    val ErrorBackground = Color(0xFF2D1111)
    val Warning = Color(0xFFFFB74D)
    val WarningBackground = Color(0xFF2D2211)
    val Info = Color(0xFF40C4FF)
    val InfoBackground = Color(0xFF0D1F2D)
}

// ─── Dark Theme Palette ─────────────────────────────────────────
object DarkPalette {
    val background = Color(0xFF111318)        // deeper, slightly blue-tinted
    val surface = Color(0xFF1A1D24)           // card surfaces
    val surfaceVariant = Color(0xFF22262E)    // secondary cards
    val surfaceElevated = Color(0xFF282C35)   // elevated elements
    val surfaceHover = Color(0xFF2E3340)      // hover states
    val sidebar = Color(0xFF14161C)           // sidebar — darker than bg
    val sidebarSelected = Color(0xFF1E2230)   // sidebar selected — accent hint
    val topBar = Color(0xFF16181F)            // top bar
    val border = Color(0xFF2A2E38)            // subtle borders
    val borderSubtle = Color(0xFF1F2229)      // very subtle borders
    val textPrimary = Color(0xFFF0F0F5)       // high-contrast text
    val textSecondary = Color(0xFF9DA3B0)      // muted text
    val textTertiary = Color(0xFF5E6370)       // hint text
    val textOnAccent = Color(0xFFFFFFFF)
    val inputBackground = Color(0xFF16181F)
    val inputBorder = Color(0xFF333845)
    val scrollbar = Color(0xFF3A404D)
    val divider = Color(0xFF22262E)
    val overlay = Color(0x80000000)
    val codeBg = Color(0xFF13151B)            // code background
}

object LightPalette {
    val background = Color(0xFFF7F8FC)        // warm off-white
    val surface = Color(0xFFFFFFFF)           // pure white cards
    val surfaceVariant = Color(0xFFF0F2F8)    // secondary surfaces
    val surfaceElevated = Color(0xFFFFFFFF)
    val surfaceHover = Color(0xFFE8EBF4)      // hover — faint indigo tint
    val sidebar = Color(0xFFFBFCFE)           // sidebar — near white
    val sidebarSelected = Color(0xFFEEF0FF)   // sidebar selected — indigo tint
    val topBar = Color(0xFFFFFFFF)
    val border = Color(0xFFE0E3ED)            // softer border
    val borderSubtle = Color(0xFFEEF0F6)
    val textPrimary = Color(0xFF0F172A)       // deep navy text
    val textSecondary = Color(0xFF475569)
    val textTertiary = Color(0xFF94A3B8)
    val textOnAccent = Color(0xFFFFFFFF)
    val inputBackground = Color(0xFFF7F8FC)
    val inputBorder = Color(0xFFCBD0DE)
    val scrollbar = Color(0xFFCBD0DE)
    val divider = Color(0xFFE4E7F0)
    val overlay = Color(0x300F172A)
    val codeBg = Color(0xFFF0F2F8)
}

// ─── App Theme State ────────────────────────────────────────────
data class AppPalette(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceElevated: Color,
    val surfaceHover: Color,
    val sidebar: Color,
    val sidebarSelected: Color,
    val topBar: Color,
    val border: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textOnAccent: Color,
    val inputBackground: Color,
    val inputBorder: Color,
    val scrollbar: Color,
    val divider: Color,
    val overlay: Color,
    val codeBg: Color,
    val isDark: Boolean
)

val LocalAppPalette = staticCompositionLocalOf<AppPalette> {
    error("No AppPalette provided")
}

val LocalAppTypography = staticCompositionLocalOf<AppTextStyles> {
    error("No AppTextStyles provided")
}

data class AppTextStyles(
    val heading1: TextStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    val heading2: TextStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    val heading3: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    val body: TextStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    val bodySmall: TextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
    val label: TextStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
    val code: TextStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp),
    val codeSmall: TextStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp),
    val button: TextStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp),
    val caption: TextStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.4.sp)
)

// ─── Legacy AppTypography (backward compatibility) ──────────────
object AppTypography {
    val headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    )

    val titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    )

    val bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    )

    val labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )

    val codeMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    )

    val codeSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )
}

object AppDimensions {
    val sidebarWidth = 240.dp
    val topBarHeight = 48.dp
    val statusBarHeight = 28.dp
    val cardRadius = 12.dp
    val buttonRadius = 8.dp
    val inputRadius = 8.dp
    val chipRadius = 6.dp
    val spacingXs = 4.dp
    val spacingSm = 8.dp
    val spacingMd = 12.dp
    val spacingLg = 16.dp
    val spacingXl = 24.dp
    val spacingXxl = 32.dp
    val iconSizeSm = 16.dp
    val iconSizeMd = 20.dp
    val iconSizeLg = 24.dp
    val borderWidth = 1.dp
    val elevation = 0.dp

    // Legacy aliases (backward compatibility)
    val spacingXxs = 4.dp
    val cornerSmall = 8.dp
    val cornerMedium = 12.dp
    val cornerLarge = 16.dp
    val iconSizeSmall = 16.dp
    val iconSizeMedium = 24.dp
    val iconSizeLarge = 32.dp
}

// ─── Legacy AppColors (backward compatibility) ──────────────────
object AppColors {
    val accent get() = BrandColors.Orange
    val accentLight get() = BrandColors.OrangeLight
    val accentDark get() = BrandColors.OrangeDark
    val background = DarkPalette.background

    // Semantic colors (legacy)
    val success = Color(0xFF4CAF50)
    val warning = Color(0xFFFFC107)
    val info = Color(0xFF2196F3)

    // Code area colors
    val codeBackground = Color(0xFF0D1117)
    val codeBorder = Color(0xFF30363D)

    // Log level colors
    val logInfo = Color(0xFF9E9E9E)
    val logWarning = Color(0xFFFFC107)
    val logSuccess = Color(0xFF4CAF50)
    val logError = Color(0xFFCF6679)

    // Card colors
    val cardBackground = Color(0xFF1E2A45)
    val cardBorder = Color(0xFF2A3A5C)

    // Sidebar colors
    val sidebarBackground = Color(0xFF0F1A2E)
    val sidebarSelected = Color(0xFF1A3050)
    val sidebarHover = Color(0xFF152238)

    // JSON syntax highlighting
    val jsonKey = Color(0xFF79C0FF)
    val jsonString = Color(0xFFA5D6A7)
    val jsonNumber = Color(0xFFFFCC80)
    val jsonBoolean = Color(0xFFCE93D8)
    val jsonNull = Color(0xFFEF9A9A)
}

@Composable
fun PostSyncProTheme(
    isDarkTheme: Boolean = true,
    language: Language = Language.ENGLISH,
    content: @Composable () -> Unit
) {
    val palette = if (isDarkTheme) {
        AppPalette(
            background = DarkPalette.background,
            surface = DarkPalette.surface,
            surfaceVariant = DarkPalette.surfaceVariant,
            surfaceElevated = DarkPalette.surfaceElevated,
            surfaceHover = DarkPalette.surfaceHover,
            sidebar = DarkPalette.sidebar,
            sidebarSelected = DarkPalette.sidebarSelected,
            topBar = DarkPalette.topBar,
            border = DarkPalette.border,
            borderSubtle = DarkPalette.borderSubtle,
            textPrimary = DarkPalette.textPrimary,
            textSecondary = DarkPalette.textSecondary,
            textTertiary = DarkPalette.textTertiary,
            textOnAccent = DarkPalette.textOnAccent,
            inputBackground = DarkPalette.inputBackground,
            inputBorder = DarkPalette.inputBorder,
            scrollbar = DarkPalette.scrollbar,
            divider = DarkPalette.divider,
            overlay = DarkPalette.overlay,
            codeBg = DarkPalette.codeBg,
            isDark = true
        )
    } else {
        AppPalette(
            background = LightPalette.background,
            surface = LightPalette.surface,
            surfaceVariant = LightPalette.surfaceVariant,
            surfaceElevated = LightPalette.surfaceElevated,
            surfaceHover = LightPalette.surfaceHover,
            sidebar = LightPalette.sidebar,
            sidebarSelected = LightPalette.sidebarSelected,
            topBar = LightPalette.topBar,
            border = LightPalette.border,
            borderSubtle = LightPalette.borderSubtle,
            textPrimary = LightPalette.textPrimary,
            textSecondary = LightPalette.textSecondary,
            textTertiary = LightPalette.textTertiary,
            textOnAccent = LightPalette.textOnAccent,
            inputBackground = LightPalette.inputBackground,
            inputBorder = LightPalette.inputBorder,
            scrollbar = LightPalette.scrollbar,
            divider = LightPalette.divider,
            overlay = LightPalette.overlay,
            codeBg = LightPalette.codeBg,
            isDark = false
        )
    }

    val colorScheme = if (isDarkTheme) {
        darkColorScheme(
            primary = BrandColors.Orange,
            onPrimary = Color.White,
            primaryContainer = BrandColors.OrangeDark,
            secondary = BrandColors.Purple,
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surfaceVariant,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            outlineVariant = palette.borderSubtle,
            error = StatusColors.Error
        )
    } else {
        lightColorScheme(
            primary = BrandColors.Orange,
            onPrimary = Color.White,
            primaryContainer = BrandColors.OrangeLight,
            secondary = BrandColors.Purple,
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surfaceVariant,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            outlineVariant = palette.borderSubtle,
            error = StatusColors.Error
        )
    }

    val layoutDirection = if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val appStrings = stringsFor(language)

    CompositionLocalProvider(
        LocalAppPalette provides palette,
        LocalAppTypography provides AppTextStyles(),
        LocalAppStrings provides appStrings,
        LocalLayoutDirection provides layoutDirection,
        LocalIsRtl provides language.isRtl
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
