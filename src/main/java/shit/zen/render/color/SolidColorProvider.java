package shit.zen.render.color;

import shit.zen.value.MizuColor;

public final class SolidColorProvider implements ColorProvider {
    private final MizuColor color;

    public SolidColorProvider(MizuColor color) {
        this.color = color;
    }

    @Override
    public int color(ColorContext context) {
        return this.color.toArgb();
    }
}
