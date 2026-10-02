import firebase_admin
from firebase_admin import credentials, firestore
import os

def check():
    possible_paths = [
        os.path.join(os.path.dirname(__file__), '..', '..', 'service-account.json'),
        'service-account.json'
    ]
    cred = None
    for path in possible_paths:
        if os.path.exists(path):
            cred = credentials.Certificate(path)
            break
    if cred:
        firebase_admin.initialize_app(cred)
    else:
        firebase_admin.initialize_app()

    db = firestore.client()
    doc = db.collection('stock_scans').document('comprehensive').get()
    if doc.exists:
        data = doc.to_dict()
        print("Document keys:", list(data.keys()))
        print("Timestamp:", data.get('timestamp'))
        print("Intraday count:", len(data.get('intraday', [])))
        print("Swing count:", len(data.get('swing', [])))
        print("Positional count:", len(data.get('positional', [])))
        if data.get('intraday'):
            print("Sample Intraday:", data['intraday'][0].get('symbol'), data['intraday'][0].get('entry_price'))
    else:
        print("Document comprehensive does not exist in collection stock_scans")

if __name__ == "__main__":
    check()
