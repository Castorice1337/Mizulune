package shit.zen.gui.panel.value;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import shit.zen.gui.PanelClickGui;
import shit.zen.gui.value.ValueColorPicker;
import shit.zen.render.CustomFont;
import shit.zen.manager.ConfigManager;
import shit.zen.render.FontPresets;
import shit.zen.render.FontRenderer;
import shit.zen.render.GlHelper;
import shit.zen.render.Renderer;
import shit.zen.render.TextGlow;
import shit.zen.utils.render.RenderUtil;
import shit.zen.value.GradientSpec;
import shit.zen.value.MizuColor;
import shit.zen.value.ModeValueGroup;
import shit.zen.value.NumericRange;
import shit.zen.value.ToggleValueGroup;
import shit.zen.value.Value;
import shit.zen.value.ValueGroup;
import shit.zen.value.ValueType;

public final class ValueRendererRegistry {
    private static final ValueRendererRegistry INSTANCE = new ValueRendererRegistry();
    private static final ValueColorPicker COLOR_PICKER = ValueColorPicker.getInstance();
    private final Map<Value<?>, Boolean> expanded = new HashMap<>();

    private ValueRendererRegistry() {
    }

    public static ValueRendererRegistry getInstance() {
        return INSTANCE;
    }

    public int render(GuiGraphics guiGraphics, Value<?> value, int x, int y, int width,
                      int mouseX, int mouseY, float alpha, float scale) {
        return this.render(guiGraphics, value, x, y, width, mouseX, mouseY, alpha, scale, 0);
    }

    private int render(GuiGraphics guiGraphics, Value<?> value, int x, int y, int width,
                       int mouseX, int mouseY, float alpha, float scale, int depth) {
        if (!value.isVisible()) {
            return 0;
        }
        int indent = Math.round(depth * 12.0f * scale);
        int rowX = x + indent;
        int rowWidth = Math.max(40, width - indent);
        if (value instanceof ValueGroup group) {
            int used = this.renderGroupHeader(guiGraphics, group, rowX, y, rowWidth, alpha, scale);
            if (this.isExpanded(group) && group.areChildrenVisible()) {
                for (Value<?> child : group.getVisibleChildren()) {
                    used += this.render(guiGraphics, child, x, y + used, width, mouseX, mouseY, alpha, scale, depth + 1);
                }
            }
            return used;
        }
        return this.renderLeaf(guiGraphics, value, rowX, y, rowWidth, mouseX, mouseY, alpha, scale);
    }

    public boolean onClick(Value<?> value, int x, int y, int width, int mouseX, int mouseY, int button, float scale) {
        return this.onClick(value, x, y, width, mouseX, mouseY, button, scale, 0);
    }

    private boolean onClick(Value<?> value, int x, int y, int width, int mouseX, int mouseY, int button, float scale, int depth) {
        if (!value.isVisible()) {
            return false;
        }
        int indent = Math.round(depth * 12.0f * scale);
        int rowX = x + indent;
        int rowWidth = Math.max(40, width - indent);
        if (value instanceof ValueGroup group) {
            int headerHeight = this.groupHeaderHeight(scale);
            if (mouseY >= y && mouseY <= y + headerHeight && this.onGroupHeaderClick(group, rowX, y, rowWidth, mouseX, mouseY, button, scale)) {
                return true;
            }
            int childY = y + headerHeight;
            if (this.isExpanded(group) && group.areChildrenVisible()) {
                for (Value<?> child : group.getVisibleChildren()) {
                    int childHeight = this.getHeight(child, scale, depth + 1);
                    if (mouseY >= childY && mouseY <= childY + childHeight
                            && this.onClick(child, x, childY, width, mouseX, mouseY, button, scale, depth + 1)) {
                        return true;
                    }
                    childY += childHeight;
                }
            }
            return false;
        }
        return this.onLeafClick(value, rowX, y, rowWidth, mouseX, mouseY, button, scale);
    }

    public int getHeight(Value<?> value, float scale) {
        return this.getHeight(value, scale, 0);
    }

    private int getHeight(Value<?> value, float scale, int depth) {
        if (!value.isVisible()) {
            return 0;
        }
        if (value instanceof ValueGroup group) {
            int height = this.groupHeaderHeight(scale);
            if (this.isExpanded(group) && group.areChildrenVisible()) {
                for (Value<?> child : group.getVisibleChildren()) {
                    height += this.getHeight(child, scale, depth + 1);
                }
            }
            return height;
        }
        return this.leafHeight(value, scale);
    }

    public void onMouseMove(double mouseX, double mouseY) {
    }

    public void onMouseRelease(double mouseX, double mouseY, int button) {
        COLOR_PICKER.onMouseRelease();
    }

    private int renderGroupHeader(GuiGraphics guiGraphics, ValueGroup group, int x, int y, int width, float alpha, float scale) {
        int rowHeight = this.groupHeaderHeight(scale);
        FontRenderer font = FontPresets.axiformaBold(13.0f * scale);
        String prefix = this.isExpanded(group) ? "[-] " : "[+] ";
        float textY = y + rowHeight / 2.0f - font.getMetrics().capHeight() / 2.0f;
        int bg = group instanceof ToggleValueGroup && !((ToggleValueGroup)group).isEnabled()
                ? new Color(255, 255, 255, 10).getRGB()
                : new Color(255, 255, 255, 16).getRGB();
        RenderUtil.drawRoundedRect(guiGraphics.pose(), x, y + Math.round(3.0f * scale), width, rowHeight - Math.round(6.0f * scale), 4.0f * scale, this.applyAlpha(bg, alpha));
        this.drawText(guiGraphics, prefix + group.getDisplayName(), x + Math.round(6.0f * scale), textY, font,
                this.applyAlpha(0xFFEAF7FF, alpha), this.applyAlpha(0x66FFFFFF, alpha), 6.0f * scale);
        if (group instanceof ToggleValueGroup toggleGroup) {
            this.drawToggle(guiGraphics, x + width - Math.round(28.0f * scale), y + Math.round(7.0f * scale), toggleGroup.isEnabled(), alpha, scale);
        } else if (group instanceof ModeValueGroup modeGroup) {
            this.drawPill(guiGraphics, modeGroup.getActiveModeId(), x + width - Math.round(96.0f * scale), y + Math.round(5.0f * scale),
                    Math.round(90.0f * scale), Math.round(14.0f * scale), alpha, scale);
        }
        return rowHeight;
    }

    private int renderLeaf(GuiGraphics guiGraphics, Value<?> value, int x, int y, int width,
                           int mouseX, int mouseY, float alpha, float scale) {
        int rowHeight = this.leafHeight(value, scale);
        int baseHeight = this.leafBaseHeight(scale);
        FontRenderer nameFont = FontPresets.axiformaRegular(13.0f * scale);
        float nameY = y + Math.round(24.0f * scale) / 2.0f - nameFont.getMetrics().capHeight() / 2.0f;
        this.drawText(guiGraphics, value.getDisplayName(), x + Math.round(3.0f * scale), nameY, nameFont,
                this.applyAlpha(-1, alpha), this.applyAlpha(new Color(255, 255, 255, 90).getRGB(), alpha), 6.0f * scale);

        switch (value.getType()) {
            case BOOLEAN -> this.drawToggle(guiGraphics, x + width - Math.round(26.0f * scale), y + Math.round(7.0f * scale),
                    Boolean.TRUE.equals(value.getValue()), alpha, scale);
            case INTEGER, DECIMAL, KEY_BIND -> this.drawPill(guiGraphics, this.formatNumber((Number)value.getValue()),
                    x + width - Math.round(84.0f * scale), y + Math.round(5.0f * scale), Math.round(78.0f * scale), Math.round(14.0f * scale), alpha, scale);
            case ENUM -> this.drawPill(guiGraphics, String.valueOf(value.getValue()),
                    x + width - Math.round(96.0f * scale), y + Math.round(5.0f * scale), Math.round(90.0f * scale), Math.round(14.0f * scale), alpha, scale);
            case MULTI_ENUM -> this.renderMultiEnum(guiGraphics, value, x, y + Math.round(22.0f * scale), width, alpha, scale);
            case COLOR -> this.renderColor(guiGraphics, (MizuColor)value.getValue(), x + width - Math.round(74.0f * scale), y + Math.round(4.0f * scale), alpha, scale);
            case GRADIENT -> this.renderGradient(guiGraphics, (GradientSpec)value.getValue(), x + width - Math.round(98.0f * scale), y + Math.round(4.0f * scale), alpha, scale);
            case INT_RANGE, DECIMAL_RANGE -> this.drawPill(guiGraphics, this.formatRange((NumericRange)value.getValue()),
                    x + width - Math.round(104.0f * scale), y + Math.round(5.0f * scale), Math.round(98.0f * scale), Math.round(14.0f * scale), alpha, scale);
            default -> this.drawPill(guiGraphics, String.valueOf(value.getValue()),
                    x + width - Math.round(110.0f * scale), y + Math.round(5.0f * scale), Math.round(104.0f * scale), Math.round(14.0f * scale), alpha, scale);
        }
        if (value.getType() == ValueType.COLOR || value.getType() == ValueType.GRADIENT) {
            COLOR_PICKER.render(guiGraphics.pose(), value, x, y + baseHeight, width, mouseX, mouseY, alpha, scale);
        }
        return rowHeight;
    }

    private boolean onGroupHeaderClick(ValueGroup group, int x, int y, int width, int mouseX, int mouseY, int button, float scale) {
        if (group instanceof ToggleValueGroup toggleGroup) {
            int toggleX = x + width - Math.round(28.0f * scale);
            int toggleY = y + Math.round(7.0f * scale);
            if (button == 0 && inBounds(mouseX, mouseY, toggleX, toggleY, Math.round(20.0f * scale), Math.round(10.0f * scale))) {
                toggleGroup.setEnabled(!toggleGroup.isEnabled());
                ConfigManager.requestSaveIfReady();
                this.addToast(group.getDisplayName() + (toggleGroup.isEnabled() ? " On" : " Off"));
                return true;
            }
        }
        if (group instanceof ModeValueGroup modeGroup) {
            int pillX = x + width - Math.round(96.0f * scale);
            int pillY = y + Math.round(5.0f * scale);
            if (button == 0 && inBounds(mouseX, mouseY, pillX, pillY, Math.round(90.0f * scale), Math.round(14.0f * scale))) {
                this.cycleEnum(modeGroup.getActiveValue(), 1);
                ConfigManager.requestSaveIfReady();
                return true;
            }
        }
        if (button == 0 || button == 1) {
            this.expanded.put(group, !this.isExpanded(group));
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean onLeafClick(Value<?> value, int x, int y, int width, int mouseX, int mouseY, int button, float scale) {
        if (button != 0 && button != 1) {
            return false;
        }
        int baseHeight = this.leafBaseHeight(scale);
        if ((value.getType() == ValueType.COLOR || value.getType() == ValueType.GRADIENT)
                && COLOR_PICKER.onClick(value, x, y + baseHeight, width, mouseX, mouseY, button, scale)) {
            return true;
        }
        switch (value.getType()) {
            case BOOLEAN -> {
                value.setRawValue(!Boolean.TRUE.equals(value.getValue()));
                ConfigManager.requestSaveIfReady();
                return true;
            }
            case INTEGER, DECIMAL, KEY_BIND -> {
                this.bumpNumber((Value<Number>)value, button == 0 ? 1.0 : -1.0);
                ConfigManager.requestSaveIfReady();
                return true;
            }
            case ENUM -> {
                this.cycleEnum((Value<String>)value, button == 0 ? 1 : -1);
                ConfigManager.requestSaveIfReady();
                return true;
            }
            case MULTI_ENUM -> {
                return this.onMultiEnumClick((Value<List<String>>)value, x, y + Math.round(22.0f * scale), width, mouseX, mouseY, scale);
            }
            case COLOR -> {
                COLOR_PICKER.toggle(value, ValueColorPicker.Channel.SINGLE);
                return true;
            }
            case GRADIENT -> {
                COLOR_PICKER.toggle(value, this.gradientChannel(value, x, width, mouseX, button, scale));
                return true;
            }
            case INT_RANGE, DECIMAL_RANGE -> {
                this.bumpRange((Value<NumericRange>)value, mouseX > x + width / 2, button == 0 ? 1.0 : -1.0);
                ConfigManager.requestSaveIfReady();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void bumpNumber(Value<Number> value, double direction) {
        double current = value.getValue().doubleValue();
        double min = numberMetadata(value, "min", -Double.MAX_VALUE);
        double max = numberMetadata(value, "max", Double.MAX_VALUE);
        double step = numberMetadata(value, "step", 1.0);
        double next = Mth.clamp(current + step * direction, min, max);
        if (value.getType() == ValueType.INTEGER || value.getType() == ValueType.KEY_BIND) {
            value.setValue((int)Math.round(next));
        } else {
            value.setValue(next);
        }
    }

    @SuppressWarnings("unchecked")
    private void cycleEnum(Value<String> value, int direction) {
        Object optionsObject = value.getMetadata().get("options");
        if (!(optionsObject instanceof List<?> options) || options.isEmpty()) {
            return;
        }
        int current = 0;
        for (int i = 0; i < options.size(); i++) {
            if (String.valueOf(options.get(i)).equalsIgnoreCase(String.valueOf(value.getValue()))) {
                current = i;
                break;
            }
        }
        int next = Math.floorMod(current + direction, options.size());
        value.setValue(String.valueOf(options.get(next)));
    }

    private boolean onMultiEnumClick(Value<List<String>> value, int x, int y, int width, int mouseX, int mouseY, float scale) {
        Object optionsObject = value.getMetadata().get("options");
        if (!(optionsObject instanceof List<?> options)) {
            return false;
        }
        int rowHeight = Math.round(18.0f * scale);
        for (Object optionObject : options) {
            String option = String.valueOf(optionObject);
            if (inBounds(mouseX, mouseY, x, y, width, rowHeight)) {
                List<String> next = new java.util.ArrayList<>(value.getValue());
                if (next.contains(option)) {
                    next.remove(option);
                } else {
                    next.add(option);
                }
                value.setValue(next);
                ConfigManager.requestSaveIfReady();
                return true;
            }
            y += rowHeight;
        }
        return false;
    }

    private void bumpRange(Value<NumericRange> value, boolean upper, double direction) {
        NumericRange range = value.getValue();
        double step = range.step() <= 0.0 ? 1.0 : range.step();
        double lower = range.lower();
        double high = range.upper();
        if (upper) {
            high = range.clamp(high + step * direction);
        } else {
            lower = range.clamp(lower + step * direction);
        }
        value.setValue(new NumericRange(lower, high, range.min(), range.max(), range.step(), range.integer()));
    }

    private void renderMultiEnum(GuiGraphics guiGraphics, Value<?> value, int x, int y, int width, float alpha, float scale) {
        Object optionsObject = value.getMetadata().get("options");
        if (!(optionsObject instanceof List<?> options)) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<String> selected = (List<String>)value.getValue();
        int rowHeight = Math.round(18.0f * scale);
        FontRenderer font = FontPresets.axiformaRegular(12.0f * scale);
        for (Object optionObject : options) {
            String option = String.valueOf(optionObject);
            boolean on = selected.contains(option);
            int color = on ? 0xFF8FD694 : 0xFFAAAAAA;
            float textY = y + rowHeight / 2.0f - font.getMetrics().capHeight() / 2.0f;
            this.drawText(guiGraphics, (on ? "[x] " : "[ ] ") + option, x + Math.round(8.0f * scale), textY, font, this.applyAlpha(color, alpha));
            y += rowHeight;
        }
    }

    private void renderColor(GuiGraphics guiGraphics, MizuColor color, int x, int y, float alpha, float scale) {
        int size = Math.round(14.0f * scale);
        RenderUtil.drawRoundedRect(guiGraphics.pose(), x, y, size, size, 3.0f * scale, this.applyAlpha(color.toArgb(), alpha));
        this.drawPill(guiGraphics, color.toHexArgb(), x + size + Math.round(4.0f * scale), y, Math.round(54.0f * scale), size, alpha, scale);
    }

    private void renderGradient(GuiGraphics guiGraphics, GradientSpec gradient, int x, int y, float alpha, float scale) {
        int size = Math.round(14.0f * scale);
        RenderUtil.drawRoundedRect(guiGraphics.pose(), x, y, size, size, 3.0f * scale, this.applyAlpha(gradient.start().toArgb(), alpha));
        RenderUtil.drawRoundedRect(guiGraphics.pose(), x + size + Math.round(4.0f * scale), y, size, size, 3.0f * scale, this.applyAlpha(gradient.end().toArgb(), alpha));
    }

    private void drawToggle(GuiGraphics guiGraphics, int x, int y, boolean on, float alpha, float scale) {
        int width = Math.round(20.0f * scale);
        int height = Math.round(10.0f * scale);
        int color = on ? new Color(76, 175, 80, 190).getRGB() : new Color(158, 158, 158, 150).getRGB();
        RenderUtil.drawRoundedRect(guiGraphics.pose(), x, y, width, height, height / 2.0f, this.applyAlpha(color, alpha));
        int knob = Math.max(4, height - Math.round(3.0f * scale));
        int knobX = on ? x + width - knob - Math.round(2.0f * scale) : x + Math.round(2.0f * scale);
        RenderUtil.drawRoundedRect(guiGraphics.pose(), knobX, y + (height - knob) / 2.0f, knob, knob, knob / 2.0f, this.applyAlpha(-1, alpha));
    }

    private void drawPill(GuiGraphics guiGraphics, String text, int x, int y, int width, int height, float alpha, float scale) {
        RenderUtil.drawRoundedRect(guiGraphics.pose(), x, y, width, height, height / 2.0f, this.applyAlpha(new Color(255, 255, 255, 18).getRGB(), alpha));
        FontRenderer font = FontPresets.axiformaRegular(11.0f * scale);
        String clipped = text == null ? "" : text;
        float textWidth = GlHelper.getStringWidth(clipped, font);
        while (textWidth > width - 8.0f * scale && clipped.length() > 1) {
            clipped = clipped.substring(0, clipped.length() - 1);
            textWidth = GlHelper.getStringWidth(clipped + ".", font);
        }
        if (!clipped.equals(text)) {
            clipped += ".";
        }
        float textX = x + (width - GlHelper.getStringWidth(clipped, font)) / 2.0f;
        float textY = y + height / 2.0f - font.getMetrics().capHeight() / 2.0f;
        this.drawText(guiGraphics, clipped, textX, textY, font, this.applyAlpha(0xFFEAF7FF, alpha));
    }

    private float drawText(GuiGraphics guiGraphics, String text, float x, float y, FontRenderer fontRenderer, int color) {
        return this.drawText(guiGraphics, text, x, y, fontRenderer, color, 0, 0.0f);
    }

    private float drawText(GuiGraphics guiGraphics, String text, float x, float y, FontRenderer fontRenderer,
                           int color, int glowColor, float radius) {
        if (Renderer.getCanvas() != null) {
            return TextGlow.drawGlowText(text, x, y, fontRenderer, color, glowColor, radius);
        }
        CustomFont customFont = fontRenderer.getFont();
        if (customFont == null) {
            return x;
        }
        if ((glowColor >>> 24) > 0 && radius > 0.0f) {
            customFont.drawStringWithShadow(guiGraphics.pose(), text, x, y, color);
        } else {
            customFont.drawString(guiGraphics.pose(), text, x, y, color);
        }
        return x + customFont.getStringWidth(text);
    }

    private void addToast(String message) {
        if (PanelClickGui.panelClickGui != null) {
            PanelClickGui.panelClickGui.addToast(message);
        }
    }

    private int groupHeaderHeight(float scale) {
        return Math.round(24.0f * scale);
    }

    private int leafHeight(Value<?> value, float scale) {
        if (value.getType() == ValueType.MULTI_ENUM && value.getMetadata().get("options") instanceof List<?> options) {
            return Math.round(22.0f * scale) + Math.round(options.size() * 18.0f * scale);
        }
        int height = this.leafBaseHeight(scale);
        if (value.getType() == ValueType.COLOR || value.getType() == ValueType.GRADIENT) {
            height += COLOR_PICKER.getExtraHeight(value, scale);
        }
        return height;
    }

    private int leafBaseHeight(float scale) {
        return Math.round(24.0f * scale);
    }

    private ValueColorPicker.Channel gradientChannel(Value<?> value, int x, int width, int mouseX, int button, float scale) {
        if (button == 1) {
            return ValueColorPicker.Channel.GRADIENT_END;
        }
        int swatchX = x + width - Math.round(98.0f * scale);
        int swatchSize = Math.round(14.0f * scale);
        int endX = swatchX + swatchSize + Math.round(4.0f * scale);
        return mouseX >= endX ? ValueColorPicker.Channel.GRADIENT_END : ValueColorPicker.Channel.GRADIENT_START;
    }

    private boolean isExpanded(ValueGroup group) {
        return this.expanded.getOrDefault(group, true);
    }

    private String formatNumber(Number number) {
        double value = number.doubleValue();
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.valueOf((int)Math.round(value));
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private String formatRange(NumericRange range) {
        return this.formatNumber(range.lower()) + " - " + this.formatNumber(range.upper());
    }

    private String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.valueOf((int)Math.round(value));
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private double numberMetadata(Value<?> value, String key, double fallback) {
        Object metadataValue = value.getMetadata().get(key);
        return metadataValue instanceof Number number ? number.doubleValue() : fallback;
    }

    private boolean inBounds(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private int applyAlpha(int color, float alpha) {
        int origAlpha = color >> 24 & 0xFF;
        int newAlpha = (int)((float)origAlpha * alpha);
        return newAlpha << 24 | color & 0xFFFFFF;
    }
}
