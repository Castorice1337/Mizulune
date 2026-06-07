package shit.zen.render;

import shit.zen.render.GlyphPage;

record Glyph(int u, int v, int width, int height, char value, GlyphPage owner) {
}