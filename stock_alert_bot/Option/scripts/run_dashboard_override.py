"""
Run the dashboard using mock snapshots but override spot price with given value.
"""
import os
import sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

from step6_buildup import load_mock_snapshot
from step2_parse import parse_option_chain
from step7_dashboard import build_dashboard, print_dashboard

raw_prev = load_mock_snapshot(offset=0)
raw_curr = load_mock_snapshot(offset=1)

df_prev, spot_prev = parse_option_chain(raw_prev)
df_curr, spot_curr = parse_option_chain(raw_curr)

# Override spot price with the user-provided live spot
spot_curr = 24265

dashboard = build_dashboard(df_prev, df_curr, spot_curr)
print_dashboard(dashboard)
