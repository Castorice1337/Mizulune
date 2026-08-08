package shit.zen.fabric.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

/**
 * Keeps protocol-shape changes introduced after 1.20.1 out of the shared
 * gameplay sources. Callers keep expressing the same operation while this
 * adapter supplies fields that became mandatory in 26.2.
 */
public final class FabricPacketCompat {
    private FabricPacketCompat() {
    }

    public static ServerboundUseItemPacket useItem(InteractionHand hand, int sequence) {
        Minecraft minecraft = Minecraft.getInstance();
        float yaw = minecraft.player == null ? 0.0F : minecraft.player.getYRot();
        float pitch = minecraft.player == null ? 0.0F : minecraft.player.getXRot();
        return new ServerboundUseItemPacket(hand, sequence, yaw, pitch);
    }

    public static ServerboundInteractPacket attack(Entity entity, boolean usingSecondaryAction) {
        return new ServerboundInteractPacket(entity.getId(), null, null, usingSecondaryAction);
    }
}
