package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

final class Id3StaticParityTest {
    @Test
    void usesCachedClockForClicksWindowAndCooldownButWallClockForWireTimestamp()
        throws Exception {
        ClassNode runtime = readClass(HeyPixelProtocolRuntime.class);
        MethodNode click = method(runtime, "recordMouseButton", "(II)V");
        MethodNode tick = method(runtime, "tickId3Phase", "()V");

        assertEquals(1, calls(click, Id2CachedClock.class, "currentTimeMillis"));
        assertEquals(0, calls(click, System.class, "currentTimeMillis"));
        assertEquals(1, calls(tick, Id2CachedClock.class, "currentTimeMillis"));
        assertEquals(1, calls(tick, System.class, "currentTimeMillis"));
    }

    private static long calls(MethodNode method, Class<?> owner, String name) {
        return Arrays.stream(method.instructions.toArray())
            .filter(MethodInsnNode.class::isInstance)
            .map(MethodInsnNode.class::cast)
            .filter(invocation -> Type.getInternalName(owner).equals(invocation.owner)
                && name.equals(invocation.name))
            .count();
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream()
            .filter(method -> name.equals(method.name) && descriptor.equals(method.desc))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing method " + name + descriptor));
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
