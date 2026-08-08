package shit.zen.protocol.heypixel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class Id1PacketBuilder {
    public static final int PACKET_ID = 1;
    public static final int SHORT_EVIDENCE_LIMIT = 40;
    public static final int OFFICIAL_LOADED_MOD_COUNT = 16;
    public static final int OFFICIAL_TOP_LEVEL_JAR_COUNT = 13;

    private final Id1SignatureProvider signatures;
    private final Id1CryptoTransform crypto;
    private final EvidenceSampler sampler;
    private final AttackValueProvider attackValues;
    private final UuidSelectedPayloadFramer framer;

    public Id1PacketBuilder(
        Id1SignatureProvider signatures,
        Id1CryptoTransform crypto,
        EvidenceSampler sampler,
        AttackValueProvider attackValues
    ) {
        this.signatures = Objects.requireNonNull(signatures, "signatures");
        this.crypto = Objects.requireNonNull(crypto, "crypto");
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.attackValues = Objects.requireNonNull(attackValues, "attackValues");
        this.framer = new UuidSelectedPayloadFramer();
    }

    public byte[] buildPacket(Challenge challenge, Context context, Object subtypePayload) {
        byte[] preCrypto = buildPreCrypto(challenge, context, subtypePayload);
        if (!crypto.available()) {
            throw new IllegalStateException("ID1 crypto is unavailable; refusing to build a live packet");
        }
        byte[] postCrypto = Objects.requireNonNull(crypto.transform(preCrypto), "crypto result");
        return framer.framePacket(PACKET_ID, postCrypto, context.localUuid());
    }

    public byte[] buildInitialSprintPacket(Challenge challenge, Context context, SprintEnvironment environment) {
        return buildInitialSprint(challenge, context, environment).wire();
    }

    public BuiltPacket buildInitialSprint(Challenge challenge, Context context, SprintEnvironment environment) {
        Objects.requireNonNull(challenge, "challenge");
        Objects.requireNonNull(context, "context");
        if (challenge.subtype() != Id1Subtype.SPRINT) {
            throw new IllegalArgumentException("The initial ID1 packet must use the SPRINT subtype");
        }
        if (!challenge.packetUuid().equals(context.localUuid())) {
            throw new IllegalArgumentException("The initial ID1 packet UUID must match the local player UUID");
        }
        byte[] preCrypto = buildPreCrypto(challenge, context, environment);
        int layout = framer.selectLayout(context.localUuid());
        byte[] wire = framer.framePacket(PACKET_ID, preCrypto, context.localUuid());
        return new BuiltPacket(preCrypto, wire, layout);
    }

    public byte[] buildPreCrypto(Challenge challenge, Context context, Object subtypePayload) {
        HeyPixelMsgpackWriter writer = new HeyPixelMsgpackWriter();
        writer.packLong(context.writerTime());
        writer.packString(context.localUuid().toString());
        writer.packByte((byte) challenge.subtype().wireId);
        writer.packString(challenge.packetUuid().toString());
        writer.packLong(challenge.packetLong());

        switch (challenge.subtype()) {
            case SPRINT -> writeSprint(writer, requireType(subtypePayload, SprintEnvironment.class));
            case SNEAK -> writeSneak(writer, requireType(subtypePayload, SneakEvidence.class));
            case SWIM -> writeSwim(writer, requireType(subtypePayload, SwimEvidence.class));
            case ATTACK -> writeAttack(writer, challenge.challengeValue());
        }
        return writer.toByteArray();
    }

    private void writeSprint(HeyPixelMsgpackWriter writer, SprintEnvironment environment) {
        requireSignatures();
        validateOfficialSprintSnapshot(environment);
        writer.packArrayHeader(environment.loadedMods().size());
        for (ModEvidence mod : environment.loadedMods()) {
            writer.packString(mod.moduleName());
            writer.packString(mod.path());
            writer.packString(mod.digest());
        }
        writer.packString(environment.userDirectory());
        writer.packString(environment.javaHome());
        writer.packValue(environment.cpuInfo());
        writer.packValue(environment.computerSystemInfo());
        writer.packValue(environment.networkInterfaces());
        writer.packValue(environment.diskStores());
        writer.packValue(environment.accountTraces());
        writer.packValue(environment.userProperties());

        writer.packArrayHeader(environment.discoveredJars().size());
        for (String jar : environment.discoveredJars()) {
            writer.packString(signatures.signString(jar));
            writer.packString(signatures.signString(environment.discoveredJarDigests().get(jar)));
        }
    }

    private static void validateOfficialSprintSnapshot(SprintEnvironment environment) {
        if (environment.loadedMods().size() != OFFICIAL_LOADED_MOD_COUNT) {
            throw new IllegalStateException("ID1 requires the complete 16-entry startup LoadingModList snapshot");
        }
        Set<String> moduleNames = new LinkedHashSet<>();
        for (ModEvidence mod : environment.loadedMods()) {
            if (mod.moduleName().isBlank() || mod.digest().isBlank()
                || !moduleNames.add(mod.moduleName().toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException("ID1 startup LoadingModList evidence is incomplete or duplicated");
            }
        }

        if (environment.discoveredJars().size() != OFFICIAL_TOP_LEVEL_JAR_COUNT
            || environment.discoveredJarDigests().size() != OFFICIAL_TOP_LEVEL_JAR_COUNT) {
            throw new IllegalStateException("ID1 requires the complete 13-entry top-level JAR snapshot");
        }
        Set<String> jarPaths = new LinkedHashSet<>();
        for (String jar : environment.discoveredJars()) {
            String digest = environment.discoveredJarDigests().get(jar);
            if (jar == null || jar.isBlank() || digest == null || digest.isBlank() || !jarPaths.add(jar)) {
                throw new IllegalStateException("ID1 top-level JAR evidence is incomplete or duplicated");
            }
        }
    }

    private void writeSneak(HeyPixelMsgpackWriter writer, SneakEvidence evidence) {
        List<String> values = sampler.sample(new ArrayList<>(evidence.values()), SHORT_EVIDENCE_LIMIT);
        writer.packInt(evidence.stateCode());
        writer.packInt(evidence.values().size());
        writer.packValue(values);
    }

    private void writeSwim(HeyPixelMsgpackWriter writer, SwimEvidence evidence) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>(evidence.valuesByKey());
        List<String> keys = sampler.sample(new ArrayList<>(values.keySet()), SHORT_EVIDENCE_LIMIT);
        List<String> samples = keys.stream().map(key -> key + ":" + values.get(key)).toList();
        writer.packInt(evidence.evidenceKeyCount());
        writer.packInt(values.size());
        writer.packValue(samples);
    }

    private void writeAttack(HeyPixelMsgpackWriter writer, String challengeValue) {
        requireSignatures();
        Object derived = Objects.requireNonNull(attackValues.derive(challengeValue), "derived attack value");
        writer.packInt(derived.hashCode());
        writer.packString(signatures.signString(derived.toString()));
    }

    private void requireSignatures() {
        if (!signatures.available()) {
            throw new IllegalStateException("ID1 signatures are unavailable; refusing to construct this subtype");
        }
    }

    private static <T> T requireType(Object value, Class<T> type) {
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("Expected " + type.getSimpleName() + " but got "
                + (value == null ? "null" : value.getClass().getName()));
        }
        return type.cast(value);
    }

    public enum Id1Subtype {
        SPRINT(0), SNEAK(1), SWIM(2), ATTACK(3);

        private final int wireId;

        Id1Subtype(int wireId) {
            this.wireId = wireId;
        }

        public int wireId() {
            return wireId;
        }
    }

    public record Challenge(UUID packetUuid, long packetLong, Id1Subtype subtype, String challengeValue) {
        public Challenge {
            Objects.requireNonNull(packetUuid, "packetUuid");
            Objects.requireNonNull(subtype, "subtype");
        }
    }

    public record Context(UUID localUuid, long writerTime) {
        public Context {
            Objects.requireNonNull(localUuid, "localUuid");
        }
    }

    public record BuiltPacket(byte[] preCrypto, byte[] wire, int layout) {
        public BuiltPacket {
            preCrypto = preCrypto.clone();
            wire = wire.clone();
        }

        @Override
        public byte[] preCrypto() {
            return preCrypto.clone();
        }

        @Override
        public byte[] wire() {
            return wire.clone();
        }
    }

    public record ModEvidence(String moduleName, String path, String digest) {
        public ModEvidence {
            Objects.requireNonNull(moduleName, "moduleName");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(digest, "digest");
        }
    }

    public record SprintEnvironment(
        List<ModEvidence> loadedMods,
        String userDirectory,
        String javaHome,
        Object cpuInfo,
        Object computerSystemInfo,
        Object networkInterfaces,
        Object diskStores,
        Object accountTraces,
        Object userProperties,
        List<String> discoveredJars,
        String source,
        String hwidSource,
        String hwidProfile,
        boolean syntheticHwid,
        String syntheticHwidId,
        int syntheticHwidHistoryCount,
        Map<String, String> discoveredJarDigests
    ) {
        public SprintEnvironment {
            loadedMods = List.copyOf(loadedMods);
            discoveredJars = List.copyOf(discoveredJars);
            discoveredJarDigests = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(discoveredJarDigests, "discoveredJarDigests"))
            );
            userDirectory = Objects.requireNonNull(userDirectory, "userDirectory");
            javaHome = Objects.requireNonNull(javaHome, "javaHome");
            source = source == null ? "" : source;
            hwidSource = hwidSource == null ? "" : hwidSource;
            hwidProfile = hwidProfile == null ? "" : hwidProfile;
            syntheticHwidId = syntheticHwidId == null ? "" : syntheticHwidId;
        }

        public SprintEnvironment(
            List<ModEvidence> loadedMods,
            String userDirectory,
            String javaHome,
            Object cpuInfo,
            Object computerSystemInfo,
            Object networkInterfaces,
            Object diskStores,
            Object accountTraces,
            Object userProperties,
            List<String> discoveredJars,
            String source,
            String hwidSource,
            String hwidProfile,
            boolean syntheticHwid,
            String syntheticHwidId,
            int syntheticHwidHistoryCount
        ) {
            this(
                loadedMods,
                userDirectory,
                javaHome,
                cpuInfo,
                computerSystemInfo,
                networkInterfaces,
                diskStores,
                accountTraces,
                userProperties,
                discoveredJars,
                source,
                hwidSource,
                hwidProfile,
                syntheticHwid,
                syntheticHwidId,
                syntheticHwidHistoryCount,
                Map.of()
            );
        }
    }

    public record SneakEvidence(int stateCode, List<String> values) {
        public SneakEvidence {
            values = List.copyOf(values);
        }
    }

    public record SwimEvidence(int evidenceKeyCount, Map<String, String> valuesByKey) {
        public SwimEvidence {
            valuesByKey = Map.copyOf(valuesByKey);
        }
    }

    public interface Id1SignatureProvider {
        boolean available();
        String digestPathLike(String path);
        String signString(String value);
    }

    public interface Id1CryptoTransform {
        boolean available();
        byte[] transform(byte[] preCrypto);
    }

    public interface EvidenceSampler {
        List<String> sample(List<String> values, int limit);

        static EvidenceSampler preserveOrder() {
            return (values, limit) -> List.copyOf(values.subList(0, Math.min(values.size(), limit)));
        }
    }

    public interface AttackValueProvider {
        Object derive(String challengeValue);
    }
}
