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
    val GET = Color(0xFF54D68A)
    val POST = Color(0xFFFFC857)
    val PUT = Color(0xFF63B3FF)
    val DELETE = Color(0xFFFF707A)
    val PATCH = Color(0xFFD58AF0)
    val HEAD = Color(0xFF5ED7E5)
    val OPTIONS = Color(0xFFAAB6C5)

    private val LightGet = Color(0xFF087A3D)
    private val LightPost = Color(0xFF8A4B00)
    private val LightPut = Color(0xFF075FA8)
    private val LightDelete = Color(0xFFB4232E)
    private val LightPatch = Color(0xFF762A87)
    private val LightHead = Color(0xFF006B75)
    private val LightOptions = Color(0xFF4B5C6B)

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

    fun forMethod(method: String, isDark: Boolean): Color {
        if (isDark) return forMethod(method)
        return when (method.uppercase()) {
            "GET" -> LightGet
            "POST" -> LightPost
            "PUT" -> LightPut
            "DELETE" -> LightDelete
            "PATCH" -> LightPatch
            "HEAD" -> LightHead
            "OPTIONS" -> LightOptions
            else -> Color(0xFF526170)
        }
    }
}

@Composable
fun appHttpMethodColor(method: String): Color =
    HttpMethodColors.forMethod(method, LocalAppPalette.current.isDark)

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
    val background = Color(0xFF0B0F16)
    val surface = Color(0xFF121823)
    val surfaceVariant = Color(0xFF192230)
    val surfaceElevated = Color(0xFF202B3A)
    val surfaceHover = Color(0xFF263345)
    val sidebar = Color(0xFF0F141D)
    val sidebarSelected = Color(0xFF202A46)
    val topBar = Color(0xFF101620)
    val border = Color(0xFF2C394B)
    val borderSubtle = Color(0xFF1E2937)
    val textPrimary = Color(0xFFF4F7FB)
    val textSecondary = Color(0xFFB3BDCA)
    val textTertiary = Color(0xFF8693A5)
    val textOnAccent = Color(0xFFFFFFFF)
    val inputBackground = Color(0xFF0E141E)
    val inputBorder = Color(0xFF344459)
    val scrollbar = Color(0xFF43536A)
    val divider = Color(0xFF222E3E)
    val overlay = Color(0x80000000)
    val codeBg = Color(0xFF0A1018)
}

object LightPalette {
    val background = Color(0xFFF4F7FB)
    val surface = Color(0xFFFFFFFF)
    val surfaceVariant = Color(0xFFEEF2F7)
    val surfaceElevated = Color(0xFFFFFFFF)
    val surfaceHover = Color(0xFFE7ECF4)
    val sidebar = Color(0xFFF9FBFD)
    val sidebarSelected = Color(0xFFE8EAFF)
    val topBar = Color(0xFFFFFFFF)
    val border = Color(0xFFD7DEE8)
    val borderSubtle = Color(0xFFE8EDF3)
    val textPrimary = Color(0xFF132033)
    val textSecondary = Color(0xFF455468)
    val textTertiary = Color(0xFF66758A)
    val textOnAccent = Color(0xFFFFFFFF)
    val inputBackground = Color(0xFFF8FAFC)
    val inputBorder = Color(0xFFBCC7D5)
    val scrollbar = Color(0xFFAAB6C5)
    val divider = Color(0xFFDDE4EC)
    val overlay = Color(0x300F172A)
    val codeBg = Color(0xFFF1F4F8)
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
    val accent: Color,
    val accentHover: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
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
    val bodySmall: TextStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    val label: TextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.45.sp),
    val code: TextStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp),
    val codeSmall: TextStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 17.sp),
    val button: TextStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp),
    val caption: TextStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.35.sp)
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
            accent = Color(0xFF9298FF),
            accentHover = Color(0xFFAEB2FF),
            onAccent = Color(0xFF0A0D14),
            success = Color(0xFF54D68A),
            warning = Color(0xFFFFC857),
            error = Color(0xFFFF707A),
            info = Color(0xFF63B3FF),
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
            accent = Color(0xFF4F46E5),
            accentHover = Color(0xFF4338CA),
            onAccent = Color.White,
            success = Color(0xFF137A45),
            warning = Color(0xFF8A5200),
            error = Color(0xFFBF2533),
            info = Color(0xFF075FA8),
            isDark = false
        )
    }

    val colorScheme = if (isDarkTheme) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
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
            error = palette.error
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
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
            error = palette.error
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
