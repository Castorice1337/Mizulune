package shit.zen.fabric.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import shit.zen.hook.PlayerHookCallbacks;

/** Preserves local-player drop yaw after the drop implementation moved to LivingEntity. */
@Mixin(LivingEntity.class)
abstract class LivingEntityDropMixin {
    @Redirect(
        method = "createItemStackToDrop",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F")
    )
    private float mizulune$dropYaw(LivingEntity entity) {
        return entity instanceof Player player
            ? PlayerHookCallbacks.actionYaw(player, entity.getYRot())
            : entity.getYRot();
    }
}
