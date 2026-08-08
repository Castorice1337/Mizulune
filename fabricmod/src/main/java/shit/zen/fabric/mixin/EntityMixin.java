package shit.zen.fabric.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import shit.zen.hook.EntityHookCallbacks;
import shit.zen.hook.HookDecision;

/** Fabric adapter for the movement-rotation event used by Sprint and movement correction. */
@Mixin(Entity.class)
abstract class EntityMixin {
    @Inject(method = "makeStuckInBlock", at = @At("TAIL"))
    private void mizulune$makeStuckInBlock(
            BlockState state,
            Vec3 motion,
            CallbackInfo callbackInfo) {
        EntityHookCallbacks.onMakeStuckInBlock((Entity) (Object) this, state, motion);
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void mizulune$push(Entity other, CallbackInfo callbackInfo) {
        if (EntityHookCallbacks.onPush((Entity) (Object) this).handled()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "moveRelative", at = @At("HEAD"), cancellable = true)
    private void mizulune$moveRelative(float speed, Vec3 movement, CallbackInfo callbackInfo) {
        EntityHookCallbacks.moveRelative((Entity) (Object) this, speed, movement);
        callbackInfo.cancel();
    }

    @Inject(method = "calculateViewVector", at = @At("HEAD"), cancellable = true)
    private void mizulune$calculateViewVector(
            float pitch,
            float yaw,
            CallbackInfoReturnable<Vec3> callbackInfo) {
        callbackInfo.setReturnValue(EntityHookCallbacks.calculateViewVector(
                (Entity) (Object) this, pitch, yaw));
    }
}
