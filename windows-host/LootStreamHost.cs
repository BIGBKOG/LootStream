using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Web.Script.Serialization;
using System.Windows.Forms;

namespace LootStreamHost
{
    public class HostState
    {
        public string ServerId { get; set; }
        public string ServerName { get; set; }
        public int Port { get; set; }
        public string Pin { get; set; }
        public List<string> Folders { get; set; }
        public List<string> ValidTokens { get; set; }
        public Dictionary<string, ItemProgress> Progress { get; set; }

        public HostState()
        {
            ServerId = Guid.NewGuid().ToString("N").Substring(0, 12);
            ServerName = Environment.MachineName + " LootStream";
            Port = 8765;
            Pin = "";
            Folders = new List<string>();
            ValidTokens = new List<string>();
            Progress = new Dictionary<string, ItemProgress>();
        }
    }

    public class ItemProgress
    {
        public long PlaybackPositionMs { get; set; }
        public long DurationMs { get; set; }
        public string WatchStatus { get; set; }
        public bool IsFavorite { get; set; }
        public int PlayCount { get; set; }
        public long LastPlayedAt { get; set; }
    }

    public class MediaEntry
    {
        public string Id { get; set; }
        public string FilePath { get; set; }
        public string Title { get; set; }
        public string Subtitle { get; set; }
        public string MediaType { get; set; }
        public string CodecBadge { get; set; }
        public int? ReleaseYear { get; set; }
        public string SeriesName { get; set; }
        public int? SeasonNumber { get; set; }
        public int? EpisodeNumber { get; set; }
        public long FileSizeBytes { get; set; }
        public long AddedAt { get; set; }
    }

    public class MainForm : Form
    {
        private static readonly HashSet<string> VideoExtensions = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            ".mp4", ".mkv", ".webm", ".mov", ".avi", ".m4v", ".ts"
        };

        private static readonly HashSet<string> AudioExtensions = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            ".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg"
        };

        private readonly JavaScriptSerializer _json = new JavaScriptSerializer();
        private readonly object _syncLock = new object();
        private readonly string _stateFilePath;

        private HostState _state;
        private List<MediaEntry> _catalog = new List<MediaEntry>();
        private Dictionary<string, MediaEntry> _catalogById = new Dictionary<string, MediaEntry>();
        private HttpListener _listener;
        private Thread _listenerThread;
        private volatile bool _isRunning;

        // Cyber UI Controls
        private TextBox _txtServerName;
        private TextBox _txtPort;
        private TextBox _txtPin;
        private Label _lblStatusBadge;
        private Label _lblLanUrl;
        private Label _lblMediaCount;
        private Button _btnToggleServer;
        private Button _btnCopyUrl;
        private ListBox _lstFolders;
        private TextBox _txtLog;

        public MainForm()
        {
            _json.MaxJsonLength = 32 * 1024 * 1024;
            string appData = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                "LootStreamHost"
            );
            Directory.CreateDirectory(appData);
            _stateFilePath = Path.Combine(appData, "state.json");
            _state = LoadState();
            if (_state.Folders.Count == 0)
            {
                string videos = Environment.GetFolderPath(Environment.SpecialFolder.MyVideos);
                string music = Environment.GetFolderPath(Environment.SpecialFolder.MyMusic);
                if (!string.IsNullOrEmpty(videos) && Directory.Exists(videos)) _state.Folders.Add(videos);
                if (!string.IsNullOrEmpty(music) && Directory.Exists(music)) _state.Folders.Add(music);
            }

            InitializeCyberUi();
            RescanLibrary();
            StartServer();
        }

        private void InitializeCyberUi()
        {
            Color bg = Color.FromArgb(11, 15, 25);
            Color surface = Color.FromArgb(19, 25, 38);
            Color surfaceVar = Color.FromArgb(28, 37, 56);
            Color cyan = Color.FromArgb(0, 229, 255);
            Color green = Color.FromArgb(0, 255, 135);
            Color purple = Color.FromArgb(176, 102, 255);
            Color textPrimary = Color.FromArgb(241, 245, 249);
            Color textSecondary = Color.FromArgb(148, 163, 184);

            this.Text = "LootStream PC Host v1.0";
            this.Size = new Size(760, 620);
            this.MinimumSize = new Size(680, 560);
            this.BackColor = bg;
            this.ForeColor = textPrimary;
            this.Font = new Font("Segoe UI", 9.5f);
            this.StartPosition = FormStartPosition.CenterScreen;

            Panel header = new Panel();
            header.Dock = DockStyle.Top;
            header.Height = 76;
            header.BackColor = surface;
            header.Padding = new Padding(18, 12, 18, 12);

            Label lblTitle = new Label();
            lblTitle.Text = "LOOTSTREAM PC HOST";
            lblTitle.Font = new Font("Segoe UI", 15f, FontWeightBold());
            lblTitle.ForeColor = cyan;
            lblTitle.Location = new Point(18, 12);
            lblTitle.AutoSize = true;

            Label lblSub = new Label();
            lblSub.Text = "Direct LAN Media Streaming Server for LootStream Android";
            lblSub.Font = new Font("Segoe UI", 9f);
            lblSub.ForeColor = textSecondary;
            lblSub.Location = new Point(20, 42);
            lblSub.AutoSize = true;

            _lblStatusBadge = new Label();
            _lblStatusBadge.Text = "STOPPED";
            _lblStatusBadge.Font = new Font("Segoe UI", 9.5f, FontWeightBold());
            _lblStatusBadge.ForeColor = textSecondary;
            _lblStatusBadge.BackColor = surfaceVar;
            _lblStatusBadge.TextAlign = ContentAlignment.MiddleCenter;
            _lblStatusBadge.Size = new Size(110, 30);
            _lblStatusBadge.Location = new Point(610, 22);
            _lblStatusBadge.Anchor = AnchorStyles.Top | AnchorStyles.Right;

            header.Controls.Add(lblTitle);
            header.Controls.Add(lblSub);
            header.Controls.Add(_lblStatusBadge);

            Panel body = new Panel();
            body.Dock = DockStyle.Fill;
            body.Padding = new Padding(18);

            // Connection & Server Config Card
            Panel cfgCard = new Panel();
            cfgCard.Location = new Point(18, 14);
            cfgCard.Size = new Size(706, 148);
            cfgCard.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
            cfgCard.BackColor = surface;

            Label lblName = MakeLabel("Host Name:", 14, 14, textSecondary);
            _txtServerName = MakeTextBox(_state.ServerName, 14, 34, 240, surfaceVar, textPrimary);

            Label lblPort = MakeLabel("Port:", 270, 14, textSecondary);
            _txtPort = MakeTextBox(_state.Port.ToString(), 270, 34, 90, surfaceVar, textPrimary);

            Label lblPin = MakeLabel("Pairing PIN (optional):", 376, 14, textSecondary);
            _txtPin = MakeTextBox(_state.Pin ?? "", 376, 34, 130, surfaceVar, textPrimary);

            _btnToggleServer = new Button();
            _btnToggleServer.Text = "Start Server";
            _btnToggleServer.Location = new Point(526, 32);
            _btnToggleServer.Size = new Size(162, 32);
            _btnToggleServer.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            _btnToggleServer.FlatStyle = FlatStyle.Flat;
            _btnToggleServer.FlatAppearance.BorderSize = 0;
            _btnToggleServer.BackColor = cyan;
            _btnToggleServer.ForeColor = bg;
            _btnToggleServer.Font = new Font("Segoe UI", 9.5f, FontWeightBold());
            _btnToggleServer.Click += delegate { ToggleServer(); };

            Label lblConnectHint = MakeLabel("Enter this URL in LootStream Android -> Add Media -> Connect PC Host:", 14, 78, textSecondary);
            _lblLanUrl = new Label();
            _lblLanUrl.Text = GetLanUrl();
            _lblLanUrl.Font = new Font("Consolas", 11.5f, FontWeightBold());
            _lblLanUrl.ForeColor = green;
            _lblLanUrl.Location = new Point(14, 100);
            _lblLanUrl.Size = new Size(470, 26);

            _btnCopyUrl = new Button();
            _btnCopyUrl.Text = "Copy URL";
            _btnCopyUrl.Location = new Point(494, 98);
            _btnCopyUrl.Size = new Size(96, 30);
            _btnCopyUrl.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            _btnCopyUrl.FlatStyle = FlatStyle.Flat;
            _btnCopyUrl.FlatAppearance.BorderColor = cyan;
            _btnCopyUrl.BackColor = surfaceVar;
            _btnCopyUrl.ForeColor = cyan;
            _btnCopyUrl.Click += delegate
            {
                try
                {
                    Clipboard.SetText(_lblLanUrl.Text);
                    AppendLog("Copied LAN URL to clipboard: " + _lblLanUrl.Text);
                }
                catch { }
            };

            _lblMediaCount = new Label();
            _lblMediaCount.Text = "0 items";
            _lblMediaCount.Font = new Font("Segoe UI", 9.5f, FontWeightBold());
            _lblMediaCount.ForeColor = purple;
            _lblMediaCount.TextAlign = ContentAlignment.MiddleRight;
            _lblMediaCount.Location = new Point(596, 100);
            _lblMediaCount.Size = new Size(92, 26);
            _lblMediaCount.Anchor = AnchorStyles.Top | AnchorStyles.Right;

            cfgCard.Controls.Add(lblName);
            cfgCard.Controls.Add(_txtServerName);
            cfgCard.Controls.Add(lblPort);
            cfgCard.Controls.Add(_txtPort);
            cfgCard.Controls.Add(lblPin);
            cfgCard.Controls.Add(_txtPin);
            cfgCard.Controls.Add(_btnToggleServer);
            cfgCard.Controls.Add(lblConnectHint);
            cfgCard.Controls.Add(_lblLanUrl);
            cfgCard.Controls.Add(_btnCopyUrl);
            cfgCard.Controls.Add(_lblMediaCount);

            // Shared Folders Card
            Panel foldersCard = new Panel();
            foldersCard.Location = new Point(18, 174);
            foldersCard.Size = new Size(706, 170);
            foldersCard.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
            foldersCard.BackColor = surface;

            Label lblFolders = MakeLabel("Shared Media Folders (Videos, Movies, Shows, Music):", 14, 12, textPrimary);
            lblFolders.Font = new Font("Segoe UI", 9.5f, FontWeightBold());

            _lstFolders = new ListBox();
            _lstFolders.Location = new Point(14, 38);
            _lstFolders.Size = new Size(540, 116);
            _lstFolders.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right | AnchorStyles.Bottom;
            _lstFolders.BackColor = surfaceVar;
            _lstFolders.ForeColor = textPrimary;
            _lstFolders.BorderStyle = BorderStyle.FixedSingle;
            RefreshFolderList();

            Button btnAddFolder = new Button();
            btnAddFolder.Text = "+ Add Folder";
            btnAddFolder.Location = new Point(568, 38);
            btnAddFolder.Size = new Size(122, 32);
            btnAddFolder.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnAddFolder.FlatStyle = FlatStyle.Flat;
            btnAddFolder.FlatAppearance.BorderSize = 0;
            btnAddFolder.BackColor = green;
            btnAddFolder.ForeColor = bg;
            btnAddFolder.Font = new Font("Segoe UI", 9f, FontWeightBold());
            btnAddFolder.Click += delegate { AddFolder(); };

            Button btnRemoveFolder = new Button();
            btnRemoveFolder.Text = "Remove Selected";
            btnRemoveFolder.Location = new Point(568, 78);
            btnRemoveFolder.Size = new Size(122, 32);
            btnRemoveFolder.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnRemoveFolder.FlatStyle = FlatStyle.Flat;
            btnRemoveFolder.FlatAppearance.BorderColor = Color.FromArgb(239, 68, 68);
            btnRemoveFolder.BackColor = surfaceVar;
            btnRemoveFolder.ForeColor = Color.FromArgb(248, 113, 113);
            btnRemoveFolder.Click += delegate { RemoveSelectedFolder(); };

            Button btnRescan = new Button();
            btnRescan.Text = "Rescan Library";
            btnRescan.Location = new Point(568, 118);
            btnRescan.Size = new Size(122, 32);
            btnRescan.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnRescan.FlatStyle = FlatStyle.Flat;
            btnRescan.FlatAppearance.BorderColor = purple;
            btnRescan.BackColor = surfaceVar;
            btnRescan.ForeColor = purple;
            btnRescan.Click += delegate { RescanLibrary(); };

            foldersCard.Controls.Add(lblFolders);
            foldersCard.Controls.Add(_lstFolders);
            foldersCard.Controls.Add(btnAddFolder);
            foldersCard.Controls.Add(btnRemoveFolder);
            foldersCard.Controls.Add(btnRescan);

            // Live Activity Log
            Label lblLog = MakeLabel("Server & Streaming Activity Log:", 18, 356, textSecondary);
            _txtLog = new TextBox();
            _txtLog.Location = new Point(18, 378);
            _txtLog.Size = new Size(706, 110);
            _txtLog.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right | AnchorStyles.Bottom;
            _txtLog.Multiline = true;
            _txtLog.ReadOnly = true;
            _txtLog.ScrollBars = ScrollBars.Vertical;
            _txtLog.BackColor = surface;
            _txtLog.ForeColor = textSecondary;
            _txtLog.BorderStyle = BorderStyle.FixedSingle;
            _txtLog.Font = new Font("Consolas", 8.5f);

            body.Controls.Add(cfgCard);
            body.Controls.Add(foldersCard);
            body.Controls.Add(lblLog);
            body.Controls.Add(_txtLog);

            this.Controls.Add(body);
            this.Controls.Add(header);

            this.FormClosing += delegate
            {
                StopServer();
                SaveState();
            };
        }

        private static FontStyle FontWeightBold()
        {
            return FontStyle.Bold;
        }

        private static Label MakeLabel(string text, int x, int y, Color color)
        {
            Label lbl = new Label();
            lbl.Text = text;
            lbl.Location = new Point(x, y);
            lbl.ForeColor = color;
            lbl.AutoSize = true;
            return lbl;
        }

        private static TextBox MakeTextBox(string text, int x, int y, int width, Color back, Color fore)
        {
            TextBox tb = new TextBox();
            tb.Text = text;
            tb.Location = new Point(x, y);
            tb.Width = width;
            tb.BackColor = back;
            tb.ForeColor = fore;
            tb.BorderStyle = BorderStyle.FixedSingle;
            return tb;
        }

        private void RefreshFolderList()
        {
            _lstFolders.Items.Clear();
            foreach (string folder in _state.Folders)
            {
                _lstFolders.Items.Add(folder);
            }
        }

        private void AddFolder()
        {
            using (FolderBrowserDialog dlg = new FolderBrowserDialog())
            {
                dlg.Description = "Select a folder containing movies, TV episodes, videos, or music";
                if (dlg.ShowDialog() == DialogResult.OK && !string.IsNullOrEmpty(dlg.SelectedPath))
                {
                    lock (_syncLock)
                    {
                        if (!_state.Folders.Contains(dlg.SelectedPath))
                        {
                            _state.Folders.Add(dlg.SelectedPath);
                        }
                    }
                    RefreshFolderList();
                    SaveState();
                    RescanLibrary();
                }
            }
        }

        private void RemoveSelectedFolder()
        {
            if (_lstFolders.SelectedIndex >= 0)
            {
                string folder = _lstFolders.SelectedItem.ToString();
                lock (_syncLock)
                {
                    _state.Folders.Remove(folder);
                }
                RefreshFolderList();
                SaveState();
                RescanLibrary();
            }
        }

        private void ToggleServer()
        {
            if (_isRunning)
            {
                StopServer();
            }
            else
            {
                StartServer();
            }
        }

        private void StartServer()
        {
            if (_isRunning) return;

            int parsedPort;
            if (!int.TryParse(_txtPort.Text.Trim(), out parsedPort) || parsedPort < 1024 || parsedPort > 65535)
            {
                parsedPort = 8765;
                _txtPort.Text = "8765";
            }

            lock (_syncLock)
            {
                _state.ServerName = string.IsNullOrEmpty(_txtServerName.Text.Trim())
                    ? Environment.MachineName + " LootStream"
                    : _txtServerName.Text.Trim();
                _state.Port = parsedPort;
                _state.Pin = _txtPin.Text.Trim();
                SaveState();
            }

            try
            {
                _listener = new HttpListener();
                try
                {
                    _listener.Prefixes.Add(string.Format("http://+:{0}/", parsedPort));
                    _listener.Start();
                }
                catch
                {
                    // Fallback without URL ACL admin reservation: bind all local IPv4 addresses + localhost
                    _listener = new HttpListener();
                    _listener.Prefixes.Add(string.Format("http://localhost:{0}/", parsedPort));
                    _listener.Prefixes.Add(string.Format("http://127.0.0.1:{0}/", parsedPort));
                    foreach (string ip in GetAllLocalIpv4Addresses())
                    {
                        _listener.Prefixes.Add(string.Format("http://{0}:{1}/", ip, parsedPort));
                    }
                    _listener.Start();
                }

                _isRunning = true;
                _listenerThread = new Thread(ListenLoop);
                _listenerThread.IsBackground = true;
                _listenerThread.Start();

                _lblStatusBadge.Text = "ONLINE";
                _lblStatusBadge.ForeColor = Color.FromArgb(0, 255, 135);
                _btnToggleServer.Text = "Stop Server";
                _btnToggleServer.BackColor = Color.FromArgb(239, 68, 68);
                _btnToggleServer.ForeColor = Color.White;
                _lblLanUrl.Text = GetLanUrl();
                AppendLog("Server started on " + _lblLanUrl.Text + " (" + _catalog.Count + " media items)");
            }
            catch (Exception ex)
            {
                _isRunning = false;
                _lblStatusBadge.Text = "ERROR";
                _lblStatusBadge.ForeColor = Color.FromArgb(248, 113, 113);
                AppendLog("Failed to start server: " + ex.Message);
            }
        }

        private void StopServer()
        {
            _isRunning = false;
            try
            {
                if (_listener != null)
                {
                    _listener.Stop();
                    _listener.Close();
                    _listener = null;
                }
            }
            catch { }

            if (!this.IsDisposed)
            {
                try
                {
                    this.BeginInvoke((Action)delegate
                    {
                        _lblStatusBadge.Text = "STOPPED";
                        _lblStatusBadge.ForeColor = Color.FromArgb(148, 163, 184);
                        _btnToggleServer.Text = "Start Server";
                        _btnToggleServer.BackColor = Color.FromArgb(0, 229, 255);
                        _btnToggleServer.ForeColor = Color.FromArgb(11, 15, 25);
                    });
                }
                catch { }
            }
            AppendLog("Server stopped.");
        }

        private void ListenLoop()
        {
            while (_isRunning && _listener != null)
            {
                try
                {
                    HttpListenerContext ctx = _listener.GetContext();
                    ThreadPool.QueueUserWorkItem(delegate { HandleRequest(ctx); });
                }
                catch
                {
                    if (!_isRunning) break;
                }
            }
        }

        private void HandleRequest(HttpListenerContext ctx)
        {
            HttpListenerRequest req = ctx.Request;
            HttpListenerResponse res = ctx.Response;
            string path = req.Url.AbsolutePath;
            string method = req.HttpMethod.ToUpperInvariant();

            try
            {
                if (method == "GET" && path == "/api/info")
                {
                    Dictionary<string, object> info = new Dictionary<string, object>();
                    lock (_syncLock)
                    {
                        info["serverId"] = _state.ServerId;
                        info["serverName"] = _state.ServerName;
                        info["version"] = "1.0.0";
                        info["hostPlatform"] = "Windows";
                        info["requiresPin"] = !string.IsNullOrEmpty(_state.Pin);
                        info["itemCount"] = _catalog.Count;
                    }
                    WriteJson(res, 200, info);
                    return;
                }

                if (method == "POST" && path == "/api/pair")
                {
                    string body = ReadBody(req);
                    Dictionary<string, object> payload = ParseJsonObject(body);
                    string submittedPin = payload.ContainsKey("pin") && payload["pin"] != null ? payload["pin"].ToString().Trim() : "";
                    string deviceName = payload.ContainsKey("deviceName") && payload["deviceName"] != null ? payload["deviceName"].ToString() : "Android";

                    Dictionary<string, object> result = new Dictionary<string, object>();
                    lock (_syncLock)
                    {
                        if (!string.IsNullOrEmpty(_state.Pin) && !string.Equals(_state.Pin, submittedPin, StringComparison.Ordinal))
                        {
                            AppendLog("Rejected pairing from " + deviceName + " (invalid PIN)");
                            WriteJson(res, 401, new Dictionary<string, object> { { "error", "Incorrect pairing PIN" } });
                            return;
                        }
                        string token = Guid.NewGuid().ToString("N");
                        if (!_state.ValidTokens.Contains(token)) _state.ValidTokens.Add(token);
                        SaveState();

                        result["token"] = token;
                        result["serverId"] = _state.ServerId;
                        result["serverName"] = _state.ServerName;
                        result["version"] = "1.0.0";
                        result["hostPlatform"] = "Windows";
                    }
                    AppendLog("Paired Android device: " + deviceName);
                    WriteJson(res, 200, result);
                    return;
                }

                if (method == "GET" && path == "/api/library")
                {
                    if (!IsAuthorized(req))
                    {
                        WriteJson(res, 401, new Dictionary<string, object> { { "error", "Unauthorized" } });
                        return;
                    }

                    List<Dictionary<string, object>> itemsOut = new List<Dictionary<string, object>>();
                    string serverId;
                    string serverName;
                    lock (_syncLock)
                    {
                        serverId = _state.ServerId;
                        serverName = _state.ServerName;
                        foreach (MediaEntry entry in _catalog)
                        {
                            ItemProgress prog = _state.Progress.ContainsKey(entry.Id) ? _state.Progress[entry.Id] : null;
                            Dictionary<string, object> d = new Dictionary<string, object>();
                            d["id"] = entry.Id;
                            d["title"] = entry.Title;
                            d["subtitle"] = entry.Subtitle;
                            d["overview"] = "Streamed directly from " + serverName;
                            d["mediaType"] = entry.MediaType;
                            d["streamUrl"] = "/api/stream/" + entry.Id;
                            d["posterUrl"] = "";
                            d["backdropUrl"] = "";
                            d["durationMs"] = prog != null ? prog.DurationMs : 0L;
                            d["playbackPositionMs"] = prog != null ? prog.PlaybackPositionMs : 0L;
                            d["releaseYear"] = entry.ReleaseYear;
                            d["genres"] = new string[] { entry.Subtitle };
                            d["codecBadge"] = entry.CodecBadge;
                            d["seriesName"] = entry.SeriesName;
                            d["seasonNumber"] = entry.SeasonNumber;
                            d["episodeNumber"] = entry.EpisodeNumber;
                            d["isFavorite"] = prog != null && prog.IsFavorite;
                            d["watchStatus"] = prog != null && !string.IsNullOrEmpty(prog.WatchStatus) ? prog.WatchStatus : "UNWATCHED";
                            d["playCount"] = prog != null ? prog.PlayCount : 0;
                            d["lastPlayedAt"] = prog != null ? prog.LastPlayedAt : 0L;
                            d["addedAt"] = entry.AddedAt;
                            d["fileSizeBytes"] = entry.FileSizeBytes;
                            itemsOut.Add(d);
                        }
                    }

                    Dictionary<string, object> libResp = new Dictionary<string, object>();
                    libResp["serverId"] = serverId;
                    libResp["serverName"] = serverName;
                    libResp["items"] = itemsOut;
                    WriteJson(res, 200, libResp);
                    AppendLog("Synced library (" + itemsOut.Count + " items)");
                    return;
                }

                if ((method == "GET" || method == "HEAD") && path.StartsWith("/api/stream/"))
                {
                    string itemId = Uri.UnescapeDataString(path.Substring("/api/stream/".Length));
                    MediaEntry entry = null;
                    lock (_syncLock)
                    {
                        if (_catalogById.ContainsKey(itemId)) entry = _catalogById[itemId];
                    }
                    if (entry == null || !File.Exists(entry.FilePath))
                    {
                        res.StatusCode = 404;
                        res.Close();
                        return;
                    }
                    ServeFileWithRange(req, res, entry.FilePath, method == "HEAD");
                    return;
                }

                if (method == "POST" && path.StartsWith("/api/items/"))
                {
                    if (!IsAuthorized(req))
                    {
                        WriteJson(res, 401, new Dictionary<string, object> { { "error", "Unauthorized" } });
                        return;
                    }
                    string rest = path.Substring("/api/items/".Length);
                    int slash = rest.IndexOf('/');
                    if (slash <= 0)
                    {
                        res.StatusCode = 404;
                        res.Close();
                        return;
                    }
                    string itemId = Uri.UnescapeDataString(rest.Substring(0, slash));
                    string action = rest.Substring(slash + 1).TrimEnd('/');
                    Dictionary<string, object> payload = ParseJsonObject(ReadBody(req));

                    lock (_syncLock)
                    {
                        ItemProgress prog;
                        if (!_state.Progress.TryGetValue(itemId, out prog))
                        {
                            prog = new ItemProgress();
                            _state.Progress[itemId] = prog;
                        }
                        if (action == "progress")
                        {
                            if (payload.ContainsKey("positionMs")) prog.PlaybackPositionMs = Convert.ToInt64(payload["positionMs"]);
                            if (payload.ContainsKey("durationMs")) prog.DurationMs = Convert.ToInt64(payload["durationMs"]);
                            if (payload.ContainsKey("watchStatus") && payload["watchStatus"] != null) prog.WatchStatus = payload["watchStatus"].ToString();
                            prog.LastPlayedAt = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                        }
                        else if (action == "favorite")
                        {
                            if (payload.ContainsKey("isFavorite")) prog.IsFavorite = Convert.ToBoolean(payload["isFavorite"]);
                        }
                        else if (action == "watched")
                        {
                            if (payload.ContainsKey("watchStatus") && payload["watchStatus"] != null) prog.WatchStatus = payload["watchStatus"].ToString();
                            if (prog.WatchStatus == "WATCHED") prog.PlayCount++;
                        }
                        SaveState();
                    }
                    WriteJson(res, 200, new Dictionary<string, object> { { "ok", true } });
                    return;
                }

                res.StatusCode = 404;
                res.Close();
            }
            catch (Exception ex)
            {
                try
                {
                    res.StatusCode = 500;
                    res.Close();
                }
                catch { }
                AppendLog("Request error (" + path + "): " + ex.Message);
            }
        }

        private bool IsAuthorized(HttpListenerRequest req)
        {
            lock (_syncLock)
            {
                if (string.IsNullOrEmpty(_state.Pin) && _state.ValidTokens.Count == 0)
                {
                    return true;
                }
                string auth = req.Headers["Authorization"];
                if (!string.IsNullOrEmpty(auth) && auth.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
                {
                    string token = auth.Substring(7).Trim();
                    if (_state.ValidTokens.Contains(token)) return true;
                }
                return string.IsNullOrEmpty(_state.Pin);
            }
        }

        private void ServeFileWithRange(HttpListenerRequest req, HttpListenerResponse res, string filePath, bool headOnly)
        {
            FileInfo info = new FileInfo(filePath);
            long totalLen = info.Length;
            string mime = GetMimeType(info.Extension);
            res.ContentType = mime;
            res.AddHeader("Accept-Ranges", "bytes");

            long start = 0;
            long end = totalLen - 1;
            string rangeHeader = req.Headers["Range"];
            bool isPartial = false;

            if (!string.IsNullOrEmpty(rangeHeader) && rangeHeader.StartsWith("bytes=", StringComparison.OrdinalIgnoreCase))
            {
                string spec = rangeHeader.Substring(6).Trim();
                int dash = spec.IndexOf('-');
                if (dash >= 0)
                {
                    string startStr = spec.Substring(0, dash).Trim();
                    string endStr = spec.Substring(dash + 1).Trim();
                    long parsedStart;
                    long parsedEnd;
                    if (long.TryParse(startStr, out parsedStart))
                    {
                        start = Math.Max(0, parsedStart);
                        end = long.TryParse(endStr, out parsedEnd) ? Math.Min(totalLen - 1, parsedEnd) : totalLen - 1;
                        isPartial = true;
                    }
                    else if (long.TryParse(endStr, out parsedEnd))
                    {
                        start = Math.Max(0, totalLen - parsedEnd);
                        end = totalLen - 1;
                        isPartial = true;
                    }
                }
            }

            if (start > end || start >= totalLen)
            {
                res.StatusCode = 416;
                res.AddHeader("Content-Range", string.Format("bytes */{0}", totalLen));
                res.Close();
                return;
            }

            long contentLen = (end - start) + 1;
            res.StatusCode = isPartial ? 206 : 200;
            if (isPartial)
            {
                res.AddHeader("Content-Range", string.Format("bytes {0}-{1}/{2}", start, end, totalLen));
            }
            res.ContentLength64 = contentLen;

            if (headOnly)
            {
                res.Close();
                return;
            }

            byte[] buffer = new byte[64 * 1024];
            using (FileStream fs = new FileStream(filePath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite))
            {
                fs.Seek(start, SeekOrigin.Begin);
                long remaining = contentLen;
                while (remaining > 0 && _isRunning)
                {
                    int toRead = (int)Math.Min(buffer.Length, remaining);
                    int read = fs.Read(buffer, 0, toRead);
                    if (read <= 0) break;
                    res.OutputStream.Write(buffer, 0, read);
                    remaining -= read;
                }
            }
            res.OutputStream.Flush();
            res.Close();
        }

        private void RescanLibrary()
        {
            List<string> foldersSnapshot;
            lock (_syncLock)
            {
                foldersSnapshot = new List<string>(_state.Folders);
            }

            List<MediaEntry> scanned = new List<MediaEntry>();
            Dictionary<string, MediaEntry> byId = new Dictionary<string, MediaEntry>();

            foreach (string folder in foldersSnapshot)
            {
                if (string.IsNullOrEmpty(folder) || !Directory.Exists(folder)) continue;
                try
                {
                    string[] files = Directory.GetFiles(folder, "*.*", SearchOption.AllDirectories);
                    foreach (string file in files)
                    {
                        string ext = Path.GetExtension(file);
                        bool isVideo = VideoExtensions.Contains(ext);
                        bool isAudio = AudioExtensions.Contains(ext);
                        if (!isVideo && !isAudio) continue;

                        FileInfo fi = new FileInfo(file);
                        string id = ComputeShortHash(file);
                        if (byId.ContainsKey(id)) continue;

                        string rawName = Path.GetFileNameWithoutExtension(file);
                        int? year = null;
                        Match yearMatch = Regex.Match(rawName, @"\((\d{4})\)");
                        if (yearMatch.Success)
                        {
                            int y;
                            if (int.TryParse(yearMatch.Groups[1].Value, out y)) year = y;
                        }

                        string seriesName = null;
                        int? season = null;
                        int? episode = null;
                        string mediaType = isAudio ? "AUDIO" : "VIDEO";
                        Match epMatch = Regex.Match(rawName, @"^(.*?)[.\s_-]+[Ss](\d{1,2})[Ee](\d{1,2})");
                        if (isVideo && epMatch.Success)
                        {
                            mediaType = "EPISODE";
                            seriesName = epMatch.Groups[1].Value.Replace('.', ' ').Trim();
                            season = int.Parse(epMatch.Groups[2].Value);
                            episode = int.Parse(epMatch.Groups[3].Value);
                        }
                        else if (isVideo && year != null)
                        {
                            mediaType = "MOVIE";
                        }

                        string cleanTitle = Regex.Replace(rawName, @"[._]+", " ").Trim();
                        string parentName = fi.Directory != null ? fi.Directory.Name : "Windows PC";

                        MediaEntry entry = new MediaEntry
                        {
                            Id = id,
                            FilePath = file,
                            Title = cleanTitle,
                            Subtitle = !string.IsNullOrEmpty(seriesName) ? seriesName : parentName,
                            MediaType = mediaType,
                            CodecBadge = ext.TrimStart('.').ToUpperInvariant(),
                            ReleaseYear = year,
                            SeriesName = seriesName,
                            SeasonNumber = season,
                            EpisodeNumber = episode,
                            FileSizeBytes = fi.Length,
                            AddedAt = new DateTimeOffset(fi.LastWriteTimeUtc).ToUnixTimeMilliseconds()
                        };
                        scanned.Add(entry);
                        byId[id] = entry;
                    }
                }
                catch (Exception ex)
                {
                    AppendLog("Scan warning (" + folder + "): " + ex.Message);
                }
            }

            lock (_syncLock)
            {
                _catalog = scanned;
                _catalogById = byId;
            }
            _lblMediaCount.Text = scanned.Count + " items";
            AppendLog("Library scan complete: " + scanned.Count + " playable files found.");
        }

        private static string ComputeShortHash(string input)
        {
            using (SHA256 sha = SHA256.Create())
            {
                byte[] bytes = sha.ComputeHash(Encoding.UTF8.GetBytes(input.ToLowerInvariant()));
                StringBuilder sb = new StringBuilder(16);
                for (int i = 0; i < 8; i++) sb.Append(bytes[i].ToString("x2"));
                return sb.ToString();
            }
        }

        private static string GetMimeType(string ext)
        {
            switch (ext.ToLowerInvariant())
            {
                case ".mp4":
                case ".m4v": return "video/mp4";
                case ".mkv": return "video/x-matroska";
                case ".webm": return "video/webm";
                case ".mov": return "video/quicktime";
                case ".avi": return "video/x-msvideo";
                case ".ts": return "video/mp2t";
                case ".mp3": return "audio/mpeg";
                case ".flac": return "audio/flac";
                case ".wav": return "audio/wav";
                case ".m4a":
                case ".aac": return "audio/mp4";
                case ".ogg": return "audio/ogg";
                default: return "application/octet-stream";
            }
        }

        private string GetLanUrl()
        {
            List<string> ips = GetAllLocalIpv4Addresses();
            string ip = ips.Count > 0 ? ips[0] : "127.0.0.1";
            return string.Format("http://{0}:{1}", ip, _state.Port);
        }

        private static List<string> GetAllLocalIpv4Addresses()
        {
            List<string> ips = new List<string>();
            try
            {
                foreach (NetworkInterface ni in NetworkInterface.GetAllNetworkInterfaces())
                {
                    if (ni.OperationalStatus != OperationalStatus.Up) continue;
                    if (ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
                    foreach (UnicastIPAddressInformation ua in ni.GetIPProperties().UnicastAddresses)
                    {
                        if (ua.Address.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(ua.Address))
                        {
                            ips.Add(ua.Address.ToString());
                        }
                    }
                }
            }
            catch { }
            return ips;
        }

        private static string ReadBody(HttpListenerRequest req)
        {
            using (StreamReader reader = new StreamReader(req.InputStream, req.ContentEncoding ?? Encoding.UTF8))
            {
                return reader.ReadToEnd();
            }
        }

        private Dictionary<string, object> ParseJsonObject(string json)
        {
            if (string.IsNullOrEmpty(json)) return new Dictionary<string, object>();
            try
            {
                Dictionary<string, object> obj = _json.Deserialize<Dictionary<string, object>>(json);
                return obj ?? new Dictionary<string, object>();
            }
            catch
            {
                return new Dictionary<string, object>();
            }
        }

        private void WriteJson(HttpListenerResponse res, int statusCode, object payload)
        {
            string raw = _json.Serialize(payload);
            byte[] bytes = Encoding.UTF8.GetBytes(raw);
            res.StatusCode = statusCode;
            res.ContentType = "application/json; charset=utf-8";
            res.ContentLength64 = bytes.Length;
            res.OutputStream.Write(bytes, 0, bytes.Length);
            res.OutputStream.Flush();
            res.Close();
        }

        private HostState LoadState()
        {
            try
            {
                if (File.Exists(_stateFilePath))
                {
                    string raw = File.ReadAllText(_stateFilePath, Encoding.UTF8);
                    HostState loaded = _json.Deserialize<HostState>(raw);
                    if (loaded != null) return loaded;
                }
            }
            catch { }
            return new HostState();
        }

        private void SaveState()
        {
            try
            {
                string raw = _json.Serialize(_state);
                File.WriteAllText(_stateFilePath, raw, Encoding.UTF8);
            }
            catch { }
        }

        private void AppendLog(string message)
        {
            string line = string.Format("[{0:HH:mm:ss}] {1}{2}", DateTime.Now, message, Environment.NewLine);
            if (this.IsDisposed || !this.IsHandleCreated) return;
            try
            {
                this.BeginInvoke((Action)delegate
                {
                    if (_txtLog != null && !_txtLog.IsDisposed)
                    {
                        _txtLog.AppendText(line);
                    }
                });
            }
            catch { }
        }

        [STAThread]
        public static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm());
        }
    }
}
