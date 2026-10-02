from datetime import datetime, time
from zoneinfo import ZoneInfo


IST = ZoneInfo("Asia/Kolkata")
MARKET_OPEN = time(9, 15)
MARKET_CLOSE = time(15, 30)


def market_status(now=None):
    current = now or datetime.now(IST)
    if current.tzinfo is None:
        current = current.replace(tzinfo=IST)
    else:
        current = current.astimezone(IST)

    is_open = current.weekday() < 5 and MARKET_OPEN <= current.time() < MARKET_CLOSE
    if current.weekday() >= 5:
        reason = "weekend"
    elif current.time() < MARKET_OPEN:
        reason = "before market open"
    elif current.time() >= MARKET_CLOSE:
        reason = "after market close"
    else:
        reason = "market open"
    return {
        "is_open": is_open,
        "reason": reason,
        "timezone": "Asia/Kolkata",
        "local_time": current.strftime("%Y-%m-%d %H:%M:%S"),
    }