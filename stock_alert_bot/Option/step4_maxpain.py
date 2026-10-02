import pandas as pd
from step2_parse import parse_option_chain, load_mock_nse_response


def calculate_max_pain(df):
    """
    Max Pain = the strike at which total notional loss to option HOLDERS
    (call buyers + put buyers) is minimized -- equivalently, where option
    WRITERS as a group lose the least / gain the most.

    Method: for each candidate expiry strike S, assume the index settles at S.
      - Every Call with strike < S is in-the-money by (S - strike); writer pays out (S-strike) * call_oi
      - Every Put with strike > S is in-the-money by (strike - S); writer pays out (strike-S) * put_oi
      - Sum these payouts across all strikes = total pain at that settlement price S
    The strike S with the LOWEST total pain is the Max Pain point.

    Caveat: most reliable close to expiry, in low-volatility conditions.
    In strongly trending or event-driven markets it can be overridden easily.
    """
    strikes = df["strike"].values
    pain_by_strike = []

    for candidate_settle in strikes:
        total_pain = 0

        # Call side: ITM calls are those with strike < candidate settle price
        itm_calls = df[df["strike"] < candidate_settle]
        call_payout = ((candidate_settle - itm_calls["strike"]) * itm_calls["call_oi"]).sum()

        # Put side: ITM puts are those with strike > candidate settle price
        itm_puts = df[df["strike"] > candidate_settle]
        put_payout = ((itm_puts["strike"] - candidate_settle) * itm_puts["put_oi"]).sum()

        total_pain = call_payout + put_payout
        pain_by_strike.append({"strike": candidate_settle, "total_pain": total_pain})

    pain_df = pd.DataFrame(pain_by_strike).sort_values("strike").reset_index(drop=True)
    max_pain_strike = pain_df.loc[pain_df["total_pain"].idxmin(), "strike"]

    return max_pain_strike, pain_df


if __name__ == "__main__":
    raw = load_mock_nse_response()   # on your machine: raw = get_option_chain("NIFTY")
    df, spot = parse_option_chain(raw)

    max_pain, pain_table = calculate_max_pain(df)

    print(f"Underlying spot: {spot}")
    print(f"Max Pain strike: {max_pain}\n")
    print("Pain by strike (lower = more likely settle point):")
    print(pain_table.to_string(index=False))
