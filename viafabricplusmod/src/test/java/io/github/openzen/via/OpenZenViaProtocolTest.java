package io.github.openzen.via;

import de.florianmichael.viafabricplus.protocolhack.ProtocolHack;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.raphimc.vialoader.util.VersionEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenZenViaProtocolTest {
    private static final String ENDPOINT = "127.0.0.1:25577";

    @AfterEach
    void clearForcedVersion() {
        OpenZenViaProtocol.clearForcedProtocol(ENDPOINT);
    }

    @Test
    void viaLoaderVersionTableContainsProtocol766() {
        assertTrue(OpenZenViaProtocol.supportsProtocol(763));
        assertTrue(OpenZenViaProtocol.supportsProtocol(766));
        assertFalse(OpenZenViaProtocol.supportsProtocol(Integer.MAX_VALUE));
        VersionEnum version = VersionEnum.fromProtocolId(766);
        assertSame(VersionEnum.r1_20_5tor1_20_6, version);
        assertSame(ProtocolVersion.v1_20_5, version.getProtocol());
        assertEquals(766, version.getVersion());
        assertSame(version, VersionEnum.SORTED_VERSIONS.get(0));
        assertTrue(VersionEnum.OFFICIAL_SUPPORTED_PROTOCOLS.contains(version));
    }

    @Test
    void fantnelForceUsesViaFabricPlusNativeForcedVersionMap() {
        OpenZenViaProtocol.forceProtocol(ENDPOINT, 766);

        VersionEnum forced = ProtocolHack.getForcedVersions()
            .get(new InetSocketAddress("127.0.0.1", 25577));
        assertEquals(766, forced.getVersion());

        OpenZenViaProtocol.clearForcedProtocol(ENDPOINT);
        assertTrue(ProtocolHack.getForcedVersions().isEmpty());
    }

    @Test
    void rejectsUnknownProtocolsAndMalformedEndpoints() {
        assertThrows(IllegalArgumentException.class,
            () -> OpenZenViaProtocol.forceProtocol(ENDPOINT, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,
            () -> OpenZenViaProtocol.forceProtocol("missing-port", 766));
    }
}
