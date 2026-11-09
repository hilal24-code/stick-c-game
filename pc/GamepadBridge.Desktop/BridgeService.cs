using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using GamepadBridge.Core;
using GamepadBridge.Protocol;
using Windows.Devices.Bluetooth;
using Windows.Devices.Bluetooth.Rfcomm;
using Windows.Networking.Sockets;

namespace GamepadBridge.Desktop;

public enum BridgeStatus { Starting, WaitingForBluetooth, Waiting, Connected, Error }

/// <summary>
/// Inti server: layanan RFCOMM -> parser protokol -> controller Xbox 360 virtual.
/// Tidak punya UI; MainWindow hanya membaca properti dan mendengarkan event.
/// </summary>
public sealed class BridgeService : IDisposable
{
    private static readonly Guid ServiceUuid = Guid.Parse("6f1c2a40-5b7e-4a53-9c1e-3b8d2f4a7c10");
    private static readonly TimeSpan IdleTimeout = TimeSpan.FromMilliseconds(500);

    private readonly CancellationTokenSource _cts = new();
    private readonly object _padLock = new();
    private readonly object _outLock = new();
    private readonly object _svcLock = new();
    private readonly object _stateLock = new();

    private readonly List<RfcommServiceProvider> _providers = new();
    private readonly List<StreamSocketListener> _listeners = new();

    private IVirtualController? _pad;
    private Stream? _output;
    private StreamSocket? _activeSocket;
    private GamepadState _lastState;
    private byte _feedbackSeq;
    private long _lastPacketTimestamp;
    private long _totalPackets;
    private long _droppedBytes;
    private int _connId;
    private int _rumble;
    private bool _isNeutral = true;

    public event Action<string>? Log;
    public event Action? StatusChanged;

    public BridgeStatus Status { get; private set; } = BridgeStatus.Starting;
    public string StatusDetail { get; private set; } = "";
    public string? ClientName { get; private set; }
    public long TotalPackets => Interlocked.Read(ref _totalPackets);
    public long DroppedBytes => Interlocked.Read(ref _droppedBytes);
    public int RumbleLevel => Volatile.Read(ref _rumble);

    public GamepadState LastState
    {
        get { lock (_stateLock) return _lastState; }
    }

    public void Start() => _ = Task.Run(RunAsync);

    private async Task RunAsync()
    {
        try
        {
            try
            {
                _pad = new ViGEmXbox360Controller();
                _pad.Connect();
            }
            catch (Exception ex)
            {
                SetStatus(BridgeStatus.Error, "ViGEmBus belum terpasang atau tidak bisa dibuka.");
                Log?.Invoke("ViGEmBus error: " + ex.Message);
                return;
            }
            _pad.Feedback += SendFeedback;
            SubmitNeutral();

            while (!_cts.IsCancellationRequested)
            {
                int opened = await OpenServicesAsync();
                if (opened > 0) break;
                SetStatus(BridgeStatus.WaitingForBluetooth, "Bluetooth belum siap, mencoba lagi...");
                try { await Task.Delay(5000, _cts.Token); }
                catch (OperationCanceledException) { return; }
            }
            if (_cts.IsCancellationRequested) return;

            SetStatus(BridgeStatus.Waiting, "Menunggu HP terhubung...");

            // Watchdog: tidak ada paket valid > 500 ms -> kembalikan controller ke netral
            while (!_cts.IsCancellationRequested)
            {
                try { await Task.Delay(100, _cts.Token); }
                catch (OperationCanceledException) { break; }

                if (Status == BridgeStatus.Connected && !_isNeutral &&
                    Stopwatch.GetElapsedTime(Interlocked.Read(ref _lastPacketTimestamp)) > IdleTimeout)
                {
                    SubmitNeutral();
                    Log?.Invoke("Tidak ada data > 500 ms, controller direset ke netral.");
                }
            }
        }
        catch (Exception ex)
        {
            SetStatus(BridgeStatus.Error, "Terjadi kesalahan: " + ex.Message);
            Log?.Invoke("Error: " + ex);
        }
    }

    /// <summary>Membuka dua layanan: UUID khusus (aplikasi HP) dan Serial Port standar (aplikasi uji).</summary>
    private async Task<int> OpenServicesAsync()
    {
        var ids = new (string Name, RfcommServiceId Id)[]
        {
            ("khusus", RfcommServiceId.FromUuid(ServiceUuid)),
            ("serial standar", RfcommServiceId.SerialPort),
        };

        int opened = 0;
        foreach (var (name, id) in ids)
        {
            try
            {
                var provider = await RfcommServiceProvider.CreateAsync(id);
                var listener = new StreamSocketListener();
                listener.ConnectionReceived += (sender, e) => { _ = HandleClientAsync(e.Socket); };
                await listener.BindServiceNameAsync(
                    provider.ServiceId.AsString(),
                    SocketProtectionLevel.BluetoothEncryptionAllowNullAuthentication);
                provider.StartAdvertising(listener, true);

                lock (_svcLock)
                {
                    _providers.Add(provider);
                    _listeners.Add(listener);
                }
                opened++;
                Log?.Invoke($"Layanan Bluetooth {name} aktif.");
            }
            catch (Exception ex)
            {
                Log?.Invoke($"Layanan Bluetooth {name} gagal dibuka: {ex.Message}");
            }
        }
        return opened;
    }

    private void CloseServices()
    {
        lock (_svcLock)
        {
            foreach (var p in _providers)
            {
                try { p.StopAdvertising(); } catch { }
            }
            foreach (var l in _listeners)
            {
                try { l.Dispose(); } catch { }
            }
            _providers.Clear();
            _listeners.Clear();
        }
    }

    public async Task RestartBluetoothAsync()
    {
        Log?.Invoke("Memulai ulang layanan Bluetooth...");
        CloseServices();
        int opened = await OpenServicesAsync();
        if (Status != BridgeStatus.Connected)
        {
            if (opened > 0) SetStatus(BridgeStatus.Waiting, "Menunggu HP terhubung...");
            else SetStatus(BridgeStatus.WaitingForBluetooth, "Bluetooth belum siap. Cek Bluetooth laptop lalu coba lagi.");
        }
    }

    private async Task HandleClientAsync(StreamSocket socket)
    {
        int myId = Interlocked.Increment(ref _connId);
        var old = Interlocked.Exchange(ref _activeSocket, socket);
        try { old?.Dispose(); } catch { }

        string name = await ResolveNameAsync(socket);
        ClientName = name;
        Log?.Invoke("HP terhubung: " + name);
        Interlocked.Exchange(ref _lastPacketTimestamp, Stopwatch.GetTimestamp());
        SetStatus(BridgeStatus.Connected, name);

        var parser = new PacketStreamParser();
        var buf = new byte[256];
        bool haveSeq = false;
        byte lastSeq = 0;

        Stream input = System.IO.WindowsRuntimeStreamExtensions.AsStreamForRead(socket.InputStream);
        Stream output = System.IO.WindowsRuntimeStreamExtensions.AsStreamForWrite(socket.OutputStream);
        lock (_outLock) _output = output;

        try
        {
            while (!_cts.IsCancellationRequested)
            {
                int n = await input.ReadAsync(buf, 0, buf.Length, _cts.Token);
                if (n == 0) break;   // HP menutup koneksi

                parser.Feed(buf.AsSpan(0, n), p =>
                {
                    if (haveSeq && !SequenceComparer.IsNewer(lastSeq, p.Sequence)) return;   // paket lama/duplikat
                    haveSeq = true;
                    lastSeq = p.Sequence;
                    Interlocked.Exchange(ref _lastPacketTimestamp, Stopwatch.GetTimestamp());
                    Interlocked.Increment(ref _totalPackets);

                    lock (_padLock)
                    {
                        _pad?.Submit(p.State);
                        _isNeutral = IsNeutral(p.State);
                    }
                    lock (_stateLock) _lastState = p.State;
                });
            }
        }
        catch (OperationCanceledException) { }
        catch (Exception ex)
        {
            Log?.Invoke("Koneksi error: " + ex.Message);
        }
        finally
        {
            Interlocked.Add(ref _droppedBytes, parser.DroppedBytes);
            if (myId == Volatile.Read(ref _connId))
            {
                lock (_outLock) _output = null;
                SubmitNeutral();
                ClientName = null;
                Volatile.Write(ref _rumble, 0);
                Log?.Invoke("HP terputus.");
                if (!_cts.IsCancellationRequested)
                    SetStatus(BridgeStatus.Waiting, "Menunggu HP terhubung...");
            }
            try { socket.Dispose(); } catch { }
            Interlocked.CompareExchange(ref _activeSocket, null, socket);
        }
    }

    private static async Task<string> ResolveNameAsync(StreamSocket socket)
    {
        try
        {
            var host = socket.Information.RemoteHostName;
            using var device = await BluetoothDevice.FromHostNameAsync(host);
            if (device != null && !string.IsNullOrWhiteSpace(device.Name)) return device.Name;
            return host?.DisplayName ?? "HP";
        }
        catch
        {
            return "HP";
        }
    }

    private static bool IsNeutral(in GamepadState s) =>
        s.Buttons == GamepadButtons.None && s.LeftTrigger == 0 && s.RightTrigger == 0 &&
        s.LeftX == 0 && s.LeftY == 0 && s.RightX == 0 && s.RightY == 0;

    private void SubmitNeutral()
    {
        GamepadState neutral = default;
        lock (_padLock)
        {
            _pad?.Submit(neutral);
            _isNeutral = true;
        }
        lock (_stateLock) _lastState = neutral;
    }

    private void SendFeedback(byte large, byte small)
    {
        Volatile.Write(ref _rumble, Math.Max(large, small));
        lock (_outLock)
        {
            if (_output is null) return;
            try
            {
                var packet = new byte[PacketCodec.FeedbackLength];
                PacketCodec.EncodeFeedback(_feedbackSeq++, large, small, packet);
                _output.Write(packet, 0, packet.Length);
                _output.Flush();
            }
            catch
            {
                // koneksi putus; ditangani di loop baca
            }
        }
    }

    private void SetStatus(BridgeStatus status, string detail)
    {
        Status = status;
        StatusDetail = detail;
        StatusChanged?.Invoke();
    }

    public void Dispose()
    {
        _cts.Cancel();
        CloseServices();
        try { _activeSocket?.Dispose(); } catch { }
        try { SubmitNeutral(); } catch { }
        try { _pad?.Dispose(); } catch { }
        _cts.Dispose();
    }
}
