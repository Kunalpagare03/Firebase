import hashlib
import ipaddress
import json
import logging
import os
import re
import socket
import ssl
import threading
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlsplit

import firebase_admin
import psycopg
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID
from firebase_admin import credentials
from google.auth import jwt
from psycopg.types.json import Jsonb
from zeroconf import IPVersion, ServiceInfo, Zeroconf

SERVICE_TYPE = "_kunalstocksync._tcp.local."
SERVICE_NAME = "Kunal Stock Sync._kunalstocksync._tcp.local."
PORT = 8766
COLLECTIONS = frozenset(("stock_strategies", "stock_backtest_history"))
TABLE = "stock_app_live_documents"
MAX_BODY_BYTES = 512 * 1024
CERT_CACHE_MAX_AGE = 7 * 24 * 60 * 60
CERT_URL = "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com"
LOG = logging.getLogger("kunal.stock.local-sync")
STATE_DIR = Path(os.environ.get("LOCALAPPDATA", Path.home())) / "PostgreSQL" / "17" / "api" / "stock"


def discover_wifi_ip():
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
        probe.connect(("8.8.8.8", 80))
        address = probe.getsockname()[0]
    if address.startswith("127.") or address.startswith("169.254."):
        raise RuntimeError("No usable Wi-Fi IPv4 address is available.")
    return address


def ensure_tls_certificate(address):
    STATE_DIR.mkdir(parents=True, exist_ok=True)
    key_path = STATE_DIR / "server-key.pem"
    cert_path = STATE_DIR / "server-cert.pem"
    if key_path.is_file() and cert_path.is_file():
        return cert_path, key_path

    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    now = datetime.now(timezone.utc)
    subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Kunal Stock Local Sync")])
    certificate = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(subject)
        .public_key(private_key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - timedelta(minutes=5))
        .not_valid_after(now + timedelta(days=3650))
        .add_extension(x509.SubjectAlternativeName([x509.IPAddress(ipaddress.ip_address(address))]), critical=False)
        .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
        .sign(private_key, hashes.SHA256())
    )
    key_path.write_bytes(
        private_key.private_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PrivateFormat.PKCS8,
            encryption_algorithm=serialization.NoEncryption(),
        )
    )
    cert_path.write_bytes(certificate.public_bytes(serialization.Encoding.PEM))
    digest = hashlib.sha256(
        private_key.public_key().public_bytes(
            serialization.Encoding.DER,
            serialization.PublicFormat.SubjectPublicKeyInfo,
        )
    ).hexdigest()
    LOG.warning("Pin this Stock local sync certificate SPKI SHA-256 in the Android app: %s", digest)
    return cert_path, key_path


def get_project_id():
    if firebase_admin._apps:
        app = firebase_admin.get_app()
    else:
        credential_path = os.environ.get("GOOGLE_APPLICATION_CREDENTIALS")
        if not credential_path or not Path(credential_path).is_file():
            raise RuntimeError("GOOGLE_APPLICATION_CREDENTIALS must point to the Firebase service-account file.")
        app = firebase_admin.initialize_app(credentials.Certificate(credential_path))
    if not app.project_id:
        raise RuntimeError("Firebase project ID is missing from the configured service account.")
    return app.project_id


class FirebaseTokenVerifier:
    def __init__(self, project_id):
        self.project_id = project_id
        self.cache_path = STATE_DIR / "firebase-public-certs.json"
        self.lock = threading.Lock()
        self.certificates = None
        self.fetched_at = 0.0
        self._load_cache()

    def _load_cache(self):
        try:
            cache = json.loads(self.cache_path.read_text(encoding="utf-8"))
            self.certificates = cache["certificates"]
            self.fetched_at = float(cache["fetched_at"])
        except (OSError, ValueError, KeyError, TypeError):
            self.certificates = None
            self.fetched_at = 0.0

    def _refresh(self):
        request = urllib.request.Request(CERT_URL, headers={"Cache-Control": "no-cache"})
        with urllib.request.urlopen(request, timeout=5) as response:
            certificates = json.loads(response.read(256 * 1024).decode("utf-8"))
        if not isinstance(certificates, dict) or not certificates:
            raise RuntimeError("Google returned an invalid Firebase signing certificate set.")
        self.certificates = certificates
        self.fetched_at = time.time()
        self.cache_path.parent.mkdir(parents=True, exist_ok=True)
        temporary = self.cache_path.with_suffix(".tmp")
        temporary.write_text(
            json.dumps({"fetched_at": self.fetched_at, "certificates": certificates}),
            encoding="utf-8",
        )
        temporary.replace(self.cache_path)

    def verify(self, token):
        with self.lock:
            if self.certificates is None:
                try:
                    self._refresh()
                except (OSError, urllib.error.URLError, TimeoutError, ValueError):
                    raise PermissionError("Firebase signing keys are unavailable on this PC.") from None
            elif time.time() - self.fetched_at > 24 * 60 * 60:
                try:
                    self._refresh()
                except (OSError, urllib.error.URLError, TimeoutError, ValueError):
                    if time.time() - self.fetched_at > CERT_CACHE_MAX_AGE:
                        raise PermissionError("Cached Firebase signing keys have expired.") from None

            try:
                claims = jwt.decode(token, self.certificates, audience=self.project_id)
            except Exception:
                raise PermissionError("Firebase ID token is invalid or expired.") from None

        if claims.get("iss") != f"https://securetoken.google.com/{self.project_id}":
            raise PermissionError("Firebase ID token issuer is invalid.")
        user_id = claims.get("sub")
        if not isinstance(user_id, str) or not user_id or len(user_id) > 128:
            raise PermissionError("Firebase ID token has no valid user ID.")
        return user_id

    def ensure_certificates(self):
        with self.lock:
            if self.certificates is None or time.time() - self.fetched_at > CERT_CACHE_MAX_AGE:
                try:
                    self._refresh()
                except (OSError, urllib.error.URLError, TimeoutError, ValueError):
                    if self.certificates is None or time.time() - self.fetched_at > CERT_CACHE_MAX_AGE:
                        raise RuntimeError("Fresh Firebase signing keys are required before local app writes can be accepted.")


def ensure_table():
    database_url = os.environ.get("STOCK_APP_DATABASE_URL")
    if not database_url:
        raise RuntimeError("STOCK_APP_DATABASE_URL is not configured.")
    for attempt in range(20):
        try:
            with psycopg.connect(database_url, connect_timeout=3) as connection:
                connection.execute(f"""
                    CREATE TABLE IF NOT EXISTS {TABLE} (
                        collection_name TEXT NOT NULL,
                        document_id TEXT NOT NULL,
                        user_id TEXT NOT NULL,
                        payload JSONB NOT NULL,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                        PRIMARY KEY (collection_name, document_id, user_id)
                    )
                """)
            return
        except psycopg.OperationalError:
            if attempt == 19:
                raise
            time.sleep(3)


def make_handler(token_verifier):
    class RequestHandler(BaseHTTPRequestHandler):
        server_version = "KunalStockSync/1"
        sys_version = ""

        def log_message(self, fmt, *args):
            LOG.info("%s - %s", self.client_address[0], fmt % args)

        def send_json(self, status, value):
            body = json.dumps(value, separators=(",", ":")).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            if urlsplit(self.path).path == "/health":
                self.send_json(200, {"status": "ok", "app": "stock"})
                return
            self.send_json(404, {"error": "not_found"})

        def do_POST(self):
            path = [unquote(part) for part in urlsplit(self.path).path.split("/")]
            if len(path) != 5 or path[:3] != ["", "v1", "documents"]:
                self.send_json(404, {"error": "not_found"})
                return
            collection_name, document_id = path[3], path[4]
            if collection_name not in COLLECTIONS or not re.fullmatch(r"[A-Za-z0-9_-]{1,150}", document_id):
                self.send_json(404, {"error": "not_found"})
                return
            authorization = self.headers.get("Authorization", "")
            if not authorization.startswith("Bearer ") or len(authorization) > 8192:
                self.send_json(401, {"error": "unauthorized"})
                return
            try:
                user_id = token_verifier.verify(authorization[7:])
                content_length = int(self.headers.get("Content-Length", "0"))
                if content_length <= 0 or content_length > MAX_BODY_BYTES:
                    self.send_json(413, {"error": "payload_too_large"})
                    return
                payload = json.loads(self.rfile.read(content_length).decode("utf-8"))
                if not isinstance(payload, dict):
                    raise ValueError("JSON document must be an object.")
                database_url = os.environ["STOCK_APP_DATABASE_URL"]
                with psycopg.connect(database_url, connect_timeout=3) as connection:
                    connection.execute(
                        f"""
                        INSERT INTO {TABLE}(collection_name, document_id, user_id, payload, updated_at)
                        VALUES (%s, %s, %s, %s, now())
                        ON CONFLICT(collection_name, document_id, user_id)
                        DO UPDATE SET payload = EXCLUDED.payload, updated_at = now()
                        """,
                        (collection_name, document_id, user_id, Jsonb(payload)),
                    )
            except PermissionError:
                self.send_json(401, {"error": "unauthorized"})
                return
            except (ValueError, UnicodeDecodeError, json.JSONDecodeError):
                self.send_json(400, {"error": "invalid_document"})
                return
            except Exception:
                LOG.exception("Stock local save failed.")
                self.send_json(503, {"error": "local_save_failed"})
                return
            self.send_json(200, {"saved": True})

    return RequestHandler


def main():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    address = discover_wifi_ip()
    ensure_table()
    verifier = FirebaseTokenVerifier(get_project_id())
    verifier.ensure_certificates()
    cert_path, key_path = ensure_tls_certificate(address)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(certfile=str(cert_path), keyfile=str(key_path))

    server = ThreadingHTTPServer((address, PORT), make_handler(verifier))
    server.daemon_threads = True
    server.socket = context.wrap_socket(server.socket, server_side=True)
    zeroconf = Zeroconf(interfaces=[address], ip_version=IPVersion.V4Only)
    service = ServiceInfo(
        SERVICE_TYPE,
        SERVICE_NAME,
        addresses=[socket.inet_aton(address)],
        port=PORT,
        properties={b"app": b"stock", b"api": b"v1"},
        server="kunal-stock-sync.local.",
    )
    zeroconf.register_service(service)
    LOG.info("Stock local sync API listening on Wi-Fi at %s:%s.", address, PORT)
    try:
        server.serve_forever()
    finally:
        server.shutdown()
        zeroconf.unregister_service(service)
        zeroconf.close()
        server.server_close()


if __name__ == "__main__":
    main()
