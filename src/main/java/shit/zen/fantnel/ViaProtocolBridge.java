package shit.zen.fantnel;

import java.lang.reflect.Method;

/** Keeps Mizulune independent from the separately distributed ViaFabricPlus fork. */
public final class ViaProtocolBridge {
    private static final String API_CLASS = "io.github.openzen.via.OpenZenViaProtocol";

    private ViaProtocolBridge() {
    }

    public static boolean supports(int protocol) {
        try {
            return (boolean) api().getMethod("supportsProtocol", int.class).invoke(null, protocol);
        } catch (ReflectiveOperationException error) {
            return false;
        }
    }

    public static void force(String endpoint, int protocol) {
        try {
            Method method = api().getMethod("forceProtocol", String.class, int.class);
            method.invoke(null, endpoint, protocol);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("ViaFabricPlus OpenZen 766 is unavailable or incompatible", error);
        }
    }

    public static void clear(String endpoint) {
        try {
            api().getMethod("clearForcedProtocol", String.class).invoke(null, endpoint);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Class<?> api() throws ClassNotFoundException {
        return Class.forName(API_CLASS, true, ViaProtocolBridge.class.getClassLoader());
    }
}
