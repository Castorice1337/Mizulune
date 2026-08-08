package shit.zen.protocol.heypixel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Safe, non-reversible metadata retained after an ID114 token is decoded. */
public record SyncTokenMetadata(
    int tokenLength,
    String tokenSha256,
    String format,
    Optional<Hpac5SyncToken> hpac5,
    boolean nativeSinkAvailable
) {
    public SyncTokenMetadata {
        if (tokenLength < 0) throw new IllegalArgumentException("negative token length");
        tokenSha256 = Objects.requireNonNull(tokenSha256, "tokenSha256");
        format = Objects.requireNonNull(format, "format");
        hpac5 = Objects.requireNonNull(hpac5, "hpac5");
    }

    public static SyncTokenMetadata fromToken(String token) {
        Objects.requireNonNull(token, "token");
        Optional<Hpac5SyncToken> hpac5 = Hpac5SyncToken.parse(token);
        return new SyncTokenMetadata(
            token.length(),
            sha256(token.getBytes(StandardCharsets.UTF_8)),
            hpac5.isPresent() ? "HPAC5" : "unknown",
            hpac5,
            false
        );
    }

    public SyncTokenMetadata withNativeSinkAvailable(boolean available) {
        if (nativeSinkAvailable == available) return this;
        return new SyncTokenMetadata(tokenLength, tokenSha256, format, hpac5, available);
    }

    public Map<String, Object> traceDetails() {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("tokenLength", tokenLength);
        details.put("tokenSha256", tokenSha256);
        details.put("format", format);
        details.put("nativeSinkAvailable", nativeSinkAvailable);
        hpac5.ifPresent(envelope -> {
            details.put("versionMarker", envelope.versionMarker());
            details.put("issuedAtMillis", envelope.issuedAtMillis());
            details.put("expiresAtMillis", envelope.expiresAtMillis());
            details.put("validityMillis", envelope.validityMillis());
            details.put("nonceBytes", envelope.nonceBytes());
            details.put("ciphertextBytes", envelope.ciphertextBytes());
            details.put("tagBytes", envelope.tagBytes());
        });
        return Map.copyOf(details);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
