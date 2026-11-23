"""Historical day matching for regime identification (Part C3)."""

from typing import Dict, List, Optional
import numpy as np
import pandas as pd


def find_similar_trading_days(
    target_gap_pct: float,
    target_vix: float,
    historical_df: pd.DataFrame,
    top_n: int = 5,
) -> List[Dict[str, any]]:
    """
    Find past trading days with closest gap % and VIX level using Euclidean distance.
    historical_df columns expected: [date, gap_pct, vix, day_return_pct, day_range_pct]
    """
    if historical_df.empty or len(historical_df) < top_n:
        return []

    df = historical_df.copy()
    # Normalize features
    gap_std = df["gap_pct"].std() or 1.0
    vix_std = df["vix"].std() or 1.0

    dist = np.sqrt(
        ((df["gap_pct"] - target_gap_pct) / gap_std) ** 2 +
        ((df["vix"] - target_vix) / vix_std) ** 2
    )
    df["distance"] = dist
    sorted_df = df.sort_values(by="distance").head(top_n)

    matches = []
    for _, row in sorted_df.iterrows():
        matches.append({
            "date": str(row.get("date")),
            "gap_pct": float(row.get("gap_pct", 0.0)),
            "vix": float(row.get("vix", 0.0)),
            "day_return_pct": float(row.get("day_return_pct", 0.0)),
            "day_range_pct": float(row.get("day_range_pct", 0.0)),
            "distance": float(row.get("distance", 0.0)),
        })

    return matches
