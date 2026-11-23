import pytest
import pandas as pd
import numpy as np
from stockapp.app.indicators import (
    compute_wilder_atr, compute_wilder_rsi, compute_supertrend, calculate_all_indicators
)
from stockapp.app.settings import settings
from stockapp.app.market import MarketTrafficLight
from stockapp.app.score import compute_technical_scores
from stockapp.app.longterm import evaluate_long_term_candidates

def test_indicators_calculation():
    # Synthetic series
    dates = pd.date_range("2026-01-01", periods=250, freq="B")
    prices = 100 + np.cumsum(np.random.normal(0.1, 1.0, 250))
    df = pd.DataFrame({
        "Date": dates,
        "Open": prices,
        "High": prices + 1.5,
        "Low": prices - 1.5,
        "Close": prices,
        "Volume": 100000
    })
    ind_df = calculate_all_indicators(df)
    assert "SMA200" in ind_df.columns
    assert "RSI2" in ind_df.columns
    assert "ATR14" in ind_df.columns
    assert "RAM" in ind_df.columns
    assert "Supertrend" in ind_df.columns

def test_traffic_light_states():
    tl = MarketTrafficLight(mode="INFO")
    # Test Green State
    res = tl.evaluate(market_breadth={"pct_above_sma50": 65.0}, offline_mode=True)
    assert res["state"] in [1, 2, 3, 4, 5]
    assert res["color"] in ["GREEN", "AMBER", "RED"]

def test_technical_score():
    candidates = pd.DataFrame([
        {
            "symbol": "STOCK_A", "quick_score": 85.0, "adx14": 30.0, "sma200_slope": 0.05,
            "sector_breadth": 60.0, "up_down_vol": 1.5, "ret5": -0.07
        },
        {
            "symbol": "STOCK_B", "quick_score": 45.0, "adx14": 15.0, "sma200_slope": -0.01,
            "sector_breadth": 40.0, "up_down_vol": 0.8, "ret5": 0.02
        }
    ])
    scored = compute_technical_scores(candidates)
    assert "tech_score" in scored.columns
    assert scored.iloc[0]["tech_score"] > scored.iloc[1]["tech_score"]
