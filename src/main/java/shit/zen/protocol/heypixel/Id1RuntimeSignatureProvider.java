package shit.zen.protocol.heypixel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/** Java implementation of the string signature and file digest paths used by ID1. */
public final class Id1RuntimeSignatureProvider implements Id1PacketBuilder.Id1SignatureProvider {
    private static final int BUFFER_SIZE = 8192;
    private final PbeMd5DesId1Crypto crypto;

    public Id1RuntimeSignatureProvider(PbeMd5DesId1Crypto crypto) {
        this.crypto = Objects.requireNonNull(crypto, "crypto");
    }

    @Override
    public boolean available() {
        return crypto.available();
    }

    @Override
    public String digestPathLike(String path) {
        if (path == null || path.isBlank()) return "";
        try {
            return digestPath(Path.of(path));
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    public String digestPath(Path path) {
        if (path == null || !Files.isRegularFile(path)) return "";
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-1 is unavailable", error);
        }
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = Files.newInputStream(path)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException ignored) {
            return "";
        }
    }

    @Override
    public String signString(String value) {
        if (value == null) return null;
        byte[] encrypted = crypto.encrypt(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }
}
