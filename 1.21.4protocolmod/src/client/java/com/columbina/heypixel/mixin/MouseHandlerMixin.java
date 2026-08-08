package com.columbina.heypixel.mixin;

import com.columbina.heypixel.HeyPixelFabricRuntime;
import com.columbina.heypixel.HeyPixelProtocolModClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Inject(method = "onPress", at = @At("HEAD"))
    private void mizuluneProtocol$recordClick(
        long window,
        int button,
        int action,
        int modifiers,
        CallbackInfo callbackInfo
    ) {
        HeyPixelFabricRuntime runtime = HeyPixelProtocolModClient.runtime();
        if (runtime != null) runtime.recordMouseButton(button, action);
    }
}
