package de.florianmichael.viafabricplus.injection.mixin.fixes.viaversion;

import com.viaversion.viaversion.api.type.types.misc.NamedCompoundTagType;
import com.viaversion.viaversion.libs.opennbt.tag.limiter.TagLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = NamedCompoundTagType.class, remap = false)
public abstract class MixinNamedCompoundTagType {
    @Redirect(method = "read(Lio/netty/buffer/ByteBuf;Z)Lcom/viaversion/viaversion/libs/opennbt/tag/builtin/CompoundTag;",
        at = @At(value = "INVOKE", target = "Lcom/viaversion/viaversion/libs/opennbt/tag/limiter/TagLimiter;create(II)Lcom/viaversion/viaversion/libs/opennbt/tag/limiter/TagLimiter;"))
    private static TagLimiter removeNbtSizeLimit(int maxBytes, int maxLevels) {
        return TagLimiter.noop();
    }
}
