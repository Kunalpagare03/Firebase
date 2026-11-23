"""Candle aggregator, multi-timeframe builder, and SQLite cache (Part C1 & C8)."""

from datetime import datetime, timedelta
from typing import List, Optional
import pandas as pd
from optionapp.app.db import get_db
from optionapp.app.log import get_logger

logger = get_logger("candles")


def save_candles(df: pd.DataFrame, symbol: str, timeframe: str):
    """Save candles into SQLite table 'candles'."""
    if df.empty:
        return

    records = []
    for ts, row in df.iterrows():
        ts_str = ts.isoformat() if hasattr(ts, "isoformat") else str(ts)
        records.append((
            symbol.upper(),
            timeframe,
            ts_str,
            float(row["open"]),
            float(row["high"]),
            float(row["low"]),
            float(row["close"]),
            float(row.get("volume", 0.0)),
            float(row.get("oi", 0.0)),
        ))

    with get_db() as conn:
        conn.executemany("""
        INSERT OR REPLACE INTO candles (symbol, tf, ts, open, high, low, close, volume, oi)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, records)


def load_candles(symbol: str, timeframe: str, limit: int = 200) -> pd.DataFrame:
    """Load latest candles from SQLite table 'candles'."""
    with get_db() as conn:
        rows = conn.execute("""
        SELECT ts, open, high, low, close, volume, oi
        FROM candles
        WHERE symbol = ? AND tf = ?
        ORDER BY ts DESC
        LIMIT ?
        """, (symbol.upper(), timeframe, limit)).fetchall()

    if not rows:
        return pd.DataFrame(columns=["open", "high", "low", "close", "volume", "oi"])

    # Reverse to chronological order
    data = [dict(r) for r in reversed(rows)]
    df = pd.DataFrame(data)
    df["ts"] = pd.to_datetime(df["ts"])
    df.set_index("ts", inplace=True)
    return df


def resample_candles(df_1m: pd.DataFrame, timeframe: str = "5m") -> pd.DataFrame:
    """Resample 1-minute OHLCV candles to 5m, 15m, 1h, or 1d."""
    if df_1m.empty:
        return df_1m

    rule_map = {
        "5m": "5min",
        "15m": "15min",
        "1h": "1h",
        "1d": "1D",
    }
    rule = rule_map.get(timeframe, "5min")

    resampled = df_1m.resample(rule).agg({
        "open": "first",
        "high": "max",
        "low": "min",
        "close": "last",
        "volume": "sum",
        "oi": "last",
    }).dropna()

    return resampled
