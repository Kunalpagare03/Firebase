package com.Kunal.stock.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirestoreRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /**
     * Streams real-time Option Sentiment for a given index (e.g., "NIFTY", "BANKNIFTY")
     */
    fun streamOptionSentiment(symbol: String): Flow<UiState<OptionSentimentData>> = callbackFlow {
        trySend(UiState.Loading)
        val docRef = db.collection("option_sentiment").document(symbol)

        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(UiState.Error("Failed to load $symbol sentiment: ${error.localizedMessage}"))
                return@addSnapshotListener
            }

            if (snapshot == null || !snapshot.exists()) {
                trySend(UiState.Error("No sentiment data found for $symbol."))
                return@addSnapshotListener
            }

            try {
                val data = parseOptionSentiment(symbol, snapshot)
                trySend(UiState.Success(data))
            } catch (e: Exception) {
                trySend(UiState.Error("Error parsing sentiment: ${e.localizedMessage}"))
            }
        }

        awaitClose { registration.remove() }
    }

    /**
     * Streams real-time Stock Scans from "latest" or "comprehensive"
     */
    fun streamStockScans(): Flow<UiState<List<StockScanItem>>> = callbackFlow {
        trySend(UiState.Loading)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        var sharedSnapshot: DocumentSnapshot? = null
        var personalSnapshot: DocumentSnapshot? = null
        fun publishBestAvailable() {
            val preferred = newerScanSnapshot(personalSnapshot, sharedSnapshot)
            if (preferred != null && preferred.exists()) {
                try {
                    val items = parseStockScans(preferred)
                    if (items.isNotEmpty()) {
                        trySend(UiState.Success(items))
                        return
                    }
                } catch (e: Exception) {
                    // Try fallback
                }
            }
            db.collection("stock_scans").document("comprehensive").get()
                .addOnSuccessListener { compSnap ->
                    if (compSnap != null && compSnap.exists()) {
                        val compItems = parseComprehensiveScans(compSnap)
                        trySend(UiState.Success(compItems))
                    } else {
                        trySend(UiState.Success(emptyList()))
                    }
                }
                .addOnFailureListener {
                    trySend(UiState.Error("No stock scans available."))
                }
        }

        val registrations = mutableListOf(
            db.collection("stock_scans").document("latest").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(UiState.Error("Failed to load stock scans: ${error.localizedMessage}"))
                    return@addSnapshotListener
                }
                sharedSnapshot = snapshot
                publishBestAvailable()
            }
        )
        if (uid != null) {
            registrations += db.collection("users").document(uid).collection("stock_scans")
                .document("latest").addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(UiState.Error("Failed to load your stock scan: ${error.localizedMessage}"))
                        return@addSnapshotListener
                    }
                    personalSnapshot = snapshot
                    publishBestAvailable()
                }
        }

        awaitClose { registrations.forEach { it.remove() } }
    }

    /** Streams the throttled Angel One spot quote separately from slower option analysis. */
    fun streamLiveQuote(symbol: String): Flow<LiveQuote?> = callbackFlow {
        val docRef = db.collection("live_quotes").document(symbol)
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(null)
                return@addSnapshotListener
            }
            val price = toDouble(snapshot.get("spot_price"))
            val source = snapshot.getString("source") ?: ""
            val updatedAt = snapshot.getString("updated_at") ?: ""
            trySend(if (price > 0 && source == "angel_one") LiveQuote(
                spotPrice = price,
                updatedAt = updatedAt,
                tickAtEpoch = toDouble(snapshot.get("tick_at_epoch")),
                tickRate10s = toDouble(snapshot.get("tick_rate_10s")),
                move10sPct = toDouble(snapshot.get("move_10s_pct")),
                rapidActivity = snapshot.getBoolean("rapid_activity") ?: false,
                activityNote = snapshot.getString("activity_note") ?: "",
                tickAgeSeconds = (System.currentTimeMillis() / 1000.0 - toDouble(
                    snapshot.get("tick_at_epoch") ?: snapshot.get("updated_at_epoch")
                )).toLong().coerceAtLeast(0),
                activityEvents = (snapshot.get("rapid_activity_events") as? List<*>)?.mapNotNull { raw ->
                    val event = raw as? Map<*, *> ?: return@mapNotNull null
                    RapidActivityEvent(
                        at = event["at"]?.toString() ?: "",
                        spot = toDouble(event["spot"]),
                        tickRate10s = toDouble(event["tick_rate_10s"]),
                        move10sPct = toDouble(event["move_10s_pct"])
                    )
                } ?: emptyList(),
                spotHistory = (snapshot.get("spot_history") as? List<*>)?.mapNotNull { raw ->
                    val point = raw as? Map<*, *> ?: return@mapNotNull null
                    val time = (point["time"] as? Number)?.toLong() ?: return@mapNotNull null
                    val value = toDouble(point["value"])
                    if (value > 0) ChartPoint(time, value) else null
                } ?: emptyList()
            ) else null)
        }
        awaitClose { registration.remove() }
    }

    private fun parseOptionSentiment(symbol: String, snapshot: DocumentSnapshot): OptionSentimentData {
        val spotPrice = toDouble(snapshot.get("spot_price"))
        val finalSignal = snapshot.getString("final_signal") ?: "Neutral"
        val combinedRead = snapshot.getString("combined_sentiment_read") ?: finalSignal
        val pcrOi = toDouble(snapshot.get("pcr_oi"))
        val pcrVol = toDouble(snapshot.get("pcr_volume"))
        val maxPain = toDouble(snapshot.get("max_pain"))
        val oiBuildup = snapshot.getString("oi_buildup_overall") ?: ""
        val oiDirectional = snapshot.getString("oi_directional_read") ?: ""
        val oiTrend = snapshot.getString("oi_trend") ?: "Stable"
        val fetchedAt = snapshot.getString("fetched_at") ?: ""
        val statusStr = snapshot.getString("market_status_str") ?: ""

        val support = (snapshot.get("support_zones") as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() } ?: emptyList()
        val resistance = (snapshot.get("resistance_zones") as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() } ?: emptyList()

        val alert = snapshot.get("trade_alert") as? Map<*, *>
        val action = alert?.get("action")?.toString() ?: "WAIT"
        val trigger = alert?.get("trigger")?.toString() ?: ""
        val note = alert?.get("note")?.toString() ?: ""

        val tech = alert?.get("technicals") as? Map<*, *>
        val rsi = tech?.get("rsi")?.toString() ?: "-"
        val macd = tech?.get("macd")?.toString() ?: "-"
        val trendline = tech?.get("trendline")?.toString() ?: "N/A"
        val candle = tech?.get("candle")?.toString() ?: "N/A"
        val deep = snapshot.get("deep_analysis") as? Map<*, *>
        val analysisConfidence = deep?.get("confidence")?.toString() ?: "Low"
        val analysisSummary = deep?.get("summary")?.toString() ?: ""
        val chartPattern = deep?.get("chart_pattern")?.toString() ?: "Building data"
        val chartTrend = deep?.get("chart_trend")?.toString() ?: "Neutral"
        val marketPressure = deep?.get("market_pressure")?.toString() ?: "Unavailable"
        val volumePcr = toDouble(deep?.get("volume_pcr"))
        val greeksSummary = deep?.get("greeks_summary")?.toString() ?: "Greeks unavailable"
        val nextCandleOutlook = deep?.get("next_candle_outlook")?.toString()
            ?: "Waiting for live candle data."
        val spotHistory = (snapshot.get("spot_history") as? List<*>)?.mapNotNull { raw ->
            val point = raw as? Map<*, *> ?: return@mapNotNull null
            val time = (point["time"] as? Number)?.toLong() ?: return@mapNotNull null
            val value = toDouble(point["value"])
            if (value > 0) ChartPoint(time, value) else null
        } ?: emptyList()
        val priceAction = snapshot.get("price_action") as? Map<*, *>
        val greeks = snapshot.get("greeks") as? Map<*, *>
        fun parseStrikeRows(rawRows: Any?): List<OptionStrike> = (rawRows as? List<*>)?.mapNotNull { raw ->
            val row = raw as? Map<*, *> ?: return@mapNotNull null
            OptionStrike(
                strike = toDouble(row["strike"]), callOi = toDouble(row["call_oi"]).toLong(),
                putOi = toDouble(row["put_oi"]).toLong(),
                totalOi = toDouble(row["total_oi"]).toLong(),
                callOiChange = toDouble(row["window_call_oi_change"] ?: row["call_oi_change"]).toLong(),
                putOiChange = toDouble(row["window_put_oi_change"] ?: row["put_oi_change"]).toLong(),
                callVolume = toDouble(row["call_volume"]).toLong(), putVolume = toDouble(row["put_volume"]).toLong(),
                callLtp = toDouble(row["call_ltp"]), putLtp = toDouble(row["put_ltp"]),
                callIv = toDouble(row["call_iv"]), putIv = toDouble(row["put_iv"]),
                callBuild = row["call_buildup"]?.toString() ?: "",
                putBuild = row["put_buildup"]?.toString() ?: "", oiSide = row["oi_side"]?.toString() ?: ""
            )
        } ?: emptyList()
        val topStrikes = parseStrikeRows(snapshot.get("top_strikes")).take(50)
        val optionChain = parseStrikeRows(snapshot.get("option_chain")).take(100)

        val rawLiveSignal = snapshot.get("live_trade_signal") as? Map<*, *>
        val liveSignal = if (rawLiveSignal != null) {
            LiveTradeSignal(
                recommendedContract = rawLiveSignal["recommended_contract"]?.toString() ?: "NO ACTIVE SIGNAL",
                action = rawLiveSignal["action"]?.toString() ?: "WAIT",
                optionType = rawLiveSignal["option_type"]?.toString() ?: "",
                strike = toDouble(rawLiveSignal["strike"]),
                entrySpot = toDouble(rawLiveSignal["entry_spot"]),
                targetSpot = toDouble(rawLiveSignal["target_spot"]),
                stopLossSpot = toDouble(rawLiveSignal["stop_loss_spot"]),
                estimatedPremium = toDouble(rawLiveSignal["estimated_premium"]),
                targetPremium = toDouble(rawLiveSignal["target_premium"]),
                stopLossPremium = toDouble(rawLiveSignal["stop_loss_premium"]),
                riskRewardRatio = rawLiveSignal["risk_reward_ratio"]?.toString() ?: "1:2.0",
                confidenceScore = (rawLiveSignal["confidence_score"] as? Number)?.toInt() ?: 0
            )
        } else LiveTradeSignal()

        val rawProof = snapshot.get("proof_of_evidence") as? Map<*, *>
        val proof = if (rawProof != null) {
            ProofOfEvidence(
                oiFlowProof = rawProof["oi_flow_proof"]?.toString() ?: "",
                priceActionProof = rawProof["price_action_proof"]?.toString() ?: "",
                gammaDeltaProof = rawProof["gamma_delta_proof"]?.toString() ?: ""
            )
        } else ProofOfEvidence()

        val rawMomentum = snapshot.get("big_momentum_alert") as? Map<*, *>
        val momentum = if (rawMomentum != null) {
            BigMomentumAlert(
                isImminent = rawMomentum["is_imminent"] as? Boolean ?: false,
                title = rawMomentum["title"]?.toString() ?: "",
                similarityPct = (rawMomentum["similarity_pct"] as? Number)?.toInt() ?: 0,
                historicalPrecedent = rawMomentum["historical_precedent"]?.toString() ?: "",
                whyItHappenedHistory = rawMomentum["why_it_happened_history"]?.toString() ?: "",
                actionPlan = rawMomentum["action_plan"]?.toString() ?: ""
            )
        } else BigMomentumAlert()

        val rawTimeWindow = snapshot.get("time_window_reversal") as? Map<*, *>
        val timeWindow = if (rawTimeWindow != null) {
            TimeWindowReversal(
                timeWindow = rawTimeWindow["time_window"]?.toString() ?: "09:15 - 09:45 (Opening Drive)",
                windowWeight = toDouble(rawTimeWindow["window_weight"]),
                reversalType = rawTimeWindow["reversal_type"]?.toString() ?: "Opening Drive Volatility Reversal",
                candlestickPattern = rawTimeWindow["candlestick_pattern"]?.toString() ?: "Consolidation Doji",
                reversalScore = (rawTimeWindow["reversal_score"] as? Number)?.toInt() ?: 45,
                directionalBias = rawTimeWindow["directional_bias"]?.toString() ?: "Neutral / Wait",
                nearSupport = rawTimeWindow["near_support"] as? Boolean ?: false,
                nearResistance = rawTimeWindow["near_resistance"] as? Boolean ?: false,
                commentary = rawTimeWindow["commentary"]?.toString() ?: ""
            )
        } else TimeWindowReversal()

        val rawHft = snapshot.get("hft_burst_alert") as? Map<*, *>
        val hft = if (rawHft != null) {
            HftBurstAlert(
                isActive = rawHft["is_active"] as? Boolean ?: false,
                burstType = rawHft["burst_type"]?.toString() ?: "Standard Liquidity Flow",
                surgeMultiplier = toDouble(rawHft["surge_multiplier"]),
                recommendedReaction = rawHft["recommended_reaction"]?.toString() ?: "Normal Execution"
            )
        } else HftBurstAlert()

        val rawQuant = snapshot.get("quant_option_chain") as? Map<*, *>
        val quantOptionChain = if (rawQuant != null) {
            QuantOptionChainAnalysis(
                ivSkewPct = toDouble(rawQuant["iv_skew_pct"]),
                ivSkewRead = rawQuant["iv_skew_read"]?.toString() ?: "Neutral Skew",
                netGexMillion = toDouble(rawQuant["net_gex_million"]),
                gexRegime = rawQuant["gex_regime"]?.toString() ?: "Neutral GEX",
                vannaDrag = toDouble(rawQuant["vanna_drag"]),
                charmDrag = toDouble(rawQuant["charm_drag"]),
                maxPainStrike = toDouble(rawQuant["max_pain_strike"]),
                maxPainGravityPct = toDouble(rawQuant["max_pain_gravity_pct"]),
                callWallStrike = toDouble(rawQuant["call_wall_strike"]),
                callWallOi = toDouble(rawQuant["call_wall_oi"]).toLong(),
                putWallStrike = toDouble(rawQuant["put_wall_strike"]),
                putWallOi = toDouble(rawQuant["put_wall_oi"]).toLong(),
                pcrOi = toDouble(rawQuant["pcr_oi"]),
                pcrVolume = toDouble(rawQuant["pcr_volume"]),
                institutionalRead = rawQuant["institutional_read"]?.toString() ?: "Balanced Flow"
            )
        } else QuantOptionChainAnalysis()

        val rawHero = snapshot.get("hero_zero_analysis") as? Map<*, *>
        val heroZeroAnalysis = if (rawHero != null) {
            HeroZeroAnalysis(
                isHeroZeroCandidate = rawHero["is_hero_zero_candidate"] as? Boolean ?: false,
                recommendation = rawHero["recommendation"]?.toString() ?: "NO_HERO_ZERO_TRADE",
                reason = rawHero["reason"]?.toString() ?: "",
                contract = rawHero["contract"]?.toString() ?: "",
                entryPremium = toDouble(rawHero["entry_premium"]),
                target1Premium = toDouble(rawHero["target_1_premium"]),
                target2Premium = toDouble(rawHero["target_2_premium"]),
                stopLossPremium = toDouble(rawHero["stop_loss_premium"]),
                targetMultiplier = rawHero["target_multiplier"]?.toString() ?: "1.0",
                expectedHoldingTime = rawHero["expected_holding_time"]?.toString() ?: ""
            )
        } else HeroZeroAnalysis()

        val rawHorizon = snapshot.get("multi_horizon_probabilities") as? Map<*, *>
        val h1 = rawHorizon?.get("horizon_1_candle") as? Map<*, *>
        val h3 = rawHorizon?.get("horizon_3_candle") as? Map<*, *>
        val h5 = rawHorizon?.get("horizon_5_candle") as? Map<*, *>
        val h10 = rawHorizon?.get("horizon_10_candle") as? Map<*, *>
        val multiHorizonProbabilities = MultiHorizonProbabilities(
            horizon1CandleBullish = toDouble(h1?.get("bullish_pct")), horizon1CandleBearish = toDouble(h1?.get("bearish_pct")), horizon1CandleSideways = toDouble(h1?.get("sideways_pct")),
            horizon3CandleBullish = toDouble(h3?.get("bullish_pct")), horizon3CandleBearish = toDouble(h3?.get("bearish_pct")), horizon3CandleSideways = toDouble(h3?.get("sideways_pct")),
            horizon5CandleBullish = toDouble(h5?.get("bullish_pct")), horizon5CandleBearish = toDouble(h5?.get("bearish_pct")), horizon5CandleSideways = toDouble(h5?.get("sideways_pct")),
            horizon10CandleBullish = toDouble(h10?.get("bullish_pct")), horizon10CandleBearish = toDouble(h10?.get("bearish_pct")), horizon10CandleSideways = toDouble(h10?.get("sideways_pct"))
        )

        return OptionSentimentData(
            symbol = symbol,
            spotPrice = spotPrice,
            finalSignal = finalSignal,
            combinedSentimentRead = combinedRead,
            pcrOi = pcrOi,
            pcrVolume = pcrVol,
            maxPain = maxPain,
            supportZones = support,
            resistanceZones = resistance,
            oiBuildupOverall = oiBuildup,
            oiDirectionalRead = oiDirectional,
            oiTrend = oiTrend,
            tradeAction = action,
            tradeTrigger = trigger,
            tradeNote = note,
            fetchedAt = fetchedAt,
            marketStatusStr = statusStr,
            rsi = rsi,
            macd = macd,
            trendline = trendline,
            candle = candle,
            analysisConfidence = analysisConfidence,
            analysisSummary = analysisSummary,
            chartPattern = chartPattern,
            chartTrend = chartTrend,
            marketPressure = marketPressure,
            volumePcr = volumePcr,
            deepDirection = deep?.get("direction")?.toString() ?: "WAIT",
            deepEntryCondition = deep?.get("entry_condition")?.toString() ?: "",
            deepTargetSpot = toDouble(deep?.get("target_spot")),
            deepInvalidationSpot = toDouble(deep?.get("invalidation_spot")),
            candidateContract = deep?.get("option_contract")?.toString() ?: "WAIT",
            candidatePremium = toDouble(deep?.get("option_premium")),
            greeksSummary = greeksSummary,
            nextCandleOutlook = nextCandleOutlook,
            spotHistory = spotHistory,
            flow = snapshot.getString("vol_flow") ?: "",
            oiDeltas = snapshot.getString("oi_deltas") ?: "",
            manipulationRisk = snapshot.getString("manipulation_risk") ?: "",
            manipulationNote = snapshot.getString("manipulation_note") ?: "",
            priceAction = priceAction?.get("reading")?.toString() ?: "",
            priceClose = toDouble(priceAction?.get("close")),
            priceChangePct = toDouble(priceAction?.get("change_pct")),
            sma20 = toDouble(priceAction?.get("sma20")),
            greekStrike = toDouble(greeks?.get("strike")), callDelta = toDouble(greeks?.get("call_delta")),
            putDelta = toDouble(greeks?.get("put_delta")), gamma = toDouble(greeks?.get("gamma")),
            vega = toDouble(greeks?.get("vega_per_1pct")), topStrikes = topStrikes,
            optionChain = optionChain,
            chainExpiry = snapshot.getString("chain_expiry") ?: "",
            chainUpdatedAt = snapshot.getString("chain_updated_at") ?: fetchedAt,
            liveTradeSignal = liveSignal,
            proofOfEvidence = proof,
            bigMomentumAlert = momentum,
            timeWindowReversal = timeWindow,
            hftBurstAlert = hft,
            quantOptionChain = quantOptionChain,
            regimeName = snapshot.getString("regime") ?: "Trending (Bullish)",
            expectedValue = toDouble(snapshot.get("expected_value")),
            heroZeroAnalysis = heroZeroAnalysis,
            multiHorizonProbabilities = multiHorizonProbabilities
        )
    }

    private fun parseStockScans(snapshot: DocumentSnapshot): List<StockScanItem> {
        val rawResults = (snapshot.get("results") as? List<*>)?.take(100) ?: return emptyList()
        return rawResults.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            StockScanItem(
                symbol = map["symbol"]?.toString() ?: "",
                entry = toDouble(map["entry"]),
                close = toDouble(map["close"]),
                changePct = toDouble(map["change_pct"]),
                volumeRatio = toDouble(map["volume_ratio"]),
                rsi14 = toDouble(map["rsi14"]),
                pattern = map["pattern"]?.toString() ?: "",
                score = (map["score"] as? Number)?.toInt() ?: 0,
                signals = (map["signals"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                target2Pct = toDouble(map["target_2pct"]),
                stopLoss = toDouble(map["stop_loss"]),
                riskReward = toDouble(map["risk_reward"]),
                recommendation = map["recommendation"]?.toString() ?: "WAIT",
                screeners = (map["screeners"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                sector = map["sector"]?.toString() ?: "",
                rating = map["rating"]?.toString() ?: "",
                barDate = map["bar_date"]?.toString() ?: "",
                liveBar = map["live_bar"] as? Boolean ?: false,
                targetPrice = toDouble(map["target_price"] ?: map["target_2pct"]),
                ema20 = toDouble(map["ema20"]),
                tradedVolume = toDouble(map["traded_volume"]).toLong(),
                averageVolume = toDouble(map["average_volume"]).toLong(),
                candlePattern = map["candle_pattern"]?.toString() ?: "",
                chartPattern = map["chart_pattern"]?.toString() ?: "",
                basePattern = map["base_pattern"]?.toString() ?: "",
                baseScore = (map["base_score"] as? Number)?.toInt() ?: 0,
                baseBuyPoint = toDouble(map["base_buy_point"]),
                baseBreakout = map["base_breakout"] as? Boolean ?: false,
                analysisSide = map["analysis_side"]?.toString() ?: "Mixed",
                candles = parseCandles(map["candles"])
            )
        }
    }

    private fun parseComprehensiveScans(snapshot: DocumentSnapshot): List<StockScanItem> {
        val list = mutableListOf<StockScanItem>()
        val categories = listOf("intraday", "swing", "positional")
        for (cat in categories) {
            val items = snapshot.get(cat) as? List<*> ?: continue
            for (raw in items) {
                val map = raw as? Map<*, *> ?: continue
                val sym = map["symbol"]?.toString() ?: continue
                val ratingStr = map["rating"]?.toString() ?: "0/10"
                val score = (map["setup_score"] as? Number)?.toInt()
                    ?: (map["bear_rating"] as? Number)?.toInt()
                    ?: ratingStr.split("/").firstOrNull()?.toIntOrNull() ?: 0
                list.add(
                    StockScanItem(
                        symbol = sym,
                        entry = toDouble(map["price"]),
                        close = toDouble(map["price"]),
                        changePct = toDouble(map["change"]),
                        volumeRatio = toDouble(map["vol_ratio"]),
                        rsi14 = 50.0,
                        pattern = cat.uppercase(),
                        score = score,
                        signals = listOf(map["justification"]?.toString() ?: ""),
                        target2Pct = toDouble(map["target_price"]),
                        stopLoss = toDouble(map["sl_price"]),
                        riskReward = 1.5,
                        recommendation = map["recommendation"]?.toString() ?: "WATCH",
                        sector = map["sector"]?.toString() ?: "",
                        rating = ratingStr,
                        barDate = map["bar_date"]?.toString() ?: "",
                        liveBar = map["live_bar"] as? Boolean ?: false,
                        targetPrice = toDouble(map["target_price"] ?: map["target_price"]),
                        ema20 = toDouble(map["ema20"]),
                        tradedVolume = toDouble(map["traded_volume"]).toLong(),
                        averageVolume = toDouble(map["average_volume"]).toLong(),
                        candlePattern = map["candle_pattern"]?.toString() ?: "",
                        chartPattern = map["chart_pattern"]?.toString() ?: "",
                        basePattern = map["base_pattern"]?.toString() ?: "",
                        baseScore = (map["base_score"] as? Number)?.toInt() ?: 0,
                        baseBuyPoint = toDouble(map["base_buy_point"]),
                        baseBreakout = map["base_breakout"] as? Boolean ?: false,
                        analysisSide = map["analysis_side"]?.toString() ?: "Mixed",
                        candles = parseCandles(map["candles"])
                    )
                )
            }
        }
        return list.distinctBy { it.symbol }.take(150)
    }

    private fun parseCandles(raw: Any?): List<StockCandle> = (raw as? List<*>)?.mapNotNull { item ->
        val row = item as? Map<*, *> ?: return@mapNotNull null
        val time = (row["time"] as? Number)?.toLong() ?: return@mapNotNull null
        StockCandle(
            time = time,
            open = toDouble(row["open"]), high = toDouble(row["high"]),
            low = toDouble(row["low"]), close = toDouble(row["close"]),
            volume = toDouble(row["volume"]).toLong()
        )
    } ?: emptyList()

    /**
     * Streams real-time scan and system status from stock_scans/status
     */
    fun streamScanStatus(): Flow<ScanStatus> = streamStatus("stock_scans", userScoped = true)

    fun streamOptionStatus(): Flow<ScanStatus> = streamStatus("option_scans")

    private fun newerScanSnapshot(
        personal: DocumentSnapshot?,
        shared: DocumentSnapshot?
    ): DocumentSnapshot? {
        if (personal?.exists() != true) return shared
        if (shared?.exists() != true) return personal
        val personalTime = statusUpdatedAtMillis(personal)
        val sharedTime = statusUpdatedAtMillis(shared)
        if (personalTime != sharedTime) return if (personalTime > sharedTime) personal else shared
        val personalRunning = personal.getBoolean("is_scanning") == true
        val sharedRunning = shared.getBoolean("is_scanning") == true
        if (personalRunning != sharedRunning) return if (personalRunning) shared else personal
        return personal
    }

    private fun statusUpdatedAtMillis(snapshot: DocumentSnapshot): Long {
        val updatedAt = toDouble(snapshot.get("updated_at_epoch"))
        if (updatedAt > 0) return (updatedAt * 1000).toLong()
        val startTime = snapshot.getString("start_time")
        if (snapshot.getBoolean("is_scanning") == true && !startTime.isNullOrBlank()) {
            parseStartTimeMillis(startTime)?.let { return it }
        }
        val scanTime = snapshot.getString("last_scan_time")
            ?: snapshot.getString("timestamp")
        if (!scanTime.isNullOrBlank()) {
            val parsed = runCatching {
                SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US).parse(scanTime)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return parseStartTimeMillis(startTime ?: return 0L) ?: 0L
    }

    private fun parseStartTimeMillis(startTime: String): Long? {
        val parts = startTime.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size != 3 || parts[0] !in 0..23 || parts[1] !in 0..59 || parts[2] !in 0..59) return null
        val now = java.util.Calendar.getInstance()
        val parsed = java.util.Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            set(java.util.Calendar.HOUR_OF_DAY, parts[0])
            set(java.util.Calendar.MINUTE, parts[1])
            set(java.util.Calendar.SECOND, parts[2])
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        return if (parsed > now.timeInMillis) parsed - 86_400_000L else parsed
    }

    private fun streamStatus(collection: String, userScoped: Boolean = false): Flow<ScanStatus> = callbackFlow {
        val uid = if (userScoped) FirebaseAuth.getInstance().currentUser?.uid else null
        var sharedSnapshot: DocumentSnapshot? = null
        var personalSnapshot: DocumentSnapshot? = null
        fun publishStatus() {
            val snapshot = newerScanSnapshot(personalSnapshot, sharedSnapshot)
            if (snapshot == null || !snapshot.exists()) {
                trySend(ScanStatus())
                return
            }
            val storedStatus = snapshot.getString("status") ?: "Idle"
            val storedScanning = snapshot.getBoolean("is_scanning") ?: storedStatus.contains("Scanning", ignoreCase = true)
            val lastProgressAt = statusUpdatedAtMillis(snapshot)
            val stalled = storedScanning && lastProgressAt > 0 &&
                System.currentTimeMillis() - lastProgressAt > 45 * 60 * 1000L
            val statusStr = if (stalled) "Idle / Scan stalled" else storedStatus
            val isScanning = storedScanning && !stalled
            val progress = (snapshot.get("progress") as? Number)?.toInt() ?: 0
            val lastScanTime = snapshot.getString("last_scan_time") ?: ""
            val results = if (stalled) "No scan progress for 45 minutes. You can start another scan." else snapshot.getString("results") ?: ""
            val startTime = snapshot.getString("start_time") ?: ""
            trySend(
                ScanStatus(
                    status = statusStr,
                    isScanning = isScanning,
                    progress = progress,
                    lastScanTime = lastScanTime,
                    results = results,
                    startTime = startTime
                )
            )
        }
        val registrations = mutableListOf(
            db.collection(collection).document("status").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(ScanStatus(status = "Failed to load scan status"))
                    return@addSnapshotListener
                }
                sharedSnapshot = snapshot
                publishStatus()
            }
        )
        if (uid != null) {
            registrations += db.collection("users").document(uid).collection(collection)
                .document("status").addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(ScanStatus(status = "Failed to load your scan status"))
                        return@addSnapshotListener
                    }
                    personalSnapshot = snapshot
                    publishStatus()
                }
        }
        val ticker = launch {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                publishStatus()
            }
        }
        awaitClose {
            ticker.cancel()
            registrations.forEach { it.remove() }
        }
    }

    /**
     * Sends a command to trigger a scan manually (e.g. "stock" or "option")
     */
    fun triggerScan(command: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            onResult(false, "Sign in before starting a scan")
            return
        }
        val payload = mapOf(
            "command" to command,
            "timestamp" to System.currentTimeMillis(),
            "processed" to false
        )
        db.collection("commands").document(uid)
            .set(payload)
            .addOnSuccessListener {
                onResult(true, null)
            }
            .addOnFailureListener { error ->
                onResult(false, error.localizedMessage)
            }
    }

    /**
     * Streams real-time Backtest Result from Firestore collection "backtest_results"
     */
    fun streamBacktestResult(docId: String = "latest"): Flow<UiState<BacktestResult>> = callbackFlow {
        trySend(UiState.Loading)
        val docRef = db.collection("backtest_results").document(docId)

        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(UiState.Error("Failed to load backtest: ${error.localizedMessage}"))
                return@addSnapshotListener
            }

            if (snapshot == null || !snapshot.exists()) {
                // If specific docId doesn't exist, try "latest"
                if (docId != "latest") {
                    db.collection("backtest_results").document("latest").get()
                        .addOnSuccessListener { fallbackSnap ->
                            if (fallbackSnap != null && fallbackSnap.exists()) {
                                trySend(UiState.Success(parseBacktestResult(fallbackSnap)))
                            } else {
                                trySend(UiState.Error("No backtest results found. Tap 'Run Backtest' to start."))
                            }
                        }
                        .addOnFailureListener {
                            trySend(UiState.Error("No backtest results found."))
                        }
                    return@addSnapshotListener
                }
                trySend(UiState.Error("No backtest results found. Tap 'Run Backtest' to start."))
                return@addSnapshotListener
            }

            try {
                val data = parseBacktestResult(snapshot)
                trySend(UiState.Success(data))
            } catch (e: Exception) {
                trySend(UiState.Error("Error parsing backtest data: ${e.localizedMessage}"))
            }
        }

        awaitClose { registration.remove() }
    }

    private fun parseBacktestResult(snapshot: DocumentSnapshot): BacktestResult {
        val rawTrades = snapshot.get("trades") as? List<*> ?: emptyList<Any>()
        val tradesList = rawTrades.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            TradeRecord(
                entryDate = map["entry_date"]?.toString() ?: "",
                exitDate = map["exit_date"]?.toString() ?: "",
                type = map["type"]?.toString() ?: "LONG",
                entryPrice = toDouble(map["entry_price"]),
                exitPrice = toDouble(map["exit_price"]),
                returnPct = toDouble(map["return_pct"]),
                holdingDays = (map["holding_days"] as? Number)?.toInt() ?: 0,
                exitReason = map["exit_reason"]?.toString() ?: "",
                patternTrigger = map["pattern_trigger"]?.toString() ?: "",
                isWin = map["is_win"] as? Boolean ?: (toDouble(map["return_pct"]) > 0),
                rMultiple = toDouble(map["r_multiple"])
            )
        }

        val rawBp = snapshot.get("blueprint") as? Map<*, *>
        val bp = if (rawBp != null) {
            StrategyBlueprint(
                name = rawBp["name"]?.toString() ?: "Master Confluence Quant",
                type = rawBp["type"]?.toString() ?: "MULTI_CONFLUENCE_QUANT",
                rules = (rawBp["rules"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                indicators = (rawBp["indicators"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
                mathematicalEdge = rawBp["mathematical_edge"]?.toString() ?: "",
                isRecommendedLive = rawBp["is_recommended_live"] as? Boolean ?: true,
                regimeCommentary = rawBp["regime_commentary"]?.toString() ?: "RECOMMENDED FOR LIVE MARKET: High win-probability regime."
            )
        } else StrategyBlueprint()

        return BacktestResult(
            status = snapshot.getString("status") ?: "ok",
            symbol = snapshot.getString("symbol") ?: "NIFTY",
            strategyName = snapshot.getString("strategy_name") ?: "Master Confluence Quant",
            strategyType = snapshot.getString("strategy_type") ?: "MULTI_CONFLUENCE_QUANT",
            timeframe = snapshot.getString("timeframe") ?: "2y",
            targetPct = toDouble(snapshot.get("target_pct")),
            stopLossPct = toDouble(snapshot.get("stop_loss_pct")),
            atrMultiplier = toDouble(snapshot.get("atr_multiplier")),
            totalTrades = (snapshot.get("total_trades") as? Number)?.toInt() ?: tradesList.size,
            wins = (snapshot.get("wins") as? Number)?.toInt() ?: tradesList.count { it.isWin },
            losses = (snapshot.get("losses") as? Number)?.toInt() ?: tradesList.count { !it.isWin },
            winRate = toDouble(snapshot.get("win_rate")),
            totalReturn = toDouble(snapshot.get("total_return")),
            profitFactor = toDouble(snapshot.get("profit_factor")),
            maxDrawdown = toDouble(snapshot.get("max_drawdown")),
            sharpeRatio = toDouble(snapshot.get("sharpe_ratio")),
            sortinoRatio = toDouble(snapshot.get("sortino_ratio")),
            calmarRatio = toDouble(snapshot.get("calmar_ratio")),
            expectancy = toDouble(snapshot.get("expectancy")),
            avgTradeReturn = toDouble(snapshot.get("avg_trade_return")),
            maxConsecutiveWins = (snapshot.get("max_consecutive_wins") as? Number)?.toInt() ?: 0,
            maxConsecutiveLosses = (snapshot.get("max_consecutive_losses") as? Number)?.toInt() ?: 0,
            generatedAt = snapshot.getString("generated_at") ?: "",
            trades = tradesList,
            blueprint = bp
        )
    }

    /**
     * Saves user's custom Strategy configuration to Firestore
     */
    fun saveStrategyConfig(config: StrategyConfig, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val docRef = if (uid != null) {
            db.collection("users").document(uid).collection("strategies").document(config.id.ifEmpty { "${config.symbol}_${config.strategyType}" })
        } else {
            db.collection("strategies").document(config.id.ifEmpty { "${config.symbol}_${config.strategyType}" })
        }

        val payload = mapOf(
            "name" to config.name,
            "symbol" to config.symbol,
            "strategy_type" to config.strategyType,
            "fast_period" to config.fastPeriod,
            "slow_period" to config.slowPeriod,
            "rsi_threshold" to config.rsiThreshold,
            "target_pct" to config.targetPct,
            "stop_loss_pct" to config.stopLossPct,
            "max_holding_days" to config.maxHoldingDays,
            "period" to config.period,
            "updated_at" to System.currentTimeMillis()
        )

        docRef.set(payload)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { onResult(false, it.localizedMessage) }
    }

    private fun toDouble(value: Any?): Double {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }
}

