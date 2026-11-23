import math
import os
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

import firebase_admin
import psycopg
from firebase_admin import credentials, firestore
from psycopg.types.json import Jsonb

COLLECTIONS = (
    "option_sentiment",
    "live_quotes",
    "option_scans",
    "backtest_results",
    "option_backtest_history",
    "option_strategies",
)
TABLE = "option_app_daily_documents"
SYNC_TIME_ZONE = timezone(timedelta(hours=5, minutes=30), "Asia/Kolkata")


def json_safe(value):
    if isinstance(value, dict):
        return {str(key): json_safe(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [json_safe(item) for item in value]
    if isinstance(value, (datetime, date)):
        return value.isoformat()
    if hasattr(value, "path") and value.__class__.__name__ == "DocumentReference":
        return value.path
    if hasattr(value, "latitude") and hasattr(value, "longitude"):
        return {"latitude": value.latitude, "longitude": value.longitude}
    if isinstance(value, bytes):
        return value.hex()
    if isinstance(value, float) and not math.isfinite(value):
        return None
    if value is None or isinstance(value, (str, int, float, bool)):
        return value
    raise TypeError(f"Unsupported Firestore value type: {type(value).__name__}")


def record_from_snapshot(snapshot):
    parts = snapshot.reference.path.split("/")
    if len(parts) < 2:
        raise ValueError(f"Unexpected Firestore document path: {snapshot.reference.path}")
    collection_name = parts[-2]
    user_id = parts[1] if len(parts) >= 4 and parts[0] == "users" else ""
    return collection_name, snapshot.id, user_id, json_safe(snapshot.to_dict() or {})


def create_table(connection):
    connection.execute(f"""
        CREATE TABLE IF NOT EXISTS {TABLE} (
            collection_name TEXT NOT NULL,
            document_id TEXT NOT NULL,
            user_id TEXT NOT NULL DEFAULT '',
            snapshot_date DATE NOT NULL,
            payload JSONB NOT NULL,
            synced_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            PRIMARY KEY (collection_name, document_id, user_id, snapshot_date)
        )
    """)
    connection.execute(
        f"CREATE INDEX IF NOT EXISTS idx_{TABLE}_snapshot_date ON {TABLE}(snapshot_date)"
    )


def sync_collection(db, connection, collection_name, snapshot_date):
    count = 0
    for snapshot in db.collection_group(collection_name).stream():
        collection, document_id, user_id, payload = record_from_snapshot(snapshot)
        connection.execute(
            f"""
            INSERT INTO {TABLE}(
                collection_name, document_id, user_id, snapshot_date, payload, synced_at
            )
            VALUES (%s, %s, %s, %s, %s, now())
            ON CONFLICT(collection_name, document_id, user_id, snapshot_date)
            DO UPDATE SET payload = EXCLUDED.payload, synced_at = now()
            """,
            (collection, document_id, user_id, snapshot_date, Jsonb(payload)),
        )
        count += 1
    return count


def initialize_firebase():
    if firebase_admin._apps:
        return firestore.client()
    credential_path = os.environ.get("GOOGLE_APPLICATION_CREDENTIALS")
    if not credential_path:
        raise RuntimeError("Set GOOGLE_APPLICATION_CREDENTIALS to a Firebase service-account file.")
    if not Path(credential_path).is_file():
        raise FileNotFoundError("The configured Firebase credential file does not exist.")
    firebase_admin.initialize_app(credentials.Certificate(credential_path))
    return firestore.client()


def main():
    database_url = os.environ.get("OPTION_APP_DATABASE_URL")
    if not database_url:
        raise RuntimeError("Set OPTION_APP_DATABASE_URL to the local option_app PostgreSQL database.")
    db = initialize_firebase()
    snapshot_date = datetime.now(SYNC_TIME_ZONE).date()
    with psycopg.connect(database_url) as connection:
        create_table(connection)
        counts = {
            collection_name: sync_collection(db, connection, collection_name, snapshot_date)
            for collection_name in COLLECTIONS
        }
    total = sum(counts.values())
    print(
        f"Option app: saved {total} documents for {snapshot_date} (Asia/Kolkata): {counts}",
        flush=True,
    )


if __name__ == "__main__":
    main()
