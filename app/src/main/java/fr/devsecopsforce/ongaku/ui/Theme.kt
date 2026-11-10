package fr.devsecopsforce.ongaku.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.devsecopsforce.ongaku.data.Appearance
import fr.devsecopsforce.ongaku.data.AppearanceStore
import fr.devsecopsforce.ongaku.data.ThemeMode
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalAppearance = compositionLocalOf { Appearance() }

@Composable
fun OngakuTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val store = AppearanceStore.get(context)
    val appearance by store.state.collectAsStateWithLifecycle()
    val artworkAccent by store.artworkAccent.collectAsStateWithLifecycle()
    val accent = artworkAccent.takeIf { appearance.accentFromArtwork } ?: appearance.accent
    val dark = when (appearance.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val base = when {
        accent != null -> schemeFromSeed(accent, dark)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val colors = if (dark && appearance.pureBlack) base.withPureBlack() else base

    // Le thème peut différer de celui du système : les icônes de la barre d'état doivent suivre.
    val activity = LocalActivity.current
    val view = LocalView.current
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalAppearance provides appearance,
        LocalDensity provides Density(density.density, density.fontScale * appearance.textScale),
    ) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

/** Tient à jour la couleur de la pochette en cours, seulement si l'option est active. */
@Composable
fun ArtworkAccentEffect(artworkUri: Uri?) {
    val context = LocalContext.current
    val enabled = LocalAppearance.current.accentFromArtwork
    LaunchedEffect(artworkUri, enabled) {
        AppearanceStore.get(context).artworkAccent.value =
            if (enabled && artworkUri != null) extractAccent(context, artworkUri) else null
    }
}

/** Teinte la plus présente de l'image, ou null si elle n'a pas de couleur franche. */
private suspend fun extractAccent(context: Context, uri: Uri): Int? = withContext(Dispatchers.IO) {
    val bitmap = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = 8 })
        }
    } catch (e: IOException) {
        null
    } ?: return@withContext null

    val weights = FloatArray(HUE_BINS)
    val hsl = FloatArray(3)
    val stepX = (bitmap.width / 32).coerceAtLeast(1)
    val stepY = (bitmap.height / 32).coerceAtLeast(1)
    for (x in 0 until bitmap.width step stepX) {
        for (y in 0 until bitmap.height step stepY) {
            ColorUtils.colorToHSL(bitmap.getPixel(x, y), hsl)
            if (hsl[1] > 0.25f && hsl[2] in 0.15f..0.85f) {
                weights[(hsl[0] / 360f * HUE_BINS).toInt().coerceAtMost(HUE_BINS - 1)] += hsl[1]
            }
        }
    }
    bitmap.recycle()
    val best = weights.indices.maxBy { weights[it] }
    if (weights[best] == 0f) null else hueToColor((best + 0.5f) * 360f / HUE_BINS)
}

private const val HUE_BINS = 36

private fun ColorScheme.withPureBlack() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
)

/** Décline une couleur unique en palette complète, en conservant sa teinte. */
private fun schemeFromSeed(seed: Int, dark: Boolean): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed, hsl)
    val hue = hsl[0]
    val saturation = hsl[1].coerceAtLeast(0.15f)

    fun tone(lightness: Float, saturationFactor: Float = 1f, hueShift: Float = 0f) = Color(
        ColorUtils.HSLToColor(
            floatArrayOf(
                (hue + hueShift + 360f) % 360f,
                (saturation * saturationFactor).coerceIn(0f, 1f),
                lightness,
            ),
        ),
    )

    return if (dark) {
        darkColorScheme(
            primary = tone(0.78f),
            onPrimary = tone(0.16f),
            primaryContainer = tone(0.28f),
            onPrimaryContainer = tone(0.90f),
            secondary = tone(0.76f, 0.45f),
            onSecondary = tone(0.16f, 0.45f),
            secondaryContainer = tone(0.26f, 0.45f),
            onSecondaryContainer = tone(0.90f, 0.45f),
            tertiary = tone(0.78f, 0.6f, 60f),
            onTertiary = tone(0.16f, 0.6f, 60f),
            tertiaryContainer = tone(0.28f, 0.6f, 60f),
            onTertiaryContainer = tone(0.90f, 0.6f, 60f),
            background = tone(0.07f, 0.3f),
            onBackground = tone(0.92f, 0.15f),
            surface = tone(0.07f, 0.3f),
            onSurface = tone(0.92f, 0.15f),
            surfaceVariant = tone(0.24f, 0.2f),
            onSurfaceVariant = tone(0.80f, 0.2f),
            surfaceDim = tone(0.07f, 0.3f),
            surfaceBright = tone(0.24f, 0.25f),
            surfaceContainerLowest = tone(0.04f, 0.3f),
            surfaceContainerLow = tone(0.10f, 0.3f),
            surfaceContainer = tone(0.12f, 0.3f),
            surfaceContainerHigh = tone(0.16f, 0.3f),
            surfaceContainerHighest = tone(0.21f, 0.3f),
            outline = tone(0.56f, 0.15f),
            outlineVariant = tone(0.28f, 0.15f),
            inverseSurface = tone(0.90f, 0.15f),
            inverseOnSurface = tone(0.18f, 0.2f),
            inversePrimary = tone(0.38f),
            surfaceTint = tone(0.78f),
        )
    } else {
        lightColorScheme(
            primary = tone(0.38f),
            onPrimary = Color.White,
            primaryContainer = tone(0.88f),
            onPrimaryContainer = tone(0.14f),
            secondary = tone(0.38f, 0.4f),
            onSecondary = Color.White,
            secondaryContainer = tone(0.88f, 0.45f),
            onSecondaryContainer = tone(0.14f, 0.45f),
            tertiary = tone(0.38f, 0.6f, 60f),
            onTertiary = Color.White,
            tertiaryContainer = tone(0.88f, 0.6f, 60f),
            onTertiaryContainer = tone(0.14f, 0.6f, 60f),
            background = tone(0.97f, 0.5f),
            onBackground = tone(0.11f, 0.15f),
            surface = tone(0.97f, 0.5f),
            onSurface = tone(0.11f, 0.15f),
            surfaceVariant = tone(0.89f, 0.2f),
            onSurfaceVariant = tone(0.30f, 0.2f),
            surfaceDim = tone(0.86f, 0.25f),
            surfaceBright = tone(0.97f, 0.5f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = tone(0.95f, 0.45f),
            surfaceContainer = tone(0.93f, 0.4f),
            surfaceContainerHigh = tone(0.91f, 0.35f),
            surfaceContainerHighest = tone(0.89f, 0.3f),
            outline = tone(0.48f, 0.15f),
            outlineVariant = tone(0.78f, 0.15f),
            inverseSurface = tone(0.20f, 0.15f),
            inverseOnSurface = tone(0.95f, 0.3f),
            inversePrimary = tone(0.78f),
            surfaceTint = tone(0.38f),
        )
    }
}

fun hueToColor(hue: Float): Int =
    ColorUtils.HSLToColor(floatArrayOf(hue.coerceIn(0f, 359f), 0.7f, 0.5f))

fun colorToHue(color: Int): Float {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    return hsl[0]
}

val AccentPresets = listOf(
    "Indigo" to Color(0xFF5C6BC0),
    "Bleu" to Color(0xFF2196F3),
    "Turquoise" to Color(0xFF009688),
    "Vert" to Color(0xFF4CAF50),
    "Ambre" to Color(0xFFFFB300),
    "Orange" to Color(0xFFFF7043),
    "Rouge" to Color(0xFFE53935),
    "Rose" to Color(0xFFEC407A),
    "Violet" to Color(0xFF8E24AA),
    "Gris" to Color(0xFF78909C),
).map { (name, color) -> name to color.toArgb() }
