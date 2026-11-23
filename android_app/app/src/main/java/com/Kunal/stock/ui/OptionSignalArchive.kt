package com.Kunal.stock.ui

internal enum class OptionSignalOutcome {
    OPEN,
    TARGET_HIT,
    STOP_HIT,
    UNRESOLVED
}

internal data class ArchivedOptionSignal(
    val createdAtMillis: Long,
    val symbol: String,
    val contract: String,
    val optionType: String,
    val strike: Double,
    val entryPremium: Double,
    val targetPremium: Double,
    val stopLossPremium: Double,
    val expiry: String,
    val outcome: OptionSignalOutcome = OptionSignalOutcome.OPEN,
    val resolvedAtMillis: Long = 0L,
    val observedPremium: Double = 0.0
) {
    fun display(): String {
        val timestamp = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        }.format(java.util.Date(createdAtMillis))
        val status = when (outcome) {
            OptionSignalOutcome.OPEN -> "OPEN · awaiting observed option LTP"
            OptionSignalOutcome.TARGET_HIT -> "TARGET HIT"
            OptionSignalOutcome.STOP_HIT -> "STOP HIT"
            OptionSignalOutcome.UNRESOLVED -> "UNRESOLVED · contract no longer in current chain"
        }
        val observed = observedPremium.takeIf { it.isFinite() && it > 0.0 }
            ?.let { " | First observed ₹${priceLabel(it)}" }
            .orEmpty()
        return "$timestamp | $symbol ${strikeLabel(strike)} $optionType" +
            (if (expiry.isNotBlank()) " · $expiry" else "") +
            " | Entry ₹${priceLabel(entryPremium)}$observed | " +
            "Target ₹${priceLabel(targetPremium)} | SL ₹${priceLabel(stopLossPremium)} | $status"
    }

    private fun strikeLabel(value: Double) =
        if (value % 1.0 == 0.0) value.toLong().toString() else String.format("%.1f", value)

    private fun priceLabel(value: Double) = String.format("%.2f", value)
}

internal data class OptionSignalPerformance(
    val targetHits: Int,
    val stopHits: Int,
    val unresolved: Int,
    val observedReturnSamples: Int,
    val averageObservedReturnPct: Double?
) {
    val resolved: Int get() = targetHits + stopHits
    val targetHitRatePct: Double?
        get() = if (resolved == 0) null else targetHits * 100.0 / resolved
}

internal object OptionSignalArchive {
    private const val FIELD_SEPARATOR = '\t'

    fun decode(raw: String): List<ArchivedOptionSignal> = raw.lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val fields = line.split(FIELD_SEPARATOR)
            val isV1 = fields.size == 12 && fields[9] == "v1"
            val isV2 = fields.size == 13 && fields[9] == "v2"
            if (!isV1 && !isV2) return@mapNotNull null
            val outcome = when (fields[10]) {
                OptionSignalOutcome.OPEN.name -> OptionSignalOutcome.OPEN
                OptionSignalOutcome.TARGET_HIT.name -> OptionSignalOutcome.TARGET_HIT
                OptionSignalOutcome.STOP_HIT.name -> OptionSignalOutcome.STOP_HIT
                OptionSignalOutcome.UNRESOLVED.name -> OptionSignalOutcome.UNRESOLVED
                else -> return@mapNotNull null
            }
            ArchivedOptionSignal(
                createdAtMillis = fields[0].toLongOrNull() ?: return@mapNotNull null,
                symbol = fields[1],
                contract = fields[2],
                optionType = fields[3],
                strike = fields[4].toDoubleOrNull() ?: return@mapNotNull null,
                entryPremium = fields[5].toDoubleOrNull() ?: return@mapNotNull null,
                targetPremium = fields[6].toDoubleOrNull() ?: return@mapNotNull null,
                stopLossPremium = fields[7].toDoubleOrNull() ?: return@mapNotNull null,
                expiry = fields[8],
                outcome = outcome,
                resolvedAtMillis = fields[11].toLongOrNull() ?: return@mapNotNull null,
                observedPremium = if (isV2) {
                    fields[12].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
                        ?: return@mapNotNull null
                } else 0.0
            )
        }
        .toList()

    fun encode(records: List<ArchivedOptionSignal>): String = records.joinToString("\n") { record ->
        listOf(
            record.createdAtMillis,
            record.symbol,
            record.contract,
            record.optionType,
            record.strike,
            record.entryPremium,
            record.targetPremium,
            record.stopLossPremium,
            record.expiry,
            "v2",
            record.outcome.name,
            record.resolvedAtMillis,
            record.observedPremium
        ).joinToString(FIELD_SEPARATOR.toString())
    }

    fun performance(records: List<ArchivedOptionSignal>): OptionSignalPerformance {
        val resolvedRecords = records.filter {
            it.outcome == OptionSignalOutcome.TARGET_HIT || it.outcome == OptionSignalOutcome.STOP_HIT
        }
        val observedReturns = resolvedRecords.mapNotNull { record ->
            if (
                record.entryPremium.isFinite() && record.entryPremium > 0.0 &&
                record.observedPremium.isFinite() && record.observedPremium > 0.0
            ) {
                (record.observedPremium / record.entryPremium - 1.0) * 100.0
            } else null
        }.filter { it.isFinite() }
        return OptionSignalPerformance(
            targetHits = records.count { it.outcome == OptionSignalOutcome.TARGET_HIT },
            stopHits = records.count { it.outcome == OptionSignalOutcome.STOP_HIT },
            unresolved = records.count {
                it.outcome == OptionSignalOutcome.OPEN || it.outcome == OptionSignalOutcome.UNRESOLVED
            },
            observedReturnSamples = observedReturns.size,
            averageObservedReturnPct = observedReturns.average().takeIf { it.isFinite() }
        )
    }

    fun appendIfNew(
        records: List<ArchivedOptionSignal>,
        candidate: ArchivedOptionSignal,
        dedupeWindowMillis: Long = 5 * 60 * 1000L
    ): List<ArchivedOptionSignal> {
        val duplicateExists = records.any {
            it.symbol == candidate.symbol &&
                it.contract == candidate.contract &&
                it.expiry == candidate.expiry &&
                candidate.createdAtMillis - it.createdAtMillis in 0..dedupeWindowMillis
        }
        return if (duplicateExists) records else records + candidate
    }

    fun observePremium(
        records: List<ArchivedOptionSignal>,
        symbol: String,
        expiry: String,
        optionType: String,
        strike: Double,
        premium: Double,
        observedAtMillis: Long
    ): List<ArchivedOptionSignal> {
        if (!premium.isFinite() || premium <= 0.0) return records
        return records.map { record ->
            if (
                record.outcome != OptionSignalOutcome.OPEN ||
                record.symbol != symbol ||
                (record.expiry.isNotBlank() && record.expiry != expiry) ||
                record.optionType != optionType ||
                kotlin.math.abs(record.strike - strike) >= 0.5 ||
                observedAtMillis <= record.createdAtMillis
            ) {
                record
            } else {
                val outcome = when {
                    premium >= record.targetPremium -> OptionSignalOutcome.TARGET_HIT
                    premium <= record.stopLossPremium -> OptionSignalOutcome.STOP_HIT
                    else -> OptionSignalOutcome.OPEN
                }
                if (outcome == OptionSignalOutcome.OPEN) record
                else record.copy(
                    outcome = outcome,
                    resolvedAtMillis = observedAtMillis,
                    observedPremium = premium
                )
            }
        }
    }
}
