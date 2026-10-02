"""
Master Quantitative AI Agent Engine
Consolidates Regime Detection, Multi-Horizon Probabilities, EV & Risk/Reward filtering (RR >= 1:4),
Anomaly Detection, Fake Breakout/Breakdown Detection, Hero Zero scans, and HFT Burst signals into an auditable master trade signal.
Enforces exact dynamic ATM strike calculation per symbol and option chain timestamp synchronization.
"""

from typing import Dict, Any, List
import datetime
from .regime_detector import RegimeDetector
from .ev_rr_engine import EvRrEngine
from .anomaly_detector import AnomalyDetector
from .hero_zero import HeroZeroEngine
from .hft_burst import HftBurstDetector
from .fake_breakout_detector import FakeBreakoutDetector

class MasterQuantAgent:
    def __init__(self):
        self.regime_detector = RegimeDetector()
        self.ev_rr_engine = EvRrEngine(min_rr_ratio=4.0)
        self.anomaly_detector = AnomalyDetector()
        self.hero_zero_engine = HeroZeroEngine()
        self.hft_detector = HftBurstDetector()
        self.fake_breakout_detector = FakeBreakoutDetector()

    def get_step_size(self, symbol: str) -> float:
        """Returns the standard strike step size for indices."""
        sym_upper = symbol.upper()
        if "BANKNIFTY" in sym_upper:
            return 100.0
        elif "MIDCPNIFTY" in sym_upper:
            return 25.0
        elif "FINNIFTY" in sym_upper:
            return 50.0
        elif "NIFTY" in sym_upper:
            return 50.0
        else:
            return 50.0

    def find_atm_strike(self, symbol: str, spot_price: float, option_chain: List[Dict[str, Any]] = None) -> float:
        """
        Calculates exact ATM strike from spot price, or finds closest strike in available option chain.
        """
        if option_chain and len(option_chain) > 0:
            try:
                closest = min(option_chain, key=lambda row: abs(float(row.get("strike", 0.0)) - spot_price))
                return float(closest.get("strike", spot_price))
            except Exception:
                pass

        step = self.get_step_size(symbol)
        return round(spot_price / step) * step

    def evaluate(
        self,
        symbol: str,
        spot_price: float,
        rsi: float,
        adx: float,
        pcr_oi: float,
        pcr_volume: float,
        oi_buildup: str,
        volume_ratio: float,
        price_change_pct: float,
        resistance_level: float = 0.0,
        support_level: float = 0.0,
        days_to_expiry: float = 3.0,
        tick_rate_10s: float = 5.0,
        move_10s_pct: float = 0.05,
        iv_change_pct: float = 0.0,
        call_oi_change_pct: float = 0.0,
        put_oi_change_pct: float = 0.0,
        option_chain: List[Dict[str, Any]] = None,
        chain_updated_at: str = None
    ) -> Dict[str, Any]:
        """
        Generates the comprehensive real-time quant analysis report matching Section 21/33 specifications.
        """
        current_time_str = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        timestamp = chain_updated_at if chain_updated_at else current_time_str

        res_lvl = resistance_level if resistance_level > 0 else spot_price * 1.005
        sup_lvl = support_level if support_level > 0 else spot_price * 0.995

        # 1. Regime Detection & Multi-Horizon Probabilities
        regime_info = self.regime_detector.detect_regime(
            rsi=rsi, adx=adx, volume_ratio=volume_ratio, atr=spot_price*0.01, price_change_pct=price_change_pct
        )
        probabilities = self.regime_detector.calculate_multi_horizon_probabilities(
            rsi=rsi, pcr_oi=pcr_oi, oi_buildup=oi_buildup, regime=regime_info["regime"], volume_ratio=volume_ratio
        )

        # 2. Anomaly & Manipulation Detection
        anomaly_info = self.anomaly_detector.inspect_market_behavior(
            spot_price_change_pct=price_change_pct, option_oi_change_pct=pcr_oi * 10, volume_ratio=volume_ratio, iv_change_pct=iv_change_pct
        )

        # 3. Fake Breakout / Fake Breakdown Detection (Bull Trap & Bear Trap)
        fake_breakout_info = self.fake_breakout_detector.inspect_breakout(
            spot_price=spot_price,
            resistance_level=res_lvl,
            support_level=sup_lvl,
            candle_open=spot_price * (1.0 - price_change_pct/100.0),
            candle_high=spot_price * (1.0 + abs(price_change_pct)/200.0),
            candle_low=spot_price * (1.0 - abs(price_change_pct)/200.0),
            candle_close=spot_price,
            volume_ratio=volume_ratio,
            call_oi_change_pct=call_oi_change_pct,
            put_oi_change_pct=put_oi_change_pct,
            rsi=rsi
        )

        # 4. Dynamic ATM Strike & Premium Lookup
        strike = self.find_atm_strike(symbol, spot_price, option_chain)
        
        real_ce_ltp = 0.0
        real_pe_ltp = 0.0
        if option_chain:
            for row in option_chain:
                if float(row.get("strike", 0.0)) == strike:
                    real_ce_ltp = float(row.get("call_ltp", 0.0))
                    real_pe_ltp = float(row.get("put_ltp", 0.0))
                    break

        h3_probs = probabilities["horizon_3_candle"]
        bull_pct = h3_probs["bullish_pct"]
        bear_pct = h3_probs["bearish_pct"]

        if fake_breakout_info["is_fake_breakout"]:
            direction = "Bearish (Fake Breakout Reversal)"
            option_type = "PE"
            entry_spot = spot_price
            target_spot = round(spot_price * 0.985, 2)
            stop_spot = round(spot_price * 1.004, 2)
            est_premium = real_pe_ltp if real_pe_ltp > 0 else max(10.0, round(spot_price * 0.006, 2))
            target_premium = round(est_premium * 3.5, 2)
            stop_premium = round(est_premium * 0.5, 2)
            win_prob = 0.65
        elif fake_breakout_info["is_fake_breakdown"]:
            direction = "Bullish (Fake Breakdown Reversal)"
            option_type = "CE"
            entry_spot = spot_price
            target_spot = round(spot_price * 1.015, 2)
            stop_spot = round(spot_price * 0.996, 2)
            est_premium = real_ce_ltp if real_ce_ltp > 0 else max(10.0, round(spot_price * 0.006, 2))
            target_premium = round(est_premium * 3.5, 2)
            stop_premium = round(est_premium * 0.5, 2)
            win_prob = 0.65
        elif bull_pct > bear_pct and bull_pct > 45.0:
            direction = "Bullish"
            option_type = "CE"
            entry_spot = spot_price
            target_spot = round(spot_price * 1.015, 2)
            stop_spot = round(spot_price * 0.996, 2)
            est_premium = real_ce_ltp if real_ce_ltp > 0 else max(10.0, round(spot_price * 0.006, 2))
            target_premium = round(est_premium * 3.5, 2)
            stop_premium = round(est_premium * 0.5, 2)
            win_prob = bull_pct / 100.0
        elif bear_pct > bull_pct and bear_pct > 45.0:
            direction = "Bearish"
            option_type = "PE"
            entry_spot = spot_price
            target_spot = round(spot_price * 0.985, 2)
            stop_spot = round(spot_price * 1.004, 2)
            est_premium = real_pe_ltp if real_pe_ltp > 0 else max(10.0, round(spot_price * 0.006, 2))
            target_premium = round(est_premium * 3.5, 2)
            stop_premium = round(est_premium * 0.5, 2)
            win_prob = bear_pct / 100.0
        else:
            direction = "Neutral"
            option_type = "NONE"
            entry_spot = spot_price
            target_spot = spot_price
            stop_spot = spot_price
            est_premium = 0.0
            target_premium = 0.0
            stop_premium = 0.0
            win_prob = 0.33

        # 5. EV & Risk/Reward Engine (Strict RR >= 1:4 filter)
        ev_rr_info = self.ev_rr_engine.evaluate_trade_setup(
            entry_price=est_premium,
            target_price=target_premium,
            stop_loss_price=stop_premium,
            win_probability=win_prob
        )

        if anomaly_info["status"] == "HIGH_ANOMALY_RISK":
            final_decision = "NO_TRADE"
            decision_reason = f"High anomaly risk detected: {anomaly_info['summary']}"
        elif fake_breakout_info["has_trap"]:
            final_decision = "WAIT" if ev_rr_info["decision"] == "NO_TRADE" else ev_rr_info["decision"]
            decision_reason = fake_breakout_info["reason"]
        else:
            final_decision = ev_rr_info["decision"]
            decision_reason = ev_rr_info["reason"]

        # 6. Hero Zero Scan
        hero_zero_info = self.hero_zero_engine.evaluate_hero_zero_setup(
            symbol=symbol,
            spot_price=spot_price,
            strike_price=strike,
            option_type=option_type,
            premium=est_premium,
            days_to_expiry=days_to_expiry,
            underlying_momentum_score=rsi,
            pcr_oi=pcr_oi
        )

        # 7. HFT Microstructure Burst Detector
        hft_info = self.hft_detector.detect_burst(
            symbol=symbol, spot_price=spot_price, tick_rate_10s=tick_rate_10s, move_10s_pct=move_10s_pct, volume_ratio=volume_ratio
        )

        return {
            "symbol": symbol,
            "chain_updated_at": timestamp,
            "regime": regime_info["regime"],
            "direction": direction,
            "atm_strike": strike,
            "selected_option": f"{int(strike)} {option_type}" if option_type != "NONE" else "NO_OPTION",
            "multi_horizon_probabilities": probabilities,
            "live_trade_setup": {
                "contract": f"{symbol} {int(strike)} {option_type}",
                "option_type": option_type,
                "strike": strike,
                "entry_premium": est_premium,
                "target_premium": target_premium,
                "stop_loss_premium": stop_premium,
                "entry_spot": entry_spot,
                "target_spot": target_spot,
                "stop_loss_spot": stop_spot,
                "risk_reward_ratio": ev_rr_info["risk_reward_ratio"],
                "expected_value": ev_rr_info["expected_value"],
                "win_probability_pct": ev_rr_info["win_probability_pct"]
            },
            "anomaly_analysis": anomaly_info,
            "fake_breakout_analysis": fake_breakout_info,
            "hero_zero_analysis": hero_zero_info,
            "hft_burst_analysis": hft_info,
            "final_decision": final_decision,
            "decision_reason": decision_reason
        }
