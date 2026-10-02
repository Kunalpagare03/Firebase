"""
Quant Engine Package (Option Engine)
Consolidates Regime Detection, Multi-Horizon Probabilities, EV/RR Filtering, Anomaly Detection, Fake Breakout Detection, Hero Zero, and HFT Burst scanning.
"""
from .master_quant_agent import MasterQuantAgent
from .regime_detector import RegimeDetector
from .ev_rr_engine import EvRrEngine
from .anomaly_detector import AnomalyDetector
from .hero_zero import HeroZeroEngine
from .hft_burst import HftBurstDetector
from .fake_breakout_detector import FakeBreakoutDetector

__all__ = [
    "MasterQuantAgent",
    "RegimeDetector",
    "EvRrEngine",
    "AnomalyDetector",
    "HeroZeroEngine",
    "HftBurstDetector",
    "FakeBreakoutDetector"
]
