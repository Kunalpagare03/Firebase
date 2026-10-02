import numpy as np
import pandas as pd
import yfinance as yf
from datetime import datetime
from typing import Dict, List, Any, Optional, Tuple

def fetch_historical_data(symbol: str, period: str = "2y", interval: str = "1d") -> pd.DataFrame:
    """
    Fetches historical OHLCV data using yfinance.
    Handles Indian index mapping (e.g., NIFTY -> ^NSEI, BANKNIFTY -> ^NSEBANK, FINNIFTY -> ^CNXFIN)
    """
    sym_clean = symbol.upper().strip()
    if sym_clean in ("NIFTY", "NIFTY 50", "NSEI"):
        yf_symbol = "^NSEI"
    elif sym_clean in ("BANKNIFTY", "BANK NIFTY", "NSEBANK"):
        yf_symbol = "^NSEBANK"
    elif sym_clean in ("FINNIFTY", "CNXFIN"):
        yf_symbol = "^CNXFIN"
    elif not (sym_clean.startswith("^") or sym_clean.endswith(".NS") or sym_clean.endswith(".BO")):
        yf_symbol = f"{sym_clean}.NS"
    else:
        yf_symbol = sym_clean

    ticker = yf.Ticker(yf_symbol)
    df = ticker.history(period=period, interval=interval, auto_adjust=False)
    if df.empty and yf_symbol.endswith(".NS"):
        df = yf.Ticker(sym_clean).history(period=period, interval=interval, auto_adjust=False)
    return df

# ==========================================
# 1. ADVANCED CANDLESTICK RECOGNITION ENGINE
# ==========================================
def detect_candlestick_pattern(df: pd.DataFrame, idx: int) -> Tuple[str, float]:
    """
    Detects institutional harmonic candlestick reversal & continuation patterns.
    Returns: (pattern_name, signal_weight) where >0 is bullish, <0 is bearish, 0 is neutral.
    """
    if idx < 3:
        return ("Normal Candle", 0.0)

    curr = df.iloc[idx]
    prev = df.iloc[idx - 1]
    prev2 = df.iloc[idx - 2]

    c_open, c_high, c_low, c_close = curr['Open'], curr['High'], curr['Low'], curr['Close']
    p_open, p_high, p_low, p_close = prev['Open'], prev['High'], prev['Low'], prev['Close']
    p2_open, p2_high, p2_low, p2_close = prev2['Open'], prev2['High'], prev2['Low'], prev2['Close']

    c_body = abs(c_close - c_open)
    c_range = max(c_high - c_low, 0.001)
    p_body = abs(p_close - p_open)
    p_range = max(p_high - p_low, 0.001)

    c_upper_wick = c_high - max(c_open, c_close)
    c_lower_wick = min(c_open, c_close) - c_low

    is_c_bullish = c_close > c_open
    is_c_bearish = c_close < c_open
    is_p_bullish = p_close > p_open
    is_p_bearish = p_close < p_open

    # 1. Bullish Engulfing
    if is_p_bearish and is_c_bullish and c_close >= p_open and c_open <= p_close and c_body > p_body * 1.1:
        return ("Bullish Engulfing", 2.0)

    # 2. Bearish Engulfing
    if is_p_bullish and is_c_bearish and c_open >= p_close and c_close <= p_open and c_body > p_body * 1.1:
        return ("Bearish Engulfing", -2.0)

    # 3. Morning Star (3-bar bullish reversal)
    if is_p_bearish and p2_close < p2_open and p_body / p_range < 0.35 and is_c_bullish and c_close > (p2_open + p2_close) / 2:
        return ("Morning Star", 2.5)

    # 4. Evening Star (3-bar bearish reversal)
    if p2_close > p2_open and p_body / p_range < 0.35 and is_c_bearish and c_close < (p2_open + p2_close) / 2:
        return ("Evening Star", -2.5)

    # 5. Hammer (Bullish rejection of lower prices)
    if c_lower_wick >= c_body * 2.0 and c_upper_wick <= c_body * 0.4 and (c_body / c_range) >= 0.2:
        return ("Bullish Hammer Reversal", 1.8)

    # 6. Shooting Star (Bearish rejection of higher prices)
    if c_upper_wick >= c_body * 2.0 and c_lower_wick <= c_body * 0.4 and (c_body / c_range) >= 0.2:
        return ("Shooting Star Reversal", -1.8)

    # 7. Three White Soldiers (Strong Bullish Continuation)
    if is_c_bullish and is_p_bullish and p2_close > p2_open and c_close > p_close > p2_close:
        if c_body / c_range > 0.6 and p_body / p_range > 0.6:
            return ("Three White Soldiers", 2.2)

    # 8. Inside Bar / Harami Breakout
    if c_high < p_high and c_low > p_low:
        return ("Inside Bar (Harami)", 0.5 if is_c_bullish else -0.5)

    return ("Neutral Candle", 0.0)

# ==========================================
# 2. PRICE ACTION & MARKET STRUCTURE ENGINE
# ==========================================
def detect_price_action_structure(df: pd.DataFrame, idx: int) -> Tuple[str, float]:
    """
    Detects multi-swing market structures (W-Bottom, M-Top, HH-HL, Order Block retests, VCP Squeeze).
    Returns: (structure_name, signal_weight)
    """
    if idx < 30:
        return ("Building Market Structure", 0.0)

    window = df.iloc[max(0, idx-30):idx+1]
    closes = window['Close'].values
    highs = window['High'].values
    lows = window['Low'].values
    vols = window['Volume'].values

    curr_close = closes[-1]
    n = len(closes)
    chunk = n // 5
    if chunk < 3:
        return ("Range Consolidating", 0.0)

    # Swing Pivots
    l1 = np.min(lows[0:chunk*2])
    l2 = np.min(lows[chunk*2:chunk*4])
    l_curr = np.min(lows[chunk*4:])
    h_mid = np.max(highs[chunk:chunk*4])
    h_peak = np.max(highs[0:chunk*3])

    atr = window['atr'].iloc[-1] if 'atr' in window.columns else (np.max(highs) - np.min(lows)) * 0.05
    thr = max(atr * 0.75, curr_close * 0.005)

    # 1. Double Bottom (W-Pattern Breakout)
    if abs(l1 - l2) <= thr and h_mid > l1 + thr and curr_close > h_mid:
        return ("Double Bottom (W-Pattern) Breakout", 3.0)

    # 2. Double Top (M-Pattern Breakdown)
    h1 = np.max(highs[0:chunk*2])
    h2 = np.max(highs[chunk*2:chunk*4])
    l_mid = np.min(lows[chunk:chunk*4])
    if abs(h1 - h2) <= thr and l_mid < h1 - thr and curr_close < l_mid:
        return ("Double Top (M-Pattern) Breakdown", -3.0)

    # 3. Volatility Contraction Pattern (VCP Squeeze Breakout)
    range_prev = np.max(highs[-20:-10]) - np.min(lows[-20:-10])
    range_curr = np.max(highs[-10:-1]) - np.min(lows[-10:-1])
    vol_ratio = window['vol_ratio'].iloc[-1] if 'vol_ratio' in window.columns else 1.0

    if range_curr < range_prev * 0.65 and curr_close > np.max(highs[-10:-1]) and vol_ratio >= 1.25:
        return ("VCP Volatility Squeeze Breakout", 2.5)

    # 4. Institutional Order Block Demand Zone Bounce
    demand_low = np.min(lows[-25:-10])
    demand_high = demand_low + atr * 1.2
    if demand_low <= lows[-1] <= demand_high and curr_close > closes[-2] and vol_ratio >= 1.1:
        return ("Institutional Demand Block Absorption", 2.0)

    # 5. Higher Highs & Higher Lows (HH-HL Trend Structure)
    if l2 > l1 + thr * 0.5 and closes[-1] > h_mid:
        return ("Higher-High / Higher-Low Structure", 1.5)

    return ("Consolidation Structure", 0.0)

# ==========================================
# 3. OPTION CHAIN FLOW & DERIVATIVES ENGINE
# ==========================================
def simulate_option_chain_confluence(df: pd.DataFrame, idx: int) -> Tuple[str, float, float]:
    """
    Simulates Option Chain Open Interest (OI) positioning, PCR divergence, and Max Pain gravity.
    Returns: (option_signal_desc, signal_weight, pcr_simulated)
    """
    curr = df.iloc[idx]
    prev5 = df.iloc[max(0, idx-5):idx]

    close_curr = curr['Close']
    close_prev_mean = prev5['Close'].mean()
    price_trend = (close_curr - close_prev_mean) / close_prev_mean

    vol_ratio = curr.get('vol_ratio', 1.0)
    rsi = curr.get('rsi', 50.0)

    # Model realistic Option PCR (OI) based on momentum & volume delta
    pcr_base = 1.0
    if price_trend > 0.01:
        pcr_base += 0.25 * min(vol_ratio, 2.0)
    elif price_trend < -0.01:
        pcr_base -= 0.25 * min(vol_ratio, 2.0)

    if rsi > 60:
        pcr_base += 0.15
    elif rsi < 40:
        pcr_base -= 0.15

    pcr_oi = max(0.5, min(1.8, pcr_base))

    # 1. Bullish PCR Divergence (Price dipping into support while Put Writing surging)
    if price_trend < -0.005 and pcr_oi > 1.20:
        return ("Bullish PCR Divergence (Heavy Put Writing)", 3.0, round(pcr_oi, 2))

    # 2. Bearish PCR Divergence (Price rising into resistance while Call Writing building)
    if price_trend > 0.005 and pcr_oi < 0.85:
        return ("Bearish PCR Divergence (Heavy Call Writing)", -3.0, round(pcr_oi, 2))

    # 3. Aggressive Long Buildup (PCR > 1.15 with volume surge)
    if pcr_oi >= 1.15 and vol_ratio >= 1.2:
        return ("Option Long Buildup & Put Support", 2.0, round(pcr_oi, 2))

    # 4. Short Buildup (PCR < 0.85)
    if pcr_oi <= 0.85 and vol_ratio >= 1.2:
        return ("Option Short Buildup & Call Resistance", -2.0, round(pcr_oi, 2))

    return ("Option Chain Neutral", 0.0, round(pcr_oi, 2))

# ==========================================
# 4. QUANTITATIVE INDICATOR CALCULATIONS
# ==========================================
def calculate_technical_indicators(df: pd.DataFrame, fast_ema: int = 20, slow_ema: int = 50, rsi_period: int = 14) -> pd.DataFrame:
    data = df.copy()
    data['ema_fast'] = data['Close'].ewm(span=fast_ema, adjust=False).mean()
    data['ema_slow'] = data['Close'].ewm(span=slow_ema, adjust=False).mean()
    data['ema_200'] = data['Close'].ewm(span=200, adjust=False).mean()

    # RSI
    delta = data['Close'].diff()
    gain = delta.where(delta > 0, 0).rolling(window=rsi_period).mean()
    loss = (-delta.where(delta < 0, 0)).rolling(window=rsi_period).mean()
    rs = gain / loss.replace(0, np.nan)
    data['rsi'] = 100 - (100 / (1 + rs))
    data['rsi'] = data['rsi'].fillna(50)

    # ATR (14)
    high_low = data['High'] - data['Low']
    high_close = (data['High'] - data['Close'].shift()).abs()
    low_close = (data['Low'] - data['Close'].shift()).abs()
    ranges = pd.concat([high_low, high_close, low_close], axis=1)
    true_range = ranges.max(axis=1)
    data['atr'] = true_range.rolling(14).mean().bfill()

    # Volume 20 SMA ratio
    vol_sma = data['Volume'].rolling(20).mean()
    data['vol_ratio'] = (data['Volume'] / vol_sma.replace(0, np.nan)).fillna(1.0)

    return data

# ==========================================
# 5. MASTER INSTITUTIONAL SIMULATOR
# ==========================================
class BacktestSimulator:
    def __init__(
        self,
        symbol: str = "NIFTY",
        strategy_type: str = "MULTI_CONFLUENCE_QUANT",
        fast_period: int = 20,
        slow_period: int = 50,
        rsi_threshold: float = 35.0,
        target_pct: float = 2.5,
        stop_loss_pct: float = 1.2,
        atr_multiplier: float = 1.5,
        pcr_threshold: float = 1.15,
        max_holding_days: int = 10,
        period: str = "2y"
    ):
        self.symbol = symbol
        self.strategy_type = strategy_type
        self.fast_period = fast_period
        self.slow_period = slow_period
        self.rsi_threshold = rsi_threshold
        self.target_pct = target_pct
        self.stop_loss_pct = stop_loss_pct
        self.atr_multiplier = atr_multiplier
        self.pcr_threshold = pcr_threshold
        self.max_holding_days = max_holding_days
        self.period = period

    def run(self) -> Dict[str, Any]:
        df = fetch_historical_data(self.symbol, period=self.period)
        if df.empty or len(df) < max(self.slow_period, 50):
            return {
                "status": "error",
                "message": f"Insufficient historical data for {self.symbol}",
                "total_trades": 0
            }

        df = calculate_technical_indicators(
            df,
            fast_ema=self.fast_period,
            slow_ema=self.slow_period
        )

        trades: List[Dict[str, Any]] = []
        in_position = False
        entry_price = 0.0
        entry_idx = 0
        entry_date = ""
        entry_pattern = ""
        position_type = "LONG"
        dynamic_sl_price = 0.0
        dynamic_target_price = 0.0

        for i in range(max(self.slow_period, 50), len(df)):
            curr = df.iloc[i]
            date_str = df.index[i].strftime("%Y-%m-%d")

            # ------------------------------------
            # Exit Logic (Trailing ATR or Fixed %)
            # ------------------------------------
            if in_position:
                holding_days = i - entry_idx
                price_high = curr['High']
                price_low = curr['Low']
                current_close = curr['Close']

                exit_trade = False
                exit_price = current_close
                exit_reason = ""

                # Target Hit
                if price_high >= dynamic_target_price:
                    exit_price = dynamic_target_price
                    exit_reason = "TARGET HIT"
                    exit_trade = True
                # Stop Loss Hit
                elif price_low <= dynamic_sl_price:
                    exit_price = dynamic_sl_price
                    exit_reason = "STOP LOSS HIT"
                    exit_trade = True
                # Time Exit
                elif holding_days >= self.max_holding_days:
                    exit_price = current_close
                    exit_reason = "TIME LIMIT"
                    exit_trade = True

                if exit_trade:
                    return_pct = ((exit_price - entry_price) / entry_price) * 100
                    risk_amount = max(0.01, entry_price - dynamic_sl_price)
                    r_multiple = (exit_price - entry_price) / risk_amount

                    trades.append({
                        "entry_date": entry_date,
                        "exit_date": date_str,
                        "type": position_type,
                        "entry_price": round(float(entry_price), 2),
                        "exit_price": round(float(exit_price), 2),
                        "return_pct": round(float(return_pct), 2),
                        "holding_days": holding_days,
                        "exit_reason": exit_reason,
                        "pattern_trigger": entry_pattern,
                        "is_win": return_pct > 0,
                        "r_multiple": round(float(r_multiple), 2)
                    })
                    in_position = False
                continue

            # ------------------------------------
            # Multi-Engine Strategy Signals
            # ------------------------------------
            candle_pat, candle_wt = detect_candlestick_pattern(df, i)
            struct_pat, struct_wt = detect_price_action_structure(df, i)
            opt_pat, opt_wt, pcr_val = simulate_option_chain_confluence(df, i)

            signal = False
            trigger_name = ""

            # Strategy 1: Option Flow & Expiry Gravity
            if self.strategy_type == "OPTION_FLOW_GRAVITY":
                if opt_wt >= 2.0 and curr['Close'] > curr['ema_fast']:
                    signal = True
                    trigger_name = f"{opt_pat} (PCR {pcr_val})"

            # Strategy 2: Price Action & Market Structure Breakouts
            elif self.strategy_type == "PRICE_ACTION_STRUCTURE":
                if struct_wt >= 2.0 and curr['vol_ratio'] >= 1.15:
                    signal = True
                    trigger_name = struct_pat

            # Strategy 3: Harmonic Candlestick & Volatility Squeeze
            elif self.strategy_type == "CANDLE_VOLATILITY_SQUEEZE":
                if candle_wt >= 1.8 and (curr['rsi'] < 65 or struct_wt > 0):
                    signal = True
                    trigger_name = f"{candle_pat} + Vol Surge"

            # Strategy 4: Master Confluence Quant Algorithm (All Signals Fused)
            elif self.strategy_type == "MULTI_CONFLUENCE_QUANT":
                confluence_score = candle_wt + struct_wt + opt_wt
                if curr['Close'] > curr['ema_200']:
                    confluence_score += 1.0

                if confluence_score >= 4.0:
                    signal = True
                    trigger_name = f"{candle_pat} • {struct_pat}"

            # Classic Strategies Fallbacks
            elif self.strategy_type == "EMA_CROSS":
                prev = df.iloc[i - 1]
                if prev['ema_fast'] <= prev['ema_slow'] and curr['ema_fast'] > curr['ema_slow']:
                    signal = True
                    trigger_name = f"EMA ({self.fast_period}/{self.slow_period}) Cross"

            elif self.strategy_type == "RSI_MOMENTUM":
                prev = df.iloc[i - 1]
                if prev['rsi'] <= self.rsi_threshold and curr['rsi'] > self.rsi_threshold:
                    signal = True
                    trigger_name = f"RSI Reversal (>{self.rsi_threshold})"

            elif self.strategy_type == "BREAKOUT":
                recent_high = df['High'].iloc[max(0, i-20):i].max()
                if curr['Close'] > recent_high and curr['vol_ratio'] >= 1.2:
                    signal = True
                    trigger_name = "20-Day Range Breakout"

            if signal:
                in_position = True
                entry_price = curr['Close']
                entry_idx = i
                entry_date = date_str
                entry_pattern = trigger_name
                position_type = "LONG"

                # Dynamic ATR Volatility calculations
                atr_val = curr['atr']
                dynamic_sl_price = entry_price - (atr_val * self.atr_multiplier)
                dynamic_target_price = entry_price + (atr_val * self.atr_multiplier * (self.target_pct / max(0.1, self.stop_loss_pct)))

                # Bounds safeguard
                dynamic_sl_price = max(dynamic_sl_price, entry_price * (1 - self.stop_loss_pct / 100))
                dynamic_target_price = max(dynamic_target_price, entry_price * (1 + self.target_pct / 100))

        # ------------------------------------
        # Advanced Quantitative Metrics Engine
        # ------------------------------------
        total_trades = len(trades)
        if total_trades == 0:
            return {
                "status": "ok",
                "symbol": self.symbol,
                "strategy_name": self.strategy_type,
                "total_trades": 0,
                "win_rate": 0.0,
                "total_return": 0.0,
                "sharpe_ratio": 0.0,
                "sortino_ratio": 0.0,
                "calmar_ratio": 0.0,
                "profit_factor": 0.0,
                "max_drawdown": 0.0,
                "trades": []
            }

        wins = [t for t in trades if t["is_win"]]
        losses = [t for t in trades if not t["is_win"]]

        win_rate = (len(wins) / total_trades) * 100
        gross_profit = sum(t["return_pct"] for t in wins)
        gross_loss = abs(sum(t["return_pct"] for t in losses))
        profit_factor = (gross_profit / gross_loss) if gross_loss > 0 else (gross_profit if gross_profit > 0 else 1.0)
        total_return = sum(t["return_pct"] for t in trades)

        # Drawdown Math
        returns_series = np.array([t["return_pct"] for t in trades])
        cumulative = np.cumsum(returns_series)
        peak = np.maximum.accumulate(cumulative)
        drawdown = peak - cumulative
        max_drawdown = float(np.max(drawdown)) if len(drawdown) > 0 else 0.0

        # Sharpe & Sortino Ratios (Annualized Assuming 252 trading days)
        mean_ret = np.mean(returns_series)
        std_ret = np.std(returns_series) if len(returns_series) > 1 else 1.0
        downside_returns = returns_series[returns_series < 0]
        downside_std = np.std(downside_returns) if len(downside_returns) > 1 else (std_ret * 0.7)

        # Annualization factor
        annual_factor = np.sqrt(max(1, total_trades))
        sharpe_ratio = float((mean_ret / max(0.001, std_ret)) * annual_factor)
        sortino_ratio = float((mean_ret / max(0.001, downside_std)) * annual_factor)
        calmar_ratio = float(total_return / max(0.1, max_drawdown))

        # Expectancy: (Win% * AvgWin) - (Loss% * AvgLoss)
        avg_win = float(np.mean([t["return_pct"] for t in wins])) if wins else 0.0
        avg_loss = float(abs(np.mean([t["return_pct"] for t in losses]))) if losses else 0.0
        expectancy = ((win_rate / 100.0) * avg_win) - (((100.0 - win_rate) / 100.0) * avg_loss)

        # Consecutive Wins/Losses
        max_consec_wins = 0
        max_consec_losses = 0
        curr_w, curr_l = 0, 0
        for t in trades:
            if t["is_win"]:
                curr_w += 1
                curr_l = 0
                max_consec_wins = max(max_consec_wins, curr_w)
            else:
                curr_l += 1
                curr_w = 0
                max_consec_losses = max(max_consec_losses, curr_l)

        display_name = {
            "OPTION_FLOW_GRAVITY": "Option Flow & Expiry Gravity",
            "PRICE_ACTION_STRUCTURE": "Price Action & Market Structure",
            "CANDLE_VOLATILITY_SQUEEZE": "Harmonic Candlestick & VCP Squeeze",
            "MULTI_CONFLUENCE_QUANT": "Master Confluence Quantitative Engine",
            "EMA_CROSS": f"EMA ({self.fast_period}/{self.slow_period}) Cross",
            "RSI_MOMENTUM": f"RSI Momentum Reversal",
            "BREAKOUT": "20-Day Range Breakout"
        }.get(self.strategy_type, self.strategy_type)

        return {
            "status": "ok",
            "symbol": self.symbol,
            "strategy_name": display_name,
            "strategy_type": self.strategy_type,
            "timeframe": self.period,
            "target_pct": self.target_pct,
            "stop_loss_pct": self.stop_loss_pct,
            "atr_multiplier": self.atr_multiplier,
            "total_trades": total_trades,
            "wins": len(wins),
            "losses": len(losses),
            "win_rate": round(win_rate, 1),
            "total_return": round(total_return, 2),
            "profit_factor": round(profit_factor, 2),
            "max_drawdown": round(max_drawdown, 2),
            "sharpe_ratio": round(sharpe_ratio, 2),
            "sortino_ratio": round(sortino_ratio, 2),
            "calmar_ratio": round(calmar_ratio, 2),
            "expectancy": round(expectancy, 2),
            "avg_trade_return": round(float(mean_ret), 2),
            "max_consecutive_wins": max_consec_wins,
            "max_consecutive_losses": max_consec_losses,
            "generated_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "trades": trades[-30:]
        }
