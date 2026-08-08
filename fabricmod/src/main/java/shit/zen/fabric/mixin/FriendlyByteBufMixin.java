package shit.zen.fabric.mixin;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import shit.zen.hook.NetworkSerializationHookCallbacks;

/** Fabric adapter for component JSON NameProtect filtering. */
@Mixin(FriendlyByteBuf.class)
abstract class FriendlyByteBufMixin {
    @Redirect(
            method = "readComponent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/Component$Serializer;fromJson(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
    private static MutableComponent mizulune$fromJson(String json) {
        return NetworkSerializationHookCallbacks.readComponentJson(json);
    }
}
