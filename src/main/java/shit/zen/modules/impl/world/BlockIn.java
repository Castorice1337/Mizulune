/*
 * This file includes logic adapted from LiquidBounce Nextgen:
 * net.ccbluex.liquidbounce.features.module.modules.world.ModuleBlockIn
 *
 * LiquidBounce is licensed under GPL-3.0-or-later.
 * Modified for Mizulune/OpenZen to use the shared placement base without
 * replacing Mizulune's existing rotation manager.
 */
package shit.zen.modules.impl.world;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import shit.zen.event.EventPriority;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.movement.GodBridgeAssist;
import shit.zen.modules.impl.movement.Scaffold;
import shit.zen.modules.impl.player.AutoWebPlace;
import shit.zen.utils.game.BlockPlacementOptions;
import shit.zen.utils.game.BlockPlacementTarget;
import shit.zen.utils.game.BlockPlacementUtil;
import shit.zen.utils.game.BlockUtil;
import shit.zen.utils.game.QueuedBlockPlacer;
import shit.zen.utils.misc.ChatUtil;
import shit.zen.utils.rotation.RotationApplyMode;
import shit.zen.utils.rotation.SmoothMode;
import shit.zen.value.ValueGroup;
import shit.zen.value.impl.BooleanValue;
import shit.zen.value.impl.ModeValue;
import shit.zen.value.impl.NumberValue;

public class BlockIn extends Module {
    public static BlockIn INSTANCE;
    private static final double JUMP_SUPPORT_MIN_PEAK_Y_OFFSET = 0.85;
    private static final double JUMP_SUPPORT_MAX_UPWARD_SPEED = 0.12;
    private static final double JUMP_SUPPORT_MAX_DOWNWARD_SPEED = -0.08;

    public final BooleanValue autoDisable = new BooleanValue("Auto Disable", true);
    public final ModeValue placeOrder = new ModeValue("Place Order", "Normal", "Random", "BottomTop", "TopBottom")
            .withDefault("Normal");
    public final BooleanValue autoBlock = new BooleanValue("Auto Block", true);
    public final BooleanValue switchBack = new BooleanValue("Switch Back", true, () -> this.autoBlock.getValue());
    public final BooleanValue useOffhand = new BooleanValue("Use Offhand", true);
    public final NumberValue range = new NumberValue("Range", 4.5, 1.0, 6.0, 0.1);
    public final NumberValue wallRange = new NumberValue("Wall Range", 4.5, 0.0, 6.0, 0.1);
    public final NumberValue cooldown = new NumberValue("Cooldown", 1, 0, 20, 1);
    public final BooleanValue constructFailResult = new BooleanValue("Construct Fail Result", true);
    public final BooleanValue considerBackFaces = new BooleanValue("Consider Back Faces", true);
    public final ModeValue rotationMode = new ModeValue("Rotation Mode", "Silent", "ChangeLook", "Off")
            .withDefault("Silent");
    public final ModeValue smoothMode = new ModeValue("Smooth Mode", "SNAP", "LINEAR", "SIGMOID").withDefault("SNAP");
    public final BooleanValue movementFix = new BooleanValue("Movement Fix", false, () -> !this.rotationMode.is("Off"));
    public final BooleanValue roofSupport = new BooleanValue("Roof Support", true);
    public final BooleanValue diagonalRoofSupport = new BooleanValue("Diagonal Roof Support", true,
            this.roofSupport::getValue);
    public final BooleanValue pillarRoofSupport = new BooleanValue("Pillar Roof Support", true,
            this.roofSupport::getValue);
    public final BooleanValue useJump = new BooleanValue("Use Jump", false,
            () -> this.roofSupport.getValue() && this.pillarRoofSupport.getValue());
    public final NumberValue jumpTicks = new NumberValue("Jump Ticks", 3, 1, 8, 1, this.useJump::getValue);
    public final BooleanValue debug = new BooleanValue("Debug", false);
    public final NumberValue debugInterval = new NumberValue("Debug Interval", 5, 1, 40, 1, this.debug::getValue);

    private final QueuedBlockPlacer placer;
    private BlockPos startPos;
    private boolean rotateClockwise;
    private RoofPhase roofPhase = RoofPhase.NORMAL;
    private RoofSupportMode roofSupportMode = RoofSupportMode.NONE;
    private BlockPos roofPos;
    private BlockPos roofSupportPos;
    private int jumpTicksRemaining;
    private int jumpAirTicks;
    private int jumpWindowTicks;
    private boolean moduleJumpDown;
    private boolean waitingLanding;
    private String roofDebugReason = "idle";
    private int debugTicks;
    private String lastDebugState;

    public BlockIn() {
        super("BlockIn", Category.EXPLOIT);
        INSTANCE = this;
        this.placer = new QueuedBlockPlacer(
                this,
                "BlockInPlacer",
                this::findSlot,
                this::getApplyMode,
                this::getSmoothMode,
                this.movementFix::getValue,
                () -> 45);
    }

    @Override
    protected void configureValueTree(ValueGroup root) {
        ValueGroup blockIn = root.group("block_in", "BlockIn");
        blockIn.add(this.autoDisable);
        blockIn.add(this.placeOrder);

        ValueGroup placerGroup = root.group("placer", "Placer");
        placerGroup.add(this.autoBlock);
        placerGroup.add(this.switchBack);
        placerGroup.add(this.useOffhand);
        placerGroup.add(this.range);
        placerGroup.add(this.wallRange);
        placerGroup.add(this.cooldown);
        placerGroup.add(this.constructFailResult);
        placerGroup.add(this.considerBackFaces);

        ValueGroup rotation = root.group("rotation", "Rotation");
        rotation.add(this.rotationMode);
        rotation.add(this.smoothMode);
        rotation.add(this.movementFix);

        ValueGroup roof = root.group("roof_support", "Roof Support");
        roof.add(this.roofSupport);
        roof.add(this.diagonalRoofSupport);
        roof.add(this.pillarRoofSupport);
        roof.add(this.useJump);
        roof.add(this.jumpTicks);

        ValueGroup debug = root.group("debug", "Debug");
        debug.add(this.debug);
        debug.add(this.debugInterval);
    }

    @Override
    protected void onEnable() {
        if (Scaffold.INSTANCE != null && Scaffold.INSTANCE.isEnabled()) {
            Scaffold.INSTANCE.setEnabled(false);
        }
        if (GodBridgeAssist.INSTANCE != null && GodBridgeAssist.INSTANCE.isEnabled()) {
            GodBridgeAssist.INSTANCE.setEnabled(false);
        }
        if (AutoWebPlace.INSTANCE != null && AutoWebPlace.INSTANCE.isEnabled()) {
            AutoWebPlace.INSTANCE.setEnabled(false);
        }
        if (mc.player != null) {
            this.startPos = mc.player.blockPosition();
            this.rotateClockwise = ThreadLocalRandom.current().nextBoolean();
            this.resetRoofSupportState();
            this.placer.updatePositions(this.generatePositions());
        }
        this.debugTicks = 0;
        this.lastDebugState = null;
        this.placer.enable();
        this.debugLog("enable");
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        this.debugLog("disable");
        this.restoreControlledKeys();
        this.resetRoofSupportState();
        this.placer.disable();
        this.startPos = null;
        super.onDisable();
    }

    @EventTarget(value = 1)
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null || this.startPos == null) {
            return;
        }
        BlockPos currentPos = mc.player.blockPosition();
        if (!currentPos.equals(this.startPos) && !currentPos.equals(this.startPos.above())) {
            this.debugLog("auto-disable moved current=" + this.formatBlockPos(currentPos));
            this.setEnabled(false);
            return;
        }

        List<BlockPos> positions = this.generatePositions();
        this.updateJumpControl();
        this.placer.updatePositions(positions);
        this.refreshJumpSupportTargetBeforePrepare();
        this.placer.prepare(this.getPreparePlacementOptions());
        this.debugLog("prepare");
    }

    @EventTarget(value = EventPriority.LOWEST)
    public void onTickLate(TickEvent event) {
        if (mc.player == null || mc.level == null || this.startPos == null) {
            return;
        }
        if (this.shouldDeferJumpSupportPlacement()) {
            this.placer.deferCurrentTarget("jump-wait-airborne");
            this.roofDebugReason = "jump-wait-airborne";
            this.debugLog("place deferred");
            return;
        }

        boolean placed = this.placer.place(this.getCurrentPlacementOptions(), this.cooldown.getValue().intValue());
        this.handlePostPlaceRoofState();
        this.debugLog("place placed=" + placed);
        if (this.autoDisable.getValue() && this.placer.isDone() && this.isBlockInComplete()) {
            this.setEnabled(false);
        }
    }

    private List<BlockPos> generatePositions() {
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
        if (mc.player == null || this.startPos == null) {
            return List.of();
        }

        int playerHeight = Mth.ceil(mc.player.getBbHeight());
        this.roofPos = this.startPos.above(playerHeight);
        if (this.waitingLanding && !mc.player.onGround()) {
            this.roofDebugReason = "waiting-landing";
            return List.of();
        }

        result.add(this.startPos.below());

        for (Direction direction : this.getOrderedHorizontalDirections()) {
            BlockPos value = this.startPos.relative(direction);
            for (int i = 0; i < playerHeight; i++) {
                result.add(value.above(i));
            }
        }

        result.add(this.roofPos);
        List<BlockPos> ordered = this.orderPositions(new ArrayList<>(result));
        BlockPos supportPos = this.updateRoofSupportPlan(playerHeight);
        return this.insertRoofSupportBeforeRoof(ordered, supportPos);
    }

    private List<BlockPos> orderPositions(List<BlockPos> positions) {
        if (this.placeOrder.is("Random")) {
            Collections.shuffle(positions);
        } else if (this.placeOrder.is("BottomTop")) {
            positions.sort((first, second) -> Integer.compare(first.getY(), second.getY()));
        } else if (this.placeOrder.is("TopBottom")) {
            positions.sort((first, second) -> Integer.compare(second.getY(), first.getY()));
        }
        return positions;
    }

    private BlockPlacementOptions getPlacementOptions() {
        return BlockPlacementOptions.defaults()
                .withRange(this.range.getValue().doubleValue())
                .withWallRange(this.wallRange.getValue().doubleValue())
                .withConstructFailResult(this.constructFailResult.getValue())
                .withConsiderFacingAwayFaces(this.considerBackFaces.getValue());
    }

    private BlockPlacementOptions getRoofSupportPlacementOptions() {
        return this.getPlacementOptions()
                .withConstructFailResult(false)
                .withConsiderFacingAwayFaces(false);
    }

    private BlockPlacementOptions getPreparePlacementOptions() {
        return this.roofSupportPos == null ? this.getPlacementOptions() : this.getRoofSupportPlacementOptions();
    }

    private BlockPlacementOptions getCurrentPlacementOptions() {
        BlockPlacementTarget target = this.placer.getCurrentTarget();
        if (target != null && this.roofSupportPos != null
                && this.roofSupportPos.equals(target.placedBlockPos())) {
            return this.getRoofSupportPlacementOptions();
        }
        return this.getPlacementOptions();
    }

    private RotationApplyMode getApplyMode() {
        if (this.rotationMode.is("ChangeLook")) {
            return RotationApplyMode.CHANGE_LOOK;
        }
        if (this.rotationMode.is("Off")) {
            return RotationApplyMode.OFF;
        }
        return RotationApplyMode.SILENT;
    }

    private SmoothMode getSmoothMode() {
        if (this.smoothMode.is("LINEAR")) {
            return SmoothMode.LINEAR;
        }
        if (this.smoothMode.is("SIGMOID")) {
            return SmoothMode.SIGMOID;
        }
        return SmoothMode.SNAP;
    }

    private QueuedBlockPlacer.SlotSelection findSlot(BlockPos pos) {
        if (mc.player == null) {
            return null;
        }

        int selected = mc.player.getInventory().selected;
        if (this.isHotbarPlaceableBlock(selected)) {
            return QueuedBlockPlacer.SlotSelection.hotbar(selected, false);
        }

        if (this.autoBlock.getValue()) {
            int slot = this.findBestHotbarBlockSlot();
            if (slot != -1) {
                return QueuedBlockPlacer.SlotSelection.hotbar(slot, this.switchBack.getValue());
            }
        }

        if (this.useOffhand.getValue()) {
            ItemStack offhand = mc.player.getOffhandItem();
            if (offhand.getItem() instanceof BlockItem && BlockUtil.isPlaceable(offhand)) {
                return QueuedBlockPlacer.SlotSelection.offhand();
            }
        }
        return null;
    }

    private int findBestHotbarBlockSlot() {
        if (mc.player == null) {
            return -1;
        }
        int bestSlot = -1;
        int bestCount = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack) && stack.getCount() > bestCount) {
                bestSlot = i;
                bestCount = stack.getCount();
            }
        }
        return bestSlot;
    }

    private boolean isHotbarPlaceableBlock(int slot) {
        if (mc.player == null || slot < 0 || slot > 8) {
            return false;
        }
        ItemStack stack = mc.player.getInventory().getItem(slot);
        return stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack);
    }

    private BlockPos updateRoofSupportPlan(int playerHeight) {
        if (mc.player == null || mc.level == null || this.startPos == null || this.roofPos == null) {
            this.resetRoofSupportState();
            return null;
        }
        if (!this.roofSupport.getValue()) {
            this.clearRoofSupportPlan("disabled");
            return null;
        }

        QueuedBlockPlacer.SlotSelection slot = this.findSlot(this.roofPos);
        if (slot == null) {
            this.clearRoofSupportPlan("no-slot");
            return null;
        }
        ItemStack stack = slot.itemStack();
        BlockPlacementOptions options = this.getPlacementOptions();
        BlockPlacementOptions supportOptions = this.getRoofSupportPlacementOptions();
        if (!BlockPlacementUtil.canReplace(this.roofPos, stack)) {
            this.clearRoofSupportPlan("roof-complete");
            return null;
        }
        if (this.hasPlacementTarget(this.roofPos, stack, options)) {
            this.clearRoofSupportPlan("roof-direct");
            return null;
        }
        if (!this.isBaseStructureComplete(playerHeight, stack)) {
            this.clearRoofSupportPlan("base-pending");
            return null;
        }
        if (this.roofSupportPos != null && !BlockPlacementUtil.canReplace(this.roofSupportPos, stack)) {
            this.clearRoofSupportPlan("support-placed");
            return null;
        }
        if (this.roofSupportPos != null && this.canKeepRoofSupportPlan(stack, supportOptions)) {
            this.roofDebugReason = "keep-support";
            return this.roofSupportPos;
        }

        BlockPos diagonalSupport = this.diagonalRoofSupport.getValue()
                ? this.findDiagonalRoofSupport(playerHeight, stack, supportOptions)
                : null;
        if (diagonalSupport != null) {
            this.setRoofSupportPlan(RoofPhase.DIAGONAL_SUPPORT, RoofSupportMode.DIAGONAL,
                    diagonalSupport, "diagonal");
            return diagonalSupport;
        }

        BlockPos jumpSupport = this.pillarRoofSupport.getValue() && this.useJump.getValue()
                ? this.findJumpPillarRoofSupport(playerHeight, stack)
                : null;
        if (jumpSupport != null) {
            this.setRoofSupportPlan(RoofPhase.JUMP_PILLAR_SUPPORT, RoofSupportMode.JUMP_PILLAR,
                    jumpSupport, "jump-pillar");
            return jumpSupport;
        }

        BlockPos pillarSupport = this.pillarRoofSupport.getValue()
                ? this.findPillarRoofSupport(playerHeight, stack, supportOptions)
                : null;
        if (pillarSupport != null) {
            this.setRoofSupportPlan(RoofPhase.PILLAR_SUPPORT, RoofSupportMode.PILLAR,
                    pillarSupport, "pillar");
            return pillarSupport;
        }

        this.clearRoofSupportPlan("no-support");
        return null;
    }

    private boolean canKeepRoofSupportPlan(ItemStack stack, BlockPlacementOptions options) {
        if (this.roofSupportPos == null || !BlockPlacementUtil.canReplace(this.roofSupportPos, stack)) {
            return false;
        }
        if (this.roofSupportMode == RoofSupportMode.JUMP_PILLAR) {
            return this.isPillarRaiseCandidate(this.roofSupportPos, stack);
        }
        return this.hasPlacementTarget(this.roofSupportPos, stack, options);
    }

    private BlockPos findDiagonalRoofSupport(int playerHeight, ItemStack stack, BlockPlacementOptions options) {
        int[] offsets = new int[]{-1, 1};
        for (int dx : offsets) {
            for (int dz : offsets) {
                BlockPos diagonal = this.startPos.offset(dx, playerHeight, dz);
                if (!BlockPlacementUtil.isValidSupport(diagonal)) {
                    continue;
                }
                BlockPos xSupport = this.startPos.offset(dx, playerHeight, 0);
                if (this.isPlaceableSupportCandidate(xSupport, stack, options)) {
                    return xSupport;
                }
                BlockPos zSupport = this.startPos.offset(0, playerHeight, dz);
                if (this.isPlaceableSupportCandidate(zSupport, stack, options)) {
                    return zSupport;
                }
            }
        }
        return null;
    }

    private BlockPos findPillarRoofSupport(int playerHeight, ItemStack stack, BlockPlacementOptions options) {
        for (Direction direction : this.getOrderedHorizontalDirections()) {
            BlockPos support = this.startPos.relative(direction).above(playerHeight);
            if (this.isPillarRaiseCandidate(support, stack)
                    && this.hasSafeGroundPillarTarget(support, stack, options)) {
                return support;
            }
        }
        return null;
    }

    private BlockPos findJumpPillarRoofSupport(int playerHeight, ItemStack stack) {
        for (Direction direction : this.getOrderedHorizontalDirections()) {
            BlockPos support = this.startPos.relative(direction).above(playerHeight);
            if (this.isPillarRaiseCandidate(support, stack)) {
                return support;
            }
        }
        return null;
    }

    private boolean isPlaceableSupportCandidate(BlockPos pos, ItemStack stack, BlockPlacementOptions options) {
        return pos != null
                && BlockPlacementUtil.canReplace(pos, stack)
                && this.hasSafeNonJumpSupportTarget(pos, stack, options);
    }

    private boolean isPillarRaiseCandidate(BlockPos pos, ItemStack stack) {
        return pos != null
                && BlockPlacementUtil.canReplace(pos, stack)
                && BlockPlacementUtil.isValidSupport(pos.below());
    }

    private boolean hasPlacementTarget(BlockPos pos, ItemStack stack, BlockPlacementOptions options) {
        return pos != null
                && stack != null
                && !stack.isEmpty()
                && BlockPlacementUtil.findBestPlacementTarget(pos, stack, options) != null;
    }

    private boolean hasSafeGroundPillarTarget(BlockPos pos, ItemStack stack, BlockPlacementOptions options) {
        return this.hasSafeNonJumpSupportTarget(pos, stack, options);
    }

    private boolean hasSafeNonJumpSupportTarget(BlockPos pos, ItemStack stack, BlockPlacementOptions options) {
        if (pos == null || stack == null || stack.isEmpty()) {
            return false;
        }
        BlockPlacementTarget target = BlockPlacementUtil.findBestPlacementTarget(pos, stack, options);
        return target != null && target.facing() != Direction.UP;
    }

    private List<BlockPos> insertRoofSupportBeforeRoof(List<BlockPos> positions, BlockPos supportPos) {
        if (supportPos == null || this.roofPos == null) {
            return positions;
        }
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
        boolean inserted = false;
        for (BlockPos pos : positions) {
            if (pos.equals(supportPos)) {
                continue;
            }
            if (!inserted && pos.equals(this.roofPos)) {
                result.add(supportPos.immutable());
                inserted = true;
            }
            result.add(pos);
        }
        if (!inserted) {
            result.add(supportPos.immutable());
        }
        return new ArrayList<>(result);
    }

    private Direction[] getOrderedHorizontalDirections() {
        Direction[] result = new Direction[4];
        Direction direction = mc.player == null ? Direction.NORTH : mc.player.getDirection();
        for (int i = 0; i < result.length; i++) {
            result[i] = direction;
            direction = this.rotateClockwise ? direction.getClockWise() : direction.getCounterClockWise();
        }
        return result;
    }

    private void updateJumpControl() {
        if (mc.player == null || mc.options == null || mc.getWindow() == null) {
            return;
        }
        if (this.roofPhase == RoofPhase.WAIT_LANDING) {
            this.releaseJumpControl();
            if (mc.player.onGround()) {
                this.waitingLanding = false;
                this.clearRoofSupportPlan("landed");
            }
            return;
        }
        if (this.roofPhase != RoofPhase.JUMP_PILLAR_SUPPORT) {
            this.releaseJumpControl();
            this.jumpAirTicks = 0;
            this.jumpWindowTicks = 0;
            return;
        }
        if (this.roofSupportPos != null && !BlockPlacementUtil.canReplace(this.roofSupportPos, ItemStack.EMPTY)) {
            this.enterWaitLanding("support-placed");
            return;
        }
        if (!this.moduleJumpDown && mc.player.onGround()) {
            this.jumpTicksRemaining = Math.max(1, this.jumpTicks.getValue().intValue());
            this.setKeyDown(mc.options.keyJump, true);
            this.moduleJumpDown = true;
        }
        if (this.moduleJumpDown) {
            this.jumpTicksRemaining--;
            if (this.jumpTicksRemaining <= 0) {
                this.releaseJumpControl();
            }
        }
        if (this.isJumpSupportAirborne()) {
            this.jumpAirTicks++;
        } else {
            this.jumpAirTicks = 0;
        }
        if (this.isJumpSupportPlacementWindow()) {
            this.jumpWindowTicks++;
        } else {
            this.jumpWindowTicks = 0;
        }
    }

    private boolean shouldDeferJumpSupportPlacement() {
        if (this.roofPhase != RoofPhase.JUMP_PILLAR_SUPPORT || this.roofSupportPos == null) {
            return false;
        }
        if (this.placer.getCurrentTarget() == null
                || !this.roofSupportPos.equals(this.placer.getCurrentTarget().placedBlockPos())) {
            return false;
        }
        return !this.isJumpSupportPlacementWindow();
    }

    private boolean isJumpSupportAirborne() {
        return mc.player != null
                && this.startPos != null
                && !mc.player.onGround()
                && mc.player.getY() > this.startPos.getY() + JUMP_SUPPORT_MIN_PEAK_Y_OFFSET;
    }

    private boolean isJumpSupportPlacementWindow() {
        if (!this.isJumpSupportAirborne()) {
            return false;
        }
        double velocityY = mc.player.getDeltaMovement().y;
        return velocityY <= JUMP_SUPPORT_MAX_UPWARD_SPEED
                && velocityY >= JUMP_SUPPORT_MAX_DOWNWARD_SPEED;
    }

    private void refreshJumpSupportTargetBeforePrepare() {
        if (this.roofPhase != RoofPhase.JUMP_PILLAR_SUPPORT || this.roofSupportPos == null) {
            return;
        }
        BlockPlacementTarget target = this.placer.getCurrentTarget();
        if (target == null || !this.roofSupportPos.equals(target.placedBlockPos())) {
            return;
        }
        if (!this.isJumpSupportPlacementWindow()) {
            this.placer.clearCurrentTarget("jump-refresh-before-peak");
        }
    }

    private void handlePostPlaceRoofState() {
        if (this.roofSupportPos == null || mc.player == null) {
            return;
        }
        if (BlockPlacementUtil.canReplace(this.roofSupportPos, ItemStack.EMPTY)) {
            return;
        }
        if (this.roofSupportMode == RoofSupportMode.JUMP_PILLAR) {
            this.enterWaitLanding("support-placed");
        } else {
            this.clearRoofSupportPlan("support-placed");
        }
    }

    private void enterWaitLanding(String reason) {
        this.releaseJumpControl();
        this.roofPhase = RoofPhase.WAIT_LANDING;
        this.waitingLanding = true;
        this.jumpAirTicks = 0;
        this.jumpWindowTicks = 0;
        this.roofDebugReason = reason;
    }

    private void setRoofSupportPlan(
            RoofPhase phase,
            RoofSupportMode mode,
            BlockPos supportPos,
            String reason) {
        this.roofPhase = phase;
        this.roofSupportMode = mode;
        this.roofSupportPos = supportPos == null ? null : supportPos.immutable();
        this.waitingLanding = false;
        if (mode != RoofSupportMode.JUMP_PILLAR) {
            this.jumpAirTicks = 0;
            this.jumpWindowTicks = 0;
        }
        this.roofDebugReason = reason;
    }

    private void clearRoofSupportPlan(String reason) {
        this.roofPhase = RoofPhase.NORMAL;
        this.roofSupportMode = RoofSupportMode.NONE;
        this.roofSupportPos = null;
        this.waitingLanding = false;
        this.roofDebugReason = reason;
        this.jumpTicksRemaining = 0;
        this.jumpAirTicks = 0;
        this.jumpWindowTicks = 0;
        this.releaseJumpControl();
    }

    private void resetRoofSupportState() {
        this.roofPhase = RoofPhase.NORMAL;
        this.roofSupportMode = RoofSupportMode.NONE;
        this.roofPos = null;
        this.roofSupportPos = null;
        this.jumpTicksRemaining = 0;
        this.jumpAirTicks = 0;
        this.jumpWindowTicks = 0;
        this.moduleJumpDown = false;
        this.waitingLanding = false;
        this.roofDebugReason = "idle";
    }

    private void releaseJumpControl() {
        if (this.moduleJumpDown) {
            this.restoreControlledKeys();
        }
        this.jumpTicksRemaining = 0;
    }

    private void restoreControlledKeys() {
        if (mc == null || mc.options == null || mc.getWindow() == null) {
            this.moduleJumpDown = false;
            return;
        }
        if (this.moduleJumpDown) {
            this.setKeyDown(mc.options.keyJump, this.isPhysicalKeyDown(mc.options.keyJump));
            this.moduleJumpDown = false;
        }
    }

    private boolean isPhysicalKeyDown(KeyMapping keyMapping) {
        return mc != null
                && mc.getWindow() != null
                && InputConstants.isKeyDown(mc.getWindow().getWindow(), keyMapping.getKey().getValue());
    }

    private void setKeyDown(KeyMapping keyMapping, boolean down) {
        KeyMapping.set(keyMapping.getKey(), down);
        keyMapping.setDown(down);
    }

    private boolean isBlockInComplete() {
        if (mc.player == null || mc.level == null || this.startPos == null) {
            return false;
        }
        QueuedBlockPlacer.SlotSelection slot = this.findSlot(null);
        ItemStack stack = slot == null ? ItemStack.EMPTY : slot.itemStack();
        int playerHeight = Mth.ceil(mc.player.getBbHeight());
        for (BlockPos pos : this.generateBasePositions(playerHeight)) {
            if (BlockPlacementUtil.canReplace(pos, stack)) {
                return false;
            }
        }
        return true;
    }

    private boolean isBaseStructureComplete(int playerHeight, ItemStack stack) {
        if (mc.player == null || mc.level == null || this.startPos == null) {
            return false;
        }
        for (BlockPos pos : this.generateBasePositionsWithoutRoof(playerHeight)) {
            if (BlockPlacementUtil.canReplace(pos, stack)) {
                return false;
            }
        }
        return true;
    }

    private List<BlockPos> generateBasePositions(int playerHeight) {
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
        result.addAll(this.generateBasePositionsWithoutRoof(playerHeight));
        result.add(this.startPos.above(playerHeight));
        return new ArrayList<>(result);
    }

    private List<BlockPos> generateBasePositionsWithoutRoof(int playerHeight) {
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
        result.add(this.startPos.below());
        for (Direction direction : this.getOrderedHorizontalDirections()) {
            BlockPos value = this.startPos.relative(direction);
            for (int i = 0; i < playerHeight; i++) {
                result.add(value.above(i));
            }
        }
        return new ArrayList<>(result);
    }

    private void debugLog(String phase) {
        if (!this.debug.getValue() || mc.player == null) {
            return;
        }
        int interval = Math.max(1, this.debugInterval.getValue().intValue());
        String state = phase
                + " start=" + this.formatBlockPos(this.startPos)
                + " player=" + this.formatBlockPos(mc.player.blockPosition())
                + " roofPhase=" + this.roofPhase
                + " roofSupportMode=" + this.roofSupportMode
                + " roofPos=" + this.formatBlockPos(this.roofPos)
                + " supportPos=" + this.formatBlockPos(this.roofSupportPos)
                + " jumpTicks=" + this.jumpTicksRemaining
                + " jumpAirTicks=" + this.jumpAirTicks
                + " jumpWindowTicks=" + this.jumpWindowTicks
                + " jumpY=" + this.formatDouble(this.getJumpYOffset())
                + " jumpVy=" + this.formatDouble(this.getJumpVelocityY())
                + " waitingLanding=" + this.waitingLanding
                + " roofReason=" + this.roofDebugReason
                + " " + this.placer.getDebugSummary();
        this.debugTicks++;
        if (state.equals(this.lastDebugState) && this.debugTicks % interval != 0) {
            return;
        }

        this.lastDebugState = state;
        String line = "[BlockInDebug] tick=" + mc.player.tickCount + " " + state;
        logger.info(line);
        ChatUtil.print(line);
    }

    private String formatBlockPos(BlockPos pos) {
        if (pos == null) {
            return "null";
        }
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private double getJumpYOffset() {
        if (mc.player == null || this.startPos == null) {
            return 0.0;
        }
        return mc.player.getY() - this.startPos.getY();
    }

    private double getJumpVelocityY() {
        return mc.player == null ? 0.0 : mc.player.getDeltaMovement().y;
    }

    private String formatDouble(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    private enum RoofPhase {
        NORMAL,
        DIAGONAL_SUPPORT,
        PILLAR_SUPPORT,
        JUMP_PILLAR_SUPPORT,
        WAIT_LANDING
    }

    private enum RoofSupportMode {
        NONE,
        DIAGONAL,
        PILLAR,
        JUMP_PILLAR
    }
}
