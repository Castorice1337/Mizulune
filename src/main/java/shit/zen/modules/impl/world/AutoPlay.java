package shit.zen.modules.impl.world;

import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import shit.zen.event.impl.DisconnectEvent;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.value.impl.NumberValue;
import shit.zen.utils.misc.ChatUtil;
import shit.zen.event.EventTarget;

public class AutoPlay
extends Module {
    public static AutoPlay instance;
    private final NumberValue delay = new NumberValue("Delay", 2.0, 0.0, 10.0, 0.1);
    public long disconnectTime = -1L;
    public boolean pendingDisconnect = false;
    public long reconnectTime = -1L;

    public AutoPlay() {
        super("AutoPlay", Category.WORLD);
        instance = this;
    }

    @Override
    protected void onEnable() {
        this.pendingDisconnect = false;
        this.disconnectTime = -1L;
        this.reconnectTime = -1L;
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.pendingDisconnect = false;
        this.disconnectTime = -1L;
        this.reconnectTime = -1L;
        super.onDisable();
    }

    @EventTarget
    public void onDisconnect(DisconnectEvent disconnectEvent) {
        this.pendingDisconnect = false;
        this.disconnectTime = -1L;
        this.reconnectTime = -1L;
    }

    @EventTarget
    public void onPacket(PacketEvent packetEvent) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (packetEvent.getPacket() instanceof ClientboundSystemChatPacket chatPacket) {
            String message = chatPacket.content().getString().replaceAll("\u00a7[0-9a-fk-or]", "").trim();
            if (message.contains("\u5730\u56fe\u8bc4\u5206")) {
                ChatUtil.print("1");
                if (this.disconnectTime == -1L) {
                    this.disconnectTime = System.currentTimeMillis();
                    this.pendingDisconnect = true;
                }
            } else if (message.contains("\u6e38\u620f\u5c06\u5728 1 \u79d2 \u540e\u5f00\u59cb")) {
                ChatUtil.print("2");
                this.disconnectTime = -1L;
                this.pendingDisconnect = false;
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent tickEvent) {
        long elapsed;
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (this.reconnectTime != -1L) {
            if (System.currentTimeMillis() - this.reconnectTime > 1000L) {
                this.disconnectTime = -1L;
                this.pendingDisconnect = false;
                this.reconnectTime = -1L;
            }
            return;
        }
        if (this.disconnectTime != -1L && (double)(elapsed = System.currentTimeMillis() - this.disconnectTime) >= this.delay.getValue().doubleValue() * 1000.0) {
            mc.player.connection.sendCommand("again");
            this.reconnectTime = System.currentTimeMillis();
        }
    }

    public NumberValue getDelay() {
        return this.delay;
    }
}
