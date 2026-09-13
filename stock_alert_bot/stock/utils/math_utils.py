import numpy as np

def daily_returns(df):
    """
    Calculate daily returns.
    R_t = (P_t - P_{t-1}) / P_{t-1}
    """
    df['DailyReturn'] = df['Close'].pct_change()
    return df

def moving_average(df, window=20):
    """
    Calculate simple moving average.
    """
    df[f"SMA{window}"] = df['Close'].rolling(window).mean()
    return df

def volatility(df, window=20):
    """
    Calculate rolling volatility.
    """
    df['Volatility'] = df['DailyReturn'].rolling(window).std()
    return df
