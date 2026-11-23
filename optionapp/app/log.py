"""Structured logging configuration for Option App v8."""

import logging
import os
import sys
from datetime import datetime
from pathlib import Path

_logger = None


def setup_logger(name: str = "optionapp", log_dir: str = "logs", level: str = "INFO") -> logging.Logger:
    global _logger
    if _logger is not None:
        return _logger

    logger = logging.getLogger(name)
    numeric_level = getattr(logging, level.upper(), logging.INFO)
    logger.setLevel(numeric_level)
    logger.propagate = False

    if not logger.handlers:
        formatter = logging.Formatter(
            fmt="%(asctime)s [%(levelname)s] [%(name)s] %(message)s",
            datefmt="%Y-%m-%d %H:%M:%S",
        )

        # Console handler
        console_handler = logging.StreamHandler(sys.stdout)
        console_handler.setLevel(numeric_level)
        console_handler.setFormatter(formatter)
        logger.addHandler(console_handler)

        # File handler
        try:
            log_path = Path(log_dir)
            log_path.mkdir(parents=True, exist_ok=True)
            log_file = log_path / f"{datetime.now().strftime('%Y%m%d')}.log"
            file_handler = logging.FileHandler(str(log_file), encoding="utf-8")
            file_handler.setLevel(numeric_level)
            file_handler.setFormatter(formatter)
            logger.addHandler(file_handler)
        except Exception as e:
            logger.warning(f"Failed to setup file logging: {e}")

    _logger = logger
    return logger


def get_logger(name: str = "optionapp") -> logging.Logger:
    global _logger
    if _logger is None:
        return setup_logger(name)
    return _logger
