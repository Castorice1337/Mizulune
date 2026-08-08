package asm.patchify.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Proxy;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class PremainBridgeTest {
    @Test
    void premainUsesOnlyTheCrossLoaderJdkContract() {
        Properties properties = System.getProperties();
        Object oldInstrumentation = properties.get(PremainBridge.INSTRUMENTATION_KEY);
        String oldMode = properties.getProperty(PremainBridge.STARTUP_MODE_KEY);
        Instrumentation instrumentation = (Instrumentation) Proxy.newProxyInstance(
            PremainBridge.class.getClassLoader(),
            new Class<?>[]{Instrumentation.class},
            (proxy, method, args) -> defaultValue(method.getReturnType())
        );
        try {
            properties.remove(PremainBridge.INSTRUMENTATION_KEY);
            properties.remove(PremainBridge.STARTUP_MODE_KEY);
            PremainBridge.premain(null, instrumentation);
            assertSame(instrumentation, properties.get(PremainBridge.INSTRUMENTATION_KEY));
            assertEquals("premain", properties.getProperty(PremainBridge.STARTUP_MODE_KEY));
        } finally {
            if (oldInstrumentation == null) {
                properties.remove(PremainBridge.INSTRUMENTATION_KEY);
            } else {
                properties.put(PremainBridge.INSTRUMENTATION_KEY, oldInstrumentation);
            }
            if (oldMode == null) {
                properties.remove(PremainBridge.STARTUP_MODE_KEY);
            } else {
                properties.setProperty(PremainBridge.STARTUP_MODE_KEY, oldMode);
            }
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
