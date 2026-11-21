@file:OptIn(ExperimentalLayoutApi::class)

package fr.devsecopsforce.ongaku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fr.devsecopsforce.ongaku.R
import fr.devsecopsforce.ongaku.data.Appearance
import fr.devsecopsforce.ongaku.data.AppData
import fr.devsecopsforce.ongaku.data.AppearanceStore
import fr.devsecopsforce.ongaku.data.CoverShape
import fr.devsecopsforce.ongaku.data.ThemeMode
import java.io.File
import kotlin.math.roundToInt

@Composable
fun AppearanceScreen(
    data: AppData,
    backgroundFile: (String?) -> File?,
    onPick: (String) -> Unit,
    onRemove: (BackgroundTarget) -> Unit,
    /** Images de morceaux qui ne sont plus sur le téléphone (gardées au cas où ils reviennent). */
    missingTrackImages: Int = 0,
    onRemoveMissing: () -> Unit = {},
) {
    val store = AppearanceStore.get(LocalContext.current)
    val appearance = LocalAppearance.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Heading(stringResource(R.string.appearance_backgrounds))
        ImageRow(
            label = stringResource(R.string.appearance_background_app),
            file = backgroundFile(data.appBackground),
            onPick = { onPick("app") },
            onRemove = { onRemove(BackgroundTarget.App) },
        )
        ImageRow(
            label = stringResource(R.string.appearance_background_player),
            file = backgroundFile(data.playerBackground),
            onPick = { onPick("player") },
            onRemove = { onRemove(BackgroundTarget.Player) },
        )
        Note(stringResource(R.string.appearance_background_track_note))
        if (missingTrackImages > 0) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(
                        R.plurals.appearance_missing_track_images,
                        missingTrackImages,
                        missingTrackImages,
                    ),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onRemoveMissing) { Text(stringResource(R.string.common_delete)) }
            }
        }
        Spacer(Modifier.height(12.dp))
        ImageRow(
            label = stringResource(R.string.appearance_background_widget),
            file = backgroundFile(data.widgetBackground),
            onPick = { onPick("widget") },
            onRemove = { onRemove(BackgroundTarget.Widget) },
        )
        Note(stringResource(R.string.appearance_background_widget_note))
        Spacer(Modifier.height(12.dp))

        var dim by remember(appearance.backgroundDim) {
            mutableFloatStateOf(appearance.backgroundDim)
        }
        Text(stringResource(R.string.appearance_background_dim, (dim * 100).roundToInt()))
        Slider(
            value = dim,
            valueRange = 0f..0.9f,
            onValueChange = { dim = it },
            onValueChangeFinished = { store.update { it.copy(backgroundDim = dim) } },
        )
        var blur by remember(appearance.backgroundBlur) {
            mutableFloatStateOf(appearance.backgroundBlur.toFloat())
        }
        Text(
            if (blur < 1f) {
                stringResource(R.string.appearance_background_blur_none)
            } else {
                stringResource(R.string.appearance_background_blur, blur.roundToInt())
            },
        )
        Slider(
            value = blur,
            valueRange = 0f..24f,
            onValueChange = { blur = it },
            onValueChangeFinished = {
                store.update { it.copy(backgroundBlur = blur.roundToInt()) }
            },
        )
        var playerDim by remember(appearance.playerDim) {
            mutableFloatStateOf(appearance.playerDim)
        }
        Text(stringResource(R.string.appearance_player_dim, (playerDim * 100).roundToInt()))
        Slider(
            value = playerDim,
            valueRange = 0f..0.9f,
            onValueChange = { playerDim = it },
            onValueChangeFinished = { store.update { it.copy(playerDim = playerDim) } },
        )

        Separator()

        Heading(stringResource(R.string.appearance_color))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SystemSwatch(selected = appearance.accent == null) {
                store.update { it.copy(accent = null) }
            }
            AccentPresets.forEach { (name, color) ->
                ColorSwatch(stringResource(name), color, selected = appearance.accent == color) {
                    store.update { it.copy(accent = color) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        var hue by remember(appearance.accent) {
            mutableFloatStateOf(appearance.accent?.let(::colorToHue) ?: 220f)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(hueToColor(hue))),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.appearance_custom_color))
        }
        Slider(
            value = hue,
            valueRange = 0f..359f,
            onValueChange = { hue = it },
            onValueChangeFinished = { store.update { it.copy(accent = hueToColor(hue)) } },
        )
        OptionSwitch(
            title = stringResource(R.string.appearance_accent_from_artwork),
            note = stringResource(R.string.appearance_accent_from_artwork_note),
            checked = appearance.accentFromArtwork,
            onChange = { value -> store.update { it.copy(accentFromArtwork = value) } },
        )

        Separator()

        Heading(stringResource(R.string.appearance_theme))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ThemeMode.SYSTEM to R.string.appearance_theme_system,
                ThemeMode.LIGHT to R.string.appearance_theme_light,
                ThemeMode.DARK to R.string.appearance_theme_dark,
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = appearance.themeMode == mode,
                    onClick = { store.update { it.copy(themeMode = mode) } },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OptionSwitch(
            title = stringResource(R.string.appearance_pure_black),
            note = stringResource(R.string.appearance_pure_black_note),
            checked = appearance.pureBlack,
            onChange = { value -> store.update { it.copy(pureBlack = value) } },
        )

        Separator()

        Heading(stringResource(R.string.appearance_covers))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                CoverShape.ROUNDED to R.string.appearance_cover_rounded,
                CoverShape.CIRCLE to R.string.appearance_cover_circle,
                CoverShape.SQUARE to R.string.appearance_cover_square,
            ).forEach { (shape, label) ->
                FilterChip(
                    selected = appearance.coverShape == shape,
                    onClick = { store.update { it.copy(coverShape = shape) } },
                    label = { Text(stringResource(label)) },
                )
            }
        }

        Separator()

        Heading(stringResource(R.string.appearance_display))
        Text(stringResource(R.string.appearance_text_size))
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                0.85f to R.string.appearance_text_small,
                1f to R.string.appearance_text_normal,
                1.15f to R.string.appearance_text_large,
                1.3f to R.string.appearance_text_huge,
            )
                .forEach { (scale, label) ->
                    FilterChip(
                        selected = appearance.textScale == scale,
                        onClick = { store.update { it.copy(textScale = scale) } },
                        label = { Text(stringResource(label)) },
                    )
                }
        }
        Spacer(Modifier.height(8.dp))
        OptionSwitch(
            title = stringResource(R.string.appearance_compact_list),
            note = stringResource(R.string.appearance_compact_list_note),
            checked = appearance.compactList,
            onChange = { value -> store.update { it.copy(compactList = value) } },
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.appearance_start_tab))
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LIBRARY_TABS.forEachIndexed { index, label ->
                FilterChip(
                    selected = appearance.startTab == index,
                    onClick = { store.update { it.copy(startTab = index) } },
                    label = { Text(stringResource(label)) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = { store.update { Appearance() } }) {
            Text(stringResource(R.string.appearance_reset))
        }
        Spacer(Modifier.height(4.dp))
        Note(stringResource(R.string.appearance_reset_note))
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun OptionSwitch(
    title: String,
    note: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Note(note)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Separator() {
    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ImageRow(label: String, file: File?, onPick: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (file != null) {
                AsyncImage(
                    model = file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f))
        if (file != null) TextButton(onClick = onRemove) { Text(stringResource(R.string.common_remove)) }
        TextButton(onClick = onPick) { Text(stringResource(R.string.appearance_choose)) }
    }
}

private val SwatchSize = 44.dp

@Composable
private fun ColorSwatch(name: String, color: Int, selected: Boolean, onClick: () -> Unit) {
    val swatch = Color(color)
    val description = stringResource(R.string.appearance_color_description, name)
    Box(
        Modifier
            .size(SwatchSize)
            .clip(CircleShape)
            .background(swatch)
            .selectionBorder(selected)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = if (swatch.luminance() > 0.5f) Color.Black else Color.White,
            )
        }
    }
}

@Composable
private fun SystemSwatch(selected: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.appearance_system_colors)
    Box(
        Modifier
            .size(SwatchSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .selectionBorder(selected)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (selected) Icons.Rounded.Check else Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Modifier.selectionBorder(selected: Boolean): Modifier =
    if (selected) {
        border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
    } else {
        this
    }
