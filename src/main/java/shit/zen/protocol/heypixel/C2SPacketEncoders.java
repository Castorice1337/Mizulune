package shit.zen.protocol.heypixel;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class C2SPacketEncoders {
    private static final Gson GSON = new Gson();

    private C2SPacketEncoders() {
    }

    /** ID0 has an empty official payload; its trigger and business meaning remain unknown. */
    public static byte[] encodeId0() {
        return frameRawPayload(0, new byte[0]);
    }

    public static byte[] encodeId2(long writerTime, long cachedClockMillis) {
        return frameMessagePack(2,
            writer -> writer.packLong(writerTime).packLong(cachedClockMillis));
    }

    public static byte[] encodeHeartbeat(long writerTime, long cachedClockMillis) {
        return encodeId2(writerTime, cachedClockMillis);
    }

    public static byte[] encodeId3(long writerTime, int stateA, int stateB) {
        return frameMessagePack(3, writer -> writer.packLong(writerTime).packInt(stateA).packInt(stateB));
    }

    public static byte[] encodeCpsTelemetry(long writerTime, int leftCps, int rightCps) {
        return encodeId3(writerTime, leftCps, rightCps);
    }

    public static byte[] encodeId4(long writerTime, List<String> values) {
        List<String> stableValues = List.copyOf(values);
        return frameMessagePack(4, writer -> writer.packLong(writerTime)
            .packArrayHeader(stableValues.size())
            .packValue(stableValues));
    }

    public static byte[] encodeVshSourceReport(long writerTime, List<String> values) {
        return encodeId4(writerTime, values);
    }

    public static byte[] encodeId5(Id5UseBlock packet) {
        return frameMessagePack(5, writer -> {
            writer.packLong(packet.writerTime());
            writer.packDouble(packet.playerX());
            writer.packDouble(packet.playerY());
            writer.packDouble(packet.playerZ());
            writer.packInt(packet.directionOrdinal());
            writer.packInt(packet.hitTypeOrdinal());
            writer.packDouble(packet.hitX());
            writer.packDouble(packet.hitY());
            writer.packDouble(packet.hitZ());
            writer.packDouble(packet.blockX());
            writer.packDouble(packet.blockY());
            writer.packDouble(packet.blockZ());
            writer.packBoolean(packet.inside());
            writer.packFloat(packet.yaw());
            writer.packFloat(packet.pitch());
            writer.packBoolean(packet.mainHand());
        });
    }

    public static byte[] encodeUseBlockTelemetry(Id5UseBlock packet) {
        return encodeId5(packet);
    }

    public static byte[] encodeId6(Id6AttackEntity packet) {
        return frameMessagePack(6, writer -> {
            writer.packLong(packet.writerTime());
            writer.packValue(packet.targetUuid());
            writer.packInt(packet.hitTypeOrdinal());
            writer.packDouble(packet.hitX());
            writer.packDouble(packet.hitY());
            writer.packDouble(packet.hitZ());
            writer.packInt(packet.playerPoseOrdinal());
            writer.packDouble(packet.playerX());
            writer.packDouble(packet.playerY());
            writer.packDouble(packet.playerZ());
            writer.packFloat(packet.playerYaw());
            writer.packFloat(packet.playerPitch());
            writer.packInt(packet.targetPoseOrdinal());
            writer.packDouble(packet.targetX());
            writer.packDouble(packet.targetY());
            writer.packDouble(packet.targetZ());
            writer.packFloat(packet.targetYaw());
            writer.packFloat(packet.targetPitch());
        });
    }

    public static byte[] encodeAttackEntityTelemetry(Id6AttackEntity packet) {
        return encodeId6(packet);
    }

    public static byte[] encodeId7(long writerTime, long field00, String field01) {
        return encodePanelAction(writerTime, field00, field01);
    }

    /**
     * Encodes the official panel action envelope. Current official producers use
     * {@code banner} for the panel button and {@code close} when the screen closes.
     */
    public static byte[] encodePanelAction(long writerTime, long panelId, String action) {
        return encodePanelAction(writerTime, new C2SPanelActionPacket(panelId, action));
    }

    public static byte[] encodePanelAction(long writerTime, C2SPanelActionPacket packet) {
        C2SPanelActionPacket value = Objects.requireNonNull(packet, "packet");
        return frameMessagePack(7, writer -> writer.packLong(writerTime)
            .packLong(value.panelId())
            .packString(value.action()));
    }

    /** @deprecated Use {@link #encodePanelAction(long, long, String)}. */
    @Deprecated
    public static byte[] encodePanelState(long writerTime, long field00, String field01) {
        return encodePanelAction(writerTime, field00, field01);
    }

    public static byte[] encodeId8Json(String json) {
        return frameRawPayload(8, utf8(json));
    }

    public static byte[] encodeId8(Map<String, ?> payload) {
        return frameJsonMap(8, payload);
    }

    public static byte[] encodeId8(C2SShopRequestPacket packet) {
        return encodeId8(Objects.requireNonNull(packet, "packet").map());
    }

    public static byte[] encodePanelJsonReport(String json) {
        return encodeId8Json(json);
    }

    /** ID8 is an official Shop manager/Shop screen request; this API never installs a send trigger. */
    public static byte[] encodeShopRequest(Map<String, ?> payload) {
        return encodeId8(payload);
    }

    public static byte[] encodeShopRequest(C2SShopRequestPacket packet) {
        return encodeId8(packet);
    }

    public static byte[] encodeId9Json(String json) {
        return frameRawPayload(9, utf8(json));
    }

    public static byte[] encodeId9(C2SHexSelectionClickPacket packet) {
        return frameJsonMap(9, Objects.requireNonNull(packet, "packet").map());
    }

    /** @deprecated ID9 is the official three-slot Hex Selection click packet. */
    @Deprecated
    public static byte[] encodeGuiClickAction(String json) {
        return encodeId9Json(json);
    }

    public static byte[] encodeHexSelectionClick(C2SHexSelectionClickPacket packet) {
        return encodeId9(packet);
    }

    public static byte[] encodeId10Json(String json) {
        return frameRawPayload(10, utf8(json));
    }

    public static byte[] encodeId10(Map<String, ?> payload) {
        return frameJsonMap(10, payload);
    }

    public static byte[] encodeFashionInfo(C2SFashionInfoPacket packet) {
        return encodeId10(Objects.requireNonNull(packet, "packet").map());
    }

    /** @deprecated ID10 is the official FashionInfo packet, not an exchange-only request. */
    @Deprecated
    public static byte[] encodeExchangeRequest(String json) {
        return encodeId10Json(json);
    }

    public static byte[] encodeId11(Map<String, ?> payload) {
        return frameJsonMap(11, payload);
    }

    public static byte[] encodeBoardAction(C2SBoardActionPacket packet) {
        return encodeId11(Objects.requireNonNull(packet, "packet").map());
    }

    public static byte[] encodeId108(long writerTime, String field00, String field01) {
        return frameMessagePack(108, writer -> {
            writer.packLong(writerTime).packString(field00);
            if (field01 != null) writer.packString(field01);
        });
    }

    public static byte[] encodeResourceBlob(long writerTime, String resourceName, String hash) {
        return encodeId108(writerTime, resourceName, hash);
    }

    public static byte[] encodeId109(long writerTime, String field00, String field01, Integer field02) {
        return frameMessagePack(109, writer -> {
            writer.packLong(writerTime).packString(field00).packString(field01);
            if (field02 != null) writer.packInt(field02);
        });
    }

    public static byte[] encodeResourceIndex(
        long writerTime,
        String indexName,
        String cacheHash,
        Integer mode
    ) {
        return encodeId109(writerTime, indexName, cacheHash, mode);
    }

    /** @deprecated Use {@link #encodeResourceIndex(long, String, String, Integer)}. */
    @Deprecated
    public static byte[] encodeChunkedData(long writerTime, String streamId, String metadata,
                                            Integer chunkHint) {
        return encodeResourceIndex(writerTime, streamId, metadata, chunkHint);
    }

    public static byte[] frameMessagePack(int packetId, Consumer<HeyPixelMsgpackWriter> payloadWriter) {
        HeyPixelMsgpackWriter writer = new HeyPixelMsgpackWriter();
        payloadWriter.accept(writer);
        return frameRawPayload(packetId, writer.toByteArray());
    }

    public static byte[] frameRawPayload(int packetId, byte[] payload) {
        if (packetId < 0) throw new IllegalArgumentException("packet id must be non-negative");
        Objects.requireNonNull(payload, "payload");
        ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length + 8);
        UuidSelectedPayloadFramer.writeVarInt(out, packetId);
        UuidSelectedPayloadFramer.writeVarInt(
            out,
            Math.addExact(payload.length, varIntSize(packetId))
        );
        out.writeBytes(payload);
        return out.toByteArray();
    }

    private static int varIntSize(int value) {
        int bytes = 1;
        while ((value & ~0x7f) != 0) {
            value >>>= 7;
            bytes++;
        }
        return bytes;
    }

    private static byte[] frameJsonMap(int packetId, Map<String, ?> payload) {
        return frameRawPayload(packetId, utf8(GSON.toJson(immutableJsonMap(payload))));
    }

    private static Map<String, Object> immutableJsonMap(Map<String, ?> payload) {
        Objects.requireNonNull(payload, "payload");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        payload.forEach((key, value) -> result.put(
            Objects.requireNonNull(key, "payload key"),
            immutableJsonValue(value)
        ));
        return Collections.unmodifiableMap(result);
    }

    private static Object immutableJsonValue(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean
            || value instanceof Character || value instanceof Enum<?>) {
            return value;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer
            || value instanceof Long || value instanceof Float || value instanceof Double
            || value instanceof BigInteger || value instanceof BigDecimal) {
            return value;
        }
        if (value instanceof Number number) {
            return com.google.gson.JsonParser.parseString(GSON.toJson(number));
        }
        if (value instanceof JsonElement element) return element.deepCopy();
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, nested) -> result.put(
                Objects.requireNonNull((String) key, "nested payload key"),
                immutableJsonValue(nested)
            ));
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof Collection<?> collection) {
            ArrayList<Object> result = new ArrayList<>(collection.size());
            collection.forEach(nested -> result.add(immutableJsonValue(nested)));
            return Collections.unmodifiableList(result);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            ArrayList<Object> result = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                result.add(immutableJsonValue(Array.get(value, index)));
            }
            return Collections.unmodifiableList(result);
        }
        return GSON.toJsonTree(value).deepCopy();
    }

    private static byte[] utf8(String value) {
        return Objects.requireNonNull(value, "json").getBytes(StandardCharsets.UTF_8);
    }

    public record Id5UseBlock(
        long writerTime,
        double playerX,
        double playerY,
        double playerZ,
        int directionOrdinal,
        int hitTypeOrdinal,
        double hitX,
        double hitY,
        double hitZ,
        double blockX,
        double blockY,
        double blockZ,
        boolean inside,
        float yaw,
        float pitch,
        boolean mainHand
    ) {
    }

    public record Id6AttackEntity(
        long writerTime,
        String targetUuid,
        int hitTypeOrdinal,
        double hitX,
        double hitY,
        double hitZ,
        int playerPoseOrdinal,
        double playerX,
        double playerY,
        double playerZ,
        float playerYaw,
        float playerPitch,
        int targetPoseOrdinal,
        double targetX,
        double targetY,
        double targetZ,
        float targetYaw,
        float targetPitch
    ) {
        public Id6AttackEntity {
            Objects.requireNonNull(targetUuid, "targetUuid");
        }
    }

    /** Official C2S ID7 panel action payload. No sending trigger is installed here. */
    public record C2SPanelActionPacket(long panelId, String action) {
        public static final String ACTION_BANNER = "banner";
        public static final String ACTION_CLOSE = "close";

        public C2SPanelActionPacket {
            action = Objects.requireNonNull(action, "action");
        }

        public static C2SPanelActionPacket banner(long panelId) {
            return new C2SPanelActionPacket(panelId, ACTION_BANNER);
        }

        public static C2SPanelActionPacket close(long panelId) {
            return new C2SPanelActionPacket(panelId, ACTION_CLOSE);
        }
    }

    /** Official C2S ID8 Shop request map recovered from the current client. */
    public record C2SShopRequestPacket(Map<String, Object> map) {
        public C2SShopRequestPacket {
            map = immutableJsonMap(map);
        }

        @Override
        public Map<String, Object> map() {
            return immutableJsonMap(map);
        }

        public static C2SShopRequestPacket buy(String item) {
            Objects.requireNonNull(item, "item");
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            result.put("type", "buy");
            result.put("item", item);
            return new C2SShopRequestPacket(result);
        }

        public static C2SShopRequestPacket infos(String keys) {
            Objects.requireNonNull(keys, "keys");
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            result.put("type", "infos");
            result.put("keys", keys);
            return new C2SShopRequestPacket(result);
        }
    }

    /** Official C2S ID9 three-slot Hex Selection click payload. */
    public record C2SHexSelectionClickPacket(
        long sessionId,
        String actionType,
        int slotIndex,
        String key
    ) {
        public Map<String, Object> map() {
            Objects.requireNonNull(actionType, "actionType");
            Objects.requireNonNull(key, "key");
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            result.put("sessionId", sessionId);
            result.put("actionType", actionType);
            result.put("slotIndex", slotIndex);
            result.put("key", key);
            return Collections.unmodifiableMap(result);
        }

        public static C2SHexSelectionClickPacket select(
            long sessionId,
            int slotIndex,
            String key
        ) {
            return new C2SHexSelectionClickPacket(sessionId, "select", slotIndex, key);
        }

        public static C2SHexSelectionClickPacket reroll(
            long sessionId,
            int slotIndex,
            String key
        ) {
            return new C2SHexSelectionClickPacket(sessionId, "reroll", slotIndex, key);
        }
    }

    /** Official C2S ID10 payload and factory semantics recovered from the current client. */
    public record C2SFashionInfoPacket(Map<String, Object> map) {
        public C2SFashionInfoPacket {
            map = immutableJsonMap(map);
        }

        @Override
        public Map<String, Object> map() {
            return immutableJsonMap(map);
        }

        public static C2SFashionInfoPacket infos() {
            return new C2SFashionInfoPacket(Map.of("type", "infos"));
        }

        public static C2SFashionInfoPacket apply(Map<String, ?> equipped) {
            Map<String, Object> stableEquipped = immutableJsonMap(equipped);
            List<String> fashions = stableEquipped.values().stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .distinct()
                .toList();
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "apply");
            payload.put("equipped", stableEquipped);
            payload.put("fashions", fashions);
            return new C2SFashionInfoPacket(payload);
        }

        public static C2SFashionInfoPacket apply(Collection<String> fashions) {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "apply");
            payload.put("fashions", List.copyOf(fashions));
            return new C2SFashionInfoPacket(payload);
        }

        public static C2SFashionInfoPacket exchange(String fashion, String group, String exchange) {
            return exchange(fashion, group, exchange, 0);
        }

        public static C2SFashionInfoPacket exchange(
            String fashion,
            String group,
            String exchange,
            int day
        ) {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "exchange");
            payload.put("fashion", fashion);
            payload.put("group", group);
            payload.put("exchange", exchange);
            payload.put("day", day);
            return new C2SFashionInfoPacket(payload);
        }

        public static C2SFashionInfoPacket obtain(
            String fashion,
            String group,
            String obtainCommandKey
        ) {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "obtain");
            payload.put("fashion", fashion);
            payload.put("group", group);
            payload.put("obtainCommandKey", obtainCommandKey);
            return new C2SFashionInfoPacket(payload);
        }

        public static C2SFashionInfoPacket equip(String fashion, String group) {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "equip");
            payload.put("fashion", fashion);
            payload.put("group", group);
            return new C2SFashionInfoPacket(payload);
        }

        public static C2SFashionInfoPacket unequipCategory(String category) {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "unequip_category");
            payload.put("category", category);
            return new C2SFashionInfoPacket(payload);
        }

        public static C2SFashionInfoPacket unequipAll() {
            return new C2SFashionInfoPacket(Map.of("type", "unequip_all"));
        }
    }

    /** Official C2S ID11 Board Action envelope. No sending trigger is installed here. */
    public record C2SBoardActionPacket(Map<String, Object> map) {
        public C2SBoardActionPacket {
            map = immutableJsonMap(map);
        }

        @Override
        public Map<String, Object> map() {
            return immutableJsonMap(map);
        }

        public static C2SBoardActionPacket createBoardAction(
            String boardId,
            int revision,
            String tabId,
            String action,
            String actionId,
            Map<String, ?> payload
        ) {
            return createBoardAction(
                UUID.randomUUID().toString(),
                boardId,
                revision,
                tabId,
                action,
                actionId,
                System.currentTimeMillis(),
                payload
            );
        }

        /** Deterministic form for capture replay comparisons and golden tests. */
        public static C2SBoardActionPacket createBoardAction(
            String requestId,
            String boardId,
            int revision,
            String tabId,
            String action,
            String actionId,
            long clientTime,
            Map<String, ?> payload
        ) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            result.put("protocol", 1);
            result.put("type", "board_action");
            result.put("requestId", requestId);
            result.put("boardId", boardId);
            result.put("revision", revision);
            result.put("tabId", tabId);
            result.put("action", action);
            result.put("actionId", actionId);
            result.put("clientTime", clientTime);
            result.put("payload", payload == null ? Map.of() : immutableJsonMap(payload));
            return new C2SBoardActionPacket(result);
        }

        public static C2SBoardActionPacket requestBoard(String boardId) {
            return createBoardAction(
                boardId,
                0,
                "",
                "request_board",
                "request_board",
                Map.of()
            );
        }

        /** Deterministic form of {@link #requestBoard(String)}. */
        public static C2SBoardActionPacket requestBoard(
            String requestId,
            String boardId,
            long clientTime
        ) {
            return createBoardAction(
                requestId,
                boardId,
                0,
                "",
                "request_board",
                "request_board",
                clientTime,
                Map.of()
            );
        }
    }
}
