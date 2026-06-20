package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.AttackEvent;

@Patch(MultiPlayerGameMode.class)
public class MultiPlayerGameModePatch {
    @Inject(
            method = "attack",
            desc = "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V",
            at = @At(At.Type.TAIL)
    )
    public static void onAttack(Player player, Entity target, CallbackInfo callbackInfo) {
        if (!ZenClient.isReady()
                || player == null
                || target == null
                || player != ClientBase.mc.player) {
            return;
        }

        ZenClient.getInstance().getEventBus().call(new AttackEvent(target, true));
    }
}