"""Base Setup Agent Interface (Part C4)."""

from abc import ABC, abstractmethod
from typing import Any, Dict, List, Optional
from datetime import datetime
from pydantic import BaseModel


class AlertMessage(BaseModel):
    timestamp: str
    setup_name: str
    symbol: str
    stage: str  # FORMING, READY, EXIT, CANCELLED, INFO, BIAS
    mode: str   # LIVE, PAPER, BIAS, INFO, WARN
    details: Dict[str, Any]
    action_required: bool = False


class SetupAgent(ABC):
    def __init__(self, name: str, mode: str = "PAPER", symbol: str = "NIFTY"):
        self.name = name
        self.mode = mode.upper()
        self.symbol = symbol.upper()

    @abstractmethod
    def evaluate(self, context: Dict[str, Any]) -> Optional[AlertMessage]:
        """
        Evaluate current market state and return an AlertMessage if condition is met.
        context includes:
        - 'now': datetime
        - 'spot': float
        - 'vix': float
        - 'candles': DataFrame
        - 'chain': Dict
        - 'cpr': Dict
        - 'open_positions': List[Dict]
        """
        pass

    def check_exit(self, position: Dict[str, Any], context: Dict[str, Any]) -> Optional[AlertMessage]:
        """Check if an open position for this setup should exit."""
        return None
