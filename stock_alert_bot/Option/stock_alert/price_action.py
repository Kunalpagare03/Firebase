import yfinance as yf


YAHOO_SYMBOLS = {
    "NIFTY": "^NSEI",
    "NSEI": "^NSEI",
    "NIFTY 50": "^NSEI",
    "BANKNIFTY": "^NSEBANK",
    "NSEBANK": "^NSEBANK",
    "FINNIFTY": "^CNXFIN",
    "SENSEX": "^BSESN",
}


def analyze_price_action(symbol="NIFTY", period="3mo"):
    """Return recent index price-action metrics from Yahoo Finance."""
    yahoo_symbol = YAHOO_SYMBOLS.get(symbol.upper(), symbol)
    history = yf.Ticker(yahoo_symbol).history(period=period, interval="1d", auto_adjust=False)
    if history.empty:
        raise ValueError(f"No usable price history returned for {symbol}")

    close = history["Close"].dropna()
    if close.empty:
        raise ValueError(f"No usable closing prices returned for {symbol}")

    latest = float(close.iloc[-1])
    if len(close) < 2:
        recent = close.tail(5)
        return {
            "symbol": symbol,
            "yahoo_symbol": yahoo_symbol,
            "close": round(latest, 2),
            "previous_close": None,
            "change": None,
            "change_pct": None,
            "sma20": round(float(close.mean()), 2),
            "five_day_high": round(float(recent.max()), 2),
            "five_day_low": round(float(recent.min()), 2),
            "reading": "Insufficient previous close",
        }

    previous = float(close.iloc[-2])
    day_change = latest - previous
    day_change_pct = (day_change / previous) * 100 if previous else 0.0
    sma20 = float(close.tail(20).mean())
    recent = close.tail(5)

    if latest > sma20 and day_change > 0:
        reading = "Bullish price action"
    elif latest < sma20 and day_change < 0:
        reading = "Bearish price action"
    else:
        reading = "Mixed / range-bound price action"

    return {
        "symbol": symbol,
        "yahoo_symbol": yahoo_symbol,
        "close": round(latest, 2),
        "previous_close": round(previous, 2),
        "change": round(day_change, 2),
        "change_pct": round(day_change_pct, 2),
        "sma20": round(sma20, 2),
        "five_day_high": round(float(recent.max()), 2),
        "five_day_low": round(float(recent.min()), 2),
        "reading": reading,
    }


def print_price_action(result):
    print("\n PRICE ACTION")
    print(f" Close               : {result['close']}")
    print(f" Previous close      : {result['previous_close']}")
    print(f" Daily change        : {result['change']} ({result['change_pct']}%)")
    print(f" 20-session SMA      : {result['sma20']}")
    print(f" 5-session range     : {result['five_day_low']} - {result['five_day_high']}")
    print(f" Price-action read   : {result['reading']}")