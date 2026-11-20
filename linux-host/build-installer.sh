#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_FILE="$SCRIPT_DIR/LootStreamHost-Linux.run"
TMP_STAGE="$(mktemp -d)"
trap 'rm -rf "$TMP_STAGE"' EXIT

cp "$SCRIPT_DIR/lootstream_linux_host.py" "$TMP_STAGE/"
cp "$SCRIPT_DIR/lootstream_desktop_app.py" "$TMP_STAGE/"
cp "$SCRIPT_DIR/start-lootstream-host.sh" "$TMP_STAGE/"
cp "$SCRIPT_DIR/LootStreamHost.desktop" "$TMP_STAGE/"
cp "$SCRIPT_DIR/lootstream-host.service" "$TMP_STAGE/"
cp "$SCRIPT_DIR/lootstream-host.svg" "$TMP_STAGE/"
mkdir -p "$TMP_STAGE/web"
cp "$SCRIPT_DIR/web/index.html" "$TMP_STAGE/web/"
cp "$SCRIPT_DIR/web/styles.css" "$TMP_STAGE/web/"
cp "$SCRIPT_DIR/web/app.js" "$TMP_STAGE/web/"

chmod +x "$TMP_STAGE/start-lootstream-host.sh" "$TMP_STAGE/lootstream_linux_host.py" "$TMP_STAGE/lootstream_desktop_app.py"

PAYLOAD_TAR="$TMP_STAGE/payload.tar.gz"
tar -czf "$PAYLOAD_TAR" -C "$TMP_STAGE" \
  lootstream_linux_host.py \
  lootstream_desktop_app.py \
  start-lootstream-host.sh \
  LootStreamHost.desktop \
  lootstream-host.service \
  lootstream-host.svg \
  web

PAYLOAD_SHA256="$(sha256sum "$PAYLOAD_TAR" | awk '{print $1}')"

cat > "$OUT_FILE" <<'INSTALLER_STUB'
#!/usr/bin/env bash
set -euo pipefail

# LootStream Linux Host — Easy Single-File Graphical & CLI Installer
# Double-click in file manager OR run from terminal: ./LootStreamHost-Linux.run

APP_ID="lootstream-host"
APP_VERSION="1.0.0"
PAYLOAD_SHA256="__PAYLOAD_SHA256__"

PREFIX_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/$APP_ID"
BIN_DIR="$HOME/.local/bin"
APPS_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
ICONS_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/icons/hicolor/scalable/apps"
SYSTEMD_USER_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
CONFIG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/$APP_ID"

INSTALL_ONLY=0
RUN_ONLY=0
SILENT=0
AUTO_DEPS=0
SKIP_DEPS=0
ENABLE_SERVICE=0
CREATE_DESKTOP_SHORTCUT=1
CONFIGURE_FIREWALL=0
UNINSTALL=0
PURGE=0
APP_ARGS=()

show_help() {
  cat <<EOF
LootStream Linux Host v$APP_VERSION (All-in-One Installer & Launcher)

Usage:
  ./LootStreamHost-Linux.run [installer-options] [host-options]

Installer Options:
  --install-only      Install files, icon & shortcuts without launching the host
  --run-only          Skip install/update and launch from existing install
  --silent, --yes     Non-interactive express install using smart defaults
  --prefix <dir>      Custom install directory (default: ~/.local/share/lootstream-host)
  --auto-deps         Automatically install missing system packages (python3/ffmpeg/zenity)
  --skip-deps         Do not prompt or install system packages
  --enable-service    Enable background systemd --user service on login
  --firewall          Allow TCP 8765 & UDP 8766 in ufw if ufw is active
  --no-desktop        Do not place a shortcut icon on ~/Desktop
  --uninstall         Remove installed files, CLI command & shortcuts
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
    --silent|--yes|-y) SILENT=1; AUTO_DEPS=1; shift ;;
    --auto-deps) AUTO_DEPS=1; shift ;;
    --skip-deps) SKIP_DEPS=1; shift ;;
    --enable-service) ENABLE_SERVICE=1; shift ;;
    --firewall) CONFIGURE_FIREWALL=1; shift ;;
    --no-desktop) CREATE_DESKTOP_SHORTCUT=0; shift ;;
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

has_gui_session() {
  [[ -n "${DISPLAY:-}${WAYLAND_DISPLAY:-}" ]]
}

notify_error() {
  local msg="$1"
  echo "[ERROR] $msg" >&2
  if has_gui_session; then
    if command -v zenity >/dev/null 2>&1; then
      zenity --error --title="LootStream Host Setup" --text="$msg" --width=400 2>/dev/null || true
    elif command -v kdialog >/dev/null 2>&1; then
      kdialog --error "$msg" --title "LootStream Host Setup" 2>/dev/null || true
    fi
  fi
}

run_as_root() {
  if [[ "$(id -u)" -eq 0 ]]; then
    "$@"
  elif has_gui_session && command -v pkexec >/dev/null 2>&1 && [[ ! -t 0 ]]; then
    pkexec "$@"
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
    run_as_root bash -c "apt-get update && apt-get install -y ${pkgs[*]}"
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

run_gui_wizard_if_needed() {
  [[ "$SILENT" -eq 1 || "$RUN_ONLY" -eq 1 || "$INSTALL_ONLY" -eq 1 ]] && return 0
  # Only show setup wizard when first installing or upgrading
  local installed_sha=""
  if [[ -f "$PREFIX_DIR/.installed-sha256" ]]; then
    installed_sha="$(cat "$PREFIX_DIR/.installed-sha256" 2>/dev/null || true)"
  fi
  if [[ "$installed_sha" == "$PAYLOAD_SHA256" && -f "$PREFIX_DIR/lootstream_linux_host.py" ]]; then
    return 0
  fi

  if has_gui_session && command -v zenity >/dev/null 2>&1 && [[ ! -t 0 ]]; then
    local choices
    choices="$(zenity --list --checklist \
      --title="LootStream Linux Host v$APP_VERSION Setup" \
      --text="One-click installation for LootStream Media Center & Android Wi-Fi Host.\nSelect options below and click OK to install & launch:" \
      --width=540 --height=330 \
      --column="Enable" --column="Option" --column="Description" \
      TRUE "desktop" "Create Desktop & App Menu Launcher Icon" \
      TRUE "deps" "Install FFmpeg (video durations) if missing" \
      FALSE "service" "Auto-start LootStream Host in background on login" \
      FALSE "firewall" "Open UFW Firewall for Android Wi-Fi / QR Login (8765, 8766)" \
      --print-column=2 --separator="|" 2>/dev/null || echo "__CANCELLED__")"
    if [[ "$choices" == "__CANCELLED__" ]]; then
      exit 0
    fi
    [[ "$choices" == *"desktop"* ]] && CREATE_DESKTOP_SHORTCUT=1 || CREATE_DESKTOP_SHORTCUT=0
    [[ "$choices" == *"deps"* ]] && AUTO_DEPS=1 || SKIP_DEPS=1
    [[ "$choices" == *"service"* ]] && ENABLE_SERVICE=1
    [[ "$choices" == *"firewall"* ]] && CONFIGURE_FIREWALL=1
  elif has_gui_session && command -v kdialog >/dev/null 2>&1 && [[ ! -t 0 ]]; then
    if ! kdialog --title "LootStream Linux Host v$APP_VERSION Setup" \
      --yesno "Install LootStream Linux Host v$APP_VERSION and open the Media Center?\n\nFiles will be installed to:\n$PREFIX_DIR" 2>/dev/null; then
      exit 0
    fi
  elif [[ -t 0 && "$SILENT" -eq 0 ]]; then
    echo "================================================================"
    echo "  LootStream Linux Host v$APP_VERSION — Express Installer"
    echo "================================================================"
    echo "Install path : $PREFIX_DIR"
    echo "Launcher CLI : $BIN_DIR/lootstream-host"
    if [[ "$ENABLE_SERVICE" -eq 0 ]]; then
      read -r -p "Auto-start LootStream Host in background on login? [y/N] " svc_ans || true
      if [[ "${svc_ans:-}" =~ ^[Yy] ]]; then
        ENABLE_SERVICE=1
      fi
    fi
  fi
}

has_desktop_gui_runtime() {
  command -v python3 >/dev/null 2>&1 || return 1
  python3 -c '
import importlib.util, sys
try:
    import gi
    gi.require_version("Gtk", "3.0")
    for v in ("4.1", "4.0"):
        try:
            gi.require_version("WebKit2", v)
            sys.exit(0)
        except ValueError:
            pass
except Exception:
    pass
if importlib.util.find_spec("webview") is not None:
    sys.exit(0)
if importlib.util.find_spec("tkinter") is not None:
    sys.exit(0)
sys.exit(1)
' >/dev/null 2>&1
}

distro_gui_packages() {
  if command -v apt-get >/dev/null 2>&1; then
    local wk_pkg="gir1.2-webkit2-4.0"
    if command -v apt-cache >/dev/null 2>&1 && apt-cache show gir1.2-webkit2-4.1 >/dev/null 2>&1; then
      wk_pkg="gir1.2-webkit2-4.1"
    fi
    echo "python3-gi gir1.2-gtk-3.0 $wk_pkg python3-tk"
  elif command -v dnf >/dev/null 2>&1; then
    echo "python3-gobject gtk3 webkit2gtk4.1 python3-tkinter"
  elif command -v pacman >/dev/null 2>&1; then
    echo "python-gobject gtk3 webkit2gtk-4.1 tk"
  elif command -v zypper >/dev/null 2>&1; then
    echo "python3-gobject gtk3 typelib-1_0-WebKit2-4_1 python3-tk"
  else
    echo ""
  fi
}

check_and_install_deps() {
  [[ "$SKIP_DEPS" -eq 1 ]] && return 0

  local missing_required=()
  local missing_optional=()

  command -v python3 >/dev/null 2>&1 || missing_required+=("python3")
  if has_gui_session && ! has_desktop_gui_runtime; then
    local gui_pkgs
    gui_pkgs="$(distro_gui_packages)"
    if [[ -n "$gui_pkgs" ]]; then
      read -r -a _arr <<< "$gui_pkgs"
      missing_required+=("${_arr[@]}")
    fi
  fi
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
    elif has_gui_session; then
      install_system_packages "${to_install[@]}" || true
    fi
  elif [[ -t 0 && ${#missing_optional[@]} -gt 0 ]]; then
    echo "[LootStream] Optional enhancements not installed: ${missing_optional[*]} (for durations & OS folder picker)."
    read -r -p "Install optional packages now? [y/N] " ans
    if [[ "$ans" =~ ^[Yy] ]]; then
      install_system_packages "${missing_optional[@]}" || true
    fi
  fi

  if ! command -v python3 >/dev/null 2>&1; then
    notify_error "python3 is required to run LootStream Linux Host. Install python3 and retry."
    exit 1
  fi
}

configure_ufw_firewall() {
  [[ "$CONFIGURE_FIREWALL" -eq 1 ]] || return 0
  if command -v ufw >/dev/null 2>&1; then
    if run_as_root bash -c "ufw status 2>/dev/null | grep -qi 'Status: active'"; then
      echo "[LootStream] Opening UFW ports 8765/tcp & 8766/udp for Android Wi-Fi Login..."
      run_as_root bash -c "ufw allow 8765/tcp && ufw allow 8766/udp" >/dev/null 2>&1 || true
    fi
  fi
}

ensure_local_bin_in_path() {
  case ":$PATH:" in
    *":$BIN_DIR:"*) return 0 ;;
  esac
  local export_line='export PATH="$HOME/.local/bin:$PATH"'
  for rc in "$HOME/.profile" "$HOME/.bashrc" "$HOME/.zshrc"; do
    if [[ -f "$rc" ]] && ! grep -qF '$HOME/.local/bin' "$rc" 2>/dev/null; then
      printf '\n# Added by LootStream Host Installer\n%s\n' "$export_line" >> "$rc"
    fi
  done
}

do_uninstall() {
  echo "[LootStream] Uninstalling LootStream Linux Host..."
  if command -v systemctl >/dev/null 2>&1; then
    systemctl --user disable --now lootstream-host.service >/dev/null 2>&1 || true
  fi
  rm -rf "$PREFIX_DIR"
  rm -f "$BIN_DIR/lootstream-host"
  rm -f "$APPS_DIR/lootstream-host.desktop"
  rm -f "$ICONS_DIR/lootstream-host.svg"
  rm -f "$SYSTEMD_USER_DIR/lootstream-host.service"
  rm -f "$HOME/Desktop/LootStream.desktop"
  rm -f "$HOME/Desktop/LootStream Linux Host.desktop"
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
      notify_error "Embedded installer payload checksum mismatch! Please re-download LootStreamHost-Linux.run."
      exit 1
    fi
  fi

  mkdir -p "$PREFIX_DIR" "$BIN_DIR" "$APPS_DIR" "$ICONS_DIR" "$SYSTEMD_USER_DIR"
  tar -xzf "$tmp_tar" -C "$PREFIX_DIR"
  chmod +x "$PREFIX_DIR/lootstream_linux_host.py" "$PREFIX_DIR/lootstream_desktop_app.py" "$PREFIX_DIR/start-lootstream-host.sh"
  cp "$PREFIX_DIR/lootstream-host.svg" "$ICONS_DIR/lootstream-host.svg"

  cat > "$BIN_DIR/lootstream-host" <<EOF
#!/usr/bin/env bash
exec "$PREFIX_DIR/start-lootstream-host.sh" "\$@"
EOF
  chmod +x "$BIN_DIR/lootstream-host"
  ensure_local_bin_in_path

  local desktop_contents
  desktop_contents="[Desktop Entry]
Type=Application
Name=LootStream
GenericName=Personal Media Center & Streaming Host
Comment=Watch Movies, TV Shows & Music and stream over Wi-Fi to LootStream Android
Exec=\"$PREFIX_DIR/start-lootstream-host.sh\"
Icon=$ICONS_DIR/lootstream-host.svg
Terminal=false
StartupWMClass=lootstream-host
Categories=AudioVideo;Video;Audio;Network;
Keywords=media;server;movies;tv;music;streaming;lootstream;"

  printf '%s\n' "$desktop_contents" > "$APPS_DIR/lootstream-host.desktop"
  chmod +x "$APPS_DIR/lootstream-host.desktop"

  if [[ "$CREATE_DESKTOP_SHORTCUT" -eq 1 && -d "$HOME/Desktop" ]]; then
    local desk_file="$HOME/Desktop/LootStream.desktop"
    rm -f "$HOME/Desktop/LootStream Linux Host.desktop"
    printf '%s\n' "$desktop_contents" > "$desk_file"
    chmod +x "$desk_file"
    if command -v gio >/dev/null 2>&1; then
      gio set "$desk_file" metadata::trusted true >/dev/null 2>&1 || true
    fi
  fi

  if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database "$APPS_DIR" >/dev/null 2>&1 || true
  fi
  if command -v gtk-update-icon-cache >/dev/null 2>&1; then
    gtk-update-icon-cache -q "${XDG_DATA_HOME:-$HOME/.local/share}/icons/hicolor" >/dev/null 2>&1 || true
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
  echo "[LootStream] Installed cleanly!"
  echo "  App files : $PREFIX_DIR"
  echo "  Command   : $BIN_DIR/lootstream-host"
  echo "  App menu  : $APPS_DIR/lootstream-host.desktop"
  echo "  Service   : $SYSTEMD_USER_DIR/lootstream-host.service"
}

if [[ "$UNINSTALL" -eq 1 ]]; then
  do_uninstall
fi

run_gui_wizard_if_needed
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

configure_ufw_firewall

if [[ "$ENABLE_SERVICE" -eq 1 ]]; then
  if command -v systemctl >/dev/null 2>&1; then
    systemctl --user daemon-reload >/dev/null 2>&1 || true
    systemctl --user enable --now lootstream-host.service >/dev/null 2>&1 || true
    echo "[LootStream] Enabled and started systemd --user service."
  else
    echo "[WARN] systemctl not found; skipping --enable-service." >&2
  fi
fi

if [[ "$INSTALL_ONLY" -eq 1 ]]; then
  echo "[LootStream] Setup complete. Launch 'lootstream-host' or pick LootStream Linux Host from your app menu."
  exit 0
fi

exec python3 "$PREFIX_DIR/lootstream_linux_host.py" "${APP_ARGS[@]}"

__ARCHIVE_BELOW__
INSTALLER_STUB

sed -i "s/__PAYLOAD_SHA256__/$PAYLOAD_SHA256/" "$OUT_FILE"
base64 "$PAYLOAD_TAR" >> "$OUT_FILE"
chmod +x "$OUT_FILE"

echo "Built single-file Linux executable: $OUT_FILE ($(du -h "$OUT_FILE" | cut -f1))"
