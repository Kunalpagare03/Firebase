import pandas as pd
import numpy as np
from step2_parse import parse_option_chain
from step1_fetch import get_option_chain

def perform_deep_dive(df, spot_price):
    """
    Analyzes option chain with heavy focus on Volume and smart-money activity.
    """
    total_call_vol = df['call_volume'].sum()
    total_put_vol = df['put_volume'].sum()
    vol_pcr = round(total_put_vol / total_call_vol, 3) if total_call_vol > 0 else 0

    # 1. Volume Concentration
    # Find top 3 strikes by volume for Calls and Puts
    top_call_vol_strikes = df.nlargest(3, 'call_volume')[['strike', 'call_volume', 'call_oi']]
    top_put_vol_strikes = df.nlargest(3, 'put_volume')[['strike', 'put_volume', 'put_oi']]

    # 2. Volume-to-OI Ratio (Conviction Metric)
    # High Ratio (> 2.0) = Fast intraday movement, low institutional holding
    # Low Ratio (< 0.5) = Institutional building, high conviction
    df['call_v_oi_ratio'] = df['call_volume'] / df['call_oi'].replace(0, 1)
    df['put_v_oi_ratio'] = df['put_volume'] / df['put_oi'].replace(0, 1)

    # 3. Active Resistance/Support based on Volume
    resistance_vol = top_call_vol_strikes.iloc[0]['strike']
    support_vol = top_put_vol_strikes.iloc[0]['strike']

    # 4. Volume Distribution Read
    # If volume is higher above spot for calls -> Bearish (sellers active)
    # If volume is higher below spot for puts -> Bullish (buyers active)
    vol_above_spot_calls = df[df['strike'] > spot_price]['call_volume'].sum()
    vol_below_spot_puts = df[df['strike'] < spot_price]['put_volume'].sum()

    analysis = {
        "spot": spot_price,
        "volume_pcr": vol_pcr,
        "vol_pcr_sentiment": "Bullish" if vol_pcr > 1.1 else "Bearish" if vol_pcr < 0.8 else "Neutral",
        "resistance_vol": int(resistance_vol),
        "support_vol": int(support_vol),
        "top_call_vol_strikes": top_call_vol_strikes.to_dict('records'),
        "top_put_vol_strikes": top_put_vol_strikes.to_dict('records'),
        "v_oi_read": "High Intraday Churn" if df['call_v_oi_ratio'].mean() > 1.5 else "Institutional Setup",
        "market_pressure": "Selling Pressure" if vol_above_spot_calls > vol_below_spot_puts else "Buying Support"
    }
    return analysis

def print_deep_dive(a):
    print("\n" + "="*60)
    print("      OPTION CHAIN DEEP DIVE & VOLUME ANALYSIS")
    print("="*60)
    print(f" Current Spot Price    : {a['spot']}")
    print(f" Volume PCR            : {a['volume_pcr']} ({a['vol_pcr_sentiment']})")
    print(f" Volume Resistance     : {a['resistance_vol']} (Highest Call Volume)")
    print(f" Volume Support        : {a['support_vol']} (Highest Put Volume)")
    print(f" Market Pressure       : {a['market_pressure']}")
    print(f" Participant Type      : {a['v_oi_read']}")

    print("\n[TOP 3 VOLUME STRIKES - CALLS (Resistance Building)]")
    for r in a['top_call_vol_strikes']:
        print(f" Strike: {r['strike']} | Volume: {r['call_volume']:,} | OI: {r['call_oi']:,}")

    print("\n[TOP 3 VOLUME STRIKES - PUTS (Support Building)]")
    for r in a['top_put_vol_strikes']:
        print(f" Strike: {r['strike']} | Volume: {r['put_volume']:,} | OI: {r['put_oi']:,}")
    print("="*60 + "\n")

if __name__ == "__main__":
    try:
        raw = get_option_chain("NIFTY")
        df, spot = parse_option_chain(raw)
        analysis = perform_deep_dive(df, spot)
        print_deep_dive(analysis)
    except Exception as e:
        print(f"Deep Dive Failed: {e}")
