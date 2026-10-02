import yfinance as yf
import pandas as pd

def fetch_stock_data(symbol: str, period="6mo", interval="1d") -> pd.DataFrame:
    """
    Fetch OHLCV data for a stock using Yahoo Finance.
    Args:
        symbol (str): Stock ticker symbol
        period (str): Data period (default 6 months)
        interval (str): Data interval (default daily)
    Returns:
        pd.DataFrame: OHLCV data
    """
    try:
        df = yf.download(symbol, period=period, interval=interval)
        if isinstance(df.columns, pd.MultiIndex):
            df.columns = df.columns.get_level_values(0)
        df.dropna(inplace=True)
        return df
    except Exception as e:
        print(f"Error fetching data for {symbol}: {e}")
        return pd.DataFrame()
