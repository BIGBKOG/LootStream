# LootStream PC Host (`LootStreamHost.exe`)

Standalone personal media server host designed specifically for **LootStream Android**.

## Quick Start on Windows 10 / 11 (Zero Downloads Required)

Windows includes the `.NET Framework 4.x` C# compiler (`csc.exe`) out of the box.

1. Double-click **`Build-LootStreamHost-Exe.bat`** in this folder.
2. It produces **`LootStreamHost.exe`** right beside the script.
3. Double-click **`LootStreamHost.exe`**:
   - Automatically adds your Windows `Videos` and `Music` folders (you can add/remove any folders in the UI).
   - Displays your PC's LAN URL (for example `http://192.168.1.42:8765`).
   - Optionally set a numeric pairing PIN.
4. In **LootStream Android**, tap **Add Media -> Connect PC Host**, enter the displayed LAN URL (and PIN if set), and tap **Pair & Sync Host**.

> **Windows Firewall Note:** If prompted by Windows Defender Firewall on first launch, allow access on **Private networks** so your Android device on the same Wi-Fi can reach port `8765`.

## Cross-Platform Python Option

You can also run the zero-dependency Python 3 host on Windows, macOS, or Linux:

```bash
python3 lootstream_host.py --folders ~/Videos ~/Music --port 8765 --pin 1234
```

## Implemented Host Endpoints

- `GET /api/info` — Host name, platform, PIN requirement, indexed item count
- `POST /api/pair` — Validates optional PIN and returns bearer token
- `GET /api/library` — Returns indexed videos, movies, TV episodes, and audio with saved watch progress & favorites
- `GET /api/stream/{id}` — Direct HTTP streaming with `Accept-Ranges: bytes` and `206 Partial Content` seeking
- `POST /api/items/{id}/progress` — Saves playback position and watch status back to the host
- `POST /api/items/{id}/favorite` — Toggles favorite state on the host
- `POST /api/items/{id}/watched` — Updates watched state on the host
