package de.florianmichael.viafabricplus.injection.mixin.fixes.viaversion.protocol1_20to1_20_2;

import com.viaversion.viabackwards.protocol.protocol1_20to1_20_2.rewriter.EntityPacketRewriter1_20_2;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import io.github.openzen.via.compat.EntityEffectPacketCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityPacketRewriter1_20_2.class, remap = false)
public abstract class MixinEntityPacketRewriter1_20_2 {
    @Inject(method = "lambda$registerPackets$0", at = @At("HEAD"), cancellable = true, remap = false)
    private static void rewriteEntityEffect(PacketWrapper wrapper, CallbackInfo ci) throws Exception {
        EntityEffectPacketCompat.rewrite(wrapper);
        ci.cancel();
    }
}
