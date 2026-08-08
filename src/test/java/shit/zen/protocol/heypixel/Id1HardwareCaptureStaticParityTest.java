package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

final class Id1HardwareCaptureStaticParityTest {
    @Test
    void productionCaptureSamplesNetworkThenDiskThenCpuThenComputerSystem() throws Exception {
        MethodNode capture = method(readClass(Id1EnvironmentCollector.class), "collectHardware");
        int network = callIndex(capture, "getNetworkIFs");
        int disk = callIndex(capture, "getDiskStores");
        int cpu = callIndex(capture, "getProcessor");
        int computerSystem = callIndex(capture, "getComputerSystem");

        assertTrue(network >= 0);
        assertTrue(network < disk);
        assertTrue(disk < cpu);
        assertTrue(cpu < computerSystem);
    }

    private static int callIndex(MethodNode method, String name) {
        return Arrays.stream(method.instructions.toArray())
            .filter(MethodInsnNode.class::isInstance)
            .map(MethodInsnNode.class::cast)
            .filter(call -> name.equals(call.name))
            .mapToInt(call -> method.instructions.indexOf(call))
            .findFirst()
            .orElse(-1);
    }

    private static MethodNode method(ClassNode owner, String name) {
        return owner.methods.stream()
            .filter(method -> name.equals(method.name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing method " + name));
    }

    private static ClassNode readClass(Class<?> type) throws Exception {
        String resource = type.getName().replace('.', '/') + ".class";
        try (InputStream stream = type.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }
}
