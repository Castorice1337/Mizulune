package shit.zen.fabric.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import shit.zen.hook.CameraHookCallbacks;
import shit.zen.utils.rotation.Rotation;

/** Fabric adapter for silent-rotation visual camera interpolation. */
@Mixin(Camera.class)
abstract class CameraMixin {
    @ModifyArgs(
            method = "setup",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;setRotation(FF)V",
                    ordinal = 0))
    private void mizulune$setRotation(
            Args args,
            BlockGetter level,
            Entity entity,
            boolean detached,
            boolean thirdPersonReverse,
            float partialTick) {
        Rotation visualRotation = CameraHookCallbacks.visualRotation(entity, partialTick);
        if (visualRotation != null) {
            args.set(0, visualRotation.getYaw());
            args.set(1, visualRotation.getPitch());
        }
    }
}
