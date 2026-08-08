package shit.zen.utils.misc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class PacketUtilDirectSendTest {
    @Test
    void preparedDirectPacketBypassesBothOutboundEventLayersExactlyOnce() {
        FriendlyByteBuf payload = new FriendlyByteBuf(Unpooled.wrappedBuffer(new byte[]{0x0c}));
        try {
            ServerboundCustomPayloadPacket packet = new ServerboundCustomPayloadPacket(
                ResourceLocation.tryParse("heypixel:s2cevent"), payload);

            PacketUtil.prepareDirectSend(packet);
            PacketUtil.SendPreparation preparation = PacketUtil.prepareSend(packet);

            assertFalse(preparation.cancelled());
            assertTrue(preparation.bypass());
            PacketUtil.cancelDirectSend(packet);
        } finally {
            payload.release();
        }
    }
}
