#!/usr/bin/env python3
"""Serve mobile and wear APKs/AABs on the local network."""

import http.server
import re
import socket
import socketserver
from pathlib import Path

PORT = 8760
ROOT = Path(__file__).resolve().parent
CHUNK_SIZE = 64 * 1024

# (download filename, path on disk, display label, MIME content type)
APK_MIME = "application/vnd.android.package-archive"
AAB_MIME = "application/octet-stream"

FILES = [
    (
        "mobile-debug.apk",
        ROOT / "mobile/build/outputs/apk/debug/mobile-debug.apk",
        "Svartifoss (phone)",
        APK_MIME,
    ),
    (
        "wear-debug.apk",
        ROOT / "wear/build/outputs/apk/debug/wear-debug.apk",
        "Svartifoss (watch)",
        APK_MIME,
    ),
    (
        "wear-release.apk",
        ROOT / "wear/build/outputs/apk/release/wear-release.apk",
        "Svartifoss (watch, release)",
        APK_MIME,
    ),
    (
        "mobile-release.apk",
        ROOT / "mobile/build/outputs/apk/release/mobile-release.apk",
        "Svartifoss (phone, release)",
        APK_MIME,
    ),
    (
        "mobile-release.aab",
        ROOT / "mobile/build/outputs/bundle/release/mobile-release.aab",
        "Svartifoss (phone, Play Store bundle)",
        AAB_MIME,
    ),
    (
        "wear-release.aab",
        ROOT / "wear/build/outputs/bundle/release/wear-release.aab",
        "Svartifoss (watch, Play Store bundle)",
        AAB_MIME,
    ),
]


def local_ip() -> str:
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("8.8.8.8", 80))
            return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"


from datetime import datetime


def build_index() -> bytes:
    ip = local_ip()
    cards = []

    for name, path, label, _mime in FILES:
        build = "Release" if "release" in name else "Debug"
        kind = "AAB" if name.endswith(".aab") else "APK"

        if path.is_file():
            stat = path.stat()
            size = stat.st_size / (1024 * 1024)
            modified = datetime.fromtimestamp(stat.st_mtime).strftime("%Y-%m-%d %H:%M")

            cards.append(f"""
            <div class="card">
                <div class="title">{label}</div>

                <div class="meta">
                    {build} • {size:.1f} MB • {modified}
                </div>

                <a class="download" href="/{name}">
                    Download {kind}
                </a>
            </div>
            """)
        else:
            cards.append(f"""
            <div class="card">
                <div class="title">{label}</div>

                <div class="meta missing">
                    Not built yet
                </div>
            </div>
            """)

    html = f"""
<!DOCTYPE html>
<html lang="en">

<head>

<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">

<title>Svartifoss Downloads</title>

<style>

:root {{
    --bg:#000;
    --surface:#0b0b0b;
    --border:#1f1f1f;
    --text:#fff;
    --sub:#8a8a8a;
    --accent:#4c8dff;
}}

* {{
    margin:0;
    padding:0;
    box-sizing:border-box;
}}

body {{
    background:var(--bg);
    color:var(--text);
    font-family:Inter,system-ui,-apple-system,sans-serif;
}}

.container {{
    width:min(680px,92%);
    margin:auto;
    padding:48px 0;
}}

header {{
    margin-bottom:34px;
}}

h1 {{
    font-size:28px;
    font-weight:600;
    letter-spacing:-0.5px;
}}

.subtitle {{
    margin-top:4px;
    color:var(--sub);
    font-size:14px;
}}

.address {{
    margin-top:22px;
    border:1px solid var(--border);
    border-radius:12px;
    padding:14px 16px;
    background:var(--surface);
}}

.address small {{
    display:block;
    color:var(--sub);
    margin-bottom:6px;
    font-size:12px;
}}

.address code {{
    color:var(--accent);
    font-size:14px;
    font-family:ui-monospace,monospace;
}}

.card {{
    border:1px solid var(--border);
    border-radius:14px;
    padding:18px;
    background:var(--surface);
    margin-bottom:14px;
}}

.title {{
    font-size:17px;
    font-weight:500;
}}

.meta {{
    margin-top:6px;
    color:var(--sub);
    font-size:13px;
}}

.download {{
    display:inline-block;
    margin-top:16px;
    padding:9px 18px;
    border-radius:10px;
    border:1px solid var(--accent);
    color:var(--accent);
    text-decoration:none;
    font-size:14px;
    transition:.15s;
}}

.download:hover {{
    background:var(--accent);
    color:#fff;
}}

.missing {{
    color:#d06060;
}}

footer {{
    margin-top:26px;
    color:var(--sub);
    font-size:13px;
}}

</style>

</head>

<body>

<div class="container">

<header>

<h1>Svartifoss</h1>

<div class="subtitle">
APK Downloads
</div>

<div class="address">
<small>Server</small>
<code>http://{ip}:{PORT}/</code>
</div>

</header>

{"".join(cards)}

<footer>

Phone and Wear OS devices must be connected to the same Wi-Fi network.

</footer>

</div>

</body>

</html>
"""

    return html.encode("utf-8")


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        self._serve(head_only=False)

    def do_HEAD(self):
        self._serve(head_only=True)

    def _serve(self, head_only: bool):
        try:
            if self.path in ("/", "/index.html"):
                body = build_index()
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                if not head_only:
                    self.wfile.write(body)
                return

            for name, path, _label, mime in FILES:
                if self.path == f"/{name}":
                    self._serve_file(name, path, mime, head_only)
                    return

            self.send_error(404)
        except (ConnectionResetError, BrokenPipeError, TimeoutError):
            # The watch/phone dropped mid-transfer (flaky watch Wi-Fi radio, a
            # canceled download, or a download manager aborting to retry with a
            # Range request). Routine - one log line, no traceback.
            self.log_message("client dropped the connection during %s", self.path)

    def _serve_file(self, name: str, path: Path, mime: str, head_only: bool):
        if not path.is_file():
            task = "bundleRelease" if name.endswith(".aab") else "assembleDebug"
            self.send_error(404, f"Not built yet — run ./gradlew {task} first")
            return

        file_size = path.stat().st_size
        start, end = 0, file_size - 1
        status = 200

        # Basic single-range support so download managers (the watch's included)
        # can resume an interrupted transfer instead of restarting from zero.
        range_header = self.headers.get("Range", "")
        match = re.fullmatch(r"bytes=(\d*)-(\d*)", range_header.strip())
        if match and (match.group(1) or match.group(2)):
            if match.group(1):
                start = int(match.group(1))
                if match.group(2):
                    end = min(int(match.group(2)), file_size - 1)
            else:
                # Suffix form "bytes=-N": the last N bytes.
                start = max(file_size - int(match.group(2)), 0)
            if start > end or start >= file_size:
                self.send_response(416)
                self.send_header("Content-Range", f"bytes */{file_size}")
                self.send_header("Content-Length", "0")
                self.end_headers()
                return
            status = 206

        length = end - start + 1
        self.send_response(status)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Disposition", f'attachment; filename="{name}"')
        self.send_header("Accept-Ranges", "bytes")
        if status == 206:
            self.send_header("Content-Range", f"bytes {start}-{end}/{file_size}")
        self.send_header("Content-Length", str(length))
        self.end_headers()

        if head_only:
            return

        with path.open("rb") as f:
            f.seek(start)
            remaining = length
            while remaining > 0:
                chunk = f.read(min(CHUNK_SIZE, remaining))
                if not chunk:
                    break
                self.wfile.write(chunk)
                remaining -= len(chunk)

    def log_message(self, fmt, *args):
        print(f"[{self.address_string()}] {fmt % args}")


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    # Don't let a client that stopped reading (but kept the socket open) block
    # shutdown on Ctrl+C.
    daemon_threads = True


def main():
    missing = [label for _, path, label, _mime in FILES if not path.is_file()]
    if missing:
        print("Warning: missing build(s):", ", ".join(missing))
        print("Run: ./gradlew :mobile:assembleDebug :wear:assembleDebug")
        print("     ./gradlew :mobile:bundleRelease :wear:bundleRelease  (Play Store AABs)\n")

    ip = local_ip()
    with Server(("", PORT), Handler) as httpd:
        print(f"Serving builds on http://{ip}:{PORT}/")
        print(f"  Phone: http://{ip}:{PORT}/mobile-debug.apk")
        print(f"  Watch: http://{ip}:{PORT}/wear-debug.apk")
        print(f"  Play Store bundles: http://{ip}:{PORT}/mobile-release.aab , /wear-release.aab")
        print("Press Ctrl+C to stop.\n")
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nStopped.")


if __name__ == "__main__":
    main()
