package tv.withaibuild.customiuizer

import org.junit.Assert.*
import org.junit.Test

class PreferenceCardsTest {
    @Test fun sectionsNeverJoinAcrossHeadersIncludingHiddenPageContainers() {
        val rows = PreferenceCards.layout(listOf(
            PreferenceCards.Row(true, true), PreferenceCards.Row(), PreferenceCards.Row(),
            PreferenceCards.Row(true), PreferenceCards.Row()))
        assertTrue(rows[0].hidden)
        assertTrue(rows[1].first)
        assertFalse(rows[1].last)
        assertTrue(rows[2].last)
        assertFalse(rows[2].first)
        assertTrue(rows[4].first && rows[4].last)
    }

    @Test fun singleToggleAndDynamicRemovalKeepValidCardEnds() {
        val before = PreferenceCards.layout(listOf(PreferenceCards.Row(), PreferenceCards.Row()))
        assertTrue(before.first().first)
        assertTrue(before.last().last)
        val after = PreferenceCards.layout(listOf(PreferenceCards.Row()))
        assertTrue(after.single().first && after.single().last)
        assertTrue(PreferenceCards.layout(emptyList()).isEmpty())
    }

    @Test fun consecutiveOrEmptyHeadersDoNotBecomeSettingsCards() {
        val rows = PreferenceCards.layout(listOf(PreferenceCards.Row(true), PreferenceCards.Row(true), PreferenceCards.Row()))
        assertTrue(rows.take(2).all { it.header && !it.first && !it.last })
        assertTrue(rows.last().first && rows.last().last)
    }
}
