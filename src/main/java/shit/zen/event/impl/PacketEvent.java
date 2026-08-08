package shit.zen.event.impl;

import lombok.Getter;
import lombok.Setter;
import lombok.Generated;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import shit.zen.event.Event;

public class PacketEvent
extends Event {
    @Getter @Setter
    private Packet<?> packet;
    @Setter
    private boolean incoming;
    @Getter
    private final Connection sourceConnection;

    public boolean isIncoming() {
        return !this.incoming;
    }

    @Generated
    public boolean isIncomingRaw() {
        return this.incoming;
    }

    @Generated
    public PacketEvent(Packet<?> packet, boolean incoming) {
        this(packet, incoming, null);
    }

    public PacketEvent(Packet<?> packet, boolean incoming, Connection sourceConnection) {
        this.packet = packet;
        this.incoming = incoming;
        this.sourceConnection = sourceConnection;
    }
}
