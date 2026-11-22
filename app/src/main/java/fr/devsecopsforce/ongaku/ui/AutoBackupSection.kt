// Réglages de la sauvegarde automatique : dossier, fréquence, nombre gardé, dernière sauvegarde.
@file:OptIn(ExperimentalLayoutApi::class)

package fr.devsecopsforce.ongaku.ui

import android.content.Intent
import android.content.SharedPreferences
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import fr.devsecopsforce.ongaku.R
import fr.devsecopsforce.ongaku.data.AutoBackupRules
import fr.devsecopsforce.ongaku.data.AutoBackupSettings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Réglages lus en une fois : relus quand [AutoBackupSettings] change. */
private data class Current(
    val enabled: Boolean,
    val folder: String?,
    val intervalDays: Int,
    val keep: Int,
    val lastRunMs: Long,
    val lastFailed: Boolean,
)

@Composable
internal fun AutoBackupSection(onBackupNow: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AutoBackupSettings(context) }
    // Relu à chaque changement des réglages, y compris par la sauvegarde elle-même (date, échec).
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(settings) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> version++ }
        settings.prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { settings.prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val current = remember(version) {
        Current(settings.enabled, settings.folderUri, settings.intervalDays, settings.keep, settings.lastRunMs, settings.lastFailed)
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Accès gardé après le redémarrage du téléphone : sans lui, Android l'oublierait.
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        settings.folderUri = uri.toString()
        settings.enabled = true
        settings.lastFailed = false
    }
    val folder = current.folder
    val enabled = current.enabled

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.auto_backup_title), Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = enabled && folder != null,
            onCheckedChange = { on ->
                if (on && folder == null) folderPicker.launch(null) else settings.enabled = on
            },
        )
    }
    Hint(stringResource(R.string.auto_backup_note))
    if (folder == null) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { folderPicker.launch(null) }) { Text(stringResource(R.string.auto_backup_choose)) }
        return
    }
    // Nom donné par le stockage (les Téléchargements n'ont qu'un numéro dans leur identifiant).
    val label = remember(folder) { folderName(context, folder) }
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.auto_backup_folder, label), style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = { folderPicker.launch(null) }) { Text(stringResource(R.string.auto_backup_change)) }
    if (!enabled) return

    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.auto_backup_every))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutoBackupRules.INTERVALS_DAYS.forEach { days ->
            FilterChip(
                selected = current.intervalDays == days,
                onClick = { settings.intervalDays = days },
                label = {
                    Text(
                        stringResource(
                            when (days) {
                                1 -> R.string.auto_backup_daily
                                7 -> R.string.auto_backup_weekly
                                else -> R.string.auto_backup_monthly
                            },
                        ),
                    )
                },
            )
        }
    }
    Text(stringResource(R.string.auto_backup_keep))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutoBackupRules.KEEP_COUNTS.forEach { count ->
            FilterChip(
                selected = current.keep == count,
                onClick = { settings.keep = count },
                label = { Text(pluralStringResource(R.plurals.auto_backup_keep_count, count, count)) },
            )
        }
    }
    val last = current.lastRunMs
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.FRENCH) }
    when {
        current.lastFailed -> Text(
            stringResource(R.string.auto_backup_failed_status),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        last > 0 -> Hint(
            stringResource(R.string.auto_backup_last, Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()).format(formatter)),
        )
        else -> Hint(stringResource(R.string.auto_backup_never))
    }
    OutlinedButton(onClick = onBackupNow) { Text(stringResource(R.string.auto_backup_now)) }
}

/** Nom du dossier choisi, lu auprès du stockage ; à défaut, tiré de son identifiant. */
private fun folderName(context: android.content.Context, folder: String): String = runCatching {
    val tree = folder.toUri()
    val treeId = DocumentsContract.getTreeDocumentId(tree)
    val document = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
    context.contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: AutoBackupRules.folderLabel(treeId)
}.getOrDefault(folder)