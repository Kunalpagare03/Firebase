"""Calendar, market hours, holiday, expiry, and strike calculations for Indian markets."""

from datetime import date, datetime, time, timedelta
from typing import List, Optional
import pytz

IST = pytz.timezone("Asia/Kolkata")

# NSE/BSE Trading Holidays (2026 reference)
HOLIDAYS_2026 = {
    date(2026, 1, 26),  # Republic Day
    date(2026, 3, 6),   # Mahashivratri
    date(2026, 3, 25),  # Holi
    date(2026, 4, 3),   # Good Friday
    date(2026, 4, 14),  # Dr. Ambedkar Jayanti
    date(2026, 5, 1),   # Maharashtra Day
    date(2026, 6, 17),  # Bakri Eid
    date(2026, 8, 15),  # Independence Day
    date(2026, 10, 2),  # Gandhi Jayanti
    date(2026, 10, 20), # Dussehra
    date(2026, 11, 8),  # Diwali
    date(2026, 12, 25), # Christmas
}

MARKET_OPEN = time(9, 15)
MARKET_CLOSE = time(15, 30)

STRIKE_STEPS = {
    "NIFTY": 50,
    "BANKNIFTY": 100,
    "FINNIFTY": 50,
    "MIDCPNIFTY": 25,
    "SENSEX": 100,
}

LOT_SIZES = {
    "NIFTY": 65,
    "BANKNIFTY": 30,
    "FINNIFTY": 60,
    "MIDCPNIFTY": 120,
    "SENSEX": 20,
}


def now_ist() -> datetime:
    return datetime.now(IST)


def is_trading_day(d: Optional[date] = None) -> bool:
    if d is None:
        d = now_ist().date()
    # Monday=0, Sunday=6
    if d.weekday() >= 5:
        return False
    if d in HOLIDAYS_2026:
        return False
    return True


def is_market_open(dt: Optional[datetime] = None) -> bool:
    if dt is None:
        dt = now_ist()
    if not is_trading_day(dt.date()):
        return False
    t = dt.time()
    return MARKET_OPEN <= t <= MARKET_CLOSE


def get_expiry_weekday(symbol: str) -> int:
    """Return Python weekday (0=Mon, 1=Tue, 2=Wed, 3=Thu, 4=Fri)."""
    s = symbol.upper()
    if s == "NIFTY":
        return 1  # Tuesday
    elif s == "SENSEX":
        return 3  # Thursday
    elif s == "BANKNIFTY":
        return 3  # Thursday (monthly)
    elif s == "FINNIFTY":
        return 1  # Tuesday
    return 3


def round_to_strike(price: float, step: int) -> int:
    """Round spot to nearest valid strike step."""
    return int(round(price / step) * step)


def get_strike_step(symbol: str) -> int:
    return STRIKE_STEPS.get(symbol.upper(), 50)


def get_lot_size(symbol: str) -> int:
    return LOT_SIZES.get(symbol.upper(), 50)


def calculate_condor_strikes(spot: float, symbol: str, inner_pct: float = 0.005, wing_pct: float = 0.015):
    """
    Calculate Iron Condor strikes:
    - Short Put: spot * (1 - inner_pct) rounded down to strike step
    - Long Put: short_put - (spot * wing_pct) rounded to strike step
    - Short Call: spot * (1 + inner_pct) rounded up to strike step
    - Long Call: short_call + (spot * wing_pct) rounded to strike step
    """
    step = get_strike_step(symbol)
    short_put = int(round((spot * (1.0 - inner_pct)) / step) * step)
    long_put = int(round((short_put - spot * wing_pct) / step) * step)
    
    short_call = int(round((spot * (1.0 + inner_pct)) / step) * step)
    long_call = int(round((short_call + spot * wing_pct) / step) * step)

    # Ensure strictly separated
    if short_put >= short_call:
        short_put = round_to_strike(spot - step, step)
        short_call = round_to_strike(spot + step, step)
    if long_put >= short_put:
        long_put = short_put - step
    if long_call <= short_call:
        long_call = short_call + step

    return {
        "long_put": long_put,
        "short_put": short_put,
        "short_call": short_call,
        "long_call": long_call,
    }


def calculate_ironfly_strikes(spot: float, symbol: str, wing_pct: float = 0.015):
    """
    Calculate Iron Fly strikes:
    - ATM Short Put and Short Call at round_to_strike(spot)
    - Long Put: ATM - spot * wing_pct
    - Long Call: ATM + spot * wing_pct
    """
    step = get_strike_step(symbol)
    atm = round_to_strike(spot, step)
    wing_distance = max(step, int(round((spot * wing_pct) / step) * step))
    long_put = atm - wing_distance
    long_call = atm + wing_distance
    return {
        "long_put": long_put,
        "short_put": atm,
        "short_call": atm,
        "long_call": long_call,
    }
