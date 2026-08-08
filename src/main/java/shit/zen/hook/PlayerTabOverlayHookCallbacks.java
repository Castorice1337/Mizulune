package shit.zen.hook;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.ChatReceiveEvent;
import shit.zen.hud.TabListInfo;
import shit.zen.modules.impl.render.DynamicIsland;
import shit.zen.modules.impl.render.Watermark;
import shit.zen.utils.misc.ReflectionUtil;

/** Shared tab-list text filtering and render ownership. */
public final class PlayerTabOverlayHookCallbacks {
    private static final ThreadLocal<Boolean> SHIFTED = ThreadLocal.withInitial(() -> false);

    private PlayerTabOverlayHookCallbacks() {
    }

    public static List<FormattedCharSequence> header(Font font, FormattedText text, int width) {
        return font.split(filter(ChatReceiveEvent.MessageType.SYSTEM, (Component) text), width);
    }

    public static List<FormattedCharSequence> footer(Font font, FormattedText text, int width) {
        return font.split(filter(ChatReceiveEvent.MessageType.CHAT, (Component) text), width);
    }

    public static Component name(PlayerTabOverlay overlay, PlayerInfo info) {
        return filter(ChatReceiveEvent.MessageType.NAME, overlay.getNameForDisplay(info));
    }

    public static HookDecision<Void> onRenderPre(
            PlayerTabOverlay overlay,
            GuiGraphics graphics) {
        try {
            TabListInfo.header = (Component) ReflectionUtil.getStaticField(
                    overlay, "header", "net/minecraft/client/gui/components/PlayerTabOverlay");
            TabListInfo.footer = (Component) ReflectionUtil.getStaticField(
                    overlay, "footer", "net/minecraft/client/gui/components/PlayerTabOverlay");
        } catch (Exception ignored) {
        }
        SHIFTED.set(false);
        if (!ZenClient.isReady()
                || ZenClient.getInstance().getModuleManager() == null
                || ClientBase.mc == null
                || !ClientBase.mc.options.keyPlayerList.isDown()) {
            return HookDecision.pass();
        }
        if (DynamicIsland.shouldOwnTabOverlay()) {
            return HookDecision.cancel();
        }
        Watermark watermark = ZenClient.getInstance().getModuleManager().getModule(Watermark.class);
        if (watermark.isEnabled()) {
            SHIFTED.set(true);
            graphics.pose().pushPose();
            graphics.pose().translate(0.0f, 30.0f, 0.0f);
        }
        return HookDecision.pass();
    }

    public static void onRenderPost(GuiGraphics graphics) {
        if (SHIFTED.get()) {
            graphics.pose().popPose();
        }
        SHIFTED.remove();
    }

    private static Component filter(ChatReceiveEvent.MessageType type, Component component) {
        ChatReceiveEvent event = new ChatReceiveEvent(type, component);
        if (ZenClient.isReady()) {
            ZenClient.getInstance().getEventBus().call(event);
        }
        return event.getComponent();
    }
}
