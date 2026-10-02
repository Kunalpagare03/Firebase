import pandas as pd
from step2_parse import parse_option_chain, load_mock_nse_response


def calculate_pcr(df):
    """
    Calculates Put-Call Ratio two ways:
    - OI-based PCR: total Put OI / total Call OI  (most commonly quoted)
    - Volume-based PCR: total Put volume / total Call volume (more intraday/short-term)

    Rough, widely-used (not precise) reading conventions:
      PCR > 1.3   -> often read as oversold / potential bullish reversal zone
      PCR 0.7-1.3 -> neutral / range-bound sentiment
      PCR < 0.7   -> often read as overbought / potential bearish reversal zone

    These are heuristics, not rules — they work better as one input
    alongside price action than as standalone signals.
    """
    total_call_oi = df["call_oi"].sum()
    total_put_oi = df["put_oi"].sum()
    total_call_vol = df["call_volume"].sum()
    total_put_vol = df["put_volume"].sum()

    pcr_oi = round(total_put_oi / total_call_oi, 3) if total_call_oi else None
    pcr_volume = round(total_put_vol / total_call_vol, 3) if total_call_vol else None

    def interpret(pcr):
        if pcr is None:
            return "N/A"
        if pcr > 1.3:
            return "Elevated put activity — often read as oversold/bullish-leaning sentiment"
        elif pcr < 0.7:
            return "Elevated call activity — often read as overbought/bearish-leaning sentiment"
        else:
            return "Balanced — neutral/range-bound sentiment"

    return {
        "total_call_oi": total_call_oi,
        "total_put_oi": total_put_oi,
        "total_call_volume": total_call_vol,
        "total_put_volume": total_put_vol,
        "pcr_oi": pcr_oi,
        "pcr_oi_reading": interpret(pcr_oi),
        "pcr_volume": pcr_volume,
        "pcr_volume_reading": interpret(pcr_volume),
    }


if __name__ == "__main__":
    raw = load_mock_nse_response()   # on your machine: raw = get_option_chain("NIFTY")
    df, spot = parse_option_chain(raw)

    result = calculate_pcr(df)

    print(f"Underlying spot: {spot}\n")
    for k, v in result.items():
        print(f"{k}: {v}")
