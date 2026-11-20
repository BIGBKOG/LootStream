#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_FILE="$SCRIPT_DIR/LootStreamHost-Linux.run"
TMP_STAGE="$(mktemp -d)"
trap 'rm -rf "$TMP_STAGE"' EXIT

cp "$SCRIPT_DIR/lootstream_linux_host.py" "$TMP_STAGE/"
cp "$SCRIPT_DIR/start-lootstream-host.sh" "$TMP_STAGE/"
cp "$SCRIPT_DIR/LootStreamHost.desktop" "$TMP_STAGE/"
cp "$SCRIPT_DIR/lootstream-host.service" "$TMP_STAGE/"
mkdir -p "$TMP_STAGE/web"
cp "$SCRIPT_DIR/web/index.html" "$TMP_STAGE/web/"
cp "$SCRIPT_DIR/web/styles.css" "$TMP_STAGE/web/"
cp "$SCRIPT_DIR/web/app.js" "$TMP_STAGE/web/"

chmod +x "$TMP_STAGE/start-lootstream-host.sh" "$TMP_STAGE/lootstream_linux_host.py"

PAYLOAD_TAR="$TMP_STAGE/payload.tar.gz"
tar -czf "$PAYLOAD_TAR" -C "$TMP_STAGE" \
  lootstream_linux_host.py \
  start-lootstream-host.sh \
  LootStreamHost.desktop \
  lootstream-host.service \
  web

PAYLOAD_SHA256="$(sha256sum "$PAYLOAD_TAR" | awk '{print $1}')"

cat > "$OUT_FILE" <<'INSTALLER_STUB'
#!/usr/bin/env bash
set -euo pipefail

# LootStream Linux Host — Single-File Self-Extracting Installer & Launcher
# Contains: Python server, Ampchor-inspired Media Center Web UI, desktop launcher & systemd unit.

APP_ID="lootstream-host"
APP_VERSION="1.0.0"
PAYLOAD_SHA256="__PAYLOAD_SHA256__"

PREFIX_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/$APP_ID"
BIN_DIR="$HOME/.local/bin"
APPS_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
SYSTEMD_USER_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
CONFIG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/$APP_ID"

INSTALL_ONLY=0
RUN_ONLY=0
AUTO_DEPS=0
SKIP_DEPS=0
ENABLE_SERVICE=0
UNINSTALL=0
PURGE=0
APP_ARGS=()

show_help() {
  cat <<EOF
LootStream Linux Host v$APP_VERSION (All-in-One Executable)

Usage:
  ./LootStreamHost-Linux.run [installer-options] [host-options]

Installer Options:
  --install-only      Install files, shortcuts & dependencies without starting the host
  --run-only          Skip install/update and launch from existing install
  --prefix <dir>      Custom install directory (default: ~/.local/share/lootstream-host)
  --auto-deps         Automatically install missing system packages (python3/ffmpeg/zenity)
  --skip-deps         Do not prompt or install system packages
  --enable-service    Enable and start background systemd --user service
  --uninstall         Remove installed files, CLI command & desktop launcher
  --purge             With --uninstall, also delete saved folders/PIN/watch progress
  -h, --help          Show this help message

Host Runtime Options (passed to LootStream Host):
  --folders <dirs...> Media directories to share
  --port <port>       HTTP port (default: 8765)
  --pin <pin>         Optional pairing PIN
  --name <name>       Display name for this host
  --no-open           Run headless without opening a desktop window
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --install-only) INSTALL_ONLY=1; shift ;;
    --run-only) RUN_ONLY=1; shift ;;
    --auto-deps) AUTO_DEPS=1; shift ;;
    --skip-deps) SKIP_DEPS=1; shift ;;
    --enable-service) ENABLE_SERVICE=1; shift ;;
    --uninstall) UNINSTALL=1; shift ;;
    --purge) PURGE=1; shift ;;
    --prefix)
      [[ $# -ge 2 ]] || { echo "Error: --prefix requires a directory path" >&2; exit 1; }
      PREFIX_DIR="$2"
      shift 2
      ;;
    -h|--help) show_help; exit 0 ;;
    *) APP_ARGS+=("$1"); shift ;;
  esac
done

run_as_root() {
  if [[ "$(id -u)" -eq 0 ]]; then
    "$@"
  elif command -v sudo >/dev/null 2>&1; then
    sudo "$@"
  elif command -v pkexec >/dev/null 2>&1; then
    pkexec "$@"
  else
    return 1
  fi
}

install_system_packages() {
  local pkgs=("$@")
  [[ ${#pkgs[@]} -gt 0 ]] || return 0

  if command -v apt-get >/dev/null 2>&1; then
    run_as_root apt-get update && run_as_root apt-get install -y "${pkgs[@]}"
  elif command -v dnf >/dev/null 2>&1; then
    run_as_root dnf install -y "${pkgs[@]}"
  elif command -v pacman >/dev/null 2>&1; then
    run_as_root pacman -Sy --noconfirm "${pkgs[@]}"
  elif command -v zypper >/dev/null 2>&1; then
    run_as_root zypper --non-interactive install "${pkgs[@]}"
  elif command -v apk >/dev/null 2>&1; then
    run_as_root apk add --no-cache "${pkgs[@]}"
  else
    return 1
  fi
}

check_and_install_deps() {
  [[ "$SKIP_DEPS" -eq 1 ]] && return 0

  local missing_required=()
  local missing_optional=()

  command -v python3 >/dev/null 2>&1 || missing_required+=("python3")
  command -v ffprobe >/dev/null 2>&1 || missing_optional+=("ffmpeg")
  if ! command -v zenity >/dev/null 2>&1 && ! command -v kdialog >/dev/null 2>&1; then
    missing_optional+=("zenity")
  fi

  local to_install=("${missing_required[@]}" "${missing_optional[@]}")
  [[ ${#to_install[@]} -gt 0 ]] || return 0

  if [[ "$AUTO_DEPS" -eq 1 ]]; then
    echo "[LootStream] Installing packages: ${to_install[*]}"
    install_system_packages "${to_install[@]}" || true
  elif [[ ${#missing_required[@]} -gt 0 ]]; then
    echo "[LootStream] Required dependency missing: ${missing_required[*]}"
    if [[ -t 0 ]]; then
      read -r -p "Install required packages (${to_install[*]}) using system package manager? [Y/n] " ans
      if [[ -z "$ans" || "$ans" =~ ^[Yy] ]]; then
        install_system_packages "${to_install[@]}" || true
      fi
    fi
  elif [[ -t 0 && ${#missing_optional[@]} -gt 0 ]]; then
    echo "[LootStream] Optional enhancements not installed: ${missing_optional[*]} (for media durations & native OS folder picker)."
    read -r -p "Install optional packages now? [y/N] " ans
    if [[ "$ans" =~ ^[Yy] ]]; then
      install_system_packages "${missing_optional[@]}" || true
    fi
  fi

  if ! command -v python3 >/dev/null 2>&1; then
    echo "[ERROR] python3 is required to run LootStream Linux Host." >&2
    exit 1
  fi
}

do_uninstall() {
  echo "[LootStream] Uninstalling LootStream Linux Host..."
  if command -v systemctl >/dev/null 2>&1; then
    systemctl --user disable --now lootstream-host.service >/dev/null 2>&1 || true
  fi
  rm -rf "$PREFIX_DIR"
  rm -f "$BIN_DIR/lootstream-host"
  rm -f "$APPS_DIR/lootstream-host.desktop"
  rm -f "$SYSTEMD_USER_DIR/lootstream-host.service"
  if [[ "$PURGE" -eq 1 ]]; then
    rm -rf "$CONFIG_DIR"
    echo "[LootStream] Removed app files and saved configuration."
  else
    echo "[LootStream] Removed app files (kept saved settings at $CONFIG_DIR)."
  fi
  exit 0
}

extract_and_install() {
  echo "[LootStream] Installing LootStream Linux Host v$APP_VERSION to $PREFIX_DIR ..."
  local tmp_tar
  tmp_tar="$(mktemp)"
  trap 'rm -f "$tmp_tar"' RETURN

  local archive_line
  archive_line="$(awk '/^__ARCHIVE_BELOW__$/{print NR + 1; exit 0;}' "$0")"
  tail -n +"$archive_line" "$0" | base64 -d > "$tmp_tar"

  if command -v sha256sum >/dev/null 2>&1; then
    local actual_sha
    actual_sha="$(sha256sum "$tmp_tar" | awk '{print $1}')"
    if [[ "$actual_sha" != "$PAYLOAD_SHA256" ]]; then
      echo "[ERROR] Embedded installer payload checksum mismatch!" >&2
      exit 1
    fi
  fi

  mkdir -p "$PREFIX_DIR" "$BIN_DIR" "$APPS_DIR" "$SYSTEMD_USER_DIR"
  tar -xzf "$tmp_tar" -C "$PREFIX_DIR"
  chmod +x "$PREFIX_DIR/lootstream_linux_host.py" "$PREFIX_DIR/start-lootstream-host.sh"

  cat > "$BIN_DIR/lootstream-host" <<EOF
#!/usr/bin/env bash
exec "$PREFIX_DIR/start-lootstream-host.sh" "\$@"
EOF
  chmod +x "$BIN_DIR/lootstream-host"

  cat > "$APPS_DIR/lootstream-host.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=LootStream Linux Host
Comment=Personal self-hosted media center for Movies, TV Shows, Music & Android streaming
Exec="$PREFIX_DIR/start-lootstream-host.sh"
Icon=multimedia-video-player
Terminal=false
Categories=AudioVideo;Video;Audio;Network;
Keywords=media;server;movies;tv;music;streaming;lootstream;
EOF
  chmod +x "$APPS_DIR/lootstream-host.desktop"

  if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database "$APPS_DIR" >/dev/null 2>&1 || true
  fi

  cat > "$SYSTEMD_USER_DIR/lootstream-host.service" <<EOF
[Unit]
Description=LootStream Linux Media Host
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
WorkingDirectory=$PREFIX_DIR
ExecStart=/usr/bin/python3 $PREFIX_DIR/lootstream_linux_host.py --no-open
Restart=on-failure
RestartSec=3

[Install]
WantedBy=default.target
EOF

  echo "$PAYLOAD_SHA256" > "$PREFIX_DIR/.installed-sha256"
  echo "[LootStream] Installed:"
  echo "  App files : $PREFIX_DIR"
  echo "  Command   : $BIN_DIR/lootstream-host"
  echo "  App menu  : $APPS_DIR/lootstream-host.desktop"
  echo "  Service   : $SYSTEMD_USER_DIR/lootstream-host.service"
}

if [[ "$UNINSTALL" -eq 1 ]]; then
  do_uninstall
fi

check_and_install_deps

if [[ "$RUN_ONLY" -eq 0 ]]; then
  installed_sha=""
  if [[ -f "$PREFIX_DIR/.installed-sha256" ]]; then
    installed_sha="$(cat "$PREFIX_DIR/.installed-sha256" 2>/dev/null || true)"
  fi
  if [[ "$installed_sha" != "$PAYLOAD_SHA256" || ! -f "$PREFIX_DIR/lootstream_linux_host.py" ]]; then
    extract_and_install
  fi
fi

if [[ "$ENABLE_SERVICE" -eq 1 ]]; then
  if command -v systemctl >/dev/null 2>&1; then
    systemctl --user daemon-reload
    systemctl --user enable --now lootstream-host.service
    echo "[LootStream] Enabled and started systemd --user service."
  else
    echo "[WARN] systemctl not found; skipping --enable-service." >&2
  fi
fi

if [[ "$INSTALL_ONLY" -eq 1 ]]; then
  echo "[LootStream] Setup complete. Run 'lootstream-host' or '$PREFIX_DIR/start-lootstream-host.sh' anytime."
  exit 0
fi

exec python3 "$PREFIX_DIR/lootstream_linux_host.py" "${APP_ARGS[@]}"

__ARCHIVE_BELOW__
INSTALLER_STUB

sed -i "s/__PAYLOAD_SHA256__/$PAYLOAD_SHA256/" "$OUT_FILE"
base64 "$PAYLOAD_TAR" >> "$OUT_FILE"
chmod +x "$OUT_FILE"

echo "Built single-file Linux executable: $OUT_FILE ($(du -h "$OUT_FILE" | cut -f1))"
