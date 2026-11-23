import os
from datetime import datetime
from pathlib import Path
from typing import Dict, Any, List
import pandas as pd
from stockapp.app.settings import settings
from stockapp.app.log import logger

def generate_nightly_reports(
    run_date: str,
    traffic_light: Dict[str, Any],
    market_breadth: Dict[str, float],
    picks: List[Dict[str, Any]],
    open_trades: List[Dict[str, Any]],
    closed_trades: List[Dict[str, Any]]
) -> Dict[str, Path]:
    """Generates CSV, Markdown and structured Nightly Reports."""
    settings.reports_dir.mkdir(parents=True, exist_ok=True)
    report_base = settings.reports_dir / f"nightly_{run_date.replace('-', '')}"

    # 1. Save Picks CSV
    csv_path = report_base.with_suffix(".csv")
    if picks:
        picks_df = pd.DataFrame(picks)
        picks_df.to_csv(csv_path, index=False)
    else:
        pd.DataFrame(columns=["symbol", "horizon", "setup_code", "tech_score", "quantity", "allocated_capital"]).to_csv(csv_path, index=False)

    # 2. Save Markdown Summary
    md_path = report_base.with_suffix(".md")
    with open(md_path, "w", encoding="utf-8") as f:
        f.write(f"# Nightly Quantitative Stock Report - {run_date}\n\n")
        f.write("## 1. Market Traffic Light & Breadth\n")
        f.write(f"- **State:** {traffic_light.get('state')} ({traffic_light.get('color')})\n")
        f.write(f"- **Description:** {traffic_light.get('description')}\n")
        f.write(f"- **Nifty 50 Close:** {traffic_light.get('nifty_close'):,.2f} | **SMA200:** {traffic_light.get('nifty_sma200'):,.2f} | **SMA50:** {traffic_light.get('nifty_sma50'):,.2f}\n")
        f.write(f"- **India VIX:** {traffic_light.get('vix'):.2f} | **Panic Flagged:** {traffic_light.get('is_panic')}\n")
        f.write(f"- **Breadth:** {market_breadth.get('pct_above_sma200', 0):.1f}% > SMA200, {market_breadth.get('pct_above_sma50', 0):.1f}% > SMA50\n")
        f.write(f"- **Sizing Multiplier:** {traffic_light.get('effective_size_multiplier')} ({traffic_light.get('mode')} Mode)\n\n")

        f.write("## 2. Nightly Approved Picks\n")
        if picks:
            f.write("| Symbol | Horizon | Setup | Tech Score | Planned Entry | Stop Loss | Qty | Allocated (Rs) | Risk (Rs) |\n")
            f.write("|---|---|---|---|---|---|---|---|---|\n")
            for p in picks:
                f.write(f"| **{p['symbol']}** | {p['horizon']} | {p['setup_code']} | {p['tech_score']:.1f} | Rs {p['planned_entry']:,.2f} | Rs {p['stop_price']:,.2f} | {p['quantity']} | Rs {p['allocated_capital']:,.2f} | Rs {p['risk_rs']:,.2f} |\n")
        else:
            f.write("*No new picks generated tonight (either market RED, limits reached, or no setups triggered).*\n")
        f.write("\n")

        f.write("## 3. Open Positions Book\n")
        if open_trades:
            f.write(f"Total Open: {len(open_trades)} / {settings.limits.max_open_trades}\n\n")
            f.write("| Trade ID | Symbol | Horizon | Entry Date | Entry Price | Stop Price | Trailing Stop | Qty |\n")
            f.write("|---|---|---|---|---|---|---|---|\n")
            for t in open_trades:
                f.write(f"| #{t.get('trade_id', '-')} | {t.get('symbol')} | {t.get('horizon')} | {t.get('entry_date')} | Rs {float(t.get('entry_price', 0)):,.2f} | Rs {float(t.get('stop_price', 0)):,.2f} | Rs {float(t.get('trailing_stop', 0)):,.2f} | {t.get('quantity')} |\n")
        else:
            f.write("*No positions currently open.*\n")
        f.write("\n")

        f.write("## 4. Closed Trades Summary\n")
        if closed_trades:
            f.write("| Trade ID | Symbol | Horizon | Exit Date | Exit Price | P&L (Rs) | P&L (%) | Exit Reason |\n")
            f.write("|---|---|---|---|---|---|---|---|\n")
            for ct in closed_trades:
                f.write(f"| #{ct.get('trade_id')} | {ct.get('symbol')} | {ct.get('horizon')} | {ct.get('exit_date')} | Rs {float(ct.get('exit_price', 0)):,.2f} | Rs {float(ct.get('pnl_rs', 0)):,.2f} | {float(ct.get('pnl_pct', 0)):+.2f}% | {ct.get('exit_reason')} |\n")
        else:
            f.write("*No trades closed recently.*\n")

    # 3. Generate Word .docx if python-docx is available
    docx_path = report_base.with_suffix(".docx")
    try:
        import docx
        doc = docx.Document()
        doc.add_heading(f"Nightly Quantitative Stock Report - {run_date}", 0)
        doc.add_heading("1. Market Traffic Light & Breadth", level=1)
        doc.add_paragraph(f"Traffic Light: State {traffic_light.get('state')} ({traffic_light.get('color')}) - {traffic_light.get('description')}")
        doc.add_paragraph(f"Nifty: {traffic_light.get('nifty_close'):,.2f} | VIX: {traffic_light.get('vix'):.2f} | Breadth: {market_breadth.get('pct_above_sma50', 0):.1f}% > SMA50")
        
        doc.add_heading("2. Approved Trade Picks", level=1)
        if picks:
            t = doc.add_table(rows=1, cols=6)
            hdr_cells = t.rows[0].cells
            hdr_cells[0].text = "Symbol"
            hdr_cells[1].text = "Horizon"
            hdr_cells[2].text = "Setup"
            hdr_cells[3].text = "Entry"
            hdr_cells[4].text = "Stop"
            hdr_cells[5].text = "Qty"
            for p in picks:
                row_cells = t.add_row().cells
                row_cells[0].text = str(p['symbol'])
                row_cells[1].text = str(p['horizon'])
                row_cells[2].text = str(p['setup_code'])
                row_cells[3].text = f"Rs {p['planned_entry']:,.2f}"
                row_cells[4].text = f"Rs {p['stop_price']:,.2f}"
                row_cells[5].text = str(p['quantity'])
        else:
            doc.add_paragraph("No new trade picks tonight.")
            
        doc.save(str(docx_path))
    except Exception as e:
        logger.debug(f"python-docx generation skipped: {e}")

    logger.info(f"Generated Nightly Reports at {report_base}")
    return {"csv": csv_path, "md": md_path, "docx": docx_path}
