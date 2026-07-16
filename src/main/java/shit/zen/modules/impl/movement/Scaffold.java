/*
 * This file is part of Mizulune/OpenZen.
 *
 * Scaffold orchestration, movement correction integration, ordinary Telly,
 * stabilization, and sprint control are adapted from LiquidBounce:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * New Telly behavior is adapted from OpenSSNG Scaffold by Un4nown,
 * licensed under MIT; see liquidSRC/OpenSSNGScaffoldAndClutch-main/LICENSE.
 *
 * Modified in 2026 for Mizulune's Java/Forge 1.20.1 module and event system.
 */
package shit.zen.modules.impl.movement;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.SupportType;
import org.lwjgl.glfw.GLFW;
import shit.zen.event.EventPriority;
import shit.zen.event.EventTarget;
import shit.zen.ZenClient;
import shit.zen.event.impl.PlayerAfterJumpEvent;
import shit.zen.event.impl.DisconnectEvent;
import shit.zen.event.impl.PostMotionEvent;
import shit.zen.event.impl.MotionEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.event.impl.RenderEvent;
import shit.zen.event.impl.RotationResolvedEvent;
import shit.zen.event.impl.SafeWalkEvent;
import shit.zen.event.impl.StrafeEvent;
import shit.zen.event.impl.SprintDecisionEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.event.impl.UpdateHeldItemEvent;
import shit.zen.event.impl.WorldChangeEvent;
import shit.zen.manager.ConfigManager;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.movement.scaffold.v2.ScaffoldMovementPlanner;
import shit.zen.modules.impl.movement.scaffold.v2.ScaffoldMovementPrediction;
import shit.zen.modules.impl.movement.scaffold.v2.LedgeAction;
import shit.zen.modules.impl.movement.scaffold.v2.ScaffoldLedgePolicy;
import shit.zen.modules.impl.movement.scaffold.v2.ScaffoldPlacementPipeline;
import shit.zen.modules.impl.movement.scaffold.v2.ScaffoldRotationRetention;
import shit.zen.modules.impl.movement.scaffold.v2.ScaffoldSafeWalkPolicy;
import shit.zen.modules.impl.movement.scaffold.v2.ScaffoldStabilizeMovement;
import shit.zen.modules.impl.movement.scaffold.v2.ScaffoldSprintControl;
import shit.zen.modules.impl.movement.scaffold.v2.ScaffoldTargetFinder;
import shit.zen.modules.impl.movement.scaffold.v2.ScaffoldTellyPolicy;
import shit.zen.modules.impl.movement.scaffold.v2.ScaffoldTickFrame;
import shit.zen.modules.impl.movement.scaffold.v2.feature.ScaffoldAutoBlockFeature;
import shit.zen.modules.impl.movement.scaffold.v2.feature.ScaffoldBlockItemSelection;
import shit.zen.modules.impl.movement.scaffold.v2.feature.ScaffoldPlacementGate;
import shit.zen.modules.impl.movement.scaffold.v2.feature.ScaffoldSameYPolicy;
import shit.zen.modules.impl.movement.scaffold.v2.motion.Acceleration;
import shit.zen.modules.impl.movement.scaffold.v2.motion.AutoSpeed;
import shit.zen.modules.impl.movement.scaffold.v2.motion.Blink;
import shit.zen.modules.impl.movement.scaffold.v2.motion.ScaffoldPacketBuffer;
import shit.zen.modules.impl.movement.scaffold.v2.motion.SimulatePlacementAttempts;
import shit.zen.modules.impl.movement.scaffold.v2.motion.SpeedLimiter;
import shit.zen.modules.impl.movement.scaffold.v2.motion.Strafe;
import shit.zen.modules.impl.movement.scaffold.v2.motion.StrafeOnJump;
import shit.zen.modules.impl.movement.scaffold.v2.normal.ScaffoldCeilingFeature;
import shit.zen.modules.impl.movement.scaffold.v2.normal.ScaffoldDownFeature;
import shit.zen.modules.impl.movement.scaffold.v2.normal.ScaffoldEagleFeature;
import shit.zen.modules.impl.movement.scaffold.v2.normal.ScaffoldHeadHitterFeature;
import shit.zen.modules.impl.movement.scaffold.v2.normal.ScaffoldTellyFeature;
import shit.zen.modules.impl.movement.scaffold.v2.newtelly.ScaffoldNewTellyPolicy;
import shit.zen.modules.impl.movement.scaffold.v2.newtelly.ScaffoldNewTellyTargetState;
import shit.zen.modules.impl.movement.scaffold.v2.technique.BreezilyTechnique;
import shit.zen.modules.impl.movement.scaffold.v2.technique.ExpandTechnique;
import shit.zen.modules.impl.movement.scaffold.v2.technique.GodBridgeTechnique;
import shit.zen.modules.impl.movement.scaffold.v2.technique.NewTellyTechnique;
import shit.zen.modules.impl.movement.scaffold.v2.technique.NormalTechnique;
import shit.zen.modules.impl.movement.scaffold.v2.technique.Technique;
import shit.zen.modules.impl.movement.scaffold.v2.tower.HypixelTower;
import shit.zen.modules.impl.movement.scaffold.v2.tower.KarhuTower;
import shit.zen.modules.impl.movement.scaffold.v2.tower.MotionTower;
import shit.zen.modules.impl.movement.scaffold.v2.tower.NoneTower;
import shit.zen.modules.impl.movement.scaffold.v2.tower.PulldownTower;
import shit.zen.modules.impl.movement.scaffold.v2.tower.TickMotionDecision;
import shit.zen.modules.impl.movement.scaffold.v2.tower.Tower;
import shit.zen.modules.impl.movement.scaffold.v2.tower.VulcanTower;
import shit.zen.modules.impl.movement.scaffold.debug.ScaffoldTraceRecorder;
import shit.zen.modules.impl.render.DynamicIsland;
import shit.zen.modules.impl.player.Clutch;
import shit.zen.modules.impl.world.BlockIn;
import shit.zen.utils.game.BlockPlacementTarget;
import shit.zen.utils.game.DirectionalInput;
import shit.zen.utils.game.EdgeSafetyUtil;
import shit.zen.utils.game.PlayerPositionHold;
import shit.zen.utils.misc.ChatUtil;
import shit.zen.utils.misc.PacketUtil;
import shit.zen.utils.misc.ReflectionUtil;
import shit.zen.utils.render.RenderUtil;
import shit.zen.utils.rotation.MovementCorrection;
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

public class Scaffold extends Module implements RotationProvider {
    private static final int NEW_TELLY_DEFERRED_PENDING_DISTANCE = 2;
    private static final int NEW_TELLY_DEFERRED_PENDING_MANHATTAN_DISTANCE = 3;
    private static final int NEW_TELLY_CONNECTOR_MAX_AGE = 2;

    public static Scaffold INSTANCE;

    public static boolean interceptOutgoingPacket(
            Packet<?> packet,
            PacketSendListener listener) {
        Scaffold scaffold = INSTANCE;
        if (scaffold == null || !scaffold.isEnabled() || packet == null || mc.player == null) {
            return false;
        }
        if (RotationHandler.shouldBypassScaffoldPacketBuffer(packet)) {
            return false;
        }
        if (scaffold.isNewTelly()
                && (scaffold.newTellyYieldingToClutch
                || scaffold.isNewTellyClutchHandoffActive())) {
            scaffold.packetBuffer.flush();
            return false;
        }
        if (packet instanceof ServerboundUseItemPacket
                && !scaffold.newTellyPlacementTransaction) {
            return false;
        }
        ScaffoldTickFrame frame = scaffold.currentFrame;
        if (frame != null) {
            ScaffoldTraceRecorder.attachPacketContext(
                    packet,
                    frame.frameId(),
                    frame.target(),
                    scaffold);
        }
        return scaffold.packetBuffer.intercept(
                packet,
                listener,
                scaffold.getBlinkSettings(),
                new Blink.PacketContext(
                        true,
                        packet instanceof ServerboundUseItemOnPacket,
                        scaffold.isTowering(),
                        mc.player.isShiftKeyDown(),
                        mc.player.onGround()),
                System.currentTimeMillis());
    }

    public final ModeValue mode = new ModeValue("Mode", "Normal", "Telly")
            .withDefault("Normal")
            .withVisibility(() -> false);
    public final ModeValue technique = new ModeValue(
            "Technique",
            "Normal",
            "Expand",
            "GodBridge",
            "Breezily",
            "New Telly").withDefault("Normal");
    public final BooleanValue telly = new BooleanValue(
            "Telly",
            false,
            () -> this.technique.is("Normal"));
    public final ModeValue rotationMode = new ModeValue(
            "Rotation Mode",
            "Center",
            "Random",
            "Stabilized",
            "NearestRotation",
            "ReverseYaw",
            "DiagonalYaw",
            "AngleYaw",
            "EdgePoint")
            .withDefault("Stabilized")
            .withVisibility(() -> this.technique.is("Normal"));
    public final BooleanValue requiresSight = new BooleanValue(
            "Requires Sight",
            false,
            () -> this.technique.is("Normal"));
    public final BooleanValue newTellyAlwaysUpdateRotation = new BooleanValue(
            "Always Update Rotation",
            true,
            this::isNewTelly);
    public final NumberValue newTellyPlaceTick = new NumberValue(
            "PlaceTick",
            1,
            1,
            5,
            1,
            this::isNewTelly);
    public final NumberValue newTellyRotationTick = new NumberValue(
            "RotationTick",
            3,
            1,
            5,
            1,
            this::isNewTelly);
    public final BooleanValue newTellyNoUpTelly = new BooleanValue(
            "No Uptelly",
            false,
            this::isNewTelly);
    public final BooleanValue newTellyHeypixelUpTelly = new BooleanValue(
            "Heypixel UpTelly",
            true,
            this::isNewTelly);
    public final BooleanValue newTellySafeMode = new BooleanValue(
            "Safe Mode",
            false,
            () -> this.isNewTelly() && this.newTellyHeypixelUpTelly.getValue());
    public final BooleanValue newTellyTestOnGround = new BooleanValue(
            "Test OnGround",
            false,
            this::isNewTelly);
    public final BooleanValue newTellyFixRotation = new BooleanValue(
            "Fix Rotation",
            false,
            this::isNewTelly);
    public final BooleanValue newTellySlowUpTelly = new BooleanValue(
            "SlowUpTelly",
            false,
            this::isNewTelly);
    public final ModeValue newTellyBlockSlotMode = new ModeValue(
            "Block Slot Mode",
            "Farthest",
            "Most Blocks")
            .withDefault("Farthest")
            .withVisibility(this::isNewTelly);
    public final ModeValue newTellyJumpMode = new ModeValue(
            "Jump Mode",
            "Parkour",
            "Normal",
            "None")
            .withDefault("Normal")
            .withVisibility(this::isNewTelly);
    public final BooleanValue newTellyDuplicateRotPlace = new BooleanValue(
            "DuplicateRotPlace",
            true,
            this::isNewTelly);
    public final BooleanValue newTellyInteractItemBeforePlace = new BooleanValue(
            "Interact Item Before Place",
            true,
            this::isNewTelly);
    public final NumberValue expandLength = new NumberValue(
            "Expand Length",
            4,
            1,
            10,
            1,
            () -> this.technique.is("Expand"));
    public final BooleanValue godBridgeJump = new BooleanValue(
            "GodBridge Jump",
            true,
            () -> this.technique.is("GodBridge"));
    public final BooleanValue godBridgeSneak = new BooleanValue(
            "GodBridge Sneak",
            false,
            () -> this.technique.is("GodBridge"));
    public final BooleanValue godBridgeStopInput = new BooleanValue(
            "GodBridge Stop Input",
            false,
            () -> this.technique.is("GodBridge"));
    public final BooleanValue godBridgeBackwards = new BooleanValue(
            "GodBridge Backwards",
            false,
            () -> this.technique.is("GodBridge"));
    public final NumberValue godBridgeForceSneakBelowCount = new NumberValue(
            "GodBridge Force Sneak Below Count",
            3,
            0,
            10,
            1,
            () -> this.technique.is("GodBridge"));
    public final Value<NumericRange> godBridgeSneakTime = new Value<>(
            "god_bridge_sneak_time",
            "GodBridge Sneak Time",
            "",
            new NumericRange(1, 1, 1, 10, 1, true),
            ValueType.INT_RANGE);
    public final Value<NumericRange> breezilyEdgeDistance = new Value<>(
            "breezily_edge_distance",
            "Breezily Edge Distance",
            "",
            new NumericRange(0.45, 0.5, 0.25, 0.5, 0.01, false),
            ValueType.DECIMAL_RANGE);
    public final Value<NumericRange> delay = new Value<>(
            "delay",
            "Delay",
            "",
            new NumericRange(0, 0, 0, 40, 1, true),
            ValueType.INT_RANGE);
    public final NumberValue minDist = new NumberValue(
            "Min Dist",
            0.0,
            0.0,
            0.25,
            0.01);
    public final BooleanValue prediction = new BooleanValue("Prediction", true);
    public final BooleanValue autoBlock = new BooleanValue("Auto Block", true);
    public final BooleanValue autoBlockAlways = new BooleanValue(
            "Auto Block Always",
            false,
            this.autoBlock::getValue);
    public final NumberValue autoBlockSlotResetDelay = new NumberValue(
            "Auto Block Slot Reset Delay",
            5,
            0,
            40,
            1,
            this.autoBlock::getValue);
    public final NumberValue autoBlockDoNotUseBelowCount = new NumberValue(
            "Auto Block Do Not Use Below Count",
            1,
            0,
            64,
            1,
            this.autoBlock::getValue);
    public final BooleanValue considerInventory = new BooleanValue("Consider Inventory", false);
    public final ModeValue sameY = new ModeValue("Same Y", "Off", "On", "Falling", "Hypixel")
            .withDefault("Off");
    public final ModeValue swing = new ModeValue(
            "Swing",
            "Hide For Client",
            "Show",
            "None").withDefault("Show");
    public final BooleanValue eagle = new BooleanValue(
            "Eagle",
            false,
            () -> this.technique.is("Normal") || this.isNewTelly());
    public final Value<NumericRange> eagleBlocksToEagle = new Value<>(
            "eagle_blocks_to_eagle",
            "Eagle Blocks To Eagle",
            "",
            new NumericRange(0, 0, 0, 10, 1, true),
            ValueType.INT_RANGE);
    public final NumberValue eagleEdgeDistance = new NumberValue(
            "Eagle Edge Distance",
            0.01,
            0.01,
            1.3,
            0.01,
            this.eagle::getValue);
    public final BooleanValue eagleOnlyOnGround = new BooleanValue(
            "Eagle Only On Ground",
            true,
            this.eagle::getValue);
    public final BooleanValue down = new BooleanValue(
            "Down",
            false,
            () -> this.technique.is("Normal"));
    public final BooleanValue ceiling = new BooleanValue(
            "Ceiling",
            false,
            () -> this.technique.is("Normal"));
    public final BooleanValue headHitter = new BooleanValue(
            "Head Hitter",
            false,
            () -> this.technique.is("Normal"));
    public final BooleanValue renderItemSpoof = new BooleanValue("Render Item Spoof", true);
    public final BooleanValue scaffoldCounterOnIsland = new BooleanValue("Scaffold Counter On Island", false);
    public final ModeValue movementCorrection = new ModeValue(
            "Movement Correction",
            "Off",
            "Strict",
            "Silent",
            "ChangeLook").withDefault("Silent");
    public final ModeValue angleSmooth = new ModeValue(
            "Angle Smooth",
            "Linear",
            "Sigmoid").withDefault("Linear");
    public final Value<NumericRange> horizontalTurnSpeed = new Value<>(
            "horizontal_turn_speed",
            "Horizontal Turn Speed",
            "",
            new NumericRange(180.0, 180.0, 0.0, 180.0, 0.1, false),
            ValueType.DECIMAL_RANGE);
    public final Value<NumericRange> verticalTurnSpeed = new Value<>(
            "vertical_turn_speed",
            "Vertical Turn Speed",
            "",
            new NumericRange(180.0, 180.0, 0.0, 180.0, 0.1, false),
            ValueType.DECIMAL_RANGE);
    public final NumberValue smoothSteepness = new NumberValue(
            "Smooth Steepness",
            8.0,
            1.0,
            16.0,
            0.1,
            () -> this.angleSmooth.is("Sigmoid"));
    public final NumberValue resetTicks = new NumberValue(
            "Ticks Until Reset",
            5,
            1,
            30,
            1);
    public final BooleanValue keepRotation = new BooleanValue("Keep Rotation", false);
    public final NumberValue resetThreshold = new NumberValue(
            "Reset Threshold",
            2.0,
            1.0,
            180.0,
            1.0);
    public final ModeValue rotationTiming = new ModeValue(
            "Rotation Timing",
            "Normal",
            "On Tick",
            "On Tick Snap").withDefault("Normal");
    public final BooleanValue stabilizeMovement = new BooleanValue(
            "Stabilize Movement",
            true,
            () -> this.technique.is("Normal"));
    public final ModeValue safeWalk = new ModeValue(
            "Safe Walk",
            "None",
            "Safe",
            "OnEdge").withDefault("Safe");
    public final BooleanValue ledge = new BooleanValue("Ledge", true);
    public final NumberValue safeWalkEdgeDistance = new NumberValue(
            "Safe Walk Distance",
            0.1,
            0.1,
            0.5,
            0.01,
            () -> this.safeWalk.is("OnEdge"));
    public final Value<NumericRange> safeWalkKeep = new Value<>(
            "safe_walk_keep",
            "Safe Walk Keep",
            "",
            new NumericRange(1, 2, 1, 20, 1, true),
            ValueType.INT_RANGE);
    public final ModeValue safeWalkOnEdgeMode = new ModeValue(
            "Safe Walk On Edge",
            "Stop",
            "Invert",
            "Center")
            .withDefault("Stop")
            .withVisibility(() -> this.safeWalk.is("OnEdge"));
    public final Value<NumericRange> safeWalkSneak = new Value<>(
            "safe_walk_sneak",
            "Safe Walk Sneak",
            "",
            new NumericRange(0, 0, 0, 20, 1, true),
            ValueType.INT_RANGE);
    public final BooleanValue safeWalkJump = new BooleanValue(
            "Safe Walk Jump",
            false,
            () -> this.safeWalk.is("OnEdge"));
    public final ModeValue tower = new ModeValue(
            "Tower",
            "None",
            "Motion",
            "Pulldown",
            "Karhu",
            "Vulcan",
            "Hypixel").withDefault("None");
    public final NumberValue towerMotion = new NumberValue(
            "Tower Motion",
            0.42,
            0.0,
            1.0,
            0.01,
            () -> this.tower.is("Motion"));
    public final NumberValue towerTriggerHeight = new NumberValue(
            "Tower Trigger Height",
            0.78,
            0.76,
            1.0,
            0.01,
            () -> this.tower.is("Motion"));
    public final NumberValue towerSlow = new NumberValue(
            "Tower Slow",
            1.0,
            0.0,
            3.0,
            0.01,
            () -> this.tower.is("Motion"));
    public final NumberValue towerPulldownTrigger = new NumberValue(
            "Tower Pulldown Trigger",
            0.1,
            0.0,
            0.2,
            0.01,
            () -> this.tower.is("Pulldown"));
    public final NumberValue towerKarhuTimer = new NumberValue(
            "Tower Karhu Timer",
            5.0,
            0.1,
            10.0,
            0.1,
            () -> this.tower.is("Karhu"));
    public final NumberValue towerKarhuTrigger = new NumberValue(
            "Tower Karhu Trigger",
            0.06,
            0.0,
            0.2,
            0.01,
            () -> this.tower.is("Karhu"));
    public final BooleanValue towerKarhuPulldown = new BooleanValue(
            "Tower Karhu Pulldown",
            true,
            () -> this.tower.is("Karhu"));
    public final NumberValue timer = new NumberValue("Timer", 1.0, 0.01, 10.0, 0.01);
    public final BooleanValue acceleration = new BooleanValue("Acceleration", false);
    public final NumberValue accelerationSpeedMultiplier = new NumberValue(
            "Acceleration Speed Multiplier",
            0.6,
            0.1,
            3.0,
            0.01,
            this.acceleration::getValue);
    public final BooleanValue accelerationOnlyOnGround = new BooleanValue(
            "Acceleration Only On Ground",
            false,
            this.acceleration::getValue);
    public final BooleanValue strafe = new BooleanValue("Strafe", false);
    public final NumberValue strafeSpeed = new NumberValue(
            "Strafe Speed",
            0.247,
            0.0,
            5.0,
            0.001,
            () -> this.strafe.getValue() || this.autoSpeed.getValue());
    public final BooleanValue strafeHypixel = new BooleanValue(
            "Strafe Hypixel",
            false,
            () -> this.strafe.getValue() || this.autoSpeed.getValue());
    public final BooleanValue strafeOnlyOnGround = new BooleanValue(
            "Strafe Only On Ground",
            false,
            () -> this.strafe.getValue() || this.autoSpeed.getValue());
    public final BooleanValue strafeOnJump = new BooleanValue("Strafe On Jump", false);
    public final Value<NumericRange> strafeOnJumpStraightSpeed = new Value<>(
            "strafe_on_jump_straight_speed",
            "Strafe On Jump Straight Speed",
            "",
            new NumericRange(0.48, 0.49, 0.1, 1.0, 0.01, false),
            ValueType.DECIMAL_RANGE);
    public final Value<NumericRange> strafeOnJumpDiagonalSpeed = new Value<>(
            "strafe_on_jump_diagonal_speed",
            "Strafe On Jump Diagonal Speed",
            "",
            new NumericRange(0.48, 0.49, 0.1, 1.0, 0.01, false),
            ValueType.DECIMAL_RANGE);
    public final BooleanValue speedLimiter = new BooleanValue("Speed Limiter", false);
    public final NumberValue speedLimit = new NumberValue(
            "Speed Limit",
            0.11,
            0.01,
            0.4,
            0.01,
            this.speedLimiter::getValue);
    public final BooleanValue autoSpeed = new BooleanValue("Auto Speed", false);
    public final BooleanValue blink = new BooleanValue("Blink", false);
    public final Value<NumericRange> blinkTime = new Value<>(
            "blink_time",
            "Blink Time",
            "",
            new NumericRange(50, 250, 0, 3000, 1, true),
            ValueType.INT_RANGE);
    public final BooleanValue blinkFlushOnPlace = new BooleanValue(
            "Blink Flush On Place",
            false,
            this.blink::getValue);
    public final BooleanValue blinkFlushOnTowering = new BooleanValue(
            "Blink Flush On Towering",
            false,
            this.blink::getValue);
    public final BooleanValue blinkFlushOnSneaking = new BooleanValue(
            "Blink Flush On Sneaking",
            false,
            this.blink::getValue);
    public final BooleanValue blinkFlushOnNotSneaking = new BooleanValue(
            "Blink Flush On Not Sneaking",
            false,
            this.blink::getValue);
    public final BooleanValue blinkFlushOnGround = new BooleanValue(
            "Blink Flush On Ground",
            false,
            this.blink::getValue);
    public final BooleanValue blinkFlushInAir = new BooleanValue(
            "Blink Flush In Air",
            false,
            this.blink::getValue);
    public final BooleanValue simulatePlacementAttempts = new BooleanValue(
            "Simulate Placement Attempts",
            false);
    public final Value<NumericRange> simulatePlacementCps = new Value<>(
            "simulate_placement_cps",
            "Simulate Placement CPS",
            "",
            new NumericRange(5, 8, 1, 100, 1, true),
            ValueType.INT_RANGE);
    public final BooleanValue simulateFailedAttemptsOnly = new BooleanValue(
            "Simulate Failed Attempts Only",
            true,
            this.simulatePlacementAttempts::getValue);
    public final BooleanValue sprintControl = new BooleanValue("Sprint Control", false);
    public final ModeValue sprintClientMode = new ModeValue(
            "Sprint Client",
            "DoNotChange",
            "ForceSprint",
            "ForceNoSprint",
            "NoSprintOnPlace",
            "NoSprintOnGround")
            .withDefault("DoNotChange")
            .withVisibility(this.sprintControl::getValue);
    public final ModeValue sprintServerMode = new ModeValue(
            "Sprint Server",
            "DoNotChange",
            "ForceSprint",
            "ForceNoSprint",
            "NoSprintOnPlace",
            "NoSprintOnGround")
            .withDefault("DoNotChange")
            .withVisibility(this.sprintControl::getValue);
    public final ModeValue tellyResetMode = new ModeValue("Telly Reset Mode", "Reset", "Reverse")
            .withDefault("Reset")
            .withVisibility(this::isTelly);
    public final NumberValue tellyStraightTicks = new NumberValue(
            "Telly Straight",
            0,
            0,
            5,
            1,
            this::isTelly);
    public final Value<NumericRange> tellyJumpTicks = new Value<>(
            "telly_jump",
            "Telly Jump",
            "",
            new NumericRange(0, 0, 0, 10, 1, true),
            ValueType.INT_RANGE);
    public final BooleanValue tellyAimOnTower = new BooleanValue(
            "Telly Aim On Tower",
            true,
            this::isTelly);

    public final BooleanValue debug = new BooleanValue("Debug", false);
    public final NumberValue debugInterval = new NumberValue(
            "Debug Interval",
            5,
            1,
            40,
            1,
            this.debug::getValue);

    private final ScaffoldMovementPlanner movementPlanner = new ScaffoldMovementPlanner();
    private final ScaffoldMovementPrediction movementPrediction = new ScaffoldMovementPrediction();
    private final ScaffoldTargetFinder targetFinder = new ScaffoldTargetFinder();
    private final ScaffoldPlacementPipeline placementPipeline = new ScaffoldPlacementPipeline();
    private final ScaffoldRotationRetention rotationRetention = new ScaffoldRotationRetention();
    private final ScaffoldStabilizeMovement movementStabilizer = new ScaffoldStabilizeMovement();
    private final ScaffoldSafeWalkPolicy safeWalkPolicy = new ScaffoldSafeWalkPolicy();
    private final ScaffoldLedgePolicy ledgePolicy = new ScaffoldLedgePolicy();
    private final ScaffoldPlacementGate placementGate = new ScaffoldPlacementGate();
    private final ScaffoldSameYPolicy sameYPolicy = new ScaffoldSameYPolicy();
    private final Strafe strafeController = new Strafe();
    private final ScaffoldPacketBuffer packetBuffer = new ScaffoldPacketBuffer();
    private final ScaffoldTellyPolicy tellyPolicy = new ScaffoldTellyPolicy();
    private final ScaffoldSprintControl sprintController = new ScaffoldSprintControl();
    private final ScaffoldNewTellyPolicy newTellyPolicy = new ScaffoldNewTellyPolicy();
    private final ScaffoldNewTellyTargetState newTellyTargetState =
            new ScaffoldNewTellyTargetState();

    private int autoBlockRestoreSlot = -1;
    private int autoBlockSelectedSlot = -1;
    private int groundTicks;
    private int airTicks;
    private int lastCounterTick = -1;
    private int placementY;
    private long selectedSlotResetTick = Long.MIN_VALUE;
    private long nextSimulatedClickNanos;
    private boolean sameYInitialized;
    private boolean wasTowering;
    private boolean currentTowering;
    private boolean strafeFeatureActive;
    private Rotation onTickSnapRotation;
    private int debugTicks;
    private String lastDebugState;
    private long frameSequence;
    private long consumedFrameId = -1L;
    private DirectionalInput rawInput = DirectionalInput.NONE;
    private ScaffoldTickFrame currentFrame;
    private BlockPlacementTarget currentPlacement;
    private Rotation requestedRotation;
    private ScaffoldMovementPlanner.MovementLine currentOptimalLine;
    private ScaffoldMovementPrediction.Prediction currentPrediction;
    private ScaffoldTargetFinder.FindResult currentFindResult;
    private LedgeAction currentLedgeAction = LedgeAction.NO_LEDGE;
    private ScaffoldEagleFeature.State eagleState = ScaffoldEagleFeature.DEFAULT_STATE;
    private ScaffoldDownFeature.State downState = new ScaffoldDownFeature.State(false, false);
    private Technique activeTechnique = new NormalTechnique();
    private String activeTechniqueKey = "";
    private Tower activeTower = new NoneTower();
    private String activeTowerKey = "";
    private Vec3 towerPacketOffset = Vec3.ZERO;
    private boolean newTellyWasActive;
    private boolean newTellyYieldingToClutch;
    private boolean newTellyPhysicalJump;
    private boolean newTellyInputJumpBefore;
    private boolean newTellyInputJumpAfter;
    private int newTellyInputJumpDelay = -1;
    private boolean newTellyPlacementTransaction;
    private String newTellyRotationSource = "idle";

    public Scaffold() {
        super("Scaffold", Category.MOVEMENT);
        this.mode.metadata("optionAliases", Map.of(
                "Telly Bridge", "Telly",
                "Old Telly", "Normal",
                "New Telly", "Normal",
                "Keep Y", "Normal"));
        this.tellyJumpTicks.visibleWhen(this::isTelly).alias("Jump");
        this.angleSmooth.metadata("optionAliases", Map.of(
                "LINEAR", "Linear",
                "SIGMOID", "Sigmoid"));
        this.swing.metadata("optionAliases", Map.of(
                "DoNotHide", "Show",
                "Hide", "Hide For Client"));
        this.horizontalTurnSpeed.visibleWhen(() -> this.angleSmooth.is("Linear"));
        this.verticalTurnSpeed.visibleWhen(() -> this.angleSmooth.is("Linear"));
        this.safeWalkKeep.visibleWhen(() -> this.safeWalk.is("OnEdge"));
        this.safeWalkSneak.visibleWhen(() -> this.safeWalk.is("OnEdge"));
        this.godBridgeSneakTime.visibleWhen(() -> this.technique.is("GodBridge"));
        this.breezilyEdgeDistance.visibleWhen(() -> this.technique.is("Breezily"));
        this.eagleBlocksToEagle.visibleWhen(this.eagle::getValue);
        this.strafeOnJumpStraightSpeed.visibleWhen(this.strafeOnJump::getValue);
        this.strafeOnJumpDiagonalSpeed.visibleWhen(this.strafeOnJump::getValue);
        this.blinkTime.visibleWhen(this.blink::getValue);
        this.simulatePlacementCps.visibleWhen(this.simulatePlacementAttempts::getValue);
        this.resetTicks.alias("Reset Ticks");
        this.rotationTiming.metadata("optionAliases", Map.of(
                "OnTick", "On Tick",
                "OnTickSnap", "On Tick Snap"));
        INSTANCE = this;
    }

    @Override
    protected void configureValueTree(ValueGroup root) {
        ValueGroup general = root.group("general", "General");
        general.add(this.mode);
        general.add(this.delay);
        general.add(this.minDist);
        general.add(this.timer);
        general.add(this.autoBlock);
        general.add(this.autoBlockAlways);
        general.add(this.autoBlockSlotResetDelay);
        general.add(this.autoBlockDoNotUseBelowCount);
        general.add(this.prediction);
        general.add(this.technique);
        general.add(this.telly);
        general.add(this.sameY);
        general.add(this.swing);
        general.add(this.eagle);
        general.add(this.eagleBlocksToEagle);
        general.add(this.eagleEdgeDistance);
        general.add(this.eagleOnlyOnGround);
        general.add(this.down);
        general.add(this.ceiling);
        general.add(this.headHitter);
        general.add(this.renderItemSpoof);
        general.add(this.scaffoldCounterOnIsland);

        ValueGroup rotation = root.group("rotation", "Rotation");
        rotation.add(this.movementCorrection);
        rotation.add(this.angleSmooth);
        rotation.add(this.horizontalTurnSpeed);
        rotation.add(this.verticalTurnSpeed);
        rotation.add(this.smoothSteepness);
        rotation.add(this.keepRotation);
        rotation.add(this.resetTicks);
        rotation.add(this.resetThreshold);
        rotation.add(this.rotationTiming);
        rotation.add(this.considerInventory);

        ValueGroup movement = root.group("movement", "Movement");
        movement.add(this.stabilizeMovement);
        movement.add(this.safeWalk);
        movement.add(this.ledge);
        movement.add(this.safeWalkEdgeDistance);
        movement.add(this.safeWalkKeep);
        movement.add(this.safeWalkOnEdgeMode);
        movement.add(this.safeWalkSneak);
        movement.add(this.safeWalkJump);
        movement.add(this.sprintControl);
        movement.add(this.sprintClientMode);
        movement.add(this.sprintServerMode);
        movement.add(this.acceleration);
        movement.add(this.accelerationSpeedMultiplier);
        movement.add(this.accelerationOnlyOnGround);
        movement.add(this.strafe);
        movement.add(this.strafeSpeed);
        movement.add(this.strafeHypixel);
        movement.add(this.strafeOnlyOnGround);
        movement.add(this.strafeOnJump);
        movement.add(this.strafeOnJumpStraightSpeed);
        movement.add(this.strafeOnJumpDiagonalSpeed);
        movement.add(this.speedLimiter);
        movement.add(this.speedLimit);
        movement.add(this.autoSpeed);

        ValueGroup network = root.group("network", "Network");
        network.add(this.blink);
        network.add(this.blinkTime);
        network.add(this.blinkFlushOnPlace);
        network.add(this.blinkFlushOnTowering);
        network.add(this.blinkFlushOnSneaking);
        network.add(this.blinkFlushOnNotSneaking);
        network.add(this.blinkFlushOnGround);
        network.add(this.blinkFlushInAir);
        network.add(this.simulatePlacementAttempts);
        network.add(this.simulatePlacementCps);
        network.add(this.simulateFailedAttemptsOnly);

        ValueGroup technique = root.group("technique", "Technique");
        technique.add(this.rotationMode);
        technique.add(this.requiresSight);
        technique.add(this.expandLength);
        technique.add(this.godBridgeJump);
        technique.add(this.godBridgeSneak);
        technique.add(this.godBridgeStopInput);
        technique.add(this.godBridgeBackwards);
        technique.add(this.godBridgeForceSneakBelowCount);
        technique.add(this.godBridgeSneakTime);
        technique.add(this.breezilyEdgeDistance);

        ValueGroup newTelly = root.group("new_telly", "New Telly");
        newTelly.add(this.newTellyAlwaysUpdateRotation);
        newTelly.add(this.newTellyPlaceTick);
        newTelly.add(this.newTellyRotationTick);
        newTelly.add(this.newTellyNoUpTelly);
        newTelly.add(this.newTellyHeypixelUpTelly);
        newTelly.add(this.newTellySafeMode);
        newTelly.add(this.newTellyTestOnGround);
        newTelly.add(this.newTellyFixRotation);
        newTelly.add(this.newTellySlowUpTelly);
        newTelly.add(this.newTellyBlockSlotMode);
        newTelly.add(this.newTellyJumpMode);
        newTelly.add(this.newTellyDuplicateRotPlace);
        newTelly.add(this.newTellyInteractItemBeforePlace);

        ValueGroup tower = root.group("tower", "Tower");
        tower.add(this.tower);
        tower.add(this.towerMotion);
        tower.add(this.towerTriggerHeight);
        tower.add(this.towerSlow);
        tower.add(this.towerPulldownTrigger);
        tower.add(this.towerKarhuTimer);
        tower.add(this.towerKarhuTrigger);
        tower.add(this.towerKarhuPulldown);

        ValueGroup telly = root.group("telly", "Telly");
        telly.add(this.tellyResetMode);
        telly.add(this.tellyStraightTicks);
        telly.add(this.tellyJumpTicks);
        telly.add(this.tellyAimOnTower);

        ValueGroup diagnostics = root.group("diagnostics", "Diagnostics");
        diagnostics.add(this.debug);
        diagnostics.add(this.debugInterval);
    }

    @Override
    public String getSuffix() {
        return this.technique.getValue() + (this.isTelly() ? " Telly" : "");
    }

    @Override
    protected void onEnable() {
        this.sameYInitialized = false;
        if (GodBridgeAssist.INSTANCE != null && GodBridgeAssist.INSTANCE.isEnabled()) {
            GodBridgeAssist.INSTANCE.setEnabled(false);
        }
        if (BlockIn.INSTANCE != null && BlockIn.INSTANCE.isEnabled()) {
            BlockIn.INSTANCE.setEnabled(false);
        }
        boolean migratedLegacyTelly = this.migrateLegacyMode();
        if (mc.player != null) {
            this.sameYPolicy.reset(mc.player.blockPosition().getY());
            this.placementY = this.sameYPolicy.state().placementY();
            this.sameYInitialized = true;
        }
        if (migratedLegacyTelly) {
            ConfigManager.requestSaveIfReady();
        }
        this.resetPlanning();
        this.strafeController.onEnabled();
        this.strafeFeatureActive = this.isStrafeFeatureEnabled();
        RotationHandler.registerProvider(this);
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        if (this.keepRotation.getValue() || this.isOnTickRotationTiming()) {
            RotationHandler.clearOwnedRotation(this);
        } else {
            RotationHandler.releaseProvider(this);
        }
        this.rotationRetention.clear();
        this.restoreAutoBlockSlotNow();
        ZenClient.serverTickRate = 1.0f;
        this.activeTower.reset();
        this.packetBuffer.reset();
        ScaffoldTraceRecorder.clearPendingPlacements();
        if (mc.player != null && this.strafeFeatureActive) {
            Strafe.Decision strafeReset = this.strafeController.onDisabled(
                    mc.player.getDeltaMovement(),
                    this.getStrafeSettings());
            if (strafeReset.writesVelocity()) {
                mc.player.setDeltaMovement(strafeReset.velocity());
            }
        }
        this.strafeController.reset();
        this.strafeFeatureActive = false;
        this.onTickSnapRotation = null;
        this.wasTowering = false;
        if (mc.options != null && mc.getWindow() != null) {
            boolean physicalShift = InputConstants.isKeyDown(
                    mc.getWindow().getWindow(),
                    mc.options.keyShift.getKey().getValue());
            boolean physicalSprint = InputConstants.isKeyDown(
                    mc.getWindow().getWindow(),
                    mc.options.keySprint.getKey().getValue());
            mc.options.keyShift.setDown(physicalShift);
            KeyMapping.set(mc.options.keySprint.getKey(), physicalSprint);
            mc.options.keySprint.setDown(physicalSprint);
            mc.options.keyUse.setDown(false);
        }
        this.resetPlanning();
        super.onDisable();
    }

    @Override
    protected void onConfigLoaded() {
        if (this.migrateLegacyMode()) {
            ConfigManager.requestSaveIfReady();
        }
        if (this.isEnabled()) {
            this.restoreAutoBlockSlotNow();
            this.packetBuffer.reset();
            this.sameYInitialized = false;
            this.resetPlanning();
        }
    }

    @EventTarget
    public void onUpdateHeldItem(UpdateHeldItemEvent event) {
        if (this.renderItemSpoof.getValue()
                && event.getHand() == InteractionHand.MAIN_HAND
                && mc.player != null
                && this.autoBlockRestoreSlot >= 0
                && this.autoBlockRestoreSlot < 9) {
            event.setItemStack(mc.player.getInventory().getItem(this.autoBlockRestoreSlot));
        }
    }

    @EventTarget
    public void onPostMotion(PostMotionEvent event) {
        if (mc.player == null) {
            return;
        }
        CounterState counters = advanceCounters(
                this.lastCounterTick,
                this.groundTicks,
                this.airTicks,
                mc.player.tickCount,
                mc.player.onGround());
        this.lastCounterTick = counters.lastTick();
        this.groundTicks = counters.groundTicks();
        this.airTicks = counters.airTicks();
        if (this.debug.getValue()) {
            this.debugLog("packet-summary "
                    + RotationHandler.getOutgoingMovePacketDebug(mc.player.tickCount));
        }
    }

    @EventTarget(value = EventPriority.HIGHEST)
    public void onTowerMotionPacket(MotionEvent event) {
        if (!event.isPost() || this.towerPacketOffset.equals(Vec3.ZERO)) {
            return;
        }
        event.setX(event.getX() + this.towerPacketOffset.x);
        event.setY(event.getY() + this.towerPacketOffset.y);
        event.setZ(event.getZ() + this.towerPacketOffset.z);
        this.towerPacketOffset = Vec3.ZERO;
    }

    @EventTarget(value = EventPriority.HIGH)
    public void onStrafe(StrafeEvent event) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        float newTellyInputScale = 1.0f;
        this.newTellyInputJumpBefore = event.isJumping();
        this.newTellyInputJumpAfter = event.isJumping();
        this.newTellyInputJumpDelay = ReflectionUtil.getJumpDelay(mc.player);
        if (this.isNewTelly()
                && !this.newTellyYieldingToClutch
                && !this.isNewTellyClutchHandoffActive()) {
            boolean physicalJump = this.isPhysicalKeyDown(mc.options.keyJump);
            boolean[] parkourAir = this.getNewTellyParkourAir();
            ScaffoldNewTellyPolicy.MovementDecision newTellyMovement =
                    this.newTellyPolicy.movement(
                            this.getNewTellySettings(),
                            new ScaffoldNewTellyPolicy.MovementFrame(
                                    Math.max(0, this.groundTicks),
                                    physicalJump,
                                    event.getForward() != 0.0f || event.getStrafe() != 0.0f,
                                    this.getPlaceableBlockCount() > 0,
                                    parkourAir[0],
                                    parkourAir[1]));
            event.setJumping(event.isJumping() || physicalJump || newTellyMovement.jump());
            this.newTellyInputJumpAfter = event.isJumping();
            this.newTellyInputJumpDelay = ReflectionUtil.getJumpDelay(mc.player);
            newTellyInputScale = newTellyMovement.inputScale();
        }

        DirectionalInput physicalInput = DirectionalInput.fromImpulses(
                event.getForward(),
                event.getStrafe());
        this.rawInput = physicalInput;
        this.currentOptimalLine = this.movementPlanner.getOptimalMovementLine(physicalInput);

        if (this.isTelly()) {
            event.setJumping(this.getTellyDecision(event.isJumping()).jump());
        }

        if (this.stabilizeMovement.getValue()
                && this.technique.is("Normal")) {
            DirectionalInput stabilized = this.movementStabilizer.stabilize(
                    DirectionalInput.fromImpulses(event.getForward(), event.getStrafe()),
                    event.isJumping(),
                    mc.player.onGround(),
                    this.currentOptimalLine,
                    mc.player.position(),
                    mc.player.getDeltaMovement(),
                    mc.player.getYRot());
            event.setForward(stabilized.forwardImpulse());
            event.setStrafe(stabilized.strafeImpulse());
        }

        DirectionalInput safetyInput = DirectionalInput.fromImpulses(
                event.getForward(),
                event.getStrafe());
        safetyInput = this.getActiveTechnique().adjustInput(
                new Technique.MovementInput(
                        safetyInput,
                        event.isSneaking(),
                        mc.level.getBlockState(mc.player.blockPosition().below()).isAir(),
                        System.currentTimeMillis(),
                        mc.player.getX(),
                        mc.player.getZ(),
                        mc.player.getYRot(),
                        ThreadLocalRandom.current().nextDouble()));
        if (this.ledgePolicy.consumeForcedSneak()) {
            event.setSneaking(true);
        }
        safetyInput = this.currentLedgeAction.applyInput(safetyInput);
        event.setJumping(this.currentLedgeAction.applyJump(event.isJumping()));
        if (this.ledgePolicy.requestForcedSneak(this.currentLedgeAction.sneakTicks())) {
            event.setSneaking(true);
        }

        SpeedLimiter.Decision speedDecision = SpeedLimiter.apply(
                safetyInput,
                mc.player.getDeltaMovement(),
                new SpeedLimiter.Settings(
                        this.speedLimiter.getValue(),
                        this.speedLimit.getValue().floatValue()));
        safetyInput = speedDecision.directionalInput();

        if (!this.isNewTelly() && this.safeWalk.is("OnEdge")) {
            double horizontalSpeed = Math.hypot(
                    mc.player.getDeltaMovement().x,
                    mc.player.getDeltaMovement().z);
            boolean closeToEdge = ScaffoldSafeWalkPolicy.isCloseToEdge(
                    mc.player.getBoundingBox(),
                    safetyInput,
                    mc.player.getYRot(),
                    horizontalSpeed,
                    this.safeWalkEdgeDistance.getValue().doubleValue());
            Vec3 blockCenter = Vec3.atBottomCenterOf(mc.player.blockPosition());
            ScaffoldSafeWalkPolicy.Decision decision = this.safeWalkPolicy.update(
                    this.getSafeWalkSettings(),
                    new ScaffoldSafeWalkPolicy.Frame(
                            safetyInput,
                            event.isJumping(),
                            event.isSneaking(),
                            mc.player.onGround(),
                            closeToEdge,
                            horizontalSpeed,
                            mc.player.position(),
                            mc.player.position().add(mc.player.getDeltaMovement()),
                            mc.player.getYRot(),
                            blockCenter,
                            EdgeSafetyUtil.getDistanceToFall(mc.player.getBoundingBox())
                                    > this.safeWalkEdgeDistance.getValue().doubleValue()));
            safetyInput = decision.directionalInput();
            event.setJumping(decision.jump());
            event.setSneaking(decision.sneak());
        }

        if (!this.isNewTelly()) {
            ScaffoldDownFeature.MovementInputDecision downInput =
                    ScaffoldDownFeature.movementInput(
                            event.isSneaking(),
                            this.downState);
            if (downInput.overridden()) {
                event.setSneaking(downInput.sneak());
            }
        }

        event.setForward(safetyInput.forwardImpulse() * newTellyInputScale);
        event.setStrafe(safetyInput.strafeImpulse() * newTellyInputScale);
    }

    @EventTarget
    public void onSafeWalk(SafeWalkEvent event) {
        if (this.isNewTelly()) {
            return;
        }
        event.setSafeWalk(resolveSafeWalk(
                event.isSafeWalk() || this.safeWalk.is("Safe"),
                this.downState));
    }

    static boolean resolveSafeWalk(
            boolean baseSafeWalk,
            ScaffoldDownFeature.State downState) {
        return ScaffoldDownFeature.safeWalk(baseSafeWalk, downState).safeWalk();
    }

    static boolean useNormalFinderForTower(boolean towering, boolean wasTowering) {
        return towering || wasTowering;
    }

    static boolean shouldActivateTower(
            boolean flying,
            boolean towerEnabled,
            boolean jumpPressed,
            int blockCount) {
        return !flying && towerEnabled && jumpPressed && blockCount > 0;
    }

    static boolean shouldTargetBelowForStationaryJump(
            boolean flying,
            boolean jumpPressed,
            boolean moving,
            boolean horizontalCollision) {
        return !flying && jumpPressed && (!moving || horizontalCollision);
    }

    @EventTarget
    public void onAfterJump(PlayerAfterJumpEvent event) {
        if (this.isTelly()) {
            this.tellyPolicy.onAfterJump(this.tellyJumpTicks.getValue());
        }
        if (mc.player != null) {
            this.getActiveTower().onJump(new Tower.JumpInput(
                    (float) mc.player.getDeltaMovement().y,
                    false,
                    mc.player.getY()));
            NumericRange straight = this.strafeOnJumpStraightSpeed.getValue();
            NumericRange diagonal = this.strafeOnJumpDiagonalSpeed.getValue();
            Rotation movementRotation = RotationHandler.getCurrentRotation();
            StrafeOnJump.Decision strafeDecision = StrafeOnJump.apply(
                    new StrafeOnJump.AfterJumpInput(
                            mc.player.getDeltaMovement(),
                            this.capturePhysicalDirectionalInput(),
                            movementRotation == null
                                    ? mc.player.getYRot()
                                    : movementRotation.getYaw()),
                    new StrafeOnJump.Settings(
                            this.strafeOnJump.getValue(),
                            new StrafeOnJump.SpeedRange(
                                    (float) straight.lower(),
                                    (float) straight.upper()),
                            new StrafeOnJump.SpeedRange(
                                    (float) diagonal.lower(),
                                    (float) diagonal.upper())));
            if (strafeDecision.writesVelocity()) {
                mc.player.setDeltaMovement(strafeDecision.velocity());
            }
        }
    }

    @EventTarget(value = EventPriority.HIGH)
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null || mc.options == null) {
            this.currentTowering = false;
            this.packetBuffer.discard();
            this.clearKeptRotationImmediately();
            this.resetNewTellyState();
            this.resetCurrentPlan();
            return;
        }
        boolean newTellyActive = this.isNewTelly();
        if (newTellyActive != this.newTellyWasActive) {
            this.resetNewTellyState();
            this.safeWalkPolicy.reset();
            this.ledgePolicy.reset();
            this.currentLedgeAction = LedgeAction.NO_LEDGE;
            this.rotationRetention.clear();
            this.onTickSnapRotation = null;
            RotationHandler.clearOwnedRotation(this);
            this.newTellyWasActive = newTellyActive;
        }
        this.currentTowering = false;
        this.restoreAutoBlockSlotIfDue(mc.player.tickCount);
        if (!ScaffoldBlockItemSelection.canUpdateRotation(
                this.considerInventory.getValue(),
                mc.screen instanceof InventoryScreen,
                mc.screen instanceof AbstractContainerScreen<?>)) {
            this.updateKeptRotation(null);
            this.resetCurrentPlan();
            return;
        }
        this.restorePhysicalShift();
        this.newTellyPhysicalJump = newTellyActive
                && this.isPhysicalKeyDown(mc.options.keyJump);
        if (newTellyActive && this.isNewTellyClutchHandoffActive()) {
            this.newTellyYieldingToClutch = true;
            this.newTellyPolicy.reset();
            this.newTellyTargetState.reset();
            this.placementGate.reset();
            this.restoreAutoBlockSlotNow();
            this.packetBuffer.reset();
            this.rotationRetention.clear();
            this.onTickSnapRotation = null;
            RotationHandler.clearOwnedRotation(this);
            this.resetCurrentPlan();
            this.debugLog("new-telly:yield-clutch");
            return;
        }
        if (newTellyActive && this.newTellyYieldingToClutch) {
            this.resetNewTellyState();
            this.newTellyWasActive = true;
        }
        this.newTellyYieldingToClutch = false;
        if (!this.blink.getValue()) {
            this.packetBuffer.flush();
        }
        ZenClient.serverTickRate = this.timer.getValue().floatValue();
        if (mc.player.onGround()) {
            this.wasTowering = false;
        }
        this.applyTower();
        this.applyTickMotionFeatures();
        this.applyHeadHitter();
        if (this.isTelly()) {
            this.tellyPolicy.onGameTick(mc.player.onGround());
        }
        if (this.isOnTickRotationTiming() && !this.keepRotation.getValue()) {
            RotationHandler.clearOwnedRotation(this);
        }
        if (!this.isOnTickSnapRotationTiming()) {
            this.onTickSnapRotation = null;
        }
        if (!this.sameYInitialized) {
            this.sameYPolicy.reset(mc.player.blockPosition().getY());
            this.sameYInitialized = true;
        }
        this.sameYPolicy.onTick(
                mc.player.blockPosition().getY(),
                mc.player.onGround(),
                mc.options.keyJump.isDown());
        this.placementY = this.sameYPolicy.state().placementY();
        this.sprintController.onGameTick();

        PlanningItem planningItem = this.findPlanningItem();
        if (planningItem.hand() == InteractionHand.MAIN_HAND
                && planningItem.hotbarSlot() != -1
                && this.autoBlock.getValue()
                && this.autoBlockAlways.getValue()) {
            this.selectAutoBlockSlot(
                    planningItem.hotbarSlot(),
                    mc.player.tickCount);
        }

        Vec3 playerPosition = mc.player.position();
        Vec3 eyePosition = mc.player.getEyePosition(1.0f);
        this.rawInput = this.capturePhysicalDirectionalInput();
        this.currentOptimalLine = this.movementPlanner.getOptimalMovementLine(this.rawInput);
        this.currentPrediction = !newTellyActive && this.prediction.getValue()
                ? this.movementPrediction.predict(this.currentOptimalLine, this.rawInput)
                : new ScaffoldMovementPrediction.Prediction(null, null, null);
        Vec3 predictedPosition = this.currentPrediction.predicted()
                ? this.currentPrediction.position()
                : playerPosition;
        BlockPos actualPlayerBlockPos = mc.player.blockPosition();
        BlockPos twoBelow = actualPlayerBlockPos.below(2);
        this.downState = ScaffoldDownFeature.evaluate(
                new ScaffoldDownFeature.Settings(
                        this.technique.is("Normal") && this.down.getValue()),
                mc.options.keyShift.isDown(),
                mc.level.getBlockState(twoBelow).isFaceSturdy(
                        mc.level,
                        twoBelow,
                        Direction.UP,
                        SupportType.CENTER));
        ScaffoldEagleFeature.Decision eagleDecision = this.evaluateEagle();
        Pose predictedPose = eagleDecision.sneak() && !this.downState.shouldFallOffBlock()
                ? Pose.CROUCHING
                : Pose.STANDING;
        ItemStack planningStack = planningItem.stack();
        BlockPos playerBlockPos = BlockPos.containing(predictedPosition);
        boolean towering = this.isTowering();
        this.currentTowering = towering;
        ScaffoldCeilingFeature.Decision ceilingDecision = ScaffoldCeilingFeature.decide(
                new ScaffoldCeilingFeature.Settings(
                        this.technique.is("Normal") && this.ceiling.getValue()),
                playerBlockPos,
                mc.level.getBlockState(actualPlayerBlockPos.below()).isAir());
        int framePlacementY = this.placementY;
        if (newTellyActive) {
            ScaffoldNewTellyTargetState.TickState tickState =
                    this.newTellyTargetState.beginTick(
                            playerPosition,
                            actualPlayerBlockPos.getY(),
                            mc.player.onGround(),
                            this.newTellyPhysicalJump,
                            this.hasNewTellyRequestedLayerTarget(
                                    playerPosition,
                                    actualPlayerBlockPos.getY(),
                                    predictedPose,
                                    planningStack));
            framePlacementY = tickState.bridgeY() == null
                    ? actualPlayerBlockPos.getY() - 1
                    : tickState.bridgeY();
            this.placementY = framePlacementY;
            this.currentFindResult = this.findNewTellyTarget(
                    playerPosition,
                    predictedPose,
                    planningStack,
                    tickState.currentCell());
        } else {
            BlockPos targetedPosition;
            if (towering || this.wasTowering) {
                targetedPosition = this.getActiveTower().targetedPosition(new Tower.TargetInput(
                            playerBlockPos,
                            predictedPosition,
                            this.rawInput,
                            position -> mc.level.getBlockState(position)
                                    .isRedstoneConductor(mc.level, position)));
            } else if (this.downState.shouldGoDown()) {
                targetedPosition = ScaffoldDownFeature.targetedPosition(
                        playerBlockPos,
                        this.downState);
            } else if (ceilingDecision.active()) {
                targetedPosition = ceilingDecision.targetedPosition();
            } else if (shouldTargetBelowForStationaryJump(
                    mc.player.getAbilities().flying,
                    mc.options.keyJump.isDown(),
                    this.rawInput.isMoving(),
                    mc.player.horizontalCollision)) {
                targetedPosition = playerBlockPos.below();
            } else {
                targetedPosition = this.sameYPolicy.resolvePosition(
                            playerBlockPos,
                            this.getSameYMode(),
                            mc.player.getDeltaMovement().y);
            }
            Technique finderTechnique = useNormalFinderForTower(towering, this.wasTowering)
                    ? new NormalTechnique(new NormalTechnique.Settings(
                            this.getAimMode(),
                            this.requiresSight.getValue()))
                    : this.getActiveTechnique();
            List<Technique.TargetOffset> targetOffsets = finderTechnique.targetOffsets(
                    new Technique.TargetInput(mc.player.getYRot()));
            if (this.downState.shouldGoDown()) {
                targetOffsets = targetOffsets.stream()
                        .map(offset -> new Technique.TargetOffset(
                                offset.offset(),
                                ScaffoldDownFeature.searchOffsets(
                                        offset.searchOffsets(),
                                        this.downState),
                                offset.priority(),
                                offset.aimMode(),
                                ScaffoldDownFeature.considerFacingAwayFaces(this.downState)))
                        .toList();
            }
            this.currentFindResult = this.targetFinder.find(
                    predictedPosition,
                    predictedPose,
                    this.currentOptimalLine,
                    planningStack,
                    targetedPosition,
                    targetOffsets);
        }
        this.currentPlacement = this.currentFindResult.target();
        this.requestedRotation = this.selectRequestedRotation(eyePosition);
        this.updateKeptRotation(
                this.currentPlacement == null ? null : this.requestedRotation);
        if (this.isOnTickSnapRotationTiming()
                && (this.currentPlacement == null || this.requestedRotation == null)) {
            this.onTickSnapRotation = null;
        }
        this.currentFrame = new ScaffoldTickFrame(
                ++this.frameSequence,
                mc.player.tickCount,
                playerPosition,
                eyePosition,
                predictedPose,
                this.rawInput,
                this.currentOptimalLine,
                this.currentPrediction,
                this.currentFindResult,
                this.currentPlacement,
                this.requestedRotation,
                planningItem.hand(),
                planningItem.hotbarSlot(),
                planningStack,
                framePlacementY);
        this.applyEagle(eagleDecision);
        this.debugLog(this.currentPlacement == null ? "target:none" : "target");
    }

    @EventTarget
    public void onRotationResolved(RotationResolvedEvent event) {
        ScaffoldTickFrame frame = this.currentFrame;
        if (frame == null
                || mc.player == null
                || !frame.isCurrent(event.getTick())
                || event.getTick() != mc.player.tickCount
                || frame.frameId() == this.consumedFrameId) {
            return;
        }
        this.consumedFrameId = frame.frameId();
        this.updateLedge(frame);
        if (frame.target() == null || frame.requestedRotation() == null) {
            this.debugLog(frame.target() == null
                    ? "place:no-target"
                    : "place:no-rotation-window");
            return;
        }
        if (mc.screen != null) {
            this.debugLog("place:screen-open-skip");
            return;
        }
        if (PlayerPositionHold.isActive()) {
            this.debugLog("place:external-hold-skip");
            return;
        }
        if (!this.placementGate.canAttempt(frame.playerTick())) {
            this.debugLog("place:delay-wait until=" + this.placementGate.nextAllowedTick());
            return;
        }
        Rotation resolvedRotation = this.isOnTickRotationTiming() || this.isOnTickSnapRotationTiming()
                ? frame.requestedRotation()
                : RotationHandler.getActiveRotation(this);
        if (this.isNewTelly()) {
            ScaffoldNewTellyPolicy.TimingFrame timingFrame =
                    new ScaffoldNewTellyPolicy.TimingFrame(
                            Math.max(0, this.groundTicks),
                            Math.max(0, this.airTicks),
                            this.newTellyPhysicalJump,
                            false);
            if (!this.newTellyPolicy.canPlace(this.getNewTellySettings(), timingFrame)) {
                this.debugLog("new-telly:place-tick-wait air=" + this.airTicks
                        + " required=" + this.newTellyPlaceTick.getValue().intValue());
                return;
            }
            double pitchDifference = this.getNewTellyPitchDifference();
            if (this.newTellyPolicy.blocksDuplicatePlacement(
                    this.getNewTellySettings(),
                    pitchDifference)) {
                this.debugLog("new-telly:duplicate-block pitchDiff=" + pitchDifference);
                return;
            }
        }
        if (this.simulatePlacementAttempt(frame, resolvedRotation)) {
            return;
        }
        this.attemptPlacement(frame, resolvedRotation);
    }

    private void attemptPlacement(
            ScaffoldTickFrame frame,
            Rotation resolvedRotation) {
        if (mc.screen != null
                || mc.player == null
                || frame == null
                || frame.target() == null
                || frame.hand() == null
                || frame.hand() == InteractionHand.MAIN_HAND && frame.hotbarSlot() < 0
                || resolvedRotation == null) {
            return;
        }
        if (PlayerPositionHold.isActive()) {
            this.debugLog("place:external-hold-skip");
            return;
        }
        if (!this.placementGate.canAttempt(frame.playerTick())) {
            this.debugLog("place:delay-wait until=" + this.placementGate.nextAllowedTick());
            return;
        }

        ItemStack liveStack = frame.hand() == InteractionHand.OFF_HAND
                ? mc.player.getOffhandItem()
                : mc.player.getInventory().getItem(frame.hotbarSlot());
        if (liveStack.isEmpty()
                || !ScaffoldBlockItemSelection.isValidBlock(liveStack, mc.level, mc.player)
                || liveStack.getItem() != frame.stack().getItem()) {
            this.debugLog("place:stack-invalid");
            return;
        }

        Vec3 previousFallOffPosition = frame.movementLine() == null
                ? null
                : this.movementPrediction.getFallOffPositionOnLine(frame.movementLine());
        ScaffoldPlacementPipeline.Outcome outcome;
        int selectedSlotBeforePlacement = mc.player.getInventory().selected;
        ScaffoldTraceRecorder.attachContext(frame.frameId(), frame.target(), this);
        this.newTellyPlacementTransaction = this.isNewTelly();
        try {
            outcome = this.placementPipeline.place(
                    this,
                    frame,
                    this.getRotationTiming(),
                    resolvedRotation,
                    this.getSwingMode(),
                    this.minDist.getValue().doubleValue(),
                    new ScaffoldPlacementPipeline.AttemptOptions(
                            this.isNewTelly()
                                    && this.newTellyInteractItemBeforePlace.getValue(),
                            this.isNewTelly()));
        } finally {
            this.newTellyPlacementTransaction = false;
            ScaffoldTraceRecorder.clearContext(frame.frameId());
        }
        if (frame.hand() == InteractionHand.MAIN_HAND
                && mc.player.getInventory().selected != selectedSlotBeforePlacement) {
            this.recordAutoBlockSelection(
                    selectedSlotBeforePlacement,
                    mc.player.getInventory().selected,
                    frame.playerTick());
        } else if (frame.hand() == InteractionHand.MAIN_HAND
                && this.autoBlockSelectedSlot == frame.hotbarSlot()
                && outcome.status() != ScaffoldPlacementPipeline.Status.NO_HIT) {
            this.selectedSlotResetTick = frame.playerTick()
                    + this.autoBlockSlotResetDelay.getValue().longValue();
        }
        if (this.isOnTickSnapRotationTiming()
                && (outcome.status() == ScaffoldPlacementPipeline.Status.SUCCESS
                || outcome.status() == ScaffoldPlacementPipeline.Status.PLACE_FAILED)) {
            this.onTickSnapRotation = frame.requestedRotation() == null
                    ? null
                    : frame.requestedRotation().clone();
        }
        if (!this.isNewTelly()
                && this.keepRotation.getValue()
                && outcome.committedRotation() != null) {
            this.rotationRetention.onTarget(
                    outcome.committedRotation(),
                    this.getTicksUntilReset());
        }
        if (outcome.status() == ScaffoldPlacementPipeline.Status.ROTATION_CONFLICT) {
            this.debugLog("place:rotation-conflict source=" + outcome.rotationSource()
                    + " " + outcome.detail());
            return;
        }
        if (outcome.status() == ScaffoldPlacementPipeline.Status.NO_HIT) {
            this.debugLog("place:no-hit source=" + outcome.rotationSource()
                    + " plannedRot=" + this.formatRotation(frame.requestedRotation())
                    + " committedRot=" + this.formatRotation(outcome.committedRotation())
                    + " actualHit=" + this.formatHit(outcome.hit())
                    + " " + outcome.detail()
                    + " " + this.formatTarget(frame.target()));
            return;
        }
        this.debugLog("place:"
                + (outcome.placed()
                ? "success "
                : "fail:" + outcome.status().name().toLowerCase(Locale.ROOT)
                + ":" + outcome.detail() + " ")
                + "rotationSource=" + outcome.rotationSource() + " "
                + "detail=" + outcome.detail() + " "
                + "actualHit=" + this.formatHit(outcome.hit()) + " "
                + this.formatTarget(frame.target()));
        if (!outcome.placed()) {
            return;
        }

        this.completeSuccessfulPlacement(
                frame,
                frame.target().placedBlockPos(),
                previousFallOffPosition);
    }

    private boolean simulatePlacementAttempt(
            ScaffoldTickFrame frame,
            Rotation resolvedRotation) {
        if (!this.simulatePlacementAttempts.getValue()
                || frame == null
                || resolvedRotation == null
                || mc.player == null
                || mc.gameMode == null) {
            return false;
        }
        InteractionHand simulatedHand = this.findSuitableHeldHand();
        ItemStack heldStack = simulatedHand == null
                ? ItemStack.EMPTY
                : mc.player.getItemInHand(simulatedHand);
        BlockHitResult hit = this.placementPipeline.rayTrace(
                frame.eyePosition(),
                resolvedRotation);
        boolean suitableHand = ScaffoldBlockItemSelection.isValidBlock(
                heldStack,
                mc.level,
                mc.player);
        boolean canPlaceOnFace = false;
        if (suitableHand
                && simulatedHand != null
                && hit != null
                && heldStack.getItem() instanceof BlockItem blockItem) {
            canPlaceOnFace = blockItem.getPlacementState(new BlockPlaceContext(
                    new UseOnContext(mc.player, simulatedHand, hit))) != null;
        }
        SimulatePlacementAttempts.PlacementInput placementInput =
                new SimulatePlacementAttempts.PlacementInput(
                        suitableHand,
                        hit != null,
                        hit != null,
                        canPlaceOnFace,
                        !this.isNewTelly() && !this.sameY.is("Off"),
                        hit == null ? Integer.MIN_VALUE : hit.getBlockPos().getY(),
                        frame.placementY(),
                        hit != null && hit.getDirection() == Direction.UP,
                        mc.player.blockPosition().getY());
        SimulatePlacementAttempts.Settings settings = new SimulatePlacementAttempts.Settings(
                true,
                this.getSimulatedCpsRange(),
                this.simulateFailedAttemptsOnly.getValue());
        if (!SimulatePlacementAttempts.shouldSimulate(placementInput, settings)
                || !frame.rawInput().isMoving()
                || !this.isSimulatedClickTick()
                || simulatedHand == null) {
            return false;
        }

        ScaffoldTraceRecorder.attachContext(frame.frameId(), frame.target(), this);
        InteractionResult result;
        try {
            result = mc.gameMode.useItemOn(
                    mc.player,
                    simulatedHand,
                    hit);
            if (result.consumesAction()) {
                this.swingSimulatedAttempt(simulatedHand);
            }
        } finally {
            ScaffoldTraceRecorder.clearContext(frame.frameId());
        }
        this.debugLog("place:simulated useItemOn=" + result + " hit=" + this.formatHit(hit));
        if (!result.consumesAction()) {
            return false;
        }

        Vec3 previousFallOffPosition = frame.movementLine() == null
                ? null
                : this.movementPrediction.getFallOffPositionOnLine(frame.movementLine());
        this.completeSuccessfulPlacement(
                frame,
                hit.getBlockPos().relative(hit.getDirection()),
                previousFallOffPosition);
        return true;
    }

    private boolean isSimulatedClickTick() {
        long now = System.nanoTime();
        if (now < this.nextSimulatedClickNanos) {
            return false;
        }
        SimulatePlacementAttempts.CpsRange cpsRange = this.getSimulatedCpsRange();
        int cps = cpsRange.sample(ThreadLocalRandom.current());
        this.nextSimulatedClickNanos = now + 1_000_000_000L / Math.max(1, cps);
        return true;
    }

    private SimulatePlacementAttempts.CpsRange getSimulatedCpsRange() {
        NumericRange cps = this.simulatePlacementCps.getValue();
        return new SimulatePlacementAttempts.CpsRange(
                (int) cps.lower(),
                (int) cps.upper());
    }

    private void swingSimulatedAttempt(InteractionHand hand) {
        if (this.swing.is("Show")) {
            mc.player.swing(hand);
        } else if (this.swing.is("Hide For Client")) {
            PacketUtil.sendQueued(new ServerboundSwingPacket(hand));
        }
    }

    private void completeSuccessfulPlacement(
            ScaffoldTickFrame frame,
            BlockPos placedBlock,
            Vec3 previousFallOffPosition) {
        this.movementPlanner.trackPlacedBlock(placedBlock);
        this.movementPrediction.onPlace(frame.movementLine(), previousFallOffPosition);
        this.sprintController.onBlockPlacement();
        this.packetBuffer.onBlockPlacement(this.getBlinkSettings());
        this.eagleState = ScaffoldEagleFeature.onBlockPlacement(
                this.getEagleSettings(),
                this.eagleState,
                ThreadLocalRandom.current()).state();
        this.placementGate.onPlacementSucceeded(
                frame.playerTick(),
                this.getPlacementGateSettings());
        if (this.isNewTelly()) {
            this.newTellyTargetState.onPlacementSuccess(placedBlock);
            this.newTellyPolicy.onPlacementSuccess(this.getNewTellyPitchDifference());
        }
        this.currentPlacement = null;
    }

    @EventTarget(value = EventPriority.HIGH)
    public void onSprintDecision(SprintDecisionEvent event) {
        if (!this.sprintControl.getValue() || mc.player == null) {
            return;
        }
        event.setSprinting(this.sprintController.apply(
                event.isSprinting(),
                event.getDirectionalInput(),
                mc.player.onGround(),
                this.getSprintMode(this.sprintClientMode),
                this.getSprintMode(this.sprintServerMode),
                event.getSource()));
    }

    private Rotation selectRequestedRotation(Vec3 frameEyePosition) {
        if (this.isNewTelly()) {
            return this.selectNewTellyRotation(frameEyePosition);
        }
        boolean doNotAim = this.getTellyDecision(false).doNotAim();
        Technique active = this.getActiveTechnique();
        return active.rotation(new Technique.RotationInput(
                this.currentPlacement,
                frameEyePosition,
                mc.player.getYRot(),
                mc.player.getXRot(),
                this.rawInput,
                mc.player.onGround(),
                mc.player.getX(),
                mc.player.getZ(),
                isOnBlockEdge(0.3f),
                mc.level.getBlockState(mc.player.blockPosition().below()).isAir(),
                doNotAim,
                this.getTellyResetMode() == ScaffoldTellyFeature.ResetMode.REVERSE
                        ? Technique.AimResetMode.REVERSE
                        : Technique.AimResetMode.RESET,
                this.isCurrentTargetVisible()));
    }

    private Rotation selectNewTellyRotation(Vec3 frameEyePosition) {
        if (frameEyePosition == null || mc.player == null) {
            this.newTellyRotationSource = "no-target";
            return null;
        }
        if (this.currentPlacement == null || this.currentPlacement.rotation() == null) {
            int holdTicks = this.rawInput != null && this.rawInput.isMoving()
                    ? Math.max(1, this.newTellyRotationTick.getValue().intValue())
                    : 0;
            ScaffoldNewTellyPolicy.RotationDecision hold =
                    this.newTellyPolicy.holdForMissingTarget(holdTicks);
            this.newTellyRotationSource = hold.source();
            return hold.rotation();
        }

        Rotation serverRotation = RotationHandler.getLogicalServerRotation();
        if (serverRotation == null) {
            serverRotation = RotationHandler.getActualServerRotation();
        }
        if (serverRotation == null) {
            serverRotation = new Rotation(mc.player.getYRot(), mc.player.getXRot());
        }
        Rotation previous = this.newTellyPolicy.lastRotation();
        boolean previousHits = previous != null
                && this.placementPipeline.matchesTarget(
                        frameEyePosition,
                        previous,
                        this.currentPlacement);
        ScaffoldNewTellyPolicy.Settings settings = this.getNewTellySettings();
        boolean forceRotation = settings.safeMode()
                && settings.testOnGround()
                && this.newTellyPhysicalJump
                && this.groundTicks == 1;
        ScaffoldNewTellyPolicy.RotationDecision decision = this.newTellyPolicy.rotation(
                settings,
                new ScaffoldNewTellyPolicy.TimingFrame(
                        Math.max(0, this.groundTicks),
                        Math.max(0, this.airTicks),
                        this.newTellyPhysicalJump,
                        forceRotation),
                this.currentPlacement.rotation(),
                serverRotation,
                new Rotation(mc.player.getYRot(), mc.player.getXRot()),
                previousHits,
                new ScaffoldNewTellyPolicy.RotationNoise(
                        (float) ThreadLocalRandom.current().nextDouble(0.001, 0.005),
                        ThreadLocalRandom.current().nextFloat(),
                        (float) ThreadLocalRandom.current().nextDouble(0.0001, 0.0003),
                        (float) ThreadLocalRandom.current().nextDouble(0.001, 0.003),
                        (float) ThreadLocalRandom.current().nextDouble(0.001, 0.003)));
        if (decision.jumpDelayTicks() > 0) {
            ReflectionUtil.setJumpDelay(mc.player, decision.jumpDelayTicks());
        }
        this.newTellyRotationSource = decision.source();
        return decision.rotation();
    }

    private ScaffoldTargetFinder.FindResult findNewTellyTarget(
            Vec3 playerPosition,
            Pose predictedPose,
            ItemStack stack,
            BlockPos currentCell) {
        NewTellyTechnique newTellyTechnique = this.getActiveTechnique() instanceof NewTellyTechnique value
                ? value
                : new NewTellyTechnique();
        boolean bridgeTransitionRequested =
                this.newTellyTargetState.hasBridgeTransitionRequest();
        boolean deferredInvalidated = false;
        if (this.newTellyTargetState.hasDeferredBridgeTransition()) {
            this.newTellyTargetState.expireBridgeTransitionAfter(
                    ScaffoldNewTellyPolicy.pendingTargetTicks(this.getNewTellySettings()));
            this.newTellyTargetState.discardPendingOutside(
                    currentCell,
                    NEW_TELLY_DEFERRED_PENDING_DISTANCE,
                    NEW_TELLY_DEFERRED_PENDING_MANHATTAN_DISTANCE);
            BlockPos deferredCell = this.newTellyTargetState.pendingCell();
            if (deferredCell != null) {
                ScaffoldTargetFinder.FindResult deferred = this.targetFinder.find(
                        playerPosition,
                        predictedPose,
                        this.currentOptimalLine,
                        stack,
                        deferredCell,
                        newTellyTechnique.pendingTargetOffsets());
                if (this.isNewTellyTargetInRange(deferred, playerPosition, predictedPose)) {
                    return withFindSource(deferred, "new-telly-transition-pending");
                }
                this.newTellyTargetState.clearPending();
            }
            deferredInvalidated = true;
        }
        ScaffoldTargetFinder.FindResult fresh = this.targetFinder.find(
                playerPosition,
                predictedPose,
                this.currentOptimalLine,
                stack,
                currentCell,
                newTellyTechnique.targetOffsets(new Technique.TargetInput(mc.player.getYRot())));
        if (this.isNewTellyTargetInRange(fresh, playerPosition, predictedPose)) {
            this.newTellyTargetState.rememberPending(fresh.target().placedBlockPos());
            return withFindSource(fresh, bridgeTransitionRequested
                    ? "new-telly-transition-support-" + fresh.source()
                    : "new-telly-fresh-" + fresh.source());
        }

        if (!bridgeTransitionRequested) {
            this.newTellyTargetState.expirePendingAfter(
                    ScaffoldNewTellyPolicy.pendingTargetTicks(this.getNewTellySettings()));
            this.newTellyTargetState.discardPendingOutside(currentCell);
            BlockPos pendingCell = this.newTellyTargetState.pendingCell();
            if (pendingCell != null) {
                ScaffoldTargetFinder.FindResult pending = this.targetFinder.find(
                        playerPosition,
                        predictedPose,
                        this.currentOptimalLine,
                        stack,
                        pendingCell,
                        newTellyTechnique.pendingTargetOffsets());
                if (this.isNewTellyTargetInRange(pending, playerPosition, predictedPose)) {
                    return withFindSource(pending, "new-telly-pending");
                }
                this.newTellyTargetState.clearPending();
            }
        }

        ScaffoldTargetFinder.FindResult connector = this.findNewTellyConnectorTarget(
                playerPosition,
                predictedPose,
                stack,
                currentCell,
                newTellyTechnique);
        if (this.isNewTellyTargetInRange(connector, playerPosition, predictedPose)) {
            this.newTellyTargetState.rememberPending(connector.target().placedBlockPos());
            return withFindSource(connector, bridgeTransitionRequested
                    ? "new-telly-transition-connector"
                    : "new-telly-connector");
        }
        if (bridgeTransitionRequested) {
            return withoutFindTarget(fresh, deferredInvalidated
                    ? "new-telly-transition-aborted"
                    : "new-telly-transition-wait-support");
        }
        return withoutFindTarget(fresh, "new-telly-none");
    }

    private ScaffoldTargetFinder.FindResult findNewTellyConnectorTarget(
            Vec3 playerPosition,
            Pose predictedPose,
            ItemStack stack,
            BlockPos currentCell,
            NewTellyTechnique technique) {
        BlockPos connectorCell = this.newTellyTargetState.recentPlacementConnectorCell(
                currentCell,
                NEW_TELLY_CONNECTOR_MAX_AGE);
        if (connectorCell == null) {
            return null;
        }
        BlockPos anchorCell = this.newTellyTargetState.recentPlacedCell();
        if (anchorCell == null
                || mc.level == null
                || mc.level.getBlockState(anchorCell).isAir()) {
            return null;
        }
        ScaffoldTargetFinder.FindResult result = this.targetFinder.find(
                playerPosition,
                predictedPose,
                this.currentOptimalLine,
                stack,
                connectorCell,
                technique.pendingTargetOffsets());
        if (result == null
                || result.target() == null
                || !connectorCell.equals(result.target().placedBlockPos())
                || !anchorCell.equals(result.target().interactedBlockPos())) {
            return null;
        }
        return result;
    }

    private boolean isNewTellyTargetInRange(
            ScaffoldTargetFinder.FindResult result,
            Vec3 playerPosition,
            Pose pose) {
        if (result == null
                || result.target() == null
                || result.target().targetPoint() == null
                || playerPosition == null
                || pose == null
                || mc.player == null) {
            return false;
        }
        Vec3 eye = playerPosition.add(0.0, mc.player.getEyeHeight(pose), 0.0);
        return eye.distanceToSqr(result.target().targetPoint()) <= 4.5 * 4.5;
    }

    private boolean hasNewTellyRequestedLayerTarget(
            Vec3 playerPosition,
            int playerBlockY,
            Pose predictedPose,
            ItemStack stack) {
        Integer activeY = this.newTellyTargetState.bridgeY();
        if (activeY == null
                || playerPosition == null
                || predictedPose == null
                || stack == null
                || stack.isEmpty()
                || mc.player == null) {
            return false;
        }
        int nextBridgeY = playerBlockY - 1;
        Integer requestedBridgeY = this.newTellyTargetState.requestedBridgeY();
        int candidateY = requestedBridgeY == null
                ? Math.min(nextBridgeY, activeY + 1)
                : requestedBridgeY;
        if (candidateY <= activeY || candidateY > nextBridgeY) {
            return false;
        }
        NewTellyTechnique technique = this.getActiveTechnique() instanceof NewTellyTechnique value
                ? value
                : new NewTellyTechnique();
        BlockPos candidateCell = new BlockPos(
                (int) Math.floor(playerPosition.x),
                candidateY,
                (int) Math.floor(playerPosition.z));
        ScaffoldTargetFinder.FindResult result = this.targetFinder.find(
                playerPosition,
                predictedPose,
                this.currentOptimalLine,
                stack,
                candidateCell,
                technique.targetOffsets(new Technique.TargetInput(mc.player.getYRot())));
        return this.isNewTellyTargetInRange(result, playerPosition, predictedPose);
    }

    private static ScaffoldTargetFinder.FindResult withFindSource(
            ScaffoldTargetFinder.FindResult result,
            String source) {
        if (result == null) {
            return new ScaffoldTargetFinder.FindResult(
                    null, null, null, null, 0, 0, source);
        }
        return new ScaffoldTargetFinder.FindResult(
                result.target(),
                result.targetedPosition(),
                result.selectedOffset(),
                result.planningEye(),
                result.positionsChecked(),
                result.facesChecked(),
                source);
    }

    private static ScaffoldTargetFinder.FindResult withoutFindTarget(
            ScaffoldTargetFinder.FindResult result,
            String source) {
        if (result == null) {
            return new ScaffoldTargetFinder.FindResult(
                    null, null, null, null, 0, 0, source);
        }
        return new ScaffoldTargetFinder.FindResult(
                null,
                result.targetedPosition(),
                result.selectedOffset(),
                result.planningEye(),
                result.positionsChecked(),
                result.facesChecked(),
                source);
    }

    @EventTarget
    public void onWorldChange(WorldChangeEvent event) {
        this.clearKeptRotationImmediately();
        ScaffoldTraceRecorder.clearPendingPlacements();
        this.packetBuffer.discard();
        this.restoreAutoBlockSlotNow();
        this.sameYInitialized = false;
        this.resetPlanning();
    }

    @EventTarget
    public void onDisconnect(DisconnectEvent event) {
        this.clearKeptRotationImmediately();
        ScaffoldTraceRecorder.clearPendingPlacements();
        this.packetBuffer.discard();
        this.restoreAutoBlockSlotNow();
        this.sameYInitialized = false;
        this.resetPlanning();
    }

    private boolean isCurrentTargetVisible() {
        if (this.currentPlacement == null || this.currentPlacement.rotation() == null) {
            return false;
        }
        BlockHitResult hit = this.placementPipeline.rayTrace(this.currentPlacement.rotation());
        return hit != null
                && hit.getBlockPos().equals(this.currentPlacement.interactedBlockPos());
    }

    private ScaffoldEagleFeature.Decision evaluateEagle() {
        if (mc.player == null || mc.options == null || mc.level == null) {
            return new ScaffoldEagleFeature.Decision(false, false, false);
        }
        return ScaffoldEagleFeature.decide(
                this.getEagleSettings(),
                this.eagleState,
                new ScaffoldEagleFeature.Frame(
                        this.rawInput,
                        mc.options.keyShift.isDown(),
                        this.downState.shouldFallOffBlock(),
                        mc.player.onGround(),
                        mc.player.getAbilities().flying),
                (input, edgeDistance) -> ScaffoldSafeWalkPolicy.isCloseToEdge(
                        mc.player.getBoundingBox(),
                        input,
                        mc.player.getYRot(),
                        Math.hypot(
                                mc.player.getDeltaMovement().x,
                                mc.player.getDeltaMovement().z),
                        edgeDistance));
    }

    private void applyEagle(ScaffoldEagleFeature.Decision decision) {
        if (decision == null || mc.options == null) {
            return;
        }
        if (decision.sneak()) {
            mc.options.keyShift.setDown(true);
        }
    }

    private void updateLedge(ScaffoldTickFrame frame) {
        this.currentLedgeAction = LedgeAction.NO_LEDGE;
        if (this.isNewTelly()
                || !this.ledge.getValue()
                || mc.player == null
                || mc.options == null) {
            return;
        }
        double horizontalSpeed = Math.hypot(
                mc.player.getDeltaMovement().x,
                mc.player.getDeltaMovement().z);
        boolean closeToEdge = ScaffoldSafeWalkPolicy.isCloseToEdge(
                mc.player.getBoundingBox(),
                frame.rawInput(),
                mc.player.getYRot(),
                horizontalSpeed,
                0.1);
        Rotation targetRotation = frame.requestedRotation() == null
                ? new Rotation(mc.player.getYRot(), mc.player.getXRot())
                : frame.requestedRotation();
        Rotation currentRotation = RotationHandler.getActiveRotation(this);
        int rotationEta = this.isOnTickRotationTiming() || this.isOnTickSnapRotationTiming()
                ? 0
                : this.calculateRotationEta(currentRotation, targetRotation);
        BlockHitResult projectedHit = frame.target() == null || targetRotation == null
                ? null
                : this.placementPipeline.rayTrace(frame.eyePosition(), targetRotation);
        boolean projectedTargetMatches = projectedHit != null
                && frame.target() != null
                && projectedHit.getBlockPos().equals(frame.target().interactedBlockPos())
                && projectedHit.getDirection() == frame.target().facing();
        Technique ledgeTechnique = this.currentTowering
                ? new NormalTechnique()
                : this.getActiveTechnique();
        LedgeAction extension = ledgeTechnique.ledgeAction(
                new Technique.LedgeInput(
                        !this.currentTowering && this.technique.is("GodBridge"),
                        closeToEdge,
                        frame.target(),
                        projectedTargetMatches,
                        projectedTargetMatches,
                        this.getPlaceableBlockCount(),
                        ThreadLocalRandom.current().nextDouble(),
                        ThreadLocalRandom.current().nextDouble()));
        this.currentLedgeAction = this.ledgePolicy.decide(
                closeToEdge,
                this.getPlaceableBlockCount(),
                rotationEta,
                extension);
    }

    private int calculateRotationEta(Rotation current, Rotation target) {
        if (current == null || target == null) {
            return 0;
        }
        double maxStep = Math.max(0.1, Math.min(
                this.horizontalTurnSpeed.getValue().upper(),
                this.verticalTurnSpeed.getValue().upper()));
        return (int) Math.ceil(current.distanceTo(target) / maxStep);
    }

    private ScaffoldSafeWalkPolicy.Settings getSafeWalkSettings() {
        ScaffoldSafeWalkPolicy.Mode mode = this.safeWalk.is("Safe")
                ? ScaffoldSafeWalkPolicy.Mode.SAFE
                : this.safeWalk.is("OnEdge")
                ? ScaffoldSafeWalkPolicy.Mode.ON_EDGE
                : ScaffoldSafeWalkPolicy.Mode.NONE;
        ScaffoldSafeWalkPolicy.OnEdgeMode onEdgeMode = this.safeWalkOnEdgeMode.is("Invert")
                ? ScaffoldSafeWalkPolicy.OnEdgeMode.INVERT
                : this.safeWalkOnEdgeMode.is("Center")
                ? ScaffoldSafeWalkPolicy.OnEdgeMode.CENTER
                : ScaffoldSafeWalkPolicy.OnEdgeMode.STOP;
        NumericRange keep = this.safeWalkKeep.getValue();
        NumericRange sneak = this.safeWalkSneak.getValue();
        return new ScaffoldSafeWalkPolicy.Settings(
                mode,
                this.safeWalkEdgeDistance.getValue().doubleValue(),
                new ScaffoldSafeWalkPolicy.TickRange(
                        (int) keep.lower(),
                        (int) keep.upper()),
                onEdgeMode,
                new ScaffoldSafeWalkPolicy.TickRange(
                        (int) sneak.lower(),
                        (int) sneak.upper()),
                this.safeWalkJump.getValue());
    }

    private void restorePhysicalShift() {
        if (mc.options == null || mc.getWindow() == null) {
            return;
        }
        mc.options.keyShift.setDown(InputConstants.isKeyDown(
                mc.getWindow().getWindow(),
                mc.options.keyShift.getKey().getValue()));
    }

    private DirectionalInput capturePhysicalDirectionalInput() {
        if (mc.options == null || mc.getWindow() == null) {
            return DirectionalInput.NONE;
        }
        long window = mc.getWindow().getWindow();
        return new DirectionalInput(
                InputConstants.isKeyDown(window, mc.options.keyUp.getKey().getValue()),
                InputConstants.isKeyDown(window, mc.options.keyDown.getKey().getValue()),
                InputConstants.isKeyDown(window, mc.options.keyLeft.getKey().getValue()),
                InputConstants.isKeyDown(window, mc.options.keyRight.getKey().getValue()));
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

    private PlanningItem findPlanningItem() {
        if (mc.player == null) {
            return PlanningItem.NONE;
        }
        if (this.isNewTelly()) {
            return this.findNewTellyPlanningItem();
        }
        int selected = mc.player.getInventory().selected;
        ItemStack selectedStack = mc.player.getInventory().getItem(selected);
        boolean selectedValid = ScaffoldBlockItemSelection.isValidBlock(
                selectedStack,
                mc.level,
                mc.player);
        if (selectedValid) {
            return new PlanningItem(InteractionHand.MAIN_HAND, selected, selectedStack);
        }
        ItemStack offhandStack = mc.player.getOffhandItem();
        if (ScaffoldBlockItemSelection.isValidBlock(
                offhandStack,
                mc.level,
                mc.player)) {
            return new PlanningItem(InteractionHand.OFF_HAND, -1, offhandStack);
        }
        if (!this.autoBlock.getValue()) {
            return PlanningItem.NONE;
        }

        List<ScaffoldBlockItemSelection.Candidate<ItemStack>> candidates = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            candidates.add(ScaffoldBlockItemSelection.describe(
                    slot,
                    stack,
                    mc.level,
                    mc.player));
        }
        ScaffoldAutoBlockFeature.Decision<ItemStack> decision = ScaffoldAutoBlockFeature.decide(
                this.getAutoBlockSettings(),
                false,
                false,
                candidates);
        int slot = decision.selectedSlot();
        return slot < 0
                ? PlanningItem.NONE
                : new PlanningItem(
                        InteractionHand.MAIN_HAND,
                        slot,
                        mc.player.getInventory().getItem(slot));
    }

    private PlanningItem findNewTellyPlanningItem() {
        ItemStack offhandStack = mc.player.getOffhandItem();
        if (ScaffoldBlockItemSelection.isValidBlock(
                offhandStack,
                mc.level,
                mc.player)) {
            return new PlanningItem(InteractionHand.OFF_HAND, -1, offhandStack);
        }

        int selected = mc.player.getInventory().selected;
        ItemStack selectedStack = mc.player.getInventory().getItem(selected);
        boolean selectedValid = ScaffoldBlockItemSelection.isValidBlock(
                selectedStack,
                mc.level,
                mc.player);
        if (!this.autoBlock.getValue()) {
            return selectedValid
                    ? new PlanningItem(InteractionHand.MAIN_HAND, selected, selectedStack)
                    : PlanningItem.NONE;
        }

        List<ScaffoldNewTellyPolicy.SlotCandidate> candidates = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            candidates.add(new ScaffoldNewTellyPolicy.SlotCandidate(
                    slot,
                    Math.max(0, stack.getCount()),
                    ScaffoldBlockItemSelection.isValidBlock(
                            stack,
                            mc.level,
                            mc.player)));
        }
        int slot = ScaffoldNewTellyPolicy.selectHotbarSlot(
                this.getNewTellyBlockSlotMode(),
                selected,
                this.autoBlockDoNotUseBelowCount.getValue().intValue(),
                candidates);
        return slot < 0
                ? PlanningItem.NONE
                : new PlanningItem(
                        InteractionHand.MAIN_HAND,
                        slot,
                        mc.player.getInventory().getItem(slot));
    }

    private InteractionHand findSuitableHeldHand() {
        if (mc.player == null || mc.level == null) {
            return null;
        }
        if (ScaffoldBlockItemSelection.isValidBlock(
                mc.player.getMainHandItem(),
                mc.level,
                mc.player)) {
            return InteractionHand.MAIN_HAND;
        }
        return ScaffoldBlockItemSelection.isValidBlock(
                mc.player.getOffhandItem(),
                mc.level,
                mc.player)
                ? InteractionHand.OFF_HAND
                : null;
    }

    private ScaffoldAutoBlockFeature.Settings getAutoBlockSettings() {
        return new ScaffoldAutoBlockFeature.Settings(
                this.autoBlock.getValue(),
                this.autoBlockAlways.getValue(),
                this.autoBlockSlotResetDelay.getValue().intValue(),
                this.autoBlockDoNotUseBelowCount.getValue().intValue());
    }

    private void restoreAutoBlockSlotIfDue(long tick) {
        if (this.selectedSlotResetTick == Long.MIN_VALUE
                || tick < this.selectedSlotResetTick
                || mc.player == null) {
            return;
        }
        this.restoreAutoBlockSlotNow();
    }

    private void selectAutoBlockSlot(int slot, long tick) {
        if (mc.player == null || slot < 0 || slot >= 9) {
            return;
        }
        int previous = mc.player.getInventory().selected;
        if (previous != slot) {
            mc.player.getInventory().selected = slot;
            this.recordAutoBlockSelection(previous, slot, tick);
        } else if (this.autoBlockSelectedSlot == slot) {
            this.selectedSlotResetTick = tick
                    + this.autoBlockSlotResetDelay.getValue().longValue();
        }
    }

    private void recordAutoBlockSelection(int previousSlot, int selectedSlot, long tick) {
        if (!this.autoBlock.getValue()
                || previousSlot < 0 || previousSlot >= 9
                || selectedSlot < 0 || selectedSlot >= 9
                || previousSlot == selectedSlot) {
            return;
        }
        if (this.autoBlockRestoreSlot < 0) {
            this.autoBlockRestoreSlot = previousSlot;
        }
        this.autoBlockSelectedSlot = selectedSlot;
        this.selectedSlotResetTick = tick
                + this.autoBlockSlotResetDelay.getValue().longValue();
    }

    private void restoreAutoBlockSlotNow() {
        if (mc.player != null
                && this.autoBlockRestoreSlot >= 0
                && this.autoBlockRestoreSlot < 9
                && (this.autoBlockSelectedSlot < 0
                || mc.player.getInventory().selected == this.autoBlockSelectedSlot)) {
            mc.player.getInventory().selected = this.autoBlockRestoreSlot;
        }
        this.autoBlockRestoreSlot = -1;
        this.autoBlockSelectedSlot = -1;
        this.selectedSlotResetTick = Long.MIN_VALUE;
        this.nextSimulatedClickNanos = 0L;
    }

    private ScaffoldPlacementGate.Settings getPlacementGateSettings() {
        NumericRange delayRange = this.delay.getValue();
        return new ScaffoldPlacementGate.Settings(
                (int) delayRange.lower(),
                (int) delayRange.upper(),
                this.minDist.getValue().doubleValue());
    }

    private ScaffoldSameYPolicy.Mode getSameYMode() {
        if (this.sameY.is("On")) {
            return ScaffoldSameYPolicy.Mode.ON;
        }
        if (this.sameY.is("Falling")) {
            return ScaffoldSameYPolicy.Mode.FALLING;
        }
        if (this.sameY.is("Hypixel")) {
            return ScaffoldSameYPolicy.Mode.HYPIXEL;
        }
        return ScaffoldSameYPolicy.Mode.OFF;
    }

    private ScaffoldNewTellyPolicy.Settings getNewTellySettings() {
        return new ScaffoldNewTellyPolicy.Settings(
                this.newTellyAlwaysUpdateRotation.getValue(),
                this.newTellyPlaceTick.getValue().intValue(),
                this.newTellyRotationTick.getValue().intValue(),
                this.newTellyNoUpTelly.getValue(),
                this.newTellyHeypixelUpTelly.getValue(),
                this.newTellySafeMode.getValue(),
                this.newTellyTestOnGround.getValue(),
                this.newTellyFixRotation.getValue(),
                this.newTellySlowUpTelly.getValue(),
                this.newTellyDuplicateRotPlace.getValue(),
                this.newTellyInteractItemBeforePlace.getValue(),
                this.getNewTellyJumpMode(),
                this.getNewTellyBlockSlotMode());
    }

    private ScaffoldNewTellyPolicy.JumpMode getNewTellyJumpMode() {
        if (this.newTellyJumpMode.is("Parkour")) {
            return ScaffoldNewTellyPolicy.JumpMode.PARKOUR;
        }
        if (this.newTellyJumpMode.is("None")) {
            return ScaffoldNewTellyPolicy.JumpMode.NONE;
        }
        return ScaffoldNewTellyPolicy.JumpMode.NORMAL;
    }

    private ScaffoldNewTellyPolicy.BlockSlotMode getNewTellyBlockSlotMode() {
        return this.newTellyBlockSlotMode.is("Most Blocks")
                ? ScaffoldNewTellyPolicy.BlockSlotMode.MOST_BLOCKS
                : ScaffoldNewTellyPolicy.BlockSlotMode.FARTHEST;
    }

    private boolean[] getNewTellyParkourAir() {
        if (mc.player == null || mc.level == null) {
            return new boolean[]{false, false};
        }
        double yaw = Math.toRadians(mc.player.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        BlockPos first = new BlockPos(
                (int) (mc.player.getX() + forwardX),
                (int) (mc.player.getY() - 0.1),
                (int) (mc.player.getZ() + forwardZ));
        BlockPos second = new BlockPos(
                (int) (mc.player.getX() + forwardX * 2.0),
                (int) (mc.player.getY() - 0.1),
                (int) (mc.player.getZ() + forwardZ * 2.0));
        return new boolean[]{
                mc.level.getBlockState(first).isAir(),
                mc.level.getBlockState(second).isAir()
        };
    }

    private boolean isNewTellyClutchHandoffActive() {
        return Clutch.INSTANCE != null && Clutch.INSTANCE.isActivelyRescuing();
    }

    private double getNewTellyPitchDifference() {
        Rotation sent = RotationHandler.sentRotation;
        Rotation previous = RotationHandler.prevSentRotation;
        if (sent == null || previous == null) {
            return 0.0;
        }
        return Math.abs(sent.getPitch() - previous.getPitch());
    }

    private ScaffoldTellyFeature.ResetMode getTellyResetMode() {
        return this.tellyResetMode.is("Reverse")
                ? ScaffoldTellyFeature.ResetMode.REVERSE
                : ScaffoldTellyFeature.ResetMode.RESET;
    }

    private ScaffoldSprintControl.SprintMode getSprintMode(ModeValue value) {
        if (value.is("ForceSprint")) {
            return ScaffoldSprintControl.SprintMode.FORCE_SPRINT;
        }
        if (value.is("ForceNoSprint")) {
            return ScaffoldSprintControl.SprintMode.FORCE_NO_SPRINT;
        }
        if (value.is("NoSprintOnPlace")) {
            return ScaffoldSprintControl.SprintMode.NO_SPRINT_ON_PLACE;
        }
        if (value.is("NoSprintOnGround")) {
            return ScaffoldSprintControl.SprintMode.NO_SPRINT_ON_GROUND;
        }
        return ScaffoldSprintControl.SprintMode.DO_NOT_CHANGE;
    }

    private boolean isTelly() {
        return this.technique.is("Normal")
                && (this.telly.getValue() || this.mode.is("Telly"));
    }

    private boolean isNewTelly() {
        return this.technique.is("New Telly");
    }

    boolean migrateLegacyMode() {
        if (!this.mode.is("Telly")) {
            return false;
        }
        this.telly.setValue(true);
        this.technique.setValue("Normal");
        this.mode.setValue("Normal");
        return true;
    }

    private Technique getActiveTechnique() {
        String key = this.technique.getValue()
                + ":" + this.rotationMode.getValue()
                + ":" + this.requiresSight.getValue()
                + ":" + this.expandLength.getValue()
                + ":" + this.godBridgeJump.getValue()
                + ":" + this.godBridgeSneak.getValue()
                + ":" + this.godBridgeStopInput.getValue()
                + ":" + this.godBridgeBackwards.getValue()
                + ":" + this.godBridgeForceSneakBelowCount.getValue()
                + ":" + this.godBridgeSneakTime.getValue()
                + ":" + this.breezilyEdgeDistance.getValue();
        if (key.equals(this.activeTechniqueKey)) {
            return this.activeTechnique;
        }

        if (this.isNewTelly()) {
            this.activeTechnique = new NewTellyTechnique();
        } else if (this.technique.is("Expand")) {
            this.activeTechnique = new ExpandTechnique(new ExpandTechnique.Settings(
                    this.expandLength.getValue().intValue()));
        } else if (this.technique.is("GodBridge")) {
            Set<GodBridgeTechnique.LedgeMode> modes = EnumSet.noneOf(
                    GodBridgeTechnique.LedgeMode.class);
            if (this.godBridgeJump.getValue()) {
                modes.add(GodBridgeTechnique.LedgeMode.JUMP);
            }
            if (this.godBridgeSneak.getValue()) {
                modes.add(GodBridgeTechnique.LedgeMode.SNEAK);
            }
            if (this.godBridgeStopInput.getValue()) {
                modes.add(GodBridgeTechnique.LedgeMode.STOP_INPUT);
            }
            if (this.godBridgeBackwards.getValue()) {
                modes.add(GodBridgeTechnique.LedgeMode.BACKWARDS);
            }
            if (modes.isEmpty()) {
                modes.add(GodBridgeTechnique.LedgeMode.JUMP);
            }
            NumericRange sneak = this.godBridgeSneakTime.getValue();
            this.activeTechnique = new GodBridgeTechnique(new GodBridgeTechnique.Settings(
                    modes,
                    this.godBridgeForceSneakBelowCount.getValue().intValue(),
                    (int) sneak.lower(),
                    (int) sneak.upper()));
        } else if (this.technique.is("Breezily")) {
            NumericRange distance = this.breezilyEdgeDistance.getValue();
            this.activeTechnique = new BreezilyTechnique(new BreezilyTechnique.Settings(
                    distance.lower(),
                    distance.upper()));
        } else {
            this.activeTechnique = new NormalTechnique(new NormalTechnique.Settings(
                    this.getAimMode(),
                    this.requiresSight.getValue()));
        }
        this.activeTechniqueKey = key;
        return this.activeTechnique;
    }

    private Technique.AimMode getAimMode() {
        for (Technique.AimMode aimMode : Technique.AimMode.values()) {
            if (aimMode.name().replace("_", "")
                    .equalsIgnoreCase(this.rotationMode.getValue())) {
                return aimMode;
            }
        }
        return Technique.AimMode.STABILIZED;
    }

    private Tower getActiveTower() {
        String towerMode = this.isNewTelly() ? "None" : this.tower.getValue();
        String key = towerMode
                + ":" + this.towerMotion.getValue()
                + ":" + this.towerTriggerHeight.getValue()
                + ":" + this.towerSlow.getValue()
                + ":" + this.towerPulldownTrigger.getValue()
                + ":" + this.towerKarhuTimer.getValue()
                + ":" + this.towerKarhuTrigger.getValue()
                + ":" + this.towerKarhuPulldown.getValue();
        if (key.equals(this.activeTowerKey)) {
            return this.activeTower;
        }
        this.activeTower.reset();
        if (towerMode.equals("Motion")) {
            this.activeTower = new MotionTower(new MotionTower.Settings(
                    this.towerMotion.getValue().doubleValue(),
                    this.towerTriggerHeight.getValue().doubleValue(),
                    this.towerSlow.getValue().doubleValue()));
        } else if (towerMode.equals("Pulldown")) {
            this.activeTower = new PulldownTower(new PulldownTower.Settings(
                    this.towerPulldownTrigger.getValue().doubleValue()));
        } else if (towerMode.equals("Karhu")) {
            this.activeTower = new KarhuTower(new KarhuTower.Settings(
                    this.towerKarhuTimer.getValue().doubleValue(),
                    this.towerKarhuTrigger.getValue().doubleValue(),
                    this.towerKarhuPulldown.getValue()));
        } else if (towerMode.equals("Vulcan")) {
            this.activeTower = new VulcanTower();
        } else if (towerMode.equals("Hypixel")) {
            this.activeTower = new HypixelTower();
        } else {
            this.activeTower = new NoneTower();
        }
        this.activeTowerKey = key;
        return this.activeTower;
    }

    private boolean isTowering() {
        if (mc.player == null || mc.options == null) {
            return false;
        }
        boolean flying = mc.player.getAbilities().flying;
        boolean towering = shouldActivateTower(
                flying,
                !this.isNewTelly() && !this.tower.is("None"),
                mc.options.keyJump.isDown(),
                this.getPlaceableBlockCount());
        if (towering) {
            this.wasTowering = true;
        } else if (flying) {
            this.wasTowering = false;
        }
        return towering;
    }

    private void applyTower() {
        if (mc.player == null || mc.level == null || mc.options == null) {
            return;
        }
        Tower towerMode = this.getActiveTower();
        if (mc.player.getAbilities().flying) {
            towerMode.reset();
            this.towerPacketOffset = Vec3.ZERO;
            this.wasTowering = false;
            return;
        }
        boolean blockBelow = mc.level.getCollisions(
                mc.player,
                mc.player.getBoundingBox()
                        .inflate(0.5, 0.0, 0.5)
                        .move(0.0, -1.05, 0.0))
                .iterator()
                .hasNext();
        Rotation movementRotation = RotationHandler.getCurrentRotation();
        TickMotionDecision decision = towerMode.tick(new Tower.TickInput(
                mc.options.keyJump.isDown(),
                this.getPlaceableBlockCount(),
                blockBelow,
                mc.player.onGround(),
                mc.player.tickCount,
                this.airTicks,
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getDeltaMovement(),
                this.capturePhysicalDirectionalInput(),
                movementRotation == null
                        ? mc.player.getYRot()
                        : movementRotation.getYaw(),
                ThreadLocalRandom.current().nextDouble()));
        this.towerPacketOffset = decision.outgoingMoveOffset();
        if (decision.hasPositionSnap()) {
            mc.player.setPos(
                    mc.player.getX(),
                    decision.snappedY(),
                    mc.player.getZ());
        }
        if (decision.velocityChanged()) {
            mc.player.setDeltaMovement(decision.velocity());
        }
        if (decision.hasTimerRequest()) {
            ZenClient.serverTickRate = decision.timerSpeed();
        }
        if (decision.awardJumpStat()) {
            mc.player.awardStat(Stats.JUMP);
        }
    }

    private void applyTickMotionFeatures() {
        if (mc.player == null) {
            return;
        }
        Vec3 velocity = Acceleration.apply(
                mc.player.getDeltaMovement(),
                mc.player.onGround(),
                new Acceleration.Settings(
                        this.acceleration.getValue(),
                        this.accelerationSpeedMultiplier.getValue().floatValue(),
                        this.accelerationOnlyOnGround.getValue()));
        mc.player.setDeltaMovement(velocity);

        boolean strafeEnabled = this.isStrafeFeatureEnabled();
        if (strafeEnabled && !this.strafeFeatureActive) {
            this.strafeController.onEnabled();
        } else if (!strafeEnabled && this.strafeFeatureActive) {
            Strafe.Decision disabled = this.strafeController.onDisabled(
                    velocity,
                    new Strafe.Settings(
                            true,
                            this.strafeSpeed.getValue().floatValue(),
                            this.strafeHypixel.getValue(),
                            this.strafeOnlyOnGround.getValue()));
            if (disabled.writesVelocity()) {
                velocity = disabled.velocity();
                mc.player.setDeltaMovement(velocity);
            }
        }
        this.strafeFeatureActive = strafeEnabled;

        DirectionalInput input = this.capturePhysicalDirectionalInput();
        Rotation movementRotation = RotationHandler.getCurrentRotation();
        var speedEffect = mc.player.getEffect(MobEffects.MOVEMENT_SPEED);
        Strafe.Decision strafeDecision = this.strafeController.tick(
                new Strafe.TickInput(
                        velocity,
                        input,
                        movementRotation == null
                                ? mc.player.getYRot()
                                : movementRotation.getYaw(),
                        input.isMoving(),
                        mc.player.onGround(),
                        mc.player.tickCount,
                        speedEffect == null ? -1 : speedEffect.getAmplifier()),
                this.getStrafeSettings());
        if (strafeDecision.writesVelocity()) {
            mc.player.setDeltaMovement(strafeDecision.velocity());
        }
    }

    private void applyHeadHitter() {
        if (mc.player == null || mc.level == null) {
            return;
        }
        ScaffoldHeadHitterFeature.Decision decision = ScaffoldHeadHitterFeature.decide(
                new ScaffoldHeadHitterFeature.Settings(
                        this.technique.is("Normal") && this.headHitter.getValue()),
                new ScaffoldHeadHitterFeature.Frame(
                        mc.level.getBlockState(mc.player.blockPosition().above(2)).isAir(),
                        mc.player.onGround(),
                        this.capturePhysicalDirectionalInput().isMoving()));
        if (decision.shouldJumpFromGround()) {
            mc.player.jumpFromGround();
        }
    }

    private Strafe.Settings getStrafeSettings() {
        return new Strafe.Settings(
                this.isStrafeFeatureEnabled(),
                this.strafeSpeed.getValue().floatValue(),
                this.strafeHypixel.getValue(),
                this.strafeOnlyOnGround.getValue());
    }

    private boolean isStrafeFeatureEnabled() {
        return this.strafe.getValue() || AutoSpeed.requestsSpeed(
                this.isEnabled(),
                new AutoSpeed.Settings(this.autoSpeed.getValue()));
    }

    private Blink.Settings getBlinkSettings() {
        NumericRange time = this.blinkTime.getValue();
        Set<Blink.FlushOn> flushOn = EnumSet.noneOf(Blink.FlushOn.class);
        if (this.blinkFlushOnPlace.getValue()) {
            flushOn.add(Blink.FlushOn.PLACE);
        }
        if (this.blinkFlushOnTowering.getValue()) {
            flushOn.add(Blink.FlushOn.TOWERING);
        }
        if (this.blinkFlushOnSneaking.getValue()) {
            flushOn.add(Blink.FlushOn.SNEAKING);
        }
        if (this.blinkFlushOnNotSneaking.getValue()) {
            flushOn.add(Blink.FlushOn.NOT_SNEAKING);
        }
        if (this.blinkFlushOnGround.getValue()) {
            flushOn.add(Blink.FlushOn.ON_GROUND);
        }
        if (this.blinkFlushInAir.getValue()) {
            flushOn.add(Blink.FlushOn.IN_AIR);
        }
        return new Blink.Settings(
                this.blink.getValue(),
                new Blink.TimeRange((int) time.lower(), (int) time.upper()),
                flushOn);
    }

    private ScaffoldEagleFeature.Settings getEagleSettings() {
        NumericRange blocks = this.eagleBlocksToEagle.getValue();
        return new ScaffoldEagleFeature.Settings(
                (this.technique.is("Normal") || this.isNewTelly())
                        && this.eagle.getValue(),
                new ScaffoldEagleFeature.BlocksToEagleRange(
                        (int) blocks.lower(),
                        (int) blocks.upper()),
                this.eagleEdgeDistance.getValue().doubleValue(),
                this.eagleOnlyOnGround.getValue());
    }

    private ScaffoldTellyFeature.Settings getTellySettings() {
        NumericRange jump = this.tellyJumpTicks.getValue();
        return new ScaffoldTellyFeature.Settings(
                this.isTelly(),
                this.getTellyResetMode(),
                this.tellyStraightTicks.getValue().intValue(),
                new ScaffoldTellyFeature.JumpTickRange(
                        (int) jump.lower(),
                        (int) jump.upper()),
                this.tellyAimOnTower.getValue());
    }

    private ScaffoldTellyFeature.Decision getTellyDecision(boolean jump) {
        DirectionalInput input = this.rawInput == null
                ? DirectionalInput.NONE
                : this.rawInput;
        return ScaffoldTellyFeature.decide(
                this.getTellySettings(),
                new ScaffoldTellyFeature.TimingFrame(
                        jump,
                        input.isMoving(),
                        this.getPlaceableBlockCount(),
                        mc.player != null && mc.player.onGround(),
                        RotationHandler.getCurrentRotation() != null,
                        Math.max(0, this.airTicks),
                        Math.max(0, this.tellyPolicy.ticksUntilJump()),
                        Math.max(0, this.tellyPolicy.jumpTicks()),
                        this.isTowering()));
    }

    private boolean isOnTickRotationTiming() {
        return !this.isNewTelly() && this.rotationTiming.is("On Tick");
    }

    private boolean isOnTickSnapRotationTiming() {
        return !this.isNewTelly() && this.rotationTiming.is("On Tick Snap");
    }

    private ScaffoldPlacementPipeline.RotationTiming getRotationTiming() {
        if (this.isOnTickSnapRotationTiming()) {
            return ScaffoldPlacementPipeline.RotationTiming.ON_TICK_SNAP;
        }
        return this.isOnTickRotationTiming()
                ? ScaffoldPlacementPipeline.RotationTiming.ON_TICK
                : ScaffoldPlacementPipeline.RotationTiming.NORMAL;
    }

    private ScaffoldPlacementPipeline.SwingMode getSwingMode() {
        if (this.swing.is("Show")) {
            return ScaffoldPlacementPipeline.SwingMode.SHOW;
        }
        if (this.swing.is("None")) {
            return ScaffoldPlacementPipeline.SwingMode.NONE;
        }
        return ScaffoldPlacementPipeline.SwingMode.HIDE_FOR_CLIENT;
    }

    private static double sampleRange(NumericRange range) {
        if (range == null || range.upper() <= range.lower()) {
            return range == null ? 0.0 : range.lower();
        }
        return ThreadLocalRandom.current().nextDouble(range.lower(), range.upper());
    }

    @Override
    public Rotation getRotation() {
        if (this.isNewTelly()) {
            return this.requestedRotation == null ? null : this.requestedRotation.clone();
        }
        if (this.keepRotation.getValue()) {
            return this.rotationRetention.rotation();
        }
        if (this.isOnTickSnapRotationTiming()) {
            return this.onTickSnapRotation == null
                    ? null
                    : this.onTickSnapRotation.clone();
        }
        return this.requestedRotation;
    }

    @Override
    public boolean isRotationActive() {
        if (this.isNewTelly()) {
            return this.isEnabled()
                    && !this.newTellyYieldingToClutch
                    && this.requestedRotation != null;
        }
        if (this.keepRotation.getValue()) {
            return this.isEnabled() && this.rotationRetention.active();
        }
        return this.isEnabled()
                && !this.isOnTickRotationTiming()
                && (!this.isOnTickSnapRotationTiming()
                ? this.requestedRotation != null
                : this.onTickSnapRotation != null);
    }

    @Override
    public RotationApplyMode getApplyMode() {
        if (this.isNewTelly()) {
            return RotationApplyMode.SILENT;
        }
        if (this.keepRotation.getValue()) {
            return RotationApplyMode.SILENT;
        }
        return this.getMovementCorrection() == MovementCorrection.CHANGE_LOOK
                ? RotationApplyMode.CHANGE_LOOK
                : RotationApplyMode.SILENT;
    }

    @Override
    public MovementCorrection getMovementCorrection() {
        if (this.isNewTelly()) {
            return MovementCorrection.SILENT;
        }
        if (this.movementCorrection.is("Off")) {
            return MovementCorrection.OFF;
        }
        if (this.movementCorrection.is("Strict")) {
            return MovementCorrection.STRICT;
        }
        if (this.movementCorrection.is("ChangeLook")) {
            return this.keepRotation.getValue()
                    ? MovementCorrection.SILENT
                    : MovementCorrection.CHANGE_LOOK;
        }
        return MovementCorrection.SILENT;
    }

    @Override
    public SmoothMode getSmoothMode() {
        if (this.isNewTelly()) {
            return SmoothMode.SNAP;
        }
        return this.angleSmooth.is("Sigmoid") ? SmoothMode.SIGMOID : SmoothMode.LINEAR;
    }

    @Override
    public int getSmoothDurationTicks() {
        return 6;
    }

    @Override
    public double getMaxYawSpeed() {
        return sampleRange(this.horizontalTurnSpeed.getValue());
    }

    @Override
    public double getMaxPitchSpeed() {
        return sampleRange(this.verticalTurnSpeed.getValue());
    }

    @Override
    public double getSmoothSteepness() {
        return this.smoothSteepness.getValue().doubleValue();
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
    public boolean shouldHumanizeRotation() {
        return false;
    }

    @Override
    public boolean shouldSnapToSensitivity() {
        return !this.isNewTelly() || this.newTellyFixRotation.getValue();
    }

    @Override
    public boolean shouldNormalizeYawForServerPackets() {
        return this.isNewTelly();
    }

    @Override
    public int getTicksUntilReset() {
        return Math.max(1, this.resetTicks.getValue().intValue());
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
        return 50;
    }

    @EventTarget
    public void onRender(RenderEvent event) {
        if (this.currentPlacement == null || mc.gameRenderer == null) {
            return;
        }
        PoseStack poseStack = event.poseStack();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        AABB box = new AABB(this.currentPlacement.placedBlockPos());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        Color color = new Color(74, 144, 226);
        RenderSystem.setShaderColor(
                color.getRed() / 255.0f,
                color.getGreen() / 255.0f,
                color.getBlue() / 255.0f,
                0.25f);
        RenderUtil.drawSolidBox(box, poseStack);
        RenderSystem.setShaderColor(
                color.getRed() / 255.0f,
                color.getGreen() / 255.0f,
                color.getBlue() / 255.0f,
                0.75f);
        RenderUtil.drawOutlineBox(box, poseStack);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        poseStack.popPose();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null || DynamicIsland.shouldRenderScaffoldCounter()) {
            return;
        }
        int blockCount = this.getBlockSlot();
        if (blockCount == 0) {
            return;
        }
        String countText = String.valueOf(blockCount);
        String suffix = " Blocks";
        GuiGraphics graphics = event.guiGraphics();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        float centerX = width / 2.0f;
        float y = height / 2.0f - 20.0f;
        int textWidth = mc.font.width(countText + suffix);
        int x = (int) (centerX - textWidth / 2.0f);
        graphics.drawString(mc.font, countText, x, (int) y, -11890462);
        graphics.drawString(mc.font, suffix, x + mc.font.width(countText), (int) y, -1);
    }

    public boolean isCounterOnIslandActive() {
        return this.isEnabled()
                && this.scaffoldCounterOnIsland.getValue()
                && this.getPlaceableBlockCount() > 0;
    }

    public int getPlaceableBlockCount() {
        return this.getBlockSlot();
    }

    public ItemStack getCounterBlockItem() {
        if (mc.player == null || mc.level == null) {
            return ItemStack.EMPTY;
        }
        ItemStack selected = mc.player.getMainHandItem();
        if (ScaffoldBlockItemSelection.isValidBlock(selected, mc.level, mc.player)) {
            return selected;
        }
        ItemStack offhand = mc.player.getOffhandItem();
        if (ScaffoldBlockItemSelection.isValidBlock(offhand, mc.level, mc.player)) {
            return offhand;
        }
        if (!this.autoBlock.getValue()) {
            return ItemStack.EMPTY;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (ScaffoldBlockItemSelection.isValidBlock(stack, mc.level, mc.player)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private int getBlockSlot() {
        if (mc.player == null || mc.level == null) {
            return 0;
        }
        int total = ScaffoldBlockItemSelection.isValidBlock(
                mc.player.getOffhandItem(),
                mc.level,
                mc.player)
                ? mc.player.getOffhandItem().getCount()
                : 0;
        if (!this.autoBlock.getValue()) {
            ItemStack selected = mc.player.getMainHandItem();
            return total + (ScaffoldBlockItemSelection.isValidBlock(
                    selected,
                    mc.level,
                    mc.player)
                    ? selected.getCount()
                    : 0);
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (ScaffoldBlockItemSelection.isValidBlock(stack, mc.level, mc.player)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private void resetPlanning() {
        this.groundTicks = 0;
        this.airTicks = 0;
        this.lastCounterTick = -1;
        this.debugTicks = 0;
        this.lastDebugState = null;
        this.frameSequence = 0L;
        this.consumedFrameId = -1L;
        this.rawInput = DirectionalInput.NONE;
        this.currentLedgeAction = LedgeAction.NO_LEDGE;
        this.safeWalkPolicy.reset();
        this.ledgePolicy.reset();
        this.strafeController.reset();
        this.sprintController.onGameTick();
        this.placementGate.reset();
        this.tellyPolicy.reset(this.tellyJumpTicks.getValue());
        this.eagleState = ScaffoldEagleFeature.reset(
                this.getEagleSettings(),
                ThreadLocalRandom.current());
        this.selectedSlotResetTick = Long.MIN_VALUE;
        this.autoBlockRestoreSlot = -1;
        this.autoBlockSelectedSlot = -1;
        this.rotationRetention.clear();
        this.onTickSnapRotation = null;
        this.wasTowering = false;
        this.currentTowering = false;
        this.activeTechniqueKey = "";
        this.activeTower.reset();
        this.activeTowerKey = "";
        this.towerPacketOffset = Vec3.ZERO;
        this.resetNewTellyState();
        this.newTellyWasActive = this.isNewTelly();
        this.resetCurrentPlan();
        this.movementPlanner.reset();
        this.movementPrediction.reset();
    }

    private void resetCurrentPlan() {
        this.currentFrame = null;
        this.currentPlacement = null;
        this.requestedRotation = null;
        this.currentOptimalLine = null;
        this.currentPrediction = null;
        this.currentFindResult = null;
    }

    private void updateKeptRotation(Rotation targetRotation) {
        if (this.isNewTelly() || !this.keepRotation.getValue()) {
            this.rotationRetention.clear();
            return;
        }
        if (targetRotation != null) {
            this.rotationRetention.onTarget(targetRotation, this.getTicksUntilReset());
            return;
        }
        this.rotationRetention.onMissingTarget(RotationHandler.getActiveRotation(this));
    }

    private void clearKeptRotationImmediately() {
        this.rotationRetention.clear();
        RotationHandler.clearOwnedRotation(this);
    }

    private void resetNewTellyState() {
        this.newTellyPolicy.reset();
        this.newTellyTargetState.reset();
        this.newTellyYieldingToClutch = false;
        this.newTellyPhysicalJump = false;
        this.newTellyInputJumpBefore = false;
        this.newTellyInputJumpAfter = false;
        this.newTellyInputJumpDelay = -1;
        this.newTellyPlacementTransaction = false;
        this.newTellyRotationSource = "idle";
    }

    static CounterState advanceCounters(
            int lastTick,
            int groundTicks,
            int airTicks,
            int currentTick,
            boolean onGround) {
        if (lastTick == currentTick) {
            return new CounterState(lastTick, groundTicks, airTicks);
        }
        return onGround
                ? new CounterState(currentTick, groundTicks + 1, 0)
                : new CounterState(currentTick, 0, airTicks + 1);
    }

    private void debugLog(String phase) {
        if (!this.debug.getValue() || mc.player == null) {
            return;
        }
        String state = phase
                + " frame=" + (this.currentFrame == null
                ? "none" : this.currentFrame.frameId())
                + " mode=" + this.mode.getValue()
                + " effectiveMode=" + (this.isNewTelly()
                ? "NewTelly" : this.isTelly() ? "TellyV2" : "NormalV2")
                + " rotationTiming=" + this.rotationTiming.getValue()
                + "/" + this.getRotationTiming()
                + " sameY=" + this.sameY.getValue()
                + " placementY=" + this.placementY
                + " tower=" + this.tower.getValue()
                + " towering=" + this.currentTowering
                + " wasTowering=" + this.wasTowering
                + " flying=" + mc.player.getAbilities().flying
                + " onGround=" + mc.player.onGround()
                + " pos=" + this.formatVec(mc.player.position())
                + " velocity=" + this.formatVec(mc.player.getDeltaMovement())
                + " towerPacketOffset=" + this.formatVec(this.towerPacketOffset)
                + " correction=" + this.getMovementCorrection()
                + " rawInput=" + this.rawInput
                + " correctedInput=" + DirectionalInput.fromImpulses(
                mc.player.input.forwardImpulse,
                mc.player.input.leftImpulse)
                + " source=" + (this.currentFindResult == null ? "none" : this.currentFindResult.source())
                + " root=" + (this.currentFindResult == null
                ? "null" : this.formatPos(this.currentFindResult.targetedPosition()))
                + " offset=" + (this.currentFindResult == null
                ? "null" : this.formatPos(this.currentFindResult.selectedOffset()))
                + " linePos=" + (this.currentOptimalLine == null
                ? "null" : this.formatVec(this.currentOptimalLine.position()))
                + " lineDir=" + (this.currentOptimalLine == null
                ? "null" : this.formatVec(this.currentOptimalLine.direction()))
                + " predicted=" + (this.currentPrediction == null
                ? "null" : this.formatVec(this.currentPrediction.position()))
                + " frameEye=" + (this.currentFrame == null
                ? "null" : this.formatVec(this.currentFrame.eyePosition()))
                + " planningEye=" + (this.currentFindResult == null
                ? "null" : this.formatVec(this.currentFindResult.planningEye()))
                + " learnedOffset=" + (this.currentPrediction == null
                ? "null" : this.formatVec(this.currentPrediction.averagePlacementOffset()))
                + " telly=" + this.tellyPolicy.ticksUntilJump() + "/" + this.tellyPolicy.jumpTicks()
                + " newTelly=" + this.newTellyRotationSource
                + " pending=" + this.formatPos(this.newTellyTargetState.pendingCell())
                + " pendingAge=" + this.newTellyTargetState.pendingAge()
                + " requestedBridgeY=" + this.newTellyTargetState.requestedBridgeY()
                + " bridgeTransitionAge="
                + this.newTellyTargetState.bridgeTransitionAge()
                + " bridgeBlocked=" + this.newTellyTargetState.bridgePromotionBlocked()
                + " recentPlaced=" + this.formatPos(this.newTellyTargetState.recentPlacedCell())
                + " recentPlacedAge=" + this.newTellyTargetState.recentPlacedAge()
                + " bridgeTopGrace=" + this.newTellyTargetState.bridgeTopGraceTicks()
                + " inputJump=" + this.newTellyInputJumpBefore
                + "/" + this.newTellyInputJumpAfter
                + " jumpDelay=" + this.newTellyInputJumpDelay
                + " yield=" + this.newTellyYieldingToClutch
                + " physicalJump=" + this.newTellyPhysicalJump
                + " pitchDiff=" + this.getNewTellyPitchDifference()
                + " ground=" + this.groundTicks
                + " air=" + this.airTicks
                + " target=" + this.formatTarget(this.currentPlacement)
                + " requestedRot=" + this.formatRotation(this.requestedRotation)
                + " rotationPhase=" + RotationHandler.getRotationPhase()
                + " currentRot=" + this.formatRotation(RotationHandler.getCurrentRotation())
                + " activeRot=" + this.formatRotation(RotationHandler.getActiveRotation(this))
                + " activeCorrection=" + RotationHandler.getActiveMovementCorrection(this)
                + " sentRot=" + this.formatRotation(RotationHandler.sentRotation)
                + " actualServerRot=" + this.formatRotation(RotationHandler.getActualServerRotation())
                + " logicalServerRot=" + this.formatRotation(RotationHandler.getLogicalServerRotation());
        this.debugTicks++;
        int interval = Math.max(1, this.debugInterval.getValue().intValue());
        if (state.equals(this.lastDebugState) && this.debugTicks % interval != 0) {
            return;
        }
        this.lastDebugState = state;
        String line = "[ScaffoldDebug] tick=" + mc.player.tickCount + " " + state;
        logger.info(line);
        ChatUtil.print(line);
    }

    private String formatTarget(BlockPlacementTarget target) {
        if (target == null) {
            return "null";
        }
        return "place=" + this.formatPos(target.placedBlockPos())
                + " support=" + this.formatPos(target.interactedBlockPos())
                + " face=" + target.facing()
                + " point=" + this.formatVec(target.targetPoint());
    }

    private String formatPos(BlockPos pos) {
        if (pos == null) {
            return "null";
        }
        return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }

    private String formatRotation(Rotation rotation) {
        if (rotation == null) {
            return "null";
        }
        return String.format(Locale.US, "%.1f/%.1f", rotation.getYaw(), rotation.getPitch());
    }

    private String formatVec(Vec3 vector) {
        if (vector == null) {
            return "null";
        }
        return String.format(Locale.US, "(%.3f,%.3f,%.3f)", vector.x, vector.y, vector.z);
    }

    private String formatHit(BlockHitResult hit) {
        if (hit == null) {
            return "null";
        }
        return this.formatPos(hit.getBlockPos())
                + "/" + hit.getDirection()
                + "@" + this.formatVec(hit.getLocation());
    }

    public static boolean isOnBlockEdge(float inflate) {
        if (mc.level == null || mc.player == null) {
            return false;
        }
        return !mc.level.getCollisions(
                mc.player,
                mc.player.getBoundingBox()
                        .move(0.0, -0.5, 0.0)
                        .inflate(-inflate, 0.0, -inflate))
                .iterator()
                .hasNext();
    }

    record CounterState(int lastTick, int groundTicks, int airTicks) {
    }

    record PlanningItem(InteractionHand hand, int hotbarSlot, ItemStack stack) {
        private static final PlanningItem NONE = new PlanningItem(null, -1, ItemStack.EMPTY);

        PlanningItem {
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
        }
    }
}
