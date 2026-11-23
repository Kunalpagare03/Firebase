"""NSE / BSE Bhavcopy Downloader and Local Cache (Part C1)."""

import os
from datetime import date, datetime
from pathlib import Path
from typing import Optional
import requests
import pandas as pd
from optionapp.app.log import get_logger

logger = get_logger("bhavcopy")

DATA_DIR = Path("data")


def get_bhavcopy_path(d: date, exchange: str = "NSE") -> Path:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    return DATA_DIR / f"bhavcopy_{exchange.lower()}_{d.strftime('%Y%m%d')}.csv"


def download_bhavcopy_nse(d: date) -> Optional[Path]:
    """Download daily NSE Bhavcopy (PR zip or CSV) if available."""
    target_file = get_bhavcopy_path(d, "NSE")
    if target_file.exists():
        return target_file

    date_str = d.strftime("%d%b%Y").upper()
    month_str = d.strftime("%b").upper()
    year_str = d.strftime("%Y")

    url = f"https://archives.nseindia.com/content/historical/EQUITIES/{year_str}/{month_str}/cm{date_str}bhav.csv.zip"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    }

    try:
        response = requests.get(url, headers=headers, timeout=15)
        if response.status_code == 200:
            import zipfile
            import io
            with zipfile.ZipFile(io.BytesIO(response.content)) as z:
                z.extractall(DATA_DIR)
            extracted_name = DATA_DIR / f"cm{date_str}bhav.csv"
            if extracted_name.exists():
                extracted_name.rename(target_file)
                logger.info(f"Downloaded Bhavcopy to {target_file}")
                return target_file
    except Exception as e:
        logger.debug(f"NSE Bhavcopy download attempt for {d} failed: {e}")

    return None
