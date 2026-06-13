package shit.zen.render.color;

import shit.zen.value.MizuColor;

public final class GradientColorProvider implements ColorProvider {
    private final MizuColor start;
    private final MizuColor end;
    private final String mode;
    private final boolean dynamic;
    private final boolean animated;
    private final float animationSpeed;

    public GradientColorProvider(MizuColor start, MizuColor end, String mode, boolean dynamic, boolean animated, float animationSpeed) {
        this.start = start;
        this.end = end;
        this.mode = mode;
        this.dynamic = dynamic;
        this.animated = animated;
        this.animationSpeed = animationSpeed;
    }

    @Override
    public int color(ColorContext context) {
        float progress = this.dynamic
                ? (context.rowProgress() + context.charProgress()) * 0.5f
                : "horizontal_text".equals(this.mode) || "Horizontal Text".equals(this.mode) ? context.charProgress() : context.rowProgress();
        if (this.animated) {
            progress = cyclic(progress + this.dynamicGradientOffset(context.timeMs()));
        }
        return this.start.interpolateTo(this.end, progress).toArgb();
    }

    private float dynamicGradientOffset(long timeMs) {
        return (float)(timeMs % 60000L) * this.animationSpeed / 360000.0f;
    }

    private static float cyclic(float progress) {
        float wrapped = progress - (float)Math.floor(progress);
        return wrapped <= 0.5f ? wrapped * 2.0f : (1.0f - wrapped) * 2.0f;
    }
}
