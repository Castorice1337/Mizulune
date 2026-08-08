package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

final class Id114RuntimeLockBoundaryTest {
    private static final String OWNER = "shit/zen/protocol/heypixel/HeyPixelProtocolRuntime";

    @Test
    void deferredId114ActionRunsOnlyAfterIngressMonitorExit() throws Exception {
        ClassNode owner = readRuntime();
        MethodNode handle = owner.methods.stream()
            .filter(method -> method.name.equals("handle"))
            .filter(method -> method.desc.equals(
                "(Lnet/minecraft/network/protocol/game/ClientboundCustomPayloadPacket;"
                    + "Lnet/minecraft/network/Connection;)Z"))
            .findFirst()
            .orElseThrow();
        AbstractInsnNode[] instructions = handle.instructions.toArray();

        int prepareIndex = indexOfCall(instructions, OWNER, "handleMainChannel");
        int monitorExitIndex = indexOfOpcodeAfter(instructions, Opcodes.MONITOREXIT, prepareIndex);
        int deferredRunIndex = indexOfCallAfter(
            instructions, "java/lang/Runnable", "run", monitorExitIndex);

        assertTrue(prepareIndex >= 0, "ingress must prepare the deferred action while locked");
        assertTrue(monitorExitIndex > prepareIndex,
            "the ingress monitor must be released after preparation");
        assertTrue(deferredRunIndex > monitorExitIndex,
            "the deferred client work must run only after MONITOREXIT");
    }

    @Test
    void id114BranchReturnsADeferredActionInsteadOfEnqueuingInsideIngress() throws Exception {
        ClassNode owner = readRuntime();
        MethodNode mainChannel = owner.methods.stream()
            .filter(method -> method.name.equals("handleMainChannel"))
            .findFirst()
            .orElseThrow();
        assertTrue(mainChannel.desc.endsWith(")Ljava/lang/Runnable;"));
        assertTrue(Arrays.stream(mainChannel.instructions.toArray())
            .filter(MethodInsnNode.class::isInstance)
            .map(MethodInsnNode.class::cast)
            .anyMatch(call -> call.owner.equals(OWNER)
                && call.name.equals("prepareSyncTokenWork")
                && call.desc.endsWith(")Ljava/lang/Runnable;")));
    }

    private static ClassNode readRuntime() throws Exception {
        try (InputStream stream = Id114RuntimeLockBoundaryTest.class.getClassLoader()
            .getResourceAsStream(OWNER + ".class")) {
            assertNotNull(stream);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    private static int indexOfCall(
        AbstractInsnNode[] instructions,
        String owner,
        String name
    ) {
        return indexOfCallAfter(instructions, owner, name, -1);
    }

    private static int indexOfCallAfter(
        AbstractInsnNode[] instructions,
        String owner,
        String name,
        int after
    ) {
        for (int index = Math.max(0, after + 1); index < instructions.length; index++) {
            if (instructions[index] instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name)) {
                return index;
            }
        }
        return -1;
    }

    private static int indexOfOpcodeAfter(
        AbstractInsnNode[] instructions,
        int opcode,
        int after
    ) {
        for (int index = Math.max(0, after + 1); index < instructions.length; index++) {
            if (instructions[index].getOpcode() == opcode) return index;
        }
        return -1;
    }
}
