using GamepadBridge.Core;
using GamepadBridge.Protocol;

namespace GamepadBridge.Simulator;

/// <summary>
/// Menguji seluruh jalur PC tanpa HP: state palsu -> encode paket -> parser stream (seperti data RFCOMM)
/// -> controller Xbox 360 virtual. Buka https://hardwaretester.com/gamepad untuk melihat hasilnya.
/// </summary>
internal static class Simulator
{
    private static void Main()
    {
        using var pad = new ViGEmXbox360Controller();
        pad.Feedback += (large, small) => Console.WriteLine($"[rumble] large={large} small={small}");
        pad.Connect();

        var parser = new PacketStreamParser();
        var buffer = new byte[PacketCodec.InputLength];
        bool running = true;
        Console.CancelKeyPress += (_, e) => { e.Cancel = true; running = false; };

        Console.WriteLine("Controller virtual aktif. Buka https://hardwaretester.com/gamepad. Tekan Ctrl+C untuk berhenti.");

        GamepadButtons[] cycle = { GamepadButtons.A, GamepadButtons.B, GamepadButtons.X, GamepadButtons.Y,
                                   GamepadButtons.DPadUp, GamepadButtons.DPadRight, GamepadButtons.DPadDown, GamepadButtons.DPadLeft };
        byte seq = 0;
        var clock = System.Diagnostics.Stopwatch.StartNew();

        while (running)
        {
            double t = clock.Elapsed.TotalSeconds;
            var state = new GamepadState(
                cycle[(int)t % cycle.Length],
                (byte)(255 * (0.5 + 0.5 * Math.Sin(t))),
                (byte)(255 * (0.5 + 0.5 * Math.Cos(t))),
                (short)(short.MaxValue * Math.Cos(t * 2)),
                (short)(short.MaxValue * Math.Sin(t * 2)),
                (short)(short.MaxValue * Math.Cos(t * 3)),
                (short)(short.MaxValue * Math.Sin(t * 3)));

            PacketCodec.EncodeInput(state, seq++, buffer);
            parser.Feed(buffer, p => pad.Submit(p.State));   // paket lewat parser seperti data nyata dari transport
            Thread.Sleep(8);                                  // kira-kira 100-125 Hz
        }

        Console.WriteLine($"Selesai. Paket valid: {parser.ValidPackets}, byte dibuang: {parser.DroppedBytes}");
    }
}
