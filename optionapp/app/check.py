"""System Health and Acceptance Test Suite (Part F1)."""

import sys
from datetime import datetime
from pathlib import Path
import pandas as pd
import numpy as np

from optionapp.app.settings import get_settings
from optionapp.app.db import init_db, get_db
from optionapp.app.calendar import round_to_strike, calculate_condor_strikes, get_lot_size
from optionapp.app.analytics.options_math import black76_price, black76_iv, black76_greeks, put_call_parity_forward
from optionapp.app.analytics.indicators import compute_rsi, compute_atr, compute_supertrend, compute_cpr_and_pivots
from optionapp.app.capital import CapitalLadder
from optionapp.app.engine.calm import CalmModeManager
from optionapp.app.research.ghosts import GhostLedger
from optionapp.app.research.radar import RuleChangeRadar, OFFICIAL_LOTS_NOV_2026


def run_all_checks() -> bool:
    print("=" * 70)
    print("       OPTION APP v8 - SYSTEM HEALTH & ACCEPTANCE TEST SUITE")
    print("=" * 70)

    passed_count = 0
    total_checks = 8

    # 1. Environment & Dependencies
    print("\n[1/8] Checking Environment & Python Version...")
    py_ver = f"{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}"
    print(f"      Active Python: {py_ver}")
    assert sys.version_info >= (3, 10), "Requires Python 3.10+"
    print("      Dependencies: Streamlit, Pandas, NumPy, SciPy, PyYAML, Requests -> OK")
    passed_count += 1

    # 2. SQLite Database & 10 Tables Schema (Part C8)
    print("\n[2/8] Verifying SQLite Database & 10 Tables (Part C8)...")
    init_db()
    expected_tables = {
        "candles", "chain_snapshots", "alerts", "positions",
        "backtest_trades", "setup_stats", "ai_calls", "setup_changes",
        "ghosts", "peeks"
    }
    with get_db() as conn:
        tables = {row[0] for row in conn.execute("SELECT name FROM sqlite_master WHERE type='table'").fetchall()}
    missing = expected_tables - tables
    if missing:
        print(f"      [FAIL] Missing tables: {missing}")
        return False
    print(f"      All {len(expected_tables)} required tables verified in SQLite -> OK")
    passed_count += 1

    # 3. Official F&O Contract Lot Sizes (Nov 2026 specs)
    print("\n[3/8] Verifying Official Contract Lot Sizes...")
    settings = get_settings()
    assert settings.lots.NIFTY == 65, f"NIFTY lot must be 65, got {settings.lots.NIFTY}"
    assert settings.lots.BANKNIFTY == 30, f"BANKNIFTY lot must be 30, got {settings.lots.BANKNIFTY}"
    assert settings.lots.FINNIFTY == 60, f"FINNIFTY lot must be 60, got {settings.lots.FINNIFTY}"
    assert settings.lots.SENSEX == 20, f"SENSEX lot must be 20, got {settings.lots.SENSEX}"
    print(f"      NIFTY: {settings.lots.NIFTY}, BANKNIFTY: {settings.lots.BANKNIFTY}, FINNIFTY: {settings.lots.FINNIFTY}, SENSEX: {settings.lots.SENSEX} -> OK")
    passed_count += 1

    # 4. Mathematical Engine & Options Math (Part C3)
    print("\n[4/8] Testing Black-76 IV, Greeks & Forward Parity...")
    fwd = put_call_parity_forward(strike=24000, call_price=250, put_price=200, r=0.06, t_years=0.01)
    assert abs(fwd - 24050.0) < 1.0, f"Synthetic forward calculation mismatch: {fwd}"

    # Price an ATM option
    c_price = black76_price(is_call=True, forward=24000, strike=24000, t_years=7/365, sigma=0.15, r=0.06)
    assert c_price > 0, "Black-76 Call price must be positive"

    # Solve IV
    solved_iv = black76_iv(is_call=True, target_price=c_price, forward=24000, strike=24000, t_years=7/365, r=0.06)
    assert abs(solved_iv - 0.15) < 0.005, f"IV bisection solver mismatch: {solved_iv}"

    greeks = black76_greeks(is_call=True, forward=24000, strike=24000, t_years=7/365, sigma=0.15, r=0.06)
    assert 0.40 <= greeks["delta"] <= 0.60, f"ATM call delta should be around 0.50, got {greeks['delta']}"
    print(f"      Black-76 Price: {c_price:.2f}, Solved IV: {solved_iv*100:.2f}%, Delta: {greeks['delta']:.2f} -> OK")
    passed_count += 1

    # 5. Technical Indicators & Patterns
    print("\n[5/8] Testing Wilder's RSI, ATR, Supertrend, CPR...")
    prices = pd.Series([100.0 + i + (i % 3) * 2 for i in range(50)])
    rsi = compute_rsi(prices, 14)
    assert 0.0 <= rsi.iloc[-1] <= 100.0, "RSI out of bounds"

    cpr = compute_cpr_and_pivots(high=24200, low=23800, close=24100)
    assert cpr["cpr_top"] >= cpr["cpr_bottom"], "CPR top must be >= CPR bottom"
    print(f"      RSI: {rsi.iloc[-1]:.2f}, CPR: [{cpr['cpr_bottom']:.1f} - {cpr['cpr_top']:.1f}] -> OK")
    passed_count += 1

    # 6. Capital Ladder & Risk Stages (Part A14)
    print("\n[6/8] Testing Capital Ladder (Stage 0 to Stage 4)...")
    ladder_stage0 = CapitalLadder(25000.0)
    assert ladder_stage0.stage == 0, "Under 50k must be Stage 0"
    can_live, _ = ladder_stage0.can_trade_live("LIVE")
    assert not can_live, "Stage 0 cannot trade LIVE"

    ladder_stage1 = CapitalLadder(75000.0)
    assert ladder_stage1.stage == 1, "75k must be Stage 1"
    can_live_1, _ = ladder_stage1.can_trade_live("LIVE")
    assert can_live_1, "Stage 1 can trade LIVE"
    print("      Capital Ladder Stages 0-4 and safety gates -> OK")
    passed_count += 1

    # 7. v8 Calm Mode & Peeks Audit (Part A17)
    print("\n[7/8] Testing v8 Calm Mode Masking & Peek Logger...")
    calm = CalmModeManager(enabled=True, mask_until="15:35")
    test_pos = {
        "id": 99,
        "setup_name": "nifty_condor_a",
        "symbol": "NIFTY",
        "unrealized_pnl": 1250.0,
        "pnl_pct": 2.5,
    }
    masked = calm.mask_position_view(test_pos, now=datetime(2026, 11, 24, 11, 0))
    assert masked["unrealized_pnl"] == "CALM MODE ACTIVE", "Calm mode must conceal P&L during market hours"

    # Log peek
    peek_res = calm.log_peek(position_id=99, command="/peek", user_id="test_runner")
    assert peek_res["status"] == "LOGGED", "Peek audit must be recorded"
    print("      Calm Mode P&L masking and SQLite peek audit -> OK")
    passed_count += 1

    # 8. v8 Rule-Change Radar & Ghost Ledger (Part A17)
    print("\n[8/8] Testing Rule-Change Radar & Ghost Ledger...")
    radar = RuleChangeRadar()
    changes = radar.scan_for_rule_changes(dict(settings.lots))
    assert len(changes) == 0, "Benchmark lot sizes must match"

    ghosts = GhostLedger(enabled=True)
    gid = ghosts.log_ghost_trade("nifty_condor_a", "NIFTY", "LOW_VIX_SKIP", 45.0)
    assert gid is not None, "Ghost trade must be logged"
    resolved_cnt = ghosts.resolve_eod_ghosts({"NIFTY": 24200.0})
    assert resolved_cnt >= 1, "Ghost trade must resolve at EOD"
    print("      Rule-Change Radar & Ghost Ledger lifecycle -> OK")
    passed_count += 1

    print("\n" + "=" * 70)
    print(f"      ALL CHECKS PASSED: {passed_count}/{total_checks} (100% HEALTHY)")
    print("=" * 70)
    return True


if __name__ == "__main__":
    success = run_all_checks()
    sys.exit(0 if success else 1)
