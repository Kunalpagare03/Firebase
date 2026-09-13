import os
import sys

# Ensure project root on path so step modules import correctly
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

from step2_parse import load_mock_nse_response, parse_option_chain
from step3_pcr import calculate_pcr
from step6_buildup import analyze_buildup
from step7_dashboard import build_dashboard
from stock_alert.price_action import analyze_price_action
from stock_alert.greeks import analyze_chain_greeks, black_scholes_greeks
from stock_alert.storage import save_option_chain_snapshot, save_daily_trend_change
from stock_alert.strategy import backtest, build_signals
from scripts.web_dashboard import analyze_flow, _market_trend, _single_direction_summary
from scripts.generate_mobile_qr import dashboard_url
from stock_alert.market_hours import market_status
from src.trend_detector import analyze_price_structure


def main():
    raw = load_mock_nse_response()
    df, spot = parse_option_chain(raw)

    assert df.shape[0] == 5, "Expected 5 strikes in mock data"
    assert isinstance(spot, (int, float)), "Spot must be numeric"

    res = calculate_pcr(df)
    assert "pcr_oi" in res, "PCR result must include pcr_oi"
    print("All pipeline smoke tests passed")


def test_generated_chain_reaches_dashboard(tmp_path):
    from step1_fetch_dhan import generate_mock_option_chain

    raw = generate_mock_option_chain("^NSEI", out_path=str(tmp_path / "mock_chain.json"))
    df, spot = parse_option_chain(raw, expiry=raw["expiry"])
    dashboard = build_dashboard(df, df, spot)

    assert len(df) == 21
    assert dashboard["spot_price"] == spot


def test_parser_filters_expiry_and_buildup_handles_no_overlap():
    raw = {
        "records": {
            "underlyingValue": 100,
            "data": [
                {"expiryDate": "2026-09-01", "strikePrice": 100, "CE": {}, "PE": {}},
                {"expiryDate": "2026-09-08", "strikePrice": 100, "CE": {}, "PE": {}},
            ],
        }
    }
    df, _ = parse_option_chain(raw, expiry="2026-09-01")
    assert len(df) == 1
    assert df.iloc[0]["expiry"] == "2026-09-01"

    empty_result, summary = analyze_buildup(df, df.assign(strike=200))
    assert empty_result.empty
    assert summary["overall_read"] == "Mixed / Neutral"


def test_price_action_metrics(monkeypatch):
    import pandas as pd

    class FakeTicker:
        def history(self, **kwargs):
            return pd.DataFrame({"Close": [100, 102, 104, 103, 105]})

    monkeypatch.setattr("stock_alert.price_action.yf.Ticker", lambda symbol: FakeTicker())
    result = analyze_price_action("NIFTY", period="1mo")

    assert result["close"] == 105.0
    assert result["change_pct"] == round((105 / 103 - 1) * 100, 2)
    assert result["reading"] == "Bullish price action"


def test_finnifty_uses_finnifty_price_series(monkeypatch):
    import pandas as pd

    class FakeTicker:
        def history(self, **kwargs):
            return pd.DataFrame({"Close": [26000, 26100]})

    monkeypatch.setattr("stock_alert.price_action.yf.Ticker", lambda symbol: FakeTicker())
    result = analyze_price_action("FINNIFTY")

    assert result["yahoo_symbol"] == "^CNXFIN"
    assert result["close"] == 26100.0


def test_price_action_handles_one_available_close(monkeypatch):
    import pandas as pd

    class FakeTicker:
        def history(self, **kwargs):
            return pd.DataFrame({"Close": [28073.7]})

    monkeypatch.setattr("stock_alert.price_action.yf.Ticker", lambda symbol: FakeTicker())
    result = analyze_price_action("FINNIFTY")

    assert result["close"] == 28073.7
    assert result["previous_close"] is None
    assert result["reading"] == "Insufficient previous close"


def test_flow_analysis_detects_bullish_volume_and_oi():
    import pandas as pd

    previous = pd.DataFrame({"call_volume": [100], "put_volume": [100], "call_oi": [1000], "put_oi": [1000]})
    current = pd.DataFrame({"call_volume": [120], "put_volume": [180], "call_oi": [1010], "put_oi": [1060]})
    result = analyze_flow(previous, current)

    assert result["trend"] == "Bullish flow"
    assert result["call_volume_change"] == 20
    assert result["put_volume_change"] == 80


def test_market_trend_requires_option_and_price_confirmation():
    assert _market_trend("Leaning Bullish", "Bullish price action") == "Bullish trend"
    assert _market_trend("Leaning Bullish", "Mixed / range-bound price action") == "Mixed / neutral trend"
    assert _market_trend("Leaning Bearish", "Bearish price action") == "Bearish trend"


def test_market_trend_uses_price_signal_when_option_chain_is_conflicting():
    assert _market_trend("Leaning Bullish", "Bearish price action") == "Bearish trend"
    assert _market_trend("Leaning Bearish", "Bullish price action") == "Bullish trend"


def test_directional_market_read_is_not_hidden_by_trade_confirmation_gate():
    decision = _single_direction_summary("Bearish intraday price action", [23800], [24100], 23758)

    assert decision["signal"] == "Bearish"
    assert decision["headline"] == "Bearish bias"


def test_dashboard_url_uses_explicit_lan_host_and_port():
    assert dashboard_url("192.168.1.6", 5001) == "http://192.168.1.6:5001"


def test_market_status_blocks_weekends_and_after_close():
    from datetime import datetime
    from zoneinfo import ZoneInfo

    ist = ZoneInfo("Asia/Kolkata")
    assert market_status(datetime(2026, 9, 7, 15, 29, tzinfo=ist))["is_open"] is True
    assert market_status(datetime(2026, 9, 7, 15, 30, tzinfo=ist))["is_open"] is False
    assert market_status(datetime(2026, 9, 6, 12, 0, tzinfo=ist))["reason"] == "weekend"


def test_price_structure_detects_lower_highs_and_lower_lows():
    result = analyze_price_structure([100, 110, 105, 108, 102, 104, 98])

    assert result["trend"] == "Bearish"
    assert result["pattern"] == "Lower highs and lower lows"
    assert result["confidence"] == 1.0


def test_live_dashboard_first_snapshot_does_not_use_missing_baseline(monkeypatch):
    import scripts.web_dashboard as web_dashboard

    web_dashboard._trend_state.update({"baseline_df": None, "baseline_at": None, "last_trend": None})
    monkeypatch.setattr(web_dashboard, "get_option_chain_dhan", lambda *args, **kwargs: load_mock_nse_response())
    monkeypatch.setattr(web_dashboard, "get_latest_spot", lambda symbol: None)
    monkeypatch.setattr(web_dashboard, "start_live_feed", lambda symbol: False)
    monkeypatch.setattr(web_dashboard, "analyze_price_action", lambda symbol: {"reading": "Mixed / range-bound price action"})
    monkeypatch.setattr(web_dashboard, "market_status", lambda: {"is_open": True, "reason": "market open", "timezone": "Asia/Kolkata", "local_time": "2026-09-07 12:00:00"})

    result = web_dashboard._get_analysis("NIFTY")

    assert result["dashboard"]["trend_analysis"] == "Waiting for analysis window"


def test_dashboard_api_accepts_supported_symbol(monkeypatch):
    import scripts.web_dashboard as web_dashboard

    monkeypatch.setattr(web_dashboard, "get_analysis", lambda symbol: {"symbol": symbol})

    response = web_dashboard.APP.test_client().get("/api/trend?symbol=BANKNIFTY")

    assert response.status_code == 200
    assert response.get_json()["selected_symbol"] == "BANKNIFTY"


def test_parser_handles_nested_dhan_live_payload_and_uses_live_spot():
    raw = {
        "status": "success",
        "data": {
            "last_price": 24185.7,
            "oc": {
                "24100": {
                    "ce": {"oi": 1200, "volume": 1000, "iv": 12.5, "last_price": 120.1},
                    "pe": {"oi": 1000, "volume": 900, "iv": 14.2, "last_price": 90.2},
                },
                "24200": {
                    "ce": {"oi": 1700, "volume": 1100, "iv": 11.8, "last_price": 85.5},
                    "pe": {"oi": 2200, "volume": 1300, "iv": 15.0, "last_price": 130.5},
                },
            },
        },
    }

    df, spot = parse_option_chain(raw)

    assert spot == 24185.7
    assert len(df) == 2
    assert df.iloc[0]["strike"] == 24100
    assert df.iloc[0]["call_oi"] == 1200


def test_dashboard_reports_oi_directional_read():
    raw = load_mock_nse_response()
    df, spot = parse_option_chain(raw)
    dashboard = build_dashboard(df, df, spot)

    assert "oi_directional_read" in dashboard
    assert "oi_trend" in dashboard
    assert "final_signal" in dashboard
    assert dashboard["top_strikes"]
    assert "oi_side" in dashboard["top_strikes"][0]
    assert "call_buildup" in dashboard["top_strikes"][0]
    assert "window_call_oi_change" in dashboard["top_strikes"][0]
    assert "oi_building_analysis" in dashboard

    greek = analyze_chain_greeks(df, spot, expiry="2099-01-01")
    assert "trend" in greek
    assert "summary" in greek


def test_dashboard_flags_manipulation_risk_when_oi_is_balanced_near_atm():
    raw = load_mock_nse_response()
    df, spot = parse_option_chain(raw)
    dashboard = build_dashboard(df, df, spot)

    assert "manipulation_risk" in dashboard
    assert "manipulation_note" in dashboard
    assert dashboard["manipulation_risk"] in {"Low", "Medium", "High"}


def test_snapshot_storage_writes_raw_and_flattened_files(tmp_path, monkeypatch):
    import stock_alert.storage as storage
    from datetime import datetime

    monkeypatch.setattr(storage, "HISTORY_DIR", tmp_path)
    raw_path, csv_path = save_option_chain_snapshot(
        load_mock_nse_response(), "NIFTY", "test", datetime(2026, 8, 28, 10, 0, 0)
    )

    assert raw_path.exists()
    assert csv_path.exists()
    csv_text = csv_path.read_text()
    assert "strike" in csv_text and "call_oi" in csv_text and "put_oi" in csv_text


def test_live_snapshot_is_separate(tmp_path, monkeypatch):
    import stock_alert.storage as storage

    monkeypatch.setattr(storage, "LIVE_DIR", tmp_path)
    path = storage.save_live_snapshot(load_mock_nse_response(), "NIFTY", "test")
    assert path.parent == tmp_path
    assert path.name == "NIFTY_latest.json"


def test_daily_trend_log_writes_excel_file(tmp_path, monkeypatch):
    import pandas as pd
    import stock_alert.storage as storage
    from datetime import datetime

    monkeypatch.setattr(storage, "ANALYSIS_DIR", tmp_path)
    path = storage.save_daily_trend_change(
        "NIFTY",
        "Bearish trend",
        "test",
        captured_at=datetime(2026, 9, 2, 10, 15, 0),
        reason="Trend changed from Mixed / neutral trend to Bearish trend",
        spot_price=23850,
        pcr_oi=0.8,
        pcr_volume=1.0,
        max_pain=23900,
    )

    assert path.exists()
    df = pd.read_excel(path)
    assert df.iloc[0]["symbol"] == "NIFTY"
    assert df.iloc[0]["trend"] == "Bearish trend"
    assert df.iloc[0]["timestamp"] == "2026-09-02 10:15:00"


def test_strategy_requires_confirmation_and_avoids_lookahead():
    import pandas as pd

    dates = pd.date_range("2026-01-01", periods=22, freq="D")
    prices = pd.DataFrame({
        "Open": [100] * 22, "High": [103] * 22, "Low": [99] * 22,
        "Close": list(range(100, 122)),
    }, index=dates)
    options = pd.DataFrame({
        "captured_at": [dates[-2].isoformat(), dates[-1].isoformat()],
        "call_oi": [100, 100], "put_oi": [200, 300],
        "call_volume": [100, 100], "put_volume": [200, 300],
    })
    signals = build_signals(prices, options)

    assert signals.iloc[-1]["next_return_pct"] != signals.iloc[-1]["strategy_return_pct"]
    assert backtest(signals)["status"] in ("ok", "insufficient_history")


def test_greeks_analysis_uses_nearest_strike():
    import pandas as pd

    df = pd.DataFrame([{
        "strike": 100, "expiry": "2099-01-01", "call_iv": 20, "put_iv": 20,
        "call_volume": 1, "put_volume": 1, "call_oi": 1, "put_oi": 1,
    }])
    result = analyze_chain_greeks(df, 100, expiry="2099-01-01")
    expected = black_scholes_greeks(100, 100, "2099-01-01", 20)

    assert result["available"] is True
    assert result["strike"] == 100
    assert result["call_delta"] == round(expected["call_delta"], 4)


if __name__ == '__main__':
    main()
