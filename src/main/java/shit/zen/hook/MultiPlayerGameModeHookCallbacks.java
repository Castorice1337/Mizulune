package shit.zen.hook;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.AttackEvent;
import shit.zen.event.impl.UseBlockEvent;

/** Shared interaction callbacks used by the ASM and Fabric game-mode adapters. */
public final class MultiPlayerGameModeHookCallbacks {
    private static final boolean TRACE_INTERACTIONS =
            Boolean.getBoolean("mizulune.debug.interaction");

    private MultiPlayerGameModeHookCallbacks() {
    }

    public static void onUseItemOn(
            LocalPlayer player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (TRACE_INTERACTIONS) {
            ClientBase.logger.info(
                    "[InteractionTrace] useItemOn enter hand={} selected={} item={} block={} face={}",
                    hand,
                    player == null ? null : player.getInventory().selected,
                    player == null ? null : player.getItemInHand(hand),
                    hit == null ? null : hit.getBlockPos(),
                    hit == null ? null : hit.getDirection());
        }
        if (!ZenClient.isReady() || player == null || hand == null || hit == null
                || player != ClientBase.mc.player) {
            return;
        }
        ZenClient.getInstance().getEventBus().call(new UseBlockEvent(player, hand, hit));
    }

    public static void onUseItemOnResult(InteractionResult result) {
        if (TRACE_INTERACTIONS) {
            ClientBase.logger.info("[InteractionTrace] useItemOn result={}", result);
        }
    }

    public static void onAttack(Player player, Entity target) {
        if (!ZenClient.isReady()
                || player == null
                || target == null
                || player != ClientBase.mc.player) {
            return;
        }
        ZenClient.getInstance().getEventBus().call(new AttackEvent(target, true));
    }

    public static boolean isTraceEnabled() {
        return TRACE_INTERACTIONS;
    }
}
