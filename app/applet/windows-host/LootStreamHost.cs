using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
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
    public static class Program
    {
        [STAThread]
        public static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm());
        }
    }

    public class HostState
    {
        public string ServerId { get; set; }
        public string ServerName { get; set; }
        public int Port { get; set; }
        public string PairingPin { get; set; }
        public List<string> Folders { get; set; }
        public List<string> IssuedTokens { get; set; }
        public Dictionary<string, ItemProgress> Progress { get; set; }

        public HostState()
        {
            ServerId = "ls_" + Guid.NewGuid().ToString("N").Substring(0, 12);
            ServerName = Environment.MachineName + " - LootStream";
            Port = 8765;
            PairingPin = new Random().Next(1000, 9999).ToString();
            Folders = new List<string>();
            IssuedTokens = new List<string>();
            Progress = new Dictionary<string, ItemProgress>();
        }
    }

    public class ItemProgress
    {
        public long PlaybackPositionMs { get; set; }
        public long DurationMs { get; set; }
        public bool IsFavorite { get; set; }
        public string WatchStatus { get; set; }
        public int PlayCount { get; set; }
        public long LastPlayedAt { get; set; }
    }

    public class ScannedMediaItem
    {
        public string Id { get; set; }
        public string FilePath { get; set; }
        public string Title { get; set; }
        public string Subtitle { get; set; }
        public string Overview { get; set; }
        public string MediaType { get; set; }
        public string CodecBadge { get; set; }
        public string SeriesName { get; set; }
        public int? SeasonNumber { get; set; }
        public int? EpisodeNumber { get; set; }
        public long FileSizeBytes { get; set; }
        public long AddedAt { get; set; }
        public string MimeType { get; set; }
    }

    public class MainForm : Form
    {
        private static readonly Color BgDark = Color.FromArgb(11, 15, 25);
        private static readonly Color SurfaceDark = Color.FromArgb(19, 26, 42);
        private static readonly Color SurfaceAlt = Color.FromArgb(27, 36, 56);
        private static readonly Color AccentCyan = Color.FromArgb(0, 229, 255);
        private static readonly Color AccentGreen = Color.FromArgb(0, 255, 135);
        private static readonly Color AccentPurple = Color.FromArgb(170, 92, 195);
        private static readonly Color TextPrimary = Color.FromArgb(241, 245, 249);
        private static readonly Color TextMuted = Color.FromArgb(148, 163, 184);

        private static readonly HashSet<string> VideoExtensions = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            ".mp4", ".mkv", ".webm", ".mov", ".m4v", ".avi", ".ts", ".m2ts"
        };

        private static readonly HashSet<string> AudioExtensions = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            ".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg", ".opus"
        };

        private static readonly Regex EpisodeRegex = new Regex(@"[Ss](\d{1,2})[Ee](\d{1,3})", RegexOptions.Compiled);

        private readonly string _stateDir;
        private readonly string _stateFile;
        private readonly JavaScriptSerializer _json = new JavaScriptSerializer { MaxJsonLength = 64 * 1024 * 1024 };
        private readonly object _lock = new object();

        private HostState _state;
        private List<ScannedMediaItem> _catalog = new List<ScannedMediaItem>();
        private Dictionary<string, ScannedMediaItem> _catalogById = new Dictionary<string, ScannedMediaItem>();

        private HttpListener _listener;
        private Thread _acceptThread;
        private volatile bool _isRunning;

        // UI Controls
        private TextBox _txtServerName;
        private NumericUpDown _numPort;
        private TextBox _txtPin;
        private Label _lblStatusBadge;
        private Label _lblLanUrl;
        private Label _lblMediaCount;
        private ListBox _listFolders;
        private TextBox _txtLog;
        private Button _btnStartStop;
        private Button _btnCopyUrl;
        private Button _btnAuthorizePort;
        private Button _btnAddFolder;
        private Button _btnRemoveFolder;
        private Button _btnRescan;

        public MainForm()
        {
            _stateDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "LootStreamHost");
            _stateFile = Path.Combine(_stateDir, "state.json");
            LoadState();
            InitializeUi();
            RescanLibrary();
        }

        private void InitializeUi()
        {
            Text = "LootStream PC Host — Personal Media Server";
            Size = new Size(780, 650);
            MinimumSize = new Size(680, 560);
            BackColor = BgDark;
            ForeColor = TextPrimary;
            Font = new Font("Segoe UI", 9.5f);
            StartPosition = FormStartPosition.CenterScreen;

            var root = new TableLayoutPanel
            {
                Dock = DockStyle.Fill,
                Padding = new Padding(16),
                ColumnCount = 1,
                RowCount = 4,
                BackColor = BgDark
            };
            root.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            root.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            root.RowStyles.Add(new RowStyle(SizeType.Percent, 48f));
            root.RowStyles.Add(new RowStyle(SizeType.Percent, 52f));

            // 1. Header Card
            var headerPanel = CreateCardPanel();
            headerPanel.Padding = new Padding(14);
            var headerLayout = new TableLayoutPanel
            {
                Dock = DockStyle.Fill,
                ColumnCount = 2,
                AutoSize = true
            };
            headerLayout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 70f));
            headerLayout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 30f));

            var titleStack = new FlowLayoutPanel { FlowDirection = FlowDirection.TopDown, AutoSize = true, WrapContents = false };
            var lblTitle = new Label
            {
                Text = "LootStream PC Host",
                Font = new Font("Segoe UI", 16f, FontWeightBold()),
                ForeColor = AccentCyan,
                AutoSize = true
            };
            var lblSub = new Label
            {
                Text = "Standalone Windows media host for LootStream Android (HTTP Range Streaming)",
                Font = new Font("Segoe UI", 9f),
                ForeColor = TextMuted,
                AutoSize = true
            };
            _lblLanUrl = new Label
            {
                Text = "LAN URL: " + GetPrimaryLanUrl(),
                Font = new Font("Consolas", 10f, FontStyle.Bold),
                ForeColor = AccentGreen,
                AutoSize = true,
                Margin = new Padding(0, 6, 0, 0)
            };
            titleStack.Controls.Add(lblTitle);
            titleStack.Controls.Add(lblSub);
            titleStack.Controls.Add(_lblLanUrl);

            var statusStack = new FlowLayoutPanel
            {
                FlowDirection = FlowDirection.TopDown,
                Dock = DockStyle.Fill,
                WrapContents = false
            };
            _lblStatusBadge = new Label
            {
                Text = "● STOPPED",
                Font = new Font("Segoe UI", 10f, FontStyle.Bold),
                ForeColor = Color.FromArgb(248, 113, 113),
                AutoSize = true
            };
            _lblMediaCount = new Label
            {
                Text = "0 media files indexed",
                Font = new Font("Segoe UI", 9f),
                ForeColor = TextMuted,
                AutoSize = true,
                Margin = new Padding(0, 4, 0, 6)
            };
            _btnStartStop = CreateButton("Start Host", AccentGreen, Color.Black);
            _btnStartStop.Width = 175;
            _btnStartStop.Height = 36;
            _btnStartStop.Click += (s, e) => ToggleServer();

            statusStack.Controls.Add(_lblStatusBadge);
            statusStack.Controls.Add(_lblMediaCount);
            statusStack.Controls.Add(_btnStartStop);

            headerLayout.Controls.Add(titleStack, 0, 0);
            headerLayout.Controls.Add(statusStack, 1, 0);
            headerPanel.Controls.Add(headerLayout);

            // 2. Settings Card
            var settingsPanel = CreateCardPanel();
            settingsPanel.Padding = new Padding(14);
            settingsPanel.Margin = new Padding(0, 10, 0, 10);

            var settingsFlow = new FlowLayoutPanel
            {
                Dock = DockStyle.Fill,
                AutoSize = true,
                WrapContents = true
            };

            settingsFlow.Controls.Add(CreateFieldGroup("Host Name", out _txtServerName, _state.ServerName, 210));

            var portGroup = new FlowLayoutPanel { FlowDirection = FlowDirection.TopDown, AutoSize = true, Margin = new Padding(0, 0, 14, 0) };
            portGroup.Controls.Add(new Label { Text = "Port", ForeColor = TextMuted, Font = new Font("Segoe UI", 8.5f), AutoSize = true });
            _numPort = new NumericUpDown
            {
                Minimum = 1024,
                Maximum = 65535,
                Value = Math.Max(1024, Math.Min(65535, _state.Port)),
                Width = 90,
                BackColor = SurfaceAlt,
                ForeColor = TextPrimary
            };
            _numPort.ValueChanged += (s, e) =>
            {
                _state.Port = (int)_numPort.Value;
                _lblLanUrl.Text = "LAN URL: " + GetPrimaryLanUrl();
                SaveState();
            };
            portGroup.Controls.Add(_numPort);
            settingsFlow.Controls.Add(portGroup);

            settingsFlow.Controls.Add(CreateFieldGroup("Pairing PIN (blank = none)", out _txtPin, _state.PairingPin, 145));

            _txtServerName.TextChanged += (s, e) => { _state.ServerName = _txtServerName.Text.Trim(); SaveState(); };
            _txtPin.TextChanged += (s, e) => { _state.PairingPin = _txtPin.Text.Trim(); SaveState(); };

            _btnCopyUrl = CreateButton("Copy LAN URL", SurfaceAlt, AccentCyan);
            _btnCopyUrl.Margin = new Padding(6, 16, 6, 0);
            _btnCopyUrl.Click += (s, e) =>
            {
                var url = GetPrimaryLanUrl();
                Clipboard.SetText(url);
                AppendLog("Copied LAN URL to clipboard: " + url);
            };
            settingsFlow.Controls.Add(_btnCopyUrl);

            _btnAuthorizePort = CreateButton("Authorize Port / Firewall", SurfaceAlt, AccentPurple);
            _btnAuthorizePort.Margin = new Padding(0, 16, 0, 0);
            _btnAuthorizePort.Click += (s, e) => AuthorizeWindowsPortAndFirewall();
            settingsFlow.Controls.Add(_btnAuthorizePort);

            settingsPanel.Controls.Add(settingsFlow);

            // 3. Shared Folders Card
            var foldersCard = CreateCardPanel();
            foldersCard.Padding = new Padding(12);
            foldersCard.Margin = new Padding(0, 0, 0, 10);

            var foldersLayout = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 2, RowCount = 2 };
            foldersLayout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 75f));
            foldersLayout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 25f));
            foldersLayout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            foldersLayout.RowStyles.Add(new RowStyle(SizeType.Percent, 100f));

            foldersLayout.Controls.Add(new Label
            {
                Text = "Shared Media Folders (Movies, Series, Videos, Music)",
                ForeColor = AccentCyan,
                Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
                AutoSize = true,
                Margin = new Padding(0, 0, 0, 6)
            }, 0, 0);

            _listFolders = new ListBox
            {
                Dock = DockStyle.Fill,
                BackColor = SurfaceAlt,
                ForeColor = TextPrimary,
                BorderStyle = BorderStyle.FixedSingle
            };
            foreach (var f in _state.Folders) _listFolders.Items.Add(f);
            foldersLayout.Controls.Add(_listFolders, 0, 1);

            var folderBtnPanel = new FlowLayoutPanel
            {
                FlowDirection = FlowDirection.TopDown,
                Dock = DockStyle.Fill,
                Padding = new Padding(8, 0, 0, 0),
                WrapContents = false
            };
            _btnAddFolder = CreateButton("+ Add Folder", AccentCyan, Color.Black);
            _btnAddFolder.Width = 150;
            _btnAddFolder.Click += (s, e) => AddSharedFolder();

            _btnRemoveFolder = CreateButton("Remove Selected", SurfaceAlt, TextPrimary);
            _btnRemoveFolder.Width = 150;
            _btnRemoveFolder.Margin = new Padding(0, 6, 0, 6);
            _btnRemoveFolder.Click += (s, e) => RemoveSelectedFolder();

            _btnRescan = CreateButton("Rescan Library", SurfaceAlt, AccentGreen);
            _btnRescan.Width = 150;
            _btnRescan.Click += (s, e) => RescanLibrary();

            folderBtnPanel.Controls.Add(_btnAddFolder);
            folderBtnPanel.Controls.Add(_btnRemoveFolder);
            folderBtnPanel.Controls.Add(_btnRescan);
            foldersLayout.Controls.Add(folderBtnPanel, 1, 1);
            foldersCard.Controls.Add(foldersLayout);

            // 4. Activity Log Card
            var logCard = CreateCardPanel();
            logCard.Padding = new Padding(12);
            var logLayout = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 1, RowCount = 2 };
            logLayout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            logLayout.RowStyles.Add(new RowStyle(SizeType.Percent, 100f));
            logLayout.Controls.Add(new Label
            {
                Text = "Live Host Activity Log",
                ForeColor = TextMuted,
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                AutoSize = true,
                Margin = new Padding(0, 0, 0, 6)
            }, 0, 0);

            _txtLog = new TextBox
            {
                Dock = DockStyle.Fill,
                Multiline = true,
                ReadOnly = true,
                ScrollBars = ScrollBars.Vertical,
                BackColor = Color.FromArgb(9, 13, 22),
                ForeColor = Color.FromArgb(186, 230, 253),
                Font = new Font("Consolas", 9f),
                BorderStyle = BorderStyle.FixedSingle
            };
            logLayout.Controls.Add(_txtLog, 0, 1);
            logCard.Controls.Add(logLayout);

            root.Controls.Add(headerPanel, 0, 0);
            root.Controls.Add(settingsPanel, 0, 1);
            root.Controls.Add(foldersCard, 0, 2);
            root.Controls.Add(logCard, 0, 3);
            Controls.Add(root);

            FormClosing += (s, e) => StopServer();
            AppendLog("LootStream PC Host ready. Add folders and click 'Start Host'.");
        }

        private static FontStyle FontWeightBold() { return FontStyle.Bold; }

        private Panel CreateCardPanel()
        {
            return new Panel
            {
                Dock = DockStyle.Fill,
                BackColor = SurfaceDark
            };
        }

        private FlowLayoutPanel CreateFieldGroup(string label, out TextBox textBox, string initial, int width)
        {
            var group = new FlowLayoutPanel { FlowDirection = FlowDirection.TopDown, AutoSize = true, Margin = new Padding(0, 0, 14, 0) };
            group.Controls.Add(new Label { Text = label, ForeColor = TextMuted, Font = new Font("Segoe UI", 8.5f), AutoSize = true });
            textBox = new TextBox
            {
                Text = initial ?? "",
                Width = width,
                BackColor = SurfaceAlt,
                ForeColor = TextPrimary,
                BorderStyle = BorderStyle.FixedSingle
            };
            group.Controls.Add(textBox);
            return group;
        }

        private Button CreateButton(string text, Color bg, Color fg)
        {
            return new Button
            {
                Text = text,
                BackColor = bg,
                ForeColor = fg,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                AutoSize = false,
                Height = 32,
                Width = 140,
                Cursor = Cursors.Hand
            };
        }

        private void AddSharedFolder()
        {
            using (var dlg = new FolderBrowserDialog { Description = "Select a media folder to share with LootStream" })
            {
                if (dlg.ShowDialog() == DialogResult.OK && !string.IsNullOrWhiteSpace(dlg.SelectedPath))
                {
                    if (!_state.Folders.Contains(dlg.SelectedPath, StringComparer.OrdinalIgnoreCase))
                    {
                        _state.Folders.Add(dlg.SelectedPath);
                        _listFolders.Items.Add(dlg.SelectedPath);
                        SaveState();
                        RescanLibrary();
                    }
                }
            }
        }

        private void RemoveSelectedFolder()
        {
            if (_listFolders.SelectedItem is string path)
            {
                _state.Folders.Remove(path);
                _listFolders.Items.Remove(path);
                SaveState();
                RescanLibrary();
            }
        }

        private void RescanLibrary()
        {
            var items = new List<ScannedMediaItem>();
            var byId = new Dictionary<string, ScannedMediaItem>();

            foreach (var rootFolder in _state.Folders)
            {
                if (!Directory.Exists(rootFolder)) continue;
                try
                {
                    foreach (var file in Directory.EnumerateFiles(rootFolder, "*.*", SearchOption.AllDirectories))
                    {
                        var ext = Path.GetExtension(file);
                        bool isVideo = VideoExtensions.Contains(ext);
                        bool isAudio = AudioExtensions.Contains(ext);
                        if (!isVideo && !isAudio) continue;

                        var fi = new FileInfo(file);
                        var id = ComputeStableId(file);
                        var rawName = Path.GetFileNameWithoutExtension(file);
                        var cleanTitle = rawName.Replace('.', ' ').Replace('_', ' ').Trim();
                        var parentDir = fi.Directory != null ? fi.Directory.Name : "PC";
                        var epMatch = EpisodeRegex.Match(rawName);

                        string mediaType;
                        string seriesName = null;
                        int? seasonNum = null;
                        int? episodeNum = null;

                        if (isAudio)
                        {
                            mediaType = "AUDIO";
                        }
                        else if (epMatch.Success)
                        {
                            mediaType = "EPISODE";
                            seasonNum = int.Parse(epMatch.Groups[1].Value);
                            episodeNum = int.Parse(epMatch.Groups[2].Value);
                            seriesName = parentDir;
                        }
                        else if (file.IndexOf("movie", StringComparison.OrdinalIgnoreCase) >= 0 || fi.Length > 350L * 1024L * 1024L)
                        {
                            mediaType = "MOVIE";
                        }
                        else
                        {
                            mediaType = "VIDEO";
                        }

                        var item = new ScannedMediaItem
                        {
                            Id = id,
                            FilePath = file,
                            Title = cleanTitle,
                            Subtitle = parentDir + " • " + ext.TrimStart('.').ToUpperInvariant(),
                            Overview = "Streamed from " + _state.ServerName + " (" + file + ")",
                            MediaType = mediaType,
                            CodecBadge = ext.TrimStart('.').ToUpperInvariant(),
                            SeriesName = seriesName,
                            SeasonNumber = seasonNum,
                            EpisodeNumber = episodeNum,
                            FileSizeBytes = fi.Length,
                            AddedAt = new DateTimeOffset(fi.LastWriteTimeUtc).ToUnixTimeMilliseconds(),
                            MimeType = GetMimeType(ext)
                        };
                        items.Add(item);
                        byId[id] = item;
                    }
                }
                catch (Exception ex)
                {
                    AppendLog("Scan warning for " + rootFolder + ": " + ex.Message);
                }
            }

            lock (_lock)
            {
                _catalog = items;
                _catalogById = byId;
            }
            _lblMediaCount.Text = items.Count + " media files indexed";
            AppendLog("Indexed " + items.Count + " media files across " + _state.Folders.Count + " folder(s).");
        }

        private void ToggleServer()
        {
            if (_isRunning) StopServer();
            else StartServer();
        }

        private void StartServer()
        {
            if (_isRunning) return;
            int port = _state.Port;
            var started = false;
            var prefixes = new List<string> { "http://+:" + port + "/" };

            try
            {
                _listener = new HttpListener();
                _listener.Prefixes.Add(prefixes[0]);
                _listener.Start();
                started = true;
            }
            catch (HttpListenerException)
            {
                // Fallback without admin URL ACL: bind local LAN IP + localhost
                try
                {
                    _listener?.Close();
                    _listener = new HttpListener();
                    foreach (var ip in GetLanIpv4Addresses())
                    {
                        _listener.Prefixes.Add("http://" + ip + ":" + port + "/");
                    }
                    _listener.Prefixes.Add("http://localhost:" + port + "/");
                    _listener.Prefixes.Add("http://127.0.0.1:" + port + "/");
                    _listener.Start();
                    started = true;
                    AppendLog("Bound directly to LAN IP & localhost (click 'Authorize Port / Firewall' if external devices cannot connect).");
                }
                catch (Exception ex2)
                {
                    AppendLog("Failed to start host listener: " + ex2.Message);
                }
            }

            if (!started) return;

            _isRunning = true;
            _numPort.Enabled = false;
            _btnStartStop.Text = "Stop Host";
            _btnStartStop.BackColor = Color.FromArgb(239, 68, 68);
            _btnStartStop.ForeColor = Color.White;
            _lblStatusBadge.Text = "● ONLINE (PORT " + port + ")";
            _lblStatusBadge.ForeColor = AccentGreen;
            _lblLanUrl.Text = "LAN URL: " + GetPrimaryLanUrl();

            _acceptThread = new Thread(AcceptLoop) { IsBackground = true };
            _acceptThread.Start();

            AppendLog("Host started! Connect LootStream Android to " + GetPrimaryLanUrl() +
                (string.IsNullOrEmpty(_state.PairingPin) ? " (No PIN)" : " (PIN: " + _state.PairingPin + ")"));
        }

        private void StopServer()
        {
            if (!_isRunning) return;
            _isRunning = false;
            try { _listener?.Stop(); } catch { }
            try { _listener?.Close(); } catch { }
            _listener = null;

            if (!IsDisposed && InvokeRequired)
            {
                BeginInvoke((Action)UpdateStoppedUi);
            }
            else if (!IsDisposed)
            {
                UpdateStoppedUi();
            }
        }

        private void UpdateStoppedUi()
        {
            _numPort.Enabled = true;
            _btnStartStop.Text = "Start Host";
            _btnStartStop.BackColor = AccentGreen;
            _btnStartStop.ForeColor = Color.Black;
            _lblStatusBadge.Text = "● STOPPED";
            _lblStatusBadge.ForeColor = Color.FromArgb(248, 113, 113);
            AppendLog("Host stopped.");
        }

        private void AcceptLoop()
        {
            while (_isRunning && _listener != null)
            {
                try
                {
                    var ctx = _listener.GetContext();
                    ThreadPool.QueueUserWorkItem(_ => HandleRequest(ctx));
                }
                catch
                {
                    if (!_isRunning) break;
                }
            }
        }

        private void HandleRequest(HttpListenerContext ctx)
        {
            var req = ctx.Request;
            var res = ctx.Response;
            try
            {
                res.AddHeader("Access-Control-Allow-Origin", "*");
                var path = (req.Url.AbsolutePath ?? "/").TrimEnd('/');
                if (string.IsNullOrEmpty(path)) path = "/";

                if (req.HttpMethod == "GET" && path == "/api/info")
                {
                    int count;
                    lock (_lock) count = _catalog.Count;
                    WriteJson(res, 200, new
                    {
                        serverId = _state.ServerId,
                        serverName = _state.ServerName,
                        version = "1.0.0",
                        requiresPin = !string.IsNullOrEmpty(_state.PairingPin),
                        hostPlatform = "Windows",
                        itemCount = count
                    });
                    return;
                }

                if (req.HttpMethod == "POST" && path == "/api/pair")
                {
                    var body = ReadBody(req);
                    var data = string.IsNullOrWhiteSpace(body)
                        ? new Dictionary<string, object>()
                        : _json.Deserialize<Dictionary<string, object>>(body);
                    var pin = data != null && data.ContainsKey("pin") ? Convert.ToString(data["pin"]) ?? "" : "";
                    var device = data != null && data.ContainsKey("deviceName") ? Convert.ToString(data["deviceName"]) ?? "Android" : "Android";

                    if (!string.IsNullOrEmpty(_state.PairingPin) && !string.Equals(pin.Trim(), _state.PairingPin.Trim(), StringComparison.Ordinal))
                    {
                        WriteJson(res, 401, new { error = "Invalid pairing PIN" });
                        AppendLog("Rejected pairing attempt from " + device + " (invalid PIN).");
                        return;
                    }

                    var token = Guid.NewGuid().ToString("N") + Guid.NewGuid().ToString("N").Substring(0, 8);
                    lock (_lock)
                    {
                        if (_state.IssuedTokens.Count > 24) _state.IssuedTokens.RemoveAt(0);
                        _state.IssuedTokens.Add(token);
                        SaveState();
                    }
                    AppendLog("Paired Android device: " + device);
                    WriteJson(res, 200, new
                    {
                        token = token,
                        serverId = _state.ServerId,
                        serverName = _state.ServerName,
                        version = "1.0.0",
                        hostPlatform = "Windows"
                    });
                    return;
                }

                // All remaining routes require valid token when PIN was configured
                if (!IsAuthorized(req))
                {
                    WriteJson(res, 401, new { error = "Unauthorized" });
                    return;
                }

                if (req.HttpMethod == "GET" && path == "/api/library")
                {
                    var token = ExtractToken(req) ?? "";
                    List<object> payloadItems;
                    lock (_lock)
                    {
                        payloadItems = _catalog.Select(item =>
                        {
                            ItemProgress prog;
                            _state.Progress.TryGetValue(item.Id, out prog);
                            return (object)new
                            {
                                id = item.Id,
                                title = item.Title,
                                subtitle = item.Subtitle,
                                overview = item.Overview,
                                mediaType = item.MediaType,
                                streamUrl = "/api/stream/" + item.Id + (string.IsNullOrEmpty(token) ? "" : "?token=" + Uri.EscapeDataString(token)),
                                posterUrl = "",
                                backdropUrl = "",
                                durationMs = prog != null ? prog.DurationMs : 0L,
                                playbackPositionMs = prog != null ? prog.PlaybackPositionMs : 0L,
                                codecBadge = item.CodecBadge,
                                seriesName = item.SeriesName,
                                seasonNumber = item.SeasonNumber,
                                episodeNumber = item.EpisodeNumber,
                                genres = new[] { "Windows PC Host" },
                                isFavorite = prog != null && prog.IsFavorite,
                                watchStatus = prog != null && !string.IsNullOrEmpty(prog.WatchStatus) ? prog.WatchStatus : "UNWATCHED",
                                playCount = prog != null ? prog.PlayCount : 0,
                                lastPlayedAt = prog != null ? prog.LastPlayedAt : 0L,
                                addedAt = item.AddedAt,
                                fileSizeBytes = item.FileSizeBytes
                            };
                        }).ToList();
                    }
                    AppendLog("Library synced (" + payloadItems.Count + " items).");
                    WriteJson(res, 200, new
                    {
                        serverId = _state.ServerId,
                        serverName = _state.ServerName,
                        items = payloadItems
                    });
                    return;
                }

                if (req.HttpMethod == "GET" && path.StartsWith("/api/stream/"))
                {
                    var itemId = path.Substring("/api/stream/".Length);
                    ScannedMediaItem item;
                    lock (_lock) _catalogById.TryGetValue(itemId, out item);
                    if (item == null || !File.Exists(item.FilePath))
                    {
                        WriteJson(res, 404, new { error = "Media file not found" });
                        return;
                    }
                    StreamFileWithRange(req, res, item);
                    return;
                }

                if (req.HttpMethod == "POST" && path.StartsWith("/api/items/"))
                {
                    var parts = path.Split(new[] { '/' }, StringSplitOptions.RemoveEmptyEntries);
                    if (parts.Length == 4)
                    {
                        var itemId = parts[2];
                        var action = parts[3];
                        var body = ReadBody(req);
                        var data = string.IsNullOrWhiteSpace(body)
                            ? new Dictionary<string, object>()
                            : _json.Deserialize<Dictionary<string, object>>(body);

                        lock (_lock)
                        {
                            ItemProgress prog;
                            if (!_state.Progress.TryGetValue(itemId, out prog))
                            {
                                prog = new ItemProgress();
                                _state.Progress[itemId] = prog;
                            }
                            if (action == "progress" && data != null)
                            {
                                if (data.ContainsKey("positionMs")) prog.PlaybackPositionMs = Convert.ToInt64(data["positionMs"]);
                                if (data.ContainsKey("durationMs")) prog.DurationMs = Convert.ToInt64(data["durationMs"]);
                                if (data.ContainsKey("watchStatus")) prog.WatchStatus = Convert.ToString(data["watchStatus"]);
                                prog.LastPlayedAt = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
                            }
                            else if (action == "favorite" && data != null && data.ContainsKey("isFavorite"))
                            {
                                prog.IsFavorite = Convert.ToBoolean(data["isFavorite"]);
                            }
                            else if (action == "watched" && data != null && data.ContainsKey("watchStatus"))
                            {
                                prog.WatchStatus = Convert.ToString(data["watchStatus"]);
                            }
                            SaveState();
                        }
                        WriteJson(res, 200, new { ok = true });
                        return;
                    }
                }

                WriteJson(res, 404, new { error = "Not found" });
            }
            catch (Exception ex)
            {
                try { WriteJson(res, 500, new { error = ex.Message }); } catch { }
            }
            finally
            {
                try { res.OutputStream.Close(); } catch { }
            }
        }

        private void StreamFileWithRange(HttpListenerRequest req, HttpListenerResponse res, ScannedMediaItem item)
        {
            var fi = new FileInfo(item.FilePath);
            long totalLen = fi.Length;
            long start = 0;
            long end = totalLen - 1;
            bool isPartial = false;

            var rangeHeader = req.Headers["Range"];
            if (!string.IsNullOrEmpty(rangeHeader) && rangeHeader.StartsWith("bytes=", StringComparison.OrdinalIgnoreCase))
            {
                var spec = rangeHeader.Substring("bytes=".Length).Split(',')[0].Trim();
                var bounds = spec.Split('-');
                if (bounds.Length == 2)
                {
                    if (!string.IsNullOrEmpty(bounds[0])) long.TryParse(bounds[0], out start);
                    if (!string.IsNullOrEmpty(bounds[1])) long.TryParse(bounds[1], out end);
                    else end = totalLen - 1;
                    if (start > end || start >= totalLen)
                    {
                        res.StatusCode = 416;
                        res.AddHeader("Content-Range", "bytes */" + totalLen);
                        return;
                    }
                    isPartial = true;
                }
            }

            long lengthToSend = (end - start) + 1;
            res.StatusCode = isPartial ? 206 : 200;
            res.ContentType = item.MimeType;
            res.SendChunked = false;
            res.ContentLength64 = lengthToSend;
            res.AddHeader("Accept-Ranges", "bytes");
            if (isPartial)
            {
                res.AddHeader("Content-Range", string.Format("bytes {0}-{1}/{2}", start, end, totalLen));
            }

            if (start == 0)
            {
                AppendLog("Streaming: " + item.Title + " (" + item.CodecBadge + ")");
            }

            byte[] buffer = new byte[64 * 1024];
            using (var fs = new FileStream(item.FilePath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite))
            {
                fs.Seek(start, SeekOrigin.Begin);
                long remaining = lengthToSend;
                while (remaining > 0 && _isRunning)
                {
                    int toRead = (int)Math.Min(buffer.Length, remaining);
                    int read = fs.Read(buffer, 0, toRead);
                    if (read <= 0) break;
                    res.OutputStream.Write(buffer, 0, read);
                    remaining -= read;
                }
            }
        }

        private bool IsAuthorized(HttpListenerRequest req)
        {
            if (string.IsNullOrEmpty(_state.PairingPin)) return true;
            var token = ExtractToken(req);
            if (string.IsNullOrEmpty(token)) return false;
            lock (_lock)
            {
                return _state.IssuedTokens.Contains(token);
            }
        }

        private static string ExtractToken(HttpListenerRequest req)
        {
            var auth = req.Headers["Authorization"];
            if (!string.IsNullOrEmpty(auth) && auth.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
            {
                return auth.Substring("Bearer ".Length).Trim();
            }
            var qToken = req.QueryString["token"];
            return string.IsNullOrWhiteSpace(qToken) ? null : qToken.Trim();
        }

        private void WriteJson(HttpListenerResponse res, int statusCode, object obj)
        {
            var json = _json.Serialize(obj);
            var bytes = Encoding.UTF8.GetBytes(json);
            res.StatusCode = statusCode;
            res.ContentType = "application/json; charset=utf-8";
            res.ContentLength64 = bytes.Length;
            res.OutputStream.Write(bytes, 0, bytes.Length);
        }

        private static string ReadBody(HttpListenerRequest req)
        {
            using (var reader = new StreamReader(req.InputStream, req.ContentEncoding ?? Encoding.UTF8))
            {
                return reader.ReadToEnd();
            }
        }

        private void AuthorizeWindowsPortAndFirewall()
        {
            int port = _state.Port;
            var cmd = string.Format(
                "netsh http add urlacl url=http://+:{0}/ user=Everyone & netsh advfirewall firewall add rule name=\"LootStream PC Host ({0})\" dir=in action=allow protocol=TCP localport={0}",
                port
            );
            try
            {
                var psi = new ProcessStartInfo("cmd.exe", "/c " + cmd)
                {
                    Verb = "runas",
                    UseShellExecute = true,
                    WindowStyle = ProcessWindowStyle.Hidden
                };
                Process.Start(psi);
                AppendLog("Requested Windows firewall & URL ACL authorization for port " + port + ".");
            }
            catch (Exception ex)
            {
                AppendLog("Elevation cancelled or failed: " + ex.Message);
            }
        }

        private void AppendLog(string message)
        {
            var line = "[" + DateTime.Now.ToString("HH:mm:ss") + "] " + message + Environment.NewLine;
            if (_txtLog == null || IsDisposed) return;
            if (_txtLog.InvokeRequired)
            {
                try { _txtLog.BeginInvoke((Action)(() => { _txtLog.AppendText(line); })); } catch { }
            }
            else
            {
                _txtLog.AppendText(line);
            }
        }

        private void LoadState()
        {
            try
            {
                Directory.CreateDirectory(_stateDir);
                if (File.Exists(_stateFile))
                {
                    var raw = File.ReadAllText(_stateFile, Encoding.UTF8);
                    _state = _json.Deserialize<HostState>(raw) ?? new HostState();
                }
                else
                {
                    _state = new HostState();
                }
            }
            catch
            {
                _state = new HostState();
            }
            if (_state.Folders == null) _state.Folders = new List<string>();
            if (_state.IssuedTokens == null) _state.IssuedTokens = new List<string>();
            if (_state.Progress == null) _state.Progress = new Dictionary<string, ItemProgress>();
        }

        private void SaveState()
        {
            try
            {
                Directory.CreateDirectory(_stateDir);
                File.WriteAllText(_stateFile, _json.Serialize(_state), Encoding.UTF8);
            }
            catch { }
        }

        private string GetPrimaryLanUrl()
        {
            var ip = GetLanIpv4Addresses().FirstOrDefault() ?? "127.0.0.1";
            return "http://" + ip + ":" + _state.Port;
        }

        private static List<string> GetLanIpv4Addresses()
        {
            var ips = new List<string>();
            try
            {
                foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
                {
                    if (ni.OperationalStatus != OperationalStatus.Up) continue;
                    if (ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
                    foreach (var ua in ni.GetIPProperties().UnicastAddresses)
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

        private static string ComputeStableId(string input)
        {
            using (var sha = SHA1.Create())
            {
                var hash = sha.ComputeHash(Encoding.UTF8.GetBytes(input.ToLowerInvariant()));
                return BitConverter.ToString(hash).Replace("-", "").Substring(0, 16).ToLowerInvariant();
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
                case ".ts":
                case ".m2ts": return "video/mp2t";
                case ".mp3": return "audio/mpeg";
                case ".flac": return "audio/flac";
                case ".wav": return "audio/wav";
                case ".m4a":
                case ".aac": return "audio/mp4";
                case ".ogg":
                case ".opus": return "audio/ogg";
                default: return "application/octet-stream";
            }
        }
    }
}
