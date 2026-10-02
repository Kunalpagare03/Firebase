import logging
import os
from datetime import datetime

def setup_logger():
    os.makedirs("logs", exist_ok=True)
    timestamp = datetime.now().strftime("%Y-%m-%d")
    log_file = f"logs/analysis_{timestamp}.log"

    logging.basicConfig(
        filename=log_file,
        level=logging.INFO,
        format="%(asctime)s - %(levelname)s - %(message)s",
    )

    console = logging.StreamHandler()
    console.setLevel(logging.INFO)
    formatter = logging.Formatter("%(levelname)s - %(message)s")
    console.setFormatter(formatter)
    logging.getLogger().addHandler(console)

    logging.info("Logger initialized.")
    return logging.getLogger()
