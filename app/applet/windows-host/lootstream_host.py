#!/usr/bin/env python3
"""LootStream PC Host — cross-platform stdlib HTTP Range media server."""

import argparse
import hashlib
import json
import mimetypes
import os
import re
import secrets
import socket
import threading
import time
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

VIDEO_EXTS = {".mp4", ".mkv", ".webm", ".mov", ".m4v", ".avi", ".ts", ".m2ts"}
AUDIO_EXTS = {".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg", ".opus"}
EPISODE_RE = re.compile(r"[Ss](\d{1,2})[Ee](\d{1,3})")


class LootStreamHostEngine:
    def __init__(self, folders, server_name=None, pin="", state_path=None):
        self.folders = [str(Path(f).expanduser().resolve()) for f in folders]
        self.server_name = server_name or f"{socket.gethostname()} - LootStream"
        self.pin = pin.strip()
        self.state_path = Path(state_path) if state_path else Path.home() / ".lootstream_host_state.json"
        self.lock = threading.Lock()
        self.state = self._load_state()
        self.catalog = []
        self.catalog_by_id = {}
        self.rescan()

    def _load_state(self):
        if self.state_path.exists():
            try:
                data = json.loads(self.state_path.read_text(encoding="utf-8"))
                return {
                    "serverId": data.get("serverId") or f"ls_{secrets.token_hex(6)}",
                    "tokens": list(data.get("tokens", [])),
                    "progress": dict(data.get("progress", {})),
                }
            except Exception:
                pass
        return {
            "serverId": f"ls_{secrets.token_hex(6)}",
            "tokens": [],
            "progress": {},
        }

    def _save_state(self):
        try:
            self.state_path.parent.mkdir(parents=True, exist_ok=True)
            self.state_path.write_text(json.dumps(self.state, indent=2), encoding="utf-8")
        except Exception:
            pass

    def rescan(self):
        items = []
        by_id = {}
        for folder in self.folders:
            root = Path(folder)
            if not root.exists():
                continue
            for p in root.rglob("*"):
                if not p.is_file():
                    continue
                ext = p.suffix.lower()
                if ext not in VIDEO_EXTS and ext not in AUDIO_EXTS:
                    continue
                is_audio = ext in AUDIO_EXTS
                raw_name = p.stem
                clean_title = raw_name.replace(".", " ").replace("_", " ").strip()
                parent_name = p.parent.name or "PC"
                ep = EPISODE_RE.search(raw_name)
                series_name = None
                season_num = None
                episode_num = None
                if is_audio:
                    media_type = "AUDIO"
                elif ep:
                    media_type = "EPISODE"
                    season_num = int(ep.group(1))
                    episode_num = int(ep.group(2))
                    series_name = parent_name
                elif "movie" in str(p).lower() or p.stat().st_size > 350 * 1024 * 1024:
                    media_type = "MOVIE"
                else:
                    media_type = "VIDEO"

                item_id = hashlib.sha1(str(p).lower().encode("utf-8")).hexdigest()[:16]
                stat = p.stat()
                entry = {
                    "id": item_id,
                    "filePath": str(p),
                    "title": clean_title,
                    "subtitle": f"{parent_name} • {ext.lstrip('.').upper()}",
                    "overview": f"Streamed from {self.server_name} ({p.name})",
                    "mediaType": media_type,
                    "codecBadge": ext.lstrip(".").upper(),
                    "seriesName": series_name,
                    "seasonNumber": season_num,
                    "episodeNumber": episode_num,
                    "fileSizeBytes": stat.st_size,
                    "addedAt": int(stat.st_mtime * 1000),
                    "mimeType": mimetypes.guess_type(str(p))[0] or "application/octet-stream",
                }
                items.append(entry)
                by_id[item_id] = entry

        with self.lock:
            self.catalog = items
            self.catalog_by_id = by_id
        return len(items)


def make_handler(engine: LootStreamHostEngine):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, fmt, *args):
            pass

        def _send_json(self, code, payload):
            raw = json.dumps(payload).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)

        def _extract_token(self):
            auth = self.headers.get("Authorization", "")
            if auth.lower().startswith("bearer "):
                return auth[7:].strip()
            parsed = urllib.parse.urlparse(self.path)
            qs = urllib.parse.parse_qs(parsed.query)
            return (qs.get("token") or [""])[0].strip()

        def _is_authorized(self):
            if not engine.pin:
                return True
            token = self._extract_token()
            with engine.lock:
                return token in engine.state["tokens"]

        def do_GET(self):
            parsed = urllib.parse.urlparse(self.path)
            path = parsed.path.rstrip("/") or "/"

            if path == "/api/info":
                with engine.lock:
                    count = len(engine.catalog)
                self._send_json(200, {
                    "serverId": engine.state["serverId"],
                    "serverName": engine.server_name,
                    "version": "1.0.0",
                    "requiresPin": bool(engine.pin),
                    "hostPlatform": "Windows",
                    "itemCount": count,
                })
                return

            if not self._is_authorized():
                self._send_json(401, {"error": "Unauthorized"})
                return

            if path == "/api/library":
                token = self._extract_token()
                with engine.lock:
                    items_out = []
                    for item in engine.catalog:
                        prog = engine.state["progress"].get(item["id"], {})
                        suffix = f"?token={urllib.parse.quote(token)}" if token else ""
                        items_out.append({
                            "id": item["id"],
                            "title": item["title"],
                            "subtitle": item["subtitle"],
                            "overview": item["overview"],
                            "mediaType": item["mediaType"],
                            "streamUrl": f"/api/stream/{item['id']}{suffix}",
                            "posterUrl": "",
                            "backdropUrl": "",
                            "durationMs": prog.get("durationMs", 0),
                            "playbackPositionMs": prog.get("playbackPositionMs", 0),
                            "codecBadge": item["codecBadge"],
                            "seriesName": item["seriesName"],
                            "seasonNumber": item["seasonNumber"],
                            "episodeNumber": item["episodeNumber"],
                            "genres": ["PC Host"],
                            "isFavorite": bool(prog.get("isFavorite", False)),
                            "watchStatus": prog.get("watchStatus", "UNWATCHED"),
                            "playCount": prog.get("playCount", 0),
                            "lastPlayedAt": prog.get("lastPlayedAt", 0),
                            "addedAt": item["addedAt"],
                            "fileSizeBytes": item["fileSizeBytes"],
                        })
                self._send_json(200, {
                    "serverId": engine.state["serverId"],
                    "serverName": engine.server_name,
                    "items": items_out,
                })
                return

            if path.startswith("/api/stream/"):
                item_id = path[len("/api/stream/"):]
                with engine.lock:
                    item = engine.catalog_by_id.get(item_id)
                if not item or not os.path.exists(item["filePath"]):
                    self._send_json(404, {"error": "Media file not found"})
                    return
                total_len = os.path.getsize(item["filePath"])
                range_hdr = self.headers.get("Range", "")
                start, end = 0, total_len - 1
                partial = False
                if range_hdr.lower().startswith("bytes="):
                    spec = range_hdr[6:].split(",")[0].strip()
                    parts = spec.split("-", 1)
                    if parts[0]:
                        start = int(parts[0])
                    if len(parts) > 1 and parts[1]:
                        end = int(parts[1])
                    if start > end or start >= total_len:
                        self.send_response(416)
                        self.send_header("Content-Range", f"bytes */{total_len}")
                        self.end_headers()
                        return
                    partial = True

                length = (end - start) + 1
                self.send_response(206 if partial else 200)
                self.send_header("Content-Type", item["mimeType"])
                self.send_header("Content-Length", str(length))
                self.send_header("Accept-Ranges", "bytes")
                if partial:
                    self.send_header("Content-Range", f"bytes {start}-{end}/{total_len}")
                self.end_headers()

                with open(item["filePath"], "rb") as f:
                    f.seek(start)
                    remaining = length
                    while remaining > 0:
                        chunk = f.read(min(65536, remaining))
                        if not chunk:
                            break
                        self.wfile.write(chunk)
                        remaining -= len(chunk)
                return

            self._send_json(404, {"error": "Not found"})

        def do_POST(self):
            parsed = urllib.parse.urlparse(self.path)
            path = parsed.path.rstrip("/") or "/"
            length = int(self.headers.get("Content-Length", "0") or 0)
            body_raw = self.rfile.read(length).decode("utf-8") if length > 0 else "{}"
            try:
                data = json.loads(body_raw) if body_raw.strip() else {}
            except Exception:
                data = {}

            if path == "/api/pair":
                pin = str(data.get("pin", "")).strip()
                if engine.pin and pin != engine.pin:
                    self._send_json(401, {"error": "Invalid pairing PIN"})
                    return
                token = secrets.token_hex(20)
                with engine.lock:
                    engine.state["tokens"] = (engine.state["tokens"][-23:] + [token])
                    engine._save_state()
                self._send_json(200, {
                    "token": token,
                    "serverId": engine.state["serverId"],
                    "serverName": engine.server_name,
                    "version": "1.0.0",
                    "hostPlatform": "Windows",
                })
                return

            if not self._is_authorized():
                self._send_json(401, {"error": "Unauthorized"})
                return

            parts = [p for p in path.split("/") if p]
            if len(parts) == 4 and parts[0] == "api" and parts[1] == "items":
                item_id, action = parts[2], parts[3]
                with engine.lock:
                    prog = engine.state["progress"].setdefault(item_id, {})
                    if action == "progress":
                        if "positionMs" in data:
                            prog["playbackPositionMs"] = int(data["positionMs"])
                        if "durationMs" in data:
                            prog["durationMs"] = int(data["durationMs"])
                        if "watchStatus" in data:
                            prog["watchStatus"] = str(data["watchStatus"])
                        prog["lastPlayedAt"] = int(time.time() * 1000)
                    elif action == "favorite" and "isFavorite" in data:
                        prog["isFavorite"] = bool(data["isFavorite"])
                    elif action == "watched" and "watchStatus" in data:
                        prog["watchStatus"] = str(data["watchStatus"])
                    engine._save_state()
                self._send_json(200, {"ok": True})
                return

            self._send_json(404, {"error": "Not found"})

    return Handler


def main():
    parser = argparse.ArgumentParser(description="LootStream PC Host Media Server")
    parser.add_argument("--dir", action="append", default=[], help="Media directory to share (repeatable)")
    parser.add_argument("--port", type=int, default=8765, help="HTTP port (default 8765)")
    parser.add_argument("--pin", default="", help="Optional pairing PIN")
    parser.add_argument("--name", default=None, help="Host server display name")
    args = parser.parse_args()

    dirs = args.dir or [str(Path.home() / "Videos")]
    engine = LootStreamHostEngine(dirs, server_name=args.name, pin=args.pin)
    server = ThreadingHTTPServer(("0.0.0.0", args.port), make_handler(engine))
    print(f"LootStream Host running on port {args.port} ({len(engine.catalog)} items)")
    server.serve_forever()


if __name__ == "__main__":
    main()
