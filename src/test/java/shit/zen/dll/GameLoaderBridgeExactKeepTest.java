package shit.zen.dll;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

final class GameLoaderBridgeExactKeepTest {
    private static final String EXACT_DOTTED = "com.heypixel.heypixelmod.SyncToken";
    private static final String EXACT_INTERNAL = "com/heypixel/heypixelmod/SyncToken";
    private static final String CALLER_DOTTED = "shit.zen.dll.SyntheticExactCaller";
    private static final String CALLER_INTERNAL = "shit/zen/dll/SyntheticExactCaller";

    @Test
    void runtimeRelocationKeepsExactOwnerBytesAndCallsiteWhileRenamingOwnedCaller()
            throws Exception {
        byte[] exactBytes = readResource(EXACT_INTERNAL + ".class");
        byte[] callerBytes = syntheticCaller();

        Map<String, String> typeMap = GameLoaderBridge.buildRuntimeTypeMap(
                Set.of(EXACT_DOTTED, CALLER_DOTTED),
                "shit/zen/dll/UnrelatedSelf");
        assertFalse(typeMap.containsKey(EXACT_INTERNAL));
        assertTrue(typeMap.containsKey(CALLER_INTERNAL));

        LinkedHashMap<String, byte[]> pending = new LinkedHashMap<>();
        pending.put(EXACT_DOTTED, exactBytes);
        pending.put(CALLER_DOTTED, callerBytes);
        LinkedHashMap<String, byte[]> relocated =
                GameLoaderBridge.applyRuntimeObfuscation(pending, typeMap);

        assertSame(exactBytes, relocated.get(EXACT_DOTTED));
        assertArrayEquals(exactBytes, relocated.get(EXACT_DOTTED));
        String relocatedCaller = typeMap.get(CALLER_INTERNAL).replace('/', '.');
        assertTrue(relocated.containsKey(relocatedCaller));

        ClassNode caller = new ClassNode();
        new ClassReader(relocated.get(relocatedCaller)).accept(caller, 0);
        MethodInsnNode invocation = Arrays.stream(caller.methods.get(0).instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(Opcodes.INVOKESTATIC, invocation.getOpcode());
        assertEquals(EXACT_INTERNAL, invocation.owner);
        assertEquals("accept", invocation.name);
        assertEquals("(Ljava/lang/String;)V", invocation.desc);
        assertFalse(invocation.itf);
    }

    @Test
    void visibleExactClassRemovalTouchesOnlyThatPendingEntry() {
        LinkedHashMap<String, byte[]> pending = new LinkedHashMap<>();
        pending.put(EXACT_DOTTED, new byte[]{1});
        pending.put(CALLER_DOTTED, new byte[]{2});
        AtomicReference<String> checked = new AtomicReference<>();

        boolean removed = GameLoaderBridge.removeExactAbiClassIfVisible(
                pending,
                name -> {
                    checked.set(name);
                    return true;
                });

        assertTrue(removed);
        assertEquals(EXACT_DOTTED, checked.get());
        assertFalse(pending.containsKey(EXACT_DOTTED));
        assertArrayEquals(new byte[]{2}, pending.get(CALLER_DOTTED));

        assertFalse(GameLoaderBridge.removeExactAbiClassIfVisible(pending, name -> true));
        assertTrue(pending.containsKey(CALLER_DOTTED));
    }

    @Test
    void visibleExactClassMustPassRuntimeAbiValidation() {
        assertTrue(GameLoaderBridge.isExactSyncTokenAbiVisible(
                EXACT_DOTTED,
                GameLoaderBridgeExactKeepTest.class.getClassLoader()));
    }

    private static byte[] readResource(String name) throws Exception {
        try (InputStream stream = GameLoaderBridgeExactKeepTest.class.getClassLoader()
                .getResourceAsStream(name)) {
            assertNotNull(stream, "class bytes must be present: " + name);
            return stream.readAllBytes();
        }
    }

    private static byte[] syntheticCaller() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                CALLER_INTERNAL, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "call",
                "(Ljava/lang/String;)V",
                null,
                null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                EXACT_INTERNAL,
                "accept",
                "(Ljava/lang/String;)V",
                false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
