package shit.zen.hook;

import org.lwjgl.glfw.GLFW;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.hud.HudElement;

/** Shared chat-screen HUD editing semantics for Patchify and Mixin. */
public final class ChatScreenHookCallbacks {
    private ChatScreenHookCallbacks() {
    }

    public static void onRender(int mouseX, int mouseY) {
        try {
            ZenClient client = ZenClient.instance;
            if (client == null || client.getHudManager() == null || ClientBase.mc == null) {
                return;
            }
            boolean leftDown = GLFW.glfwGetMouseButton(
                    ClientBase.mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            for (HudElement element : client.getHudManager().getHudElements()) {
                if (!element.isEnabled() || !element.isDragging()) {
                    continue;
                }
                element.mouseDragged(mouseX, mouseY);
                if (!leftDown) {
                    element.setDragging(false);
                }
            }
        } catch (Exception exception) {
            ClientBase.logger.error("Failed to update HUD dragging from chat screen", exception);
        }
    }

    public static void onMouseClicked(double mouseX, double mouseY, int button) {
        try {
            ZenClient client = ZenClient.instance;
            if (client == null || client.getHudManager() == null) {
                return;
            }
            for (HudElement element : client.getHudManager().getHudElements()) {
                if (element.isEnabled() && element.mousePressed((int) mouseX, (int) mouseY, button)) {
                    break;
                }
            }
        } catch (Exception exception) {
            ClientBase.logger.error("Failed to start HUD dragging from chat screen", exception);
        }
    }
}
