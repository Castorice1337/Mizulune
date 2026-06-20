package shit.zen.render;

public final class TextRendererFacade {
    public static float drawText(DrawContext drawContext, String text, float x, float y, FontRenderer fontRenderer, int color) {
        if (text == null || text.isEmpty()) {
            return x;
        }
        Paint paint = new Paint().setColor(color);
        float baselineY = y + (float)GlHelper.getFontAscent(fontRenderer);
        drawContext.drawString(text, x, baselineY, fontRenderer, paint);
        return x + TextMetricsCache.getStringWidth(text, fontRenderer);
    }

    public static float drawTextWithShadow(DrawContext drawContext, String text, float x, float y,
                                           FontRenderer fontRenderer, int color) {
        int shadowAlpha = Math.round((float)(color >>> 24) * 0.65f);
        int shadowColor = shadowAlpha << 24;
        drawText(drawContext, text, x + 0.5f, y + 0.5f, fontRenderer, shadowColor);
        return drawText(drawContext, text, x, y, fontRenderer, color);
    }

    private TextRendererFacade() {
    }
}
