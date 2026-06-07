package shit.zen.render;

import shit.zen.ClientBase;
import shit.zen.render.backend.BackendType;

public final class RenderBackendProbe extends ClientBase {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("openzen.render.probe", "false"));
    private static final FontRenderer FONT = Fonts.getRenderer("axiforma_regular.ttf", 14.0f);
    private static final FontRenderer SMALL_FONT = Fonts.getRenderer("axiforma_regular.ttf", 12.0f);
    private static long frameCount;
    private static String lastLoggedStatus = "";

    private RenderBackendProbe() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static void render(DrawContext drawContext) {
        if (!ENABLED || drawContext == null) {
            return;
        }
        frameCount++;
        BackendType configured = Renderer.getBackendType();
        BackendType active = Renderer.getActiveBackendType();
        boolean skikoActive = Renderer.isSkikoEnabled() && active == BackendType.SKIKO;
        boolean fallback = Renderer.isBackendFailed() || configured != active;
        String debugSummary = Renderer.getBackendDebugSummary();
        String status = "configured=" + configured
                + " active=" + active
                + " skikoActive=" + skikoActive
                + " fallback=" + fallback
                + " " + debugSummary;
        logStatus(status);
        drawPanel(drawContext, configured, active, skikoActive, fallback, debugSummary);
    }

    private static void logStatus(String status) {
        if (!status.equals(lastLoggedStatus)) {
            logger.info("OpenZen render backend probe: {}", status);
            lastLoggedStatus = status;
        }
    }

    private static void drawPanel(DrawContext drawContext, BackendType configured, BackendType active,
                                  boolean skikoActive, boolean fallback, String debugSummary) {
        float x = 8.0f;
        float y = 8.0f;
        float width = 292.0f;
        float height = 86.0f;
        int accent = skikoActive ? 0xFF31D0AA : 0xFFFF5F6D;
        int accent2 = skikoActive ? 0xFF7CFFCB : 0xFFFFC371;

        drawContext.save();
        try {
            Paint background = new Paint().setColor(0xDD101317);
            drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(x, y, width, height, 6.0f), background);

            Paint header = new Paint()
                    .setColor(accent)
                    .setGradCoords(new Paint.GradientCoords(x, y, x + width, y, accent, accent2));
            drawContext.drawRoundedRect(RoundedRectangle.ofXYWHRadii(x, y, width, 5.0f,
                    new float[]{6.0f, 6.0f, 0.0f, 0.0f}), header);

            Paint badge = new Paint().setColor(skikoActive ? 0xAA31D0AA : 0xAAFF5F6D);
            drawContext.drawRoundedRect(RoundedRectangle.ofXYWHR(x + 10.0f, y + 16.0f, 54.0f, 20.0f, 4.0f), badge);

            Paint pathPaint = new Paint().setColor(0xCCFFFFFF);
            try (Path checkPath = new Path()) {
                checkPath.moveTo(x + 23.0f, y + 27.0f)
                        .lineTo(x + 31.0f, y + 33.0f)
                        .cubicTo(x + 38.0f, y + 20.0f, x + 47.0f, y + 19.0f, x + 55.0f, y + 23.0f);
                pathPaint.setStrokeCap(Paint.StrokeCap.STROKE);
                pathPaint.setStrokeWidth(2.0f);
                drawContext.drawPath(checkPath, pathPaint);
            }

            drawText(drawContext, "Render Probe", x + 74.0f, y + 14.0f, FONT, 0xFFFFFFFF);
            drawText(drawContext, "configured=" + configured + " active=" + active, x + 74.0f, y + 32.0f, SMALL_FONT, 0xFFE8EDF2);
            drawText(drawContext, "skikoActive=" + skikoActive + " fallback=" + fallback, x + 74.0f, y + 47.0f, SMALL_FONT, fallback ? 0xFFFFC371 : 0xFFB8FFE8);
            drawText(drawContext, debugSummary, x + 12.0f, y + 66.0f, SMALL_FONT, 0xFFB9C2CF);

            Paint pixel = new Paint().setColor(accent2);
            drawContext.drawRectXYWH(x + width - 20.0f, y + height - 20.0f, 4.0f, 4.0f, pixel);
            drawContext.drawRectXYWH(x + width - 13.0f, y + height - 13.0f, 4.0f, 4.0f, pixel);
        } finally {
            drawContext.restore();
        }
    }

    private static void drawText(DrawContext drawContext, String text, float x, float y,
                                 FontRenderer fontRenderer, int color) {
        Paint paint = new Paint().setColor(color);
        drawContext.drawString(text, x, y, fontRenderer, paint);
    }
}
