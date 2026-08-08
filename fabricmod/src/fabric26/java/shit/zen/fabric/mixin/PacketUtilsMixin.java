package shit.zen.fabric.mixin;

import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shit.zen.ZenClient;
import shit.zen.event.impl.ReceivePacketEvent;

/** Fabric 26.2 adapter for packets queued through the new PacketProcessor. */
@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
abstract class PacketUtilsMixin {
    @Shadow
    @Final
    private PacketListener listener;

    @Shadow
    @Final
    private Packet<?> packet;

    @Inject(
            method = "handle",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"),
            cancellable = true)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void mizulune$beforeQueuedPacket(CallbackInfo callbackInfo) {
        if (!ZenClient.isReady() || !(this.listener instanceof ClientGamePacketListener)) {
            return;
        }

        ReceivePacketEvent event = new ReceivePacketEvent(
                (Packet<ClientGamePacketListener>) (Packet) this.packet);
        ZenClient.getInstance().getEventBus().call(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        }
    }
}
