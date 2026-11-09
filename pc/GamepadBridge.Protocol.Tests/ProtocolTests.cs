using GamepadBridge.Protocol;
using Xunit;

namespace GamepadBridge.Protocol.Tests;

public class ProtocolTests
{
    private static readonly GamepadState Sample = new(
        GamepadButtons.A | GamepadButtons.DPadLeft | GamepadButtons.Start,
        12, 250, -32768, 32767, -1, 1);

    [Fact]
    public void Input_RoundTrip_PreservesAllFields()
    {
        var buf = new byte[PacketCodec.InputLength];
        PacketCodec.EncodeInput(Sample, 42, buf);

        Assert.True(PacketCodec.TryDecodeInput(buf, out var p));
        Assert.Equal(42, p.Sequence);
        Assert.Equal(Sample, p.State);
    }

    [Fact]
    public void Input_CorruptedByte_IsRejected()
    {
        var buf = new byte[PacketCodec.InputLength];
        PacketCodec.EncodeInput(Sample, 1, buf);
        buf[6] ^= 0x10;
        Assert.False(PacketCodec.TryDecodeInput(buf, out _));
    }

    [Fact]
    public void Feedback_RoundTrip()
    {
        var buf = new byte[PacketCodec.FeedbackLength];
        PacketCodec.EncodeFeedback(7, 200, 100, buf);
        Assert.True(PacketCodec.TryDecodeFeedback(buf, out var seq, out var large, out var small));
        Assert.Equal((7, 200, 100), (seq, large, small));
    }

    [Fact]
    public void StreamParser_HandlesGarbageAndSplitChunks()
    {
        var a = new byte[PacketCodec.InputLength];
        var b = new byte[PacketCodec.InputLength];
        PacketCodec.EncodeInput(Sample, 1, a);
        PacketCodec.EncodeInput(Sample, 2, b);

        // sampah (termasuk header palsu 0xAA) + dua paket valid
        var stream = new List<byte> { 0x01, 0xAA, 0x02 };
        stream.AddRange(a);
        stream.AddRange(b);

        var parser = new PacketStreamParser();
        var received = new List<byte>();
        var data = stream.ToArray();
        for (int i = 0; i < data.Length; i += 4)   // potong-potong 4 byte
            parser.Feed(data.AsSpan(i, Math.Min(4, data.Length - i)), p => received.Add(p.Sequence));

        Assert.Equal(new byte[] { 1, 2 }, received);
        Assert.Equal(2, parser.ValidPackets);
    }

    [Theory]
    [InlineData(10, 11, true)]
    [InlineData(250, 3, true)]    // wrap-around
    [InlineData(3, 250, false)]   // paket lama
    [InlineData(5, 5, false)]     // duplikat
    public void Sequence_Comparison(byte last, byte incoming, bool expected)
        => Assert.Equal(expected, SequenceComparer.IsNewer(last, incoming));
}
