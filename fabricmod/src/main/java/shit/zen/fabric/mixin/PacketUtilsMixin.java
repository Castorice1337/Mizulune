package shit.zen.fabric.mixin;

import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shit.zen.hook.PacketUtilsHookCallbacks;

/** Fabric adapter for cancellable ReceivePacketEvent scheduling on clientbound packets. */
@Mixin(PacketUtils.class)
abstract class PacketUtilsMixin {
    @Inject(
            method = "ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;"
                    + "Lnet/minecraft/network/PacketListener;"
                    + "Lnet/minecraft/util/thread/BlockableEventLoop;)V",
            at = @At("HEAD"),
            cancellable = true)
    private static <T extends PacketListener> void mizulune$ensureRunningOnSameThread(
            Packet<T> packet,
            T listener,
            BlockableEventLoop<?> loop,
            CallbackInfo callbackInfo) throws RunningOnDifferentThreadException {
        if (PacketUtilsHookCallbacks.onEnsureRunningOnSameThread(packet, listener, loop)) {
            callbackInfo.cancel();
        }
    }
}
