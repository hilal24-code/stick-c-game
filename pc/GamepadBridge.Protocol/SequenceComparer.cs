namespace GamepadBridge.Protocol;

public static class SequenceComparer
{
    /// <summary>True bila <paramref name="incoming"/> lebih baru dari <paramref name="last"/> (aritmetika modulo 256).</summary>
    public static bool IsNewer(byte last, byte incoming)
    {
        byte diff = (byte)(incoming - last);
        return diff is > 0 and < 128;
    }
}
