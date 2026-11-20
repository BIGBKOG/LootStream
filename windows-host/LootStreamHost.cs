using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Web.Script.Serialization;
using System.Windows.Forms;
using Microsoft.Win32;

namespace LootStreamHost
{
    public class PairedDeviceInfo
    {
        public string deviceName { get; set; }
        public long pairedAt { get; set; }
    }

    public class HostState
    {
        public string ServerId { get; set; }
        public string ServerName { get; set; }
        public int Port { get; set; }
        public string Pin { get; set; }
        public List<string> Folders { get; set; }
        public List<string> ValidTokens { get; set; }
        public List<PairedDeviceInfo> PairedDevices { get; set; }
        public Dictionary<string, ItemProgress> Progress { get; set; }

        public HostState()
        {
            ServerId = Guid.NewGuid().ToString("N").Substring(0, 12);
            ServerName = Environment.MachineName + " LootStream";
            Port = 8765;
            Pin = "";
            Folders = new List<string>();
            ValidTokens = new List<string>();
            PairedDevices = new List<PairedDeviceInfo>();
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
        public string PosterPath { get; set; }
        public string BackdropPath { get; set; }
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

    public sealed class SetupOptions
    {
        public string InstallDir { get; set; }
        public List<string> InitialFolders { get; set; }
        public bool CreateShortcuts { get; set; }
        public bool LaunchOnStartup { get; set; }
        public bool ConfigureFirewall { get; set; }
        public bool LaunchAfterInstall { get; set; }
        public bool IsSilent { get; set; }

        public SetupOptions()
        {
            InstallDir = SelfInstaller.GetSavedOrDefaultInstallDir();
            InitialFolders = SelfInstaller.GetDefaultOrExistingFolders();
            CreateShortcuts = true;
            LaunchOnStartup = true;
            ConfigureFirewall = true;
            LaunchAfterInstall = true;
            IsSilent = false;
        }
    }

    public static class SelfInstaller
    {
        public const string AppVersion = "1.0.0";
        public const string AppDisplayName = "LootStream Host";
        private const string UninstallRegKey = @"Software\Microsoft\Windows\CurrentVersion\Uninstall\LootStreamHost";
        private const string RunRegKey = @"Software\Microsoft\Windows\CurrentVersion\Run";
        private const string RunValueName = "LootStreamHost";

        public static string GetAppDataDir()
        {
            return Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                "LootStreamHost"
            );
        }

        public static string GetDefaultInstallDir()
        {
            string localApp = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            return Path.Combine(localApp, "LootStreamHost");
        }

        public static string GetSavedOrDefaultInstallDir()
        {
            try
            {
                using (RegistryKey key = Registry.CurrentUser.OpenSubKey(UninstallRegKey))
                {
                    if (key != null)
                    {
                        string loc = key.GetValue("InstallLocation") as string;
                        if (!string.IsNullOrEmpty(loc)) return loc;
                    }
                }
            }
            catch { }
            return GetDefaultInstallDir();
        }

        public static string GetInstalledExePath()
        {
            return Path.Combine(GetSavedOrDefaultInstallDir(), "LootStreamHost.exe");
        }

        public static string GetInstalledExePath(string installDir)
        {
            return Path.Combine(installDir, "LootStreamHost.exe");
        }

        public static bool IsAlreadyInstalled()
        {
            return File.Exists(GetInstalledExePath());
        }

        public static bool IsRunningFromInstallDir()
        {
            try
            {
                string current = Path.GetFullPath(Application.ExecutablePath);
                string installed = Path.GetFullPath(GetInstalledExePath());
                return string.Equals(current, installed, StringComparison.OrdinalIgnoreCase);
            }
            catch
            {
                return true;
            }
        }

        public static List<string> GetDefaultOrExistingFolders()
        {
            List<string> list = new List<string>();
            try
            {
                string statePath = Path.Combine(GetAppDataDir(), "state.json");
                if (File.Exists(statePath))
                {
                    JavaScriptSerializer ser = new JavaScriptSerializer();
                    HostState st = ser.Deserialize<HostState>(File.ReadAllText(statePath, Encoding.UTF8));
                    if (st != null && st.Folders != null)
                    {
                        foreach (string f in st.Folders)
                        {
                            if (!string.IsNullOrEmpty(f) && Directory.Exists(f) && !list.Contains(f))
                            {
                                list.Add(f);
                            }
                        }
                    }
                }
            }
            catch { }

            if (list.Count == 0)
            {
                string videos = Environment.GetFolderPath(Environment.SpecialFolder.MyVideos);
                string music = Environment.GetFolderPath(Environment.SpecialFolder.MyMusic);
                if (!string.IsNullOrEmpty(videos) && Directory.Exists(videos)) list.Add(videos);
                if (!string.IsNullOrEmpty(music) && Directory.Exists(music)) list.Add(music);
            }
            return list;
        }

        public static bool HandleInstallerArgsOrPrompt(string[] args)
        {
            bool forceInstall = false;
            bool forceUninstall = false;
            bool portableMode = false;
            bool silentMode = false;

            if (args != null)
            {
                foreach (string arg in args)
                {
                    if (string.Equals(arg, "--install", StringComparison.OrdinalIgnoreCase)) forceInstall = true;
                    else if (string.Equals(arg, "--uninstall", StringComparison.OrdinalIgnoreCase)) forceUninstall = true;
                    else if (string.Equals(arg, "--portable", StringComparison.OrdinalIgnoreCase)) portableMode = true;
                    else if (string.Equals(arg, "--silent", StringComparison.OrdinalIgnoreCase)) silentMode = true;
                }
            }

            if (forceUninstall)
            {
                if (silentMode) PerformUninstall(false, false);
                else ShowUninstallWizard();
                return false;
            }

            if (forceInstall)
            {
                SetupOptions opts = new SetupOptions();
                opts.IsSilent = silentMode;
                opts.LaunchAfterInstall = !silentMode;
                PerformInstall(opts, null);
                return false;
            }

            if (!portableMode && !IsRunningFromInstallDir())
            {
                using (SetupWizardForm wizard = new SetupWizardForm())
                {
                    DialogResult res = wizard.ShowDialog();
                    if (wizard.Action == SetupWizardForm.WizardAction.RunPortable)
                    {
                        return true;
                    }
                    if (wizard.Action == SetupWizardForm.WizardAction.Uninstall)
                    {
                        ShowUninstallWizard();
                    }
                    return false;
                }
            }

            return true;
        }

        public static void SaveInitialFoldersToState(List<string> folders)
        {
            if (folders == null) return;
            try
            {
                string appData = GetAppDataDir();
                Directory.CreateDirectory(appData);
                string statePath = Path.Combine(appData, "state.json");
                JavaScriptSerializer ser = new JavaScriptSerializer();
                ser.MaxJsonLength = 32 * 1024 * 1024;

                HostState state = null;
                if (File.Exists(statePath))
                {
                    try { state = ser.Deserialize<HostState>(File.ReadAllText(statePath, Encoding.UTF8)); }
                    catch { state = null; }
                }
                if (state == null) state = new HostState();
                state.Folders = new List<string>();
                foreach (string folder in folders)
                {
                    if (!string.IsNullOrEmpty(folder) && Directory.Exists(folder))
                    {
                        string full = Path.GetFullPath(folder);
                        if (!state.Folders.Contains(full)) state.Folders.Add(full);
                    }
                }
                File.WriteAllText(statePath, ser.Serialize(state), Encoding.UTF8);
            }
            catch { }
        }

        public static bool PerformInstall(SetupOptions options, Action<int, string> progressCallback)
        {
            Action<int, string> report = delegate(int pct, string msg)
            {
                if (progressCallback != null) progressCallback(pct, msg);
            };

            try
            {
                string installDir = string.IsNullOrEmpty(options.InstallDir)
                    ? GetDefaultInstallDir()
                    : Path.GetFullPath(options.InstallDir.Trim());
                string targetExe = GetInstalledExePath(installDir);

                report(12, "Creating install directory...");
                Directory.CreateDirectory(installDir);

                report(28, "Copying LootStream Host executable...");
                string sourceExe = Path.GetFullPath(Application.ExecutablePath);
                if (!string.Equals(sourceExe, Path.GetFullPath(targetExe), StringComparison.OrdinalIgnoreCase))
                {
                    StopOtherRunningInstances();
                    Thread.Sleep(250);
                    File.Copy(sourceExe, targetExe, true);
                }

                report(46, "Saving shared media folders...");
                SaveInitialFoldersToState(options.InitialFolders);

                if (options.CreateShortcuts)
                {
                    report(62, "Creating Desktop & Start Menu shortcuts...");
                    CreateShortcut(
                        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), "LootStream Host.lnk"),
                        targetExe,
                        installDir,
                        "LootStream Personal Media Host & Media Center"
                    );

                    string startMenuDir = Path.Combine(
                        Environment.GetFolderPath(Environment.SpecialFolder.Programs),
                        "LootStream Host"
                    );
                    Directory.CreateDirectory(startMenuDir);
                    CreateShortcut(
                        Path.Combine(startMenuDir, "LootStream Host.lnk"),
                        targetExe,
                        installDir,
                        "LootStream Personal Media Host & Media Center"
                    );
                    CreateShortcut(
                        Path.Combine(startMenuDir, "Uninstall LootStream Host.lnk"),
                        targetExe,
                        installDir,
                        "Uninstall LootStream Host",
                        "--uninstall"
                    );
                }

                report(78, "Registering Windows Apps entry & startup preference...");
                RegisterUninstallEntry(targetExe, installDir);
                SetLaunchOnStartup(options.LaunchOnStartup, targetExe);

                if (options.ConfigureFirewall)
                {
                    report(90, "Configuring Windows Firewall & LAN access (TCP 8765 / UDP 8766)...");
                    TryAddFirewallRule(8765);
                }

                report(100, "Setup complete! Launching LootStream Host...");
                if (options.LaunchAfterInstall)
                {
                    Process.Start(new ProcessStartInfo
                    {
                        FileName = targetExe,
                        WorkingDirectory = installDir,
                        UseShellExecute = true
                    });
                }
                else if (!options.IsSilent && progressCallback == null)
                {
                    MessageBox.Show(
                        "LootStream Host was installed to:\n" + targetExe,
                        "LootStream Host Installed",
                        MessageBoxButtons.OK,
                        MessageBoxIcon.Information
                    );
                }
                return true;
            }
            catch (Exception ex)
            {
                if (!options.IsSilent)
                {
                    MessageBox.Show(
                        "Installation failed: " + ex.Message,
                        "LootStream Host Setup Error",
                        MessageBoxButtons.OK,
                        MessageBoxIcon.Error
                    );
                }
                return false;
            }
        }

        public static void ShowUninstallWizard()
        {
            using (UninstallForm dlg = new UninstallForm())
            {
                if (dlg.ShowDialog() == DialogResult.Yes)
                {
                    PerformUninstall(dlg.PurgeSettings, true);
                }
            }
        }

        public static void PerformUninstall(bool purgeSettings, bool showCompletionDialog)
        {
            try
            {
                StopOtherRunningInstances();

                string desktopShortcut = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory),
                    "LootStream Host.lnk"
                );
                if (File.Exists(desktopShortcut)) File.Delete(desktopShortcut);

                string startMenuDir = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.Programs),
                    "LootStream Host"
                );
                if (Directory.Exists(startMenuDir)) Directory.Delete(startMenuDir, true);

                string installDir = GetSavedOrDefaultInstallDir();
                SetLaunchOnStartup(false, "");

                try
                {
                    Registry.CurrentUser.DeleteSubKeyTree(UninstallRegKey, false);
                }
                catch { }

                if (purgeSettings)
                {
                    try
                    {
                        string appData = GetAppDataDir();
                        if (Directory.Exists(appData)) Directory.Delete(appData, true);
                    }
                    catch { }
                }

                string cleanupCmd = "/c timeout /t 2 /nobreak >nul & rmdir /s /q \"" + installDir + "\"";
                Process.Start(new ProcessStartInfo
                {
                    FileName = "cmd.exe",
                    Arguments = cleanupCmd,
                    WindowStyle = ProcessWindowStyle.Hidden,
                    CreateNoWindow = true
                });

                if (showCompletionDialog)
                {
                    MessageBox.Show(
                        "LootStream Host has been uninstalled cleanly.",
                        "Uninstall Complete",
                        MessageBoxButtons.OK,
                        MessageBoxIcon.Information
                    );
                }
            }
            catch (Exception ex)
            {
                if (showCompletionDialog)
                {
                    MessageBox.Show(
                        "Uninstall warning: " + ex.Message,
                        "Uninstall",
                        MessageBoxButtons.OK,
                        MessageBoxIcon.Warning
                    );
                }
            }
        }

        private static void StopOtherRunningInstances()
        {
            int currentId = Process.GetCurrentProcess().Id;
            string currentName = Process.GetCurrentProcess().ProcessName;
            foreach (Process p in Process.GetProcessesByName(currentName))
            {
                if (p.Id == currentId) continue;
                try
                {
                    p.CloseMainWindow();
                    if (!p.WaitForExit(1500)) p.Kill();
                }
                catch { }
            }
        }

        private static void SetLaunchOnStartup(bool enable, string exePath)
        {
            try
            {
                using (RegistryKey key = Registry.CurrentUser.OpenSubKey(RunRegKey, true))
                {
                    if (key == null) return;
                    if (enable && !string.IsNullOrEmpty(exePath))
                    {
                        key.SetValue(RunValueName, "\"" + exePath + "\"");
                    }
                    else
                    {
                        key.DeleteValue(RunValueName, false);
                    }
                }
            }
            catch { }
        }

        private static void RegisterUninstallEntry(string exePath, string installDir)
        {
            using (RegistryKey key = Registry.CurrentUser.CreateSubKey(UninstallRegKey))
            {
                if (key == null) return;
                key.SetValue("DisplayName", AppDisplayName);
                key.SetValue("DisplayVersion", AppVersion);
                key.SetValue("Publisher", "LootStream");
                key.SetValue("InstallLocation", installDir);
                key.SetValue("DisplayIcon", exePath);
                key.SetValue("UninstallString", "\"" + exePath + "\" --uninstall");
                key.SetValue("NoModify", 1, RegistryValueKind.DWord);
                key.SetValue("NoRepair", 1, RegistryValueKind.DWord);
            }
        }

        private static void CreateShortcut(string lnkPath, string targetPath, string workingDir, string description, string args = "")
        {
            try
            {
                string psScript =
                    "$ws = New-Object -ComObject WScript.Shell; " +
                    "$sc = $ws.CreateShortcut('" + EscapePs(lnkPath) + "'); " +
                    "$sc.TargetPath = '" + EscapePs(targetPath) + "'; " +
                    "$sc.WorkingDirectory = '" + EscapePs(workingDir) + "'; " +
                    "$sc.Description = '" + EscapePs(description) + "'; " +
                    "$sc.IconLocation = '" + EscapePs(targetPath) + ",0'; " +
                    (string.IsNullOrEmpty(args) ? "" : "$sc.Arguments = '" + EscapePs(args) + "'; ") +
                    "$sc.Save()";

                Process p = Process.Start(new ProcessStartInfo
                {
                    FileName = "powershell.exe",
                    Arguments = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -Command \"" + psScript.Replace("\"", "`\"") + "\"",
                    WindowStyle = ProcessWindowStyle.Hidden,
                    CreateNoWindow = true
                });
                if (p != null) p.WaitForExit(5000);
            }
            catch { }
        }

        private static string EscapePs(string input)
        {
            return (input ?? "").Replace("'", "''");
        }

        public static void TryAddFirewallRule(int port)
        {
            try
            {
                // Run all netsh commands in ONE elevated process (at most a single UAC prompt)
                string combinedArgs = string.Format(
                    "/c netsh advfirewall firewall delete rule name=\"LootStream Host (TCP {0})\" >nul 2>&1 & " +
                    "netsh advfirewall firewall add rule name=\"LootStream Host (TCP {0})\" dir=in action=allow protocol=TCP localport={0} & " +
                    "netsh advfirewall firewall delete rule name=\"LootStream Host Discovery (UDP 8766)\" >nul 2>&1 & " +
                    "netsh advfirewall firewall add rule name=\"LootStream Host Discovery (UDP 8766)\" dir=in action=allow protocol=UDP localport=8766 & " +
                    "netsh http add urlacl url=http://+:{0}/ sddl=D:(A;;GX;;;WD) >nul 2>&1",
                    port
                );
                Process p = Process.Start(new ProcessStartInfo
                {
                    FileName = "cmd.exe",
                    Arguments = combinedArgs,
                    Verb = "runas",
                    WindowStyle = ProcessWindowStyle.Hidden,
                    CreateNoWindow = true,
                    UseShellExecute = true
                });
                if (p != null) p.WaitForExit(6000);
            }
            catch
            {
                // User may decline UAC prompt; local playback and existing bindings still function.
            }
        }
    }

    internal sealed class SetupWizardForm : Form
    {
        public enum WizardAction
        {
            Cancel,
            Install,
            RunPortable,
            Uninstall
        }

        public WizardAction Action { get; private set; }

        private TextBox _txtInstallDir;
        private ListBox _lstFolders;
        private CheckBox _chkShortcuts;
        private CheckBox _chkStartup;
        private CheckBox _chkFirewall;
        private ProgressBar _progressBar;
        private Label _lblProgress;
        private Button _btnInstall;
        private Button _btnPortable;
        private Button _btnUninstall;
        private Button _btnCancel;
        private Button _btnBrowse;
        private Button _btnAddFolder;
        private Button _btnRemoveFolder;

        public SetupWizardForm()
        {
            Action = WizardAction.Cancel;
            BuildUi();
        }

        private void BuildUi()
        {
            Color bg = Color.FromArgb(11, 15, 25);
            Color surface = Color.FromArgb(19, 25, 38);
            Color surfaceVar = Color.FromArgb(28, 37, 56);
            Color cyan = Color.FromArgb(0, 229, 255);
            Color green = Color.FromArgb(0, 255, 135);
            Color purple = Color.FromArgb(176, 102, 255);
            Color textPrimary = Color.FromArgb(241, 245, 249);
            Color textSecondary = Color.FromArgb(148, 163, 184);

            bool isUpdate = SelfInstaller.IsAlreadyInstalled();

            this.Text = "LootStream Host v" + SelfInstaller.AppVersion + " Setup";
            this.Size = new Size(590, 560);
            this.FormBorderStyle = FormBorderStyle.FixedDialog;
            this.MaximizeBox = false;
            this.MinimizeBox = true;
            this.StartPosition = FormStartPosition.CenterScreen;
            this.BackColor = bg;
            this.ForeColor = textPrimary;
            this.Font = new Font("Segoe UI", 9.5f);

            Panel header = new Panel();
            header.Dock = DockStyle.Top;
            header.Height = 82;
            header.BackColor = surface;

            Panel logoBadge = new Panel();
            logoBadge.Size = new Size(46, 46);
            logoBadge.Location = new Point(18, 18);
            logoBadge.BackColor = Color.FromArgb(16, 40, 62);
            logoBadge.Paint += delegate(object s, PaintEventArgs e)
            {
                e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
                using (Pen pen = new Pen(cyan, 2.2f))
                {
                    e.Graphics.DrawRectangle(pen, 2, 2, 41, 41);
                }
                Point[] tri = new Point[] { new Point(17, 12), new Point(35, 23), new Point(17, 34) };
                using (SolidBrush br = new SolidBrush(green))
                {
                    e.Graphics.FillPolygon(br, tri);
                }
            };

            Label lblTitle = new Label();
            lblTitle.Text = isUpdate ? "UPDATE LOOTSTREAM HOST" : "LOOTSTREAM HOST SETUP";
            lblTitle.Font = new Font("Segoe UI", 14.5f, FontStyle.Bold);
            lblTitle.ForeColor = cyan;
            lblTitle.Location = new Point(76, 15);
            lblTitle.AutoSize = true;

            Label lblSub = new Label();
            lblSub.Text = "1-Click Personal Media Center (Movies, TV, Music) + Android Wi-Fi / QR Host";
            lblSub.Font = new Font("Segoe UI", 9f);
            lblSub.ForeColor = textSecondary;
            lblSub.Location = new Point(77, 44);
            lblSub.AutoSize = true;

            header.Controls.Add(logoBadge);
            header.Controls.Add(lblTitle);
            header.Controls.Add(lblSub);
            this.Controls.Add(header);

            int y = 98;

            Label lblPath = new Label();
            lblPath.Text = "Install Folder";
            lblPath.Font = new Font("Segoe UI", 9.5f, FontStyle.Bold);
            lblPath.ForeColor = textPrimary;
            lblPath.Location = new Point(22, y);
            lblPath.AutoSize = true;
            this.Controls.Add(lblPath);

            y += 24;
            _txtInstallDir = new TextBox();
            _txtInstallDir.Text = SelfInstaller.GetSavedOrDefaultInstallDir();
            _txtInstallDir.Location = new Point(22, y);
            _txtInstallDir.Size = new Size(432, 26);
            _txtInstallDir.BackColor = surfaceVar;
            _txtInstallDir.ForeColor = textPrimary;
            _txtInstallDir.BorderStyle = BorderStyle.FixedSingle;
            this.Controls.Add(_txtInstallDir);

            _btnBrowse = new Button();
            _btnBrowse.Text = "Browse...";
            _btnBrowse.Location = new Point(464, y - 1);
            _btnBrowse.Size = new Size(92, 28);
            _btnBrowse.FlatStyle = FlatStyle.Flat;
            _btnBrowse.FlatAppearance.BorderSize = 0;
            _btnBrowse.BackColor = surfaceVar;
            _btnBrowse.ForeColor = cyan;
            _btnBrowse.Click += delegate
            {
                using (FolderBrowserDialog fb = new FolderBrowserDialog())
                {
                    fb.Description = "Choose install folder for LootStream Host";
                    fb.SelectedPath = _txtInstallDir.Text;
                    if (fb.ShowDialog() == DialogResult.OK && !string.IsNullOrEmpty(fb.SelectedPath))
                    {
                        _txtInstallDir.Text = fb.SelectedPath;
                    }
                }
            };
            this.Controls.Add(_btnBrowse);

            y += 40;
            Label lblFolders = new Label();
            lblFolders.Text = "Media Folders to Share (pre-selected automatically — change anytime)";
            lblFolders.Font = new Font("Segoe UI", 9.5f, FontStyle.Bold);
            lblFolders.ForeColor = textPrimary;
            lblFolders.Location = new Point(22, y);
            lblFolders.AutoSize = true;
            this.Controls.Add(lblFolders);

            y += 24;
            _lstFolders = new ListBox();
            _lstFolders.Location = new Point(22, y);
            _lstFolders.Size = new Size(432, 92);
            _lstFolders.BackColor = surface;
            _lstFolders.ForeColor = textPrimary;
            _lstFolders.BorderStyle = BorderStyle.FixedSingle;
            foreach (string f in SelfInstaller.GetDefaultOrExistingFolders())
            {
                _lstFolders.Items.Add(f);
            }
            this.Controls.Add(_lstFolders);

            _btnAddFolder = new Button();
            _btnAddFolder.Text = "+ Add Folder";
            _btnAddFolder.Location = new Point(464, y);
            _btnAddFolder.Size = new Size(92, 30);
            _btnAddFolder.FlatStyle = FlatStyle.Flat;
            _btnAddFolder.FlatAppearance.BorderSize = 0;
            _btnAddFolder.BackColor = purple;
            _btnAddFolder.ForeColor = Color.White;
            _btnAddFolder.Font = new Font("Segoe UI", 8.5f, FontStyle.Bold);
            _btnAddFolder.Click += delegate
            {
                using (FolderBrowserDialog fb = new FolderBrowserDialog())
                {
                    fb.Description = "Select a Movies, TV Shows, or Music folder";
                    if (fb.ShowDialog() == DialogResult.OK && !string.IsNullOrEmpty(fb.SelectedPath))
                    {
                        if (!_lstFolders.Items.Contains(fb.SelectedPath))
                        {
                            _lstFolders.Items.Add(fb.SelectedPath);
                        }
                    }
                }
            };
            this.Controls.Add(_btnAddFolder);

            _btnRemoveFolder = new Button();
            _btnRemoveFolder.Text = "Remove";
            _btnRemoveFolder.Location = new Point(464, y + 38);
            _btnRemoveFolder.Size = new Size(92, 30);
            _btnRemoveFolder.FlatStyle = FlatStyle.Flat;
            _btnRemoveFolder.FlatAppearance.BorderSize = 0;
            _btnRemoveFolder.BackColor = surfaceVar;
            _btnRemoveFolder.ForeColor = textSecondary;
            _btnRemoveFolder.Click += delegate
            {
                if (_lstFolders.SelectedIndex >= 0)
                {
                    _lstFolders.Items.RemoveAt(_lstFolders.SelectedIndex);
                }
            };
            this.Controls.Add(_btnRemoveFolder);

            y += 108;
            Panel optsPanel = new Panel();
            optsPanel.Location = new Point(22, y);
            optsPanel.Size = new Size(534, 96);
            optsPanel.BackColor = surface;
            optsPanel.Padding = new Padding(12);

            _chkShortcuts = new CheckBox();
            _chkShortcuts.Text = "Create Desktop && Start Menu shortcuts";
            _chkShortcuts.Checked = true;
            _chkShortcuts.Location = new Point(14, 10);
            _chkShortcuts.AutoSize = true;
            _chkShortcuts.ForeColor = textPrimary;

            _chkStartup = new CheckBox();
            _chkStartup.Text = "Launch automatically when Windows starts";
            _chkStartup.Checked = true;
            _chkStartup.Location = new Point(14, 38);
            _chkStartup.AutoSize = true;
            _chkStartup.ForeColor = textPrimary;

            _chkFirewall = new CheckBox();
            _chkFirewall.Text = "Allow Wi-Fi streaming && QR discovery in Windows Firewall (TCP 8765 / UDP 8766)";
            _chkFirewall.Checked = true;
            _chkFirewall.Location = new Point(14, 66);
            _chkFirewall.AutoSize = true;
            _chkFirewall.ForeColor = textPrimary;

            optsPanel.Controls.Add(_chkShortcuts);
            optsPanel.Controls.Add(_chkStartup);
            optsPanel.Controls.Add(_chkFirewall);
            this.Controls.Add(optsPanel);

            y += 110;
            _lblProgress = new Label();
            _lblProgress.Text = "Ready — click " + (isUpdate ? "Update & Launch" : "Install & Launch") + " to finish setup in seconds.";
            _lblProgress.Font = new Font("Segoe UI", 9f);
            _lblProgress.ForeColor = textSecondary;
            _lblProgress.Location = new Point(22, y);
            _lblProgress.Size = new Size(534, 20);
            this.Controls.Add(_lblProgress);

            y += 24;
            _progressBar = new ProgressBar();
            _progressBar.Location = new Point(22, y);
            _progressBar.Size = new Size(534, 14);
            _progressBar.Minimum = 0;
            _progressBar.Maximum = 100;
            _progressBar.Style = ProgressBarStyle.Continuous;
            this.Controls.Add(_progressBar);

            y += 28;
            _btnInstall = new Button();
            _btnInstall.Text = isUpdate ? "Update && Launch" : "Install && Launch";
            _btnInstall.Location = new Point(22, y);
            _btnInstall.Size = new Size(190, 38);
            _btnInstall.FlatStyle = FlatStyle.Flat;
            _btnInstall.FlatAppearance.BorderSize = 0;
            _btnInstall.BackColor = green;
            _btnInstall.ForeColor = bg;
            _btnInstall.Font = new Font("Segoe UI", 10f, FontStyle.Bold);
            _btnInstall.Click += delegate { BeginInstall(); };
            this.Controls.Add(_btnInstall);

            _btnPortable = new Button();
            _btnPortable.Text = "Run Portable";
            _btnPortable.Location = new Point(222, y);
            _btnPortable.Size = new Size(124, 38);
            _btnPortable.FlatStyle = FlatStyle.Flat;
            _btnPortable.FlatAppearance.BorderSize = 0;
            _btnPortable.BackColor = surfaceVar;
            _btnPortable.ForeColor = cyan;
            _btnPortable.Font = new Font("Segoe UI", 9.5f, FontStyle.Bold);
            _btnPortable.Click += delegate
            {
                Action = WizardAction.RunPortable;
                this.DialogResult = DialogResult.Ignore;
                this.Close();
            };
            this.Controls.Add(_btnPortable);

            if (isUpdate)
            {
                _btnUninstall = new Button();
                _btnUninstall.Text = "Uninstall";
                _btnUninstall.Location = new Point(356, y);
                _btnUninstall.Size = new Size(96, 38);
                _btnUninstall.FlatStyle = FlatStyle.Flat;
                _btnUninstall.FlatAppearance.BorderSize = 0;
                _btnUninstall.BackColor = Color.FromArgb(127, 29, 29);
                _btnUninstall.ForeColor = Color.White;
                _btnUninstall.Click += delegate
                {
                    Action = WizardAction.Uninstall;
                    this.DialogResult = DialogResult.Abort;
                    this.Close();
                };
                this.Controls.Add(_btnUninstall);
            }

            _btnCancel = new Button();
            _btnCancel.Text = "Cancel";
            _btnCancel.Location = new Point(464, y);
            _btnCancel.Size = new Size(92, 38);
            _btnCancel.FlatStyle = FlatStyle.Flat;
            _btnCancel.FlatAppearance.BorderSize = 0;
            _btnCancel.BackColor = surfaceVar;
            _btnCancel.ForeColor = textSecondary;
            _btnCancel.Click += delegate
            {
                Action = WizardAction.Cancel;
                this.DialogResult = DialogResult.Cancel;
                this.Close();
            };
            this.Controls.Add(_btnCancel);

            this.AcceptButton = _btnInstall;
            this.CancelButton = _btnCancel;
        }

        private void SetUiEnabled(bool enabled)
        {
            _txtInstallDir.Enabled = enabled;
            _lstFolders.Enabled = enabled;
            _chkShortcuts.Enabled = enabled;
            _chkStartup.Enabled = enabled;
            _chkFirewall.Enabled = enabled;
            _btnInstall.Enabled = enabled;
            _btnPortable.Enabled = enabled;
            _btnCancel.Enabled = enabled;
            _btnBrowse.Enabled = enabled;
            _btnAddFolder.Enabled = enabled;
            _btnRemoveFolder.Enabled = enabled;
            if (_btnUninstall != null) _btnUninstall.Enabled = enabled;
        }

        private void BeginInstall()
        {
            SetUiEnabled(false);

            SetupOptions opts = new SetupOptions();
            opts.InstallDir = _txtInstallDir.Text.Trim();
            opts.InitialFolders = new List<string>();
            foreach (object item in _lstFolders.Items)
            {
                string path = item != null ? item.ToString() : "";
                if (!string.IsNullOrEmpty(path)) opts.InitialFolders.Add(path);
            }
            opts.CreateShortcuts = _chkShortcuts.Checked;
            opts.LaunchOnStartup = _chkStartup.Checked;
            opts.ConfigureFirewall = _chkFirewall.Checked;
            opts.LaunchAfterInstall = true;

            Thread worker = new Thread(delegate
            {
                bool ok = SelfInstaller.PerformInstall(opts, delegate(int pct, string msg)
                {
                    if (!this.IsDisposed && this.IsHandleCreated)
                    {
                        this.BeginInvoke((Action)delegate
                        {
                            _progressBar.Value = Math.Max(0, Math.Min(100, pct));
                            _lblProgress.Text = msg;
                            _lblProgress.ForeColor = Color.FromArgb(0, 229, 255);
                        });
                    }
                });

                if (!this.IsDisposed && this.IsHandleCreated)
                {
                    this.BeginInvoke((Action)delegate
                    {
                        if (ok)
                        {
                            Action = WizardAction.Install;
                            this.DialogResult = DialogResult.OK;
                            this.Close();
                        }
                        else
                        {
                            SetUiEnabled(true);
                            _progressBar.Value = 0;
                            _lblProgress.Text = "Installation did not complete. Check folder permissions and retry.";
                            _lblProgress.ForeColor = Color.FromArgb(248, 113, 113);
                        }
                    });
                }
            });
            worker.IsBackground = true;
            worker.Start();
        }
    }

    internal sealed class UninstallForm : Form
    {
        private CheckBox _chkPurge;
        public bool PurgeSettings { get { return _chkPurge != null && _chkPurge.Checked; } }

        public UninstallForm()
        {
            Color bg = Color.FromArgb(11, 15, 25);
            Color surface = Color.FromArgb(19, 25, 38);
            Color surfaceVar = Color.FromArgb(28, 37, 56);
            Color textPrimary = Color.FromArgb(241, 245, 249);
            Color textSecondary = Color.FromArgb(148, 163, 184);

            this.Text = "Uninstall LootStream Host";
            this.Size = new Size(440, 230);
            this.FormBorderStyle = FormBorderStyle.FixedDialog;
            this.MaximizeBox = false;
            this.MinimizeBox = false;
            this.StartPosition = FormStartPosition.CenterScreen;
            this.BackColor = bg;
            this.ForeColor = textPrimary;
            this.Font = new Font("Segoe UI", 9.5f);

            Label lblTitle = new Label();
            lblTitle.Text = "Uninstall LootStream Host?";
            lblTitle.Font = new Font("Segoe UI", 13f, FontStyle.Bold);
            lblTitle.ForeColor = Color.FromArgb(0, 229, 255);
            lblTitle.Location = new Point(22, 18);
            lblTitle.AutoSize = true;
            this.Controls.Add(lblTitle);

            Label lblBody = new Label();
            lblBody.Text = "Removes LootStream Host application files, shortcuts, and startup registration.\nYour movie, show, and music files are never modified.";
            lblBody.ForeColor = textSecondary;
            lblBody.Location = new Point(22, 50);
            lblBody.Size = new Size(384, 38);
            this.Controls.Add(lblBody);

            _chkPurge = new CheckBox();
            _chkPurge.Text = "Also delete saved settings, shared folder list && watch history";
            _chkPurge.Location = new Point(22, 98);
            _chkPurge.AutoSize = true;
            _chkPurge.ForeColor = textPrimary;
            this.Controls.Add(_chkPurge);

            Button btnConfirm = new Button();
            btnConfirm.Text = "Uninstall";
            btnConfirm.Location = new Point(196, 138);
            btnConfirm.Size = new Size(105, 34);
            btnConfirm.FlatStyle = FlatStyle.Flat;
            btnConfirm.FlatAppearance.BorderSize = 0;
            btnConfirm.BackColor = Color.FromArgb(220, 38, 38);
            btnConfirm.ForeColor = Color.White;
            btnConfirm.Font = new Font("Segoe UI", 9.5f, FontStyle.Bold);
            btnConfirm.Click += delegate { this.DialogResult = DialogResult.Yes; this.Close(); };
            this.Controls.Add(btnConfirm);

            Button btnCancel = new Button();
            btnCancel.Text = "Cancel";
            btnCancel.Location = new Point(311, 138);
            btnCancel.Size = new Size(95, 34);
            btnCancel.FlatStyle = FlatStyle.Flat;
            btnCancel.FlatAppearance.BorderSize = 0;
            btnCancel.BackColor = surfaceVar;
            btnCancel.ForeColor = textPrimary;
            btnCancel.Click += delegate { this.DialogResult = DialogResult.Cancel; this.Close(); };
            this.Controls.Add(btnCancel);

            this.CancelButton = btnCancel;
        }
    }

    public class MainForm : Form
    {
        private static readonly HashSet<string> VideoExtensions = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            ".mp4", ".mkv", ".webm", ".mov", ".avi", ".m4v", ".ts", ".ogv"
        };

        private static readonly HashSet<string> AudioExtensions = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            ".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg", ".opus"
        };

        private readonly JavaScriptSerializer _json = new JavaScriptSerializer();
        private readonly object _syncLock = new object();
        private readonly string _stateFilePath;
        private readonly List<string> _activityLog = new List<string>();

        private HostState _state;
        private List<MediaEntry> _catalog = new List<MediaEntry>();
        private Dictionary<string, MediaEntry> _catalogById = new Dictionary<string, MediaEntry>();
        private HttpListener _listener;
        private Thread _listenerThread;
        private UdpClient _udpDiscovery;
        private Thread _udpThread;
        private volatile bool _isRunning;
        private bool _openedBrowserOnce;
        private string _quickCode;

        // Cyber UI Controls
        private TextBox _txtServerName;
        private TextBox _txtPort;
        private TextBox _txtPin;
        private Label _lblStatusBadge;
        private Label _lblLanUrl;
        private Label _lblQuickCode;
        private Label _lblMediaCount;
        private Button _btnToggleServer;
        private Button _btnOpenMediaCenter;
        private Button _btnShowQr;
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
            if (_state.PairedDevices == null) _state.PairedDevices = new List<PairedDeviceInfo>();
            if (_state.Folders == null) _state.Folders = new List<string>();
            if (_state.ValidTokens == null) _state.ValidTokens = new List<string>();
            if (_state.Progress == null) _state.Progress = new Dictionary<string, ItemProgress>();

            if (_state.Folders.Count == 0)
            {
                string videos = Environment.GetFolderPath(Environment.SpecialFolder.MyVideos);
                string music = Environment.GetFolderPath(Environment.SpecialFolder.MyMusic);
                if (!string.IsNullOrEmpty(videos) && Directory.Exists(videos)) _state.Folders.Add(videos);
                if (!string.IsNullOrEmpty(music) && Directory.Exists(music)) _state.Folders.Add(music);
            }

            _quickCode = GenerateSixDigitCode();
            InitializeCyberUi();
            RescanLibrary();
            StartServer();
        }

        private static string GenerateSixDigitCode()
        {
            byte[] buf = new byte[4];
            using (RNGCryptoServiceProvider rng = new RNGCryptoServiceProvider())
            {
                rng.GetBytes(buf);
            }
            uint val = BitConverter.ToUInt32(buf, 0) % 1000000U;
            return val.ToString("D6");
        }

        private static string FormatSixDigitCode(string code)
        {
            string s = (code ?? "000000").PadLeft(6, '0');
            if (s.Length > 6) s = s.Substring(0, 6);
            return s.Substring(0, 3) + " " + s.Substring(3, 3);
        }

        private string GetQuickPairUri()
        {
            string lanUrl = GetLanUrl();
            string code;
            string name;
            lock (_syncLock)
            {
                code = _quickCode;
                name = _state.ServerName;
            }
            return "lootstream://pair?url=" + Uri.EscapeDataString(lanUrl) +
                   "&code=" + Uri.EscapeDataString(code) +
                   "&name=" + Uri.EscapeDataString(name);
        }

        private void RefreshQuickCode()
        {
            string next = GenerateSixDigitCode();
            lock (_syncLock)
            {
                _quickCode = next;
            }
            RunOnUi(delegate
            {
                if (_lblQuickCode != null) _lblQuickCode.Text = "Code: " + FormatSixDigitCode(next);
            });
            AppendLog("Generated new 6-digit Easy Login code: " + next);
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

            this.Text = "LootStream Windows Host v" + SelfInstaller.AppVersion;
            this.Size = new Size(780, 640);
            this.MinimumSize = new Size(700, 580);
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
            lblTitle.Text = "LOOTSTREAM WINDOWS HOST";
            lblTitle.Font = new Font("Segoe UI", 14.5f, FontStyle.Bold);
            lblTitle.ForeColor = cyan;
            lblTitle.Location = new Point(18, 12);
            lblTitle.AutoSize = true;

            Label lblSub = new Label();
            lblSub.Text = "Built-In Media Center (Movies, TV Shows, Music) + Android LAN Server";
            lblSub.Font = new Font("Segoe UI", 9f);
            lblSub.ForeColor = textSecondary;
            lblSub.Location = new Point(20, 42);
            lblSub.AutoSize = true;

            _btnOpenMediaCenter = new Button();
            _btnOpenMediaCenter.Text = "Open Media Center";
            _btnOpenMediaCenter.Size = new Size(150, 32);
            _btnOpenMediaCenter.Location = new Point(478, 22);
            _btnOpenMediaCenter.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            _btnOpenMediaCenter.FlatStyle = FlatStyle.Flat;
            _btnOpenMediaCenter.FlatAppearance.BorderSize = 0;
            _btnOpenMediaCenter.BackColor = green;
            _btnOpenMediaCenter.ForeColor = bg;
            _btnOpenMediaCenter.Font = new Font("Segoe UI", 9f, FontStyle.Bold);
            _btnOpenMediaCenter.Click += delegate { OpenMediaCenterInBrowser(); };

            _lblStatusBadge = new Label();
            _lblStatusBadge.Text = "STOPPED";
            _lblStatusBadge.Font = new Font("Segoe UI", 9.5f, FontStyle.Bold);
            _lblStatusBadge.ForeColor = textSecondary;
            _lblStatusBadge.BackColor = surfaceVar;
            _lblStatusBadge.TextAlign = ContentAlignment.MiddleCenter;
            _lblStatusBadge.Size = new Size(106, 32);
            _lblStatusBadge.Location = new Point(638, 22);
            _lblStatusBadge.Anchor = AnchorStyles.Top | AnchorStyles.Right;

            header.Controls.Add(lblTitle);
            header.Controls.Add(lblSub);
            header.Controls.Add(_btnOpenMediaCenter);
            header.Controls.Add(_lblStatusBadge);

            Panel body = new Panel();
            body.Dock = DockStyle.Fill;
            body.Padding = new Padding(18);

            // Connection & Server Config Card
            Panel cfgCard = new Panel();
            cfgCard.Location = new Point(18, 14);
            cfgCard.Size = new Size(726, 148);
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
            _btnToggleServer.Location = new Point(546, 32);
            _btnToggleServer.Size = new Size(162, 32);
            _btnToggleServer.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            _btnToggleServer.FlatStyle = FlatStyle.Flat;
            _btnToggleServer.FlatAppearance.BorderSize = 0;
            _btnToggleServer.BackColor = cyan;
            _btnToggleServer.ForeColor = bg;
            _btnToggleServer.Font = new Font("Segoe UI", 9.5f, FontStyle.Bold);
            _btnToggleServer.Click += delegate { ToggleServer(); };

            Label lblConnectHint = MakeLabel("Easy Android Wi-Fi Login (scan QR or enter 6-digit code in LootStream):", 14, 78, textSecondary);
            _lblQuickCode = new Label();
            _lblQuickCode.Text = "Code: " + FormatSixDigitCode(_quickCode);
            _lblQuickCode.Font = new Font("Consolas", 12.5f, FontStyle.Bold);
            _lblQuickCode.ForeColor = cyan;
            _lblQuickCode.Location = new Point(14, 98);
            _lblQuickCode.Size = new Size(168, 28);

            _lblLanUrl = new Label();
            _lblLanUrl.Text = GetLanUrl();
            _lblLanUrl.Font = new Font("Consolas", 10.5f, FontStyle.Bold);
            _lblLanUrl.ForeColor = green;
            _lblLanUrl.Location = new Point(188, 100);
            _lblLanUrl.Size = new Size(236, 26);

            _btnShowQr = new Button();
            _btnShowQr.Text = "QR Login";
            _btnShowQr.Location = new Point(430, 98);
            _btnShowQr.Size = new Size(88, 30);
            _btnShowQr.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            _btnShowQr.FlatStyle = FlatStyle.Flat;
            _btnShowQr.FlatAppearance.BorderColor = green;
            _btnShowQr.BackColor = surfaceVar;
            _btnShowQr.ForeColor = green;
            _btnShowQr.Font = new Font("Segoe UI", 8.5f, FontStyle.Bold);
            _btnShowQr.Click += delegate { ShowQrLoginDialog(); };

            _btnCopyUrl = new Button();
            _btnCopyUrl.Text = "Copy URL";
            _btnCopyUrl.Location = new Point(526, 98);
            _btnCopyUrl.Size = new Size(86, 30);
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
            _lblMediaCount.Font = new Font("Segoe UI", 9.5f, FontStyle.Bold);
            _lblMediaCount.ForeColor = purple;
            _lblMediaCount.TextAlign = ContentAlignment.MiddleRight;
            _lblMediaCount.Location = new Point(618, 100);
            _lblMediaCount.Size = new Size(90, 26);
            _lblMediaCount.Anchor = AnchorStyles.Top | AnchorStyles.Right;

            cfgCard.Controls.Add(lblName);
            cfgCard.Controls.Add(_txtServerName);
            cfgCard.Controls.Add(lblPort);
            cfgCard.Controls.Add(_txtPort);
            cfgCard.Controls.Add(lblPin);
            cfgCard.Controls.Add(_txtPin);
            cfgCard.Controls.Add(_btnToggleServer);
            cfgCard.Controls.Add(lblConnectHint);
            cfgCard.Controls.Add(_lblQuickCode);
            cfgCard.Controls.Add(_lblLanUrl);
            cfgCard.Controls.Add(_btnShowQr);
            cfgCard.Controls.Add(_btnCopyUrl);
            cfgCard.Controls.Add(_lblMediaCount);

            // Shared Folders Card
            Panel foldersCard = new Panel();
            foldersCard.Location = new Point(18, 174);
            foldersCard.Size = new Size(726, 170);
            foldersCard.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
            foldersCard.BackColor = surface;

            Label lblFolders = MakeLabel("Shared Media Folders (Movies, TV Shows, Music):", 14, 12, textPrimary);
            lblFolders.Font = new Font("Segoe UI", 9.5f, FontStyle.Bold);

            _lstFolders = new ListBox();
            _lstFolders.Location = new Point(14, 38);
            _lstFolders.Size = new Size(560, 116);
            _lstFolders.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right | AnchorStyles.Bottom;
            _lstFolders.BackColor = surfaceVar;
            _lstFolders.ForeColor = textPrimary;
            _lstFolders.BorderStyle = BorderStyle.FixedSingle;
            RefreshFolderList();

            Button btnAddFolder = new Button();
            btnAddFolder.Text = "+ Add Folder";
            btnAddFolder.Location = new Point(588, 38);
            btnAddFolder.Size = new Size(122, 32);
            btnAddFolder.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnAddFolder.FlatStyle = FlatStyle.Flat;
            btnAddFolder.FlatAppearance.BorderSize = 0;
            btnAddFolder.BackColor = green;
            btnAddFolder.ForeColor = bg;
            btnAddFolder.Font = new Font("Segoe UI", 9f, FontStyle.Bold);
            btnAddFolder.Click += delegate { AddFolder(); };

            Button btnRemoveFolder = new Button();
            btnRemoveFolder.Text = "Remove Selected";
            btnRemoveFolder.Location = new Point(588, 78);
            btnRemoveFolder.Size = new Size(122, 32);
            btnRemoveFolder.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnRemoveFolder.FlatStyle = FlatStyle.Flat;
            btnRemoveFolder.FlatAppearance.BorderColor = Color.FromArgb(239, 68, 68);
            btnRemoveFolder.BackColor = surfaceVar;
            btnRemoveFolder.ForeColor = Color.FromArgb(248, 113, 113);
            btnRemoveFolder.Click += delegate { RemoveSelectedFolder(); };

            Button btnRescan = new Button();
            btnRescan.Text = "Rescan Library";
            btnRescan.Location = new Point(588, 118);
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
            _txtLog.Size = new Size(726, 130);
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

        private void OpenMediaCenterInBrowser()
        {
            string url = string.Format("http://127.0.0.1:{0}/", _state.Port);
            try
            {
                string edge = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86),
                    @"Microsoft\Edge\Application\msedge.exe"
                );
                if (File.Exists(edge))
                {
                    Process.Start(edge, "--app=" + url);
                    return;
                }
            }
            catch { }

            try
            {
                Process.Start(new ProcessStartInfo { FileName = url, UseShellExecute = true });
            }
            catch { }
        }

        private void ShowQrLoginDialog()
        {
            Form dlg = new Form();
            dlg.Text = "Easy Wi-Fi Android Login";
            dlg.Size = new Size(380, 470);
            dlg.FormBorderStyle = FormBorderStyle.FixedDialog;
            dlg.MaximizeBox = false;
            dlg.MinimizeBox = false;
            dlg.StartPosition = FormStartPosition.CenterParent;
            dlg.BackColor = Color.FromArgb(11, 15, 25);
            dlg.ForeColor = Color.FromArgb(241, 245, 249);

            PictureBox pic = new PictureBox();
            pic.Size = new Size(240, 240);
            pic.Location = new Point(62, 20);
            pic.SizeMode = PictureBoxSizeMode.Zoom;
            pic.BackColor = Color.White;

            Label lblCode = new Label();
            lblCode.Font = new Font("Consolas", 22f, FontStyle.Bold);
            lblCode.ForeColor = Color.FromArgb(0, 255, 135);
            lblCode.TextAlign = ContentAlignment.MiddleCenter;
            lblCode.Location = new Point(20, 274);
            lblCode.Size = new Size(324, 40);

            Label lblInfo = new Label();
            lblInfo.Text = "Scan this QR code or enter the 6-digit code\nin LootStream Android on the same Wi-Fi.";
            lblInfo.Font = new Font("Segoe UI", 9.5f);
            lblInfo.ForeColor = Color.FromArgb(148, 163, 184);
            lblInfo.TextAlign = ContentAlignment.MiddleCenter;
            lblInfo.Location = new Point(20, 320);
            lblInfo.Size = new Size(324, 42);

            Button btnNew = new Button();
            btnNew.Text = "Generate New Code";
            btnNew.Size = new Size(160, 34);
            btnNew.Location = new Point(102, 376);
            btnNew.FlatStyle = FlatStyle.Flat;
            btnNew.FlatAppearance.BorderSize = 0;
            btnNew.BackColor = Color.FromArgb(0, 229, 255);
            btnNew.ForeColor = Color.FromArgb(11, 15, 25);
            btnNew.Font = new Font("Segoe UI", 9.5f, FontStyle.Bold);

            Action renderDialog = delegate
            {
                if (pic.Image != null) pic.Image.Dispose();
                pic.Image = QrEncoder.RenderBitmap(GetQuickPairUri(), 6);
                lblCode.Text = FormatSixDigitCode(_quickCode);
            };
            renderDialog();
            btnNew.Click += delegate
            {
                RefreshQuickCode();
                renderDialog();
            };

            dlg.FormClosed += delegate
            {
                if (pic.Image != null) pic.Image.Dispose();
            };

            dlg.Controls.Add(pic);
            dlg.Controls.Add(lblCode);
            dlg.Controls.Add(lblInfo);
            dlg.Controls.Add(btnNew);
            dlg.ShowDialog(this);
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

        private string PickFolderDialogUiThread()
        {
            string chosen = null;
            Action showAction = delegate
            {
                using (FolderBrowserDialog dlg = new FolderBrowserDialog())
                {
                    dlg.Description = "Select a folder containing movies, TV shows, videos, or music";
                    if (dlg.ShowDialog() == DialogResult.OK && !string.IsNullOrEmpty(dlg.SelectedPath))
                    {
                        chosen = dlg.SelectedPath;
                    }
                }
            };
            if (this.InvokeRequired) this.Invoke(showAction);
            else showAction();
            return chosen;
        }

        private void AddFolder()
        {
            string picked = PickFolderDialogUiThread();
            if (!string.IsNullOrEmpty(picked))
            {
                AddFolderPath(picked);
            }
        }

        private bool AddFolderPath(string path)
        {
            if (string.IsNullOrEmpty(path) || !Directory.Exists(path)) return false;
            string full = Path.GetFullPath(path.Trim());
            lock (_syncLock)
            {
                if (!_state.Folders.Contains(full)) _state.Folders.Add(full);
                SaveState();
            }
            RunOnUi(delegate { RefreshFolderList(); });
            RescanLibrary();
            AppendLog("Added media folder: " + full);
            return true;
        }

        private void RemoveFolderPath(string path)
        {
            if (string.IsNullOrEmpty(path)) return;
            lock (_syncLock)
            {
                _state.Folders.Remove(path);
                SaveState();
            }
            RunOnUi(delegate { RefreshFolderList(); });
            RescanLibrary();
            AppendLog("Removed media folder: " + path);
        }

        private void RemoveSelectedFolder()
        {
            if (_lstFolders.SelectedIndex >= 0)
            {
                RemoveFolderPath(_lstFolders.SelectedItem.ToString());
            }
        }

        private void RunOnUi(Action action)
        {
            if (this.IsDisposed || !this.IsHandleCreated) return;
            try
            {
                if (this.InvokeRequired) this.BeginInvoke(action);
                else action();
            }
            catch { }
        }

        private void ToggleServer()
        {
            if (_isRunning) StopServer();
            else StartServer();
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
                StartUdpDiscoveryResponder();

                _lblStatusBadge.Text = "ONLINE";
                _lblStatusBadge.ForeColor = Color.FromArgb(0, 255, 135);
                _btnToggleServer.Text = "Stop Server";
                _btnToggleServer.BackColor = Color.FromArgb(239, 68, 68);
                _btnToggleServer.ForeColor = Color.White;
                _lblLanUrl.Text = GetLanUrl();
                AppendLog("Server started on " + _lblLanUrl.Text + " (" + _catalog.Count + " media items)");

                if (!_openedBrowserOnce)
                {
                    _openedBrowserOnce = true;
                    OpenMediaCenterInBrowser();
                }
            }
            catch (Exception ex)
            {
                _isRunning = false;
                _lblStatusBadge.Text = "ERROR";
                _lblStatusBadge.ForeColor = Color.FromArgb(248, 113, 113);
                AppendLog("Failed to start server: " + ex.Message);
            }
        }

        private void StartUdpDiscoveryResponder()
        {
            try
            {
                _udpDiscovery = new UdpClient();
                _udpDiscovery.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
                _udpDiscovery.Client.Bind(new IPEndPoint(IPAddress.Any, 8766));
                _udpDiscovery.EnableBroadcast = true;
                try
                {
                    _udpDiscovery.JoinMulticastGroup(IPAddress.Parse("239.255.87.66"));
                }
                catch { }

                _udpThread = new Thread(delegate
                {
                    while (_isRunning && _udpDiscovery != null)
                    {
                        try
                        {
                            IPEndPoint remote = new IPEndPoint(IPAddress.Any, 0);
                            byte[] data = _udpDiscovery.Receive(ref remote);
                            string msg = Encoding.UTF8.GetString(data).Trim();
                            if (!msg.StartsWith("LOOTSTREAM_DISCOVER", StringComparison.Ordinal)) continue;

                            List<string> urls = new List<string>();
                            foreach (string ip in GetAllLocalIpv4Addresses())
                            {
                                urls.Add(string.Format("http://{0}:{1}", ip, _state.Port));
                            }
                            if (urls.Count == 0) urls.Add(string.Format("http://127.0.0.1:{0}", _state.Port));

                            Dictionary<string, object> resp = new Dictionary<string, object>();
                            lock (_syncLock)
                            {
                                resp["service"] = "lootstream-host";
                                resp["serverId"] = _state.ServerId;
                                resp["serverName"] = _state.ServerName;
                                resp["hostPlatform"] = "Windows";
                                resp["port"] = _state.Port;
                                resp["urls"] = urls;
                                resp["requiresPin"] = !string.IsNullOrEmpty(_state.Pin);
                                resp["quickCodeActive"] = true;
                            }
                            byte[] bytes = Encoding.UTF8.GetBytes(_json.Serialize(resp));
                            _udpDiscovery.Send(bytes, bytes.Length, remote);
                        }
                        catch
                        {
                            if (!_isRunning) break;
                        }
                    }
                });
                _udpThread.IsBackground = true;
                _udpThread.Start();
            }
            catch { }
        }

        private void StopServer()
        {
            _isRunning = false;
            try
            {
                if (_udpDiscovery != null)
                {
                    _udpDiscovery.Close();
                    _udpDiscovery = null;
                }
            }
            catch { }
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

            RunOnUi(delegate
            {
                _lblStatusBadge.Text = "STOPPED";
                _lblStatusBadge.ForeColor = Color.FromArgb(148, 163, 184);
                _btnToggleServer.Text = "Start Server";
                _btnToggleServer.BackColor = Color.FromArgb(0, 229, 255);
                _btnToggleServer.ForeColor = Color.FromArgb(11, 15, 25);
            });
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
                // Built-in Ampchor-inspired Media Center Web UI (embedded in .exe)
                if (method == "GET" && (path == "/" || path == "/index.html"))
                {
                    ServeEmbeddedAsset(res, "index.html", "text/html; charset=utf-8");
                    return;
                }
                if (method == "GET" && path == "/styles.css")
                {
                    ServeEmbeddedAsset(res, "styles.css", "text/css; charset=utf-8");
                    return;
                }
                if (method == "GET" && path == "/app.js")
                {
                    ServeEmbeddedAsset(res, "app.js", "application/javascript; charset=utf-8");
                    return;
                }

                // Shared LootStream API
                if (method == "GET" && path == "/api/info")
                {
                    Dictionary<string, object> info = new Dictionary<string, object>();
                    lock (_syncLock)
                    {
                        info["serverId"] = _state.ServerId;
                        info["serverName"] = _state.ServerName;
                        info["version"] = SelfInstaller.AppVersion;
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
                    string quickCode = payload.ContainsKey("quickCode") && payload["quickCode"] != null ? payload["quickCode"].ToString().Trim() : "";
                    string deviceName = payload.ContainsKey("deviceName") && payload["deviceName"] != null ? payload["deviceName"].ToString() : "Android";

                    Dictionary<string, object> result = new Dictionary<string, object>();
                    lock (_syncLock)
                    {
                        string invalidReason = "";
                        if (!string.IsNullOrEmpty(quickCode))
                        {
                            if (!string.Equals(_quickCode, quickCode, StringComparison.Ordinal))
                            {
                                invalidReason = "Incorrect 6-digit code";
                            }
                        }
                        else if (!string.IsNullOrEmpty(_state.Pin) && !string.Equals(_state.Pin, submittedPin, StringComparison.Ordinal))
                        {
                            invalidReason = "Incorrect pairing PIN";
                        }

                        if (!string.IsNullOrEmpty(invalidReason))
                        {
                            AppendLog("Rejected pairing from " + deviceName + " (" + invalidReason + ")");
                            WriteJson(res, 401, new Dictionary<string, object> { { "error", invalidReason } });
                            return;
                        }
                        string token = Guid.NewGuid().ToString("N");
                        if (!_state.ValidTokens.Contains(token)) _state.ValidTokens.Add(token);
                        _state.PairedDevices.Add(new PairedDeviceInfo
                        {
                            deviceName = deviceName,
                            pairedAt = ToUnixMs(DateTime.UtcNow)
                        });
                        if (_state.PairedDevices.Count > 25)
                        {
                            _state.PairedDevices.RemoveRange(0, _state.PairedDevices.Count - 25);
                        }
                        SaveState();

                        result["token"] = token;
                        result["serverId"] = _state.ServerId;
                        result["serverName"] = _state.ServerName;
                        result["version"] = SelfInstaller.AppVersion;
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

                    List<Dictionary<string, object>> itemsOut = BuildPublicItems();
                    string serverId;
                    string serverName;
                    lock (_syncLock)
                    {
                        serverId = _state.ServerId;
                        serverName = _state.ServerName;
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

                if (method == "GET" && path.StartsWith("/api/artwork/"))
                {
                    string itemId = Uri.UnescapeDataString(path.Substring("/api/artwork/".Length));
                    string kind = req.QueryString["kind"] ?? "poster";
                    MediaEntry entry = null;
                    lock (_syncLock)
                    {
                        if (_catalogById.ContainsKey(itemId)) entry = _catalogById[itemId];
                    }
                    if (entry == null)
                    {
                        res.StatusCode = 404;
                        res.Close();
                        return;
                    }
                    string artPath = kind == "backdrop" ? entry.BackdropPath : entry.PosterPath;
                    if (string.IsNullOrEmpty(artPath)) artPath = !string.IsNullOrEmpty(entry.PosterPath) ? entry.PosterPath : entry.BackdropPath;
                    if (string.IsNullOrEmpty(artPath) || !File.Exists(artPath))
                    {
                        res.StatusCode = 404;
                        res.Close();
                        return;
                    }
                    byte[] artBytes = File.ReadAllBytes(artPath);
                    res.StatusCode = 200;
                    res.ContentType = GetMimeType(Path.GetExtension(artPath));
                    res.ContentLength64 = artBytes.Length;
                    res.OutputStream.Write(artBytes, 0, artBytes.Length);
                    res.OutputStream.Flush();
                    res.Close();
                    return;
                }

                if (method == "POST" && path.StartsWith("/api/items/"))
                {
                    bool isLocal = req.RemoteEndPoint != null && IPAddress.IsLoopback(req.RemoteEndPoint.Address);
                    if (!isLocal && !IsAuthorized(req))
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
                            prog.LastPlayedAt = ToUnixMs(DateTime.UtcNow);
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

                // Host Web UI Management Endpoints
                if (method == "GET" && path == "/api/host/state")
                {
                    List<Dictionary<string, object>> items = BuildPublicItems();
                    List<string> lanUrls = new List<string>();
                    foreach (string ip in GetAllLocalIpv4Addresses())
                    {
                        lanUrls.Add(string.Format("http://{0}:{1}", ip, _state.Port));
                    }
                    if (lanUrls.Count == 0) lanUrls.Add(string.Format("http://127.0.0.1:{0}", _state.Port));

                    Dictionary<string, object> stateResp = new Dictionary<string, object>();
                    string pairUri = GetQuickPairUri();
                    lock (_syncLock)
                    {
                        stateResp["serverId"] = _state.ServerId;
                        stateResp["serverName"] = _state.ServerName;
                        stateResp["hostPlatform"] = "Windows";
                        stateResp["port"] = _state.Port;
                        stateResp["requiresPin"] = !string.IsNullOrEmpty(_state.Pin);
                        stateResp["pin"] = _state.Pin ?? "";
                        stateResp["quickCode"] = _quickCode;
                        stateResp["quickPairUri"] = pairUri;
                        stateResp["lanUrls"] = lanUrls;
                        stateResp["folders"] = new List<string>(_state.Folders);
                        stateResp["pairedDevices"] = new List<PairedDeviceInfo>(_state.PairedDevices);
                        stateResp["activityLog"] = new List<string>(_activityLog);
                        stateResp["ffprobeAvailable"] = false;
                        stateResp["items"] = items;
                    }
                    WriteJson(res, 200, stateResp);
                    return;
                }

                if (method == "GET" && path == "/api/host/browse-dirs")
                {
                    string rawDir = req.QueryString["path"];
                    string userHome = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
                    string target = string.IsNullOrEmpty(rawDir) ? userHome : rawDir;
                    List<Dictionary<string, string>> dirs = new List<Dictionary<string, string>>();
                    string parentPath = null;

                    if (string.Equals(target, "__DRIVES__", StringComparison.Ordinal))
                    {
                        foreach (DriveInfo d in DriveInfo.GetDrives())
                        {
                            if (d.IsReady)
                            {
                                dirs.Add(new Dictionary<string, string> { { "name", d.Name }, { "path", d.Name } });
                            }
                        }
                    }
                    else
                    {
                        if (!Directory.Exists(target)) target = userHome;
                        try
                        {
                            string[] subdirs = Directory.GetDirectories(target);
                            Array.Sort(subdirs, StringComparer.OrdinalIgnoreCase);
                            foreach (string sd in subdirs)
                            {
                                DirectoryInfo di = new DirectoryInfo(sd);
                                if ((di.Attributes & FileAttributes.Hidden) != 0) continue;
                                dirs.Add(new Dictionary<string, string> { { "name", di.Name }, { "path", di.FullName } });
                                if (dirs.Count >= 150) break;
                            }
                        }
                        catch { }
                        DirectoryInfo parent = Directory.GetParent(target);
                        parentPath = parent != null ? parent.FullName : "__DRIVES__";
                    }

                    WriteJson(res, 200, new Dictionary<string, object>
                    {
                        { "currentPath", target },
                        { "parentPath", parentPath },
                        { "directories", dirs }
                    });
                    return;
                }

                if (method == "POST" && path == "/api/host/folders")
                {
                    Dictionary<string, object> payload = ParseJsonObject(ReadBody(req));
                    string folder = payload.ContainsKey("path") && payload["path"] != null ? payload["path"].ToString().Trim() : "";
                    bool ok = AddFolderPath(folder);
                    WriteJson(res, ok ? 200 : 400, new Dictionary<string, object>
                    {
                        { "ok", ok },
                        { "message", ok ? folder : "Directory does not exist: " + folder }
                    });
                    return;
                }

                if (method == "POST" && path == "/api/host/folders/remove")
                {
                    Dictionary<string, object> payload = ParseJsonObject(ReadBody(req));
                    string folder = payload.ContainsKey("path") && payload["path"] != null ? payload["path"].ToString().Trim() : "";
                    RemoveFolderPath(folder);
                    WriteJson(res, 200, new Dictionary<string, object> { { "ok", true } });
                    return;
                }

                if (method == "POST" && path == "/api/host/rescan")
                {
                    RescanLibrary();
                    WriteJson(res, 200, new Dictionary<string, object>
                    {
                        { "ok", true },
                        { "itemCount", _catalog.Count }
                    });
                    return;
                }

                if (method == "POST" && path == "/api/host/config")
                {
                    Dictionary<string, object> payload = ParseJsonObject(ReadBody(req));
                    lock (_syncLock)
                    {
                        if (payload.ContainsKey("serverName") && payload["serverName"] != null && !string.IsNullOrEmpty(payload["serverName"].ToString().Trim()))
                        {
                            _state.ServerName = payload["serverName"].ToString().Trim();
                        }
                        if (payload.ContainsKey("pin") && payload["pin"] != null)
                        {
                            _state.Pin = payload["pin"].ToString().Trim();
                        }
                        SaveState();
                    }
                    RunOnUi(delegate
                    {
                        _txtServerName.Text = _state.ServerName;
                        _txtPin.Text = _state.Pin;
                    });
                    AppendLog("Updated host configuration.");
                    WriteJson(res, 200, new Dictionary<string, object> { { "ok", true } });
                    return;
                }

                if (method == "POST" && path == "/api/host/quick-code/refresh")
                {
                    RefreshQuickCode();
                    WriteJson(res, 200, new Dictionary<string, object>
                    {
                        { "ok", true },
                        { "quickCode", _quickCode },
                        { "quickPairUri", GetQuickPairUri() }
                    });
                    return;
                }

                if (method == "POST" && path == "/api/host/pick-folder")
                {
                    string picked = PickFolderDialogUiThread();
                    if (!string.IsNullOrEmpty(picked) && AddFolderPath(picked))
                    {
                        WriteJson(res, 200, new Dictionary<string, object> { { "ok", true }, { "path", picked } });
                    }
                    else
                    {
                        WriteJson(res, 200, new Dictionary<string, object> { { "ok", false }, { "cancelled", true } });
                    }
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

        private List<Dictionary<string, object>> BuildPublicItems()
        {
            List<Dictionary<string, object>> itemsOut = new List<Dictionary<string, object>>();
            lock (_syncLock)
            {
                string serverName = _state.ServerName;
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
                    d["posterUrl"] = !string.IsNullOrEmpty(entry.PosterPath) ? "/api/artwork/" + entry.Id + "?kind=poster" : "";
                    d["backdropUrl"] = !string.IsNullOrEmpty(entry.BackdropPath) ? "/api/artwork/" + entry.Id + "?kind=backdrop" : "";
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
            return itemsOut;
        }

        private static void ServeEmbeddedAsset(HttpListenerResponse res, string assetName, string contentType)
        {
            Assembly asm = Assembly.GetExecutingAssembly();
            Stream stream = asm.GetManifestResourceStream(assetName);
            if (stream == null)
            {
                foreach (string resName in asm.GetManifestResourceNames())
                {
                    if (resName.EndsWith("." + assetName, StringComparison.OrdinalIgnoreCase) ||
                        string.Equals(resName, assetName, StringComparison.OrdinalIgnoreCase))
                    {
                        stream = asm.GetManifestResourceStream(resName);
                        break;
                    }
                }
            }

            if (stream == null)
            {
                byte[] fallback = Encoding.UTF8.GetBytes("<html><body><h1>LootStream Windows Host</h1><p>Embedded Web UI asset missing: " + assetName + "</p></body></html>");
                res.StatusCode = 404;
                res.ContentType = "text/html; charset=utf-8";
                res.ContentLength64 = fallback.Length;
                res.OutputStream.Write(fallback, 0, fallback.Length);
                res.Close();
                return;
            }

            using (stream)
            using (MemoryStream ms = new MemoryStream())
            {
                stream.CopyTo(ms);
                byte[] bytes = ms.ToArray();
                res.StatusCode = 200;
                res.ContentType = contentType;
                res.ContentLength64 = bytes.Length;
                res.OutputStream.Write(bytes, 0, bytes.Length);
                res.OutputStream.Flush();
                res.Close();
            }
        }

        private bool IsAuthorized(HttpListenerRequest req)
        {
            lock (_syncLock)
            {
                if (string.IsNullOrEmpty(_state.Pin) && _state.ValidTokens.Count == 0) return true;
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

        private static void FindLocalArtwork(string filePath, out string posterPath, out string backdropPath)
        {
            posterPath = null;
            backdropPath = null;
            string dir = Path.GetDirectoryName(filePath);
            if (string.IsNullOrEmpty(dir)) return;
            string stem = Path.GetFileNameWithoutExtension(filePath);

            string[] posterCandidates = new string[]
            {
                stem + ".jpg", stem + ".png", stem + "-poster.jpg", stem + "-poster.png",
                "poster.jpg", "poster.png", "cover.jpg", "cover.png", "folder.jpg", "folder.png", "album.jpg", "front.jpg"
            };
            foreach (string c in posterCandidates)
            {
                string full = Path.Combine(dir, c);
                if (File.Exists(full)) { posterPath = full; break; }
            }
            if (posterPath == null)
            {
                DirectoryInfo parent = Directory.GetParent(dir);
                if (parent != null)
                {
                    foreach (string c in new string[] { "poster.jpg", "poster.png", "cover.jpg", "folder.jpg" })
                    {
                        string full = Path.Combine(parent.FullName, c);
                        if (File.Exists(full)) { posterPath = full; break; }
                    }
                }
            }

            string[] backdropCandidates = new string[]
            {
                stem + "-fanart.jpg", stem + "-backdrop.jpg", "backdrop.jpg", "backdrop.png", "fanart.jpg", "fanart.png"
            };
            foreach (string c in backdropCandidates)
            {
                string full = Path.Combine(dir, c);
                if (File.Exists(full)) { backdropPath = full; break; }
            }
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
                    Array.Sort(files, StringComparer.OrdinalIgnoreCase);
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
                        else if (isVideo)
                        {
                            string lowerPath = file.ToLowerInvariant();
                            if (lowerPath.Contains(@"\tv\") || lowerPath.Contains(@"\shows\") || lowerPath.Contains(@"\series\") || lowerPath.Contains(@"\anime\"))
                            {
                                mediaType = "SERIES";
                            }
                            else
                            {
                                mediaType = "MOVIE";
                            }
                        }

                        string cleanTitle = Regex.Replace(rawName, @"[._]+", " ").Trim();
                        string parentName = fi.Directory != null ? fi.Directory.Name : "Windows PC";
                        string posterPath;
                        string backdropPath;
                        FindLocalArtwork(file, out posterPath, out backdropPath);

                        MediaEntry entry = new MediaEntry
                        {
                            Id = id,
                            FilePath = file,
                            PosterPath = posterPath ?? "",
                            BackdropPath = backdropPath ?? "",
                            Title = cleanTitle,
                            Subtitle = !string.IsNullOrEmpty(seriesName) ? seriesName : parentName,
                            MediaType = mediaType,
                            CodecBadge = ext.TrimStart('.').ToUpperInvariant(),
                            ReleaseYear = year,
                            SeriesName = seriesName,
                            SeasonNumber = season,
                            EpisodeNumber = episode,
                            FileSizeBytes = fi.Length,
                            AddedAt = ToUnixMs(fi.LastWriteTimeUtc)
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
            RunOnUi(delegate { _lblMediaCount.Text = scanned.Count + " items"; });
            AppendLog("Library scan complete: " + scanned.Count + " playable files found.");
        }

        private static readonly DateTime UnixEpochUtc = new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc);

        private static long ToUnixMs(DateTime utcTime)
        {
            return (long)(utcTime.ToUniversalTime() - UnixEpochUtc).TotalMilliseconds;
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
                case ".ogv": return "video/ogg";
                case ".mp3": return "audio/mpeg";
                case ".flac": return "audio/flac";
                case ".wav": return "audio/wav";
                case ".m4a":
                case ".aac": return "audio/mp4";
                case ".ogg": return "audio/ogg";
                case ".opus": return "audio/opus";
                case ".jpg":
                case ".jpeg": return "image/jpeg";
                case ".png": return "image/png";
                case ".webp": return "image/webp";
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
            string line = string.Format("[{0:HH:mm:ss}] {1}", DateTime.Now, message);
            lock (_syncLock)
            {
                _activityLog.Add(line);
                if (_activityLog.Count > 120) _activityLog.RemoveAt(0);
            }
            RunOnUi(delegate
            {
                if (_txtLog != null && !_txtLog.IsDisposed)
                {
                    _txtLog.AppendText(line + Environment.NewLine);
                }
            });
        }

        [STAThread]
        public static void Main(string[] args)
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            if (!SelfInstaller.HandleInstallerArgsOrPrompt(args))
            {
                return;
            }
            Application.Run(new MainForm());
        }
    }

    internal static class QrEncoder
    {
        private sealed class QrSpec
        {
            public readonly int Size;
            public readonly int DataCw;
            public readonly int EccPerBlock;
            public readonly int G1Blocks;
            public readonly int G1Data;
            public readonly int G2Blocks;
            public readonly int G2Data;
            public readonly int[] Align;

            public QrSpec(int size, int dataCw, int eccPerBlock, int g1Blocks, int g1Data, int g2Blocks, int g2Data, int[] align)
            {
                Size = size;
                DataCw = dataCw;
                EccPerBlock = eccPerBlock;
                G1Blocks = g1Blocks;
                G1Data = g1Data;
                G2Blocks = g2Blocks;
                G2Data = g2Data;
                Align = align;
            }
        }

        private static readonly QrSpec[] SpecsM = new QrSpec[]
        {
            null,
            new QrSpec(21, 16, 10, 1, 16, 0, 0, new int[0]),
            new QrSpec(25, 28, 16, 1, 28, 0, 0, new int[] { 6, 18 }),
            new QrSpec(29, 44, 26, 1, 44, 0, 0, new int[] { 6, 22 }),
            new QrSpec(33, 64, 18, 2, 32, 0, 0, new int[] { 6, 26 }),
            new QrSpec(37, 86, 24, 2, 43, 0, 0, new int[] { 6, 30 }),
            new QrSpec(41, 108, 16, 4, 27, 0, 0, new int[] { 6, 34 }),
            new QrSpec(45, 124, 18, 4, 31, 0, 0, new int[] { 6, 22, 38 }),
            new QrSpec(49, 154, 22, 2, 38, 2, 39, new int[] { 6, 24, 42 }),
            new QrSpec(53, 182, 22, 3, 36, 2, 37, new int[] { 6, 26, 46 })
        };

        private static readonly byte[] GfExp = new byte[512];
        private static readonly byte[] GfLog = new byte[256];

        static QrEncoder()
        {
            int x = 1;
            for (int i = 0; i < 255; i++)
            {
                GfExp[i] = (byte)x;
                GfLog[x] = (byte)i;
                x <<= 1;
                if ((x & 0x100) != 0) x ^= 0x11d;
            }
            for (int i = 255; i < 512; i++)
            {
                GfExp[i] = GfExp[i - 255];
            }
        }

        private static byte GfMul(byte a, byte b)
        {
            if (a == 0 || b == 0) return 0;
            return GfExp[GfLog[a] + GfLog[b]];
        }

        private static byte[] RsGenerator(int deg)
        {
            byte[] poly = new byte[] { 1 };
            for (int i = 0; i < deg; i++)
            {
                byte[] next = new byte[poly.Length + 1];
                byte factor = GfExp[i];
                for (int j = 0; j < poly.Length; j++)
                {
                    next[j] ^= poly[j];
                    next[j + 1] ^= GfMul(poly[j], factor);
                }
                poly = next;
            }
            return poly;
        }

        private static byte[] RsRemainder(byte[] data, int eccLen)
        {
            byte[] gen = RsGenerator(eccLen);
            byte[] res = new byte[data.Length + eccLen];
            Array.Copy(data, 0, res, 0, data.Length);
            for (int i = 0; i < data.Length; i++)
            {
                byte coef = res[i];
                if (coef != 0)
                {
                    for (int j = 0; j < gen.Length; j++)
                    {
                        res[i + j] ^= GfMul(gen[j], coef);
                    }
                }
            }
            byte[] ecc = new byte[eccLen];
            Array.Copy(res, data.Length, ecc, 0, eccLen);
            return ecc;
        }

        private static void PushBits(List<int> bits, int val, int len)
        {
            for (int i = len - 1; i >= 0; i--)
            {
                bits.Add((val >> i) & 1);
            }
        }

        public static bool[,] BuildMatrix(string text)
        {
            byte[] raw = Encoding.UTF8.GetBytes(text ?? "");
            int ver = 1;
            while (ver < SpecsM.Length && raw.Length + 2 > SpecsM[ver].DataCw)
            {
                ver++;
            }
            if (ver >= SpecsM.Length) ver = SpecsM.Length - 1;
            QrSpec spec = SpecsM[ver];
            int payloadLen = Math.Min(raw.Length, spec.DataCw - 2);

            List<int> bits = new List<int>();
            PushBits(bits, 0x4, 4); // Byte mode indicator
            PushBits(bits, payloadLen, 8);
            for (int i = 0; i < payloadLen; i++)
            {
                PushBits(bits, raw[i], 8);
            }
            int capBits = spec.DataCw * 8;
            PushBits(bits, 0, Math.Min(4, capBits - bits.Count));
            while (bits.Count % 8 != 0) bits.Add(0);

            List<byte> dataCw = new List<byte>();
            for (int i = 0; i < bits.Count; i += 8)
            {
                int v = 0;
                for (int j = 0; j < 8; j++)
                {
                    v = (v << 1) | bits[i + j];
                }
                dataCw.Add((byte)v);
            }
            byte[] pads = new byte[] { 0xec, 0x11 };
            int pi = 0;
            while (dataCw.Count < spec.DataCw)
            {
                dataCw.Add(pads[pi % 2]);
                pi++;
            }

            List<byte[]> blocks = new List<byte[]>();
            List<byte[]> eccBlocks = new List<byte[]>();
            int offset = 0;
            for (int b = 0; b < spec.G1Blocks; b++)
            {
                byte[] slice = new byte[spec.G1Data];
                dataCw.CopyTo(offset, slice, 0, spec.G1Data);
                offset += spec.G1Data;
                blocks.Add(slice);
                eccBlocks.Add(RsRemainder(slice, spec.EccPerBlock));
            }
            for (int b = 0; b < spec.G2Blocks; b++)
            {
                byte[] slice = new byte[spec.G2Data];
                dataCw.CopyTo(offset, slice, 0, spec.G2Data);
                offset += spec.G2Data;
                blocks.Add(slice);
                eccBlocks.Add(RsRemainder(slice, spec.EccPerBlock));
            }

            List<byte> interleaved = new List<byte>();
            int maxDataLen = Math.Max(spec.G1Data, spec.G2Data);
            for (int i = 0; i < maxDataLen; i++)
            {
                for (int b = 0; b < blocks.Count; b++)
                {
                    if (i < blocks[b].Length) interleaved.Add(blocks[b][i]);
                }
            }
            for (int i = 0; i < spec.EccPerBlock; i++)
            {
                for (int b = 0; b < eccBlocks.Count; b++)
                {
                    if (i < eccBlocks[b].Length) interleaved.Add(eccBlocks[b][i]);
                }
            }

            int n = spec.Size;
            bool[,] mod = new bool[n, n];
            bool[,] isFunc = new bool[n, n];

            Action<int, int, bool> setFn = delegate(int r, int c, bool val)
            {
                if (r >= 0 && r < n && c >= 0 && c < n)
                {
                    mod[r, c] = val;
                    isFunc[r, c] = true;
                }
            };

            Action<int, int> drawFinder = delegate(int top, int left)
            {
                for (int dr = -1; dr <= 7; dr++)
                {
                    for (int dc = -1; dc <= 7; dc++)
                    {
                        int r = top + dr;
                        int c = left + dc;
                        if (r < 0 || r >= n || c < 0 || c >= n) continue;
                        bool on = dr >= 0 && dr <= 6 && dc >= 0 && dc <= 6 &&
                                  (dr == 0 || dr == 6 || dc == 0 || dc == 6 || (dr >= 2 && dr <= 4 && dc >= 2 && dc <= 4));
                        setFn(r, c, on);
                    }
                }
            };

            drawFinder(0, 0);
            drawFinder(0, n - 7);
            drawFinder(n - 7, 0);

            for (int i = 8; i < n - 8; i++)
            {
                setFn(6, i, i % 2 == 0);
                setFn(i, 6, i % 2 == 0);
            }

            for (int ri = 0; ri < spec.Align.Length; ri++)
            {
                int r0 = spec.Align[ri];
                for (int ci = 0; ci < spec.Align.Length; ci++)
                {
                    int c0 = spec.Align[ci];
                    if (isFunc[r0, c0]) continue;
                    for (int dr = -2; dr <= 2; dr++)
                    {
                        for (int dc = -2; dc <= 2; dc++)
                        {
                            setFn(r0 + dr, c0 + dc, Math.Max(Math.Abs(dr), Math.Abs(dc)) != 1);
                        }
                    }
                }
            }

            for (int i = 0; i < 9; i++)
            {
                if (i != 6)
                {
                    isFunc[8, i] = true;
                    isFunc[i, 8] = true;
                }
            }
            for (int i = 0; i < 8; i++)
            {
                isFunc[8, n - 1 - i] = true;
                isFunc[n - 1 - i, 8] = true;
            }
            setFn(n - 8, 8, true);

            if (ver >= 7)
            {
                int rem = ver;
                for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >> 11) * 0x1f25);
                int verBits = (ver << 12) | rem;
                for (int i = 0; i < 18; i++)
                {
                    bool bit = ((verBits >> i) & 1) == 1;
                    int a = i / 3;
                    int b = (n - 11) + (i % 3);
                    setFn(a, b, bit);
                    setFn(b, a, bit);
                }
            }

            List<bool> codeBits = new List<bool>(interleaved.Count * 8);
            for (int i = 0; i < interleaved.Count; i++)
            {
                byte cw = interleaved[i];
                for (int b = 7; b >= 0; b--)
                {
                    codeBits.Add(((cw >> b) & 1) == 1);
                }
            }

            int bitIdx = 0;
            for (int right = n - 1; right >= 1; right -= 2)
            {
                if (right == 6) right = 5;
                for (int vert = 0; vert < n; vert++)
                {
                    for (int j = 0; j < 2; j++)
                    {
                        int c = right - j;
                        bool upward = ((right + 1) & 2) == 0;
                        int r = upward ? n - 1 - vert : vert;
                        if (!isFunc[r, c] && bitIdx < codeBits.Count)
                        {
                            mod[r, c] = codeBits[bitIdx++];
                        }
                    }
                }
            }

            bool[,] best = ApplyMaskAndFormat(mod, isFunc, n, 0);
            int bestPen = ScorePenalty(best, n);
            for (int mid = 1; mid < 8; mid++)
            {
                bool[,] cand = ApplyMaskAndFormat(mod, isFunc, n, mid);
                int pen = ScorePenalty(cand, n);
                if (pen < bestPen)
                {
                    bestPen = pen;
                    best = cand;
                }
            }
            return best;
        }

        private static bool MaskMatch(int maskId, int r, int c)
        {
            switch (maskId)
            {
                case 0: return (r + c) % 2 == 0;
                case 1: return r % 2 == 0;
                case 2: return c % 3 == 0;
                case 3: return (r + c) % 3 == 0;
                case 4: return ((r / 2) + (c / 3)) % 2 == 0;
                case 5: return ((r * c) % 2) + ((r * c) % 3) == 0;
                case 6: return (((r * c) % 2) + ((r * c) % 3)) % 2 == 0;
                default: return (((r + c) % 2) + ((r * c) % 3)) % 2 == 0;
            }
        }

        private static bool[,] ApplyMaskAndFormat(bool[,] mod, bool[,] isFunc, int n, int maskId)
        {
            bool[,] m = new bool[n, n];
            for (int r = 0; r < n; r++)
            {
                for (int c = 0; c < n; c++)
                {
                    bool v = mod[r, c];
                    if (!isFunc[r, c] && MaskMatch(maskId, r, c)) v = !v;
                    m[r, c] = v;
                }
            }

            int data5 = maskId; // ECC Level M (00) << 3 | maskId
            int rem = data5;
            for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >> 9) * 0x537);
            int fmt = ((data5 << 10) | rem) ^ 0x5412;

            for (int i = 0; i < 6; i++) m[8, i] = ((fmt >> (14 - i)) & 1) == 1;
            m[8, 7] = ((fmt >> 8) & 1) == 1;
            m[8, 8] = ((fmt >> 7) & 1) == 1;
            m[7, 8] = ((fmt >> 6) & 1) == 1;
            for (int i = 0; i < 6; i++) m[i, 8] = ((fmt >> i) & 1) == 1;
            for (int i = 0; i < 8; i++) m[8, n - 1 - i] = ((fmt >> i) & 1) == 1;
            for (int i = 8; i < 15; i++) m[n - 15 + i, 8] = ((fmt >> i) & 1) == 1;
            return m;
        }

        private static int ScorePenalty(bool[,] m, int n)
        {
            int pen = 0;
            for (int r = 0; r < n; r++)
            {
                int run = 1;
                for (int c = 1; c < n; c++)
                {
                    if (m[r, c] == m[r, c - 1]) run++;
                    else { if (run >= 5) pen += run - 2; run = 1; }
                }
                if (run >= 5) pen += run - 2;
            }
            for (int c = 0; c < n; c++)
            {
                int run = 1;
                for (int r = 1; r < n; r++)
                {
                    if (m[r, c] == m[r - 1, c]) run++;
                    else { if (run >= 5) pen += run - 2; run = 1; }
                }
                if (run >= 5) pen += run - 2;
            }
            for (int r = 0; r < n - 1; r++)
            {
                for (int c = 0; c < n - 1; c++)
                {
                    if (m[r, c] == m[r, c + 1] && m[r, c] == m[r + 1, c] && m[r, c] == m[r + 1, c + 1]) pen += 3;
                }
            }
            return pen;
        }

        public static Bitmap RenderBitmap(string text, int scale)
        {
            if (scale < 1) scale = 4;
            bool[,] matrix = BuildMatrix(text);
            int n = matrix.GetLength(0);
            int quiet = 2;
            int total = (n + quiet * 2) * scale;
            Bitmap bmp = new Bitmap(total, total);
            using (Graphics g = Graphics.FromImage(bmp))
            {
                g.Clear(Color.White);
                using (SolidBrush dark = new SolidBrush(Color.FromArgb(11, 15, 25)))
                {
                    for (int r = 0; r < n; r++)
                    {
                        for (int c = 0; c < n; c++)
                        {
                            if (matrix[r, c])
                            {
                                g.FillRectangle(dark, (c + quiet) * scale, (r + quiet) * scale, scale, scale);
                            }
                        }
                    }
                }
            }
            return bmp;
        }
    }
}
