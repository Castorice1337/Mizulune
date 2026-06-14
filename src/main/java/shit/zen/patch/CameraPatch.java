package shit.zen.patch;

import asm.patchify.annotation.Patch;
import asm.patchify.annotation.WrapInvoke;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.asm.Invocation;
import shit.zen.utils.rotation.Rotation;
import shit.zen.utils.rotation.RotationHandler;

@Patch(Camera.class)
public class CameraPatch {
    @WrapInvoke(
            method = "setup",
            desc = "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            target = "net/minecraft/client/Camera/setRotation",
            targetDesc = "(FF)V"
    )
    public static void onSetRotation(
            Camera camera,
            BlockGetter level,
            Entity entity,
            boolean detached,
            boolean thirdPersonReverse,
            float partialTick,
            Invocation<Camera, Void> original) throws Exception {
        if (ZenClient.isReady() && entity == ClientBase.mc.player && original.args().size() >= 2) {
            Rotation visualRotation = RotationHandler.getVisualRotation(partialTick);
            if (visualRotation != null) {
                original.args().set(0, visualRotation.getYaw());
                original.args().set(1, visualRotation.getPitch());
            }
        }
        original.call();
    }
}
