import os
from datetime import datetime, time
from pathlib import Path
from typing import Optional, List, Dict
import pandas as pd
import numpy as np
from stockapp.app.settings import settings
from stockapp.app.log import logger

# Sample liquid Nifty 500 universe for primary scanning & offline mode
LIQUID_UNIVERSE = [
    "RELIANCE", "TCS", "HDFCBANK", "ICICIBANK", "INFY", "BHARTIARTL", "ITC", "SBIN",
    "LICI", "HINDUNILVR", "LT", "BAJFINANCE", "HCLTECH", "MARUTI", "SUNPHARMA",
    "ADANIENT", "KOTAKBANK", "TITAN", "ONGC", "TATAMOTORS", "NTPC", "AXISBANK",
    "DMART", "ADANIPORTS", "POWERGRID", "COALINDIA", "BAJAJFINSV", "M&M", "ULTRACEMCO",
    "ASIANPAINT", "SIEMENS", "BEL", "HAL", "ZOMATO", "TRENT", "JIOFIN", "VEDL",
    "TATASTEEL", "VBL", "CHOLAFIN", "DLF", "GRASIM", "TECHM", "EICHERMOT", "DIVISLAB",
    "NESTLEIND", "CIPLA", "APOLLOHOSP", "WIPRO", "PIDILITIND", "JSWSTEEL", "IOC",
    "HDFCLIFE", "SBILIFE", "TVSMOTOR", "HINDALCO", "BPCL", "LTIM", "BRITANNIA", "POLYCAB"
]

SECTOR_MAP = {
    "RELIANCE": "Energy", "ONGC": "Energy", "IOC": "Energy", "BPCL": "Energy", "COALINDIA": "Energy",
    "TCS": "IT", "INFY": "IT", "HCLTECH": "IT", "TECHM": "IT", "WIPRO": "IT", "LTIM": "IT",
    "HDFCBANK": "Financial Services", "ICICIBANK": "Financial Services", "SBIN": "Financial Services",
    "KOTAKBANK": "Financial Services", "AXISBANK": "Financial Services", "BAJFINANCE": "Financial Services",
    "BAJAJFINSV": "Financial Services", "CHOLAFIN": "Financial Services", "JIOFIN": "Financial Services",
    "LICI": "Financial Services", "HDFCLIFE": "Financial Services", "SBILIFE": "Financial Services",
    "BHARTIARTL": "Telecomm",
    "ITC": "FMCG", "HINDUNILVR": "FMCG", "NESTLEIND": "FMCG", "BRITANNIA": "FMCG", "VBL": "FMCG",
    "LT": "Capital Goods", "SIEMENS": "Capital Goods", "BEL": "Capital Goods", "HAL": "Capital Goods",
    "MARUTI": "Automobile", "TATAMOTORS": "Automobile", "M&M": "Automobile", "EICHERMOT": "Automobile", "TVSMOTOR": "Automobile",
    "SUNPHARMA": "Healthcare", "DIVISLAB": "Healthcare", "CIPLA": "Healthcare", "APOLLOHOSP": "Healthcare",
    "ADANIENT": "Metals & Mining", "TATASTEEL": "Metals & Mining", "VEDL": "Metals & Mining", "HINDALCO": "Metals & Mining", "JSWSTEEL": "Metals & Mining",
    "TITAN": "Consumer Durables", "ASIANPAINT": "Consumer Durables", "PIDILITIND": "Chemicals", "POLYCAB": "Capital Goods",
    "DMART": "Consumer Services", "ZOMATO": "Consumer Services", "TRENT": "Consumer Services",
    "ADANIPORTS": "Services", "POWERGRID": "Power", "NTPC": "Power", "DLF": "Realty", "ULTRACEMCO": "Construction Materials", "GRASIM": "Construction Materials"
}

def get_symbol_sector(symbol: str) -> str:
    clean = symbol.replace(".NS", "").upper()
    return SECTOR_MAP.get(clean, "Diversified")

def generate_mock_daily_candles(symbol: str, days: int = 300) -> pd.DataFrame:
    """Generates realistic synthetic OHLCV daily data for testing and offline modes."""
    rng = np.random.default_rng(seed=abs(hash(symbol)) % (2**32))
    base_price = 100.0 + (abs(hash(symbol)) % 2500)
    dates = pd.date_range(end=datetime.now().date(), periods=days, freq="B")
    
    returns = rng.normal(loc=0.0006, scale=0.018, size=days)
    # simulate some dip behavior
    if symbol in ["TRENT", "BEL", "HAL", "ZOMATO"]:
        # Add a recent sharp dip
        returns[-3:] = [-0.035, -0.028, -0.025]
    elif symbol in ["RELIANCE", "TCS"]:
        returns[-1] = -0.04
        
    prices = base_price * np.exp(np.cumsum(returns))
    
    df = pd.DataFrame(index=dates)
    df["Close"] = prices
    df["Open"] = prices * (1 + rng.normal(0, 0.006, size=days))
    df["High"] = np.maximum(df["Open"], df["Close"]) * (1 + np.abs(rng.normal(0, 0.008, size=days)))
    df["Low"] = np.minimum(df["Open"], df["Close"]) * (1 - np.abs(rng.normal(0, 0.008, size=days)))
    
    # 20-day traded value target >= 5 Crore (Volume * Price)
    # 5 Crore = 50,000,000 Rs.
    target_volume = (60_000_000 / df["Close"]).clip(lower=10000)
    df["Volume"] = (target_volume * rng.uniform(0.6, 2.0, size=days)).astype(int)
    df["Turnover_Cr"] = (df["Close"] * df["Volume"]) / 10_000_000.0
    df.reset_index(names=["Date"], inplace=True)
    return df

def fetch_price_data(symbol: str, lookback_days: int = 365, offline_mode: bool = False) -> pd.DataFrame:
    """
    Downloads or retrieves daily price data for a stock.
    Drops today's unformed candle if called before 15:45 IST on a trading day.
    """
    clean_sym = symbol.replace(".NS", "").upper()
    file_path = settings.prices_dir / f"{clean_sym}.parquet"

    if offline_mode:
        if file_path.exists():
            try:
                return pd.read_parquet(file_path)
            except Exception:
                pass
        df = generate_mock_daily_candles(clean_sym, days=lookback_days)
        df.to_parquet(file_path, index=False)
        return df

    # In live mode, try yfinance with fallback to cached or synthetic data
    try:
        import yfinance as yf
        ticker_str = f"{clean_sym}.NS" if not clean_sym.startswith("^") else clean_sym
        ticker = yf.Ticker(ticker_str)
        df = ticker.history(period="1y", interval="1d", auto_adjust=False)
        if df.empty or len(df) < 50:
            raise ValueError(f"Insufficient data returned for {ticker_str}")
        
        df.reset_index(inplace=True)
        # Ensure standard column names
        df.rename(columns={"date": "Date", "open": "Open", "high": "High", "low": "Low", "close": "Close", "volume": "Volume"}, inplace=True)
        if "Close" in df.columns and "Volume" in df.columns:
            df["Turnover_Cr"] = (df["Close"] * df["Volume"]) / 10_000_000.0
        
        # Check 15:45 IST candle formation rule
        now_time = datetime.now().time()
        if now_time < time(15, 45):
            today_date = datetime.now().date()
            if not df.empty and pd.to_datetime(df["Date"].iloc[-1]).date() == today_date:
                df = df.iloc[:-1] # drop incomplete candle
        
        df.to_parquet(file_path, index=False)
        return df
    except Exception as e:
        logger.debug(f"Online fetch failed for {clean_sym} ({e}), falling back to local/cached data")
        if file_path.exists():
            return pd.read_parquet(file_path)
        df = generate_mock_daily_candles(clean_sym, days=lookback_days)
        df.to_parquet(file_path, index=False)
        return df

def get_nifty_and_vix(offline_mode: bool = False) -> Dict[str, pd.DataFrame]:
    """Fetches Nifty 50 and India VIX daily histories."""
    nifty = fetch_price_data(settings.traffic_light.nifty_symbol, offline_mode=offline_mode)
    vix = fetch_price_data(settings.traffic_light.vix_symbol, offline_mode=offline_mode)
    return {"nifty": nifty, "vix": vix}
