package shit.zen.utils.game;

public record BlockPlacementOptions(
        double range,
        double wallRange,
        boolean constructFailResult,
        boolean considerFacingAwayFaces,
        boolean requireDirectionMatch) {

    public static BlockPlacementOptions defaults() {
        return new BlockPlacementOptions(4.5, 4.5, true, false, true);
    }

    public double maxRange() {
        return Math.max(this.range, this.wallRange);
    }

    public BlockPlacementOptions withRange(double range) {
        return new BlockPlacementOptions(range, this.wallRange, this.constructFailResult,
                this.considerFacingAwayFaces, this.requireDirectionMatch);
    }

    public BlockPlacementOptions withWallRange(double wallRange) {
        return new BlockPlacementOptions(this.range, wallRange, this.constructFailResult,
                this.considerFacingAwayFaces, this.requireDirectionMatch);
    }

    public BlockPlacementOptions withConstructFailResult(boolean constructFailResult) {
        return new BlockPlacementOptions(this.range, this.wallRange, constructFailResult,
                this.considerFacingAwayFaces, this.requireDirectionMatch);
    }

    public BlockPlacementOptions withConsiderFacingAwayFaces(boolean considerFacingAwayFaces) {
        return new BlockPlacementOptions(this.range, this.wallRange, this.constructFailResult,
                considerFacingAwayFaces, this.requireDirectionMatch);
    }

    public BlockPlacementOptions withRequireDirectionMatch(boolean requireDirectionMatch) {
        return new BlockPlacementOptions(this.range, this.wallRange, this.constructFailResult,
                this.considerFacingAwayFaces, requireDirectionMatch);
    }
}
