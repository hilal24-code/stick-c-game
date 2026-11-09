using System.Buffers.Binary;

namespace GamepadBridge.Protocol;

/// <summary>Encode/decode paket input (HP -> PC) dan feedback rumble (PC -> HP). Semua nilai little-endian.</summary>
public static class PacketCodec
{
    public const byte InputHeader = 0xAA;
    public const int InputLength = 15;

    public const byte FeedbackHeader = 0x55;
    public const int FeedbackLength = 5;

    public static void EncodeInput(in GamepadState s, byte sequence, Span<byte> dest)
    {
        if (dest.Length < InputLength) throw new ArgumentException("Buffer terlalu kecil.", nameof(dest));
        dest[0] = InputHeader;
        dest[1] = sequence;
        BinaryPrimitives.WriteUInt16LittleEndian(dest[2..], (ushort)s.Buttons);
        dest[4] = s.LeftTrigger;
        dest[5] = s.RightTrigger;
        BinaryPrimitives.WriteInt16LittleEndian(dest[6..], s.LeftX);
        BinaryPrimitives.WriteInt16LittleEndian(dest[8..], s.LeftY);
        BinaryPrimitives.WriteInt16LittleEndian(dest[10..], s.RightX);
        BinaryPrimitives.WriteInt16LittleEndian(dest[12..], s.RightY);
        dest[14] = Crc8.Compute(dest[..14]);
    }

    public static bool TryDecodeInput(ReadOnlySpan<byte> src, out InputPacket packet)
    {
        packet = default;
        if (src.Length != InputLength || src[0] != InputHeader) return false;
        if (Crc8.Compute(src[..14]) != src[14]) return false;

        var state = new GamepadState(
            (GamepadButtons)BinaryPrimitives.ReadUInt16LittleEndian(src[2..]),
            src[4],
            src[5],
            BinaryPrimitives.ReadInt16LittleEndian(src[6..]),
            BinaryPrimitives.ReadInt16LittleEndian(src[8..]),
            BinaryPrimitives.ReadInt16LittleEndian(src[10..]),
            BinaryPrimitives.ReadInt16LittleEndian(src[12..]));
        packet = new InputPacket(src[1], state);
        return true;
    }

    public static void EncodeFeedback(byte sequence, byte largeMotor, byte smallMotor, Span<byte> dest)
    {
        if (dest.Length < FeedbackLength) throw new ArgumentException("Buffer terlalu kecil.", nameof(dest));
        dest[0] = FeedbackHeader;
        dest[1] = sequence;
        dest[2] = largeMotor;
        dest[3] = smallMotor;
        dest[4] = Crc8.Compute(dest[..4]);
    }

    public static bool TryDecodeFeedback(ReadOnlySpan<byte> src, out byte sequence, out byte largeMotor, out byte smallMotor)
    {
        sequence = largeMotor = smallMotor = 0;
        if (src.Length != FeedbackLength || src[0] != FeedbackHeader) return false;
        if (Crc8.Compute(src[..4]) != src[4]) return false;
        sequence = src[1]; largeMotor = src[2]; smallMotor = src[3];
        return true;
    }
}
