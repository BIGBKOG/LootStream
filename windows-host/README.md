# LootStream Windows Host (`LootStreamHost-Setup.exe`)

Single-file self-installing Windows personal media host & built-in Media Center designed for **LootStream Android** and desktop playback.

## Option 1: Download the Ready-to-Run `.exe` Installer (Recommended)

Once pushed to GitHub, the **Build Windows & Linux Host Installers** workflow automatically compiles and publishes **`LootStreamHost-Setup.exe`** to **GitHub Releases** (and Workflow Artifacts):

1. Download **`LootStreamHost-Setup.exe`** from the repository's **Releases** page — no `.cs` files or extra downloads needed.
2. Double-click **`LootStreamHost-Setup.exe`** to open the **LootStream Host Setup Wizard**, then click **Install & Launch**:
   - Lets you pick or keep the install folder (`%LocalAppData%\LootStreamHost\LootStreamHost.exe`) and pre-add your Movie/TV/Music folders right inside the installer
   - Creates **Start Menu** and **Desktop** shortcuts (`LootStream Host`)
   - Optionally launches automatically when Windows starts
   - Registers **LootStream Host** in Windows **Installed Apps** (`--uninstall` supported with optional settings cleanup)
   - Adds Windows Firewall allow rules for TCP `8765` + UDP discovery `8766` and HTTP LAN URL binding in a single UAC step
   - Launches immediately and opens the built-in **Media Center** (`http://127.0.0.1:8765/`) so you can watch Movies, TV Shows, and Music on Windows and connect **LootStream Android** via **QR Code**, **6-digit Easy Login code**, or auto-discovery.

*(CLI flags: `--install`, `--install --silent`, `--portable`, `--uninstall`, `--uninstall --silent`.)*

## Option 2: Compile Locally on Windows 10 / 11

If you cloned the source directly on Windows, double-click **`Build-LootStreamHost-Exe.bat`** — it uses Windows' built-in `.NET Framework 4.x` (`csc.exe`) to compile `LootStreamHost.cs` + embedded Web UI resources into the single **`LootStreamHost-Setup.exe`**.

## Implemented Host Endpoints

- `UDP 8766` (`LOOTSTREAM_DISCOVER_V1`) — Responds on the local Wi-Fi with host name, platform, and LAN URLs for zero-IP discovery
- `GET /api/info` — Host name, platform, PIN requirement, indexed item count
- `POST /api/pair` — Validates either the **6-digit Easy Login code** (`quickCode`) or optional static `pin` and returns a bearer token
- `POST /api/host/quick-code/refresh` — Generates a fresh 6-digit Easy Login code & QR URI
- `GET /api/library` — Returns indexed videos, movies, TV episodes, and audio with saved watch progress & favorites
- `GET /api/stream/{id}` — Direct HTTP streaming with `Accept-Ranges: bytes` and `206 Partial Content` seeking
- `POST /api/items/{id}/progress` — Saves playback position and watch status back to the host
- `POST /api/items/{id}/favorite` — Toggles favorite state on the host
- `POST /api/items/{id}/watched` — Updates watched state on the host
