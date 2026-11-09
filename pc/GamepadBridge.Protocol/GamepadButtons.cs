namespace GamepadBridge.Protocol;

/// <summary>Bitmask tombol (16-bit). Urutan bit adalah bagian dari kontrak protokol v1: JANGAN diubah.</summary>
[Flags]
public enum GamepadButtons : ushort
{
    None          = 0,
    A             = 1 << 0,   // PS: Cross (X)
    B             = 1 << 1,   // PS: Circle
    X             = 1 << 2,   // PS: Square
    Y             = 1 << 3,   // PS: Triangle
    LeftShoulder  = 1 << 4,   // L1 / LB
    RightShoulder = 1 << 5,   // R1 / RB
    LeftThumb     = 1 << 6,   // L3 (tekan analog kiri)
    RightThumb    = 1 << 7,   // R3 (tekan analog kanan)
    Start         = 1 << 8,
    Back          = 1 << 9,   // PS: Select
    Guide         = 1 << 10,  // PS: tombol PS / Home
    DPadUp        = 1 << 11,
    DPadDown      = 1 << 12,
    DPadLeft      = 1 << 13,
    DPadRight     = 1 << 14,
    // bit 15 dicadangkan
}
