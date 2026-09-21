import sys
import os

# Add the stock and Option directories to sys.path
root = r"C:\Users\Kunal\Desktop\Stock\stock_alert_bot"
sys.path.append(os.path.join(root, "stock"))

try:
    from utils.firebase_sync import sync_to_firestore
    print("Import successful.")

    test_data = {
        "status": "success",
        "message": "Firebase sync verified from scratch script",
        "timestamp": "test_sync"
    }

    sync_to_firestore("test_connection", "connection_status", test_data)
    print("Sync attempted. Check console for warnings or success messages.")
except Exception as e:
    print(f"Error: {e}")
