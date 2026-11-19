# LootStream Windows PC Host

A standalone Windows desktop media host server designed exclusively for the **LootStream** Android app — no Jellyfin, Emby, or third-party media servers required.

## Quick Start on Windows (Build `LootStreamHost.exe`)

Every Windows 10 and Windows 11 PC already includes Microsoft .NET Framework 4.x (`csc.exe`). No Visual Studio, Python, or Node.js installation is needed:

1. Copy the `windows-host` folder to your Windows PC.
2. Double-click **`Build-LootStreamHost-Exe.bat`**.
3. It compiles **`LootStreamHost.exe`** in the same folder in ~2 seconds.
4. Double-click **`LootStreamHost.exe`**:
   - Add one or more folders containing your movies, shows, videos, or music.
   - Click **Start Host** (note your LAN URL, e.g., `http://192.168.1.42:8765`, and your 4-digit Pairing PIN).
   - If Windows Defender Firewall prompts, allow access on **Private Networks**.
   - *(Optional)* If you want external LAN access on standard non-admin accounts, click **Authorize Port / Firewall** inside the app (uses `netsh` UAC prompt once).
5. On your Android phone running **LootStream** (same Wi-Fi):
   - Tap **Connect PC Host** (Home or Add `+` sheet).
   - Enter the URL (e.g. `192.168.1.42:8765`) and PIN, then tap **Pair & Sync**.

## Cross-Platform / Python Option (`lootstream_host.py`)

If you prefer Python 3 (Windows, macOS, or Linux) or want to package via PyInstaller:

```bash
# Run headless CLI host
python3 lootstream_host.py --dir /path/to/media --port 8765 --pin 4242

# Optional: package to standalone .exe with PyInstaller
pip install pyinstaller
pyinstaller --onefile --name LootStreamHost lootstream_host.py
```

## Supported Formats & REST Endpoints

- **Video**: `.mp4`, `.mkv`, `.webm`, `.mov`, `.m4v`, `.avi`, `.ts`
- **Audio**: `.mp3`, `.flac`, `.wav`, `.m4a`, `.aac`, `.ogg`, `.opus`
- **Protocol**:
  - `GET /api/info` — Host identity, version, and PIN requirement
  - `POST /api/pair` — PIN pairing returning a persistent Bearer token
  - `GET /api/library` — Authenticated JSON library catalog with saved watch progress & favorites
  - `GET /api/stream/{id}` — Full `HTTP 206 Partial Content` byte-range streaming for smooth ExoPlayer seeking
  - `POST /api/items/{id}/progress` — Sync playback resume position & watched state back to PC
  - `POST /api/items/{id}/favorite` — Sync favorite state back to PC
  - `POST /api/items/{id}/watched` — Mark watched/unwatched on PC
