package shit.zen.protocol.heypixel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ProtocolTraceLogger {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Logger LOGGER = LogManager.getLogger("Mizulune.Protocol");
    private final Path directory;
    private volatile boolean enabled;

    public ProtocolTraceLogger(Path directory) {
        this.directory = directory;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public synchronized void log(String event, String channel, Integer packetId, Map<String, ?> details) {
        if (!enabled) return;
        Map<String, ?> safeDetails = redact(details == null ? Map.of() : details);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("timestamp", Instant.now().toString());
        value.put("event", event);
        value.put("channel", channel);
        value.put("packetId", packetId);
        value.put("details", safeDetails);
        String standardLogLine = formatStandardLogLine(event, channel, packetId, safeDetails);
        if ("packet-response".equals(event)) {
            LOGGER.info(standardLogLine);
        } else {
            LOGGER.debug(standardLogLine);
        }
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve("protocol-" + java.time.LocalDate.now() + ".jsonl");
            Files.writeString(file, GSON.toJson(value) + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException error) {
            LOGGER.warn("[Protocol] trace-write-failed event={} error={}",
                event, error.getClass().getSimpleName());
        }
    }

    public void logPacketResponse(
        String channel,
        int requestPacketId,
        int responsePacketId,
        String trigger,
        String outcome
    ) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("requestDirection", "S2C");
        details.put("requestPacketId", requestPacketId);
        details.put("requestSemantic", HeyPixelPacketSemantics.canonicalName(
            HeyPixelPacketSemantics.Direction.S2C, requestPacketId));
        details.put("responseDirection", "C2S");
        details.put("responsePacketId", responsePacketId);
        details.put("responseSemantic", HeyPixelPacketSemantics.canonicalName(
            HeyPixelPacketSemantics.Direction.C2S, responsePacketId));
        details.put("trigger", trigger);
        details.put("outcome", outcome);
        log("packet-response", channel, responsePacketId, details);
    }

    static String standardLogLine(
        String event,
        String channel,
        Integer packetId,
        Map<String, ?> details
    ) {
        return formatStandardLogLine(
            event,
            channel,
            packetId,
            redact(details == null ? Map.of() : details)
        );
    }

    private static String formatStandardLogLine(
        String event,
        String channel,
        Integer packetId,
        Map<String, ?> safeDetails
    ) {
        return "[Protocol] event=" + String.valueOf(event)
            + " channel=" + (channel == null ? "-" : channel)
            + " packetId=" + (packetId == null ? "-" : packetId)
            + " details=" + safeDetails;
    }

    static Map<String, ?> redact(Map<String, ?> details) {
        Map<String, Object> safe = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : details.entrySet()) {
            String key = entry.getKey();
            if (isSensitiveKey(key)) {
                safe.put(key, "<redacted>");
            } else {
                safe.put(key, redactValue(entry.getValue()));
            }
        }
        return safe;
    }

    private static boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (normalized.endsWith("sha256") || normalized.endsWith("length")
            || normalized.endsWith("count")) {
            return false;
        }
        return normalized.contains("token")
            || normalized.contains("secret")
            || normalized.contains("password")
            || normalized.contains("credential")
            || normalized.contains("uuid")
            || normalized.contains("userid")
            || normalized.contains("entityid")
            || normalized.contains("rolename")
            || normalized.contains("sessionid")
            || normalized.contains("deviceid")
            || normalized.contains("hwidprofile")
            || normalized.contains("hwidid")
            || normalized.contains("path")
            || normalized.contains("directory")
            || normalized.contains("javahome")
            || normalized.contains("endpoint")
            || normalized.contains("address")
            || normalized.equals("host")
            || normalized.equals("targethost")
            || normalized.contains("prefixhex")
            || normalized.equals("message")
            || normalized.equals("rootmessage")
            || normalized.equals("wrappererror")
            || normalized.equals("lengthprefixerror");
    }

    private static Object redactValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                nested.put(key, isSensitiveKey(key) ? "<redacted>" : redactValue(entry.getValue()));
            }
            return nested;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> nested = new ArrayList<>();
            for (Object item : iterable) nested.add(redactValue(item));
            return nested;
        }
        if (value != null && value.getClass().isArray()) {
            List<Object> nested = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) nested.add(redactValue(Array.get(value, i)));
            return nested;
        }
        return value;
    }
}
