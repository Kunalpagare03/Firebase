"""Transaction costs, statutory levies, taxes, and slippage calculations (Part C3 & F2)."""

from typing import Dict, List


def calculate_trade_costs(
    exchange: str,
    action: str,  # BUY or SELL
    price: float,
    qty: int,
    slippage_points: float = 1.5,
    brokerage_per_order: float = 20.0,
) -> Dict[str, float]:
    """
    Calculate statutory taxes, charges, and slippage for an option leg execution.
    """
    exchange = exchange.upper()
    action = action.upper()
    premium_turnover = price * qty

    # 1. Slippage cost
    slippage_cost = slippage_points * qty

    # 2. Brokerage
    brokerage = brokerage_per_order

    # 3. STT (Securities Transaction Tax) - 0.1% on SELL turnover for options
    stt = (0.001 * premium_turnover) if action == "SELL" else 0.0

    # 4. Exchange Transaction Charges
    # BSE: 0.05%, NSE: 0.035%
    txn_rate = 0.0005 if exchange == "BSE" else 0.00035
    exchange_charges = txn_rate * premium_turnover

    # 5. SEBI Turnover Charges (Rs 10 per crore = 0.000001)
    sebi_charges = 0.000001 * premium_turnover

    # 6. Stamp Duty - 0.003% on BUY turnover
    stamp_duty = (0.00003 * premium_turnover) if action == "BUY" else 0.0

    # 7. GST - 18% on (Brokerage + Exchange Charges + SEBI Charges)
    gst = 0.18 * (brokerage + exchange_charges + sebi_charges)

    total_statutory = stt + exchange_charges + sebi_charges + stamp_duty + gst
    total_charges = brokerage + total_statutory + slippage_cost

    return {
        "turnover": premium_turnover,
        "brokerage": brokerage,
        "stt": stt,
        "exchange_charges": exchange_charges,
        "sebi_charges": sebi_charges,
        "stamp_duty": stamp_duty,
        "gst": gst,
        "slippage_cost": slippage_cost,
        "total_statutory": total_statutory,
        "total_charges": total_charges,
    }


def calculate_structure_costs(legs: List[Dict[str, any]], exchange: str = "NSE", slippage_points: float = 1.5) -> float:
    """Calculate total execution costs across all legs of a multi-leg structure (e.g. Iron Condor)."""
    total = 0.0
    for leg in legs:
        cost_info = calculate_trade_costs(
            exchange=exchange,
            action=leg.get("action", "SELL"),
            price=leg.get("price", 0.0),
            qty=leg.get("qty", 1),
            slippage_points=slippage_points,
        )
        total += cost_info["total_charges"]
    return total
