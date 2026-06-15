package shit.zen.utils.game;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
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
    private final IntSupplier prioritySupplier;
    private final LinkedHashSet<BlockPos> queue = new LinkedHashSet<>();

    private BlockPlacementTarget currentTarget;
    private Rotation currentRotation;
    private int ticksToWait;
    private int lastPlaceTick = -1;

    public QueuedBlockPlacer(
            Module owner,
            String name,
            SlotFinder slotFinder,
            Supplier<RotationApplyMode> applyModeSupplier,
            Supplier<SmoothMode> smoothModeSupplier,
            BooleanSupplier movementFixSupplier,
            IntSupplier prioritySupplier) {
        this.owner = owner;
        this.name = name;
        this.slotFinder = slotFinder;
        this.applyModeSupplier = applyModeSupplier;
        this.smoothModeSupplier = smoothModeSupplier;
        this.movementFixSupplier = movementFixSupplier;
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
        this.currentTarget = null;
        this.currentRotation = null;
        if (!this.isOwnerEnabled() || mc.player == null || mc.level == null || this.queue.isEmpty()) {
            return;
        }

        SlotSelection defaultSlot = this.slotFinder.find(null);
        if (defaultSlot == null) {
            return;
        }
        ItemStack defaultStack = defaultSlot.itemStack();
        Iterator<BlockPos> iterator = this.queue.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            if (!BlockPlacementUtil.canReplace(pos, defaultStack)) {
                iterator.remove();
                continue;
            }

            SlotSelection slot = this.slotFinder.find(pos);
            if (slot == null) {
                continue;
            }
            BlockPlacementTarget target = BlockPlacementUtil.findBestPlacementTarget(pos, slot.itemStack(), options);
            if (target != null) {
                this.currentTarget = target;
                this.currentRotation = target.rotation();
                return;
            }
        }
    }

    public boolean place(BlockPlacementOptions options, int cooldownTicks) {
        if (!this.isOwnerEnabled() || this.currentTarget == null || mc.player == null || mc.player.tickCount == this.lastPlaceTick) {
            return false;
        }
        if (this.ticksToWait > 0) {
            this.ticksToWait--;
            return false;
        }

        SlotSelection slot = this.slotFinder.find(this.currentTarget.placedBlockPos());
        if (slot == null) {
            return false;
        }

        Rotation placeRotation = this.getApplyMode() == RotationApplyMode.OFF
                ? this.currentRotation
                : RotationHandler.getSmoothedRotation(this);
        if (placeRotation == null) {
            placeRotation = this.currentRotation;
        }
        if (placeRotation == null) {
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

        boolean placed = BlockPlacementUtil.place(this.currentTarget, slot.hand(), placeRotation, slot.itemStack(), options);
        this.lastPlaceTick = mc.player.tickCount;
        if (placed) {
            this.queue.remove(this.currentTarget.placedBlockPos());
            this.currentTarget = null;
            this.currentRotation = null;
            this.ticksToWait = Math.max(0, cooldownTicks);
        }

        if (switched && slot.restoreAfterPlace() && previousSlot >= 0 && previousSlot <= 8) {
            mc.player.getInventory().selected = previousSlot;
            PlayerUtil.sendCarriedItem();
        }
        return placed;
    }

    public void clear() {
        this.queue.clear();
        this.currentTarget = null;
        this.currentRotation = null;
        this.ticksToWait = 0;
        this.lastPlaceTick = -1;
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
    public int getRotationPriority() {
        return this.prioritySupplier == null ? 0 : this.prioritySupplier.getAsInt();
    }

    private boolean isOwnerEnabled() {
        return this.owner != null && this.owner.isEnabled();
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
