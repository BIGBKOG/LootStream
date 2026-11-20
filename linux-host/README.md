# LootStream Linux Desktop App & Host

Standalone native Linux desktop media application & LAN streaming server. Browse and watch **Movies**, **TV Shows** (grouped by series & episode), and **Music** directly inside a real desktop window while streaming the same library and watch progress to **LootStream Android**.

## Features

- **Real Standalone Linux Desktop App Window** (never opens browser tabs by default):
  - **Primary Engine**: Native **GTK3 + WebKit2GTK** desktop application window with app icon, `StartupWMClass`, integrated video/audio playback, and clean server shutdown on window close.
  - **Built-In Tkinter Fallback**: Native **Tkinter** dark cyber desktop window with built-in QR code canvas, 6-digit Easy Login, Movies/TV/Music library browser, search, Favorites/Watched toggles, folder management, and native playback (`mpv` / `vlc` / `celluloid` / `ffplay` / `xdg-open`).
- **Media Library & Local Artwork**:
  - Automatically scans Movies, TV Shows (`Show.S01E02.mkv` or `TV/`/`Shows/`/`Series/`/`Anime/`), and Music (`.mp3`, `.flac`, `.wav`, `.m4a`, `.aac`, `.ogg`, `.opus`).
  - Automatically detects `poster.jpg`, `cover.jpg`, `folder.jpg`, `<filename>.jpg`, and `fanart.jpg`/`backdrop.jpg` alongside your media files and serves them to both Linux and Android.
- **Easy Wi-Fi Login & Cross-Device Sync with LootStream Android**:
  - **QR Code & 6-Digit Code**: Scan the live **Easy Wi-Fi Login QR code** or enter the rotating **6-digit code** in LootStream Android — zero IP typing needed.
  - **UDP Auto-Discovery**: Listens on UDP port `8766` so Android automatically finds your Linux Host on the same Wi-Fi.
  - Implements the full LootStream LAN protocol (`/api/info`, `/api/pair`, `/api/host/quick-code/refresh`, `/api/library`, `/api/stream/{id}` with HTTP `206 Partial Content` byte-range seeking, `/api/artwork/{id}`, and `/api/items/{id}/progress|favorite|watched`).
  - Persists shared folders, pairing PIN/tokens, paired device list, and watch progress in `~/.config/lootstream-host/state.json`.

## Quick Start (3 Easy Install Options)

### Option 1: Double-Click `.deb` (Ubuntu, Debian, Linux Mint, Pop!_OS)
Download **`LootStreamHost-Linux.deb`** from GitHub Releases (or build with `./linux-host/build-deb.sh`) and double-click it to install via Software Center / `apt`:
- Automatically ensures `python3` + native desktop GUI runtime (`python3-gi`, `gir1.2-gtk-3.0`, `gir1.2-webkit2`, or `python3-tk`) are installed, registers **LootStream** with its custom cyber icon in your app menu, creates `/usr/bin/lootstream-host`, and configures UFW firewall rules if active.

### Option 2: 1-Command Terminal Installer (No `chmod +x` needed)
```bash
bash linux-host/install.sh
```
*(Or pass flags like `bash linux-host/install.sh --silent --enable-service --firewall`.)*

### Option 3: Graphical All-in-One Executable (`LootStreamHost-Linux.run`)
Everything — Python server, native desktop app (`lootstream_desktop_app.py`), Media Center assets, custom SVG icon, desktop shortcut, and `systemd --user` service — is bundled inside **`LootStreamHost-Linux.run`**. When run in a desktop session, it opens a **Zenity/KDialog Setup Wizard** for 1-click install, shortcuts, autostart, firewall setup, and launches the LootStream desktop app:

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

Or pass runtime options (add `--no-open` for headless background servers, or `--open-browser` if you explicitly want a browser):

```bash
lootstream-host --folders ~/Videos ~/Music --port 8765 --pin 1234
```

## Optional Linux Packages

- **`ffmpeg` (`ffprobe`)**: Extracts accurate video & audio durations automatically (`sudo apt install ffmpeg`).
- **`mpv` or `vlc`**: Recommended if using the Tkinter fallback desktop mode for local video/audio playback.
- **`zenity` or `kdialog`**: Enables the OS setup wizard & native folder picker dialog.
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
