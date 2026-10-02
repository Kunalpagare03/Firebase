import csv
import json
from datetime import datetime
from pathlib import Path

import pandas as pd


ROOT = Path(__file__).resolve().parent.parent
HISTORY_DIR = ROOT / "data" / "history"
LIVE_DIR = ROOT / "data" / "live"
ANALYSIS_DIR = ROOT / "data" / "analysis"


def _safe_name(value):
    return str(value).replace("^", "").replace("/", "_").replace(" ", "_")


def save_option_chain_snapshot(raw, symbol, source="unknown", captured_at=None):
    """Save raw and flattened option-chain data for later research."""
    captured_at = captured_at or datetime.now()
    day_dir = HISTORY_DIR / captured_at.strftime("%Y-%m-%d")
    day_dir.mkdir(parents=True, exist_ok=True)
    stem = f"{_safe_name(symbol)}_{captured_at.strftime('%H%M%S')}"
    raw_path = day_dir / f"{stem}.json"
    raw_path.write_text(json.dumps({"captured_at": captured_at.isoformat(), "source": source, "data": raw}, indent=2))

    rows = raw.get("data", []) if isinstance(raw, dict) else []
    if "records" in raw:
        rows = raw["records"].get("data", [])
    csv_path = day_dir / f"{_safe_name(symbol)}_option_chain.csv"
    fields = ["captured_at", "source", "expiry", "spot_price", "strike", "call_oi", "put_oi", "call_oi_change", "put_oi_change", "call_volume", "put_volume", "call_ltp", "put_ltp", "call_iv", "put_iv"]
    spot_price = raw.get("spot_price", raw.get("records", {}).get("underlyingValue"))
    write_header = not csv_path.exists()
    with csv_path.open("a", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        if write_header:
            writer.writeheader()
        for entry in rows:
            call = entry.get("CE", entry.get("CALL", {}))
            put = entry.get("PE", entry.get("PUT", {}))
            expiry = entry.get("expiryDate", entry.get("expiry", raw.get("expiry")))
            expiry = expiry or call.get("expiryDate") or put.get("expiryDate")
            writer.writerow({
                "captured_at": captured_at.isoformat(), "source": source, "expiry": expiry, "spot_price": spot_price,
                "strike": entry.get("strikePrice", entry.get("strike_price")),
                "call_oi": call.get("openInterest", call.get("oi", 0)), "put_oi": put.get("openInterest", put.get("oi", 0)),
                "call_oi_change": call.get("changeinOpenInterest", call.get("oi_change", 0)), "put_oi_change": put.get("changeinOpenInterest", put.get("oi_change", 0)),
                "call_volume": call.get("totalTradedVolume", call.get("volume", 0)), "put_volume": put.get("totalTradedVolume", put.get("volume", 0)),
                "call_ltp": call.get("lastPrice", call.get("ltp", 0)), "put_ltp": put.get("lastPrice", put.get("ltp", 0)),
                "call_iv": call.get("impliedVolatility", call.get("iv", 0)), "put_iv": put.get("impliedVolatility", put.get("iv", 0)),
            })
    return raw_path, csv_path


def save_live_snapshot(raw, symbol, source="unknown", captured_at=None):
    """Keep only the newest live response separate from historical research data."""
    captured_at = captured_at or datetime.now()
    LIVE_DIR.mkdir(parents=True, exist_ok=True)
    path = LIVE_DIR / f"{_safe_name(symbol)}_latest.json"
    path.write_text(json.dumps({"captured_at": captured_at.isoformat(), "source": source, "data": raw}, indent=2))
    return path


def save_daily_trend_change(symbol, trend, source="unknown", captured_at=None, reason=None, spot_price=None, pcr_oi=None, pcr_volume=None, max_pain=None, call_oi_change=None, put_oi_change=None, call_volume_change=None, put_volume_change=None, alert_action=None, confirmation_score=None):
    """Append a daily trend-change event to an Excel log for easy review."""
    captured_at = captured_at or datetime.now()
    day_dir = ANALYSIS_DIR / captured_at.strftime("%Y-%m-%d")
    day_dir.mkdir(parents=True, exist_ok=True)
    path = day_dir / f"{_safe_name(symbol)}_trend_log.xlsx"

    fields = [
        "timestamp",
        "symbol",
        "source",
        "trend",
        "reason",
        "spot_price",
        "pcr_oi",
        "pcr_volume",
        "max_pain",
        "call_oi_change",
        "put_oi_change",
        "call_volume_change",
        "put_volume_change",
        "alert_action",
        "confirmation_score",
    ]
    row = {
        "timestamp": captured_at.strftime("%Y-%m-%d %H:%M:%S"),
        "symbol": symbol,
        "source": source,
        "trend": trend,
        "reason": reason or "Trend changed",
        "spot_price": spot_price,
        "pcr_oi": pcr_oi,
        "pcr_volume": pcr_volume,
        "max_pain": max_pain,
        "call_oi_change": call_oi_change,
        "put_oi_change": put_oi_change,
        "call_volume_change": call_volume_change,
        "put_volume_change": put_volume_change,
        "alert_action": alert_action,
        "confirmation_score": confirmation_score,
    }

    if path.exists():
        try:
            df = pd.read_excel(path)
        except Exception:
            df = pd.DataFrame(columns=fields)
    else:
        df = pd.DataFrame(columns=fields)

    if df.empty:
        df = pd.DataFrame([row], columns=fields)
    else:
        df = pd.concat([df, pd.DataFrame([row], columns=fields)], ignore_index=True)
    df.to_excel(path, index=False)
    return path