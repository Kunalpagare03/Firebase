def detect_vcp(df):
    """
    Simplified VCP detection: narrowing volatility and breakout potential.
    """
    df['Volatility'] = (df['High'] - df['Low']) / df['Close']
    narrowing = df['Volatility'].rolling(20).mean().diff().mean() < 0
    breakout = df['Close'].iloc[-1] > df['Close'].rolling(50).mean().iloc[-1]
    if narrowing and breakout:
        return "VCP breakout candidate"
    return "No VCP pattern"
