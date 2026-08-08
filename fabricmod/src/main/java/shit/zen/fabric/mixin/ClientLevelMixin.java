package shit.zen.fabric.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import shit.zen.hook.ClientLevelHookCallbacks;

/** Fabric adapter for the delayed local-player tick task queue. */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    @Redirect(
            method = "tickNonPassenger",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void mizulune$tickEntity(Entity entity) {
        if (!ClientLevelHookCallbacks.consumeDelayedPlayerTask(entity)) {
            entity.tick();
        }
    }
}
