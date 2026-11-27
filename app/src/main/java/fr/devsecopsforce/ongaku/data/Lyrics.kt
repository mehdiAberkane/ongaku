package fr.devsecopsforce.ongaku.data

/** Une ligne de paroles ; [timeMs] null pour des paroles sans horodatage. */
data class LyricLine(val timeMs: Long?, val text: String)

/** Paroles d'un morceau, synchronisées quand leurs lignes ont des horodatages. */
data class Lyrics(val lines: List<LyricLine>) {
    val synced: Boolean get() = lines.isNotEmpty() && lines.all { it.timeMs != null }

    /** Ligne en cours à [positionMs] : la dernière déjà commencée ; -1 avant la première. */
    fun currentLine(positionMs: Long): Int {
        if (!synced) return -1
        var current = -1
        for ((index, line) in lines.withIndex()) {
            if ((line.timeMs ?: return current) <= positionMs) current = index else break
        }
        return current
    }
}

/**
 * Format LRC : « [01:23.45]texte », plusieurs horodatages possibles sur une ligne, décalage
 * « [offset:+500] » en millisecondes (positif : les paroles arrivent plus tôt). Les autres balises
 * ([ar:], [ti:]…) sont ignorées. Un texte sans aucun horodatage donne des paroles simples.
 */
object LrcParser {
    private val TIME = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val OFFSET = Regex("""^\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("""^\[[a-zA-Z]+:.*]$""")

    fun parse(text: String): Lyrics? {
        val rawLines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val offset = rawLines.firstNotNullOfOrNull { OFFSET.find(it.trim())?.groupValues?.get(1)?.toLongOrNull() } ?: 0L
        val timed = mutableListOf<LyricLine>()
        val plain = mutableListOf<LyricLine>()
        for (raw in rawLines) {
            val line = raw.trim()
            var rest = line
            val times = mutableListOf<Long>()
            while (true) {
                val match = TIME.find(rest) ?: break
                if (match.range.first != 0) break
                times += toMs(match) - offset
                rest = rest.substring(match.range.last + 1)
            }
            when {
                times.isNotEmpty() -> times.forEach { timed += LyricLine(it.coerceAtLeast(0), rest.trim()) }
                line.isEmpty() || TAG.matches(line) -> Unit
                else -> plain += LyricLine(null, line)
            }
        }
        val lines = if (timed.isNotEmpty()) timed.sortedBy { it.timeMs } else plain
        return if (lines.isEmpty()) null else Lyrics(lines)
    }

    private fun toMs(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLong()
        val seconds = match.groupValues[2].toLong()
        val fraction = match.groupValues[3]
        // « .5 » = 500 ms, « .45 » = 450 ms, « .456 » = 456 ms.
        val millis = if (fraction.isEmpty()) 0L else fraction.padEnd(3, '0').take(3).toLong()
        return (minutes * 60 + seconds) * 1000 + millis
    }
}
