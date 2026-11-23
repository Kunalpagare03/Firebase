"""Database interface for Option App v8 (Part C8 Schema)."""

import json
import sqlite3
import threading
from contextlib import contextmanager
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional
from optionapp.app.log import get_logger

logger = get_logger("db")

DEFAULT_DB_PATH = Path("optionapp.sqlite")
_lock = threading.Lock()


def get_db_path() -> Path:
    candidates = [
        Path("optionapp.sqlite"),
        Path("optionapp/optionapp.sqlite"),
        Path(__file__).resolve().parent.parent / "optionapp.sqlite",
    ]
    for c in candidates:
        if c.exists():
            return c.resolve()
    return Path("optionapp.sqlite")


def init_db(db_path: Optional[Path] = None):
    """Initialize all 10 tables according to Part C8."""
    target_path = db_path or get_db_path()
    with _lock:
        conn = sqlite3.connect(str(target_path))
        cursor = conn.cursor()

        # 1. Candles (1m, 5m, 15m, 1d)
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS candles (
            symbol TEXT NOT NULL,
            tf TEXT NOT NULL,
            ts TEXT NOT NULL,
            open REAL NOT NULL,
            high REAL NOT NULL,
            low REAL NOT NULL,
            close REAL NOT NULL,
            volume REAL DEFAULT 0,
            oi REAL DEFAULT 0,
            PRIMARY KEY (symbol, tf, ts)
        );
        """)

        # 2. Chain Snapshots
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS chain_snapshots (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ts TEXT NOT NULL,
            symbol TEXT NOT NULL,
            spot REAL NOT NULL,
            expiry TEXT NOT NULL,
            data_json TEXT NOT NULL
        );
        """)

        # 3. Alerts
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS alerts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ts TEXT NOT NULL,
            setup_name TEXT NOT NULL,
            symbol TEXT NOT NULL,
            stage TEXT NOT NULL, -- FORMING, READY, EXIT, CANCELLED
            data_json TEXT NOT NULL,
            status TEXT DEFAULT 'NEW'
        );
        """)

        # 4. Positions (Live & Paper)
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS positions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            setup_name TEXT NOT NULL,
            symbol TEXT NOT NULL,
            mode TEXT NOT NULL DEFAULT 'PAPER', -- LIVE or PAPER
            status TEXT NOT NULL, -- OPEN, CLOSED, STOPPED
            entry_ts TEXT NOT NULL,
            exit_ts TEXT,
            entry_pnl REAL DEFAULT 0.0,
            exit_pnl REAL DEFAULT 0.0,
            legs_json TEXT NOT NULL,
            metadata_json TEXT DEFAULT '{}'
        );
        """)

        # 5. Backtest Trades
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS backtest_trades (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            setup_name TEXT NOT NULL,
            symbol TEXT NOT NULL,
            entry_date TEXT NOT NULL,
            exit_date TEXT NOT NULL,
            entry_price REAL NOT NULL,
            exit_price REAL NOT NULL,
            pnl REAL NOT NULL,
            costs REAL NOT NULL,
            net_pnl REAL NOT NULL,
            max_dd REAL DEFAULT 0.0,
            legs_json TEXT DEFAULT '[]'
        );
        """)

        # 6. Setup Stats
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS setup_stats (
            setup_name TEXT PRIMARY KEY,
            updated_at TEXT NOT NULL,
            win_rate REAL NOT NULL,
            profit_factor REAL NOT NULL,
            max_drawdown REAL NOT NULL,
            sample_size INTEGER NOT NULL,
            expectancy REAL NOT NULL
        );
        """)

        # 7. AI Calls
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS ai_calls (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ts TEXT NOT NULL,
            agent_name TEXT NOT NULL,
            prompt_tokens INTEGER NOT NULL,
            completion_tokens INTEGER NOT NULL,
            cost_usd REAL NOT NULL,
            cached INTEGER DEFAULT 0
        );
        """)

        # 8. Setup Changes (Rule-Change Radar / Paper demotions)
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS setup_changes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ts TEXT NOT NULL,
            setup_name TEXT NOT NULL,
            old_mode TEXT NOT NULL,
            new_mode TEXT NOT NULL,
            reason TEXT NOT NULL
        );
        """)

        # 9. Ghosts (Ghost Ledger for skipped setups)
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS ghosts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ts TEXT NOT NULL,
            setup_name TEXT NOT NULL,
            symbol TEXT NOT NULL,
            skip_reason TEXT NOT NULL,
            entry_price REAL NOT NULL,
            eod_price REAL DEFAULT 0.0,
            simulated_pnl REAL DEFAULT 0.0,
            resolved INTEGER DEFAULT 0,
            metadata_json TEXT DEFAULT '{}'
        );
        """)

        # 10. Peeks (Calm Mode audit log)
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS peeks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ts TEXT NOT NULL,
            position_id INTEGER,
            command TEXT NOT NULL,
            user_id TEXT DEFAULT 'local'
        );
        """)

        conn.commit()
        conn.close()
        logger.info(f"Database initialized at {target_path}")


@contextmanager
def get_db(db_path: Optional[Path] = None):
    path = db_path or get_db_path()
    conn = sqlite3.connect(str(path))
    conn.row_factory = sqlite3.Row
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()
