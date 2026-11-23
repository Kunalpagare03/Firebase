package com.Kunal.stock.ui

import com.Kunal.stock.data.MultiHorizonProbabilities

internal data class PublishedHorizonForecast(
    val horizon: String,
    val bullishPct: Double,
    val bearishPct: Double,
    val sidewaysPct: Double
)

internal object PublishedHorizonForecasts {
    fun validForecasts(source: MultiHorizonProbabilities): List<PublishedHorizonForecast> =
        listOf(
            PublishedHorizonForecast(
                "1 candle",
                source.horizon1CandleBullish,
                source.horizon1CandleBearish,
                source.horizon1CandleSideways
            ),
            PublishedHorizonForecast(
                "3 candles",
                source.horizon3CandleBullish,
                source.horizon3CandleBearish,
                source.horizon3CandleSideways
            ),
            PublishedHorizonForecast(
                "5 candles",
                source.horizon5CandleBullish,
                source.horizon5CandleBearish,
                source.horizon5CandleSideways
            ),
            PublishedHorizonForecast(
                "10 candles",
                source.horizon10CandleBullish,
                source.horizon10CandleBearish,
                source.horizon10CandleSideways
            )
        ).filter { forecast ->
            val values = listOf(forecast.bullishPct, forecast.bearishPct, forecast.sidewaysPct)
            values.all { it.isFinite() && it in 0.0..100.0 } &&
                values.sum() in 98.0..102.0
        }
}
