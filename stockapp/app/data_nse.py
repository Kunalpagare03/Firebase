import os
from datetime import datetime, timedelta
from pathlib import Path
from typing import Dict, Set, Optional
import pandas as pd
from stockapp.app.settings import settings
from stockapp.app.log import logger

def get_upcoming_results_symbols(window_days: int = 2) -> Set[str]:
    """
    Returns the set of symbols reporting financial results within the next `window_days` days.
    Stocks reporting results are vetoed from the nightly pick selection.
    """
    calendar_file = settings.nse_dir / "results_calendar.csv"
    if calendar_file.exists():
        try:
            df = pd.read_csv(calendar_file)
            if "symbol" in df.columns and "date" in df.columns:
                df["date"] = pd.to_datetime(df["date"]).dt.date
                today = datetime.now().date()
                cutoff = today + timedelta(days=window_days)
                flagged = df[(df["date"] >= today) & (df["date"] <= cutoff)]["symbol"].str.upper().tolist()
                return set(flagged)
        except Exception as e:
            logger.warning(f"Error reading results calendar: {e}")
            
    # Default placeholder: no veto if file absent or parsing fails
    return set()

def fetch_nse_delivery_data() -> Dict[str, float]:
    """
    Loads delivery percentage from local NSE bhavcopy.
    Returns mapping of symbol -> delivery_pct (0.0 to 1.0).
    """
    delivery_map: Dict[str, float] = {}
    bhav_file = settings.nse_dir / "sec_bhavdata_full.csv"
    if bhav_file.exists():
        try:
            df = pd.read_csv(bhav_file)
            # NSE columns: SYMBOL, SERIES, DELIV_QTY, DELIV_PER
            if "SYMBOL" in df.columns and "DELIV_PER" in df.columns:
                # filter EQ series
                if "SERIES" in df.columns:
                    df = df[df["SERIES"].str.strip() == "EQ"]
                for _, row in df.iterrows():
                    sym = str(row["SYMBOL"]).strip().upper()
                    try:
                        deliv = float(row["DELIV_PER"]) / 100.0
                        delivery_map[sym] = deliv
                    except ValueError:
                        continue
        except Exception as e:
            logger.warning(f"Error reading delivery bhavdata: {e}")
    return delivery_map
