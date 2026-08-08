package shit.zen.render.color;

public record ColorContext(long timeMs, int rowIndex, int maxRowIndex, float charProgress) {
    public float rowProgress() {
        return this.maxRowIndex <= 0 ? 0.0f : (float)this.rowIndex / (float)this.maxRowIndex;
    }
}
