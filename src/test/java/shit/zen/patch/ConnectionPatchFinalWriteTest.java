package shit.zen.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import asm.patchify.loader.PatchTransformer;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import net.minecraft.network.Connection;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.util.CheckClassAdapter;
import shit.zen.ZenClient;
import shit.zen.hook.ConnectionHookCallbacks;
import shit.zen.modules.impl.world.Protocol;

final class ConnectionPatchFinalWriteTest {
    private static final String DO_SEND_PACKET_DESC =
            "(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;"
                    + "Lnet/minecraft/network/ConnectionProtocol;Lnet/minecraft/network/ConnectionProtocol;)V";
    private static final String VIAFORGE_COMMON =
            "com/viaversion/viaforge/common/ViaForgeCommon";
    private static final String VIAFORGE_EXTENDED_NETWORK_MANAGER =
            "com/viaversion/viaforge/common/extended/ExtendedNetworkManager";
    private static final String VIAFORGE_PROTOCOL_VERSION =
            "Lcom/viaversion/viaversion/api/protocol/version/ProtocolVersion;";

    @Test
    void staticHooksRouteEarlyIngressAndFinalWriteIntoProtocolRuntime() throws Exception {
        ClassNode patch = readClass(ConnectionPatch.class);
        MethodNode receiveAdapter = findMethod(
            patch,
            "onPacketReceive",
            "(Lnet/minecraft/network/Connection;Lnet/minecraft/network/protocol/Packet;)Z");
        assertTrue(calls(receiveAdapter, ConnectionHookCallbacks.class, "onPacketReceive"));
        MethodNode finalWriteAdapter = findMethod(
            patch,
            "onFinalPacketWrite",
            "(Lnet/minecraft/network/Connection;Lnet/minecraft/network/protocol/Packet;)V");
        assertTrue(calls(finalWriteAdapter, ConnectionHookCallbacks.class, "onFinalPacketWrite"));

        ClassNode callbacks = readClass(ConnectionHookCallbacks.class);
        MethodNode receive = findMethod(
            callbacks,
            "onPacketReceive",
            "(Lnet/minecraft/network/Connection;Lnet/minecraft/network/protocol/Packet;)Z");
        List<MethodInsnNode> receiveCalls = Arrays.stream(receive.instructions.toArray())
            .filter(MethodInsnNode.class::isInstance)
            .map(MethodInsnNode.class::cast)
            .toList();
        int protocolIngress = invocationIndex(
            receiveCalls,
            Type.getInternalName(Protocol.class),
            "consumeEarlyPacket");
        int readinessGate = invocationIndex(
            receiveCalls,
            Type.getInternalName(ZenClient.class),
            "isReady");
        assertTrue(protocolIngress >= 0);
        assertTrue(readinessGate >= 0);
        assertTrue(protocolIngress < readinessGate,
            "MAIN-channel protocol ingress must run before the general readiness/EventBus gate");

        MethodNode finalWrite = findMethod(
            callbacks,
            "onFinalPacketWrite",
            "(Lnet/minecraft/network/Connection;Lnet/minecraft/network/protocol/Packet;)V");
        boolean routesToProtocol = Arrays.stream(finalWrite.instructions.toArray())
            .filter(MethodInsnNode.class::isInstance)
            .map(MethodInsnNode.class::cast)
            .anyMatch(invocation -> Type.getInternalName(Protocol.class).equals(invocation.owner)
                && "onFinalPacketWrite".equals(invocation.name));
        assertTrue(routesToProtocol);
    }

    private static boolean calls(MethodNode method, Class<?> owner, String name) {
        return Arrays.stream(method.instructions.toArray())
            .filter(MethodInsnNode.class::isInstance)
            .map(MethodInsnNode.class::cast)
            .anyMatch(invocation -> Type.getInternalName(owner).equals(invocation.owner)
                && name.equals(invocation.name));
    }

    @Test
    void finalObserverRunsImmediatelyAfterChannelWriteAndTransformedClassVerifies() throws Exception {
        String resourceName = Connection.class.getName().replace('.', '/') + ".class";
        ClassLoader loader = Connection.class.getClassLoader();

        byte[] original;
        try (InputStream stream = loader.getResourceAsStream(resourceName)) {
            assertNotNull(stream, "Connection bytecode must be available on the test classpath");
            original = stream.readAllBytes();
        }

        ClassReader reader = new ClassReader(original);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        PatchTransformer.apply(ConnectionPatch.class, classNode);

        MethodNode doSendPacket = findMethod(classNode, "doSendPacket", DO_SEND_PACKET_DESC);
        List<MethodInsnNode> writes = Arrays.stream(doSendPacket.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(invocation -> invocation.getOpcode() == Opcodes.INVOKEINTERFACE)
                .filter(invocation -> "io/netty/channel/Channel".equals(invocation.owner))
                .filter(invocation -> "writeAndFlush".equals(invocation.name))
                .toList();
        assertEquals(1, writes.size());

        AbstractInsnNode connectionLoadNode = nextExecutable(writes.get(0).getNext());
        assertTrue(connectionLoadNode instanceof VarInsnNode);
        VarInsnNode connectionLoad = (VarInsnNode) connectionLoadNode;
        assertEquals(Opcodes.ALOAD, connectionLoad.getOpcode());
        assertEquals(0, connectionLoad.var);

        AbstractInsnNode packetLoadNode = nextExecutable(connectionLoad.getNext());
        assertTrue(packetLoadNode instanceof VarInsnNode);
        VarInsnNode packetLoad = (VarInsnNode) packetLoadNode;
        assertEquals(Opcodes.ALOAD, packetLoad.getOpcode());
        assertEquals(1, packetLoad.var);

        AbstractInsnNode observerNode = nextExecutable(packetLoad.getNext());
        assertTrue(observerNode instanceof MethodInsnNode);
        MethodInsnNode observer = (MethodInsnNode) observerNode;
        assertEquals(Opcodes.INVOKESTATIC, observer.getOpcode());
        assertEquals(Type.getInternalName(ConnectionPatch.class), observer.owner);
        assertEquals("onFinalPacketWrite", observer.name);
        assertEquals(
                "(Lnet/minecraft/network/Connection;Lnet/minecraft/network/protocol/Packet;)V",
                observer.desc);

        MethodNode sendPacket = findMethod(
                classNode,
                "sendPacket",
                "(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V");
        MethodInsnNode sendObserver = Arrays.stream(sendPacket.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(invocation -> Type.getInternalName(ConnectionPatch.class).equals(invocation.owner))
                .filter(invocation -> "onPacketSend".equals(invocation.name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing onPacketSend hook"));
        assertEquals(
                "(Lnet/minecraft/network/Connection;Lnet/minecraft/network/protocol/Packet;"
                        + "Lnet/minecraft/network/PacketSendListener;)Z",
                sendObserver.desc);
        AbstractInsnNode listenerLoadNode = previousExecutable(sendObserver.getPrevious());
        assertTrue(listenerLoadNode instanceof VarInsnNode);
        VarInsnNode listenerLoad = (VarInsnNode) listenerLoadNode;
        assertEquals(Opcodes.ALOAD, listenerLoad.getOpcode());
        assertEquals(2, listenerLoad.var);
        AbstractInsnNode sendPacketLoadNode = previousExecutable(listenerLoad.getPrevious());
        assertTrue(sendPacketLoadNode instanceof VarInsnNode);
        VarInsnNode sendPacketLoad = (VarInsnNode) sendPacketLoadNode;
        assertEquals(Opcodes.ALOAD, sendPacketLoad.getOpcode());
        assertEquals(1, sendPacketLoad.var);
        AbstractInsnNode sendConnectionLoadNode = previousExecutable(sendPacketLoad.getPrevious());
        assertTrue(sendConnectionLoadNode instanceof VarInsnNode);
        VarInsnNode sendConnectionLoad = (VarInsnNode) sendConnectionLoadNode;
        assertEquals(Opcodes.ALOAD, sendConnectionLoad.getOpcode());
        assertEquals(0, sendConnectionLoad.var);

        long contextCaptures = Arrays.stream(sendPacket.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(invocation -> Type.getInternalName(ConnectionPatch.class).equals(invocation.owner))
                .filter(invocation -> "captureScaffoldTraceContext".equals(invocation.name))
                .count();
        assertEquals(1L, contextCaptures);

        MethodNode channelRead = findMethod(
                classNode,
                "channelRead0",
                "(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V");
        MethodInsnNode receiveObserver = Arrays.stream(channelRead.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(invocation -> Type.getInternalName(ConnectionPatch.class).equals(invocation.owner))
                .filter(invocation -> "onPacketReceive".equals(invocation.name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing onPacketReceive hook"));
        assertEquals(
                "(Lnet/minecraft/network/Connection;Lnet/minecraft/network/protocol/Packet;)Z",
                receiveObserver.desc);
        AbstractInsnNode receivePacketLoadNode = previousExecutable(receiveObserver.getPrevious());
        assertTrue(receivePacketLoadNode instanceof VarInsnNode);
        VarInsnNode receivePacketLoad = (VarInsnNode) receivePacketLoadNode;
        assertEquals(Opcodes.ALOAD, receivePacketLoad.getOpcode());
        assertEquals(2, receivePacketLoad.var);
        AbstractInsnNode sourceConnectionLoadNode = previousExecutable(receivePacketLoad.getPrevious());
        assertTrue(sourceConnectionLoadNode instanceof VarInsnNode);
        VarInsnNode sourceConnectionLoad = (VarInsnNode) sourceConnectionLoadNode;
        assertEquals(Opcodes.ALOAD, sourceConnectionLoad.getOpcode());
        assertEquals(0, sourceConnectionLoad.var);

        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);

        StringWriter verificationOutput = new StringWriter();
        CheckClassAdapter.verify(
                new ClassReader(writer.toByteArray()),
                loader,
                false,
                new PrintWriter(verificationOutput));
        assertTrue(
                verificationOutput.toString().isBlank(),
                () -> "Transformed Connection failed verification:\n" + verificationOutput);
    }

    @Test
    void repairsOnlyANullViaForgeTrackedVersionAfterItsOneShotSetter() {
        String setDescriptor = "(" + VIAFORGE_PROTOCOL_VERSION + ")V";
        MethodNode connect = new MethodNode(
                Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "connect",
                "(Ljava/net/InetSocketAddress;ZLnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;",
                null,
                null);
        connect.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        connect.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        MethodInsnNode oneShotSetter = new MethodInsnNode(
                Opcodes.INVOKEINTERFACE,
                VIAFORGE_EXTENDED_NETWORK_MANAGER,
                "viaForge$setTrackedVersion",
                setDescriptor,
                true);
        connect.instructions.add(oneShotSetter);
        connect.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        connect.instructions.add(new InsnNode(Opcodes.ARETURN));

        ConnectionPatch.transformViaForgeTrackedVersion(connect);

        AbstractInsnNode connectionLoadNode = nextExecutable(oneShotSetter.getNext());
        assertTrue(connectionLoadNode instanceof VarInsnNode);
        assertEquals(Opcodes.ALOAD, connectionLoadNode.getOpcode());
        assertEquals(2, ((VarInsnNode) connectionLoadNode).var);

        List<MethodInsnNode> calls = Arrays.stream(connect.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .toList();
        int originalSetter = calls.indexOf(oneShotSetter);
        int trackedGetter = invocationIndex(
                calls, VIAFORGE_EXTENDED_NETWORK_MANAGER, "viaForge$getTrackedVersion");
        int managerGetter = invocationIndex(calls, VIAFORGE_COMMON, "getManager");
        int targetGetter = invocationIndex(calls, VIAFORGE_COMMON, "getTargetVersion");
        int fallbackSetter = -1;
        for (int index = originalSetter + 1; index < calls.size(); index++) {
            MethodInsnNode invocation = calls.get(index);
            if (VIAFORGE_EXTENDED_NETWORK_MANAGER.equals(invocation.owner)
                    && "viaForge$setTrackedVersion".equals(invocation.name)) {
                fallbackSetter = index;
                break;
            }
        }

        assertTrue(originalSetter >= 0);
        assertTrue(originalSetter < trackedGetter);
        assertTrue(trackedGetter < managerGetter);
        assertTrue(managerGetter < targetGetter);
        assertTrue(targetGetter < fallbackSetter);
        assertEquals(2L, calls.stream()
                .filter(invocation -> VIAFORGE_EXTENDED_NETWORK_MANAGER.equals(invocation.owner))
                .filter(invocation -> "viaForge$setTrackedVersion".equals(invocation.name))
                .count());
    }

    @Test
    void leavesVanillaConnectUntouchedWhenViaForgeIsAbsent() {
        MethodNode connect = new MethodNode(
                Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "connect",
                "(Ljava/net/InetSocketAddress;ZLnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;",
                null,
                null);
        connect.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        connect.instructions.add(new InsnNode(Opcodes.ARETURN));
        int instructionCount = connect.instructions.size();

        ConnectionPatch.transformViaForgeTrackedVersion(connect);

        assertEquals(instructionCount, connect.instructions.size());
    }

    private static MethodNode findMethod(ClassNode classNode, String name, String descriptor) {
        return classNode.methods.stream()
                .filter(method -> name.equals(method.name) && descriptor.equals(method.desc))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing method " + name + descriptor));
    }

    private static ClassNode readClass(Class<?> type) throws Exception {
        String resourceName = type.getName().replace('.', '/') + ".class";
        try (InputStream stream = type.getClassLoader().getResourceAsStream(resourceName)) {
            assertNotNull(stream, "class bytecode must be available: " + type.getName());
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    private static int invocationIndex(List<MethodInsnNode> calls, String owner, String name) {
        for (int index = 0; index < calls.size(); index++) {
            MethodInsnNode invocation = calls.get(index);
            if (owner.equals(invocation.owner) && name.equals(invocation.name)) return index;
        }
        return -1;
    }

    private static AbstractInsnNode nextExecutable(AbstractInsnNode instruction) {
        AbstractInsnNode current = instruction;
        while (current != null && current.getOpcode() < 0) {
            current = current.getNext();
        }
        return current;
    }

    private static AbstractInsnNode previousExecutable(AbstractInsnNode instruction) {
        AbstractInsnNode current = instruction;
        while (current != null && current.getOpcode() < 0) {
            current = current.getPrevious();
        }
        return current;
    }
}
