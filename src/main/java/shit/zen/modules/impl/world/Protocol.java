package shit.zen.modules.impl.world;

import java.nio.file.Path;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import shit.zen.ZenClient;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.protocol.heypixel.HeyPixelProtocolRuntime;
import shit.zen.value.Value;
import shit.zen.value.ValueGroup;

public final class Protocol extends Module {
    private final HeyPixelProtocolRuntime runtime = new HeyPixelProtocolRuntime(
        mc, Path.of(ZenClient.configDir));
    private Value<String> enabledHosts;
    private Value<Boolean> traceLogger;
    private Value<Boolean> observeOnly;
    private Value<Boolean> allowLiveSend;
    private Value<Boolean> strictProviderGate;

    public Protocol() {
        super("Protocol", Category.WORLD);
    }

    @Override
    protected void configureValueTree(ValueGroup root) {
        ValueGroup connection = root.group("connection", "Connection");
        enabledHosts = connection.text("enabled_hosts", "Enabled Hosts", "pc.bjdmc.net,*.bjdmc.net");
        traceLogger = connection.bool("trace_logger", "Trace Logger", false);
        observeOnly = connection.bool("observe_only", "Observe Only", true);
        allowLiveSend = connection.bool("allow_live_send", "Allow Live Send", false);
        strictProviderGate = connection.bool("strict_provider_gate", "Strict Provider Gate", true);
    }

    @Override
    protected void onEnable() {
        updateRuntimeSettings();
        runtime.start();
    }

    @Override
    protected void onDisable() {
        runtime.stop();
    }

    @Override
    public String getSuffix() {
        return runtime.isActiveForCurrentServer() ? "HeyPixel" : "Idle";
    }

    @EventTarget
    public void onTick(TickEvent event) {
        updateRuntimeSettings();
        runtime.tick();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!event.isIncoming()) return;
        if (event.getPacket() instanceof ClientboundCustomPayloadPacket payload) {
            runtime.handle(payload);
        }
    }

    public HeyPixelProtocolRuntime getRuntime() {
        return runtime;
    }

    private void updateRuntimeSettings() {
        if (enabledHosts == null) return;
        runtime.configure(
            enabledHosts.getValue(),
            Boolean.TRUE.equals(traceLogger.getValue()),
            Boolean.TRUE.equals(observeOnly.getValue()),
            Boolean.TRUE.equals(allowLiveSend.getValue()),
            Boolean.TRUE.equals(strictProviderGate.getValue())
        );
    }
}
