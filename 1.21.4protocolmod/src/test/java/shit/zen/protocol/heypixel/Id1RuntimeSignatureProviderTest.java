package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class Id1RuntimeSignatureProviderTest {
    private static final UUID LOCAL_UUID = UUID.fromString("4bfbce09-3e82-44be-bfad-b2c218b82c98");
    private static final byte[] CAPTURED_SALT = HexFormat.of().parseHex("d206d327dcd19632");

    @Test
    void reproducesCapturedAttackSignatureAndPreCrypto() {
        PbeMd5DesId1Crypto crypto = new PbeMd5DesId1Crypto(LOCAL_UUID, () -> CAPTURED_SALT);
        Id1RuntimeSignatureProvider signatures = new Id1RuntimeSignatureProvider(crypto);
        assertEquals("0gbTJ9zRljJHDBOAfv5OE5cm1XdrkpIg", signatures.signString("237580721"));

        Id1PacketBuilder builder = new Id1PacketBuilder(
            signatures,
            crypto,
            Id1PacketBuilder.EvidenceSampler.preserveOrder(),
            value -> value
        );
        byte[] actual = builder.buildPreCrypto(
            new Id1PacketBuilder.Challenge(
                UUID.fromString("9135521d-f537-42df-b02b-5f374dcd7093"),
                1782140927334L,
                Id1PacketBuilder.Id1Subtype.ATTACK,
                "237580721"
            ),
            new Id1PacketBuilder.Context(LOCAL_UUID, 1782140930124L),
            null
        );
        assertArrayEquals(HexFormat.of().parseHex(
            "cf0000019eefe0884cd92a2d343633343835313839343738363234393537367c2d7c"
                + "3534373531393633313130393737323230343603d92b2d353735323339393430373930"
                + "323532373334317c2d7c2d37393833333834343735383232373637333933cf0000019e"
                + "efe07d66ce624cfd3fd920306762544a397a526c6a4a4844424f416676354f453563"
                + "6d315864726b704967"
        ), actual);
    }

    @Test
    void usesSha1ForFileEvidence(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("evidence.jar");
        Files.writeString(file, "abc", StandardCharsets.UTF_8);
        PbeMd5DesId1Crypto crypto = new PbeMd5DesId1Crypto(LOCAL_UUID, () -> CAPTURED_SALT);
        Id1RuntimeSignatureProvider signatures = new Id1RuntimeSignatureProvider(crypto);

        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", signatures.digestPath(file));
        assertEquals("", signatures.digestPathLike(directory.resolve("missing.jar").toString()));
        assertEquals("237580721", new String(
            crypto.decrypt(Base64.getDecoder().decode(signatures.signString("237580721"))),
            StandardCharsets.UTF_8
        ));
    }
}
