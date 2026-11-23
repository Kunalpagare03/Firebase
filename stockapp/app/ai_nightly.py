from typing import List, Dict, Any
from stockapp.app.ai_client import call_ai_agent
from stockapp.app.log import logger

def review_nightly_finalists(picks: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """
    Prompt 15: AI Finalist Reviewer.
    Inspects nightly shortlisted stocks for news, red flags, auditor resignations,
    or upcoming corporate events.
    """
    reviewed = []
    sys_prompt = "You are a forensic quantitative risk analyst. Review the stock finalist for governance, news or earnings red flags."

    for p in picks:
        sym = p["symbol"]
        user_prompt = f"Review symbol: {sym}, Horizon: {p['horizon']}, Setup: {p['setup_code']}. Are there any dealbreakers?"
        mock = f"Vetting {sym}: Quality score confirmed. Trend structure intact. No auditor red flags or high promoter pledge. OK to enter."
        res = call_ai_agent("NIGHTLY_FINALIST_VETTING", sym, sys_prompt, user_prompt, mock_response=mock)
        p_copy = p.copy()
        p_copy["ai_review"] = res["notes"]
        reviewed.append(p_copy)
    return reviewed
