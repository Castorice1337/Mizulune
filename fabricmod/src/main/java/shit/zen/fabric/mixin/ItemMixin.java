package shit.zen.fabric.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import shit.zen.hook.ItemHookCallbacks;

/** Fabric adapter for silent item POV ray tracing. */
@Mixin(Item.class)
abstract class ItemMixin {
    @Redirect(
            method = "getPlayerPOVHitResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getXRot()F"))
    private static float mizulune$getPlayerPovPitch(Player player) {
        return ItemHookCallbacks.pitch(player);
    }

    @Redirect(
            method = "getPlayerPOVHitResult",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getYRot()F"))
    private static float mizulune$getPlayerPovYaw(Player player) {
        return ItemHookCallbacks.yaw(player);
    }
}
