package shit.zen.fabric.mixin;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shit.zen.hook.ConnectionHookCallbacks;
import shit.zen.fantnel.FantnelProxySession;

/** Fabric network adapter preserving early protocol ingress and final-write observation. */
@Mixin(Connection.class)
abstract class ConnectionMixin {
    @Inject(method = "disconnect", at = @At("RETURN"))
    private void mizulune$disconnect(Component reason, CallbackInfo callbackInfo) {
        FantnelProxySession.onConnectionClosed(((Connection) (Object) this).getRemoteAddress());
    }

    @Inject(
        method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void mizulune$receive(
        ChannelHandlerContext context,
        Packet<?> packet,
        CallbackInfo callbackInfo
    ) {
        if (ConnectionHookCallbacks.onPacketReceive((Connection) (Object) this, packet)) {
            callbackInfo.cancel();
        }
    }

    @Inject(
        method = "sendPacket(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void mizulune$send(
        Packet<?> packet,
        PacketSendListener listener,
        CallbackInfo callbackInfo
    ) {
        Connection connection = (Connection) (Object) this;
        if (ConnectionHookCallbacks.onPacketSend(connection, packet, listener)) {
            callbackInfo.cancel();
            return;
        }
        ConnectionHookCallbacks.captureScaffoldTraceContext(connection, packet);
    }

    @Inject(
        method = "doSendPacket(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Lnet/minecraft/network/ConnectionProtocol;Lnet/minecraft/network/ConnectionProtocol;)V",
        at = @At(
            value = "INVOKE",
            target = "Lio/netty/channel/Channel;writeAndFlush(Ljava/lang/Object;)Lio/netty/channel/ChannelFuture;",
            shift = At.Shift.AFTER,
            remap = false
        )
    )
    private void mizulune$finalWrite(
        Packet<?> packet,
        PacketSendListener listener,
        ConnectionProtocol sourceProtocol,
        ConnectionProtocol targetProtocol,
        CallbackInfo callbackInfo
    ) {
        ConnectionHookCallbacks.onFinalPacketWrite((Connection) (Object) this, packet);
    }
}
