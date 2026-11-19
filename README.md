# LootStream

Personal media streaming hub for Android (Kotlin + Jetpack Compose + Media3) with a companion **Windows PC Host (`LootStreamHost.exe`)**.

## Features

- **LootStream PC Host (`windows-host/`)**: Stream movies, TV shows, videos, and music directly from your Windows PC over LAN with HTTP `206 Partial Content` range seeking, optional numeric pairing PIN, and saved watch progress/favorites.
- **Local Media Import**: Zero-permission Android Photo Picker & Storage Access Framework (`SAF`) support for local videos and audio files.
- **Direct Stream URLs**: Play HLS (`.m3u8`), MPEG-DASH (`.mpd`), MP4, MKV, and audio streams via Jetpack Media3 (ExoPlayer).
- **Cyber-Neon Material 3 UI**: 5-tab layout (Home, Library, Up Next, Vault, Settings), 23 accent themes, 6 background palettes, custom `.ttf`/`.otf` font support, and JSON backup/restore.

## Building & Running the Windows Host

1. Open the `windows-host/` folder on Windows 10 or 11.
2. Double-click **`Build-LootStreamHost-Exe.bat`** to compile `LootStreamHost.cs` into **`LootStreamHost.exe`** using Windows' built-in `.NET Framework 4.x` (`csc.exe`).
3. Launch **`LootStreamHost.exe`**, select your media folders, and pair from the Android app under **Add Media -> Connect PC Host**.

*(Cross-platform Python host is also available via `python3 windows-host/lootstream_host.py`.)*
