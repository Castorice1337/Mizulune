package shit.zen.fabric.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shit.zen.hook.EntityHookCallbacks;
import shit.zen.hook.HookDecision;
import shit.zen.hook.PlayerHookCallbacks;

/** Local-player action adapter for the 26.2 Player method surface. */
@Mixin(Player.class)
abstract class PlayerMixin {
    @Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true)
    private void mizulune$isStayingOnGroundSurface(CallbackInfoReturnable<Boolean> callbackInfo) {
        HookDecision<Boolean> decision = EntityHookCallbacks.onIsStayingOnGroundSurface(
            (Player) (Object) this);
        if (decision.handled()) callbackInfo.setReturnValue(decision.value());
    }

    @Redirect(
        method = "die",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getYRot()F")
    )
    private float mizulune$dieYaw(Player player) {
        return PlayerHookCallbacks.actionYaw((Player) (Object) this, player.getYRot());
    }

    @Inject(method = "attack", at = @At("HEAD"))
    private void mizulune$attackHead(Entity target, CallbackInfo callbackInfo) {
        PlayerHookCallbacks.onAttack((Player) (Object) this, target, false);
    }

    @Inject(method = "attack", at = @At("TAIL"))
    private void mizulune$attackTail(Entity target, CallbackInfo callbackInfo) {
        PlayerHookCallbacks.onAttack((Player) (Object) this, target, true);
    }
}
