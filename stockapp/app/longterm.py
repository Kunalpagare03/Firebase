import math
from datetime import datetime
from typing import Dict, Any, List, Tuple
import pandas as pd
from stockapp.app.settings import settings
from stockapp.app.db import get_db_connection
from stockapp.app.data_screener import fetch_screener_fundamentals, LONG_TERM_UNIVERSE
from stockapp.app.data_prices import fetch_price_data
from stockapp.app.indicators import calculate_all_indicators
from stockapp.app.log import logger

def evaluate_long_term_candidates(offline_mode: bool = False) -> List[Dict[str, Any]]:
    """
    Evaluates Long-Term Compounders against the 6-rule Quality Gate:
    1. ROCE 5-yr avg >= 15%
    2. ROCE min single year >= 10%
    3. 3-yr Sales Growth >= 10%
    4. 3-yr Profit Growth >= 10%
    5. Debt / Equity <= 0.5
    6. CFO / PAT >= 0.70
    
    Scores on 100-point scale:
    - Business Quality: 30 pts
    - Growth: 25 pts
    - Financial Strength: 15 pts
    - Valuation: 15 pts
    - Trend: 15 pts
    """
    fundamentals = fetch_screener_fundamentals(offline_mode=offline_mode)
    today_str = datetime.now().strftime("%Y-%m-%d")
    results = []

    conn = get_db_connection()
    cursor = conn.cursor()

    for sym, fund in fundamentals.items():
        roce_avg = float(fund.get("roce_5yr_avg", 0.0))
        roce_min = float(fund.get("roce_min", 0.0))
        sales_growth = float(fund.get("sales_growth_3yr", 0.0))
        profit_growth = float(fund.get("profit_growth_3yr", 0.0))
        de_ratio = float(fund.get("debt_equity", 1.0))
        cfo_pat = float(fund.get("cfo_to_pat", 0.0))

        # Check Quality Gate
        gate_passed = (
            roce_avg >= settings.long_term.min_roce_5yr_avg and
            roce_min >= settings.long_term.min_roce_single_year and
            sales_growth >= settings.long_term.min_sales_growth_3yr and
            profit_growth >= settings.long_term.min_profit_growth_3yr and
            de_ratio <= settings.long_term.max_debt_to_equity and
            cfo_pat >= settings.long_term.min_cfo_to_pat
        )

        # Technical check (Trend)
        price_df = fetch_price_data(sym, offline_mode=offline_mode)
        curr_price = 1000.0
        above_sma200 = True
        rsi14 = 50.0

        if not price_df.empty and len(price_df) >= 50:
            df_ind = calculate_all_indicators(price_df)
            last = df_ind.iloc[-1]
            curr_price = float(last["Close"])
            sma200 = float(last["SMA200"]) if not pd.isna(last["SMA200"]) else (curr_price * 0.9)
            above_sma200 = curr_price > sma200
            rsi14 = float(last["RSI14"]) if not pd.isna(last["RSI14"]) else 50.0

        # Score Breakdown (Max 100)
        # Quality (30 pts)
        q_score = min(30.0, (roce_avg / 25.0) * 30.0)
        # Growth (25 pts)
        g_score = min(25.0, (sales_growth / 20.0) * 12.5 + (profit_growth / 20.0) * 12.5)
        # Strength (15 pts)
        s_score = 15.0 if de_ratio <= 0.2 else (10.0 if de_ratio <= 0.5 else 0.0)
        # Valuation (15 pts)
        v_score = 12.0
        # Trend (15 pts)
        t_score = 15.0 if above_sma200 else 5.0

        total_score = round(q_score + g_score + s_score + v_score + t_score, 1)

        # Tranche Accumulation Status
        tranche_timing = "BUY TRANCHE 1" if above_sma200 and rsi14 < 55 else "WAIT FOR DIP"

        candidate = {
            "symbol": sym,
            "as_of_date": today_str,
            "roce_5yr_avg": roce_avg,
            "roce_min": roce_min,
            "sales_growth_3yr": sales_growth,
            "profit_growth_3yr": profit_growth,
            "debt_equity": de_ratio,
            "cfo_to_pat": cfo_pat,
            "quality_pass": 1 if gate_passed else 0,
            "score_total": total_score,
            "price": curr_price,
            "tranche_timing": tranche_timing
        }
        results.append(candidate)

        # Persist to lt_metrics
        cursor.execute("""
            INSERT OR REPLACE INTO lt_metrics (
                symbol, as_of_date, roce_5yr_avg, roce_min, sales_growth_3yr,
                profit_growth_3yr, debt_equity, cfo_to_pat, score_total, quality_pass
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            sym, today_str, roce_avg, roce_min, sales_growth, profit_growth,
            de_ratio, cfo_pat, total_score, 1 if gate_passed else 0
        ))

    conn.commit()
    conn.close()
    logger.info(f"Evaluated {len(results)} long-term compounders. Qualified: {sum(r['quality_pass'] for r in results)}")
    return results
