import os
import json
from pathlib import Path

scanner_path = Path(r"c:\Users\Kunal\Desktop\antigrvity\stock_alert_bot\stock\stock_scanner.py")
with open(scanner_path, "r", encoding="utf-8") as f:
    code = f.read()

func_def = """

def load_sector_stocks_universe():
    json_path = ROOT / "data" / "stock_universe_5200.json"
    if json_path.exists():
        try:
            with open(json_path, "r") as f:
                data = json.load(f)
                symbols = [item["NSE_Ticker"] for item in data if "NSE_Ticker" in item]
                if symbols:
                    return symbols
        except Exception:
            pass
    return FALLBACK_SYMBOLS
"""

if "load_sector_stocks_universe" not in code:
    code = code.replace("MAX_CANDIDATES = 60", "MAX_CANDIDATES = 60" + func_def)

code = code.replace("FALLBACK_SYMBOLS", "load_sector_stocks_universe()")

with open(scanner_path, "w", encoding="utf-8") as f:
    f.write(code)

print("Updated stock_scanner.py successfully to use 5,209 stock universe!")
