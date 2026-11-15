package app.plainlauncher

import app.plainlauncher.data.AppSearch
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSearchTest {
    @Test fun letterOf_handlesDigitsSymbolsAndEmpty() {
        assertEquals('A', AppSearch.letterOf("  apple"))
        assertEquals('#', AppSearch.letterOf("1Password"))
        assertEquals('#', AppSearch.letterOf(""))
        assertEquals('#', AppSearch.letterOf("متجر"))
    }

    @Test fun rank_prefixBeforeWordBeforeContains() {
        assertEquals(0, AppSearch.rank("Chrome", "ch"))
        assertEquals(1, AppSearch.rank("Google Chrome", "ch"))
        assertEquals(2, AppSearch.rank("Search", "arc"))
        assertEquals(-1, AppSearch.rank("Maps", "zzz"))
        assertEquals(0, AppSearch.rank("Anything", "  "))
    }

    @Test fun filter_ordersByRankAndKeepsOrderWithinRank() {
        val labels = listOf("Camera", "Chrome", "Google Chrome", "Search", "Settings")
        // "ch": prefix (Chrome), then word-prefix (Google Chrome), then contains (Search)
        assertEquals(listOf("Chrome", "Google Chrome", "Search"), AppSearch.filter(labels, "ch") { it })
        assertEquals(listOf("Search", "Settings"), AppSearch.filter(labels, "se") { it })
        assertEquals(labels, AppSearch.filter(labels, "") { it })
    }

    @Test fun sectionStarts_pointsAtFirstItemPerLetter() {
        val starts = AppSearch.sectionStarts(listOf("Alpha", "Apple", "Bravo", "1Pass"))
        assertEquals(0, starts[0]) // A
        assertEquals(2, starts[1]) // B
        assertEquals(-1, starts[2]) // C
        assertEquals(3, starts[AppSearch.LETTERS.indexOf('#')])
    }
}
