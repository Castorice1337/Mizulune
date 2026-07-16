package shit.zen.modules.impl.player;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import shit.zen.ClientBase;
import shit.zen.utils.game.BlockPlacementOptions;
import shit.zen.utils.game.BlockPlacementTarget;
import shit.zen.utils.game.BlockPlacementUtil;
import shit.zen.utils.game.MotionSimulator;
import shit.zen.utils.game.PlayerPositionHold;
import shit.zen.utils.game.PlayerTickDelay;
import shit.zen.utils.rotation.Rotation;

final class ClutchAirStuckController extends ClientBase implements PlayerPositionHold.DebugSink {
    static final int HOLD_TICKS = 2;
    static final int WINDOW_TICKS = 8;

    private final Consumer<String> debug;

    private boolean active;
    private int windowTicks;

    ClutchAirStuckController(Consumer<String> debug) {
        this.debug = debug == null ? ignored -> { } : debug;
    }

    void update(UpdateInput input) {
        if (mc.player == null || mc.level == null || input == null) {
            this.end("missing-context");
            return;
        }
        if (!input.enabled() || !input.clutchActive()) {
            this.end("inactive");
            return;
        }
        if (mc.player.onGround() || mc.player.getDeltaMovement().y > 0.01) {
            this.end(mc.player.onGround() ? "landed" : "rising");
            return;
        }
        if (this.hasSupportAt(mc.player.position())) {
            this.end("safe-underfoot");
            return;
        }
        if (!input.canPlaceMoreBlocks()) {
            this.end("max-blocks");
            return;
        }
        if (!this.isConcreteTarget(input.target(), input.stack(), input.options())) {
            this.end("no-target");
            return;
        }
        if (PlayerPositionHold.hasExternalHold(this) || PlayerTickDelay.hasExternalTasks(this)) {
            this.end("conflict");
            return;
        }

        boolean sentHit = this.canRayTrace(input.appliedRotation(), input.target(), input.options());
        boolean placementRisk = !input.placementReady()
                || !sentHit
                || this.isFallingPastSupportSoon(input.target())
                || this.isLeavingReachSoon(input.target(), input.options());

        if (!this.active) {
            if (mc.player.getDeltaMovement().y >= -0.05 || this.hasSupportNextTick() || !placementRisk) {
                return;
            }
            this.active = true;
            this.windowTicks = 1;
            this.captureHold();
            this.debug.accept("air-stuck:activate window=1/" + WINDOW_TICKS
                    + " ready=" + input.placementReady()
                    + " sentHit=" + sentHit);
            return;
        }

        if (this.windowTicks >= WINDOW_TICKS) {
            this.end("timeout");
            return;
        }
        this.windowTicks++;
        this.extendHold();
        this.debug.accept("air-stuck:window=" + this.windowTicks + "/" + WINDOW_TICKS
                + " ready=" + input.placementReady()
                + " sentHit=" + sentHit);
    }

    void onPlacementSuccess(BlockPlacementTarget target) {
        if (!this.active) {
            return;
        }
        if (this.hasSupportAt(mc.player == null ? null : mc.player.position())) {
            this.end("safe-underfoot");
            return;
        }
        this.debug.accept("air-stuck:chain-continue target=" + this.formatTarget(target));
    }

    void onPlacementFailure(String reason) {
        if (this.active) {
            this.debug.accept("air-stuck:place-fail reason=" + (reason == null ? "unknown" : reason));
        }
    }

    void reset() {
        this.end("reset");
    }

    void reset(String reason) {
        this.end(reason == null || reason.isBlank() ? "reset" : reason);
    }

    boolean isActive() {
        return this.active;
    }

    int windowTicks() {
        return this.windowTicks;
    }

    int remainingHoldTicks() {
        return PlayerPositionHold.remainingTicks(this);
    }

    @Override
    public void onPositionHoldDebug(String phase) {
        this.debug.accept("air-stuck:" + phase + " hold=" + this.remainingHoldTicks());
    }

    private void captureHold() {
        PlayerPositionHold.hold(this, HOLD_TICKS);
        this.debug.accept("air-stuck:anchor-capture hold=" + this.remainingHoldTicks());
    }

    private void extendHold() {
        PlayerPositionHold.extend(this, HOLD_TICKS);
        this.debug.accept("air-stuck:anchor-stable hold=" + this.remainingHoldTicks());
    }

    private void end(String reason) {
        boolean hadState = this.active || PlayerPositionHold.isOwnedActive(this);
        this.active = false;
        this.windowTicks = 0;
        PlayerPositionHold.release(this);
        PlayerTickDelay.release(this);
        if (hadState) {
            this.debug.accept("air-stuck:" + reason);
            this.debug.accept("air-stuck:end reason=" + reason);
        }
    }

    private boolean isConcreteTarget(
            BlockPlacementTarget target,
            ItemStack stack,
            BlockPlacementOptions options) {
        if (target == null || stack == null || stack.isEmpty() || options == null
                || target.rotation() == null || mc.player == null) {
            return false;
        }
        if (mc.player.getEyePosition().distanceToSqr(target.targetPoint())
                > options.maxRange() * options.maxRange()) {
            return false;
        }
        return BlockPlacementUtil.isValidPlacementTarget(target, stack, options)
                && this.canRayTrace(target.rotation(), target, options);
    }

    private boolean canRayTrace(
            Rotation rotation,
            BlockPlacementTarget target,
            BlockPlacementOptions options) {
        return rotation != null
                && target != null
                && options != null
                && BlockPlacementUtil.rayTraceTarget(rotation, target, options, false) != null;
    }

    private boolean hasSupportNextTick() {
        MotionSimulator simulator = new MotionSimulator(mc.player);
        simulator.simulateWithFriction(1);
        return this.hasSupportAt(new Vec3(simulator.x, simulator.y, simulator.z));
    }

    private boolean isFallingPastSupportSoon(BlockPlacementTarget target) {
        if (target == null || mc.player == null) {
            return false;
        }
        MotionSimulator simulator = new MotionSimulator(mc.player);
        simulator.simulateWithFriction(2);
        return target.interactedBlockPos().getY() > simulator.y;
    }

    private boolean isLeavingReachSoon(BlockPlacementTarget target, BlockPlacementOptions options) {
        if (target == null || options == null || mc.player == null) {
            return false;
        }
        MotionSimulator simulator = new MotionSimulator(mc.player);
        simulator.simulateWithFriction(1);
        Vec3 nextEye = new Vec3(simulator.x, simulator.y + mc.player.getEyeHeight(), simulator.z);
        return nextEye.distanceToSqr(target.targetPoint()) > options.maxRange() * options.maxRange();
    }

    private boolean hasSupportAt(Vec3 position) {
        if (mc.player == null || mc.level == null || position == null) {
            return false;
        }
        AABB current = mc.player.getBoundingBox();
        AABB box = current.move(
                position.x - mc.player.getX(),
                position.y - mc.player.getY(),
                position.z - mc.player.getZ());
        int minX = Mth.floor(box.minX + 1.0E-4);
        int maxX = Mth.floor(box.maxX - 1.0E-4);
        int minZ = Mth.floor(box.minZ + 1.0E-4);
        int maxZ = Mth.floor(box.maxZ - 1.0E-4);
        int supportY = Mth.floor(position.y) - 1;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos support = new BlockPos(x, supportY, z);
                if (mc.level.getBlockState(support).entityCanStandOn(mc.level, support, mc.player)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String formatTarget(BlockPlacementTarget target) {
        if (target == null) {
            return "null";
        }
        return target.placedBlockPos() + " support=" + target.interactedBlockPos()
                + " face=" + target.facing();
    }

    record UpdateInput(
            boolean enabled,
            boolean clutchActive,
            BlockPlacementTarget target,
            Rotation appliedRotation,
            ItemStack stack,
            BlockPlacementOptions options,
            boolean placementReady,
            boolean canPlaceMoreBlocks) {
    }
}
