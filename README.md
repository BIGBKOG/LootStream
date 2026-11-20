# LootStream

Personal media streaming hub for Android (Kotlin + Jetpack Compose + Media3) with native **Linux Desktop App (`linux-host/`)** and **Windows Desktop App (`LootStreamHost-Setup.exe`)** hosts.

## Features

- **LootStream Linux Desktop App (`linux-host/`)**: Standalone native Linux desktop application (GTK3 + WebKit2GTK / native Tkinter fallback) where you can browse and play **Movies**, **TV Shows**, and **Music**, view QR / 6-digit Easy Login, and stream the same library and watch progress to Android over LAN — without opening browser tabs.
- **LootStream Windows Desktop App (`windows-host/`)**: Standalone native WinForms + embedded Windows Media Player desktop application (`LootStreamHost-Setup.exe`) with Home & QR Login dashboard, Movies/TV/Music card grids, Search, embedded Theater Player (with fullscreen, seek bar, volume, and progress sync), Persistent Music bar, Folder Picker, optional pairing PIN, and HTTP `206 Partial Content` range seeking.
- **Easy Wi-Fi Login**: Scan the QR code from Android's camera or enter the 6-digit code over the same Wi-Fi (UDP `:8766` auto-discovery + `/24` LAN fallback).
- **Local Media Import**: Zero-permission Android Photo Picker & Storage Access Framework (`SAF`) support for local videos and audio files.
- **Direct Stream URLs**: Play HLS (`.m3u8`), MPEG-DASH (`.mpd`), MP4, MKV, FLAC, and MP3 streams via Jetpack Media3 (ExoPlayer).
- **Cyber-Neon Material 3 UI**: 5-tab layout (Home, Library, Up Next, Vault, Settings), 23 accent themes, 6 background palettes, custom `.ttf`/`.otf` font support, and JSON backup/restore.

## Installing the Linux Desktop App (1-Click or 1-Command)

- **Ubuntu / Debian / Mint (1-Click `.deb`)**: Download **`linux-host/LootStreamHost-Linux.deb`** (or from GitHub Releases) and double-click it to install cleanly via Software Center / `apt`.
- **Any Linux Distro (GUI Wizard / Terminal)**: Run the easy installer without even needing `chmod +x`:
  ```bash
  bash linux-host/install.sh
  ```
  *(Or double-click / run `linux-host/LootStreamHost-Linux.run` for the graphical Zenity/KDialog setup wizard, desktop shortcut, custom SVG icon, and native desktop app launch.)*

See `linux-host/README.md` for `--silent`, `--install-only`, `--enable-service`, `--firewall`, `--no-open`, and `--uninstall`.

## Installing the Windows Desktop App (`LootStreamHost-Setup.exe`)

- **1-File Download (GitHub Releases)**: Pushing to GitHub triggers `.github/workflows/build-hosts.yml` on `windows-latest` and publishes **`LootStreamHost-Setup.exe`** to **GitHub Releases** — double-click it for a 1-click **Setup Wizard** where you can pick your Media Folders, enable Windows autostart, create shortcuts, open TCP `8765` / UDP `8766` in Windows Firewall in a single UAC step, and launch directly into the native Windows Desktop Media App.
- **Local Windows Build**: Or double-click `windows-host/Build-LootStreamHost-Exe.bat` on Windows 10/11 to compile `LootStreamHost-Setup.exe` using Windows' built-in `.NET Framework 4.x` (`csc.exe`).
