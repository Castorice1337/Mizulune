package shit.zen.render;

public final class ShapeShortcuts {
    public static void drawRoundedRect(DrawContext drawContext, float x, float y, float width, float height,
                                       float radius, Paint paint) {
        drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(x, y, width, height, radius), paint);
    }

    public static void drawShapeShadow(DrawContext drawContext, RoundedRectangle bounds, float blurRadius, int color) {
        drawContext.drawBlurredRoundedRect(bounds, 0.0f, 0.0f, blurRadius, 1.0f, color);
    }

    private ShapeShortcuts() {
    }
}
