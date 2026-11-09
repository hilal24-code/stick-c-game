namespace GamepadBridge.Protocol;

/// <summary>CRC-8 (polinomial 0x07, init 0x00, tanpa refleksi).</summary>
public static class Crc8
{
    public static byte Compute(ReadOnlySpan<byte> data)
    {
        byte crc = 0;
        foreach (byte b in data)
        {
            crc ^= b;
            for (int i = 0; i < 8; i++)
                crc = (crc & 0x80) != 0 ? (byte)((crc << 1) ^ 0x07) : (byte)(crc << 1);
        }
        return crc;
    }
}
