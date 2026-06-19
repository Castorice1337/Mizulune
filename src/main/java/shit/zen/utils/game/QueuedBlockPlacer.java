package shit.zen.utils.game;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import shit.zen.ClientBase;
import shit.zen.modules.Module;
import shit.zen.utils.rotation.Rotation;
import shit.zen.utils.rotation.RotationApplyMode;
import shit.zen.utils.rotation.RotationHandler;
import shit.zen.utils.rotation.RotationProvider;
import shit.zen.utils.rotation.SmoothMode;

public class QueuedBlockPlacer extends ClientBase implements RotationProvider {
    private final Module owner;
    private final String name;
    private final SlotFinder slotFinder;
    private final Supplier<RotationApplyMode> applyModeSupplier;
    private final Supplier<SmoothMode> smoothModeSupplier;
    private final BooleanSupplier movementFixSupplier;
    private final IntSupplier resetTicksSupplier;
    private final DoubleSupplier resetThresholdSupplier;
    private final IntSupplier prioritySupplier;
    private final LinkedHashSet<BlockPos> queue = new LinkedHashSet<>();

    private BlockPlacementTarget currentTarget;
    private Rotation currentRotation;
    private int ticksToWait;
    private int lastPlaceTick = -1;
    private int targetPreparedTick = -1;
    private String lastDebugStatus = "idle";

    public QueuedBlockPlacer(
            Module owner,
            String name,
            SlotFinder slotFinder,
            Supplier<RotationApplyMode> applyModeSupplier,
            Supplier<SmoothMode> smoothModeSupplier,
            BooleanSupplier movementFixSupplier,
            IntSupplier resetTicksSupplier,
            DoubleSupplier resetThresholdSupplier,
            IntSupplier prioritySupplier) {
        this.owner = owner;
        this.name = name;
        this.slotFinder = slotFinder;
        this.applyModeSupplier = applyModeSupplier;
        this.smoothModeSupplier = smoothModeSupplier;
        this.movementFixSupplier = movementFixSupplier;
        this.resetTicksSupplier = resetTicksSupplier;
        this.resetThresholdSupplier = resetThresholdSupplier;
        this.prioritySupplier = prioritySupplier;
    }

    public void enable() {
        RotationHandler.registerProvider(this);
    }

    public void disable() {
        RotationHandler.unregisterProvider(this);
        this.clear();
    }

    public void updatePositions(Collection<BlockPos> positions) {
        this.queue.removeIf(pos -> positions == null || !positions.contains(pos));
        if (positions != null) {
            for (BlockPos pos : positions) {
                if (pos != null) {
                    this.queue.add(pos.immutable());
                }
            }
        }
    }

    public void prepare(BlockPlacementOptions options) {
        if (!this.isOwnerEnabled() || mc.player == null || mc.level == null || this.queue.isEmpty()) {
            this.updateDebugStatus("prepare:no-context owner=" + this.isOwnerEnabled()
                    + " player=" + (mc.player != null)
                    + " level=" + (mc.level != null)
                    + " queue=" + this.queue.size());
            this.currentTarget = null;
            this.currentRotation = null;
            this.targetPreparedTick = -1;
            return;
        }

        if (this.currentTarget != null && this.queue.contains(this.currentTarget.placedBlockPos())) {
            SlotSelection currentSlot = this.slotFinder.find(this.currentTarget.placedBlockPos());
            if (currentSlot != null && BlockPlacementUtil.isValidPlacementTarget(
                    this.currentTarget, currentSlot.itemStack(), options)
                    && this.canUseTarget(this.currentTarget, options)) {
                this.currentRotation = this.currentTarget.rotation();
                this.updateDebugStatus("prepare:locked " + this.formatTarget(this.currentTarget));
                return;
            }
        }
        this.currentTarget = null;
        this.currentRotation = null;
        this.targetPreparedTick = -1;

        SlotSelection defaultSlot = this.slotFinder.find(null);
        if (defaultSlot == null) {
            this.updateDebugStatus("prepare:no-slot queue=" + this.queue.size());
            return;
        }
        ItemStack defaultStack = defaultSlot.itemStack();
        int removed = 0;
        Iterator<BlockPos> iterator = this.queue.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            if (!BlockPlacementUtil.canReplace(pos, defaultStack)) {
                iterator.remove();
                removed++;
                continue;
            }

            SlotSelection slot = this.slotFinder.find(pos);
            if (slot == null) {
                continue;
            }
            BlockPlacementTarget target = this.findBestTarget(pos, slot.itemStack(), options);
            if (target != null) {
                this.currentTarget = target;
                this.currentRotation = target.rotation();
                this.targetPreparedTick = mc.player.tickCount;
                this.updateDebugStatus("prepare:target " + this.formatTarget(target));
                return;
            }
        }
        this.updateDebugStatus("prepare:no-target queue=" + this.queue.size() + " removed=" + removed);
    }

    public boolean place(BlockPlacementOptions options, int cooldownTicks) {
        if (!this.isOwnerEnabled()) {
            this.updateDebugStatus("place:no-owner");
            return false;
        }
        if (this.currentTarget == null) {
            this.updateDebugStatus("place:no-target queue=" + this.queue.size());
            return false;
        }
        if (mc.player == null) {
            this.updateDebugStatus("place:no-player");
            return false;
        }
        if (mc.player.tickCount == this.lastPlaceTick) {
            this.updateDebugStatus("place:same-tick lastPlaceTick=" + this.lastPlaceTick);
            return false;
        }
        if (this.ticksToWait > 0) {
            this.ticksToWait--;
            this.updateDebugStatus("place:cooldown wait=" + this.ticksToWait);
            return false;
        }

        SlotSelection slot = this.slotFinder.find(this.currentTarget.placedBlockPos());
        if (slot == null) {
            this.updateDebugStatus("place:no-slot " + this.formatTarget(this.currentTarget));
            return false;
        }

        Rotation placeRotation = this.getApplyMode() == RotationApplyMode.OFF
                ? this.currentRotation
                : RotationHandler.getSmoothedRotation(this);
        if (placeRotation == null) {
            placeRotation = this.currentRotation;
        }
        if (placeRotation == null) {
            this.updateDebugStatus("place:no-rotation " + this.formatTarget(this.currentTarget));
            return false;
        }
        if (this.getApplyMode() != RotationApplyMode.OFF && mc.player.tickCount <= this.targetPreparedTick) {
            this.updateDebugStatus("place:wait-target-tick current=" + mc.player.tickCount
                    + " prepared=" + this.targetPreparedTick
                    + " " + this.formatTarget(this.currentTarget));
            return false;
        }

        int previousSlot = -1;
        boolean switched = false;
        if (slot.hand() == InteractionHand.MAIN_HAND && slot.hotbarSlot() >= 0 && slot.hotbarSlot() <= 8) {
            previousSlot = mc.player.getInventory().selected;
            if (previousSlot != slot.hotbarSlot()) {
                mc.player.getInventory().selected = slot.hotbarSlot();
                PlayerUtil.sendCarriedItem();
                switched = true;
            }
        }

        BlockPlacementTarget placedTarget = this.currentTarget;
        BlockPlacementUtil.PlacementResult placement = BlockPlacementUtil.placeDetailed(
                placedTarget, slot.hand(), placeRotation, slot.itemStack(), options);
        boolean placed = placement.placed();
        this.updateDebugStatus((placed ? "place:success " : "place:fail reason=" + placement.reason() + " ")
                + this.formatTarget(placedTarget)
                + " hand=" + slot.hand()
                + " slot=" + slot.hotbarSlot()
                + " rot=" + this.formatRotation(placeRotation)
                + " " + this.formatHit(placement.hit()));
        if (placed) {
            this.lastPlaceTick = mc.player.tickCount;
            this.queue.remove(placedTarget.placedBlockPos());
            this.currentTarget = null;
            this.currentRotation = null;
            this.targetPreparedTick = -1;
            this.ticksToWait = Math.max(0, cooldownTicks);
        }

        if (switched && slot.restoreAfterPlace() && previousSlot >= 0 && previousSlot <= 8) {
            mc.player.getInventory().selected = previousSlot;
            PlayerUtil.sendCarriedItem();
        }
        return placed;
    }

    public void deferCurrentTarget(String reason) {
        this.updateDebugStatus("place:defer reason=" + reason + " " + this.formatTarget(this.currentTarget));
    }

    public void clearCurrentTarget(String reason) {
        this.updateDebugStatus("prepare:refresh reason=" + reason + " " + this.formatTarget(this.currentTarget));
        this.currentTarget = null;
        this.currentRotation = null;
        this.targetPreparedTick = -1;
    }

    public void clear() {
        this.queue.clear();
        this.currentTarget = null;
        this.currentRotation = null;
        this.ticksToWait = 0;
        this.lastPlaceTick = -1;
        this.targetPreparedTick = -1;
        this.lastDebugStatus = "idle";
    }

    public boolean isDone() {
        return this.queue.isEmpty() && this.currentTarget == null;
    }

    public int size() {
        return this.queue.size();
    }

    public BlockPlacementTarget getCurrentTarget() {
        return this.currentTarget;
    }

    public String getDebugSummary() {
        return this.lastDebugStatus
                + " queue=" + this.queue.size()
                + " wait=" + this.ticksToWait
                + " preparedTick=" + this.targetPreparedTick
                + " lastPlaceTick=" + this.lastPlaceTick;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Rotation getRotation() {
        return this.currentRotation;
    }

    @Override
    public boolean isRotationActive() {
        return this.isOwnerEnabled()
                && this.getApplyMode() != RotationApplyMode.OFF
                && this.currentRotation != null;
    }

    @Override
    public RotationApplyMode getApplyMode() {
        RotationApplyMode mode = this.applyModeSupplier == null ? null : this.applyModeSupplier.get();
        return mode == null ? RotationApplyMode.SILENT : mode;
    }

    @Override
    public SmoothMode getSmoothMode() {
        SmoothMode mode = this.smoothModeSupplier == null ? null : this.smoothModeSupplier.get();
        return mode == null ? SmoothMode.SNAP : mode;
    }

    @Override
    public boolean shouldFixMovement() {
        return this.movementFixSupplier != null && this.movementFixSupplier.getAsBoolean();
    }

    @Override
    public int getTicksUntilReset() {
        return this.resetTicksSupplier == null ? RotationProvider.super.getTicksUntilReset() : Math.max(0, this.resetTicksSupplier.getAsInt());
    }

    @Override
    public double getResetThreshold() {
        return this.resetThresholdSupplier == null ? RotationProvider.super.getResetThreshold() : Math.max(0.0, this.resetThresholdSupplier.getAsDouble());
    }

    @Override
    public boolean shouldAffectRayTrace() {
        return false;
    }

    @Override
    public boolean shouldAffectUseItemRayTrace() {
        return false;
    }

    @Override
    public int getRotationPriority() {
        return this.prioritySupplier == null ? 0 : this.prioritySupplier.getAsInt();
    }

    private boolean isOwnerEnabled() {
        return this.owner != null && this.owner.isEnabled();
    }

    private BlockPlacementTarget findBestTarget(BlockPos pos, ItemStack stack, BlockPlacementOptions options) {
        if (this.requiresStrictReachability(options)) {
            return BlockPlacementUtil.findBestReachablePlacementTarget(pos, stack, options);
        }
        return BlockPlacementUtil.findBestPlacementTarget(pos, stack, options);
    }

    private boolean canUseTarget(BlockPlacementTarget target, BlockPlacementOptions options) {
        return !this.requiresStrictReachability(options)
                || BlockPlacementUtil.isRayTraceReachable(target, options);
    }

    private boolean requiresStrictReachability(BlockPlacementOptions options) {
        return options != null && !options.constructFailResult();
    }

    private void updateDebugStatus(String status) {
        this.lastDebugStatus = status == null ? "unknown" : status;
    }

    private String formatTarget(BlockPlacementTarget target) {
        if (target == null) {
            return "target=null";
        }
        return "target=" + this.formatBlockPos(target.placedBlockPos())
                + " support=" + this.formatBlockPos(target.interactedBlockPos())
                + " face=" + target.facing()
                + " targetRot=" + this.formatRotation(target.rotation());
    }

    private String formatHit(BlockHitResult hit) {
        if (hit == null) {
            return "hit=null";
        }
        return "hit=" + this.formatBlockPos(hit.getBlockPos()) + "/" + hit.getDirection();
    }

    private String formatBlockPos(BlockPos pos) {
        if (pos == null) {
            return "null";
        }
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private String formatRotation(Rotation rotation) {
        if (rotation == null) {
            return "null";
        }
        return String.format(Locale.US, "%.1f/%.1f", rotation.getYaw(), rotation.getPitch());
    }

    @FunctionalInterface
    public interface SlotFinder {
        SlotSelection find(BlockPos pos);
    }

    public record SlotSelection(int hotbarSlot, InteractionHand hand, boolean restoreAfterPlace) {
        public static SlotSelection hotbar(int slot, boolean restoreAfterPlace) {
            return new SlotSelection(slot, InteractionHand.MAIN_HAND, restoreAfterPlace);
        }

        public static SlotSelection offhand() {
            return new SlotSelection(-1, InteractionHand.OFF_HAND, false);
        }

        public ItemStack itemStack() {
            if (mc.player == null) {
                return ItemStack.EMPTY;
            }
            if (this.hand == InteractionHand.OFF_HAND) {
                return mc.player.getOffhandItem();
            }
            if (this.hotbarSlot >= 0 && this.hotbarSlot <= 8) {
                return mc.player.getInventory().getItem(this.hotbarSlot);
            }
            return mc.player.getMainHandItem();
        }
    }
}
