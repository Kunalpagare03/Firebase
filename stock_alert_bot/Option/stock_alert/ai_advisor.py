import os
import json
import sqlite3
import requests
from typing import Dict, Any, Optional

DEFAULT_GATEWAY_URL = os.environ.get("FREELLMAPI_URL", "http://127.0.0.1:3001/v1")

def get_unified_api_key() -> str:
    env_key = os.environ.get("FREELLMAPI_KEY")
    if env_key:
        return env_key

    base_dir = os.path.dirname(os.path.abspath(__file__))
    possible_paths = [
        r"C:\Users\Kunal\Desktop\antigrvity\freellmapi\server\data\freeapi.db",
        os.path.abspath(os.path.join(base_dir, "..", "..", "..", "freellmapi", "server", "data", "freeapi.db")),
        os.path.abspath(os.path.join(base_dir, "..", "freellmapi", "server", "data", "freeapi.db")),
        os.path.expanduser("~/freellmapi/server/data/freeapi.db")
    ]
    for p in possible_paths:
        if os.path.exists(p):
            try:
                conn = sqlite3.connect(p)
                cur = conn.cursor()
                cur.execute("SELECT value FROM settings WHERE key='unified_api_key';")
                row = cur.fetchone()
                conn.close()
                if row and row[0]:
                    return row[0]
            except Exception:
                pass
    return "freellmapi-unified-key"

def ask_freellm_advisor(prompt: str, system_prompt: Optional[str] = None, model: str = "auto") -> Dict[str, Any]:
    api_key = get_unified_api_key()
    url = f"{DEFAULT_GATEWAY_URL.rstrip('/')}/chat/completions"

    messages = []
    if system_prompt:
        messages.append({"role": "system", "content": system_prompt})
    messages.append({"role": "user", "content": prompt})

    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {api_key}"
    }

    payload = {
        "model": model,
        "messages": messages,
        "temperature": 0.3,
        "max_tokens": 400
    }

    try:
        resp = requests.post(url, json=payload, headers=headers, timeout=25)
        if resp.status_code == 200:
            data = resp.json()
            content = data.get("choices", [{}])[0].get("message", {}).get("content", "")
            return {
                "success": True,
                "text": content.strip(),
                "model_used": data.get("model", model),
                "error": None
            }
        else:
            return {
                "success": False,
                "text": None,
                "model_used": None,
                "error": f"Gateway HTTP {resp.status_code}: {resp.text}"
            }
    except requests.exceptions.ConnectionError:
        return {
            "success": False,
            "text": None,
            "model_used": None,
            "error": "FreeLLMAPI gateway is not running. Start it with .\\start_freellmapi.ps1"
        }
    except Exception as e:
        return {
            "success": False,
            "text": None,
            "model_used": None,
            "error": str(e)
        }

def generate_market_ai_narrative(market_snapshot: Dict[str, Any]) -> str:
    system_prompt = (
        "You are an expert Indian stock & options quant analyst for NIFTY & BANKNIFTY. "
        "Analyze the provided live quantitative parameters (PCR, OI Build-up, Max Pain, Support/Resistance). "
        "Provide a crisp 3-sentence intraday executive summary: "
        "1. Overall Market Sentiment & Institutional Bias. "
        "2. Key Battleground Strike / Breakout Trigger. "
        "3. Actionable recommendation (Long Call, Long Put, or Wait)."
    )

    prompt = (
        f"Symbol: {market_snapshot.get('symbol', 'NIFTY')}\n"
        f"Spot Price: {market_snapshot.get('spot', 'N/A')}\n"
        f"PCR (OI): {market_snapshot.get('pcr_oi', 'N/A')}\n"
        f"Volume PCR: {market_snapshot.get('volume_pcr', 'N/A')}\n"
        f"Max Pain: {market_snapshot.get('max_pain', 'N/A')}\n"
        f"Major Call Resistance: {market_snapshot.get('resistance', 'N/A')}\n"
        f"Major Put Support: {market_snapshot.get('support', 'N/A')}\n"
        f"Algorithmic Action: {market_snapshot.get('action', 'WAIT')}\n"
        f"Contract: {market_snapshot.get('recommended_contract', 'N/A')}\n"
        "Provide your professional AI analysis now."
    )

    res = ask_freellm_advisor(prompt, system_prompt=system_prompt)
    if res["success"]:
        return f"[AI Quant Advisor ({res['model_used']})]\n{res['text']}"
    else:
        return (
            f"[Rule-based Summary]\n"
            f"Spot: {market_snapshot.get('spot')} | PCR: {market_snapshot.get('pcr_oi')} | "
            f"Action: {market_snapshot.get('action', 'WAIT')} ({market_snapshot.get('recommended_contract', 'N/A')}).\n"
            f"(AI Status: {res.get('error', 'No response')})\n"
            f"(To enable live multi-provider failover, add your free Gemini/Groq keys at http://localhost:3001/keys)"
        )

if __name__ == "__main__":
    print("Testing AI Advisor integration with FreeLLMAPI...")
    mock_data = {
        "symbol": "NIFTY",
        "spot": 24850.50,
        "pcr_oi": 1.25,
        "volume_pcr": 1.30,
        "max_pain": 24800,
        "support": 24700,
        "resistance": 25000,
        "action": "BUY CALL",
        "recommended_contract": "NIFTY 24850 CE"
    }
    summary = generate_market_ai_narrative(mock_data)
    print("\n--- Output ---")
    print(summary)
