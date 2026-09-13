import re
from io import StringIO
from urllib.parse import unquote

import pandas as pd
import requests


LIVE_FALLBACK_SYMBOLS = [
    "RELIANCE.NS", "TCS.NS", "HDFCBANK.NS", "INFY.NS", "ICICIBANK.NS",
    "SBIN.NS", "ITC.NS", "LTIM.NS", "AXISBANK.NS", "KOTAKBANK.NS",
    "SUNPHARMA.NS", "TITAN.NS", "BHARTIARTL.NS", "MARUTI.NS", "WIPRO.NS",
    "POWERGRID.NS", "NTPC.NS", "HINDUNILVR.NS", "ULTRACEMCO.NS", "ASIANPAINT.NS",
]


def _normalise_symbol(value):
    value = str(value).strip().upper().replace("NSE:", "").replace("BSE:", "")
    if not value or value in {"SYMBOL", "STOCK", "NAME", "NIFTY", "BANKNIFTY"}:
        return None
    if value.endswith(".NS") or value.endswith(".BO"):
        return value
    return f"{value}.NS"


def _extract_symbols_from_html(html):
    symbols = set()
    patterns = (r"/stocks/([A-Za-z0-9._-]+)", r"data-symbol=[\"']([^\"']+)")
    for pattern in patterns:
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


def _cookie_header_to_dict(cookie_header):
    if not cookie_header:
        return {}
    cookies = {}
    for item in cookie_header.split(";"):
        if "=" in item:
            name, value = item.strip().split("=", 1)
            cookies[name] = unquote(value)
    return cookies


def fetch_chartink_symbols(scanner_url, scan_clause=None, cookie_header=None):
    """Fetch symbols using a real Chartink session when a clause is available."""
    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36"
        ),
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    }
    session = requests.Session()
    session.headers.update(headers)
    session.cookies.update(_cookie_header_to_dict(cookie_header))
    page = session.get(scanner_url, timeout=20)
    page.raise_for_status()
    if not scan_clause:
        return [], "Chartink page reached, but no scan_clause was provided"
    if scan_clause.strip().lower() == "chartians-vcp-scanner":
        return [], "Chartink requires the decoded scan_clause, not the screener name"

    token_match = re.search(
        r'<meta[^>]+name=["\']csrf-token["\'][^>]+content=["\']([^"\']+)',
        page.text,
        flags=re.IGNORECASE,
    )
    request_headers = {
        "X-Requested-With": "XMLHttpRequest",
        "X-CSRF-TOKEN": token_match.group(1) if token_match else "",
        "Referer": scanner_url,
        "Accept": "application/json, text/javascript, */*; q=0.01",
    }
    response = session.post(
        "https://chartink.com/screener/process",
        headers=request_headers,
        data={"scan_clause": scan_clause},
        timeout=20,
    )
    if response.status_code != 200:
        return [], f"Chartink process rejected the session (HTTP {response.status_code})"
    try:
        data = response.json()
    except ValueError:
        return [], "Chartink process returned a non-JSON response"
    rows = data.get("data", []) if isinstance(data, dict) else []
    symbols = sorted({
        symbol
        for row in rows
        for symbol in [_normalise_symbol(row.get("nsecode"))]
        if symbol
    })
    return symbols, None


def run_chartlink_scanner(scanner_url: str, scan_clause: str):
    """
    Fetch shortlisted symbols from Chartink screener using JSON API and fall back to HTML parsing.
    """
    try:
        api_url = "https://chartink.com/screener/process"

        headers = {
            "User-Agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/115.0 Safari/537.36"
            ),
            "X-Requested-With": "XMLHttpRequest",
            "Referer": scanner_url,
        }

        cookies = {}
        payload = {"scan_clause": scan_clause}

        response = requests.post(api_url, headers=headers, data=payload, cookies=cookies, timeout=15)
        response.raise_for_status()
        data = response.json()
        if isinstance(data, dict):
            symbols = [row["nsecode"] for row in data.get("data", []) if "nsecode" in row]
            if symbols:
                return symbols
    except Exception as e:
        print(f"Chartink JSON fetch failed for {scanner_url}: {e}")

    try:
        response = requests.get(scanner_url, headers={"User-Agent": "Mozilla/5.0"}, timeout=20)
        response.raise_for_status()
        symbols = _extract_symbols_from_html(response.text)
        if symbols:
            return symbols
    except Exception as e:
        print(f"Error fetching scanner {scanner_url}: {e}")

    return LIVE_FALLBACK_SYMBOLS
