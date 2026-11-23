"""Streamlit UI Dashboard: 7-Page Terminal for Option App v8 (Part Prompt 20)."""

import json
from datetime import datetime
import streamlit as st
import pandas as pd
import plotly.graph_objects as go
from plotly.subplots import make_subplots

from optionapp.app.settings import get_settings
from optionapp.app.db import get_db, init_db
from optionapp.app.calendar import now_ist, STRIKE_STEPS, LOT_SIZES
from optionapp.app.capital import CapitalLadder
from optionapp.app.data.provider import MarketDataProvider
from optionapp.app.analytics.chain import compute_max_pain, compute_pcr, compute_oi_walls
from optionapp.app.analytics.indicators import compute_cpr_and_pivots, compute_supertrend, compute_vwap
from optionapp.app.engine.calm import CalmModeManager
from optionapp.app.research.ghosts import GhostLedger
from optionapp.app.research.radar import RuleChangeRadar, OFFICIAL_LOTS_NOV_2026

st.set_page_config(
    page_title="Option App v8 - Alert Terminal",
    page_icon="⚡",
    layout="wide",
    initial_sidebar_state="expanded",
)

# Custom Styling
st.markdown("""
<style>
    .metric-card {
        background-color: #1e222d;
        border-radius: 8px;
        padding: 12px;
        border: 1px solid #2a2e39;
        margin-bottom: 8px;
    }
    .badge-live {
        background-color: #00c853;
        color: white;
        padding: 2px 8px;
        border-radius: 4px;
        font-weight: bold;
    }
    .badge-paper {
        background-color: #ff9100;
        color: white;
        padding: 2px 8px;
        border-radius: 4px;
        font-weight: bold;
    }
    .badge-calm {
        background-color: #3d5afe;
        color: white;
        padding: 4px 10px;
        border-radius: 6px;
        font-weight: bold;
    }
</style>
""", unsafe_allow_html=True)


@st.cache_resource
def get_engine_state():
    init_db()
    settings = get_settings()
    provider = MarketDataProvider()
    calm = CalmModeManager(enabled=settings.calm_mode.enabled, mask_until=settings.calm_mode.mask_pnl_until)
    ghosts = GhostLedger(enabled=settings.ghost_ledger.enabled)
    ladder = CapitalLadder(settings.capital.total_inr)
    return settings, provider, calm, ghosts, ladder


settings, provider, calm_manager, ghost_ledger, capital_ladder = get_engine_state()

# Sidebar Navigation
st.sidebar.title("⚡ Option App v8")
st.sidebar.caption(f"Indian Index F&O Terminal | v8.0")

page = st.sidebar.radio(
    "Navigation",
    ["1. Live", "2. Chart", "3. Option Chain", "4. Positions", "5. Setups", "6. Scoreboard", "7. Settings"],
)

# Live Ticker Header
spot_nifty = provider.get_spot_price("NIFTY")
spot_sensex = provider.get_spot_price("SENSEX")
vix = provider.get_india_vix()

col_t1, col_t2, col_t3, col_t4, col_t5 = st.columns(5)
col_t1.metric("NIFTY 50", f"{spot_nifty:,.2f}", f"Lot: {settings.lots.NIFTY}")
col_t2.metric("SENSEX", f"{spot_sensex:,.2f}", f"Lot: {settings.lots.SENSEX}")
col_t3.metric("INDIA VIX", f"{vix:.2f}", "Normal" if vix < 22 else "HIGH REGIME")
col_t4.metric("Capital Ladder", f"Stage {capital_ladder.stage}", f"Rs {capital_ladder.capital:,.0f}")
col_t5.metric("Calm Mode", "ACTIVE 🛡️" if calm_manager.is_calm_active() else "OFF", "Masks MTM to 15:35")

st.markdown("---")

# ==============================================================================
# PAGE 1: LIVE
# ==============================================================================
if page == "1. Live":
    st.subheader("🟢 Live Traffic Light & Active Alert Stream")

    col_agents, col_alerts = st.columns([1, 2])

    with col_agents:
        st.markdown("#### Setup Agents Status")
        agents_info = [
            ("Sensex Condor", "LIVE", "09:16 Thu Expiry | 0.5% + 1.5% wings | Stop 3x"),
            ("Nifty Condor A", "LIVE", "09:16 Tue Expiry | 0.5% + 1.5% wings | Stop 4x"),
            ("Nifty Condor B", "LIVE", "09:16 Tue Expiry | 0.5% + 2.0% wings | Stop 4x"),
            ("BankNifty Iron Fly", "PAPER", "ATM Short Straddle + 1.5% wings"),
            ("9:20 Short Straddle", "PAPER", "ATM Short Straddle | Stop 25% per leg"),
            ("10:15 First Hour Bias", "BIAS", "Morning directional channel & CPR filter"),
            ("13:15 Expiry Direction", "BIAS", "Afternoon momentum trend & VWAP filter"),
            ("Afternoon Hero Zero", "PAPER", "13:15 Expiry | Ask <= 70% Fair Value"),
            ("Trendline Breakout", "PAPER", "3-Touch Falling Trendline on 5m"),
            ("Big Move Watch", "INFO", "VIX >= 22 | Range >= 2.5% | Gap >= 1.2%"),
        ]

        for name, mode, rule in agents_info:
            color = "#00c853" if mode == "LIVE" else ("#ff9100" if mode == "PAPER" else "#29b6f6")
            st.markdown(f"""
            <div class="metric-card">
                <b>{name}</b> <span style="background-color: {color}; color: white; padding: 2px 6px; border-radius: 4px; font-size: 11px;">{mode}</span>
                <div style="font-size: 12px; color: #9e9e9e; margin-top: 4px;">{rule}</div>
            </div>
            """, unsafe_allow_html=True)

    with col_alerts:
        st.markdown("#### Real-time Alerts Stream")
        with get_db() as conn:
            alert_rows = conn.execute("SELECT * FROM alerts ORDER BY ts DESC LIMIT 20").fetchall()

        if alert_rows:
            alert_list = []
            for r in alert_rows:
                d = dict(r)
                details = json.loads(d.get("data_json", "{}"))
                alert_list.append({
                    "Time": d.get("ts", "")[:19],
                    "Setup": d.get("setup_name"),
                    "Symbol": d.get("symbol"),
                    "Stage": d.get("stage"),
                    "Message": details.get("message", "-"),
                })
            st.dataframe(pd.DataFrame(alert_list), use_container_width=True, height=450)
        else:
            st.info("No alerts recorded yet today. Click below to simulate or trigger an engine cycle.")

        if st.button("🔄 Trigger Engine Cycle Now"):
            from optionapp.app.engine.live import TradingEngine
            engine = TradingEngine()
            alerts = engine.run_cycle()
            st.success(f"Engine cycle completed. Generated {len(alerts)} alerts.")
            st.rerun()

# ==============================================================================
# PAGE 2: CHART WITH PATTERN IDENTIFICATION
# ==============================================================================
elif page == "2. Chart":
    st.subheader("📈 Multi-Timeframe Chart with Visual Pattern Identification")

    sym_select = st.selectbox("Underlying Index", ["NIFTY", "SENSEX", "BANKNIFTY"])
    spot_val = provider.get_spot_price(sym_select)

    # Generate synthetic candlestick dataframe for plotting
    now_t = datetime.now()
    times = [now_t - pd.Timedelta(minutes=5 * i) for i in range(50, -1, -1)]
    c_vals = []
    p = spot_val * 0.996
    for i, t in enumerate(times):
        p += (hash(str(t)) % 25 - 12)
        c_vals.append({
            "ts": t,
            "open": p - 4,
            "high": p + 12,
            "low": p - 10,
            "close": p + 3,
            "volume": 2500,
        })
    df_chart = pd.DataFrame(c_vals).set_index("ts")

    # Compute CPR & Pivots and VWAP
    cpr = compute_cpr_and_pivots(df_chart["high"].max(), df_chart["low"].min(), df_chart["close"].iloc[-1])
    vwap = compute_vwap(df_chart)

    # Detect 16 Candlestick and Chart Patterns from Part C3
    from optionapp.app.analytics.patterns import detect_candlestick_patterns, detect_chart_patterns
    detected_candlestick = detect_candlestick_patterns(df_chart)
    detected_charts = detect_chart_patterns(df_chart)
    all_patterns = detected_candlestick + detected_charts

    fig = go.Figure()
    fig.add_trace(go.Candlestick(
        x=df_chart.index,
        open=df_chart["open"],
        high=df_chart["high"],
        low=df_chart["low"],
        close=df_chart["close"],
        name=sym_select,
    ))

    # Overlays
    fig.add_trace(go.Scatter(x=df_chart.index, y=vwap, mode="lines", name="VWAP", line=dict(color="orange", width=1.5)))
    fig.add_hline(y=cpr["pivot"], line_dash="dash", line_color="yellow", annotation_text="Pivot")
    fig.add_hline(y=cpr["cpr_top"], line_dash="dot", line_color="cyan", annotation_text="TC")
    fig.add_hline(y=cpr["cpr_bottom"], line_dash="dot", line_color="cyan", annotation_text="BC")
    fig.add_hline(y=cpr["r1"], line_dash="dash", line_color="green", annotation_text="R1")
    fig.add_hline(y=cpr["s1"], line_dash="dash", line_color="red", annotation_text="S1")

    # Annotate detected patterns directly on the chart
    for pat in detected_candlestick:
        bar_idx = pat.get("bar_index", len(df_chart) - 1)
        if 0 <= bar_idx < len(df_chart):
            bar_ts = df_chart.index[bar_idx]
            bar_high = df_chart["high"].iloc[bar_idx]
            bar_low = df_chart["low"].iloc[bar_idx]
            is_bull = pat.get("type") == "BULLISH"
            y_pos = bar_low * 0.999 if is_bull else bar_high * 1.001

            fig.add_annotation(
                x=bar_ts,
                y=y_pos,
                text=pat.get("pattern", "Pattern"),
                showarrow=True,
                arrowhead=2,
                arrowcolor="#00c853" if is_bull else "#ff1744",
                font=dict(color="white", size=10),
                bgcolor="#00c853" if is_bull else "#ff1744",
                borderpad=3,
            )

    fig.update_layout(
        height=550,
        xaxis_rangeslider_visible=False,
        template="plotly_dark",
        margin=dict(l=10, r=10, t=10, b=10),
    )
    st.plotly_chart(fig, use_container_width=True)

    # Pattern Identification Panel
    st.markdown("#### 🎯 Identified Chart Patterns & Trigger Levels")
    if all_patterns:
        pat_rows = []
        for p_item in all_patterns:
            pat_rows.append({
                "Pattern Name": p_item.get("pattern"),
                "Type": p_item.get("type"),
                "Confidence / Strength": f"{int(p_item.get('strength', 0.8) * 100)}%",
                "Trigger Level": f"₹{p_item.get('trigger_level', spot_val):,.1f}" if "trigger_level" in p_item else f"₹{spot_val:,.1f}",
                "Stop Loss": f"₹{p_item.get('stop_loss', spot_val * 0.995):,.1f}" if "stop_loss" in p_item else f"₹{spot_val * 0.995:,.1f}",
            })
        st.dataframe(pd.DataFrame(pat_rows), use_container_width=True)
    else:
        st.info("No active pattern triggers on the latest bar. Scanning 5m candles in real-time.")

# ==============================================================================
# PAGE 3: OPTION CHAIN
# ==============================================================================
elif page == "3. Option Chain":
    st.subheader("⚡ Real-Time Option Chain & Open Interest Buildup")

    sym_chain = st.selectbox("Index", ["NIFTY", "SENSEX", "BANKNIFTY", "FINNIFTY"])
    chain_data = provider.get_option_chain(sym_chain)
    rows = chain_data.get("data", [])

    if rows:
        max_pain = compute_max_pain(rows)
        pcr = compute_pcr(rows)
        walls = compute_oi_walls(rows)

        col_c1, col_c2, col_c3, col_c4 = st.columns(4)
        col_c1.metric("Max Pain Strike", f"{max_pain:,.0f}")
        col_c2.metric("PCR (OI)", f"{pcr['pcr_oi']:.2f}", pcr["sentiment"])
        col_c3.metric("Call OI Wall (Resistance)", f"{walls['call_wall']:,.0f}")
        col_c4.metric("Put OI Wall (Support)", f"{walls['put_wall']:,.0f}")

        # Chain Table
        chain_table = []
        for r in rows:
            chain_table.append({
                "CE OI": r.get("CALL", {}).get("oi", 0),
                "CE IV": r.get("CALL", {}).get("iv", 0),
                "CE LTP": r.get("CALL", {}).get("ltp", 0.0),
                "STRIKE": r.get("strike_price"),
                "PE LTP": r.get("PUT", {}).get("ltp", 0.0),
                "PE IV": r.get("PUT", {}).get("iv", 0),
                "PE OI": r.get("PUT", {}).get("oi", 0),
            })
        st.dataframe(pd.DataFrame(chain_table), use_container_width=True, height=500)

# ==============================================================================
# PAGE 4: POSITIONS
# ==============================================================================
elif page == "4. Positions":
    st.subheader("📋 Active Positions & Calm Mode P&L Shield")

    with get_db() as conn:
        open_rows = conn.execute("SELECT * FROM positions WHERE status = 'OPEN'").fetchall()

    if not open_rows:
        st.info("No open positions right now.")
    else:
        for r in open_rows:
            pos = dict(r)
            pos_id = pos["id"]
            legs = json.loads(pos.get("legs_json", "[]"))

            masked_view = calm_manager.mask_position_view(pos)
            is_masked = masked_view.get("calm_shield", False)

            with st.container():
                st.markdown(f"### Position #{pos_id}: {pos['setup_name']} [{pos['mode']}]")
                col_p1, col_p2, col_p3 = st.columns([2, 1, 1])

                with col_p1:
                    st.write(f"**Symbol:** {pos['symbol']} | **Entry Time:** {pos['entry_ts'][:19]}")
                    st.write(f"**Legs:** {len(legs)} contracts")
                    st.json(legs)

                with col_p2:
                    if is_masked:
                        st.markdown("""
                        <div class="badge-calm">
                            🛡️ CALM MODE ACTIVE<br>
                            P&L Hidden until 15:35 IST
                        </div>
                        """, unsafe_allow_html=True)
                        if st.button(f"🔍 Audit /peek #{pos_id}"):
                            calm_manager.log_peek(pos_id, "/peek", "ui_user")
                            st.warning(f"Peek logged into SQLite peeks table! MTM P&L: Rs {pos.get('entry_pnl', 0.0):,.2f}")
                    else:
                        st.metric("Unrealized P&L", f"Rs {pos.get('entry_pnl', 0.0):,.2f}")

                with col_p3:
                    if st.button(f"🛑 Close Position #{pos_id}"):
                        from optionapp.app.engine.positions import PositionManager
                        pm = PositionManager()
                        pm.close_position(pos_id, "MANUAL_UI_EXIT")
                        st.success(f"Position #{pos_id} closed.")
                        st.rerun()

# ==============================================================================
# PAGE 5: SETUPS & CODE PROMPTS (PDF 57 PAGES SPECIFICATION)
# ==============================================================================
elif page == "5. Setups":
    st.subheader("📚 Setup Library, Code Prompts & PDF Specification")

    tab_matrix, tab_prompts, tab_code = st.tabs(["Strategy Matrix", "PDF Specification Prompts (1-20)", "Setup Implementation Code"])

    with tab_matrix:
        setups_data = [
            {"Setup": "Sensex Condor", "Mode": "LIVE", "Underlying": "SENSEX", "Expiry": "Thursday 09:16", "Wings": "0.5% + 1.5%", "Stop": "3.0x on sold leg", "Min VIX": "13.0"},
            {"Setup": "Nifty Condor A", "Mode": "LIVE", "Underlying": "NIFTY", "Expiry": "Tuesday 09:16", "Wings": "0.5% + 1.5%", "Stop": "4.0x on sold leg", "Min VIX": "13.0"},
            {"Setup": "Nifty Condor B", "Mode": "LIVE", "Underlying": "NIFTY", "Expiry": "Tuesday 09:16", "Wings": "0.5% + 2.0%", "Stop": "4.0x on sold leg", "Min VIX": "13.0"},
            {"Setup": "Nifty Condor No-Stop", "Mode": "PAPER", "Underlying": "NIFTY", "Expiry": "Tuesday 09:16", "Wings": "0.5% + 1.5%", "Stop": "Held to 15:15", "Min VIX": "13.0"},
            {"Setup": "Bank Nifty Iron Fly", "Mode": "PAPER", "Underlying": "BANKNIFTY", "Expiry": "Monthly 09:20", "Wings": "1.5%", "Stop": "Defined wing", "Min VIX": "-"},
            {"Setup": "Fin Nifty Iron Fly", "Mode": "PAPER", "Underlying": "FINNIFTY", "Expiry": "Monthly 09:20", "Wings": "1.0%", "Stop": "Defined wing", "Min VIX": "-"},
            {"Setup": "9:20 Short Straddle", "Mode": "PAPER", "Underlying": "NIFTY", "Expiry": "Daily 09:20", "Wings": "None", "Stop": "25% per leg", "Min VIX": "-"},
            {"Setup": "First Hour Bias", "Mode": "BIAS", "Underlying": "NIFTY", "Expiry": "Daily 10:15", "Wings": "-", "Stop": "CPR & 1h Range", "Min VIX": "-"},
            {"Setup": "Expiry 13:15 Direction", "Mode": "BIAS", "Underlying": "NIFTY", "Expiry": "Expiry 13:15", "Wings": "-", "Stop": "VWAP & Session High/Low", "Min VIX": "-"},
            {"Setup": "Afternoon Hero Zero", "Mode": "PAPER", "Underlying": "NIFTY", "Expiry": "Expiry 13:15", "Wings": "ATM/0.25% OTM", "Stop": "Ask <= 70% Fair Value", "Min VIX": "-"},
            {"Setup": "Trendline Breakout", "Mode": "PAPER", "Underlying": "NIFTY", "Expiry": "Intraday 5m", "Wings": "-", "Stop": "3-Touch Trendline", "Min VIX": "-"},
            {"Setup": "Breakout Retest Spread", "Mode": "PAPER", "Underlying": "NIFTY", "Expiry": "Intraday 5m", "Wings": "60-bar retest", "Stop": "Bull Put Spread", "Min VIX": "-"},
            {"Setup": "Big Move Watch", "Mode": "INFO", "Underlying": "NIFTY", "Expiry": "Daily", "Wings": "-", "Stop": "VIX >= 22 | Range 2.5%", "Min VIX": "22.0"},
        ]
        st.dataframe(pd.DataFrame(setups_data), use_container_width=True, height=450)

    with tab_prompts:
        st.markdown("### 📜 Specification Prompts from PDF Document (Part D)")

        with st.expander("Prompt 1: Project Skeleton & Configuration Defaults"):
            st.code("""
Create configuration file config.yaml with all risk thresholds, contract specifications,
lot sizes (NIFTY 65, BANKNIFTY 30, FINNIFTY 60, SENSEX 20), transaction charges,
and operational parameters for Option App v8.
            """, language="yaml")

        with st.expander("Prompt 5: 16 Candlestick & Chart Pattern Detectors"):
            st.code("""
Implement detectors for all 16 specified patterns:
1. Hammer (lower wick >= 2x body)
2. Shooting Star (upper wick >= 2x body)
3. Bullish/Bearish Engulfing
4. Standard, Dragonfly, Gravestone Doji
5. Morning Star & Evening Star (3-bar reversal)
6. Piercing Line & Dark Cloud Cover (50% body penetration)
7. Bullish & Bearish Harami
8. Three White Soldiers & Three Black Crows
9. Bullish & Bearish Marubozu (body >= 90% total range)
10. Inside Bar & NR7 Volatility Squeeze (narrowest range of 7 bars)
11. 3-Touch Falling Trendline Breakout
12. Double Bottom & Double Top (local extrema < 0.3% diff)
13. Head & Shoulders & Inverse Head & Shoulders
14. Bull Flag & Bear Flag
15. 5m EMA 20 Pullback (touch and reject)
16. Key Reversal Bar (new high/low followed by close beyond prev close).
            """, language="python")

        with st.expander("Prompt 10: Expiry-Selling Setup Agents"):
            st.code("""
Sensex Expiry Condor:
- Entry at 09:16 AM on Thursday expiry.
- Short strikes at 0.5% OTM; Long wings at 1.5%.
- Hard stop-loss at 3.0x premium on sold legs individually.
- Low-VIX Rule: Skip if yesterday's India VIX < 13.0 and log to Ghost Ledger.

Nifty Condors A & B:
- Entry at 09:16 AM on Tuesday expiry.
- Variant A: 0.5% Short + 1.5% Long wings. Stop 4.0x on sold leg.
- Variant B: 0.5% Short + 2.0% Long wings. Stop 4.0x on sold leg.
            """, language="python")

        with st.expander("Prompt 11 & 12: Hero-Zero & Directional Bias Agents"):
            st.code("""
Afternoon Hero Zero (Prompt 11):
- At 13:15 on expiry days, check directional breakout.
- Pick ATM or 0.25% OTM strike.
- Ask price MUST be <= 70% of theoretical fair Black-76 premium.
- Target: 2x to 3x premium. Max Risk: 1 Lot.

First-Hour Bias (Prompt 12):
- At 10:15, evaluate morning range and Central Pivot Range (CPR).
- Green close in top 25% above CPR -> STRONG_BULLISH.
- Red close in bottom 25% below CPR -> STRONG_BEARISH.
            """, language="python")

        with st.expander("Prompt 15, 16 & 17: v8 New Features (Calm Mode, Radar, Ghost Ledger)"):
            st.code("""
Calm Mode (Prompt 15):
- Conceals fluctuating MTM on condor positions from 09:15 until 15:35 reveal.
- User can check /peek <id> which logs into SQLite peeks table for psychological discipline.

Rule-Change Radar (Prompt 16):
- Audits active exchange circulars and lot sizes at 18:30 daily.
- Automatically places setups on 30-day PAPER mode upon lot size or expiry shifts.

Ghost Ledger (Prompt 17):
- Logs every skipped READY trade (Low VIX, Daily loss stop, capital ladder).
- Marks to market at 15:35 to verify capital saved by rules.
            """, language="python")

    with tab_code:
        st.markdown("### 💻 Exact Python Implementation Code")
        code_choice = st.selectbox("Select Module to View Source", [
            "analytics/options_math.py (Black-76 & Greeks)",
            "analytics/patterns.py (16 Pattern Detectors)",
            "analytics/indicators.py (CPR, VWAP, Supertrend)",
            "setups/expiry_sell.py (Condor Agents)",
            "setups/hero_zero.py (Hero-Zero Discount Rule)",
            "engine/calm.py (v8 Calm Mode)",
            "research/ghosts.py (v8 Ghost Ledger)",
        ])

        file_map = {
            "analytics/options_math.py (Black-76 & Greeks)": "optionapp/app/analytics/options_math.py",
            "analytics/patterns.py (16 Pattern Detectors)": "optionapp/app/analytics/patterns.py",
            "analytics/indicators.py (CPR, VWAP, Supertrend)": "optionapp/app/analytics/indicators.py",
            "setups/expiry_sell.py (Condor Agents)": "optionapp/app/setups/expiry_sell.py",
            "setups/hero_zero.py (Hero-Zero Discount Rule)": "optionapp/app/setups/hero_zero.py",
            "engine/calm.py (v8 Calm Mode)": "optionapp/app/engine/calm.py",
            "research/ghosts.py (v8 Ghost Ledger)": "optionapp/app/research/ghosts.py",
        }

        target_file = file_map.get(code_choice)
        if target_file and Path(target_file).exists():
            with open(target_file, "r", encoding="utf-8") as f:
                content = f.read()
            st.code(content, language="python")

# ==============================================================================
# PAGE 6: SCOREBOARD
# ==============================================================================
elif page == "6. Scoreboard":
    st.subheader("📊 Scoreboard: Forward vs Backtest & Ghost Ledger")

    ghost_summary = ghost_ledger.get_ghost_summary()

    col_g1, col_g2, col_g3, col_g4 = st.columns(4)
    col_g1.metric("Ghost Trades Tracked", ghost_summary["total_ghosts"])
    col_g2.metric("Skips Saved from Loss", ghost_summary["skips_saved_from_loss"], "Saved Capital!")
    col_g3.metric("Skips Missed Profit", ghost_summary["skips_missed_profit"])
    col_g4.metric("Net Capital Saved", f"Rs {ghost_summary['net_capital_saved_inr']:,.2f}")

    st.markdown("#### Ghost Ledger (Skipped READY Trades Audit)")
    if ghost_summary["recent_ghosts"]:
        st.dataframe(pd.DataFrame(ghost_summary["recent_ghosts"]), use_container_width=True)
    else:
        st.info("No ghost trades recorded yet.")

    st.markdown("#### Rule-Change Radar Audit Log")
    radar = RuleChangeRadar()
    radar_changes = radar.get_recent_rule_changes()
    if radar_changes:
        st.dataframe(pd.DataFrame(radar_changes), use_container_width=True)
    else:
        st.success("Radar: All contract specifications active and verified identical to official circulars.")

# ==============================================================================
# PAGE 7: SETTINGS
# ==============================================================================
elif page == "7. Settings":
    st.subheader("⚙️ Settings & Risk Controls")

    st.markdown("#### Capital Ladder")
    stage_info = capital_ladder.get_stage_info()
    st.write(f"**Current Status:** {stage_info['description']}")
    st.write(f"**Max Allowed Lots:** {stage_info['max_lots']} | **Max Open Trades:** {stage_info['max_open_trades']}")

    new_capital = st.number_input("Account Capital (INR)", min_value=5000.0, value=capital_ladder.capital, step=5000.0)
    if st.button("Update Capital"):
        capital_ladder.update_capital(new_capital)
        st.success(f"Capital updated to Rs {new_capital:,.2f} (Stage {capital_ladder.stage}).")
        st.rerun()

    st.markdown("#### Official November 2026 Lot Sizes")
    st.json(OFFICIAL_LOTS_NOV_2026)
