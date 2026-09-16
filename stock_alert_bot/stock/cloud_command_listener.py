import firebase_admin
from firebase_admin import credentials, firestore
import os
import time
import subprocess
import sys

# Setup Path
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
# Force use of .venv python to ensure environment consistency
PYTHON_EXE = os.path.join(ROOT, ".venv", "Scripts", "python.exe")

def run_task(task_type):
    print(f"--- Triggering Manual Run: {task_type} ---")
    if task_type == "stock":
        cmd = [PYTHON_EXE, "live_stock_scanner.py"]
        cwd = os.path.join(ROOT, "stock")
    elif task_type == "option":
        cmd = [PYTHON_EXE, "step7_dashboard.py"]
        cwd = os.path.join(ROOT, "Option")
    else:
        return

    try:
        # Use shell=False for stability, and capture output in logs
        log_file = os.path.join(ROOT, "logs", f"manual_{task_type}.log")
        os.makedirs(os.path.dirname(log_file), exist_ok=True)

        with open(log_file, "a") as f:
            f.write(f"\n--- Manual Run Started at {time.ctime()} ---\n")
            subprocess.Popen(cmd, cwd=cwd, stdout=f, stderr=f)

        print(f"Successfully started {task_type} scan. Logs: {log_file}")
    except Exception as e:
        print(f"Error starting {task_type}: {e}")

def listen():
    print("Starting Cloud Command Listener...")
    print(f"Using Python: {PYTHON_EXE}")

    cred_path = os.path.join(ROOT, "service-account.json")
    if not firebase_admin._apps:
        cred = credentials.Certificate(cred_path)
        firebase_admin.initialize_app(cred)

    db = firestore.client()
    doc_ref = db.collection("commands").document("latest")

    def on_snapshot(doc_snapshot, changes, read_time):
        for doc in doc_snapshot:
            data = doc.to_dict()
            if not data: continue

            command = data.get("command")
            timestamp = data.get("timestamp")
            processed = data.get("processed", False)

            if command and not processed:
                print(f"Received Command: {command} at {timestamp}")
                run_task(command)
                # Mark as processed
                doc_ref.update({"processed": True})

    doc_ref.on_snapshot(on_snapshot)

    while True:
        time.sleep(1)

if __name__ == "__main__":
    listen()
