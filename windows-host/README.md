# LootStream Windows Desktop App (`LootStreamHost-Setup.exe`)

Single-file self-installing **native Windows Desktop Media App** & Android LAN streaming host. Runs directly inside its own Windows desktop window with no browser required.

## Option 1: Download the Ready-to-Run `.exe` Installer (Recommended)

Once pushed to GitHub, the **Build Windows & Linux Host Installers** workflow automatically compiles and publishes **`LootStreamHost-Setup.exe`** to **GitHub Releases** (and Workflow Artifacts):

1. Download **`LootStreamHost-Setup.exe`** from the repository's **Releases** page — no `.cs` files or extra downloads needed.
2. Double-click **`LootStreamHost-Setup.exe`** to open the **LootStream Host Setup Wizard**, then click **Install & Launch**:
   - Lets you pick or keep the install folder (`%LocalAppData%\LootStreamHost\LootStreamHost.exe`) and pre-add your Movie/TV/Music folders right inside the installer
   - Creates **Start Menu** and **Desktop** shortcuts (`LootStream Host`)
   - Optionally launches automatically when Windows starts
   - Registers **LootStream Host** in Windows **Installed Apps** (`--uninstall` supported with optional settings cleanup)
   - Adds Windows Firewall allow rules for TCP `8765` + UDP discovery `8766` and HTTP LAN URL binding in a single UAC step
   - Opens directly as a full **Windows Desktop App** featuring:
     - **Home & Easy Wi-Fi Login**: Library stats, live **QR Code** + **6-digit code**, and Continue Watching / Recently Added cards
     - **Movies, TV Shows & Music Libraries**: Filterable poster/cover cards, live Search, ★ Favorites, and ✓ Watched toggles
     - **Built-In Theater Player**: Watch movies & shows or listen to music right inside the app with Play/Pause, Seek bar, `-10s/+10s`, Prev/Next, Volume/Mute, Fullscreen (`Esc` to exit), and shared Android watch progress sync
     - **Folders & Server Manager**: Add/remove media folders, configure optional PIN, inspect paired Android devices, and view live activity logs

*(CLI flags: `--install`, `--install --silent`, `--portable`, `--uninstall`, `--uninstall --silent`.)*

## Option 2: Compile Locally on Windows 10 / 11

If you cloned the source directly on Windows, double-click **`Build-LootStreamHost-Exe.bat`** — it uses Windows' built-in `.NET Framework 4.x` (`csc.exe`) to compile `LootStreamHost.cs` into **`LootStreamHost-Setup.exe`**.

## Implemented Host Endpoints (for Android & LAN)

- `UDP 8766` (`LOOTSTREAM_DISCOVER_V1`) — Responds on the local Wi-Fi with host name, platform, and LAN URLs for zero-IP discovery
- `GET /api/info` — Host name, platform, PIN requirement, indexed item count
- `POST /api/pair` — Validates either the **6-digit Easy Login code** (`quickCode`) or optional static `pin` and returns a bearer token
- `POST /api/host/quick-code/refresh` — Generates a fresh 6-digit Easy Login code & QR URI
- `GET /api/library` — Returns indexed videos, movies, TV episodes, and audio with saved watch progress & favorites
- `GET /api/stream/{id}` — Direct HTTP streaming with `Accept-Ranges: bytes` and `206 Partial Content` seeking
- `POST /api/items/{id}/progress` — Saves playback position and watch status back to the host
- `POST /api/items/{id}/favorite` — Toggles favorite state on the host
- `POST /api/items/{id}/watched` — Updates watched state on the host
