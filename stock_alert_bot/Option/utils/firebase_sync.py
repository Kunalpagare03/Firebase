import firebase_admin
from firebase_admin import credentials, firestore
import os
import json

def sync_to_firestore(collection_name, document_id, data):
    """
    Syncs data to a Firestore collection.
    Expects a service-account.json in the project root or GOOGLE_APPLICATION_CREDENTIALS env var.
    """
    if not firebase_admin._apps:
        # Try to find credentials in common locations
        possible_paths = [
            os.path.join(os.path.dirname(__file__), '..', '..', '..', 'service-account.json'),
            os.path.join(os.path.dirname(__file__), '..', '..', 'service-account.json'),
            'service-account.json'
        ]

        cred = None
        for path in possible_paths:
            if os.path.exists(path):
                cred = credentials.Certificate(path)
                break

        try:
            if cred:
                firebase_admin.initialize_app(cred)
            else:
                firebase_admin.initialize_app()
        except Exception as e:
            print(f"Warning: Firebase not initialized. Data not synced. Ensure service-account.json exists or GOOGLE_APPLICATION_CREDENTIALS is set. Error: {e}")
            return

    try:
        db = firestore.client()
        db.collection(collection_name).document(document_id).set(data)
        print(f"Synced to Firestore: {collection_name}/{document_id}")
    except Exception as e:
        print(f"Error syncing to Firestore: {e}")
