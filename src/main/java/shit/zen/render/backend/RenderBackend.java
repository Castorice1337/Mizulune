package shit.zen.render.backend;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import shit.zen.render.DrawContext;
import shit.zen.render.FontRenderer;
import shit.zen.render.Paint;
import shit.zen.render.Path;
import shit.zen.render.Rectangle;
import shit.zen.render.RoundedRectangle;
import shit.zen.render.Texture;

public interface RenderBackend {
    BackendType type();

    default boolean handles2D() {
        return false;
    }

    default String debugSummary() {
        return this.type().name();
    }

    default void begin(GuiGraphics guiGraphics, PoseStack poseStack) {
    }

    default void end() {
    }

    default void flush() {
    }

    default void beforeExternalGlDraw() {
        this.flush();
    }

    default void afterExternalGlDraw() {
    }

    default void pushExternalPose(PoseStack poseStack) {
    }

    default void popExternalPose() {
    }

    default void save(DrawContext drawContext) {
    }

    default void restore(DrawContext drawContext) {
    }

    default void translate(float x, float y) {
    }

    default void scale(float scaleX, float scaleY) {
    }

    default void rotate(float degrees) {
    }

    default void clipRect(DrawContext drawContext, Rectangle rectangle) {
    }

    default void clipRoundedRect(DrawContext drawContext, RoundedRectangle roundedRectangle) {
    }

    default void clearClipStack() {
    }

    default void drawRect(DrawContext drawContext, float x, float y, float width, float height, Paint paint) {
    }

    default void drawRoundedRect(DrawContext drawContext, RoundedRectangle roundedRectangle, Paint paint) {
    }

    default void drawLine(DrawContext drawContext, float x1, float y1, float x2, float y2, Paint paint) {
    }

    default void drawString(DrawContext drawContext, String text, float x, float y, FontRenderer fontRenderer, Paint paint) {
    }

    default float measureTextWidth(String text, FontRenderer fontRenderer) {
        return fontRenderer == null || text == null ? 0.0f : fontRenderer.getWidth(text);
    }

    default void drawArc(DrawContext drawContext, float x1, float y1, float x2, float y2, float startAngle, float sweepAngle, Paint paint) {
    }

    default void drawPath(DrawContext drawContext, Path path, Paint paint) {
    }

    default void drawTexture(DrawContext drawContext, Texture texture, Rectangle srcRect, Rectangle dstRect, Paint paint) {
    }

    default void drawBlurredRoundedRect(DrawContext drawContext, RoundedRectangle roundedRectangle,
                                        float offsetX, float offsetY, float blurRadius, float spread, int color) {
    }

    default void drawBlur(DrawContext drawContext, float x, float y, float width, float height, float radius, Runnable runnable) {
        runnable.run();
    }
}
