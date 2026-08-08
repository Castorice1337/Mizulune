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
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
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
import shit.zen.utils.misc.PacketUtil;
import shit.zen.utils.rotation.Rotation;
import shit.zen.utils.rotation.RotationApplyMode;
import shit.zen.utils.rotation.RotationHandler;
import shit.zen.utils.rotation.RotationProvider;
import shit.zen.utils.rotation.SmoothMode;
import shit.zen.value.NumericRange;
import shit.zen.value.Value;
import shit.zen.value.ValueGroup;
import shit.zen.value.ValueType;
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

    public final ModeValue activationMode = new ModeValue("Activation Mode", "Strict", "Always")
            .withDefault("Strict");
    public final BooleanValue autoClutch = new BooleanValue("Auto Clutch", false);
    public final NumberValue minimumFallDistance = new NumberValue("Minimum Fall Distance", 10, 3, 20, 1,
            () -> this.autoClutch.getValue() || this.activationMode.is("Strict"));
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
    public final Value<NumericRange> horizontalTurnSpeed = new Value<>(
            "horizontal_turn_speed",
            "Horizontal Turn Speed",
            "",
            new NumericRange(8.0, 8.0, 0.0, 180.0, 0.1, false),
            ValueType.DECIMAL_RANGE).visibleWhen(() -> !this.smoothMode.is("SNAP"));
    public final Value<NumericRange> verticalTurnSpeed = new Value<>(
            "vertical_turn_speed",
            "Vertical Turn Speed",
            "",
            new NumericRange(8.0, 8.0, 0.0, 180.0, 0.1, false),
            ValueType.DECIMAL_RANGE).visibleWhen(() -> !this.smoothMode.is("SNAP"));
    public final Value<NumericRange> resetTurnSpeed = new Value<>(
            "reset_turn_speed",
            "Reset Turn Speed",
            "",
            new NumericRange(12.0, 12.0, 0.0, 180.0, 0.1, false),
            ValueType.DECIMAL_RANGE).visibleWhen(() -> !this.smoothMode.is("SNAP"));
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
    private boolean velocityRescueArmed;
    private boolean velocityAirborneSeen;
    private Vec3 lastReceivedVelocity = Vec3.ZERO;
    private int lastReceivedVelocityTick = -1;
    private String lastVelocitySource = "none";
    private FallRiskAssessment lastRiskAssessment = FallRiskAssessment.unchecked();
    private FallRiskAssessment latchedRiskAssessment = FallRiskAssessment.unchecked();
    private boolean strictRiskLatched;
    private String activationGate = "reset";
    private final ClutchAirStuckController airStuckController;

    public Clutch() {
        super("clutch", "Clutch", Category.PLAYER);
        this.airStuckController = new ClutchAirStuckController(this::debugLog);
        INSTANCE = this;
    }

    /** True while Clutch owns server rotation or its position hold is active. */
    public boolean isActivelyRescuing() {
        boolean predictiveRescue = this.strictRiskLatched
                && this.isEnabled()
                && mc.player != null
                && !mc.player.onGround()
                && RotationHandler.isActiveRotationOwner(this)
                && this.hasAim
                && this.currentTarget != null
                && !this.resetting;
        if (predictiveRescue) {
            return true;
        }
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
        if (!enabled || onGround) {
            return false;
        }
        if (airStuckActive) {
            return verticalVelocity <= 0.01;
        }
        return verticalVelocity < 0.0
                && activeRotationOwner
                && hasAim
                && hasTarget
                && !resetting;
    }

    @Override
    protected void configureValueTree(ValueGroup root) {
        ValueGroup activation = root.group("activation", "Activation");
        activation.add(this.activationMode);
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
        rotation.add(this.horizontalTurnSpeed);
        rotation.add(this.verticalTurnSpeed);
        rotation.add(this.resetTurnSpeed);
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

        this.updateVelocityRescueArm();
        if (mc.player.onGround()) {
            this.clutchBlocksPlaced = 0;
            this.lastPlacedBlock = null;
            this.lastSupportBlock = null;
            this.clearStrictRiskLatch();
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

        boolean retainedAim = false;
        AimResult aim = null;
        if ((this.airStuckController.isActive() || this.strictRiskLatched)
                && this.canRetainCurrentAim(slot.itemStack())) {
            aim = new AimResult(this.currentTarget, this.currentRotation);
            retainedAim = true;
        }
        if (aim == null) {
            aim = this.findClutchAim(slot.itemStack());
        }
        if (aim == null) {
            if (this.canRetainCurrentAim(slot.itemStack())) {
                aim = new AimResult(this.currentTarget, this.currentRotation);
                retainedAim = true;
            } else {
                this.clearAim(true);
                this.disablePlacing(false);
                this.airStuckController.reset("no-target");
                this.debugLog("no-aim");
                return;
            }
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
        Rotation placementRotation = this.resolvePlacementRotation(false);
        boolean placementReady = this.cooldownTicks <= 0
                && this.canPlaceMoreBlocks()
                && placementRotation != null;
        this.airStuckController.update(new ClutchAirStuckController.UpdateInput(
                this.airStuck.getValue(),
                true,
                this.currentTarget,
                this.getLastAppliedRotation(),
                slot.itemStack(),
                this.getPlacementOptions(),
                placementReady,
                this.canPlaceMoreBlocks()));
        this.debugLog(retainedAim ? "aim:retained" : "aim");
    }

    @EventTarget(value = EventPriority.HIGHEST)
    public void onPacket(PacketEvent event) {
        if (mc.player == null || event.getPacket() == null || !event.isIncoming()) {
            return;
        }
        Vec3 velocity;
        String source;
        if (event.getPacket() instanceof ClientboundSetEntityMotionPacket motion) {
            if (motion.getId() != mc.player.getId()) {
                return;
            }
            velocity = new Vec3(
                    motion.getXa() / 8000.0,
                    motion.getYa() / 8000.0,
                    motion.getZa() / 8000.0);
            source = "entity-motion";
        } else if (event.getPacket() instanceof ClientboundExplodePacket explosion) {
            velocity = new Vec3(
                    explosion.getKnockbackX(),
                    explosion.getKnockbackY(),
                    explosion.getKnockbackZ());
            source = "explosion";
        } else {
            return;
        }

        double horizontal = this.horizontalLength(velocity);
        double threshold = Math.max(0.0, this.minimumVelocity.getValue().doubleValue());
        if (horizontal < threshold && velocity.length() < threshold) {
            return;
        }

        this.armVelocityRescue(velocity, source);
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
        if (this.activationMode.is("Strict")
                && this.strictRiskLatched
                && mc.player.getDeltaMovement().y > 0.01) {
            this.suppressControlledInputs();
            this.debugLog("place:strict-preaim");
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
        SlotSelection slot = this.findSlot();
        if (slot == null) {
            this.airStuckController.reset();
            this.debugLog("place:no-slot");
            return;
        }
        this.equipSlot(slot);

        if (!this.isPlacementSpaceClear(this.currentTarget.placedBlockPos())) {
            this.airStuckController.reset("player-target-intersection");
            this.clearAim(true);
            this.disablePlacing(false);
            this.suppressControlledInputs();
            this.debugLog("place:player-target-intersection");
            return;
        }

        Rotation placeRotation = this.resolvePlacementRotation(true);
        if (placeRotation == null) {
            this.suppressControlledInputs();
            this.debugLog("place:wait-rotation");
            return;
        }
        if (!this.canPlaceMoreBlocks()) {
            this.airStuckController.reset();
            this.debugLog("place:max-blocks");
            return;
        }

        BlockPlacementUtil.PlacementResult result = BlockPlacementUtil.placeDetailed(
                this.currentTarget, slot.hand(), placeRotation, slot.itemStack(), this.getPlacementOptions());
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
        return sampleTurnSpeed((this.resetting ? this.resetTurnSpeed : this.horizontalTurnSpeed).getValue());
    }

    @Override
    public double getMaxPitchSpeed() {
        return sampleTurnSpeed((this.resetting ? this.resetTurnSpeed : this.verticalTurnSpeed).getValue());
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
        this.velocityRescueArmed = false;
        this.velocityAirborneSeen = false;
        this.lastReceivedVelocity = Vec3.ZERO;
        this.lastReceivedVelocityTick = -1;
        this.lastVelocitySource = "none";
        this.lastRiskAssessment = FallRiskAssessment.unchecked();
        this.latchedRiskAssessment = FallRiskAssessment.unchecked();
        this.strictRiskLatched = false;
        this.activationGate = "reset";
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
        if (mc.player == null || mc.level == null) {
            this.activationGate = "missing-context";
            this.lastRiskAssessment = FallRiskAssessment.unchecked();
            return false;
        }
        if (mc.screen != null) {
            this.activationGate = "gui";
            return false;
        }
        if (mc.player.getAbilities().flying || mc.player.isFallFlying()
                || mc.player.isInWaterRainOrBubble() || mc.player.isInLava()) {
            this.activationGate = "invalid-movement-state";
            this.clearStrictRiskLatch();
            this.lastRiskAssessment = FallRiskAssessment.unchecked();
            return false;
        }
        boolean airStuckActive = this.airStuckController.isActive();
        if (this.onlyByReceiveVelocity.getValue()
                && !airStuckActive
                && !this.hasRecentReceivedVelocity()) {
            this.activationGate = "no-velocity-transaction";
            this.lastRiskAssessment = FallRiskAssessment.unchecked();
            return false;
        }

        if (airStuckActive) {
            boolean active = isRescueActivation(
                    false,
                    false,
                    true,
                    mc.player.onGround(),
                    mc.player.getDeltaMovement().y);
            if (!active) {
                this.activationGate = "air-stuck-motion-ended";
                return false;
            }
            this.activationGate = "air-stuck-transaction";
            return true;
        }

        if (this.activationMode.is("Strict")) {
            if (mc.player.onGround()) {
                this.activationGate = "grounded";
                this.lastRiskAssessment = FallRiskAssessment.unchecked();
                return false;
            }
            FallRiskAssessment currentRisk = this.assessFallRisk(
                    this.minimumFallDistance.getValue().doubleValue());
            if (currentRisk.requiresRescue()) {
                this.latchStrictRisk(currentRisk);
            } else if (this.strictRiskLatched && this.clutchBlocksPlaced > 0) {
                this.clearStrictRiskLatch();
                this.lastRiskAssessment = currentRisk;
            } else if (this.strictRiskLatched) {
                this.lastRiskAssessment = this.latchedRiskAssessment;
            } else {
                this.lastRiskAssessment = currentRisk;
            }
            if (!strictRescueEligible(
                    mc.player.onGround(),
                    currentRisk,
                    this.strictRiskLatched)) {
                this.activationGate = "risk-" + currentRisk.risk().name().toLowerCase(Locale.ROOT);
                return false;
            }
            this.activationGate = mc.player.getDeltaMovement().y > 0.01
                    ? "strict-preaim"
                    : "strict-rescue";
            return true;
        }

        if (this.strictRiskLatched) {
            this.clearStrictRiskLatch();
        }

        boolean active = isRescueActivation(
                this.isManualClutchContext(),
                this.autoClutchActive,
                false,
                mc.player.onGround(),
                mc.player.getDeltaMovement().y);
        if (!active) {
            this.activationGate = "always-wait-downward";
            this.lastRiskAssessment = FallRiskAssessment.unchecked();
            return false;
        }

        this.lastRiskAssessment = FallRiskAssessment.always();
        BlockPos below = BlockPos.containing(mc.player.getX(), mc.player.getY() - 1.0, mc.player.getZ());
        boolean centerOpen = this.canPlaceThrough(below);
        this.activationGate = centerOpen ? "always-rescue" : "always-center-supported";
        return centerOpen;
    }

    static boolean strictRescueEligible(
            boolean onGround,
            FallRiskAssessment currentRisk,
            boolean riskLatched) {
        return !onGround
                && (riskLatched || currentRisk != null && currentRisk.requiresRescue());
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
            boolean airStuckActive,
            boolean onGround,
            double verticalVelocity) {
        if (onGround) {
            return false;
        }
        if (airStuckActive) {
            return verticalVelocity <= 0.01;
        }
        return (manualActive || autoActive) && verticalVelocity < 0.0;
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
            futurePos = this.predictFuturePosition(
                    FUTURE_POSITION_TICKS,
                    this.airStuckController.planningMotion(mc.player.getDeltaMovement()));
        }

        SearchBounds bounds = ravenSearchBounds(playerPos);

        List<BlockCandidate> candidates = new ArrayList<>();
        for (int y = bounds.maxY(); y >= bounds.minY(); y--) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    BlockPos support = new BlockPos(x, y, z);
                    if (!BlockPlacementUtil.isValidSupport(support)) {
                        continue;
                    }
                    double currentDistance = this.distToBlockAabb(playerPos, support);
                    double futureDistance = this.distToBlockAabb(futurePos, support);
                    double score = ravenCandidateScore(
                            currentDistance,
                            futureDistance,
                            this.simulateFuturePosition.getValue());
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

    private boolean canRetainCurrentAim(ItemStack stack) {
        if (!this.hasAim
                || this.currentTarget == null
                || this.currentRotation == null
                || stack == null
                || stack.isEmpty()) {
            return false;
        }
        BlockPlacementOptions options = this.getPlacementOptions();
        return this.isPlacementSpaceClear(this.currentTarget.placedBlockPos())
                && BlockPlacementUtil.isValidPlacementTarget(this.currentTarget, stack, options)
                && BlockPlacementUtil.rayTraceTarget(
                this.currentRotation,
                this.currentTarget,
                options,
                false) != null;
    }

    static SearchBounds ravenSearchBounds(Vec3 playerPos) {
        int feetX = Mth.floor(playerPos.x);
        int feetY = Mth.floor(playerPos.y);
        int feetZ = Mth.floor(playerPos.z);
        return new SearchBounds(
                feetX - 5,
                feetX + 4,
                feetY - 4,
                feetY - 1,
                feetZ - 5,
                feetZ + 4);
    }

    static double ravenCandidateScore(
            double currentDistance,
            double futureDistance,
            boolean simulateFuturePosition) {
        return simulateFuturePosition
                ? currentDistance * 0.3 + futureDistance * 0.7
                : currentDistance;
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
            if (!BlockPlacementUtil.canReplace(placedBlock, stack)
                    || !this.isPlacementSpaceClear(placedBlock)) {
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
        return this.velocityRescueArmed;
    }

    private void armVelocityRescue(Vec3 velocity, String source) {
        this.lastReceivedVelocity = velocity == null ? Vec3.ZERO : velocity;
        this.lastReceivedVelocityTick = mc.player.tickCount;
        this.lastVelocitySource = source == null ? "unknown" : source;
        this.receivedVelocityTicks = Math.max(1, this.velocityWindow.getValue().intValue());
        this.velocityRescueArmed = true;
        this.velocityAirborneSeen = !mc.player.onGround();
        this.autoClutchChecking = true;
        this.autoClutchCheckCounter = 0;
        this.autoClutchLandedGuard = false;
        this.activationGate = "velocity-received";
        this.debugLog("velocity:" + this.lastVelocitySource);
    }

    private void updateVelocityRescueArm() {
        VelocityArmState previous = new VelocityArmState(
                this.velocityRescueArmed,
                this.velocityAirborneSeen,
                this.receivedVelocityTicks);
        VelocityArmState next = advanceVelocityArm(previous, mc.player.onGround());
        this.velocityRescueArmed = next.armed();
        this.velocityAirborneSeen = next.airborneSeen();
        this.receivedVelocityTicks = next.remainingTicks();
        if (previous.armed() && !next.armed()) {
            this.debugLog(previous.airborneSeen()
                    ? "velocity:landed-release"
                    : "velocity:takeoff-timeout");
        }
    }

    static VelocityArmState advanceVelocityArm(VelocityArmState state, boolean onGround) {
        if (state == null || !state.armed()) {
            return new VelocityArmState(false, false, 0);
        }
        if (!onGround) {
            return new VelocityArmState(
                    true,
                    true,
                    Math.max(1, state.remainingTicks()));
        }
        if (state.airborneSeen()) {
            return new VelocityArmState(false, false, 0);
        }
        int remaining = Math.max(0, state.remainingTicks() - 1);
        return new VelocityArmState(remaining > 0, false, remaining);
    }

    private void latchStrictRisk(FallRiskAssessment assessment) {
        if (assessment == null || !assessment.requiresRescue()) {
            return;
        }
        this.strictRiskLatched = true;
        this.latchedRiskAssessment = assessment;
        this.lastRiskAssessment = assessment;
    }

    private void clearStrictRiskLatch() {
        this.strictRiskLatched = false;
        this.latchedRiskAssessment = FallRiskAssessment.unchecked();
        this.lastRiskAssessment = FallRiskAssessment.unchecked();
    }

    private double horizontalLength(Vec3 vec) {
        if (vec == null) {
            return 0.0;
        }
        return Math.sqrt(vec.x * vec.x + vec.z * vec.z);
    }

    private Rotation resolvePlacementRotation(boolean allowAirStuckCommit) {
        if (this.currentTarget == null || this.currentRotation == null || mc.player == null) {
            return null;
        }

        Rotation candidate = this.getApplyMode() == RotationApplyMode.OFF
                ? new Rotation(mc.player.getYRot(), mc.player.getXRot())
                : RotationHandler.getSmoothedRotation(this);
        BlockPlacementOptions options = this.getPlacementOptions();
        if (candidate == null
                || BlockPlacementUtil.rayTraceTarget(
                candidate,
                this.currentTarget,
                options,
                false) == null) {
            return null;
        }

        Rotation serverRotation = RotationHandler.getActualServerRotation();
        if (serverRotation == null) {
            serverRotation = RotationHandler.getLogicalServerRotation();
        }
        boolean serverReady = isWithinRavenRotationWindow(
                candidate,
                serverRotation,
                this.rotationTolerance.getValue().doubleValue())
                && BlockPlacementUtil.rayTraceTarget(
                serverRotation,
                this.currentTarget,
                options,
                false) != null;
        if (serverReady) {
            return serverRotation;
        }
        if (!this.airStuckController.isActive()) {
            return this.getApplyMode() == RotationApplyMode.OFF ? candidate : null;
        }
        if (!allowAirStuckCommit) {
            return null;
        }
        return this.commitAirStuckRotation(candidate, options);
    }

    private Rotation commitAirStuckRotation(
            Rotation candidate,
            BlockPlacementOptions options) {
        if (candidate == null || options == null || mc.player == null || mc.getConnection() == null) {
            return null;
        }
        Rotation packetRotation = RotationHandler.toServerPacketRotation(this, candidate);
        if (packetRotation == null
                || BlockPlacementUtil.rayTraceTarget(
                packetRotation,
                this.currentTarget,
                options,
                false) == null) {
            return null;
        }
        PacketUtil.sendQueued(new ServerboundMovePlayerPacket.Rot(
                packetRotation.getYaw(),
                packetRotation.getPitch(),
                mc.player.onGround()));
        this.debugLog("air-stuck:rotation-commit");
        return packetRotation;
    }

    static boolean isWithinRavenRotationWindow(
            Rotation candidate,
            Rotation serverRotation,
            double tolerance) {
        return candidate != null
                && serverRotation != null
                && BlockPlacementUtil.rotationDistance(candidate, serverRotation)
                <= Math.max(0.0, tolerance);
    }

    private Rotation getLastAppliedRotation() {
        if (this.getApplyMode() == RotationApplyMode.OFF) {
            return mc.player == null
                    ? null
                    : new Rotation(mc.player.getYRot(), mc.player.getXRot());
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

    static double sampleTurnSpeed(NumericRange range) {
        if (range == null) {
            return 0.0;
        }
        double lower = Math.max(0.0, range.lower());
        double upper = Math.max(lower, range.upper());
        return upper <= lower
                ? lower
                : ThreadLocalRandom.current().nextDouble(lower, upper);
    }

    private boolean isPlacementSpaceClear(BlockPos placedBlock) {
        if (mc.player == null || placedBlock == null) {
            return false;
        }
        AABB targetBox = new AABB(placedBlock);
        return !intersectsPlacementBox(mc.player.getBoundingBox(), placedBlock)
                && !this.airStuckController.intersectsAnchor(targetBox);
    }

    static boolean intersectsPlacementBox(AABB playerBox, BlockPos placedBlock) {
        return playerBox == null
                || placedBlock == null
                || playerBox.intersects(new AABB(placedBlock));
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
        return this.assessFallRisk(minFall).requiresRescue();
    }

    private FallRiskAssessment assessFallRisk(double minFall) {
        if (mc.player == null || mc.level == null) {
            return FallRiskAssessment.unchecked();
        }
        PredictionState prediction = PredictionState.fromPlayerWithInput(mc.player.getDeltaMovement());
        double startY = mc.player.getY();
        double predictedFall = Math.max(0.0, mc.player.fallDistance);
        double threshold = Math.max(0.0, minFall);
        for (int tick = 1; tick <= FALL_PREDICT_TICKS; tick++) {
            prediction.tick(false);
            predictedFall = Math.max(
                    predictedFall,
                    Math.max(0.0, mc.player.fallDistance) + Math.max(0.0, startY - prediction.pos().y));
            if (prediction.box().maxY < mc.level.getMinBuildHeight()) {
                return classifyFallRisk(false, true, predictedFall, threshold, -1);
            }
            if (mc.level.getFluidState(BlockPos.containing(prediction.pos())).is(FluidTags.WATER)) {
                return new FallRiskAssessment(FallRisk.SAFE, predictedFall, tick);
            }
            if (prediction.onGround()) {
                return classifyFallRisk(true, false, predictedFall, threshold, tick);
            }
        }
        return classifyFallRisk(false, false, predictedFall, threshold, -1);
    }

    static FallRiskAssessment classifyFallRisk(
            boolean landed,
            boolean belowWorld,
            double predictedFall,
            double minimumFall,
            int landingTick) {
        double fall = Math.max(0.0, predictedFall);
        if (belowWorld || !landed) {
            return new FallRiskAssessment(FallRisk.VOID, fall, -1);
        }
        if (fall >= Math.max(0.0, minimumFall)) {
            return new FallRiskAssessment(FallRisk.HIGH_FALL, fall, landingTick);
        }
        return new FallRiskAssessment(FallRisk.SAFE, fall, landingTick);
    }

    private boolean willFallSoon() {
        PredictionState prediction = PredictionState.fromPlayer(mc.player.getDeltaMovement());
        for (int tick = 0; tick < 10; tick++) {
            prediction.tick(true);
            if (!prediction.onGround() && prediction.motionY() < 0.0) {
                return true;
            }
        }
        return false;
    }

    private Vec3 predictFuturePosition(int ticks, Vec3 initialMotion) {
        PredictionState prediction = PredictionState.fromPlayer(initialMotion);
        Vec3 start = prediction.pos();
        for (int tick = 0; tick < ticks; tick++) {
            prediction.tick(false);
            if (shouldStopRavenFuturePrediction(start.y, prediction.pos().y, prediction.onGround())) {
                break;
            }
        }
        return prediction.pos();
    }

    static boolean shouldStopRavenFuturePrediction(
            double startY,
            double predictedY,
            boolean onGround) {
        return onGround || predictedY < startY - 2.0;
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
                + " activation=" + this.activationMode.getValue()
                + " gate=" + this.activationGate
                + " risk=" + this.lastRiskAssessment.risk()
                + " riskLatched=" + this.strictRiskLatched
                + " predictedFall=" + String.format(
                Locale.US, "%.2f", this.lastRiskAssessment.predictedFall())
                + " landingTick=" + this.lastRiskAssessment.landingTick()
                + " blocks=" + this.clutchBlocksPlaced
                + " airStuck=" + this.airStuckController.windowTicks()
                + "/" + ClutchAirStuckController.WINDOW_TICKS
                + " airStuckActive=" + this.airStuckController.isActive()
                + " velTicks=" + this.receivedVelocityTicks
                + " velArmed=" + this.velocityRescueArmed
                + " velAirborne=" + this.velocityAirborneSeen
                + " velSource=" + this.lastVelocitySource
                + " velAge=" + (this.lastReceivedVelocityTick < 0
                ? -1
                : mc.player.tickCount - this.lastReceivedVelocityTick)
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
        InputConstants.Key key = keyMapping.key;
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
        KeyMapping.set(keyMapping.key, down);
        keyMapping.setDown(down);
    }

    enum FallRisk {
        UNCHECKED,
        ALWAYS,
        SAFE,
        HIGH_FALL,
        VOID
    }

    record FallRiskAssessment(FallRisk risk, double predictedFall, int landingTick) {
        static FallRiskAssessment unchecked() {
            return new FallRiskAssessment(FallRisk.UNCHECKED, 0.0, -1);
        }

        static FallRiskAssessment always() {
            return new FallRiskAssessment(FallRisk.ALWAYS, 0.0, -1);
        }

        boolean requiresRescue() {
            return this.risk == FallRisk.HIGH_FALL || this.risk == FallRisk.VOID;
        }
    }

    record VelocityArmState(boolean armed, boolean airborneSeen, int remainingTicks) {
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

    record SearchBounds(
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ) {
    }

    private record RotationCandidate(double cost, Rotation rotation) {
    }

    private record AimResult(BlockPlacementTarget target, Rotation rotation) {
    }

    private static final class PredictionState {
        private AABB box;
        private double motionX;
        private double motionY;
        private double motionZ;
        private boolean onGround;
        private boolean simulateInput;
        private float inputStrafe;
        private float inputForward;
        private float inputYaw;
        private float airAcceleration;

        static PredictionState fromPlayer(Vec3 initialMotion) {
            PredictionState state = new PredictionState();
            Vec3 motion = initialMotion == null ? mc.player.getDeltaMovement() : initialMotion;
            state.box = mc.player.getBoundingBox();
            state.motionX = motion.x;
            state.motionY = motion.y;
            state.motionZ = motion.z;
            state.onGround = mc.player.onGround();
            return state;
        }

        static PredictionState fromPlayerWithInput(Vec3 initialMotion) {
            PredictionState state = fromPlayer(initialMotion);
            state.simulateInput = true;
            state.inputStrafe = mc.player.xxa;
            state.inputForward = mc.player.zza;
            state.inputYaw = mc.player.getYRot();
            state.airAcceleration = mc.player.isSprinting() ? 0.026f : 0.02f;
            return state;
        }

        void tick(boolean stopHorizontal) {
            if (stopHorizontal) {
                this.motionX = 0.0;
                this.motionZ = 0.0;
            } else if (this.simulateInput) {
                this.applyAirControl();
            }
            this.motionY -= 0.08;
            this.move(this.motionX, this.motionY, this.motionZ);
            this.motionY *= 0.9800000190734863;
            this.motionX *= 0.91;
            this.motionZ *= 0.91;
        }

        private void applyAirControl() {
            double lengthSquared = this.inputStrafe * this.inputStrafe
                    + this.inputForward * this.inputForward;
            if (lengthSquared < 1.0E-7) {
                return;
            }
            double normalization = lengthSquared > 1.0
                    ? this.airAcceleration / Math.sqrt(lengthSquared)
                    : this.airAcceleration;
            double strafe = this.inputStrafe * normalization;
            double forward = this.inputForward * normalization;
            double radians = this.inputYaw * Math.PI / 180.0;
            double sinYaw = Math.sin(radians);
            double cosYaw = Math.cos(radians);
            this.motionX += strafe * cosYaw - forward * sinYaw;
            this.motionZ += forward * cosYaw + strafe * sinYaw;
        }

        private void move(double x, double y, double z) {
            Vec3 requested = new Vec3(x, y, z);
            List<VoxelShape> entityCollisions = mc.level.getEntityCollisions(
                    mc.player,
                    this.box.expandTowards(requested));
            Vec3 resolved = Entity.collideBoundingBox(
                    mc.player,
                    requested,
                    this.box,
                    mc.level,
                    entityCollisions);
            this.box = this.box.move(resolved);
            this.onGround = Double.compare(y, resolved.y) != 0 && y < 0.0;
            if (Double.compare(x, resolved.x) != 0) {
                this.motionX = 0.0;
            }
            if (Double.compare(y, resolved.y) != 0) {
                this.motionY = 0.0;
            }
            if (Double.compare(z, resolved.z) != 0) {
                this.motionZ = 0.0;
            }
        }

        Vec3 pos() {
            return new Vec3(
                    (this.box.minX + this.box.maxX) * 0.5,
                    this.box.minY,
                    (this.box.minZ + this.box.maxZ) * 0.5);
        }

        AABB box() {
            return this.box;
        }

        double motionY() {
            return this.motionY;
        }

        boolean onGround() {
            return this.onGround;
        }
    }
}
