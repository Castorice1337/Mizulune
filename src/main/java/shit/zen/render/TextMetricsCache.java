package shit.zen.render;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TextMetricsCache {
    private static final Map<FontRenderer, Map<String, Float>> CACHE = new ConcurrentHashMap<>();

    public static float getStringWidth(String text, FontRenderer fontRenderer) {
        if (text == null || text.isEmpty()) {
            return 0.0f;
        }
        DrawContext drawContext = Renderer.getCanvas();
        if (drawContext != null && drawContext.getBackend() != null && drawContext.getBackend().handles2D()) {
            return drawContext.getBackend().measureTextWidth(text, fontRenderer);
        }
        return CACHE.computeIfAbsent(fontRenderer, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(text, key -> fontRenderer.getWidth(key.replaceAll("搂.", "")));
    }

    public static void clear() {
        CACHE.clear();
    }

    private TextMetricsCache() {
    }
}
