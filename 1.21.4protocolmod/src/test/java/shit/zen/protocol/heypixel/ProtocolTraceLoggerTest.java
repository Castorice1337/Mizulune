package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtocolTraceLoggerTest {
    @Test
    void redactsIdentifiersPathsEndpointsAndRawPrefixesRecursively() {
        Map<String, ?> redacted = ProtocolTraceLogger.redact(Map.of(
            "localUuid", "11111111-2222-3333-4444-555555555555",
            "gameDirectory", "D:\\private\\game",
            "connectionEndpoint", "127.0.0.1:25565",
            "preCryptoPrefixHex", "deadbeef",
            "nested", Map.of("sessionId", "session-secret", "length", 12),
            "list", List.of(Map.of("roleName", "player-name"))
        ));

        assertEquals("<redacted>", redacted.get("localUuid"));
        assertEquals("<redacted>", redacted.get("gameDirectory"));
        assertEquals("<redacted>", redacted.get("connectionEndpoint"));
        assertEquals("<redacted>", redacted.get("preCryptoPrefixHex"));
        assertEquals(12, ((Map<?, ?>) redacted.get("nested")).get("length"));
        assertEquals("<redacted>", ((Map<?, ?>) redacted.get("nested")).get("sessionId"));
        Map<?, ?> listItem = (Map<?, ?>) ((List<?>) redacted.get("list")).get(0);
        assertEquals("<redacted>", listItem.get("roleName"));
    }

    @Test
    void preservesNonSensitiveDiagnosticsAndDigests() {
        Map<String, ?> redacted = ProtocolTraceLogger.redact(Map.of(
            "packetId", 114,
            "wireLength", 1200,
            "wireSha256", "012345",
            "rootError", "IllegalArgumentException",
            "environmentSource", "official-install-root",
            "syntheticHwid", false
        ));

        assertEquals(114, redacted.get("packetId"));
        assertEquals(1200, redacted.get("wireLength"));
        assertEquals("012345", redacted.get("wireSha256"));
        assertEquals("IllegalArgumentException", redacted.get("rootError"));
        assertEquals("official-install-root", redacted.get("environmentSource"));
        assertFalse((Boolean) redacted.get("syntheticHwid"));
        assertTrue(redacted.values().stream().noneMatch("<redacted>"::equals));
    }

    @Test
    void doesNotLetSensitivePrefixesHideBehindASourceSuffix() {
        Map<String, ?> redacted = ProtocolTraceLogger.redact(Map.of(
            "tokenSource", "raw-token-origin",
            "pathSource", "D:\\private",
            "uuidSource", "11111111-2222-3333-4444-555555555555",
            "environmentSource", "official-install-root"
        ));

        assertEquals("<redacted>", redacted.get("tokenSource"));
        assertEquals("<redacted>", redacted.get("pathSource"));
        assertEquals("<redacted>", redacted.get("uuidSource"));
        assertEquals("official-install-root", redacted.get("environmentSource"));
    }

    @Test
    void writesCanonicalPacketResponseRecord(@TempDir Path directory) throws Exception {
        ProtocolTraceLogger logger = new ProtocolTraceLogger(directory);
        logger.setEnabled(true);

        logger.logPacketResponse(
            "heypixel:s2cevent",
            101,
            1,
            "S2C_ID101",
            "final-write"
        );

        Path traceFile;
        try (var files = Files.list(directory)) {
            traceFile = files.findFirst().orElseThrow();
        }
        List<String> lines = Files.readAllLines(traceFile);
        assertEquals(1, lines.size());
        JsonObject record = JsonParser.parseString(lines.get(0)).getAsJsonObject();
        assertEquals("packet-response", record.get("event").getAsString());
        assertEquals(1, record.get("packetId").getAsInt());
        JsonObject details = record.getAsJsonObject("details");
        assertEquals("S2C", details.get("requestDirection").getAsString());
        assertEquals(101, details.get("requestPacketId").getAsInt());
        assertEquals(HeyPixelPacketSemantics.canonicalName(
            HeyPixelPacketSemantics.Direction.S2C, 101),
            details.get("requestSemantic").getAsString());
        assertEquals("C2S", details.get("responseDirection").getAsString());
        assertEquals(1, details.get("responsePacketId").getAsInt());
        assertEquals(HeyPixelPacketSemantics.canonicalName(
            HeyPixelPacketSemantics.Direction.C2S, 1),
            details.get("responseSemantic").getAsString());
        assertEquals("S2C_ID101", details.get("trigger").getAsString());
        assertEquals("final-write", details.get("outcome").getAsString());
    }

    @Test
    void standardLogLineContainsOnlyRedactedDetails() {
        String line = ProtocolTraceLogger.standardLogLine(
            "packet-response",
            "heypixel:s2cevent",
            1,
            Map.of("token", "raw-secret", "responseLength", 48)
        );

        assertTrue(line.startsWith("[Protocol] event=packet-response"));
        assertTrue(line.contains("token=<redacted>"));
        assertTrue(line.contains("responseLength=48"));
        assertFalse(line.contains("raw-secret"));
    }
}
