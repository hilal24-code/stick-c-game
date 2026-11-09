using GamepadBridge.Protocol;
using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Targets;
using Nefarius.ViGEm.Client.Targets.Xbox360;

namespace GamepadBridge.Core;

public sealed class ViGEmXbox360Controller : IVirtualController
{
    private static readonly (GamepadButtons Source, Xbox360Button Target)[] Map =
    {
        (GamepadButtons.A,             Xbox360Button.A),
        (GamepadButtons.B,             Xbox360Button.B),
        (GamepadButtons.X,             Xbox360Button.X),
        (GamepadButtons.Y,             Xbox360Button.Y),
        (GamepadButtons.LeftShoulder,  Xbox360Button.LeftShoulder),
        (GamepadButtons.RightShoulder, Xbox360Button.RightShoulder),
        (GamepadButtons.LeftThumb,     Xbox360Button.LeftThumb),
        (GamepadButtons.RightThumb,    Xbox360Button.RightThumb),
        (GamepadButtons.Start,         Xbox360Button.Start),
        (GamepadButtons.Back,          Xbox360Button.Back),
        (GamepadButtons.Guide,         Xbox360Button.Guide),
        (GamepadButtons.DPadUp,        Xbox360Button.Up),
        (GamepadButtons.DPadDown,      Xbox360Button.Down),
        (GamepadButtons.DPadLeft,      Xbox360Button.Left),
        (GamepadButtons.DPadRight,     Xbox360Button.Right),
    };

    private readonly ViGEmClient _client;
    private readonly IXbox360Controller _pad;

    public event Action<byte, byte>? Feedback;

    public ViGEmXbox360Controller()
    {
        _client = new ViGEmClient();   // melempar exception bila ViGEmBus belum terpasang
        _pad = _client.CreateXbox360Controller();
        _pad.AutoSubmitReport = false;
        _pad.FeedbackReceived += (_, e) => Feedback?.Invoke(e.LargeMotor, e.SmallMotor);
    }

    public void Connect() => _pad.Connect();

    public void Submit(in GamepadState s)
    {
        foreach (var (source, target) in Map)
            _pad.SetButtonState(target, (s.Buttons & source) != 0);

        _pad.SetSliderValue(Xbox360Slider.LeftTrigger, s.LeftTrigger);
        _pad.SetSliderValue(Xbox360Slider.RightTrigger, s.RightTrigger);
        _pad.SetAxisValue(Xbox360Axis.LeftThumbX, s.LeftX);
        _pad.SetAxisValue(Xbox360Axis.LeftThumbY, s.LeftY);
        _pad.SetAxisValue(Xbox360Axis.RightThumbX, s.RightX);
        _pad.SetAxisValue(Xbox360Axis.RightThumbY, s.RightY);
        _pad.SubmitReport();
    }

    public void Dispose()
    {
        try { _pad.Disconnect(); } catch { /* sudah terputus */ }
        _client.Dispose();
    }
}
