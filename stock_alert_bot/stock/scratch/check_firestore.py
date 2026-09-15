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
    doc = db.collection('option_sentiment').document('NIFTY').get()
    if doc.exists:
        data = doc.to_dict()
        print("Document keys:", list(data.keys()))
        print("fetched_at:", data.get('fetched_at'))
        print("final_signal:", data.get('final_signal'))
        print("spot_price:", data.get('spot_price'))
    else:
        print("Document NIFTY does not exist in collection option_sentiment")

if __name__ == "__main__":
    check()
