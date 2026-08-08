package shit.zen.protocol.heypixel;

import java.util.HexFormat;
import java.util.Optional;

/** Structural view of the short-lived HPAC5 envelope delivered by S2C ID114. */
public record Hpac5SyncToken(
    String versionMarker,
    long issuedAtMillis,
    long expiresAtMillis,
    int nonceBytes,
    int ciphertextBytes,
    int tagBytes
) {
    private static final String PREFIX = "HPAC5";

    public static Optional<Hpac5SyncToken> parse(String token) {
        if (token == null) return Optional.empty();
        String[] parts = token.split("\\.", -1);
        if (parts.length != 5 || !PREFIX.equals(parts[0])
            || parts[1].length() != 36 || parts[2].length() != 24
            || parts[4].length() != 32 || (parts[3].length() & 1) != 0) {
            return Optional.empty();
        }
        try {
            if (!isHex(parts[1]) || !isHex(parts[2]) || !isHex(parts[3]) || !isHex(parts[4])) {
                return Optional.empty();
            }
            long issuedAt = Long.parseUnsignedLong(parts[1].substring(4, 20), 16);
            long expiresAt = Long.parseUnsignedLong(parts[1].substring(20), 16);
            if (Long.compareUnsigned(expiresAt, issuedAt) < 0) return Optional.empty();
            return Optional.of(new Hpac5SyncToken(
                parts[1].substring(0, 4),
                issuedAt,
                expiresAt,
                parts[2].length() / 2,
                parts[3].length() / 2,
                parts[4].length() / 2
            ));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    public long validityMillis() {
        return expiresAtMillis - issuedAtMillis;
    }

    private static boolean isHex(String value) {
        try {
            HexFormat.of().parseHex(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
