import os
from pathlib import Path
from typing import Optional, Dict, Any
from pydantic import BaseModel, Field
import yaml

class LimitsConfig(BaseModel):
    max_open_trades: int = 10
    max_swing_trades: int = 4
    max_positional_trades: int = 6
    max_per_sector: int = 2
    max_new_per_night: int = 3

class ExitsConfig(BaseModel):
    swing_max_days: int = 5
    positional_max_days: int = 22
    trailing_stop_profit_trigger: float = 0.08
    trailing_stop_step: float = 0.02

class TrafficLightConfig(BaseModel):
    mode: str = "INFO"
    vix_panic_threshold: float = 22.0
    breadth_panic_threshold: float = 30.0
    nifty_symbol: str = "^NSEI"
    vix_symbol: str = "^INDIAVIX"

class ScannerConfig(BaseModel):
    min_price: float = 50.0
    min_turnover_cr: float = 5.0
    results_veto_days: int = 2
    min_swing_tech_score: float = 55.0
    leader_tech_score: float = 70.0

class LongTermConfig(BaseModel):
    min_roce_5yr_avg: float = 15.0
    min_roce_single_year: float = 10.0
    min_sales_growth_3yr: float = 10.0
    min_profit_growth_3yr: float = 10.0
    max_debt_to_equity: float = 0.5
    min_cfo_to_pat: float = 0.70
    allocation_parts: int = 3

class AiConfig(BaseModel):
    enabled: bool = True
    provider: str = "gemini"
    model: str = "gemini-2.5-flash"
    monthly_budget_usd: float = 5.0
    max_nightly_tokens: int = 4000

class AppSettings(BaseModel):
    capital: float = 1000000.0
    risk_per_trade: float = 0.01
    initial_stop_pct: float = 0.08
    max_entry_gap_pct: float = 0.03
    limits: LimitsConfig = Field(default_factory=LimitsConfig)
    exits: ExitsConfig = Field(default_factory=ExitsConfig)
    traffic_light: TrafficLightConfig = Field(default_factory=TrafficLightConfig)
    scanner: ScannerConfig = Field(default_factory=ScannerConfig)
    long_term: LongTermConfig = Field(default_factory=LongTermConfig)
    ai: AiConfig = Field(default_factory=AiConfig)

    # Runtime path properties
    base_dir: Path = Field(default_factory=lambda: Path(__file__).resolve().parent.parent)

    @property
    def data_dir(self) -> Path:
        return self.base_dir / "data"

    @property
    def prices_dir(self) -> Path:
        return self.data_dir / "prices"

    @property
    def nse_dir(self) -> Path:
        return self.data_dir / "nse"

    @property
    def screener_dir(self) -> Path:
        return self.data_dir / "screener"

    @property
    def db_dir(self) -> Path:
        return self.base_dir / "db"

    @property
    def db_path(self) -> Path:
        return self.db_dir / "stockapp.sqlite"

    @property
    def reports_dir(self) -> Path:
        return self.base_dir / "reports"

    @property
    def logs_dir(self) -> Path:
        return self.base_dir / "logs"

def load_settings(config_path: Optional[str] = None) -> AppSettings:
    if config_path is None:
        default_config = Path(__file__).resolve().parent.parent / "config.yaml"
        if default_config.exists():
            config_path = str(default_config)
    
    if config_path and Path(config_path).exists():
        with open(config_path, "r", encoding="utf-8") as f:
            raw = yaml.safe_load(f) or {}
            return AppSettings(**raw)
    return AppSettings()

settings = load_settings()
