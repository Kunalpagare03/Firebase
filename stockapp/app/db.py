import sqlite3
from pathlib import Path
from typing import Optional
from stockapp.app.settings import settings
from stockapp.app.log import logger

def get_db_connection(db_path: Optional[Path] = None) -> sqlite3.Connection:
    target_path = db_path or settings.db_path
    target_path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(target_path))
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON;")
    return conn

def init_db(db_path: Optional[Path] = None):
    conn = get_db_connection(db_path)
    cursor = conn.cursor()

    # 1. runs
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS runs (
        run_id INTEGER PRIMARY KEY AUTOINCREMENT,
        run_date TEXT NOT NULL UNIQUE,
        traffic_light_state INTEGER NOT NULL,
        traffic_light_color TEXT NOT NULL,
        size_multiplier REAL NOT NULL,
        total_scanned INTEGER DEFAULT 0,
        total_signals INTEGER DEFAULT 0,
        total_picks INTEGER DEFAULT 0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
    """)

    # 2. signals
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS signals (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        run_date TEXT NOT NULL,
        symbol TEXT NOT NULL,
        setup_code TEXT NOT NULL,
        setup_name TEXT NOT NULL,
        horizon TEXT NOT NULL,
        close REAL NOT NULL,
        sma200 REAL,
        rsi2 REAL,
        z20 REAL,
        ram REAL,
        tech_score REAL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
    """)

    # 3. picks
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS picks (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        run_date TEXT NOT NULL,
        symbol TEXT NOT NULL,
        horizon TEXT NOT NULL,
        setup_code TEXT NOT NULL,
        tech_score REAL NOT NULL,
        rank INTEGER NOT NULL,
        planned_entry REAL NOT NULL,
        stop_price REAL NOT NULL,
        target_price REAL,
        quantity INTEGER NOT NULL,
        allocated_capital REAL NOT NULL,
        risk_rs REAL NOT NULL,
        status TEXT DEFAULT 'PENDING',
        rejection_reason TEXT,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
    """)

    # 4. trades
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS trades (
        trade_id INTEGER PRIMARY KEY AUTOINCREMENT,
        symbol TEXT NOT NULL,
        horizon TEXT NOT NULL,
        setup_code TEXT NOT NULL,
        entry_date TEXT NOT NULL,
        entry_price REAL NOT NULL,
        quantity INTEGER NOT NULL,
        stop_price REAL NOT NULL,
        trailing_stop REAL,
        target_price REAL,
        highest_price REAL,
        status TEXT DEFAULT 'OPEN',
        exit_date TEXT,
        exit_price REAL,
        pnl_rs REAL,
        pnl_pct REAL,
        exit_reason TEXT
    );
    """)

    # 5. outcomes
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS outcomes (
        trade_id INTEGER PRIMARY KEY,
        symbol TEXT NOT NULL,
        day_1_ret REAL,
        day_3_ret REAL,
        day_5_ret REAL,
        day_10_ret REAL,
        day_22_ret REAL,
        mfe_pct REAL,
        mae_pct REAL,
        FOREIGN KEY(trade_id) REFERENCES trades(trade_id)
    );
    """)

    # 6. ai_calls
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS ai_calls (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        run_date TEXT NOT NULL,
        symbol TEXT NOT NULL,
        prompt_type TEXT NOT NULL,
        model TEXT NOT NULL,
        tokens_used INTEGER DEFAULT 0,
        cost_usd REAL DEFAULT 0.0,
        decision TEXT,
        notes TEXT,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
    """)

    # 7. lt_metrics
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS lt_metrics (
        symbol TEXT PRIMARY KEY,
        as_of_date TEXT NOT NULL,
        roce_5yr_avg REAL NOT NULL,
        roce_min REAL NOT NULL,
        sales_growth_3yr REAL NOT NULL,
        profit_growth_3yr REAL NOT NULL,
        debt_equity REAL NOT NULL,
        cfo_to_pat REAL NOT NULL,
        score_total REAL NOT NULL,
        quality_pass INTEGER NOT NULL
    );
    """)

    # 8. lt_dossiers
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS lt_dossiers (
        symbol TEXT PRIMARY KEY,
        as_of_date TEXT NOT NULL,
        bull_case TEXT,
        bear_case TEXT,
        fact_check TEXT,
        conviction_tier TEXT,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
    """)

    # 9. lt_holdings
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS lt_holdings (
        symbol TEXT PRIMARY KEY,
        tranche_1_date TEXT,
        tranche_1_qty INTEGER DEFAULT 0,
        tranche_1_price REAL DEFAULT 0.0,
        tranche_2_date TEXT,
        tranche_2_qty INTEGER DEFAULT 0,
        tranche_2_price REAL DEFAULT 0.0,
        tranche_3_date TEXT,
        tranche_3_qty INTEGER DEFAULT 0,
        tranche_3_price REAL DEFAULT 0.0,
        avg_cost REAL DEFAULT 0.0,
        total_qty INTEGER DEFAULT 0,
        status TEXT DEFAULT 'ACCUMULATING'
    );
    """)

    # 10. lt_actions
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS lt_actions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        action_date TEXT NOT NULL,
        symbol TEXT NOT NULL,
        action_type TEXT NOT NULL,
        qty INTEGER NOT NULL,
        price REAL NOT NULL,
        reason TEXT
    );
    """)

    conn.commit()
    conn.close()
    logger.info(f"Database schema verified at {settings.db_path}")

if __name__ == "__main__":
    init_db()
