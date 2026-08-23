#!/usr/bin/env python3
"""Serve mobile and wear APKs/AABs on the local network."""

import http.server
import re
import socket
import socketserver
from datetime import datetime
from pathlib import Path
from urllib.parse import unquote, urlsplit

PORT = 8760
ROOT = Path(__file__).resolve().parent
CHUNK_SIZE = 64 * 1024

# (download filename, path on disk, display label, MIME content type)
APK_MIME = "application/vnd.android.package-archive"
AAB_MIME = "application/octet-stream"

# Material Design "bug_report" icon used as the site favicon.
FAVICON_SVG = b"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
<rect width="24" height="24" rx="5" fill="#0a0a0a"/>
<path fill="#f5f5f5" d="M20 8h-2.81c-.45-.78-1.07-1.45-1.82-1.96L17 4.41 15.59 3l-2.17 2.17C12.96 5.06 12.49 5 12 5s-.96.06-1.41.17L8.41 3 7 4.41l1.62 1.63C7.88 6.55 7.26 7.22 6.81 8H4v2h2.09c-.05.33-.09.66-.09 1v1H4v2h2v1c0 .34.04.67.09 1H4v2h2.81C7.85 19.79 9.78 21 12 21s4.15-1.21 5.19-3H20v-2h-2.09c.05-.33.09-.66.09-1v-1h2v-2h-2v-1c0-.34-.04-.67-.09-1H20V8zm-6 8h-4v-2h4v2zm0-4h-4v-2h4v2z"/>
</svg>"""

FILES = [
    # Debug APKs: App first, then Wear OS.
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

    # Release APKs: App first, then Wear OS.
    (
        "mobile-release.apk",
        ROOT / "mobile/build/outputs/apk/release/mobile-release.apk",
        "Svartifoss (phone, release)",
        APK_MIME,
    ),
    (
        "wear-release.apk",
        ROOT / "wear/build/outputs/apk/release/wear-release.apk",
        "Svartifoss (watch, release)",
        APK_MIME,
    ),

    # Play Store bundles: App first, then Wear OS.
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


def get_svf_version() -> str | None:
    """Try to read the app version from common Gradle project files."""
    gradle_files = [
        ROOT / "mobile/build.gradle.kts",
        ROOT / "mobile/build.gradle",
        ROOT / "wear/build.gradle.kts",
        ROOT / "wear/build.gradle",
    ]

    version_name_patterns = [
        r'\bversionName\s*=\s*["\']([^"\']+)["\']',
        r'\bversionName\s+["\']([^"\']+)["\']',
    ]

    for path in gradle_files:
        if not path.is_file():
            continue

        try:
            content = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue

        for pattern in version_name_patterns:
            match = re.search(pattern, content)
            if match:
                return match.group(1).strip()

    # Fallback for projects that keep the version in gradle.properties.
    properties = ROOT / "gradle.properties"
    if properties.is_file():
        try:
            content = properties.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            content = ""

        property_patterns = [
            r'^\s*(?:VERSION_NAME|versionName|appVersionName)\s*=\s*([^#\r\n]+)',
        ]

        for pattern in property_patterns:
            match = re.search(pattern, content, flags=re.MULTILINE)
            if match:
                return match.group(1).strip().strip('"\'')

    return None


def build_task(name: str) -> str:
    module = "wear" if name.startswith("wear-") else "mobile"

    if name.endswith(".aab"):
        task = "bundleRelease"
    elif "-release." in name:
        task = "assembleRelease"
    else:
        task = "assembleDebug"

    return f":{module}:{task}"


def build_index() -> bytes:
    ip = local_ip()
    version = get_svf_version()
    cards = []

    for name, path, _label, _mime in FILES:
        is_wear = name.startswith("wear")
        is_release = "release" in name
        is_aab = name.endswith(".aab")

        device_icon = "watch" if is_wear else "phone_android"

        if is_aab:
            build_icon = "publish"
        elif is_release:
            build_icon = "android"
        else:
            build_icon = "bug_report"

        kind = "AAB" if is_aab else "APK"
        device_title = "Wear OS" if is_wear else "Phone"

        if is_aab:
            build_title = "Play Store Bundle"
        elif is_release:
            build_title = "Release"
        else:
            build_title = "Debug"

        if path.is_file():
            stat = path.stat()
            size = stat.st_size / (1024 * 1024)
            modified = datetime.fromtimestamp(stat.st_mtime).strftime("%d/%m/%Y %H:%M")

            cards.append(
                f"""
                <div class="card">
                    <div class="card-header">
                        <div class="icons">
                            <span
                                class="material-symbols-outlined"
                                title="{device_title}"
                                aria-label="{device_title}"
                            >{device_icon}</span>

                            <span
                                class="material-symbols-outlined"
                                title="{build_title}"
                                aria-label="{build_title}"
                            >{build_icon}</span>
                        </div>

                        <span class="available">Available</span>
                    </div>

                    <div class="details">
                        {size:.1f} MB · {modified}
                    </div>

                    <a
                        class="download-button"
                        href="/{name}"
                        download="{name}"
                    >
                        <span class="material-symbols-outlined">download</span>
                        <span>Download {kind}</span>
                    </a>
                </div>
                """
            )
        else:
            task = build_task(name)

            cards.append(
                f"""
                <div class="card missing-card">
                    <div class="card-header">
                        <div class="icons">
                            <span
                                class="material-symbols-outlined"
                                title="{device_title}"
                                aria-label="{device_title}"
                            >{device_icon}</span>

                            <span
                                class="material-symbols-outlined"
                                title="{build_title}"
                                aria-label="{build_title}"
                            >{build_icon}</span>
                        </div>

                        <span class="missing">Unavailable</span>
                    </div>

                    <div class="details">
                        Build not found · ./gradlew {task}
                    </div>
                </div>
                """
            )

    version_text = f"Svf {version}" if version else "Svf"

    html = f"""
<!DOCTYPE html>
<html lang="en">

<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="theme-color" content="#000000">

<title>Svartifoss</title>

<link rel="icon" type="image/svg+xml" href="/favicon.svg">

<link
    href="https://fonts.googleapis.com/css2?family=Manrope:wght@400;500;600&family=Material+Symbols+Outlined&icon_names=android,bug_report,download,phone_android,publish,refresh,watch&display=block"
    rel="stylesheet"
>

<style>
:root {{
    --background: #000000;
    --surface: #0a0a0a;
    --surface-hover: #0d0d0d;
    --border: #202020;
    --border-hover: #303030;
    --text: #f5f5f5;
    --secondary: #808080;
    --accent: #7ba2ff;
    --accent-hover: #91b1ff;
    --accent-surface: #10182a;
    --green: #7dbb91;
    --red: #c77979;
}}

* {{
    box-sizing: border-box;
}}

html {{
    background: var(--background);
}}

body {{
    margin: 0;
    background: var(--background);
    color: var(--text);
    font-family: "Manrope", system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    -webkit-font-smoothing: antialiased;
    text-rendering: optimizeLegibility;
    overscroll-behavior-y: contain;
    -webkit-overflow-scrolling: touch;
}}

.material-symbols-outlined {{
    font-size: 19px;
    line-height: 1;
    font-variation-settings:
        'FILL' 0,
        'wght' 400,
        'GRAD' 0,
        'opsz' 20;
}}

main {{
    width: min(580px, calc(100% - 24px));
    margin: 0 auto;
    padding: clamp(24px, 8vw, 64px) 0;
}}

header {{
    margin-bottom: 32px;
}}

.header-title {{
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 20px;
}}

h1 {{
    margin: 0;
    min-width: 0;
    font-size: clamp(20px, 7vw, 26px);
    font-weight: 600;
    letter-spacing: -0.8px;
}}

.header-actions {{
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
}}

.version {{
    color: var(--secondary);
    font-size: 12px;
    font-weight: 500;
    white-space: nowrap;
}}

.reload-button {{
    width: 30px;
    height: 30px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 0;
    border: 1px solid var(--border);
    border-radius: 8px;
    background: var(--surface);
    color: var(--secondary);
    cursor: pointer;
    transition:
        color .15s ease,
        background .15s ease,
        border-color .15s ease;
}}

.reload-button .material-symbols-outlined {{
    font-size: 18px;
}}

.reload-button:hover {{
    color: var(--text);
    background: var(--surface-hover);
    border-color: var(--border-hover);
}}

.reload-button:active {{
    transform: scale(.96);
}}

.subtitle {{
    margin-top: 4px;
    color: var(--secondary);
    font-size: 13px;
    font-weight: 400;
}}

.server {{
    margin-top: 18px;
    padding: 11px 12px;
    border: 1px solid var(--border);
    border-radius: 10px;
    background: var(--surface);
    overflow: hidden;
}}

.server code {{
    display: block;
    color: var(--accent);
    font-family: ui-monospace, "SFMono-Regular", Consolas, monospace;
    font-size: clamp(10px, 3.5vw, 12px);
    line-height: 1.4;
    overflow-wrap: anywhere;
    word-break: break-word;
}}

.builds {{
    display: grid;
    gap: 10px;
}}

.card {{
    padding: 16px;
    border: 1px solid var(--border);
    border-radius: 11px;
    background: var(--surface);
    transition: background .15s ease, border-color .15s ease;
}}

.card:hover {{
    background: var(--surface-hover);
    border-color: var(--border-hover);
}}

.card-header {{
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
}}

.icons {{
    display: flex;
    align-items: center;
    gap: 12px;
    flex: 0 0 auto;
    color: #d0d0d0;
}}

.icons .material-symbols-outlined {{
    display: grid;
    place-items: center;
    flex: 0 0 32px;
    width: 32px;
    height: 32px;
    font-size: 21px;
    cursor: default;
}}

.available,
.missing {{
    font-size: 11px;
    font-weight: 500;
}}

.available {{
    color: var(--green);
}}

.missing {{
    color: var(--red);
}}

.details {{
    margin-top: 14px;
    color: var(--secondary);
    font-size: 11px;
    font-weight: 400;
}}

.download-button {{
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 7px;
    width: 100%;
    min-height: 42px;
    margin-top: 15px;
    border: 1px solid #26395f;
    border-radius: 8px;
    background: var(--accent-surface);
    color: var(--accent);
    font-size: 12px;
    font-weight: 600;
    text-decoration: none;
    transition: background .15s ease, border-color .15s ease, color .15s ease;
}}

.download-button .material-symbols-outlined {{
    font-size: 17px;
}}

.download-button:hover {{
    background: #15213a;
    border-color: #345184;
    color: var(--accent-hover);
}}

.download-button:active {{
    transform: translateY(1px);
}}

.missing-card {{
    opacity: .55;
}}

footer {{
    margin-top: 30px;
    color: var(--secondary);
    font-size: 11px;
    line-height: 1.6;
}}

.rotary-debug {{
    position: fixed;
    left: 6px;
    right: 6px;
    bottom: 6px;
    z-index: 9999;
    padding: 8px 10px;
    border: 1px solid var(--border-hover);
    border-radius: 9px;
    background: rgba(10, 10, 10, .94);
    color: var(--secondary);
    font-family: ui-monospace, "SFMono-Regular", Consolas, monospace;
    font-size: 9px;
    line-height: 1.45;
    pointer-events: none;
    backdrop-filter: blur(8px);
}}

.rotary-debug strong {{
    color: var(--text);
    font-weight: 500;
}}

.rotary-debug[hidden] {{
    display: none;
}}

@media (max-width: 500px) {{
    main {{
        width: calc(100% - 16px);
        padding: 24px 0;
    }}

    header {{
        margin-bottom: 22px;
    }}

    .header-title {{
        gap: 10px;
    }}

    .header-actions {{
        gap: 6px;
    }}

    .version {{
        font-size: 11px;
    }}

    .reload-button {{
        width: 32px;
        height: 32px;
        flex: 0 0 32px;
    }}

    .subtitle {{
        font-size: 12px;
    }}

    .server {{
        margin-top: 15px;
        padding: 10px 11px;
    }}

    .builds {{
        gap: 8px;
    }}

    .card {{
        padding: 13px;
        border-radius: 10px;
    }}

    .card-header {{
        gap: 10px;
    }}

    .icons {{
        gap: 10px;
    }}

    .icons .material-symbols-outlined {{
        flex: 0 0 34px;
        width: 34px;
        height: 34px;
        font-size: 22px;
    }}

    .available,
    .missing {{
        flex: 0 0 auto;
        white-space: nowrap;
    }}

    .details {{
        margin-top: 10px;
        line-height: 1.45;
        overflow-wrap: anywhere;
    }}

    .download-button {{
        min-height: 44px;
        margin-top: 12px;
        font-size: 12px;
    }}

    footer {{
        margin-top: 22px;
        font-size: 10px;
    }}
}}

/* Smartwatch / very compact screens */
@media (max-width: 340px) {{
    main {{
        width: calc(100% - 10px);
        padding: 14px 0 18px;
    }}

    header {{
        margin-bottom: 16px;
    }}

    .header-title {{
        align-items: center;
        gap: 6px;
    }}

    h1 {{
        font-size: clamp(18px, 7.5vw, 22px);
        letter-spacing: -0.5px;
    }}

    .header-actions {{
        gap: 4px;
    }}

    .version {{
        font-size: 10px;
    }}

    .reload-button {{
        width: 30px;
        height: 30px;
        flex-basis: 30px;
        border-radius: 50%;
    }}

    .subtitle {{
        margin-top: 2px;
        font-size: 11px;
    }}

    .server {{
        margin-top: 11px;
        padding: 9px 10px;
    }}

    .card {{
        padding: 11px;
    }}

    .icons {{
        gap: 8px;
    }}

    .icons .material-symbols-outlined {{
        flex: 0 0 36px;
        width: 36px;
        height: 36px;
        font-size: 23px;
    }}

    .available,
    .missing {{
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 10px;
        height: 10px;
        overflow: hidden;
        font-size: 0;
        border-radius: 50%;
    }}

    .available {{
        background: var(--green);
    }}

    .missing {{
        background: var(--red);
    }}

    .details {{
        font-size: 10px;
    }}

    .download-button {{
        min-height: 46px;
        border-radius: 9px;
    }}

    footer {{
        margin-top: 18px;
        padding: 0 6px;
        text-align: center;
        font-size: 9px;
    }}
}}

/* Extra narrow round/square watch displays */
@media (max-width: 260px) {{
    main {{
        width: calc(100% - 8px);
        padding-top: 10px;
    }}

    .header-title {{
        flex-wrap: wrap;
    }}

    .header-actions {{
        margin-left: auto;
    }}

    .server {{
        border-radius: 8px;
    }}

    .card {{
        padding: 10px;
        border-radius: 9px;
    }}

    .icons .material-symbols-outlined {{
        flex-basis: 38px;
        width: 38px;
        height: 38px;
        font-size: 24px;
    }}

    .download-button {{
        min-height: 48px;
        font-size: 11px;
    }}
}}
</style>

</head>

<body tabindex="-1">

<main>
<header>
    <div class="header-title">
        <h1>Svartifoss</h1>

        <div class="header-actions">
            <div class="version">{version_text}</div>

            <button
                class="reload-button"
                type="button"
                title="Reload"
                aria-label="Reload page"
                onclick="window.location.reload()"
            >
                <span class="material-symbols-outlined">refresh</span>
            </button>
        </div>
    </div>

    <div class="subtitle">Downloads</div>

    <div class="server">
        <code>http://{ip}:{PORT}/</code>
    </div>
</header>

<section class="builds">
    {"".join(cards)}
</section>

<footer>
    Phone and Wear OS devices must be connected to the same Wi-Fi network.
</footer>
</main>

<div class="rotary-debug" id="rotary-debug" hidden>
    <strong>Rotary diagnostic</strong><br>
    wheel: <span id="diag-wheel">0</span> ·
    key: <span id="diag-key">0</span> ·
    scroll: <span id="diag-scroll">0</span><br>
    last: <span id="diag-last">waiting...</span>
</div>

<script>
(() => {{
    const params = new URLSearchParams(window.location.search);
    const debugEnabled = params.get("rotarydebug") === "1";
    const debugPanel = document.getElementById("rotary-debug");

    const state = {{
        wheel: 0,
        key: 0,
        scroll: 0,
        last: "waiting..."
    }};

    if (debugEnabled) {{
        debugPanel.hidden = false;
    }}

    function updateDebug() {{
        if (!debugEnabled) return;

        document.getElementById("diag-wheel").textContent = state.wheel;
        document.getElementById("diag-key").textContent = state.key;
        document.getElementById("diag-scroll").textContent = state.scroll;
        document.getElementById("diag-last").textContent = state.last;
    }}

    function normalizeWheelDelta(event) {{
        let delta = event.deltaY || event.deltaX || 0;

        if (event.deltaMode === 1) {{
            delta *= 16;
        }} else if (event.deltaMode === 2) {{
            delta *= window.innerHeight;
        }}

        return delta;
    }}

    function rotaryScroll(delta) {{
        if (!delta) return;

        const direction = Math.sign(delta);
        const magnitude = Math.min(Math.max(Math.abs(delta), 56), 140);

        window.scrollBy({{
            top: direction * magnitude,
            left: 0,
            behavior: "auto"
        }});
    }}

    // Wear OS browsers may expose the crown as a standard WheelEvent.
    window.addEventListener("wheel", (event) => {{
        const delta = normalizeWheelDelta(event);

        state.wheel += 1;
        state.last = `wheel ${{Math.round(delta)}}`;
        updateDebug();

        if (delta) {{
            // We handle the motion ourselves so the crown works even when the
            // browser dispatches wheel events but does not scroll the page.
            event.preventDefault();
            rotaryScroll(delta);
        }}
    }}, {{ passive: false }});

    // Some browser/WebView implementations expose rotary navigation as keys.
    window.addEventListener("keydown", (event) => {{
        const keySteps = {{
            ArrowDown: 76,
            ArrowUp: -76,
            PageDown: Math.max(window.innerHeight * 0.72, 100),
            PageUp: -Math.max(window.innerHeight * 0.72, 100)
        }};

        state.key += 1;
        state.last = `key ${{event.key || event.code || "unknown"}}`;
        updateDebug();

        if (Object.prototype.hasOwnProperty.call(keySteps, event.key)) {{
            event.preventDefault();
            rotaryScroll(keySteps[event.key]);
        }}
    }});

    window.addEventListener("scroll", () => {{
        state.scroll += 1;
        state.last = `scroll y=${{Math.round(window.scrollY)}}`;
        updateDebug();
    }}, {{ passive: true }});

    // Keyboard-style rotary events are more likely to reach the page when it
    // owns focus. Do not move the viewport while requesting focus.
    window.addEventListener("load", () => {{
        try {{
            window.focus();
            document.body.focus({{ preventScroll: true }});
        }} catch (_) {{
            // Older/limited Wear OS browsers may not support focus options.
        }}
    }});
}})();
</script>

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
            request_path = unquote(urlsplit(self.path).path)

            if request_path == "/favicon.svg":
                self.send_response(200)
                self.send_header("Content-Type", "image/svg+xml")
                self.send_header("Content-Length", str(len(FAVICON_SVG)))
                self.send_header("Cache-Control", "public, max-age=86400")
                self.end_headers()

                if not head_only:
                    self.wfile.write(FAVICON_SVG)

                return

            if request_path in ("/", "/index.html"):
                body = build_index()
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.send_header("Cache-Control", "no-store")
                self.end_headers()
                if not head_only:
                    self.wfile.write(body)
                return

            for name, path, _label, mime in FILES:
                if request_path == f"/{name}":
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
            task = build_task(name)
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
                suffix_length = int(match.group(2))
                if suffix_length <= 0:
                    self.send_response(416)
                    self.send_header("Content-Range", f"bytes */{file_size}")
                    self.send_header("Content-Length", "0")
                    self.end_headers()
                    return
                start = max(file_size - suffix_length, 0)

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
        self.send_header("X-Content-Type-Options", "nosniff")
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
    missing = [
        (name, label)
        for name, path, label, _mime in FILES
        if not path.is_file()
    ]

    if missing:
        print("Warning: missing build(s):")
        for name, label in missing:
            print(f"  - {label}: ./gradlew {build_task(name)}")
        print()

    ip = local_ip()
    version = get_svf_version()

    with Server(("", PORT), Handler) as httpd:
        print(f"Serving Svartifoss{f' {version}' if version else ''} on http://{ip}:{PORT}/")
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