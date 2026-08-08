package com.columbina.heypixel.mixin;

import com.columbina.heypixel.HeyPixelFabricRuntime;
import com.columbina.heypixel.HeyPixelProtocolModClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void mizuluneProtocol$recordUseBlock(
        LocalPlayer player,
        InteractionHand hand,
        BlockHitResult hit,
        CallbackInfoReturnable<?> callbackInfo
    ) {
        HeyPixelFabricRuntime runtime = HeyPixelProtocolModClient.runtime();
        if (runtime != null) runtime.sendUseBlock(player, hand, hit);
    }
}
