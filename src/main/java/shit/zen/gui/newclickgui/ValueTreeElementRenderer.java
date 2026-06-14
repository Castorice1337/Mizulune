package shit.zen.gui.newclickgui;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import shit.zen.gui.NewClickGui;
import shit.zen.gui.value.ValueColorPicker;
import shit.zen.manager.ConfigManager;
import shit.zen.render.CustomFont;
import shit.zen.render.FontStore;
import shit.zen.utils.animation.SmoothAnimationTimer;
import shit.zen.utils.math.Easings;
import shit.zen.utils.misc.CursorUtil;
import shit.zen.utils.render.ColorUtil;
import shit.zen.utils.render.RenderHelper;
import shit.zen.utils.render.RenderUtil;
import shit.zen.value.GradientSpec;
import shit.zen.value.MizuColor;
import shit.zen.value.ModeValueGroup;
import shit.zen.value.NumericRange;
import shit.zen.value.ToggleValueGroup;
import shit.zen.value.Value;
import shit.zen.value.ValueGroup;
import shit.zen.value.ValueType;

public final class ValueTreeElementRenderer {
    private static final ValueTreeElementRenderer INSTANCE = new ValueTreeElementRenderer();
    private static final ValueColorPicker COLOR_PICKER = ValueColorPicker.getInstance();
    private static final float WIDTH = 120.0f;
    private static final float SIDE_PADDING = 6.0f;
    private static final float BOOLEAN_HEIGHT = 18.0f;
    private static final float SLIDER_HEIGHT = 30.0f;
    private static final float DROPDOWN_BASE_HEIGHT = 36.0f;
    private static final float DROPDOWN_ITEM_HEIGHT = 14.0f;
    private static final float GROUP_HEIGHT = 18.0f;

    private final Map<Value<?>, Boolean> groupExpanded = new HashMap<>();
    private final Map<Value<?>, Boolean> dropdownOpen = new HashMap<>();
    private final Map<Value<?>, String> hoveredOption = new HashMap<>();
    private final Map<Value<?>, SmoothAnimationTimer> groupTimers = new HashMap<>();
    private final Map<Value<?>, SmoothAnimationTimer> toggleTimers = new HashMap<>();
    private final Map<Value<?>, SmoothAnimationTimer> sliderTimers = new HashMap<>();
    private final Map<Value<?>, SmoothAnimationTimer> hoverTimers = new HashMap<>();
    private final Map<Value<?>, SmoothAnimationTimer> dropdownTimers = new HashMap<>();
    private final Map<Value<?>, SmoothAnimationTimer> highlightTimers = new HashMap<>();
    private final Map<Value<?>, SmoothAnimationTimer> highlightYTimers = new HashMap<>();
    private Value<Number> draggingNumber;
    private Value<NumericRange> draggingRange;
    private boolean draggingUpperRange;

    private ValueTreeElementRenderer() {
    }

    public static ValueTreeElementRenderer getInstance() {
        return INSTANCE;
    }

    public int render(CategoryPanel parentPanel, NewClickGui clickGui, GuiGraphics guiGraphics, PoseStack poseStack,
                      Value<?> value, float x, float y, int mouseX, int mouseY, float alpha, float partialTicks) {
        return Math.round(this.render(parentPanel, clickGui, guiGraphics, poseStack, value, x, y, mouseX, mouseY, alpha, 0));
    }

    private float render(CategoryPanel parentPanel, NewClickGui clickGui, GuiGraphics guiGraphics, PoseStack poseStack,
                         Value<?> value, float x, float y, int mouseX, int mouseY, float alpha, int depth) {
        if (!value.isVisible() || alpha <= 0.0f) {
            return 0.0f;
        }
        float indent = depth * 6.0f;
        float rowX = x + indent;
        float rowWidth = Math.max(48.0f, WIDTH - indent);
        if (value instanceof ModeValueGroup modeGroup) {
            return this.renderModeGroup(parentPanel, poseStack, modeGroup, rowX, y, rowWidth, x, mouseX, mouseY, alpha, depth);
        }
        if (value instanceof ValueGroup group) {
            return this.renderGroup(parentPanel, poseStack, group, rowX, y, rowWidth, x, mouseX, mouseY, alpha, depth);
        }
        return this.renderLeaf(parentPanel, poseStack, value, rowX, y, rowWidth, mouseX, mouseY, alpha);
    }

    public boolean onClick(Value<?> value, float x, float y, int mouseX, int mouseY, int button) {
        return this.onClick(value, x, y, mouseX, mouseY, button, 0);
    }

    private boolean onClick(Value<?> value, float x, float y, int mouseX, int mouseY, int button, int depth) {
        if (!value.isVisible()) {
            return false;
        }
        float indent = depth * 6.0f;
        float rowX = x + indent;
        float rowWidth = Math.max(48.0f, WIDTH - indent);
        if (value instanceof ModeValueGroup modeGroup) {
            return this.onModeGroupClick(modeGroup, rowX, y, rowWidth, x, mouseX, mouseY, button, depth);
        }
        if (value instanceof ValueGroup group) {
            return this.onGroupClick(group, rowX, y, rowWidth, x, mouseX, mouseY, button, depth);
        }
        return this.onLeafClick(value, rowX, y, rowWidth, mouseX, mouseY, button);
    }

    public int getHeight(Value<?> value) {
        return Math.round(this.getHeight(value, 0));
    }

    private float getHeight(Value<?> value, int depth) {
        if (!value.isVisible()) {
            return 0.0f;
        }
        if (value instanceof ModeValueGroup modeGroup) {
            return this.dropdownHeight(modeGroup.getActiveValue())
                    + this.modeChildrenHeight(modeGroup, depth, this.groupAmount(modeGroup));
        }
        if (value instanceof ValueGroup group) {
            return GROUP_HEIGHT + this.childrenHeight(group, depth, this.groupAmount(group));
        }
        return this.leafHeight(value);
    }

    public void onMouseRelease(double mouseX, double mouseY, int button) {
        COLOR_PICKER.onMouseRelease();
        if (this.draggingNumber != null || this.draggingRange != null) {
            ConfigManager.saveAllIfReady();
        }
        this.draggingNumber = null;
        this.draggingRange = null;
    }

    private float renderGroup(CategoryPanel parentPanel, PoseStack poseStack, ValueGroup group, float rowX, float y,
                              float rowWidth, float rootX, int mouseX, int mouseY, float alpha, int depth) {
        boolean expanded = this.isGroupExpanded(group);
        SmoothAnimationTimer groupTimer = this.timer(this.groupTimers, group, expanded ? 1.0 : 0.0);
        groupTimer.animate(expanded ? 1.0 : 0.0, 0.2, Easings.EASE_OUT_POW2);
        groupTimer.tick();

        this.renderGroupHeader(poseStack, group, rowX, y, rowWidth, mouseX, mouseY, alpha);
        float used = GROUP_HEIGHT;
        if (group.areChildrenVisible()) {
            float childAlpha = alpha * groupTimer.getValueF();
            for (Value<?> child : group.getVisibleChildren()) {
                used += this.render(parentPanel, null, null, poseStack, child, rootX, y + used, mouseX, mouseY, childAlpha, depth + 1);
            }
        }
        return GROUP_HEIGHT + (used - GROUP_HEIGHT) * groupTimer.getValueF();
    }

    private float renderModeGroup(CategoryPanel parentPanel, PoseStack poseStack, ModeValueGroup group, float rowX, float y,
                                  float rowWidth, float rootX, int mouseX, int mouseY, float alpha, int depth) {
        boolean expanded = this.isGroupExpanded(group);
        SmoothAnimationTimer groupTimer = this.timer(this.groupTimers, group, expanded ? 1.0 : 0.0);
        groupTimer.animate(expanded ? 1.0 : 0.0, 0.2, Easings.EASE_OUT_POW2);
        groupTimer.tick();

        this.renderGroupDisclosure(poseStack, group, rowX, y, mouseX, mouseY, alpha);
        this.renderDropdownValue(poseStack, group.getActiveValue(), group.getDisplayName(), rowX, y, rowWidth, mouseX, mouseY, alpha, true);

        float used = this.dropdownHeight(group.getActiveValue());
        if (group.areChildrenVisible()) {
            float childAlpha = alpha * groupTimer.getValueF();
            for (Value<?> child : this.visibleModeChildren(group)) {
                used += this.render(parentPanel, null, null, poseStack, child, rootX, y + used, mouseX, mouseY, childAlpha, depth + 1);
            }
        }
        return this.dropdownHeight(group.getActiveValue()) + (used - this.dropdownHeight(group.getActiveValue())) * groupTimer.getValueF();
    }

    private void renderGroupHeader(PoseStack poseStack, ValueGroup group, float x, float y, float width,
                                   int mouseX, int mouseY, float alpha) {
        boolean hovered = CursorUtil.isInBounds(mouseX, mouseY, x + SIDE_PADDING, y, width - SIDE_PADDING * 2.0f, GROUP_HEIGHT);
        if (hovered) {
            RenderUtil.drawRoundedRect(poseStack, x + 3.0f, y + 2.0f, width - 6.0f, GROUP_HEIGHT - 4.0f, 3.0f,
                    ColorUtil.withAlpha(-1, alpha * 0.06f));
        }
        this.renderGroupDisclosure(poseStack, group, x, y, mouseX, mouseY, alpha);
        String name = this.clip(group.getDisplayName(), FontStore.AXIFORMA_BOLD_13, width - 42.0f);
        FontStore.AXIFORMA_BOLD_13.drawString(poseStack, name, x + 18.0f,
                y + (GROUP_HEIGHT - FontStore.AXIFORMA_BOLD_13.getFontHeight()) / 2.0f + 1.0f,
                ColorUtil.withAlpha(-1, alpha * 0.72f));
        if (group instanceof ToggleValueGroup toggleGroup) {
            this.renderToggle(poseStack, group, x + width - 26.0f, y + (GROUP_HEIGHT - 10.0f) / 2.0f,
                    toggleGroup.isEnabled(), alpha);
        }
    }

    private void renderGroupDisclosure(PoseStack poseStack, ValueGroup group, float x, float y,
                                       int mouseX, int mouseY, float alpha) {
        String arrowIcon = String.valueOf('\ueb4e');
        float arrowX = x + 6.0f;
        float arrowY = y + (GROUP_HEIGHT - FontStore.MATERIAL_14.getFontHeight()) / 2.0f + 1.0f;
        float amount = this.groupAmount(group);
        RenderHelper.pushRotateAround(poseStack, arrowX + FontStore.MATERIAL_14.getStringWidth(arrowIcon) / 2.0f,
                arrowY + FontStore.MATERIAL_14.getFontHeight() / 2.0f - 0.5f, 180.0f * amount);
        FontStore.MATERIAL_14.drawString(poseStack, arrowIcon, arrowX, arrowY, ColorUtil.withAlpha(-1, alpha * (0.48f + 0.24f * amount)));
        RenderHelper.popPose(poseStack);
    }

    private float renderLeaf(CategoryPanel parentPanel, PoseStack poseStack, Value<?> value, float x, float y,
                             float width, int mouseX, int mouseY, float alpha) {
        return switch (value.getType()) {
            case BOOLEAN -> this.renderBoolean(poseStack, value, x, y, width, mouseX, mouseY, alpha);
            case INTEGER, DECIMAL -> this.renderNumber(poseStack, value, x, y, width, mouseX, alpha);
            case ENUM -> this.renderDropdownValue(poseStack, value, value.getDisplayName(), x, y, width, mouseX, mouseY, alpha, false);
            case MULTI_ENUM -> this.renderMultiEnum(poseStack, value, x, y, width, mouseX, mouseY, alpha);
            case INT_RANGE, DECIMAL_RANGE -> this.renderRange(poseStack, value, x, y, width, mouseX, alpha);
            case COLOR -> this.renderColor(poseStack, value, x, y, width, mouseX, mouseY, alpha);
            case GRADIENT -> this.renderGradient(poseStack, value, x, y, width, mouseX, mouseY, alpha);
            default -> this.renderCompactValue(poseStack, value, x, y, width, alpha);
        };
    }

    private float renderBoolean(PoseStack poseStack, Value<?> value, float x, float y, float width,
                                int mouseX, int mouseY, float alpha) {
        boolean hovered = CursorUtil.isInBounds(mouseX, mouseY, x, y, width, BOOLEAN_HEIGHT);
        if (hovered) {
            RenderUtil.drawRoundedRect(poseStack, x + 3.0f, y + 2.0f, width - 6.0f, BOOLEAN_HEIGHT - 4.0f, 3.0f,
                    ColorUtil.withAlpha(-1, alpha * 0.045f));
        }
        String name = this.clip(value.getDisplayName(), FontStore.AXIFORMA_REGULAR_14, width - 38.0f);
        FontStore.AXIFORMA_REGULAR_14.drawString(poseStack, name, x + SIDE_PADDING,
                y + (BOOLEAN_HEIGHT - FontStore.AXIFORMA_REGULAR_14.getFontHeight()) / 2.0f,
                ColorUtil.withAlpha(-1, alpha * 0.8f));
        this.renderToggle(poseStack, value, x + width - 26.0f, y + (BOOLEAN_HEIGHT - 10.0f) / 2.0f,
                Boolean.TRUE.equals(value.getValue()), alpha);
        return BOOLEAN_HEIGHT;
    }

    private float renderNumber(PoseStack poseStack, Value<?> value, float x, float y, float width, int mouseX, float alpha) {
        @SuppressWarnings("unchecked")
        Value<Number> numberValue = (Value<Number>)value;
        if (this.draggingNumber == numberValue) {
            this.applyNumberFromMouse(numberValue, x, width, mouseX);
        }
        double min = this.numberMetadata(value, "min", 0.0);
        double max = this.numberMetadata(value, "max", 1.0);
        double current = numberValue.getValue().doubleValue();
        float progress = this.ratio(current, min, max);
        SmoothAnimationTimer sliderTimer = this.timer(this.sliderTimers, value, progress);
        sliderTimer.animate(progress, 0.2, Easings.EASE_OUT_POW2);
        sliderTimer.tick();

        float nameY = y + (SLIDER_HEIGHT / 2.0f - FontStore.AXIFORMA_REGULAR_14.getFontHeight()) / 2.0f + 1.0f;
        String name = this.clip(value.getDisplayName(), FontStore.AXIFORMA_REGULAR_14, width - 42.0f);
        FontStore.AXIFORMA_REGULAR_14.drawString(poseStack, name, x + SIDE_PADDING, nameY,
                ColorUtil.withAlpha(-1, alpha * 0.8f));
        String valueText = this.formatNumber(current);
        FontStore.AXIFORMA_BOLD_13.drawString(poseStack, valueText,
                x + width - FontStore.AXIFORMA_BOLD_13.getStringWidth(valueText) - SIDE_PADDING,
                nameY, ColorUtil.withAlpha(-1, alpha * 0.92f));
        this.renderSlider(poseStack, x + SIDE_PADDING, y + SLIDER_HEIGHT / 2.0f + 4.0f,
                width - SIDE_PADDING * 2.0f, sliderTimer.getValueF(), alpha);
        return SLIDER_HEIGHT;
    }

    private float renderRange(PoseStack poseStack, Value<?> value, float x, float y, float width, int mouseX, float alpha) {
        @SuppressWarnings("unchecked")
        Value<NumericRange> rangeValue = (Value<NumericRange>)value;
        if (this.draggingRange == rangeValue) {
            this.applyRangeFromMouse(rangeValue, x, width, mouseX, this.draggingUpperRange);
        }
        NumericRange range = rangeValue.getValue();
        float lowerRatio = this.ratio(range.lower(), range.min(), range.max());
        float upperRatio = this.ratio(range.upper(), range.min(), range.max());
        float nameY = y + (SLIDER_HEIGHT / 2.0f - FontStore.AXIFORMA_REGULAR_14.getFontHeight()) / 2.0f + 1.0f;
        String name = this.clip(value.getDisplayName(), FontStore.AXIFORMA_REGULAR_14, width - 52.0f);
        FontStore.AXIFORMA_REGULAR_14.drawString(poseStack, name, x + SIDE_PADDING, nameY,
                ColorUtil.withAlpha(-1, alpha * 0.8f));
        String valueText = this.formatNumber(range.lower()) + "-" + this.formatNumber(range.upper());
        FontStore.AXIFORMA_BOLD_13.drawString(poseStack, valueText,
                x + width - FontStore.AXIFORMA_BOLD_13.getStringWidth(valueText) - SIDE_PADDING,
                nameY, ColorUtil.withAlpha(-1, alpha * 0.92f));
        this.renderRangeSlider(poseStack, x + SIDE_PADDING, y + SLIDER_HEIGHT / 2.0f + 4.0f,
                width - SIDE_PADDING * 2.0f, lowerRatio, upperRatio, alpha);
        return SLIDER_HEIGHT;
    }

    private float renderDropdownValue(PoseStack poseStack, Value<?> value, String displayName, float x, float y,
                                      float width, int mouseX, int mouseY, float alpha, boolean compactGroupLabel) {
        List<String> options = this.options(value);
        float dropdownY = y + 20.0f;
        float itemHeight = DROPDOWN_ITEM_HEIGHT;
        float dropdownWidth = Math.max(40.0f, width - SIDE_PADDING * 2.0f);
        boolean headerHovered = CursorUtil.isInBounds(mouseX, mouseY, x + SIDE_PADDING, dropdownY, dropdownWidth, itemHeight);
        SmoothAnimationTimer hoverTimer = this.timer(this.hoverTimers, value, 0.0);
        hoverTimer.animate(headerHovered ? 1.0 : 0.0, 0.22, Easings.EASE_OUT_POW2);
        hoverTimer.tick();
        SmoothAnimationTimer openTimer = this.timer(this.dropdownTimers, value, this.isDropdownOpen(value) ? 1.0 : 0.0);
        openTimer.animate(this.isDropdownOpen(value) ? 1.0 : 0.0, 0.2, Easings.EASE_OUT_POW2);
        openTimer.tick();

        float nameY = y + (18.0f - FontStore.AXIFORMA_REGULAR_14.getFontHeight()) / 2.0f + 1.0f;
        String name = this.clip(displayName, compactGroupLabel ? FontStore.AXIFORMA_BOLD_13 : FontStore.AXIFORMA_REGULAR_14, width - 16.0f);
        if (compactGroupLabel) {
            FontStore.AXIFORMA_BOLD_13.drawString(poseStack, name, x + 18.0f, nameY, ColorUtil.withAlpha(-1, alpha * 0.72f));
        } else {
            FontStore.AXIFORMA_REGULAR_14.drawString(poseStack, name, x + SIDE_PADDING, nameY, ColorUtil.withAlpha(-1, alpha * 0.8f));
        }

        this.renderDropdownOptions(poseStack, value, options, x + SIDE_PADDING, dropdownY, dropdownWidth, itemHeight,
                mouseX, mouseY, alpha, openTimer.getValueF());

        float hoverAmount = hoverTimer.getValueF();
        RenderUtil.drawRoundedRect(poseStack, x + SIDE_PADDING, dropdownY, dropdownWidth, itemHeight, 3.0f,
                ColorUtil.withAlpha(ColorUtil.fromRGB((int)(60.0f + 30.0f * hoverAmount),
                        (int)(60.0f + 30.0f * hoverAmount), (int)(60.0f + 30.0f * hoverAmount)), alpha));
        String current = this.clip(this.optionLabel(value, String.valueOf(value.getValue())), FontStore.AXIFORMA_BOLD_13, dropdownWidth - 22.0f);
        FontStore.AXIFORMA_BOLD_13.drawStringCentered(poseStack, current, x + width / 2.0f,
                dropdownY + (itemHeight - FontStore.AXIFORMA_BOLD_13.getFontHeight()) / 2.0f,
                ColorUtil.withAlpha(-1, alpha * 0.8f));
        String arrowIcon = String.valueOf('\ueb5d');
        FontStore.MATERIAL_20.drawString(poseStack, arrowIcon,
                x + SIDE_PADDING + dropdownWidth - FontStore.MATERIAL_20.getStringWidth(arrowIcon) - 2.0f,
                dropdownY + (itemHeight - FontStore.MATERIAL_20.getFontHeight()) / 2.0f + 0.5f,
                ColorUtil.withAlpha(-1, alpha * 0.8f));
        return this.dropdownHeight(value);
    }

    private float renderMultiEnum(PoseStack poseStack, Value<?> value, float x, float y, float width,
                                  int mouseX, int mouseY, float alpha) {
        List<String> options = this.options(value);
        float dropdownY = y + 20.0f;
        float itemHeight = DROPDOWN_ITEM_HEIGHT;
        float dropdownWidth = Math.max(40.0f, width - SIDE_PADDING * 2.0f);
        boolean headerHovered = CursorUtil.isInBounds(mouseX, mouseY, x + SIDE_PADDING, dropdownY, dropdownWidth, itemHeight);
        SmoothAnimationTimer hoverTimer = this.timer(this.hoverTimers, value, 0.0);
        hoverTimer.animate(headerHovered ? 1.0 : 0.0, 0.22, Easings.EASE_OUT_POW2);
        hoverTimer.tick();
        SmoothAnimationTimer openTimer = this.timer(this.dropdownTimers, value, this.isDropdownOpen(value) ? 1.0 : 0.0);
        openTimer.animate(this.isDropdownOpen(value) ? 1.0 : 0.0, 0.2, Easings.EASE_OUT_POW2);
        openTimer.tick();

        float nameY = y + (18.0f - FontStore.AXIFORMA_REGULAR_14.getFontHeight()) / 2.0f + 1.0f;
        String name = this.clip(value.getDisplayName(), FontStore.AXIFORMA_REGULAR_14, width - 16.0f);
        FontStore.AXIFORMA_REGULAR_14.drawString(poseStack, name, x + SIDE_PADDING, nameY, ColorUtil.withAlpha(-1, alpha * 0.8f));
        this.renderDropdownOptions(poseStack, value, options, x + SIDE_PADDING, dropdownY, dropdownWidth, itemHeight,
                mouseX, mouseY, alpha, openTimer.getValueF());
        float hoverAmount = hoverTimer.getValueF();
        RenderUtil.drawRoundedRect(poseStack, x + SIDE_PADDING, dropdownY, dropdownWidth, itemHeight, 3.0f,
                ColorUtil.withAlpha(ColorUtil.fromRGB((int)(60.0f + 30.0f * hoverAmount),
                        (int)(60.0f + 30.0f * hoverAmount), (int)(60.0f + 30.0f * hoverAmount)), alpha));
        String selectedLabel = this.multiLabel(value);
        selectedLabel = this.clip(selectedLabel, FontStore.AXIFORMA_BOLD_13, dropdownWidth - 22.0f);
        FontStore.AXIFORMA_BOLD_13.drawStringCentered(poseStack, selectedLabel, x + width / 2.0f,
                dropdownY + (itemHeight - FontStore.AXIFORMA_BOLD_13.getFontHeight()) / 2.0f,
                ColorUtil.withAlpha(-1, alpha * 0.8f));
        String arrowIcon = String.valueOf('\ueb5d');
        FontStore.MATERIAL_20.drawString(poseStack, arrowIcon,
                x + SIDE_PADDING + dropdownWidth - FontStore.MATERIAL_20.getStringWidth(arrowIcon) - 2.0f,
                dropdownY + (itemHeight - FontStore.MATERIAL_20.getFontHeight()) / 2.0f + 0.5f,
                ColorUtil.withAlpha(-1, alpha * 0.8f));
        return this.dropdownHeight(value);
    }

    private void renderDropdownOptions(PoseStack poseStack, Value<?> value, List<String> options, float x, float y,
                                       float width, float itemHeight, int mouseX, int mouseY, float alpha, float openAmount) {
        this.hoveredOption.remove(value);
        if (openAmount > 0.0f && !options.isEmpty()) {
            float dropdownHeight = itemHeight + options.size() * itemHeight * openAmount;
            RenderUtil.drawRoundedRect(poseStack, x, y, width, dropdownHeight, 3.0f,
                    ColorUtil.withAlpha(ColorUtil.fromRGB(60, 60, 60), alpha * openAmount));
            float textInsetY = (itemHeight - FontStore.AXIFORMA_BOLD_13.getFontHeight()) / 2.0f;
            float itemY = y + itemHeight;
            for (String option : options) {
                if (CursorUtil.isInBounds(mouseX, mouseY, x, itemY, width, itemHeight)) {
                    this.hoveredOption.put(value, option);
                    this.timer(this.highlightYTimers, value, itemY).animate(itemY, 0.2, Easings.EASE_OUT_POW2);
                }
                if (y + dropdownHeight > itemY + FontStore.AXIFORMA_BOLD_13.getFontHeight()) {
                    String label = this.optionLabel(value, option);
                    FontStore.AXIFORMA_BOLD_13.drawStringCentered(poseStack, label, x + width / 2.0f,
                            itemY + textInsetY, ColorUtil.withAlpha(-1, alpha * 0.8f * openAmount));
                    if (this.isOptionSelected(value, option)) {
                        FontStore.AXIFORMA_BOLD_13.drawString(poseStack, "x",
                                x + width - FontStore.AXIFORMA_BOLD_13.getStringWidth("x") - 5.0f,
                                itemY + textInsetY, ColorUtil.withAlpha(-1, alpha * 0.56f * openAmount));
                    }
                }
                itemY += itemHeight;
            }
        }
        SmoothAnimationTimer highlightTimer = this.timer(this.highlightTimers, value, 0.0);
        highlightTimer.animate(this.hoveredOption.containsKey(value) && this.isDropdownOpen(value) ? 1.0 : 0.0,
                0.18, Easings.EASE_OUT_POW2);
        highlightTimer.tick();
        SmoothAnimationTimer highlightYTimer = this.timer(this.highlightYTimers, value, y + itemHeight);
        highlightYTimer.tick();
        if (highlightTimer.getValueF() > 0.0f) {
            RenderUtil.drawRoundedRect(poseStack, x, highlightYTimer.getValueF(), width, itemHeight, 3.0f,
                    ColorUtil.withAlpha(-1, alpha * highlightTimer.getValueF() * 0.1f));
        }
    }

    private float renderColor(PoseStack poseStack, Value<?> value, float x, float y, float width,
                              int mouseX, int mouseY, float alpha) {
        MizuColor color = (MizuColor)value.getValue();
        String name = this.clip(value.getDisplayName(), FontStore.AXIFORMA_REGULAR_14, width - 72.0f);
        FontStore.AXIFORMA_REGULAR_14.drawString(poseStack, name, x + SIDE_PADDING,
                y + (BOOLEAN_HEIGHT - FontStore.AXIFORMA_REGULAR_14.getFontHeight()) / 2.0f,
                ColorUtil.withAlpha(-1, alpha * 0.8f));
        float swatch = 12.0f;
        float swatchX = x + width - 68.0f;
        float swatchY = y + (BOOLEAN_HEIGHT - swatch) / 2.0f;
        RenderUtil.drawRoundedRect(poseStack, swatchX, swatchY, swatch, swatch, 3.0f, ColorUtil.withAlpha(color.toArgb(), alpha));
        this.renderSmallPill(poseStack, color.toHexArgb(), swatchX + swatch + 4.0f, swatchY, 50.0f, swatch, alpha);
        return BOOLEAN_HEIGHT + COLOR_PICKER.render(poseStack, value, Math.round(x), Math.round(y + BOOLEAN_HEIGHT),
                Math.round(width), mouseX, mouseY, alpha, 1.0f);
    }

    private float renderGradient(PoseStack poseStack, Value<?> value, float x, float y, float width,
                                 int mouseX, int mouseY, float alpha) {
        GradientSpec gradient = (GradientSpec)value.getValue();
        String name = this.clip(value.getDisplayName(), FontStore.AXIFORMA_REGULAR_14, width - 44.0f);
        FontStore.AXIFORMA_REGULAR_14.drawString(poseStack, name, x + SIDE_PADDING,
                y + (BOOLEAN_HEIGHT - FontStore.AXIFORMA_REGULAR_14.getFontHeight()) / 2.0f,
                ColorUtil.withAlpha(-1, alpha * 0.8f));
        float swatch = 12.0f;
        float swatchX = x + width - 34.0f;
        float swatchY = y + (BOOLEAN_HEIGHT - swatch) / 2.0f;
        RenderUtil.drawRoundedRect(poseStack, swatchX, swatchY, swatch, swatch, 3.0f, ColorUtil.withAlpha(gradient.start().toArgb(), alpha));
        RenderUtil.drawRoundedRect(poseStack, swatchX + swatch + 4.0f, swatchY, swatch, swatch, 3.0f, ColorUtil.withAlpha(gradient.end().toArgb(), alpha));
        return BOOLEAN_HEIGHT + COLOR_PICKER.render(poseStack, value, Math.round(x), Math.round(y + BOOLEAN_HEIGHT),
                Math.round(width), mouseX, mouseY, alpha, 1.0f);
    }

    private float renderCompactValue(PoseStack poseStack, Value<?> value, float x, float y, float width, float alpha) {
        String name = this.clip(value.getDisplayName(), FontStore.AXIFORMA_REGULAR_14, width - 58.0f);
        FontStore.AXIFORMA_REGULAR_14.drawString(poseStack, name, x + SIDE_PADDING,
                y + (BOOLEAN_HEIGHT - FontStore.AXIFORMA_REGULAR_14.getFontHeight()) / 2.0f,
                ColorUtil.withAlpha(-1, alpha * 0.8f));
        String text = this.clip(String.valueOf(value.getValue()), FontStore.AXIFORMA_BOLD_13, 48.0f);
        this.renderSmallPill(poseStack, text, x + width - 54.0f, y + 3.0f, 48.0f, 12.0f, alpha);
        return BOOLEAN_HEIGHT;
    }

    private boolean onGroupClick(ValueGroup group, float rowX, float y, float rowWidth, float rootX,
                                 int mouseX, int mouseY, int button, int depth) {
        if (mouseY >= y && mouseY <= y + GROUP_HEIGHT) {
            if (group instanceof ToggleValueGroup toggleGroup
                    && button == 0
                    && CursorUtil.isInBounds(mouseX, mouseY, rowX + rowWidth - 26.0f, y + (GROUP_HEIGHT - 10.0f) / 2.0f, 20.0f, 10.0f)) {
                toggleGroup.setEnabled(!toggleGroup.isEnabled());
                ConfigManager.saveAllIfReady();
                return true;
            }
            if (button == 0 || button == 1) {
                this.groupExpanded.put(group, !this.isGroupExpanded(group));
                return true;
            }
        }
        float childY = y + GROUP_HEIGHT;
        if (group.areChildrenVisible()) {
            for (Value<?> child : group.getVisibleChildren()) {
                float childHeight = this.getHeight(child, depth + 1);
                if (mouseY >= childY && mouseY <= childY + childHeight
                        && this.onClick(child, rootX, childY, mouseX, mouseY, button, depth + 1)) {
                    return true;
                }
                childY += childHeight;
            }
        }
        return false;
    }

    private boolean onModeGroupClick(ModeValueGroup group, float rowX, float y, float rowWidth, float rootX,
                                     int mouseX, int mouseY, int button, int depth) {
        if (mouseY >= y && mouseY <= y + GROUP_HEIGHT && mouseX < rowX + rowWidth - SIDE_PADDING) {
            if (button == 0 || button == 1) {
                this.groupExpanded.put(group, !this.isGroupExpanded(group));
                return true;
            }
        }
        if (this.onDropdownClick(group.getActiveValue(), rowX, y, rowWidth, mouseX, mouseY, button)) {
            return true;
        }
        float childY = y + this.dropdownHeight(group.getActiveValue());
        if (group.areChildrenVisible()) {
            for (Value<?> child : this.visibleModeChildren(group)) {
                float childHeight = this.getHeight(child, depth + 1);
                if (mouseY >= childY && mouseY <= childY + childHeight
                        && this.onClick(child, rootX, childY, mouseX, mouseY, button, depth + 1)) {
                    return true;
                }
                childY += childHeight;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean onLeafClick(Value<?> value, float x, float y, float width, int mouseX, int mouseY, int button) {
        if (button != 0 && button != 1) {
            return false;
        }
        return switch (value.getType()) {
            case BOOLEAN -> {
                if (CursorUtil.isInBounds(mouseX, mouseY, x, y, width, BOOLEAN_HEIGHT)) {
                    value.setRawValue(!Boolean.TRUE.equals(value.getValue()));
                    ConfigManager.saveAllIfReady();
                    yield true;
                }
                yield false;
            }
            case INTEGER, DECIMAL -> {
                if (CursorUtil.isInBounds(mouseX, mouseY, x + SIDE_PADDING, y + SLIDER_HEIGHT / 2.0f + 4.0f,
                        width - SIDE_PADDING * 2.0f, 7.0f)) {
                    this.draggingNumber = (Value<Number>)value;
                    this.applyNumberFromMouse(this.draggingNumber, x, width, mouseX);
                    yield true;
                }
                yield false;
            }
            case ENUM -> this.onDropdownClick(value, x, y, width, mouseX, mouseY, button);
            case MULTI_ENUM -> this.onMultiEnumClick((Value<List<String>>)value, x, y, width, mouseX, mouseY, button);
            case INT_RANGE, DECIMAL_RANGE -> {
                if (CursorUtil.isInBounds(mouseX, mouseY, x + SIDE_PADDING, y + SLIDER_HEIGHT / 2.0f + 4.0f,
                        width - SIDE_PADDING * 2.0f, 7.0f)) {
                    Value<NumericRange> rangeValue = (Value<NumericRange>)value;
                    NumericRange range = rangeValue.getValue();
                    float sliderX = x + SIDE_PADDING;
                    float sliderWidth = width - SIDE_PADDING * 2.0f;
                    float lowerX = sliderX + this.ratio(range.lower(), range.min(), range.max()) * sliderWidth;
                    float upperX = sliderX + this.ratio(range.upper(), range.min(), range.max()) * sliderWidth;
                    this.draggingRange = rangeValue;
                    this.draggingUpperRange = Math.abs(mouseX - upperX) <= Math.abs(mouseX - lowerX);
                    this.applyRangeFromMouse(rangeValue, x, width, mouseX, this.draggingUpperRange);
                    yield true;
                }
                yield false;
            }
            case COLOR -> {
                if (COLOR_PICKER.onClick(value, Math.round(x), Math.round(y + BOOLEAN_HEIGHT), Math.round(width), mouseX, mouseY, button, 1.0f)) {
                    yield true;
                }
                if (CursorUtil.isInBounds(mouseX, mouseY, x, y, width, BOOLEAN_HEIGHT)) {
                    COLOR_PICKER.toggle(value, ValueColorPicker.Channel.SINGLE);
                    yield true;
                }
                yield false;
            }
            case GRADIENT -> {
                if (COLOR_PICKER.onClick(value, Math.round(x), Math.round(y + BOOLEAN_HEIGHT), Math.round(width), mouseX, mouseY, button, 1.0f)) {
                    yield true;
                }
                if (CursorUtil.isInBounds(mouseX, mouseY, x, y, width, BOOLEAN_HEIGHT)) {
                    COLOR_PICKER.toggle(value, this.gradientChannel(x, width, mouseX, button));
                    yield true;
                }
                yield false;
            }
            default -> false;
        };
    }

    private boolean onDropdownClick(Value<?> value, float x, float y, float width, int mouseX, int mouseY, int button) {
        if (button != 0 && button != 1) {
            return false;
        }
        float dropdownY = y + 20.0f;
        float dropdownWidth = Math.max(40.0f, width - SIDE_PADDING * 2.0f);
        if (this.isDropdownOpen(value)) {
            List<String> options = this.options(value);
            float optionY = dropdownY + DROPDOWN_ITEM_HEIGHT;
            for (String option : options) {
                if (CursorUtil.isInBounds(mouseX, mouseY, x + SIDE_PADDING, optionY, dropdownWidth, DROPDOWN_ITEM_HEIGHT)) {
                    @SuppressWarnings("unchecked")
                    Value<String> stringValue = (Value<String>)value;
                    stringValue.setValue(option);
                    this.dropdownOpen.put(value, false);
                    ConfigManager.saveAllIfReady();
                    return true;
                }
                optionY += DROPDOWN_ITEM_HEIGHT;
            }
        }
        if (CursorUtil.isInBounds(mouseX, mouseY, x + SIDE_PADDING, dropdownY, dropdownWidth, DROPDOWN_ITEM_HEIGHT)) {
            this.dropdownOpen.put(value, !this.isDropdownOpen(value));
            return true;
        }
        return false;
    }

    private boolean onMultiEnumClick(Value<List<String>> value, float x, float y, float width, int mouseX, int mouseY, int button) {
        if (button != 0) {
            return false;
        }
        float dropdownY = y + 20.0f;
        float dropdownWidth = Math.max(40.0f, width - SIDE_PADDING * 2.0f);
        if (this.isDropdownOpen(value)) {
            List<String> options = this.options(value);
            float optionY = dropdownY + DROPDOWN_ITEM_HEIGHT;
            for (String option : options) {
                if (CursorUtil.isInBounds(mouseX, mouseY, x + SIDE_PADDING, optionY, dropdownWidth, DROPDOWN_ITEM_HEIGHT)) {
                    List<String> next = new ArrayList<>(value.getValue());
                    if (next.contains(option)) {
                        if (next.size() > 1) {
                            next.remove(option);
                        }
                    } else {
                        next.add(option);
                    }
                    value.setValue(next);
                    ConfigManager.saveAllIfReady();
                    return true;
                }
                optionY += DROPDOWN_ITEM_HEIGHT;
            }
        }
        if (CursorUtil.isInBounds(mouseX, mouseY, x + SIDE_PADDING, dropdownY, dropdownWidth, DROPDOWN_ITEM_HEIGHT)) {
            this.dropdownOpen.put(value, !this.isDropdownOpen(value));
            return true;
        }
        return false;
    }

    private void renderToggle(PoseStack poseStack, Value<?> key, float x, float y, boolean enabled, float alpha) {
        SmoothAnimationTimer toggleTimer = this.timer(this.toggleTimers, key, enabled ? 1.0 : 0.0);
        toggleTimer.animate(enabled ? 1.0 : 0.0, 0.2, Easings.EASE_OUT_POW2);
        toggleTimer.tick();
        float amount = toggleTimer.getValueF();
        if (amount > 0.0f) {
            RenderUtil.drawShadow(poseStack, x, y, 20.0f, 10.0f, 12, ColorUtil.withAlpha(-13768502, 0.36f * alpha * amount));
        }
        RenderUtil.drawRoundedRect(poseStack, x, y, 20.0f, 10.0f, 4.0f, ColorUtil.withAlpha(ColorUtil.fromRGB(60, 60, 60), alpha));
        if (amount > 0.0f) {
            RenderUtil.drawRoundedRect(poseStack, x, y, 10.0f + 10.0f * amount, 10.0f, 4.0f,
                    ColorUtil.withAlpha(CategoryPanel.ACCENT_COLOR, alpha * amount));
        }
        RenderUtil.drawRoundedRect(poseStack, x + 10.0f * amount, y, 10.0f, 10.0f, 4.8f, ColorUtil.withAlpha(-1, alpha));
    }

    private void renderSlider(PoseStack poseStack, float x, float y, float width, float progress, float alpha) {
        RenderUtil.drawRoundedRect(poseStack, x, y, width, 5.0f, 2.0f, ColorUtil.withAlpha(ColorUtil.fromRGB(60, 60, 60), alpha));
        float glowInset = 10.0f;
        float glowSize = 5.0f + glowInset * 2.0f;
        RenderUtil.drawRoundedRect(poseStack, x - glowInset, y - glowInset,
                Math.max(width * progress + glowInset * 2.0f, glowSize), glowSize, 2.0f + glowInset / 2.0f + 1.0f,
                glowInset, ColorUtil.withAlpha(-13768502, 0.26f * alpha));
        RenderUtil.drawRoundedRect(poseStack, x, y, width * progress, 5.0f, 2.0f, ColorUtil.withAlpha(CategoryPanel.ACCENT_COLOR, alpha));
        float knobX = Math.max(x + progress * width - 5.5f, x - 0.5f);
        RenderUtil.drawRoundedRect(poseStack, knobX - glowInset, y - 0.5f - glowInset,
                6.0f + glowInset * 2.0f, 6.0f + glowInset * 2.0f, 2.0f + glowInset / 2.0f + 1.0f,
                glowInset, ColorUtil.withAlpha(-1, 0.36f * alpha));
        RenderUtil.drawRoundedRect(poseStack, knobX, y - 0.5f, 6.0f, 6.0f, 2.9f, ColorUtil.withAlpha(-1, alpha));
    }

    private void renderRangeSlider(PoseStack poseStack, float x, float y, float width, float lower, float upper, float alpha) {
        RenderUtil.drawRoundedRect(poseStack, x, y, width, 5.0f, 2.0f, ColorUtil.withAlpha(ColorUtil.fromRGB(60, 60, 60), alpha));
        float fillX = x + lower * width;
        float fillWidth = Math.max(0.0f, (upper - lower) * width);
        RenderUtil.drawRoundedRect(poseStack, fillX, y, fillWidth, 5.0f, 2.0f, ColorUtil.withAlpha(CategoryPanel.ACCENT_COLOR, alpha));
        this.renderRangeKnob(poseStack, fillX, y, alpha);
        this.renderRangeKnob(poseStack, x + upper * width, y, alpha);
    }

    private void renderRangeKnob(PoseStack poseStack, float centerX, float y, float alpha) {
        float glowInset = 8.0f;
        RenderUtil.drawRoundedRect(poseStack, centerX - 3.0f - glowInset, y - 0.5f - glowInset,
                6.0f + glowInset * 2.0f, 6.0f + glowInset * 2.0f, 2.0f + glowInset / 2.0f + 1.0f,
                glowInset, ColorUtil.withAlpha(-1, 0.26f * alpha));
        RenderUtil.drawRoundedRect(poseStack, centerX - 3.0f, y - 0.5f, 6.0f, 6.0f, 2.9f, ColorUtil.withAlpha(-1, alpha));
    }

    private void renderSmallPill(PoseStack poseStack, String text, float x, float y, float width, float height, float alpha) {
        RenderUtil.drawRoundedRect(poseStack, x, y, width, height, 3.0f, ColorUtil.withAlpha(ColorUtil.fromRGB(60, 60, 60), alpha));
        String clipped = this.clip(text, FontStore.AXIFORMA_BOLD_13, width - 6.0f);
        FontStore.AXIFORMA_BOLD_13.drawStringCentered(poseStack, clipped, x + width / 2.0f,
                y + (height - FontStore.AXIFORMA_BOLD_13.getFontHeight()) / 2.0f,
                ColorUtil.withAlpha(-1, alpha * 0.8f));
    }

    private void applyNumberFromMouse(Value<Number> value, float x, float width, int mouseX) {
        double min = this.numberMetadata(value, "min", 0.0);
        double max = this.numberMetadata(value, "max", 1.0);
        double step = this.numberMetadata(value, "step", 1.0);
        double sliderWidth = Math.max(8.0, width - SIDE_PADDING * 2.0f);
        double ratio = Mth.clamp(((double)mouseX - (x + SIDE_PADDING)) / sliderWidth, 0.0, 1.0);
        double next = this.snap(min + (max - min) * ratio, step);
        next = Mth.clamp(next, min, max);
        if (value.getType() == ValueType.INTEGER || value.getType() == ValueType.KEY_BIND) {
            value.setValue((int)Math.round(next));
        } else {
            value.setValue(Math.round(next * 1000.0) / 1000.0);
        }
    }

    private void applyRangeFromMouse(Value<NumericRange> value, float x, float width, int mouseX, boolean upper) {
        NumericRange range = value.getValue();
        double sliderWidth = Math.max(8.0, width - SIDE_PADDING * 2.0f);
        double ratio = Mth.clamp(((double)mouseX - (x + SIDE_PADDING)) / sliderWidth, 0.0, 1.0);
        double next = range.min() + (range.max() - range.min()) * ratio;
        next = range.step() > 0.0 ? this.snap(next, range.step()) : next;
        if (upper) {
            value.setValue(new NumericRange(range.lower(), next, range.min(), range.max(), range.step(), range.integer()));
        } else {
            value.setValue(new NumericRange(next, range.upper(), range.min(), range.max(), range.step(), range.integer()));
        }
    }

    private float childrenHeight(ValueGroup group, int depth, float amount) {
        if (!group.areChildrenVisible()) {
            return 0.0f;
        }
        float height = 0.0f;
        for (Value<?> child : group.getVisibleChildren()) {
            height += this.getHeight(child, depth + 1);
        }
        return height * amount;
    }

    private float modeChildrenHeight(ModeValueGroup group, int depth, float amount) {
        if (!group.areChildrenVisible()) {
            return 0.0f;
        }
        float height = 0.0f;
        for (Value<?> child : this.visibleModeChildren(group)) {
            height += this.getHeight(child, depth + 1);
        }
        return height * amount;
    }

    private List<Value<?>> visibleModeChildren(ModeValueGroup group) {
        ValueGroup activeGroup = group.getActiveGroup();
        return activeGroup == null ? List.of() : activeGroup.getVisibleChildren();
    }

    private float dropdownHeight(Value<?> value) {
        return DROPDOWN_BASE_HEIGHT + this.options(value).size() * DROPDOWN_ITEM_HEIGHT * this.dropdownAmount(value);
    }

    private float leafHeight(Value<?> value) {
        return switch (value.getType()) {
            case INTEGER, DECIMAL, INT_RANGE, DECIMAL_RANGE -> SLIDER_HEIGHT;
            case ENUM, MULTI_ENUM -> this.dropdownHeight(value);
            case COLOR, GRADIENT -> BOOLEAN_HEIGHT + COLOR_PICKER.getExtraHeight(value, 1.0f);
            default -> BOOLEAN_HEIGHT;
        };
    }

    private ValueColorPicker.Channel gradientChannel(float x, float width, int mouseX, int button) {
        if (button == 1) {
            return ValueColorPicker.Channel.GRADIENT_END;
        }
        float swatch = 12.0f;
        float endX = x + width - 34.0f + swatch + 4.0f;
        return mouseX >= endX ? ValueColorPicker.Channel.GRADIENT_END : ValueColorPicker.Channel.GRADIENT_START;
    }

    private float groupAmount(ValueGroup group) {
        SmoothAnimationTimer timer = this.timer(this.groupTimers, group, this.isGroupExpanded(group) ? 1.0 : 0.0);
        return timer.getValueF();
    }

    private float dropdownAmount(Value<?> value) {
        SmoothAnimationTimer timer = this.timer(this.dropdownTimers, value, this.isDropdownOpen(value) ? 1.0 : 0.0);
        return timer.getValueF();
    }

    private boolean isGroupExpanded(ValueGroup group) {
        return this.groupExpanded.getOrDefault(group, true);
    }

    private boolean isDropdownOpen(Value<?> value) {
        return this.dropdownOpen.getOrDefault(value, false);
    }

    private SmoothAnimationTimer timer(Map<Value<?>, SmoothAnimationTimer> map, Value<?> value, double initial) {
        return map.computeIfAbsent(value, key -> {
            SmoothAnimationTimer timer = new SmoothAnimationTimer();
            timer.setCurrentValue(initial);
            timer.setFromValue(initial);
            timer.setToValue(initial);
            timer.setDuration(1.0);
            return timer;
        });
    }

    private List<String> options(Value<?> value) {
        Object raw = value.getMetadata().get("options");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private String optionLabel(Value<?> value, String option) {
        if (value.getParent() instanceof ModeValueGroup modeGroup) {
            ValueGroup branch = modeGroup.getModes().get(Value.normalizeId(option));
            if (branch != null) {
                return branch.getDisplayName();
            }
        }
        return option;
    }

    @SuppressWarnings("unchecked")
    private boolean isOptionSelected(Value<?> value, String option) {
        if (value.getType() == ValueType.MULTI_ENUM) {
            Object raw = value.getValue();
            return raw instanceof List<?> list && list.contains(option);
        }
        return String.valueOf(value.getValue()).equals(option);
    }

    @SuppressWarnings("unchecked")
    private String multiLabel(Value<?> value) {
        Object raw = value.getValue();
        if (!(raw instanceof List<?> selected) || selected.isEmpty()) {
            return "None";
        }
        String first = String.valueOf(selected.get(0));
        return selected.size() > 1 ? first + "..." : first;
    }

    private double numberMetadata(Value<?> value, String key, double fallback) {
        Object metadataValue = value.getMetadata().get(key);
        return metadataValue instanceof Number number ? number.doubleValue() : fallback;
    }

    private float ratio(double value, double min, double max) {
        if (Math.abs(max - min) < 0.0001) {
            return 0.0f;
        }
        return (float)Mth.clamp((value - min) / (max - min), 0.0, 1.0);
    }

    private double snap(double value, double step) {
        if (step <= 0.0) {
            return value;
        }
        return Math.round(value / step) * step;
    }

    private String formatNumber(Number number) {
        return this.formatNumber(number.doubleValue());
    }

    private String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.valueOf((int)Math.round(value));
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private String clip(String text, CustomFont font, float maxWidth) {
        String clipped = text == null ? "" : text;
        if (font.getStringWidth(clipped) <= maxWidth) {
            return clipped;
        }
        while (clipped.length() > 1 && font.getStringWidth(clipped + "...") > maxWidth) {
            clipped = clipped.substring(0, clipped.length() - 1);
        }
        return clipped + "...";
    }
}
