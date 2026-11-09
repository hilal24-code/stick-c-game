using GamepadBridge.Protocol;

namespace GamepadBridge.Core;

/// <summary>Abstraksi controller virtual. Implementasi ViGEm bisa diganti backend lain tanpa mengubah sisa program.</summary>
public interface IVirtualController : IDisposable
{
    void Connect();
    void Submit(in GamepadState state);

    /// <summary>Dipicu saat game mengirim rumble: (largeMotor, smallMotor), 0-255.</summary>
    event Action<byte, byte>? Feedback;
}
