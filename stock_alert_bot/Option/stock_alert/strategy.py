import pandas as pd


def classify_candle(frame):
    """Classify the latest candle using OHLC data when available."""
    if frame.empty:
        return "unknown"
    row = frame.iloc[-1]
    body = abs(row["Close"] - row["Open"])
    candle_range = row["High"] - row["Low"]
    if candle_range <= 0:
        return "unknown"
    lower_wick = min(row["Open"], row["Close"]) - row["Low"]
    upper_wick = row["High"] - max(row["Open"], row["Close"])
    if row["Close"] > row["Open"] and body / candle_range >= 0.6:
        return "strong bullish candle"
    if row["Close"] < row["Open"] and body / candle_range >= 0.6:
        return "strong bearish candle"
    if lower_wick >= body * 2 and upper_wick <= body:
        return "hammer-like candle"
    if upper_wick >= body * 2 and lower_wick <= body:
        return "rejection candle"
    return "small / indecisive candle"


def build_signals(price_history, option_summary):
    """Create one signal per option snapshot using only prior information."""
    prices = price_history.copy().sort_index()
    prices["sma20"] = prices["Close"].rolling(20).mean()
    prices["previous_close"] = prices["Close"].shift(1)
    prices["price_change_pct"] = prices["Close"].pct_change() * 100
    prices["candle"] = [classify_candle(prices.iloc[: index + 1]) for index in range(len(prices))]

    options = option_summary.copy()
    options["date"] = pd.to_datetime(options["captured_at"]).dt.normalize()
    options = options.sort_values("date").drop_duplicates("date", keep="last")
    options["pcr_oi"] = options["put_oi"] / options["call_oi"].replace(0, pd.NA)
    options["pcr_volume"] = options["put_volume"] / options["call_volume"].replace(0, pd.NA)
    options["oi_imbalance"] = options["put_oi"] - options["call_oi"]
    options["volume_imbalance"] = options["put_volume"] - options["call_volume"]
    price_rows = prices.reset_index(names="date")
    price_rows["date"] = pd.to_datetime(price_rows["date"]).dt.tz_localize(None).dt.normalize()
    merged = options.merge(price_rows, on="date", how="inner")

    def signal(row):
        bullish = int(row["Close"] > row["sma20"] and row["price_change_pct"] > 0)
        bearish = int(row["Close"] < row["sma20"] and row["price_change_pct"] < 0)
        bullish += int(row["pcr_oi"] > 1.1 and row["oi_imbalance"] > 0)
        bearish += int(row["pcr_oi"] < 0.9 and row["oi_imbalance"] < 0)
        bullish += int(row["volume_imbalance"] > 0)
        bearish += int(row["volume_imbalance"] < 0)
        bullish += int(row["candle"] in ("strong bullish candle", "hammer-like candle"))
        bearish += int(row["candle"] in ("strong bearish candle", "rejection candle"))
        if bullish >= 3 and bullish > bearish:
            return "LONG"
        if bearish >= 3 and bearish > bullish:
            return "SHORT"
        return "FLAT"

    merged["signal"] = merged.apply(signal, axis=1)
    merged["next_return_pct"] = merged["Close"].shift(-1).div(merged["Close"]).sub(1).mul(100)
    merged["strategy_return_pct"] = merged["next_return_pct"] * merged["signal"].map({"LONG": 1, "SHORT": -1, "FLAT": 0})
    return merged


def backtest(signals):
    trades = signals[signals["signal"].isin(("LONG", "SHORT")) & signals["next_return_pct"].notna()].copy()
    if trades.empty:
        return {"status": "insufficient_history", "observations": len(signals), "trades": 0}
    wins = (trades["strategy_return_pct"] > 0).sum()
    return {
        "status": "ok",
        "observations": len(signals),
        "trades": len(trades),
        "wins": int(wins),
        "win_rate_pct": round(float(wins / len(trades) * 100), 2),
        "total_return_pct": round(float(trades["strategy_return_pct"].sum()), 2),
        "average_trade_pct": round(float(trades["strategy_return_pct"].mean()), 2),
    }