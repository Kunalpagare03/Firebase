import pandas as pd
import json
import os
import requests
from io import StringIO
import re

def clean_name(name):
    name = str(name).upper()
    # Remove common corporate suffixes
    name = re.sub(r' LIMITED| LTD| CORP| CORPORATION| INDUSTRIES| INDS| TRDG| TRADING| SERVICE| SERVICES| TECHNOLOGIES| TECH| INFRASTRUCTURE| INFRA', '', name)
    return "".join(e for e in name if e.isalnum())

def get_words(name):
    return set(re.findall(r'\w+', str(name).upper()))

def sync():
    print("Fetching master instrument list (Kite)...")
    url = "https://api.kite.trade/instruments"
    headers = {"User-Agent": "Mozilla/5.0"}

    try:
        response = requests.get(url, headers=headers)
        response.raise_for_status()
        master_df = pd.read_csv(StringIO(response.text))
    except Exception as e:
        print(f"Error fetching Kite list: {e}")
        return

    # Filter for Cash Equities on NSE and BSE
    eq_df = master_df[(master_df['instrument_type'] == 'EQ') & (master_df['segment'] != 'INDICES')]

    # Create mapping: Cleaned Name -> [ (symbol, exchange) ]
    master_data = []
    for _, row in eq_df.iterrows():
        name = str(row['name']).upper()
        symbol = str(row['tradingsymbol']).upper()
        exch = str(row['exchange']).upper()

        master_data.append({
            'name': name,
            'clean': clean_name(name),
            'words': get_words(name),
            'sym': symbol,
            'exch': exch
        })

    print(f"Loaded {len(master_data)} tradeable equities from Kite.")

    # Read User Excel
    excel_path = r"C:\Users\Kunal\Desktop\stocks list sector wise.xlsx"
    user_df = pd.read_excel(excel_path)

    new_sector_map = {}
    found_symbols = set()
    total_requested = 0

    for col in user_df.columns:
        sector_name = col.replace(" stocks", "").upper()
        companies = user_df[col].dropna().unique()
        total_requested += len(companies)
        sector_symbols = []

        for company in companies:
            cl_comp = clean_name(company)
            words_comp = get_words(company)

            matched_full_sym = None

            # Match 1: Clean Name match
            for item in master_data:
                if cl_comp == item['clean']:
                    matched_full_sym = f"{item['sym']}.{'NS' if item['exch'] == 'NSE' else 'BO'}"
                    break

            # Match 2: Partial/Word overlap
            if not matched_full_sym:
                for item in master_data:
                    overlap = words_comp.intersection(item['words'])
                    if len(overlap) >= 2 or (len(words_comp) == 1 and list(words_comp)[0] in item['words']):
                        common = {'THE', 'AND', 'INDIA', 'LIMITED', 'LTD'}
                        meaningful = overlap - common
                        if len(meaningful) >= 1:
                            matched_full_sym = f"{item['sym']}.{'NS' if item['exch'] == 'NSE' else 'BO'}"
                            break

            if matched_full_sym:
                sector_symbols.append(matched_full_sym)
                found_symbols.add(matched_full_sym)

        if sector_symbols:
            new_sector_map[sector_name] = sorted(list(set(sector_symbols)))

    print(f"Targeting {total_requested} unique entries from Excel.")
    print(f"Successfully mapped {len(found_symbols)} unique symbols (NSE + BSE).")

    # Update sector_map.py
    map_path = os.path.join(os.path.dirname(__file__), "utils", "sector_map.py")
    with open(map_path, "w") as f:
        f.write("# Auto-generated Multi-Exchange Sector Map\n")
        f.write("SECTOR_MAP = " + json.dumps(new_sector_map, indent=4) + "\n\n")
        f.write("def get_sector(symbol):\n")
        f.write("    for sector, symbols in SECTOR_MAP.items():\n")
        f.write("        if symbol in symbols: return sector\n")
        f.write("    return 'OTHERS'\n")

if __name__ == "__main__":
    sync()
