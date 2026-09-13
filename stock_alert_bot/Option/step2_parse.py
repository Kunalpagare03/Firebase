import pandas as pd

def parse_option_chain(raw_json, expiry=None):
    """
    Parse NSE or normalized Dhan option-chain JSON into one row per strike.

    If an expiry is supplied, rows from other expiries are ignored. This is
    important for NSE responses that contain several expiries in one payload.
    """
    rows = []
    if "records" in raw_json:
        records = raw_json["records"]
        underlying = records.get("underlyingValue")
        entries = records.get("data", [])
        default_expiry = None
    else:
        underlying = raw_json.get("spot_price", raw_json.get("underlyingValue"))
        data_section = raw_json.get("data", {})
        default_expiry = raw_json.get("expiry")

        if isinstance(data_section, dict):
            underlying = underlying or data_section.get("last_price") or data_section.get("spot_price")
            oc_entries = data_section.get("oc", data_section.get("data", []))
            if isinstance(oc_entries, dict):
                entries = []
                for strike_key, legs in oc_entries.items():
                    if not isinstance(legs, dict):
                        continue
                    normalized_entry = {"strike_price": strike_key}
                    if "expiryDate" in legs:
                        normalized_entry["expiryDate"] = legs["expiryDate"]
                    ce = legs.get("ce", legs.get("CALL", {}))
                    pe = legs.get("pe", legs.get("PUT", {}))
                    normalized_entry["CE"] = ce
                    normalized_entry["PE"] = pe
                    entries.append(normalized_entry)
                default_expiry = default_expiry or data_section.get("expiry")
            else:
                entries = oc_entries
        else:
            entries = data_section

    for entry in entries:
        entry_expiry = entry.get("expiryDate", entry.get("expiry", default_expiry))
        strike = entry.get("strikePrice", entry.get("strike_price"))
        ce = entry.get("CE", entry.get("CALL", {}))
        pe = entry.get("PE", entry.get("PUT", {}))
        entry_expiry = entry_expiry or ce.get("expiryDate") or pe.get("expiryDate")
        if expiry is not None and entry_expiry != expiry:
            continue
        if strike is None:
            continue

        rows.append({
            "strike": int(strike),
            "expiry": entry_expiry,
            "call_oi": ce.get("openInterest", ce.get("oi", 0)) or 0,
            "call_oi_change": ce.get("changeinOpenInterest", ce.get("oi_change", 0)) or 0,
            "call_volume": ce.get("totalTradedVolume", ce.get("volume", 0)) or 0,
            "call_iv": ce.get("impliedVolatility", ce.get("iv", 0)) or 0,
            "call_ltp": ce.get("lastPrice", ce.get("ltp", 0)) or 0,
            "put_oi": pe.get("openInterest", pe.get("oi", 0)) or 0,
            "put_oi_change": pe.get("changeinOpenInterest", pe.get("oi_change", 0)) or 0,
            "put_volume": pe.get("totalTradedVolume", pe.get("volume", 0)) or 0,
            "put_iv": pe.get("impliedVolatility", pe.get("iv", 0)) or 0,
            "put_ltp": pe.get("lastPrice", pe.get("ltp", 0)) or 0,
        })

    if not rows:
        requested = f" for expiry {expiry}" if expiry is not None else ""
        raise ValueError(f"Option-chain response contains no usable strikes{requested}")

    df = pd.DataFrame(rows).sort_values("strike").reset_index(drop=True)
    return df, underlying


def load_mock_nse_response():
    """
    Mimics the real structure NSE returns, so we can test parsing
    logic without hitting the live API (which this sandbox can't reach).
    Replace this with a real requests.get(...).json() call on your machine.
    """
    mock = {
        "records": {
            "underlyingValue": 24850.35,
            "data": [
                {"strikePrice": 24600,
                 "CE": {"openInterest": 12500, "changeinOpenInterest": 800, "totalTradedVolume": 21000, "impliedVolatility": 13.2, "lastPrice": 305.5},
                 "PE": {"openInterest": 45200, "changeinOpenInterest": 6100, "totalTradedVolume": 38000, "impliedVolatility": 14.1, "lastPrice": 55.2}},
                {"strikePrice": 24700,
                 "CE": {"openInterest": 18700, "changeinOpenInterest": 1200, "totalTradedVolume": 26500, "impliedVolatility": 13.0, "lastPrice": 220.1},
                 "PE": {"openInterest": 38900, "changeinOpenInterest": 4200, "totalTradedVolume": 31000, "impliedVolatility": 13.8, "lastPrice": 80.4}},
                {"strikePrice": 24800,
                 "CE": {"openInterest": 26100, "changeinOpenInterest": -1500, "totalTradedVolume": 41000, "impliedVolatility": 12.7, "lastPrice": 140.3},
                 "PE": {"openInterest": 29800, "changeinOpenInterest": 2100, "totalTradedVolume": 27000, "impliedVolatility": 13.4, "lastPrice": 120.6}},
                {"strikePrice": 24900,
                 "CE": {"openInterest": 41500, "changeinOpenInterest": 5300, "totalTradedVolume": 52000, "impliedVolatility": 12.5, "lastPrice": 78.9},
                 "PE": {"openInterest": 21000, "changeinOpenInterest": -900, "totalTradedVolume": 19500, "impliedVolatility": 13.1, "lastPrice": 175.2}},
                {"strikePrice": 25000,
                 "CE": {"openInterest": 58200, "changeinOpenInterest": 9100, "totalTradedVolume": 71000, "impliedVolatility": 12.3, "lastPrice": 38.4},
                 "PE": {"openInterest": 15600, "changeinOpenInterest": -2100, "totalTradedVolume": 14000, "impliedVolatility": 12.9, "lastPrice": 240.7}},
            ]
        }
    }
    return mock


if __name__ == "__main__":
    raw = load_mock_nse_response()   # on your machine: raw = get_option_chain("NIFTY")
    df, spot = parse_option_chain(raw)

    print(f"Underlying spot price: {spot}\n")
    print(df.to_string(index=False))
