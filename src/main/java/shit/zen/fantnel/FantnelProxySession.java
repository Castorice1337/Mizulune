package shit.zen.fantnel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Owns the one active in-game FantNEL proxy and its protocol override. */
public final class FantnelProxySession {
    private static final Object LOCK = new Object();
    private static final FantnelClientService SERVICE = new FantnelClientService();
    private static FantnelClientService.Proxy activeProxy;

    static {
        SERVICE.host().addEventListener(FantnelProxySession::onHostEvent);
    }

    private FantnelProxySession() {
    }

    public static FantnelClientService.Proxy activeProxy() {
        synchronized (LOCK) {
            return activeProxy;
        }
    }

    public static void activate(FantnelClientService.Proxy proxy) {
        Objects.requireNonNull(proxy, "proxy");
        if (!ViaProtocolBridge.supports(766)) {
            stopDetached(proxy);
            throw new IllegalStateException("协议 Mod 未提供 Minecraft 1.20.5/1.20.6 (766) 支持");
        }

        try {
            ViaProtocolBridge.force(proxy.endpoint(), 766);
        } catch (RuntimeException error) {
            stopDetached(proxy);
            throw error;
        }
        FantnelClientService.Proxy previous;
        synchronized (LOCK) {
            previous = activeProxy;
            activeProxy = proxy;
        }
        if (previous != null && previous.id() != proxy.id()) {
            ViaProtocolBridge.clear(previous.endpoint());
            stopDetached(previous);
        }
    }

    public static CompletableFuture<Void> stopActive() {
        FantnelClientService.Proxy proxy;
        synchronized (LOCK) {
            proxy = activeProxy;
            activeProxy = null;
        }
        if (proxy == null) return CompletableFuture.completedFuture(null);
        ViaProtocolBridge.clear(proxy.endpoint());
        if (!SERVICE.host().isRunning()) return CompletableFuture.completedFuture(null);
        return SERVICE.stopProxy(proxy.id());
    }

    public static void onConnectionClosed(SocketAddress address) {
        FantnelClientService.Proxy proxy = activeProxy();
        if (proxy != null && isProxyAddress(proxy, address)) {
            stopActive().exceptionally(ignored -> null);
        }
    }

    public static void onPlayDisconnected() {
        if (activeProxy() != null) stopActive().exceptionally(ignored -> null);
    }

    static boolean isProxyAddress(FantnelClientService.Proxy proxy, SocketAddress address) {
        if (!(address instanceof InetSocketAddress inet) || inet.getPort() != proxy.localPort()) return false;
        String expected = normalizeHost(proxy.localAddress());
        String actual = normalizeHost(inet.getHostString());
        if (expected.equals(actual)) return true;
        InetAddress resolved = inet.getAddress();
        return isLoopback(expected) && resolved != null && resolved.isLoopbackAddress();
    }

    private static void onHostEvent(FantnelHostClient.HostEvent event) {
        if (event.name().equals("host.disconnected")) {
            clearActive(null);
            return;
        }
        if (!event.name().equals("proxy.stopped")) return;
        JsonElement data = event.data();
        if (data != null && data.isJsonObject()) {
            JsonObject object = data.getAsJsonObject();
            if (object.has("id") && object.get("id").isJsonPrimitive()) {
                try {
                    clearActive(object.get("id").getAsInt());
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private static void clearActive(Integer expectedId) {
        FantnelClientService.Proxy proxy;
        synchronized (LOCK) {
            proxy = activeProxy;
            if (proxy == null || (expectedId != null && proxy.id() != expectedId)) return;
            activeProxy = null;
        }
        ViaProtocolBridge.clear(proxy.endpoint());
    }

    private static void stopDetached(FantnelClientService.Proxy proxy) {
        if (SERVICE.host().isRunning()) {
            SERVICE.stopProxy(proxy.id()).exceptionally(ignored -> null);
        }
    }

    private static String normalizeHost(String value) {
        String host = Objects.toString(value, "").trim().toLowerCase(java.util.Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        return host;
    }

    private static boolean isLoopback(String host) {
        return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1")
            || host.equals("0:0:0:0:0:0:0:1");
    }
}
