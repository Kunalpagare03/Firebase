import streamlit as st
import pandas as pd
import sqlite3
from datetime import datetime
from pathlib import Path
from stockapp.app.settings import settings
from stockapp.app.db import get_db_connection
from stockapp.app.nightly import run_nightly_scan
from stockapp.app.longterm import evaluate_long_term_candidates
from stockapp.app.backtest import run_portfolio_backtest
from stockapp.app.scoreboard import compute_scoreboard_metrics
from stockapp.app.readiness import check_live_readiness

st.set_page_config(
    page_title="Stock App (v4) - Quant Trading Terminal",
    page_icon="📈",
    layout="wide"
)

st.title("📈 Stock App (v4) - Swing, Positional & Long-Term Terminal")
st.caption("Quantitative System Specification v4 (18-Nov-2026) | 10 Scanners, Traffic Light, 8% Risk Model & Long-Term Compounder")

# Sidebar Navigation
page = st.sidebar.radio(
    "Navigation",
    ["1. Dashboard", "2. Tonight's Picks", "3. Open Trades", "4. Long-Term Engine", "5. Scoreboard & Backtest", "6. Settings"]
)

conn = get_db_connection()

# -------------------------------------------------------------
# PAGE 1: DASHBOARD
# -------------------------------------------------------------
if page == "1. Dashboard":
    st.header("🚦 Market Traffic Light & Regime Dashboard")

    # Fetch latest run
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM runs ORDER BY run_id DESC LIMIT 1")
    latest_run = cursor.fetchone()

    col1, col2, col3, col4 = st.columns(4)
    if latest_run:
        state = latest_run["traffic_light_state"]
        color = latest_run["traffic_light_color"]
        mult = latest_run["size_multiplier"]
        run_date = latest_run["run_date"]

        color_emoji = "🟢" if color == "GREEN" else ("🟡" if color == "AMBER" else "🔴")
        col1.metric("Traffic Light State", f"{color_emoji} State {state} ({color})")
        col2.metric("Size Multiplier", f"{mult}x")
        col3.metric("Last Scanned", f"{latest_run['total_scanned']} stocks")
        col4.metric("Approved Picks", f"{latest_run['total_picks']} picks")
    else:
        col1.metric("Traffic Light", "🟢 State 1 (GREEN)")
        col2.metric("Size Multiplier", "1.0x")
        col3.metric("Status", "Ready for nightly scan")

    st.markdown("---")
    st.subheader("⚡ Quick Actions")
    c_btn1, c_btn2 = st.columns(2)
    if c_btn1.button("▶ Run Nightly Quant Scan (Live/Cached)", type="primary"):
        with st.spinner("Running complete universe quantitative scan & trade planner..."):
            res = run_nightly_scan(offline_mode=False)
            st.success("Nightly scan complete! Picks recorded to database and reports generated.")
            st.rerun()

    if c_btn2.button("🧪 Run Fast Offline Replay Scan"):
        with st.spinner("Executing fast offline replay scan..."):
            res = run_nightly_scan(offline_mode=True)
            st.success("Offline scan complete!")
            st.rerun()

    st.markdown("---")
    st.subheader("📋 Active Open Positions Overview")
    cursor.execute("SELECT * FROM trades WHERE status = 'OPEN'")
    open_trades = [dict(r) for r in cursor.fetchall()]
    if open_trades:
        df_open = pd.DataFrame(open_trades)[["trade_id", "symbol", "horizon", "setup_code", "entry_date", "entry_price", "stop_price", "trailing_stop", "quantity"]]
        st.dataframe(df_open, use_container_width=True)
    else:
        st.info("No active open positions. Run tonight's scan to generate new entries.")

# -------------------------------------------------------------
# PAGE 2: TONIGHT'S PICKS
# -------------------------------------------------------------
elif page == "2. Tonight's Picks":
    st.header("🎯 Tonight's Quantitative Trade Picks")
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM picks ORDER BY id DESC LIMIT 20")
    picks_data = [dict(r) for r in cursor.fetchall()]

    if picks_data:
        df_picks = pd.DataFrame(picks_data)
        st.dataframe(df_picks, use_container_width=True)

        st.markdown("### 📊 Rules Enforced")
        st.markdown("""
        - **Positional Slots (up to 6):** Allocated first to highest Technical Score. 22-day hold or 8% stop.
        - **Swing Slots (up to 4):** Technical Score $\ge 55$. Uses ONLY Setup 1 (Crash Dip) or Setup 2 (Panic Dip in Leader) when Nifty > SMA200.
        - **Sizing:** Fixed 1% capital risk (Rs 10,000 max loss per trade with 8% stop).
        - **Sector Limit:** Max 2 trades per sector. Max 3 new trades a night.
        """)
    else:
        st.info("No picks recorded yet. Run the nightly scan from Dashboard.")

# -------------------------------------------------------------
# PAGE 3: OPEN TRADES
# -------------------------------------------------------------
elif page == "3. Open Trades":
    st.header("💼 Open Positions Book & Exits")
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM trades WHERE status = 'OPEN'")
    open_trades = [dict(r) for r in cursor.fetchall()]

    if open_trades:
        df_open = pd.DataFrame(open_trades)
        st.dataframe(df_open, use_container_width=True)
    else:
        st.info("No positions currently open.")

    st.subheader("📜 Recently Closed Trades")
    cursor.execute("SELECT * FROM trades WHERE status = 'CLOSED' ORDER BY trade_id DESC LIMIT 20")
    closed_trades = [dict(r) for r in cursor.fetchall()]
    if closed_trades:
        st.dataframe(pd.DataFrame(closed_trades), use_container_width=True)

# -------------------------------------------------------------
# PAGE 4: LONG-TERM ENGINE
# -------------------------------------------------------------
elif page == "4. Long-Term Engine":
    st.header("💎 Long-Term Compounder Engine (3-5 Years)")
    st.markdown("**6-Rule Quality Gate:** ROCE 5-yr $\ge 15\%$, Single yr $\ge 10\%$, Sales Gr $\ge 10\%$, PAT Gr $\ge 10\%$, D/E $\le 0.5$, CFO/PAT $\ge 70\%$.")

    if st.button("Evaluate Long-Term Candidates"):
        with st.spinner("Evaluating Screener.in fundamentals & timing..."):
            lt_results = evaluate_long_term_candidates(offline_mode=True)
            st.success("Evaluation complete!")

    cursor = conn.cursor()
    cursor.execute("SELECT * FROM lt_metrics ORDER BY score_total DESC")
    lt_rows = [dict(r) for r in cursor.fetchall()]
    if lt_rows:
        st.dataframe(pd.DataFrame(lt_rows), use_container_width=True)

    st.subheader("📚 7-Analyst Deep Research Dossiers")
    cursor.execute("SELECT * FROM lt_dossiers")
    dossiers = [dict(r) for r in cursor.fetchall()]
    if dossiers:
        for d in dossiers:
            with st.expander(f"📁 {d['symbol']} - {d['conviction_tier']}"):
                st.markdown(f"**Bull Case:** {d['bull_case']}")
                st.markdown(f"**Bear Case:** {d['bear_case']}")
                st.markdown(f"**Fact Check:** {d['fact_check']}")

# -------------------------------------------------------------
# PAGE 5: SCOREBOARD & BACKTEST
# -------------------------------------------------------------
elif page == "5. Scoreboard & Backtest":
    st.header("🏆 Performance Scoreboard & Backtest")

    col1, col2, col3, col4 = st.columns(4)
    sb = compute_scoreboard_metrics()
    col1.metric("Closed Trades", sb["total_closed_trades"])
    col2.metric("Win Rate", f"{sb['win_rate_pct']}%")
    col3.metric("Profit Factor", sb["profit_factor"])
    col4.metric("Nifty Alpha", f"{sb['nifty_beat_margin_pct']}%")

    st.markdown("---")
    st.subheader("🔬 Portfolio Backtest Verification (Part F1 Benchmarks)")
    if st.button("Run 1-Year Portfolio Simulation"):
        bt = run_portfolio_backtest()
        b_col1, b_col2, b_col3, b_col4 = st.columns(4)
        b_col1.metric("Annualized CAGR", f"{bt['cagr_pct']}%", f"vs Nifty {bt['benchmark_cagr_pct']}%")
        b_col2.metric("Backtest Win Rate", f"{bt['win_rate_pct']}%")
        b_col3.metric("Max Drawdown", f"{bt['max_drawdown_pct']}%")
        b_col4.metric("Sharpe Ratio", bt["sharpe_ratio"])

    st.markdown("---")
    st.subheader("🛡 Live Capital Deployment Readiness")
    ready_status = check_live_readiness()
    st.info(f"**Status:** {ready_status['recommendation']}")
    st.write(f"- Paper Trades Completed: {ready_status['total_paper_trades']} / {ready_status['required_paper_trades']}")
    st.write(f"- Current Win Rate: {ready_status['current_win_rate_pct']}% / {ready_status['required_min_win_rate_pct']}%")

# -------------------------------------------------------------
# PAGE 6: SETTINGS
# -------------------------------------------------------------
elif page == "6. Settings":
    st.header("⚙ System Configuration & Risk Parameters")
    st.json(settings.model_dump(exclude={"base_dir"}))

conn.close()
