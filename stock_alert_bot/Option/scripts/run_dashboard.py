"""
Simple runner that uses mock snapshots to build and print the dashboard.
This does not call live NSE APIs; it's safe to run in this environment.
"""
import os
import sys

# Ensure project root is on sys.path so `stock_alert` package imports work
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

from stock_alert import parse_option_chain, load_mock_snapshot, build_dashboard, print_dashboard

if __name__ == "__main__":
    raw_prev = load_mock_snapshot(offset=0)
    raw_curr = load_mock_snapshot(offset=1)

    df_prev, spot_prev = parse_option_chain(raw_prev)
    df_curr, spot_curr = parse_option_chain(raw_curr)

    dashboard = build_dashboard(df_prev, df_curr, spot_curr)
    print_dashboard(dashboard)
