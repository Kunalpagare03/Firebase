"""After-market stock scanner for the configured Chartink screeners."""

import json
import os
import re
import webbrowser
from datetime import datetime
from io import StringIO
from pathlib import Path
from html import escape

import pandas as pd
import requests
import yfinance as yf

from scanners.chartlink_scanner import fetch_chartink_symbols
from utils.firebase_sync import sync_to_firestore


ROOT = Path(__file__).resolve().parent
REPORT_DIR = ROOT / "reports"
HTML_REPORT_DIR = REPORT_DIR / "html"
EXCEL_REPORT_DIR = REPORT_DIR / "excel"
SCREENER_URLS = {
    "vcp": "https://chartink.com/screener/chartians-vcp-scanner?utm_source=copilot.com",
    "bullish_5d": "https://chartink.com/screener/bullish-from-last-5-day",
}
FALLBACK_SYMBOLS = [
    "RELIANCE.NS", "HDFCBANK.NS", "ICICIBANK.NS", "INFY.NS", "TCS.NS",
    "SBIN.NS", "AXISBANK.NS", "KOTAKBANK.NS", "INDUSINDBK.NS", "BANKBARODA.NS",
    "ITC.NS", "HINDUNILVR.NS", "NESTLEIND.NS", "ASIANPAINT.NS", "TITAN.NS",
    "MARUTI.NS", "M&M.NS", "BAJAJ-AUTO.NS", "EICHERMOT.NS", "TATAMOTORS.NS",
    "BHARTIARTL.NS", "SUNPHARMA.NS", "DRREDDY.NS", "CIPLA.NS", "APOLLOHOSP.NS",
    "LT.NS", "ULTRACEMCO.NS", "POWERGRID.NS", "NTPC.NS", "ONGC.NS",
    "TATASTEEL.NS", "JSWSTEEL.NS", "HINDALCO.NS", "COALINDIA.NS", "ADANIENT.NS",
    "WIPRO.NS", "HCLTECH.NS", "TECHM.NS", "LTIM.NS", "PERSISTENT.NS",
    "TRENT.NS", "BEL.NS", "HAL.NS", "DIXON.NS", "POLYCAB.NS",
    "SIEMENS.NS", "ABB.NS", "CANBK.NS", "AMBUJACEM.NS", "VEDL.NS",
    "BANDHANBNK.NS", "IDFCFIRSTB.NS", "FEDERALBNK.NS", "AUROPHARMA.NS", "TORNTPHARM.NS",
    "BIOCON.NS", "ZYDUSLIFE.NS", "GODREJCP.NS", "DABUR.NS", "BRITANNIA.NS",
    "HEROMOTOCO.NS", "TVSMOTOR.NS", "ASHOKLEY.NS", "CUMMINSIND.NS", "INDIGO.NS",
]
MIN_CANDIDATES = 40
MAX_CANDIDATES = 60


def _normalise_symbol(value):
    value = str(value).strip().upper().replace("NSE:", "").replace("BSE:", "")
    if not value or value in {"SYMBOL", "STOCK", "NAME", "NIFTY", "BANKNIFTY"}:
        return None
    if value.endswith(".NS") or value.endswith(".BO"):
        return value
    return f"{value}.NS"


def fetch_screener_symbols(url):
    response = requests.get(url, headers={"User-Agent": "Mozilla/5.0"}, timeout=20)
    response.raise_for_status()
    html = response.text
    symbols = set()
    for pattern in (r"/stocks/([A-Za-z0-9._-]+)", r"data-symbol=[\"']([^\"']+)"):
        for match in re.findall(pattern, html, flags=re.IGNORECASE):
            symbol = _normalise_symbol(match)
            if symbol:
                symbols.add(symbol)
    try:
        for table in pd.read_html(StringIO(html)):
            for column in table.columns:
                if "symbol" in str(column).lower() or "name" in str(column).lower():
                    for value in table[column].dropna().tolist():
                        symbol = _normalise_symbol(value)
                        if symbol:
                            symbols.add(symbol)
    except ValueError:
        pass
    return sorted(symbols)


def _rsi(close, period=14):
    delta = close.diff()
    gain = delta.clip(lower=0).rolling(period).mean()
    loss = -delta.clip(upper=0).rolling(period).mean()
    rs = gain / loss.replace(0, pd.NA)
    return float((100 - (100 / (1 + rs.iloc[-1]))) if pd.notna(rs.iloc[-1]) else 50)


def _normalise_history(history):
    if isinstance(history.columns, pd.MultiIndex):
        price_level = next(
            (level for level in range(history.columns.nlevels)
             if {"Open", "High", "Low", "Close", "Volume"}.intersection(
                 set(history.columns.get_level_values(level)))),
            None,
        )
        if price_level is not None:
            history = history.copy()
            history.columns = history.columns.get_level_values(price_level)
    return history.loc[:, ~history.columns.duplicated()]


def analyse_symbol(symbol):
    history = yf.Ticker(symbol).history(period="6mo", interval="1d", auto_adjust=False)
    history = _normalise_history(history)
    required_columns = {"Open", "High", "Low", "Close", "Volume"}
    if len(history) < 60 or not required_columns.issubset(history.columns):
        return None
    history = history.dropna(subset=list(required_columns))
    if len(history) < 60:
        return None
    close = pd.to_numeric(history["Close"], errors="coerce")
    volume = pd.to_numeric(history["Volume"], errors="coerce").fillna(0)
    history = history.loc[close.notna()]
    close = close.loc[history.index]
    volume = volume.loc[history.index]
    latest = float(close.iloc[-1])
    previous = float(close.iloc[-2])
    sma20 = float(close.rolling(20).mean().iloc[-1])
    sma50 = float(close.rolling(50).mean().iloc[-1])
    avg_volume = float(volume.rolling(20).mean().iloc[-1])
    volume_ratio = float(volume.iloc[-1] / avg_volume) if avg_volume else 0
    true_range = pd.concat([
        history["High"] - history["Low"],
        (history["High"] - history["Close"].shift()).abs(),
        (history["Low"] - history["Close"].shift()).abs(),
    ], axis=1).max(axis=1)
    atr = float(true_range.rolling(14).mean().iloc[-1])
    five_day_high = float(close.tail(5).max())
    twenty_day_high = float(close.tail(20).max())
    body = abs(float(history["Close"].iloc[-1] - history["Open"].iloc[-1]))
    candle_range = max(float(history["High"].iloc[-1] - history["Low"].iloc[-1]), 0.01)
    change_pct = (latest / previous - 1) * 100 if previous else 0
    score = 0
    signals = []
    if latest > sma20 > sma50:
        score += 2; signals.append("above rising SMA20/SMA50")
    if latest > five_day_high * 0.995:
        score += 1; signals.append("near 5-day high")
    if latest >= twenty_day_high * 0.995:
        score += 1; signals.append("20-day breakout pressure")
    if volume_ratio >= 1.5:
        score += 2; signals.append(f"volume {volume_ratio:.1f}x average")
    if 45 <= _rsi(close) <= 72:
        score += 1; signals.append(f"RSI {_rsi(close):.0f} healthy")
    if latest < sma20:
        score -= 2; signals.append("below SMA20")
    if volume_ratio < 0.7:
        score -= 1; signals.append("weak volume")
    pattern = "Bullish close" if float(history["Close"].iloc[-1]) > float(history["Open"].iloc[-1]) else "Bearish close"
    if body / candle_range < 0.2:
        pattern = "Indecision / doji"
    target = latest * 1.02
    stop = max(latest - 1.5 * atr, latest * 0.97)
    return {
        "symbol": symbol,
        "entry": round(latest, 2),
        "close": round(latest, 2),
        "change_pct": round(change_pct, 2),
        "volume_ratio": round(volume_ratio, 2),
        "rsi14": round(_rsi(close), 1),
        "sma20": round(sma20, 2),
        "sma50": round(sma50, 2),
        "atr14": round(atr, 2),
        "pattern": pattern,
        "score": score,
        "signals": signals,
        "target_2pct": round(target, 2),
        "stop_loss": round(stop, 2),
        "risk_reward": round((target - latest) / max(latest - stop, 0.01), 2),
        "recommendation": "WATCH / BUY ON CONFIRMATION" if score >= 4 else "WAIT",
    }


def _write_html_report(report):
    def cell(value):
        return escape(str(value))

    def rows(items):
        if not items:
            return "<tr><td colspan='9'>No qualified setups found.</td></tr>"
        return "".join(
            "<tr>"
            + "".join(cell(item.get(key, "")) for key in (
                "symbol", "entry", "target_2pct", "stop_loss", "score",
                "risk_reward", "volume_ratio", "rsi14", "recommendation",
            ))
            + "</tr>"
            for item in items
        )

    headers = "".join(
        f"<th>{escape(label)}</th>"
        for label in (
            "Symbol", "Entry", "Target +2%", "Stop", "Score", "R:R",
            "Volume Ratio", "RSI", "Recommendation",
        )
    )
    status = "<br>".join(cell(error) for error in report.get("errors", [])) or "Chartink status: OK"
    html = f"""<!doctype html>
<html><head><meta charset="utf-8"><title>After-hours trade scan</title>
<style>
body {{ font-family: Segoe UI, sans-serif; margin: 32px; color: #17202a; background: #f4f7f9; }}
main {{ max-width: 1200px; margin: auto; }}
.meta, .notice {{ background: white; padding: 16px; border-radius: 8px; margin: 14px 0; }}
.notice {{ border-left: 4px solid #d9822b; }}
table {{ width: 100%; border-collapse: collapse; background: white; margin: 12px 0 28px; }}
th, td {{ padding: 10px; border-bottom: 1px solid #e5e7eb; text-align: right; }}
th:first-child, td:first-child {{ text-align: left; }}
th {{ background: #243b53; color: white; }}
</style></head><body><main>
<h1>After-hours next-day trade scan</h1>
<div class="meta">Generated: {cell(report.get("generated_at"))}<br>Evaluated: {cell(report.get("evaluated_count", 0))} stocks<br>Objective: {cell(report.get("target"))}</div>
<div class="notice">{status}</div>
<h2>Qualified setups</h2><table><thead><tr>{headers}</tr></thead><tbody>{rows(report.get("top_setups", []))}</tbody></table>
<h2>Top monitored candidates</h2><table><thead><tr>{headers}</tr></thead><tbody>{rows(report.get("top_candidates", []))}</tbody></table>
</main></body></html>"""
    HTML_REPORT_DIR.mkdir(parents=True, exist_ok=True)
    path = HTML_REPORT_DIR / "latest.html"
    path.write_text(html, encoding="utf-8")
    webbrowser.open(path.resolve().as_uri())
    return path


def run_scan():
    symbols = set()
    sources = {}
    for name, url in SCREENER_URLS.items():
        try:
            clause = os.environ.get(f"CHARTINK_{name.upper()}_CLAUSE")
            if name == "vcp":
                found, status = fetch_chartink_symbols(
                    url,
                    scan_clause=clause,
                    cookie_header=os.environ.get("CHARTINK_COOKIE"),
                )
                if status:
                    sources.setdefault("_errors", []).append(f"{name}: {status}")
            else:
                found = fetch_screener_symbols(url)
            symbols.update(found)
            for symbol in found:
                sources.setdefault(symbol, []).append(name)
        except Exception as error:
            sources.setdefault("_errors", []).append(f"{name}: {error}")
    configured = os.environ.get("STOCK_SYMBOLS", "")
    symbols.update(_normalise_symbol(item) for item in configured.split(",") if item.strip())
    fallback_index = 0
    while len(symbols) < MIN_CANDIDATES and fallback_index < len(FALLBACK_SYMBOLS):
        symbols.add(FALLBACK_SYMBOLS[fallback_index])
        fallback_index += 1
    if len(symbols) > MAX_CANDIDATES:
        symbols = set(sorted(symbols)[:MAX_CANDIDATES])
    if not symbols:
        symbols.update(FALLBACK_SYMBOLS)
    results = []
    for symbol in sorted(symbol for symbol in symbols if symbol):
        try:
            result = analyse_symbol(symbol)
            if result:
                result["screeners"] = sources.get(symbol, ["fallback"])
                results.append(result)
        except Exception as error:
            sources.setdefault("_errors", []).append(f"{symbol}: {error}")
    results.sort(
        key=lambda item: (item["score"], item["risk_reward"], item["volume_ratio"]),
        reverse=True,
    )
    top_candidates = results[:3]
    top_setups = [item for item in results if item["score"] >= 4][:3]
    report_results = results[:50]
    report = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "target": "2% objective, not guaranteed",
        "screeners": SCREENER_URLS,
        "errors": sources.get("_errors", []),
        "evaluated_count": len(results),
        "candidate_count": len(report_results),
        "top_candidates": top_candidates,
        "top_setups": top_setups,
        "results": report_results,
    }
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    (REPORT_DIR / "latest.json").write_text(json.dumps(report, indent=2))
    sync_to_firestore("stock_scans", "latest", report)
    pd.DataFrame(results).to_csv(REPORT_DIR / "latest.csv", index=False)
    EXCEL_REPORT_DIR.mkdir(parents=True, exist_ok=True)
    pd.DataFrame(results).to_excel(EXCEL_REPORT_DIR / "latest.xlsx", index=False)
    _write_html_report(report)
    return report


if __name__ == "__main__":
    report = run_scan()
    print(json.dumps({"generated_at": report["generated_at"], "candidates": len(report["results"]), "top": report["results"][:5], "errors": report["errors"]}, indent=2))