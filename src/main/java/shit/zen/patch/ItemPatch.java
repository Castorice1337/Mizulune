package shit.zen.patch;

import asm.patchify.annotation.Patch;
import asm.patchify.annotation.WrapInvoke;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import shit.zen.asm.Invocation;
import shit.zen.hook.ItemHookCallbacks;

@Patch(Item.class)
public class ItemPatch {
    private static final String DEBUG_PREFIX = "YRot: ";

    @WrapInvoke(
            method = "getPlayerPOVHitResult",
            desc = "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/ClipContext$Fluid;)Lnet/minecraft/world/phys/BlockHitResult;",
            target = "net/minecraft/world/entity/player/Player/getXRot",
            targetDesc = "()F"
    )
    public static float onGetPOVHitXRot(Level level, Player player, ClipContext.Fluid fluidContext, Invocation<Player, Float> original) throws Exception {
        return ItemHookCallbacks.pitch(player);
    }

    @WrapInvoke(
            method = "getPlayerPOVHitResult",
            desc = "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/ClipContext$Fluid;)Lnet/minecraft/world/phys/BlockHitResult;",
            target = "net/minecraft/world/entity/player/Player/getYRot",
            targetDesc = "()F"
    )
    public static float onGetPOVHitYRot(Level level, Player player, ClipContext.Fluid fluidContext, Invocation<Player, Float> original) throws Exception {
        return ItemHookCallbacks.yaw(player);
    }
}
