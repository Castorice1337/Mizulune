package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.util.thread.BlockableEventLoop;
import shit.zen.hook.PacketUtilsHookCallbacks;

@Patch(PacketUtils.class)
public class PacketUtilsPatch {
    @Inject(
            method = "ensureRunningOnSameThread",
            desc = "(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
            at = @At(At.Type.HEAD)
    )
    public static <T extends PacketListener> void onEnsureRunningOnSameThread(Packet<T> packet, T listener, BlockableEventLoop<?> loop, CallbackInfo callbackInfo) throws RunningOnDifferentThreadException {
        if (PacketUtilsHookCallbacks.onEnsureRunningOnSameThread(packet, listener, loop)) {
            callbackInfo.cancel();
        }
    }
}
