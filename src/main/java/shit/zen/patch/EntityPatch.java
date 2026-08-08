package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Overwrite;
import asm.patchify.annotation.Patch;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import shit.zen.hook.EntityHookCallbacks;
import shit.zen.hook.HookDecision;

@Patch(Entity.class)
public class EntityPatch {
    @Inject(method = "makeStuckInBlock", desc = "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;)V", at = @At(At.Type.TAIL))
    public static void onMakeStuckInBlock(Entity entity, BlockState state, Vec3 motion, CallbackInfo callbackInfo) {
        EntityHookCallbacks.onMakeStuckInBlock(entity, state, motion);
    }

    @Inject(method = "push", desc = "(Lnet/minecraft/world/entity/Entity;)V", at = @At(At.Type.HEAD))
    public static void onPush(Entity entity, CallbackInfo callbackInfo) {
        if (EntityHookCallbacks.onPush(entity).handled()) {
            callbackInfo.cancel();
        }
    }

    @Overwrite(method = "moveRelative", desc = "(FLnet/minecraft/world/phys/Vec3;)V")
    public static void overwriteMoveRelative(Entity entity, float speed, Vec3 movement) throws Exception {
        EntityHookCallbacks.moveRelative(entity, speed, movement);
    }

    @Overwrite(method = "calculateViewVector", desc = "(FF)Lnet/minecraft/world/phys/Vec3;")
    public static Vec3 overwriteCalculateViewVector(Entity entity, float pitch, float yaw) throws Exception {
        return EntityHookCallbacks.calculateViewVector(entity, pitch, yaw);
    }

    public static Vec3 applyRotation(Vec3 movement, float speed, float yaw) {
        return EntityHookCallbacks.applyRotation(movement, speed, yaw);
    }
}
