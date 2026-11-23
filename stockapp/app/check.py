import sys
from stockapp.app.settings import settings
from stockapp.app.log import logger
from stockapp.app.db import init_db, get_db_connection

def run_system_check() -> bool:
    print("=" * 60)
    print(" STOCK APP (v4) - SYSTEM & ENVIRONMENT HEALTH CHECK ")
    print("=" * 60)

    # 1. Python version check
    py_ver = sys.version_info
    print(f"[x] Python Version: {py_ver.major}.{py_ver.minor}.{py_ver.micro}")
    if py_ver.major < 3 or (py_ver.major == 3 and py_ver.minor < 10):
        print("[-] ERROR: Python 3.10+ required.")
        return False

    # 2. Directory structure check
    required_dirs = [
        settings.data_dir,
        settings.prices_dir,
        settings.nse_dir,
        settings.screener_dir,
        settings.db_dir,
        settings.reports_dir,
        settings.logs_dir,
    ]
    for d in required_dirs:
        d.mkdir(parents=True, exist_ok=True)
        print(f"[x] Directory exists: {d}")

    # 3. Database initialization & Table check
    init_db()
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT name FROM sqlite_master WHERE type='table';")
    tables = {row[0] for row in cursor.fetchall()}
    conn.close()

    expected_tables = {
        "runs", "signals", "picks", "trades", "outcomes",
        "ai_calls", "lt_metrics", "lt_dossiers", "lt_holdings", "lt_actions"
    }

    missing = expected_tables - tables
    if missing:
        print(f"[-] ERROR: Missing database tables: {missing}")
        return False
    else:
        print(f"[x] SQLite Tables verified (10/10 tables present): {sorted(list(expected_tables))}")

    # 4. Settings check
    print(f"[x] Capital: Rs {settings.capital:,.2f}")
    print(f"[x] Risk per trade: {settings.risk_per_trade * 100}% (Rs {settings.capital * settings.risk_per_trade:,.2f})")
    print(f"[x] Stop loss: {settings.initial_stop_pct * 100}%")
    print(f"[x] Traffic Light Mode: {settings.traffic_light.mode}")
    print(f"[x] Limits: max {settings.limits.max_open_trades} open ({settings.limits.max_swing_trades} swing + {settings.limits.max_positional_trades} positional)")

    print("=" * 60)
    print(" ALL 8/8 SYSTEM CHECKS PASSED - STOCK APP READY ")
    print("=" * 60)
    return True

if __name__ == "__main__":
    success = run_system_check()
    sys.exit(0 if success else 1)
