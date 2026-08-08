package shit.zen.fabric.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shit.zen.hook.MultiPlayerGameModeHookCallbacks;

@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void mizulune$useItemOn(
            LocalPlayer player,
            InteractionHand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> callbackInfo) {
        MultiPlayerGameModeHookCallbacks.onUseItemOn(player, hand, hit);
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void mizulune$useItemOnResult(
            LocalPlayer player,
            InteractionHand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> callbackInfo) {
        MultiPlayerGameModeHookCallbacks.onUseItemOnResult(callbackInfo.getReturnValue());
    }

    @Inject(method = "attack", at = @At("TAIL"))
    private void mizulune$attack(Player player, Entity target, CallbackInfo callbackInfo) {
        MultiPlayerGameModeHookCallbacks.onAttack(player, target);
    }
}
