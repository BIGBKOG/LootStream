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
            Icon setupIcon = CyberUiAssets.CreateLootStreamIcon();
            if (setupIcon != null) try { this.Icon = setupIcon; } catch { }

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
            Icon uninstIcon = CyberUiAssets.CreateLootStreamIcon();
            if (uninstIcon != null) try { this.Icon = uninstIcon; } catch { }

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

    internal sealed class DoubleBufferedPanel : Panel
    {
        public DoubleBufferedPanel()
        {
            this.DoubleBuffered = true;
            this.ResizeRedraw = true;
        }
    }

    internal static class CyberUiAssets
    {
        public static Icon CreateLootStreamIcon()
        {
            try
            {
                using (Bitmap bmp = new Bitmap(32, 32))
                using (Graphics g = Graphics.FromImage(bmp))
                {
                    g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
                    g.Clear(Color.FromArgb(11, 15, 25));
                    using (Pen pen = new Pen(Color.FromArgb(0, 229, 255), 2.5f))
                    {
                        g.DrawRectangle(pen, 2, 2, 27, 27);
                    }
                    Point[] tri = new Point[] { new Point(11, 8), new Point(23, 16), new Point(11, 24) };
                    using (SolidBrush br = new SolidBrush(Color.FromArgb(0, 255, 135)))
                    {
                        g.FillPolygon(br, tri);
                    }
                    IntPtr hIcon = bmp.GetHicon();
                    return Icon.FromHandle(hIcon);
                }
            }
            catch
            {
                return null;
            }
        }

        public static Image LoadImageUnlocked(string path)
        {
            if (string.IsNullOrEmpty(path) || !File.Exists(path)) return null;
            try
            {
                byte[] bytes = File.ReadAllBytes(path);
                using (MemoryStream ms = new MemoryStream(bytes))
                using (Image raw = Image.FromStream(ms))
                {
                    return new Bitmap(raw);
                }
            }
            catch
            {
                return null;
            }
        }
    }

    internal sealed class WmpPlayerHost : AxHost
    {
        private object _ocx;
        private bool _initFailed;

        public bool IsAvailable
        {
            get { return !_initFailed; }
        }

        public WmpPlayerHost() : base("6bf52a52-394a-11d3-b153-00c04f79faa6")
        {
        }

        protected override void AttachInterfaces()
        {
            try
            {
                _ocx = this.GetOcx();
                if (_ocx != null)
                {
                    SetProp(_ocx, "uiMode", "none");
                    SetProp(_ocx, "stretchToFit", true);
                    SetProp(_ocx, "enableContextMenu", false);
                    object settings = GetProp(_ocx, "settings");
                    if (settings != null)
                    {
                        SetProp(settings, "autoStart", true);
                        SetProp(settings, "volume", 85);
                    }
                }
            }
            catch
            {
                _initFailed = true;
            }
        }

        private void EnsureOcx()
        {
            if (_ocx == null && !_initFailed)
            {
                try
                {
                    if (!this.IsHandleCreated) this.CreateControl();
                    _ocx = this.GetOcx();
                    if (_ocx != null)
                    {
                        SetProp(_ocx, "uiMode", "none");
                        SetProp(_ocx, "stretchToFit", true);
                        SetProp(_ocx, "enableContextMenu", false);
                    }
                }
                catch
                {
                    _initFailed = true;
                }
            }
        }

        public bool TryLoad(string filePath, double resumeSeconds)
        {
            try
            {
                EnsureOcx();
                if (_ocx == null) return false;
                object settings = GetProp(_ocx, "settings");
                if (settings != null) SetProp(settings, "autoStart", true);
                SetProp(_ocx, "URL", filePath);
                if (resumeSeconds > 2.0)
                {
                    object controls = GetProp(_ocx, "controls");
                    if (controls != null) SetProp(controls, "currentPosition", resumeSeconds);
                }
                Play();
                return true;
            }
            catch
            {
                return false;
            }
        }

        public void Play()
        {
            try
            {
                EnsureOcx();
                if (_ocx == null) return;
                object controls = GetProp(_ocx, "controls");
                if (controls != null) InvokeMethod(controls, "play");
            }
            catch { }
        }

        public void Pause()
        {
            try
            {
                EnsureOcx();
                if (_ocx == null) return;
                object controls = GetProp(_ocx, "controls");
                if (controls != null) InvokeMethod(controls, "pause");
            }
            catch { }
        }

        public void StopPlayback()
        {
            try
            {
                if (_ocx == null) return;
                object controls = GetProp(_ocx, "controls");
                if (controls != null) InvokeMethod(controls, "stop");
                SetProp(_ocx, "URL", "");
            }
            catch { }
        }

        public void SeekToSeconds(double sec)
        {
            try
            {
                if (_ocx == null) return;
                object controls = GetProp(_ocx, "controls");
                if (controls != null) SetProp(controls, "currentPosition", Math.Max(0.0, sec));
            }
            catch { }
        }

        public void SetVolume(int volumePercent)
        {
            try
            {
                EnsureOcx();
                if (_ocx == null) return;
                object settings = GetProp(_ocx, "settings");
                if (settings != null) SetProp(settings, "volume", Math.Max(0, Math.Min(100, volumePercent)));
            }
            catch { }
        }

        public void SetMute(bool muted)
        {
            try
            {
                EnsureOcx();
                if (_ocx == null) return;
                object settings = GetProp(_ocx, "settings");
                if (settings != null) SetProp(settings, "mute", muted);
            }
            catch { }
        }

        public void GetStateSnapshot(out int playState, out double positionSec, out double durationSec)
        {
            playState = 0;
            positionSec = 0.0;
            durationSec = 0.0;
            try
            {
                if (_ocx == null) return;
                object ps = GetProp(_ocx, "playState");
                if (ps != null) playState = Convert.ToInt32(ps);
                object controls = GetProp(_ocx, "controls");
                if (controls != null)
                {
                    object pos = GetProp(controls, "currentPosition");
                    if (pos != null) positionSec = Convert.ToDouble(pos);
                }
                object media = GetProp(_ocx, "currentMedia");
                if (media != null)
                {
                    object dur = GetProp(media, "duration");
                    if (dur != null) durationSec = Convert.ToDouble(dur);
                }
            }
            catch { }
        }

        private static object GetProp(object target, string name)
        {
            return target.GetType().InvokeMember(name, BindingFlags.GetProperty, null, target, null);
        }

        private static void SetProp(object target, string name, object value)
        {
            target.GetType().InvokeMember(name, BindingFlags.SetProperty, null, target, new object[] { value });
        }

        private static void InvokeMethod(object target, string name)
        {
            target.GetType().InvokeMember(name, BindingFlags.InvokeMethod, null, target, null);
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

        private enum AppTab
        {
            Home,
            Movies,
            TvShows,
            Music,
            Theater,
            Folders
        }

        private readonly Color _bg = Color.FromArgb(11, 15, 25);
        private readonly Color _surface = Color.FromArgb(19, 25, 38);
        private readonly Color _surfaceAlt = Color.FromArgb(28, 37, 56);
        private readonly Color _border = Color.FromArgb(38, 50, 76);
        private readonly Color _cyan = Color.FromArgb(0, 229, 255);
        private readonly Color _green = Color.FromArgb(0, 255, 135);
        private readonly Color _purple = Color.FromArgb(176, 102, 255);
        private readonly Color _textPrimary = Color.FromArgb(241, 245, 249);
        private readonly Color _textSecondary = Color.FromArgb(148, 163, 184);

        private readonly JavaScriptSerializer _json = new JavaScriptSerializer();
        private readonly object _syncLock = new object();
        private readonly string _stateFilePath;
        private readonly List<string> _activityLog = new List<string>();
        private readonly List<Image> _cardBitmaps = new List<Image>();

        private HostState _state;
        private List<MediaEntry> _catalog = new List<MediaEntry>();
        private Dictionary<string, MediaEntry> _catalogById = new Dictionary<string, MediaEntry>();
        private HttpListener _listener;
        private Thread _listenerThread;
        private UdpClient _udpDiscovery;
        private Thread _udpThread;
        private volatile bool _isRunning;
        private string _quickCode;

        // Navigation & Filter State
        private AppTab _activeTab = AppTab.Home;
        private string _libraryFilter = "ALL"; // ALL, FAV, UNWATCHED
        private string _searchQuery = "";
        private bool _isFullscreen = false;
        private FormBorderStyle _savedBorderStyle;
        private FormWindowState _savedWindowState;

        // Top Bar & Sidebar Controls
        private Panel _topBar;
        private Panel _sidebar;
        private Panel _mainContentHost;
        private TextBox _txtSearch;
        private Button _btnLanStatus;
        private Label _lblSidebarQuickCode;
        private Button _navHome;
        private Button _navMovies;
        private Button _navTv;
        private Button _navMusic;
        private Button _navTheater;
        private Button _navFolders;

        // Home QR Controls
        private PictureBox _picHomeQr;
        private Label _lblHomeQuickCode;
        private Label _lblHomeLanUrl;

        // Server & Folder Controls (Folders Tab)
        private Panel _foldersPage;
        private TextBox _txtServerName;
        private TextBox _txtPort;
        private TextBox _txtPin;
        private Label _lblServerStatusPill;
        private Label _lblLanUrl;
        private Label _lblQuickCode;
        private Label _lblMediaCount;
        private Button _btnToggleServer;
        private ListBox _lstFolders;
        private ListBox _lstPairedDevices;
        private TextBox _txtLog;

        // Library Grid Container
        private Panel _libraryPage;
        private FlowLayoutPanel _mediaGrid;
        private Label _lblPageHeading;
        private Label _lblPageSubheading;
        private Panel _filterBar;
        private Button _btnFilterAll;
        private Button _btnFilterFav;
        private Button _btnFilterUnwatched;

        // Theater Player & Now Playing Mini-Bar
        private Panel _theaterPage;
        private Panel _theaterVideoFrame;
        private WmpPlayerHost _wmpHost;
        private Panel _audioStagePanel;
        private PictureBox _picAudioArt;
        private Label _lblAudioStageTitle;
        private Label _lblAudioStageSub;
        private Panel _wmpFallbackPanel;
        private Label _lblTheaterTitle;
        private Label _lblTheaterSub;
        private Label _lblTheaterTime;
        private Button _btnTheaterPlayPause;
        private Button _btnTheaterFav;
        private Button _btnTheaterWatched;
        private Button _btnTheaterMute;
        private Button _btnTheaterFullscreen;
        private TrackBar _seekBar;
        private TrackBar _volumeBar;
        private bool _isSeekingDrag;
        private bool _isMuted;

        private Panel _miniPlayerBar;
        private Label _lblMiniNowPlaying;
        private Button _btnMiniPlayPause;

        private System.Windows.Forms.Timer _playbackTimer;
        private List<MediaEntry> _activePlaylist = new List<MediaEntry>();
        private MediaEntry _currentItem;
        private long _lastProgressPersistMs;
        private Icon _appIcon;

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
            InitializeDesktopAppUi();
            RescanLibrary();
            StartServer();
            SwitchTab(AppTab.Home);
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
                string formatted = FormatSixDigitCode(next);
                if (_lblQuickCode != null) _lblQuickCode.Text = "Code: " + formatted;
                if (_lblSidebarQuickCode != null) _lblSidebarQuickCode.Text = "Easy Code: " + formatted;
                if (_lblHomeQuickCode != null) _lblHomeQuickCode.Text = formatted;
                if (_picHomeQr != null)
                {
                    if (_picHomeQr.Image != null) _picHomeQr.Image.Dispose();
                    _picHomeQr.Image = QrEncoder.RenderBitmap(GetQuickPairUri(), 4);
                }
            });
            AppendLog("Generated new 6-digit Easy Login code: " + next);
        }

        private void InitializeDesktopAppUi()
        {
            this.Text = "LootStream — Desktop Media Center";
            this.Size = new Size(1220, 800);
            this.MinimumSize = new Size(960, 640);
            this.BackColor = _bg;
            this.ForeColor = _textPrimary;
            this.Font = new Font("Segoe UI", 9.5f);
            this.StartPosition = FormStartPosition.CenterScreen;
            this.KeyPreview = true;

            _appIcon = CyberUiAssets.CreateLootStreamIcon();
            if (_appIcon != null)
            {
                try { this.Icon = _appIcon; } catch { }
            }

            this.KeyDown += delegate(object s, KeyEventArgs e)
            {
                if (e.KeyCode == Keys.Escape && _isFullscreen)
                {
                    ToggleFullscreen();
                    e.Handled = true;
                }
                else if (e.KeyCode == Keys.Space && _activeTab == AppTab.Theater && !_txtSearch.Focused)
                {
                    TogglePlayPauseCurrent();
                    e.Handled = true;
                }
            };

            BuildTopBar();
            BuildSidebar();
            BuildMiniPlayerBar();

            _mainContentHost = new DoubleBufferedPanel();
            _mainContentHost.Dock = DockStyle.Fill;
            _mainContentHost.BackColor = _bg;
            _mainContentHost.Padding = new Padding(0);

            BuildLibraryPage();
            BuildTheaterPage();
            BuildFoldersPage();

            _mainContentHost.Controls.Add(_libraryPage);
            _mainContentHost.Controls.Add(_theaterPage);
            _mainContentHost.Controls.Add(_foldersPage);

            // Dock order matters in WinForms: Fill first, then sides/top/bottom
            this.Controls.Add(_mainContentHost);
            this.Controls.Add(_miniPlayerBar);
            this.Controls.Add(_sidebar);
            this.Controls.Add(_topBar);

            _playbackTimer = new System.Windows.Forms.Timer();
            _playbackTimer.Interval = 400;
            _playbackTimer.Tick += delegate { OnPlaybackTick(); };
            _playbackTimer.Start();

            this.FormClosing += delegate
            {
                try
                {
                    if (_playbackTimer != null) _playbackTimer.Stop();
                    PersistCurrentPlaybackProgress(false);
                    if (_wmpHost != null) _wmpHost.StopPlayback();
                }
                catch { }
                DisposeCardBitmaps();
                StopServer();
                SaveState();
            };
        }

        private void BuildTopBar()
        {
            _topBar = new DoubleBufferedPanel();
            _topBar.Dock = DockStyle.Top;
            _topBar.Height = 62;
            _topBar.BackColor = _surface;
            _topBar.Paint += delegate(object s, PaintEventArgs e)
            {
                using (Pen p = new Pen(_border, 1))
                {
                    e.Graphics.DrawLine(p, 0, _topBar.Height - 1, _topBar.Width, _topBar.Height - 1);
                }
            };

            Panel logoBox = new Panel();
            logoBox.Size = new Size(34, 34);
            logoBox.Location = new Point(16, 14);
            logoBox.BackColor = Color.FromArgb(16, 40, 62);
            logoBox.Paint += delegate(object s, PaintEventArgs e)
            {
                e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
                using (Pen pen = new Pen(_cyan, 2f)) e.Graphics.DrawRectangle(pen, 1, 1, 31, 31);
                Point[] tri = new Point[] { new Point(12, 9), new Point(25, 17), new Point(12, 25) };
                using (SolidBrush br = new SolidBrush(_green)) e.Graphics.FillPolygon(br, tri);
            };

            Label lblBrand = new Label();
            lblBrand.Text = "LOOTSTREAM";
            lblBrand.Font = new Font("Segoe UI", 13.5f, FontStyle.Bold);
            lblBrand.ForeColor = _cyan;
            lblBrand.Location = new Point(58, 11);
            lblBrand.AutoSize = true;

            Label lblTag = new Label();
            lblTag.Text = "Desktop Media App";
            lblTag.Font = new Font("Segoe UI", 8.5f);
            lblTag.ForeColor = _textSecondary;
            lblTag.Location = new Point(59, 36);
            lblTag.AutoSize = true;

            _txtSearch = new TextBox();
            _txtSearch.Location = new Point(242, 17);
            _txtSearch.Size = new Size(340, 28);
            _txtSearch.BackColor = _surfaceAlt;
            _txtSearch.ForeColor = _textPrimary;
            _txtSearch.BorderStyle = BorderStyle.FixedSingle;
            _txtSearch.Font = new Font("Segoe UI", 10f);
            _txtSearch.TextChanged += delegate
            {
                _searchQuery = (_txtSearch.Text ?? "").Trim();
                if (_activeTab == AppTab.Home || _activeTab == AppTab.Movies || _activeTab == AppTab.TvShows || _activeTab == AppTab.Music)
                {
                    RenderActiveTab();
                }
            };

            Label lblSearchHint = new Label();
            lblSearchHint.Text = "Search:";
            lblSearchHint.ForeColor = _textSecondary;
            lblSearchHint.Font = new Font("Segoe UI", 9f);
            lblSearchHint.Location = new Point(192, 21);
            lblSearchHint.AutoSize = true;

            Button btnClearSearch = CreateCyberButton("×", _surfaceAlt, _textSecondary, 28, 28);
            btnClearSearch.Location = new Point(586, 17);
            btnClearSearch.Click += delegate
            {
                _txtSearch.Text = "";
                _searchQuery = "";
            };

            _btnLanStatus = CreateCyberButton("OFFLINE", _surfaceAlt, _textSecondary, 226, 32);
            _btnLanStatus.Location = new Point(772, 15);
            _btnLanStatus.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            _btnLanStatus.Font = new Font("Consolas", 8.5f, FontStyle.Bold);
            _btnLanStatus.Click += delegate
            {
                try
                {
                    string u = GetLanUrl();
                    Clipboard.SetText(u);
                    AppendLog("Copied LAN URL: " + u);
                }
                catch { }
            };

            Button btnRescanTop = CreateCyberButton("Rescan", _surfaceAlt, _purple, 86, 32);
            btnRescanTop.Location = new Point(1008, 15);
            btnRescanTop.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnRescanTop.Click += delegate
            {
                RescanLibrary();
                RenderActiveTab();
            };

            Button btnQrTop = CreateCyberButton("QR Login", _green, _bg, 96, 32);
            btnQrTop.Location = new Point(1102, 15);
            btnQrTop.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnQrTop.Font = new Font("Segoe UI", 9f, FontStyle.Bold);
            btnQrTop.Click += delegate { ShowQrLoginDialog(); };

            _topBar.Controls.Add(logoBox);
            _topBar.Controls.Add(lblBrand);
            _topBar.Controls.Add(lblTag);
            _topBar.Controls.Add(lblSearchHint);
            _topBar.Controls.Add(_txtSearch);
            _topBar.Controls.Add(btnClearSearch);
            _topBar.Controls.Add(_btnLanStatus);
            _topBar.Controls.Add(btnRescanTop);
            _topBar.Controls.Add(btnQrTop);
        }

        private void BuildSidebar()
        {
            _sidebar = new DoubleBufferedPanel();
            _sidebar.Dock = DockStyle.Left;
            _sidebar.Width = 216;
            _sidebar.BackColor = _surface;
            _sidebar.Paint += delegate(object s, PaintEventArgs e)
            {
                using (Pen p = new Pen(_border, 1))
                {
                    e.Graphics.DrawLine(p, _sidebar.Width - 1, 0, _sidebar.Width - 1, _sidebar.Height);
                }
            };

            Label lblNav = new Label();
            lblNav.Text = "LIBRARY & HOST";
            lblNav.Font = new Font("Segoe UI", 8f, FontStyle.Bold);
            lblNav.ForeColor = _textSecondary;
            lblNav.Location = new Point(18, 16);
            lblNav.AutoSize = true;
            _sidebar.Controls.Add(lblNav);

            _navHome = CreateSidebarButton("  Home & QR Login", 42, delegate { SwitchTab(AppTab.Home); });
            _navMovies = CreateSidebarButton("  Movies", 88, delegate { SwitchTab(AppTab.Movies); });
            _navTv = CreateSidebarButton("  TV Shows", 134, delegate { SwitchTab(AppTab.TvShows); });
            _navMusic = CreateSidebarButton("  Music", 180, delegate { SwitchTab(AppTab.Music); });
            _navTheater = CreateSidebarButton("  Theater Player", 226, delegate { SwitchTab(AppTab.Theater); });
            _navFolders = CreateSidebarButton("  Folders & Server", 272, delegate { SwitchTab(AppTab.Folders); });

            _sidebar.Controls.Add(_navHome);
            _sidebar.Controls.Add(_navMovies);
            _sidebar.Controls.Add(_navTv);
            _sidebar.Controls.Add(_navMusic);
            _sidebar.Controls.Add(_navTheater);
            _sidebar.Controls.Add(_navFolders);

            // Bottom Easy Wi-Fi Pill
            Panel sideCard = new Panel();
            sideCard.Size = new Size(188, 102);
            sideCard.Location = new Point(14, 540);
            sideCard.Anchor = AnchorStyles.Bottom | AnchorStyles.Left;
            sideCard.BackColor = _surfaceAlt;

            Label lblSideHdr = new Label();
            lblSideHdr.Text = "ANDROID EASY LOGIN";
            lblSideHdr.Font = new Font("Segoe UI", 7.5f, FontStyle.Bold);
            lblSideHdr.ForeColor = _textSecondary;
            lblSideHdr.Location = new Point(12, 10);
            lblSideHdr.AutoSize = true;

            _lblSidebarQuickCode = new Label();
            _lblSidebarQuickCode.Text = "Easy Code: " + FormatSixDigitCode(_quickCode);
            _lblSidebarQuickCode.Font = new Font("Consolas", 10.5f, FontStyle.Bold);
            _lblSidebarQuickCode.ForeColor = _green;
            _lblSidebarQuickCode.Location = new Point(12, 32);
            _lblSidebarQuickCode.AutoSize = true;

            Button btnSideQr = CreateCyberButton("Open QR Popup", _cyan, _bg, 164, 28);
            btnSideQr.Location = new Point(12, 62);
            btnSideQr.Font = new Font("Segoe UI", 8.5f, FontStyle.Bold);
            btnSideQr.Click += delegate { ShowQrLoginDialog(); };

            sideCard.Controls.Add(lblSideHdr);
            sideCard.Controls.Add(_lblSidebarQuickCode);
            sideCard.Controls.Add(btnSideQr);
            _sidebar.Controls.Add(sideCard);
        }

        private Button CreateSidebarButton(string text, int y, EventHandler onClick)
        {
            Button btn = new Button();
            btn.Text = text;
            btn.Location = new Point(12, y);
            btn.Size = new Size(192, 40);
            btn.TextAlign = ContentAlignment.MiddleLeft;
            btn.FlatStyle = FlatStyle.Flat;
            btn.FlatAppearance.BorderSize = 0;
            btn.BackColor = _surface;
            btn.ForeColor = _textPrimary;
            btn.Font = new Font("Segoe UI", 10f, FontStyle.Bold);
            btn.Cursor = Cursors.Hand;
            btn.Click += onClick;
            return btn;
        }

        private Button CreateCyberButton(string text, Color back, Color fore, int width, int height)
        {
            Button btn = new Button();
            btn.Text = text;
            btn.Size = new Size(width, height);
            btn.FlatStyle = FlatStyle.Flat;
            btn.FlatAppearance.BorderSize = 0;
            btn.BackColor = back;
            btn.ForeColor = fore;
            btn.Font = new Font("Segoe UI", 9f, FontStyle.Bold);
            btn.Cursor = Cursors.Hand;
            return btn;
        }

        private void BuildMiniPlayerBar()
        {
            _miniPlayerBar = new DoubleBufferedPanel();
            _miniPlayerBar.Dock = DockStyle.Bottom;
            _miniPlayerBar.Height = 56;
            _miniPlayerBar.BackColor = Color.FromArgb(14, 21, 35);
            _miniPlayerBar.Visible = false;
            _miniPlayerBar.Paint += delegate(object s, PaintEventArgs e)
            {
                using (Pen p = new Pen(_border, 1))
                {
                    e.Graphics.DrawLine(p, 0, 0, _miniPlayerBar.Width, 0);
                }
            };

            _lblMiniNowPlaying = new Label();
            _lblMiniNowPlaying.Text = "Not playing";
            _lblMiniNowPlaying.Font = new Font("Segoe UI", 10f, FontStyle.Bold);
            _lblMiniNowPlaying.ForeColor = _green;
            _lblMiniNowPlaying.Location = new Point(18, 17);
            _lblMiniNowPlaying.Size = new Size(560, 24);
            _lblMiniNowPlaying.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;

            _btnMiniPlayPause = CreateCyberButton("Pause", _cyan, _bg, 84, 32);
            _btnMiniPlayPause.Location = new Point(880, 12);
            _btnMiniPlayPause.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            _btnMiniPlayPause.Click += delegate { TogglePlayPauseCurrent(); };

            Button btnMiniStop = CreateCyberButton("Stop", _surfaceAlt, _textPrimary, 72, 32);
            btnMiniStop.Location = new Point(972, 12);
            btnMiniStop.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnMiniStop.Click += delegate { StopCurrentPlayback(); };

            Button btnMiniOpen = CreateCyberButton("Open Theater", _purple, Color.White, 124, 32);
            btnMiniOpen.Location = new Point(1052, 12);
            btnMiniOpen.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnMiniOpen.Click += delegate { SwitchTab(AppTab.Theater); };

            _miniPlayerBar.Controls.Add(_lblMiniNowPlaying);
            _miniPlayerBar.Controls.Add(_btnMiniPlayPause);
            _miniPlayerBar.Controls.Add(btnMiniStop);
            _miniPlayerBar.Controls.Add(btnMiniOpen);
        }

        private void BuildLibraryPage()
        {
            _libraryPage = new DoubleBufferedPanel();
            _libraryPage.Dock = DockStyle.Fill;
            _libraryPage.BackColor = _bg;
            _libraryPage.AutoScroll = true;

            Panel headerArea = new Panel();
            headerArea.Dock = DockStyle.Top;
            headerArea.Height = 64;
            headerArea.BackColor = _bg;
            headerArea.Padding = new Padding(22, 14, 22, 6);

            _lblPageHeading = new Label();
            _lblPageHeading.Text = "Home";
            _lblPageHeading.Font = new Font("Segoe UI", 16f, FontStyle.Bold);
            _lblPageHeading.ForeColor = _textPrimary;
            _lblPageHeading.Location = new Point(22, 12);
            _lblPageHeading.AutoSize = true;

            _lblPageSubheading = new Label();
            _lblPageSubheading.Text = "";
            _lblPageSubheading.Font = new Font("Segoe UI", 9f);
            _lblPageSubheading.ForeColor = _textSecondary;
            _lblPageSubheading.Location = new Point(24, 41);
            _lblPageSubheading.AutoSize = true;

            _filterBar = new Panel();
            _filterBar.Size = new Size(310, 34);
            _filterBar.Location = new Point(650, 16);
            _filterBar.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            _filterBar.BackColor = _bg;

            _btnFilterAll = CreateCyberButton("All", _cyan, _bg, 84, 30);
            _btnFilterAll.Location = new Point(0, 2);
            _btnFilterAll.Click += delegate { _libraryFilter = "ALL"; RenderActiveTab(); };

            _btnFilterFav = CreateCyberButton("Favorites ★", _surfaceAlt, _textPrimary, 104, 30);
            _btnFilterFav.Location = new Point(92, 2);
            _btnFilterFav.Click += delegate { _libraryFilter = "FAV"; RenderActiveTab(); };

            _btnFilterUnwatched = CreateCyberButton("Unwatched", _surfaceAlt, _textPrimary, 104, 30);
            _btnFilterUnwatched.Location = new Point(204, 2);
            _btnFilterUnwatched.Click += delegate { _libraryFilter = "UNWATCHED"; RenderActiveTab(); };

            _filterBar.Controls.Add(_btnFilterAll);
            _filterBar.Controls.Add(_btnFilterFav);
            _filterBar.Controls.Add(_btnFilterUnwatched);

            headerArea.Controls.Add(_lblPageHeading);
            headerArea.Controls.Add(_lblPageSubheading);
            headerArea.Controls.Add(_filterBar);

            _mediaGrid = new FlowLayoutPanel();
            _mediaGrid.Dock = DockStyle.Fill;
            _mediaGrid.AutoScroll = true;
            _mediaGrid.BackColor = _bg;
            _mediaGrid.Padding = new Padding(18, 6, 18, 20);
            _mediaGrid.WrapContents = true;

            _libraryPage.Controls.Add(_mediaGrid);
            _libraryPage.Controls.Add(headerArea);
        }

        private void BuildTheaterPage()
        {
            _theaterPage = new DoubleBufferedPanel();
            _theaterPage.Dock = DockStyle.Fill;
            _theaterPage.BackColor = Color.FromArgb(5, 8, 17);
            _theaterPage.Visible = false;

            // Theater Top Overlay Bar
            Panel theaterTop = new Panel();
            theaterTop.Dock = DockStyle.Top;
            theaterTop.Height = 56;
            theaterTop.BackColor = _surface;

            Button btnBack = CreateCyberButton("← Library", _surfaceAlt, _textPrimary, 96, 32);
            btnBack.Location = new Point(14, 12);
            btnBack.Click += delegate
            {
                if (_isFullscreen) ToggleFullscreen();
                SwitchTab(AppTab.Home);
            };

            _lblTheaterTitle = new Label();
            _lblTheaterTitle.Text = "Select any Movie, TV Show, or Music track to play";
            _lblTheaterTitle.Font = new Font("Segoe UI", 11.5f, FontStyle.Bold);
            _lblTheaterTitle.ForeColor = _textPrimary;
            _lblTheaterTitle.Location = new Point(122, 8);
            _lblTheaterTitle.Size = new Size(470, 22);
            _lblTheaterTitle.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;

            _lblTheaterSub = new Label();
            _lblTheaterSub.Text = "Built-in Desktop Media Player";
            _lblTheaterSub.Font = new Font("Segoe UI", 8.5f);
            _lblTheaterSub.ForeColor = _cyan;
            _lblTheaterSub.Location = new Point(123, 31);
            _lblTheaterSub.Size = new Size(470, 18);
            _lblTheaterSub.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;

            _btnTheaterFav = CreateCyberButton("★ Favorite", _surfaceAlt, _textPrimary, 102, 32);
            _btnTheaterFav.Location = new Point(628, 12);
            _btnTheaterFav.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            _btnTheaterFav.Click += delegate
            {
                if (_currentItem != null)
                {
                    ToggleFavoriteItem(_currentItem.Id);
                    UpdateTheaterActionBadges();
                }
            };

            _btnTheaterWatched = CreateCyberButton("✓ Watched", _surfaceAlt, _textPrimary, 102, 32);
            _btnTheaterWatched.Location = new Point(738, 12);
            _btnTheaterWatched.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            _btnTheaterWatched.Click += delegate
            {
                if (_currentItem != null)
                {
                    ToggleWatchedItem(_currentItem.Id);
                    UpdateTheaterActionBadges();
                }
            };

            _btnTheaterFullscreen = CreateCyberButton("⛶ Fullscreen", _cyan, _bg, 112, 32);
            _btnTheaterFullscreen.Location = new Point(848, 12);
            _btnTheaterFullscreen.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            _btnTheaterFullscreen.Click += delegate { ToggleFullscreen(); };

            theaterTop.Controls.Add(btnBack);
            theaterTop.Controls.Add(_lblTheaterTitle);
            theaterTop.Controls.Add(_lblTheaterSub);
            theaterTop.Controls.Add(_btnTheaterFav);
            theaterTop.Controls.Add(_btnTheaterWatched);
            theaterTop.Controls.Add(_btnTheaterFullscreen);

            // Theater Bottom Controls
            Panel theaterBottom = new Panel();
            theaterBottom.Dock = DockStyle.Bottom;
            theaterBottom.Height = 98;
            theaterBottom.BackColor = _surface;

            _seekBar = new TrackBar();
            _seekBar.Location = new Point(16, 8);
            _seekBar.Size = new Size(850, 32);
            _seekBar.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
            _seekBar.Minimum = 0;
            _seekBar.Maximum = 1000;
            _seekBar.TickStyle = TickStyle.None;
            _seekBar.BackColor = _surface;
            _seekBar.MouseDown += delegate { _isSeekingDrag = true; };
            _seekBar.MouseUp += delegate
            {
                _isSeekingDrag = false;
                SeekFromTrackBar();
            };

            _lblTheaterTime = new Label();
            _lblTheaterTime.Text = "0:00 / 0:00";
            _lblTheaterTime.Font = new Font("Consolas", 9.5f, FontStyle.Bold);
            _lblTheaterTime.ForeColor = _textSecondary;
            _lblTheaterTime.TextAlign = ContentAlignment.MiddleRight;
            _lblTheaterTime.Location = new Point(874, 14);
            _lblTheaterTime.Size = new Size(106, 22);
            _lblTheaterTime.Anchor = AnchorStyles.Top | AnchorStyles.Right;

            Button btnPrev = CreateCyberButton("⏮ Prev", _surfaceAlt, _textPrimary, 78, 34);
            btnPrev.Location = new Point(18, 48);
            btnPrev.Click += delegate { PlayAdjacentItem(-1); };

            Button btnRew = CreateCyberButton("-10s", _surfaceAlt, _textPrimary, 60, 34);
            btnRew.Location = new Point(104, 48);
            btnRew.Click += delegate { SeekRelative(-10.0); };

            _btnTheaterPlayPause = CreateCyberButton("▶ Play", _green, _bg, 108, 34);
            _btnTheaterPlayPause.Location = new Point(172, 48);
            _btnTheaterPlayPause.Click += delegate { TogglePlayPauseCurrent(); };

            Button btnFwd = CreateCyberButton("+10s", _surfaceAlt, _textPrimary, 60, 34);
            btnFwd.Location = new Point(288, 48);
            btnFwd.Click += delegate { SeekRelative(10.0); };

            Button btnNext = CreateCyberButton("Next ⏭", _surfaceAlt, _textPrimary, 78, 34);
            btnNext.Location = new Point(356, 48);
            btnNext.Click += delegate { PlayAdjacentItem(1); };

            Button btnStop = CreateCyberButton("⏹ Stop", _surfaceAlt, Color.FromArgb(248, 113, 113), 78, 34);
            btnStop.Location = new Point(442, 48);
            btnStop.Click += delegate { StopCurrentPlayback(); };

            _btnTheaterMute = CreateCyberButton("Vol", _surfaceAlt, _cyan, 56, 34);
            _btnTheaterMute.Location = new Point(774, 48);
            _btnTheaterMute.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            _btnTheaterMute.Click += delegate
            {
                _isMuted = !_isMuted;
                if (_wmpHost != null) _wmpHost.SetMute(_isMuted);
                _btnTheaterMute.Text = _isMuted ? "Muted" : "Vol";
                _btnTheaterMute.ForeColor = _isMuted ? Color.FromArgb(248, 113, 113) : _cyan;
            };

            _volumeBar = new TrackBar();
            _volumeBar.Location = new Point(836, 48);
            _volumeBar.Size = new Size(140, 34);
            _volumeBar.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            _volumeBar.Minimum = 0;
            _volumeBar.Maximum = 100;
            _volumeBar.Value = 85;
            _volumeBar.TickStyle = TickStyle.None;
            _volumeBar.BackColor = _surface;
            _volumeBar.Scroll += delegate
            {
                if (_wmpHost != null) _wmpHost.SetVolume(_volumeBar.Value);
            };

            theaterBottom.Controls.Add(_seekBar);
            theaterBottom.Controls.Add(_lblTheaterTime);
            theaterBottom.Controls.Add(btnPrev);
            theaterBottom.Controls.Add(btnRew);
            theaterBottom.Controls.Add(_btnTheaterPlayPause);
            theaterBottom.Controls.Add(btnFwd);
            theaterBottom.Controls.Add(btnNext);
            theaterBottom.Controls.Add(btnStop);
            theaterBottom.Controls.Add(_btnTheaterMute);
            theaterBottom.Controls.Add(_volumeBar);

            // Center Video + Audio Stage
            _theaterVideoFrame = new Panel();
            _theaterVideoFrame.Dock = DockStyle.Fill;
            _theaterVideoFrame.BackColor = Color.Black;

            _wmpHost = new WmpPlayerHost();
            _wmpHost.Dock = DockStyle.Fill;
            _theaterVideoFrame.Controls.Add(_wmpHost);

            // Music Cover Stage overlay (shown during AUDIO playback cleanly above hidden video frame)
            _audioStagePanel = new DoubleBufferedPanel();
            _audioStagePanel.Dock = DockStyle.Fill;
            _audioStagePanel.BackColor = Color.FromArgb(9, 13, 24);
            _audioStagePanel.Visible = false;

            _picAudioArt = new PictureBox();
            _picAudioArt.Size = new Size(260, 260);
            _picAudioArt.Location = new Point(360, 90);
            _picAudioArt.Anchor = AnchorStyles.None;
            _picAudioArt.SizeMode = PictureBoxSizeMode.Zoom;
            _picAudioArt.BackColor = _surface;

            _lblAudioStageTitle = new Label();
            _lblAudioStageTitle.Font = new Font("Segoe UI", 16f, FontStyle.Bold);
            _lblAudioStageTitle.ForeColor = _textPrimary;
            _lblAudioStageTitle.TextAlign = ContentAlignment.MiddleCenter;
            _lblAudioStageTitle.Location = new Point(140, 368);
            _lblAudioStageTitle.Size = new Size(700, 34);
            _lblAudioStageTitle.Anchor = AnchorStyles.None;

            _lblAudioStageSub = new Label();
            _lblAudioStageSub.Font = new Font("Segoe UI", 10.5f);
            _lblAudioStageSub.ForeColor = _green;
            _lblAudioStageSub.TextAlign = ContentAlignment.MiddleCenter;
            _lblAudioStageSub.Location = new Point(140, 404);
            _lblAudioStageSub.Size = new Size(700, 24);
            _lblAudioStageSub.Anchor = AnchorStyles.None;

            _audioStagePanel.Controls.Add(_picAudioArt);
            _audioStagePanel.Controls.Add(_lblAudioStageTitle);
            _audioStagePanel.Controls.Add(_lblAudioStageSub);
            _theaterVideoFrame.Controls.Add(_audioStagePanel);

            // External Player Fallback Panel (only shown if ActiveX WMP isn't available on host OS)
            _wmpFallbackPanel = new DoubleBufferedPanel();
            _wmpFallbackPanel.Dock = DockStyle.Fill;
            _wmpFallbackPanel.BackColor = _bg;
            _wmpFallbackPanel.Visible = false;

            Label lblNoWmp = new Label();
            lblNoWmp.Text = "Embedded Windows Media component is disabled on this PC.\nLaunch the file directly in your installed desktop media player:";
            lblNoWmp.ForeColor = _textSecondary;
            lblNoWmp.Font = new Font("Segoe UI", 11f);
            lblNoWmp.TextAlign = ContentAlignment.MiddleCenter;
            lblNoWmp.Location = new Point(180, 210);
            lblNoWmp.Size = new Size(620, 60);
            lblNoWmp.Anchor = AnchorStyles.None;

            Button btnOpenExternal = CreateCyberButton("Open in Windows Media Player", _green, _bg, 260, 42);
            btnOpenExternal.Location = new Point(360, 286);
            btnOpenExternal.Anchor = AnchorStyles.None;
            btnOpenExternal.Click += delegate
            {
                if (_currentItem != null && File.Exists(_currentItem.FilePath))
                {
                    try { Process.Start(new ProcessStartInfo { FileName = _currentItem.FilePath, UseShellExecute = true }); }
                    catch { }
                }
            };

            _wmpFallbackPanel.Controls.Add(lblNoWmp);
            _wmpFallbackPanel.Controls.Add(btnOpenExternal);
            _theaterVideoFrame.Controls.Add(_wmpFallbackPanel);

            _theaterPage.Controls.Add(_theaterVideoFrame);
            _theaterPage.Controls.Add(theaterBottom);
            _theaterPage.Controls.Add(theaterTop);
        }

        private void BuildFoldersPage()
        {
            _foldersPage = new DoubleBufferedPanel();
            _foldersPage.Dock = DockStyle.Fill;
            _foldersPage.BackColor = _bg;
            _foldersPage.Padding = new Padding(22, 18, 22, 18);
            _foldersPage.Visible = false;

            Label lblHdr = new Label();
            lblHdr.Text = "Shared Folders, Pairing & Server Console";
            lblHdr.Font = new Font("Segoe UI", 16f, FontStyle.Bold);
            lblHdr.ForeColor = _textPrimary;
            lblHdr.Location = new Point(22, 16);
            lblHdr.AutoSize = true;

            Panel cfgCard = new Panel();
            cfgCard.Location = new Point(22, 56);
            cfgCard.Size = new Size(940, 146);
            cfgCard.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
            cfgCard.BackColor = _surface;

            Label lblName = MakeLabel("Host Name:", 16, 14, _textSecondary);
            _txtServerName = MakeTextBox(_state.ServerName, 16, 36, 250, _surfaceAlt, _textPrimary);

            Label lblPort = MakeLabel("Port:", 282, 14, _textSecondary);
            _txtPort = MakeTextBox(_state.Port.ToString(), 282, 36, 86, _surfaceAlt, _textPrimary);

            Label lblPin = MakeLabel("Pairing PIN (optional):", 384, 14, _textSecondary);
            _txtPin = MakeTextBox(_state.Pin ?? "", 384, 36, 140, _surfaceAlt, _textPrimary);

            Button btnSaveCfg = CreateCyberButton("Save Config", _purple, Color.White, 122, 32);
            btnSaveCfg.Location = new Point(544, 34);
            btnSaveCfg.Click += delegate
            {
                lock (_syncLock)
                {
                    if (!string.IsNullOrEmpty(_txtServerName.Text.Trim())) _state.ServerName = _txtServerName.Text.Trim();
                    _state.Pin = _txtPin.Text.Trim();
                    SaveState();
                }
                AppendLog("Saved host name & PIN configuration.");
            };

            _lblServerStatusPill = new Label();
            _lblServerStatusPill.Text = "STOPPED";
            _lblServerStatusPill.Font = new Font("Segoe UI", 9.5f, FontStyle.Bold);
            _lblServerStatusPill.ForeColor = _textSecondary;
            _lblServerStatusPill.BackColor = _surfaceAlt;
            _lblServerStatusPill.TextAlign = ContentAlignment.MiddleCenter;
            _lblServerStatusPill.Location = new Point(682, 34);
            _lblServerStatusPill.Size = new Size(96, 32);
            _lblServerStatusPill.Anchor = AnchorStyles.Top | AnchorStyles.Right;

            _btnToggleServer = CreateCyberButton("Start Server", _cyan, _bg, 134, 32);
            _btnToggleServer.Location = new Point(790, 34);
            _btnToggleServer.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            _btnToggleServer.Click += delegate { ToggleServer(); };

            _lblQuickCode = new Label();
            _lblQuickCode.Text = "Code: " + FormatSixDigitCode(_quickCode);
            _lblQuickCode.Font = new Font("Consolas", 13f, FontStyle.Bold);
            _lblQuickCode.ForeColor = _cyan;
            _lblQuickCode.Location = new Point(16, 92);
            _lblQuickCode.Size = new Size(170, 30);

            _lblLanUrl = new Label();
            _lblLanUrl.Text = GetLanUrl();
            _lblLanUrl.Font = new Font("Consolas", 10.5f, FontStyle.Bold);
            _lblLanUrl.ForeColor = _green;
            _lblLanUrl.Location = new Point(196, 96);
            _lblLanUrl.Size = new Size(250, 26);

            Button btnNewCode = CreateCyberButton("New Code", _surfaceAlt, _cyan, 96, 30);
            btnNewCode.Location = new Point(460, 92);
            btnNewCode.Click += delegate { RefreshQuickCode(); };

            Button btnShowQr = CreateCyberButton("Show QR Code", _green, _bg, 120, 30);
            btnShowQr.Location = new Point(566, 92);
            btnShowQr.Click += delegate { ShowQrLoginDialog(); };

            _lblMediaCount = new Label();
            _lblMediaCount.Text = "0 items";
            _lblMediaCount.Font = new Font("Segoe UI", 10f, FontStyle.Bold);
            _lblMediaCount.ForeColor = _purple;
            _lblMediaCount.TextAlign = ContentAlignment.MiddleRight;
            _lblMediaCount.Location = new Point(780, 94);
            _lblMediaCount.Size = new Size(144, 26);
            _lblMediaCount.Anchor = AnchorStyles.Top | AnchorStyles.Right;

            cfgCard.Controls.Add(lblName);
            cfgCard.Controls.Add(_txtServerName);
            cfgCard.Controls.Add(lblPort);
            cfgCard.Controls.Add(_txtPort);
            cfgCard.Controls.Add(lblPin);
            cfgCard.Controls.Add(_txtPin);
            cfgCard.Controls.Add(btnSaveCfg);
            cfgCard.Controls.Add(_lblServerStatusPill);
            cfgCard.Controls.Add(_btnToggleServer);
            cfgCard.Controls.Add(_lblQuickCode);
            cfgCard.Controls.Add(_lblLanUrl);
            cfgCard.Controls.Add(btnNewCode);
            cfgCard.Controls.Add(btnShowQr);
            cfgCard.Controls.Add(_lblMediaCount);

            // Shared Folders + Paired Devices split
            Panel foldersCard = new Panel();
            foldersCard.Location = new Point(22, 214);
            foldersCard.Size = new Size(600, 196);
            foldersCard.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
            foldersCard.BackColor = _surface;

            Label lblFolders = MakeLabel("Shared Media Folders (Movies, TV Shows, Music):", 16, 12, _textPrimary);
            lblFolders.Font = new Font("Segoe UI", 9.5f, FontStyle.Bold);

            _lstFolders = new ListBox();
            _lstFolders.Location = new Point(16, 40);
            _lstFolders.Size = new Size(430, 140);
            _lstFolders.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right | AnchorStyles.Bottom;
            _lstFolders.BackColor = _surfaceAlt;
            _lstFolders.ForeColor = _textPrimary;
            _lstFolders.BorderStyle = BorderStyle.FixedSingle;
            RefreshFolderList();

            Button btnAddFolder = CreateCyberButton("+ Add Folder", _green, _bg, 126, 32);
            btnAddFolder.Location = new Point(458, 40);
            btnAddFolder.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnAddFolder.Click += delegate { AddFolder(); };

            Button btnRemoveFolder = CreateCyberButton("Remove Folder", _surfaceAlt, Color.FromArgb(248, 113, 113), 126, 32);
            btnRemoveFolder.Location = new Point(458, 80);
            btnRemoveFolder.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnRemoveFolder.Click += delegate { RemoveSelectedFolder(); };

            Button btnRescan = CreateCyberButton("Rescan Library", _surfaceAlt, _purple, 126, 32);
            btnRescan.Location = new Point(458, 120);
            btnRescan.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnRescan.Click += delegate { RescanLibrary(); };

            foldersCard.Controls.Add(lblFolders);
            foldersCard.Controls.Add(_lstFolders);
            foldersCard.Controls.Add(btnAddFolder);
            foldersCard.Controls.Add(btnRemoveFolder);
            foldersCard.Controls.Add(btnRescan);

            Panel pairedCard = new Panel();
            pairedCard.Location = new Point(634, 214);
            pairedCard.Size = new Size(328, 196);
            pairedCard.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            pairedCard.BackColor = _surface;

            Label lblPaired = MakeLabel("Paired Android Devices:", 14, 12, _textPrimary);
            lblPaired.Font = new Font("Segoe UI", 9.5f, FontStyle.Bold);

            _lstPairedDevices = new ListBox();
            _lstPairedDevices.Location = new Point(14, 40);
            _lstPairedDevices.Size = new Size(300, 140);
            _lstPairedDevices.BackColor = _surfaceAlt;
            _lstPairedDevices.ForeColor = _textPrimary;
            _lstPairedDevices.BorderStyle = BorderStyle.FixedSingle;
            RefreshPairedDevicesList();

            pairedCard.Controls.Add(lblPaired);
            pairedCard.Controls.Add(_lstPairedDevices);

            Label lblLog = MakeLabel("Server & Streaming Activity Log:", 22, 422, _textSecondary);
            _txtLog = new TextBox();
            _txtLog.Location = new Point(22, 444);
            _txtLog.Size = new Size(940, 200);
            _txtLog.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right | AnchorStyles.Bottom;
            _txtLog.Multiline = true;
            _txtLog.ReadOnly = true;
            _txtLog.ScrollBars = ScrollBars.Vertical;
            _txtLog.BackColor = _surface;
            _txtLog.ForeColor = _textSecondary;
            _txtLog.BorderStyle = BorderStyle.FixedSingle;
            _txtLog.Font = new Font("Consolas", 8.5f);

            _foldersPage.Controls.Add(lblHdr);
            _foldersPage.Controls.Add(cfgCard);
            _foldersPage.Controls.Add(foldersCard);
            _foldersPage.Controls.Add(pairedCard);
            _foldersPage.Controls.Add(lblLog);
            _foldersPage.Controls.Add(_txtLog);
        }

        private void SwitchTab(AppTab tab)
        {
            _activeTab = tab;
            UpdateSidebarSelection();
            _libraryPage.Visible = (tab == AppTab.Home || tab == AppTab.Movies || tab == AppTab.TvShows || tab == AppTab.Music);
            _theaterPage.Visible = (tab == AppTab.Theater);
            _foldersPage.Visible = (tab == AppTab.Folders);
            UpdateMiniPlayerVisibility();
            if (_libraryPage.Visible) RenderActiveTab();
            if (tab == AppTab.Folders) RefreshPairedDevicesList();
        }

        private void UpdateSidebarSelection()
        {
            Button[] all = new Button[] { _navHome, _navMovies, _navTv, _navMusic, _navTheater, _navFolders };
            AppTab[] tabs = new AppTab[] { AppTab.Home, AppTab.Movies, AppTab.TvShows, AppTab.Music, AppTab.Theater, AppTab.Folders };
            for (int i = 0; i < all.Length; i++)
            {
                if (all[i] == null) continue;
                bool active = (_activeTab == tabs[i]);
                all[i].BackColor = active ? Color.FromArgb(24, 42, 68) : _surface;
                all[i].ForeColor = active ? _cyan : _textPrimary;
            }
        }

        private void UpdateSidebarCounts()
        {
            int movies = 0;
            int tv = 0;
            int music = 0;
            lock (_syncLock)
            {
                foreach (MediaEntry e in _catalog)
                {
                    if (e.MediaType == "AUDIO") music++;
                    else if (e.MediaType == "EPISODE" || e.MediaType == "SERIES") tv++;
                    else movies++;
                }
            }
            if (_navMovies != null) _navMovies.Text = "  Movies (" + movies + ")";
            if (_navTv != null) _navTv.Text = "  TV Shows (" + tv + ")";
            if (_navMusic != null) _navMusic.Text = "  Music (" + music + ")";
        }

        private void UpdateMiniPlayerVisibility()
        {
            if (_miniPlayerBar == null) return;
            _miniPlayerBar.Visible = (!_isFullscreen && _activeTab != AppTab.Theater && _currentItem != null);
        }

        private void DisposeCardBitmaps()
        {
            foreach (Image img in _cardBitmaps)
            {
                try { if (img != null) img.Dispose(); } catch { }
            }
            _cardBitmaps.Clear();
        }

        private void RenderActiveTab()
        {
            if (!_libraryPage.Visible) return;
            _mediaGrid.SuspendLayout();
            _mediaGrid.Controls.Clear();
            DisposeCardBitmaps();

            List<MediaEntry> snapshot;
            Dictionary<string, ItemProgress> progSnapshot;
            lock (_syncLock)
            {
                snapshot = new List<MediaEntry>(_catalog);
                progSnapshot = new Dictionary<string, ItemProgress>(_state.Progress);
            }

            if (_activeTab == AppTab.Home)
            {
                _filterBar.Visible = false;
                _lblPageHeading.Text = "Home & Easy Wi-Fi Login";
                _lblPageSubheading.Text = _catalog.Count + " indexed media items ready to play locally or stream to Android";
                RenderHomeDashboard(snapshot, progSnapshot);
                _mediaGrid.ResumeLayout();
                return;
            }

            _filterBar.Visible = true;
            _btnFilterAll.BackColor = _libraryFilter == "ALL" ? _cyan : _surfaceAlt;
            _btnFilterAll.ForeColor = _libraryFilter == "ALL" ? _bg : _textPrimary;
            _btnFilterFav.BackColor = _libraryFilter == "FAV" ? _cyan : _surfaceAlt;
            _btnFilterFav.ForeColor = _libraryFilter == "FAV" ? _bg : _textPrimary;
            _btnFilterUnwatched.BackColor = _libraryFilter == "UNWATCHED" ? _cyan : _surfaceAlt;
            _btnFilterUnwatched.ForeColor = _libraryFilter == "UNWATCHED" ? _bg : _textPrimary;

            List<MediaEntry> filtered = new List<MediaEntry>();
            string q = (_searchQuery ?? "").ToLowerInvariant();
            foreach (MediaEntry e in snapshot)
            {
                bool matchesTab = false;
                if (_activeTab == AppTab.Movies) matchesTab = (e.MediaType == "MOVIE" || e.MediaType == "VIDEO");
                else if (_activeTab == AppTab.TvShows) matchesTab = (e.MediaType == "EPISODE" || e.MediaType == "SERIES");
                else if (_activeTab == AppTab.Music) matchesTab = (e.MediaType == "AUDIO");
                if (!matchesTab) continue;

                ItemProgress prog = progSnapshot.ContainsKey(e.Id) ? progSnapshot[e.Id] : null;
                if (_libraryFilter == "FAV" && (prog == null || !prog.IsFavorite)) continue;
                if (_libraryFilter == "UNWATCHED" && prog != null && prog.WatchStatus == "WATCHED") continue;

                if (!string.IsNullOrEmpty(q))
                {
                    bool hit = (e.Title ?? "").ToLowerInvariant().Contains(q) ||
                               (e.Subtitle ?? "").ToLowerInvariant().Contains(q) ||
                               (e.SeriesName ?? "").ToLowerInvariant().Contains(q);
                    if (!hit) continue;
                }
                filtered.Add(e);
            }

            if (_activeTab == AppTab.Movies) _lblPageHeading.Text = "Movies";
            else if (_activeTab == AppTab.TvShows) _lblPageHeading.Text = "TV Shows";
            else _lblPageHeading.Text = "Music";
            _lblPageSubheading.Text = filtered.Count + " items matching";

            if (filtered.Count == 0)
            {
                Panel emptyBox = new Panel();
                emptyBox.Size = new Size(720, 120);
                emptyBox.Margin = new Padding(8, 18, 8, 8);
                emptyBox.BackColor = _surface;
                Label lblEmpty = new Label();
                lblEmpty.Text = "No matching media files here yet.\nClick '+ Add Folder' on Home or Folders & Server to add Movies, TV Shows, or Music folders.";
                lblEmpty.ForeColor = _textSecondary;
                lblEmpty.Font = new Font("Segoe UI", 10.5f);
                lblEmpty.Location = new Point(22, 32);
                lblEmpty.Size = new Size(660, 56);
                emptyBox.Controls.Add(lblEmpty);
                _mediaGrid.Controls.Add(emptyBox);
            }
            else
            {
                foreach (MediaEntry item in filtered)
                {
                    ItemProgress prog = progSnapshot.ContainsKey(item.Id) ? progSnapshot[item.Id] : null;
                    _mediaGrid.Controls.Add(CreateMediaCard(item, prog, filtered));
                }
            }

            _mediaGrid.ResumeLayout();
        }

        private void RenderHomeDashboard(List<MediaEntry> snapshot, Dictionary<string, ItemProgress> progSnapshot)
        {
            int movies = 0;
            int tv = 0;
            int music = 0;
            foreach (MediaEntry e in snapshot)
            {
                if (e.MediaType == "AUDIO") music++;
                else if (e.MediaType == "EPISODE" || e.MediaType == "SERIES") tv++;
                else movies++;
            }

            Panel topBand = new Panel();
            topBand.Size = new Size(Math.Max(680, _mediaGrid.ClientSize.Width - 46), 278);
            topBand.Margin = new Padding(4, 4, 4, 14);
            topBand.BackColor = _bg;

            // Hero Welcome & Stats Card
            Panel heroCard = new Panel();
            heroCard.Location = new Point(0, 0);
            heroCard.Size = new Size(Math.Max(420, topBand.Width - 294), 270);
            heroCard.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
            heroCard.BackColor = _surface;

            Label lblHeroTitle = new Label();
            lblHeroTitle.Text = _state.ServerName;
            lblHeroTitle.Font = new Font("Segoe UI", 16f, FontStyle.Bold);
            lblHeroTitle.ForeColor = _textPrimary;
            lblHeroTitle.Location = new Point(20, 16);
            lblHeroTitle.AutoSize = true;

            Label lblHeroSub = new Label();
            lblHeroSub.Text = "Play Movies, TV Shows, and Music directly inside LootStream or pair your Android phone over Wi-Fi.";
            lblHeroSub.Font = new Font("Segoe UI", 9.5f);
            lblHeroSub.ForeColor = _textSecondary;
            lblHeroSub.Location = new Point(20, 48);
            lblHeroSub.Size = new Size(heroCard.Width - 40, 38);

            Panel stat1 = CreateStatTile("Movies", movies.ToString(), _cyan, 20, 98, delegate { SwitchTab(AppTab.Movies); });
            Panel stat2 = CreateStatTile("TV Shows", tv.ToString(), _purple, 178, 98, delegate { SwitchTab(AppTab.TvShows); });
            Panel stat3 = CreateStatTile("Music Tracks", music.ToString(), _green, 336, 98, delegate { SwitchTab(AppTab.Music); });

            Button btnHeroAddFolder = CreateCyberButton("+ Add Media Folder", _green, _bg, 166, 36);
            btnHeroAddFolder.Location = new Point(20, 208);
            btnHeroAddFolder.Click += delegate { AddFolder(); RenderActiveTab(); };

            Button btnHeroRescan = CreateCyberButton("Rescan Library", _surfaceAlt, _cyan, 142, 36);
            btnHeroRescan.Location = new Point(196, 208);
            btnHeroRescan.Click += delegate
            {
                RescanLibrary();
                RenderActiveTab();
            };

            Button btnHeroSettings = CreateCyberButton("Server & PIN", _surfaceAlt, _textPrimary, 128, 36);
            btnHeroSettings.Location = new Point(348, 208);
            btnHeroSettings.Click += delegate { SwitchTab(AppTab.Folders); };

            heroCard.Controls.Add(lblHeroTitle);
            heroCard.Controls.Add(lblHeroSub);
            heroCard.Controls.Add(stat1);
            heroCard.Controls.Add(stat2);
            heroCard.Controls.Add(stat3);
            heroCard.Controls.Add(btnHeroAddFolder);
            heroCard.Controls.Add(btnHeroRescan);
            heroCard.Controls.Add(btnHeroSettings);

            // Embedded QR + 6-Digit Easy Wi-Fi Login Card
            Panel qrCard = new Panel();
            qrCard.Location = new Point(topBand.Width - 280, 0);
            qrCard.Size = new Size(276, 270);
            qrCard.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            qrCard.BackColor = _surface;

            Label lblQrHdr = new Label();
            lblQrHdr.Text = "Easy Wi-Fi Android Login";
            lblQrHdr.Font = new Font("Segoe UI", 10.5f, FontStyle.Bold);
            lblQrHdr.ForeColor = _cyan;
            lblQrHdr.TextAlign = ContentAlignment.MiddleCenter;
            lblQrHdr.Location = new Point(12, 10);
            lblQrHdr.Size = new Size(252, 22);

            _picHomeQr = new PictureBox();
            _picHomeQr.Size = new Size(138, 138);
            _picHomeQr.Location = new Point(69, 36);
            _picHomeQr.SizeMode = PictureBoxSizeMode.Zoom;
            _picHomeQr.BackColor = Color.White;
            _picHomeQr.Image = QrEncoder.RenderBitmap(GetQuickPairUri(), 4);

            _lblHomeQuickCode = new Label();
            _lblHomeQuickCode.Text = FormatSixDigitCode(_quickCode);
            _lblHomeQuickCode.Font = new Font("Consolas", 17f, FontStyle.Bold);
            _lblHomeQuickCode.ForeColor = _green;
            _lblHomeQuickCode.TextAlign = ContentAlignment.MiddleCenter;
            _lblHomeQuickCode.Location = new Point(12, 178);
            _lblHomeQuickCode.Size = new Size(252, 30);

            _lblHomeLanUrl = new Label();
            _lblHomeLanUrl.Text = GetLanUrl();
            _lblHomeLanUrl.Font = new Font("Consolas", 8.5f);
            _lblHomeLanUrl.ForeColor = _textSecondary;
            _lblHomeLanUrl.TextAlign = ContentAlignment.MiddleCenter;
            _lblHomeLanUrl.Location = new Point(12, 208);
            _lblHomeLanUrl.Size = new Size(252, 18);

            Button btnHomeNewCode = CreateCyberButton("New 6-Digit Code", _surfaceAlt, _cyan, 168, 28);
            btnHomeNewCode.Location = new Point(54, 232);
            btnHomeNewCode.Click += delegate { RefreshQuickCode(); };

            qrCard.Controls.Add(lblQrHdr);
            qrCard.Controls.Add(_picHomeQr);
            qrCard.Controls.Add(_lblHomeQuickCode);
            qrCard.Controls.Add(_lblHomeLanUrl);
            qrCard.Controls.Add(btnHomeNewCode);

            topBand.Controls.Add(heroCard);
            topBand.Controls.Add(qrCard);
            _mediaGrid.Controls.Add(topBand);

            // Section Title + Quick Cards
            List<MediaEntry> homeItems = new List<MediaEntry>();
            string q = (_searchQuery ?? "").ToLowerInvariant();
            foreach (MediaEntry e in snapshot)
            {
                if (!string.IsNullOrEmpty(q))
                {
                    bool hit = (e.Title ?? "").ToLowerInvariant().Contains(q) ||
                               (e.Subtitle ?? "").ToLowerInvariant().Contains(q);
                    if (!hit) continue;
                }
                homeItems.Add(e);
            }
            homeItems.Sort(delegate(MediaEntry a, MediaEntry b)
            {
                ItemProgress pa = progSnapshot.ContainsKey(a.Id) ? progSnapshot[a.Id] : null;
                ItemProgress pb = progSnapshot.ContainsKey(b.Id) ? progSnapshot[b.Id] : null;
                long la = pa != null ? pa.LastPlayedAt : 0L;
                long lb = pb != null ? pb.LastPlayedAt : 0L;
                if (la != lb) return lb.CompareTo(la);
                return b.AddedAt.CompareTo(a.AddedAt);
            });

            Label lblRecentHdr = new Label();
            lblRecentHdr.Text = string.IsNullOrEmpty(q) ? "Continue Watching & Recently Added" : "Search Results";
            lblRecentHdr.Font = new Font("Segoe UI", 13f, FontStyle.Bold);
            lblRecentHdr.ForeColor = _textPrimary;
            lblRecentHdr.Size = new Size(Math.Max(400, _mediaGrid.ClientSize.Width - 60), 28);
            lblRecentHdr.Margin = new Padding(6, 4, 6, 8);
            _mediaGrid.Controls.Add(lblRecentHdr);

            int maxHome = Math.Min(24, homeItems.Count);
            if (maxHome == 0)
            {
                Panel emptyHome = new Panel();
                emptyHome.Size = new Size(680, 100);
                emptyHome.BackColor = _surface;
                emptyHome.Margin = new Padding(6);
                Label lblNone = new Label();
                lblNone.Text = "Your library is empty. Click '+ Add Media Folder' above to point LootStream at your Movies, TV Shows, or Music.";
                lblNone.ForeColor = _textSecondary;
                lblNone.Location = new Point(18, 34);
                lblNone.Size = new Size(640, 40);
                emptyHome.Controls.Add(lblNone);
                _mediaGrid.Controls.Add(emptyHome);
            }
            else
            {
                for (int i = 0; i < maxHome; i++)
                {
                    MediaEntry entry = homeItems[i];
                    ItemProgress prog = progSnapshot.ContainsKey(entry.Id) ? progSnapshot[entry.Id] : null;
                    _mediaGrid.Controls.Add(CreateMediaCard(entry, prog, homeItems));
                }
            }
        }

        private Panel CreateStatTile(string label, string count, Color accent, int x, int y, Action onClick)
        {
            Panel p = new Panel();
            p.Size = new Size(148, 86);
            p.Location = new Point(x, y);
            p.BackColor = _surfaceAlt;
            p.Cursor = Cursors.Hand;

            Label lblCount = new Label();
            lblCount.Text = count;
            lblCount.Font = new Font("Segoe UI", 20f, FontStyle.Bold);
            lblCount.ForeColor = accent;
            lblCount.Location = new Point(14, 12);
            lblCount.AutoSize = true;

            Label lblName = new Label();
            lblName.Text = label;
            lblName.Font = new Font("Segoe UI", 9f);
            lblName.ForeColor = _textSecondary;
            lblName.Location = new Point(16, 52);
            lblName.AutoSize = true;

            EventHandler clickHandler = delegate { onClick(); };
            p.Click += clickHandler;
            lblCount.Click += clickHandler;
            lblName.Click += clickHandler;

            p.Controls.Add(lblCount);
            p.Controls.Add(lblName);
            return p;
        }

        private Panel CreateMediaCard(MediaEntry item, ItemProgress prog, List<MediaEntry> playlistContext)
        {
            bool isAudio = item.MediaType == "AUDIO";
            bool isTv = item.MediaType == "EPISODE" || item.MediaType == "SERIES";
            Color accent = isAudio ? _green : (isTv ? _purple : _cyan);
            bool isFav = prog != null && prog.IsFavorite;
            bool isWatched = prog != null && prog.WatchStatus == "WATCHED";

            Panel card = new Panel();
            card.Size = new Size(226, 282);
            card.Margin = new Padding(8);
            card.BackColor = _surface;

            Panel thumbBox = new Panel();
            thumbBox.Size = new Size(226, 134);
            thumbBox.Location = new Point(0, 0);
            thumbBox.BackColor = Color.FromArgb(20, 29, 46);
            thumbBox.Cursor = Cursors.Hand;

            Image poster = CyberUiAssets.LoadImageUnlocked(item.PosterPath);
            if (poster != null)
            {
                _cardBitmaps.Add(poster);
                PictureBox pic = new PictureBox();
                pic.Dock = DockStyle.Fill;
                pic.Image = poster;
                pic.SizeMode = PictureBoxSizeMode.Zoom;
                pic.Cursor = Cursors.Hand;
                pic.Click += delegate { PlayMediaItem(item, playlistContext); };
                thumbBox.Controls.Add(pic);
            }
            else
            {
                Label lblGlyph = new Label();
                lblGlyph.Text = isAudio ? "AUDIO" : (isTv ? "TV SHOW" : "MOVIE");
                lblGlyph.Font = new Font("Segoe UI", 12.5f, FontStyle.Bold);
                lblGlyph.ForeColor = accent;
                lblGlyph.TextAlign = ContentAlignment.MiddleCenter;
                lblGlyph.Dock = DockStyle.Fill;
                lblGlyph.Cursor = Cursors.Hand;
                lblGlyph.Click += delegate { PlayMediaItem(item, playlistContext); };
                thumbBox.Controls.Add(lblGlyph);
            }

            Label lblCodec = new Label();
            lblCodec.Text = item.CodecBadge;
            lblCodec.Font = new Font("Consolas", 7.5f, FontStyle.Bold);
            lblCodec.ForeColor = _bg;
            lblCodec.BackColor = accent;
            lblCodec.TextAlign = ContentAlignment.MiddleCenter;
            lblCodec.Size = new Size(44, 18);
            lblCodec.Location = new Point(174, 8);
            thumbBox.Controls.Add(lblCodec);
            lblCodec.BringToFront();

            if (prog != null && prog.DurationMs > 0 && prog.PlaybackPositionMs > 0)
            {
                double pct = Math.Min(1.0, Math.Max(0.0, (double)prog.PlaybackPositionMs / (double)prog.DurationMs));
                Panel barBg = new Panel();
                barBg.Size = new Size(226, 4);
                barBg.Location = new Point(0, 130);
                barBg.BackColor = Color.FromArgb(40, 50, 70);
                Panel barFill = new Panel();
                barFill.Size = new Size(Math.Max(6, (int)(226.0 * pct)), 4);
                barFill.Location = new Point(0, 0);
                barFill.BackColor = _green;
                barBg.Controls.Add(barFill);
                thumbBox.Controls.Add(barBg);
                barBg.BringToFront();
            }

            Label lblTitle = new Label();
            lblTitle.Text = item.Title;
            lblTitle.Font = new Font("Segoe UI", 9.5f, FontStyle.Bold);
            lblTitle.ForeColor = _textPrimary;
            lblTitle.Location = new Point(10, 142);
            lblTitle.Size = new Size(206, 40);

            string metaSub = item.Subtitle ?? "";
            if (item.SeasonNumber.HasValue && item.EpisodeNumber.HasValue)
            {
                metaSub = string.Format("{0} • S{1:00}E{2:00}", metaSub, item.SeasonNumber.Value, item.EpisodeNumber.Value);
            }
            if (isWatched) metaSub = "✓ " + metaSub;

            Label lblSub = new Label();
            lblSub.Text = metaSub;
            lblSub.Font = new Font("Segoe UI", 8.5f);
            lblSub.ForeColor = isWatched ? _green : _textSecondary;
            lblSub.Location = new Point(10, 184);
            lblSub.Size = new Size(206, 20);

            Label lblSize = new Label();
            lblSize.Text = FormatBytes(item.FileSizeBytes);
            lblSize.Font = new Font("Consolas", 8f);
            lblSize.ForeColor = _textSecondary;
            lblSize.Location = new Point(10, 206);
            lblSize.Size = new Size(206, 18);

            Button btnPlay = CreateCyberButton(isAudio ? "▶ Listen" : "▶ Watch", accent, _bg, 118, 32);
            btnPlay.Location = new Point(10, 236);
            btnPlay.Click += delegate { PlayMediaItem(item, playlistContext); };

            Button btnFav = CreateCyberButton("★", isFav ? Color.FromArgb(62, 50, 16) : _surfaceAlt, isFav ? Color.Gold : _textSecondary, 40, 32);
            btnFav.Location = new Point(134, 236);
            btnFav.Click += delegate
            {
                ToggleFavoriteItem(item.Id);
                RenderActiveTab();
            };

            Button btnWat = CreateCyberButton("✓", isWatched ? Color.FromArgb(16, 58, 42) : _surfaceAlt, isWatched ? _green : _textSecondary, 40, 32);
            btnWat.Location = new Point(178, 236);
            btnWat.Click += delegate
            {
                ToggleWatchedItem(item.Id);
                RenderActiveTab();
            };

            card.Controls.Add(thumbBox);
            card.Controls.Add(lblTitle);
            card.Controls.Add(lblSub);
            card.Controls.Add(lblSize);
            card.Controls.Add(btnPlay);
            card.Controls.Add(btnFav);
            card.Controls.Add(btnWat);
            return card;
        }

        private static string FormatBytes(long bytes)
        {
            if (bytes <= 0) return "";
            string[] units = new string[] { "B", "KB", "MB", "GB", "TB" };
            double val = bytes;
            int idx = 0;
            while (val >= 1024.0 && idx < units.Length - 1)
            {
                val /= 1024.0;
                idx++;
            }
            return idx >= 2 ? string.Format("{0:0.0} {1}", val, units[idx]) : string.Format("{0:0} {1}", val, units[idx]);
        }

        private static string FormatSeconds(double seconds)
        {
            if (seconds < 0 || double.IsNaN(seconds) || double.IsInfinity(seconds)) seconds = 0;
            int total = (int)seconds;
            int h = total / 3600;
            int m = (total % 3600) / 60;
            int s = total % 60;
            return h > 0 ? string.Format("{0}:{1:00}:{2:00}", h, m, s) : string.Format("{0}:{1:00}", m, s);
        }

        private void PlayMediaItem(MediaEntry item, List<MediaEntry> playlistContext)
        {
            if (item == null || !File.Exists(item.FilePath))
            {
                AppendLog("Media file missing on disk: " + (item != null ? item.FilePath : ""));
                return;
            }

            PersistCurrentPlaybackProgress(false);

            _currentItem = item;
            _activePlaylist = playlistContext != null && playlistContext.Count > 0
                ? new List<MediaEntry>(playlistContext)
                : new List<MediaEntry> { item };

            double resumeSec = 0.0;
            lock (_syncLock)
            {
                ItemProgress prog;
                if (_state.Progress.TryGetValue(item.Id, out prog) && prog != null)
                {
                    if (prog.PlaybackPositionMs > 5000 && (prog.DurationMs <= 0 || prog.PlaybackPositionMs < prog.DurationMs - 10000))
                    {
                        resumeSec = prog.PlaybackPositionMs / 1000.0;
                    }
                    prog.LastPlayedAt = ToUnixMs(DateTime.UtcNow);
                    if (string.IsNullOrEmpty(prog.WatchStatus) || prog.WatchStatus == "UNWATCHED")
                    {
                        prog.WatchStatus = "IN_PROGRESS";
                    }
                    SaveState();
                }
                else
                {
                    _state.Progress[item.Id] = new ItemProgress
                    {
                        WatchStatus = "IN_PROGRESS",
                        LastPlayedAt = ToUnixMs(DateTime.UtcNow)
                    };
                    SaveState();
                }
            }

            _lblTheaterTitle.Text = item.Title;
            _lblTheaterSub.Text = string.Format("{0}  •  {1}  •  {2}", item.MediaType, item.CodecBadge, item.Subtitle ?? "");
            _lblMiniNowPlaying.Text = "Now Playing: " + item.Title;
            _btnTheaterPlayPause.Text = "⏸ Pause";
            _btnMiniPlayPause.Text = "Pause";

            bool isAudio = item.MediaType == "AUDIO";
            _audioStagePanel.Visible = isAudio;
            if (isAudio)
            {
                _lblAudioStageTitle.Text = item.Title;
                _lblAudioStageSub.Text = (item.Subtitle ?? "Music") + "  •  " + item.CodecBadge;
                if (_picAudioArt.Image != null)
                {
                    _picAudioArt.Image.Dispose();
                    _picAudioArt.Image = null;
                }
                _picAudioArt.Image = CyberUiAssets.LoadImageUnlocked(item.PosterPath);
                _audioStagePanel.BringToFront();
            }

            SwitchTab(AppTab.Theater);
            UpdateTheaterActionBadges();

            bool started = _wmpHost != null && _wmpHost.TryLoad(item.FilePath, resumeSec);
            _wmpFallbackPanel.Visible = !started;
            if (!started)
            {
                _audioStagePanel.Visible = false;
                _wmpFallbackPanel.BringToFront();
            }
            else if (isAudio)
            {
                _audioStagePanel.BringToFront();
            }

            AppendLog("Playing in desktop app: " + item.Title);
        }

        private void TogglePlayPauseCurrent()
        {
            if (_currentItem == null || _wmpHost == null) return;
            int ps;
            double pos;
            double dur;
            _wmpHost.GetStateSnapshot(out ps, out pos, out dur);
            if (ps == 3)
            {
                _wmpHost.Pause();
                _btnTheaterPlayPause.Text = "▶ Play";
                _btnMiniPlayPause.Text = "Play";
                PersistCurrentPlaybackProgress(false);
            }
            else
            {
                _wmpHost.Play();
                _btnTheaterPlayPause.Text = "⏸ Pause";
                _btnMiniPlayPause.Text = "Pause";
            }
        }

        private void StopCurrentPlayback()
        {
            PersistCurrentPlaybackProgress(false);
            if (_wmpHost != null) _wmpHost.StopPlayback();
            _currentItem = null;
            _audioStagePanel.Visible = false;
            _wmpFallbackPanel.Visible = false;
            _seekBar.Value = 0;
            _lblTheaterTime.Text = "0:00 / 0:00";
            _btnTheaterPlayPause.Text = "▶ Play";
            UpdateMiniPlayerVisibility();
        }

        private void SeekRelative(double deltaSec)
        {
            if (_currentItem == null || _wmpHost == null) return;
            int ps;
            double pos;
            double dur;
            _wmpHost.GetStateSnapshot(out ps, out pos, out dur);
            double next = Math.Max(0.0, pos + deltaSec);
            if (dur > 0) next = Math.Min(dur, next);
            _wmpHost.SeekToSeconds(next);
        }

        private void SeekFromTrackBar()
        {
            if (_currentItem == null || _wmpHost == null) return;
            int ps;
            double pos;
            double dur;
            _wmpHost.GetStateSnapshot(out ps, out pos, out dur);
            if (dur > 0)
            {
                double target = ((double)_seekBar.Value / 1000.0) * dur;
                _wmpHost.SeekToSeconds(target);
            }
        }

        private void PlayAdjacentItem(int delta)
        {
            if (_currentItem == null || _activePlaylist.Count == 0) return;
            int idx = _activePlaylist.FindIndex(delegate(MediaEntry e) { return e.Id == _currentItem.Id; });
            if (idx < 0) return;
            int nextIdx = idx + delta;
            if (nextIdx >= 0 && nextIdx < _activePlaylist.Count)
            {
                PlayMediaItem(_activePlaylist[nextIdx], _activePlaylist);
            }
        }

        private void ToggleFullscreen()
        {
            _isFullscreen = !_isFullscreen;
            if (_isFullscreen)
            {
                _savedBorderStyle = this.FormBorderStyle;
                _savedWindowState = this.WindowState;
                _topBar.Visible = false;
                _sidebar.Visible = false;
                _miniPlayerBar.Visible = false;
                this.FormBorderStyle = FormBorderStyle.None;
                this.WindowState = FormWindowState.Maximized;
                _btnTheaterFullscreen.Text = "Exit Fullscreen";
            }
            else
            {
                this.FormBorderStyle = _savedBorderStyle;
                this.WindowState = _savedWindowState == FormWindowState.Maximized ? FormWindowState.Maximized : FormWindowState.Normal;
                _topBar.Visible = true;
                _sidebar.Visible = true;
                _btnTheaterFullscreen.Text = "⛶ Fullscreen";
                UpdateMiniPlayerVisibility();
            }
        }

        private void OnPlaybackTick()
        {
            if (_currentItem == null || _wmpHost == null || !_wmpHost.IsAvailable) return;
            int ps;
            double pos;
            double dur;
            _wmpHost.GetStateSnapshot(out ps, out pos, out dur);

            if (dur > 0 && !_isSeekingDrag)
            {
                int val = Math.Max(0, Math.Min(1000, (int)((pos / dur) * 1000.0)));
                _seekBar.Value = val;
            }
            _lblTheaterTime.Text = FormatSeconds(pos) + " / " + FormatSeconds(dur);

            if (ps == 3)
            {
                _btnTheaterPlayPause.Text = "⏸ Pause";
                _btnMiniPlayPause.Text = "Pause";
            }
            else if (ps == 2)
            {
                _btnTheaterPlayPause.Text = "▶ Play";
                _btnMiniPlayPause.Text = "Play";
            }

            long nowMs = ToUnixMs(DateTime.UtcNow);
            if (ps == 3 && nowMs - _lastProgressPersistMs >= 4000)
            {
                _lastProgressPersistMs = nowMs;
                PersistCurrentPlaybackProgress(false);
            }

            if (ps == 8) // MediaEnded
            {
                PersistCurrentPlaybackProgress(true);
                int idx = _activePlaylist.FindIndex(delegate(MediaEntry e) { return e.Id == _currentItem.Id; });
                if (idx >= 0 && idx + 1 < _activePlaylist.Count)
                {
                    PlayMediaItem(_activePlaylist[idx + 1], _activePlaylist);
                }
            }
        }

        private void PersistCurrentPlaybackProgress(bool markWatched)
        {
            if (_currentItem == null || _wmpHost == null) return;
            int ps;
            double pos;
            double dur;
            _wmpHost.GetStateSnapshot(out ps, out pos, out dur);
            long posMs = (long)(Math.Max(0.0, pos) * 1000.0);
            long durMs = (long)(Math.Max(0.0, dur) * 1000.0);

            lock (_syncLock)
            {
                ItemProgress prog;
                if (!_state.Progress.TryGetValue(_currentItem.Id, out prog) || prog == null)
                {
                    prog = new ItemProgress();
                    _state.Progress[_currentItem.Id] = prog;
                }
                if (posMs > 0) prog.PlaybackPositionMs = posMs;
                if (durMs > 0) prog.DurationMs = durMs;
                if (markWatched || (durMs > 0 && posMs > (long)(durMs * 0.92)))
                {
                    prog.WatchStatus = "WATCHED";
                    if (markWatched) prog.PlayCount++;
                }
                else if (posMs > 2000 && prog.WatchStatus != "WATCHED")
                {
                    prog.WatchStatus = "IN_PROGRESS";
                }
                prog.LastPlayedAt = ToUnixMs(DateTime.UtcNow);
                SaveState();
            }
        }

        private void ToggleFavoriteItem(string itemId)
        {
            lock (_syncLock)
            {
                ItemProgress prog;
                if (!_state.Progress.TryGetValue(itemId, out prog) || prog == null)
                {
                    prog = new ItemProgress();
                    _state.Progress[itemId] = prog;
                }
                prog.IsFavorite = !prog.IsFavorite;
                SaveState();
            }
        }

        private void ToggleWatchedItem(string itemId)
        {
            lock (_syncLock)
            {
                ItemProgress prog;
                if (!_state.Progress.TryGetValue(itemId, out prog) || prog == null)
                {
                    prog = new ItemProgress();
                    _state.Progress[itemId] = prog;
                }
                if (prog.WatchStatus == "WATCHED")
                {
                    prog.WatchStatus = "UNWATCHED";
                }
                else
                {
                    prog.WatchStatus = "WATCHED";
                    prog.PlayCount++;
                }
                SaveState();
            }
        }

        private void UpdateTheaterActionBadges()
        {
            if (_currentItem == null) return;
            bool isFav = false;
            bool isWatched = false;
            lock (_syncLock)
            {
                ItemProgress prog;
                if (_state.Progress.TryGetValue(_currentItem.Id, out prog) && prog != null)
                {
                    isFav = prog.IsFavorite;
                    isWatched = prog.WatchStatus == "WATCHED";
                }
            }
            _btnTheaterFav.BackColor = isFav ? Color.FromArgb(62, 50, 16) : _surfaceAlt;
            _btnTheaterFav.ForeColor = isFav ? Color.Gold : _textPrimary;
            _btnTheaterWatched.BackColor = isWatched ? Color.FromArgb(16, 58, 42) : _surfaceAlt;
            _btnTheaterWatched.ForeColor = isWatched ? _green : _textPrimary;
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
            dlg.BackColor = _bg;
            dlg.ForeColor = _textPrimary;
            if (_appIcon != null) try { dlg.Icon = _appIcon; } catch { }

            PictureBox pic = new PictureBox();
            pic.Size = new Size(240, 240);
            pic.Location = new Point(62, 20);
            pic.SizeMode = PictureBoxSizeMode.Zoom;
            pic.BackColor = Color.White;

            Label lblCode = new Label();
            lblCode.Font = new Font("Consolas", 22f, FontStyle.Bold);
            lblCode.ForeColor = _green;
            lblCode.TextAlign = ContentAlignment.MiddleCenter;
            lblCode.Location = new Point(20, 274);
            lblCode.Size = new Size(324, 40);

            Label lblInfo = new Label();
            lblInfo.Text = "Scan this QR code or enter the 6-digit code\nin LootStream Android on the same Wi-Fi.";
            lblInfo.Font = new Font("Segoe UI", 9.5f);
            lblInfo.ForeColor = _textSecondary;
            lblInfo.TextAlign = ContentAlignment.MiddleCenter;
            lblInfo.Location = new Point(20, 320);
            lblInfo.Size = new Size(324, 42);

            Button btnNew = CreateCyberButton("Generate New Code", _cyan, _bg, 160, 34);
            btnNew.Location = new Point(102, 376);

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
            if (_lstFolders == null) return;
            _lstFolders.Items.Clear();
            foreach (string folder in _state.Folders)
            {
                _lstFolders.Items.Add(folder);
            }
        }

        private void RefreshPairedDevicesList()
        {
            if (_lstPairedDevices == null) return;
            _lstPairedDevices.Items.Clear();
            lock (_syncLock)
            {
                if (_state.PairedDevices.Count == 0)
                {
                    _lstPairedDevices.Items.Add("No Android devices paired yet.");
                }
                else
                {
                    foreach (PairedDeviceInfo d in _state.PairedDevices)
                    {
                        _lstPairedDevices.Items.Add(d.deviceName);
                    }
                }
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
            if (this.IsDisposed) return;
            try
            {
                if (this.IsHandleCreated && this.InvokeRequired) this.BeginInvoke(action);
                else if (!this.InvokeRequired) action();
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

                string lanUrl = GetLanUrl();
                _lblServerStatusPill.Text = "ONLINE";
                _lblServerStatusPill.ForeColor = _green;
                _btnToggleServer.Text = "Stop Server";
                _btnToggleServer.BackColor = Color.FromArgb(239, 68, 68);
                _btnToggleServer.ForeColor = Color.White;
                _lblLanUrl.Text = lanUrl;
                _btnLanStatus.Text = "ONLINE  " + lanUrl;
                _btnLanStatus.BackColor = Color.FromArgb(16, 44, 40);
                _btnLanStatus.ForeColor = _green;
                if (_lblHomeLanUrl != null) _lblHomeLanUrl.Text = lanUrl;
                AppendLog("Server started on " + lanUrl + " (" + _catalog.Count + " media items)");
            }
            catch (Exception ex)
            {
                _isRunning = false;
                _lblServerStatusPill.Text = "ERROR";
                _lblServerStatusPill.ForeColor = Color.FromArgb(248, 113, 113);
                _btnLanStatus.Text = "SERVER ERROR";
                _btnLanStatus.ForeColor = Color.FromArgb(248, 113, 113);
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
                if (_lblServerStatusPill != null)
                {
                    _lblServerStatusPill.Text = "STOPPED";
                    _lblServerStatusPill.ForeColor = _textSecondary;
                }
                if (_btnLanStatus != null)
                {
                    _btnLanStatus.Text = "SERVER STOPPED";
                    _btnLanStatus.BackColor = _surfaceAlt;
                    _btnLanStatus.ForeColor = _textSecondary;
                }
                if (_btnToggleServer != null)
                {
                    _btnToggleServer.Text = "Start Server";
                    _btnToggleServer.BackColor = _cyan;
                    _btnToggleServer.ForeColor = _bg;
                }
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
                    RunOnUi(delegate { RefreshPairedDevicesList(); });
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
            RunOnUi(delegate
            {
                if (_lblMediaCount != null) _lblMediaCount.Text = scanned.Count + " items";
                UpdateSidebarCounts();
                if (_libraryPage != null && _libraryPage.Visible) RenderActiveTab();
            });
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
