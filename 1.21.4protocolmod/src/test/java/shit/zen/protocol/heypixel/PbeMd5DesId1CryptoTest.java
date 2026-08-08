package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PbeMd5DesId1CryptoTest {
    private static final HexFormat HEX = HexFormat.of();
    private static final UUID LOCAL_UUID = UUID.fromString("4bfbce09-3e82-44be-bfad-b2c218b82c98");
    private static final byte[] CAPTURED_PRE_CRYPTO = HEX.parseHex(
        "cf0000019eefe0884cd92a2d343633343835313839343738363234393537367c2d7c3534373531393633313130393737323230343603"
            + "d92b2d353735323339393430373930323532373334317c2d7c2d37393833333834343735383232373637333933cf0000019eefe07d66"
            + "ce624cfd3fd920306762544a397a526c6a4a4844424f416676354f4535636d315864726b704967"
    );
    private static final byte[] CAPTURED_POST_CRYPTO = HEX.parseHex(
        "87c8ef26c054888ebfe2382c5afdfaa50e73e443c2dd2b218888ee12254d6467ec567841999c79b03cde9f7acc06ac1f6a5e670d2"
            + "006ecc642eac0013757c0fd42c8ff0f1ce92a994ec09cbcd4b6d6e26b22f34c51d11df5bbebe36bb25c3460f19e33245ba46378e"
            + "50fbd7061892129905b6be1af30f327af97f3ad77cc9cd0c26342be38c68ea8bb1c2f755e4aa935418e0b853ce4f70b64f69fb2f"
            + "4e653d1"
    );

    @Test
    void decryptsAndRebuildsCapturedId1VectorExactly() {
        byte[] fixedSalt = java.util.Arrays.copyOf(CAPTURED_POST_CRYPTO, PbeMd5DesId1Crypto.SALT_LENGTH);
        PbeMd5DesId1Crypto crypto = new PbeMd5DesId1Crypto(LOCAL_UUID, () -> fixedSalt);

        assertEquals(147, CAPTURED_PRE_CRYPTO.length);
        assertEquals(160, CAPTURED_POST_CRYPTO.length);
        assertArrayEquals(CAPTURED_PRE_CRYPTO, crypto.decrypt(CAPTURED_POST_CRYPTO));
        assertArrayEquals(CAPTURED_POST_CRYPTO, crypto.encrypt(CAPTURED_PRE_CRYPTO));
    }

    @Test
    void unwrapsEncryptedAttackAndShortChallenges() {
        byte[] salt = HEX.parseHex("0102030405060708");
        PbeMd5DesId1Crypto crypto = new PbeMd5DesId1Crypto(LOCAL_UUID, () -> salt);

        byte[] attackPlaintext = new HeyPixelMsgpackWriter()
            .packString(LOCAL_UUID.toString())
            .packLong(123)
            .packInt(3)
            .packString("[{\"action\":\"getEnumConstant\"}]")
            .toByteArray();
        S2CPayloadUnwrapper.UnwrappedPacket attack = S2CPayloadUnwrapper.unwrap(
            envelope(101, crypto.encrypt(attackPlaintext), new byte[]{0x55, 0x66}), crypto);
        assertEquals(101, attack.packetId());
        assertEquals(2, attack.trailingBytes());
        assertArrayEquals(attackPlaintext, attack.payload());
        S2CPacketDecoders.Id101Challenge decoded = S2CPacketDecoders.decodeId101(attack.payload());
        assertEquals(Id1PacketBuilder.Id1Subtype.ATTACK, decoded.subtype());

        byte[] sprintPlaintext = new HeyPixelMsgpackWriter()
            .packString(LOCAL_UUID.toString()).packLong(456).packInt(0).toByteArray();
        S2CPayloadUnwrapper.UnwrappedPacket sprint = S2CPayloadUnwrapper.unwrap(
            envelope(101, crypto.encrypt(sprintPlaintext), new byte[0]), crypto);
        assertEquals(Id1PacketBuilder.Id1Subtype.SPRINT,
            S2CPacketDecoders.decodeId101(sprint.payload()).subtype());
    }

    @Test
    void decryptsCapturedServerSprintChallengeWithThePlayerUuid() {
        byte[] encrypted = HEX.parseHex(
            "c9c4b87a20a66f52bda5ae33213d193dda66c4c9ba23b3154b92b1d3aead234"
                + "821d05111a7eb9b7e7cf262e7c5d8562259f3541e086a14cafabbc5fc3623e767"
        );
        PbeMd5DesId1Crypto crypto = new PbeMd5DesId1Crypto(LOCAL_UUID);

        S2CPayloadUnwrapper.UnwrappedPacket packet = S2CPayloadUnwrapper.unwrap(
            envelope(101, encrypted, new byte[0]), crypto);
        S2CPacketDecoders.Id101Challenge challenge = S2CPacketDecoders.decodeId101(packet.payload());

        assertEquals(UUID.fromString("08ccbd4e-2412-4039-91ce-b9f3f12970ef"), challenge.packetUuid());
        assertEquals(1782141125847L, challenge.packetLong());
        assertEquals(Id1PacketBuilder.Id1Subtype.SPRINT, challenge.subtype());
    }

    private static byte[] envelope(int packetId, byte[] encrypted, byte[] trailing) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(S2CPacketDecoders.BINARY_BRIDGE_DISCRIMINATOR);
        out.write(packetId >>> 24);
        out.write(packetId >>> 16);
        out.write(packetId >>> 8);
        out.write(packetId);
        UuidSelectedPayloadFramer.writeVarInt(out, encrypted.length);
        out.writeBytes(encrypted);
        out.writeBytes(trailing);
        return out.toByteArray();
    }
}
