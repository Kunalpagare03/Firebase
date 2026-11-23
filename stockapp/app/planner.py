import math
from typing import List, Dict, Any, Tuple
import pandas as pd
from stockapp.app.settings import settings
from stockapp.app.data_nse import get_upcoming_results_symbols
from stockapp.app.log import logger

def plan_nightly_trades(
    swing_df: pd.DataFrame,
    positional_df: pd.DataFrame,
    traffic_light: Dict[str, Any],
    open_trades: List[Dict[str, Any]]
) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
    """
    Allocates trade slots according to v4 specification:
    - Max open trades: 10 (up to 4 swing, up to 6 positional)
    - Max per sector: 2
    - Max new per night: 3
    - Results veto within 2 days
    - Positional slots filled first by highest Technical Score
    - Swing slots filled next (Tech Score >= 55)
    - Fixed 1% capital risk with 8% stop loss
    """
    total_capital = settings.capital
    risk_per_trade = settings.risk_per_trade * total_capital # e.g. Rs 10,000
    stop_pct = settings.initial_stop_pct # 8%
    size_mult = traffic_light.get("effective_size_multiplier", 1.0)
    adjusted_risk = risk_per_trade * size_mult

    # Check existing open counts
    existing_swing_count = sum(1 for t in open_trades if t.get("horizon") == "SWING" and t.get("status") == "OPEN")
    existing_pos_count = sum(1 for t in open_trades if t.get("horizon") == "POSITIONAL" and t.get("status") == "OPEN")
    total_open_count = existing_swing_count + existing_pos_count

    # Check sector exposure
    sector_counts: Dict[str, int] = {}
    for t in open_trades:
        if t.get("status") == "OPEN":
            sec = t.get("sector", "Diversified")
            sector_counts[sec] = sector_counts.get(sec, 0) + 1

    avail_pos_slots = max(0, settings.limits.max_positional_trades - existing_pos_count)
    avail_swing_slots = max(0, settings.limits.max_swing_trades - existing_swing_count)
    avail_total_slots = max(0, settings.limits.max_open_trades - total_open_count)

    veto_symbols = get_upcoming_results_symbols(window_days=settings.scanner.results_veto_days)

    accepted_picks: List[Dict[str, Any]] = []
    rejected_picks: List[Dict[str, Any]] = []

    # If traffic light is RED (size 0), we reject all new real entries
    if traffic_light.get("state") == 5 and traffic_light.get("mode") == "STRICT":
        logger.info("Traffic light RED in STRICT mode: no new trades permitted (WAIT).")
        return [], []

    # Sort candidates by Technical Score descending
    pos_sorted = positional_df.sort_values(by="tech_score", ascending=False) if not positional_df.empty else pd.DataFrame()
    swing_sorted = swing_df.sort_values(by="tech_score", ascending=False) if not swing_df.empty else pd.DataFrame()

    picked_symbols = set(t["symbol"] for t in open_trades if t.get("status") == "OPEN")
    new_count = 0

    # 1. Allocate Positional Slots First
    if not pos_sorted.empty and avail_pos_slots > 0 and avail_total_slots > 0 and new_count < settings.limits.max_new_per_night:
        for _, row in pos_sorted.iterrows():
            if new_count >= settings.limits.max_new_per_night or avail_pos_slots <= 0 or avail_total_slots <= 0:
                break

            sym = row["symbol"]
            sec = row.get("sector", "Diversified")

            # Check duplication
            if sym in picked_symbols:
                continue

            # Check Results Veto
            if sym in veto_symbols:
                rejected_picks.append({
                    "symbol": sym, "horizon": "POSITIONAL", "reason": "Results within 2 days veto", "tech_score": row["tech_score"]
                })
                continue

            # Check Sector Cap (max 2 per sector)
            if sector_counts.get(sec, 0) >= settings.limits.max_per_sector:
                rejected_picks.append({
                    "symbol": sym, "horizon": "POSITIONAL", "reason": f"Sector limit ({sec} >= 2)", "tech_score": row["tech_score"]
                })
                continue

            # Sizing calculation: Risk Rs / (0.08 * Entry)
            entry_price = float(row["close"])
            stop_price = round(entry_price * (1.0 - stop_pct), 2)
            per_share_risk = entry_price * stop_pct
            qty = math.floor(adjusted_risk / per_share_risk) if per_share_risk > 0 else 0

            if qty <= 0:
                continue

            allocated_capital = round(qty * entry_price, 2)
            actual_risk = round(qty * per_share_risk, 2)

            pick = {
                "symbol": sym,
                "horizon": "POSITIONAL",
                "setup_code": row["setup_code"],
                "setup_name": row["setup_name"],
                "sector": sec,
                "tech_score": float(row["tech_score"]),
                "planned_entry": entry_price,
                "stop_price": stop_price,
                "target_price": round(entry_price * 1.15, 2), # 15% target
                "quantity": qty,
                "allocated_capital": allocated_capital,
                "risk_rs": actual_risk,
                "status": "APPROVED",
                "rejection_reason": None
            }
            accepted_picks.append(pick)
            picked_symbols.add(sym)
            sector_counts[sec] = sector_counts.get(sec, 0) + 1
            avail_pos_slots -= 1
            avail_total_slots -= 1
            new_count += 1

    # 2. Allocate Swing Slots Next (Technical Score >= 55)
    if not swing_sorted.empty and avail_swing_slots > 0 and avail_total_slots > 0 and new_count < settings.limits.max_new_per_night:
        for _, row in swing_sorted.iterrows():
            if new_count >= settings.limits.max_new_per_night or avail_swing_slots <= 0 or avail_total_slots <= 0:
                break

            sym = row["symbol"]
            sec = row.get("sector", "Diversified")

            if sym in picked_symbols:
                continue

            if float(row["tech_score"]) < settings.scanner.min_swing_tech_score:
                rejected_picks.append({
                    "symbol": sym, "horizon": "SWING", "reason": f"Tech score {row['tech_score']} < 55", "tech_score": row["tech_score"]
                })
                continue

            if sym in veto_symbols:
                rejected_picks.append({
                    "symbol": sym, "horizon": "SWING", "reason": "Results within 2 days veto", "tech_score": row["tech_score"]
                })
                continue

            if sector_counts.get(sec, 0) >= settings.limits.max_per_sector:
                rejected_picks.append({
                    "symbol": sym, "horizon": "SWING", "reason": f"Sector limit ({sec} >= 2)", "tech_score": row["tech_score"]
                })
                continue

            entry_price = float(row["close"])
            stop_price = round(entry_price * (1.0 - stop_pct), 2)
            per_share_risk = entry_price * stop_pct
            qty = math.floor(adjusted_risk / per_share_risk) if per_share_risk > 0 else 0

            if qty <= 0:
                continue

            allocated_capital = round(qty * entry_price, 2)
            actual_risk = round(qty * per_share_risk, 2)

            pick = {
                "symbol": sym,
                "horizon": "SWING",
                "setup_code": row["setup_code"],
                "setup_name": row.get("swing_setup_label", row["setup_name"]),
                "sector": sec,
                "tech_score": float(row["tech_score"]),
                "planned_entry": entry_price,
                "stop_price": stop_price,
                "target_price": round(entry_price * 1.08, 2), # 8% swing target
                "quantity": qty,
                "allocated_capital": allocated_capital,
                "risk_rs": actual_risk,
                "status": "APPROVED",
                "rejection_reason": None
            }
            accepted_picks.append(pick)
            picked_symbols.add(sym)
            sector_counts[sec] = sector_counts.get(sec, 0) + 1
            avail_swing_slots -= 1
            avail_total_slots -= 1
            new_count += 1

    return accepted_picks, rejected_picks
