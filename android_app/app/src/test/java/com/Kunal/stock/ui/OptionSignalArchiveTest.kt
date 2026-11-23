package com.Kunal.stock.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OptionSignalArchiveTest {
    @Test
    fun archiveRoundTripsStructuredSignal() {
        val record = signal()

        assertEquals(listOf(record), OptionSignalArchive.decode(OptionSignalArchive.encode(listOf(record))))
    }

    @Test
    fun archiveRoundTripsObservedPremiumAndStillReadsVersionOneRecords() {
        val resolved = signal().copy(
            outcome = OptionSignalOutcome.TARGET_HIT,
            resolvedAtMillis = 2_000L,
            observedPremium = 138.5
        )
        val versionOne = listOf(
            "1000", "NIFTY", "NIFTY 25000 CE", "CE", "25000.0",
            "100.0", "135.0", "82.0", "2026-11-12", "v1",
            "TARGET_HIT", "2000"
        ).joinToString("\t")

        assertEquals(listOf(resolved), OptionSignalArchive.decode(OptionSignalArchive.encode(listOf(resolved))))
        assertEquals(0.0, OptionSignalArchive.decode(versionOne).single().observedPremium, 0.0)
    }

    @Test
    fun onlyObservedOptionPremiumCanResolveOutcome() {
        val open = listOf(signal())
        val unchanged = OptionSignalArchive.observePremium(
            open, "NIFTY", "2026-11-12", "CE", 25_000.0, 115.0, 2_000L
        )
        val target = OptionSignalArchive.observePremium(
            open, "NIFTY", "2026-11-12", "CE", 25_000.0, 136.0, 2_000L
        )
        val stop = OptionSignalArchive.observePremium(
            open, "NIFTY", "2026-11-12", "CE", 25_000.0, 81.0, 2_000L
        )

        assertEquals(OptionSignalOutcome.OPEN, unchanged.single().outcome)
        assertEquals(OptionSignalOutcome.TARGET_HIT, target.single().outcome)
        assertEquals(OptionSignalOutcome.STOP_HIT, stop.single().outcome)
    }

    @Test
    fun cannotResolveBeforeSignalOrFromDifferentExpiry() {
        val open = listOf(signal())
        val beforeSignal = OptionSignalArchive.observePremium(
            open, "NIFTY", "2026-11-12", "CE", 25_000.0, 140.0, 999L
        )
        val wrongExpiry = OptionSignalArchive.observePremium(
            open, "NIFTY", "2026-11-19", "CE", 25_000.0, 140.0, 2_000L
        )

        assertEquals(OptionSignalOutcome.OPEN, beforeSignal.single().outcome)
        assertEquals(OptionSignalOutcome.OPEN, wrongExpiry.single().outcome)
    }

    @Test
    fun deduplicatesRepeatedSignalForShortWindow() {
        val first = signal()
        val duplicate = first.copy(createdAtMillis = first.createdAtMillis + 1_000L)

        assertEquals(
            listOf(first),
            OptionSignalArchive.appendIfNew(listOf(first), duplicate)
        )
        assertNotEquals(
            listOf(first),
            OptionSignalArchive.appendIfNew(listOf(first), first.copy(createdAtMillis = first.createdAtMillis + 10 * 60 * 1000L))
        )
    }

    @Test
    fun archiveDoesNotTrimOlderSignalsPastOneHundredRecords() {
        val records = (1..125).map { index ->
            signal().copy(createdAtMillis = index * 60_000L, contract = "NIFTY 25000 CE #$index")
        }

        val appended = OptionSignalArchive.appendIfNew(
            records,
            signal().copy(createdAtMillis = 126 * 60_000L, contract = "NIFTY 25000 CE #126")
        )

        assertEquals(126, appended.size)
        assertEquals("NIFTY 25000 CE #1", appended.first().contract)
    }

    @Test
    fun performanceUsesOnlyResolvedPremiumOutcomes() {
        val records = listOf(
            signal().copy(outcome = OptionSignalOutcome.TARGET_HIT),
            signal().copy(createdAtMillis = 2_000L, outcome = OptionSignalOutcome.STOP_HIT),
            signal().copy(createdAtMillis = 3_000L),
            signal().copy(createdAtMillis = 4_000L, outcome = OptionSignalOutcome.UNRESOLVED)
        )

        val performance = OptionSignalArchive.performance(records)

        assertEquals(2, performance.resolved)
        assertEquals(2, performance.unresolved)
        assertEquals(50.0, performance.targetHitRatePct!!, 0.0)
    }

    @Test
    fun observedPremiumReturnUsesOnlyResolvedRecordsWithObservedPremium() {
        val records = listOf(
            signal().copy(
                outcome = OptionSignalOutcome.TARGET_HIT,
                observedPremium = 140.0
            ),
            signal().copy(
                createdAtMillis = 2_000L,
                outcome = OptionSignalOutcome.STOP_HIT,
                observedPremium = 80.0
            ),
            signal().copy(
                createdAtMillis = 3_000L,
                outcome = OptionSignalOutcome.TARGET_HIT
            ),
            signal().copy(createdAtMillis = 4_000L)
        )

        val performance = OptionSignalArchive.performance(records)

        assertEquals(2, performance.observedReturnSamples)
        assertEquals(10.0, performance.averageObservedReturnPct!!, 1e-9)
    }

    private fun signal() = ArchivedOptionSignal(
        createdAtMillis = 1_000L,
        symbol = "NIFTY",
        contract = "NIFTY 25000 CE",
        optionType = "CE",
        strike = 25_000.0,
        entryPremium = 100.0,
        targetPremium = 135.0,
        stopLossPremium = 82.0,
        expiry = "2026-11-12"
    )
}
