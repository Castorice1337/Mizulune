package shit.zen.gui.panel.value;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import shit.zen.gui.PanelClickGui;
import shit.zen.manager.ConfigManager;
import shit.zen.render.FontPresets;
import shit.zen.render.FontRenderer;
import shit.zen.render.GlHelper;
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
        TextGlow.drawGlowText(prefix + group.getDisplayName(), x + Math.round(6.0f * scale), textY, font,
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
        FontRenderer nameFont = FontPresets.axiformaRegular(13.0f * scale);
        float nameY = y + Math.round(24.0f * scale) / 2.0f - nameFont.getMetrics().capHeight() / 2.0f;
        TextGlow.drawGlowText(value.getDisplayName(), x + Math.round(3.0f * scale), nameY, nameFont,
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
        return rowHeight;
    }

    private boolean onGroupHeaderClick(ValueGroup group, int x, int y, int width, int mouseX, int mouseY, int button, float scale) {
        if (group instanceof ToggleValueGroup toggleGroup) {
            int toggleX = x + width - Math.round(28.0f * scale);
            int toggleY = y + Math.round(7.0f * scale);
            if (button == 0 && inBounds(mouseX, mouseY, toggleX, toggleY, Math.round(20.0f * scale), Math.round(10.0f * scale))) {
                toggleGroup.setEnabled(!toggleGroup.isEnabled());
                ConfigManager.saveAllIfReady();
                PanelClickGui.panelClickGui.addToast(group.getDisplayName() + (toggleGroup.isEnabled() ? " On" : " Off"));
                return true;
            }
        }
        if (group instanceof ModeValueGroup modeGroup) {
            int pillX = x + width - Math.round(96.0f * scale);
            int pillY = y + Math.round(5.0f * scale);
            if (button == 0 && inBounds(mouseX, mouseY, pillX, pillY, Math.round(90.0f * scale), Math.round(14.0f * scale))) {
                this.cycleEnum(modeGroup.getActiveValue(), 1);
                ConfigManager.saveAllIfReady();
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
        switch (value.getType()) {
            case BOOLEAN -> {
                value.setRawValue(!Boolean.TRUE.equals(value.getValue()));
                ConfigManager.saveAllIfReady();
                return true;
            }
            case INTEGER, DECIMAL, KEY_BIND -> {
                this.bumpNumber((Value<Number>)value, button == 0 ? 1.0 : -1.0);
                ConfigManager.saveAllIfReady();
                return true;
            }
            case ENUM -> {
                this.cycleEnum((Value<String>)value, button == 0 ? 1 : -1);
                ConfigManager.saveAllIfReady();
                return true;
            }
            case MULTI_ENUM -> {
                return this.onMultiEnumClick((Value<List<String>>)value, x, y + Math.round(22.0f * scale), width, mouseX, mouseY, scale);
            }
            case COLOR -> {
                this.cycleColor((Value<MizuColor>)value, button);
                ConfigManager.saveAllIfReady();
                return true;
            }
            case GRADIENT -> {
                this.cycleGradient((Value<GradientSpec>)value, button);
                ConfigManager.saveAllIfReady();
                return true;
            }
            case INT_RANGE, DECIMAL_RANGE -> {
                this.bumpRange((Value<NumericRange>)value, mouseX > x + width / 2, button == 0 ? 1.0 : -1.0);
                ConfigManager.saveAllIfReady();
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
                ConfigManager.saveAllIfReady();
                return true;
            }
            y += rowHeight;
        }
        return false;
    }

    private void cycleColor(Value<MizuColor> value, int button) {
        MizuColor color = value.getValue();
        if (button == 1) {
            value.setValue(color.withAlpha(Math.floorMod(color.alpha() - 16, 256)));
            return;
        }
        float[] hsb = color.toHsb();
        value.setValue(MizuColor.ofHsb((hsb[0] + 0.045f) % 1.0f, hsb[1], hsb[2], color.alpha()));
    }

    private void cycleGradient(Value<GradientSpec> value, int button) {
        GradientSpec gradient = value.getValue();
        if (button == 1) {
            value.setValue(new GradientSpec(gradient.start(), this.shiftHue(gradient.end())));
        } else {
            value.setValue(new GradientSpec(this.shiftHue(gradient.start()), gradient.end()));
        }
    }

    private MizuColor shiftHue(MizuColor color) {
        float[] hsb = color.toHsb();
        return MizuColor.ofHsb((hsb[0] + 0.045f) % 1.0f, hsb[1], hsb[2], color.alpha());
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
            GlHelper.drawText((on ? "[x] " : "[ ] ") + option, x + Math.round(8.0f * scale), textY, font, this.applyAlpha(color, alpha));
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
        GlHelper.drawText(clipped, textX, textY, font, this.applyAlpha(0xFFEAF7FF, alpha));
    }

    private int groupHeaderHeight(float scale) {
        return Math.round(24.0f * scale);
    }

    private int leafHeight(Value<?> value, float scale) {
        if (value.getType() == ValueType.MULTI_ENUM && value.getMetadata().get("options") instanceof List<?> options) {
            return Math.round(22.0f * scale) + Math.round(options.size() * 18.0f * scale);
        }
        return Math.round(24.0f * scale);
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
