import os
import sys
import argparse
import json

# Setup root path
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

from backtesting.backtest_engine import BacktestSimulator
from Option.utils.firebase_sync import sync_to_firestore

def run_and_sync():
    parser = argparse.ArgumentParser(description="Run Advanced Algorithmic Backtest & Sync to Cloud")
    parser.add_argument("--symbol", default="NIFTY", help="Stock or Index symbol (e.g. NIFTY, BANKNIFTY, RELIANCE, TCS)")
    parser.add_argument("--strategy", default="MULTI_CONFLUENCE_QUANT", 
                        choices=["OPTION_FLOW_GRAVITY", "PRICE_ACTION_STRUCTURE", "CANDLE_VOLATILITY_SQUEEZE", "MULTI_CONFLUENCE_QUANT", "EMA_CROSS", "RSI_MOMENTUM", "BREAKOUT"], 
                        help="Strategy Type")
    parser.add_argument("--target", type=float, default=2.5, help="Target return percentage (e.g. 2.5)")
    parser.add_argument("--stop", type=float, default=1.2, help="Stop loss percentage (e.g. 1.2)")
    parser.add_argument("--atr-mult", type=float, default=1.5, help="ATR Volatility Multiplier (e.g. 1.5)")
    parser.add_argument("--period", default="2y", help="Historical data period (e.g. 1y, 2y, 5y)")
    parser.add_argument("--no-sync", action="store_true", help="Skip syncing to Firebase")
    args = parser.parse_args()

    print(f"\n[RUNNING] Institutional Quant Backtest for [{args.symbol}]")
    print(f"  Strategy:  [{args.strategy}]")
    print(f"  Target:    +{args.target}% | Stop: -{args.stop}% (ATR x{args.atr_mult}) | Period: {args.period}")
    
    sim = BacktestSimulator(
        symbol=args.symbol,
        strategy_type=args.strategy,
        target_pct=args.target,
        stop_loss_pct=args.stop,
        atr_multiplier=args.atr_mult,
        period=args.period
    )
    result = sim.run()

    if result.get("status") != "ok":
        print(f"[FAILED] Backtest failed: {result.get('message')}")
        return

    print("\n" + "="*60)
    print(f"  QUANTITATIVE PERFORMANCE REPORT: {result['symbol']}")
    print(f"  Strategy: {result['strategy_name']}")
    print("="*60)
    print(f"  Total Trades:     {result['total_trades']}")
    print(f"  Win Rate:         {result['win_rate']}% ({result['wins']} W / {result['losses']} L)")
    print(f"  Net Total Return: {result['total_return']:+}%")
    print(f"  Profit Factor:    {result['profit_factor']}")
    print(f"  Max Drawdown:     -{result['max_drawdown']}%")
    print(f"  Sharpe Ratio:     {result['sharpe_ratio']} (Annualized Alpha)")
    print(f"  Sortino Ratio:    {result['sortino_ratio']} (Downside Risk-Adjusted)")
    print(f"  Calmar Ratio:     {result['calmar_ratio']} (Return / Max DD)")
    print(f"  Trade Expectancy: {result['expectancy']:+}% edge per trade")
    print(f"  Max Win Streak:   {result['max_consecutive_wins']} trades")
    print("="*60)

    if result.get("trades"):
        print("\n  LATEST 5 SIMULATED TRADES:")
        for t in result["trades"][-5:]:
            status = "WIN " if t["is_win"] else "LOSS"
            print(f"  [{status}] {t['entry_date']} -> {t['exit_date']} | {t['type']} at {t['entry_price']} -> {t['exit_price']} ({t['return_pct']:+}%) | Trigger: {t.get('pattern_trigger', 'N/A')}")
        print("="*60)

    if not args.no_sync:
        doc_id = f"{args.symbol}_{args.strategy}".replace("^", "").replace(".", "_")
        sync_to_firestore("backtest_results", doc_id, result)
        sync_to_firestore("backtest_results", "latest", result)
        print(f"\n[SUCCESS] Synced quant metrics to Firestore collection 'backtest_results' [doc: {doc_id}]")

if __name__ == "__main__":
    run_and_sync()
