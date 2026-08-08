package shit.zen.fabric.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Keeps the 1.20 raw HeyPixel custom-payload boundary out of shared sources.
 * Minecraft 26.2 requires typed payloads, while the protocol body is still
 * intentionally opaque to Minecraft and decoded by HeyPixelProtocolRuntime.
 */
public final class LegacyCustomPayloads {
    private static final Identifier CHANNEL = Identifier.parse("heypixel:s2cevent");
    private static final CustomPacketPayload.Type<RawPayload> TYPE =
        new CustomPacketPayload.Type<>(CHANNEL);
    private static final StreamCodec<RegistryFriendlyByteBuf, RawPayload> CODEC =
        CustomPacketPayload.codec(RawPayload::write, RawPayload::read);

    private LegacyCustomPayloads() {
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
    }

    public static FriendlyByteBuf data(ClientboundCustomPayloadPacket packet) {
        if (!(packet.payload() instanceof RawPayload raw)) {
            throw new IllegalArgumentException("Unsupported custom payload: " + packet.payload().type().id());
        }
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(raw.data()));
    }

    public static ServerboundCustomPayloadPacket packet(Identifier channel, FriendlyByteBuf source) {
        if (!CHANNEL.equals(channel)) {
            throw new IllegalArgumentException("Unsupported outbound custom payload: " + channel);
        }
        byte[] data = new byte[source.readableBytes()];
        source.getBytes(source.readerIndex(), data);
        return new ServerboundCustomPayloadPacket(new RawPayload(data));
    }

    private record RawPayload(byte[] data) implements CustomPacketPayload {
        private static RawPayload read(RegistryFriendlyByteBuf buffer) {
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return new RawPayload(data);
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeBytes(this.data);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
