---
name: kunal-trading-app-dev
description: >-
  Use this skill when the user asks to modify, build, or extend their Android
  options/futures trading app (the "Option App" at android_app/ or the
  "Stock App" at stock_quant_app/). Covers the multi-agent AI architecture,
  collapsible dashboard, Hero-Zero module, option chain, futures suggestions
  tab, signal log, and APK build + GitHub push workflow. Also use when the
  user asks about adding new features, fixing build errors, or understanding
  the multi-agent trading engine (MultiAgentFuturesEngine.kt).
---

# Kunal Trading App — Development Skill

A complete runbook for developing, building, and extending Kunal's two Android trading apps.

---

## 1. Project Layout

```
c:\Users\Kunal\Desktop\antigrvity\
├── android_app/                  ← OPTION APP (main focus)
│   └── app/src/main/java/com/Kunal/stock/ui/
│       ├── OptionSentimentScreen.kt   (2900+ lines — main dashboard)
│       ├── FuturesQuantScreen.kt      (1580+ lines — futures + agent tabs)
│       ├── MultiAgentFuturesEngine.kt (578 lines — pure engine, no UI)
│       ├── ViewModels.kt
│       └── theme/
│   └── app/src/main/java/com/Kunal/stock/data/
│       ├── Models.kt                  (all data classes)
│       └── FirestoreRepository.kt     (Firestore parsing, ~860 lines)
├── stock_quant_app/              ← STOCK APP (do NOT modify unless asked)
├── stock_alert_bot/Option/       ← Python backend (cloud_sync_full.py)
└── Kunal-Option-Quant-debug.apk  ← Output APK (in .gitignore, copy manually)
```

> **RULE:** Only modify `android_app/`. Never touch `stock_quant_app/` unless the user explicitly asks.

---

## 2. Architecture — Multi-Agent AI System

```
runFullAgentPipeline(symbol, spotPrice)
  │
  ├── OPTIONS CLUSTER
  │   ├── computeGreeksAgent()        → Delta/Gamma/Theta/Vega/GEX
  │   ├── computeIvSmileAgent()       → ATM IV, put skew, call skew
  │   └── computeOiConcentrationAgent() → Call/Put walls, PCR, sweeps
  │          ↓
  │   computeOptionsManager()         → clusterBias + structuralHypothesis
  │
  ├── FUTURES CLUSTER
  │   ├── computeLongShortBuildupAgent() → OI Δ%, buildup type
  │   ├── computePremiumDiscountAgent()  → basis%, carry, contango/backwardation
  │   └── computeDeliveryVolumeAgent()   → VWAP, delivery%, OFT, imbalance
  │          ↓
  │   computeFuturesManager()         → momentumMatrix
  │
  └── computeMasterConsensus()
        ├── Trend Alignment Lock (blocks BULLISH if macro = BEARISH)
        ├── Pillars of Proof (Volume ≥1.5σ, S/R retest, MTF alignment)
        └── FuturesSignalPayload (entry range, TP1/TP2, SL, synthesis)
```

**All engine logic lives in:** [`MultiAgentFuturesEngine.kt`](file:///c:/Users/Kunal/Desktop/antigrvity/android_app/app/src/main/java/com/Kunal/stock/ui/MultiAgentFuturesEngine.kt)

---

## 3. Key Data Classes (Models.kt)

| Class | Key Fields |
|---|---|
| `OptionSentimentData` | symbol, spotPrice, finalSignal, liveTradeSignal, heroZeroAnalysis, multiHorizonProbabilities, optionChain |
| `LiveTradeSignal` | recommendedContract, action, optionType, strike, estimatedPremium, **targetPremium**, **stopLossPremium**, riskRewardRatio |
| `HeroZeroAnalysis` | isHeroZeroCandidate, contract, entryPremium, target1Premium, stopLossPremium, targetMultiplier |
| `OptionStrike` | strike, callLtp, putLtp, callOi, putOi, callVolume, putVolume, oiSide |
| `MasterConsensus` | finalDirection, finalSignal, trendAlignmentLock, overallConfidence, payload |
| `FuturesSignalPayload` | isActive, entryFloor, entryCeiling, takeProfit1/2, hardStopLoss, pillarVolume/SR/MTF |
| `FuturesLedgerEntry` | symbol, actionType, pnl, roiPct, isHeroZero, status (OPEN/CLOSED_WIN/CLOSED_LOSS/CLOSED_SL) |

> **IMPORTANT:** `LiveTradeSignal` uses `targetPremium` and `stopLossPremium` — NOT `target1` or `stopLoss`.

---

## 4. Option App Dashboard Structure (OptionSentimentScreen.kt)

The main `OptionSentimentContent()` has **12 collapsible sections**:

| # | Section | Default |
|---|---|---|
| 1 | Price Overview | Always visible |
| 2 | ⚡ Live Trade Signal | Always visible |
| 3 | 🔔 Market Alerts | Expanded |
| 4 | 📊 Probability & Momentum | Expanded |
| 5 | ⭐ Hero-Zero Module | Expanded if candidate |
| 6 | 🧮 Quant Analytics | Collapsed |
| 6B | 🤖 Multi-Agent AI Analysis | Collapsed |
| 6C | 📋 Signal Log & Hero-Zero Archive | Collapsed |
| 7 | 📈 Technical Setup | Collapsed |
| 8 | 🕯 Chart & Candle Outlook | Collapsed |
| 9 | 🔍 Market Evidence | Collapsed |
| 10 | 📐 Key Metrics & Zones | Collapsed |

**DashboardSectionHeader** composable: `title`, `description`, `isExpanded`, `onToggle`, `alwaysExpanded`.

---

## 5. Futures Tab Structure (FuturesQuantScreen.kt)

3 tabs: `Analytics` | `Signal Scanner` | `History`

- **Tab 0 (Analytics):** `MultiAgentAnalyticsTab` → contract card, quick-switcher, `AgentArchitectureBanner`, `MasterConsensusCard`, `OptionsClusterCard`, `FuturesClusterCard`, `SignalPayloadCard`, `NextCandleForecastCard`
- **Tab 1 (Signal Scanner):** `FuturesSignalScannerTab` → "Scan Now" button, INTRADAY/EOD mode, per-future BUY/SELL/NEUTRAL cards, saves to SharedPreferences `futures_suggestions_prefs`
- **Tab 2 (History):** `FuturesScanHistoryTab` → reads SharedPreferences, expandable scan batches, Performance Ledger view

---

## 6. SharedPreferences Keys

| Key | Store | Purpose |
|---|---|---|
| `hero_zero_prefs` → `hero_zero_log` | `||`-delimited strings | Hero-Zero saved setups |
| `option_signal_log` → `log` | `||`-delimited strings | Options signal history |
| `futures_suggestions_prefs` → `futures_suggestion_history` | `===START===`-delimited batches | Futures scan history |

---

## 7. Build & Deploy Commands

```powershell
# Build APK
cmd /c "cd /d c:\Users\Kunal\Desktop\antigrvity\android_app && gradlew.bat assembleDebug 2>&1"

# Copy APK to root
cmd /c "copy /y android_app\app\build\outputs\apk\debug\app-debug.apk Kunal-Option-Quant-debug.apk"

# Commit & push (BOTH branches)
cd c:\Users\Kunal\Desktop\antigrvity
git add android_app/
git commit -m "feat: <description>"
git push origin My/analysis:main
git push origin My/analysis:master
```

> Build takes ~2 minutes. Output APK is in `.gitignore` — copy manually before pushing.

---

## 8. Common Build Errors & Fixes

| Error | Cause | Fix |
|---|---|---|
| `Unresolved reference: EvidenceRow` | `EvidenceRow` is `private` in another composable scope | Replace with inline `Row { Text(...) Text(...) }` |
| `Unresolved reference: target1 / stopLoss` | Wrong field name on `LiveTradeSignal` | Use `targetPremium` / `stopLossPremium` |
| `This material API is experimental` | `FilterChip` or `FilterChipDefaults` in a non-annotated function | Add `@OptIn(ExperimentalMaterial3Api::class)` above the `@Composable` |
| `Conflicting declarations: val spotPrice` | Duplicate `val` declaration in same scope | Remove the duplicate declaration |
| `BUILD FAILED in 1m 51s` | Kotlin compilation error | Read the `e:` lines — only errors, `w:` are warnings (OK) |

---

## 9. F&O Universe

- **219 official NSE F&O contracts** loaded from `fno_universe.json` via `FnoUniverseProvider`
- **Do NOT add cash equities** — only F&O eligible stocks + indices
- Quick-switcher shows 16 benchmark futures: NIFTY, BANKNIFTY, FINNIFTY, RELIANCE, HDFCBANK, ICICIBANK, TATAMOTORS, INFY, TCS, SBIN, BHARTIARTL, LT, BAJFINANCE, AXISBANK, MARUTI, TATASTEEL

---

## 10. Option Chain ATM Highlighting

In `OptionChainTab()`: `atmStrikeIndex` is computed from `visibleStrikes` by min distance to `data.spotPrice`. A blue `◀ ATM ₹{price} ▶` separator row is inserted **before** the ATM strike row.

---

## 11. Hero-Zero Persistence Flow

```
HeroZeroCard() onLaunch:
  1. LaunchedEffect(Unit) → load hero_zero_log from SharedPreferences
  2. LaunchedEffect(isHeroZeroCandidate, contract) →
       if candidate && contract not blank:
         serialize entry → deduplicate → save to SharedPreferences
         update savedEntries state list
  3. Render "WATCHING" state when not candidate
  4. Render expandable "Saved Setups" list when history exists
```

---

## 12. Python Backend (Optional)

Files in `stock_alert_bot/Option/scripts/cloud_sync_full.py`:
- Writes `hero_zero_analysis` to Firestore
- `select_optimal_strike(option_chain=df)` picks live LTP from chain
- `candidate_contract` / `candidate_premium` set from live signal

---

## 13. Git Info

- **Repo:** `https://github.com/Kunalpagare03/Firebase.git`
- **Working branch:** `My/analysis`
- **Push targets:** both `main` and `master` branches
- **APK name:** `Kunal-Option-Quant-debug.apk`
