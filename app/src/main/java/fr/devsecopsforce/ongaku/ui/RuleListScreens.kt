// Listes par règle : éditeur des conditions, et résumé affiché sous chaque liste.
package fr.devsecopsforce.ongaku.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.devsecopsforce.ongaku.R
import fr.devsecopsforce.ongaku.data.RuleList
import fr.devsecopsforce.ongaku.data.Track

/** « Rock · 1990–1999 · 2 à 6 min · Music/Rock » : les conditions d'une liste, en une ligne. */
@Composable
internal fun ruleSummary(rule: RuleList): String {
    val parts = mutableListOf<String>()
    if (rule.genre.isNotBlank()) parts += rule.genre
    when {
        rule.yearFrom > 0 && rule.yearTo > 0 -> parts += "${rule.yearFrom}–${rule.yearTo}"
        rule.yearFrom > 0 -> parts += stringResource(R.string.rule_summary_from, rule.yearFrom)
        rule.yearTo > 0 -> parts += stringResource(R.string.rule_summary_until, rule.yearTo)
    }
    when {
        rule.minSeconds > 0 && rule.maxSeconds > 0 -> parts += "${formatTime(rule.minSeconds * 1000L)}–${formatTime(rule.maxSeconds * 1000L)}"
        rule.minSeconds > 0 -> parts += stringResource(R.string.rule_summary_longer, formatTime(rule.minSeconds * 1000L))
        rule.maxSeconds > 0 -> parts += stringResource(R.string.rule_summary_shorter, formatTime(rule.maxSeconds * 1000L))
    }
    if (rule.folder.isNotBlank()) parts += rule.folder
    return parts.joinToString(" · ")
}

/**
 * Création ou modification d'une liste par règle. [library] : morceaux affichés, pour annoncer
 * combien la règle en retient avant de l'enregistrer.
 */
@Composable
internal fun RuleListDialog(
    initial: RuleList,
    library: List<Track>,
    genres: List<String>,
    folders: List<String>,
    onConfirm: (RuleList) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var genre by rememberSaveable { mutableStateOf(initial.genre) }
    var yearFrom by rememberSaveable { mutableStateOf(numberText(initial.yearFrom)) }
    var yearTo by rememberSaveable { mutableStateOf(numberText(initial.yearTo)) }
    var minSeconds by rememberSaveable { mutableStateOf(numberText(initial.minSeconds)) }
    var maxSeconds by rememberSaveable { mutableStateOf(numberText(initial.maxSeconds)) }
    var folder by rememberSaveable { mutableStateOf(initial.folder) }

    val rule = initial.copy(
        name = name.trim(),
        genre = genre,
        yearFrom = number(yearFrom),
        yearTo = number(yearTo),
        minSeconds = number(minSeconds),
        maxSeconds = number(maxSeconds),
        folder = folder,
    )
    val count = remember(rule, library) { library.count(rule::matches) }
    val problem = when {
        !rule.hasRule -> stringResource(R.string.rule_need_condition)
        !rule.rangesValid -> stringResource(R.string.rule_invalid_range)
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.id.isEmpty()) R.string.rule_new else R.string.rule_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.rule_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Choice(stringResource(R.string.rule_genre), genre, genres) { genre = it }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(stringResource(R.string.rule_year_from), yearFrom, 4, Modifier.weight(1f)) { yearFrom = it }
                    NumberField(stringResource(R.string.rule_year_to), yearTo, 4, Modifier.weight(1f)) { yearTo = it }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(stringResource(R.string.filter_min_duration), minSeconds, 6, Modifier.weight(1f)) { minSeconds = it }
                    NumberField(stringResource(R.string.filter_max_duration), maxSeconds, 6, Modifier.weight(1f)) { maxSeconds = it }
                }
                Spacer(Modifier.height(8.dp))
                Choice(stringResource(R.string.rule_folder), folder, folders) { folder = it }
                Spacer(Modifier.height(12.dp))
                Text(
                    problem ?: pluralStringResource(R.plurals.rule_matching, count, count),
                    color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(rule) },
                enabled = rule.name.isNotEmpty() && problem == null,
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

private fun numberText(value: Int) = if (value == 0) "" else value.toString()

private fun number(text: String) = text.toIntOrNull() ?: 0

@Composable
private fun NumberField(label: String, value: String, maxDigits: Int, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(maxDigits)) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** Un choix dans une liste, ou « Tous » (valeur vide). */
@Composable
private fun Choice(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val any = stringResource(R.string.rule_any)
    Text(label, style = MaterialTheme.typography.labelLarge)
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(value.ifBlank { any }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (listOf("") + options).forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.ifBlank { any }) },
                    onClick = {
                        open = false
                        onChange(option)
                    },
                )
            }
        }
    }
}
