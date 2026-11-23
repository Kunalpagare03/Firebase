from datetime import datetime, date
from typing import List, Dict, Any, Optional
import pandas as pd
from stockapp.app.db import get_db_connection
from stockapp.app.settings import settings
from stockapp.app.log import logger

def record_new_picks_as_trades(picks: List[Dict[str, Any]], run_date: str):
    """Inserts approved picks into the trades table as OPEN positions."""
    if not picks:
        return
    conn = get_db_connection()
    cursor = conn.cursor()
    for p in picks:
        cursor.execute("""
            INSERT INTO trades (
                symbol, horizon, setup_code, entry_date, entry_price, quantity,
                stop_price, trailing_stop, target_price, highest_price, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN')
        """, (
            p["symbol"], p["horizon"], p["setup_code"], run_date, p["planned_entry"],
            p["quantity"], p["stop_price"], p["stop_price"], p["target_price"], p["planned_entry"]
        ))
    conn.commit()
    conn.close()
    logger.info(f"Recorded {len(picks)} new trades for {run_date}")

def get_open_trades() -> List[Dict[str, Any]]:
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM trades WHERE status = 'OPEN'")
    rows = [dict(r) for r in cursor.fetchall()]
    conn.close()
    return rows

def update_trades_daily(current_date: str, price_lookup: Dict[str, Dict[str, float]]):
    """
    Checks stop loss, trailing stop, and time/target exits for all open trades.
    - Stop loss: hard 8% below entry
    - Trailing stop: active after +8% gain
    - Swing exit: Close of Day 5 or first close > previous day high
    - Positional exit: Close of Day 22
    """
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM trades WHERE status = 'OPEN'")
    open_trades = [dict(r) for r in cursor.fetchall()]

    for trade in open_trades:
        sym = trade["symbol"]
        tid = trade["trade_id"]
        horizon = trade["horizon"]
        entry_price = float(trade["entry_price"])
        stop_price = float(trade["stop_price"])
        highest_price = float(trade["highest_price"] or entry_price)
        trailing_stop = float(trade["trailing_stop"] or stop_price)

        market_data = price_lookup.get(sym)
        if not market_data:
            continue

        c = market_data["close"]
        h = market_data["high"]
        l = market_data["low"]
        h_prev = market_data.get("h_prev", h)

        # Update highest price seen
        if h > highest_price:
            highest_price = h
            # Update trailing stop if in profit >= 8%
            if (highest_price - entry_price) / entry_price >= settings.exits.trailing_stop_profit_trigger:
                new_trail = round(highest_price * (1.0 - settings.exits.trailing_stop_step), 2)
                trailing_stop = max(trailing_stop, new_trail)

        exit_triggered = False
        exit_price = 0.0
        exit_reason = ""

        # Calculate holding days
        entry_d = datetime.strptime(trade["entry_date"], "%Y-%m-%d").date()
        curr_d = datetime.strptime(current_date, "%Y-%m-%d").date()
        days_held = (curr_d - entry_d).days

        # 1. Check Hard Stop & Trailing Stop
        effective_stop = max(stop_price, trailing_stop)
        if l <= effective_stop:
            exit_triggered = True
            exit_price = effective_stop
            exit_reason = "STOP_LOSS_HIT" if effective_stop == stop_price else "TRAILING_STOP_HIT"

        # 2. Check Swing Exits
        elif horizon == "SWING":
            # First close above previous day's high
            if c > h_prev:
                exit_triggered = True
                exit_price = c
                exit_reason = "SWING_CLOSE_ABOVE_PREV_HIGH"
            # Or Max Days 5
            elif days_held >= settings.exits.swing_max_days:
                exit_triggered = True
                exit_price = c
                exit_reason = "SWING_DAY_5_TIME_EXIT"

        # 3. Check Positional Exits
        elif horizon == "POSITIONAL":
            if days_held >= settings.exits.positional_max_days:
                exit_triggered = True
                exit_price = c
                exit_reason = "POSITIONAL_DAY_22_TIME_EXIT"

        # Execute exit if triggered
        if exit_triggered:
            pnl_rs = round((exit_price - entry_price) * trade["quantity"], 2)
            pnl_pct = round(((exit_price - entry_price) / entry_price) * 100.0, 2)
            cursor.execute("""
                UPDATE trades SET
                    status = 'CLOSED',
                    exit_date = ?,
                    exit_price = ?,
                    pnl_rs = ?,
                    pnl_pct = ?,
                    exit_reason = ?,
                    highest_price = ?,
                    trailing_stop = ?
                WHERE trade_id = ?
            """, (current_date, exit_price, pnl_rs, pnl_pct, exit_reason, highest_price, trailing_stop, tid))
            logger.info(f"Closed trade #{tid} {sym} ({horizon}): {exit_reason} at Rs {exit_price} (P&L: {pnl_pct}%)")
        else:
            # Update in-flight metrics
            cursor.execute("""
                UPDATE trades SET
                    highest_price = ?,
                    trailing_stop = ?
                WHERE trade_id = ?
            """, (highest_price, trailing_stop, tid))

    conn.commit()
    conn.close()
