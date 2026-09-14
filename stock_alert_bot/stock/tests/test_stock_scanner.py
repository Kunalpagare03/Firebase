from io import StringIO

import pandas as pd

from scanners.chartlink_scanner import run_chartlink_scanner
from analysis.price_action import detect_trend
from stock_scanner import fetch_screener_symbols
from live_stock_scanner import build_sectioned_report


HTML = """
<html><body>
<table>
  <tr><th>Symbol</th></tr>
  <tr><td>RELIANCE</td></tr>
  <tr><td>INFY</td></tr>
</table>
<div data-symbol="TCS"></div>
</body></html>
"""


def test_fetch_screener_symbols_parses_html(monkeypatch):
    class FakeResponse:
        text = HTML

        def raise_for_status(self):
            pass

    monkeypatch.setattr("requests.get", lambda *args, **kwargs: FakeResponse())

    symbols = fetch_screener_symbols("https://example.com")

    assert "RELIANCE.NS" in symbols
    assert "INFY.NS" in symbols
    assert "TCS.NS" in symbols


def test_run_chartlink_scanner_falls_back_to_html(monkeypatch):
    class FakePostResponse:
        def raise_for_status(self):
            raise RuntimeError("blocked")

    class FakeGetResponse:
        text = HTML

        def raise_for_status(self):
            pass

    monkeypatch.setattr("requests.post", lambda *args, **kwargs: FakePostResponse())
    monkeypatch.setattr("requests.get", lambda *args, **kwargs: FakeGetResponse())

    symbols = run_chartlink_scanner("https://chartink.com/screener/test", "fake clause")

    assert "RELIANCE.NS" in symbols
    assert "TCS.NS" in symbols


def test_detect_trend_uses_latest_ema_values():
    df = pd.DataFrame({
        "Close": [100, 101, 102, 103, 104, 105],
        "EMA20": [90, 92, 95, 98, 101, 104],
        "EMA50": [80, 82, 85, 88, 90, 92],
    })

    assert detect_trend(df) == "Uptrend"


def test_build_sectioned_report_groups_by_section_and_sector():
    report = build_sectioned_report({
        "intraday": [
            {"symbol": "TCS.NS", "sector": "IT", "recommendation": "TRADE TODAY"},
            {"symbol": "INFY.NS", "sector": "IT", "recommendation": "TRADE TODAY"},
        ],
        "swing": [
            {"symbol": "TATAMOTORS.NS", "sector": "AUTO", "recommendation": "SWING ENTRY"},
        ],
        "positional": [
            {"symbol": "SUNPHARMA.NS", "sector": "PHARMA", "recommendation": "POS HOLD"},
        ],
        "market_news": [{"headline": "Test news", "impact": "Positive"}],
    })

    assert "intraday" not in report["sections"]
    assert report["sections"]["swing"][0]["symbol"] == "TATAMOTORS.NS"
    assert report["stocks_by_sector"]["AUTO"][0]["symbol"] == "TATAMOTORS.NS"
    assert report["market_news"][0]["headline"] == "Test news"
