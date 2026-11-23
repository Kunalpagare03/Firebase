import sys
from datetime import datetime
from typing import Optional
from stockapp.app.settings import settings
from stockapp.app.log import logger
from stockapp.app.db import get_db_connection, init_db
from stockapp.app.universe import compute_universe_metrics
from stockapp.app.market import MarketTrafficLight
from stockapp.app.scanner import scan_all_setups
from stockapp.app.planner import plan_nightly_trades
from stockapp.app.trades import get_open_trades, update_trades_daily, record_new_picks_as_trades
from stockapp.app.report_nightly import generate_nightly_reports

def run_nightly_scan(run_date: Optional[str] = None, offline_mode: bool = False):
    """
    Executes the complete end-of-day quantitative scan.
    """
    if run_date is None:
        run_date = datetime.now().strftime("%Y-%m-%d")

    logger.info(f"========== STARTING NIGHTLY QUANT SCAN: {run_date} ==========")
    init_db()

    # 1. Universe Metrics & Breadth
    snapshot_df, market_breadth, sector_breadth = compute_universe_metrics(offline_mode=offline_mode)
    if snapshot_df.empty:
        logger.error("Universe snapshot empty. Aborting nightly run.")
        return

    # 2. Market Traffic Light
    tl_engine = MarketTrafficLight()
    tl_eval = tl_engine.evaluate(market_breadth, offline_mode=offline_mode)

    # 3. Update Open Trades (Exits & Trailing Stops)
    price_lookup = {}
    for _, row in snapshot_df.iterrows():
        price_lookup[row["symbol"]] = {
            "close": float(row["close"]),
            "high": float(row["high"]),
            "low": float(row["low"]),
            "h_prev": float(row.get("h_prev", row["high"]))
        }
    update_trades_daily(run_date, price_lookup)
    open_trades = get_open_trades()

    # 4. Scanner Execution (10 Setups + v4 Swing Setups)
    swing_df, positional_df = scan_all_setups(snapshot_df, tl_eval)
    total_signals = len(swing_df) + len(positional_df)
    logger.info(f"Scanner generated {total_signals} total signals ({len(swing_df)} swing, {len(positional_df)} positional)")

    # 5. Persist Signals to DB
    conn = get_db_connection()
    cursor = conn.cursor()
    all_signals = []
    if not swing_df.empty:
        all_signals.extend(swing_df.to_dict(orient="records"))
    if not positional_df.empty:
        all_signals.extend(positional_df.to_dict(orient="records"))

    for sig in all_signals:
        cursor.execute("""
            INSERT INTO signals (
                run_date, symbol, setup_code, setup_name, horizon, close,
                sma200, rsi2, z20, ram, tech_score
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            run_date, sig["symbol"], sig["setup_code"], sig["setup_name"], sig["horizon"],
            sig["close"], sig["sma200"], sig["rsi2"], sig["z20"], sig["ram"], sig.get("tech_score", 0.0)
        ))

    # 6. Trade Planner
    accepted_picks, rejected_picks = plan_nightly_trades(swing_df, positional_df, tl_eval, open_trades)
    logger.info(f"Trade planner accepted {len(accepted_picks)} picks (rejected {len(rejected_picks)})")

    # 7. Persist Picks to DB
    for rank, p in enumerate(accepted_picks, start=1):
        cursor.execute("""
            INSERT INTO picks (
                run_date, symbol, horizon, setup_code, tech_score, rank,
                planned_entry, stop_price, target_price, quantity,
                allocated_capital, risk_rs, status, rejection_reason
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'APPROVED', NULL)
        """, (
            run_date, p["symbol"], p["horizon"], p["setup_code"], p["tech_score"], rank,
            p["planned_entry"], p["stop_price"], p["target_price"], p["quantity"],
            p["allocated_capital"], p["risk_rs"]
        ))

    for r in rejected_picks:
        cursor.execute("""
            INSERT INTO picks (
                run_date, symbol, horizon, setup_code, tech_score, rank,
                planned_entry, stop_price, target_price, quantity,
                allocated_capital, risk_rs, status, rejection_reason
            ) VALUES (?, ?, ?, ?, ?, 999, 0, 0, 0, 0, 0, 0, 'REJECTED', ?)
        """, (
            run_date, r["symbol"], r["horizon"], "VETO", r.get("tech_score", 0), r["reason"]
        ))

    # 8. Record Run
    cursor.execute("""
        INSERT OR REPLACE INTO runs (
            run_date, traffic_light_state, traffic_light_color, size_multiplier,
            total_scanned, total_signals, total_picks
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
    """, (
        run_date, tl_eval["state"], tl_eval["color"], tl_eval["effective_size_multiplier"],
        len(snapshot_df), total_signals, len(accepted_picks)
    ))
    conn.commit()
    conn.close()

    # 9. Enter New Trades
    record_new_picks_as_trades(accepted_picks, run_date)

    # 10. Generate Reports
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM trades WHERE status = 'CLOSED' ORDER BY exit_date DESC LIMIT 10")
    closed_trades = [dict(r) for r in cursor.fetchall()]
    conn.close()

    reports = generate_nightly_reports(
        run_date, tl_eval, market_breadth, accepted_picks, get_open_trades(), closed_trades
    )

    logger.info(f"========== NIGHTLY SCAN COMPLETE FOR {run_date} ==========")
    return {
        "run_date": run_date,
        "traffic_light": tl_eval,
        "picks": accepted_picks,
        "reports": reports
    }

if __name__ == "__main__":
    offline = "--offline" in sys.argv
    run_nightly_scan(offline_mode=offline)
