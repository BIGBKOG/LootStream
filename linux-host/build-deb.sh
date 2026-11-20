#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DEB="$SCRIPT_DIR/LootStreamHost-Linux.deb"
TMP_PKG="$(mktemp -d)"
trap 'rm -rf "$TMP_PKG"' EXIT

PKG_NAME="lootstream-host"
PKG_VERSION="1.0.0"

chmod 0755 "$TMP_PKG"
mkdir -p \
  "$TMP_PKG/DEBIAN" \
  "$TMP_PKG/opt/lootstream-host/web" \
  "$TMP_PKG/usr/bin" \
  "$TMP_PKG/usr/share/applications" \
  "$TMP_PKG/usr/share/icons/hicolor/scalable/apps" \
  "$TMP_PKG/usr/lib/systemd/user"

cp "$SCRIPT_DIR/lootstream_linux_host.py" "$TMP_PKG/opt/lootstream-host/"
cp "$SCRIPT_DIR/start-lootstream-host.sh" "$TMP_PKG/opt/lootstream-host/"
cp "$SCRIPT_DIR/web/index.html" "$TMP_PKG/opt/lootstream-host/web/"
cp "$SCRIPT_DIR/web/styles.css" "$TMP_PKG/opt/lootstream-host/web/"
cp "$SCRIPT_DIR/web/app.js" "$TMP_PKG/opt/lootstream-host/web/"
cp "$SCRIPT_DIR/lootstream-host.svg" "$TMP_PKG/usr/share/icons/hicolor/scalable/apps/lootstream-host.svg"

chmod 0755 "$TMP_PKG/opt/lootstream-host/lootstream_linux_host.py" "$TMP_PKG/opt/lootstream-host/start-lootstream-host.sh"

cat > "$TMP_PKG/usr/bin/lootstream-host" <<'EOF'
#!/usr/bin/env bash
exec /opt/lootstream-host/start-lootstream-host.sh "$@"
EOF
chmod 0755 "$TMP_PKG/usr/bin/lootstream-host"

cat > "$TMP_PKG/usr/share/applications/lootstream-host.desktop" <<'EOF'
[Desktop Entry]
Type=Application
Name=LootStream Linux Host
GenericName=Personal Media Center & Streaming Host
Comment=Watch Movies, TV Shows & Music and stream over Wi-Fi to LootStream Android
Exec=/usr/bin/lootstream-host
Icon=lootstream-host
Terminal=false
Categories=AudioVideo;Video;Audio;Network;
Keywords=media;server;movies;tv;music;streaming;lootstream;
EOF
chmod 0644 "$TMP_PKG/usr/share/applications/lootstream-host.desktop"

cat > "$TMP_PKG/usr/lib/systemd/user/lootstream-host.service" <<'EOF'
[Unit]
Description=LootStream Linux Media Host
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
WorkingDirectory=/opt/lootstream-host
ExecStart=/usr/bin/python3 /opt/lootstream-host/lootstream_linux_host.py --no-open
Restart=on-failure
RestartSec=3

[Install]
WantedBy=default.target
EOF
chmod 0644 "$TMP_PKG/usr/lib/systemd/user/lootstream-host.service"

INSTALLED_KB="$(du -sk "$TMP_PKG/opt" "$TMP_PKG/usr" | awk '{sum+=$1} END{print sum}')"

cat > "$TMP_PKG/DEBIAN/control" <<EOF
Package: $PKG_NAME
Version: $PKG_VERSION
Section: video
Priority: optional
Architecture: all
Installed-Size: ${INSTALLED_KB:-128}
Maintainer: LootStream <support@lootstream.local>
Depends: python3 (>= 3.8)
Recommends: ffmpeg, zenity | kdialog
Description: LootStream Linux Host Media Center and Android LAN Server
 Personal self-hosted media center for browsing and watching Movies,
 TV Shows, and Music on Linux while streaming to LootStream Android
 over Wi-Fi with QR Code and 6-digit Easy Login.
EOF

cat > "$TMP_PKG/DEBIAN/postinst" <<'EOF'
#!/usr/bin/env bash
set -e
if command -v update-desktop-database >/dev/null 2>&1; then
  update-desktop-database /usr/share/applications >/dev/null 2>&1 || true
fi
if command -v gtk-update-icon-cache >/dev/null 2>&1; then
  gtk-update-icon-cache -q /usr/share/icons/hicolor >/dev/null 2>&1 || true
fi
if command -v ufw >/dev/null 2>&1; then
  if ufw status 2>/dev/null | grep -qi "Status: active"; then
    ufw allow 8765/tcp comment "LootStream Host HTTP" >/dev/null 2>&1 || true
    ufw allow 8766/udp comment "LootStream Host Discovery" >/dev/null 2>&1 || true
  fi
fi
exit 0
EOF
chmod 0755 "$TMP_PKG/DEBIAN/postinst"

dpkg-deb --build --root-owner-group "$TMP_PKG" "$OUT_DEB"
echo "Built Debian/Ubuntu package: $OUT_DEB ($(du -h "$OUT_DEB" | cut -f1))"
