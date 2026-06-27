package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ProtocolWireGoldenTest {
    @Test
    void rebuildsCapturedId2Id3AndId5Wire() {
        assertHex("0213cf0000019eefe085a0cf0000019eefe085a0",
            C2SPacketEncoders.encodeId2(1782140929440L, 1782140929440L));
        assertHex("030ccf0000019eefe0abfe0100",
            C2SPacketEncoders.encodeId3(1782140939262L, 1, 0));
        assertHex(
            "0569cf0000019eefe2a702cb40707a8638e7ccc2cb404b1fe55cb8e5a8cbc053b05ba826efa80101"
                + "cb40707fbccf0fe8a1cb404b000000000000cbc052b814a1278be4cb4070700000000000cb404a800000000000"
                + "cbc052c00000000000c2cac099a69aca41cd3341c3",
            C2SPacketEncoders.encodeId5(new C2SPacketEncoders.Id5UseBlock(
                1782141069058L,
                263.6577691130334,
                54.24918707874468,
                -78.75559428980216,
                1,
                1,
                263.98359590734805,
                54.0,
                -74.87625912534673,
                263.0,
                53.0,
                -75.0,
                false,
                -4.801587104797363f,
                25.650026321411133f,
                true
            ))
        );
    }

    @Test
    void preservesObservedId1LayoutSixFraming() {
        UUID uuid = new UUID(5475196311097722046L, -4634851894786249576L);
        UuidSelectedPayloadFramer framer = new UuidSelectedPayloadFramer();
        assertHex("0104101828", framer.framePacket(1, new byte[]{0x01, (byte)0x81, (byte)0x82}, uuid));
    }

    private static void assertHex(String expected, byte[] actual) {
        assertArrayEquals(unhex(expected), actual);
    }

    private static byte[] unhex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte)Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }
}
