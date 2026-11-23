"""Option Chain Analytics: Max Pain, PCR, OI Walls, IV Rank & Skew (Part C3)."""

from typing import Any, Dict, List, Optional, Tuple
import numpy as np


def compute_max_pain(chain_rows: List[Dict[str, Any]]) -> float:
    """
    Calculate Max Pain strike:
    For every strike K, calculate total cash payout if market settles at K:
    Payout(K) = sum_strikes [ max(0, K - strike) * Call_OI + max(0, strike - K) * Put_OI ]
    Strike with minimum payout is Max Pain.
    """
    if not chain_rows:
        return 0.0

    strikes = [float(row["strike_price"]) for row in chain_rows]
    call_ois = [float(row.get("CALL", {}).get("oi", 0) or 0) for row in chain_rows]
    put_ois = [float(row.get("PUT", {}).get("oi", 0) or 0) for row in chain_rows]

    min_payout = float("inf")
    best_strike = strikes[0]

    for test_k in strikes:
        payout = 0.0
        for k, c_oi, p_oi in zip(strikes, call_ois, put_ois):
            # If market settles at test_k:
            # Calls below test_k are in the money: (test_k - k) * c_oi
            if test_k > k:
                payout += (test_k - k) * c_oi
            # Puts above test_k are in the money: (k - test_k) * p_oi
            elif test_k < k:
                payout += (k - test_k) * p_oi

        if payout < min_payout:
            min_payout = payout
            best_strike = test_k

    return best_strike


def compute_pcr(chain_rows: List[Dict[str, Any]]) -> Dict[str, float]:
    """Calculate Put-Call Ratio for Open Interest and Traded Volume."""
    total_put_oi = sum(float(row.get("PUT", {}).get("oi", 0) or 0) for row in chain_rows)
    total_call_oi = sum(float(row.get("CALL", {}).get("oi", 0) or 0) for row in chain_rows)

    total_put_vol = sum(float(row.get("PUT", {}).get("volume", 0) or 0) for row in chain_rows)
    total_call_vol = sum(float(row.get("CALL", {}).get("volume", 0) or 0) for row in chain_rows)

    pcr_oi = total_put_oi / max(1.0, total_call_oi)
    pcr_vol = total_put_vol / max(1.0, total_call_vol)

    return {
        "pcr_oi": round(pcr_oi, 3),
        "pcr_volume": round(pcr_vol, 3),
        "total_put_oi": total_put_oi,
        "total_call_oi": total_call_oi,
        "sentiment": "BULLISH" if pcr_oi > 1.2 else ("BEARISH" if pcr_oi < 0.8 else "NEUTRAL"),
    }


def compute_oi_walls(chain_rows: List[Dict[str, Any]]) -> Dict[str, float]:
    """Find key Call OI Wall (Resistance) and Put OI Wall (Support)."""
    if not chain_rows:
        return {"call_wall": 0.0, "put_wall": 0.0}

    max_call_oi = -1.0
    call_wall = 0.0

    max_put_oi = -1.0
    put_wall = 0.0

    for row in chain_rows:
        k = float(row["strike_price"])
        c_oi = float(row.get("CALL", {}).get("oi", 0) or 0)
        p_oi = float(row.get("PUT", {}).get("oi", 0) or 0)

        if c_oi > max_call_oi:
            max_call_oi = c_oi
            call_wall = k

        if p_oi > max_put_oi:
            max_put_oi = p_oi
            put_wall = k

    return {
        "call_wall": call_wall,
        "call_wall_oi": max_call_oi,
        "put_wall": put_wall,
        "put_wall_oi": max_put_oi,
    }


def compute_iv_rank(current_iv: float, iv_history_252d: List[float]) -> float:
    """
    Calculate 252-day IV Rank:
    IVR = (Current_IV - Min_IV) / (Max_IV - Min_IV) * 100
    """
    if not iv_history_252d:
        return 50.0

    min_iv = min(iv_history_252d)
    max_iv = max(iv_history_252d)

    if max_iv - min_iv <= 1e-4:
        return 50.0

    ivr = ((current_iv - min_iv) / (max_iv - min_iv)) * 100.0
    return max(0.0, min(100.0, ivr))
