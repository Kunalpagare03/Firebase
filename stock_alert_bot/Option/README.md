# Option Chain Sentiment Toolkit (Nifty / Bank Nifty / Sensex)

A step-by-step Python toolkit that reads live option chain data and estimates
market SENTIMENT and probable support/resistance zones.

live market web based 
python scripts/web_dashboard.py

Generate a mobile QR for the running dashboard (the LAN address is detected automatically):

```powershell
python scripts/generate_mobile_qr.py --start
```

This creates the QR and starts the web dashboard. The phone and computer must be on the same Wi-Fi network. Use `--host` when the computer has multiple network adapters.

### Mobile data access (4G/5G)

The private address `192.168.1.6` works only on the same local Wi-Fi. To use
the dashboard from mobile data, run a public HTTPS tunnel in another terminal
while the dashboard is running:

Cloudflare Tunnel:

```powershell
cloudflared tunnel --url http://localhost:5000
```

Or ngrok:

```powershell
ngrok http 5000
```

Copy the HTTPS URL printed by the tunnel and generate a QR for it:

```powershell
python scripts/generate_mobile_qr.py --url https://your-tunnel-url.example --output mobile_data_qr.png
```

Keep both the dashboard and tunnel terminals running. The temporary tunnel URL
changes when the tunnel restarts. Do not use `127.0.0.1` or `192.168.1.6` on a
mobile network.

### Automatic weekday session

To run automatically Monday-Friday from 09:10 until 15:35, register the Windows task:

```powershell
PowerShell -ExecutionPolicy Bypass -File scripts/register_market_task.ps1
```

For a permanent URL, first create a Cloudflare named tunnel and DNS hostname,
then register the task with the hostname in the user environment:

```powershell
$env:DASHBOARD_TUNNEL_NAME = "stock-dashboard"
$env:DASHBOARD_PUBLIC_HOSTNAME = "dashboard.example.com"
PowerShell -ExecutionPolicy Bypass -File scripts/register_market_task.ps1
```

The permanent hostname requires a domain managed by Cloudflare and a named
tunnel configured with `cloudflared tunnel login`, `cloudflared tunnel create
stock-dashboard`, a tunnel config/ingress for `http://localhost:5000`, and a
DNS route for `dashboard.example.com`. The scheduled task then runs that named
tunnel every weekday. Without this configuration it uses a temporary
`trycloudflare.com` URL, which changes each session.

## IMPORTANT — read this first
This toolkit estimates **probability and sentiment from current option
positioning**. It does **not** and **cannot** predict the exact future trend
of the market. No legitimate tool can. Option chain data reflects what
traders have already bet on — it can be, and often is, overridden by news,
FII/DII flows, global cues, and momentum. Use this as ONE input alongside
price action and broader context, not as a standalone trading signal.

## Requirements
```
pip install requests pandas
```
Run everything on your own machine with normal internet access — NSE's API
is not reachable from sandboxed/offline environments.

## Files, in build order

| File | What it does |
|---|---|
| `step1_fetch.py` | Fetches live option chain JSON from NSE for NIFTY/BANKNIFTY |
| `step2_parse.py` | Parses raw JSON into a clean per-strike pandas table |
| `step3_pcr.py` | Calculates Put-Call Ratio (OI-based and volume-based) |
| `step4_maxpain.py` | Calculates the Max Pain strike |
| `step5_support_resistance.py` | Finds support/resistance zones from OI concentration |
| `step6_buildup.py` | Classifies OI buildup (Long/Short Buildup, Unwinding, Covering) between two snapshots |
| `step7_dashboard.py` | Combines everything into one transparent sentiment dashboard |
| `step8_scheduler.py` | Runs the full pipeline on a timer during market hours and logs results to CSV |

Each step file up through Step 6 can be run standalone with built-in mock
data, so you can see the logic work without hitting the live API.

## Quick start (real data)

1. Edit `step8_scheduler.py` — set `SYMBOL = "NIFTY"` or `"BANKNIFTY"`, and
   `REFRESH_MINUTES` to how often you want to poll (5 min is reasonable).
2. Run:
   ```
   python step8_scheduler.py
   ```
3. It will print a dashboard every cycle and append rows to
   `sentiment_log.csv` so you can review how sentiment tracked price
   over the day.

## Sensex note
This toolkit is written for NSE (Nifty/Bank Nifty). Sensex option chain
data comes from BSE and has a different API structure — `step1_fetch.py`
would need a BSE-specific version if you want to extend to Sensex.

## Extending further
- Add a matplotlib chart of spot price vs sentiment votes over the day
  (reads straight from `sentiment_log.csv`)
- Add IV skew (call IV vs put IV) as an additional vote in
  `step7_dashboard.py`'s `build_dashboard()` function
- Add email/Telegram alerts when `combined_sentiment_read` flips

## Live-mode setup (packaged helper)

- A lightweight start helper is available at `scripts/start_live.ps1` to create a virtualenv and install dependencies.
- Edit `config/settings.json` to enable live mode:

```json
{
   "use_live_api": true,
   "symbol": "NIFTY",
   "refresh_minutes": 5,
   "nse_retry_count": 3,
   "nse_retry_backoff_seconds": 2
}
```

Run the scheduler during market hours:

```powershell
python step8_scheduler.py
```

## Broker connector scaffold

There is a scaffold at `brokers/zerodha.py` with placeholder functions `authenticate()` and `place_order()` if you want to wire live execution. Implement these functions with your broker SDK and keep credentials out of source control (use environment variables or a secrets manager).

### Zerodha (Kite)

Install the official SDK:

```powershell
pip install kiteconnect
```

Set environment variables (example):

```powershell
setx KITE_API_KEY "your_api_key"
setx KITE_API_SECRET "your_api_secret"
# Optionally set KITE_ACCESS_TOKEN when you have it
setx KITE_ACCESS_TOKEN "your_access_token"
```

Use `scripts/trade_example.py` to preview order payloads in dry-run mode before enabling live orders.

### Dhan

`brokers/dhan.py` is a placeholder scaffold — replace `BASE_URL` and endpoints per Dhan's API docs and set these env vars:

```powershell
setx DHAN_API_KEY "your_api_key"
setx DHAN_API_SECRET "your_api_secret"
setx DHAN_ACCESS_TOKEN "your_access_token"
```

Run the trade example (dry-run):

```powershell
python scripts/trade_example.py
```

## Tests

Run the basic pipeline smoke test (uses mock data):

```powershell
python tests/test_pipeline.py
```

## Historical option-chain snapshots

The data folders are separated so live monitoring and research do not mix:

- `data/live/` contains the newest live response per symbol.
- `data/history/YYYY-MM-DD/` contains timestamped raw and flattened snapshots.
- `data/analysis/` contains summaries produced from historical data.

The web dashboard saves the newest live response every ten seconds and appends
historical data at most every five minutes per symbol.
Capture one manually with:

```powershell
python scripts/capture_snapshot.py --symbol NIFTY --source nse
```

Summarize saved snapshots for strategy research:

```powershell
python scripts/analyze_history.py --symbol NIFTY
```

Use `--source dhan` after configuring valid Dhan credentials. NSE exposes the
current chain through its website, but it does not guarantee a complete
historical option-chain archive. These saved snapshots are therefore the
reliable source for future OI, volume, Greeks, and strategy research.

