"""Notification Dispatcher: Telegram, Windows Sound, Console (Part C4, F2)."""

import sys
from typing import Optional
import requests
from optionapp.app.log import get_logger

logger = get_logger("notify")


class NotificationManager:
    def __init__(self, telegram_token: str = "", chat_id: str = "", sound_enabled: bool = True):
        self.telegram_token = telegram_token
        self.chat_id = chat_id
        self.sound_enabled = sound_enabled

    def play_sound(self):
        """Play short audio beep on Windows."""
        if not self.sound_enabled:
            return
        try:
            if sys.platform == "win32":
                import winsound
                winsound.Beep(1000, 250)
        except Exception:
            pass

    def send_telegram(self, message: str) -> bool:
        """Send message via Telegram Bot API."""
        if not self.telegram_token or not self.chat_id:
            return False

        url = f"https://api.telegram.org/bot{self.telegram_token}/sendMessage"
        payload = {"chat_id": self.chat_id, "text": message, "parse_mode": "Markdown"}
        try:
            r = requests.post(url, json=payload, timeout=8)
            return r.status_code == 200
        except Exception as e:
            logger.error(f"Telegram dispatch failed: {e}")
            return False

    def alert(self, title: str, message: str, stage: str = "READY"):
        """Dispatch notification across all enabled channels."""
        self.play_sound()
        formatted = f"🚨 *[{stage}] {title}*\n{message}"
        logger.info(f"NOTIFICATION [{stage}]: {title} - {message}")
        self.send_telegram(formatted)
