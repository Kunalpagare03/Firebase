import pandas as pd


def detect_trend(df):
    """
    Detect trend using moving averages.
    """
    df['EMA20'] = df['Close'].ewm(span=20).mean()
    df['EMA50'] = df['Close'].ewm(span=50).mean()
    latest = df.iloc[-1]
    if pd.isna(latest['EMA20']) or pd.isna(latest['EMA50']):
        return "Sideways"
    if float(latest['EMA20']) > float(latest['EMA50']):
        return "Uptrend"
    elif float(latest['EMA20']) < float(latest['EMA50']):
        return "Downtrend"
    else:
        return "Sideways"
