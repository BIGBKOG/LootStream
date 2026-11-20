#!/usr/bin/env bash
set -euo pipefail

# LootStream Linux Host — Easy 1-Command Installer
# Works either from a cloned repo (bash linux-host/install.sh) or standalone via curl/wget.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" 2>/dev/null && pwd || pwd)"
LOCAL_RUN="$SCRIPT_DIR/LootStreamHost-Linux.run"
LOCAL_BUILD="$SCRIPT_DIR/build-installer.sh"
DEFAULT_REPO="${LOOTSTREAM_GITHUB_REPO:-BIGBKOG/LootStream}"

echo "================================================================"
echo "  LootStream Linux Host — Easy Installer"
echo "================================================================"

if [[ ! -f "$LOCAL_RUN" && -f "$LOCAL_BUILD" ]]; then
  echo "[LootStream] Building installer bundle..."
  bash "$LOCAL_BUILD"
fi

if [[ -f "$LOCAL_RUN" ]]; then
  chmod +x "$LOCAL_RUN"
  exec "$LOCAL_RUN" "$@"
fi

TMP_RUN="$(mktemp /tmp/LootStreamHost-Linux.XXXXXX.run)"
trap 'rm -f "$TMP_RUN"' EXIT

DOWNLOAD_URL="https://github.com/$DEFAULT_REPO/releases/latest/download/LootStreamHost-Linux.run"
echo "[LootStream] Downloading LootStreamHost-Linux.run from $DOWNLOAD_URL ..."

if command -v curl >/dev/null 2>&1; then
  curl -fL --progress-bar "$DOWNLOAD_URL" -o "$TMP_RUN"
elif command -v wget >/dev/null 2>&1; then
  wget -q --show-progress "$DOWNLOAD_URL" -O "$TMP_RUN"
else
  echo "[ERROR] Please install curl or wget, or download LootStreamHost-Linux.run manually." >&2
  exit 1
fi

chmod +x "$TMP_RUN"
exec "$TMP_RUN" "$@"
