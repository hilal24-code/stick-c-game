using System.Diagnostics;
using GamepadBridge.Core;
using GamepadBridge.Protocol;
using Windows.Devices.Bluetooth.Rfcomm;
using Windows.Networking.Sockets;

namespace GamepadBridge.Server;

/// <summary>
/// Server RFCOMM: HP terhubung lewat Bluetooth, data masuk ke parser protokol, lalu ke controller Xbox 360 virtual.
/// UUID layanan WAJIB sama dengan yang dipakai aplikasi Android nanti.
/// </summary>
internal static class Server
{
    private static readonly Guid ServiceUuid = Guid.Parse("6f1c2a40-5b7e-4a53-9c1e-3b8d2f4a7c10");
    private static TimeSpan IdleTimeout = TimeSpan.FromMilliseconds(500);

    private static readonly object PadLock = new();
    private static readonly object OutLock = new();
    private static Stream? _output;
    private static byte _feedbackSeq;
    private static long _lastPacketTimestamp;
    private static bool _connected;
    private static bool _isNeutral = true;

    private static async Task Main(string[] args)
    {
        // Opsional untuk pengujian manual: dotnet run --project GamepadBridge.Server -- --timeout 10000
        int idx = Array.IndexOf(args, "--timeout");
        if (idx >= 0 && idx + 1 < args.Length && int.TryParse(args[idx + 1], out int ms) && ms >= 100)
            IdleTimeout = TimeSpan.FromMilliseconds(ms);
        Console.WriteLine($"Batas diam sebelum reset ke netral: {IdleTimeout.TotalMilliseconds:0} ms");

        using var pad = new ViGEmXbox360Controller();
        pad.Connect();
        SubmitNeutral(pad);
        pad.Feedback += SendFeedback;

        using var cts = new CancellationTokenSource();
        Console.CancelKeyPress += (_, e) => { e.Cancel = true; cts.Cancel(); };

        // Dua layanan: UUID khusus (untuk aplikasi Android kita) dan Serial Port standar (agar app uji seperti
        // Serial Bluetooth Terminal bisa tersambung tanpa pengaturan tambahan).
        var providers = new List<RfcommServiceProvider>();
        var listeners = new List<StreamSocketListener>();
        foreach (var (name, id) in new[]
        {
            ("khusus", RfcommServiceId.FromUuid(ServiceUuid)),
            ("serial standar (mode uji)", RfcommServiceId.SerialPort),
        })
        {
            try
            {
                var provider = await RfcommServiceProvider.CreateAsync(id);
                var listener = new StreamSocketListener();
                listener.ConnectionReceived += (sender, e) => { _ = HandleClientAsync(e.Socket, pad, cts.Token); };
                await listener.BindServiceNameAsync(
                    provider.ServiceId.AsString(),
                    SocketProtectionLevel.BluetoothEncryptionAllowNullAuthentication);
                provider.StartAdvertising(listener, true);
                providers.Add(provider);
                listeners.Add(listener);
                Console.WriteLine($"Layanan {name} aktif: {id.Uuid}");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Layanan {name} GAGAL dibuka: {ex.Message}");
            }
        }

        if (providers.Count == 0)
        {
            Console.WriteLine("Tidak ada layanan Bluetooth yang bisa dibuka. Cek Bluetooth laptop aktif.");
            return;
        }

        Console.WriteLine("Server Bluetooth aktif.");
        Console.WriteLine("Menunggu HP terhubung... (Ctrl+C untuk berhenti)");

        // Watchdog: tidak ada paket valid > 500 ms -> kembalikan controller ke netral
        while (!cts.IsCancellationRequested)
        {
            try { await Task.Delay(100, cts.Token); } catch (OperationCanceledException) { break; }
            if (_connected && !_isNeutral &&
                Stopwatch.GetElapsedTime(Interlocked.Read(ref _lastPacketTimestamp)) > IdleTimeout)
            {
                Console.WriteLine($"Tidak ada data > {IdleTimeout.TotalMilliseconds:0} ms, controller direset ke netral.");
                SubmitNeutral(pad);
            }
        }

        foreach (var p in providers) p.StopAdvertising();
        foreach (var l in listeners) l.Dispose();
        SubmitNeutral(pad);
        Console.WriteLine("Server berhenti.");
    }

    private static async Task HandleClientAsync(StreamSocket socket, IVirtualController pad, CancellationToken ct)
    {
        Console.WriteLine("HP terhubung.");
        var parser = new PacketStreamParser();
        var buf = new byte[256];
        bool haveSeq = false;
        byte lastSeq = 0;
        int received = 0;

        using var input = socket.InputStream.AsStreamForRead();
        var output = socket.OutputStream.AsStreamForWrite();
        lock (OutLock) _output = output;
        _connected = true;
        Interlocked.Exchange(ref _lastPacketTimestamp, Stopwatch.GetTimestamp());

        try
        {
            while (!ct.IsCancellationRequested)
            {
                int n = await input.ReadAsync(buf, 0, buf.Length, ct);
                if (n == 0) break;   // HP menutup koneksi

                parser.Feed(buf.AsSpan(0, n), p =>
                {
                    if (haveSeq && !SequenceComparer.IsNewer(lastSeq, p.Sequence)) return;   // paket lama/duplikat
                    haveSeq = true;
                    lastSeq = p.Sequence;
                    Interlocked.Exchange(ref _lastPacketTimestamp, Stopwatch.GetTimestamp());

                    lock (PadLock) { pad.Submit(p.State); _isNeutral = IsNeutral(p.State); }

                    if (++received % 100 == 1)
                        Console.WriteLine($"Paket #{received}: tombol={p.State.Buttons} LX={p.State.LeftX} LY={p.State.LeftY} RX={p.State.RightX} RY={p.State.RightY}");
                });
            }
        }
        catch (OperationCanceledException) { }
        catch (Exception ex) { Console.WriteLine($"Koneksi error: {ex.Message}"); }
        finally
        {
            _connected = false;
            lock (OutLock) _output = null;
            SubmitNeutral(pad);
            socket.Dispose();
            Console.WriteLine($"HP terputus. Paket valid: {parser.ValidPackets}, byte dibuang: {parser.DroppedBytes}");
        }
    }

    private static bool IsNeutral(in GamepadState s) =>
        s.Buttons == GamepadButtons.None && s.LeftTrigger == 0 && s.RightTrigger == 0 &&
        s.LeftX == 0 && s.LeftY == 0 && s.RightX == 0 && s.RightY == 0;

    private static void SubmitNeutral(IVirtualController pad)
    {
        GamepadState neutral = default;
        lock (PadLock) { pad.Submit(neutral); _isNeutral = true; }
    }

    private static void SendFeedback(byte large, byte small)
    {
        lock (OutLock)
        {
            if (_output is null) return;
            try
            {
                Span<byte> packet = stackalloc byte[PacketCodec.FeedbackLength];
                PacketCodec.EncodeFeedback(_feedbackSeq++, large, small, packet);
                _output.Write(packet);
                _output.Flush();
            }
            catch { /* koneksi putus; ditangani di loop baca */ }
        }
    }
}
