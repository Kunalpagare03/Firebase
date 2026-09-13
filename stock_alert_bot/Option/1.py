import requests
import pandas as pd

# -------------------------------
# Step 1: Fetch Option Chain Data
# -------------------------------
def fetch_option_chain(symbol="NIFTY"):
    url = f"https://www.nseindia.com/api/option-chain-indices?symbol={symbol}"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
        "Accept-Language": "en-US,en;q=0.9",
        "Accept-Encoding": "gzip, deflate, br",
    }
    session = requests.Session()
    session.get("https://www.nseindia.com", headers=headers)  # warm-up
    response = session.get(url, headers=headers)
    try:
        data = response.json()
    except Exception as e:
        print(f"Failed to parse JSON from {url}; status={response.status_code}")
        body_snippet = response.text[:500].replace('\n', ' ')
        print(f"Response snippet: {body_snippet!r}")
        raise
    return data

# -------------------------------
# Step 2: Parse Data into Table
# -------------------------------
def parse_option_chain(data):
    spot_price = data["records"]["underlyingValue"]
    options = data["records"]["data"]

    rows = []
    for opt in options:
        strike = opt["strikePrice"]
        call = opt.get("CE", {})
        put = opt.get("PE", {})
        rows.append({
            "strike": strike,
            "call_OI": call.get("openInterest", 0),
            "call_change_OI": call.get("changeinOpenInterest", 0),
            "call_volume": call.get("totalTradedVolume", 0),
            "call_IV": call.get("impliedVolatility", 0),
            "put_OI": put.get("openInterest", 0),
            "put_change_OI": put.get("changeinOpenInterest", 0),
            "put_volume": put.get("totalTradedVolume", 0),
            "put_IV": put.get("impliedVolatility", 0),
        })
    df = pd.DataFrame(rows)
    return spot_price, df

# -------------------------------
# Step 3: PCR Calculation
# -------------------------------
def calculate_pcr(df):
    pcr_oi = df["put_OI"].sum() / df["call_OI"].sum()
    pcr_vol = df["put_volume"].sum() / df["call_volume"].sum()
    return pcr_oi, pcr_vol

# -------------------------------
# Step 4: Max Pain Calculation
# -------------------------------
def calculate_max_pain(df):
    strikes = df["strike"].unique()
    pain = {}
    for strike in strikes:
        call_loss = ((df["strike"] - strike).clip(lower=0) * df["call_OI"]).sum()
        put_loss = ((strike - df["strike"]).clip(lower=0) * df["put_OI"]).sum()
        pain[strike] = call_loss + put_loss
    max_pain_strike = min(pain, key=pain.get)
    return max_pain_strike

# -------------------------------
# Step 5: Support & Resistance
# -------------------------------
def support_resistance(df):
    support = df.loc[df["put_OI"].idxmax(), "strike"]
    resistance = df.loc[df["call_OI"].idxmax(), "strike"]
    return support, resistance

# -------------------------------
# Step 6: OI Buildup Classification
# -------------------------------
def classify_oi_buildup(df):
    conditions = []
    for _, row in df.iterrows():
        if row["call_change_OI"] > 0 and row["call_volume"] > 0:
            conditions.append((row["strike"], "Call Long Buildup"))
        elif row["call_change_OI"] < 0:
            conditions.append((row["strike"], "Call Unwinding"))
        if row["put_change_OI"] > 0 and row["put_volume"] > 0:
            conditions.append((row["strike"], "Put Long Buildup"))
        elif row["put_change_OI"] < 0:
            conditions.append((row["strike"], "Put Unwinding"))
    return conditions

# -------------------------------
# Step 7: IV Skew Analysis
# -------------------------------
def iv_skew(df):
    avg_call_iv = df["call_IV"].mean()
    avg_put_iv = df["put_IV"].mean()
    skew = avg_put_iv - avg_call_iv
    sentiment = "Bullish bias" if skew > 0 else "Bearish bias"
    return skew, sentiment

# -------------------------------
# Step 8: Dashboard
# -------------------------------
def sentiment_dashboard(symbol="NIFTY"):
    data = fetch_option_chain(symbol)
    spot, df = parse_option_chain(data)
    pcr_oi, pcr_vol = calculate_pcr(df)
    max_pain = calculate_max_pain(df)
    support, resistance = support_resistance(df)
    buildups = classify_oi_buildup(df)
    skew, skew_sentiment = iv_skew(df)

    print(f"\n📊 Sentiment Dashboard for {symbol}")
    print(f"Spot Price: {spot}")
    print(f"PCR (OI): {pcr_oi:.2f}, PCR (Volume): {pcr_vol:.2f}")
    print(f"Max Pain: {max_pain}")
    print(f"Support Zone: {support}, Resistance Zone: {resistance}")
    print(f"IV Skew: {skew:.2f} → {skew_sentiment}")
    print("\nOI Buildup Signals:")
    for strike, signal in buildups[:10]:  # show first 10
        print(f"  Strike {strike}: {signal}")
    print("\n⚠️ Note: These are probabilistic signals. News, FII/DII flows, and global cues can override option chain sentiment.")

# -------------------------------
# Run Example
# -------------------------------
if __name__ == "__main__":
    sentiment_dashboard("NIFTY")   # Try "BANKNIFTY" or "NIFTY" first
    # For Sensex, you’ll need BSE option chain API (different endpoint).
