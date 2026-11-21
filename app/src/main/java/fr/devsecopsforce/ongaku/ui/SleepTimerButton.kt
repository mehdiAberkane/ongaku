// Minuteur de sommeil sur l'écran de lecture : réglage rapide, temps restant, annulation.
package fr.devsecopsforce.ongaku.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BedtimeOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.devsecopsforce.ongaku.R
import fr.devsecopsforce.ongaku.playback.SleepTimerRules
import fr.devsecopsforce.ongaku.playback.SleepTimerState
import kotlinx.coroutines.delay

@Composable
internal fun SleepTimerButton(viewModel: PlayerViewModel) {
    val timer by viewModel.sleepTimer.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val until = timer as? SleepTimerState.Until
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    if (until != null) {
        // Le temps restant avance à chaque seconde, seulement quand un minuteur tourne.
        LaunchedEffect(until) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                delay(1000 - (until.deadlineMs - now).mod(1000L))
            }
        }
    }
    val description = stringResource(R.string.sleep_title)

    Box {
        when {
            until != null -> TextButton(onClick = { open = true }) {
                Icon(Icons.Rounded.Bedtime, description)
                Spacer(Modifier.width(6.dp))
                Text(SleepTimerRules.format((until.deadlineMs - now).coerceAtLeast(0)))
            }
            timer == SleepTimerState.TrackEnd -> TextButton(onClick = { open = true }) {
                Icon(Icons.Rounded.Bedtime, description)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.sleep_track_end_short))
            }
            else -> IconButton(onClick = { open = true }) {
                Icon(Icons.Rounded.Bedtime, description, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                description,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SleepTimerRules.PRESET_MINUTES.forEach { minutes ->
                MenuEntry(pluralStringResource(R.plurals.sleep_minutes, minutes, minutes), Icons.Rounded.Timer) {
                    open = false
                    viewModel.setSleepTimer(minutes)
                }
            }
            MenuEntry(stringResource(R.string.sleep_track_end), Icons.Rounded.MusicNote) {
                open = false
                viewModel.sleepAtTrackEnd()
            }
            if (timer != null) {
                HorizontalDivider()
                MenuEntry(stringResource(R.string.sleep_cancel), Icons.Rounded.BedtimeOff) {
                    open = false
                    viewModel.cancelSleepTimer()
                }
            }
        }
    }
}
