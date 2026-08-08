package com.heypixel.heypixelmod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

final class SyncTokenExactAbiTest {
    private static final String INTERNAL_NAME = "com/heypixel/heypixelmod/SyncToken";

    @Test
    void classFileMatchesTheOfficialExactAbiWithoutInvokingIt() throws Exception {
        ClassNode node = readClassBytes();

        assertEquals(Opcodes.V17, node.version);
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, node.access);
        assertEquals(INTERNAL_NAME, node.name);
        assertEquals("java/lang/Object", node.superName);
        assertTrue(node.interfaces.isEmpty());
        assertTrue(node.fields.isEmpty());
        assertEquals(3, node.methods.size());

        Map<String, MethodNode> methods = node.methods.stream().collect(Collectors.toMap(
                method -> method.name + method.desc,
                Function.identity()));
        assertEquals(Set.of("<init>()V", "accept(Ljava/lang/String;)V", "logout()V"),
                methods.keySet());

        assertEquals(Opcodes.ACC_PUBLIC, methods.get("<init>()V").access);
        MethodNode accept = methods.get("accept(Ljava/lang/String;)V");
        MethodNode logout = methods.get("logout()V");
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, accept.access);
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, logout.access);
        assertOnlyReturn(accept);
        assertOnlyReturn(logout);
    }

    private static ClassNode readClassBytes() throws Exception {
        String resource = INTERNAL_NAME + ".class";
        try (InputStream stream = SyncTokenExactAbiTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull(stream, "exact ABI class bytes must be present on the test classpath");
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    private static void assertOnlyReturn(MethodNode method) {
        List<Integer> opcodes = Arrays.stream(method.instructions.toArray())
                .mapToInt(AbstractInsnNode::getOpcode)
                .filter(opcode -> opcode >= 0)
                .boxed()
                .toList();
        assertEquals(List.of(Opcodes.RETURN), opcodes);
        assertTrue(method.tryCatchBlocks.isEmpty());
        assertFalse((method.access & (Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT)) != 0);
    }
}
