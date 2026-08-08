package shit.zen.protocol.heypixel;

import java.time.Instant;

public record ProtocolSessionSnapshot(
    int version,
    String source,
    String roleName,
    String serverAddress,
    int serverPort,
    String userId,
    String userTokenHash,
    String entityId,
    String sdkUid,
    String sessionId,
    String deviceId,
    String gameId,
    String launcherVersion,
    Instant createdAt,
    Instant expiresAt,
    String signature
) {
    public ProtocolSessionSnapshot(
        String roleName,
        String serverAddress,
        int serverPort,
        int userId,
        String userTokenHash,
        String entityId,
        String sdkUid,
        String sessionId,
        String deviceId,
        String gameId,
        String launcherVersion,
        Instant createdAt,
        Instant expiresAt,
        String signature
    ) {
        this(1, "opensdk", roleName, serverAddress, serverPort, Integer.toString(userId),
            userTokenHash, entityId, sdkUid, sessionId, deviceId, gameId, launcherVersion,
            createdAt, expiresAt, signature);
    }

    public boolean isExpired(Instant now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }
}
