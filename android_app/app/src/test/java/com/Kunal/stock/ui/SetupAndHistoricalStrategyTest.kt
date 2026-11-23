package com.Kunal.stock.ui

import com.Kunal.stock.data.HeroZeroAnalysis
import com.Kunal.stock.data.LiveQuote
import com.Kunal.stock.data.OptionSentimentData
import com.Kunal.stock.data.OptionStrike
import com.Kunal.stock.data.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class SetupAndHistoricalStrategyTest {
    @Test
    fun setupWindowsIncludeFirstHourAndExpiryReview() {
        val firstHour = TimeContextAgent.analyze(
            SetupAgentInput(hhmm = 1020, isExpiryDay = false)
        )
        val expiryWindow = TimeContextAgent.analyze(
            SetupAgentInput(hhmm = 1320, isExpiryDay = true)
        )

        assertTrue(2 in firstHour.activeSetups)
        assertTrue(3 in expiryWindow.activeSetups)
        assertEquals("OPEN", expiryWindow.windowStatus)
    }

    @Test
    fun setupRulesAndRiskGuardDoNotPassWithMissingInputs() {
        val input = SetupAgentInput(
            hhmm = 1320,
            isExpiryDay = true,
            selectedSetup = 3
        )

        assertFalse(RuleCheckAgent.analyze(input).setup3Met)
        assertFalse(RiskGuardAgent.analyze(input).capitalSafe)
        assertEquals("NO-GO", SetupMasterAgent.analyze(input).verdict)
    }

    @Test
    fun journalPnlDrivesDailyAndWeeklyRiskStops() {
        val india = TimeZone.getTimeZone("Asia/Kolkata")
        val now = Calendar.getInstance(india).apply {
            set(2026, Calendar.SEPTEMBER, 24, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val summary = OptionTradeJournalStore.pnlSummary(
            listOf(
                journalEntry(date = "2026-09-24", pnl = "-1000"),
                journalEntry(date = "2026-09-23", pnl = "-2000"),
                journalEntry(date = "2026-09-14", pnl = "-5000")
            ),
            now
        )
        val risk = RiskGuardAgent.analyze(
            SetupAgentInput(
                hhmm = 1000,
                isExpiryDay = false,
                capital = 100_000.0,
                lotSize = 1,
                maxLossPerLot = 100.0,
                dailyPnlRs = summary.dailyPnlRs,
                weeklyPnlRs = summary.weeklyPnlRs
            )
        )

        assertEquals(-1000.0, summary.dailyPnlRs, 0.0)
        assertEquals(-3000.0, summary.weeklyPnlRs, 0.0)
        assertTrue(risk.dailyStopBreached)
        assertTrue(risk.weeklyStopBreached)
        assertFalse(risk.capitalSafe)
    }

    @Test
    fun journalAcceptsOnlyCompleteCalendarDates() {
        assertTrue(OptionTradeJournalStore.isValidDate("2026-09-24"))
        assertTrue(OptionTradeJournalStore.isValidDate("24/09/2026"))
        assertTrue(OptionTradeJournalStore.isValidDate("24/09"))
        assertFalse(OptionTradeJournalStore.isValidDate("2026-02-30"))
        assertFalse(OptionTradeJournalStore.isValidDate(""))
    }

    @Test
    fun heroZeroRequiresCurrentExpiryAndMatchingLiveContract() {
        val india = TimeZone.getTimeZone("Asia/Kolkata")
        val now = Calendar.getInstance(india).apply {
            set(2026, Calendar.SEPTEMBER, 24, 13, 20, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val expiry = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = india }
            .format(now)
        val chainTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            .apply { timeZone = india }
            .format(now - 30_000L)
        val data = OptionSentimentData(
            symbol = "NIFTY",
            spotPrice = 22_500.0,
            chainExpiry = expiry,
            chainUpdatedAt = chainTime,
            optionChain = listOf(OptionStrike(strike = 22_500.0, callLtp = 20.0))
        )
        val hero = HeroZeroAnalysis(
            isHeroZeroCandidate = true,
            contract = "NIFTY 22500 CE",
            entryPremium = 20.0,
            target1Premium = 40.0,
            stopLossPremium = 8.0
        )

        assertTrue(evaluateHeroZeroReview(hero, data, true, now).ready)
        assertFalse(evaluateHeroZeroReview(hero, data.copy(chainExpiry = "2026-09-25"), true, now).ready)
        assertFalse(evaluateHeroZeroReview(hero.copy(contract = "NIFTY 22500 PE"), data, true, now).ready)
    }

    @Test
    fun liveSetupCheckWaitsWhenSpotQuoteIsStale() {
        val now = 1_800_000_000_000L
        val data = OptionSentimentData(
            symbol = "NIFTY",
            chainUpdatedAt = formatChainTime(now - 30_000L)
        )
        val quote = LiveQuote(
            spotPrice = 22_500.0,
            tickAtEpoch = (now - 6_000L) / 1000.0,
            tickAgeSeconds = 6
        )

        val result = LiveSetupCheck.evaluate(UiState.Success(data), quote, now)

        assertFalse(result.spotFresh)
        assertEquals("WAIT", result.status)
    }

    @Test
    fun dailyBreakoutAndSqueezeSignalsRequireTheirExplicitRules() {
        val breakoutCandles = (0..20).map { index ->
            if (index == 20) {
                HistoricalBacktestEngine.Candle(index.toLong(), 99.0, 102.0, 98.0, 101.0, 100)
            } else {
                HistoricalBacktestEngine.Candle(index.toLong(), 99.0, 100.0, 98.0, 99.0, 100)
            }
        }
        val unused = List(breakoutCandles.size) { Double.NaN }
        val noAtr = List(breakoutCandles.size) { 0.0 }
        assertTrue(
            HistoricalBacktestEngine.isEntrySignal(
                breakoutCandles, 20, "BREAKOUT", unused, unused, unused, noAtr
            )
        )
        assertFalse(
            HistoricalBacktestEngine.isEntrySignal(
                breakoutCandles, 20, "OPTION_FLOW_GRAVITY", unused, unused, unused, noAtr
            )
        )

        val squeezeCandles = (0..41).map { index ->
            if (index == 41) {
                HistoricalBacktestEngine.Candle(index.toLong(), 100.0, 103.0, 99.0, 102.0, 130)
            } else {
                HistoricalBacktestEngine.Candle(index.toLong(), 99.0, 100.0, 98.0, 99.0, 100)
            }
        }
        val atr = MutableList(squeezeCandles.size) { 10.0 }
        atr[40] = 5.0
        val unusedSqueeze = List(squeezeCandles.size) { Double.NaN }

        assertTrue(
            HistoricalBacktestEngine.isEntrySignal(
                squeezeCandles, 41, "ATR_SQUEEZE_BREAKOUT", unusedSqueeze, unusedSqueeze, unusedSqueeze, atr
            )
        )
        assertFalse(
            HistoricalBacktestEngine.isEntrySignal(
                squeezeCandles, 41, "ATR_SQUEEZE_BREAKOUT", unusedSqueeze, unusedSqueeze, unusedSqueeze, List(42) { 10.0 }
            )
        )
    }

    private fun formatChainTime(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }
            .format(epochMillis)

    private fun journalEntry(date: String, pnl: String) = JournalEntry(
        id = 1,
        date = date,
        setup = "Setup 1",
        entryTime = "",
        side = "CE",
        strike = "",
        buyPrice = "",
        indexEntry = "",
        stop = "",
        target = "",
        exitTime = "",
        exitReason = "",
        pnlRs = pnl,
        followedRules = true
    )
}
