package io.github.openzen.via;

import de.florianmichael.viafabricplus.protocolhack.ProtocolHack;
import net.raphimc.vialoader.util.VersionEnum;

import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small integration API exposed by the OpenZen ViaFabricPlus build.
 *
 * <p>Protocol translation, selection UI and the Netty pipeline remain owned by
 * ViaFabricPlus.  Mizulune only uses this API to tell VFP that a FantNEL
 * loopback endpoint represents a protocol 766 server.</p>
 */
public final class OpenZenViaProtocol {
    private static final Map<String, InetSocketAddress> FORCED_ENDPOINTS = new ConcurrentHashMap<>();

    private OpenZenViaProtocol() {
    }

    public static boolean supportsProtocol(int protocol) {
        VersionEnum version = VersionEnum.fromProtocolId(protocol);
        return version != null && version != VersionEnum.UNKNOWN;
    }

    public static void forceProtocol(String endpoint, int protocol) {
        VersionEnum version = requireVersion(protocol);
        String key = normalizeEndpoint(endpoint);
        InetSocketAddress address = parseEndpoint(key);
        InetSocketAddress previous = FORCED_ENDPOINTS.put(key, address);
        if (previous != null) ProtocolHack.getForcedVersions().remove(previous);
        ProtocolHack.getForcedVersions().put(address, version);
    }

    public static void clearForcedProtocol(String endpoint) {
        InetSocketAddress address = FORCED_ENDPOINTS.remove(normalizeEndpoint(endpoint));
        if (address != null) ProtocolHack.getForcedVersions().remove(address);
    }

    public static void setDefaultProtocol(int protocol) {
        ProtocolHack.setTargetVersion(requireVersion(protocol));
    }

    public static int activeProtocol() {
        return ProtocolHack.getTargetVersion().getVersion();
    }

    private static VersionEnum requireVersion(int protocol) {
        VersionEnum version = VersionEnum.fromProtocolId(protocol);
        if (version == null || version == VersionEnum.UNKNOWN) {
            throw new IllegalArgumentException("Unsupported ViaFabricPlus protocol: " + protocol);
        }
        return version;
    }

    private static InetSocketAddress parseEndpoint(String endpoint) {
        final String host;
        final String portText;
        if (endpoint.startsWith("[")) {
            int close = endpoint.indexOf(']');
            if (close <= 1 || close + 2 > endpoint.length() || endpoint.charAt(close + 1) != ':') {
                throw new IllegalArgumentException("Invalid endpoint: " + endpoint);
            }
            host = endpoint.substring(1, close);
            portText = endpoint.substring(close + 2);
        } else {
            int separator = endpoint.lastIndexOf(':');
            if (separator <= 0 || separator == endpoint.length() - 1) {
                throw new IllegalArgumentException("Invalid endpoint: " + endpoint);
            }
            host = endpoint.substring(0, separator);
            portText = endpoint.substring(separator + 1);
        }

        try {
            int port = Integer.parseInt(portText);
            if (port < 1 || port > 65535) throw new NumberFormatException("port out of range");
            return new InetSocketAddress(host, port);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid endpoint port: " + endpoint, error);
        }
    }

    static String normalizeEndpoint(String endpoint) {
        if (endpoint == null) throw new IllegalArgumentException("endpoint is null");
        String value = endpoint.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) throw new IllegalArgumentException("endpoint is blank");
        return value;
    }
}
