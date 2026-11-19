#!/usr/bin/env python3
"""
LootStream PC Host — Cross-platform stdlib media server matching LootStreamHost.exe.
Supports /api/info, /api/pair, /api/library, /api/stream/{id} (HTTP 206 Range),
and /api/items/{id}/progress|favorite|watched.
"""

import argparse
import hashlib
import http.server
import json
import os
import re
import socket
import socketserver
import sys
import threading
import time
import uuid
from pathlib import Path
from typing import Dict, List, Optional
from urllib.parse import unquote

VIDEO_EXTS = {".mp4", ".mkv", ".webm", ".mov", ".avi", ".m4v", ".ts"}
AUDIO_EXTS = {".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg"}

MIME_MAP = {
    ".mp4": "video/mp4",
    ".m4v": "video/mp4",
    ".mkv": "video/x-matroska",
    ".webm": "video/webm",
    ".mov": "video/quicktime",
    ".avi": "video/x-msvideo",
    ".ts": "video/mp2t",
    ".mp3": "audio/mpeg",
    ".flac": "audio/flac",
    ".wav": "audio/wav",
    ".m4a": "audio/mp4",
    ".aac": "audio/mp4",
    ".ogg": "audio/ogg",
}


class HostStateStore:
    def __init__(self, folders: List[str], server_name: str, pin: str, state_path: Optional[str] = None):
        self.lock = threading.Lock()
        self.folders = [os.path.abspath(f) for f in folders if os.path.isdir(f)]
        self.server_name = server_name
        self.pin = pin.strip()
        self.server_id = hashlib.sha256(socket.gethostname().encode("utf-8")).hexdigest()[:12]
        self.tokens: set = set()
        self.progress: Dict[str, dict] = {}
        self.catalog: List[dict] = []
        self.by_id: Dict[str, dict] = {}
        self.state_path = state_path
        if self.state_path and os.path.isfile(self.state_path):
            self._load_state()
        self.scan_library()

    def _load_state(self) -> None:
        try:
            with open(self.state_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            self.tokens = set(data.get("tokens", []))
            self.progress = dict(data.get("progress", {}))
            if data.get("serverId"):
                self.server_id = data["serverId"]
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
                        "tokens": sorted(self.tokens),
                        "progress": self.progress,
                    },
                    f,
                    indent=2,
                )
        except Exception:
            pass

    def scan_library(self) -> List[dict]:
        scanned: List[dict] = []
        by_id: Dict[str, dict] = {}
        for folder in self.folders:
            for root, _, files in os.walk(folder):
                for fname in sorted(files):
                    ext = Path(fname).suffix.lower()
                    is_video = ext in VIDEO_EXTS
                    is_audio = ext in AUDIO_EXTS
                    if not (is_video or is_audio):
                        continue
                    full_path = os.path.join(root, fname)
                    item_id = hashlib.sha256(full_path.lower().encode("utf-8")).hexdigest()[:16]
                    if item_id in by_id:
                        continue
                    stat = os.stat(full_path)
                    stem = Path(fname).stem
                    year_match = re.search(r"\((\d{4})\)", stem)
                    release_year = int(year_match.group(1)) if year_match else None

                    series_name = None
                    season_num = None
                    episode_num = None
                    media_type = "AUDIO" if is_audio else "VIDEO"
                    ep_match = re.match(r"^(.*?)[.\s_-]+[Ss](\d{1,2})[Ee](\d{1,2})", stem)
                    if is_video and ep_match:
                        media_type = "EPISODE"
                        series_name = ep_match.group(1).replace(".", " ").strip()
                        season_num = int(ep_match.group(2))
                        episode_num = int(ep_match.group(3))
                    elif is_video and release_year is not None:
                        media_type = "MOVIE"

                    clean_title = re.sub(r"[._]+", " ", stem).strip()
                    parent_name = Path(root).name or "PC Host"
                    entry = {
                        "id": item_id,
                        "filePath": full_path,
                        "title": clean_title,
                        "subtitle": series_name or parent_name,
                        "overview": f"Streamed directly from {self.server_name}",
                        "mediaType": media_type,
                        "streamUrl": f"/api/stream/{item_id}",
                        "posterUrl": "",
                        "backdropUrl": "",
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


class LootStreamRequestHandler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, format: str, *args) -> None:
        pass

    @property
    def store(self) -> HostStateStore:
        return self.server.store  # type: ignore[attr-defined]

    def _send_json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

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
        if self.path.startswith("/api/stream/"):
            self._serve_stream(head_only=True)
        else:
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()

    def do_GET(self) -> None:
        if self.path == "/api/info":
            with self.store.lock:
                self._send_json(
                    200,
                    {
                        "serverId": self.store.server_id,
                        "serverName": self.store.server_name,
                        "version": "1.0.0",
                        "hostPlatform": sys.platform,
                        "requiresPin": bool(self.store.pin),
                        "itemCount": len(self.store.catalog),
                    },
                )
            return

        if self.path == "/api/library":
            if not self.store.is_authorized(self.headers.get("Authorization")):
                self._send_json(401, {"error": "Unauthorized"})
                return
            with self.store.lock:
                items = []
                for entry in self.store.catalog:
                    prog = self.store.progress.get(entry["id"], {})
                    item_out = {k: v for k, v in entry.items() if k != "filePath"}
                    item_out["durationMs"] = prog.get("durationMs", 0)
                    item_out["playbackPositionMs"] = prog.get("playbackPositionMs", 0)
                    item_out["isFavorite"] = prog.get("isFavorite", False)
                    item_out["watchStatus"] = prog.get("watchStatus", "UNWATCHED")
                    item_out["playCount"] = prog.get("playCount", 0)
                    item_out["lastPlayedAt"] = prog.get("lastPlayedAt", 0)
                    items.append(item_out)
                self._send_json(
                    200,
                    {
                        "serverId": self.store.server_id,
                        "serverName": self.store.server_name,
                        "items": items,
                    },
                )
            return

        if self.path.startswith("/api/stream/"):
            self._serve_stream(head_only=False)
            return

        self._send_json(404, {"error": "Not found"})

    def do_POST(self) -> None:
        if self.path == "/api/pair":
            payload = self._read_json_body()
            pin = str(payload.get("pin", "")).strip()
            with self.store.lock:
                if self.store.pin and pin != self.store.pin:
                    self._send_json(401, {"error": "Incorrect pairing PIN"})
                    return
                token = uuid.uuid4().hex
                self.store.tokens.add(token)
                self.store._save_state()
                self._send_json(
                    200,
                    {
                        "token": token,
                        "serverId": self.store.server_id,
                        "serverName": self.store.server_name,
                        "version": "1.0.0",
                        "hostPlatform": sys.platform,
                    },
                )
            return

        if self.path.startswith("/api/items/"):
            if not self.store.is_authorized(self.headers.get("Authorization")):
                self._send_json(401, {"error": "Unauthorized"})
                return
            rest = self.path[len("/api/items/") :]
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

        self._send_json(404, {"error": "Not found"})

    def _serve_stream(self, head_only: bool) -> None:
        item_id = unquote(self.path[len("/api/stream/") :])
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

        with open(file_path, "rb") as f:
            f.seek(start)
            remaining = content_len
            while remaining > 0:
                chunk = f.read(min(65536, remaining))
                if not chunk:
                    break
                self.wfile.write(chunk)
                remaining -= len(chunk)


class ThreadingHttpServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True


def start_server(
    folders: List[str],
    host: str = "0.0.0.0",
    port: int = 8765,
    pin: str = "",
    server_name: Optional[str] = None,
    state_path: Optional[str] = None,
) -> ThreadingHttpServer:
    store = HostStateStore(
        folders=folders,
        server_name=server_name or f"{socket.gethostname()} LootStream",
        pin=pin,
        state_path=state_path,
    )
    httpd = ThreadingHttpServer((host, port), LootStreamRequestHandler)
    httpd.store = store  # type: ignore[attr-defined]
    thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    thread.start()
    return httpd


def main() -> None:
    parser = argparse.ArgumentParser(description="LootStream PC Host Server")
    parser.add_argument("--folders", nargs="+", default=[os.path.expanduser("~/Videos"), os.path.expanduser("~/Music")])
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--pin", default="")
    parser.add_argument("--name", default=f"{socket.gethostname()} LootStream")
    args = parser.parse_args()

    state_file = os.path.join(os.path.expanduser("~"), ".lootstream_host", "state.json")
    srv = start_server(args.folders, host="0.0.0.0", port=args.port, pin=args.pin, server_name=args.name, state_path=state_file)
    print(f"LootStream PC Host listening on http://0.0.0.0:{args.port} ({len(srv.store.catalog)} items)")
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        srv.shutdown()
        srv.server_close()


if __name__ == "__main__":
    main()
