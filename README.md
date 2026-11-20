# LootStream

Personal media streaming hub for Android (Kotlin + Jetpack Compose + Media3) with companion **Linux Host (`linux-host/`)** and **Windows Host (`LootStreamHost.exe`)** applications.

## Features

- **LootStream Linux Host (`linux-host/`)**: Ampchor-inspired self-hosted Linux media center where you can browse and watch **Movies**, **TV Shows**, and **Music** directly inside the desktop app while streaming the same library and watch progress to Android over LAN.
- **LootStream Windows Host (`windows-host/`)**: Native Windows desktop server (`LootStreamHost.exe`) with folder picker, optional pairing PIN, and HTTP `206 Partial Content` range seeking.
- **Local Media Import**: Zero-permission Android Photo Picker & Storage Access Framework (`SAF`) support for local videos and audio files.
- **Direct Stream URLs**: Play HLS (`.m3u8`), MPEG-DASH (`.mpd`), MP4, MKV, FLAC, and MP3 streams via Jetpack Media3 (ExoPlayer).
- **Cyber-Neon Material 3 UI**: 5-tab layout (Home, Library, Up Next, Vault, Settings), 23 accent themes, 6 background palettes, custom `.ttf`/`.otf` font support, and JSON backup/restore.

## Running the Linux Host (All-in-One Executable)

Everything comes bundled inside **`linux-host/LootStreamHost-Linux.run`**, which automatically installs the app files, desktop shortcut, optional system dependencies (`ffmpeg`, `zenity`), and launches the Media Center:

```bash
chmod +x linux-host/LootStreamHost-Linux.run
./linux-host/LootStreamHost-Linux.run
```

See `linux-host/README.md` for `--install-only`, `--auto-deps`, `--enable-service`, `--no-open`, and `ufw` firewall instructions.

## Running the Windows Host (Single `.exe` Installer)

- **1-File Download (GitHub Releases)**: Pushing to GitHub triggers `.github/workflows/build-hosts.yml` on `windows-latest` and publishes **`LootStreamHost-Setup.exe`** to **GitHub Releases** — double-click it to self-install (`%LocalAppData%\LootStreamHost`), create Start Menu & Desktop shortcuts, configure Firewall port `8765`, and launch the built-in Media Center.
- **Local Windows Build**: Or double-click `windows-host/Build-LootStreamHost-Exe.bat` on Windows 10/11 to compile `LootStreamHost-Setup.exe` using Windows' built-in `.NET Framework 4.x` (`csc.exe`).
