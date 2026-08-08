package shit.zen.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import shit.zen.event.impl.SprintDecisionEvent;
import shit.zen.hook.LocalPlayerHookCallbacks;

/**
 * Runs before ViaFabricPlus' legacy sprint redirect so both transformations
 * can consume the same forward-impulse expression without a Redirect clash.
 */
@Mixin(value = LocalPlayer.class, priority = 2100)
abstract class LocalPlayerSprintCompatMixin {
    @ModifyExpressionValue(
        method = "aiStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/Input;hasForwardImpulse()Z"
        )
    )
    private boolean mizulune$hasForwardImpulse(boolean original) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        return LocalPlayerHookCallbacks.applySprintDecision(
            player.input,
            original,
            SprintDecisionEvent.Source.MOVEMENT_TICK
        );
    }
}
