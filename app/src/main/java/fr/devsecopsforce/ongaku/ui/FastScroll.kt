package fr.devsecopsforce.ongaku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** En dessous, la liste se parcourt assez vite au doigt : pas besoin d'index. */
private const val FAST_SCROLL_MIN_ITEMS = 40

/**
 * Liste avec, à droite, les lettres de l'index : toucher ou glisser sur une lettre amène la liste à
 * la première entrée de cette lettre. [letters] associe chaque lettre à sa position dans la liste.
 */
@Composable
internal fun FastScrollList(
    listState: LazyListState,
    letters: List<Pair<Char, Int>>?,
    itemCount: Int,
    content: @Composable () -> Unit,
) {
    if (letters == null || letters.size < 2 || itemCount < FAST_SCROLL_MIN_ITEMS) {
        content()
        return
    }
    var active by remember { mutableStateOf<Char?>(null) }
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { content() }
            AlphabetBar(letters, listState, onActiveChange = { active = it })
        }
        active?.let { letter ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    letter.toString(),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun AlphabetBar(
    letters: List<Pair<Char, Int>>,
    listState: LazyListState,
    onActiveChange: (Char?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var heightPx by remember { mutableIntStateOf(1) }
    var current by remember { mutableStateOf<Char?>(null) }

    fun select(y: Float) {
        val index = (y / heightPx * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val (letter, position) = letters[index]
        if (letter == current) return
        current = letter
        onActiveChange(letter)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch { listState.scrollToItem(position) }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(24.dp)
            .padding(vertical = 4.dp)
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .semantics { contentDescription = "Index alphabétique" }
            .pointerInput(letters) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    select(down.position.y)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        select(change.position.y)
                        change.consume()
                    }
                    current = null
                    onActiveChange(null)
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { (letter, _) ->
            Text(
                letter.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
