(() => {
  const state = {
    view: "home",
    search: "",
    host: null,
    items: [],
    activeVideo: null,
    videoQueue: [],
    activeTrack: null,
    audioQueue: [],
    browseCurrentPath: "",
    browseParentPath: null,
    progressTimer: null,
  };

  const viewEl = document.getElementById("view-container");
  const searchInput = document.getElementById("search-input");
  const toastEl = document.getElementById("toast");

  // Video elements
  const theaterModal = document.getElementById("theater-modal");
  const videoEl = document.getElementById("video-player");
  const theaterTitle = document.getElementById("theater-title");
  const theaterSubtitle = document.getElementById("theater-subtitle");
  const theaterFavBtn = document.getElementById("theater-fav-btn");
  const theaterWatchedBtn = document.getElementById("theater-watched-btn");
  const theaterSpeed = document.getElementById("theater-speed");
  const theaterFitBtn = document.getElementById("theater-fit-btn");
  const theaterSyncStatus = document.getElementById("theater-sync-status");

  // Audio elements
  const musicDock = document.getElementById("music-dock");
  const audioEl = document.getElementById("audio-player");
  const musicDockTitle = document.getElementById("music-dock-title");
  const musicDockSub = document.getElementById("music-dock-sub");
  const musicPlayPause = document.getElementById("music-play-pause");
  const musicSeek = document.getElementById("music-seek");
  const musicTimeCur = document.getElementById("music-time-cur");
  const musicTimeDur = document.getElementById("music-time-dur");
  const musicVolume = document.getElementById("music-volume");

  // Folder modal
  const folderModal = document.getElementById("folder-modal");
  const folderManualInput = document.getElementById("folder-manual-input");
  const dirCurrentLabel = document.getElementById("dir-current-label");
  const dirListEl = document.getElementById("dir-list");

  function showToast(msg) {
    toastEl.textContent = msg;
    toastEl.classList.remove("hidden");
    clearTimeout(toastEl._timer);
    toastEl._timer = setTimeout(() => toastEl.classList.add("hidden"), 2800);
  }

  function escapeHtml(str) {
    return String(str || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function formatDuration(ms) {
    if (!ms || ms <= 0) return "";
    const sec = Math.floor(ms / 1000);
    const h = Math.floor(sec / 3600);
    const m = Math.floor((sec % 3600) / 60);
    const s = sec % 60;
    if (h > 0) return `${h}h ${String(m).padStart(2, "0")}m`;
    if (m > 0) return `${m}m ${String(s).padStart(2, "0")}s`;
    return `${s}s`;
  }

  function formatClock(seconds) {
    const sec = Math.max(0, Math.floor(seconds || 0));
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
  }

  function formatSize(bytes) {
    if (!bytes || bytes <= 0) return "";
    const mb = bytes / (1024 * 1024);
    const gb = mb / 1024;
    return gb >= 1 ? `${gb.toFixed(2)} GB` : `${Math.round(mb)} MB`;
  }

  // Standard ISO/IEC 18004 Byte-mode QR Code Generator (ECC Level M, Versions 1-9)
  const QR_SPEC_M = [
    null,
    { size: 21, dataCw: 16, eccPerBlock: 10, g1Blocks: 1, g1Data: 16, g2Blocks: 0, g2Data: 0, align: [] },
    { size: 25, dataCw: 28, eccPerBlock: 16, g1Blocks: 1, g1Data: 28, g2Blocks: 0, g2Data: 0, align: [6, 18] },
    { size: 29, dataCw: 44, eccPerBlock: 26, g1Blocks: 1, g1Data: 44, g2Blocks: 0, g2Data: 0, align: [6, 22] },
    { size: 33, dataCw: 64, eccPerBlock: 18, g1Blocks: 2, g1Data: 32, g2Blocks: 0, g2Data: 0, align: [6, 26] },
    { size: 37, dataCw: 86, eccPerBlock: 24, g1Blocks: 2, g1Data: 43, g2Blocks: 0, g2Data: 0, align: [6, 30] },
    { size: 41, dataCw: 108, eccPerBlock: 16, g1Blocks: 4, g1Data: 27, g2Blocks: 0, g2Data: 0, align: [6, 34] },
    { size: 45, dataCw: 124, eccPerBlock: 18, g1Blocks: 4, g1Data: 31, g2Blocks: 0, g2Data: 0, align: [6, 22, 38] },
    { size: 49, dataCw: 154, eccPerBlock: 22, g1Blocks: 2, g1Data: 38, g2Blocks: 2, g2Data: 39, align: [6, 24, 42] },
    { size: 53, dataCw: 182, eccPerBlock: 22, g1Blocks: 3, g1Data: 36, g2Blocks: 2, g2Data: 37, align: [6, 26, 46] },
  ];

  const GF_EXP = new Uint8Array(512);
  const GF_LOG = new Uint8Array(256);
  (() => {
    let x = 1;
    for (let i = 0; i < 255; i++) {
      GF_EXP[i] = x;
      GF_LOG[x] = i;
      x <<= 1;
      if (x & 0x100) x ^= 0x11d;
    }
    for (let i = 255; i < 512; i++) GF_EXP[i] = GF_EXP[i - 255];
  })();

  function gfMul(a, b) {
    return a === 0 || b === 0 ? 0 : GF_EXP[GF_LOG[a] + GF_LOG[b]];
  }

  function rsGenerator(deg) {
    let poly = [1];
    for (let i = 0; i < deg; i++) {
      const next = new Array(poly.length + 1).fill(0);
      const factor = GF_EXP[i];
      for (let j = 0; j < poly.length; j++) {
        next[j] ^= poly[j];
        next[j + 1] ^= gfMul(poly[j], factor);
      }
      poly = next;
    }
    return poly;
  }

  function rsRemainder(data, eccLen) {
    const gen = rsGenerator(eccLen);
    const res = data.concat(new Array(eccLen).fill(0));
    for (let i = 0; i < data.length; i++) {
      const coef = res[i];
      if (coef !== 0) {
        for (let j = 0; j < gen.length; j++) {
          res[i + j] ^= gfMul(gen[j], coef);
        }
      }
    }
    return res.slice(data.length);
  }

  function buildQrMatrix(text) {
    const bytes = Array.from(new TextEncoder().encode(text || ""));
    let ver = 1;
    while (ver < QR_SPEC_M.length && bytes.length + 2 > QR_SPEC_M[ver].dataCw) ver++;
    if (ver >= QR_SPEC_M.length) ver = QR_SPEC_M.length - 1;
    const spec = QR_SPEC_M[ver];
    const maxBytes = spec.dataCw - 2;
    const payload = bytes.slice(0, maxBytes);

    const bits = [];
    const pushBits = (val, len) => {
      for (let i = len - 1; i >= 0; i--) bits.push((val >> i) & 1);
    };
    pushBits(0b0100, 4);
    pushBits(payload.length, 8);
    payload.forEach((b) => pushBits(b, 8));
    const capBits = spec.dataCw * 8;
    pushBits(0, Math.min(4, capBits - bits.length));
    while (bits.length % 8 !== 0) bits.push(0);
    const dataCw = [];
    for (let i = 0; i < bits.length; i += 8) {
      let v = 0;
      for (let j = 0; j < 8; j++) v = (v << 1) | bits[i + j];
      dataCw.push(v);
    }
    const pads = [0xec, 0x11];
    let pi = 0;
    while (dataCw.length < spec.dataCw) {
      dataCw.push(pads[pi % 2]);
      pi++;
    }

    const blocks = [];
    const eccBlocks = [];
    let offset = 0;
    for (let b = 0; b < spec.g1Blocks; b++) {
      const slice = dataCw.slice(offset, offset + spec.g1Data);
      offset += spec.g1Data;
      blocks.push(slice);
      eccBlocks.push(rsRemainder(slice, spec.eccPerBlock));
    }
    for (let b = 0; b < spec.g2Blocks; b++) {
      const slice = dataCw.slice(offset, offset + spec.g2Data);
      offset += spec.g2Data;
      blocks.push(slice);
      eccBlocks.push(rsRemainder(slice, spec.eccPerBlock));
    }

    const interleaved = [];
    const maxDataLen = Math.max(spec.g1Data, spec.g2Data);
    for (let i = 0; i < maxDataLen; i++) {
      for (const bl of blocks) if (i < bl.length) interleaved.push(bl[i]);
    }
    for (let i = 0; i < spec.eccPerBlock; i++) {
      for (const eb of eccBlocks) if (i < eb.length) interleaved.push(eb[i]);
    }

    const N = spec.size;
    const mod = Array.from({ length: N }, () => new Array(N).fill(false));
    const isFunc = Array.from({ length: N }, () => new Array(N).fill(false));
    const setFn = (r, c, val) => {
      if (r >= 0 && r < N && c >= 0 && c < N) {
        mod[r][c] = !!val;
        isFunc[r][c] = true;
      }
    };

    const drawFinder = (top, left) => {
      for (let dr = -1; dr <= 7; dr++) {
        for (let dc = -1; dc <= 7; dc++) {
          const r = top + dr;
          const c = left + dc;
          if (r < 0 || r >= N || c < 0 || c >= N) continue;
          const on =
            dr >= 0 && dr <= 6 && dc >= 0 && dc <= 6 &&
            (dr === 0 || dr === 6 || dc === 0 || dc === 6 || (dr >= 2 && dr <= 4 && dc >= 2 && dc <= 4));
          setFn(r, c, on);
        }
      }
    };
    drawFinder(0, 0);
    drawFinder(0, N - 7);
    drawFinder(N - 7, 0);

    for (let i = 8; i < N - 8; i++) {
      setFn(6, i, i % 2 === 0);
      setFn(i, 6, i % 2 === 0);
    }

    for (const r0 of spec.align) {
      for (const c0 of spec.align) {
        if (isFunc[r0][c0]) continue;
        for (let dr = -2; dr <= 2; dr++) {
          for (let dc = -2; dc <= 2; dc++) {
            setFn(r0 + dr, c0 + dc, Math.max(Math.abs(dr), Math.abs(dc)) !== 1);
          }
        }
      }
    }

    for (let i = 0; i < 9; i++) {
      if (i !== 6) {
        isFunc[8][i] = true;
        isFunc[i][8] = true;
      }
    }
    for (let i = 0; i < 8; i++) {
      isFunc[8][N - 1 - i] = true;
      isFunc[N - 1 - i][8] = true;
    }
    setFn(N - 8, 8, true);

    if (ver >= 7) {
      let rem = ver;
      for (let i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >> 11) * 0x1f25);
      const verBits = (ver << 12) | rem;
      for (let i = 0; i < 18; i++) {
        const bit = ((verBits >> i) & 1) === 1;
        const a = Math.floor(i / 3);
        const b = (N - 11) + (i % 3);
        setFn(a, b, bit);
        setFn(b, a, bit);
      }
    }

    const codeBits = [];
    for (const cw of interleaved) {
      for (let b = 7; b >= 0; b--) codeBits.push(((cw >> b) & 1) === 1);
    }
    let bitIdx = 0;
    for (let right = N - 1; right >= 1; right -= 2) {
      if (right === 6) right = 5;
      for (let vert = 0; vert < N; vert++) {
        for (let j = 0; j < 2; j++) {
          const c = right - j;
          const upward = ((right + 1) & 2) === 0;
          const r = upward ? N - 1 - vert : vert;
          if (!isFunc[r][c] && bitIdx < codeBits.length) {
            mod[r][c] = codeBits[bitIdx++];
          }
        }
      }
    }

    const maskPreds = [
      (r, c) => (r + c) % 2 === 0,
      (r, c) => r % 2 === 0,
      (r, c) => c % 3 === 0,
      (r, c) => (r + c) % 3 === 0,
      (r, c) => (Math.floor(r / 2) + Math.floor(c / 3)) % 2 === 0,
      (r, c) => ((r * c) % 2) + ((r * c) % 3) === 0,
      (r, c) => (((r * c) % 2) + ((r * c) % 3)) % 2 === 0,
      (r, c) => (((r + c) % 2) + ((r * c) % 3)) % 2 === 0,
    ];

    const applyMaskAndFormat = (maskId) => {
      const m = mod.map((row) => row.slice());
      const pred = maskPreds[maskId];
      for (let r = 0; r < N; r++) {
        for (let c = 0; c < N; c++) {
          if (!isFunc[r][c] && pred(r, c)) m[r][c] = !m[r][c];
        }
      }
      // ECC Level M format bits = 0b00
      const data5 = (0b00 << 3) | maskId;
      let rem = data5;
      for (let i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >> 9) * 0x537);
      const fmt = ((data5 << 10) | rem) ^ 0x5412;
      for (let i = 0; i < 6; i++) m[8][i] = ((fmt >> (14 - i)) & 1) === 1;
      m[8][7] = ((fmt >> 8) & 1) === 1;
      m[8][8] = ((fmt >> 7) & 1) === 1;
      m[7][8] = ((fmt >> 6) & 1) === 1;
      for (let i = 0; i < 6; i++) m[i][8] = ((fmt >> i) & 1) === 1;
      for (let i = 0; i < 8; i++) m[8][N - 1 - i] = ((fmt >> i) & 1) === 1;
      for (let i = 8; i < 15; i++) m[N - 15 + i][8] = ((fmt >> i) & 1) === 1;
      return m;
    };

    const scorePenalty = (m) => {
      let pen = 0;
      for (let r = 0; r < N; r++) {
        let run = 1;
        for (let c = 1; c < N; c++) {
          if (m[r][c] === m[r][c - 1]) run++;
          else { if (run >= 5) pen += run - 2; run = 1; }
        }
        if (run >= 5) pen += run - 2;
      }
      for (let c = 0; c < N; c++) {
        let run = 1;
        for (let r = 1; r < N; r++) {
          if (m[r][c] === m[r - 1][c]) run++;
          else { if (run >= 5) pen += run - 2; run = 1; }
        }
        if (run >= 5) pen += run - 2;
      }
      for (let r = 0; r < N - 1; r++) {
        for (let c = 0; c < N - 1; c++) {
          if (m[r][c] === m[r][c + 1] && m[r][c] === m[r + 1][c] && m[r][c] === m[r + 1][c + 1]) pen += 3;
        }
      }
      return pen;
    };

    let best = applyMaskAndFormat(0);
    let bestPen = scorePenalty(best);
    for (let mid = 1; mid < 8; mid++) {
      const cand = applyMaskAndFormat(mid);
      const p = scorePenalty(cand);
      if (p < bestPen) {
        bestPen = p;
        best = cand;
      }
    }
    return best;
  }

  function renderQrSvg(text, pxSize = 132) {
    const matrix = buildQrMatrix(text);
    const n = matrix.length;
    const quiet = 2;
    const total = n + quiet * 2;
    const rects = [];
    for (let r = 0; r < n; r++) {
      for (let c = 0; c < n; c++) {
        if (matrix[r][c]) {
          rects.push(`<rect x="${c + quiet}" y="${r + quiet}" width="1" height="1"/>`);
        }
      }
    }
    return `<svg xmlns="http://www.w3.org/2000/svg" width="${pxSize}" height="${pxSize}" viewBox="0 0 ${total} ${total}" shape-rendering="crispEdges"><rect width="${total}" height="${total}" fill="#ffffff"/><g fill="#0b0f19">${rects.join("")}</g></svg>`;
  }

  function formatSixDigit(code) {
    const s = String(code || "").replace(/\D/g, "").padStart(6, "0").slice(0, 6);
    return `${s.slice(0, 3)} ${s.slice(3, 6)}`;
  }

  async function refreshQuickCode() {
    try {
      const res = await apiJson("/api/host/quick-code/refresh", { method: "POST", body: "{}" });
      if (state.host) {
        state.host.quickCode = res.quickCode;
        state.host.quickPairUri = res.quickPairUri;
      }
      updateSidebarCounters();
      renderCurrentView();
      showToast(`New Easy Login code: ${res.quickCode}`);
    } catch (_) {
      showToast("Could not refresh code");
    }
  }

  async function apiJson(url, options = {}) {
    const res = await fetch(url, {
      headers: { "Content-Type": "application/json", ...(options.headers || {}) },
      ...options,
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return res.json();
  }

  async function refreshState() {
    try {
      const data = await apiJson("/api/host/state");
      state.host = data;
      state.items = data.items || [];
      updateSidebarCounters();
      renderCurrentView();
    } catch (err) {
      showToast("Failed to load Linux host state");
    }
  }

  function filteredItems(predicate) {
    const q = state.search.trim().toLowerCase();
    return state.items.filter((item) => {
      if (predicate && !predicate(item)) return false;
      if (!q) return true;
      return (
        (item.title || "").toLowerCase().includes(q) ||
        (item.subtitle || "").toLowerCase().includes(q) ||
        (item.seriesName || "").toLowerCase().includes(q)
      );
    });
  }

  function isMovieOrVideo(item) {
    return item.mediaType === "MOVIE" || item.mediaType === "VIDEO";
  }

  function isShowOrEpisode(item) {
    return item.mediaType === "SERIES" || item.mediaType === "EPISODE";
  }

  function isAudio(item) {
    return item.mediaType === "AUDIO";
  }

  function updateSidebarCounters() {
    document.getElementById("count-movies").textContent = state.items.filter(isMovieOrVideo).length;
    document.getElementById("count-shows").textContent = state.items.filter(isShowOrEpisode).length;
    document.getElementById("count-music").textContent = state.items.filter(isAudio).length;
    const platformLabel = ((state.host && state.host.hostPlatform) || "PC").toUpperCase();
    const subEl = document.getElementById("brand-platform-sub");
    if (subEl) subEl.textContent = `${platformLabel} MEDIA HOST`;
    const lan = (state.host && state.host.lanUrls && state.host.lanUrls[0]) || "http://127.0.0.1:8765";
    document.getElementById("sidebar-lan-url").textContent = lan;
    const qCodeEl = document.getElementById("sidebar-quick-code");
    if (qCodeEl) {
      qCodeEl.textContent = state.host && state.host.quickCode ? formatSixDigit(state.host.quickCode) : "------";
    }
  }

  function renderMediaCard(item, queueList) {
    const prog = item.durationMs > 0 && item.playbackPositionMs > 0
      ? Math.min(100, Math.round((item.playbackPositionMs / item.durationMs) * 100))
      : 0;
    const typeIcon = isAudio(item) ? "&#9835;" : isShowOrEpisode(item) ? "&#128250;" : "&#127909;";
    const subParts = [
      item.subtitle,
      item.releaseYear,
      formatDuration(item.durationMs),
      formatSize(item.fileSizeBytes),
    ].filter(Boolean);

    const card = document.createElement("div");
    card.className = "media-card";
    card.innerHTML = `
      <div class="card-thumb">
        ${item.posterUrl ? `<img src="${escapeHtml(item.posterUrl)}" alt="${escapeHtml(item.title)}" />` : `<div class="card-thumb-icon">${typeIcon}</div>`}
        <span class="card-badge-tl">${escapeHtml(item.codecBadge || item.mediaType)}</span>
        ${item.watchStatus === "WATCHED" ? `<span class="card-badge-tr">&#10003; WATCHED</span>` : ""}
        <div class="card-play-overlay">&#9654;</div>
        ${prog > 1 ? `<div class="card-progress-bar"><div class="card-progress-fill" style="width:${prog}%"></div></div>` : ""}
      </div>
      <div class="card-body">
        <div class="card-title" title="${escapeHtml(item.title)}">${escapeHtml(item.title)}</div>
        <div class="card-sub">${escapeHtml(subParts.join(" • ") || "Linux Host Media")}</div>
        <div class="card-actions">
          <button class="btn-primary btn-play">${prog > 2 && item.watchStatus !== "WATCHED" ? "Resume" : "Play"}</button>
          <button class="icon-square-btn btn-fav ${item.isFavorite ? "active" : ""}" title="Toggle Favorite">${item.isFavorite ? "&#9829;" : "&#9825;"}</button>
        </div>
      </div>
    `;

    const playFn = () => playMedia(item, queueList);
    card.querySelector(".card-thumb").addEventListener("click", playFn);
    card.querySelector(".btn-play").addEventListener("click", playFn);
    card.querySelector(".btn-fav").addEventListener("click", () => toggleFavorite(item));
    return card;
  }

  function renderHomeView() {
    const movies = filteredItems(isMovieOrVideo);
    const shows = filteredItems(isShowOrEpisode);
    const music = filteredItems(isAudio);
    const continueList = filteredItems(
      (i) => i.playbackPositionMs > 5000 && i.watchStatus !== "WATCHED"
    ).sort((a, b) => (b.lastPlayedAt || 0) - (a.lastPlayedAt || 0));
    const recentList = filteredItems().sort((a, b) => (b.addedAt || 0) - (a.addedAt || 0)).slice(0, 12);
    const lanUrl = (state.host && state.host.lanUrls && state.host.lanUrls[0]) || "http://127.0.0.1:8765";
    const quickCode = (state.host && state.host.quickCode) || "000000";
    const pairUri = (state.host && state.host.quickPairUri) || `lootstream://pair?url=${encodeURIComponent(lanUrl)}&code=${quickCode}`;

    viewEl.innerHTML = "";

    const hero = document.createElement("section");
    hero.className = "hero-banner";
    hero.innerHTML = `
      <div>
        <span class="hero-badge">AMPCHOR-INSPIRED PERSONAL MEDIA HUB</span>
        <h1 class="hero-title">${escapeHtml((state.host && state.host.serverName) || "LootStream Media Host")}</h1>
        <p class="hero-desc">
          Watch your movies &amp; TV shows, listen to lossless music directly in this desktop app, or pair your Android LootStream app instantly on the same Wi-Fi.
        </p>
        <div class="hero-cta-row">
          <button class="btn-primary" id="hero-open-folders">+ Manage Media Folders</button>
          <button class="btn-outline" id="hero-copy-lan">Copy Pairing URL</button>
          <button class="btn-outline" id="hero-new-code">&#8635; New 6-Digit Code</button>
        </div>
      </div>
      <div class="easy-pair-widget">
        <div class="qr-frame">${renderQrSvg(pairUri, 128)}</div>
        <div class="easy-pair-meta">
          <span class="easy-pair-label">EASY WI-FI LOGIN</span>
          <div class="quick-code-display">${escapeHtml(formatSixDigit(quickCode))}</div>
          <div class="quick-code-hint">Scan QR or enter this 6-digit code in LootStream Android on the same Wi-Fi.</div>
        </div>
      </div>
    `;
    hero.querySelector("#hero-open-folders").addEventListener("click", () => switchView("host"));
    hero.querySelector("#hero-copy-lan").addEventListener("click", () => copyText(lanUrl));
    hero.querySelector("#hero-new-code").addEventListener("click", refreshQuickCode);
    viewEl.appendChild(hero);

    const stats = document.createElement("section");
    stats.className = "stats-grid";
    stats.innerHTML = `
      <div class="stat-card"><div class="stat-value">${state.items.length}</div><div class="stat-label">Total Media Items</div></div>
      <div class="stat-card"><div class="stat-value">${movies.length}</div><div class="stat-label">Movies &amp; Videos</div></div>
      <div class="stat-card"><div class="stat-value">${shows.length}</div><div class="stat-label">TV Episodes</div></div>
      <div class="stat-card"><div class="stat-value">${music.length}</div><div class="stat-label">Music Tracks</div></div>
      <div class="stat-card"><div class="stat-value">${(state.host && state.host.pairedDevices && state.host.pairedDevices.length) || 0}</div><div class="stat-label">Paired Android Devices</div></div>
    `;
    viewEl.appendChild(stats);

    if (continueList.length > 0) {
      const sec = document.createElement("section");
      sec.innerHTML = `<div class="section-header"><h2 class="section-title">Continue Watching</h2></div>`;
      const grid = document.createElement("div");
      grid.className = "media-grid";
      continueList.slice(0, 8).forEach((item) => grid.appendChild(renderMediaCard(item, continueList)));
      sec.appendChild(grid);
      viewEl.appendChild(sec);
    }

    const recentSec = document.createElement("section");
    recentSec.innerHTML = `<div class="section-header"><h2 class="section-title">Recently Added</h2></div>`;
    if (recentList.length === 0) {
      recentSec.innerHTML += `<div class="empty-card">No media files found in your shared folders yet. Click <strong>+ Add Folder</strong> above to share Movies, TV Shows, or Music.</div>`;
    } else {
      const grid = document.createElement("div");
      grid.className = "media-grid";
      recentList.forEach((item) => grid.appendChild(renderMediaCard(item, recentList)));
      recentSec.appendChild(grid);
    }
    viewEl.appendChild(recentSec);
  }

  function renderMoviesView() {
    const movies = filteredItems(isMovieOrVideo);
    viewEl.innerHTML = `<div class="section-header"><h2 class="section-title">Movies &amp; Videos (${movies.length})</h2></div>`;
    if (movies.length === 0) {
      viewEl.innerHTML += `<div class="empty-card">No movies or videos found matching your filter.</div>`;
      return;
    }
    const grid = document.createElement("div");
    grid.className = "media-grid";
    movies.forEach((m) => grid.appendChild(renderMediaCard(m, movies)));
    viewEl.appendChild(grid);
  }

  function renderShowsView() {
    const episodes = filteredItems(isShowOrEpisode);
    viewEl.innerHTML = `<div class="section-header"><h2 class="section-title">TV Shows &amp; Series (${episodes.length} episodes)</h2></div>`;
    if (episodes.length === 0) {
      viewEl.innerHTML += `<div class="empty-card">No TV shows found. Name episode files like <code>Show.S01E02.mkv</code> or place them inside a <code>TV/</code> or <code>Shows/</code> folder.</div>`;
      return;
    }
    const grouped = new Map();
    episodes.forEach((ep) => {
      const key = ep.seriesName || ep.subtitle || "TV Series";
      if (!grouped.has(key)) grouped.set(key, []);
      grouped.get(key).push(ep);
    });

    grouped.forEach((epList, seriesTitle) => {
      epList.sort((a, b) => (a.seasonNumber || 0) - (b.seasonNumber || 0) || (a.episodeNumber || 0) - (b.episodeNumber || 0));
      const group = document.createElement("div");
      group.className = "series-group";
      group.innerHTML = `
        <div class="series-header">
          <div class="series-name">${escapeHtml(seriesTitle)}</div>
          <span class="nav-count">${epList.length} episodes</span>
        </div>
      `;
      const grid = document.createElement("div");
      grid.className = "media-grid";
      epList.forEach((ep) => grid.appendChild(renderMediaCard(ep, epList)));
      group.appendChild(grid);
      viewEl.appendChild(group);
    });
  }

  function renderMusicView() {
    const tracks = filteredItems(isAudio);
    viewEl.innerHTML = `
      <div class="section-header">
        <h2 class="section-title">Music Library (${tracks.length} tracks)</h2>
        ${tracks.length > 0 ? `<button class="btn-primary btn-sm" id="btn-play-all-music">&#9654; Play All</button>` : ""}
      </div>
    `;
    if (tracks.length === 0) {
      viewEl.innerHTML += `<div class="empty-card">No music files found (.mp3, .flac, .wav, .m4a, .aac, .ogg, .opus).</div>`;
      return;
    }
    const playAllBtn = viewEl.querySelector("#btn-play-all-music");
    if (playAllBtn) playAllBtn.addEventListener("click", () => playAudioTrack(tracks[0], tracks));

    const list = document.createElement("div");
    list.className = "track-list";
    tracks.forEach((tr) => {
      const row = document.createElement("div");
      row.className = "track-row";
      row.innerHTML = `
        <div class="track-icon">${tr.posterUrl ? `<img src="${escapeHtml(tr.posterUrl)}" alt="" />` : "&#9835;"}</div>
        <div class="track-meta">
          <div class="track-title">${escapeHtml(tr.title)}</div>
          <div class="track-sub">${escapeHtml([tr.subtitle, tr.codecBadge, formatDuration(tr.durationMs), formatSize(tr.fileSizeBytes)].filter(Boolean).join(" • "))}</div>
        </div>
        <button class="icon-square-btn ${tr.isFavorite ? "active" : ""}">${tr.isFavorite ? "&#9829;" : "&#9825;"}</button>
        <button class="btn-primary btn-sm">&#9654; Listen</button>
      `;
      row.querySelector(".icon-square-btn").addEventListener("click", () => toggleFavorite(tr));
      row.querySelector(".btn-primary").addEventListener("click", () => playAudioTrack(tr, tracks));
      list.appendChild(row);
    });
    viewEl.appendChild(list);
  }

  function renderHostView() {
    const host = state.host || {};
    const lanUrls = host.lanUrls || [];
    const folders = host.folders || [];
    const logs = (host.activityLog || []).slice().reverse();
    const quickCode = host.quickCode || "000000";
    const pairUri = host.quickPairUri || `lootstream://pair?url=${encodeURIComponent(lanUrls[0] || "http://127.0.0.1:8765")}&code=${quickCode}`;

    viewEl.innerHTML = "";
    const grid = document.createElement("div");
    grid.className = "panel-grid";

    // Easy Wi-Fi Login Card
    const pairCard = document.createElement("div");
    pairCard.className = "panel-card";
    pairCard.innerHTML = `
      <div class="section-header" style="margin-bottom:0">
        <h3>Easy Wi-Fi Android Login</h3>
        <button class="btn-outline btn-sm" id="host-refresh-code-btn">&#8635; New Code</button>
      </div>
      <div class="easy-pair-widget" style="background:var(--surface-var)">
        <div class="qr-frame">${renderQrSvg(pairUri, 148)}</div>
        <div class="easy-pair-meta">
          <span class="easy-pair-label">6-DIGIT QUICK CODE</span>
          <div class="quick-code-display">${escapeHtml(formatSixDigit(quickCode))}</div>
          <div class="quick-code-hint">Open LootStream Android on the same Wi-Fi, tap <strong>Connect Host</strong>, and scan this QR or enter the 6-digit code.</div>
        </div>
      </div>
    `;
    pairCard.querySelector("#host-refresh-code-btn").addEventListener("click", refreshQuickCode);
    grid.appendChild(pairCard);

    // Server Configuration Card
    const cfgCard = document.createElement("div");
    cfgCard.className = "panel-card";
    cfgCard.innerHTML = `
      <h3>Host Server Settings</h3>
      <div class="field-group">
        <label>Host Display Name</label>
        <input id="cfg-server-name" value="${escapeHtml(host.serverName || "LootStream Host")}" />
      </div>
      <div class="field-group">
        <label>Static Manual PIN (optional; QR &amp; 6-digit code always work)</label>
        <input id="cfg-pin" value="${escapeHtml(host.pin || "")}" placeholder="e.g. 1234" />
      </div>
      <button class="btn-primary" id="cfg-save-btn">Save Host Settings</button>
      <div class="field-group">
        <label>Android App LAN Endpoints</label>
        ${lanUrls.map((u) => `<div class="folder-item"><code>${escapeHtml(u)}</code><button class="btn-outline btn-sm copy-btn" data-url="${escapeHtml(u)}">Copy</button></div>`).join("")}
      </div>
    `;
    cfgCard.querySelector("#cfg-save-btn").addEventListener("click", async () => {
      const serverName = cfgCard.querySelector("#cfg-server-name").value;
      const pin = cfgCard.querySelector("#cfg-pin").value;
      await apiJson("/api/host/config", { method: "POST", body: JSON.stringify({ serverName, pin }) });
      showToast("Saved host settings");
      refreshState();
    });
    cfgCard.querySelectorAll(".copy-btn").forEach((b) => {
      b.addEventListener("click", () => copyText(b.dataset.url));
    });
    grid.appendChild(cfgCard);

    // Shared Folders Card
    const foldersCard = document.createElement("div");
    foldersCard.className = "panel-card";
    foldersCard.innerHTML = `
      <div class="section-header" style="margin-bottom:0">
        <h3>Shared Media Folders</h3>
        <button class="btn-primary btn-sm" id="host-add-folder-btn">+ Add Folder</button>
      </div>
      <div class="track-list" id="host-folders-list">
        ${folders.length === 0 ? `<div class="empty-card">No folders shared yet.</div>` : folders.map((f) => `
          <div class="folder-item">
            <span style="overflow:hidden;text-overflow:ellipsis">${escapeHtml(f)}</span>
            <button class="btn-danger btn-sm remove-folder-btn" data-path="${escapeHtml(f)}">Remove</button>
          </div>
        `).join("")}
      </div>
      ${host.hostPlatform === "Linux" ? `<div style="font-size:12px;color:var(--text-sec)">
        Duration metadata probe (ffprobe): <strong style="color:${host.ffprobeAvailable ? "var(--green)" : "var(--gold)"}">${host.ffprobeAvailable ? "Available" : "Not installed (install ffmpeg for runtimes)"}</strong>
      </div>` : ""}
    `;
    foldersCard.querySelector("#host-add-folder-btn").addEventListener("click", openFolderModal);
    foldersCard.querySelectorAll(".remove-folder-btn").forEach((b) => {
      b.addEventListener("click", async () => {
        await apiJson("/api/host/folders/remove", { method: "POST", body: JSON.stringify({ path: b.dataset.path }) });
        showToast("Removed folder");
        refreshState();
      });
    });
    grid.appendChild(foldersCard);

    // Activity Log Card
    const logCard = document.createElement("div");
    logCard.className = "panel-card";
    logCard.innerHTML = `
      <h3>Activity &amp; Sync Log</h3>
      <div class="activity-box">${logs.length ? logs.map(escapeHtml).join("<br>") : "Waiting for activity..."}</div>
    `;
    grid.appendChild(logCard);

    viewEl.appendChild(grid);
  }

  function renderCurrentView() {
    if (state.view === "home") renderHomeView();
    else if (state.view === "movies") renderMoviesView();
    else if (state.view === "shows") renderShowsView();
    else if (state.view === "music") renderMusicView();
    else if (state.view === "host") renderHostView();
  }

  function switchView(view) {
    state.view = view;
    document.querySelectorAll(".nav-btn").forEach((btn) => {
      btn.classList.toggle("active", btn.dataset.view === view);
    });
    renderCurrentView();
  }

  // Playback: Video vs Music
  function playMedia(item, queueList) {
    if (isAudio(item)) {
      playAudioTrack(item, (queueList || state.items).filter(isAudio));
      return;
    }
    state.activeVideo = item;
    state.videoQueue = (queueList || state.items).filter((i) => !isAudio(i));
    theaterTitle.textContent = item.title;
    theaterSubtitle.textContent = [item.subtitle, item.codecBadge, formatSize(item.fileSizeBytes)].filter(Boolean).join(" • ");
    theaterFavBtn.innerHTML = item.isFavorite ? "&#9829; Favorited" : "&#9734; Favorite";
    theaterModal.classList.remove("hidden");

    videoEl.src = item.streamUrl;
    videoEl.playbackRate = Number(theaterSpeed.value) || 1;
    videoEl.onloadedmetadata = () => {
      if (item.playbackPositionMs > 5000 && item.watchStatus !== "WATCHED") {
        const resumeSec = item.playbackPositionMs / 1000;
        if (!videoEl.duration || resumeSec < videoEl.duration - 5) {
          videoEl.currentTime = resumeSec;
        }
      }
      videoEl.play().catch(() => {});
    };

    clearInterval(state.progressTimer);
    state.progressTimer = setInterval(() => saveVideoProgress(false), 4000);
  }

  async function saveVideoProgress(isEnded) {
    const item = state.activeVideo;
    if (!item || !videoEl.duration) return;
    const posMs = isEnded ? Math.round(videoEl.duration * 1000) : Math.round(videoEl.currentTime * 1000);
    const durMs = Math.round(videoEl.duration * 1000);
    const status = isEnded || (durMs > 0 && posMs / durMs > 0.94) ? "WATCHED" : posMs > 4000 ? "IN_PROGRESS" : "UNWATCHED";
    item.playbackPositionMs = posMs;
    item.durationMs = durMs;
    item.watchStatus = status;
    try {
      await apiJson(`/api/items/${encodeURIComponent(item.id)}/progress`, {
        method: "POST",
        body: JSON.stringify({ positionMs: posMs, durationMs: durMs, watchStatus: status }),
      });
      theaterSyncStatus.textContent = `Synced ${formatClock(posMs / 1000)} with host & Android`;
    } catch (_) {}
  }

  function closeTheater() {
    saveVideoProgress(false);
    clearInterval(state.progressTimer);
    videoEl.pause();
    videoEl.removeAttribute("src");
    videoEl.load();
    theaterModal.classList.add("hidden");
    state.activeVideo = null;
    refreshState();
  }

  function playAudioTrack(track, queue) {
    state.activeTrack = track;
    state.audioQueue = queue && queue.length ? queue : [track];
    musicDock.classList.remove("hidden");
    musicDockTitle.textContent = track.title;
    musicDockSub.textContent = [track.subtitle, track.codecBadge].filter(Boolean).join(" • ");
    audioEl.src = track.streamUrl;
    audioEl.volume = Number(musicVolume.value);
    audioEl.play().catch(() => {});
    musicPlayPause.innerHTML = "&#10074;&#10074;";
  }

  function stepAudioTrack(delta) {
    if (!state.activeTrack || !state.audioQueue.length) return;
    const idx = state.audioQueue.findIndex((t) => t.id === state.activeTrack.id);
    const nextIdx = (idx + delta + state.audioQueue.length) % state.audioQueue.length;
    playAudioTrack(state.audioQueue[nextIdx], state.audioQueue);
  }

  async function toggleFavorite(item) {
    const next = !item.isFavorite;
    item.isFavorite = next;
    await apiJson(`/api/items/${encodeURIComponent(item.id)}/favorite`, {
      method: "POST",
      body: JSON.stringify({ isFavorite: next }),
    });
    if (state.activeVideo && state.activeVideo.id === item.id) {
      theaterFavBtn.innerHTML = next ? "&#9829; Favorited" : "&#9734; Favorite";
    }
    renderCurrentView();
    showToast(next ? `Added "${item.title}" to Favorites` : `Removed "${item.title}" from Favorites`);
  }

  function copyText(text) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(() => showToast(`Copied ${text}`)).catch(() => showToast(text));
    } else {
      showToast(text);
    }
  }

  // Folder Browser Modal
  async function loadBrowseDirs(path) {
    const data = await apiJson(`/api/host/browse-dirs?path=${encodeURIComponent(path || "")}`);
    state.browseCurrentPath = data.currentPath;
    state.browseParentPath = data.parentPath;
    dirCurrentLabel.textContent = data.currentPath;
    folderManualInput.value = data.currentPath;
    dirListEl.innerHTML = "";
    (data.directories || []).forEach((d) => {
      const row = document.createElement("div");
      row.className = "dir-row";
      row.innerHTML = `<span>&#128193;</span><span>${escapeHtml(d.name)}</span>`;
      row.addEventListener("click", () => loadBrowseDirs(d.path));
      dirListEl.appendChild(row);
    });
  }

  function openFolderModal() {
    folderModal.classList.remove("hidden");
    loadBrowseDirs(state.browseCurrentPath || "");
  }

  // Event Bindings
  document.querySelectorAll(".nav-btn").forEach((btn) => {
    btn.addEventListener("click", () => switchView(btn.dataset.view));
  });

  searchInput.addEventListener("input", () => {
    state.search = searchInput.value;
    renderCurrentView();
  });

  document.getElementById("btn-copy-lan").addEventListener("click", () => {
    const lan = document.getElementById("sidebar-lan-url").textContent;
    copyText(lan);
  });

  const refreshCodeBtn = document.getElementById("btn-refresh-code");
  if (refreshCodeBtn) refreshCodeBtn.addEventListener("click", refreshQuickCode);

  document.getElementById("btn-rescan").addEventListener("click", async () => {
    const res = await apiJson("/api/host/rescan", { method: "POST", body: "{}" });
    showToast(`Rescanned library (${res.itemCount} items)`);
    refreshState();
  });

  document.getElementById("btn-quick-add-folder").addEventListener("click", openFolderModal);
  document.getElementById("folder-modal-close").addEventListener("click", () => folderModal.classList.add("hidden"));
  document.getElementById("btn-dir-up").addEventListener("click", () => {
    if (state.browseParentPath) loadBrowseDirs(state.browseParentPath);
  });
  document.getElementById("btn-add-current-dir").addEventListener("click", async () => {
    const target = folderManualInput.value.trim();
    if (!target) return;
    const res = await apiJson("/api/host/folders", { method: "POST", body: JSON.stringify({ path: target }) });
    if (res.ok) {
      folderModal.classList.add("hidden");
      showToast(`Added ${res.message}`);
      refreshState();
    } else {
      showToast(res.message || "Invalid directory");
    }
  });
  document.getElementById("btn-native-picker").addEventListener("click", async () => {
    const res = await apiJson("/api/host/pick-folder", { method: "POST", body: "{}" });
    if (res.ok) {
      folderModal.classList.add("hidden");
      showToast(`Added ${res.path}`);
      refreshState();
    } else if (res.cancelled) {
      showToast("Native OS picker unavailable or cancelled");
    }
  });

  // Theater controls
  document.getElementById("theater-close-btn").addEventListener("click", closeTheater);
  document.getElementById("theater-rew-btn").addEventListener("click", () => {
    videoEl.currentTime = Math.max(0, videoEl.currentTime - 10);
  });
  document.getElementById("theater-fwd-btn").addEventListener("click", () => {
    videoEl.currentTime = Math.min(videoEl.duration || videoEl.currentTime + 10, videoEl.currentTime + 10);
  });
  theaterSpeed.addEventListener("change", () => {
    videoEl.playbackRate = Number(theaterSpeed.value) || 1;
  });
  theaterFitBtn.addEventListener("click", () => {
    const fill = videoEl.classList.toggle("fill-mode");
    theaterFitBtn.textContent = fill ? "Fill" : "Fit";
  });
  theaterFavBtn.addEventListener("click", () => {
    if (state.activeVideo) toggleFavorite(state.activeVideo);
  });
  theaterWatchedBtn.addEventListener("click", async () => {
    if (!state.activeVideo) return;
    await apiJson(`/api/items/${encodeURIComponent(state.activeVideo.id)}/watched`, {
      method: "POST",
      body: JSON.stringify({ watchStatus: "WATCHED" }),
    });
    state.activeVideo.watchStatus = "WATCHED";
    showToast("Marked as watched");
  });
  document.getElementById("theater-next-btn").addEventListener("click", () => {
    if (!state.activeVideo || !state.videoQueue.length) return;
    const idx = state.videoQueue.findIndex((i) => i.id === state.activeVideo.id);
    const next = state.videoQueue[(idx + 1) % state.videoQueue.length];
    playMedia(next, state.videoQueue);
  });
  videoEl.addEventListener("ended", () => saveVideoProgress(true));

  // Audio controls
  musicPlayPause.addEventListener("click", () => {
    if (audioEl.paused) {
      audioEl.play().catch(() => {});
      musicPlayPause.innerHTML = "&#10074;&#10074;";
    } else {
      audioEl.pause();
      musicPlayPause.innerHTML = "&#9654;";
    }
  });
  document.getElementById("music-prev").addEventListener("click", () => stepAudioTrack(-1));
  document.getElementById("music-next").addEventListener("click", () => stepAudioTrack(1));
  document.getElementById("music-close").addEventListener("click", () => {
    audioEl.pause();
    audioEl.removeAttribute("src");
    musicDock.classList.add("hidden");
  });
  musicVolume.addEventListener("input", () => {
    audioEl.volume = Number(musicVolume.value);
  });
  audioEl.addEventListener("timeupdate", () => {
    if (!audioEl.duration) return;
    musicTimeCur.textContent = formatClock(audioEl.currentTime);
    musicTimeDur.textContent = formatClock(audioEl.duration);
    musicSeek.value = Math.round((audioEl.currentTime / audioEl.duration) * 1000);
  });
  audioEl.addEventListener("ended", () => stepAudioTrack(1));
  musicSeek.addEventListener("input", () => {
    if (!audioEl.duration) return;
    audioEl.currentTime = (Number(musicSeek.value) / 1000) * audioEl.duration;
  });

  refreshState();
})();
