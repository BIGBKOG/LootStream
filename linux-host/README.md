# LootStream Linux Host

Self-hosted personal media center & LAN streaming server for Linux — inspired by [Ampchor](https://ampchor.com/). Browse and watch **Movies**, **TV Shows** (grouped by series & episode), and listen to **Music** directly inside the Linux Host desktop web app while streaming the same library and watch progress to the **LootStream Android** app.

## Features

- **Watch & Listen Inside the Linux Host App**:
  - **Movies & Videos**: Poster grid with codec badges, runtimes (via `ffprobe` when installed), resume progress bars, and a fullscreen **Theater Video Player** (`0.75x–2.0x` speed, Fit/Fill toggle, `-10s/+10s` skip, Next, Favorite, Mark Watched).
  - **TV Shows**: Automatically groups `Show.S01E02.mkv` or files inside `TV/` / `Shows/` / `Series/` / `Anime/` folders by series, season, and episode.
  - **Music**: Dedicated track browser + persistent bottom **Music Player Dock** (play/pause, previous/next, seek bar, volume control) supporting `.mp3`, `.flac`, `.wav`, `.m4a`, `.aac`, `.ogg`, `.opus`.
  - **Local Artwork**: Automatically detects `poster.jpg`, `cover.jpg`, `folder.jpg`, `<filename>.jpg`, and `fanart.jpg`/`backdrop.jpg` alongside your media files and serves them to both Linux and Android.
- **Easy Wi-Fi Login & Cross-Device Sync with LootStream Android**:
  - **QR Code & 6-Digit Code**: Open the Linux Host Media Center to scan the live **Easy Wi-Fi Login QR code** or enter the rotating **6-digit code** in LootStream Android — zero IP typing needed.
  - **UDP Auto-Discovery**: Listens on UDP port `8766` so Android automatically finds your Linux Host on the same Wi-Fi.
  - Implements the full LootStream LAN protocol (`/api/info`, `/api/pair`, `/api/host/quick-code/refresh`, `/api/library`, `/api/stream/{id}` with HTTP `206 Partial Content` byte-range seeking, `/api/artwork/{id}`, and `/api/items/{id}/progress|favorite|watched`).
  - Persists shared folders, pairing PIN/tokens, paired device list, and watch progress in `~/.config/lootstream-host/state.json`.
- **Zero Third-Party Python Dependencies**: Runs purely on standard library **Python 3**.

## Quick Start (3 Easy Install Options)

### Option 1: Double-Click `.deb` (Ubuntu, Debian, Linux Mint, Pop!_OS)
Download **`LootStreamHost-Linux.deb`** from GitHub Releases (or build with `./linux-host/build-deb.sh`) and double-click it to install via Software Center / `apt`:
- Automatically installs `python3` if needed, registers **LootStream Linux Host** with its custom cyber icon in your app menu, creates `/usr/bin/lootstream-host`, and configures UFW firewall rules if active.

### Option 2: 1-Command Terminal Installer (No `chmod +x` needed)
```bash
bash linux-host/install.sh
```
*(Or pass flags like `bash linux-host/install.sh --silent --enable-service --firewall`.)*

### Option 3: Graphical All-in-One Executable (`LootStreamHost-Linux.run`)
Everything — Python server, Media Center Web UI, custom SVG icon, desktop shortcut, and `systemd --user` service — is bundled inside **`LootStreamHost-Linux.run`**. When run in a desktop session, it opens a **Zenity/KDialog Setup Wizard** for 1-click install, shortcuts, autostart, and firewall setup:

```bash
chmod +x linux-host/LootStreamHost-Linux.run
./linux-host/LootStreamHost-Linux.run
```

Useful installer flags:
- `--silent` / `--yes` — 1-command unattended express install + launch
- `--install-only --auto-deps` — install everything without launching yet
- `--enable-service` — enable background autostart on login (`systemd --user`)
- `--firewall` — open TCP `8765` & UDP `8766` in `ufw` if active
- `--uninstall` — remove installed files and shortcuts (add `--purge` to delete saved config)

You can rebuild `LootStreamHost-Linux.run` and `LootStreamHost-Linux.deb` anytime using `./linux-host/build-installer.sh` and `./linux-host/build-deb.sh`.

Or pass runtime options (add `--no-open` for headless servers):

```bash
lootstream-host --folders ~/Videos ~/Music --port 8765 --pin 1234
```

Then open `http://127.0.0.1:8765/` in your browser or connect from the **LootStream Android** app by scanning the QR code, typing the 6-digit code, or entering your LAN URL.

## Optional Linux Packages

- **`ffmpeg` (`ffprobe`)**: Extracts accurate video & audio durations automatically (`sudo apt install ffmpeg`).
- **`zenity` or `kdialog`**: Enables the native **OS Picker** button in **+ Add Folder** (a built-in Linux directory browser works even without them).
- **Firewall (`ufw`)**: If `ufw` is active and Android cannot reach or discover the host, allow TCP `8765` and UDP `8766`:
  ```bash
  sudo ufw allow 8765/tcp
  sudo ufw allow 8766/udp
  ```

## Optional Background Service (`systemd --user`)

Copy `lootstream-host.service` to `~/.config/systemd/user/` (adjust `WorkingDirectory` and `ExecStart` paths if needed), then enable:

```bash
mkdir -p ~/.config/systemd/user
cp linux-host/lootstream-host.service ~/.config/systemd/user/
systemctl --user daemon-reload
systemctl --user enable --now lootstream-host.service
```
