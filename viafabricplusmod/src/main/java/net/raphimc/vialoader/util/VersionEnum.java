/*
 * Compatibility copy of ViaLoader 2.2.9's VersionEnum.
 *
 * ViaLoader 2.2.13 moved the version list to ProtocolVersionList.  The
 * 1.20.1 ViaFabricPlus UI still intentionally uses VersionEnum, so this
 * fork keeps that API locally and adds the 1.20.5/1.20.6 protocol range.
 */
package net.raphimc.vialoader.util;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The version model consumed by the original ViaFabricPlus 2.8 UI and
 * settings.  Keep the names and ordering compatible with ViaLoader 2.2.9.
 */
public enum VersionEnum {
    c0_0_15a_1(getViaLegacyProtocol("c0_0_15a_1")),
    c0_0_16a_02(getViaLegacyProtocol("c0_0_16a_02")),
    c0_0_18a_02(getViaLegacyProtocol("c0_0_18a_02")),
    c0_0_19a_06(getViaLegacyProtocol("c0_0_19a_06")),
    c0_0_20ac0_27(getViaLegacyProtocol("c0_0_20ac0_27")),
    c0_30cpe(getViaLegacyProtocol("c0_30cpe")),
    c0_28toc0_30(getViaLegacyProtocol("c0_28toc0_30")),
    a1_0_15(getViaLegacyProtocol("a1_0_15")),
    a1_0_16toa1_0_16_2(getViaLegacyProtocol("a1_0_16toa1_0_16_2")),
    a1_0_17toa1_0_17_4(getViaLegacyProtocol("a1_0_17toa1_0_17_4")),
    a1_1_0toa1_1_2_1(getViaLegacyProtocol("a1_1_0toa1_1_2_1")),
    a1_2_0toa1_2_1_1(getViaLegacyProtocol("a1_2_0toa1_2_1_1")),
    a1_2_2(getViaLegacyProtocol("a1_2_2")),
    a1_2_3toa1_2_3_4(getViaLegacyProtocol("a1_2_3toa1_2_3_4")),
    a1_2_3_5toa1_2_6(getViaLegacyProtocol("a1_2_3_5toa1_2_6")),
    b1_0tob1_1_1(getViaLegacyProtocol("b1_0tob1_1_1")),
    b1_1_2(getViaLegacyProtocol("b1_1_2")),
    b1_2_0tob1_2_2(getViaLegacyProtocol("b1_2_0tob1_2_2")),
    b1_3tob1_3_1(getViaLegacyProtocol("b1_3tob1_3_1")),
    b1_4tob1_4_1(getViaLegacyProtocol("b1_4tob1_4_1")),
    b1_5tob1_5_2(getViaLegacyProtocol("b1_5tob1_5_2")),
    b1_6tob1_6_6(getViaLegacyProtocol("b1_6tob1_6_6")),
    b1_7tob1_7_3(getViaLegacyProtocol("b1_7tob1_7_3")),
    b1_8tob1_8_1(getViaLegacyProtocol("b1_8tob1_8_1")),
    r1_0_0tor1_0_1(getViaLegacyProtocol("r1_0_0tor1_0_1")),
    r1_1(getViaLegacyProtocol("r1_1")),
    r1_2_1tor1_2_3(getViaLegacyProtocol("r1_2_1tor1_2_3")),
    r1_2_4tor1_2_5(getViaLegacyProtocol("r1_2_4tor1_2_5")),
    r1_3_1tor1_3_2(getViaLegacyProtocol("r1_3_1tor1_3_2")),
    r1_4_2(getViaLegacyProtocol("r1_4_2")),
    r1_4_4tor1_4_5(getViaLegacyProtocol("r1_4_4tor1_4_5")),
    r1_4_6tor1_4_7(getViaLegacyProtocol("r1_4_6tor1_4_7")),
    r1_5tor1_5_1(getViaLegacyProtocol("r1_5tor1_5_1")),
    r1_5_2(getViaLegacyProtocol("r1_5_2")),
    r1_6_1(getViaLegacyProtocol("r1_6_1")),
    r1_6_2(getViaLegacyProtocol("r1_6_2")),
    r1_6_4(getViaLegacyProtocol("r1_6_4")),
    r1_7_2tor1_7_5(ProtocolVersion.v1_7_1),
    r1_7_6tor1_7_10(ProtocolVersion.v1_7_6),
    r1_8(ProtocolVersion.v1_8),
    r1_9(ProtocolVersion.v1_9),
    r1_9_1(ProtocolVersion.v1_9_1),
    r1_9_2(ProtocolVersion.v1_9_2),
    r1_9_3tor1_9_4(ProtocolVersion.v1_9_3),
    r1_10(ProtocolVersion.v1_10),
    r1_11(ProtocolVersion.v1_11),
    r1_11_1to1_11_2(ProtocolVersion.v1_11_1),
    r1_12(ProtocolVersion.v1_12),
    r1_12_1(ProtocolVersion.v1_12_1),
    r1_12_2(ProtocolVersion.v1_12_2),
    r1_13(ProtocolVersion.v1_13),
    r1_13_1(ProtocolVersion.v1_13_1),
    r1_13_2(ProtocolVersion.v1_13_2),
    s3d_shareware(getViaAprilFoolsProtocol("s3d_shareware")),
    r1_14(ProtocolVersion.v1_14),
    r1_14_1(ProtocolVersion.v1_14_1),
    r1_14_2(ProtocolVersion.v1_14_2),
    r1_14_3(ProtocolVersion.v1_14_3),
    r1_14_4(ProtocolVersion.v1_14_4),
    r1_15(ProtocolVersion.v1_15),
    r1_15_1(ProtocolVersion.v1_15_1),
    r1_15_2(ProtocolVersion.v1_15_2),
    s20w14infinite(getViaAprilFoolsProtocol("s20w14infinite")),
    r1_16(ProtocolVersion.v1_16),
    r1_16_1(ProtocolVersion.v1_16_1),
    sCombatTest8c(getViaAprilFoolsProtocol("sCombatTest8c")),
    r1_16_2(ProtocolVersion.v1_16_2),
    r1_16_3(ProtocolVersion.v1_16_3),
    r1_16_4tor1_16_5(ProtocolVersion.v1_16_4),
    r1_17(ProtocolVersion.v1_17),
    r1_17_1(ProtocolVersion.v1_17_1),
    r1_18tor1_18_1(ProtocolVersion.v1_18),
    r1_18_2(ProtocolVersion.v1_18_2),
    r1_19(ProtocolVersion.v1_19),
    r1_19_1tor1_19_2(ProtocolVersion.v1_19_1),
    r1_19_3(ProtocolVersion.v1_19_3),
    r1_19_4(ProtocolVersion.v1_19_4),
    r1_20tor1_20_1(ProtocolVersion.v1_20),
    bedrockLatest(getViaBedrockProtocol("bedrockLatest")),
    r1_20_2(ProtocolVersion.v1_20_2),
    /**
     * Minecraft 1.20.5 and 1.20.6 both use protocol 766.
     */
    r1_20_5tor1_20_6(ProtocolVersion.v1_20_5),
    UNKNOWN(ProtocolVersion.unknown);

    private static final Map<ProtocolVersion, VersionEnum> VERSION_REGISTRY;
    public static final List<VersionEnum> SORTED_VERSIONS;
    public static final List<VersionEnum> OFFICIAL_SUPPORTED_PROTOCOLS;
    private final ProtocolVersion protocolVersion;

    public static VersionEnum fromProtocolVersion(final ProtocolVersion protocolVersion) {
        if (!protocolVersion.isKnown()) {
            return UNKNOWN;
        }
        return VERSION_REGISTRY.getOrDefault(protocolVersion, UNKNOWN);
    }

    public static VersionEnum fromProtocolId(final int protocolId) {
        return fromProtocolVersion(ProtocolVersion.getProtocol(protocolId));
    }

    public static VersionEnum fromUserConnection(final UserConnection userConnection) {
        return fromUserConnection(userConnection, true);
    }

    public static VersionEnum fromUserConnection(final UserConnection userConnection, final boolean serverProtocol) {
        return fromProtocolId(serverProtocol
                ? userConnection.getProtocolInfo().getServerProtocolVersion()
                : userConnection.getProtocolInfo().getProtocolVersion());
    }

    public static Collection<VersionEnum> getAllVersions() {
        return VERSION_REGISTRY.values();
    }

    private static ProtocolVersion getViaLegacyProtocol(final String name) {
        try {
            return (ProtocolVersion) Class.forName("net.raphimc.vialegacy.api.LegacyProtocolVersion")
                    .getField(name).get(null);
        } catch (Throwable ignored) {
            return ProtocolVersion.unknown;
        }
    }

    private static ProtocolVersion getViaAprilFoolsProtocol(final String name) {
        try {
            return (ProtocolVersion) Class.forName("net.raphimc.viaaprilfools.api.AprilFoolsProtocolVersion")
                    .getField(name).get(null);
        } catch (Throwable ignored) {
            return ProtocolVersion.unknown;
        }
    }

    private static ProtocolVersion getViaBedrockProtocol(final String name) {
        try {
            return (ProtocolVersion) Class.forName("net.raphimc.viabedrock.api.BedrockProtocolVersion")
                    .getField(name).get(null);
        } catch (Throwable ignored) {
            return ProtocolVersion.unknown;
        }
    }

    private VersionEnum(final ProtocolVersion protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public ProtocolVersion getProtocol() {
        return protocolVersion;
    }

    public String getName() {
        return protocolVersion.getName();
    }

    public int getVersion() {
        return protocolVersion.getVersion();
    }

    public int getOriginalVersion() {
        return protocolVersion.getOriginalVersion();
    }

    public boolean isOlderThan(final VersionEnum other) {
        return ordinal() < other.ordinal();
    }

    public boolean isOlderThanOrEqualTo(final VersionEnum other) {
        return ordinal() <= other.ordinal();
    }

    public boolean isNewerThan(final VersionEnum other) {
        return ordinal() > other.ordinal();
    }

    public boolean isNewerThanOrEqualTo(final VersionEnum other) {
        return ordinal() >= other.ordinal();
    }

    public boolean isBetweenInclusive(final VersionEnum min, final VersionEnum max) {
        return isNewerThanOrEqualTo(min) && isOlderThanOrEqualTo(max);
    }

    public boolean isBetweenExclusive(final VersionEnum min, final VersionEnum max) {
        return isNewerThan(min) && isOlderThan(max);
    }

    static {
        VERSION_REGISTRY = new HashMap<>();
        SORTED_VERSIONS = new ArrayList<>();
        OFFICIAL_SUPPORTED_PROTOCOLS = new ArrayList<>();

        for (final VersionEnum version : values()) {
            if (version.protocolVersion.isKnown()) {
                VERSION_REGISTRY.put(version.protocolVersion, version);
            }
        }
        for (final VersionEnum version : getAllVersions()) {
            if (version.isNewerThan(r1_6_4)
                    && version != s3d_shareware
                    && version != s20w14infinite
                    && version != sCombatTest8c
                    && version != bedrockLatest) {
                OFFICIAL_SUPPORTED_PROTOCOLS.add(version);
            }
        }

        SORTED_VERSIONS.add(r1_20_5tor1_20_6);
        SORTED_VERSIONS.add(r1_20_2);
        SORTED_VERSIONS.add(r1_20tor1_20_1);
        SORTED_VERSIONS.add(r1_19_4);
        SORTED_VERSIONS.add(r1_19_3);
        SORTED_VERSIONS.add(r1_19_1tor1_19_2);
        SORTED_VERSIONS.add(r1_19);
        SORTED_VERSIONS.add(r1_18_2);
        SORTED_VERSIONS.add(r1_18tor1_18_1);
        SORTED_VERSIONS.add(r1_17_1);
        SORTED_VERSIONS.add(r1_17);
        SORTED_VERSIONS.add(r1_16_4tor1_16_5);
        SORTED_VERSIONS.add(r1_16_3);
        SORTED_VERSIONS.add(r1_16_2);
        SORTED_VERSIONS.add(r1_16_1);
        SORTED_VERSIONS.add(r1_16);
        SORTED_VERSIONS.add(r1_15_2);
        SORTED_VERSIONS.add(r1_15_1);
        SORTED_VERSIONS.add(r1_15);
        SORTED_VERSIONS.add(r1_14_4);
        SORTED_VERSIONS.add(r1_14_3);
        SORTED_VERSIONS.add(r1_14_2);
        SORTED_VERSIONS.add(r1_14_1);
        SORTED_VERSIONS.add(r1_14);
        SORTED_VERSIONS.add(r1_13_2);
        SORTED_VERSIONS.add(r1_13_1);
        SORTED_VERSIONS.add(r1_13);
        SORTED_VERSIONS.add(r1_12_2);
        SORTED_VERSIONS.add(r1_12_1);
        SORTED_VERSIONS.add(r1_12);
        SORTED_VERSIONS.add(r1_11_1to1_11_2);
        SORTED_VERSIONS.add(r1_11);
        SORTED_VERSIONS.add(r1_10);
        SORTED_VERSIONS.add(r1_9_3tor1_9_4);
        SORTED_VERSIONS.add(r1_9_2);
        SORTED_VERSIONS.add(r1_9_1);
        SORTED_VERSIONS.add(r1_9);
        SORTED_VERSIONS.add(r1_8);
        SORTED_VERSIONS.add(r1_7_6tor1_7_10);
        SORTED_VERSIONS.add(r1_7_2tor1_7_5);
        SORTED_VERSIONS.add(r1_6_4);
        SORTED_VERSIONS.add(r1_6_2);
        SORTED_VERSIONS.add(r1_6_1);
        SORTED_VERSIONS.add(r1_5_2);
        SORTED_VERSIONS.add(r1_5tor1_5_1);
        SORTED_VERSIONS.add(r1_4_6tor1_4_7);
        SORTED_VERSIONS.add(r1_4_4tor1_4_5);
        SORTED_VERSIONS.add(r1_4_2);
        SORTED_VERSIONS.add(r1_3_1tor1_3_2);
        SORTED_VERSIONS.add(r1_2_4tor1_2_5);
        SORTED_VERSIONS.add(r1_2_1tor1_2_3);
        SORTED_VERSIONS.add(r1_1);
        SORTED_VERSIONS.add(r1_0_0tor1_0_1);
        SORTED_VERSIONS.add(b1_8tob1_8_1);
        SORTED_VERSIONS.add(b1_7tob1_7_3);
        SORTED_VERSIONS.add(b1_6tob1_6_6);
        SORTED_VERSIONS.add(b1_5tob1_5_2);
        SORTED_VERSIONS.add(b1_4tob1_4_1);
        SORTED_VERSIONS.add(b1_3tob1_3_1);
        SORTED_VERSIONS.add(b1_2_0tob1_2_2);
        SORTED_VERSIONS.add(b1_1_2);
        SORTED_VERSIONS.add(b1_0tob1_1_1);
        SORTED_VERSIONS.add(a1_2_3_5toa1_2_6);
        SORTED_VERSIONS.add(a1_2_3toa1_2_3_4);
        SORTED_VERSIONS.add(a1_2_2);
        SORTED_VERSIONS.add(a1_2_0toa1_2_1_1);
        SORTED_VERSIONS.add(a1_1_0toa1_1_2_1);
        SORTED_VERSIONS.add(a1_0_17toa1_0_17_4);
        SORTED_VERSIONS.add(a1_0_16toa1_0_16_2);
        SORTED_VERSIONS.add(a1_0_15);
        SORTED_VERSIONS.add(c0_28toc0_30);
        SORTED_VERSIONS.add(c0_0_20ac0_27);
        SORTED_VERSIONS.add(c0_0_19a_06);
        SORTED_VERSIONS.add(c0_0_18a_02);
        SORTED_VERSIONS.add(c0_0_16a_02);
        SORTED_VERSIONS.add(c0_0_15a_1);
        SORTED_VERSIONS.add(bedrockLatest);
        SORTED_VERSIONS.add(sCombatTest8c);
        SORTED_VERSIONS.add(s20w14infinite);
        SORTED_VERSIONS.add(s3d_shareware);
        SORTED_VERSIONS.add(c0_30cpe);
        SORTED_VERSIONS.removeIf(version -> !version.protocolVersion.isKnown());
    }
}
