package shit.zen.render;

public final class LiquidGlassStyle {
    private final float power;
    private final float refractionPower;
    private final float refractionStrength;
    private final float noise;
    private final float glowWeight;
    private final float glowBias;
    private final float glowEdge0;
    private final float glowEdge1;
    private final int blurIterations;
    private final float blurRadius;
    private final float blurDownscale;
    private final float opacity;
    private final int tintColor;
    private final float tintStrength;
    private final float chromaStrength;
    private final float darkness;

    private LiquidGlassStyle(Builder builder) {
        this.power = builder.power;
        this.refractionPower = builder.refractionPower;
        this.refractionStrength = builder.refractionStrength;
        this.noise = builder.noise;
        this.glowWeight = builder.glowWeight;
        this.glowBias = builder.glowBias;
        this.glowEdge0 = builder.glowEdge0;
        this.glowEdge1 = builder.glowEdge1;
        this.blurIterations = builder.blurIterations;
        this.blurRadius = builder.blurRadius;
        this.blurDownscale = builder.blurDownscale;
        this.opacity = builder.opacity;
        this.tintColor = builder.tintColor;
        this.tintStrength = builder.tintStrength;
        this.chromaStrength = builder.chromaStrength;
        this.darkness = builder.darkness;
    }

    public static LiquidGlassStyle defaultClear() {
        return builder()
                .power(LiquidGlassSettings.getRadius())
                .refractionPower(LiquidGlassSettings.getRefractionPower())
                .refractionStrength(LiquidGlassSettings.getRefractionStrength())
                .noise(LiquidGlassSettings.getNoise())
                .glow(0.22f, 0.02f)
                .glowEdges(0.0f, 0.9f)
                .blurIterations(LiquidGlassSettings.getBlurIterations())
                .blurRadius(LiquidGlassSettings.getBlurRadius())
                .blurDownscale(LiquidGlassSettings.getBlurDownscale())
                .opacity(LiquidGlassSettings.getOpacity())
                .tint(0x00FFFFFF, 0.0f)
                .chromaStrength(0.0f)
                .darkness(LiquidGlassSettings.getDarkness())
                .build();
    }

    public static LiquidGlassStyle defaultTinted() {
        return builder()
                .power(LiquidGlassSettings.getRadius())
                .refractionPower(LiquidGlassSettings.getRefractionPower())
                .refractionStrength(LiquidGlassSettings.getRefractionStrength())
                .noise(LiquidGlassSettings.getNoise())
                .glow(0.16f, 0.03f)
                .glowEdges(0.0f, 0.85f)
                .blurIterations(LiquidGlassSettings.getBlurIterations())
                .blurRadius(LiquidGlassSettings.getBlurRadius())
                .blurDownscale(LiquidGlassSettings.getBlurDownscale())
                .opacity(LiquidGlassSettings.getOpacity())
                .tint(0x66D9F2FF, 0.22f)
                .chromaStrength(0.012f)
                .darkness(Math.max(0.06f, LiquidGlassSettings.getDarkness()))
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public float getPower() {
        return this.power;
    }

    public float getRefractionPower() {
        return this.refractionPower;
    }

    public float getRefractionStrength() {
        return this.refractionStrength;
    }

    public float getNoise() {
        return this.noise;
    }

    public float getGlowWeight() {
        return this.glowWeight;
    }

    public float getGlowBias() {
        return this.glowBias;
    }

    public float getGlowEdge0() {
        return this.glowEdge0;
    }

    public float getGlowEdge1() {
        return this.glowEdge1;
    }

    public int getBlurIterations() {
        return this.blurIterations;
    }

    public float getBlurRadius() {
        return this.blurRadius;
    }

    public float getBlurDownscale() {
        return this.blurDownscale;
    }

    public float getOpacity() {
        return this.opacity;
    }

    public int getTintColor() {
        return this.tintColor;
    }

    public float getTintStrength() {
        return this.tintStrength;
    }

    public float getChromaStrength() {
        return this.chromaStrength;
    }

    public float getDarkness() {
        return this.darkness;
    }

    public static final class Builder {
        private float power = 4.0f;
        private float refractionPower = 1.25f;
        private float refractionStrength = 1.0f;
        private float noise = 0.012f;
        private float glowWeight = 0.22f;
        private float glowBias = 0.02f;
        private float glowEdge0 = 0.0f;
        private float glowEdge1 = 0.9f;
        private int blurIterations = LiquidGlassSettings.DEFAULT_BLUR_ITERATIONS;
        private float blurRadius = LiquidGlassSettings.DEFAULT_BLUR_RADIUS;
        private float blurDownscale = LiquidGlassSettings.DEFAULT_BLUR_DOWNSCALE;
        private float opacity = LiquidGlassSettings.DEFAULT_OPACITY;
        private int tintColor = 0x00FFFFFF;
        private float tintStrength = 0.0f;
        private float chromaStrength = 0.0f;
        private float darkness = LiquidGlassSettings.DEFAULT_DARKNESS;

        private Builder() {
        }

        public Builder power(float power) {
            this.power = clamp(power, 1.0f, 12.0f);
            return this;
        }

        public Builder refractionPower(float refractionPower) {
            this.refractionPower = clamp(refractionPower, 0.1f, 4.0f);
            return this;
        }

        public Builder refractionStrength(float refractionStrength) {
            this.refractionStrength = clamp(refractionStrength, 0.0f, 1.5f);
            return this;
        }

        public Builder noise(float noise) {
            this.noise = clamp(noise, 0.0f, 0.25f);
            return this;
        }

        public Builder glow(float glowWeight, float glowBias) {
            this.glowWeight = clamp(glowWeight, -1.0f, 1.0f);
            this.glowBias = clamp(glowBias, -0.5f, 0.5f);
            return this;
        }

        public Builder glowEdges(float edge0, float edge1) {
            this.glowEdge0 = clamp(edge0, 0.0f, 1.0f);
            this.glowEdge1 = clamp(edge1, this.glowEdge0 + 0.001f, 1.0f);
            return this;
        }

        public Builder blurIterations(int blurIterations) {
            this.blurIterations = Math.round(clamp(blurIterations, 0.0f, 4.0f));
            return this;
        }

        public Builder blurRadius(float blurRadius) {
            this.blurRadius = clamp(blurRadius, 0.0f, 64.0f);
            return this;
        }

        public Builder blurDownscale(float blurDownscale) {
            this.blurDownscale = clamp(blurDownscale, 0.25f, 2.0f);
            return this;
        }

        public Builder opacity(float opacity) {
            this.opacity = clamp(opacity, 0.0f, 1.0f);
            return this;
        }

        public Builder tint(int tintColor, float tintStrength) {
            this.tintColor = tintColor;
            this.tintStrength = clamp(tintStrength, 0.0f, 1.0f);
            return this;
        }

        public Builder chromaStrength(float chromaStrength) {
            this.chromaStrength = clamp(chromaStrength, 0.0f, 0.08f);
            return this;
        }

        public Builder darkness(float darkness) {
            this.darkness = clamp(darkness, 0.0f, 1.0f);
            return this;
        }

        public LiquidGlassStyle build() {
            return new LiquidGlassStyle(this);
        }

        private static float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
