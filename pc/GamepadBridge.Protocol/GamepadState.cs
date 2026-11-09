namespace GamepadBridge.Protocol;

/// <summary>Snapshot lengkap kondisi gamepad. Sumbu: -32768..32767, Y positif = ke atas.</summary>
public readonly record struct GamepadState(
    GamepadButtons Buttons,
    byte LeftTrigger,
    byte RightTrigger,
    short LeftX,
    short LeftY,
    short RightX,
    short RightY);

public readonly record struct InputPacket(byte Sequence, GamepadState State);
