package shit.zen.patch;

import asm.patchify.annotation.At;
import asm.patchify.annotation.Inject;
import asm.patchify.annotation.Patch;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.AttackEvent;
import shit.zen.event.impl.UseBlockEvent;

@Patch(MultiPlayerGameMode.class)
public class MultiPlayerGameModePatch {
    @Inject(
            method = "useItemOn",
            desc = "(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At(At.Type.HEAD)
    )
    public static void onUseItemOn(MultiPlayerGameMode gameMode, LocalPlayer player, InteractionHand hand,
                                   BlockHitResult hit, CallbackInfo callbackInfo) {
        if (!ZenClient.isReady() || player == null || hand == null || hit == null
                || player != ClientBase.mc.player) {
            return;
        }
        ZenClient.getInstance().getEventBus().call(new UseBlockEvent(player, hand, hit));
    }

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
