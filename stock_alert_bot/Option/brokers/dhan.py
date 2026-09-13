"""
Scaffold for Dhan broker connector.
Dhan's public API requires an API key/secret and an auth flow. This scaffold
provides placeholders — fill endpoints and parameters per Dhan's SDK/docs.
DO NOT commit credentials to source control.
"""
import os
import logging
import requests

DHAN_API_KEY = os.environ.get("DHAN_API_KEY")
DHAN_API_SECRET = os.environ.get("DHAN_API_SECRET")
DHAN_ACCESS_TOKEN = os.environ.get("DHAN_ACCESS_TOKEN")

log = logging.getLogger("brokers.dhan")

BASE_URL = "https://api.dhan.com"  # <-- replace with actual Dhan API base
BASE_URL = "https://api.dhan.co/v2"


def _prefer_sdk_client():
	"""Return an SDK client if an official Dhan SDK is installed, else None."""
	for modname in ("dhan", "dhanhq", "DhanHQ"):
		try:
			mod = __import__(modname)
			return mod
		except Exception:
			continue
	return None


def authenticate(api_key=None, api_secret=None, access_token=None):
	"""Authenticate with Dhan and return an access token or token container.

	Behavior:
	  - If `access_token` provided, returns a dict {"access_token": ...}.
	  - If an official SDK is installed, prefer it (user must configure per SDK docs).
	  - Otherwise perform a REST token request to the token endpoint. Update the
		token endpoint path if Dhan's docs specify a different route.
	"""
	api_key = api_key or DHAN_API_KEY
	api_secret = api_secret or DHAN_API_SECRET
	access_token = access_token or DHAN_ACCESS_TOKEN

	if access_token:
		return {"access_token": access_token}

	sdk = _prefer_sdk_client()
	if sdk:
		# The SDK usage varies; user should refer to https://github.com/dhan-oss/DhanHQ-py
		# and initialize the SDK with their api_key/secret. We return the SDK module
		# or an initialized client if the module exposes such a helper.
		return {"sdk_module": sdk}

	if not api_key or not api_secret:
		raise ValueError("DHAN_API_KEY and DHAN_API_SECRET must be set as env vars or passed in")

	# REST token request -- placeholder endpoint. Confirm with Dhan docs if different.
	token_url = f"{BASE_URL}/auth/token"
	resp = requests.post(token_url, json={"api_key": api_key, "api_secret": api_secret}, timeout=10)
	resp.raise_for_status()
	data = resp.json()
	token = data.get("access_token") or data.get("token")
	if not token:
		raise RuntimeError("Failed to obtain access token from Dhan API; check credentials and endpoint")
	return {"access_token": token}


def place_order(client_token, tradingsymbol, quantity, side="BUY", order_type="MARKET", price=None, dry_run=True):
	"""Place an order via Dhan REST API v2. Returns order response or payload for dry run.

	`client_token` may be:
	  - a dict containing `access_token` from `authenticate()`
	  - a string access token
	  - an SDK client module (if using SDK mode)
	"""
	# support SDK module shortcut
	if isinstance(client_token, dict) and client_token.get("sdk_module"):
		# User has SDK module; they should implement order placement via SDK.
		raise RuntimeError("SDK mode detected: use the Dhan SDK to place orders (see README)")

	token = client_token if isinstance(client_token, str) else client_token.get("access_token") if isinstance(client_token, dict) else None

	payload = {
		"tradingsymbol": tradingsymbol,
		"quantity": quantity,
		"side": side,
		"order_type": order_type,
	}
	if price is not None:
		payload["price"] = price

	log.info("Dhan order payload: %s", payload)
	if dry_run:
		return {"dry_run": True, "payload": payload}

	if not token:
		raise ValueError("No access token provided for live order")

	order_url = f"{BASE_URL}/orders"
	headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
	resp = requests.post(order_url, json=payload, headers=headers, timeout=10)
	resp.raise_for_status()
	return resp.json()


def get_order(order_id, token):
	"""Fetch order status by `order_id` from Dhan API v2."""
	headers = {"Authorization": f"Bearer {token}"}
	resp = requests.get(f"{BASE_URL}/orders/{order_id}", headers=headers, timeout=10)
	resp.raise_for_status()
	return resp.json()


def cancel_order(order_id, token):
	headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
	resp = requests.delete(f"{BASE_URL}/orders/{order_id}", headers=headers, timeout=10)
	resp.raise_for_status()
	return resp.json()


def get_positions(token):
	headers = {"Authorization": f"Bearer {token}"}
	resp = requests.get(f"{BASE_URL}/positions", headers=headers, timeout=10)
	resp.raise_for_status()
	return resp.json()
