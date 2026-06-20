package shit.zen.gui;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import shit.zen.gui.newclickgui.CategoryPanel;
import shit.zen.gui.newclickgui.ValueTreeElementRenderer;
import shit.zen.manager.ConfigManager;
import shit.zen.modules.Category;
import shit.zen.render.Renderer;
import shit.zen.utils.animation.SmoothAnimationTimer;
import shit.zen.utils.math.Easings;

public class NewClickGui
extends Screen {
    public static final NewClickGui INSTANCE = new NewClickGui();
    private final List<CategoryPanel> categoryPanels = new ArrayList<>();
    private CategoryPanel focusedPanel;
    @Getter
    private boolean closing = false;
    @Getter
    private final SmoothAnimationTimer closeAnim = new SmoothAnimationTimer();

    public NewClickGui() {
        super(Component.literal("ClickGui"));
        for (Category category : Category.values()) {
            this.categoryPanels.add(new CategoryPanel(category));
        }
    }

    protected void init() {
        this.focusedPanel = this.categoryPanels.isEmpty() ? null : this.categoryPanels.get(0);
        float panelX = (float)this.width / 2.0f - 380.0f;
        for (CategoryPanel categoryPanel : this.categoryPanels) {
            categoryPanel.setX(panelX);
            categoryPanel.setY(36.0f);
            panelX += 128.0f;
        }
    }

    public void render(@NonNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.closeAnim.animate(this.closing ? 0.0 : 1.0, 0.2, Easings.EASE_OUT_POW2);
        this.closeAnim.tick();
        float closeProgress = this.closeAnim.getValueF();
        if (Mth.equal(closeProgress, 0.0f) && this.closing) {
            this.closing = false;
            ConfigManager.requestSaveIfReady();
            super.onClose();
            this.categoryPanels.forEach(CategoryPanel::reset);
            return;
        }
        Renderer.render(guiGraphics, drawContext -> {
            for (CategoryPanel categoryPanel : this.categoryPanels) {
                categoryPanel.render(this, guiGraphics, guiGraphics.pose(), mouseX, mouseY, closeProgress, partialTicks);
            }
        });
    }

    public void onClose() {
        this.closing = true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (CategoryPanel categoryPanel : this.categoryPanels) {
            if (!categoryPanel.mouseClicked(mouseX, mouseY, button)) continue;
            this.focusedPanel = categoryPanel;
            return true;
        }
        ValueTreeElementRenderer.getInstance().blurText();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (ValueTreeElementRenderer.getInstance().charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ValueTreeElementRenderer.getInstance().keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (CategoryPanel categoryPanel : this.categoryPanels) {
            categoryPanel.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    public boolean isPauseScreen() {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        for (CategoryPanel categoryPanel : this.categoryPanels) {
            if (!categoryPanel.mouseScrolled(mouseX, mouseY, scrollDelta)) continue;
            return true;
        }
        return false;
    }

    public CategoryPanel getFocusedPanel() {
        return this.focusedPanel;
    }

    public void setFocusedPanel(CategoryPanel focusedPanel) {
        this.focusedPanel = focusedPanel;
    }
}
