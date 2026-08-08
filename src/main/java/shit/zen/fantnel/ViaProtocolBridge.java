package shit.zen.fantnel;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** Reflection boundary over the stable upstream ViaFabricPlus public API. */
public final class ViaProtocolBridge {
    private static final String API_CLASS = "com.viaversion.viafabricplus.ViaFabricPlus";
    private static final String PROTOCOL_CLASS =
        "com.viaversion.viaversion.api.protocol.version.ProtocolVersion";
    private static final Object LOCK = new Object();
    private static final Map<String, Object> PREVIOUS_TARGETS = new HashMap<>();
    private static String activeEndpoint;

    private ViaProtocolBridge() {
    }

    public static boolean supports(int protocol) {
        try {
            Object target = protocolClass().getMethod("getProtocol", int.class).invoke(null, protocol);
            return target != null && (boolean) protocolClass().getMethod("isRegistered", int.class)
                .invoke(null, protocol);
        } catch (ReflectiveOperationException error) {
            return false;
        }
    }

    public static void force(String endpoint, int protocol) {
        synchronized (LOCK) {
            try {
                if (activeEndpoint != null && !activeEndpoint.equals(endpoint)) {
                    throw new IllegalStateException(
                        "ViaFabricPlus target is already leased by " + activeEndpoint);
                }
                Object implementation = implementation();
                Object target = protocolClass().getMethod("getProtocol", int.class).invoke(null, protocol);
                if (target == null) {
                    throw new IllegalStateException("ViaFabricPlus does not register protocol " + protocol);
                }
                PREVIOUS_TARGETS.computeIfAbsent(endpoint, ignored -> currentTarget(implementation));
                setTarget(implementation, target);
                activeEndpoint = endpoint;
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(
                    "ViaFabricPlus public API is unavailable or incompatible", error);
            }
        }
    }

    public static void clear(String endpoint) {
        synchronized (LOCK) {
            Object previous = PREVIOUS_TARGETS.remove(endpoint);
            if (previous == null) return;
            try {
                setTarget(implementation(), previous);
            } catch (ReflectiveOperationException ignored) {
                // Disconnect cleanup must remain best-effort if VFP is unloading.
            } finally {
                if (endpoint.equals(activeEndpoint)) activeEndpoint = null;
            }
        }
    }

    private static Class<?> api() throws ClassNotFoundException {
        return Class.forName(API_CLASS, true, ViaProtocolBridge.class.getClassLoader());
    }

    private static Class<?> protocolClass() throws ClassNotFoundException {
        return Class.forName(PROTOCOL_CLASS, true, ViaProtocolBridge.class.getClassLoader());
    }

    private static Object implementation() throws ReflectiveOperationException {
        Object implementation = api().getMethod("getImpl").invoke(null);
        if (implementation == null) throw new IllegalStateException("ViaFabricPlus is not initialized");
        return implementation;
    }

    private static Object currentTarget(Object implementation) {
        try {
            return implementation.getClass().getMethod("getTargetVersion").invoke(implementation);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot read ViaFabricPlus target version", error);
        }
    }

    private static void setTarget(Object implementation, Object target)
        throws ReflectiveOperationException {
        Method method = implementation.getClass().getMethod(
            "setTargetVersion", protocolClass(), boolean.class);
        method.invoke(implementation, target, true);
    }
}
