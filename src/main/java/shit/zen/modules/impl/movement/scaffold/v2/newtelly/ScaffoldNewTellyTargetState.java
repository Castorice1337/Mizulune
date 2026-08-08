package shit.zen.modules.impl.movement.scaffold.v2.newtelly;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Maintains the active New Telly bridge layer. */
public final class ScaffoldNewTellyTargetState {
    private static final double BRIDGE_TOP_DROP_GRACE = 0.5;
    private static final int BRIDGE_TOP_GRACE_TICKS = 3;

    private Integer bridgeY;
    private Integer requestedBridgeY;
    private BlockPos pendingCell;
    private int pendingAge;
    private int bridgeTransitionAge;
    private BlockPos recentPlacedCell;
    private int recentPlacedAge;
    private int bridgeTopGraceTicks;
    private boolean lastPhysicalJump;
    private boolean bridgePromotionBlocked;

    public TickState beginTick(
            Vec3 playerPosition,
            int playerBlockY,
            boolean onGround,
            boolean physicalJump) {
        return this.beginTick(
                playerPosition,
                playerBlockY,
                onGround,
                physicalJump,
                false);
    }

    public TickState beginTick(
            Vec3 playerPosition,
            int playerBlockY,
            boolean onGround,
            boolean physicalJump,
            boolean activeLayerSupportsRequestedY) {
        if (playerPosition == null) {
            this.reset();
            return new TickState(null, null, false);
        }
        if (this.pendingCell != null) {
            this.pendingAge++;
            if (this.requestedBridgeY != null) {
                this.bridgeTransitionAge++;
            }
        }
        if (this.recentPlacedCell != null) {
            this.recentPlacedAge++;
        }

        boolean physicalJumpStarted = physicalJump && !this.lastPhysicalJump;
        // Follow upward UpTelly crossings only. Falling recovery belongs to Clutch.
        int nextBridgeY = playerBlockY - 1;
        if (this.bridgeY == null && !onGround) {
            this.lastPhysicalJump = physicalJump;
            return new TickState(null, null, physicalJumpStarted);
        }
        double feetBelowBridgeTop = this.bridgeY == null
                ? 0.0
                : this.bridgeY + 1.0 - playerPosition.y;
        this.bridgeTopGraceTicks = !onGround && feetBelowBridgeTop > 0.0
                ? this.bridgeTopGraceTicks + 1
                : 0;
        if (this.bridgeY != null
                && !onGround
                && (feetBelowBridgeTop > BRIDGE_TOP_DROP_GRACE
                || this.bridgeTopGraceTicks > BRIDGE_TOP_GRACE_TICKS)) {
            this.clearPendingCell();
            this.clearBridgeTransitionRequest();
            this.clearRecentPlacedCell();
            this.lastPhysicalJump = physicalJump;
            return new TickState(this.bridgeY, null, physicalJumpStarted);
        }
        if (this.bridgeY == null || onGround) {
            if (this.bridgeY != null && this.bridgeY != nextBridgeY) {
                this.clearPendingCell();
                this.clearRecentPlacedCell();
            }
            this.bridgeY = nextBridgeY;
            this.clearBridgeTransitionRequest();
        } else if (this.requestedBridgeY != null) {
            if (nextBridgeY < this.requestedBridgeY) {
                this.clearBridgeTransitionRequest();
            } else if (this.pendingCell == null && activeLayerSupportsRequestedY) {
                if (nextBridgeY >= this.requestedBridgeY) {
                    this.bridgeY = this.requestedBridgeY;
                }
                this.clearBridgeTransitionRequest();
            }
        } else if (physicalJump
                && nextBridgeY > this.bridgeY
                && !this.bridgePromotionBlocked) {
            int requestedY = Math.min(nextBridgeY, this.bridgeY + 1);
            if (this.pendingCell == null && activeLayerSupportsRequestedY) {
                this.bridgeY = requestedY;
            } else {
                this.requestedBridgeY = requestedY;
                // A deferred transaction owns its own bounded lifetime.
                this.pendingAge = 0;
                this.bridgeTransitionAge = 0;
                this.bridgePromotionBlocked = false;
            }
        }
        this.lastPhysicalJump = physicalJump;

        BlockPos currentCell = new BlockPos(
                (int) Math.floor(playerPosition.x),
                this.bridgeY,
                (int) Math.floor(playerPosition.z));
        return new TickState(this.bridgeY, currentCell, physicalJumpStarted);
    }

    public void rememberPending(BlockPos placedBlockPos) {
        if (this.hasDeferredBridgeTransition()) {
            return;
        }
        BlockPos next = placedBlockPos == null ? null : placedBlockPos.immutable();
        if (next == null) {
            this.clearPendingCell();
            return;
        }
        this.pendingCell = next;
        // Age measures consecutive ticks without a fresh confirmation.
        this.pendingAge = 0;
        if (this.requestedBridgeY != null) {
            this.bridgeTransitionAge = 0;
        }
    }

    public void expirePendingAfter(int maxAge) {
        if (maxAge < 0 || this.pendingAge > maxAge) {
            this.clearPending();
        }
    }

    public void expireBridgeTransitionAfter(int maxAge) {
        if (this.hasDeferredBridgeTransition()
                && (maxAge < 0 || this.bridgeTransitionAge > maxAge)) {
            this.clearPending();
        }
    }

    public void discardPendingOutside(BlockPos currentCell) {
        this.discardPendingOutside(currentCell, 1);
    }

    public void discardPendingOutside(BlockPos currentCell, int maxHorizontalDistance) {
        int safeDistance = Math.max(0, maxHorizontalDistance);
        this.discardPendingOutside(currentCell, safeDistance, safeDistance * 2);
    }

    public void discardPendingOutside(
            BlockPos currentCell,
            int maxAxisDistance,
            int maxManhattanDistance) {
        if (this.pendingCell == null) {
            return;
        }
        int safeAxisDistance = Math.max(0, maxAxisDistance);
        int safeManhattanDistance = Math.max(0, maxManhattanDistance);
        int deltaX = currentCell == null
                ? Integer.MAX_VALUE
                : Math.abs(this.pendingCell.getX() - currentCell.getX());
        int deltaZ = currentCell == null
                ? Integer.MAX_VALUE
                : Math.abs(this.pendingCell.getZ() - currentCell.getZ());
        if (currentCell == null
                || this.pendingCell.getY() != currentCell.getY()
                || deltaX > safeAxisDistance
                || deltaZ > safeAxisDistance
                || (long) deltaX + deltaZ > safeManhattanDistance) {
            this.clearPending();
        }
    }

    public void onPlacementSuccess(BlockPos placedBlockPos) {
        if (placedBlockPos != null && placedBlockPos.equals(this.pendingCell)) {
            this.clearPendingCell();
            this.bridgePromotionBlocked = false;
        }
        if (placedBlockPos != null
                && this.bridgeY != null
                && placedBlockPos.getY() == this.bridgeY) {
            this.recentPlacedCell = placedBlockPos.immutable();
            this.recentPlacedAge = 0;
        }
    }

    public BlockPos recentPlacementConnectorCell(BlockPos currentCell, int maxAge) {
        if (currentCell == null
                || this.recentPlacedCell == null
                || this.recentPlacedAge > Math.max(0, maxAge)
                || this.recentPlacedCell.getY() != currentCell.getY()) {
            return null;
        }
        int deltaX = currentCell.getX() - this.recentPlacedCell.getX();
        int deltaZ = currentCell.getZ() - this.recentPlacedCell.getZ();
        int absX = Math.abs(deltaX);
        int absZ = Math.abs(deltaZ);
        if (absX + absZ != 3 || Math.max(absX, absZ) != 2) {
            return null;
        }
        if (absX == 2) {
            return this.recentPlacedCell.offset(Integer.signum(deltaX), 0, 0);
        }
        return this.recentPlacedCell.offset(0, 0, Integer.signum(deltaZ));
    }

    public BlockPos pendingCell() {
        return this.pendingCell;
    }

    public int pendingAge() {
        return this.pendingAge;
    }

    public int bridgeTransitionAge() {
        return this.bridgeTransitionAge;
    }

    public Integer requestedBridgeY() {
        return this.requestedBridgeY;
    }

    public boolean hasDeferredBridgeTransition() {
        return this.requestedBridgeY != null && this.pendingCell != null;
    }

    public boolean hasBridgeTransitionRequest() {
        return this.requestedBridgeY != null;
    }

    public boolean bridgePromotionBlocked() {
        return this.bridgePromotionBlocked;
    }

    public BlockPos recentPlacedCell() {
        return this.recentPlacedCell;
    }

    public int recentPlacedAge() {
        return this.recentPlacedAge;
    }

    public int bridgeTopGraceTicks() {
        return this.bridgeTopGraceTicks;
    }

    public void clearPending() {
        boolean deferredBridgeTransition = this.requestedBridgeY != null;
        this.clearPendingCell();
        if (deferredBridgeTransition) {
            this.bridgeTransitionAge = 0;
            this.bridgePromotionBlocked = true;
        }
    }

    private void clearPendingCell() {
        this.pendingCell = null;
        this.pendingAge = 0;
    }

    private void clearBridgeTransitionRequest() {
        this.requestedBridgeY = null;
        this.bridgeTransitionAge = 0;
        this.bridgePromotionBlocked = false;
    }

    private void clearRecentPlacedCell() {
        this.recentPlacedCell = null;
        this.recentPlacedAge = 0;
    }

    public Integer bridgeY() {
        return this.bridgeY;
    }

    public void reset() {
        this.bridgeY = null;
        this.clearPendingCell();
        this.clearBridgeTransitionRequest();
        this.clearRecentPlacedCell();
        this.bridgeTopGraceTicks = 0;
        this.lastPhysicalJump = false;
    }

    public record TickState(Integer bridgeY, BlockPos currentCell, boolean physicalJumpStarted) {
    }

}
