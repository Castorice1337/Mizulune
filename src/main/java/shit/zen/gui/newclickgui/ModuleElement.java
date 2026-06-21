package shit.zen.gui.newclickgui;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.Generated;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import shit.zen.gui.NewClickGui;
import shit.zen.gui.newclickgui.CategoryPanel;
import shit.zen.gui.newclickgui.UIElement;
import shit.zen.gui.newclickgui.ValueTreeElementRenderer;
import shit.zen.manager.ConfigManager;
import shit.zen.modules.Module;
import shit.zen.render.FontStore;
import shit.zen.utils.animation.SmoothAnimationTimer;
import shit.zen.utils.math.Easings;
import shit.zen.utils.misc.CursorUtil;
import shit.zen.utils.render.Argb;
import shit.zen.utils.render.RenderHelper;
import shit.zen.utils.render.RenderUtil;
import shit.zen.value.Value;

public class ModuleElement
extends UIElement {
    public static final int BG_COLOR;
    private static final float BIND_ROW_HEIGHT = 18.0f;
    @Getter
    private final CategoryPanel parentPanel;
    @Getter
    private final Module module;
    @Getter
    private final SmoothAnimationTimer enabledTimer = new SmoothAnimationTimer();
    private final SmoothAnimationTimer hoveredTimer = new SmoothAnimationTimer();
    private final SmoothAnimationTimer expandTimer = new SmoothAnimationTimer();
    private final SmoothAnimationTimer settingsHeightTimer = new SmoothAnimationTimer();
    private float posX;
    private float posY;
    private float totalHeight = 20.0f;
    @Getter @Setter
    private float scrollOffset;
    @Getter @Setter
    private boolean isHovered;
    @Getter @Setter
    private boolean isExpanded;
    private static ModuleElement bindingElement;
    private static final String BUILD_TAG;

    public ModuleElement(CategoryPanel categoryPanel, Module module) {
        this.parentPanel = categoryPanel;
        this.module = module;
    }

    @Override
    public void render(NewClickGui clickGui, GuiGraphics guiGraphics, PoseStack poseStack, int mouseX, int mouseY, float alpha, float partialTicks) {
        float titleY;
        float titleWidth;
        float enabledAmount;
        float settingsTotalHeight = 0.0f;
        ValueTreeElementRenderer renderer = ValueTreeElementRenderer.getInstance();
        List<Value<?>> values = this.visibleValues();
        settingsTotalHeight += BIND_ROW_HEIGHT;
        for (Value<?> value : values) {
            settingsTotalHeight += renderer.getHeight(value);
        }
        this.settingsHeightTimer.animate(settingsTotalHeight, 0.2, Easings.EASE_OUT_POW2);
        this.settingsHeightTimer.tick();
        this.parentPanel.setCollapsed(!this.settingsHeightTimer.isDone());
        this.hoveredTimer.animate(this.isHovered ? 1.0 : 0.0, 0.2, Easings.EASE_OUT_POW2);
        this.hoveredTimer.tick();
        this.enabledTimer.animate(this.module.isEnabled() ? 1.0 : 0.0, 0.3, Easings.EASE_OUT_POW2);
        this.enabledTimer.tick();
        this.expandTimer.animate(this.isExpanded ? 1.0 : 0.0, 0.2, Easings.EASE_OUT_POW3);
        this.expandTimer.tick();
        float expandAmount = this.expandTimer.getValueF();
        this.totalHeight = 20.0f + expandAmount * this.settingsHeightTimer.getValueF();
        this.isHovered = this.parentPanel.equals(clickGui.getFocusedPanel()) && CursorUtil.isInBounds(mouseX, mouseY, this.posX, this.posY, 120.0f, this.totalHeight);
        RenderUtil.drawFilledRect(poseStack, this.posX + 1.0f, this.posY + 20.0f, 118.0f, this.totalHeight - 20.0f, Argb.withAlpha(BG_COLOR, expandAmount * alpha));
        float hoverAmount = this.hoveredTimer.getValueF();
        if (hoverAmount > 0.0f) {
            RenderUtil.drawFilledRect(poseStack, this.posX + 0.5f, this.posY, 119.0f, 20.0f, Argb.withAlpha(-1, 0.1f * alpha * hoverAmount));
        }
        if (1.0f - (enabledAmount = this.enabledTimer.getValueF()) > 0.0f) {
            FontStore.AXIFORMA_REGULAR_16.drawStringCentered(poseStack, this.module.getName(), this.posX + 60.0f, this.posY + (20.0f - FontStore.AXIFORMA_REGULAR_16.getFontHeight()) / 2.0f, Argb.withAlpha(-1, alpha * (1.0f - enabledAmount) * 0.6f));
        }
        if (enabledAmount > 0.0f) {
            titleWidth = FontStore.AXIFORMA_BOLD_16.getStringWidth(this.module.getName());
            titleY = this.posY + (20.0f - FontStore.AXIFORMA_BOLD_16.getFontHeight()) / 2.0f;
            RenderUtil.drawShadow(poseStack, this.posX + (120.0f - titleWidth) / 2.0f, titleY + FontStore.AXIFORMA_BOLD_16.getFontHeight() / 4.0f, titleWidth, FontStore.AXIFORMA_BOLD_16.getFontHeight() / 2.0f, 12, Argb.withAlpha(-13768502, alpha * enabledAmount * 0.36f));
            FontStore.AXIFORMA_BOLD_16.drawStringCentered(poseStack, this.module.getName(), this.posX + 60.0f, titleY, Argb.withAlpha(-13768502, alpha * enabledAmount));
        }
        if (this.hasExpandableContent()) {
            String arrowIcon = String.valueOf('\ueb4e');
            titleY = FontStore.MATERIAL_20.getStringWidth(arrowIcon);
            float arrowX = this.posX + 120.0f - titleY - 6.0f;
            float arrowY = this.posY + (20.0f - FontStore.MATERIAL_20.getFontHeight()) / 2.0f + 1.0f;
            RenderHelper.pushRotateAround(poseStack, arrowX + titleY / 2.0f, arrowY + FontStore.MATERIAL_20.getFontHeight() / 2.0f - 1.0f, 180.0f * expandAmount);
            FontStore.MATERIAL_20.drawString(poseStack, arrowIcon, arrowX, arrowY, Argb.withAlpha(-1, (0.8f - 0.3f * expandAmount) * alpha));
            RenderHelper.popPose(poseStack);
        }
        if (this.isExpanded) {
            titleWidth = this.posY + 20.0f;
            this.renderBindRow(poseStack, mouseX, mouseY, alpha * expandAmount, titleWidth);
            titleWidth += BIND_ROW_HEIGHT;
            int valueX = Math.round(this.posX);
            for (Value<?> value : values) {
                int height = renderer.render(this.parentPanel, clickGui, guiGraphics, poseStack, value, valueX, titleWidth, mouseX, mouseY, alpha * expandAmount, partialTicks);
                titleWidth += height;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isHovered) {
            return false;
        }
        if (CursorUtil.isInBounds((float) mouseX, (float) mouseY, this.posX, this.posY, 120.0f, 20.0f)) {
            if (button == 0) {
                this.module.toggleFromUser();
            } else if (button == 1 && this.hasExpandableContent()) {
                this.isExpanded = !this.isExpanded;
            }
            return true;
        }
        if (CursorUtil.isInBounds((float) mouseX, (float) mouseY, this.posX, this.posY + 20.0f, 120.0f, this.totalHeight - 20.0f)) {
            int valueX = Math.round(this.posX);
            int valueY = Math.round(this.posY + 20.0f);
            if (CursorUtil.isInBounds((float) mouseX, (float) mouseY, this.posX + 6.0f, valueY + 2.0f, 108.0f, BIND_ROW_HEIGHT - 4.0f)) {
                if (button == 0) {
                    bindingElement = this;
                    ValueTreeElementRenderer.getInstance().blurText();
                    return true;
                }
                return true;
            }
            valueY += Math.round(BIND_ROW_HEIGHT);
            ValueTreeElementRenderer renderer = ValueTreeElementRenderer.getInstance();
            for (Value<?> value : this.visibleValues()) {
                int height = renderer.getHeight(value);
                if (mouseY >= valueY && mouseY <= valueY + height
                        && renderer.onClick(value, valueX, valueY, (int)mouseX, (int)mouseY, button)) {
                    return true;
                }
                valueY += height;
            }
        }
        return this.isHovered;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        ValueTreeElementRenderer.getInstance().onMouseRelease(mouseX, mouseY, button);
        return false;
    }

    private List<Value<?>> visibleValues() {
        return this.module.getValueTree().getVisibleChildren();
    }

    private boolean hasValues() {
        return !this.visibleValues().isEmpty();
    }

    private boolean hasExpandableContent() {
        return true;
    }

    private boolean isBinding() {
        return bindingElement == this;
    }

    private void renderBindRow(PoseStack poseStack, int mouseX, int mouseY, float alpha, float y) {
        if (alpha <= 0.0f) {
            return;
        }
        boolean hovered = CursorUtil.isInBounds(mouseX, mouseY, this.posX + 6.0f, y + 2.0f, 108.0f, BIND_ROW_HEIGHT - 4.0f);
        int background = this.isBinding()
                ? Argb.withAlpha(CategoryPanel.ACCENT_COLOR, alpha * 0.42f)
                : Argb.withAlpha(Argb.fromRgb(60, 60, 60), alpha * (hovered ? 0.9f : 0.68f));
        RenderUtil.drawRoundedRect(poseStack, this.posX + 6.0f, y + 2.0f, 108.0f, BIND_ROW_HEIGHT - 4.0f, 3.0f, background);
        String keyName = this.isBinding() ? "..." : this.module.getBind().getName().toUpperCase();
        String text = this.clipBindText("BIND: " + keyName, 100.0f);
        FontStore.AXIFORMA_BOLD_13.drawStringCentered(poseStack, text, this.posX + 60.0f,
                y + (BIND_ROW_HEIGHT - FontStore.AXIFORMA_BOLD_13.getFontHeight()) / 2.0f,
                Argb.withAlpha(-1, alpha * 0.88f));
    }

    private String clipBindText(String text, float maxWidth) {
        String clipped = text == null ? "" : text;
        if (FontStore.AXIFORMA_BOLD_13.getStringWidth(clipped) <= maxWidth) {
            return clipped;
        }
        while (clipped.length() > 1 && FontStore.AXIFORMA_BOLD_13.getStringWidth(clipped + "...") > maxWidth) {
            clipped = clipped.substring(0, clipped.length() - 1);
        }
        return clipped + "...";
    }

    public static boolean hasActiveBindCapture() {
        return bindingElement != null;
    }

    public static boolean keyPressed(int keyCode) {
        if (bindingElement == null) {
            return false;
        }
        ModuleElement target = bindingElement;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            bindingElement = null;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            target.module.setKey(0);
            ConfigManager.requestSaveIfReady();
            bindingElement = null;
            return true;
        }
        if (keyCode != GLFW.GLFW_KEY_UNKNOWN) {
            target.module.setKey(keyCode);
            ConfigManager.requestSaveIfReady();
            bindingElement = null;
            return true;
        }
        return true;
    }

    @Override
    @Generated
    public SmoothAnimationTimer getAnimTimer() {
        return this.hoveredTimer;
    }

    @Generated
    public SmoothAnimationTimer getHoveredTimer() {
        return this.expandTimer;
    }

    @Generated
    public SmoothAnimationTimer getExpandTimer() {
        return this.settingsHeightTimer;
    }

    @Override
    @Generated
    public float getX() {
        return this.posX;
    }

    @Override
    @Generated
    public float getY() {
        return this.posY;
    }

    @Override
    @Generated
    public float getHeight() {
        return this.totalHeight;
    }

    @Override
    @Generated
    public void setX(float x) {
        this.posX = x;
    }

    @Override
    @Generated
    public void setY(float y) {
        this.posY = y;
    }

    @Override
    @Generated
    public void setHeight(float height) {
        this.totalHeight = height;
    }

    static {
        BUILD_TAG = "17";
        BG_COLOR = Argb.fromRgb(32, 32, 32);
    }
}
