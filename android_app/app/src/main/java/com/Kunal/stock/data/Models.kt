package com.Kunal.stock.data

sealed interface UiState<out T> {
    object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class ChartPoint(val time: Long = 0L, val value: Double = 0.0)
data class StockCandle(
    val time: Long = 0L, val open: Double = 0.0, val high: Double = 0.0,
    val low: Double = 0.0, val close: Double = 0.0, val volume: Long = 0
)
data class OptionStrike(
    val strike: Double = 0.0, val callOi: Long = 0, val putOi: Long = 0,
    val totalOi: Long = 0, val callOiChange: Long = 0, val putOiChange: Long = 0,
    val callVolume: Long = 0, val putVolume: Long = 0,
    val callLtp: Double = 0.0, val putLtp: Double = 0.0,
    val callIv: Double = 0.0, val putIv: Double = 0.0,
    val callBuild: String = "", val putBuild: String = "", val oiSide: String = ""
)

data class StockScanItem(
    val symbol: String = "",
    val entry: Double = 0.0,
    val close: Double = 0.0,
    val changePct: Double = 0.0,
    val volumeRatio: Double = 0.0,
    val rsi14: Double = 0.0,
    val pattern: String = "",
    val score: Int = 0,
    val signals: List<String> = emptyList(),
    val target2Pct: Double = 0.0,
    val stopLoss: Double = 0.0,
    val riskReward: Double = 0.0,
    val recommendation: String = "",
    val screeners: List<String> = emptyList(),
    val sector: String = "",
    val rating: String = "",
    val barDate: String = "",
    val liveBar: Boolean = false,
    val targetPrice: Double = 0.0,
    val ema20: Double = 0.0,
    val tradedVolume: Long = 0,
    val averageVolume: Long = 0,
    val candlePattern: String = "",
    val chartPattern: String = "",
    val basePattern: String = "",
    val baseScore: Int = 0,
    val baseBuyPoint: Double = 0.0,
    val baseBreakout: Boolean = false,
    val analysisSide: String = "Mixed",
    val candles: List<StockCandle> = emptyList()
)

data class OptionSentimentData(
    val symbol: String = "NIFTY",
    val spotPrice: Double = 0.0,
    val finalSignal: String = "Neutral",
    val combinedSentimentRead: String = "Neutral",
    val pcrOi: Double = 0.0,
    val pcrVolume: Double = 0.0,
    val deepDirection: String = "WAIT",
    val deepEntryCondition: String = "",
    val deepTargetSpot: Double = 0.0,
    val deepInvalidationSpot: Double = 0.0,
    val candidateContract: String = "WAIT",
    val candidatePremium: Double = 0.0,
    val liveTickRate10s: Double = 0.0,
    val liveMove10sPct: Double = 0.0,
    val rapidActivity: Boolean = false,
    val rapidActivityNote: String = "",
    val activityEvents: List<RapidActivityEvent> = emptyList(),
    val maxPain: Double = 0.0,
    val supportZones: List<Double> = emptyList(),
    val resistanceZones: List<Double> = emptyList(),
    val oiBuildupOverall: String = "",
    val oiDirectionalRead: String = "",
    val oiTrend: String = "Stable",
    val tradeAction: String = "WAIT",
    val tradeTrigger: String = "",
    val tradeNote: String = "",
    val fetchedAt: String = "",
    val marketStatusStr: String = "",
    val rsi: String = "-",
    val macd: String = "-",
    val trendline: String = "N/A",
    val candle: String = "N/A",
    val analysisConfidence: String = "Low",
    val analysisSummary: String = "",
    val chartPattern: String = "Building data",
    val chartTrend: String = "Neutral",
    val marketPressure: String = "Unavailable",
    val volumePcr: Double = 0.0,
    val greeksSummary: String = "Greeks unavailable",
    val nextCandleOutlook: String = "Waiting for live candle data.",
    val spotHistory: List<ChartPoint> = emptyList(),
    val flow: String = "", val oiDeltas: String = "", val manipulationRisk: String = "",
    val manipulationNote: String = "", val priceAction: String = "", val priceClose: Double = 0.0,
    val priceChangePct: Double = 0.0, val sma20: Double = 0.0, val greekStrike: Double = 0.0,
    val callDelta: Double = 0.0, val putDelta: Double = 0.0, val gamma: Double = 0.0,
    val vega: Double = 0.0, val topStrikes: List<OptionStrike> = emptyList(),
    val optionChain: List<OptionStrike> = emptyList(),
    val chainExpiry: String = "", val chainUpdatedAt: String = "",
    val liveTradeSignal: LiveTradeSignal = LiveTradeSignal(),
    val proofOfEvidence: ProofOfEvidence = ProofOfEvidence(),
    val bigMomentumAlert: BigMomentumAlert = BigMomentumAlert(),
    val timeWindowReversal: TimeWindowReversal = TimeWindowReversal(),
    val hftBurstAlert: HftBurstAlert = HftBurstAlert(),
    val quantOptionChain: QuantOptionChainAnalysis = QuantOptionChainAnalysis(),
    val regimeName: String = "Trending (Bullish)",
    val expectedValue: Double = 0.0,
    val heroZeroAnalysis: HeroZeroAnalysis = HeroZeroAnalysis(),
    val multiHorizonProbabilities: MultiHorizonProbabilities = MultiHorizonProbabilities(),
    val fakeBreakoutAnalysis: FakeBreakoutAnalysis = FakeBreakoutAnalysis()
)

data class MultiHorizonProbabilities(
    val horizon1CandleBullish: Double = 0.0,
    val horizon1CandleBearish: Double = 0.0,
    val horizon1CandleSideways: Double = 0.0,
    val horizon3CandleBullish: Double = 0.0,
    val horizon3CandleBearish: Double = 0.0,
    val horizon3CandleSideways: Double = 0.0,
    val horizon5CandleBullish: Double = 0.0,
    val horizon5CandleBearish: Double = 0.0,
    val horizon5CandleSideways: Double = 0.0,
    val horizon10CandleBullish: Double = 0.0,
    val horizon10CandleBearish: Double = 0.0,
    val horizon10CandleSideways: Double = 0.0
)

data class HeroZeroAnalysis(
    val isHeroZeroCandidate: Boolean = false,
    val recommendation: String = "NO_HERO_ZERO_TRADE",
    val reason: String = "",
    val contract: String = "",
    val entryPremium: Double = 0.0,
    val target1Premium: Double = 0.0,
    val target2Premium: Double = 0.0,
    val stopLossPremium: Double = 0.0,
    val targetMultiplier: String = "1.0",
    val expectedHoldingTime: String = ""
)

data class FakeBreakoutAnalysis(
    val status: String = "NORMAL_PRICE_ACTION",
    val isFakeBreakout: Boolean = false,
    val isFakeBreakdown: Boolean = false,
    val hasTrap: Boolean = false,
    val recommendation: String = "NEUTRAL",
    val reason: String = "",
    val upperWickRatio: Double = 0.0,
    val lowerWickRatio: Double = 0.0,
    val volumeRatio: Double = 0.0
)

data class LiveTradeSignal(
    val recommendedContract: String = "NO ACTIVE SIGNAL",
    val action: String = "WAIT",
    val optionType: String = "",
    val strike: Double = 0.0,
    val entrySpot: Double = 0.0,
    val targetSpot: Double = 0.0,
    val stopLossSpot: Double = 0.0,
    val estimatedPremium: Double = 0.0,
    val targetPremium: Double = 0.0,
    val stopLossPremium: Double = 0.0,
    val riskRewardRatio: String = "1:2.0",
    val confidenceScore: Int = 0
)

data class ProofOfEvidence(
    val oiFlowProof: String = "",
    val priceActionProof: String = "",
    val gammaDeltaProof: String = ""
)

data class BigMomentumAlert(
    val isImminent: Boolean = false,
    val title: String = "",
    val similarityPct: Int = 0,
    val historicalPrecedent: String = "",
    val whyItHappenedHistory: String = "",
    val actionPlan: String = ""
)

data class TimeWindowReversal(
    val timeWindow: String = "09:15 - 09:45 (Opening Drive)",
    val windowWeight: Double = 0.85,
    val reversalType: String = "Opening Drive Volatility Reversal",
    val candlestickPattern: String = "Consolidation Doji",
    val reversalScore: Int = 45,
    val directionalBias: String = "Neutral / Wait",
    val nearSupport: Boolean = false,
    val nearResistance: Boolean = false,
    val commentary: String = ""
)

data class HftBurstAlert(
    val isActive: Boolean = false,
    val burstType: String = "Standard Liquidity Flow",
    val surgeMultiplier: Double = 1.0,
    val recommendedReaction: String = "Normal Execution"
)

data class QuantOptionChainAnalysis(
    val ivSkewPct: Double = 1.2,
    val ivSkewRead: String = "Neutral / Slight Put Skew (+1.2%)",
    val netGexMillion: Double = -0.85,
    val gexRegime: String = "Negative GEX Squeeze (High Volatility Acceleration)",
    val vannaDrag: Double = 0.042,
    val charmDrag: Double = -0.018,
    val maxPainStrike: Double = 0.0,
    val maxPainGravityPct: Double = 0.0,
    val callWallStrike: Double = 0.0,
    val callWallOi: Long = 0,
    val putWallStrike: Double = 0.0,
    val putWallOi: Long = 0,
    val pcrOi: Double = 1.0,
    val pcrVolume: Double = 1.0,
    val institutionalRead: String = "Heavy Put Defense at Support. Gamma Squeeze Risk Active."
)

data class StrategyBlueprint(
    val name: String = "Master Confluence Quant",
    val type: String = "MULTI_CONFLUENCE_QUANT",
    val rules: List<String> = listOf(
        "Buy Call when Price > 20 EMA, PCR OI > 1.15, and Bullish Candlestick forms at Support.",
        "Buy Put when Price < 20 EMA, PCR OI < 0.85, and Bearish Candlestick forms at Resistance.",
        "Dynamic ATR trailing stop loss at 1.5x ATR."
    ),
    val indicators: List<String> = listOf("20 EMA", "RSI(14)", "PCR OI", "ATM Delta", "ATR(14)"),
    val mathematicalEdge: String = "Exploits option writer delta squeeze and intraday time-window reversal probability.",
    val isRecommendedLive: Boolean = true,
    val regimeCommentary: String = "RECOMMENDED FOR LIVE MARKET: Regime metrics (ATR + PCR + Momentum) align with historical win conditions."
)

data class ScanStatus(
    val status: String = "Idle",
    val isScanning: Boolean = false,
    val progress: Int = 0,
    val lastScanTime: String = "",
    val results: String = "",
    val startTime: String = ""
)


data class LiveQuote(
    val spotPrice: Double = 0.0,
    val updatedAt: String = "",
    val tickAtEpoch: Double = 0.0,
    val tickRate10s: Double = 0.0,
    val move10sPct: Double = 0.0,
    val rapidActivity: Boolean = false,
    val activityNote: String = "",
    val tickAgeSeconds: Long = 0,
    val activityEvents: List<RapidActivityEvent> = emptyList(),
    val spotHistory: List<ChartPoint> = emptyList()
)

data class RapidActivityEvent(
    val at: String = "",
    val spot: Double = 0.0,
    val tickRate10s: Double = 0.0,
    val move10sPct: Double = 0.0
)

data class TradeRecord(
    val entryDate: String = "",
    val exitDate: String = "",
    val type: String = "LONG",
    val entryPrice: Double = 0.0,
    val exitPrice: Double = 0.0,
    val returnPct: Double = 0.0,
    val holdingDays: Int = 0,
    val exitReason: String = "",
    val patternTrigger: String = "",
    val isWin: Boolean = false,
    val rMultiple: Double = 0.0
)

data class StrategyConfig(
    val id: String = "",
    val name: String = "Master Confluence Quant",
    val symbol: String = "NIFTY",
    val strategyType: String = "MULTI_CONFLUENCE_QUANT", // OPTION_FLOW_GRAVITY, PRICE_ACTION_STRUCTURE, CANDLE_VOLATILITY_SQUEEZE, MULTI_CONFLUENCE_QUANT, EMA_CROSS, RSI_MOMENTUM, BREAKOUT
    val fastPeriod: Int = 20,
    val slowPeriod: Int = 50,
    val rsiThreshold: Double = 35.0,
    val targetPct: Double = 2.5,
    val stopLossPct: Double = 1.2,
    val atrMultiplier: Double = 1.5,
    val pcrThreshold: Double = 1.15,
    val maxHoldingDays: Int = 10,
    val period: String = "2y"
)

data class BacktestResult(
    val status: String = "ok",
    val symbol: String = "NIFTY",
    val strategyName: String = "Master Confluence Quant",
    val strategyType: String = "MULTI_CONFLUENCE_QUANT",
    val timeframe: String = "2y",
    val targetPct: Double = 2.5,
    val stopLossPct: Double = 1.2,
    val atrMultiplier: Double = 1.5,
    val totalTrades: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val winRate: Double = 0.0,
    val totalReturn: Double = 0.0,
    val profitFactor: Double = 0.0,
    val maxDrawdown: Double = 0.0,
    val sharpeRatio: Double = 0.0,
    val sortinoRatio: Double = 0.0,
    val calmarRatio: Double = 0.0,
    val expectancy: Double = 0.0,
    val avgTradeReturn: Double = 0.0,
    val maxConsecutiveWins: Int = 0,
    val maxConsecutiveLosses: Int = 0,
    val generatedAt: String = "",
    val trades: List<TradeRecord> = emptyList(),
    val blueprint: StrategyBlueprint = StrategyBlueprint()
)


