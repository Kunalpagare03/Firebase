"""Black-76 options pricing, IV solver, Greeks, and Expected Move for Option App v8 (Part C3)."""

import math
from typing import Dict, Optional, Tuple
from scipy.stats import norm


def put_call_parity_forward(strike: float, call_price: float, put_price: float, r: float = 0.06, t_years: float = 0.001) -> float:
    """
    Synthetic Forward calculation via Put-Call Parity:
    F = K + e^(rT) * (C - P)
    """
    discount = math.exp(r * t_years)
    return strike + discount * (call_price - put_price)


def black76_price(is_call: bool, forward: float, strike: float, t_years: float, sigma: float, r: float = 0.06) -> float:
    """
    Black-76 model for European options on futures/synthetic forward.
    """
    if t_years <= 0 or sigma <= 0:
        return max(0.0, (forward - strike) if is_call else (strike - forward))

    df = math.exp(-r * t_years)
    sqrt_t = math.sqrt(t_years)
    d1 = (math.log(forward / strike) + 0.5 * sigma * sigma * t_years) / (sigma * sqrt_t)
    d2 = d1 - sigma * sqrt_t

    if is_call:
        return df * (forward * norm.cdf(d1) - strike * norm.cdf(d2))
    else:
        return df * (strike * norm.cdf(-d2) - forward * norm.cdf(-d1))


def black76_iv(is_call: bool, target_price: float, forward: float, strike: float, t_years: float, r: float = 0.06) -> float:
    """
    Solve for Implied Volatility (sigma) using bisection.
    Returns annual IV (e.g. 0.15 for 15%).
    """
    intrinsic = max(0.0, (forward - strike) if is_call else (strike - forward))
    if target_price <= intrinsic or t_years <= 1e-5:
        return 0.0

    low = 0.01
    high = 3.50
    tolerance = 1e-4

    for _ in range(60):
        mid = (low + high) / 2.0
        price = black76_price(is_call, forward, strike, t_years, mid, r)
        diff = price - target_price
        if abs(diff) < tolerance:
            return mid
        if diff > 0:
            high = mid
        else:
            low = mid

    return (low + high) / 2.0


def black76_greeks(is_call: bool, forward: float, strike: float, t_years: float, sigma: float, r: float = 0.06) -> Dict[str, float]:
    """
    Calculate Greeks: Delta, Gamma, Theta (per day), Vega (per 1% IV).
    """
    if t_years <= 1e-5 or sigma <= 1e-4:
        delta = 1.0 if is_call and forward > strike else (0.0 if is_call else (-1.0 if forward < strike else 0.0))
        return {"delta": delta, "gamma": 0.0, "theta": 0.0, "vega": 0.0}

    df = math.exp(-r * t_years)
    sqrt_t = math.sqrt(t_years)
    d1 = (math.log(forward / strike) + 0.5 * sigma * sigma * t_years) / (sigma * sqrt_t)
    d2 = d1 - sigma * sqrt_t
    pdf_d1 = norm.pdf(d1)

    if is_call:
        delta = df * norm.cdf(d1)
    else:
        delta = -df * norm.cdf(-d1)

    gamma = (df * pdf_d1) / (forward * sigma * sqrt_t)

    # Vega per 1% change in vol
    vega = (forward * df * sqrt_t * pdf_d1) * 0.01

    # Theta per day (365 days convention)
    term1 = -(forward * df * pdf_d1 * sigma) / (2.0 * sqrt_t)
    if is_call:
        theta_annual = term1 - r * strike * df * norm.cdf(d2)
    else:
        theta_annual = term1 + r * strike * df * norm.cdf(-d2)
    theta_day = theta_annual / 365.0

    return {
        "delta": delta,
        "gamma": gamma,
        "theta": theta_day,
        "vega": vega,
    }


def compute_expected_move(atm_straddle_price: float = 0.0, spot: float = 0.0, iv_annual: float = 0.0, t_days: float = 1.0) -> float:
    """
    Calculate Expected Move:
    Method 1 (Rule of thumb): 0.85 * (ATM Call + ATM Put)
    Method 2: Spot * IV * sqrt(t_days / 365)
    """
    if atm_straddle_price > 0:
        return 0.85 * atm_straddle_price
    elif spot > 0 and iv_annual > 0:
        return spot * iv_annual * math.sqrt(t_days / 365.0)
    return 0.0


def check_hero_zero_discount(ask_price: float, fair_price: float, max_ratio: float = 0.70) -> Tuple[bool, float]:
    """
    Check if option ask price is at least a 30% discount to fair price (ask <= 70% of fair).
    Returns (is_discounted, ratio).
    """
    if fair_price <= 0:
        return False, 1.0
    ratio = ask_price / fair_price
    return ratio <= max_ratio, ratio
