// Égaliseur intégré (bandes, préréglages, basses) et accès à l'égaliseur du téléphone.
@file:OptIn(ExperimentalLayoutApi::class)

package fr.devsecopsforce.ongaku.ui

import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.devsecopsforce.ongaku.R
import fr.devsecopsforce.ongaku.data.EqualizerLevels
import fr.devsecopsforce.ongaku.data.EqualizerSettings
import fr.devsecopsforce.ongaku.playback.AudioEffects
import fr.devsecopsforce.ongaku.playback.EqualizerInfo
import kotlin.math.roundToInt

@Composable
internal fun EqualizerScreen() {
    val context = LocalContext.current
    val settings = remember { EqualizerSettings(context) }
    val info by AudioEffects.info.collectAsStateWithLifecycle()
    var enabled by remember { mutableStateOf(settings.enabled) }
    var preset by remember { mutableStateOf(settings.preset) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.eq_enabled), Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    settings.enabled = it
                },
            )
        }
        Hint(stringResource(R.string.eq_note))

        val current = info
        when {
            current == null -> Hint(stringResource(R.string.eq_not_ready))
            current.bands == null -> Hint(stringResource(R.string.eq_unsupported))
            else -> EqualizerControls(
                info = current,
                bands = current.bands,
                settings = settings,
                enabled = enabled,
                preset = EqualizerLevels.preset(preset, current.presets.size),
                onPresetChange = {
                    preset = it
                    settings.preset = it
                },
            )
        }

        if (current?.bassSupported == true) {
            Spacer(Modifier.height(24.dp))
            BassControl(settings, enabled)
        }

        current?.let { SystemEqualizerButton(it.audioSessionId) }
    }
}

@Composable
private fun EqualizerControls(
    info: EqualizerInfo,
    bands: List<EqualizerInfo.Band>,
    settings: EqualizerSettings,
    enabled: Boolean,
    preset: Int,
    onPresetChange: (Int) -> Unit,
) {
    Spacer(Modifier.height(16.dp))
    SectionTitle(stringResource(R.string.eq_presets))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = preset == EqualizerSettings.CUSTOM,
            onClick = {
                // Repart des niveaux en place : passer en « Personnalisé » ne change pas le son.
                settings.setCustomLevels(info.levels)
                onPresetChange(EqualizerSettings.CUSTOM)
            },
            label = { Text(stringResource(R.string.eq_custom)) },
            enabled = enabled,
        )
        info.presets.forEachIndexed { index, name ->
            FilterChip(
                selected = preset == index,
                onClick = { onPresetChange(index) },
                label = { Text(name) },
                enabled = enabled,
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    SectionTitle(stringResource(R.string.eq_bands))
    // Niveaux en cours de glissement : affichés tout de suite, sans attendre le service.
    var dragged by remember { mutableStateOf<List<Int>?>(null) }
    val levels = dragged ?: info.levels
    bands.forEachIndexed { band, bandInfo ->
        val level = levels.getOrElse(band) { 0 }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(EqualizerLevels.frequencyLabel(bandInfo.centerMilliHertz), Modifier.width(72.dp))
            Slider(
                value = level.toFloat(),
                valueRange = info.minLevel.toFloat()..info.maxLevel.toFloat(),
                onValueChange = { value ->
                    val updated = levels.toMutableList().also { it[band] = value.roundToInt() }
                    dragged = updated
                    settings.setCustomLevels(updated)
                    onPresetChange(EqualizerSettings.CUSTOM)
                },
                onValueChangeFinished = { dragged = null },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            Text(
                EqualizerLevels.levelLabel(level),
                Modifier.width(72.dp).padding(start = 8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    TextButton(
        onClick = {
            settings.setCustomLevels(List(bands.size) { 0 })
            onPresetChange(EqualizerSettings.CUSTOM)
        },
        enabled = enabled,
    ) { Text(stringResource(R.string.eq_flat)) }
}

@Composable
private fun BassControl(settings: EqualizerSettings, enabled: Boolean) {
    var strength by remember { mutableFloatStateOf(settings.bassStrength.toFloat()) }
    SectionTitle(stringResource(R.string.eq_bass))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = strength,
            valueRange = 0f..EqualizerLevels.MAX_BASS.toFloat(),
            onValueChange = {
                strength = it
                settings.bassStrength = it.roundToInt()
            },
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (strength < 1f) stringResource(R.string.eq_bass_off) else "${(strength / 10).roundToInt()} %",
            Modifier.width(72.dp).padding(start = 8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Égaliseur fourni par le téléphone, ouvert sur la musique d'Ongaku ; absent sur certains appareils. */
@Composable
private fun SystemEqualizerButton(audioSessionId: Int) {
    val context = LocalContext.current
    val intent = remember(audioSessionId) {
        Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
    }
    val available = remember(intent) { intent.resolveActivity(context.packageManager) != null }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    Spacer(Modifier.height(24.dp))
    SectionTitle(stringResource(R.string.eq_system))
    if (available) {
        Hint(stringResource(R.string.eq_system_note))
        OutlinedButton(onClick = { runCatching { launcher.launch(intent) } }) { Text(stringResource(R.string.eq_system_open)) }
    } else {
        Hint(stringResource(R.string.eq_system_missing))
    }
}
