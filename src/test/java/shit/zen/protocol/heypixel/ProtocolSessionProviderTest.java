package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtocolSessionProviderTest {
    @TempDir
    Path temp;

    @Test
    void acceptsValidSignedSnapshot() throws Exception {
        Instant now = Instant.parse("2026-06-27T10:00:00Z");
        ProtocolSessionSnapshot snapshot = snapshot(now, now.plusSeconds(300), "pc.bjdmc.net", "");
        byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        String signature = Base64.getEncoder().encodeToString(hmac(key, ProtocolSessionProvider.canonical(snapshot)));
        assertEquals("6fiZ1gLZHDf3qT79GoKxBNB04nOKgkNJtAjY9rABVXs=", signature);
        write(snapshot(now, now.plusSeconds(300), "pc.bjdmc.net", signature), key);

        ProtocolSessionProvider provider = new ProtocolSessionProvider(
            temp.resolve(ProtocolSessionProvider.SNAPSHOT_NAME),
            temp.resolve(ProtocolSessionProvider.KEY_NAME),
            Clock.fixed(now, ZoneOffset.UTC)
        );
        assertEquals("Player", provider.loadRequired("pc.bjdmc.net").roleName());
    }

    @Test
    void rejectsExpiredSnapshot() throws Exception {
        Instant now = Instant.parse("2026-06-27T10:00:00Z");
        byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        ProtocolSessionSnapshot unsigned = snapshot(now.minusSeconds(600), now.minusSeconds(1), "pc.bjdmc.net", "");
        String signature = Base64.getEncoder().encodeToString(hmac(key, ProtocolSessionProvider.canonical(unsigned)));
        write(snapshot(unsigned.createdAt(), unsigned.expiresAt(), unsigned.serverAddress(), signature), key);
        ProtocolSessionProvider provider = new ProtocolSessionProvider(
            temp.resolve(ProtocolSessionProvider.SNAPSHOT_NAME),
            temp.resolve(ProtocolSessionProvider.KEY_NAME),
            Clock.fixed(now, ZoneOffset.UTC)
        );
        assertThrows(IllegalArgumentException.class, () -> provider.loadRequired("pc.bjdmc.net"));
    }

    @Test
    void rejectsWrongHostSnapshot() throws Exception {
        Instant now = Instant.parse("2026-06-27T10:00:00Z");
        byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        ProtocolSessionSnapshot unsigned = snapshot(now, now.plusSeconds(300), "pc.bjdmc.net", "");
        String signature = Base64.getEncoder().encodeToString(hmac(key, ProtocolSessionProvider.canonical(unsigned)));
        write(snapshot(unsigned.createdAt(), unsigned.expiresAt(), unsigned.serverAddress(), signature), key);
        ProtocolSessionProvider provider = new ProtocolSessionProvider(
            temp.resolve(ProtocolSessionProvider.SNAPSHOT_NAME),
            temp.resolve(ProtocolSessionProvider.KEY_NAME),
            Clock.fixed(now, ZoneOffset.UTC)
        );
        assertThrows(IllegalArgumentException.class, () -> provider.loadRequired("example.net"));
    }

    @Test
    void rejectsTamperedSignatureAndMissingFields() throws Exception {
        Instant now = Instant.parse("2026-06-27T10:00:00Z");
        byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        write(snapshot(now, now.plusSeconds(300), "pc.bjdmc.net", Base64.getEncoder().encodeToString(new byte[32])), key);
        ProtocolSessionProvider provider = new ProtocolSessionProvider(
            temp.resolve(ProtocolSessionProvider.SNAPSHOT_NAME),
            temp.resolve(ProtocolSessionProvider.KEY_NAME),
            Clock.fixed(now, ZoneOffset.UTC)
        );
        assertThrows(IllegalArgumentException.class, () -> provider.loadRequired("pc.bjdmc.net"));

        Files.writeString(temp.resolve(ProtocolSessionProvider.SNAPSHOT_NAME), "{}", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> provider.loadRequired("pc.bjdmc.net"));
    }

    private void write(ProtocolSessionSnapshot snapshot, byte[] key) throws Exception {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("roleName", snapshot.roleName());
        json.put("serverAddress", snapshot.serverAddress());
        json.put("serverPort", snapshot.serverPort());
        json.put("userId", snapshot.userId());
        json.put("userTokenHash", snapshot.userTokenHash());
        json.put("entityId", snapshot.entityId());
        json.put("sdkUid", snapshot.sdkUid());
        json.put("sessionId", snapshot.sessionId());
        json.put("deviceId", snapshot.deviceId());
        json.put("gameId", snapshot.gameId());
        json.put("launcherVersion", snapshot.launcherVersion());
        json.put("createdAt", snapshot.createdAt().toString());
        json.put("expiresAt", snapshot.expiresAt().toString());
        json.put("signature", snapshot.signature());
        Files.writeString(temp.resolve(ProtocolSessionProvider.SNAPSHOT_NAME), new Gson().toJson(json), StandardCharsets.UTF_8);
        Files.writeString(temp.resolve(ProtocolSessionProvider.KEY_NAME), Base64.getEncoder().encodeToString(key), StandardCharsets.US_ASCII);
    }

    private static ProtocolSessionSnapshot snapshot(Instant created, Instant expires, String host, String signature) {
        return new ProtocolSessionSnapshot(
            "Player", host, 25565, 42, "token-hash", "", "", "", "", "x19", "1.0",
            created, expires, signature
        );
    }

    private static byte[] hmac(byte[] key, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }
}
