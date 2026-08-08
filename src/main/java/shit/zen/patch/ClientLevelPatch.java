package shit.zen.patch;

import asm.patchify.annotation.Patch;
import asm.patchify.annotation.Slice;
import asm.patchify.annotation.WrapInvoke;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import shit.zen.asm.Invocation;
import shit.zen.hook.ClientLevelHookCallbacks;

@Patch(ClientLevel.class)
public class ClientLevelPatch {
    @WrapInvoke(
            method = "tickNonPassenger",
            desc = "(Lnet/minecraft/world/entity/Entity;)V",
            target = "net/minecraft/world/entity/Entity/tick",
            targetDesc = "()V",
            slice = @Slice(startIndex = 1, endIndex = 1)
    )
    public static void onTickEntity(ClientLevel level, Entity entity, Invocation<Entity, Void> original) throws Exception {
        if (!ClientLevelHookCallbacks.consumeDelayedPlayerTask(entity)) {
            original.call();
        }
    }
}
