package io.github.openzen.via.compat;

import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.libs.opennbt.tag.builtin.CompoundTag;

import java.io.EOFException;
import java.nio.BufferUnderflowException;

/**
 * Bridges the incompatible factor-data representation produced by the 766 -> 763
 * ViaBackwards protocol chain for ENTITY_EFFECT packets.
 */
public final class EntityEffectPacketCompat {
    private EntityEffectPacketCompat() {
    }

    public static void rewrite(PacketWrapper wrapper) throws Exception {
        wrapper.passthrough(Type.VAR_INT); // Entity ID
        wrapper.write(Type.VAR_INT, wrapper.read(Type.VAR_INT) + 1); // Effect ID
        wrapper.passthrough(Type.BYTE); // Amplifier
        wrapper.passthrough(Type.VAR_INT); // Duration
        wrapper.passthrough(Type.BYTE); // Flags

        CompoundTag factorData = readFactorData(wrapper);
        wrapper.write(Type.BOOLEAN, factorData != null);
        if (factorData != null) {
            wrapper.write(Type.NAMED_COMPOUND_TAG, factorData);
        }
    }

    private static CompoundTag readFactorData(PacketWrapper wrapper) throws Exception {
        // Native 1.20.2 form: a presence boolean followed by an unnamed compound.
        // Check this first because isReadable matches by base class and may also see
        // the compound that follows a readable Boolean.
        if (wrapper.isReadable(Type.BOOLEAN, 0)) {
            return readBooleanAndCompound(wrapper);
        }

        // The 1.20.5 -> 1.20.3 ViaBackwards hop writes this as one typed value.
        if (wrapper.isReadable(Type.OPTIONAL_COMPOUND_TAG, 0)) {
            return wrapper.read(Type.OPTIONAL_COMPOUND_TAG);
        }

        // Raw packet input is not visible through isReadable. A missing tail is a
        // valid compatibility case for the 1.20.5 form; malformed compound data is not.
        final boolean present;
        try {
            present = wrapper.read(Type.BOOLEAN);
        } catch (Exception exception) {
            if (isEndOfInput(exception)) {
                return null;
            }
            throw exception;
        }
        return present ? wrapper.read(Type.COMPOUND_TAG) : null;
    }

    private static CompoundTag readBooleanAndCompound(PacketWrapper wrapper) throws Exception {
        if (!wrapper.read(Type.BOOLEAN)) {
            return null;
        }
        return wrapper.read(Type.COMPOUND_TAG);
    }

    private static boolean isEndOfInput(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof IndexOutOfBoundsException
                || cause instanceof EOFException
                || cause instanceof BufferUnderflowException) {
                return true;
            }
        }
        return false;
    }
}
