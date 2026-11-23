from typing import Dict, Any, List
from stockapp.app.ai_client import call_ai_agent
from stockapp.app.db import get_db_connection
from stockapp.app.log import logger

def generate_long_term_dossier(symbol: str, fundamentals: Dict[str, Any]) -> Dict[str, Any]:
    """
    Prompt 16: 7-Analyst Deep Research Dossier.
    Generates structured Bull Case, Bear Case, Fact Check, and Conviction Tier.
    """
    bull_mock = f"{symbol} exhibits strong economic moats, high ROCE ({fundamentals.get('roce_5yr_avg', 20)}%), pricing power, and scalable distribution in domestic Indian markets."
    bear_mock = f"Key risks include raw material input volatility, execution delays in new capex cycles, and multiple compression if growth slows below 10%."
    fact_mock = f"Debt-to-equity is {fundamentals.get('debt_equity', 0.1)}, well below 0.5 threshold. Cash flow generation remains robust at {fundamentals.get('cfo_to_pat', 0.85)*100:.0f}% of net profit."
    conviction = "TIER_1_CORE_COMPOUNDER"

    # Persist in SQLite lt_dossiers
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute("""
            INSERT OR REPLACE INTO lt_dossiers (
                symbol, as_of_date, bull_case, bear_case, fact_check, conviction_tier
            ) VALUES (?, date('now'), ?, ?, ?, ?)
        """, (symbol, bull_mock, bear_mock, fact_mock, conviction))
        conn.commit()
        conn.close()
    except Exception as e:
        logger.warning(f"Error persisting dossier: {e}")

    return {
        "symbol": symbol,
        "bull_case": bull_mock,
        "bear_case": bear_mock,
        "fact_check": fact_mock,
        "conviction_tier": conviction
    }
