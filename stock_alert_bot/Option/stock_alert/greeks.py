import math
from datetime import date, datetime


def _normal_pdf(value):
    return math.exp(-0.5 * value * value) / math.sqrt(2 * math.pi)


def _normal_cdf(value):
    return 0.5 * (1 + math.erf(value / math.sqrt(2)))


def _days_to_expiry(expiry):
    if not expiry:
        return None
    for fmt in ("%d-%b-%Y", "%d-%m-%Y", "%Y-%m-%d"):
        try:
            return max((datetime.strptime(expiry, fmt).date() - date.today()).days, 0.0)
        except ValueError:
            continue
    return None


def black_scholes_greeks(spot, strike, expiry, volatility, risk_free_rate=0.07):
    """Calculate approximate European option Greeks for one strike."""
    days = _days_to_expiry(expiry)
    if not spot or not strike or not volatility or days is None:
        return None
    time_to_expiry = max(days / 365.0, 1 / 365.0)
    sigma = volatility / 100 if volatility > 1 else volatility
    if sigma <= 0:
        return None
    root_time = math.sqrt(time_to_expiry)
    d1 = (math.log(spot / strike) + (risk_free_rate + 0.5 * sigma**2) * time_to_expiry) / (sigma * root_time)
    d2 = d1 - sigma * root_time
    discount = math.exp(-risk_free_rate * time_to_expiry)
    return {
        "call_delta": _normal_cdf(d1),
        "put_delta": _normal_cdf(d1) - 1,
        "gamma": _normal_pdf(d1) / (spot * sigma * root_time),
        "vega_per_1pct": spot * _normal_pdf(d1) * root_time / 100,
        "call_theta_per_day": (-spot * _normal_pdf(d1) * sigma / (2 * root_time) - risk_free_rate * strike * discount * _normal_cdf(d2)) / 365,
        "put_theta_per_day": (-spot * _normal_pdf(d1) * sigma / (2 * root_time) + risk_free_rate * strike * discount * _normal_cdf(-d2)) / 365,
    }


def analyze_chain_greeks(df, spot, expiry=None):
    """Summarize Greeks at the closest available strike to the spot."""
    if df.empty or spot is None:
        return {"available": False, "reason": "Spot or option chain unavailable"}
    row = df.iloc[(df["strike"] - float(spot)).abs().argmin()]
    expiry = expiry or row.get("expiry")
    call = black_scholes_greeks(spot, row["strike"], expiry, row["call_iv"])
    put = black_scholes_greeks(spot, row["strike"], expiry, row["put_iv"])
    if call is None or put is None:
        return {"available": False, "reason": "Valid expiry and IV unavailable"}
    directional = "Positive directional delta" if call["call_delta"] > 0.5 else "Negative directional delta"
    if call["call_delta"] > 0.6:
        trend = "Bullish ATM skew / directional bullish"
    elif call["call_delta"] < 0.4:
        trend = "Bearish ATM skew / directional bearish"
    else:
        trend = "Neutral / mixed ATM skew"
    return {
        "available": True,
        "strike": int(row["strike"]),
        "expiry": expiry,
        "call_delta": round(call["call_delta"], 4),
        "put_delta": round(put["put_delta"], 4),
        "gamma": round((call["gamma"] + put["gamma"]) / 2, 6),
        "vega_per_1pct": round((call["vega_per_1pct"] + put["vega_per_1pct"]) / 2, 4),
        "call_theta_per_day": round(call["call_theta_per_day"], 4),
        "put_theta_per_day": round(put["put_theta_per_day"], 4),
        "reading": directional,
        "trend": trend,
        "summary": f"{directional}; {trend}",
    }