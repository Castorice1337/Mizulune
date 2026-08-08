package shit.zen.protocol.heypixel;

import java.util.Arrays;
import java.util.Set;

/** Splits the outer int32 business id and decrypts only recovered encrypted packet families. */
public final class S2CPayloadUnwrapper {
    private static final Set<Integer> ENCRYPTED_IDS = Set.of(100, 101, 103, 104, 105, 110, 111, 112);
    private static final Set<Integer> LENGTH_PREFIXED_PLAINTEXT_IDS = Set.of(114);

    private S2CPayloadUnwrapper() {
    }

    public static UnwrappedPacket unwrap(byte[] wire, PbeMd5DesId1Crypto crypto) {
        S2CPacketDecoders.WrappedPacket wrapped = S2CPacketDecoders.decodeWrapper(wire);
        if (LENGTH_PREFIXED_PLAINTEXT_IDS.contains(wrapped.packetId())) {
            LengthPrefixedPayload payload = readLengthPrefixedPayload(wrapped.packetId(), wrapped.payload());
            return new UnwrappedPacket(wrapped.packetId(), payload.bytes(), false, payload.trailingBytes());
        }
        if (!ENCRYPTED_IDS.contains(wrapped.packetId())) {
            return new UnwrappedPacket(wrapped.packetId(), wrapped.payload(), false, 0);
        }
        if (crypto == null) {
            throw new IllegalStateException("S2C ID" + wrapped.packetId() + " arrived before ID1 crypto initialization");
        }

        LengthPrefixedPayload payload = readLengthPrefixedPayload(wrapped.packetId(), wrapped.payload());
        byte[] plaintext = crypto.decrypt(payload.bytes());
        return new UnwrappedPacket(wrapped.packetId(), plaintext, true, payload.trailingBytes());
    }

    private static LengthPrefixedPayload readLengthPrefixedPayload(int packetId, byte[] payload) {
        LengthPrefix prefix = readVarInt(payload);
        if (prefix.value() < 0 || prefix.value() > prefix.remaining()) {
            throw new IllegalArgumentException("S2C ID" + packetId
                + " payload length " + prefix.value() + " exceeds remaining " + prefix.remaining());
        }
        int end = prefix.bytesRead() + prefix.value();
        return new LengthPrefixedPayload(
            Arrays.copyOfRange(payload, prefix.bytesRead(), end),
            payload.length - end
        );
    }

    static LengthPrefix readVarInt(byte[] input) {
        int result = 0;
        int shift = 0;
        for (int index = 0; index < Math.min(input.length, 5); index++) {
            int value = input[index] & 0xff;
            result |= (value & 0x7f) << shift;
            if ((value & 0x80) == 0) {
                return new LengthPrefix(result, index + 1, input.length - index - 1);
            }
            shift += 7;
        }
        throw new IllegalArgumentException("malformed S2C VarInt length prefix");
    }

    public record UnwrappedPacket(int packetId, byte[] payload, boolean encrypted, int trailingBytes) {
        public UnwrappedPacket {
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }

    record LengthPrefix(int value, int bytesRead, int remaining) {
    }

    private record LengthPrefixedPayload(byte[] bytes, int trailingBytes) {
    }
}
