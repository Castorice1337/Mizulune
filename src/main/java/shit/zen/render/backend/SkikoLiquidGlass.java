package shit.zen.render.backend;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.skia.Canvas;
import org.jetbrains.skia.FilterTileMode;
import org.jetbrains.skia.Image;
import org.jetbrains.skia.Matrix33;
import org.jetbrains.skia.PaintMode;
import org.jetbrains.skia.RRect;
import org.jetbrains.skia.Rect;
import org.jetbrains.skia.RuntimeEffect;
import org.jetbrains.skia.RuntimeShaderBuilder;
import org.jetbrains.skia.SamplingMode;
import org.jetbrains.skia.Shader;
import shit.zen.render.LiquidGlassStyle;
import shit.zen.utils.misc.Assets;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class SkikoLiquidGlass {
    private static final Logger LOGGER = LogManager.getLogger(SkikoLiquidGlass.class);
    private static final String SHADER_PATH = "/assets/mizulune/shaders/skia/liquid_glass.sksl";
    private RuntimeEffect effect;
    private boolean compileFailed;
    private int renderCount;
    private int missCount;

    boolean draw(Canvas canvas, Image snapshot, RRect clip, Rect dst, LiquidGlassStyle style,
                 float guiScale, int surfaceWidth, int surfaceHeight) {
        if (canvas == null || snapshot == null || clip == null || dst == null || style == null
                || dst.getWidth() <= 0.0f || dst.getHeight() <= 0.0f) {
            this.missCount++;
            return false;
        }
        RuntimeEffect runtimeEffect = this.getEffect();
        if (runtimeEffect == null) {
            this.missCount++;
            return false;
        }
        float scale = Math.max(1.0f, guiScale);
        try (Shader backdrop = snapshot.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP,
                SamplingMode.Companion.getLINEAR(), Matrix33.Companion.getIDENTITY());
             RuntimeShaderBuilder builder = new RuntimeShaderBuilder(runtimeEffect);
             Shader shader = this.makeShader(builder, backdrop, dst, style, scale, surfaceWidth, surfaceHeight);
             org.jetbrains.skia.Paint paint = new org.jetbrains.skia.Paint()) {
            paint.setAntiAlias(true);
            paint.setMode(PaintMode.FILL);
            paint.setShader(shader);
            canvas.save();
            try {
                canvas.clipRRect(clip, true);
                canvas.drawRect(dst, paint);
            } finally {
                canvas.restore();
            }
            this.renderCount++;
            return true;
        } catch (Throwable throwable) {
            this.missCount++;
            LOGGER.warn("Liquid glass SkSL draw failed; using local fallback", throwable);
            return false;
        }
    }

    String debugSummary() {
        return "liquid=" + this.renderCount
                + " liquidMiss=" + this.missCount
                + " liquidShader=" + (this.effect != null)
                + " liquidCompileFailed=" + this.compileFailed;
    }

    private Shader makeShader(RuntimeShaderBuilder builder, Shader backdrop, Rect dst, LiquidGlassStyle style,
                              float guiScale, int surfaceWidth, int surfaceHeight) {
        builder.child("uBackdrop", backdrop);
        builder.uniform("uRect", dst.getLeft(), dst.getTop(), dst.getWidth(), dst.getHeight());
        builder.uniform("uSurfaceSize", (float)surfaceWidth, (float)surfaceHeight);
        builder.uniform("uGuiScale", guiScale);
        builder.uniform("uPowerFactor", style.getPower());
        builder.uniform("uRefractionPower", style.getRefractionPower());
        builder.uniform("uRefractionStrength", style.getRefractionStrength());
        builder.uniform("uNoise", style.getNoise());
        builder.uniform("uGlowWeight", style.getGlowWeight());
        builder.uniform("uGlowBias", style.getGlowBias());
        builder.uniform("uGlowEdge0", style.getGlowEdge0());
        builder.uniform("uGlowEdge1", style.getGlowEdge1());
        builder.uniform("uBlurIterations", (float)style.getBlurIterations());
        builder.uniform("uBlurRadius", style.getBlurRadius());
        builder.uniform("uBlurDownscale", style.getBlurDownscale());
        builder.uniform("uOpacity", style.getOpacity());
        builder.uniform("uTintColor", red(style.getTintColor()), green(style.getTintColor()), blue(style.getTintColor()));
        builder.uniform("uTintStrength", style.getTintStrength());
        builder.uniform("uChromaStrength", style.getChromaStrength());
        builder.uniform("uDarkness", style.getDarkness());
        return builder.makeShader(Matrix33.Companion.getIDENTITY());
    }

    private RuntimeEffect getEffect() {
        if (this.effect != null || this.compileFailed) {
            return this.effect;
        }
        String source = this.readShaderSource();
        if (source == null || source.isBlank()) {
            this.compileFailed = true;
            LOGGER.warn("Liquid glass SkSL source not found: {}", SHADER_PATH);
            return null;
        }
        try {
            this.effect = RuntimeEffect.Companion.makeForShader(source);
            return this.effect;
        } catch (Throwable throwable) {
            this.compileFailed = true;
            LOGGER.warn("Liquid glass SkSL compile failed", throwable);
            return null;
        }
    }

    private String readShaderSource() {
        try (InputStream stream = Assets.open(SHADER_PATH)) {
            if (stream == null) {
                return null;
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.warn("Failed to read liquid glass SkSL source", exception);
            return null;
        }
    }

    private static float red(int color) {
        return (float)(color >> 16 & 0xFF) / 255.0f;
    }

    private static float green(int color) {
        return (float)(color >> 8 & 0xFF) / 255.0f;
    }

    private static float blue(int color) {
        return (float)(color & 0xFF) / 255.0f;
    }
}
