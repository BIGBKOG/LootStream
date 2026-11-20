#!/usr/bin/env python3
"""
LootStream Linux Host — Self-hosted media server & desktop web app.

Serves:
  1. Ampchor-inspired dark cyber Media Center UI at `/` (Movies, TV Shows, Music,
     Folder/PIN Manager, and built-in Video & Music player with live progress sync).
  2. LootStream Android client API (`/api/info`, `/api/pair`, `/api/library`,
     `/api/stream/{id}` with HTTP 206 Range, `/api/artwork/{id}`,
     `/api/items/{id}/progress|favorite|watched`).
"""

import argparse
import hashlib
import http.server
import json
import mimetypes
import os
import re
import secrets
import shutil
import socket
import socketserver
import struct
import subprocess
import sys
import threading
import time
import uuid
import webbrowser
from pathlib import Path
from typing import Dict, List, Optional, Tuple
from urllib.parse import parse_qs, quote, unquote, urlparse

DISCOVERY_PORT = 8766
DISCOVERY_MULTICAST_GROUP = "239.255.87.66"

VIDEO_EXTS = {".mp4", ".mkv", ".webm", ".mov", ".avi", ".m4v", ".ts", ".ogv"}
AUDIO_EXTS = {".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg", ".opus"}
IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp"}

MIME_MAP = {
    ".mp4": "video/mp4",
    ".m4v": "video/mp4",
    ".mkv": "video/x-matroska",
    ".webm": "video/webm",
    ".mov": "video/quicktime",
    ".avi": "video/x-msvideo",
    ".ts": "video/mp2t",
    ".ogv": "video/ogg",
    ".mp3": "audio/mpeg",
    ".flac": "audio/flac",
    ".wav": "audio/wav",
    ".m4a": "audio/mp4",
    ".aac": "audio/aac",
    ".ogg": "audio/ogg",
    ".opus": "audio/opus",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".png": "image/png",
    ".webp": "image/webp",
}

WEB_DIR = Path(__file__).resolve().parent / "web"


def get_lan_ipv4_addresses() -> List[str]:
    ips: List[str] = []
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.settimeout(0.3)
        sock.connect(("10.255.255.255", 1))
        primary = sock.getsockname()[0]
        sock.close()
        if primary and not primary.startswith("127."):
            ips.append(primary)
    except Exception:
        pass

    try:
        out = subprocess.check_output(["ip", "-4", "-o", "addr", "show"], text=True, timeout=1.5)
        for line in out.splitlines():
            m = re.search(r"\binet\s+(\d+\.\d+\.\d+\.\d+)/", line)
            if m:
                ip = m.group(1)
                if not ip.startswith("127.") and ip not in ips:
                    ips.append(ip)
    except Exception:
        pass

    return ips or ["127.0.0.1"]


def probe_duration_ms(file_path: str) -> int:
    ffprobe = shutil.which("ffprobe")
    if not ffprobe:
        return 0
    try:
        out = subprocess.check_output(
            [
                ffprobe,
                "-v",
                "error",
                "-show_entries",
                "format=duration",
                "-of",
                "default=noprint_wrappers=1:nokey=1",
                file_path,
            ],
            stderr=subprocess.DEVNULL,
            text=True,
            timeout=2.5,
        ).strip()
        if out:
            return max(0, int(float(out) * 1000))
    except Exception:
        pass
    return 0


def find_local_artwork(file_path: str) -> Tuple[Optional[str], Optional[str]]:
    """Find matching poster/cover and backdrop artwork near a media file."""
    p = Path(file_path)
    parent = p.parent
    stem = p.stem
    poster_path: Optional[str] = None
    backdrop_path: Optional[str] = None

    poster_candidates = [
        f"{stem}.jpg",
        f"{stem}.png",
        f"{stem}-poster.jpg",
        f"{stem}-poster.png",
        "poster.jpg",
        "poster.png",
        "cover.jpg",
        "cover.png",
        "folder.jpg",
        "folder.png",
        "album.jpg",
        "front.jpg",
    ]
    backdrop_candidates = [
        f"{stem}-fanart.jpg",
        f"{stem}-backdrop.jpg",
        "backdrop.jpg",
        "backdrop.png",
        "fanart.jpg",
        "fanart.png",
    ]

    for name in poster_candidates:
        cand = parent / name
        if cand.is_file():
            poster_path = str(cand)
            break

    if not poster_path and parent.parent != parent:
        for name in ("poster.jpg", "poster.png", "cover.jpg", "folder.jpg"):
            cand = parent.parent / name
            if cand.is_file():
                poster_path = str(cand)
                break

    for name in backdrop_candidates:
        cand = parent / name
        if cand.is_file():
            backdrop_path = str(cand)
            break

    return poster_path, backdrop_path


class LinuxHostStore:
    def __init__(
        self,
        folders: List[str],
        server_name: str,
        pin: str = "",
        port: int = 8765,
        state_path: Optional[str] = None,
        enable_ffprobe: bool = True,
    ):
        self.lock = threading.RLock()
        self.state_path = state_path
        self.server_id = hashlib.sha256(socket.gethostname().encode("utf-8")).hexdigest()[:12]
        self.server_name = server_name
        self.pin = pin.strip()
        self.port = port
        self.enable_ffprobe = enable_ffprobe
        self.folders: List[str] = []
        self.tokens: set = set()
        self.progress: Dict[str, dict] = {}
        self.paired_devices: List[dict] = []
        self.activity_log: List[str] = []
        self.catalog: List[dict] = []
        self.by_id: Dict[str, dict] = {}
        self.duration_cache: Dict[str, int] = {}
        self.quick_code: str = f"{secrets.randbelow(1000000):06d}"

        if self.state_path and os.path.isfile(self.state_path):
            self._load_state()

        for f in folders:
            abs_f = os.path.abspath(os.path.expanduser(f))
            if os.path.isdir(abs_f) and abs_f not in self.folders:
                self.folders.append(abs_f)

        self.scan_library()

    def log_event(self, message: str) -> None:
        stamp = time.strftime("%H:%M:%S")
        with self.lock:
            self.activity_log.append(f"[{stamp}] {message}")
            if len(self.activity_log) > 120:
                self.activity_log = self.activity_log[-120:]

    def _load_state(self) -> None:
        try:
            with open(self.state_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            if data.get("serverId"):
                self.server_id = str(data["serverId"])
            if data.get("serverName"):
                self.server_name = str(data["serverName"])
            if "pin" in data:
                self.pin = str(data.get("pin") or "").strip()
            if isinstance(data.get("folders"), list):
                self.folders = [
                    os.path.abspath(x)
                    for x in data["folders"]
                    if isinstance(x, str) and os.path.isdir(x)
                ]
            self.tokens = set(data.get("tokens", []))
            self.progress = dict(data.get("progress", {}))
            self.paired_devices = list(data.get("pairedDevices", []))
            self.duration_cache = dict(data.get("durationCache", {}))
        except Exception:
            pass

    def _save_state(self) -> None:
        if not self.state_path:
            return
        try:
            os.makedirs(os.path.dirname(self.state_path), exist_ok=True)
            with open(self.state_path, "w", encoding="utf-8") as f:
                json.dump(
                    {
                        "serverId": self.server_id,
                        "serverName": self.server_name,
                        "pin": self.pin,
                        "folders": self.folders,
                        "tokens": sorted(self.tokens),
                        "progress": self.progress,
                        "pairedDevices": self.paired_devices[-25:],
                        "durationCache": self.duration_cache,
                    },
                    f,
                    indent=2,
                )
        except Exception:
            pass

    def add_folder(self, folder_path: str) -> Tuple[bool, str]:
        expanded = os.path.abspath(os.path.expanduser(folder_path.strip()))
        if not os.path.isdir(expanded):
            return False, f"Directory does not exist: {expanded}"
        with self.lock:
            if expanded not in self.folders:
                self.folders.append(expanded)
            self._save_state()
        self.scan_library()
        self.log_event(f"Added media folder: {expanded}")
        return True, expanded

    def remove_folder(self, folder_path: str) -> None:
        expanded = os.path.abspath(os.path.expanduser(folder_path.strip()))
        with self.lock:
            self.folders = [f for f in self.folders if f != expanded]
            self._save_state()
        self.scan_library()
        self.log_event(f"Removed media folder: {expanded}")

    def update_config(self, server_name: Optional[str] = None, pin: Optional[str] = None) -> None:
        with self.lock:
            if server_name is not None and server_name.strip():
                self.server_name = server_name.strip()
            if pin is not None:
                self.pin = pin.strip()
            self._save_state()
        self.log_event("Updated host configuration")

    def refresh_quick_code(self) -> str:
        with self.lock:
            self.quick_code = f"{secrets.randbelow(1000000):06d}"
            code = self.quick_code
        self.log_event("Generated new 6-digit Quick Connect code")
        return code

    def scan_library(self) -> List[dict]:
        with self.lock:
            folders_snapshot = list(self.folders)
            duration_cache = dict(self.duration_cache)
            server_name = self.server_name

        scanned: List[dict] = []
        by_id: Dict[str, dict] = {}
        new_durations: Dict[str, int] = {}

        for folder in folders_snapshot:
            if not os.path.isdir(folder):
                continue
            for root, dirs, files in os.walk(folder):
                dirs[:] = sorted(d for d in dirs if not d.startswith("."))
                for fname in sorted(files):
                    if fname.startswith("."):
                        continue
                    ext = Path(fname).suffix.lower()
                    is_video = ext in VIDEO_EXTS
                    is_audio = ext in AUDIO_EXTS
                    if not (is_video or is_audio):
                        continue

                    full_path = os.path.join(root, fname)
                    item_id = hashlib.sha256(full_path.encode("utf-8")).hexdigest()[:16]
                    if item_id in by_id:
                        continue

                    try:
                        stat = os.stat(full_path)
                    except OSError:
                        continue

                    stem = Path(fname).stem
                    year_match = re.search(r"\((\d{4})\)", stem)
                    release_year = int(year_match.group(1)) if year_match else None

                    series_name = None
                    season_num = None
                    episode_num = None
                    media_type = "AUDIO" if is_audio else "VIDEO"

                    ep_match = re.match(r"^(.*?)[.\s_-]+[Ss](\d{1,2})[Ee](\d{1,2})(?:[.\s_-]*(.*))?$", stem)
                    if is_video and ep_match:
                        media_type = "EPISODE"
                        series_name = re.sub(r"[._]+", " ", ep_match.group(1)).strip(" -")
                        season_num = int(ep_match.group(2))
                        episode_num = int(ep_match.group(3))
                    elif is_video:
                        lower_parts = [p.lower() for p in Path(full_path).parts]
                        if any(k in lower_parts for k in ("tv", "shows", "series", "anime")):
                            media_type = "SERIES"
                        else:
                            media_type = "MOVIE"

                    clean_title = re.sub(r"[._]+", " ", stem).strip()
                    parent_name = Path(root).name or "Linux Host"
                    poster_path, backdrop_path = find_local_artwork(full_path)

                    cache_key = f"{item_id}:{int(stat.st_mtime)}"
                    dur_ms = duration_cache.get(cache_key, 0)
                    if dur_ms == 0 and self.enable_ffprobe:
                        dur_ms = probe_duration_ms(full_path)
                        if dur_ms > 0:
                            new_durations[cache_key] = dur_ms

                    entry = {
                        "id": item_id,
                        "filePath": full_path,
                        "folderRoot": folder,
                        "posterPath": poster_path or "",
                        "backdropPath": backdrop_path or "",
                        "title": clean_title,
                        "subtitle": series_name or parent_name,
                        "overview": f"Hosted from {server_name} ({Path(full_path).parent.name})",
                        "mediaType": media_type,
                        "streamUrl": f"/api/stream/{item_id}",
                        "posterUrl": f"/api/artwork/{item_id}?kind=poster" if poster_path else "",
                        "backdropUrl": f"/api/artwork/{item_id}?kind=backdrop" if backdrop_path else "",
                        "durationMs": dur_ms,
                        "releaseYear": release_year,
                        "genres": [series_name or parent_name],
                        "codecBadge": ext.lstrip(".").upper(),
                        "seriesName": series_name,
                        "seasonNumber": season_num,
                        "episodeNumber": episode_num,
                        "addedAt": int(stat.st_mtime * 1000),
                        "fileSizeBytes": stat.st_size,
                    }
                    scanned.append(entry)
                    by_id[item_id] = entry

        with self.lock:
            self.catalog = scanned
            self.by_id = by_id
            if new_durations:
                self.duration_cache.update(new_durations)
                self._save_state()
        self.log_event(f"Indexed {len(scanned)} playable files across {len(folders_snapshot)} folders")
        return scanned

    def is_authorized(self, auth_header: Optional[str]) -> bool:
        with self.lock:
            if not self.pin and not self.tokens:
                return True
            if auth_header and auth_header.lower().startswith("bearer "):
                token = auth_header[7:].strip()
                if token in self.tokens:
                    return True
            return not self.pin

    def build_public_items(self) -> List[dict]:
        with self.lock:
            items = []
            for entry in self.catalog:
                prog = self.progress.get(entry["id"], {})
                item_out = {
                    k: v
                    for k, v in entry.items()
                    if k not in ("filePath", "folderRoot", "posterPath", "backdropPath")
                }
                item_out["durationMs"] = prog.get("durationMs") or entry.get("durationMs", 0)
                item_out["playbackPositionMs"] = prog.get("playbackPositionMs", 0)
                item_out["isFavorite"] = prog.get("isFavorite", False)
                item_out["watchStatus"] = prog.get("watchStatus", "UNWATCHED")
                item_out["playCount"] = prog.get("playCount", 0)
                item_out["lastPlayedAt"] = prog.get("lastPlayedAt", 0)
                items.append(item_out)
            return items

    def get_public_items(self) -> List[dict]:
        return self.build_public_items()

    def update_progress(
        self,
        item_id: str,
        position_ms: Optional[int] = None,
        duration_ms: Optional[int] = None,
        watch_status: Optional[str] = None,
    ) -> None:
        with self.lock:
            prog = self.progress.setdefault(item_id, {})
            if position_ms is not None:
                prog["playbackPositionMs"] = int(position_ms)
            if duration_ms is not None and duration_ms > 0:
                prog["durationMs"] = int(duration_ms)
            if watch_status is not None:
                prog["watchStatus"] = str(watch_status)
            prog["lastPlayedAt"] = int(time.time() * 1000)
            self._save_state()

    def toggle_favorite(self, item_id: str, is_favorite: bool) -> None:
        with self.lock:
            prog = self.progress.setdefault(item_id, {})
            prog["isFavorite"] = bool(is_favorite)
            self._save_state()

    def set_watched(self, item_id: str, watch_status: str) -> None:
        with self.lock:
            prog = self.progress.setdefault(item_id, {})
            prog["watchStatus"] = str(watch_status)
            if prog.get("watchStatus") == "WATCHED":
                prog["playCount"] = int(prog.get("playCount", 0)) + 1
            self._save_state()


class LootStreamLinuxHandler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, format: str, *args) -> None:
        pass

    @property
    def store(self) -> LinuxHostStore:
        return self.server.store  # type: ignore[attr-defined]

    def _send_json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _send_static_file(self, file_path: Path) -> None:
        if not file_path.is_file():
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        mime, _ = mimetypes.guess_type(str(file_path))
        data = file_path.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", f"{mime or 'application/octet-stream'}; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _read_json_body(self) -> dict:
        length = int(self.headers.get("Content-Length", "0") or "0")
        if length <= 0:
            return {}
        raw = self.rfile.read(length).decode("utf-8", errors="replace")
        try:
            return json.loads(raw) if raw.strip() else {}
        except Exception:
            return {}

    def do_HEAD(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path.startswith("/api/stream/"):
            self._serve_stream(parsed.path, head_only=True)
        else:
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        path = parsed.path

        # Desktop Web UI assets
        if path in ("/", "/index.html"):
            self._send_static_file(WEB_DIR / "index.html")
            return
        if path == "/styles.css":
            self._send_static_file(WEB_DIR / "styles.css")
            return
        if path == "/app.js":
            self._send_static_file(WEB_DIR / "app.js")
            return

        # Android + Host Shared API
        if path == "/api/info":
            with self.store.lock:
                self._send_json(
                    200,
                    {
                        "serverId": self.store.server_id,
                        "serverName": self.store.server_name,
                        "version": "1.0.0",
                        "hostPlatform": "Linux",
                        "requiresPin": bool(self.store.pin),
                        "itemCount": len(self.store.catalog),
                    },
                )
            return

        if path == "/api/library":
            if not self.store.is_authorized(self.headers.get("Authorization")):
                self._send_json(401, {"error": "Unauthorized"})
                return
            items = self.store.build_public_items()
            self._send_json(
                200,
                {
                    "serverId": self.store.server_id,
                    "serverName": self.store.server_name,
                    "items": items,
                },
            )
            return

        if path.startswith("/api/stream/"):
            self._serve_stream(path, head_only=False)
            return

        if path.startswith("/api/artwork/"):
            item_id = unquote(path[len("/api/artwork/") :])
            qs = parse_qs(parsed.query)
            kind = (qs.get("kind") or ["poster"])[0]
            with self.store.lock:
                entry = self.store.by_id.get(item_id)
            if not entry:
                self.send_response(404)
                self.send_header("Content-Length", "0")
                self.end_headers()
                return
            art_path = entry.get("backdropPath") if kind == "backdrop" else entry.get("posterPath")
            if not art_path:
                art_path = entry.get("posterPath") or entry.get("backdropPath")
            if not art_path or not os.path.isfile(art_path):
                self.send_response(404)
                self.send_header("Content-Length", "0")
                self.end_headers()
                return
            ext = Path(art_path).suffix.lower()
            data = Path(art_path).read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", MIME_MAP.get(ext, "image/jpeg"))
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return

        # Host UI Dashboard Endpoints
        if path == "/api/host/state":
            items = self.store.build_public_items()
            lan_ips = get_lan_ipv4_addresses()
            lan_urls = [f"http://{ip}:{self.store.port}" for ip in lan_ips]
            primary_url = lan_urls[0] if lan_urls else f"http://127.0.0.1:{self.store.port}"
            with self.store.lock:
                quick_code = self.store.quick_code
                quick_pair_uri = (
                    f"lootstream://pair?url={quote(primary_url, safe='')}"
                    f"&code={quote(quick_code, safe='')}"
                    f"&name={quote(self.store.server_name, safe='')}"
                )
                self._send_json(
                    200,
                    {
                        "serverId": self.store.server_id,
                        "serverName": self.store.server_name,
                        "hostPlatform": "Linux",
                        "port": self.store.port,
                        "requiresPin": bool(self.store.pin),
                        "pin": self.store.pin,
                        "quickCode": quick_code,
                        "quickPairUri": quick_pair_uri,
                        "lanUrls": lan_urls,
                        "folders": list(self.store.folders),
                        "pairedDevices": list(self.store.paired_devices),
                        "activityLog": list(self.store.activity_log[-30:]),
                        "ffprobeAvailable": shutil.which("ffprobe") is not None,
                        "items": items,
                    },
                )
            return

        if path == "/api/host/browse-dirs":
            qs = parse_qs(parsed.query)
            raw_dir = (qs.get("path") or [os.path.expanduser("~")])[0]
            target = os.path.abspath(os.path.expanduser(raw_dir or os.path.expanduser("~")))
            if not os.path.isdir(target):
                target = os.path.expanduser("~")
            subdirs = []
            try:
                for entry in sorted(os.scandir(target), key=lambda e: e.name.lower()):
                    if entry.is_dir() and not entry.name.startswith("."):
                        subdirs.append({"name": entry.name, "path": entry.path})
            except OSError:
                pass
            parent = str(Path(target).parent)
            self._send_json(
                200,
                {
                    "currentPath": target,
                    "parentPath": parent if parent != target else None,
                    "directories": subdirs[:150],
                },
            )
            return

        self._send_json(404, {"error": "Not found"})

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        path = parsed.path

        if path == "/api/pair":
            payload = self._read_json_body()
            pin = str(payload.get("pin", "")).strip()
            quick_code = str(payload.get("quickCode", "")).strip()
            device_name = str(payload.get("deviceName", "Android")).strip() or "Android"
            with self.store.lock:
                if quick_code:
                    invalid_reason = "Incorrect 6-digit code" if quick_code != self.store.quick_code else ""
                elif self.store.pin and pin != self.store.pin:
                    invalid_reason = "Incorrect pairing PIN"
                else:
                    invalid_reason = ""
            if invalid_reason:
                self.store.log_event(f"Rejected pairing from {device_name} ({invalid_reason})")
                self._send_json(401, {"error": invalid_reason})
                return
            with self.store.lock:
                token = uuid.uuid4().hex
                self.store.tokens.add(token)
                self.store.paired_devices.append(
                    {"deviceName": device_name, "pairedAt": int(time.time() * 1000)}
                )
                self.store._save_state()
                resp = {
                    "token": token,
                    "serverId": self.store.server_id,
                    "serverName": self.store.server_name,
                    "version": "1.0.0",
                    "hostPlatform": "Linux",
                }
            self.store.log_event(f"Paired Android device: {device_name}")
            self._send_json(200, resp)
            return

        if path.startswith("/api/items/"):
            is_local_loopback = self.client_address[0] in ("127.0.0.1", "::1")
            if not is_local_loopback and not self.store.is_authorized(self.headers.get("Authorization")):
                self._send_json(401, {"error": "Unauthorized"})
                return
            rest = path[len("/api/items/") :]
            if "/" not in rest:
                self._send_json(404, {"error": "Invalid path"})
                return
            item_id, action = rest.split("/", 1)
            item_id = unquote(item_id)
            action = action.strip("/")
            payload = self._read_json_body()
            with self.store.lock:
                prog = self.store.progress.setdefault(item_id, {})
                if action == "progress":
                    if "positionMs" in payload:
                        prog["playbackPositionMs"] = int(payload["positionMs"])
                    if "durationMs" in payload:
                        prog["durationMs"] = int(payload["durationMs"])
                    if "watchStatus" in payload:
                        prog["watchStatus"] = str(payload["watchStatus"])
                    prog["lastPlayedAt"] = int(time.time() * 1000)
                elif action == "favorite":
                    if "isFavorite" in payload:
                        prog["isFavorite"] = bool(payload["isFavorite"])
                elif action == "watched":
                    if "watchStatus" in payload:
                        prog["watchStatus"] = str(payload["watchStatus"])
                    if prog.get("watchStatus") == "WATCHED":
                        prog["playCount"] = int(prog.get("playCount", 0)) + 1
                self.store._save_state()
            self._send_json(200, {"ok": True})
            return

        if path == "/api/host/folders":
            payload = self._read_json_body()
            folder = str(payload.get("path", "")).strip()
            ok, msg = self.store.add_folder(folder)
            self._send_json(200 if ok else 400, {"ok": ok, "message": msg})
            return

        if path == "/api/host/folders/remove":
            payload = self._read_json_body()
            folder = str(payload.get("path", "")).strip()
            self.store.remove_folder(folder)
            self._send_json(200, {"ok": True})
            return

        if path == "/api/host/rescan":
            items = self.store.scan_library()
            self._send_json(200, {"ok": True, "itemCount": len(items)})
            return

        if path == "/api/host/config":
            payload = self._read_json_body()
            self.store.update_config(
                server_name=payload.get("serverName"),
                pin=payload.get("pin"),
            )
            self._send_json(200, {"ok": True})
            return

        if path == "/api/host/quick-code/refresh":
            new_code = self.store.refresh_quick_code()
            lan_ips = get_lan_ipv4_addresses()
            primary_url = f"http://{lan_ips[0]}:{self.store.port}" if lan_ips else f"http://127.0.0.1:{self.store.port}"
            with self.store.lock:
                quick_pair_uri = (
                    f"lootstream://pair?url={quote(primary_url, safe='')}"
                    f"&code={quote(new_code, safe='')}"
                    f"&name={quote(self.store.server_name, safe='')}"
                )
            self._send_json(200, {"ok": True, "quickCode": new_code, "quickPairUri": quick_pair_uri})
            return

        if path == "/api/host/pick-folder":
            picked = pick_native_linux_folder()
            if picked:
                ok, msg = self.store.add_folder(picked)
                self._send_json(200 if ok else 400, {"ok": ok, "path": picked, "message": msg})
            else:
                self._send_json(200, {"ok": False, "cancelled": True})
            return

        self._send_json(404, {"error": "Not found"})

    def _serve_stream(self, path: str, head_only: bool) -> None:
        item_id = unquote(path[len("/api/stream/") :])
        with self.store.lock:
            entry = self.store.by_id.get(item_id)
        if not entry or not os.path.isfile(entry["filePath"]):
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return

        file_path = entry["filePath"]
        total_len = os.path.getsize(file_path)
        ext = Path(file_path).suffix.lower()
        mime = MIME_MAP.get(ext, "application/octet-stream")

        start = 0
        end = total_len - 1
        partial = False
        range_hdr = self.headers.get("Range")
        if range_hdr and range_hdr.lower().startswith("bytes="):
            spec = range_hdr[6:].strip()
            if "-" in spec:
                s_part, e_part = spec.split("-", 1)
                if s_part.strip().isdigit():
                    start = int(s_part.strip())
                    end = int(e_part.strip()) if e_part.strip().isdigit() else total_len - 1
                    partial = True
                elif e_part.strip().isdigit():
                    start = max(0, total_len - int(e_part.strip()))
                    end = total_len - 1
                    partial = True

        end = min(end, total_len - 1)
        if start > end or start >= total_len:
            self.send_response(416)
            self.send_header("Content-Range", f"bytes */{total_len}")
            self.send_header("Content-Length", "0")
            self.end_headers()
            return

        content_len = (end - start) + 1
        self.send_response(206 if partial else 200)
        self.send_header("Content-Type", mime)
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(content_len))
        if partial:
            self.send_header("Content-Range", f"bytes {start}-{end}/{total_len}")
        self.end_headers()

        if head_only:
            return

        try:
            with open(file_path, "rb") as f:
                f.seek(start)
                remaining = content_len
                while remaining > 0:
                    chunk = f.read(min(65536, remaining))
                    if not chunk:
                        break
                    self.wfile.write(chunk)
                    remaining -= len(chunk)
        except (BrokenPipeError, ConnectionResetError):
            pass


def pick_native_linux_folder() -> Optional[str]:
    if not (os.environ.get("DISPLAY") or os.environ.get("WAYLAND_DISPLAY")):
        return None
    if shutil.which("zenity"):
        try:
            res = subprocess.check_output(
                ["zenity", "--file-selection", "--directory", "--title=Select Media Folder"],
                stderr=subprocess.DEVNULL,
                text=True,
                timeout=60,
            ).strip()
            if res and os.path.isdir(res):
                return res
        except Exception:
            return None
    if shutil.which("kdialog"):
        try:
            res = subprocess.check_output(
                ["kdialog", "--getexistingdirectory", os.path.expanduser("~")],
                stderr=subprocess.DEVNULL,
                text=True,
                timeout=60,
            ).strip()
            if res and os.path.isdir(res):
                return res
        except Exception:
            return None
    return None


class ThreadingHttpServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True


def start_udp_discovery_responder(
    store: LinuxHostStore,
    stop_event: threading.Event,
    discovery_port: int = DISCOVERY_PORT,
) -> Optional[socket.socket]:
    try:
        udp_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        udp_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        udp_sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        udp_sock.bind(("0.0.0.0", discovery_port))
        udp_sock.settimeout(0.6)
        try:
            mreq = struct.pack("4sl", socket.inet_aton(DISCOVERY_MULTICAST_GROUP), socket.INADDR_ANY)
            udp_sock.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, mreq)
        except Exception:
            pass
    except Exception:
        return None

    def loop() -> None:
        while not stop_event.is_set():
            try:
                data, addr = udp_sock.recvfrom(1024)
            except socket.timeout:
                continue
            except OSError:
                break
            try:
                msg = data.decode("utf-8", errors="ignore").strip()
                if not msg.startswith("LOOTSTREAM_DISCOVER"):
                    continue
                lan_ips = get_lan_ipv4_addresses()
                with store.lock:
                    resp = json.dumps(
                        {
                            "service": "lootstream-host",
                            "serverId": store.server_id,
                            "serverName": store.server_name,
                            "hostPlatform": "Linux",
                            "port": store.port,
                            "urls": [f"http://{ip}:{store.port}" for ip in lan_ips],
                            "requiresPin": bool(store.pin),
                            "quickCodeActive": True,
                        }
                    ).encode("utf-8")
                udp_sock.sendto(resp, addr)
            except Exception:
                pass

    threading.Thread(target=loop, daemon=True).start()
    return udp_sock


def start_linux_host(
    folders: List[str],
    host: str = "0.0.0.0",
    port: int = 8765,
    pin: str = "",
    server_name: Optional[str] = None,
    state_path: Optional[str] = None,
    enable_ffprobe: bool = True,
    discovery_port: int = DISCOVERY_PORT,
) -> ThreadingHttpServer:
    store = LinuxHostStore(
        folders=folders,
        server_name=server_name or f"{socket.gethostname()} Linux Host",
        pin=pin,
        port=port,
        state_path=state_path,
        enable_ffprobe=enable_ffprobe,
    )
    httpd = ThreadingHttpServer((host, port), LootStreamLinuxHandler)
    actual_port = httpd.server_address[1]
    store.port = actual_port
    httpd.store = store  # type: ignore[attr-defined]
    stop_event = threading.Event()
    udp_sock = start_udp_discovery_responder(store, stop_event, discovery_port=discovery_port)
    orig_close = httpd.server_close

    def close_all() -> None:
        stop_event.set()
        if udp_sock is not None:
            try:
                udp_sock.close()
            except Exception:
                pass
        orig_close()

    httpd.server_close = close_all  # type: ignore[method-assign]
    thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    thread.start()
    return httpd


def launch_desktop_window(
    url: str,
    store: Optional[LinuxHostStore] = None,
    lan_urls: Optional[List[str]] = None,
    force_browser: bool = False,
) -> bool:
    """
    Launch LootStream as an actual standalone Linux desktop application.
    Returns True if a desktop app window was opened and closed (meaning the
    foreground app session finished cleanly), or False to stay running in
    background/headless server mode.
    """
    if not (os.environ.get("DISPLAY") or os.environ.get("WAYLAND_DISPLAY")):
        return False

    if force_browser:
        try:
            webbrowser.open(url)
        except Exception:
            pass
        return False

    try:
        from lootstream_desktop_app import launch_native_desktop_app

        launched = launch_native_desktop_app(url, store=store, lan_urls=lan_urls)
        if launched:
            return True
    except Exception as exc:
        print(f"[LootStream] Native desktop GUI unavailable ({exc}); continuing in server mode.")

    print(
        "[LootStream] Native desktop GUI library (GTK WebKit2 or Tkinter) is missing. "
        "Install python3-gi + gir1.2-webkit2-4.1 (or python3-tk) to display the desktop app window."
    )
    return False


def default_media_folders() -> List[str]:
    candidates = [
        os.path.expanduser("~/Videos"),
        os.path.expanduser("~/Movies"),
        os.path.expanduser("~/Music"),
        os.path.expanduser("~/Downloads"),
    ]
    found = [d for d in candidates if os.path.isdir(d)]
    return found or [os.path.expanduser("~")]


def main() -> None:
    parser = argparse.ArgumentParser(description="LootStream Linux Desktop App & Media Host")
    parser.add_argument("--folders", nargs="*", default=None, help="Initial media directories to scan")
    parser.add_argument("--port", type=int, default=8765, help="HTTP server port (default: 8765)")
    parser.add_argument("--pin", default="", help="Optional pairing PIN")
    parser.add_argument("--name", default=f"{socket.gethostname()} Linux Host", help="Display name for this host")
    parser.add_argument("--no-open", action="store_true", help="Run headless server without opening desktop app window")
    parser.add_argument("--open-browser", action="store_true", help="Explicitly open URL in web browser instead of native app")
    args = parser.parse_args()

    config_dir = os.path.join(
        os.environ.get("XDG_CONFIG_HOME", os.path.expanduser("~/.config")),
        "lootstream-host",
    )
    state_file = os.path.join(config_dir, "state.json")
    folders = args.folders if args.folders is not None else default_media_folders()

    srv = start_linux_host(
        folders=folders,
        host="0.0.0.0",
        port=args.port,
        pin=args.pin,
        server_name=args.name,
        state_path=state_file,
    )
    lan_ips = get_lan_ipv4_addresses()
    lan_urls = [f"http://{ip}:{args.port}" for ip in lan_ips]
    local_url = f"http://127.0.0.1:{args.port}/"
    print(f"LootStream Linux Host running at {local_url}")
    for u in lan_urls:
        print(f"  Android LAN URL: {u}")
    print(f"  Indexed items: {len(srv.store.catalog)}")  # type: ignore[attr-defined]

    if not args.no_open:
        closed_from_app = launch_desktop_window(
            local_url,
            store=srv.store,  # type: ignore[attr-defined]
            lan_urls=lan_urls,
            force_browser=args.open_browser,
        )
        if closed_from_app:
            print("LootStream desktop app closed. Stopping server...")
            srv.shutdown()
            srv.server_close()
            return

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\nShutting down LootStream Linux Host...")
        srv.shutdown()
        srv.server_close()


if __name__ == "__main__":
    main()
