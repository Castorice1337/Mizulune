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
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.PrePacketEvent;
import shit.zen.modules.impl.movement.Scaffold;
import shit.zen.modules.impl.movement.scaffold.debug.ScaffoldTraceRecorder;
import shit.zen.utils.misc.PacketUtil;
import shit.zen.utils.rotation.RotationHandler;

@Patch(Connection.class)
public class ConnectionPatch extends ClientBase {
    public static boolean onPacketReceive(Connection sourceConnection, Packet<?> packet) {
        if (packet == null) return false;
        if (mc == null || mc.level == null || mc.player == null || packet == null || !ZenClient.isReady()) {
            return false;
        }
        try {
            ScaffoldTraceRecorder.recordIncoming(packet);
        } catch (Throwable ignored) {
        }
        PrePacketEvent prePacket = new PrePacketEvent(packet);
        ZenClient.getInstance().getEventBus().call(prePacket);
        if (prePacket.isCancelled()) {
            return true;
        }
        PacketEvent event = new PacketEvent(prePacket.getPacket(), false, sourceConnection);
        ZenClient.getInstance().getEventBus().call(event);
        return event.isCancelled();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean onPacketSend(
            Packet<?> packet,
            PacketSendListener listener) {
        if (mc == null || mc.level == null || mc.player == null || packet == null || !ZenClient.isReady()) {
            return false;
        }
        PacketUtil.SendPreparation preparation = PacketUtil.prepareSend((Packet) packet);
        if (preparation.cancelled()) {
            return true;
        }
        if (!preparation.bypass()) {
            PacketEvent event = new PacketEvent(packet, true);
            ZenClient.getInstance().getEventBus().call(event);
            if (event.isCancelled()) {
                return true;
            }
        }
        boolean intercepted = Scaffold.interceptOutgoingPacket(packet, listener);
        RotationHandler.onOutgoingPacketAccepted(packet);
        return intercepted;
    }

    public static void captureScaffoldTraceContext(Packet<?> packet) {
        try {
            ScaffoldTraceRecorder.captureCurrentContext(packet);
        } catch (Throwable ignored) {
        }
    }

    public static void onFinalPacketWrite(Packet<?> packet) {
        try {
            RotationHandler.onFinalPacketWrite(packet);
        } catch (Throwable ignored) {
        }
        try {
            ScaffoldTraceRecorder.recordFinalWrite(packet);
        } catch (Throwable ignored) {
        }
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
        header.add(new VarInsnNode(Opcodes.ALOAD, 1));
        header.add(new VarInsnNode(Opcodes.ALOAD, 2));
        header.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(ConnectionPatch.class),
                "onPacketSend",
                "(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)Z",
                false));
        LabelNode label = new LabelNode();
        header.add(new JumpInsnNode(Opcodes.IFEQ, label));
        header.add(new InsnNode(Opcodes.RETURN));
        header.add(label);
        header.add(new VarInsnNode(Opcodes.ALOAD, 1));
        header.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(ConnectionPatch.class),
                "captureScaffoldTraceContext",
                "(Lnet/minecraft/network/protocol/Packet;)V",
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
            observer.add(new VarInsnNode(Opcodes.ALOAD, 1));
            observer.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(ConnectionPatch.class),
                    "onFinalPacketWrite",
                    "(Lnet/minecraft/network/protocol/Packet;)V",
                    false));
            methodNode.instructions.insert(invocation, observer);
        }
    }
}
