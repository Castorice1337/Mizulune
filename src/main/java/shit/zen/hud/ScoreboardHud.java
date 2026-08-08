package shit.zen.hud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Team;
import shit.zen.ZenClient;
import shit.zen.event.impl.GlRenderEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.modules.impl.render.Interface;
import shit.zen.render.DrawContext;
import shit.zen.render.FontPresets;
import shit.zen.render.FontRenderer;
import shit.zen.render.GlHelper;
import shit.zen.render.LiquidGlassSettings;
import shit.zen.render.LiquidGlassStyle;
import shit.zen.render.Paint;
import shit.zen.render.Renderer;
import shit.zen.render.RoundedRectangle;
import shit.zen.render.TextGlow;
import shit.zen.utils.render.Argb;
import shit.zen.value.MizuColor;
import shit.zen.value.ToggleValueGroup;
import shit.zen.value.Value;
import shit.zen.value.ValueGroup;

public class ScoreboardHud extends HudElement {
    private static final int MAX_ROWS = 15;
    private static final String DEFAULT_PAYLOAD_MATCH = "\u5E03\u5409\u5C9B";

    private final FontRenderer titleFont = FontPresets.pingfang(17.0f);
    private final FontRenderer rowFont = FontPresets.pingfang(14.0f);
    private final FontRenderer scoreFont = FontPresets.poppinsMedium(12.5f);
    private final Paint paint = new Paint();

    private Value<Boolean> liquidGlass;
    private Value<Boolean> backgroundBlur;
    private Value<Boolean> panelGlow;
    private Value<Boolean> fontGlow;
    private Value<Boolean> showScores;
    private Value<Boolean> hideRedNumbers;
    private Value<Number> paddingX;
    private Value<Number> paddingY;
    private Value<Number> rowHeight;
    private Value<Number> radius;
    private Value<Number> minWidth;
    private Value<MizuColor> titleColor;
    private Value<MizuColor> textColor;
    private Value<MizuColor> scoreColor;
    private Value<MizuColor> fallbackGlassColor;
    private Value<MizuColor> glowColor;
    private Value<Boolean> customPayloadEnabled;
    private Value<String> payloadMatch;
    private Value<String> payloadText;

    public ScoreboardHud() {
        super("Scoreboard");
        float screenWidth = mc != null && mc.getWindow() != null ? mc.getWindow().getGuiScaledWidth() : 854.0f;
        float screenHeight = mc != null && mc.getWindow() != null ? mc.getWindow().getGuiScaledHeight() : 480.0f;
        this.setX(Math.max(4.0f, screenWidth - 150.0f));
        this.setY(Math.max(24.0f, screenHeight * 0.5f - 54.0f));
        this.setWidth(136.0f);
        this.setHeight(84.0f);
        this.setEnabled(true);
    }

    @Override
    protected void configureValueTree(ValueGroup root) {
        ValueGroup layout = root.group("layout", "Layout");
        this.paddingX = layout.decimal("padding_x", "Padding X", 9.0f, 2.0f, 24.0f, 0.5f);
        this.paddingY = layout.decimal("padding_y", "Padding Y", 7.0f, 2.0f, 20.0f, 0.5f);
        this.rowHeight = layout.decimal("row_height", "Row Height", 13.0f, 9.0f, 24.0f, 0.5f);
        this.radius = layout.decimal("radius", "Radius", 7.0f, 1.0f, 18.0f, 0.5f);
        this.minWidth = layout.decimal("min_width", "Min Width", 118.0f, 70.0f, 240.0f, 1.0f);

        ToggleValueGroup appearance = root.toggleGroup("appearance", "Appearance", true);
        this.liquidGlass = appearance.bool("liquid_glass", "Liquid Glass", true).alias("liquidglass");
        this.backgroundBlur = appearance.bool("background_blur", "Background Blur", true)
                .visibleWhen(() -> !Boolean.TRUE.equals(this.liquidGlass.getValue()));
        this.panelGlow = appearance.bool("glow", "Glow", true);
        this.fontGlow = appearance.bool("font_glow", "Font Glow", true);
        this.showScores = appearance.bool("show_scores", "Show Scores", true)
                .alias("Show Scores")
                .visibleWhen(() -> false);
        this.hideRedNumbers = appearance.bool("hide_red_numbers", "Hide Red Numbers", false)
                .alias("Hide Scores")
                .alias("Hide Score Numbers");

        ValueGroup colors = root.group("colors", "Colors");
        this.titleColor = colors.color("title", "Title", MizuColor.ofArgb(255, 255, 255, 255));
        this.textColor = colors.color("text", "Text", MizuColor.ofArgb(238, 242, 248, 255));
        this.scoreColor = colors.color("score", "Score", MizuColor.ofArgb(255, 255, 105, 119))
                .visibleWhen(this::shouldRenderScores);
        this.fallbackGlassColor = colors.color("fallback_glass", "Fallback Glass", MizuColor.ofArgb(142, 255, 255, 255));
        this.glowColor = colors.color("glow", "Glow", MizuColor.ofArgb(122, 155, 220, 255));

        ToggleValueGroup payload = root.toggleGroup("custom_payload", "Custom Payload", true);
        this.customPayloadEnabled = payload.getEnabledValue();
        this.payloadMatch = payload.text("match", "Match", DEFAULT_PAYLOAD_MATCH).metadata("max_length", 48);
        this.payloadText = payload.text("text", "Text", "Mizulune").metadata("max_length", 96);
    }

    @Override
    public void onRender2D(Render2DEvent event, float x, float y) {
        if (!this.shouldRenderCustom()) {
            return;
        }
        Objective objective = this.sidebarObjective();
        if (objective == null) {
            this.setWidth(0.0f);
            this.setHeight(0.0f);
            return;
        }
        List<ScoreboardRow> rows = this.collectRows(objective);
        if (rows.isEmpty()) {
            this.setWidth(0.0f);
            this.setHeight(0.0f);
            return;
        }

        String title = this.rewritePayload(objective.getDisplayName().getString());
        Layout layout = this.measure(title, rows);
        this.clampToScreen(layout.width(), layout.height());
        this.setWidth(layout.width());
        this.setHeight(layout.height());

        float drawX = this.getX();
        float drawY = this.getY();
        Renderer.render(event.guiGraphics(), drawContext -> this.renderPanel(drawContext, title, rows, drawX, drawY, layout));
    }

    @Override
    public void onGlRender(GlRenderEvent event, float x, float y) {
    }

    public static boolean shouldCancelVanillaSidebar() {
        if (!ZenClient.isReady()) {
            return false;
        }
        try {
            ScoreboardHud scoreboard = ZenClient.getInstance().getHudManager().getHudElement(ScoreboardHud.class);
            return scoreboard != null && scoreboard.shouldRenderCustom();
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean shouldRenderCustom() {
        if (!this.isEnabled() || mc == null || mc.level == null || mc.player == null) {
            return false;
        }
        try {
            Interface interfaceModule = ZenClient.getInstance().getModuleManager().getModule(Interface.class);
            return interfaceModule == null || interfaceModule.isEnabled();
        } catch (Exception ignored) {
            return true;
        }
    }

    private Objective sidebarObjective() {
        if (mc.level == null || mc.player == null) {
            return null;
        }
        net.minecraft.world.scores.Scoreboard scoreboard = mc.level.getScoreboard();
        Objective teamObjective = null;
        PlayerTeam playerTeam = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
        if (playerTeam != null) {
            ChatFormatting color = playerTeam.getColor();
            int colorId = color.getId();
            if (colorId >= 0) {
                teamObjective = scoreboard.getDisplayObjective(3 + colorId);
            }
        }
        return teamObjective != null ? teamObjective : scoreboard.getDisplayObjective(net.minecraft.world.scores.Scoreboard.DISPLAY_SLOT_SIDEBAR);
    }

    private List<ScoreboardRow> collectRows(Objective objective) {
        net.minecraft.world.scores.Scoreboard scoreboard = objective.getScoreboard();
        Collection<Score> scores = scoreboard.getPlayerScores(objective);
        List<Score> visibleScores = scores.stream()
                .filter(score -> score.getOwner() != null && !score.getOwner().startsWith("#"))
                .toList();
        int start = Math.max(0, visibleScores.size() - MAX_ROWS);
        List<ScoreboardRow> rows = new ArrayList<>();
        for (int i = visibleScores.size() - 1; i >= start; i--) {
            Score score = visibleScores.get(i);
            Team team = scoreboard.getPlayersTeam(score.getOwner());
            Component name = PlayerTeam.formatNameForTeam(team, Component.literal(score.getOwner()));
            rows.add(new ScoreboardRow(this.rewritePayload(name.getString()), score.getScore()));
        }
        return rows;
    }

    private Layout measure(String title, List<ScoreboardRow> rows) {
        float paddingXValue = this.floatValue(this.paddingX);
        float paddingYValue = this.floatValue(this.paddingY);
        float rowHeightValue = this.floatValue(this.rowHeight);
        float contentWidth = GlHelper.getStringWidth(title, this.titleFont);
        boolean renderScores = this.shouldRenderScores();
        for (ScoreboardRow row : rows) {
            float rowWidth = GlHelper.getStringWidth(row.name(), this.rowFont);
            if (renderScores) {
                rowWidth += 9.0f + GlHelper.getStringWidth(String.valueOf(row.score()), this.scoreFont);
            }
            contentWidth = Math.max(contentWidth, rowWidth);
        }
        float screenWidth = mc != null && mc.getWindow() != null ? mc.getWindow().getGuiScaledWidth() : 854.0f;
        float maxWidth = Math.max(72.0f, screenWidth - 8.0f);
        float width = Math.min(maxWidth, Math.max(this.floatValue(this.minWidth), contentWidth + paddingXValue * 2.0f));
        float titleHeight = this.titleFont.getMetrics().height() + 4.0f;
        float height = paddingYValue * 2.0f + titleHeight + rows.size() * rowHeightValue;
        return new Layout(width, height, titleHeight, paddingXValue, paddingYValue, rowHeightValue);
    }

    private void renderPanel(DrawContext ctx, String title, List<ScoreboardRow> rows, float x, float y, Layout layout) {
        float radiusValue = this.floatValue(this.radius);
        RoundedRectangle bounds = RoundedRectangle.ofXYWHR(x, y, layout.width(), layout.height(), radiusValue);

        if (Boolean.TRUE.equals(this.panelGlow.getValue())) {
            ctx.drawBlurredRoundedRect(bounds, 0.0f, 0.0f, 14.0f, 3.0f, Argb.scaleAlpha(this.glowColor.getValue().toArgb(), 0.42f));
        }

        if (Boolean.TRUE.equals(this.liquidGlass.getValue())) {
            ctx.drawLiquidGlassPanel(bounds, this.liquidGlassStyle());
        } else {
            this.drawFallbackGlass(ctx, bounds);
        }

        this.paint.setStrokeCap(Paint.StrokeCap.STROKE)
                .setStrokeWidth(0.75f)
                .setColor(Argb.scaleAlpha(0xFFFFFFFF, 0.34f));
        ctx.drawRoundedRect(bounds, this.paint);
        this.paint.setStrokeCap(Paint.StrokeCap.FILL);

        String visibleTitle = this.clipText(title, this.titleFont, layout.width() - layout.paddingX() * 2.0f);
        float titleWidth = GlHelper.getStringWidth(visibleTitle, this.titleFont);
        float titleX = x + (layout.width() - titleWidth) * 0.5f;
        float titleY = y + layout.paddingY();
        this.drawText(visibleTitle, titleX, titleY, this.titleFont, this.titleColor.getValue().toArgb(), 8.0f);

        float rowY = y + layout.paddingY() + layout.titleHeight();
        for (ScoreboardRow row : rows) {
            boolean renderScores = this.shouldRenderScores();
            String scoreText = String.valueOf(row.score());
            float scoreWidth = renderScores ? GlHelper.getStringWidth(scoreText, this.scoreFont) : 0.0f;
            float nameWidth = layout.width() - layout.paddingX() * 2.0f - (renderScores ? scoreWidth + 9.0f : 0.0f);
            String visibleName = this.clipText(row.name(), this.rowFont, Math.max(12.0f, nameWidth));
            this.drawText(visibleName, x + layout.paddingX(), rowY + 1.0f, this.rowFont, this.textColor.getValue().toArgb(), 6.0f);
            if (renderScores) {
                this.drawText(scoreText, x + layout.width() - layout.paddingX() - scoreWidth, rowY + 1.0f,
                        this.scoreFont, this.scoreColor.getValue().toArgb(), 5.0f);
            }
            rowY += layout.rowHeight();
        }
    }

    private void drawFallbackGlass(DrawContext ctx, RoundedRectangle bounds) {
        int tint = this.fallbackGlassColor.getValue().toArgb();
        if (Boolean.TRUE.equals(this.backgroundBlur.getValue())) {
            ctx.drawBackdropBlurredRoundedRect(bounds, 9.0f, 0.62f, 0x00000000);
        }

        ctx.drawBlurredRoundedRect(bounds, 0.0f, 2.0f, 8.0f, 1.8f, Argb.scaleAlpha(0xFF000000, 0.20f));
        ctx.drawBlurredRoundedRect(bounds, 0.0f, 0.8f, 5.0f, 0.6f, Argb.scaleAlpha(tint, 0.18f));

        this.paint.setGradCoords(new Paint.GradientCoords(bounds.x1, bounds.y1, bounds.x1, bounds.y2,
                        Argb.scaleAlpha(tint, 0.78f),
                        Argb.scaleAlpha(0xFFF5F8FF, 0.38f)))
                .setStrokeCap(Paint.StrokeCap.FILL)
                .setColor(0xFFFFFFFF);
        ctx.drawRoundedRect(bounds, this.paint);

        this.paint.setGradCoords(null)
                .setColor(Argb.scaleAlpha(0xFF10141B, 0.08f));
        ctx.drawRoundedRect(bounds, this.paint);

        this.paint.setColor(Argb.scaleAlpha(0xFFFFFFFF, 0.18f));
        ctx.drawRoundedRect(RoundedRectangle.ofXYWHR(
                bounds.x1 + bounds.topLeftRadius * 0.75f,
                bounds.y1 + 1.1f,
                Math.max(0.0f, bounds.getWidth() - bounds.topLeftRadius * 1.5f),
                0.75f,
                0.35f
        ), this.paint);
    }

    private void drawText(String text, float x, float y, FontRenderer font, int color, float glowRadius) {
        if (Boolean.TRUE.equals(this.fontGlow.getValue())) {
            TextGlow.drawGlowText(text, x, y, font, color, Argb.scaleAlpha(this.glowColor.getValue().toArgb(), 0.72f), glowRadius);
            return;
        }
        this.paint.setGradCoords(null).setColor(color);
        DrawContext ctx = Renderer.getCanvas();
        if (ctx != null) {
            ctx.drawString(text, x, y, font, this.paint);
        } else {
            GlHelper.drawText(text, x, y, font, color);
        }
    }

    private LiquidGlassStyle liquidGlassStyle() {
        return LiquidGlassStyle.builder()
                .power(Math.max(1.0f, this.floatValue(this.radius)))
                .refractionPower(LiquidGlassSettings.getRefractionPower())
                .refractionStrength(LiquidGlassSettings.getRefractionStrength())
                .noise(LiquidGlassSettings.getNoise())
                .glow(0.18f, 0.02f)
                .glowEdges(0.0f, 0.88f)
                .blurIterations(LiquidGlassSettings.getBlurIterations())
                .blurRadius(LiquidGlassSettings.getBlurRadius())
                .blurDownscale(LiquidGlassSettings.getBlurDownscale())
                .opacity(LiquidGlassSettings.getOpacity())
                .tint(0x72F6FBFF, 0.18f)
                .chromaStrength(0.01f)
                .darkness(Math.max(0.04f, LiquidGlassSettings.getDarkness()))
                .build();
    }

    private String rewritePayload(String text) {
        if (!Boolean.TRUE.equals(this.customPayloadEnabled.getValue())) {
            return text;
        }
        String match = this.payloadMatch.getValue();
        String replacement = this.payloadText.getValue();
        if (match == null || match.isEmpty() || replacement == null || !text.contains(match)) {
            return text;
        }
        return text.replace(match, replacement);
    }

    private void clampToScreen(float width, float height) {
        if (mc == null || mc.getWindow() == null) {
            return;
        }
        float maxX = Math.max(4.0f, mc.getWindow().getGuiScaledWidth() - width - 4.0f);
        float maxY = Math.max(4.0f, mc.getWindow().getGuiScaledHeight() - height - 4.0f);
        this.setX(Mth.clamp(this.getX(), 4.0f, maxX));
        this.setY(Mth.clamp(this.getY(), 4.0f, maxY));
    }

    private String clipText(String text, FontRenderer font, float maxWidth) {
        if (text == null || text.isEmpty() || GlHelper.getStringWidth(text, font) <= maxWidth) {
            return text == null ? "" : text;
        }
        String clipped = text;
        while (clipped.length() > 1 && GlHelper.getStringWidth(clipped + "...", font) > maxWidth) {
            clipped = clipped.substring(0, clipped.length() - 1);
        }
        return clipped + "...";
    }

    private float floatValue(Value<Number> value) {
        return value.getValue().floatValue();
    }

    private boolean shouldRenderScores() {
        boolean show = this.showScores == null || Boolean.TRUE.equals(this.showScores.getValue());
        boolean hide = this.hideRedNumbers != null && Boolean.TRUE.equals(this.hideRedNumbers.getValue());
        return show && !hide;
    }

    private record ScoreboardRow(String name, int score) {
    }

    private record Layout(float width, float height, float titleHeight, float paddingX, float paddingY, float rowHeight) {
    }
}
