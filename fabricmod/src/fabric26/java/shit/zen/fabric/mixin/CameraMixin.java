package shit.zen.fabric.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import shit.zen.hook.CameraHookCallbacks;
import shit.zen.utils.rotation.Rotation;

/** Fabric 26.2 adapter for silent-rotation visual camera interpolation. */
@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow
    private @Nullable Entity entity;

    @ModifyArgs(
            method = "alignWithEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;setRotation(FF)V",
                    ordinal = 0))
    private void mizulune$setMinecartRotation(Args args, float partialTick) {
        this.mizulune$setVisualRotation(args, partialTick);
    }

    @ModifyArgs(
            method = "alignWithEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;setRotation(FF)V",
                    ordinal = 1))
    private void mizulune$setEntityRotation(Args args, float partialTick) {
        this.mizulune$setVisualRotation(args, partialTick);
    }

    private void mizulune$setVisualRotation(Args args, float partialTick) {
        Rotation visualRotation = CameraHookCallbacks.visualRotation(this.entity, partialTick);
        if (visualRotation != null) {
            args.set(0, visualRotation.getYaw());
            args.set(1, visualRotation.getPitch());
        }
    }
}
