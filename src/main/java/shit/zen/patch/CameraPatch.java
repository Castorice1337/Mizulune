package shit.zen.patch;

import asm.patchify.annotation.Patch;
import asm.patchify.annotation.Slice;
import asm.patchify.annotation.WrapInvoke;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.asm.Invocation;
import shit.zen.hook.CameraHookCallbacks;
import shit.zen.utils.rotation.Rotation;
import shit.zen.utils.rotation.RotationHandler;

@Patch(Camera.class)
public class CameraPatch {
    @WrapInvoke(
            method = "setup",
            desc = "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            target = "net/minecraft/client/Camera/setRotation",
            targetDesc = "(FF)V",
            slice = @Slice(startIndex = 1, endIndex = 1)
    )
    public static void onSetRotation(
            Camera camera,
            BlockGetter level,
            Entity entity,
            boolean detached,
            boolean thirdPersonReverse,
            float partialTick,
            Invocation<Camera, Void> original) throws Exception {
        Rotation visualRotation = CameraHookCallbacks.visualRotation(entity, partialTick);
        if (visualRotation != null && original.args().size() >= 2) {
            original.args().set(0, visualRotation.getYaw());
            original.args().set(1, visualRotation.getPitch());
        }
        original.call();
    }
}
