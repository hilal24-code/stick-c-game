namespace GamepadBridge.Protocol;

/// <summary>
/// Parser aliran byte (RFCOMM adalah stream, bukan pesan): menyusun paket dari potongan data,
/// membuang sampah, dan sinkron ulang ke header berikutnya bila checksum gagal.
/// </summary>
public sealed class PacketStreamParser
{
    private readonly byte[] _buf = new byte[PacketCodec.InputLength];
    private int _count;

    public int DroppedBytes { get; private set; }
    public int ValidPackets { get; private set; }

    public void Feed(ReadOnlySpan<byte> data, Action<InputPacket> onPacket)
    {
        foreach (byte b in data)
        {
            if (_count == 0 && b != PacketCodec.InputHeader) { DroppedBytes++; continue; }

            _buf[_count++] = b;
            if (_count < PacketCodec.InputLength) continue;

            if (PacketCodec.TryDecodeInput(_buf, out var packet))
            {
                ValidPackets++;
                _count = 0;
                onPacket(packet);
            }
            else
            {
                DroppedBytes++;   // buang byte header palsu, cari header berikutnya di sisa buffer
                Resync();
            }
        }
    }

    private void Resync()
    {
        int start = 1;
        while (start < _count && _buf[start] != PacketCodec.InputHeader) { DroppedBytes++; start++; }
        int remain = _count - start;
        Buffer.BlockCopy(_buf, start, _buf, 0, remain);
        _count = remain;
    }

    public void Reset() => _count = 0;
}
