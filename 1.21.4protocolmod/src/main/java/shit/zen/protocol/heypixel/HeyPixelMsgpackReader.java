package shit.zen.protocol.heypixel;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

/** Minimal reader for the token subset used by the recovered HeyPixel packets. */
public final class HeyPixelMsgpackReader {
    private final byte[] input;
    private int offset;

    public HeyPixelMsgpackReader(byte[] input) {
        this.input = input.clone();
    }

    public boolean hasRemaining() {
        return offset < input.length;
    }

    public int remaining() {
        return input.length - offset;
    }

    public boolean nextIsArray() {
        int token = peekToken();
        return (token & 0xf0) == 0x90 || token == 0xdc || token == 0xdd;
    }

    public long readLong() {
        int token = u8();
        if (token <= 0x7f) return token;
        if (token >= 0xe0) return (byte) token;
        return switch (token) {
            case 0xcc -> u8();
            case 0xcd -> u16();
            case 0xce -> u32();
            case 0xcf, 0xd3 -> i64();
            case 0xd0 -> (byte) u8();
            case 0xd1 -> (short) u16();
            case 0xd2 -> (int) u32();
            default -> throw tokenError("integer", token);
        };
    }

    public int readInt() {
        long value = readLong();
        if (value < Integer.MIN_VALUE || value > 0xffffffffL) {
            throw new IllegalArgumentException("integer does not fit 32 bits: " + value);
        }
        return (int) value;
    }

    public byte readByte() {
        long value = readLong();
        if (value < Byte.MIN_VALUE || value > 0xffL) {
            throw new IllegalArgumentException("integer does not fit 8 bits: " + value);
        }
        return (byte) value;
    }

    public boolean readBoolean() {
        int token = u8();
        return switch (token) {
            case 0xc2 -> false;
            case 0xc3 -> true;
            default -> throw tokenError("boolean", token);
        };
    }

    public float readFloat() {
        int token = u8();
        return switch (token) {
            case 0xca -> Float.intBitsToFloat((int) u32());
            case 0xcb -> (float) Double.longBitsToDouble(i64());
            default -> throw tokenError("float", token);
        };
    }

    public String readString() {
        int token = u8();
        int length;
        if ((token & 0xe0) == 0xa0) {
            length = token & 0x1f;
        } else {
            length = switch (token) {
                case 0xd9 -> u8();
                case 0xda -> u16();
                case 0xdb -> Math.toIntExact(u32());
                default -> throw tokenError("string", token);
            };
        }
        require(length);
        String value = new String(input, offset, length, StandardCharsets.UTF_8);
        offset += length;
        return value;
    }

    /** Reads a MessagePack raw value, matching asRawValue().asByteArray(). */
    public byte[] readRawBytes() {
        int token = u8();
        int length;
        if ((token & 0xe0) == 0xa0) {
            length = token & 0x1f;
        } else {
            length = switch (token) {
                case 0xc4, 0xd9 -> u8();
                case 0xc5, 0xda -> u16();
                case 0xc6, 0xdb -> Math.toIntExact(u32());
                default -> throw tokenError("raw string/binary", token);
            };
        }
        require(length);
        byte[] value = Arrays.copyOfRange(input, offset, offset + length);
        offset += length;
        return value;
    }

    public UUID readUuid() {
        String value = readString();
        int separator = value.indexOf("|-|");
        if (separator >= 0) {
            long least = Long.parseLong(value.substring(0, separator));
            long most = Long.parseLong(value.substring(separator + 3));
            return new UUID(most, least);
        }
        return UUID.fromString(value);
    }

    public UUID readCanonicalUuid() {
        return UUID.fromString(readString());
    }

    public int readArrayHeader() {
        int token = u8();
        if ((token & 0xf0) == 0x90) return token & 0x0f;
        return switch (token) {
            case 0xdc -> u16();
            case 0xdd -> Math.toIntExact(u32());
            default -> throw tokenError("array", token);
        };
    }

    public void skipValue() {
        int token = u8();
        if (token <= 0x7f || token >= 0xe0) return;
        if ((token & 0xe0) == 0xa0) {
            skipBytes(token & 0x1f);
            return;
        }
        if ((token & 0xf0) == 0x90) {
            skipValues(token & 0x0f);
            return;
        }
        if ((token & 0xf0) == 0x80) {
            skipValues((token & 0x0f) * 2);
            return;
        }
        switch (token) {
            case 0xc0, 0xc2, 0xc3 -> {
            }
            case 0xc4, 0xd9 -> skipBytes(u8());
            case 0xc5, 0xda -> skipBytes(u16());
            case 0xc6, 0xdb -> skipBytes(Math.toIntExact(u32()));
            case 0xc7 -> skipBytes(Math.addExact(u8(), 1));
            case 0xc8 -> skipBytes(Math.addExact(u16(), 1));
            case 0xc9 -> skipBytes(Math.addExact(Math.toIntExact(u32()), 1));
            case 0xca, 0xce, 0xd2 -> skipBytes(4);
            case 0xcb, 0xcf, 0xd3 -> skipBytes(8);
            case 0xcc, 0xd0 -> skipBytes(1);
            case 0xcd, 0xd1 -> skipBytes(2);
            case 0xd4 -> skipBytes(2);
            case 0xd5 -> skipBytes(3);
            case 0xd6 -> skipBytes(5);
            case 0xd7 -> skipBytes(9);
            case 0xd8 -> skipBytes(17);
            case 0xdc -> skipValues(u16());
            case 0xdd -> skipValues(Math.toIntExact(u32()));
            case 0xde -> skipValues(Math.multiplyExact(u16(), 2));
            case 0xdf -> skipValues(Math.multiplyExact(Math.toIntExact(u32()), 2));
            default -> throw tokenError("skippable value", token);
        }
    }

    private void skipValues(int count) {
        for (int i = 0; i < count; i++) skipValue();
    }

    private void skipBytes(int count) {
        require(count);
        offset += count;
    }

    private int peekToken() {
        require(1);
        return input[offset] & 0xff;
    }

    private int u8() {
        require(1);
        return input[offset++] & 0xff;
    }

    private int u16() {
        return (u8() << 8) | u8();
    }

    private long u32() {
        return ((long) u8() << 24) | ((long) u8() << 16) | ((long) u8() << 8) | u8();
    }

    private long i64() {
        long value = 0;
        for (int i = 0; i < 8; i++) value = (value << 8) | u8();
        return value;
    }

    private void require(int count) {
        if (count < 0 || count > input.length - offset) {
            throw new IllegalArgumentException("truncated MessagePack at " + offset + " need=" + count);
        }
    }

    private IllegalArgumentException tokenError(String expected, int token) {
        return new IllegalArgumentException("expected " + expected + " token at " + (offset - 1)
            + " but found 0x" + Integer.toHexString(token));
    }
}
