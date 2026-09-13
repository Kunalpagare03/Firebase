import pandas as pd
from step2_parse import parse_option_chain, load_mock_nse_response


def find_support_resistance(df, top_n=3, spot=None, range_points=500):
    """
    Support/Resistance from OI concentration:
      - Highest Put OI strikes -> potential SUPPORT (put writers defend price staying above)
      - Highest Call OI strikes -> potential RESISTANCE (call writers defend price staying below)

    Returns top_n strikes on each side, ranked by OI.

    Caveat: these are 'currently defended' zones based on today's positioning.
    They can break decisively on strong news/momentum days -- OI walls are
    not hard floors/ceilings, just areas of concentrated interest.
    """
    working = df.copy()
    if spot is not None:
        working = working[
            working["strike"].between(spot - range_points, spot + range_points)
        ]
    resistance_source = working[working["strike"] > spot] if spot is not None else working
    support_source = working[working["strike"] < spot] if spot is not None else working
    resistance = (
        resistance_source[["strike", "call_oi", "call_oi_change"]]
        .sort_values("call_oi", ascending=False)
        .head(top_n)
        .reset_index(drop=True)
    )
    support = (
        support_source[["strike", "put_oi", "put_oi_change"]]
        .sort_values("put_oi", ascending=False)
        .head(top_n)
        .reset_index(drop=True)
    )
    return support, resistance


if __name__ == "__main__":
    raw = load_mock_nse_response()   # on your machine: raw = get_option_chain("NIFTY")
    df, spot = parse_option_chain(raw)

    support, resistance = find_support_resistance(df, top_n=3)

    print(f"Underlying spot: {spot}\n")
    print("Top SUPPORT zones (highest Put OI):")
    print(support.to_string(index=False))
    print("\nTop RESISTANCE zones (highest Call OI):")
    print(resistance.to_string(index=False))
