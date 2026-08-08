package shit.zen.protocol.heypixel;

import java.util.Objects;

/** Adds the one-byte HeyPixel bridge discriminator around an internal business packet. */
public final class HeyPixelOuterBridge {
    private HeyPixelOuterBridge() {
    }

    public static byte[] wrapBinary(byte[] businessWire) {
        Objects.requireNonNull(businessWire, "businessWire");
        byte[] result = new byte[businessWire.length + 1];
        result[0] = (byte) S2CPacketDecoders.BINARY_BRIDGE_DISCRIMINATOR;
        System.arraycopy(businessWire, 0, result, 1, businessWire.length);
        return result;
    }
}
