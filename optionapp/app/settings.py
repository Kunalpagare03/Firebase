"""Configuration loader and schema validation for Option App v8."""

import os
from pathlib import Path
from typing import Any, Dict, List, Optional
import yaml
from pydantic import BaseModel, Field


class AppConfig(BaseModel):
    name: str = "Option Alert App"
    version: str = "8.0"
    timezone: str = "Asia/Kolkata"
    log_level: str = "INFO"


class CapitalConfig(BaseModel):
    total_inr: float = 50000.0
    ladder_stage: int = 1
    risk_per_trade_pct: float = 0.015
    daily_loss_limit_pct: float = 0.03
    max_structure_loss_pct: float = 0.05
    max_open_trades: int = 3
    max_trades_per_day: int = 3
    stale_data_timeout_seconds: int = 60


class LotsConfig(BaseModel):
    NIFTY: int = 65
    BANKNIFTY: int = 30
    FINNIFTY: int = 60
    MIDCPNIFTY: int = 120
    SENSEX: int = 20


class StrikeStepsConfig(BaseModel):
    NIFTY: int = 50
    BANKNIFTY: int = 100
    FINNIFTY: int = 50
    MIDCPNIFTY: int = 25
    SENSEX: int = 100


class ChargesConfig(BaseModel):
    brokerage_per_order: float = 20.0
    stt_sell_options_pct: float = 0.001
    exchange_txn_bse_pct: float = 0.0005
    exchange_txn_nse_pct: float = 0.00035
    sebi_turnover_pct: float = 0.000001
    stamp_duty_buy_pct: float = 0.00003
    gst_pct: float = 0.18
    slippage_points: float = 1.5


class CalmModeConfig(BaseModel):
    enabled: bool = True
    mask_pnl_until: str = "15:35"
    allowed_peek_command: str = "/peek"
    log_peeks: bool = True


class RadarConfig(BaseModel):
    enabled: bool = True
    auto_switch_to_paper_on_rule_change: bool = True
    check_time: str = "18:30"
    official_lot_sources: List[str] = ["NSE_CONTRACT_SPECS", "BSE_CONTRACT_SPECS"]


class GhostLedgerConfig(BaseModel):
    enabled: bool = True
    log_skipped_ready: bool = True
    mtm_at_eod: bool = True
    track_reasons: List[str] = [
        "LOW_VIX_SKIP",
        "DAILY_LOSS_LIMIT",
        "CAPITAL_LADDER_RESTRICTION",
        "USER_MANUAL_SKIP",
        "MAX_OPEN_TRADES_REACHED",
    ]


class ProviderConfig(BaseModel):
    primary: str = "angel"
    fallback: str = "nse"
    angel_credentials_file: str = "../stock_alert_bot/Option/config/credentials.json"
    dhan_credentials_file: str = "../stock_alert_bot/Option/config/dhan_config.json"


class NotifyTelegramConfig(BaseModel):
    enabled: bool = False
    bot_token: str = ""
    chat_id: str = ""


class ToggleConfig(BaseModel):
    enabled: bool = True


class NotifyConfig(BaseModel):
    telegram: NotifyTelegramConfig = NotifyTelegramConfig()
    sound: Any = ToggleConfig()
    desktop_notification: Any = ToggleConfig()


class Settings(BaseModel):
    app: AppConfig = AppConfig()
    capital: CapitalConfig = CapitalConfig()
    lots: LotsConfig = LotsConfig()
    strike_steps: StrikeStepsConfig = StrikeStepsConfig()
    charges: ChargesConfig = ChargesConfig()
    calm_mode: CalmModeConfig = CalmModeConfig()
    radar: RadarConfig = RadarConfig()
    ghost_ledger: GhostLedgerConfig = GhostLedgerConfig()
    provider: ProviderConfig = ProviderConfig()
    setups: Dict[str, Any] = Field(default_factory=dict)
    notify: NotifyConfig = NotifyConfig()


_settings_instance: Optional[Settings] = None


def find_config_path() -> Path:
    # Look in current dir, or parent dirs
    candidates = [
        Path("config.yaml"),
        Path("optionapp/config.yaml"),
        Path(__file__).resolve().parent.parent / "config.yaml",
    ]
    for c in candidates:
        if c.exists():
            return c.resolve()
    return Path("config.yaml")


def load_settings(config_path: Optional[str | Path] = None) -> Settings:
    global _settings_instance
    if _settings_instance is not None and config_path is None:
        return _settings_instance

    path = Path(config_path) if config_path else find_config_path()
    if path.exists():
        with open(path, "r", encoding="utf-8") as f:
            raw = yaml.safe_load(f) or {}
        _settings_instance = Settings(**raw)
    else:
        _settings_instance = Settings()
    return _settings_instance


def get_settings() -> Settings:
    return load_settings()
