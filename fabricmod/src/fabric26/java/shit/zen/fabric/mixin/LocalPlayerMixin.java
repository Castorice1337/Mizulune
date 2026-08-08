package shit.zen.fabric.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shit.zen.event.impl.SlowdownEvent;
import shit.zen.event.impl.SprintDecisionEvent;
import shit.zen.hook.LocalPlayerHookCallbacks;

/** Movement tick, slowdown, sprint and motion packet adapter for 26.2. */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
    @Inject(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V")
    )
    private void mizulune$tickSprintEvent(CallbackInfo callbackInfo) {
        LocalPlayerHookCallbacks.onTickSprintEvent((LocalPlayer) (Object) this);
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void mizulune$aiStep(CallbackInfo callbackInfo) {
        LocalPlayerHookCallbacks.onAiStep((LocalPlayer) (Object) this);
    }

    @Redirect(
        method = "aiStep",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSlowDueToUsingItem()Z")
    )
    private boolean mizulune$slowdown(LocalPlayer player) {
        SlowdownEvent event = LocalPlayerHookCallbacks.onSlowDown(player.isUsingItem());
        return event.isSlowDown();
    }

    @Redirect(
        method = "sendIsSprintingIfNeeded",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSprinting()Z")
    )
    private boolean mizulune$networkSprint(LocalPlayer player) {
        return LocalPlayerHookCallbacks.applySprintDecision(
            player.input,
            player.isSprinting(),
            SprintDecisionEvent.Source.NETWORK
        );
    }

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void mizulune$motionHead(CallbackInfo callbackInfo) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        LocalPlayerHookCallbacks.onMotion(
            player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
            player.onGround(), false);
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void mizulune$motionTail(CallbackInfo callbackInfo) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        LocalPlayerHookCallbacks.onMotion(
            player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
            player.onGround(), true);
    }

    @Redirect(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getX()D"))
    private double mizulune$motionX(LocalPlayer player) {
        return LocalPlayerHookCallbacks.motionX(player.getX());
    }

    @Redirect(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getY()D"))
    private double mizulune$motionY(LocalPlayer player) {
        return LocalPlayerHookCallbacks.motionY(player.getY());
    }

    @Redirect(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getZ()D"))
    private double mizulune$motionZ(LocalPlayer player) {
        return LocalPlayerHookCallbacks.motionZ(player.getZ());
    }

    @Redirect(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
    private float mizulune$motionYaw(LocalPlayer player) {
        return LocalPlayerHookCallbacks.motionYaw(player.getYRot());
    }

    @Redirect(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
    private float mizulune$motionPitch(LocalPlayer player) {
        return LocalPlayerHookCallbacks.motionPitch(player.getXRot());
    }

    @Redirect(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;onGround()Z"))
    private boolean mizulune$motionOnGround(LocalPlayer player) {
        return LocalPlayerHookCallbacks.motionOnGround(player.onGround());
    }
}
