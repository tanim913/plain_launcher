package app.plainlauncher.data

/** Pure search/section helpers, kept free of Android types so they unit-test on the JVM. */
object AppSearch {
    const val LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#"

    fun letterOf(label: String): Char {
        val c = label.trim().firstOrNull()?.uppercaseChar() ?: return '#'
        return if (c in 'A'..'Z') c else '#'
    }

    /** 0 = starts with the query, 1 = a word starts with it, 2 = contains it, -1 = no match. */
    fun rank(label: String, query: String): Int {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return 0
        val l = label.lowercase()
        if (l.startsWith(q)) return 0
        if (l.split(' ', '-', '.', '_').any { it.startsWith(q) }) return 1
        if (l.contains(q)) return 2
        return -1
    }

    fun <T> filter(items: List<T>, query: String, label: (T) -> String): List<T> {
        if (query.isBlank()) return items
        return items
            .mapNotNull { item -> rank(label(item), query).let { if (it < 0) null else it to item } }
            .sortedBy { it.first }
            .map { it.second }
    }

    /** For each letter in [LETTERS]: index of the first item at or after that letter, or -1. */
    fun sectionStarts(labels: List<String>): IntArray {
        val first = HashMap<Char, Int>()
        labels.forEachIndexed { i, l -> first.putIfAbsent(letterOf(l), i) }
        return IntArray(LETTERS.length) { first[LETTERS[it]] ?: -1 }
    }
}
