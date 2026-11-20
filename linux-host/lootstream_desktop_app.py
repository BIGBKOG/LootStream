#!/usr/bin/env python3
"""
LootStream Linux Native Desktop Application

Runs LootStream as a genuine standalone Linux desktop application window:
  1. Primary: Native GTK3 + WebKit2GTK embedded desktop app window (with app icon,
     WM_CLASS, local media playback, zero browser chrome/URL bar, and clean
     shutdown on window close).
  2. Fallback: Native Tkinter cyber desktop app window (`LootStreamTkApp`) with
     built-in Canvas QR code renderer, 6-digit Easy Login, Movies/TV/Music
     browsing, Search, Favorites/Watched toggles, Folder/PIN settings, and native
     desktop media playback (mpv / vlc / celluloid / ffplay / xdg-open).
"""

import math
import os
import shutil
import subprocess
import threading
import time
from pathlib import Path
from typing import Callable, Dict, List, Optional, Tuple


# ---------------------------------------------------------------------------
# Pure-Python QR Encoder (Versions 1..9, Byte Mode, ECC Level M)
# Used by the native Tkinter desktop UI so zero external QR deps are needed.
# ---------------------------------------------------------------------------
_QR_SPECS_M = [
    None,
    (21, 16, 10, 1, 16, 0, 0, ()),
    (25, 28, 16, 1, 28, 0, 0, (6, 18)),
    (29, 44, 26, 1, 44, 0, 0, (6, 22)),
    (33, 64, 18, 2, 32, 0, 0, (6, 26)),
    (37, 86, 24, 2, 43, 0, 0, (6, 30)),
    (41, 108, 16, 4, 27, 0, 0, (6, 34)),
    (45, 124, 18, 4, 31, 0, 0, (6, 22, 38)),
    (49, 154, 22, 2, 38, 2, 39, (6, 24, 42)),
    (53, 182, 22, 3, 36, 2, 37, (6, 26, 46)),
]

_GF_EXP = [0] * 512
_GF_LOG = [0] * 256
_x = 1
for _i in range(255):
    _GF_EXP[_i] = _x
    _GF_LOG[_x] = _i
    _x <<= 1
    if _x & 0x100:
        _x ^= 0x11D
for _i in range(255, 512):
    _GF_EXP[_i] = _GF_EXP[_i - 255]


def _gf_mul(a: int, b: int) -> int:
    if a == 0 or b == 0:
        return 0
    return _GF_EXP[_GF_LOG[a] + _GF_LOG[b]]


def _rs_remainder(data: List[int], ecc_len: int) -> List[int]:
    poly = [1]
    for i in range(ecc_len):
        nxt = [0] * (len(poly) + 1)
        factor = _GF_EXP[i]
        for j, c in enumerate(poly):
            nxt[j] ^= c
            nxt[j + 1] ^= _gf_mul(c, factor)
        poly = nxt
    res = list(data) + [0] * ecc_len
    for i in range(len(data)):
        coef = res[i]
        if coef != 0:
            for j, g in enumerate(poly):
                res[i + j] ^= _gf_mul(g, coef)
    return res[len(data) :]


def build_qr_matrix(text: str) -> List[List[bool]]:
    raw = (text or "").encode("utf-8")
    ver = 1
    while ver < len(_QR_SPECS_M) and len(raw) + 2 > _QR_SPECS_M[ver][1]:
        ver += 1
    if ver >= len(_QR_SPECS_M):
        ver = len(_QR_SPECS_M) - 1
    n, data_cw_count, ecc_per_block, g1_blocks, g1_data, g2_blocks, g2_data, align = _QR_SPECS_M[ver]
    payload = raw[: max(0, data_cw_count - 2)]

    bits: List[int] = []

    def push_bits(val: int, length: int) -> None:
        for shift in range(length - 1, -1, -1):
            bits.append((val >> shift) & 1)

    push_bits(0x4, 4)
    push_bits(len(payload), 8)
    for b in payload:
        push_bits(b, 8)
    cap_bits = data_cw_count * 8
    push_bits(0, min(4, cap_bits - len(bits)))
    while len(bits) % 8 != 0:
        bits.append(0)

    data_cw: List[int] = []
    for i in range(0, len(bits), 8):
        v = 0
        for j in range(8):
            v = (v << 1) | bits[i + j]
        data_cw.append(v)

    pads = (0xEC, 0x11)
    pi = 0
    while len(data_cw) < data_cw_count:
        data_cw.append(pads[pi % 2])
        pi += 1

    blocks: List[List[int]] = []
    ecc_blocks: List[List[int]] = []
    offset = 0
    for _ in range(g1_blocks):
        sl = data_cw[offset : offset + g1_data]
        offset += g1_data
        blocks.append(sl)
        ecc_blocks.append(_rs_remainder(sl, ecc_per_block))
    for _ in range(g2_blocks):
        sl = data_cw[offset : offset + g2_data]
        offset += g2_data
        blocks.append(sl)
        ecc_blocks.append(_rs_remainder(sl, ecc_per_block))

    interleaved: List[int] = []
    max_data_len = max(g1_data, g2_data)
    for i in range(max_data_len):
        for blk in blocks:
            if i < len(blk):
                interleaved.append(blk[i])
    for i in range(ecc_per_block):
        for ecc_blk in ecc_blocks:
            if i < len(ecc_blk):
                interleaved.append(ecc_blk[i])

    mod = [[False] * n for _ in range(n)]
    is_func = [[False] * n for _ in range(n)]

    def set_fn(r: int, c: int, val: bool) -> None:
        if 0 <= r < n and 0 <= c < n:
            mod[r][c] = val
            is_func[r][c] = True

    def draw_finder(top: int, left: int) -> None:
        for dr in range(-1, 8):
            for dc in range(-1, 8):
                r, c = top + dr, left + dc
                if 0 <= r < n and 0 <= c < n:
                    on = (
                        0 <= dr <= 6
                        and 0 <= dc <= 6
                        and (dr in (0, 6) or dc in (0, 6) or (2 <= dr <= 4 and 2 <= dc <= 4))
                    )
                    set_fn(r, c, on)

    draw_finder(0, 0)
    draw_finder(0, n - 7)
    draw_finder(n - 7, 0)

    for i in range(8, n - 8):
        set_fn(6, i, i % 2 == 0)
        set_fn(i, 6, i % 2 == 0)

    for r0 in align:
        for c0 in align:
            if is_func[r0][c0]:
                continue
            for dr in range(-2, 3):
                for dc in range(-2, 3):
                    set_fn(r0 + dr, c0 + dc, max(abs(dr), abs(dc)) != 1)

    for i in range(9):
        if i != 6:
            is_func[8][i] = True
            is_func[i][8] = True
    for i in range(8):
        is_func[8][n - 1 - i] = True
        is_func[n - 1 - i][8] = True
    set_fn(n - 8, 8, True)

    if ver >= 7:
        rem = ver
        for _ in range(12):
            rem = (rem << 1) ^ ((rem >> 11) * 0x1F25)
        ver_bits = (ver << 12) | rem
        for i in range(18):
            bit = ((ver_bits >> i) & 1) == 1
            a = i // 3
            b = (n - 11) + (i % 3)
            set_fn(a, b, bit)
            set_fn(b, a, bit)

    code_bits: List[bool] = []
    for cw in interleaved:
        for b in range(7, -1, -1):
            code_bits.append(((cw >> b) & 1) == 1)

    bit_idx = 0
    right = n - 1
    while right >= 1:
        if right == 6:
            right = 5
        for vert in range(n):
            for j in range(2):
                c = right - j
                upward = ((right + 1) & 2) == 0
                r = n - 1 - vert if upward else vert
                if not is_func[r][c] and bit_idx < len(code_bits):
                    mod[r][c] = code_bits[bit_idx]
                    bit_idx += 1
        right -= 2

    def mask_match(mask_id: int, r: int, c: int) -> bool:
        if mask_id == 0:
            return (r + c) % 2 == 0
        if mask_id == 1:
            return r % 2 == 0
        if mask_id == 2:
            return c % 3 == 0
        if mask_id == 3:
            return (r + c) % 3 == 0
        if mask_id == 4:
            return ((r // 2) + (c // 3)) % 2 == 0
        if mask_id == 5:
            return ((r * c) % 2) + ((r * c) % 3) == 0
        if mask_id == 6:
            return (((r * c) % 2) + ((r * c) % 3)) % 2 == 0
        return (((r + c) % 2) + ((r * c) % 3)) % 2 == 0

    def apply_mask_and_format(mask_id: int) -> List[List[bool]]:
        m = [[False] * n for _ in range(n)]
        for r in range(n):
            for c in range(n):
                v = mod[r][c]
                if not is_func[r][c] and mask_match(mask_id, r, c):
                    v = not v
                m[r][c] = v
        rem = mask_id
        for _ in range(10):
            rem = (rem << 1) ^ ((rem >> 9) * 0x537)
        fmt = ((mask_id << 10) | rem) ^ 0x5412
        for i in range(6):
            m[8][i] = ((fmt >> (14 - i)) & 1) == 1
        m[8][7] = ((fmt >> 8) & 1) == 1
        m[8][8] = ((fmt >> 7) & 1) == 1
        m[7][8] = ((fmt >> 6) & 1) == 1
        for i in range(6):
            m[i][8] = ((fmt >> i) & 1) == 1
        for i in range(8):
            m[8][n - 1 - i] = ((fmt >> i) & 1) == 1
        for i in range(8, 15):
            m[n - 15 + i][8] = ((fmt >> i) & 1) == 1
        return m

    def score_penalty(m: List[List[bool]]) -> int:
        pen = 0
        for r in range(n):
            run = 1
            for c in range(1, n):
                if m[r][c] == m[r][c - 1]:
                    run += 1
                else:
                    if run >= 5:
                        pen += run - 2
                    run = 1
            if run >= 5:
                pen += run - 2
        for c in range(n):
            run = 1
            for r in range(1, n):
                if m[r][c] == m[r - 1][c]:
                    run += 1
                else:
                    if run >= 5:
                        pen += run - 2
                    run = 1
            if run >= 5:
                pen += run - 2
        for r in range(n - 1):
            for c in range(n - 1):
                if m[r][c] == m[r][c + 1] == m[r + 1][c] == m[r + 1][c + 1]:
                    pen += 3
        return pen

    best = apply_mask_and_format(0)
    best_pen = score_penalty(best)
    for mid in range(1, 8):
        cand = apply_mask_and_format(mid)
        pen = score_penalty(cand)
        if pen < best_pen:
            best_pen = pen
            best = cand
    return best


def _find_icon_path() -> Optional[str]:
    here = Path(__file__).resolve().parent
    candidates = [
        here / "lootstream-host.svg",
        Path("/usr/share/icons/hicolor/scalable/apps/lootstream-host.svg"),
        Path.home() / ".local/share/icons/hicolor/scalable/apps/lootstream-host.svg",
    ]
    for p in candidates:
        if p.is_file():
            return str(p)
    return None


# ---------------------------------------------------------------------------
# Tier 1: Native GTK3 + WebKit2GTK Desktop App Window (Real Desktop App)
# ---------------------------------------------------------------------------
def _try_launch_gtk_webkit(url: str, app_title: str = "LootStream") -> Optional[bool]:
    try:
        import gi  # type: ignore

        gi.require_version("Gtk", "3.0")
        for wk_ver in ("4.1", "4.0"):
            try:
                gi.require_version("WebKit2", wk_ver)
                break
            except ValueError:
                continue
        else:
            return None

        from gi.repository import Gdk, GLib, Gtk, WebKit2  # type: ignore
    except Exception:
        return None

    GLib.set_prgname("lootstream-host")
    GLib.set_application_name(app_title)

    window = Gtk.Window(title=app_title)
    window.set_default_size(1280, 820)
    window.set_size_request(920, 620)
    window.set_position(Gtk.WindowPosition.CENTER)
    try:
        window.set_wmclass("lootstream-host", "LootStream")
    except Exception:
        pass

    icon_path = _find_icon_path()
    if icon_path:
        try:
            window.set_icon_from_file(icon_path)
        except Exception:
            pass

    bg_rgba = Gdk.RGBA()
    bg_rgba.parse("#0b0f19")
    try:
        window.override_background_color(Gtk.StateFlags.NORMAL, bg_rgba)
    except Exception:
        pass

    webview = WebKit2.WebView()
    try:
        webview.set_background_color(bg_rgba)
    except Exception:
        pass

    settings = webview.get_settings()
    try:
        settings.set_enable_developer_extras(False)
        settings.set_javascript_can_access_clipboard(True)
        settings.set_enable_media_stream(True)
        settings.set_enable_webaudio(True)
        settings.set_media_playback_requires_user_gesture(False)
        settings.set_user_agent("LootStreamDesktopApp/1.0 Linux")
    except Exception:
        pass

    # Disable browser context menu so it behaves strictly as a native desktop app
    webview.connect("context-menu", lambda *args: True)

    def on_decide_policy(view, decision, decision_type):
        if decision_type == WebKit2.PolicyDecisionType.NEW_WINDOW_ACTION:
            decision.ignore()
            return True
        return False

    webview.connect("decide-policy", on_decide_policy)
    webview.load_uri(url)

    window.add(webview)
    window.connect("destroy", lambda *_: Gtk.main_quit())
    window.show_all()
    Gtk.main()
    return True


def _try_launch_pywebview(url: str, app_title: str = "LootStream") -> Optional[bool]:
    try:
        import webview  # type: ignore
    except Exception:
        return None

    try:
        webview.create_window(
            app_title,
            url,
            width=1280,
            height=820,
            min_size=(920, 620),
            background_color="#0b0f19",
        )
        webview.start()
        return True
    except Exception:
        return None


# ---------------------------------------------------------------------------
# Tier 2: Native Tkinter Cyber Desktop App (Fallback if WebKit2 unavailable)
# ---------------------------------------------------------------------------
def _format_bytes(num_bytes: int) -> str:
    if not num_bytes or num_bytes <= 0:
        return ""
    units = ("B", "KB", "MB", "GB", "TB")
    val = float(num_bytes)
    idx = 0
    while val >= 1024.0 and idx < len(units) - 1:
        val /= 1024.0
        idx += 1
    return f"{val:.1f} {units[idx]}" if idx >= 2 else f"{int(val)} {units[idx]}"


def _format_ms(ms: int) -> str:
    if not ms or ms <= 0:
        return "--:--"
    total_sec = int(ms // 1000)
    h = total_sec // 3600
    m = (total_sec % 3600) // 60
    s = total_sec % 60
    if h > 0:
        return f"{h}:{m:02d}:{s:02d}"
    return f"{m}:{s:02d}"


def _launch_native_media_player(file_path: str) -> Tuple[Optional[subprocess.Popen], str]:
    """Play local media in an external native Linux media player window (never a browser)."""
    if not file_path or not os.path.isfile(file_path):
        return None, "File not found"
    for candidate, args in (
        ("mpv", ["--force-window=yes", "--keep-open=no"]),
        ("vlc", ["--play-and-exit"]),
        ("celluloid", []),
        ("totem", []),
        ("ffplay", ["-autoexit", "-window_title", f"LootStream — {os.path.basename(file_path)}"]),
        ("xdg-open", []),
    ):
        exe = shutil.which(candidate)
        if exe:
            try:
                proc = subprocess.Popen(
                    [exe, *args, file_path],
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                )
                return proc, candidate
            except Exception:
                continue
    return None, "No Linux desktop media player found (install mpv or vlc)"


def _try_launch_tk_desktop_app(store, lan_urls: List[str]) -> Optional[bool]:
    try:
        import tkinter as tk
        from tkinter import filedialog, messagebox
    except Exception:
        return None

    BG = "#0B0F19"
    SURFACE = "#131926"
    SURFACE_ALT = "#1C2538"
    CYAN = "#00E5FF"
    GREEN = "#00FF87"
    PURPLE = "#B066FF"
    TEXT = "#F1F5F9"
    MUTED = "#94A3B8"
    BORDER = "#243048"

    root = tk.Tk()
    root.title("LootStream — Desktop Media Center & Host")
    root.geometry("1160x740")
    root.minsize(900, 600)
    root.configure(bg=BG)

    state_ui = {
        "tab": "home",
        "search": tk.StringVar(),
        "server_name": tk.StringVar(value=store.server_name),
        "pin": tk.StringVar(value=store.pin),
        "now_playing_proc": None,
        "now_playing_item": None,
    }

    # Top Header
    header = tk.Frame(root, bg=SURFACE, height=60, highlightthickness=1, highlightbackground=BORDER)
    header.pack(side=tk.TOP, fill=tk.X)
    header.pack_propagate(False)

    tk.Label(
        header,
        text="LOOTSTREAM",
        bg=SURFACE,
        fg=CYAN,
        font=("Sans", 15, "bold"),
    ).pack(side=tk.LEFT, padx=(18, 6))

    tk.Label(
        header,
        text="Linux Desktop Media App",
        bg=SURFACE,
        fg=MUTED,
        font=("Sans", 10),
    ).pack(side=tk.LEFT, padx=(0, 18))

    search_entry = tk.Entry(
        header,
        textvariable=state_ui["search"],
        bg=SURFACE_ALT,
        fg=TEXT,
        insertbackground=CYAN,
        relief=tk.FLAT,
        font=("Sans", 10),
        width=32,
    )
    search_entry.pack(side=tk.LEFT, padx=8, ipady=6)

    lbl_status = tk.Label(
        header,
        text=f"ONLINE  •  {(lan_urls[0] if lan_urls else f'http://127.0.0.1:{store.port}')}",
        bg="#102C28",
        fg=GREEN,
        font=("Monospace", 9, "bold"),
        padx=10,
        pady=5,
    )
    lbl_status.pack(side=tk.RIGHT, padx=14)

    # Main Split (Sidebar + Content)
    body = tk.Frame(root, bg=BG)
    body.pack(side=tk.TOP, fill=tk.BOTH, expand=True)

    sidebar = tk.Frame(body, bg=SURFACE, width=210, highlightthickness=1, highlightbackground=BORDER)
    sidebar.pack(side=tk.LEFT, fill=tk.Y)
    sidebar.pack_propagate(False)

    content = tk.Frame(body, bg=BG)
    content.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=18, pady=14)

    # Persistent Bottom Now Playing Bar
    player_bar = tk.Frame(root, bg="#0F172A", height=54, highlightthickness=1, highlightbackground=BORDER)
    player_bar.pack(side=tk.BOTTOM, fill=tk.X)
    player_bar.pack_propagate(False)

    lbl_now_playing = tk.Label(
        player_bar,
        text="Ready — select any Movie, TV Show, or Music track to play",
        bg="#0F172A",
        fg=MUTED,
        font=("Sans", 10),
    )
    lbl_now_playing.pack(side=tk.LEFT, padx=16)

    def stop_local_player() -> None:
        proc = state_ui.get("now_playing_proc")
        if proc is not None and proc.poll() is None:
            try:
                proc.terminate()
            except Exception:
                pass
        state_ui["now_playing_proc"] = None
        lbl_now_playing.configure(text="Playback stopped", fg=MUTED)

    tk.Button(
        player_bar,
        text="Stop Player",
        command=stop_local_player,
        bg=SURFACE_ALT,
        fg=TEXT,
        relief=tk.FLAT,
        padx=12,
        pady=4,
    ).pack(side=tk.RIGHT, padx=14, pady=10)

    def current_pair_uri() -> str:
        base_url = lan_urls[0] if lan_urls else f"http://127.0.0.1:{store.port}"
        with store.lock:
            code = store.quick_code
            name = store.server_name
        from urllib.parse import quote
        return (
            f"lootstream://pair?url={quote(base_url, safe='')}"
            f"&code={quote(code, safe='')}"
            f"&name={quote(name, safe='')}"
        )

    def draw_qr_on_canvas(canvas: tk.Canvas, uri: str) -> None:
        canvas.delete("all")
        matrix = build_qr_matrix(uri)
        n = len(matrix)
        w = int(canvas["width"])
        quiet = 2
        cell = max(3, w // (n + quiet * 2))
        offset = max(2, (w - n * cell) // 2)
        canvas.create_rectangle(0, 0, w, w, fill="#FFFFFF", outline="")
        for r in range(n):
            for c in range(n):
                if matrix[r][c]:
                    x0 = offset + c * cell
                    y0 = offset + r * cell
                    canvas.create_rectangle(x0, y0, x0 + cell, y0 + cell, fill="#0B0F19", outline="")

    def clear_content() -> None:
        for w in content.winfo_children():
            w.destroy()

    def play_item(item: dict) -> None:
        file_path = item.get("filePath") or ""
        if not file_path:
            # Look up in catalog by id
            with store.lock:
                raw = store.by_id.get(item.get("id", ""))
                if raw:
                    file_path = raw.get("filePath", "")
        stop_local_player()
        proc, info = _launch_native_media_player(file_path)
        if proc is None:
            messagebox.showwarning("LootStream Player", info)
            return
        state_ui["now_playing_proc"] = proc
        state_ui["now_playing_item"] = item
        store.update_progress(
            item["id"],
            position_ms=item.get("playbackPositionMs", 0),
            duration_ms=item.get("durationMs", 0),
            watch_status="IN_PROGRESS",
        )
        lbl_now_playing.configure(
            text=f"Playing ({info}): {item['title']}  •  {item.get('subtitle', '')}",
            fg=GREEN,
        )

    def render_home() -> None:
        clear_content()
        items = store.get_public_items()
        movies_cnt = sum(1 for x in items if x["mediaType"] == "MOVIE")
        tv_cnt = sum(1 for x in items if x["mediaType"] in ("EPISODE", "SERIES"))
        music_cnt = sum(1 for x in items if x["mediaType"] == "AUDIO")

        top_row = tk.Frame(content, bg=BG)
        top_row.pack(fill=tk.X, pady=(0, 14))

        hero = tk.Frame(top_row, bg=SURFACE, padx=18, pady=16, highlightthickness=1, highlightbackground=BORDER)
        hero.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=(0, 12))

        tk.Label(hero, text=store.server_name, bg=SURFACE, fg=TEXT, font=("Sans", 16, "bold")).pack(anchor="w")
        tk.Label(
            hero,
            text="Browse & play local Movies, TV Shows & Music directly on Linux, or connect LootStream Android on Wi-Fi.",
            bg=SURFACE,
            fg=MUTED,
            wraplength=520,
            justify="left",
            font=("Sans", 10),
        ).pack(anchor="w", pady=(6, 14))

        stats_frame = tk.Frame(hero, bg=SURFACE)
        stats_frame.pack(fill=tk.X, pady=(0, 12))
        for label_txt, count_val, accent in (
            ("Movies", movies_cnt, CYAN),
            ("TV Shows", tv_cnt, PURPLE),
            ("Music Tracks", music_cnt, GREEN),
        ):
            card = tk.Frame(stats_frame, bg=SURFACE_ALT, padx=14, pady=10)
            card.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(0, 8))
            tk.Label(card, text=str(count_val), bg=SURFACE_ALT, fg=accent, font=("Sans", 18, "bold")).pack(anchor="w")
            tk.Label(card, text=label_txt, bg=SURFACE_ALT, fg=MUTED, font=("Sans", 9)).pack(anchor="w")

        btns = tk.Frame(hero, bg=SURFACE)
        btns.pack(anchor="w")
        tk.Button(
            btns,
            text="Browse Movies",
            command=lambda: switch_tab("movies"),
            bg=CYAN,
            fg=BG,
            font=("Sans", 9, "bold"),
            relief=tk.FLAT,
            padx=12,
            pady=6,
        ).pack(side=tk.LEFT, padx=(0, 8))
        tk.Button(
            btns,
            text="+ Add Media Folder",
            command=add_folder_via_dialog,
            bg=GREEN,
            fg=BG,
            font=("Sans", 9, "bold"),
            relief=tk.FLAT,
            padx=12,
            pady=6,
        ).pack(side=tk.LEFT, padx=(0, 8))
        tk.Button(
            btns,
            text="Rescan Library",
            command=lambda: (store.scan_library(), refresh_current_tab()),
            bg=SURFACE_ALT,
            fg=TEXT,
            font=("Sans", 9),
            relief=tk.FLAT,
            padx=12,
            pady=6,
        ).pack(side=tk.LEFT)

        # Easy Wi-Fi Login Card with QR + 6-digit code
        qr_card = tk.Frame(top_row, bg=SURFACE, padx=16, pady=14, highlightthickness=1, highlightbackground=BORDER)
        qr_card.pack(side=tk.RIGHT, fill=tk.Y)

        tk.Label(qr_card, text="Easy Wi-Fi Android Login", bg=SURFACE, fg=CYAN, font=("Sans", 11, "bold")).pack()
        qr_canvas = tk.Canvas(qr_card, width=172, height=172, bg="#FFFFFF", highlightthickness=0)
        qr_canvas.pack(pady=8)
        draw_qr_on_canvas(qr_canvas, current_pair_uri())

        code = store.quick_code
        lbl_code = tk.Label(
            qr_card,
            text=f"{code[:3]} {code[3:]}",
            bg=SURFACE,
            fg=GREEN,
            font=("Monospace", 18, "bold"),
        )
        lbl_code.pack()

        def do_new_code() -> None:
            new_c = store.refresh_quick_code()
            lbl_code.configure(text=f"{new_c[:3]} {new_c[3:]}")
            draw_qr_on_canvas(qr_canvas, current_pair_uri())

        tk.Button(
            qr_card,
            text="New 6-Digit Code",
            command=do_new_code,
            bg=SURFACE_ALT,
            fg=CYAN,
            relief=tk.FLAT,
            font=("Sans", 9, "bold"),
            padx=10,
            pady=4,
        ).pack(pady=(6, 0))

        # Recently Added List
        tk.Label(content, text="Recently Added Media", bg=BG, fg=TEXT, font=("Sans", 12, "bold")).pack(
            anchor="w", pady=(4, 6)
        )
        recent = sorted(items, key=lambda x: x.get("addedAt", 0), reverse=True)[:20]
        render_media_listbox(recent)

    def render_media_listbox(items: List[dict]) -> None:
        query = state_ui["search"].get().strip().lower()
        if query:
            items = [
                x
                for x in items
                if query in x["title"].lower()
                or query in (x.get("subtitle") or "").lower()
                or query in (x.get("seriesName") or "").lower()
            ]

        list_wrap = tk.Frame(content, bg=SURFACE, highlightthickness=1, highlightbackground=BORDER)
        list_wrap.pack(fill=tk.BOTH, expand=True)

        if not items:
            tk.Label(
                list_wrap,
                text="No matching media files found. Add a media folder or adjust your search.",
                bg=SURFACE,
                fg=MUTED,
                font=("Sans", 10),
            ).pack(pady=40)
            return

        lb = tk.Listbox(
            list_wrap,
            bg=SURFACE,
            fg=TEXT,
            selectbackground="#1E3A5F",
            selectforeground=CYAN,
            font=("Sans", 10),
            relief=tk.FLAT,
            activestyle="none",
        )
        sb = tk.Scrollbar(list_wrap, command=lb.yview)
        lb.configure(yscrollcommand=sb.set)
        sb.pack(side=tk.RIGHT, fill=tk.Y)
        lb.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=8, pady=8)

        for it in items:
            badge = f"[{it['mediaType']}/{it['codecBadge']}]"
            fav = "★ " if it.get("isFavorite") else ""
            watched = " ✓" if it.get("watchStatus") == "WATCHED" else ""
            dur = _format_ms(it.get("durationMs", 0))
            size_str = _format_bytes(it.get("fileSizeBytes", 0))
            meta = f"{dur} • {size_str}".strip(" •")
            lb.insert(tk.END, f"{fav}{it['title']}{watched}   {badge}   ({it.get('subtitle', '')})  {meta}")

        def get_selected() -> Optional[dict]:
            sel = lb.curselection()
            if not sel:
                return None
            return items[sel[0]]

        lb.bind("<Double-Button-1>", lambda _e: get_selected() and play_item(get_selected()))

        actions = tk.Frame(content, bg=BG)
        actions.pack(fill=tk.X, pady=(8, 0))
        tk.Button(
            actions,
            text="▶ Play Selected",
            command=lambda: get_selected() and play_item(get_selected()),
            bg=GREEN,
            fg=BG,
            font=("Sans", 9, "bold"),
            relief=tk.FLAT,
            padx=14,
            pady=6,
        ).pack(side=tk.LEFT, padx=(0, 8))
        tk.Button(
            actions,
            text="★ Toggle Favorite",
            command=lambda: (
                get_selected()
                and (
                    store.toggle_favorite(get_selected()["id"], not get_selected().get("isFavorite")),
                    refresh_current_tab(),
                )
            ),
            bg=SURFACE_ALT,
            fg=CYAN,
            relief=tk.FLAT,
            padx=12,
            pady=6,
        ).pack(side=tk.LEFT, padx=(0, 8))
        tk.Button(
            actions,
            text="✓ Toggle Watched",
            command=lambda: (
                get_selected()
                and (
                    store.set_watched(
                        get_selected()["id"],
                        "UNWATCHED" if get_selected().get("watchStatus") == "WATCHED" else "WATCHED",
                    ),
                    refresh_current_tab(),
                )
            ),
            bg=SURFACE_ALT,
            fg=PURPLE,
            relief=tk.FLAT,
            padx=12,
            pady=6,
        ).pack(side=tk.LEFT)

    def render_category(title: str, predicate: Callable[[dict], bool]) -> None:
        clear_content()
        tk.Label(content, text=title, bg=BG, fg=TEXT, font=("Sans", 15, "bold")).pack(anchor="w", pady=(0, 10))
        items = [x for x in store.get_public_items() if predicate(x)]
        render_media_listbox(items)

    def add_folder_via_dialog() -> None:
        chosen = filedialog.askdirectory(title="Select Movies, TV Shows, or Music folder")
        if chosen and store.add_folder(chosen):
            refresh_current_tab()

    def render_folders_tab() -> None:
        clear_content()
        tk.Label(content, text="Folders & Host Settings", bg=BG, fg=TEXT, font=("Sans", 15, "bold")).pack(
            anchor="w", pady=(0, 10)
        )

        cfg = tk.Frame(content, bg=SURFACE, padx=16, pady=12, highlightthickness=1, highlightbackground=BORDER)
        cfg.pack(fill=tk.X, pady=(0, 12))

        tk.Label(cfg, text="Host Display Name:", bg=SURFACE, fg=MUTED).grid(row=0, column=0, sticky="w", padx=(0, 8))
        tk.Entry(cfg, textvariable=state_ui["server_name"], bg=SURFACE_ALT, fg=TEXT, width=28, relief=tk.FLAT).grid(
            row=0, column=1, padx=(0, 16), ipady=4
        )
        tk.Label(cfg, text="Pairing PIN (optional):", bg=SURFACE, fg=MUTED).grid(row=0, column=2, sticky="w", padx=(0, 8))
        tk.Entry(cfg, textvariable=state_ui["pin"], bg=SURFACE_ALT, fg=TEXT, width=14, relief=tk.FLAT).grid(
            row=0, column=3, padx=(0, 16), ipady=4
        )

        def save_cfg() -> None:
            with store.lock:
                if state_ui["server_name"].get().strip():
                    store.server_name = state_ui["server_name"].get().strip()
                store.pin = state_ui["pin"].get().strip()
                store._save_state()
            messagebox.showinfo("LootStream Host", "Saved host settings.")

        tk.Button(
            cfg,
            text="Save Settings",
            command=save_cfg,
            bg=CYAN,
            fg=BG,
            font=("Sans", 9, "bold"),
            relief=tk.FLAT,
            padx=12,
            pady=5,
        ).grid(row=0, column=4)

        tk.Label(content, text="Shared Media Folders", bg=BG, fg=TEXT, font=("Sans", 11, "bold")).pack(
            anchor="w", pady=(4, 6)
        )
        fld_box = tk.Frame(content, bg=SURFACE, highlightthickness=1, highlightbackground=BORDER)
        fld_box.pack(fill=tk.BOTH, expand=True)

        lb_folders = tk.Listbox(fld_box, bg=SURFACE, fg=TEXT, selectbackground="#1E3A5F", relief=tk.FLAT, font=("Sans", 10))
        lb_folders.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=8, pady=8)
        for f in store.folders:
            lb_folders.insert(tk.END, f)

        fld_btns = tk.Frame(fld_box, bg=SURFACE, padx=10, pady=10)
        fld_btns.pack(side=tk.RIGHT, fill=tk.Y)
        tk.Button(
            fld_btns,
            text="+ Add Folder",
            command=add_folder_via_dialog,
            bg=GREEN,
            fg=BG,
            font=("Sans", 9, "bold"),
            relief=tk.FLAT,
            width=16,
            pady=6,
        ).pack(pady=(0, 8))
        tk.Button(
            fld_btns,
            text="Remove Selected",
            command=lambda: (
                lb_folders.curselection()
                and (store.remove_folder(store.folders[lb_folders.curselection()[0]]), refresh_current_tab())
            ),
            bg=SURFACE_ALT,
            fg="#F87171",
            relief=tk.FLAT,
            width=16,
            pady=6,
        ).pack(pady=(0, 8))
        tk.Button(
            fld_btns,
            text="Rescan Library",
            command=lambda: (store.scan_library(), refresh_current_tab()),
            bg=SURFACE_ALT,
            fg=PURPLE,
            relief=tk.FLAT,
            width=16,
            pady=6,
        ).pack()

    nav_buttons: Dict[str, tk.Button] = {}

    def refresh_current_tab(*_args) -> None:
        tab = state_ui["tab"]
        for key, btn in nav_buttons.items():
            if key == tab:
                btn.configure(bg="#192841", fg=CYAN)
            else:
                btn.configure(bg=SURFACE, fg=TEXT)
        if tab == "home":
            render_home()
        elif tab == "movies":
            render_category("Movies", lambda x: x["mediaType"] in ("MOVIE", "VIDEO"))
        elif tab == "tv":
            render_category("TV Shows & Episodes", lambda x: x["mediaType"] in ("EPISODE", "SERIES"))
        elif tab == "music":
            render_category("Music", lambda x: x["mediaType"] == "AUDIO")
        elif tab == "folders":
            render_folders_tab()

    def switch_tab(tab_name: str) -> None:
        state_ui["tab"] = tab_name
        refresh_current_tab()

    state_ui["search"].trace_add("write", refresh_current_tab)

    tk.Label(sidebar, text="NAVIGATION", bg=SURFACE, fg=MUTED, font=("Sans", 8, "bold")).pack(
        anchor="w", padx=16, pady=(16, 6)
    )
    for key, label in (
        ("home", "Home & QR Login"),
        ("movies", "Movies"),
        ("tv", "TV Shows"),
        ("music", "Music"),
        ("folders", "Folders & Server"),
    ):
        btn = tk.Button(
            sidebar,
            text=f"  {label}",
            anchor="w",
            command=lambda k=key: switch_tab(k),
            bg=SURFACE,
            fg=TEXT,
            activebackground="#1E3250",
            activeforeground=CYAN,
            relief=tk.FLAT,
            font=("Sans", 10, "bold"),
            padx=12,
            pady=10,
        )
        btn.pack(fill=tk.X, padx=8, pady=2)
        nav_buttons[key] = btn

    refresh_current_tab()

    def on_close() -> None:
        stop_local_player()
        root.destroy()

    root.protocol("WM_DELETE_WINDOW", on_close)
    root.mainloop()
    return True


def launch_native_desktop_app(
    url: str,
    store=None,
    lan_urls: Optional[List[str]] = None,
) -> bool:
    """
    Launch LootStream as an actual standalone Linux desktop application window.
    Returns True once the desktop window was shown and closed cleanly,
    or False if no GUI toolkit or display session is available.
    """
    if not (os.environ.get("DISPLAY") or os.environ.get("WAYLAND_DISPLAY")):
        return False

    # 1. Native GTK3 + WebKit2GTK embedded desktop app window
    if _try_launch_gtk_webkit(url):
        return True

    # 2. pywebview native desktop window (if installed)
    if _try_launch_pywebview(url):
        return True

    # 3. Native Tkinter cyber desktop app window
    if store is not None and _try_launch_tk_desktop_app(store, lan_urls or [url]):
        return True

    return False
