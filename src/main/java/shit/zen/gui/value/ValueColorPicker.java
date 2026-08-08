package shit.zen.gui.value;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import net.minecraft.util.Mth;
import shit.zen.manager.ConfigManager;
import shit.zen.utils.render.Argb;
import shit.zen.utils.render.RenderUtil;
import shit.zen.value.GradientSpec;
import shit.zen.value.MizuColor;
import shit.zen.value.Value;
import shit.zen.value.ValueType;

public final class ValueColorPicker {
    public enum Channel {
        SINGLE,
        GRADIENT_START,
        GRADIENT_END
    }

    private enum DragPart {
        NONE,
        PALETTE,
        HUE,
        ALPHA
    }

    private static final ValueColorPicker INSTANCE = new ValueColorPicker();
    private Value<?> openValue;
    private Channel openChannel = Channel.SINGLE;
    private DragPart dragging = DragPart.NONE;

    private ValueColorPicker() {
    }

    public static ValueColorPicker getInstance() {
        return INSTANCE;
    }

    public boolean isOpen(Value<?> value) {
        return this.openValue == value;
    }

    public boolean isOpen(Value<?> value, Channel channel) {
        return this.openValue == value && this.openChannel == channel;
    }

    public void toggle(Value<?> value, Channel channel) {
        if (this.isOpen(value, channel)) {
            this.close();
            return;
        }
        this.openValue = value;
        this.openChannel = channel;
        this.dragging = DragPart.NONE;
    }

    public void close() {
        this.openValue = null;
        this.openChannel = Channel.SINGLE;
        this.dragging = DragPart.NONE;
    }

    public int getExtraHeight(Value<?> value, float scale) {
        return this.isOpen(value) ? this.height(scale) : 0;
    }

    public int render(PoseStack poseStack, Value<?> value, int x, int y, int width, int mouseX, int mouseY, float alpha, float scale) {
        if (!this.isOpen(value)) {
            return 0;
        }
        Layout layout = this.layout(x, y, width, scale);
        if (this.dragging != DragPart.NONE) {
            this.applyFromMouse(value, mouseX, mouseY, layout, this.dragging);
        }

        MizuColor color = this.currentColor(value);
        float[] hsb = color.toHsb();
        RenderUtil.drawRoundedRect(poseStack, layout.x, layout.y, layout.width, layout.height, 4.0f * scale,
                this.applyAlpha(new Color(18, 18, 18, 220).getRGB(), alpha));
        RenderUtil.drawRoundedRect(poseStack, layout.paletteX - 1.0f, layout.paletteY - 1.0f,
                layout.paletteWidth + 2.0f, layout.paletteHeight + 2.0f, 3.0f * scale,
                this.applyAlpha(new Color(255, 255, 255, 28).getRGB(), alpha));
        this.drawPalette(poseStack, layout, hsb[0], color.alpha(), alpha);
        this.drawHueSlider(poseStack, layout, color, alpha);
        this.drawAlphaSlider(poseStack, layout, color, alpha);
        this.drawIndicators(poseStack, layout, hsb, color.alpha(), alpha);
        return layout.height;
    }

    public boolean onClick(Value<?> value, int x, int y, int width, int mouseX, int mouseY, int button, float scale) {
        if (!this.isOpen(value) || button != 0) {
            return false;
        }
        Layout layout = this.layout(x, y, width, scale);
        DragPart part = this.partAt(mouseX, mouseY, layout);
        if (part == DragPart.NONE) {
            return false;
        }
        this.dragging = part;
        this.applyFromMouse(value, mouseX, mouseY, layout, part);
        return true;
    }

    public void onMouseRelease() {
        if (this.dragging != DragPart.NONE) {
            ConfigManager.requestSaveIfReady();
        }
        this.dragging = DragPart.NONE;
    }

    private void drawPalette(PoseStack poseStack, Layout layout, float hue, int alphaValue, float alpha) {
        int columns = Math.max(12, Math.min(28, Math.round(layout.paletteWidth / 4.0f)));
        int rows = Math.max(8, Math.min(16, Math.round(layout.paletteHeight / 4.0f)));
        for (int row = 0; row < rows; row++) {
            float brightness = 1.0f - (float)row / (float)Math.max(1, rows - 1);
            float cellY = layout.paletteY + layout.paletteHeight * (float)row / (float)rows;
            float nextY = layout.paletteY + layout.paletteHeight * (float)(row + 1) / (float)rows;
            for (int col = 0; col < columns; col++) {
                float saturation = (float)col / (float)Math.max(1, columns - 1);
                float cellX = layout.paletteX + layout.paletteWidth * (float)col / (float)columns;
                float nextX = layout.paletteX + layout.paletteWidth * (float)(col + 1) / (float)columns;
                int color = MizuColor.ofHsb(hue, saturation, brightness, Math.round(255.0f * alpha)).toArgb();
                RenderUtil.drawFilledRect(poseStack, cellX, cellY, nextX - cellX + 0.5f, nextY - cellY + 0.5f, color);
            }
        }
    }

    private void drawHueSlider(PoseStack poseStack, Layout layout, MizuColor color, float alpha) {
        int segments = 28;
        for (int i = 0; i < segments; i++) {
            float hue = (float)i / (float)segments;
            float cellX = layout.hueX + layout.hueWidth * (float)i / (float)segments;
            float nextX = layout.hueX + layout.hueWidth * (float)(i + 1) / (float)segments;
            RenderUtil.drawFilledRect(poseStack, cellX, layout.hueY, nextX - cellX + 0.5f, layout.sliderHeight,
                    MizuColor.ofHsb(hue, 1.0f, 1.0f, Math.round(255.0f * alpha)).toArgb());
        }
    }

    private void drawAlphaSlider(PoseStack poseStack, Layout layout, MizuColor color, float alpha) {
        int checkerCells = Math.max(8, Math.round(layout.alphaWidth / 8.0f));
        for (int i = 0; i < checkerCells; i++) {
            float cellX = layout.alphaX + layout.alphaWidth * (float)i / (float)checkerCells;
            float nextX = layout.alphaX + layout.alphaWidth * (float)(i + 1) / (float)checkerCells;
            int checker = i % 2 == 0 ? Argb.fromRgb(46, 46, 46) : Argb.fromRgb(74, 74, 74);
            RenderUtil.drawFilledRect(poseStack, cellX, layout.alphaY, nextX - cellX + 0.5f, layout.sliderHeight,
                    this.applyAlpha(checker, alpha));
        }
        int segments = 28;
        for (int i = 0; i < segments; i++) {
            float ratio = (float)i / (float)Math.max(1, segments - 1);
            float cellX = layout.alphaX + layout.alphaWidth * (float)i / (float)segments;
            float nextX = layout.alphaX + layout.alphaWidth * (float)(i + 1) / (float)segments;
            int alphaColor = MizuColor.ofArgb(Math.round(255.0f * ratio * alpha), color.red(), color.green(), color.blue()).toArgb();
            RenderUtil.drawFilledRect(poseStack, cellX, layout.alphaY, nextX - cellX + 0.5f, layout.sliderHeight, alphaColor);
        }
    }

    private void drawIndicators(PoseStack poseStack, Layout layout, float[] hsb, int alphaValue, float alpha) {
        float paletteX = layout.paletteX + hsb[1] * layout.paletteWidth;
        float paletteY = layout.paletteY + (1.0f - hsb[2]) * layout.paletteHeight;
        this.drawMarker(poseStack, paletteX, paletteY, 4.0f, alpha);
        float hueX = layout.hueX + hsb[0] * layout.hueWidth;
        this.drawSliderMarker(poseStack, hueX, layout.hueY, layout.sliderHeight, alpha);
        float alphaX = layout.alphaX + ((float)alphaValue / 255.0f) * layout.alphaWidth;
        this.drawSliderMarker(poseStack, alphaX, layout.alphaY, layout.sliderHeight, alpha);
    }

    private void drawMarker(PoseStack poseStack, float x, float y, float size, float alpha) {
        RenderUtil.drawRoundedRect(poseStack, x - size, y - size, size * 2.0f, size * 2.0f, size,
                this.applyAlpha(0xCC000000, alpha));
        RenderUtil.drawRoundedRect(poseStack, x - size + 1.0f, y - size + 1.0f, size * 2.0f - 2.0f, size * 2.0f - 2.0f, size,
                this.applyAlpha(0xFFFFFFFF, alpha));
    }

    private void drawSliderMarker(PoseStack poseStack, float x, float y, float height, float alpha) {
        RenderUtil.drawRoundedRect(poseStack, x - 2.0f, y - 2.0f, 4.0f, height + 4.0f, 2.0f,
                this.applyAlpha(0xDD000000, alpha));
        RenderUtil.drawRoundedRect(poseStack, x - 1.0f, y - 1.0f, 2.0f, height + 2.0f, 1.0f,
                this.applyAlpha(0xFFFFFFFF, alpha));
    }

    private void applyFromMouse(Value<?> value, int mouseX, int mouseY, Layout layout, DragPart part) {
        MizuColor color = this.currentColor(value);
        float[] hsb = color.toHsb();
        int alpha = color.alpha();
        if (part == DragPart.PALETTE) {
            hsb[1] = Mth.clamp(((float)mouseX - layout.paletteX) / layout.paletteWidth, 0.0f, 1.0f);
            hsb[2] = Mth.clamp(1.0f - (((float)mouseY - layout.paletteY) / layout.paletteHeight), 0.0f, 1.0f);
        } else if (part == DragPart.HUE) {
            hsb[0] = Mth.clamp(((float)mouseX - layout.hueX) / layout.hueWidth, 0.0f, 1.0f);
        } else if (part == DragPart.ALPHA) {
            alpha = Math.round(Mth.clamp(((float)mouseX - layout.alphaX) / layout.alphaWidth, 0.0f, 1.0f) * 255.0f);
        }
        this.setCurrentColor(value, MizuColor.ofHsb(hsb[0], hsb[1], hsb[2], alpha));
    }

    private DragPart partAt(int mouseX, int mouseY, Layout layout) {
        if (this.inBounds(mouseX, mouseY, layout.paletteX, layout.paletteY, layout.paletteWidth, layout.paletteHeight)) {
            return DragPart.PALETTE;
        }
        if (this.inBounds(mouseX, mouseY, layout.hueX, layout.hueY, layout.hueWidth, layout.sliderHeight)) {
            return DragPart.HUE;
        }
        if (this.inBounds(mouseX, mouseY, layout.alphaX, layout.alphaY, layout.alphaWidth, layout.sliderHeight)) {
            return DragPart.ALPHA;
        }
        return DragPart.NONE;
    }

    private MizuColor currentColor(Value<?> value) {
        if (value.getType() == ValueType.GRADIENT && value.getValue() instanceof GradientSpec gradient) {
            return this.openChannel == Channel.GRADIENT_END ? gradient.end() : gradient.start();
        }
        if (value.getValue() instanceof MizuColor color) {
            return color;
        }
        return MizuColor.ofRgb(255, 255, 255);
    }

    @SuppressWarnings("unchecked")
    private void setCurrentColor(Value<?> value, MizuColor color) {
        if (value.getType() == ValueType.GRADIENT && value.getValue() instanceof GradientSpec gradient) {
            Value<GradientSpec> gradientValue = (Value<GradientSpec>)value;
            gradientValue.setValue(this.openChannel == Channel.GRADIENT_END
                    ? new GradientSpec(gradient.start(), color)
                    : new GradientSpec(color, gradient.end()));
            return;
        }
        if (value.getType() == ValueType.COLOR) {
            ((Value<MizuColor>)value).setValue(color);
        }
    }

    private Layout layout(int x, int y, int width, float scale) {
        int padding = Math.max(4, Math.round(6.0f * scale));
        int gap = Math.max(3, Math.round(5.0f * scale));
        int sliderHeight = Math.max(6, Math.round(8.0f * scale));
        int paletteHeight = Math.max(30, Math.round(42.0f * scale));
        int height = padding + paletteHeight + gap + sliderHeight + gap + sliderHeight + padding;
        int innerWidth = Math.max(48, width - padding * 2);
        int paletteX = x + padding;
        int paletteY = y + padding;
        int hueY = paletteY + paletteHeight + gap;
        int alphaY = hueY + sliderHeight + gap;
        return new Layout(x, y, width, height, paletteX, paletteY, innerWidth, paletteHeight,
                paletteX, hueY, innerWidth, paletteX, alphaY, innerWidth, sliderHeight);
    }

    private int height(float scale) {
        return this.layout(0, 0, 100, scale).height;
    }

    private boolean inBounds(int mouseX, int mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private int applyAlpha(int color, float alpha) {
        int origAlpha = color >>> 24;
        int nextAlpha = Math.round(origAlpha * Mth.clamp(alpha, 0.0f, 1.0f));
        return nextAlpha << 24 | color & 0xFFFFFF;
    }

    private record Layout(int x, int y, int width, int height,
                          int paletteX, int paletteY, int paletteWidth, int paletteHeight,
                          int hueX, int hueY, int hueWidth,
                          int alphaX, int alphaY, int alphaWidth,
                          int sliderHeight) {
    }
}
