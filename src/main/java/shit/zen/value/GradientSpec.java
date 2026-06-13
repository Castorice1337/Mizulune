package shit.zen.value;

public final class GradientSpec {
    private final MizuColor start;
    private final MizuColor end;

    public GradientSpec(MizuColor start, MizuColor end) {
        this.start = start;
        this.end = end;
    }

    public MizuColor start() {
        return this.start;
    }

    public MizuColor end() {
        return this.end;
    }

    public MizuColor colorAt(float progress) {
        return this.start.interpolateTo(this.end, progress);
    }
}
