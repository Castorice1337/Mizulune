package shit.zen.protocol.heypixel;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Resolves a trusted Fantnel loopback connection back to its signed HeyPixel target. */
public final class ProxyTargetResolver {
    private static final String DEFAULT_HEYPIXEL_HOSTS = "pc.bjdmc.net,*.bjdmc.net";
    private final ProtocolSessionProvider sessions;

    public ProxyTargetResolver(ProtocolSessionProvider sessions) {
        this.sessions = sessions;
    }

    public Optional<ResolvedTarget> resolve(String connectionAddress, String enabledHosts) {
        ConnectionEndpoint endpoint = parseConnectionEndpoint(connectionAddress);
        String connectionHost = endpoint.host();
        if (connectionHost.isBlank()) return Optional.empty();

        if (!isLoopback(connectionHost) && matchesEnabledHost(connectionHost, enabledHosts)) {
            return Optional.of(new ResolvedTarget(
                endpoint,
                connectionHost,
                endpoint.port(),
                false,
                sessions.loadValid(connectionHost)
            ));
        }

        if (!isLoopback(connectionHost)) return Optional.empty();
        Optional<ProtocolSessionSnapshot> session = sessions.loadValid(null)
            .filter(snapshot -> snapshot.version() >= 2)
            .filter(snapshot -> "fantnel".equalsIgnoreCase(snapshot.source()));
        if (session.isEmpty()) return Optional.empty();

        String targetHost = ProtocolSessionProvider.normalizeHost(session.get().serverAddress());
        boolean targetEnabled = matchesEnabledHost(targetHost, enabledHosts);
        boolean legacyLoopbackConfig = matchesEnabledHost(connectionHost, enabledHosts)
            && matchesEnabledHost(targetHost, DEFAULT_HEYPIXEL_HOSTS);
        if (!targetEnabled && !legacyLoopbackConfig) return Optional.empty();
        return Optional.of(new ResolvedTarget(
            endpoint,
            targetHost,
            session.get().serverPort(),
            true,
            session
        ));
    }

    static ConnectionEndpoint parseConnectionEndpoint(String connectionAddress) {
        String value = connectionAddress == null ? "" : connectionAddress.trim();
        if (value.isBlank()) return new ConnectionEndpoint("", "", -1);

        String host = value;
        int port = -1;
        if (value.startsWith("[")) {
            int closingBracket = value.indexOf(']');
            if (closingBracket > 0) {
                host = value.substring(1, closingBracket);
                if (closingBracket + 2 < value.length() && value.charAt(closingBracket + 1) == ':') {
                    port = parsePort(value.substring(closingBracket + 2));
                }
            }
        } else {
            int colon = value.lastIndexOf(':');
            if (colon > 0 && value.indexOf(':') == colon) {
                int parsedPort = parsePort(value.substring(colon + 1));
                if (parsedPort >= 0) {
                    host = value.substring(0, colon);
                    port = parsedPort;
                }
            }
        }

        String normalizedHost = ProtocolSessionProvider.normalizeHost(host);
        String normalizedAddress = port < 0
            ? normalizedHost
            : (normalizedHost.contains(":") ? "[" + normalizedHost + "]:" + port : normalizedHost + ":" + port);
        return new ConnectionEndpoint(normalizedAddress, normalizedHost, port);
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            return port >= 1 && port <= 65_535 ? port : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    static boolean matchesEnabledHost(String host, String enabledHosts) {
        if (host == null || host.isBlank() || enabledHosts == null || enabledHosts.isBlank()) return false;
        String normalized = ProtocolSessionProvider.normalizeHost(host);
        return Arrays.stream(enabledHosts.split("[,;\\s]+"))
            .map(ProtocolSessionProvider::normalizeHost)
            .filter(value -> !value.isBlank())
            .anyMatch(pattern -> pattern.startsWith("*.")
                ? normalized.equals(pattern.substring(2)) || normalized.endsWith(pattern.substring(1))
                : normalized.equals(pattern));
    }

    static boolean isLoopback(String host) {
        String normalized = ProtocolSessionProvider.normalizeHost(host).toLowerCase(Locale.ROOT);
        if (normalized.equals("localhost") || normalized.equals("::1")
            || normalized.equals("0:0:0:0:0:0:0:1")) {
            return true;
        }
        if (!normalized.startsWith("127.")) return false;
        String[] parts = normalized.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) return false;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }

    public record ConnectionEndpoint(String address, String host, int port) {
    }

    public record ResolvedTarget(
        ConnectionEndpoint connection,
        String targetHost,
        int targetPort,
        boolean proxied,
        Optional<ProtocolSessionSnapshot> session
    ) {
        public ResolvedTarget {
            if (connection == null) connection = new ConnectionEndpoint("", "", -1);
            session = session == null ? Optional.empty() : session;
        }

        public String connectionEndpoint() {
            return connection.address();
        }

        public String connectionHost() {
            return connection.host();
        }

        public int connectionPort() {
            return connection.port();
        }
    }
}
