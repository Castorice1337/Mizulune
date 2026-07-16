/*
 * Reimplements Raven bS Clutch behavior for Mizulune/OpenZen's 1.20.1
 * architecture. The original Raven module is a Minecraft 1.8.9 Forge module;
 * this version keeps the self-rescue target selection idea while using
 * RotationProvider and BlockPlacementUtil instead of copying the old event and
 * setting systems.
 */
package shit.zen.modules.impl.player;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import shit.zen.event.EventPriority;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.movement.GodBridgeAssist;
import shit.zen.modules.impl.world.BlockIn;
import shit.zen.utils.game.BlockPlacementOptions;
import shit.zen.utils.game.BlockPlacementTarget;
import shit.zen.utils.game.BlockPlacementUtil;
import shit.zen.utils.game.BlockUtil;
import shit.zen.utils.game.PlayerUtil;
import shit.zen.utils.misc.ChatUtil;
import shit.zen.utils.rotation.Rotation;
import shit.zen.utils.rotation.RotationApplyMode;
import shit.zen.utils.rotation.RotationHandler;
import shit.zen.utils.rotation.RotationProvider;
import shit.zen.utils.rotation.SmoothMode;
import shit.zen.value.ValueGroup;
import shit.zen.value.impl.BooleanValue;
import shit.zen.value.impl.ModeValue;
import shit.zen.value.impl.NumberValue;

public class Clutch extends Module implements RotationProvider {
    public static Clutch INSTANCE;

    private static final double HALF_WIDTH = 0.3;
    private static final double[][] CORNERS = {
            {-HALF_WIDTH, -HALF_WIDTH},
            {HALF_WIDTH, -HALF_WIDTH},
            {-HALF_WIDTH, HALF_WIDTH},
            {HALF_WIDTH, HALF_WIDTH}
    };
    private static final int FALL_PREDICT_TICKS = 60;
    private static final int FUTURE_POSITION_TICKS = 20;
    private static final int LANDED_GUARD_TICKS = 10;
    private static final Map<String, Integer> BLOCK_SCORE = new HashMap<>();

    static {
        BLOCK_SCORE.put("obsidian", 0);
        BLOCK_SCORE.put("end_stone", 1);
        BLOCK_SCORE.put("planks", 2);
        BLOCK_SCORE.put("log", 2);
        BLOCK_SCORE.put("glass", 3);
        BLOCK_SCORE.put("stained_glass", 3);
        BLOCK_SCORE.put("terracotta", 4);
        BLOCK_SCORE.put("stone", 5);
        BLOCK_SCORE.put("wool", 5);
    }

    public final BooleanValue autoClutch = new BooleanValue("Auto Clutch", false);
    public final NumberValue minimumFallDistance = new NumberValue("Minimum Fall Distance", 10, 3, 20, 1,
            this.autoClutch::getValue);
    public final BooleanValue simulateFuturePosition = new BooleanValue("Simulate Future Position", true);
    public final BooleanValue onlyByReceiveVelocity = new BooleanValue("Only By Receive Velocity", true);
    public final NumberValue minimumVelocity = new NumberValue("Minimum Velocity", 0.35, 0.0, 3.0, 0.05,
            this.onlyByReceiveVelocity::getValue);
    public final NumberValue velocityWindow = new NumberValue("Velocity Window", 20, 1, 60, 1,
            this.onlyByReceiveVelocity::getValue);

    public final NumberValue reach = new NumberValue("Reach", 4.5, 0.5, 4.5, 0.1);
    public final NumberValue maxBlocks = new NumberValue("Max Blocks", 10, 0, 20, 1);
    public final NumberValue rotationTolerance = new NumberValue("Rotation Tolerance", 25, 20, 100, 1);
    public final BooleanValue autoBlock = new BooleanValue("Auto Block", true);
    public final BooleanValue switchBack = new BooleanValue("Switch Back", true, this.autoBlock::getValue);
    public final BooleanValue useOffhand = new BooleanValue("Use Offhand", true);
    public final NumberValue cooldown = new NumberValue("Cooldown", 1, 0, 10, 1);
    public final BooleanValue airStuck = new BooleanValue("Air Stuck", false);

    public final ModeValue rotationMode = new ModeValue("Rotation Mode", "Silent", "ChangeLook", "Off")
            .withDefault("Silent");
    public final ModeValue smoothMode = new ModeValue("Smooth Mode", "SNAP", "LINEAR", "SIGMOID")
            .withDefault("LINEAR");
    public final NumberValue speed = new NumberValue("Speed", 8, 0, 100, 1, () -> !this.smoothMode.is("SNAP"));
    public final NumberValue snapbackSpeed = new NumberValue("Snapback Speed", 12, 0, 100, 1,
            () -> !this.smoothMode.is("SNAP"));
    public final BooleanValue movementFix = new BooleanValue("Movement Fix", true, () -> !this.rotationMode.is("Off"));
    public final NumberValue resetTicks = new NumberValue("Reset Ticks", 3, 0, 10, 1, this::isSilentRotation);
    public final NumberValue resetThreshold = new NumberValue("Reset Threshold", 1.0, 0.1, 10.0, 0.1,
            this::isSilentRotation);

    public final BooleanValue debug = new BooleanValue("Debug", false);
    public final NumberValue debugInterval = new NumberValue("Debug Interval", 5, 1, 40, 1, this.debug::getValue);

    private BlockPlacementTarget currentTarget;
    private Rotation currentRotation;
    private boolean hasAim;
    private boolean placing;
    private boolean resetting;
    private boolean slotWasSwapped;
    private boolean attackSuppressed;
    private boolean useSuppressed;
    private int previousSlot = -1;
    private int plannedSlot = -1;
    private InteractionHand plannedHand = InteractionHand.MAIN_HAND;
    private int clutchBlocksPlaced;
    private int targetPreparedTick = -1;
    private int lastPlaceTick = -1;
    private int cooldownTicks;
    private BlockPos lastPlacedBlock;
    private BlockPos lastSupportBlock;
    private boolean autoClutchActive;
    private boolean autoClutchChecking;
    private int autoClutchCheckCounter;
    private boolean autoClutchLandedGuard;
    private int autoClutchLandedTick;
    private int previousHurtTime = -1;
    private int debugTicks;
    private String lastDebugState;
    private int receivedVelocityTicks;
    private Vec3 lastReceivedVelocity = Vec3.ZERO;
    private int lastReceivedVelocityTick = -1;
    private final ClutchAirStuckController airStuckController;

    public Clutch() {
        super("clutch", "Clutch", Category.PLAYER);
        this.airStuckController = new ClutchAirStuckController(this::debugLog);
        INSTANCE = this;
    }

    /** True while Clutch owns server rotation or its position hold is active. */
    public boolean isActivelyRescuing() {
        return isActiveRescueState(
                this.isEnabled(),
                mc.player != null && mc.player.onGround(),
                mc.player == null ? 0.0 : mc.player.getDeltaMovement().y,
                RotationHandler.isActiveRotationOwner(this),
                this.hasAim,
                this.currentTarget != null,
                this.resetting,
                this.airStuckController.isActive());
    }

    static boolean isActiveRescueState(
            boolean enabled,
            boolean onGround,
            double verticalVelocity,
            boolean activeRotationOwner,
            boolean hasAim,
            boolean hasTarget,
            boolean resetting,
            boolean airStuckActive) {
        return enabled
                && !onGround
                && verticalVelocity < 0.0
                && (airStuckActive
                || activeRotationOwner && hasAim && hasTarget && !resetting);
    }

    @Override
    protected void configureValueTree(ValueGroup root) {
        ValueGroup activation = root.group("activation", "Activation");
        activation.add(this.autoClutch);
        activation.add(this.minimumFallDistance);
        activation.add(this.simulateFuturePosition);
        activation.add(this.onlyByReceiveVelocity);
        activation.add(this.minimumVelocity);
        activation.add(this.velocityWindow);

        ValueGroup placement = root.group("placement", "Placement");
        placement.add(this.reach);
        placement.add(this.maxBlocks);
        placement.add(this.rotationTolerance);
        placement.add(this.autoBlock);
        placement.add(this.switchBack);
        placement.add(this.useOffhand);
        placement.add(this.cooldown);
        placement.add(this.airStuck);

        ValueGroup rotation = root.group("rotation", "Rotation");
        rotation.add(this.rotationMode);
        rotation.add(this.smoothMode);
        rotation.add(this.speed);
        rotation.add(this.snapbackSpeed);
        rotation.add(this.movementFix);
        rotation.add(this.resetTicks);
        rotation.add(this.resetThreshold);

        ValueGroup debugGroup = root.group("debug", "Debug");
        debugGroup.add(this.debug);
        debugGroup.add(this.debugInterval);
    }

    @Override
    protected void onEnable() {
        this.resetState();
        RotationHandler.registerProvider(this);
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        RotationHandler.unregisterProvider(this);
        this.restoreSuppressedInputs();
        this.restoreSlot(true);
        this.resetState();
        super.onDisable();
    }

    @EventTarget(value = EventPriority.HIGH)
    public void onTick(TickEvent event) {
        this.restoreSuppressedInputs();
        if (mc.player == null || mc.level == null || mc.options == null) {
            this.clearAim(false);
            this.airStuckController.reset();
            return;
        }

        if (this.receivedVelocityTicks > 0) {
            this.receivedVelocityTicks--;
        }
        if (mc.player.onGround()) {
            this.clutchBlocksPlaced = 0;
            this.lastPlacedBlock = null;
            this.lastSupportBlock = null;
            this.airStuckController.reset();
        }

        this.updateAutoClutch(mc.player.tickCount);
        if (this.shouldLogConflicts()) {
            this.debugLog("conflict");
        }

        if (!this.shouldAttemptClutch()) {
            this.clearAim(shouldAllowSnapback(
                    mc.player.onGround(),
                    mc.player.getDeltaMovement().y));
            this.disablePlacing(false);
            this.airStuckController.reset();
            this.debugLog("idle");
            return;
        }

        SlotSelection slot = this.findSlot();
        if (slot == null) {
            this.clearAim(true);
            this.disablePlacing(false);
            this.airStuckController.reset();
            this.debugLog("no-slot");
            return;
        }

        AimResult aim = this.findClutchAim(slot.itemStack());
        if (aim == null) {
            this.clearAim(true);
            this.disablePlacing(false);
            this.airStuckController.reset("no-target");
            this.debugLog("no-aim");
            return;
        }

        if (this.isDifferentTarget(this.currentTarget, aim.target())) {
            this.targetPreparedTick = mc.player.tickCount;
        }
        this.currentTarget = aim.target();
        this.currentRotation = aim.rotation();
        this.hasAim = true;
        this.resetting = false;
        this.plannedSlot = slot.hotbarSlot();
        this.plannedHand = slot.hand();
        this.enablePlacing();
        this.equipSlot(slot);
        this.suppressControlledInputs();
        boolean placementReady = this.cooldownTicks <= 0
                && (this.getApplyMode() == RotationApplyMode.OFF
                || mc.player.tickCount > this.targetPreparedTick)
                && this.canPlaceMoreBlocks()
                && this.isServerRotationReady(this.currentRotation);
        this.airStuckController.update(new ClutchAirStuckController.UpdateInput(
                this.airStuck.getValue(),
                true,
                this.currentTarget,
                this.getLastAppliedRotation(),
                slot.itemStack(),
                this.getPlacementOptions(),
                placementReady,
                this.canPlaceMoreBlocks()));
        this.debugLog("aim");
    }

    @EventTarget(value = EventPriority.HIGH)
    public void onPacket(PacketEvent event) {
        if (mc.player == null || event.getPacket() == null) {
            return;
        }
        if (!(event.getPacket() instanceof ClientboundSetEntityMotionPacket motion)
                || motion.getId() != mc.player.getId()) {
            return;
        }

        Vec3 velocity = new Vec3(
                motion.getXa() / 8000.0,
                motion.getYa() / 8000.0,
                motion.getZa() / 8000.0);
        double horizontal = this.horizontalLength(velocity);
        double threshold = Math.max(0.0, this.minimumVelocity.getValue().doubleValue());
        if (horizontal < threshold && velocity.length() < threshold) {
            return;
        }

        this.lastReceivedVelocity = velocity;
        this.lastReceivedVelocityTick = mc.player.tickCount;
        this.receivedVelocityTicks = Math.max(1, this.velocityWindow.getValue().intValue());
        this.autoClutchChecking = true;
        this.autoClutchCheckCounter = 0;
        this.autoClutchLandedGuard = false;
        this.debugLog("velocity");
    }

    @EventTarget(value = EventPriority.LOWEST)
    public void onTickLate(TickEvent event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        if (this.resetting) {
            this.updateSnapbackState();
            this.debugLog("reset");
            return;
        }
        if (!this.hasAim || !this.placing || this.currentTarget == null) {
            return;
        }
        if (mc.player.tickCount == this.lastPlaceTick) {
            return;
        }
        if (this.cooldownTicks > 0) {
            this.cooldownTicks--;
            this.suppressControlledInputs();
            return;
        }
        if (this.getApplyMode() != RotationApplyMode.OFF && mc.player.tickCount <= this.targetPreparedTick) {
            this.suppressControlledInputs();
            return;
        }

        SlotSelection slot = this.findSlot();
        if (slot == null) {
            this.airStuckController.reset();
            this.debugLog("place:no-slot");
            return;
        }
        this.equipSlot(slot);

        Rotation placeRotation = this.getApplyMode() == RotationApplyMode.OFF
                ? this.currentRotation
                : RotationHandler.getSmoothedRotation(this);
        if (placeRotation == null) {
            placeRotation = this.currentRotation;
        }
        if (placeRotation == null || !this.isWithinRotationTolerance(placeRotation)) {
            this.suppressControlledInputs();
            this.debugLog("place:wait-rotation");
            return;
        }
        if (!this.isServerRotationReady(this.currentRotation)) {
            this.suppressControlledInputs();
            this.debugLog("place:wait-server-rotation");
            return;
        }
        if (!this.canPlaceMoreBlocks()) {
            this.airStuckController.reset();
            this.debugLog("place:max-blocks");
            return;
        }

        BlockPlacementUtil.PlacementResult result = BlockPlacementUtil.placeDetailed(
                this.currentTarget, slot.hand(), this.currentRotation, slot.itemStack(), this.getPlacementOptions());
        if (result.placed()) {
            BlockPlacementTarget placedTarget = this.currentTarget;
            this.lastPlaceTick = mc.player.tickCount;
            this.cooldownTicks = Math.max(0, this.cooldown.getValue().intValue());
            this.clutchBlocksPlaced++;
            this.lastPlacedBlock = placedTarget.placedBlockPos();
            this.lastSupportBlock = placedTarget.interactedBlockPos();
            this.currentTarget = null;
            this.currentRotation = null;
            this.hasAim = false;
            this.targetPreparedTick = -1;
            this.airStuckController.onPlacementSuccess(placedTarget);
            if (slot.restoreAfterPlace()) {
                this.restoreSlot(false);
            }
            this.debugLog("place:success");
        } else {
            this.airStuckController.onPlacementFailure(result.reason());
            this.debugLog("place:fail " + result.reason());
        }
        this.suppressControlledInputs();
    }

    @Override
    public String getName() {
        return "Clutch";
    }

    @Override
    public Rotation getRotation() {
        return this.currentRotation;
    }

    @Override
    public boolean isRotationActive() {
        return this.isEnabled()
                && this.getApplyMode() != RotationApplyMode.OFF
                && this.currentRotation != null
                && (this.hasAim || this.resetting);
    }

    @Override
    public RotationApplyMode getApplyMode() {
        if (this.rotationMode.is("ChangeLook")) {
            return RotationApplyMode.CHANGE_LOOK;
        }
        if (this.rotationMode.is("Off")) {
            return RotationApplyMode.OFF;
        }
        return RotationApplyMode.SILENT;
    }

    @Override
    public SmoothMode getSmoothMode() {
        if (this.smoothMode.is("SNAP")) {
            return SmoothMode.SNAP;
        }
        if (this.smoothMode.is("SIGMOID")) {
            return SmoothMode.SIGMOID;
        }
        return SmoothMode.LINEAR;
    }

    @Override
    public int getSmoothDurationTicks() {
        return 6;
    }

    @Override
    public double getMaxYawSpeed() {
        return this.getCurrentRotationSpeed();
    }

    @Override
    public double getMaxPitchSpeed() {
        return this.getCurrentRotationSpeed();
    }

    @Override
    public double getMinStep() {
        return 0.05;
    }

    @Override
    public double getRotationEpsilon() {
        return 0.1;
    }

    @Override
    public boolean shouldFixMovement() {
        return this.getApplyMode() == RotationApplyMode.SILENT || this.movementFix.getValue();
    }

    @Override
    public int getTicksUntilReset() {
        return Math.max(0, this.resetTicks.getValue().intValue());
    }

    @Override
    public double getResetThreshold() {
        return this.resetThreshold.getValue().doubleValue();
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
        return rotationPriority(this.resetting);
    }

    static int rotationPriority(boolean resetting) {
        return resetting ? 40 : 60;
    }

    private void resetState() {
        this.currentTarget = null;
        this.currentRotation = null;
        this.hasAim = false;
        this.placing = false;
        this.resetting = false;
        this.slotWasSwapped = false;
        this.attackSuppressed = false;
        this.useSuppressed = false;
        this.previousSlot = -1;
        this.plannedSlot = -1;
        this.plannedHand = InteractionHand.MAIN_HAND;
        this.clutchBlocksPlaced = 0;
        this.targetPreparedTick = -1;
        this.lastPlaceTick = -1;
        this.cooldownTicks = 0;
        this.lastPlacedBlock = null;
        this.lastSupportBlock = null;
        this.autoClutchActive = false;
        this.autoClutchChecking = false;
        this.autoClutchCheckCounter = 0;
        this.autoClutchLandedGuard = false;
        this.autoClutchLandedTick = 0;
        this.previousHurtTime = -1;
        this.debugTicks = 0;
        this.lastDebugState = null;
        this.receivedVelocityTicks = 0;
        this.lastReceivedVelocity = Vec3.ZERO;
        this.lastReceivedVelocityTick = -1;
        this.airStuckController.reset();
    }

    private void updateAutoClutch(int tickCount) {
        if (!this.autoClutch.getValue()) {
            this.autoClutchActive = false;
            this.autoClutchChecking = false;
            this.autoClutchLandedGuard = false;
            this.previousHurtTime = mc.player.hurtTime;
            return;
        }

        int hurtTime = mc.player.hurtTime;
        if (hurtTime > this.previousHurtTime) {
            this.autoClutchChecking = true;
            this.autoClutchCheckCounter = 0;
            this.autoClutchLandedGuard = false;
        }
        this.previousHurtTime = hurtTime;

        if (this.autoClutchChecking && !this.autoClutchActive && !this.autoClutchLandedGuard) {
            if (this.autoClutchCheckCounter == 0 || this.autoClutchCheckCounter % 3 == 0) {
                if (this.willFallFar(this.minimumFallDistance.getValue().doubleValue())) {
                    this.autoClutchActive = true;
                }
            }
            this.autoClutchCheckCounter++;
        }

        if (this.autoClutchLandedGuard) {
            boolean expired = tickCount - this.autoClutchLandedTick >= LANDED_GUARD_TICKS;
            boolean jumped = mc.options != null && mc.options.keyJump.isDown();
            boolean airborneUp = !mc.player.onGround() && mc.player.getDeltaMovement().y > 0.0;
            if (expired || jumped || airborneUp) {
                this.autoClutchActive = false;
                this.autoClutchChecking = false;
                this.autoClutchLandedGuard = false;
            }
        }

        if (this.autoClutchActive && mc.player.onGround() && mc.player.hurtTime < Math.max(0, mc.player.hurtDuration - 2)) {
            if (!this.autoClutchLandedGuard) {
                this.autoClutchLandedGuard = true;
                this.autoClutchLandedTick = tickCount;
                if (!this.willFallSoon()) {
                    this.autoClutchActive = false;
                    this.autoClutchChecking = false;
                    this.autoClutchLandedGuard = false;
                }
            }
        }

        if (!this.autoClutchActive && !this.autoClutchLandedGuard && mc.player.onGround() && mc.player.hurtTime == 0) {
            this.autoClutchChecking = false;
            this.autoClutchCheckCounter = 0;
        }
    }

    private boolean shouldAttemptClutch() {
        if (mc.screen != null || mc.player.getAbilities().flying || mc.player.isFallFlying()
                || mc.player.isInWaterRainOrBubble() || mc.player.isInLava()) {
            return false;
        }
        if (this.onlyByReceiveVelocity.getValue() && !this.hasRecentReceivedVelocity()) {
            return false;
        }
        boolean manualActive = this.isManualClutchContext();
        boolean active = isRescueActivation(
                manualActive,
                this.autoClutchActive,
                mc.player.onGround(),
                mc.player.getDeltaMovement().y);
        if (!active) {
            return false;
        }

        BlockPos below = BlockPos.containing(mc.player.getX(), mc.player.getY() - 1.0, mc.player.getZ());
        return this.canPlaceThrough(below);
    }

    private boolean isManualClutchContext() {
        if (mc.player == null) {
            return false;
        }
        return isManualRescueMotion(
                mc.player.onGround(),
                mc.player.getDeltaMovement().y);
    }

    static boolean isManualRescueMotion(boolean onGround, double verticalVelocity) {
        return !onGround && verticalVelocity < 0.0;
    }

    static boolean isRescueActivation(
            boolean manualActive,
            boolean autoActive,
            boolean onGround,
            double verticalVelocity) {
        return (manualActive || autoActive)
                && !onGround
                && verticalVelocity < 0.0;
    }

    static boolean shouldAllowSnapback(boolean onGround, double verticalVelocity) {
        return onGround || verticalVelocity < 0.0;
    }

    private AimResult findClutchAim(ItemStack stack) {
        if (stack == null || stack.isEmpty() || mc.player == null || mc.level == null) {
            return null;
        }

        Vec3 playerPos = mc.player.position();
        Vec3 futurePos = playerPos;
        if (this.simulateFuturePosition.getValue()) {
            futurePos = this.predictFuturePosition(FUTURE_POSITION_TICKS);
        }

        int feetX = Mth.floor(playerPos.x);
        int feetY = Mth.floor(playerPos.y);
        int feetZ = Mth.floor(playerPos.z);
        int futureX = Mth.floor(futurePos.x);
        int futureY = Mth.floor(futurePos.y);
        int futureZ = Mth.floor(futurePos.z);
        double horizontalMotion = Math.max(
                this.horizontalLength(mc.player.getDeltaMovement()),
                this.horizontalLength(this.lastReceivedVelocity));
        int horizontalExpand = Mth.clamp(5 + (int) Math.ceil(horizontalMotion * 4.0), 5, 11);
        int minX = Math.min(feetX, futureX) - horizontalExpand;
        int maxX = Math.max(feetX, futureX) + horizontalExpand;
        int minY = Math.min(feetY, futureY) - 5;
        int maxY = feetY - 1;
        int minZ = Math.min(feetZ, futureZ) - horizontalExpand;
        int maxZ = Math.max(feetZ, futureZ) + horizontalExpand;
        double futureWeight = this.hasRecentReceivedVelocity() ? 0.88 : 0.7;
        double currentWeight = 1.0 - futureWeight;

        List<BlockCandidate> candidates = new ArrayList<>();
        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos support = new BlockPos(x, y, z);
                    if (!BlockPlacementUtil.isValidSupport(support)) {
                        continue;
                    }
                    double currentDistance = this.distToBlockAabb(playerPos, support);
                    double futureDistance = this.distToBlockAabb(futurePos, support);
                    double score = this.simulateFuturePosition.getValue()
                            ? currentDistance * currentWeight + futureDistance * futureWeight
                            : currentDistance;
                    if (support.equals(this.lastSupportBlock) || support.equals(this.lastPlacedBlock)) {
                        score *= 0.95;
                    }
                    candidates.add(new BlockCandidate(score, support));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(BlockCandidate::score));

        Vec3 eye = mc.player.getEyePosition(1.0f);
        double range = this.reach.getValue().doubleValue();
        for (BlockCandidate candidate : candidates) {
            boolean underPlayer = this.isBlockUnderPlayer(candidate.pos(), playerPos);
            AimResult aim = this.getBestRotationToSupport(stack, candidate.pos(), eye, range, underPlayer);
            if (aim != null) {
                return aim;
            }
        }
        return null;
    }

    private AimResult getBestRotationToSupport(
            ItemStack stack,
            BlockPos support,
            Vec3 eye,
            double range,
            boolean underPlayer) {
        double inset = 0.05;
        double step = 0.2;
        double jitter = step * 0.1;
        int samples = (int) Math.round(1.0 / step);
        boolean faceSouth = Math.abs(eye.z - (support.getZ() + 1.0)) < Math.abs(eye.z - support.getZ());
        boolean faceEast = Math.abs(eye.x - (support.getX() + 1.0)) < Math.abs(eye.x - support.getX());
        Rotation base = this.getBaseRotation();

        List<RotationCandidate> rotations = new ArrayList<>();
        rotations.add(new RotationCandidate(0.0, base));
        for (int row = 0; row <= samples; row++) {
            double v = this.clamp01(row * step + this.randomRange(-jitter, jitter));
            for (int col = 0; col <= samples; col++) {
                double u = this.clamp01(col * step + this.randomRange(-jitter, jitter));
                if (underPlayer) {
                    Rotation top = this.rotationToPoint(eye,
                            support.getX() + u,
                            support.getY() + 1.0 - inset,
                            support.getZ() + v);
                    rotations.add(new RotationCandidate(this.rotationCost(base, top), top));
                }

                Rotation zFace = this.rotationToPoint(eye,
                        support.getX() + u,
                        support.getY() + v,
                        faceSouth ? support.getZ() + 1.0 - inset : support.getZ() + inset);
                rotations.add(new RotationCandidate(this.rotationCost(base, zFace), zFace));

                Rotation xFace = this.rotationToPoint(eye,
                        faceEast ? support.getX() + 1.0 - inset : support.getX() + inset,
                        support.getY() + v,
                        support.getZ() + u);
                rotations.add(new RotationCandidate(this.rotationCost(base, xFace), xFace));
            }
        }
        rotations.sort(Comparator.comparingDouble(RotationCandidate::cost));

        for (RotationCandidate candidate : rotations) {
            Rotation rotation = this.unwrapRotation(candidate.rotation(), base);
            HitResult result = BlockPlacementUtil.rayTrace(rotation, range);
            if (!(result instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
                continue;
            }
            if (!support.equals(hit.getBlockPos())) {
                continue;
            }
            Direction face = hit.getDirection();
            if (face == Direction.DOWN || (face == Direction.UP && !underPlayer)) {
                continue;
            }
            BlockPos placedBlock = support.relative(face);
            if (!BlockPlacementUtil.canReplace(placedBlock, stack)) {
                continue;
            }

            BlockPlacementTarget target = new BlockPlacementTarget(
                    support,
                    placedBlock,
                    face,
                    hit.getLocation(),
                    hit.getLocation().y,
                    rotation);
            if (BlockPlacementUtil.isValidPlacementTarget(target, stack, this.getPlacementOptions())) {
                return new AimResult(target, rotation);
            }
        }
        return null;
    }

    private SlotSelection findSlot() {
        if (mc.player == null) {
            return null;
        }
        int selected = mc.player.getInventory().selected;
        if (this.isHotbarPlaceableBlock(selected)) {
            return new SlotSelection(selected, InteractionHand.MAIN_HAND, false);
        }
        if (this.autoBlock.getValue()) {
            int slot = this.findBestHotbarBlockSlot();
            if (slot != -1) {
                return new SlotSelection(slot, InteractionHand.MAIN_HAND, this.switchBack.getValue());
            }
        }
        if (this.useOffhand.getValue()) {
            ItemStack offhand = mc.player.getOffhandItem();
            if (offhand.getItem() instanceof BlockItem && BlockUtil.isPlaceable(offhand)) {
                return new SlotSelection(-1, InteractionHand.OFF_HAND, false);
            }
        }
        return null;
    }

    private void enablePlacing() {
        if (this.placing) {
            return;
        }
        this.placing = true;
        if (!this.slotWasSwapped && mc.player != null) {
            this.previousSlot = mc.player.getInventory().selected;
        }
    }

    private void disablePlacing(boolean forceRestore) {
        if (!this.placing && !forceRestore) {
            return;
        }
        this.placing = false;
        this.plannedSlot = -1;
        this.plannedHand = InteractionHand.MAIN_HAND;
        if (forceRestore || !this.hasAim) {
            this.restoreSlot(forceRestore);
        }
    }

    private void clearAim(boolean allowSnapback) {
        if (!this.hasAim && !this.resetting) {
            return;
        }
        this.currentTarget = null;
        this.hasAim = false;
        this.targetPreparedTick = -1;
        if (allowSnapback && this.currentRotation != null && this.getApplyMode() != RotationApplyMode.OFF) {
            this.resetting = true;
            this.currentRotation = new Rotation(mc.player.getYRot(), mc.player.getXRot());
        } else {
            this.resetting = false;
            this.currentRotation = null;
        }
    }

    private void updateSnapbackState() {
        if (!this.resetting || mc.player == null) {
            return;
        }
        Rotation smoothed = RotationHandler.getSmoothedRotation(this);
        Rotation real = new Rotation(mc.player.getYRot(), mc.player.getXRot());
        if (smoothed == null || BlockPlacementUtil.rotationDistance(smoothed, real) <= this.resetThreshold.getValue().doubleValue()) {
            this.resetting = false;
            this.currentRotation = null;
            this.restoreSlot(false);
            this.restoreSuppressedInputs();
        } else {
            this.currentRotation = real;
        }
    }

    private void equipSlot(SlotSelection slot) {
        if (slot == null || mc.player == null || slot.hand() != InteractionHand.MAIN_HAND
                || slot.hotbarSlot() < 0 || slot.hotbarSlot() > 8) {
            return;
        }
        if (!this.slotWasSwapped) {
            this.previousSlot = mc.player.getInventory().selected;
        }
        if (mc.player.getInventory().selected != slot.hotbarSlot()) {
            mc.player.getInventory().selected = slot.hotbarSlot();
            PlayerUtil.sendCarriedItem();
            this.slotWasSwapped = true;
        }
    }

    private void restoreSlot(boolean force) {
        if (mc.player != null && this.slotWasSwapped && this.previousSlot >= 0 && this.previousSlot <= 8
                && (force || this.switchBack.getValue())
                && mc.player.getInventory().selected != this.previousSlot) {
            mc.player.getInventory().selected = this.previousSlot;
            PlayerUtil.sendCarriedItem();
        }
        if (force || !this.placing) {
            this.slotWasSwapped = false;
            this.previousSlot = -1;
        }
    }

    private void suppressControlledInputs() {
        if (mc.options == null) {
            return;
        }
        this.setKeyDown(mc.options.keyAttack, false);
        this.setKeyDown(mc.options.keyUse, false);
        this.attackSuppressed = true;
        this.useSuppressed = true;
    }

    private void restoreSuppressedInputs() {
        if (mc == null || mc.options == null || mc.getWindow() == null) {
            this.attackSuppressed = false;
            this.useSuppressed = false;
            return;
        }
        if (this.attackSuppressed) {
            this.setKeyDown(mc.options.keyAttack, this.isPhysicalKeyDown(mc.options.keyAttack));
            this.attackSuppressed = false;
        }
        if (this.useSuppressed) {
            this.setKeyDown(mc.options.keyUse, this.isPhysicalKeyDown(mc.options.keyUse));
            this.useSuppressed = false;
        }
    }

    private boolean canPlaceMoreBlocks() {
        int max = Math.max(0, this.maxBlocks.getValue().intValue());
        return max == 0 || this.clutchBlocksPlaced < max;
    }

    private boolean hasRecentReceivedVelocity() {
        return this.receivedVelocityTicks > 0;
    }

    private double horizontalLength(Vec3 vec) {
        if (vec == null) {
            return 0.0;
        }
        return Math.sqrt(vec.x * vec.x + vec.z * vec.z);
    }

    private boolean isWithinRotationTolerance(Rotation rotation) {
        return this.currentRotation != null
                && BlockPlacementUtil.rotationDistance(rotation, this.currentRotation)
                <= this.rotationTolerance.getValue().doubleValue();
    }

    private boolean isServerRotationReady(Rotation rotation) {
        if (rotation == null) {
            return false;
        }
        return this.isServerRotationClose(RotationHandler.getActualServerRotation(), rotation)
                || this.isServerRotationClose(RotationHandler.getLogicalServerRotation(), rotation);
    }

    private boolean isServerRotationClose(Rotation serverRotation, Rotation targetRotation) {
        return serverRotation != null
                && BlockPlacementUtil.rotationDistance(serverRotation, targetRotation)
                <= this.getServerRotationTolerance();
    }

    private double getServerRotationTolerance() {
        return Math.max(1.0, Math.min(5.0, this.rotationTolerance.getValue().doubleValue() * 0.25));
    }

    private Rotation getLastAppliedRotation() {
        if (this.getApplyMode() == RotationApplyMode.OFF) {
            return this.currentRotation;
        }
        return RotationHandler.sentRotation != null
                ? RotationHandler.sentRotation
                : RotationHandler.prevSentRotation;
    }

    private BlockPlacementOptions getPlacementOptions() {
        double range = this.reach.getValue().doubleValue();
        return BlockPlacementOptions.defaults()
                .withRange(range)
                .withWallRange(range)
                .withConstructFailResult(false)
                .withConsiderFacingAwayFaces(true);
    }

    private RotationApplyMode getActiveApplyMode() {
        return this.getApplyMode();
    }

    private boolean isSilentRotation() {
        return this.rotationMode.is("Silent");
    }

    private double getCurrentRotationSpeed() {
        return Math.max(0.0, (this.resetting ? this.snapbackSpeed : this.speed).getValue().doubleValue());
    }

    private boolean canPlaceThrough(BlockPos pos) {
        if (mc.level == null || pos == null || mc.level.isOutsideBuildHeight(pos)) {
            return false;
        }
        BlockState state = mc.level.getBlockState(pos);
        Block block = state.getBlock();
        return state.isAir()
                || state.canBeReplaced()
                || block instanceof LiquidBlock
                || block instanceof FireBlock;
    }

    private boolean isBlockUnderPlayer(BlockPos blockPos, Vec3 pos) {
        if (blockPos.getY() >= Mth.floor(pos.y)) {
            return false;
        }
        for (double[] corner : CORNERS) {
            int x = Mth.floor(pos.x + corner[0]);
            int z = Mth.floor(pos.z + corner[1]);
            if (blockPos.getX() == x && blockPos.getZ() == z) {
                return true;
            }
        }
        return false;
    }

    private boolean willFallFar(double minFall) {
        PredictionState prediction = PredictionState.fromPlayer();
        double startY = mc.player.getY();
        for (int tick = 0; tick < FALL_PREDICT_TICKS; tick++) {
            prediction.tick(false);
            if (this.hasGroundAt(prediction.pos(), 0.8)) {
                return false;
            }
            if (startY - prediction.pos().y > minFall) {
                return true;
            }
        }
        return false;
    }

    private boolean willFallSoon() {
        PredictionState prediction = PredictionState.fromPlayer();
        for (int tick = 0; tick < 10; tick++) {
            prediction.tick(true);
            if (!this.hasGroundAt(prediction.pos(), 0.8) && prediction.motionY() < 0.0) {
                return true;
            }
        }
        return false;
    }

    private Vec3 predictFuturePosition(int ticks) {
        PredictionState prediction = PredictionState.fromPlayer();
        Vec3 start = prediction.pos();
        double maxDrop = Math.max(6.0, this.minimumFallDistance.getValue().doubleValue());
        for (int tick = 0; tick < ticks; tick++) {
            prediction.tick(false);
            if (start.y - prediction.pos().y > maxDrop || this.hasGroundAt(prediction.pos(), 0.35)) {
                break;
            }
        }
        return prediction.pos();
    }

    private boolean hasGroundAt(Vec3 pos, double maxDistance) {
        if (mc.level == null || mc.player == null) {
            return true;
        }
        double half = Math.max(0.1, mc.player.getBbWidth() * 0.5 - 0.03);
        Vec3[] probes = new Vec3[]{
                pos,
                pos.add(half, 0.0, half),
                pos.add(-half, 0.0, half),
                pos.add(half, 0.0, -half),
                pos.add(-half, 0.0, -half)
        };
        for (Vec3 probe : probes) {
            Vec3 end = probe.add(0.0, -maxDistance, 0.0);
            HitResult hit = mc.level.clip(new ClipContext(
                    probe, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.BLOCK) {
                return true;
            }
        }
        return false;
    }

    private int findBestHotbarBlockSlot() {
        if (mc.player == null) {
            return -1;
        }
        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;
        int bestCount = -1;
        for (int slot = 8; slot >= 0; slot--) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof BlockItem blockItem) || !BlockUtil.isPlaceable(stack)) {
                continue;
            }
            int score = this.getBlockScore(blockItem.getBlock());
            if (score > bestScore || (score == bestScore && stack.getCount() > bestCount)) {
                bestScore = score;
                bestCount = stack.getCount();
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private int getBlockScore(Block block) {
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
        int best = 1;
        for (Map.Entry<String, Integer> entry : BLOCK_SCORE.entrySet()) {
            if (path.equals(entry.getKey()) || path.endsWith("_" + entry.getKey())
                    || path.contains(entry.getKey())) {
                best = Math.max(best, entry.getValue());
            }
        }
        return best;
    }

    private boolean isHotbarPlaceableBlock(int slot) {
        if (mc.player == null || slot < 0 || slot > 8) {
            return false;
        }
        ItemStack stack = mc.player.getInventory().getItem(slot);
        return stack.getItem() instanceof BlockItem && BlockUtil.isPlaceable(stack);
    }

    private boolean isDifferentTarget(BlockPlacementTarget first, BlockPlacementTarget second) {
        if (first == second) {
            return false;
        }
        if (first == null || second == null) {
            return true;
        }
        return !first.interactedBlockPos().equals(second.interactedBlockPos())
                || !first.placedBlockPos().equals(second.placedBlockPos())
                || first.facing() != second.facing();
    }

    private Rotation getBaseRotation() {
        Rotation smoothed = RotationHandler.getSmoothedRotation(this);
        if (smoothed != null) {
            return smoothed;
        }
        if (RotationHandler.targetRotation != null) {
            return RotationHandler.targetRotation;
        }
        return new Rotation(mc.player.getYRot(), mc.player.getXRot());
    }

    private Rotation rotationToPoint(Vec3 eye, double x, double y, double z) {
        double dx = x - eye.x;
        double dy = y - eye.y;
        double dz = z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new Rotation(Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90.0f, 90.0f));
    }

    private Rotation unwrapRotation(Rotation rotation, Rotation reference) {
        return new Rotation(
                reference.getYaw() + Mth.wrapDegrees(rotation.getYaw() - reference.getYaw()),
                Mth.clamp(rotation.getPitch(), -90.0f, 90.0f));
    }

    private double rotationCost(Rotation base, Rotation target) {
        return Math.abs(Mth.wrapDegrees(target.getYaw() - base.getYaw()))
                + Math.abs(target.getPitch() - base.getPitch());
    }

    private double distToBlockAabb(Vec3 point, BlockPos blockPos) {
        double x = Mth.clamp(point.x, blockPos.getX(), blockPos.getX() + 1.0);
        double y = Mth.clamp(point.y, blockPos.getY(), blockPos.getY() + 1.0);
        double z = Mth.clamp(point.z, blockPos.getZ(), blockPos.getZ() + 1.0);
        double dx = point.x - x;
        double dy = point.y - y;
        double dz = point.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private double randomRange(double min, double max) {
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    private double clamp01(double value) {
        return Mth.clamp(value, 0.0, 1.0);
    }

    private boolean shouldLogConflicts() {
        return this.debug.getValue()
                && ((GodBridgeAssist.INSTANCE != null && GodBridgeAssist.INSTANCE.isEnabled())
                || (BlockIn.INSTANCE != null && BlockIn.INSTANCE.isEnabled())
                || (AutoMLG.INSTANCE != null && AutoMLG.INSTANCE.isEnabled())
                || (AntiVoid.INSTANCE != null && AntiVoid.INSTANCE.isEnabled())
                || (AutoWebPlace.INSTANCE != null && AutoWebPlace.INSTANCE.isEnabled()));
    }

    private void debugLog(String phase) {
        if (!this.debug.getValue() || mc.player == null) {
            return;
        }
        String state = phase
                + " aim=" + this.hasAim
                + " reset=" + this.resetting
                + " placing=" + this.placing
                + " auto=" + this.autoClutchActive
                + " blocks=" + this.clutchBlocksPlaced
                + " airStuck=" + this.airStuckController.windowTicks()
                + "/" + ClutchAirStuckController.WINDOW_TICKS
                + " airStuckActive=" + this.airStuckController.isActive()
                + " velTicks=" + this.receivedVelocityTicks
                + " vel=" + this.formatVec3(this.lastReceivedVelocity)
                + " hold=" + this.airStuckController.remainingHoldTicks()
                + " target=" + this.formatTarget(this.currentTarget)
                + " rot=" + this.formatRotation(this.currentRotation)
                + " actualRot=" + this.formatRotation(RotationHandler.getActualServerRotation())
                + " logicalRot=" + this.formatRotation(RotationHandler.getLogicalServerRotation())
                + " slot=" + this.plannedSlot + "/" + this.plannedHand
                + " cooldown=" + this.cooldownTicks;
        this.debugTicks++;
        int interval = Math.max(1, this.debugInterval.getValue().intValue());
        if (state.equals(this.lastDebugState) && this.debugTicks % interval != 0) {
            return;
        }
        this.lastDebugState = state;
        String line = "[ClutchDebug] tick=" + mc.player.tickCount + " " + state;
        logger.info(line);
        ChatUtil.print(line);
    }

    private String formatTarget(BlockPlacementTarget target) {
        if (target == null) {
            return "null";
        }
        return this.formatBlockPos(target.placedBlockPos())
                + " support=" + this.formatBlockPos(target.interactedBlockPos())
                + " face=" + target.facing();
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

    private String formatVec3(Vec3 vec) {
        if (vec == null) {
            return "null";
        }
        return String.format(Locale.US, "%.2f,%.2f,%.2f", vec.x, vec.y, vec.z);
    }

    private boolean isPhysicalKeyDown(KeyMapping keyMapping) {
        if (mc.getWindow() == null || keyMapping == null) {
            return false;
        }
        InputConstants.Key key = keyMapping.getKey();
        long window = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, key.getValue());
    }

    private void setKeyDown(KeyMapping keyMapping, boolean down) {
        if (keyMapping == null) {
            return;
        }
        KeyMapping.set(keyMapping.getKey(), down);
        keyMapping.setDown(down);
    }

    private record SlotSelection(int hotbarSlot, InteractionHand hand, boolean restoreAfterPlace) {
        ItemStack itemStack() {
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

    private record BlockCandidate(double score, BlockPos pos) {
    }

    private record RotationCandidate(double cost, Rotation rotation) {
    }

    private record AimResult(BlockPlacementTarget target, Rotation rotation) {
    }

    private static final class PredictionState {
        private double x;
        private double y;
        private double z;
        private double motionX;
        private double motionY;
        private double motionZ;

        static PredictionState fromPlayer() {
            PredictionState state = new PredictionState();
            state.x = mc.player.getX();
            state.y = mc.player.getY();
            state.z = mc.player.getZ();
            state.motionX = mc.player.getDeltaMovement().x;
            state.motionY = mc.player.getDeltaMovement().y;
            state.motionZ = mc.player.getDeltaMovement().z;
            return state;
        }

        void tick(boolean stopHorizontal) {
            if (stopHorizontal) {
                this.motionX = 0.0;
                this.motionZ = 0.0;
            }
            this.motionY -= 0.08;
            this.x += this.motionX;
            this.y += this.motionY;
            this.z += this.motionZ;
            this.motionY *= 0.9800000190734863;
            this.motionX *= 0.91;
            this.motionZ *= 0.91;
        }

        Vec3 pos() {
            return new Vec3(this.x, this.y, this.z);
        }

        double motionY() {
            return this.motionY;
        }
    }
}
