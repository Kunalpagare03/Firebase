# Option app daily PostgreSQL snapshot

This separate Option app job copies its Firestore market and app records to the
local `option_app` PostgreSQL database every day at 23:00 Asia/Kolkata. On this
PC, PostgreSQL 17.11 is installed under the current Windows profile, listens
only on `127.0.0.1:5432`, and uses SCRAM password authentication. Firebase
remains the source of truth; each date is retained as a separate snapshot, and
rerunning on the same date updates that day's rows. Strategy and backtest-history
saves from the Option Android app are also sent concurrently to Firestore and a
separate HTTPS API on this PC. If either destination fails, the other write is
still attempted. The Stock app uses its own job, database, API, and tables.

The Option snapshot includes `option_sentiment`, `live_quotes`, `option_scans`,
the shared `backtest_results` summary, and the per-user `option_backtest_history`
and `option_strategies` collections. It does not copy Firebase Authentication
accounts, credentials, access tokens, command queues, or rate-limit state.

## This PC's setup

1. In pgAdmin, register a server connection to `127.0.0.1`, port `5432`,
   maintenance database `postgres`, using the `kunal-admin` login and the
   password you supplied. The separate `option_app` and `stock_app` databases
   have their own restricted sync roles.
2. Keep PostgreSQL bound to localhost; do not expose port 5432 to the internet.
   The password file is protected for your Windows account and is not stored in
   the repository. The app API is TLS-pinned and accepts only Firebase-authenticated
   writes over the current Private Wi-Fi subnet; the Windows firewall rules are
   scoped to that subnet and the Option app service.
3. In PowerShell, from this directory, install the Python dependencies:

   ```powershell
   py -m venv .venv
   .\.venv\Scripts\python.exe -m pip install -r requirements.txt
   ```

   The sync service is already configured to use the local Firebase credential
   file without putting it into the APK or source control.
4. Register the local API and the daily Windows Task Scheduler tasks:

   ```powershell
   .\register_local_sync_server.ps1
   .\register_daily_sync.ps1
   ```

   Windows must use the `India Standard Time` time zone. The task runs as your
   Windows user. Keep this PC and the phone on the same trusted Wi-Fi. The local
   PostgreSQL API starts at user logon; the daily snapshot runs at 23:00, and
   missed snapshots start when the task can next run.

The local PostgreSQL server starts at user logon using the
`KunalLocalPostgreSQL17` task. The Option API listens on the PC's Wi-Fi IP at
port `8765` and advertises itself using mDNS; PostgreSQL itself stays on
localhost. App writes are stored in `option_app_live_documents`, while daily
snapshots are stored in `option_app_daily_documents`. API and snapshot logs are
under `%LOCALAPPDATA%\PostgreSQL\17\api\option` and
`%LOCALAPPDATA%\PostgreSQL\17\sync-logs`.

To run a snapshot immediately for testing, use:

```powershell
.\.venv\Scripts\python.exe .\sync_firestore_to_postgres.py
```

The daily snapshot is read-only against Firestore. It creates
`option_app_daily_documents` automatically. Query this table in pgAdmin; use
`snapshot_date` to select a day's data and `payload` for the original Firestore
fields. Authentication secrets are never mirrored.
