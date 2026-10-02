# Stock Scanner

This folder is independent from `Option`. It runs after market close against
the two configured Chartink screeners and enriches candidates with Yahoo daily
price and volume history.

Run manually:

```powershell
cd stock
..\.venv\Scripts\python.exe stock_scanner.py
```

Reports are written to `stock/reports/latest.json` and `latest.csv`.

The score combines trend, SMA20/SMA50 position, RSI, volume expansion, recent
highs, ATR risk, and the latest candle. `target_2pct` is a planning objective,
not a return guarantee. Never treat the output as a promise or an automatic
order instruction.