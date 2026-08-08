package shit.zen.fabric.mixin;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shit.zen.fantnel.FantnelProxySession;
import shit.zen.hook.ConnectionHookCallbacks;

/** 26.2 Connection signatures use Netty listeners and an explicit flush flag. */
@Mixin(Connection.class)
abstract class ConnectionMixin {
    @Inject(method = "disconnect(Lnet/minecraft/network/chat/Component;)V", at = @At("RETURN"))
    private void mizulune$disconnect(Component reason, CallbackInfo callbackInfo) {
        FantnelProxySession.onConnectionClosed(((Connection) (Object) this).getRemoteAddress());
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
        at = @At("HEAD"), cancellable = true)
    private void mizulune$receive(ChannelHandlerContext context, Packet<?> packet, CallbackInfo callbackInfo) {
        if (ConnectionHookCallbacks.onPacketReceive((Connection) (Object) this, packet)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "sendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
        at = @At("HEAD"), cancellable = true)
    private void mizulune$send(Packet<?> packet, ChannelFutureListener listener,
            boolean flush, CallbackInfo callbackInfo) {
        Connection connection = (Connection) (Object) this;
        if (ConnectionHookCallbacks.onPacketSend(connection, packet, listener)) {
            callbackInfo.cancel();
            return;
        }
        ConnectionHookCallbacks.captureScaffoldTraceContext(connection, packet);
    }

    @Inject(method = "doSendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
        at = @At("TAIL"))
    private void mizulune$finalWrite(Packet<?> packet, ChannelFutureListener listener,
            boolean flush, CallbackInfo callbackInfo) {
        ConnectionHookCallbacks.onFinalPacketWrite((Connection) (Object) this, packet);
    }
}
