using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Shapes;
using System.Windows.Threading;
using GamepadBridge.Protocol;

namespace GamepadBridge.Desktop;

public partial class MainWindow : Window
{
    private static readonly (GamepadButtons Button, string Label)[] ChipDefs =
    {
        (GamepadButtons.A, "A"), (GamepadButtons.B, "B"), (GamepadButtons.X, "X"), (GamepadButtons.Y, "Y"),
        (GamepadButtons.LeftShoulder, "LB"), (GamepadButtons.RightShoulder, "RB"),
        (GamepadButtons.LeftThumb, "L3"), (GamepadButtons.RightThumb, "R3"),
        (GamepadButtons.Start, "Start"), (GamepadButtons.Back, "Back"), (GamepadButtons.Guide, "Guide"),
        (GamepadButtons.DPadUp, "↑"), (GamepadButtons.DPadDown, "↓"),
        (GamepadButtons.DPadLeft, "←"), (GamepadButtons.DPadRight, "→"),
    };

    private static readonly Brush AccentBrush = new SolidColorBrush(Color.FromRgb(0x3D, 0xD6, 0xF5));
    private static readonly Brush DimBrush = new SolidColorBrush(Color.FromRgb(0x2A, 0x30, 0x40));
    private static readonly Brush GreenBrush = new SolidColorBrush(Color.FromRgb(0x3D, 0xDC, 0x97));
    private static readonly Brush YellowBrush = new SolidColorBrush(Color.FromRgb(0xF2, 0xC9, 0x4C));
    private static readonly Brush RedBrush = new SolidColorBrush(Color.FromRgb(0xE5, 0x64, 0x5F));
    private static readonly Brush GrayBrush = new SolidColorBrush(Color.FromRgb(0x9A, 0xA0, 0xA6));

    private readonly BridgeService _service;
    private readonly DispatcherTimer _timer;
    private readonly Dictionary<GamepadButtons, Border> _chips = new();

    /// <summary>Bila false, tombol tutup hanya menyembunyikan jendela ke tray.</summary>
    public bool ReallyClose { get; set; }

    public MainWindow(BridgeService service)
    {
        InitializeComponent();
        _service = service;

        BuildChips();

        AutoStartCheck.IsChecked = StartupManager.IsEnabled();
        AutoStartCheck.Checked += (_, _) => StartupManager.Set(true);
        AutoStartCheck.Unchecked += (_, _) => StartupManager.Set(false);

        _service.StatusChanged += () => Dispatcher.BeginInvoke(new Action(UpdateStatus));
        _service.Log += line => Dispatcher.BeginInvoke(new Action(() => AppendLog(line)));

        _timer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(33) };
        _timer.Tick += (_, _) => UpdateLive();
        IsVisibleChanged += (_, _) => _timer.IsEnabled = IsVisible;
        _timer.Start();

        UpdateStatus();
    }

    private void BuildChips()
    {
        foreach (var (button, label) in ChipDefs)
        {
            var chip = new Border
            {
                CornerRadius = new CornerRadius(6),
                Padding = new Thickness(9, 4, 9, 4),
                Margin = new Thickness(3),
                Background = DimBrush,
                Child = new TextBlock { Text = label, FontSize = 11, Foreground = Brushes.White },
            };
            _chips[button] = chip;
            ButtonPanel.Children.Add(chip);
        }
    }

    private void UpdateStatus()
    {
        var status = _service.Status;
        (string title, Brush color) = status switch
        {
            BridgeStatus.Connected => ("Terhubung", GreenBrush),
            BridgeStatus.Waiting => ("Menunggu HP", AccentBrush),
            BridgeStatus.WaitingForBluetooth => ("Menunggu Bluetooth", YellowBrush),
            BridgeStatus.Error => ("Ada masalah", RedBrush),
            _ => ("Memulai...", GrayBrush),
        };
        StatusTitle.Text = title;
        StatusDot.Fill = color;
        StatusDetailText.Text = _service.StatusDetail;
    }

    private void UpdateLive()
    {
        var s = _service.LastState;
        SetKnob(LKnob, s.LeftX, s.LeftY);
        SetKnob(RKnob, s.RightX, s.RightY);
        LtBar.Value = s.LeftTrigger;
        RtBar.Value = s.RightTrigger;

        foreach (var (button, chip) in _chips)
            chip.Background = (s.Buttons & button) != 0 ? AccentBrush : DimBrush;

        PacketsText.Text = _service.TotalPackets.ToString("N0");
        DroppedText.Text = _service.DroppedBytes.ToString("N0");
        RumbleText.Text = (_service.RumbleLevel * 100 / 255) + "%";
    }

    private static void SetKnob(Ellipse knob, short x, short y)
    {
        const double travel = 35;
        Canvas.SetLeft(knob, 35 + (x / 32768.0) * travel);
        Canvas.SetTop(knob, 35 - (y / 32768.0) * travel);   // Y positif = atas
    }

    private void AppendLog(string line)
    {
        if (LogBox.Text.Length > 20000) LogBox.Clear();
        LogBox.AppendText(DateTime.Now.ToString("HH:mm:ss") + "  " + line + Environment.NewLine);
        LogBox.ScrollToEnd();
    }

    private async void Restart_Click(object sender, RoutedEventArgs e)
    {
        RestartBtn.IsEnabled = false;
        try
        {
            await _service.RestartBluetoothAsync();
        }
        finally
        {
            RestartBtn.IsEnabled = true;
        }
    }

    private void Hide_Click(object sender, RoutedEventArgs e)
    {
        Hide();
    }

    protected override void OnClosing(CancelEventArgs e)
    {
        if (!ReallyClose)
        {
            e.Cancel = true;
            Hide();   // tombol X hanya menyembunyikan ke tray
        }
        base.OnClosing(e);
    }
}
