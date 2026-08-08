package shit.zen.protocol.heypixel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.zip.InflaterInputStream;

public final class S2CPacketDecoders {
    public static final int BINARY_BRIDGE_DISCRIMINATOR = 250;
    public static final int JSON_EVENT_DISCRIMINATOR = 233;
    static final int MAX_JSON_BYTES = 8 * 1024 * 1024;
    private static final int MAX_PANEL_BATCH_ENTRIES = 16_384;
    private static final int MIN_HUD_INFO_ENTRY_BYTES = 7;

    private S2CPacketDecoders() {
    }

    public static WrappedPacket decodeWrapper(byte[] wire) {
        if (wire.length == 0) throw new IllegalArgumentException("S2C wrapper is empty");
        int offset = (wire[0] & 0xff) == BINARY_BRIDGE_DISCRIMINATOR ? 1 : 0;
        if ((wire[0] & 0xff) == JSON_EVENT_DISCRIMINATOR) {
            throw new IllegalArgumentException("JSON event bridge is not a binary business packet");
        }
        if (wire.length - offset < 4) {
            throw new IllegalArgumentException("S2C wrapper is shorter than int32 id");
        }
        int id = (wire[offset] & 0xff) << 24 | (wire[offset + 1] & 0xff) << 16
            | (wire[offset + 2] & 0xff) << 8 | wire[offset + 3] & 0xff;
        return new WrappedPacket(id, Arrays.copyOfRange(wire, offset + 4, wire.length));
    }

    public static Id101Challenge decodeId101(byte[] payload) {
        HeyPixelMsgpackReader reader = new HeyPixelMsgpackReader(payload);
        UUID packetUuid = reader.readUuid();
        long packetLong = reader.readLong();
        Id1PacketBuilder.Id1Subtype subtype = decodeId1Subtype(reader.readInt());
        String challengeValue = subtype == Id1PacketBuilder.Id1Subtype.ATTACK
            ? reader.readString()
            : null;
        Id101Challenge result = new Id101Challenge(
            packetUuid,
            packetLong,
            subtype,
            challengeValue
        );
        requireFullyConsumed(101, reader);
        return result;
    }

    /**
     * Local fail-closed ID100 decoder. The official constructor reads only the UUID/long prefix and
     * does not check EOF; use {@link #decodeId100OfficialPrefix(byte[])} when exact prefix parity is
     * required.
     */
    public static Id100Packet decodeId100(byte[] payload) {
        HeyPixelMsgpackReader reader = new HeyPixelMsgpackReader(payload);
        Id100Packet result = readId100Prefix(reader);
        requireFullyConsumed(100, reader);
        return result;
    }

    /** Official-constructor parity: parse UUID + long and deliberately ignore trailing values. */
    public static Id100Packet decodeId100OfficialPrefix(byte[] payload) {
        return readId100Prefix(new HeyPixelMsgpackReader(payload));
    }

    private static Id100Packet readId100Prefix(HeyPixelMsgpackReader reader) {
        return new Id100Packet(reader.readCanonicalUuid(), reader.readLong());
    }

    public static Id103CpsTelemetry decodeId103(byte[] payload) {
        HeyPixelMsgpackReader reader = new HeyPixelMsgpackReader(payload);
        boolean batchEncoding = reader.nextIsArray();
        List<Id103CpsEntry> entries = new ArrayList<>();
        if (batchEncoding) {
            int count = reader.readArrayHeader();
            for (int i = 0; i < count; i++) {
                int fieldCount = reader.readArrayHeader();
                if (fieldCount < 4) {
                    throw new IllegalArgumentException(
                        "S2C ID103 batch entry " + i + " has " + fieldCount + " fields instead of at least 4");
                }
                entries.add(readId103Entry(reader));
                for (int field = 4; field < fieldCount; field++) reader.skipValue();
            }
        } else {
            entries.add(readId103Entry(reader));
        }
        return new Id103CpsTelemetry(entries, batchEncoding, reader.remaining());
    }

    /**
     * @deprecated Use {@link #decodeActivationEffect(byte[])}. Kept as a binary/source compatibility
     * adapter for the earlier numeric ID104 surface.
     */
    @Deprecated
    public static Id104SoundEffect decodeId104(byte[] payload) {
        return Id104SoundEffect.fromActivationEffect(decodeActivationEffect(payload));
    }

    public static ActivationEffectPacket decodeActivationEffect(byte[] payload) {
        HeyPixelMsgpackReader reader = new HeyPixelMsgpackReader(payload);
        ActivationEffectPacket result = new ActivationEffectPacket(
            ActivationResourceType.fromWireId(reader.readInt()),
            reader.readString(),
            reader.readString(),
            reader.readString()
        );
        requireFullyConsumed(104, reader);
        return result;
    }

    public static FlightLeanDirectionPacket decodeId105(byte[] payload) {
        HeyPixelMsgpackReader reader = new HeyPixelMsgpackReader(payload);
        FlightLeanDirectionPacket result = new FlightLeanDirectionPacket(
            reader.readCanonicalUuid(),
            reader.readInt(),
            reader.readInt()
        );
        requireFullyConsumed(105, reader);
        return result;
    }

    public static ShowGameStorePopupRequest decodeId107(byte[] payload) {
        return new ShowGameStorePopupRequest(payload.length);
    }

    public static OpenPanelPacket decodeId110(byte[] payload) {
        HeyPixelMsgpackReader reader = new HeyPixelMsgpackReader(payload);
        int panelMode = reader.readInt();
        long panelId = reader.readLong();
        int designWidth = reader.readInt();
        int designHeight = reader.readInt();
        String content = reader.hasRemaining() ? reader.readString() : "";
        boolean scaleToFit = false;
        float scaleMultiplier = 1.0f;
        if (panelMode == 0 && reader.hasRemaining()) scaleToFit = reader.readBoolean();
        if (scaleToFit && reader.hasRemaining()) scaleMultiplier = reader.readFloat();
        requireFullyConsumed(110, reader);
        return new OpenPanelPacket(
            panelMode,
            panelId,
            designWidth,
            designHeight,
            content,
            scaleToFit,
            scaleMultiplier
        );
    }

    /**
     * Decodes the official HUD registry batch. The wire-level first String is the HUD id used by
     * the official handler as the registry key; the remaining six values construct one HudInfo.
     */
    public static S2CHudInfoBatchPacket decodeHudInfoBatch(byte[] payload) {
        HeyPixelMsgpackReader reader = new HeyPixelMsgpackReader(payload);
        List<HudInfoEntry> result = new ArrayList<>();
        int index = 0;
        while (true) {
            int advertisedCount = readPanelBatchGuard(111, reader);
            if (index >= advertisedCount) break;
            if (reader.remaining() < MIN_HUD_INFO_ENTRY_BYTES + 1) {
                throw new IllegalArgumentException(
                    "S2C ID111 HUD info and terminal guard cannot fit in the remaining payload");
            }
            String hudId = reader.readString();
            result.add(new HudInfoEntry(hudId, new HudInfo(
                reader.readInt(),
                reader.readInt(),
                reader.readString(),
                reader.readString(),
                reader.readString(),
                reader.readString()
            )));
            index++;
        }
        requireFullyConsumed(111, reader);
        return new S2CHudInfoBatchPacket(result);
    }

    /**
     * @deprecated Use {@link #decodeHudInfoBatch(byte[])}. The former PanelRecord view preserves
     * the old wire-position API: {@code key()} is the first String and therefore the HUD id, not
     * the official HudInfo key field.
     */
    @Deprecated
    public static List<PanelRecord> decodeId111(byte[] payload) {
        return decodeHudInfoBatch(payload).entries().stream()
            .map(PanelRecord::fromHudInfoEntry)
            .toList();
    }

    /**
     * Decodes the high-confidence repeated-guard framing. The expected operation domain is 0..2.
     * For positive ordinals at least 3, the official helper defaults to REPLACE when control is
     * nonzero and indexes out of bounds when control is zero; negative ordinals index out of
     * bounds. This local decoder deliberately rejects every out-of-domain ordinal. The official
     * constructor does not check EOF after the terminal guard; this compatibility entry also
     * rejects trailing bytes. Both rejections are fail-closed local policy, not unconditional
     * helper parity. Canonical runtime uses {@link #decodeId112OfficialPrefix(byte[])}.
     */
    public static PanelModelOperationPacket decodeId112(byte[] payload) {
        return decodeId112(payload, true);
    }

    /** Mirrors the official constructor's terminal-guard prefix consumption. */
    public static PanelModelOperationPacket decodeId112OfficialPrefix(byte[] payload) {
        return decodeId112(payload, false);
    }

    private static PanelModelOperationPacket decodeId112(byte[] payload, boolean strictTrailing) {
        HeyPixelMsgpackReader reader = new HeyPixelMsgpackReader(payload);
        int operationOrdinal = reader.readInt();
        PanelModelOperation.fromLocalStrictOrdinal(operationOrdinal);
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (true) {
            int advertisedCount = readPanelBatchGuard(112, reader);
            if (index >= advertisedCount) break;
            if (reader.remaining() < 2) {
                throw new IllegalArgumentException(
                    "S2C ID112 model JSON and terminal guard cannot fit in the remaining payload");
            }
            entries.add(reader.readString());
            index++;
        }
        if (strictTrailing) requireFullyConsumed(112, reader);
        return new PanelModelOperationPacket(operationOrdinal, entries);
    }

    private static int readPanelBatchGuard(int packetId, HeyPixelMsgpackReader reader) {
        int advertisedCount = reader.readArrayHeader();
        if (advertisedCount > MAX_PANEL_BATCH_ENTRIES) {
            throw new IllegalArgumentException(
                "S2C ID" + packetId + " panel batch guard exceeds "
                    + MAX_PANEL_BATCH_ENTRIES + ": " + advertisedCount);
        }
        return advertisedCount;
    }

    public static SyncTokenMetadata decodeId114(byte[] payload) {
        return decodeSyncTokenStrict(payload);
    }

    public static SyncTokenMetadata decodeSyncToken(byte[] payload) {
        return decodeSyncTokenStrict(payload);
    }

    /** Mirrors the official constructor, which consumes one String and ignores trailing values. */
    public static SyncTokenMetadata decodeSyncTokenOfficialPrefix(byte[] payload) {
        return decodeSyncTokenLeaseOfficialPrefix(payload).metadata();
    }

    static Id114TokenLease decodeSyncTokenLeaseOfficialPrefix(byte[] payload) {
        HeyPixelMsgpackReader reader = new HeyPixelMsgpackReader(payload);
        String token = reader.readString();
        return Id114TokenLease.fromToken(token);
    }

    /** Retains the former local fail-closed policy for compatibility callers. */
    public static SyncTokenMetadata decodeSyncTokenStrict(byte[] payload) {
        return decodeSyncTokenLeaseStrict(payload).metadata();
    }

    static Id114TokenLease decodeSyncTokenLeaseStrict(byte[] payload) {
        HeyPixelMsgpackReader reader = new HeyPixelMsgpackReader(payload);
        String token = reader.readString();
        requireFullyConsumed(114, reader);
        return Id114TokenLease.fromToken(token);
    }

    public static ResourceBlobFragment decodeResourceBlobFragment(byte[] payload) {
        HeyPixelMsgpackReader reader = new HeyPixelMsgpackReader(payload);
        ResourceBlobFragment result = new ResourceBlobFragment(
            reader.readString(),
            reader.readString(),
            reader.readInt(),
            reader.readInt(),
            reader.readRawBytes()
        );
        requireFullyConsumed(108, reader);
        return result;
    }

    public static ResourceIndexFragment decodeResourceIndexFragment(byte[] payload) {
        HeyPixelMsgpackReader reader = new HeyPixelMsgpackReader(payload);
        ResourceIndexFragment result = new ResourceIndexFragment(
            reader.readString(),
            reader.readString(),
            reader.readInt(),
            reader.readInt(),
            reader.readInt(),
            reader.readRawBytes()
        );
        requireFullyConsumed(109, reader);
        return result;
    }

    /** @deprecated Use {@link #decodeResourceIndexFragment(byte[])}. */
    @Deprecated
    public static ChunkedDataFragment decodeChunkedDataFragment(byte[] payload) {
        return ChunkedDataFragment.fromResourceIndex(decodeResourceIndexFragment(payload));
    }

    /**
     * Local generic compatibility decoder. It intentionally keeps strict UTF-8, plain fallback and
     * arbitrary JSON-root preservation; those policies are not official ID113/115 parity.
     */
    public static JsonPayload decodeJsonPacket(int packetId, byte[] payload) {
        if (packetId != 113 && packetId != 115 && packetId != 116
            && packetId != 118 && packetId != 119) {
            throw new IllegalArgumentException("packet " + packetId + " is not a recovered JSON decoder");
        }
        return inflateOrPlainJson(packetId, payload);
    }

    public static UnlockExchangeStatePacket decodeUnlockExchangeState(byte[] payload) {
        JsonPayload json = inflateOrPlainJson(116, payload);
        return new UnlockExchangeStatePacket(parseJsonObject(116, json.json()), json.zlibCompressed());
    }

    public static ShopMessagePacket decodeShopMessage(byte[] payload) {
        JsonPayload json = inflateZlibJsonWithReplacement(113, payload);
        return new ShopMessagePacket(
            json.json(),
            parseJsonObject(113, json.json()),
            json.zlibCompressed()
        );
    }

    public static SelectionDefinitionPacket decodeSelectionDefinition(byte[] payload) {
        JsonPayload json = inflateZlibJsonWithReplacement(115, payload);
        SelectionDefinitionPacket packet = new SelectionDefinitionPacket(
            json.json(),
            parseJsonObject(115, json.json()),
            json.zlibCompressed()
        );
        packet.officialView();
        return packet;
    }

    public static FashionConfigPacket decodeFashionConfig(byte[] payload) {
        JsonPayload json = inflateOrPlainJson(118, payload);
        return new FashionConfigPacket(parseJsonObject(118, json.json()), json.zlibCompressed());
    }

    public static PlayerFashionStatePacket decodePlayerFashionState(byte[] payload) {
        JsonPayload json = inflateOrPlainJson(119, payload);
        return new PlayerFashionStatePacket(parseJsonObject(119, json.json()), json.zlibCompressed());
    }

    public static BoardInfoPacket decodeBoardInfo(byte[] payload) {
        JsonPayload json = inflateOrPlainJsonWithReplacement(121, payload);
        return new BoardInfoPacket(
            json.json(),
            parseJsonObject(121, json.json()),
            json.zlibCompressed()
        );
    }

    /** @deprecated ID121 is the official BoardInfo packet, not Notice Center state. */
    @Deprecated
    public static NoticeCenterSync decodeNoticeCenterSync(byte[] payload) {
        return NoticeCenterSync.fromBoardInfo(decodeBoardInfo(payload));
    }

    public static OpenFashionGuiPacket decodeOpenFashionGui(byte[] payload) {
        if (payload.length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException(
                "S2C ID117 JSON payload exceeds " + MAX_JSON_BYTES + " bytes");
        }
        String json = decodeUtf8Strict(117, payload);
        if (json.isEmpty()) return new OpenFashionGuiPacket(json, null);
        return new OpenFashionGuiPacket(json, JsonParser.parseString(json).getAsJsonObject());
    }

    public static FashionActionResultPacket decodeFashionActionResult(byte[] payload) {
        JsonPayload json = inflateOrPlainJson(120, payload);
        return new FashionActionResultPacket(
            json.json(),
            parseJsonObject(120, json.json()),
            json.zlibCompressed()
        );
    }

    /** @deprecated ID120 is specifically the FashionActionResult packet. */
    @Deprecated
    public static ActionResultPacket decodeActionResult(byte[] payload) {
        return ActionResultPacket.fromFashionActionResult(decodeFashionActionResult(payload));
    }

    private static Id103CpsEntry readId103Entry(HeyPixelMsgpackReader reader) {
        return new Id103CpsEntry(
            reader.readCanonicalUuid(),
            reader.readLong(),
            reader.readInt(),
            reader.readInt()
        );
    }

    private static JsonPayload inflateOrPlainJson(int packetId, byte[] payload) {
        try {
            return new JsonPayload(
                packetId,
                decodeUtf8Strict(packetId, inflateBounded(packetId, payload)),
                true
            );
        } catch (IOException ignored) {
            return new JsonPayload(packetId, decodeUtf8Strict(packetId, payload), false);
        }
    }

    private static JsonPayload inflateZlibJsonWithReplacement(int packetId, byte[] payload) {
        try {
            return new JsonPayload(
                packetId,
                decodeUtf8Replacing(inflateBounded(packetId, payload)),
                true
            );
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                "S2C ID" + packetId + " payload is not valid zlib data", exception);
        }
    }

    private static JsonPayload inflateOrPlainJsonWithReplacement(int packetId, byte[] payload) {
        try {
            return new JsonPayload(
                packetId,
                decodeUtf8Replacing(inflateBounded(packetId, payload)),
                true
            );
        } catch (IOException ignored) {
            if (payload.length > MAX_JSON_BYTES) {
                throw new IllegalArgumentException(
                    "S2C ID" + packetId + " JSON payload exceeds " + MAX_JSON_BYTES + " bytes");
            }
            return new JsonPayload(packetId, decodeUtf8Replacing(payload), false);
        }
    }

    private static byte[] inflateBounded(int packetId, byte[] payload) throws IOException {
        if (payload.length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException(
                "S2C ID" + packetId + " JSON payload exceeds " + MAX_JSON_BYTES + " bytes");
        }
        try (InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(payload));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8 * 1024];
            int total = 0;
            int read;
            while ((read = inflater.read(buffer)) != -1) {
                if (read == 0) continue;
                if (total > MAX_JSON_BYTES - read) {
                    throw new IllegalArgumentException(
                        "S2C ID" + packetId + " inflated JSON exceeds " + MAX_JSON_BYTES + " bytes");
                }
                out.write(buffer, 0, read);
                total += read;
            }
            return out.toByteArray();
        }
    }

    private static String decodeUtf8Replacing(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static String decodeUtf8Strict(int packetId, byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException(
                "S2C ID" + packetId + " payload is not valid UTF-8", exception);
        }
    }

    private static JsonElement parseJsonDocument(String json) {
        return JsonParser.parseString(json);
    }

    private static JsonObject parseJsonObject(int packetId, String json) {
        var element = JsonParser.parseString(json);
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("S2C ID" + packetId + " JSON root is not an object");
        }
        return element.getAsJsonObject();
    }

    private static Optional<String> optionalString(JsonObject state, String key) {
        if (!state.has(key) || !state.get(key).isJsonPrimitive()
            || !state.getAsJsonPrimitive(key).isString()) {
            return Optional.empty();
        }
        return Optional.of(state.get(key).getAsString());
    }

    private static Optional<String> optionalPrimitiveText(JsonObject state, String key) {
        if (!state.has(key) || !state.get(key).isJsonPrimitive()) return Optional.empty();
        return Optional.of(state.get(key).getAsString());
    }

    private static JsonField jsonField(JsonObject state, String key) {
        if (!state.has(key)) {
            return new JsonField(key, JsonFieldPresence.ABSENT, null);
        }
        JsonElement value = state.get(key);
        if (value == null || value.isJsonNull()) {
            return new JsonField(key, JsonFieldPresence.JSON_NULL, null);
        }
        return new JsonField(key, JsonFieldPresence.VALUE, value);
    }

    private static PlayerFashionPatchView playerFashionPatchView(JsonObject state) {
        return new PlayerFashionPatchView(
            optionalPrimitiveText(state, "uuid"),
            optionalPrimitiveText(state, "name"),
            jsonField(state, "id"),
            jsonField(state, "fashion"),
            jsonField(state, "equipped"),
            jsonField(state, "unlocked"),
            state
        );
    }

    private static Optional<JsonArray> optionalArray(JsonObject state, String key) {
        if (!state.has(key) || !state.get(key).isJsonArray()) return Optional.empty();
        return Optional.of(state.getAsJsonArray(key).deepCopy());
    }

    private static Optional<JsonObject> optionalObject(JsonObject state, String key) {
        if (!state.has(key) || !state.get(key).isJsonObject()) return Optional.empty();
        return Optional.of(state.getAsJsonObject(key).deepCopy());
    }

    private static Optional<Boolean> optionalBoolean(JsonObject state, String key) {
        if (!state.has(key) || !state.get(key).isJsonPrimitive()
            || !state.getAsJsonPrimitive(key).isBoolean()) {
            return Optional.empty();
        }
        return Optional.of(state.get(key).getAsBoolean());
    }

    private static OptionalLong optionalLong(JsonObject state, String key) {
        if (!state.has(key) || !state.get(key).isJsonPrimitive()
            || !state.getAsJsonPrimitive(key).isNumber()) {
            return OptionalLong.empty();
        }
        try {
            BigDecimal value = state.getAsJsonPrimitive(key).getAsBigDecimal();
            return OptionalLong.of(value.toBigIntegerExact().longValueExact());
        } catch (NumberFormatException | ArithmeticException ignored) {
            return OptionalLong.empty();
        }
    }

    private static Optional<List<String>> optionalStringList(
        JsonObject state,
        String key,
        int expectedSize
    ) {
        Optional<JsonArray> array = optionalArray(state, key);
        if (array.isEmpty() || array.get().size() != expectedSize) return Optional.empty();
        ArrayList<String> values = new ArrayList<>(expectedSize);
        for (var element : array.get()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                return Optional.empty();
            }
            values.add(element.getAsString());
        }
        return Optional.of(List.copyOf(values));
    }

    private static Optional<List<Boolean>> optionalBooleanList(
        JsonObject state,
        String key,
        int expectedSize
    ) {
        Optional<JsonArray> array = optionalArray(state, key);
        if (array.isEmpty() || array.get().size() != expectedSize) return Optional.empty();
        ArrayList<Boolean> values = new ArrayList<>(expectedSize);
        for (var element : array.get()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
                return Optional.empty();
            }
            values.add(element.getAsBoolean());
        }
        return Optional.of(List.copyOf(values));
    }

    private static SelectionDefinitionView officialSelectionDefinitionView(JsonObject state) {
        try {
            String type = state.has("type") ? state.get("type").getAsString() : "";
            long timestamp = state.get("timestamp").getAsLong();
            if ("definitions".equals(type)) {
                LinkedHashMap<String, String> definitions = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> entry
                    : state.getAsJsonObject("definitions").entrySet()) {
                    definitions.put(entry.getKey(), entry.getValue().getAsString());
                }
                return new SelectionDefinitionView(
                    type,
                    timestamp,
                    0L,
                    definitions,
                    List.of("", "", ""),
                    List.of(false, false, false)
                );
            }

            return new SelectionDefinitionView(
                type,
                timestamp,
                state.get("sessionId").getAsLong(),
                Map.of(),
                officialFixedStringArray(state.getAsJsonArray("hexKeys")),
                officialFixedBooleanArray(state.getAsJsonArray("refreshUsed"))
            );
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                "S2C ID115 JSON does not satisfy the official branch schema", exception);
        }
    }

    private static List<String> officialFixedStringArray(JsonArray source) {
        ArrayList<String> result = new ArrayList<>(List.of("", "", ""));
        for (int i = 0; i < Math.min(source.size(), 3); i++) {
            result.set(i, source.get(i).getAsString());
        }
        return List.copyOf(result);
    }

    private static List<Boolean> officialFixedBooleanArray(JsonArray source) {
        ArrayList<Boolean> result = new ArrayList<>(List.of(false, false, false));
        for (int i = 0; i < Math.min(source.size(), 3); i++) {
            result.set(i, source.get(i).getAsBoolean());
        }
        return List.copyOf(result);
    }

    private static void requireFullyConsumed(int packetId, HeyPixelMsgpackReader reader) {
        if (reader.hasRemaining()) {
            throw new IllegalArgumentException("S2C ID" + packetId + " left " + reader.remaining() + " unread bytes");
        }
    }

    private static Id1PacketBuilder.Id1Subtype decodeId1Subtype(int wireId) {
        return switch (wireId) {
            case 0 -> Id1PacketBuilder.Id1Subtype.SPRINT;
            case 1 -> Id1PacketBuilder.Id1Subtype.SNEAK;
            case 2 -> Id1PacketBuilder.Id1Subtype.SWIM;
            case 3 -> Id1PacketBuilder.Id1Subtype.ATTACK;
            default -> throw new IllegalArgumentException("unsupported ID101 subtype: " + wireId);
        };
    }

    public record WrappedPacket(int packetId, byte[] payload) {
        public WrappedPacket {
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }

    public record Id101Challenge(
        UUID packetUuid,
        long packetLong,
        Id1PacketBuilder.Id1Subtype subtype,
        String challengeValue
    ) {
        public String subtypeName() {
            return subtype.name();
        }
    }

    public record Id100Packet(UUID field00, long field01) {
    }

    /** Official mouse-button evidence closes the two integer fields as left then right CPS. */
    public record Id103CpsEntry(
        UUID playerUuid,
        long timestampMillis,
        int leftCps,
        int rightCps
    ) {
        /** @deprecated Use {@link #timestampMillis()}. */
        @Deprecated
        public long field01() {
            return timestampMillis;
        }

        /** @deprecated Use {@link #leftCps()}. */
        @Deprecated
        public int field02() {
            return leftCps;
        }

        /** @deprecated Use {@link #rightCps()}. */
        @Deprecated
        public int field03() {
            return rightCps;
        }
    }

    public record Id103CpsTelemetry(
        List<Id103CpsEntry> entries,
        boolean batchEncoding,
        int ignoredTrailingBytes
    ) {
        public Id103CpsTelemetry {
            entries = List.copyOf(entries);
            if (ignoredTrailingBytes < 0) {
                throw new IllegalArgumentException("negative ignored trailing byte count");
            }
        }

        public Id103CpsTelemetry(List<Id103CpsEntry> entries, boolean batchEncoding) {
            this(entries, batchEncoding, 0);
        }

        /** The official batch handler ignores the local player's entry. */
        public List<Id103CpsEntry> remoteEntries(UUID localPlayerUuid) {
            if (!batchEncoding || localPlayerUuid == null) return entries;
            return entries.stream()
                .filter(entry -> !localPlayerUuid.equals(entry.playerUuid()))
                .toList();
        }
    }

    public enum ActivationResourceType {
        ITEM(0),
        BLOCK(1),
        TEXTURE(2),
        TEXT(3),
        GeoModel(4);

        private final int wireId;

        ActivationResourceType(int wireId) {
            this.wireId = wireId;
        }

        public int wireId() {
            return wireId;
        }

        private static ActivationResourceType fromWireId(int wireId) {
            return switch (wireId) {
                case 0 -> ITEM;
                case 1 -> BLOCK;
                case 2 -> TEXTURE;
                case 3 -> TEXT;
                case 4 -> GeoModel;
                default -> throw new IllegalArgumentException(
                    "S2C ID104 resource type wire id out of range: " + wireId);
            };
        }
    }

    public record ActivationEffectPacket(
        ActivationResourceType resourceType,
        String field01,
        String soundId,
        String soundSource
    ) {
        public ActivationEffectPacket {
            Objects.requireNonNull(resourceType, "resourceType");
            Objects.requireNonNull(field01, "field01");
            Objects.requireNonNull(soundId, "soundId");
            Objects.requireNonNull(soundSource, "soundSource");
        }

        public int resourceTypeOrdinal() {
            return resourceType.wireId();
        }
    }

    /**
     * @deprecated Compatibility view from before the official activation-effect semantics were
     * recovered. The first field is the official {@link ActivationResourceType#wireId()}.
     */
    @Deprecated
    public record Id104SoundEffect(byte field00, String field01, String soundId, String soundSource) {
        public static Id104SoundEffect fromActivationEffect(ActivationEffectPacket packet) {
            return new Id104SoundEffect(
                (byte) packet.resourceTypeOrdinal(),
                packet.field01(),
                packet.soundId(),
                packet.soundSource()
            );
        }

        public ActivationEffectPacket toActivationEffect() {
            return new ActivationEffectPacket(
                ActivationResourceType.fromWireId(Byte.toUnsignedInt(field00)),
                field01,
                soundId,
                soundSource
            );
        }
    }

    public record FlightLeanDirectionPacket(
        UUID playerUuid,
        int effectTypeOrdinal,
        int leanDirectionOrdinal
    ) {
        public boolean isFlightLean() {
            return effectTypeOrdinal == 0;
        }

        public FlightLeanDirection leanDirection() {
            return switch (leanDirectionOrdinal) {
                case 0 -> FlightLeanDirection.NONE;
                case 1 -> FlightLeanDirection.LEFT;
                case 2 -> FlightLeanDirection.RIGHT;
                case 3 -> FlightLeanDirection.UP;
                case 4 -> FlightLeanDirection.DOWN;
                default -> FlightLeanDirection.UNKNOWN;
            };
        }
    }

    public enum FlightLeanDirection {
        NONE,
        LEFT,
        RIGHT,
        UP,
        DOWN,
        UNKNOWN
    }

    public record ShowGameStorePopupRequest(int ignoredPayloadBytes) {
        public ShowGameStorePopupRequest {
            if (ignoredPayloadBytes < 0) {
                throw new IllegalArgumentException("negative ignored payload byte count");
            }
        }
    }

    public record OpenPanelPacket(
        int panelMode,
        long panelId,
        int designWidth,
        int designHeight,
        String content,
        boolean scaleToFit,
        float scaleMultiplier
    ) {
    }

    public record HudInfo(
        int width,
        int height,
        String hudPath,
        String offsetX,
        String offsetY,
        String key
    ) {
        public HudInfo {
            Objects.requireNonNull(hudPath, "hudPath");
            Objects.requireNonNull(offsetX, "offsetX");
            Objects.requireNonNull(offsetY, "offsetY");
            Objects.requireNonNull(key, "key");
        }
    }

    public record HudInfoEntry(String hudId, HudInfo hudInfo) {
        public HudInfoEntry {
            Objects.requireNonNull(hudId, "hudId");
            Objects.requireNonNull(hudInfo, "hudInfo");
        }
    }

    public record S2CHudInfoBatchPacket(List<HudInfoEntry> entries) {
        public S2CHudInfoBatchPacket {
            entries = List.copyOf(entries);
        }

        /** Mirrors the official registry put semantics: duplicate HUD ids keep the last value. */
        public Map<String, HudInfo> hudInfosById() {
            LinkedHashMap<String, HudInfo> result = new LinkedHashMap<>();
            for (HudInfoEntry entry : entries) result.put(entry.hudId(), entry.hudInfo());
            return Collections.unmodifiableMap(result);
        }
    }

    /**
     * @deprecated Compatibility view from before ID111 was recovered as the HUD registry batch.
     * The component names remain tied to their old wire positions.
     */
    @Deprecated
    public record PanelRecord(
        String key,
        int field00,
        int field01,
        String field02,
        String field03,
        String field04,
        String field05
    ) {
        public static PanelRecord fromHudInfoEntry(HudInfoEntry entry) {
            HudInfo info = entry.hudInfo();
            return new PanelRecord(
                entry.hudId(),
                info.width(),
                info.height(),
                info.hudPath(),
                info.offsetX(),
                info.offsetY(),
                info.key()
            );
        }

        public HudInfoEntry toHudInfoEntry() {
            return new HudInfoEntry(
                key,
                new HudInfo(field00, field01, field02, field03, field04, field05)
            );
        }
    }

    public record PanelModelOperationPacket(int operationOrdinal, List<String> jsonEntries) {
        public PanelModelOperationPacket {
            jsonEntries = List.copyOf(jsonEntries);
        }

        /** Applies the local expected-domain fail-closed policy used by {@link #decodeId112(byte[])}. */
        public PanelModelOperation operation() {
            return PanelModelOperation.fromLocalStrictOrdinal(operationOrdinal);
        }

        /**
         * @deprecated Display-only compatibility adapter for manually constructed out-of-domain
         * ordinals. The canonical decoder rejects them as local fail-closed policy; it does not
         * model the official helper's control-dependent behavior outside the expected 0..2 domain.
         */
        @Deprecated
        public PanelModelOperation operationOrUnknown() {
            return PanelModelOperation.fromOrdinalOrUnknown(operationOrdinal);
        }
    }

    public enum PanelModelOperation {
        UPDATE,
        REMOVE,
        REPLACE,
        /** @deprecated Display-only, non-blocking local ABI sentinel; never accepted from transport. */
        @Deprecated
        UNKNOWN;

        /** Local expected-domain policy; not unconditional official helper parity. */
        static PanelModelOperation fromLocalStrictOrdinal(int ordinal) {
            if (ordinal < 0 || ordinal >= UNKNOWN.ordinal()) {
                throw new IllegalArgumentException(
                    "S2C ID112 operation ordinal is outside local expected domain 0..2: "
                        + ordinal);
            }
            return values()[ordinal];
        }

        static PanelModelOperation fromOrdinalOrUnknown(int ordinal) {
            return ordinal >= 0 && ordinal < UNKNOWN.ordinal() ? values()[ordinal] : UNKNOWN;
        }
    }

    public record JsonPayload(int packetId, String json, boolean zlibCompressed) {
    }

    /** Dynamically confirmed ID113 Shop message keys; decoding never invokes UI or manager effects. */
    public record ShopMessagePacket(String json, JsonElement document, boolean zlibCompressed) {
        public ShopMessagePacket {
            Objects.requireNonNull(json, "json");
            document = Objects.requireNonNull(document, "document").deepCopy();
        }

        @Override
        public JsonElement document() {
            return document.deepCopy();
        }

        private Optional<JsonObject> objectState() {
            return document.isJsonObject()
                ? Optional.of(document.getAsJsonObject())
                : Optional.empty();
        }

        public Optional<String> type() {
            return objectState().flatMap(state -> optionalString(state, "type"));
        }

        public Optional<String> keys() {
            return objectState().flatMap(state -> optionalString(state, "keys"));
        }

        public Optional<String> msg() {
            return objectState().flatMap(state -> optionalString(state, "msg"));
        }

        public Optional<String> page() {
            return objectState().flatMap(state -> optionalString(state, "page"));
        }

        public Optional<JsonObject> deposits() {
            return objectState().flatMap(state -> optionalObject(state, "deposits"));
        }

        public Optional<JsonObject> limits() {
            return objectState().flatMap(state -> optionalObject(state, "limits"));
        }

    }

    /** Official ID115 zlib/branch projection plus local conservative accessors; no manager is invoked. */
    public record SelectionDefinitionPacket(String json, JsonElement document, boolean zlibCompressed) {
        public SelectionDefinitionPacket {
            Objects.requireNonNull(json, "json");
            document = Objects.requireNonNull(document, "document").deepCopy();
        }

        @Override
        public JsonElement document() {
            return document.deepCopy();
        }

        private Optional<JsonObject> objectState() {
            return document.isJsonObject()
                ? Optional.of(document.getAsJsonObject())
                : Optional.empty();
        }

        /**
         * Official constructor parity for defaults, required fields and branch-specific reads. The
         * canonical decoder validates this view before returning the packet.
         */
        public SelectionDefinitionView officialView() {
            if (!document.isJsonObject()) {
                throw new IllegalArgumentException("S2C ID115 JSON root is not an object");
            }
            return officialSelectionDefinitionView(document.getAsJsonObject());
        }

        /** Local conservative exact-string presence view; official missing type defaults to "". */
        public Optional<String> type() {
            return objectState().flatMap(state -> optionalString(state, "type"));
        }

        /** Local exact-integer view; official parity is {@link #officialView()}. */
        public OptionalLong timestamp() {
            return objectState().map(state -> optionalLong(state, "timestamp"))
                .orElseGet(OptionalLong::empty);
        }

        public Optional<JsonObject> definitions() {
            return objectState().flatMap(state -> optionalObject(state, "definitions"));
        }

        /** Local exact-integer view; official non-definitions branches require this field. */
        public OptionalLong sessionId() {
            return objectState().map(state -> optionalLong(state, "sessionId"))
                .orElseGet(OptionalLong::empty);
        }

        /** Local strict three-string view; official parity pads/truncates through {@link #officialView()}. */
        public Optional<List<String>> hexKeys() {
            return objectState().flatMap(state -> optionalStringList(state, "hexKeys", 3));
        }

        /** Local strict three-boolean view; official parity pads/truncates through {@link #officialView()}. */
        public Optional<List<Boolean>> refreshUsed() {
            return objectState().flatMap(state -> optionalBooleanList(state, "refreshUsed", 3));
        }

    }

    public record SelectionDefinitionView(
        String type,
        long timestamp,
        long sessionId,
        Map<String, String> definitions,
        List<String> hexKeys,
        List<Boolean> refreshUsed
    ) {
        public SelectionDefinitionView {
            type = Objects.requireNonNull(type, "type");
            definitions = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(definitions, "definitions")));
            hexKeys = List.copyOf(hexKeys);
            refreshUsed = List.copyOf(refreshUsed);
            if (hexKeys.size() != 3 || refreshUsed.size() != 3) {
                throw new IllegalArgumentException("ID115 fixed arrays must contain exactly three values");
            }
        }
    }

    /** Dynamically confirmed ID116 keys: type, exchange, exchange_raw and unlocked. */
    public record UnlockExchangeStatePacket(JsonObject state, boolean zlibCompressed) {
        public UnlockExchangeStatePacket {
            state = state.deepCopy();
        }

        @Override
        public JsonObject state() {
            return state.deepCopy();
        }

        public Optional<String> type() {
            return optionalString(state, "type");
        }

        public Optional<JsonArray> exchange() {
            return optionalArray(state, "exchange");
        }

        public Optional<JsonArray> exchangeRaw() {
            return optionalArray(state, "exchange_raw");
        }

        public Optional<JsonObject> unlocked() {
            return optionalObject(state, "unlocked");
        }

        public String json() {
            return state.toString();
        }
    }

    /** Presence-preserving view for open JSON fields whose nested schema is not yet closed. */
    public enum JsonFieldPresence {
        ABSENT,
        JSON_NULL,
        VALUE
    }

    public record JsonField(String key, JsonFieldPresence presence, JsonElement value) {
        public JsonField {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(presence, "presence");
            value = value == null ? null : value.deepCopy();
            if (presence == JsonFieldPresence.VALUE
                && (value == null || value.isJsonNull())) {
                throw new IllegalArgumentException("VALUE JSON field requires a non-null value");
            }
            if (presence != JsonFieldPresence.VALUE && value != null) {
                throw new IllegalArgumentException("Absent or null JSON field cannot carry a value");
            }
        }

        @Override
        public JsonElement value() {
            return value == null ? null : value.deepCopy();
        }
    }

    /**
     * Raw-preserving projection of the ID119 fields read by the official consumer. It deliberately
     * does not resolve UUIDs/entities or normalize fashion/equipped/unlocked nested values.
     */
    public record PlayerFashionPatchView(
        Optional<String> uuidText,
        Optional<String> name,
        JsonField id,
        JsonField fashion,
        JsonField equipped,
        JsonField unlocked,
        JsonObject raw
    ) {
        public PlayerFashionPatchView {
            uuidText = Objects.requireNonNull(uuidText, "uuidText");
            name = Objects.requireNonNull(name, "name");
            id = Objects.requireNonNull(id, "id");
            fashion = Objects.requireNonNull(fashion, "fashion");
            equipped = Objects.requireNonNull(equipped, "equipped");
            unlocked = Objects.requireNonNull(unlocked, "unlocked");
            raw = Objects.requireNonNull(raw, "raw").deepCopy();
        }

        @Override
        public JsonObject raw() {
            return raw.deepCopy();
        }
    }

    /**
     * ID118 raw state. Static code reads type/configs and the enabled aliases; rarities is retained
     * from dynamic evidence without imposing a nested schema.
     */
    public record FashionConfigPacket(JsonObject state, boolean zlibCompressed) {
        public FashionConfigPacket {
            state = state.deepCopy();
        }

        @Override
        public JsonObject state() {
            return state.deepCopy();
        }

        public Optional<String> type() {
            return optionalString(state, "type");
        }

        /** Official summary precedence: enabled, enabled_fashions, then enabledFashions. */
        public Optional<JsonField> enabledSummaryField() {
            for (String key : List.of("enabled", "enabled_fashions", "enabledFashions")) {
                if (state.has(key)) return Optional.of(jsonField(state, key));
            }
            return Optional.empty();
        }

        public JsonField configsField() {
            return jsonField(state, "configs");
        }

        public JsonField raritiesField() {
            return jsonField(state, "rarities");
        }

        public Optional<JsonObject> configs() {
            return optionalObject(state, "configs");
        }

        public Optional<JsonObject> enabled() {
            return optionalObject(state, "enabled");
        }

        public Optional<JsonArray> enabledFashions() {
            return optionalArray(state, "enabled_fashions");
        }

        public Optional<JsonObject> rarities() {
            return optionalObject(state, "rarities");
        }

        public String json() {
            return state.toString();
        }
    }

    /** ID119 raw state plus official root/self/players patch routing, without manager side effects. */
    public record PlayerFashionStatePacket(JsonObject state, boolean zlibCompressed) {
        public PlayerFashionStatePacket {
            state = state.deepCopy();
        }

        @Override
        public JsonObject state() {
            return state.deepCopy();
        }

        public Optional<String> type() {
            return optionalString(state, "type");
        }

        public Optional<JsonObject> players() {
            return optionalObject(state, "players");
        }

        public boolean hasDirectPatch() {
            return state.has("fashion") || state.has("equipped");
        }

        public Optional<PlayerFashionPatchView> directPatch() {
            return hasDirectPatch()
                ? Optional.of(playerFashionPatchView(state))
                : Optional.empty();
        }

        public Optional<PlayerFashionPatchView> selfPatch() {
            return optionalObject(state, "self").map(S2CPacketDecoders::playerFashionPatchView);
        }

        public Map<String, PlayerFashionPatchView> playerPatches() {
            Optional<JsonObject> players = optionalObject(state, "players");
            if (players.isEmpty()) return Map.of();
            LinkedHashMap<String, PlayerFashionPatchView> result = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : players.get().entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    result.put(entry.getKey(), playerFashionPatchView(entry.getValue().getAsJsonObject()));
                }
            }
            return Collections.unmodifiableMap(result);
        }

        public String json() {
            return state.toString();
        }
    }

    public record OpenFashionGuiPacket(String json, JsonObject state) {
        public OpenFashionGuiPacket {
            state = state == null ? null : state.deepCopy();
        }

        @Override
        public JsonObject state() {
            return state == null ? null : state.deepCopy();
        }

        public boolean empty() {
            return json.isEmpty();
        }

        public Optional<String> category() {
            return state == null ? Optional.empty() : optionalString(state, "category");
        }
    }

    public record FashionActionResultPacket(String json, JsonObject state, boolean zlibCompressed) {
        public FashionActionResultPacket {
            Objects.requireNonNull(json, "json");
            state = state.deepCopy();
        }

        @Override
        public JsonObject state() {
            return state.deepCopy();
        }

        public Optional<String> action() {
            return optionalString(state, "action");
        }

        public Optional<Boolean> success() {
            return optionalBoolean(state, "success");
        }

        public Optional<String> message() {
            return optionalString(state, "message");
        }

    }

    /** @deprecated Compatibility view for the former generic ID120 name. */
    @Deprecated
    public record ActionResultPacket(JsonObject state, boolean zlibCompressed) {
        public ActionResultPacket {
            state = state.deepCopy();
        }

        @Override
        public JsonObject state() {
            return state.deepCopy();
        }

        public String json() {
            return state.toString();
        }

        public static ActionResultPacket fromFashionActionResult(
            FashionActionResultPacket packet
        ) {
            return new ActionResultPacket(packet.state(), packet.zlibCompressed());
        }
    }

    public record BoardInfoPacket(String json, JsonObject state, boolean zlibCompressed) {
        public BoardInfoPacket {
            Objects.requireNonNull(json, "json");
            state = state.deepCopy();
        }

        @Override
        public JsonObject state() {
            return state.deepCopy();
        }

        /** Official getAsString coercion with missing/nonprimitive default "". */
        public String officialBoardId() {
            JsonElement value = state.get("boardId");
            return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
        }

        /** Official missing/nonarray fallback is an empty array; the returned array is defensive. */
        public JsonArray officialTabs() {
            JsonElement value = state.get("tabs");
            return value != null && value.isJsonArray()
                ? value.getAsJsonArray().deepCopy()
                : new JsonArray();
        }

        /** Official getAsBoolean coercion with missing/nonprimitive default false. */
        public boolean officialOpen() {
            JsonElement value = state.get("open");
            return value != null && value.isJsonPrimitive() && value.getAsBoolean();
        }

        /** Local conservative exact-string presence view. */
        public Optional<String> boardId() {
            return optionalString(state, "boardId");
        }

        /** Local conservative array-presence view. */
        public Optional<JsonArray> tabs() {
            return optionalArray(state, "tabs");
        }

        /** Local conservative exact-boolean presence view. */
        public Optional<Boolean> open() {
            return optionalBoolean(state, "open");
        }

    }

    /** @deprecated Compatibility view for the former ID121 Notice Center name. */
    @Deprecated
    public record NoticeCenterSync(JsonObject state, boolean zlibCompressed) {
        public NoticeCenterSync {
            state = state.deepCopy();
        }

        @Override
        public JsonObject state() {
            return state.deepCopy();
        }

        public String json() {
            return state.toString();
        }

        public static NoticeCenterSync fromBoardInfo(BoardInfoPacket packet) {
            return new NoticeCenterSync(packet.state(), packet.zlibCompressed());
        }
    }

    public record ResourceBlobFragment(
        String resourceName,
        String hash,
        int chunkIndex,
        int chunkCount,
        byte[] chunkBytes
    ) {
        public ResourceBlobFragment {
            Objects.requireNonNull(resourceName, "resourceName");
            Objects.requireNonNull(hash, "hash");
            chunkBytes = chunkBytes.clone();
        }

        /** @deprecated Use {@link #resourceName()}. */
        @Deprecated
        public String field00() {
            return resourceName;
        }

        /** @deprecated Use {@link #hash()}. */
        @Deprecated
        public String field01() {
            return hash;
        }

        @Override
        public byte[] chunkBytes() {
            return chunkBytes.clone();
        }
    }

    public record ResourceIndexFragment(
        String indexName,
        String cacheHash,
        int mode,
        int chunkIndex,
        int chunkCount,
        byte[] chunkBytes
    ) {
        public ResourceIndexFragment {
            Objects.requireNonNull(indexName, "indexName");
            Objects.requireNonNull(cacheHash, "cacheHash");
            chunkBytes = chunkBytes.clone();
        }

        /** @deprecated Use {@link #indexName()}. */
        @Deprecated
        public String transferKey() {
            return indexName;
        }

        @Override
        public byte[] chunkBytes() {
            return chunkBytes.clone();
        }
    }

    /** @deprecated Use {@link ResourceIndexFragment}. */
    @Deprecated
    public record ChunkedDataFragment(
        String field00,
        String field01,
        int field02,
        int chunkIndex,
        int chunkCount,
        byte[] chunkBytes
    ) {
        public ChunkedDataFragment {
            chunkBytes = chunkBytes.clone();
        }

        public static ChunkedDataFragment fromResourceIndex(ResourceIndexFragment fragment) {
            return new ChunkedDataFragment(
                fragment.indexName(),
                fragment.cacheHash(),
                fragment.mode(),
                fragment.chunkIndex(),
                fragment.chunkCount(),
                fragment.chunkBytes()
            );
        }

        public ResourceIndexFragment toResourceIndex() {
            return new ResourceIndexFragment(
                field00, field01, field02, chunkIndex, chunkCount, chunkBytes);
        }

        @Override
        public byte[] chunkBytes() {
            return chunkBytes.clone();
        }
    }
}
