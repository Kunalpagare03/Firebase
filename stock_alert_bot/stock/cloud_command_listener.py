import firebase_admin
from firebase_admin import credentials, firestore
import os
import time
import subprocess
import sys
import threading

# Setup Path
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
VENV_PYTHON = os.path.join(ROOT, ".venv", "Scripts", "python.exe")
PYTHON_EXE = VENV_PYTHON if os.path.exists(VENV_PYTHON) else sys.executable

# Mutex to avoid running concurrent scans
RUN_LOCK = threading.Lock()
ACTIVE_PROCESS = None

def run_task(task_type, db=None, user_id=None):
    global ACTIVE_PROCESS
    with RUN_LOCK:
        if ACTIVE_PROCESS is not None and ACTIVE_PROCESS.poll() is None:
            print(f"[Listener] Task '{task_type}' ignored: a previous scan (PID {ACTIVE_PROCESS.pid}) is still running.")
            if db and user_id and task_type in ("stock", "stock_scan"):
                db.collection("users").document(user_id).collection("stock_scans").document("status").set({
                    "status": "Scan service busy",
                    "is_scanning": False,
                    "progress": 0,
                    "updated_at_epoch": time.time(),
                    "results": "Another full-universe scan is already running. Please retry when it finishes.",
                }, merge=True)
            return False

        print(f"--- Triggering Manual Run: {task_type} ---")
        log_file = os.path.join(ROOT, "logs", f"manual_{task_type}.log")
        os.makedirs(os.path.dirname(log_file), exist_ok=True)

        if task_type in ("stock", "stock_scan"):
            cmd = [PYTHON_EXE, "live_stock_scanner.py"]
            if user_id:
                cmd += ["--user-id", user_id]
            cwd = os.path.join(ROOT, "stock")
            status_desc = "Running stock scan..."
        elif task_type in ("option", "option_scan"):
            # Use the same production pipeline as the scheduled service so a
            # mobile refresh publishes chart, Greeks and deep-analysis fields.
            cmd = [PYTHON_EXE, os.path.join("scripts", "cloud_market_runner.py"), "--once"]
            cwd = os.path.join(ROOT, "Option")
            status_desc = "Updating option chain & sentiment..."
        else:
            print(f"[Listener] Unknown task type: {task_type}")
            return False

        status_collection = "option_scans" if task_type in ("option", "option_scan") else "stock_scans"
        status_ref = (
            db.collection("users").document(user_id).collection("stock_scans").document("status")
            if db and user_id and task_type in ("stock", "stock_scan")
            else db.collection(status_collection).document("status") if db else None
        )
        if db:
            try:
                status_ref.set({
                    "status": status_desc,
                    "is_scanning": True,
                    "progress": 5,
                    "updated_at_epoch": time.time(),
                    "start_time": time.strftime("%H:%M:%S")
                }, merge=True)
            except Exception as e:
                print(f"[Listener] Warning updating status: {e}")

        try:
            log_f = open(log_file, "a")
            log_f.write(f"\n--- Manual Run Initiated at {time.ctime()} ---\n")
            log_f.flush()
            ACTIVE_PROCESS = subprocess.Popen(cmd, cwd=cwd, stdout=log_f, stderr=log_f)
            print(f"Started Process ID: {ACTIVE_PROCESS.pid} for {task_type}")

            def monitor_completion(proc, f, t_type):
                """Always release the mobile UI, including after a failed stock scan."""
                proc.wait()
                f.close()
                succeeded = proc.returncode == 0
                print(f"Process {proc.pid} ({t_type}) completed with return code {proc.returncode}")
                if db:
                    result = "Option Sentiment Updated" if t_type in ("option", "option_scan") else "Stock scan updated"
                    status_ref.set({
                        "status": "Idle / Complete" if succeeded else "Idle / Scan failed",
                        "is_scanning": False,
                        "progress": 100 if succeeded else 0,
                        "updated_at_epoch": time.time(),
                        "last_scan_time": time.strftime("%d-%m-%Y %H:%M:%S"),
                        "results": result if succeeded else f"{t_type} scan exited with code {proc.returncode}"
                    }, merge=True)

            threading.Thread(target=monitor_completion, args=(ACTIVE_PROCESS, log_f, task_type), daemon=True).start()
            return True
        except Exception as e:
            print(f"Error starting {task_type}: {e}")
            if db:
                try:
                    status_ref.set({
                        "status": "Idle / Scan failed",
                        "is_scanning": False,
                        "progress": 0,
                        "updated_at_epoch": time.time(),
                        "results": f"Could not start {task_type}: {type(e).__name__}",
                    }, merge=True)
                except Exception as status_error:
                    print(f"[Listener] Could not clear failed scan status: {status_error}")
            return False

def listen():
    print("Starting Cloud Command Listener...")
    print(f"Using Python: {PYTHON_EXE}")

    possible_creds = [
        os.path.join(ROOT, "service-account.json"),
        os.path.join(ROOT, "..", "service-account.json"),
        "service-account.json"
    ]
    cred_path = next((p for p in possible_creds if os.path.exists(p)), None)
    if not firebase_admin._apps:
        if cred_path:
            cred = credentials.Certificate(cred_path)
            firebase_admin.initialize_app(cred)
        else:
            firebase_admin.initialize_app()

    db = firestore.client()
    commands_ref = db.collection("commands")

    def on_snapshot(doc_snapshot, changes, read_time):
        for doc in doc_snapshot:
            data = doc.to_dict()
            if not data: continue

            command = data.get("command")
            timestamp = data.get("timestamp")
            processed = data.get("processed", False)

            if command and not processed:
                user_id = doc.id
                print(f"[Listener] Received Command: {command} for user {user_id} at {timestamp}")
                started = run_task(command, db=db, user_id=user_id)
                doc.reference.update({
                    "processed": True,
                    "status": "started" if started else "busy",
                    "processed_at": time.strftime("%Y-%m-%d %H:%M:%S")
                })

    commands_ref.on_snapshot(on_snapshot)
    print("Listening for per-user cloud commands...")

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("Listener stopped.")

if __name__ == "__main__":
    listen()
