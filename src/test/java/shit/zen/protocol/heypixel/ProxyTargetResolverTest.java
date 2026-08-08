package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

final class ProxyTargetResolverTest {
    @TempDir
    Path temp;

    @Test
    void resolvesOnlySignedFantnelLoopbackTargets() throws Exception {
        Instant now = Instant.parse("2026-07-02T10:00:00Z");
        ProtocolSessionProvider provider = provider(now);
        writeSnapshot(now, "fantnel", "pc.bjdmc.net", true);
        ProxyTargetResolver resolver = new ProxyTargetResolver(provider);

        ProxyTargetResolver.ResolvedTarget target = resolver
            .resolve("127.0.0.1:25565", "pc.bjdmc.net,*.bjdmc.net")
            .orElseThrow();
        assertTrue(target.proxied());
        assertEquals("127.0.0.1:25565", target.connectionEndpoint());
        assertEquals("127.0.0.1", target.connectionHost());
        assertEquals(25565, target.connectionPort());
        assertEquals("pc.bjdmc.net", target.targetHost());
        assertEquals(25565, target.targetPort());
        assertTrue(target.session().isPresent());

        assertTrue(resolver.resolve("127.20.30.40:25565", "*.bjdmc.net").isPresent());
        assertTrue(resolver.resolve("127.0.0.1:25565", "127.0.0.1").isPresent());
        assertFalse(resolver.resolve("127.0.0.1", "example.net").isPresent());
    }

    @Test
    void rejectsWrongSourceTamperingAndArbitraryLocalhost() throws Exception {
        Instant now = Instant.parse("2026-07-02T10:00:00Z");
        ProtocolSessionProvider provider = provider(now);
        ProxyTargetResolver resolver = new ProxyTargetResolver(provider);

        writeSnapshot(now, "opensdk", "pc.bjdmc.net", true);
        assertFalse(resolver.resolve("localhost", "*.bjdmc.net").isPresent());

        writeSnapshot(now, "fantnel", "pc.bjdmc.net", false);
        assertFalse(resolver.resolve("127.0.0.1", "*.bjdmc.net").isPresent());

        writeSnapshot(now, "fantnel", "example.net", true);
        assertFalse(resolver.resolve("127.0.0.1", "127.0.0.1").isPresent());

        assertFalse(resolver.resolve("192.168.1.10", "*.bjdmc.net").isPresent());
    }

    @Test
    void keepsDirectTargetActiveWithoutWeakeningProviderGate() {
        Instant now = Instant.parse("2026-07-02T10:00:00Z");
        ProxyTargetResolver.ResolvedTarget target = new ProxyTargetResolver(provider(now))
            .resolve("pc.bjdmc.net:25565", "*.bjdmc.net")
            .orElseThrow();
        assertFalse(target.proxied());
        assertEquals("pc.bjdmc.net:25565", target.connectionEndpoint());
        assertEquals(25565, target.connectionPort());
        assertEquals(25565, target.targetPort());
        assertTrue(target.session().isEmpty());
    }

    @Test
    void preservesFantnelDynamicPortWithoutTreatingTargetAsConnectionEndpoint() throws Exception {
        Instant now = Instant.parse("2026-07-02T10:00:00Z");
        ProtocolSessionProvider provider = provider(now);
        writeSnapshot(now, "fantnel", "pc.bjdmc.net", true);

        ProxyTargetResolver.ResolvedTarget target = new ProxyTargetResolver(provider)
            .resolve("127.0.0.1:25573", "*.bjdmc.net")
            .orElseThrow();

        assertEquals("127.0.0.1:25573", target.connectionEndpoint());
        assertEquals(25573, target.connectionPort());
        assertEquals("pc.bjdmc.net", target.targetHost());
        assertEquals(25565, target.targetPort());
    }

    private ProtocolSessionProvider provider(Instant now) {
        return new ProtocolSessionProvider(
            temp.resolve(ProtocolSessionProvider.SNAPSHOT_NAME),
            temp.resolve(ProtocolSessionProvider.KEY_NAME),
            Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    private void writeSnapshot(Instant now, String source, String host, boolean validSignature) throws Exception {
        byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        ProtocolSessionSnapshot unsigned = new ProtocolSessionSnapshot(
            2, source, "Player", host, 25565, "987654321012345678", "token-hash",
            "", "", "", "", "game-id", "fantnel/1.7.0",
            now.minusSeconds(1), now.plusSeconds(300), ""
        );
        String signature = validSignature
            ? Base64.getEncoder().encodeToString(hmac(key, ProtocolSessionProvider.canonical(unsigned)))
            : Base64.getEncoder().encodeToString(new byte[32]);
        ProtocolSessionSnapshot signed = new ProtocolSessionSnapshot(
            unsigned.version(), unsigned.source(), unsigned.roleName(), unsigned.serverAddress(), unsigned.serverPort(),
            unsigned.userId(), unsigned.userTokenHash(), unsigned.entityId(), unsigned.sdkUid(), unsigned.sessionId(),
            unsigned.deviceId(), unsigned.gameId(), unsigned.launcherVersion(), unsigned.createdAt(), unsigned.expiresAt(), signature
        );

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("version", signed.version());
        json.put("source", signed.source());
        json.put("roleName", signed.roleName());
        json.put("serverAddress", signed.serverAddress());
        json.put("serverPort", signed.serverPort());
        json.put("userId", signed.userId());
        json.put("userTokenHash", signed.userTokenHash());
        json.put("entityId", signed.entityId());
        json.put("sdkUid", signed.sdkUid());
        json.put("sessionId", signed.sessionId());
        json.put("deviceId", signed.deviceId());
        json.put("gameId", signed.gameId());
        json.put("launcherVersion", signed.launcherVersion());
        json.put("createdAt", signed.createdAt().toString());
        json.put("expiresAt", signed.expiresAt().toString());
        json.put("signature", signed.signature());
        Files.writeString(temp.resolve(ProtocolSessionProvider.SNAPSHOT_NAME),
            new Gson().toJson(json), StandardCharsets.UTF_8);
        Files.writeString(temp.resolve(ProtocolSessionProvider.KEY_NAME),
            Base64.getEncoder().encodeToString(key), StandardCharsets.US_ASCII);
    }

    private static byte[] hmac(byte[] key, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }
}
