package com.Kunal.stock.ui

import com.Kunal.stock.data.OptionSentimentData
import com.Kunal.stock.data.OptionStrike
import kotlin.math.abs

internal data class OptionAnalysisDiagnostics(
    val chainFresh: Boolean,
    val chainAgeSeconds: Long?,
    val validChainRows: Int,
    val nearAtmRows: Int,
    val recentMinuteBuckets: Int,
    val qualityRead: String,
    val nearAtmFlow: NearAtmOptionFlow
)

internal data class NearAtmOptionFlow(
    val rowCount: Int = 0,
    val callOiChange: Long = 0L,
    val putOiChange: Long = 0L,
    val callVolume: Long = 0L,
    val putVolume: Long = 0L,
    val callBuildRead: String = "Unavailable",
    val putBuildRead: String = "Unavailable"
)

internal object OptionAnalysisDiagnosticsCalculator {
    private const val nearAtmWindow = 0.02
    private const val chainFreshnessMillis = 6 * 60 * 1000L
    private const val historyWindowMillis = 2 * 60 * 60 * 1000L

    fun calculate(
        data: OptionSentimentData,
        spotQuoteFresh: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): OptionAnalysisDiagnostics {
        val chainTimestamp = parseOptionChainUpdatedAt(data.chainUpdatedAt)
        val ageMillis = chainTimestamp?.let { nowMillis - it }
        val chainAgeSeconds = ageMillis?.takeIf { it >= 0L }?.div(1000L)
        val chainFresh = ageMillis != null && ageMillis in 0..chainFreshnessMillis
        val validRows = data.optionChain.filter { row ->
            row.strike.isFinite() && row.strike > 0.0 &&
                (row.callOi > 0L || row.putOi > 0L ||
                    row.callVolume > 0L || row.putVolume > 0L ||
                    row.callLtp > 0.0 || row.putLtp > 0.0)
        }
        val nearRows = if (data.spotPrice.isFinite() && data.spotPrice > 0.0) {
            validRows.filter { abs(it.strike - data.spotPrice) / data.spotPrice <= nearAtmWindow }
        } else {
            emptyList()
        }
        val recentMinuteBuckets = data.spotHistory.asSequence()
            .filter { it.time > 0L && it.value.isFinite() && it.value > 0.0 }
            .map { point ->
                val timestamp = if (point.time < 100_000_000_000L) point.time * 1000L else point.time
                timestamp
            }
            .filter { it in (nowMillis - historyWindowMillis)..(nowMillis + 60_000L) }
            .map { Math.floorDiv(it, 60_000L) }
            .distinct()
            .count()

        val qualityRead = when {
            !spotQuoteFresh -> "STALE SPOT QUOTE"
            !chainFresh -> "STALE OR UNAVAILABLE OPTION CHAIN"
            nearRows.isEmpty() -> "NO VALID NEAR-ATM CHAIN DATA"
            nearRows.size < 3 -> "LIMITED NEAR-ATM CHAIN COVERAGE"
            recentMinuteBuckets < 6 -> "COLLECTING RECENT PRICE SAMPLES"
            else -> "LIVE INPUTS AVAILABLE"
        }
        return OptionAnalysisDiagnostics(
            chainFresh = chainFresh,
            chainAgeSeconds = chainAgeSeconds,
            validChainRows = validRows.size,
            nearAtmRows = nearRows.size,
            recentMinuteBuckets = recentMinuteBuckets,
            qualityRead = qualityRead,
            nearAtmFlow = summarizeNearAtmFlow(nearRows)
        )
    }

    private fun summarizeNearAtmFlow(rows: List<OptionStrike>): NearAtmOptionFlow =
        NearAtmOptionFlow(
            rowCount = rows.size,
            callOiChange = rows.sumOf { it.callOiChange },
            putOiChange = rows.sumOf { it.putOiChange },
            callVolume = rows.sumOf { it.callVolume },
            putVolume = rows.sumOf { it.putVolume },
            callBuildRead = dominantBuildRead(rows.map { it.callBuild }),
            putBuildRead = dominantBuildRead(rows.map { it.putBuild })
        )

    private fun dominantBuildRead(values: List<String>): String {
        val counts = values.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("Flat / Unclear", ignoreCase = true) }
            .groupingBy { it }
            .eachCount()
        return counts.maxByOrNull { it.value }
            ?.let { "${it.key} ×${it.value}" }
            ?: "No clear build tag"
    }
}
