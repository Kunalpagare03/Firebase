import pandas as pd
import numpy as np
from stockapp.app.log import logger

def compute_technical_scores(candidates_df: pd.DataFrame) -> pd.DataFrame:
    """
    Computes the 6-factor Technical Score (0 to 100) inside tonight's candidate list:
    1. Quick score percentile
    2. ADX(14) percentile
    3. SMA200 slope (20 days) percentile
    4. Sector breadth percentile
    5. Up/Down volume ratio (50 days) percentile
    6. 5-day return (lower is better -> inverted percentile rank)

    Technical Score = Mean of these 6 percentile ranks.
    """
    if candidates_df.empty:
        return candidates_df

    df = candidates_df.copy()
    n = len(df)

    if n == 1:
        df["rank_quick"] = 100.0
        df["rank_adx"] = 100.0
        df["rank_slope"] = 100.0
        df["rank_sec_breadth"] = 100.0
        df["rank_up_down_vol"] = 100.0
        df["rank_ret5_dip"] = 100.0
        df["tech_score"] = 80.0
        return df

    # Percentile ranks (0 to 100)
    df["rank_quick"] = df["quick_score"].rank(pct=True, ascending=True) * 100.0
    df["rank_adx"] = df["adx14"].rank(pct=True, ascending=True) * 100.0
    df["rank_slope"] = df["sma200_slope"].rank(pct=True, ascending=True) * 100.0
    df["rank_sec_breadth"] = df["sector_breadth"].rank(pct=True, ascending=True) * 100.0
    df["rank_up_down_vol"] = df["up_down_vol"].rank(pct=True, ascending=True) * 100.0

    # 5-day return: LOWER is better (deepest dip gets rank 100)
    df["rank_ret5_dip"] = df["ret5"].rank(pct=True, ascending=False) * 100.0

    # Plain average of the 6 ranks
    df["tech_score"] = (
        df["rank_quick"] +
        df["rank_adx"] +
        df["rank_slope"] +
        df["rank_sec_breadth"] +
        df["rank_up_down_vol"] +
        df["rank_ret5_dip"]
    ) / 6.0

    df["tech_score"] = df["tech_score"].round(2)
    return df
