package shit.zen.modules.impl.render;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import shit.zen.ZenClient;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.movement.GodBridgeAssist;
import shit.zen.modules.impl.movement.Scaffold;
import shit.zen.modules.impl.player.ChestStealer;
import shit.zen.render.DrawContext;
import shit.zen.render.FontPresets;
import shit.zen.render.FontRenderer;
import shit.zen.render.GlHelper;
import shit.zen.render.LiquidGlassSettings;
import shit.zen.render.LiquidGlassStyle;
import shit.zen.render.Paint;
import shit.zen.render.Rectangle;
import shit.zen.render.Renderer;
import shit.zen.render.RoundedRectangle;
import shit.zen.render.Texture;
import shit.zen.value.impl.ModeValue;
import shit.zen.utils.animation.SpringAnimation;
import shit.zen.utils.game.MovementUtil;
import shit.zen.utils.render.ColorUtil;
import shit.zen.utils.render.RenderUtil;

public class DynamicIsland extends Module {
    public static DynamicIsland INSTANCE;

    private static final ResourceLocation LOGO = ResourceLocation.tryParse("mizulune:textures/gui/mizulune_logo.png");
    private static final float TOP_MARGIN = 8.0f;
    private static final float DEFAULT_HEIGHT = 38.0f;
    private static final float MIN_WIDTH = 210.0f;
    private static final float PANEL_RADIUS = 8.0f;
    private static final float STACK_GAP = 6.0f;
    private static final float BRIDGE_COUNTER_HEIGHT = 48.0f;

    public final ModeValue ModeValue = new ModeValue("Mode", "Old", "Liquid Glass").withDefault("Liquid Glass");

    private final shit.zen.hud.DynamicIsland oldIsland = new shit.zen.hud.DynamicIsland();
    private final FontRenderer nameFont = FontPresets.poppinsBold(17.0f);
    private final FontRenderer infoFont = FontPresets.poppinsMedium(11.0f);
    private final FontRenderer notificationFont = FontPresets.poppinsMedium(13.0f);
    private final FontRenderer notificationStatusFont = FontPresets.poppinsBold(11.0f);
    private final FontRenderer scaffoldTitleFont = FontPresets.poppinsBold(14.0f);
    private final FontRenderer scaffoldInfoFont = FontPresets.poppinsMedium(12.0f);
    private final FontRenderer chestTitleFont = FontPresets.poppinsBold(13.0f);
    private final FontRenderer chestSubFont = FontPresets.poppinsMedium(10.0f);
    private final SpringAnimation widthAnim = new SpringAnimation(360.0f, 1.0f, 30.0f, MIN_WIDTH);
    private final SpringAnimation heightAnim = new SpringAnimation(360.0f, 1.0f, 30.0f, DEFAULT_HEIGHT);
    private final SpringAnimation contentAlpha = new SpringAnimation(280.0f, 1.0f, 24.0f, 1.0f);
    private final SpringAnimation scaffoldProgress = new SpringAnimation(260.0f, 1.0f, 22.0f, 0.0f);
    private final List<PendingItemRender> pendingItemRenders = new ArrayList<>();
    private long lastFrameTimestamp;
    private long lastScaffoldProgressTimestamp;
    private String activeContentKey = "";

    public DynamicIsland() {
        super("Dynamic Island", Category.RENDER);
        INSTANCE = this;
    }

    public boolean isLiquidGlassMode() {
        return this.ModeValue.is("Liquid Glass");
    }

    public boolean isOldMode() {
        return this.ModeValue.is("Old");
    }

    public static boolean shouldSuppressChestScreen(ContainerScreen screen) {
        return INSTANCE != null && INSTANCE.shouldRenderChest(screen);
    }

    public static boolean shouldOwnTabOverlay() {
        return INSTANCE != null && INSTANCE.isEnabled() && INSTANCE.isOldMode();
    }

    public static boolean shouldRenderScaffoldCounter() {
        return INSTANCE != null
                && INSTANCE.isEnabled()
                && INSTANCE.isLiquidGlassMode()
                && Scaffold.INSTANCE != null
                && Scaffold.INSTANCE.isCounterOnIslandActive();
    }

    public static boolean shouldRenderBridgeAssistCounter() {
        return INSTANCE != null
                && INSTANCE.isEnabled()
                && INSTANCE.isLiquidGlassMode()
                && GodBridgeAssist.INSTANCE != null
                && GodBridgeAssist.INSTANCE.isCounterOnIslandActive();
    }

    public static boolean shouldRenderGodBridgeCounter() {
        return shouldRenderBridgeAssistCounter();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (this.isOldMode()) {
            this.oldIsland.onRender2D(event);
            return;
        }
        if (this.isLiquidGlassMode() && !mc.options.renderDebug) {
            this.renderLiquidGlass(event);
        }
    }

    private void renderLiquidGlass(Render2DEvent event) {
        ContentSnapshot content = this.resolveContent();
        long now = System.currentTimeMillis();
        float deltaSec = this.updateTiming(now);
        if (!content.key.equals(this.activeContentKey)) {
            this.activeContentKey = content.key;
            this.contentAlpha.reset(0.0f);
            this.contentAlpha.setTargetValue(1.0f);
        }
        this.widthAnim.setTargetValue(content.width);
        this.heightAnim.setTargetValue(content.height);
        this.widthAnim.update(deltaSec);
        this.heightAnim.update(deltaSec);
        this.contentAlpha.update(deltaSec);

        float islandWidth = Math.min(this.widthAnim.getValue(), mc.getWindow().getGuiScaledWidth() - 16.0f);
        float islandHeight = this.heightAnim.getValue();
        float islandX = ((float)mc.getWindow().getGuiScaledWidth() - islandWidth) / 2.0f;
        float islandY = TOP_MARGIN;
        float alpha = Mth.clamp(this.contentAlpha.getValue(), 0.0f, 1.0f);
        this.pendingItemRenders.clear();
        Renderer.render(event.guiGraphics(), drawContext -> {
            RoundedRectangle bounds = RoundedRectangle.ofXYWHR(islandX, islandY, islandWidth, islandHeight, PANEL_RADIUS);
            this.drawIslandBase(drawContext, bounds);
            drawContext.save();
            drawContext.clipRoundedRect(bounds, true);
            if (content.type == ContentType.DEFAULT) {
                this.drawDefaultContent(drawContext, islandX, islandY, islandWidth, islandHeight, alpha);
            } else {
                this.drawPayloadStack(drawContext, content, islandX, islandY, islandWidth, now, alpha);
            }
            drawContext.restore();
        });
        this.renderPendingItems(event, RoundedRectangle.ofXYWHR(islandX, islandY, islandWidth, islandHeight, PANEL_RADIUS));
    }

    private float updateTiming(long now) {
        if (this.lastFrameTimestamp == 0L) {
            this.lastFrameTimestamp = now;
            return 1.0f / 60.0f;
        }
        float deltaSec = (float)(now - this.lastFrameTimestamp) / 1000.0f;
        this.lastFrameTimestamp = now;
        return Mth.clamp(deltaSec, 1.0f / 240.0f, 1.0f / 30.0f);
    }

    private ContentSnapshot resolveContent() {
        BridgeCounterSource bridgeCounter = this.getBridgeCounterSource();
        boolean hasBridgeCounter = bridgeCounter != null;
        ChestMenu chestMenu = this.getActiveChestMenu();
        long now = System.currentTimeMillis();
        List<Notification.IslandNotification> notifications = Notification.visibleNotifications(now);

        if (!hasBridgeCounter && chestMenu == null && notifications.isEmpty()) {
            return ContentSnapshot.defaults(this.measureDefaultWidth(), DEFAULT_HEIGHT);
        }

        int sections = 0;
        float width = MIN_WIDTH;
        float height = 0.0f;
        int chestRows = 0;
        float chestHeight = 0.0f;
        float notificationHeight = 0.0f;

        if (hasBridgeCounter) {
            width = Math.max(width, this.measureBridgeCounterWidth(bridgeCounter));
            height += BRIDGE_COUNTER_HEIGHT;
            sections++;
        }

        if (chestMenu != null) {
            chestRows = Mth.clamp(chestMenu.getRowCount(), 3, 6);
            float cell = 13.0f;
            float gap = 2.0f;
            float gridWidth = 9.0f * cell + 8.0f * gap;
            float gridHeight = chestRows * cell + (chestRows - 1) * gap;
            chestHeight = Math.min(154.0f, gridHeight + 56.0f);
            width = Math.max(width, Math.max(188.0f, gridWidth + 32.0f));
            height += chestHeight;
            sections++;
        }

        if (!notifications.isEmpty()) {
            for (Notification.IslandNotification notification : notifications) {
                String status = notification.isEnabled() ? "Enabled" : "Disabled";
                float rowWidth = 58.0f
                        + GlHelper.getStringWidth(notification.getModuleName(), this.notificationFont)
                        + GlHelper.getStringWidth(status, this.notificationStatusFont);
                width = Math.max(width, rowWidth);
            }
            notificationHeight = notifications.size() == 1 ? 42.0f : 18.0f + notifications.size() * 24.0f;
            height += notificationHeight;
            sections++;
        }

        height += Math.max(0, sections - 1) * STACK_GAP;
        return ContentSnapshot.payloads(width + 24.0f, height, hasBridgeCounter,
                bridgeCounter == null ? "none" : bridgeCounter.key(), chestMenu, chestRows, chestHeight,
                notifications, notificationHeight);
    }

    private float measureDefaultWidth() {
        float logoSize = 20.0f;
        float nameWidth = GlHelper.getStringWidth("MiZuLune", this.nameFont);
        float infoWidth = GlHelper.getStringWidth(this.getInfoText(), this.infoFont);
        return Math.max(MIN_WIDTH, 28.0f + logoSize + 8.0f + nameWidth + 18.0f + infoWidth + 24.0f);
    }

    private float measureBridgeCounterWidth(BridgeCounterSource source) {
        String info = source.blocks() + " blocks available  \u00b7  " + this.getScaffoldSpeedText();
        float textWidth = Math.max(
                GlHelper.getStringWidth(source.title(), this.scaffoldTitleFont),
                GlHelper.getStringWidth(info, this.scaffoldInfoFont));
        return Math.max(230.0f, 48.0f + textWidth + 20.0f);
    }

    private BridgeCounterSource getBridgeCounterSource() {
        if (Scaffold.INSTANCE != null && Scaffold.INSTANCE.isCounterOnIslandActive()) {
            return new BridgeCounterSource("scaffold", "Scaffold Toggle",
                    Scaffold.INSTANCE.getPlaceableBlockCount(),
                    Scaffold.INSTANCE.getCounterBlockItem());
        }
        if (GodBridgeAssist.INSTANCE != null && GodBridgeAssist.INSTANCE.isCounterOnIslandActive()) {
            return new BridgeCounterSource("bridgeassist", "BridgeAssist Toggled",
                    GodBridgeAssist.INSTANCE.getPlaceableBlockCount(),
                    GodBridgeAssist.INSTANCE.getCounterBlockItem());
        }
        return null;
    }

    private ChestMenu getActiveChestMenu() {
        if (mc.screen instanceof ContainerScreen screen && this.shouldRenderChest(screen)) {
            return screen.getMenu();
        }
        return null;
    }

    private boolean shouldRenderChest(ContainerScreen screen) {
        if (!this.isEnabled() || !this.isLiquidGlassMode() || screen == null) {
            return false;
        }
        if (ChestStealer.INSTANCE == null || !ChestStealer.INSTANCE.isSilentOnIslandActive()) {
            return false;
        }
        return true;
    }

    private void drawIslandBase(DrawContext drawContext, RoundedRectangle bounds) {
        drawContext.drawBlurredRoundedRect(bounds, 0.0f, 2.0f, 18.0f, 1.0f, 0x30000000);
        drawContext.drawLiquidGlassPanel(bounds, this.islandGlassStyle());
        try (Paint border = new Paint()) {
            border.setColor(0x55FFFFFF);
            border.setStrokeCap(Paint.StrokeCap.STROKE);
            border.setStrokeWidth(0.8f);
            drawContext.save();
            drawContext.clipRoundedRect(bounds, true);
            drawContext.drawRoundedRect(bounds, border);
            drawContext.restore();
        }
    }

    private LiquidGlassStyle islandGlassStyle() {
        return LiquidGlassStyle.builder()
                .power(PANEL_RADIUS)
                .refractionPower(LiquidGlassSettings.getRefractionPower())
                .refractionStrength(LiquidGlassSettings.getRefractionStrength())
                .noise(LiquidGlassSettings.getNoise())
                .glow(0.16f, 0.03f)
                .glowEdges(0.0f, 0.85f)
                .blurIterations(LiquidGlassSettings.getBlurIterations())
                .blurRadius(LiquidGlassSettings.getBlurRadius())
                .blurDownscale(LiquidGlassSettings.getBlurDownscale())
                .opacity(LiquidGlassSettings.getOpacity())
                .tint(0x66D9F2FF, 0.22f)
                .chromaStrength(0.012f)
                .darkness(Math.max(0.06f, LiquidGlassSettings.getDarkness()))
                .build();
    }

    private void drawDefaultContent(DrawContext drawContext, float x, float y, float width, float height, float alpha) {
        float logoSize = 20.0f;
        float logoX = x + 14.0f;
        float logoY = y + (height - logoSize) / 2.0f;
        try (Paint paint = new Paint()) {
            paint.setColor(this.withAlpha(0xFFFFFFFF, alpha));
            if (LOGO != null) {
                drawContext.drawTexture(new Texture(LOGO, 200, 200),
                        Rectangle.ofXYWH(0.0f, 0.0f, 200.0f, 200.0f),
                        Rectangle.ofXYWH(logoX, logoY, logoSize, logoSize),
                        paint);
            }
        }
        float nameX = logoX + logoSize + 8.0f;
        float textY = y + height / 2.0f - this.nameFont.getMetrics().capHeight() / 2.0f + 1.0f;
        this.drawClientName(nameX, textY, alpha);
        String info = this.getInfoText();
        float infoWidth = GlHelper.getStringWidth(info, this.infoFont);
        float infoX = x + width - infoWidth - 14.0f;
        float infoY = y + height / 2.0f - this.infoFont.getMetrics().capHeight() / 2.0f + 1.0f;
        GlHelper.drawText(info, infoX, infoY, this.infoFont, this.withAlpha(0xDDECF7FF, alpha));
        try (Paint paint = new Paint()) {
            paint.setColor(this.withAlpha(0x30FFFFFF, alpha));
            drawContext.drawRectXYWH(infoX - 9.0f, y + 10.0f, 1.0f, height - 20.0f, paint);
        }
    }

    private void drawClientName(float x, float y, float alpha) {
        float cursor = x;
        String name = "MiZuLune";
        for (int i = 0; i < name.length(); i++) {
            String ch = String.valueOf(name.charAt(i));
            int color = switch (name.charAt(i)) {
                case 'M', 'Z', 'L' -> this.rainbowColor(i * 90, alpha);
                default -> this.withAlpha(0xFFFFFFFF, alpha);
            };
            GlHelper.drawText(ch, cursor, y, this.nameFont, color);
            cursor += GlHelper.getStringWidth(ch, this.nameFont);
        }
    }

    private void drawPayloadStack(DrawContext drawContext, ContentSnapshot content, float x, float y, float width,
                                  long now, float alpha) {
        float cursorY = y;
        boolean needsDivider = false;
        if (content.bridgeCounter) {
            this.drawBridgeCounterContent(drawContext, x, cursorY, width, BRIDGE_COUNTER_HEIGHT, alpha);
            cursorY += BRIDGE_COUNTER_HEIGHT;
            needsDivider = true;
        }
        if (content.chestMenu != null) {
            if (needsDivider) {
                this.drawStackDivider(drawContext, x, cursorY, width, alpha);
                cursorY += STACK_GAP;
            }
            this.drawChestContent(drawContext, content, x, cursorY, width, content.chestHeight, alpha);
            cursorY += content.chestHeight;
            needsDivider = true;
        }
        if (!content.notifications.isEmpty()) {
            if (needsDivider) {
                this.drawStackDivider(drawContext, x, cursorY, width, alpha);
                cursorY += STACK_GAP;
            }
            this.drawNotificationContent(drawContext, content.notifications, x, cursorY, width,
                    content.notificationHeight, now, alpha);
        }
    }

    private void drawStackDivider(DrawContext drawContext, float x, float y, float width, float alpha) {
        try (Paint paint = new Paint()) {
            paint.setColor(this.withAlpha(0x22FFFFFF, alpha));
            drawContext.drawRectXYWH(x + 14.0f, y + STACK_GAP / 2.0f - 0.5f, width - 28.0f, 1.0f, paint);
        }
    }

    private void drawBridgeCounterContent(DrawContext drawContext, float x, float y, float width, float height, float alpha) {
        BridgeCounterSource source = this.getBridgeCounterSource();
        if (source == null) {
            return;
        }
        int blocks = source.blocks();
        ItemStack iconStack = source.iconStack();
        float iconSize = 22.0f;
        float iconX = x + 14.0f;
        float iconY = y + 8.0f;
        if (!iconStack.isEmpty()) {
            this.queueItemRender(iconStack, iconX, iconY, iconSize, alpha, false);
        }
        float textX = iconStack.isEmpty() ? x + 16.0f : x + 46.0f;
        GlHelper.drawText(source.title(), textX, y + 6.0f, this.scaffoldTitleFont, this.withAlpha(0xFFFFFFFF, alpha));
        String info = blocks + " blocks available  \u00b7  " + this.getScaffoldSpeedText();
        GlHelper.drawText(info, textX, y + 23.0f, this.scaffoldInfoFont, this.withAlpha(0xEAF4FFFF, alpha));

        float barX = x + 14.0f;
        float barY = y + height - 8.0f;
        float barWidth = width - 28.0f;
        float barHeight = 4.0f;
        float progress = this.updateScaffoldProgress(blocks);
        this.drawBluePinkProgressBar(drawContext, barX, barY, barWidth, barHeight, progress, alpha);
    }

    private void queueItemRender(ItemStack stack, float x, float y, float size, float alpha, boolean showCount) {
        if (stack.isEmpty() || alpha <= 0.01f) {
            return;
        }
        this.pendingItemRenders.add(new PendingItemRender(stack.copy(), x, y, size, alpha, showCount));
    }

    private void drawBluePinkProgressBar(DrawContext drawContext, float x, float y, float width, float height, float progress, float alpha) {
        try (Paint paint = new Paint()) {
            paint.setColor(this.withAlpha(0x28FFFFFF, alpha));
            drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(x, y, width, height, height / 2.0f), paint);
            float filledWidth = width * Mth.clamp(progress, 0.0f, 1.0f);
            if (filledWidth <= 0.5f) {
                return;
            }
            paint.setColor(0xFFFFFFFF);
            paint.setGradCoords(new Paint.GradientCoords(x, y, x + filledWidth, y,
                    this.withAlpha(0xFF49C7FF, alpha), this.withAlpha(0xFFFF5FDA, alpha)));
            drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(x, y, filledWidth, height, height / 2.0f), paint);
        }
    }

    private String getScaffoldSpeedText() {
        return String.format("%.1f blocks/s", MovementUtil.getSpeedBps());
    }

    private float updateScaffoldProgress(int blocks) {
        long now = System.currentTimeMillis();
        float target = Mth.clamp(blocks / 256.0f, 0.0f, 1.0f);
        if (this.lastScaffoldProgressTimestamp == 0L || now - this.lastScaffoldProgressTimestamp > 1000L) {
            this.lastScaffoldProgressTimestamp = now;
            this.scaffoldProgress.setValue(target);
            this.scaffoldProgress.setTargetValue(target);
            return target;
        }
        float deltaSec = Mth.clamp((now - this.lastScaffoldProgressTimestamp) / 1000.0f, 1.0f / 240.0f, 1.0f / 30.0f);
        this.lastScaffoldProgressTimestamp = now;
        this.scaffoldProgress.setTargetValue(target);
        this.scaffoldProgress.update(deltaSec);
        return Mth.clamp(this.scaffoldProgress.getValue(), 0.0f, 1.0f);
    }

    private void drawNotificationContent(DrawContext drawContext, List<Notification.IslandNotification> notifications,
                                         float x, float y, float width, float height, long now, float contentAlpha) {
        float rowHeight = 22.0f;
        float startY = notifications.size() == 1
                ? y + (height - rowHeight) / 2.0f
                : y + 10.0f;
        for (int i = 0; i < notifications.size(); i++) {
            Notification.IslandNotification notification = notifications.get(i);
            float rowAlpha = contentAlpha * notification.getVisibleProgress(now);
            if (rowAlpha <= 0.01f) {
                continue;
            }
            float rowY = startY + i * 24.0f;
            boolean enabled = notification.isEnabled();
            int accent = enabled ? 0xFF71E19B : 0xFFFF7777;
            this.drawNotificationIcon(drawContext, x + 14.0f, rowY + 3.0f, 16.0f, accent, rowAlpha, enabled);
            float textX = x + 38.0f;
            float nameY = rowY + (rowHeight - this.notificationFont.getMetrics().capHeight()) / 2.0f + 1.0f;
            GlHelper.drawText(notification.getModuleName(), textX, nameY, this.notificationFont, this.withAlpha(0xFFFFFFFF, rowAlpha));
            String status = enabled ? "Enabled" : "Disabled";
            float statusWidth = GlHelper.getStringWidth(status, this.notificationStatusFont);
            GlHelper.drawText(status, x + width - statusWidth - 14.0f,
                    rowY + (rowHeight - this.notificationStatusFont.getMetrics().capHeight()) / 2.0f + 1.0f,
                    this.notificationStatusFont, this.withAlpha(accent, rowAlpha));
        }
    }

    private void drawNotificationIcon(DrawContext drawContext, float x, float y, float size, int accent, float alpha, boolean enabled) {
        int fill = this.withAlpha(accent, 0.22f * alpha);
        int stroke = this.withAlpha(accent, alpha);
        try (Paint paint = new Paint()) {
            paint.setColor(fill);
            drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(x, y, size, size, size / 2.0f), paint);
            paint.setColor(stroke);
            paint.setStrokeCap(Paint.StrokeCap.STROKE);
            paint.setStrokeWidth(1.4f);
            if (enabled) {
                drawContext.drawLine(x + 4.0f, y + 8.5f, x + 7.0f, y + 11.0f, paint);
                drawContext.drawLine(x + 7.0f, y + 11.0f, x + 12.0f, y + 5.0f, paint);
            } else {
                drawContext.drawLine(x + 5.0f, y + 5.0f, x + 11.0f, y + 11.0f, paint);
                drawContext.drawLine(x + 11.0f, y + 5.0f, x + 5.0f, y + 11.0f, paint);
            }
        }
    }

    private void drawChestContent(DrawContext drawContext, ContentSnapshot content, float x, float y,
                                  float width, float height, float alpha) {
        int rows = content.chestRows > 0 ? content.chestRows : Mth.clamp(content.chestMenu.getRowCount(), 3, 6);
        int slotCount = rows * 9;
        float titleY = y + 11.0f;
        GlHelper.drawText("ChestStealer", x + 14.0f, titleY, this.chestTitleFont, this.withAlpha(0xFFFFFFFF, alpha));
        String subtitle = slotCount + " slots";
        float subtitleWidth = GlHelper.getStringWidth(subtitle, this.chestSubFont);
        GlHelper.drawText(subtitle, x + width - subtitleWidth - 14.0f, titleY + 2.0f, this.chestSubFont,
                this.withAlpha(0xBFEAF7FF, alpha));

        float cell = 13.0f;
        float gap = 2.0f;
        float gridWidth = 9.0f * cell + 8.0f * gap;
        float gridX = x + (width - gridWidth) / 2.0f;
        float gridY = y + 39.0f;
        try (Paint paint = new Paint()) {
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < 9; col++) {
                    float slotX = gridX + col * (cell + gap);
                    float slotY = gridY + row * (cell + gap);
                    paint.setColor(this.withAlpha(0x24FFFFFF, alpha));
                    drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(slotX, slotY, cell, cell, 3.0f), paint);
                }
            }
        }
        this.drawChestItems(content.chestMenu, gridX, gridY, cell, gap, rows, alpha);
    }

    private void drawChestItems(ChestMenu chestMenu, float gridX, float gridY,
                                float cell, float gap, int rows, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        float iconSize = 11.0f;
        float iconInset = (cell - iconSize) / 2.0f;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = row * 9 + col;
                ItemStack stack = chestMenu.getSlot(slot).getItem();
                if (stack.isEmpty()) {
                    continue;
                }
                float itemX = gridX + col * (cell + gap) + iconInset;
                float itemY = gridY + row * (cell + gap) + iconInset;
                this.queueItemRender(stack, itemX, itemY, iconSize, alpha, true);
            }
        }
    }

    private void renderPendingItems(Render2DEvent event, RoundedRectangle islandBounds) {
        if (this.pendingItemRenders.isEmpty()) {
            return;
        }
        RenderUtil.pushScissor(Math.round(islandBounds.x1), Math.round(islandBounds.y1),
                Math.round(islandBounds.getWidth()), Math.round(islandBounds.getHeight()));
        try {
            for (PendingItemRender pending : this.pendingItemRenders) {
                this.renderItem(event, pending);
            }
            event.guiGraphics().flush();
        } finally {
            RenderUtil.popScissor();
            this.pendingItemRenders.clear();
        }
    }

    private void renderItem(Render2DEvent event, PendingItemRender pending) {
        if (pending.alpha <= 0.1f || pending.stack.isEmpty()) {
            return;
        }
        int ix = Math.round(pending.x);
        int iy = Math.round(pending.y);
        int itemSize = Math.max(1, Math.round(pending.size));
        event.guiGraphics().setColor(1.0f, 1.0f, 1.0f, Mth.clamp(pending.alpha, 0.0f, 1.0f));
        event.guiGraphics().pose().pushPose();
        event.guiGraphics().pose().translate(ix, iy, 0.0f);
        float scale = itemSize / 16.0f;
        event.guiGraphics().pose().scale(scale, scale, 1.0f);
        event.guiGraphics().renderItem(pending.stack, 0, 0);
        event.guiGraphics().pose().popPose();
        event.guiGraphics().setColor(1.0f, 1.0f, 1.0f, 1.0f);
        if (pending.showCount && pending.stack.getCount() > 1) {
            this.drawItemCount(event, pending);
        }
    }

    private void drawItemCount(Render2DEvent event, PendingItemRender pending) {
        String count = String.valueOf(Math.min(99, pending.stack.getCount()));
        float scale = pending.size < 14.0f ? 0.55f : 0.65f;
        float textX = pending.x + pending.size - mc.font.width(count) * scale + 1.0f;
        float textY = pending.y + pending.size - 7.0f;
        event.guiGraphics().pose().pushPose();
        try {
            event.guiGraphics().pose().translate(textX, textY, 250.0f);
            event.guiGraphics().pose().scale(scale, scale, 1.0f);
            event.guiGraphics().drawString(mc.font, count, 0, 0, this.withAlpha(0xFFFFFFFF, pending.alpha), true);
        } finally {
            event.guiGraphics().pose().popPose();
        }
    }

    private String getInfoText() {
        return mc.getFps() + " FPS  " + this.getPingText() + "  v" + ZenClient.VERSION;
    }

    private String getPingText() {
        if (mc.isSingleplayer()) {
            return "1ms";
        }
        if (mc.getConnection() == null || mc.player == null) {
            return "0ms";
        }
        PlayerInfo playerInfo = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        int ping = playerInfo != null ? Mth.clamp(playerInfo.getLatency(), 0, 9999) : 0;
        return ping + "ms";
    }

    private int rainbowColor(int offset, float alpha) {
        Color rainbow = ColorUtil.getRainbowColor(12, offset);
        int packed = 0xFF000000 | rainbow.getRGB() & 0x00FFFFFF;
        return this.withAlpha(packed, alpha);
    }

    private int withAlpha(int color, float alpha) {
        int baseAlpha = color >>> 24;
        int nextAlpha = Math.round(baseAlpha * Mth.clamp(alpha, 0.0f, 1.0f));
        return nextAlpha << 24 | color & 0x00FFFFFF;
    }

    private static final class PendingItemRender {
        private final ItemStack stack;
        private final float x;
        private final float y;
        private final float size;
        private final float alpha;
        private final boolean showCount;

        private PendingItemRender(ItemStack stack, float x, float y, float size, float alpha, boolean showCount) {
            this.stack = stack;
            this.x = x;
            this.y = y;
            this.size = size;
            this.alpha = alpha;
            this.showCount = showCount;
        }
    }

    private enum ContentType {
        DEFAULT,
        PAYLOADS
    }

    private record BridgeCounterSource(String key, String title, int blocks, ItemStack iconStack) {
    }

    private static final class ContentSnapshot {
        private final ContentType type;
        private final String key;
        private final float width;
        private final float height;
        private final boolean bridgeCounter;
        private final ChestMenu chestMenu;
        private final int chestRows;
        private final float chestHeight;
        private final List<Notification.IslandNotification> notifications;
        private final float notificationHeight;

        private ContentSnapshot(ContentType type, String key, float width, float height, boolean bridgeCounter,
                                ChestMenu chestMenu, int chestRows, float chestHeight,
                                List<Notification.IslandNotification> notifications, float notificationHeight) {
            this.type = type;
            this.key = key;
            this.width = width;
            this.height = height;
            this.bridgeCounter = bridgeCounter;
            this.chestMenu = chestMenu;
            this.chestRows = chestRows;
            this.chestHeight = chestHeight;
            this.notifications = notifications;
            this.notificationHeight = notificationHeight;
        }

        private static ContentSnapshot defaults(float width, float height) {
            return new ContentSnapshot(ContentType.DEFAULT, "default", width, height, false,
                    null, 0, 0.0f, List.of(), 0.0f);
        }

        private static ContentSnapshot payloads(float width, float height, boolean bridgeCounter, String bridgeCounterKey,
                                                ChestMenu chestMenu, int chestRows, float chestHeight,
                                                List<Notification.IslandNotification> notifications,
                                                float notificationHeight) {
            long firstStarted = notifications.isEmpty() ? 0L : notifications.get(0).getStartedAt();
            String key = "payloads:bridge=" + bridgeCounter + ":" + bridgeCounterKey
                    + ":chest=" + (chestMenu == null ? 0 : System.identityHashCode(chestMenu))
                    + ":rows=" + chestRows
                    + ":notifications=" + notifications.size()
                    + ":" + firstStarted;
            return new ContentSnapshot(ContentType.PAYLOADS, key, width, height, bridgeCounter,
                    chestMenu, chestRows, chestHeight, notifications, notificationHeight);
        }
    }
}
