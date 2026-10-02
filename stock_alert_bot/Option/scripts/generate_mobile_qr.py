import argparse
import os
import socket
import subprocess
import sys
from pathlib import Path


DEFAULT_PORT = 5000


def detect_lan_host():
    """Return the IPv4 address other devices can use on the local network."""
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect(("8.8.8.8", 80))
        return probe.getsockname()[0]
    except OSError:
        return socket.gethostbyname(socket.gethostname())
    finally:
        probe.close()


def dashboard_url(host=None, port=DEFAULT_PORT):
    return f"http://{host or detect_lan_host()}:{int(port)}"


def generate_qr(url: str, output_path: str | Path):
    import qrcode

    qr = qrcode.QRCode(version=1, box_size=10, border=4)
    qr.add_data(url)
    qr.make(fit=True)
    img = qr.make_image(fill_color="black", back_color="white")
    out = Path(output_path)
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    return out


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate a QR code for the dashboard on mobile.")
    parser.add_argument("--url", help="Full URL to encode; defaults to this computer's LAN URL")
    parser.add_argument("--host", help="LAN IPv4 address to encode (auto-detected when omitted)")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help="Dashboard port")
    parser.add_argument("--output", default="mobile_dashboard_qr.png", help="Output image path")
    parser.add_argument("--start", action="store_true", help="Start the web dashboard after creating the QR")
    args = parser.parse_args()

    url = args.url or dashboard_url(args.host, args.port)
    path = generate_qr(url, args.output)
    print(f"QR code saved to: {path}")
    print(f"Open this URL on your phone: {url}")
    if url.startswith(("http://192.168.", "http://10." , "http://172.16.")):
        print("The phone and computer must be connected to the same Wi-Fi network.")
    else:
        print("This is a public URL and can be opened over mobile data while the dashboard is running.")
    if args.start:
        root = Path(__file__).resolve().parent.parent
        environment = dict(os.environ, DASHBOARD_PORT=str(args.port))
        subprocess.Popen([sys.executable, str(root / "scripts" / "web_dashboard.py")], cwd=root, env=environment)
        print("Dashboard server started. Keep the computer on while using the QR URL.")
