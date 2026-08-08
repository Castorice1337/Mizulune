package io.github.openzen.via;

import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.libs.opennbt.tag.builtin.CompoundTag;
import com.viaversion.viaversion.protocol.packet.PacketWrapperImpl;
import io.github.openzen.via.compat.EntityEffectPacketCompat;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EntityEffectPacketCompatTest {
    @Test
    void rewritesBooleanFalseFactorData() throws Exception {
        PacketWrapper wrapper = typedPacket();
        wrapper.write(Type.BOOLEAN, false);
        wrapper.resetReader();

        EntityEffectPacketCompat.rewrite(wrapper);

        assertCommonOutput(wrapper, false);
    }

    @Test
    void rewritesBooleanTrueFactorData() throws Exception {
        CompoundTag factorData = factorData();
        PacketWrapper wrapper = typedPacket();
        wrapper.write(Type.BOOLEAN, true);
        wrapper.write(Type.COMPOUND_TAG, factorData);
        wrapper.resetReader();

        EntityEffectPacketCompat.rewrite(wrapper);

        assertCommonOutput(wrapper, true);
        assertEquals(factorData, wrapper.read(Type.NAMED_COMPOUND_TAG));
    }

    @Test
    void rewritesNullOptionalCompoundFromProtocol766Hop() throws Exception {
        PacketWrapper wrapper = typedPacket();
        wrapper.write(Type.OPTIONAL_COMPOUND_TAG, null);
        wrapper.resetReader();

        EntityEffectPacketCompat.rewrite(wrapper);

        assertCommonOutput(wrapper, false);
    }

    @Test
    void rewritesPresentOptionalCompoundFromProtocol766Hop() throws Exception {
        CompoundTag factorData = factorData();
        PacketWrapper wrapper = typedPacket();
        wrapper.write(Type.OPTIONAL_COMPOUND_TAG, factorData);
        wrapper.resetReader();

        EntityEffectPacketCompat.rewrite(wrapper);

        assertCommonOutput(wrapper, true);
        assertEquals(factorData, wrapper.read(Type.NAMED_COMPOUND_TAG));
    }

    @Test
    void treatsCompletelyMissingTailAsNoFactorData() throws Exception {
        PacketWrapper wrapper = rawPacket(false);

        EntityEffectPacketCompat.rewrite(wrapper);

        assertCommonOutput(wrapper, false);
    }

    @Test
    void doesNotHideMissingCompoundAfterTrueBoolean() throws Exception {
        PacketWrapper wrapper = rawPacket(true);

        assertThrows(Exception.class, () -> EntityEffectPacketCompat.rewrite(wrapper));
    }

    private static PacketWrapper typedPacket() {
        PacketWrapper wrapper = new PacketWrapperImpl(0, Unpooled.buffer(), null);
        wrapper.write(Type.VAR_INT, 142);
        wrapper.write(Type.VAR_INT, 8);
        wrapper.write(Type.BYTE, (byte) 1);
        wrapper.write(Type.VAR_INT, 199_999_980);
        wrapper.write(Type.BYTE, (byte) 0);
        return wrapper;
    }

    private static PacketWrapper rawPacket(boolean includeTrueBoolean) throws Exception {
        ByteBuf input = Unpooled.buffer();
        Type.VAR_INT.write(input, 142);
        Type.VAR_INT.write(input, 8);
        Type.BYTE.write(input, (byte) 1);
        Type.VAR_INT.write(input, 199_999_980);
        Type.BYTE.write(input, (byte) 0);
        if (includeTrueBoolean) {
            Type.BOOLEAN.write(input, true);
        }
        return new PacketWrapperImpl(0, input, null);
    }

    private static CompoundTag factorData() {
        CompoundTag factorData = new CompoundTag();
        factorData.putInt("padding_duration", 22);
        return factorData;
    }

    private static void assertCommonOutput(PacketWrapper wrapper, boolean hasFactorData) throws Exception {
        wrapper.resetReader();
        assertEquals(142, wrapper.read(Type.VAR_INT));
        assertEquals(9, wrapper.read(Type.VAR_INT));
        assertEquals((byte) 1, wrapper.read(Type.BYTE));
        assertEquals(199_999_980, wrapper.read(Type.VAR_INT));
        assertEquals((byte) 0, wrapper.read(Type.BYTE));
        assertEquals(hasFactorData, wrapper.read(Type.BOOLEAN));
        assertEquals(hasFactorData, wrapper.isReadable(Type.NAMED_COMPOUND_TAG, 0));
    }
}
