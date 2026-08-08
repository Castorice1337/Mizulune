package shit.zen.fabric.render;

/** Thread-local replacement for the removed 1.20 Tesselator singleton. */
public final class LegacyTesselator {
    private static final ThreadLocal<LegacyTesselator> INSTANCE =
            ThreadLocal.withInitial(LegacyTesselator::new);
    private final LegacyBufferBuilder builder = new LegacyBufferBuilder();

    private LegacyTesselator() {
    }

    public static LegacyTesselator getInstance() {
        return INSTANCE.get();
    }

    public LegacyBufferBuilder getBuilder() {
        return this.builder;
    }

    public void end() {
        LegacyBufferUploader.draw(this.builder.end());
    }
}
