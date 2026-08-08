package com.columbina.heypixel;

import io.netty.handler.codec.DecoderException;
import java.util.Arrays;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Raw bytes carried on the bidirectional heypixel:s2cevent custom payload channel. */
public record HeyPixelPayload(byte[] data) implements CustomPacketPayload {
    public static final int MAX_BYTES = 1_048_576;
    public static final ResourceLocation CHANNEL =
        ResourceLocation.fromNamespaceAndPath("heypixel", "s2cevent");
    public static final Type<HeyPixelPayload> TYPE = new Type<>(CHANNEL);
    public static final StreamCodec<RegistryFriendlyByteBuf, HeyPixelPayload> CODEC =
        CustomPacketPayload.codec(HeyPixelPayload::write, HeyPixelPayload::read);

    public HeyPixelPayload {
        if (data == null) throw new IllegalArgumentException("data");
        if (data.length > MAX_BYTES) throw new IllegalArgumentException("payload exceeds limit");
        data = data.clone();
    }

    private static HeyPixelPayload read(RegistryFriendlyByteBuf buffer) {
        int length = buffer.readableBytes();
        if (length < 0 || length > MAX_BYTES) {
            throw new DecoderException("HeyPixel payload exceeds " + MAX_BYTES + " bytes");
        }
        byte[] data = new byte[length];
        buffer.readBytes(data);
        return new HeyPixelPayload(data);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBytes(data);
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    byte[] copyData() {
        return Arrays.copyOf(data, data.length);
    }

    @Override
    public Type<HeyPixelPayload> type() {
        return TYPE;
    }
}
