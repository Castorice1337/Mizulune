package shit.zen.event.impl;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import shit.zen.event.EventMarker;

public record UseBlockEvent(LocalPlayer player, InteractionHand hand, BlockHitResult hit) implements EventMarker {
}
