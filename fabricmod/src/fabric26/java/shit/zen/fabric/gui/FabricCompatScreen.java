package shit.zen.fabric.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import shit.zen.fabric.render.FabricRenderBridge;

/** Adapts 1.20 Screen.render implementations to 26.2 extraction. */
public abstract class FabricCompatScreen extends Screen {
    protected FabricCompatScreen(Component title) {
        super(title);
    }

    protected FabricCompatScreen(Minecraft minecraft, Font font, Component title) {
        super(minecraft, font, title);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        FabricRenderBridge.withGui(extractor,
                () -> this.render(new GuiGraphics(extractor), mouseX, mouseY, partialTick));
    }

    /**
     * Legacy 1.20 screens decide for themselves whether {@code renderBackground}
     * is called. 26.2 extracts a blurred/menu background for every screen before
     * {@link #extractRenderState}; keeping that default changes the old contract
     * and turns transparent ClickGUI screens into a full-screen blur layer.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft != null) {
            this.minecraft.gui.hud.extractDeferredSubtitles();
        }
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics.extractor(), mouseX, mouseY, partialTick);
    }

    public void renderBackground(GuiGraphics graphics) {
        // Match the 1.20 in-world fallback used by legacy screens that explicitly
        // request a background. Custom transparent screens simply never call this.
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    public boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { return false; }
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) { return false; }
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) { return false; }
    public boolean charTyped(char codePoint, int modifiers) { return false; }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return this.mouseClicked(event.x(), event.y(), event.button())
                || super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        return this.mouseReleased(event.x(), event.y(), event.button())
                || super.mouseReleased(event);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return this.mouseDragged(event.x(), event.y(), event.button(), dragX, dragY)
                || super.mouseDragged(event, dragX, dragY);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return this.mouseScrolled(mouseX, mouseY, vertical)
                || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        return this.keyPressed(event.key(), event.scancode(), event.modifiers())
                || super.keyPressed(event);
    }
    @Override public boolean keyReleased(KeyEvent event) {
        return this.keyReleased(event.key(), event.scancode(), event.modifiers())
                || super.keyReleased(event);
    }
    @Override public boolean charTyped(CharacterEvent event) {
        return this.charTyped((char) event.codepoint(), 0)
                || super.charTyped(event);
    }
}
