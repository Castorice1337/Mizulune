package shit.zen.patch;

import asm.patchify.annotation.Patch;
import asm.patchify.annotation.Transform;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import shit.zen.hook.ConnectionHookCallbacks;

@Patch(Connection.class)
public class ConnectionPatch {
    private static final String VIAFORGE_COMMON =
            "com/viaversion/viaforge/common/ViaForgeCommon";
    private static final String VIAFORGE_EXTENDED_NETWORK_MANAGER =
            "com/viaversion/viaforge/common/extended/ExtendedNetworkManager";
    private static final String VIAFORGE_PROTOCOL_VERSION =
            "Lcom/viaversion/viaversion/api/protocol/version/ProtocolVersion;";

    public static boolean onPacketReceive(Connection sourceConnection, Packet<?> packet) {
        return ConnectionHookCallbacks.onPacketReceive(sourceConnection, packet);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean onPacketSend(
            Connection sourceConnection,
            Packet<?> packet,
            PacketSendListener listener) {
        return ConnectionHookCallbacks.onPacketSend(sourceConnection, packet, listener);
    }

    public static void captureScaffoldTraceContext(Connection sourceConnection, Packet<?> packet) {
        ConnectionHookCallbacks.captureScaffoldTraceContext(sourceConnection, packet);
    }

    public static void onFinalPacketWrite(Connection sourceConnection, Packet<?> packet) {
        ConnectionHookCallbacks.onFinalPacketWrite(sourceConnection, packet);
    }

    @Transform(method = "channelRead0", desc = "(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V")
    public static void transformReceive(MethodNode methodNode) {
        InsnList header = new InsnList();
        header.add(new VarInsnNode(Opcodes.ALOAD, 0));
        header.add(new VarInsnNode(Opcodes.ALOAD, 2));
        header.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(ConnectionPatch.class),
                "onPacketReceive",
                "(Lnet/minecraft/network/Connection;Lnet/minecraft/network/protocol/Packet;)Z",
                false));
        LabelNode label = new LabelNode();
        header.add(new JumpInsnNode(Opcodes.IFEQ, label));
        header.add(new InsnNode(Opcodes.RETURN));
        header.add(label);
        methodNode.instructions.insert(header);
    }

    @Transform(method = "sendPacket", desc = "(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V")
    public static void transformSend(MethodNode methodNode) {
        InsnList header = new InsnList();
        header.add(new VarInsnNode(Opcodes.ALOAD, 0));
        header.add(new VarInsnNode(Opcodes.ALOAD, 1));
        header.add(new VarInsnNode(Opcodes.ALOAD, 2));
        header.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(ConnectionPatch.class),
                "onPacketSend",
                "(Lnet/minecraft/network/Connection;Lnet/minecraft/network/protocol/Packet;"
                        + "Lnet/minecraft/network/PacketSendListener;)Z",
                false));
        LabelNode label = new LabelNode();
        header.add(new JumpInsnNode(Opcodes.IFEQ, label));
        header.add(new InsnNode(Opcodes.RETURN));
        header.add(label);
        header.add(new VarInsnNode(Opcodes.ALOAD, 0));
        header.add(new VarInsnNode(Opcodes.ALOAD, 1));
        header.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(ConnectionPatch.class),
                "captureScaffoldTraceContext",
                "(Lnet/minecraft/network/Connection;Lnet/minecraft/network/protocol/Packet;)V",
                false));
        methodNode.instructions.insert(header);
    }

    @Transform(method = "doSendPacket", desc = "(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Lnet/minecraft/network/ConnectionProtocol;Lnet/minecraft/network/ConnectionProtocol;)V")
    public static void transformFinalWrite(MethodNode methodNode) {
        for (AbstractInsnNode instruction : methodNode.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode invocation)
                    || invocation.getOpcode() != Opcodes.INVOKEINTERFACE
                    || !"io/netty/channel/Channel".equals(invocation.owner)
                    || !"writeAndFlush".equals(invocation.name)
                    || !"(Ljava/lang/Object;)Lio/netty/channel/ChannelFuture;".equals(invocation.desc)) {
                continue;
            }

            InsnList observer = new InsnList();
            observer.add(new VarInsnNode(Opcodes.ALOAD, 0));
            observer.add(new VarInsnNode(Opcodes.ALOAD, 1));
            observer.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(ConnectionPatch.class),
                    "onFinalPacketWrite",
                    "(Lnet/minecraft/network/Connection;Lnet/minecraft/network/protocol/Packet;)V",
                    false));
            methodNode.instructions.insert(invocation, observer);
        }
    }

    /**
     * ViaForge 4.4.0+3 stores a one-shot protocol version by destination address. Local proxy
     * connections can legitimately miss that address entry, after which its channel initializer
     * dereferences the null tracked version. Preserve every real per-server value and only fill a
     * missing value from ViaForge's own non-null global target version.
     */
    @Transform(method = "connect", desc = "(Ljava/net/InetSocketAddress;ZLnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;")
    public static void transformViaForgeTrackedVersion(MethodNode methodNode) {
        String trackedVersionDescriptor = "()" + VIAFORGE_PROTOCOL_VERSION;
        String setTrackedVersionDescriptor = "(" + VIAFORGE_PROTOCOL_VERSION + ")V";
        for (AbstractInsnNode instruction : methodNode.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode invocation)
                    || invocation.getOpcode() != Opcodes.INVOKEINTERFACE
                    || !VIAFORGE_EXTENDED_NETWORK_MANAGER.equals(invocation.owner)
                    || !"viaForge$setTrackedVersion".equals(invocation.name)
                    || !setTrackedVersionDescriptor.equals(invocation.desc)) {
                continue;
            }

            LabelNode managerPresent = new LabelNode();
            LabelNode targetPresent = new LabelNode();
            LabelNode ready = new LabelNode();
            InsnList fallback = new InsnList();
            fallback.add(new VarInsnNode(Opcodes.ALOAD, 2));
            fallback.add(new MethodInsnNode(
                    Opcodes.INVOKEINTERFACE,
                    VIAFORGE_EXTENDED_NETWORK_MANAGER,
                    "viaForge$getTrackedVersion",
                    trackedVersionDescriptor,
                    true));
            fallback.add(new JumpInsnNode(Opcodes.IFNONNULL, ready));

            fallback.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    VIAFORGE_COMMON,
                    "getManager",
                    "()L" + VIAFORGE_COMMON + ";",
                    false));
            fallback.add(new InsnNode(Opcodes.DUP));
            fallback.add(new JumpInsnNode(Opcodes.IFNONNULL, managerPresent));
            fallback.add(new InsnNode(Opcodes.POP));
            fallback.add(new JumpInsnNode(Opcodes.GOTO, ready));
            fallback.add(managerPresent);
            fallback.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    VIAFORGE_COMMON,
                    "getTargetVersion",
                    trackedVersionDescriptor,
                    false));
            fallback.add(new InsnNode(Opcodes.DUP));
            fallback.add(new JumpInsnNode(Opcodes.IFNONNULL, targetPresent));
            fallback.add(new InsnNode(Opcodes.POP));
            fallback.add(new JumpInsnNode(Opcodes.GOTO, ready));
            fallback.add(targetPresent);
            fallback.add(new VarInsnNode(Opcodes.ALOAD, 2));
            fallback.add(new InsnNode(Opcodes.SWAP));
            fallback.add(new MethodInsnNode(
                    Opcodes.INVOKEINTERFACE,
                    VIAFORGE_EXTENDED_NETWORK_MANAGER,
                    "viaForge$setTrackedVersion",
                    setTrackedVersionDescriptor,
                    true));
            fallback.add(ready);
            methodNode.instructions.insert(invocation, fallback);
        }
    }
}
