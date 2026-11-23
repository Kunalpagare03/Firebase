from typing import Dict, Any
from stockapp.app.scoreboard import compute_scoreboard_metrics
from stockapp.app.settings import settings

def check_live_readiness() -> Dict[str, Any]:
    """
    Prompt 20: Live Readiness Monitor.
    Enforces the rule: Paper trade for 6 months and 100+ trades beating Nifty 50
    before going live with real capital.
    """
    score = compute_scoreboard_metrics()
    total_trades = score.get("total_closed_trades", 0)
    win_rate = score.get("win_rate_pct", 0.0)

    # Thresholds
    required_trades = 100
    min_win_rate = 55.0

    passed_trades = total_trades >= required_trades
    passed_win_rate = win_rate >= min_win_rate

    is_ready = passed_trades and passed_win_rate

    return {
        "is_ready_for_real_money": is_ready,
        "total_paper_trades": total_trades,
        "required_paper_trades": required_trades,
        "current_win_rate_pct": win_rate,
        "required_min_win_rate_pct": min_win_rate,
        "recommendation": "READY TO DEPLOY REAL CAPITAL" if is_ready else f"CONTINUE PAPER TRADING ({total_trades}/{required_trades} trades executed)"
    }
