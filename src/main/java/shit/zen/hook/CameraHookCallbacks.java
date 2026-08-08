package shit.zen.hook;

import net.minecraft.world.entity.Entity;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.utils.rotation.Rotation;
import shit.zen.utils.rotation.RotationHandler;

/** Shared third/first-person visual camera rotation lookup. */
public final class CameraHookCallbacks {
    private CameraHookCallbacks() {
    }

    public static Rotation visualRotation(Entity entity, float partialTick) {
        if (!ZenClient.isReady()
                || ClientBase.mc == null
                || entity != ClientBase.mc.player) {
            return null;
        }
        return RotationHandler.getVisualRotation(partialTick);
    }
}
