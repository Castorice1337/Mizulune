package shit.zen.event.impl;

import lombok.Generated;
import net.minecraft.network.protocol.Packet;
import shit.zen.event.Event;

public class PacketSendEvent
extends Event {
    private final Packet<?> packet;

    @Generated
    public PacketSendEvent(Packet<?> packet) {
        this.packet = packet;
    }
}