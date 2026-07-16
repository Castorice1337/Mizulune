/*
 * This file is part of Mizulune/OpenZen.
 *
 * Placement flow is adapted from LiquidBounce ModuleScaffold:
 * https://github.com/CCBlueX/LiquidBounce
 * Copyright (c) 2015-2026 CCBlueX
 * Licensed under GNU GPL v3 or later.
 *
 * Modified in 2026 for Mizulune's RotationHandler and Forge 1.20.1 APIs.
 */
package shit.zen.modules.impl.movement.scaffold.v2;

import net.minecraft.world.phys.AABB;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import shit.zen.ClientBase;
import shit.zen.modules.impl.movement.scaffold.v2.feature.ScaffoldBlockItemSelection;
import shit.zen.modules.impl.movement.scaffold.v2.feature.ScaffoldPlacementGate;
import shit.zen.utils.game.BlockPlacementTarget;
import shit.zen.utils.misc.PacketUtil;
import shit.zen.utils.rotation.Rotation;
import shit.zen.utils.rotation.RotationHandler;

public final class ScaffoldPlacementPipeline extends ClientBase {
    private static final double RANGE = 4.5;
    private static final double HIT_EPSILON = 1.0E-5;
    private final Effects effects;

    public ScaffoldPlacementPipeline() {
        this(new LiveEffects());
    }

    ScaffoldPlacementPipeline(Effects effects) {
        this.effects = java.util.Objects.requireNonNull(effects, "effects");
    }

    public Outcome place(
            Object rotationOwner,
            ScaffoldTickFrame frame,
            RotationTiming rotationTiming,
            Rotation resolvedRotation,
            SwingMode swingMode,
            double minDistance) {
        return this.place(
                rotationOwner,
                frame,
                rotationTiming,
                resolvedRotation,
                swingMode,
                minDistance,
                AttemptOptions.DEFAULT);
    }

    public Outcome place(
            Object rotationOwner,
            ScaffoldTickFrame frame,
            RotationTiming rotationTiming,
            Rotation resolvedRotation,
            SwingMode swingMode,
            double minDistance,
            AttemptOptions attemptOptions) {
        AttemptOptions options = attemptOptions == null ? AttemptOptions.DEFAULT : attemptOptions;
        BlockPlacementTarget target = frame == null ? null : frame.target();
        if (rotationOwner == null || target == null
                || frame.playerPosition() == null
                || frame.eyePosition() == null
                || frame.pose() == null
                || frame.hand() == null
                || !this.effects.hasPlacementContext(frame)) {
            return Outcome.fail(
                    Status.INVALID_TARGET,
                    RotationHandler.PlacementRotationSource.UNAVAILABLE,
                    null,
                    null,
                    "missing-context");
        }
        if (this.effects.intersectsPlayerTarget(frame)) {
            return Outcome.fail(
                    Status.INVALID_TARGET,
                    RotationHandler.PlacementRotationSource.UNAVAILABLE,
                    null,
                    null,
                    "player-target-intersection packets=none");
        }
        if (rotationTiming == RotationTiming.ON_TICK) {
            return this.placeTransactional(
                    rotationOwner,
                    frame,
                    resolvedRotation,
                    swingMode,
                    minDistance,
                    options,
                    true,
                    RotationHandler.PlacementRotationSource.EPHEMERAL_ON_TICK);
        }
        if (rotationTiming == RotationTiming.ON_TICK_SNAP) {
            return this.placeTransactional(
                    rotationOwner,
                    frame,
                    resolvedRotation,
                    swingMode,
                    minDistance,
                    options,
                    false,
                    RotationHandler.PlacementRotationSource.EPHEMERAL_ON_TICK);
        }
        return this.placeNormal(
                rotationOwner,
                frame,
                resolvedRotation,
                swingMode,
                minDistance,
                options);
    }

    private Outcome placeNormal(
            Object rotationOwner,
            ScaffoldTickFrame frame,
            Rotation resolvedRotation,
            SwingMode swingMode,
            double minDistance,
            AttemptOptions options) {
        if (resolvedRotation == null
                || this.effects.hasExternalRotationOwner(rotationOwner)
                || this.effects.getActiveRotation(rotationOwner) == null) {
            return Outcome.fail(
                    Status.ROTATION_CONFLICT,
                    RotationHandler.PlacementRotationSource.CONFLICT,
                    null,
                    null,
                    "active-owner-unavailable");
        }

        return this.placeTransactional(
                rotationOwner,
                frame,
                resolvedRotation,
                swingMode,
                minDistance,
                options,
                !options.keepServerRotation(),
                RotationHandler.PlacementRotationSource.EPHEMERAL_NORMAL);
    }

    private Outcome placeTransactional(
            Object rotationOwner,
            ScaffoldTickFrame frame,
            Rotation resolvedRotation,
            SwingMode swingMode,
            double minDistance,
            AttemptOptions options,
            boolean restorePlayerRotation,
            RotationHandler.PlacementRotationSource rotationSource) {
        BlockPlacementTarget target = frame.target();
        if (this.effects.hasExternalRotationOwner(rotationOwner)) {
            return Outcome.fail(
                    Status.ROTATION_CONFLICT,
                    RotationHandler.PlacementRotationSource.CONFLICT,
                    null,
                    null,
                    "owner-conflict");
        }

        Rotation playerRotation = this.effects.getPlayerRotation();
        Rotation transactionRotation = resolvedRotation;
        if (transactionRotation == null) {
            return Outcome.fail(
                    Status.ROTATION_CONFLICT,
                    RotationHandler.PlacementRotationSource.UNAVAILABLE,
                    null,
                    null,
                    "rotation-unavailable");
        }
        BlockHitResult hit = this.rayTrace(frame.eyePosition(), transactionRotation);
        if (!matches(target, hit)) {
            return Outcome.fail(
                    Status.NO_HIT,
                    rotationSource,
                    hit,
                    transactionRotation,
                    "strict-mismatch packets=none");
        }
        if (!ScaffoldPlacementGate.passesMinDistance(
                hit.getDirection(), frame.eyePosition(), hit.getLocation(), minDistance)) {
            return Outcome.fail(
                    Status.NO_HIT,
                    rotationSource,
                    hit,
                    transactionRotation,
                    "min-distance packets=none");
        }
        if (!this.effects.selectFrameHand(frame)) {
            return Outcome.fail(
                    Status.INVALID_TARGET,
                    rotationSource,
                    hit,
                    transactionRotation,
                    "slot-invalid packets=none");
        }

        if (!restorePlayerRotation
                && !this.effects.canActivateSnapRotation(rotationOwner)) {
            return Outcome.fail(
                    Status.ROTATION_CONFLICT,
                    RotationHandler.PlacementRotationSource.CONFLICT,
                    hit,
                    null,
                    "snap-provider-conflict");
        }

        boolean onGround = this.effects.isPlayerOnGround();
        boolean targetCommittedByPreUse = options.interactItemBeforePlace()
                && options.keepServerRotation();
        Rotation committedRotation;
        boolean targetPacket;
        if (targetCommittedByPreUse) {
            // In 1.20.1 MultiPlayerGameMode.useItem sends a PosRot before its
            // UseItem packet. New Telly uses that one movement sample as the
            // target commit instead of sending a duplicate beforehand.
            committedRotation = transactionRotation.clone();
            targetPacket = true;
        } else {
            RotationHandler.EphemeralPositionRotationCommit targetCommit =
                    this.effects.commitEphemeralPositionRotation(
                            rotationOwner,
                            frame.playerPosition(),
                            transactionRotation,
                            onGround,
                            false);
            if (targetCommit == null || targetCommit.rotation() == null) {
                return Outcome.fail(
                        Status.ROTATION_CONFLICT,
                        RotationHandler.PlacementRotationSource.CONFLICT,
                        hit,
                        null,
                        "ephemeral-commit-conflict");
            }
            committedRotation = targetCommit.rotation();
            targetPacket = targetCommit.dispatchRequested();
        }

        if (!restorePlayerRotation
                && !this.effects.activateSnapRotation(rotationOwner, committedRotation)) {
            return Outcome.fail(
                    Status.ROTATION_CONFLICT,
                    RotationHandler.PlacementRotationSource.CONFLICT,
                    hit,
                    committedRotation,
                    "snap-provider-activation-failed");
        }

        InteractionResult result;
        boolean restorePacket = false;
        try {
            if (options.interactItemBeforePlace()) {
                this.effects.useItem(InteractionHand.MAIN_HAND, committedRotation);
            }
            result = this.effects.useItemOn(frame.hand(), hit);
            if (result.consumesAction()) {
                this.effects.swing(swingMode, frame.hand());
            }
        } finally {
            if (restorePlayerRotation) {
                Rotation restoreRotation = fixedPlayerRotation(playerRotation, committedRotation);
                boolean restoreNeeded = !sameLogicalRotation(restoreRotation, committedRotation);
                boolean forceRestore = targetPacket && restoreNeeded;
                RotationHandler.EphemeralPositionRotationCommit restoreCommit =
                        this.effects.commitEphemeralPositionRotation(
                                rotationOwner,
                                frame.playerPosition(),
                                restoreRotation,
                                onGround,
                                forceRestore);
                restorePacket = restoreNeeded
                        && restoreCommit != null
                        && restoreCommit.dispatchRequested();
            }
        }

        String packetSequence = "packets="
                + (targetCommittedByPreUse
                ? "PosRot(target:pre-use)"
                : targetPacket ? "PosRot(target)" : "target-already-synced")
                + (options.interactItemBeforePlace() ? ">UseItem(main)" : "")
                + (restorePlayerRotation
                ? ">" + (restorePacket ? "PosRot(player)" : "restore-not-needed")
                : ">server-rotation-held");
        if (result.consumesAction()) {
            return new Outcome(
                    Status.SUCCESS,
                    rotationSource,
                    hit,
                    committedRotation,
                    "useItemOn:" + result + " " + packetSequence);
        }
        return Outcome.fail(
                Status.PLACE_FAILED,
                rotationSource,
                hit,
                committedRotation,
                "useItemOn:" + result + " " + packetSequence);
    }

    static Rotation fixedPlayerRotation(Rotation playerRotation, Rotation referenceRotation) {
        if (playerRotation == null || referenceRotation == null) {
            return playerRotation;
        }
        return new Rotation(
                referenceRotation.getYaw()
                        + Mth.wrapDegrees(playerRotation.getYaw() - referenceRotation.getYaw()),
                playerRotation.getPitch());
    }

    private static boolean sameLogicalRotation(Rotation first, Rotation second) {
        return first != null
                && second != null
                && Float.compare(
                Mth.wrapDegrees(first.getYaw()),
                Mth.wrapDegrees(second.getYaw())) == 0
                && Float.compare(first.getPitch(), second.getPitch()) == 0;
    }

    public BlockHitResult rayTrace(Rotation rotation) {
        if (rotation == null) {
            return null;
        }
        return this.rayTrace(this.effects.getEyePosition(), rotation);
    }

    public BlockHitResult rayTrace(Vec3 eyePosition, Rotation rotation) {
        return this.effects.rayTrace(eyePosition, rotation);
    }

    public boolean matchesTarget(
            Vec3 eyePosition,
            Rotation rotation,
            BlockPlacementTarget target) {
        return matches(target, this.rayTrace(eyePosition, rotation));
    }

    static boolean matches(BlockPlacementTarget target, BlockHitResult hit) {
        return target != null
                && hit != null
                && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(target.interactedBlockPos())
                && hit.getDirection() == target.facing()
                && hit.getLocation().y + HIT_EPSILON >= target.minPlacementY();
    }

    interface Effects {
        boolean hasPlacementContext(ScaffoldTickFrame frame);

        boolean intersectsPlayerTarget(ScaffoldTickFrame frame);

        boolean hasExternalRotationOwner(Object owner);

        Rotation getActiveRotation(Object owner);

        Rotation getPlayerRotation();

        Vec3 getEyePosition();

        BlockHitResult rayTrace(Vec3 eyePosition, Rotation rotation);

        boolean selectFrameHand(ScaffoldTickFrame frame);

        boolean canActivateSnapRotation(Object owner);

        boolean activateSnapRotation(Object owner, Rotation rotation);

        boolean isPlayerOnGround();

        RotationHandler.EphemeralPositionRotationCommit commitEphemeralPositionRotation(
                Object owner,
                Vec3 position,
                Rotation rotation,
                boolean onGround,
                boolean forceSend);

        InteractionResult useItemOn(InteractionHand hand, BlockHitResult hit);

        default void useItem(InteractionHand hand) {
        }

        default void useItem(InteractionHand hand, Rotation rotation) {
            this.useItem(hand);
        }

        void swing(SwingMode swingMode, InteractionHand hand);
    }

    private static final class LiveEffects implements Effects {
        @Override
        public boolean hasPlacementContext(ScaffoldTickFrame frame) {
            return frame != null
                    && mc.player != null
                    && mc.level != null
                    && mc.gameMode != null
                    && ScaffoldBlockItemSelection.isValidBlock(
                    frame.stack(),
                    mc.level,
                    mc.player);
        }

        @Override
        public boolean intersectsPlayerTarget(ScaffoldTickFrame frame) {
            if (frame == null
                    || frame.playerPosition() == null
                    || frame.pose() == null
                    || frame.target() == null
                    || mc.player == null) {
                return true;
            }
            AABB playerBox = mc.player.getDimensions(frame.pose())
                    .makeBoundingBox(frame.playerPosition());
            return playerBox.intersects(new AABB(frame.target().placedBlockPos()));
        }

        @Override
        public boolean hasExternalRotationOwner(Object owner) {
            return RotationHandler.hasExternalRotationOwner(owner);
        }

        @Override
        public Rotation getActiveRotation(Object owner) {
            return RotationHandler.getActiveRotation(owner);
        }

        @Override
        public Rotation getPlayerRotation() {
            return mc.player == null
                    ? null
                    : new Rotation(mc.player.getYRot(), mc.player.getXRot());
        }

        @Override
        public Vec3 getEyePosition() {
            return mc.player == null ? null : mc.player.getEyePosition(1.0f);
        }

        @Override
        public BlockHitResult rayTrace(Vec3 eyePosition, Rotation rotation) {
            if (eyePosition == null || rotation == null || mc.player == null || mc.level == null) {
                return null;
            }
            Vec3 direction = Vec3.directionFromRotation(rotation.getPitch(), rotation.getYaw());
            HitResult result = mc.level.clip(new ClipContext(
                    eyePosition,
                    eyePosition.add(direction.scale(RANGE)),
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    mc.player));
            return result instanceof BlockHitResult blockHit
                    && blockHit.getType() == HitResult.Type.BLOCK
                    ? blockHit
                    : null;
        }

        @Override
        public boolean selectFrameHand(ScaffoldTickFrame frame) {
            if (frame == null || frame.hand() == null || mc.player == null || mc.level == null) {
                return false;
            }
            if (frame.hand() == InteractionHand.OFF_HAND) {
                ItemStack liveStack = mc.player.getOffhandItem();
                return !liveStack.isEmpty()
                        && liveStack.getItem() == frame.stack().getItem()
                        && ScaffoldBlockItemSelection.isValidBlock(
                        liveStack,
                        mc.level,
                        mc.player);
            }
            if (frame.hotbarSlot() < 0 || frame.hotbarSlot() >= 9) {
                return false;
            }
            ItemStack liveStack = mc.player.getInventory().getItem(frame.hotbarSlot());
            if (liveStack.isEmpty()
                    || liveStack.getItem() != frame.stack().getItem()
                    || !ScaffoldBlockItemSelection.isValidBlock(
                    liveStack,
                    mc.level,
                    mc.player)) {
                return false;
            }
            mc.player.getInventory().selected = frame.hotbarSlot();
            return true;
        }

        @Override
        public boolean canActivateSnapRotation(Object owner) {
            return RotationHandler.canActivateSnapRotation(owner);
        }

        @Override
        public boolean activateSnapRotation(Object owner, Rotation rotation) {
            return RotationHandler.activateSnapRotation(owner, rotation);
        }

        @Override
        public boolean isPlayerOnGround() {
            return mc.player != null && mc.player.onGround();
        }

        @Override
        public RotationHandler.EphemeralPositionRotationCommit commitEphemeralPositionRotation(
                Object owner,
                Vec3 position,
                Rotation rotation,
                boolean onGround,
                boolean forceSend) {
            return RotationHandler.commitEphemeralPositionRotation(
                    owner,
                    position,
                    rotation,
                    onGround,
                    forceSend);
        }

        @Override
        public InteractionResult useItemOn(InteractionHand hand, BlockHitResult hit) {
            return mc.gameMode.useItemOn(mc.player, hand, hit);
        }

        @Override
        public void useItem(InteractionHand hand, Rotation rotation) {
            if (mc.player == null || rotation == null) {
                return;
            }
            Rotation packetRotation = RotationHandler.toServerPacketRotation(rotation);
            float playerYaw = mc.player.getYRot();
            float playerPitch = mc.player.getXRot();
            try {
                // 1.20.1 emits a full movement packet from useItem(). Spoof
                // only for this synchronous call so the visible view stays put.
                mc.player.setYRot(packetRotation.getYaw());
                mc.player.setXRot(packetRotation.getPitch());
                mc.gameMode.useItem(mc.player, hand);
            } finally {
                mc.player.setYRot(playerYaw);
                mc.player.setXRot(playerPitch);
            }
        }

        @Override
        public void swing(SwingMode swingMode, InteractionHand hand) {
            SwingMode mode = swingMode == null ? SwingMode.HIDE_FOR_CLIENT : swingMode;
            if (mode == SwingMode.SHOW) {
                mc.player.swing(hand);
            } else if (mode == SwingMode.HIDE_FOR_CLIENT) {
                PacketUtil.sendQueued(new ServerboundSwingPacket(hand));
            }
        }
    }

    public enum Status {
        SUCCESS,
        ROTATION_CONFLICT,
        NO_HIT,
        INVALID_TARGET,
        PLACE_FAILED
    }

    public enum RotationTiming {
        NORMAL,
        ON_TICK,
        ON_TICK_SNAP
    }

    public enum SwingMode {
        SHOW,
        HIDE_FOR_CLIENT,
        NONE
    }

    public record AttemptOptions(
            boolean interactItemBeforePlace,
            boolean keepServerRotation) {
        public static final AttemptOptions DEFAULT = new AttemptOptions(false, false);

        public AttemptOptions(boolean interactItemBeforePlace) {
            this(interactItemBeforePlace, false);
        }
    }

    public record Outcome(
            Status status,
            RotationHandler.PlacementRotationSource rotationSource,
            BlockHitResult hit,
            Rotation committedRotation,
            String detail) {
        public boolean placed() {
            return this.status == Status.SUCCESS;
        }

        private static Outcome fail(
                Status status,
                RotationHandler.PlacementRotationSource rotationSource,
                BlockHitResult hit,
                Rotation committedRotation,
                String detail) {
            return new Outcome(status, rotationSource, hit, committedRotation, detail);
        }
    }
}
