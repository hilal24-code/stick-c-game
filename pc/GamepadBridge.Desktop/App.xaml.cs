using System;
using System.Threading;
using System.Windows;

namespace GamepadBridge.Desktop;

public partial class App : Application
{
    private const string MutexName = "GamepadBridge.Desktop.SingleInstance";
    private const string ShowEventName = "GamepadBridge.Desktop.ShowWindow";

    private Mutex? _mutex;
    private EventWaitHandle? _showEvent;
    private BridgeService? _service;
    private MainWindow? _window;
    private System.Windows.Forms.NotifyIcon? _tray;

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        // Hanya satu salinan yang boleh jalan; salinan kedua hanya memunculkan jendela yang pertama
        _mutex = new Mutex(true, MutexName, out bool createdNew);
        if (!createdNew)
        {
            try
            {
                using var existing = EventWaitHandle.OpenExisting(ShowEventName);
                existing.Set();
            }
            catch
            {
            }
            Shutdown();
            return;
        }

        ShutdownMode = ShutdownMode.OnExplicitShutdown;

        _showEvent = new EventWaitHandle(false, EventResetMode.AutoReset, ShowEventName);
        var watcher = new Thread(() =>
        {
            try
            {
                while (_showEvent.WaitOne())
                    Dispatcher.BeginInvoke(new Action(ShowWindow));
            }
            catch
            {
                // handle ditutup saat keluar
            }
        })
        { IsBackground = true };
        watcher.Start();

        _service = new BridgeService();
        _window = new MainWindow(_service);
        CreateTray();
        _service.Start();

        bool minimized = Array.IndexOf(e.Args, "--minimized") >= 0;
        if (!minimized) _window.Show();
    }

    private void CreateTray()
    {
        var menu = new System.Windows.Forms.ContextMenuStrip();
        menu.Items.Add("Buka GamepadBridge", null, (_, _) => ShowWindow());
        menu.Items.Add("Keluar", null, (_, _) => ExitApp());

        _tray = new System.Windows.Forms.NotifyIcon
        {
            Icon = LoadIcon(),
            Text = "GamepadBridge",
            Visible = true,
            ContextMenuStrip = menu,
        };
        _tray.DoubleClick += (_, _) => ShowWindow();

        _service!.StatusChanged += () => Dispatcher.BeginInvoke(new Action(UpdateTrayText));
        UpdateTrayText();
    }

    private void UpdateTrayText()
    {
        if (_tray == null || _service == null) return;
        string state = _service.Status switch
        {
            BridgeStatus.Connected => "Terhubung: " + (_service.ClientName ?? "HP"),
            BridgeStatus.Waiting => "Menunggu HP",
            BridgeStatus.WaitingForBluetooth => "Menunggu Bluetooth",
            BridgeStatus.Error => "Ada masalah",
            _ => "Memulai",
        };
        string text = "GamepadBridge - " + state;
        _tray.Text = text.Length > 63 ? text.Substring(0, 63) : text;
    }

    private static System.Drawing.Icon LoadIcon()
    {
        try
        {
            string? path = Environment.ProcessPath;
            if (path != null)
            {
                var icon = System.Drawing.Icon.ExtractAssociatedIcon(path);
                if (icon != null) return icon;
            }
        }
        catch
        {
        }
        return System.Drawing.SystemIcons.Application;
    }

    private void ShowWindow()
    {
        if (_window == null) return;
        _window.Show();
        if (_window.WindowState == WindowState.Minimized) _window.WindowState = WindowState.Normal;
        _window.Activate();
    }

    private void ExitApp()
    {
        if (_window != null) _window.ReallyClose = true;
        Shutdown();
    }

    protected override void OnExit(ExitEventArgs e)
    {
        if (_tray != null)
        {
            _tray.Visible = false;
            _tray.Dispose();
        }
        _service?.Dispose();
        try { _showEvent?.Dispose(); } catch { }
        try { _mutex?.ReleaseMutex(); } catch { }
        base.OnExit(e);
    }
}
