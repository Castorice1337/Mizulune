package asm.patchify.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class PatchAgentStartupModeTest {
    @Test
    void startupModeIsSharedAndPremainCannotBeDowngradedByLaterAttach() {
        Properties properties = System.getProperties();
        Object previousInstrumentation = properties.get(PatchAgent.INSTRUMENTATION_KEY);
        Object previousMode = properties.get(PatchAgent.STARTUP_MODE_KEY);
        Instrumentation instrumentation = instrumentationStub();
        try {
            properties.remove(PatchAgent.INSTRUMENTATION_KEY);
            properties.remove(PatchAgent.STARTUP_MODE_KEY);
            assertEquals(PatchAgent.StartupMode.NONE, PatchAgent.getStartupMode());

            PatchAgent.agentmain("", instrumentation);
            assertSame(instrumentation, PatchAgent.getInstrumentation());
            assertEquals(PatchAgent.StartupMode.AGENTMAIN, PatchAgent.getStartupMode());

            PatchAgent.premain("", instrumentation);
            assertEquals(PatchAgent.StartupMode.PREMAIN, PatchAgent.getStartupMode());

            PatchAgent.agentmain("", instrumentation);
            assertEquals(PatchAgent.StartupMode.PREMAIN, PatchAgent.getStartupMode());
        } finally {
            restore(properties, PatchAgent.INSTRUMENTATION_KEY, previousInstrumentation);
            restore(properties, PatchAgent.STARTUP_MODE_KEY, previousMode);
        }
    }

    @Test
    void unknownPersistedValueFailsClosedToNone() {
        Properties properties = System.getProperties();
        Object previousMode = properties.get(PatchAgent.STARTUP_MODE_KEY);
        try {
            properties.put(PatchAgent.STARTUP_MODE_KEY, "unexpected-mode");
            assertEquals(PatchAgent.StartupMode.NONE, PatchAgent.getStartupMode());
        } finally {
            restore(properties, PatchAgent.STARTUP_MODE_KEY, previousMode);
        }
    }

    @Test
    void premainEntryClassDoesNotLinkLog4jBeforeForgeClasspathIsReady() throws IOException {
        String resource = "/" + PatchAgent.class.getName().replace('.', '/') + ".class";
        byte[] classBytes;
        try (InputStream input = PatchAgent.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing class resource " + resource);
            classBytes = input.readAllBytes();
        }
        String constantPool = new String(classBytes, StandardCharsets.ISO_8859_1);
        assertFalse(constantPool.contains("org/apache/logging/log4j"));
    }

    private static Instrumentation instrumentationStub() {
        return (Instrumentation) Proxy.newProxyInstance(
            PatchAgentStartupModeTest.class.getClassLoader(),
            new Class<?>[]{Instrumentation.class},
            (proxy, method, args) -> {
                Class<?> returnType = method.getReturnType();
                if (returnType == boolean.class) return false;
                if (returnType == int.class) return 0;
                if (returnType == long.class) return 0L;
                return null;
            }
        );
    }

    private static void restore(Properties properties, String key, Object value) {
        if (value == null) {
            properties.remove(key);
        } else {
            properties.put(key, value);
        }
    }
}
