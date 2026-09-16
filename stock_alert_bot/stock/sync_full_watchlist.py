import pandas as pd
import json
import os
import requests
from io import StringIO

def sync():
    print("Fetching master symbol lists (NSE + BSE)...")
    nse_url = "https://archives.nseindia.com/content/equities/EQUITY_L.csv"
    bse_url = "https://www.bseindia.com/downloads/Help/file/scrip.csv" # BSE is trickier to get directly

    headers = {"User-Agent": "Mozilla/5.0"}

    try:
        r_nse = requests.get(nse_url, headers=headers)
        nse_df = pd.read_csv(StringIO(r_nse.text))
    except Exception as e:
        print(f"Error fetching NSE list: {e}")
        return

    def clean(val):
        return "".join(e for e in str(val).upper() if e.isalnum())

    # Map NSE: Name -> Symbol
    nse_mapping = dict(zip(nse_df['NAME OF COMPANY'].apply(clean), nse_df['SYMBOL']))
    nse_sym_map = dict(zip(nse_df['SYMBOL'].apply(clean), nse_df['SYMBOL']))

    print(f"Loaded {len(nse_mapping)} NSE stocks.")

    # Read User Excel
    excel_path = r"C:\Users\Kunal\Desktop\stocks list sector wise.xlsx"
    user_df = pd.read_excel(excel_path)

    new_sector_map = {}
    found_count = 0
    total_requested = 0

    for col in user_df.columns:
        sector_name = col.replace(" stocks", "").upper()
        companies = user_df[col].dropna().unique()
        total_requested += len(companies)
        symbols = []

        for company in companies:
            cl = clean(company)
            # Match 1: Name match in NSE
            if cl in nse_mapping:
                symbols.append(f"{nse_mapping[cl]}.NS")
                found_count += 1
            # Match 2: Symbol match in NSE (in case user put symbol as name)
            elif cl in nse_sym_map:
                symbols.append(f"{nse_sym_map[cl]}.NS")
                found_count += 1
            else:
                # Match 3: Partial name match
                matched = False
                for nse_name, sym in nse_mapping.items():
                    if cl in nse_name or nse_name in cl:
                        symbols.append(f"{sym}.NS")
                        found_count += 1
                        matched = True
                        break

                if not matched:
                    # If it's a numeric code, it might be a BSE symbol
                    if cl.isdigit() and len(cl) == 6:
                        symbols.append(f"{cl}.BO")
                        found_count += 1

        if symbols:
            new_sector_map[sector_name] = sorted(list(set(symbols)))

    print(f"Targeting {total_requested} stocks from Excel.")
    print(f"Successfully mapped {found_count} stocks to live symbols.")

    # Update sector_map.py
    map_path = os.path.join(os.path.dirname(__file__), "utils", "sector_map.py")
    with open(map_path, "w") as f:
        f.write("# Updated Full Sector Map\n")
        f.write("SECTOR_MAP = " + json.dumps(new_sector_map, indent=4) + "\n\n")
        f.write("def get_sector(symbol):\n")
        f.write("    for sector, symbols in SECTOR_MAP.items():\n")
        f.write("        if symbol in symbols: return sector\n")
        f.write("    return 'OTHERS'\n")

if __name__ == "__main__":
    sync()
