package shit.zen.utils.rotation;

import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import shit.zen.ClientBase;
import shit.zen.ZenClient;
import shit.zen.event.impl.CameraPitchEvent;
import shit.zen.event.impl.ChatEvent;
import shit.zen.event.impl.FallFlyingEvent;
import shit.zen.event.impl.RotationAnimationEvent;
import shit.zen.event.impl.JumpMarkerEvent;
import shit.zen.event.impl.MotionEvent;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.RayTraceEvent;
import shit.zen.event.impl.RotationResolvedEvent;
import shit.zen.event.impl.RotationEvent;
import shit.zen.event.impl.StrafeEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.event.impl.UseItemRayTraceEvent;
import shit.zen.event.impl.WorldChangeEvent;
import shit.zen.modules.impl.combat.AntiKB;
import shit.zen.modules.impl.combat.AutoThrow;
import shit.zen.modules.impl.combat.CrystalAura;
import shit.zen.modules.impl.combat.KillAura;
import shit.zen.modules.impl.movement.FireballBlink;
import shit.zen.modules.impl.player.AntiTNT;
import shit.zen.modules.impl.player.AntiWeb;
import shit.zen.modules.impl.player.AutoMLG;
import shit.zen.modules.impl.player.AutoWebPlace;
import shit.zen.modules.impl.player.Helper;
import shit.zen.modules.impl.player.MidPearl;
import shit.zen.utils.animation.TickTimer;
import shit.zen.utils.game.DirectionalInput;
import shit.zen.utils.misc.PacketUtil;
import shit.zen.event.EventTarget;

public class RotationHandler
extends ClientBase {
    private static final List<RotationProvider> ROTATION_PROVIDERS = new CopyOnWriteArrayList<>();
    private static final RotationSmoother PROVIDER_SMOOTHER = new RotationSmoother();
    public static Rotation targetRotation;
    public static Rotation prevRotation;
    public static Rotation sentRotation;
    public static Rotation prevSentRotation;
    public static boolean isRotating;
    private static RotationProvider activeProvider;
    private static Object activeRotationOwner;
    private static Object resetRotationOwner;
    private static MovementCorrection activeMovementCorrection;
    private static RotationApplyMode activeApplyMode;
    private static RotationPhase rotationPhase;
    private static volatile Rotation actualServerRotation;
    private static volatile Rotation theoreticalServerRotation;
    private static Rotation previousVisualRotation;
    private static Rotation currentVisualRotation;
    private static RotationResetSnapshot resetSnapshot;
    private static int resetTicksRemaining;
    private static volatile boolean resetAwaitingFinalPacket;
    private static volatile boolean resetFinalPacketWritten;
    private static int resetFinalPacketWaitTicks;
    private static boolean resetFinalPacketForced;
    private static final int RESET_FINAL_PACKET_TIMEOUT_TICKS = 5;
    private static volatile Object resetFinalizationPacket;
    private static final List<String> OUTGOING_MOVE_DEBUG = new ArrayList<>();
    private static int outgoingMoveDebugTick = -1;
    private static int outgoingMoveDebugIndex;

    public static void setTargetRotation(Rotation rotation) {
        RotationHandler.setTargetRotation(rotation, true);
    }

    public static void setTargetRotation(Rotation rotation, boolean fixMovement) {
        RotationHandler.setTargetRotation(rotation, fixMovement, RotationApplyMode.SILENT);
    }

    public static void setTargetRotation(Rotation rotation, boolean fixMovement, RotationApplyMode applyMode) {
        MovementCorrection correction = applyMode == RotationApplyMode.CHANGE_LOOK
                ? MovementCorrection.CHANGE_LOOK
                : fixMovement ? MovementCorrection.SILENT : MovementCorrection.OFF;
        RotationHandler.setTargetRotation(rotation, correction, applyMode);
    }

    public static void setTargetRotation(
            Rotation rotation,
            MovementCorrection movementCorrection,
            RotationApplyMode applyMode) {
        RotationHandler.applyTargetRotation(rotation, movementCorrection, applyMode);
        if (rotation == null) {
            return;
        }
        activeProvider = null;
        activeRotationOwner = null;
        resetRotationOwner = null;
        resetSnapshot = null;
        resetTicksRemaining = 0;
        PROVIDER_SMOOTHER.reset();
        rotationPhase = RotationPhase.ACTIVE;
        isRotating = true;
    }

    private static void applyTargetRotation(
            Rotation rotation,
            MovementCorrection movementCorrection,
            RotationApplyMode applyMode) {
        if (rotation == null) {
            return;
        }
        targetRotation = rotation;
        activeMovementCorrection = movementCorrection == null
                ? MovementCorrection.OFF
                : movementCorrection;
        activeApplyMode = RotationHandler.resolveApplyMode(applyMode, activeMovementCorrection);
        ClientBase.yaw = rotation.getYaw();
        if (activeApplyMode == RotationApplyMode.CHANGE_LOOK) {
            RotationHandler.applyToLocalCamera(rotation);
        } else {
            RotationHandler.clearVisualRotation();
        }
    }

    public static void registerProvider(RotationProvider provider) {
        if (provider != null && !ROTATION_PROVIDERS.contains(provider)) {
            ROTATION_PROVIDERS.add(provider);
        }
    }

    public static void unregisterProvider(RotationProvider provider) {
        ROTATION_PROVIDERS.remove(provider);
        if (activeProvider == provider
                || activeRotationOwner == provider
                || resetRotationOwner == provider) {
            RotationHandler.clearRotationState();
        }
    }

    public static void releaseProvider(RotationProvider provider) {
        if (provider == null) {
            return;
        }
        if (rotationPhase == RotationPhase.ACTIVE
                && activeProvider == provider
                && activeRotationOwner == provider) {
            RotationHandler.beginResetOrClear();
        }
    }

    public static Rotation getCurrentRotation() {
        if (rotationPhase == RotationPhase.IDLE || !isRotating || targetRotation == null) {
            return null;
        }
        return targetRotation.clone();
    }

    public static RotationPhase getRotationPhase() {
        return rotationPhase;
    }

    public static Rotation getActualServerRotation() {
        return actualServerRotation == null ? null : actualServerRotation.clone();
    }

    public static Rotation getLogicalServerRotation() {
        Rotation rotation = theoreticalServerRotation == null
                ? actualServerRotation
                : theoreticalServerRotation;
        return rotation == null ? null : rotation.clone();
    }

    public static Rotation getSmoothedRotation(RotationProvider provider) {
        if (provider != null && provider == activeProvider && isRotating && targetRotation != null) {
            return targetRotation.clone();
        }
        return null;
    }

    public static Rotation getActiveRotation(Object owner) {
        if (rotationPhase != RotationPhase.ACTIVE
                || owner == null
                || owner != activeRotationOwner
                || !isRotating
                || targetRotation == null) {
            return null;
        }
        return targetRotation.clone();
    }

    public static boolean isActiveRotationOwner(Object owner) {
        return owner != null
                && rotationPhase == RotationPhase.ACTIVE
                && owner == activeRotationOwner
                && isRotating
                && targetRotation != null;
    }

    public static MovementCorrection getActiveMovementCorrection(Object owner) {
        if (rotationPhase != RotationPhase.ACTIVE
                || owner == null
                || owner != activeRotationOwner
                || !isRotating
                || targetRotation == null) {
            return MovementCorrection.OFF;
        }
        return activeMovementCorrection;
    }

    /**
     * Sends a short-lived full movement sample without creating a persistent
     * provider target. Scaffold uses the last vanilla position so Grim can
     * classify the packet as a 1.17+ duplicate rather than another client tick.
     */
    public static EphemeralPositionRotationCommit commitEphemeralPositionRotation(
            Object owner,
            Vec3 position,
            Rotation rotation,
            boolean onGround,
            boolean forceSend) {
        if (owner == null
                || position == null
                || rotation == null
                || mc.player == null
                || mc.getConnection() == null
                || !Double.isFinite(position.x)
                || !Double.isFinite(position.y)
                || !Double.isFinite(position.z)
                || !Float.isFinite(rotation.getYaw())
                || !Float.isFinite(rotation.getPitch())
                || RotationHandler.hasExternalRotationOwner(owner)) {
            return null;
        }
        Rotation logicalRotation = RotationHandler.getLogicalServerRotation();
        float referenceYaw = logicalRotation == null
                ? mc.player.getYRot()
                : logicalRotation.getYaw();
        Rotation normalized = new Rotation(
                RotationHandler.nearestEquivalentYaw(rotation.getYaw(), referenceYaw),
                Mth.clamp(rotation.getPitch(), -90.0f, 90.0f));
        boolean dispatchRequested = forceSend
                || !RotationHandler.sameLogicalRotation(
                normalized,
                RotationHandler.getLogicalServerRotation());
        if (dispatchRequested) {
            Rotation packetRotation = RotationHandler.toServerPacketRotation(owner, normalized);
            ServerboundMovePlayerPacket.PosRot packet = new ServerboundMovePlayerPacket.PosRot(
                    position.x,
                    position.y,
                    position.z,
                    packetRotation.getYaw(),
                    packetRotation.getPitch(),
                    onGround);
            PacketUtil.sendQueued(packet);
        }
        return new EphemeralPositionRotationCommit(normalized, dispatchRequested);
    }

    public static boolean canActivateSnapRotation(Object owner) {
        return owner instanceof RotationProvider provider
                && ROTATION_PROVIDERS.contains(provider)
                && !RotationHandler.hasExternalRotationOwner(owner);
    }

    public static boolean activateSnapRotation(Object owner, Rotation rotation) {
        if (!(owner instanceof RotationProvider provider)
                || rotation == null
                || !RotationHandler.canActivateSnapRotation(owner)) {
            return false;
        }
        activeProvider = provider;
        RotationHandler.captureResetSnapshot(provider);
        RotationHandler.setOwnedTargetRotation(
                owner,
                rotation.clone(),
                provider.getMovementCorrection(),
                provider.getApplyMode());
        return true;
    }

    public static void clearOwnedRotation(Object owner) {
        if (owner != null && (owner == activeRotationOwner || owner == resetRotationOwner)) {
            RotationHandler.clearRotationState();
        }
    }

    public static boolean hasExternalRotationOwner(Object owner) {
        if (rotationPhase == RotationPhase.ACTIVE) {
            return activeRotationOwner != owner;
        }
        if (rotationPhase == RotationPhase.RESET) {
            return resetRotationOwner != owner;
        }
        return false;
    }

    public static String getOutgoingMovePacketDebug(int tick) {
        synchronized (OUTGOING_MOVE_DEBUG) {
            if (tick != outgoingMoveDebugTick || OUTGOING_MOVE_DEBUG.isEmpty()) {
                return "none";
            }
            return String.join(" | ", OUTGOING_MOVE_DEBUG);
        }
    }

    static boolean sameLogicalRotation(Rotation first, Rotation second) {
        return first != null
                && second != null
                && Float.compare(Mth.wrapDegrees(first.getYaw()), Mth.wrapDegrees(second.getYaw())) == 0
                && Float.compare(first.getPitch(), second.getPitch()) == 0;
    }

    static float nearestEquivalentYaw(float requestedYaw, float referenceYaw) {
        if (!Float.isFinite(requestedYaw) || !Float.isFinite(referenceYaw)) {
            return requestedYaw;
        }
        return referenceYaw + Mth.wrapDegrees(requestedYaw - referenceYaw);
    }

    public static Rotation toServerPacketRotation(Rotation rotation) {
        if (rotation == null) {
            return null;
        }
        boolean normalizeYaw = false;
        if (rotationPhase == RotationPhase.ACTIVE) {
            normalizeYaw = activeProvider != null
                    && activeProvider.shouldNormalizeYawForServerPackets();
        } else if (rotationPhase == RotationPhase.RESET && resetSnapshot != null) {
            normalizeYaw = resetSnapshot.normalizeYawForServerPackets();
        }
        return RotationHandler.toServerPacketRotation(normalizeYaw, rotation);
    }

    public static Rotation toServerPacketRotation(Object owner, Rotation rotation) {
        if (rotation == null) {
            return null;
        }
        boolean normalizeYaw = rotationPhase == RotationPhase.RESET
                && owner == resetRotationOwner
                && resetSnapshot != null
                ? resetSnapshot.normalizeYawForServerPackets()
                : owner instanceof RotationProvider provider
                && provider.shouldNormalizeYawForServerPackets();
        return RotationHandler.toServerPacketRotation(normalizeYaw, rotation);
    }

    private static Rotation toServerPacketRotation(boolean normalizeYaw, Rotation rotation) {
        Rotation serverRotation = RotationHandler.getLogicalServerRotation();
        // Keep modulo-equivalent yaw on the current wire branch without altering internal state.
        float yaw = normalizeYaw && serverRotation != null
                ? RotationHandler.nearestEquivalentYaw(
                        rotation.getYaw(),
                        serverRotation.getYaw())
                : rotation.getYaw();
        return new Rotation(yaw, rotation.getPitch());
    }

    private static void setOwnedTargetRotation(
            Object owner,
            Rotation rotation,
            MovementCorrection movementCorrection,
            RotationApplyMode applyMode) {
        RotationHandler.applyTargetRotation(rotation, movementCorrection, applyMode);
        resetAwaitingFinalPacket = false;
        resetFinalPacketWritten = false;
        resetFinalPacketWaitTicks = 0;
        resetFinalPacketForced = false;
        activeRotationOwner = owner;
        resetRotationOwner = null;
        rotationPhase = RotationPhase.ACTIVE;
        isRotating = true;
    }

    private static void setOwnedTargetRotation(Object owner, Rotation rotation) {
        RotationHandler.setOwnedTargetRotation(
                owner,
                rotation,
                MovementCorrection.SILENT,
                RotationApplyMode.SILENT);
    }

    static RotationApplyMode resolveApplyMode(
            RotationApplyMode applyMode,
            MovementCorrection movementCorrection) {
        if (movementCorrection == MovementCorrection.CHANGE_LOOK) {
            return RotationApplyMode.CHANGE_LOOK;
        }
        return applyMode == null ? RotationApplyMode.SILENT : applyMode;
    }

    public static Rotation getVisualRotation(float partialTick) {
        if (activeApplyMode != RotationApplyMode.CHANGE_LOOK || currentVisualRotation == null) {
            return null;
        }
        Rotation previous = previousVisualRotation == null ? currentVisualRotation : previousVisualRotation;
        float tickDelta = Mth.clamp(partialTick, 0.0f, 1.0f);
        return new Rotation(
                Mth.rotLerp(tickDelta, previous.getYaw(), currentVisualRotation.getYaw()),
                Mth.clamp(Mth.lerp(tickDelta, previous.getPitch(), currentVisualRotation.getPitch()), -90.0f, 90.0f));
    }

    public static void offsetChangeLookRotation(float yawDelta, float pitchDelta) {
        if (activeApplyMode != RotationApplyMode.CHANGE_LOOK
                || currentVisualRotation == null
                || mc.player == null
                || (Math.abs(yawDelta) <= 1.0E-6f && Math.abs(pitchDelta) <= 1.0E-6f)) {
            return;
        }
        currentVisualRotation = RotationHandler.offsetRotation(currentVisualRotation, yawDelta, pitchDelta);
        if (previousVisualRotation != null) {
            previousVisualRotation = RotationHandler.offsetRotation(previousVisualRotation, yawDelta, pitchDelta);
        }
        if (targetRotation != null) {
            targetRotation = RotationHandler.offsetRotation(targetRotation, yawDelta, pitchDelta);
            ClientBase.yaw = targetRotation.getYaw();
        }
        PROVIDER_SMOOTHER.offsetCurrentRotation(yawDelta, pitchDelta);
    }

    private static RotationProvider resolveProvider() {
        return RotationHandler.selectProvider(ROTATION_PROVIDERS);
    }

    static RotationProvider selectProvider(List<RotationProvider> providers) {
        if (providers == null) {
            return null;
        }
        return providers.stream()
                .filter(provider -> provider.isRotationActive()
                        && provider.getApplyMode() != RotationApplyMode.OFF
                        && provider.getRotation() != null)
                .max(Comparator.comparingInt(RotationProvider::getRotationPriority))
                .orElse(null);
    }

    private static void applyToLocalCamera(Rotation rotation) {
        if (mc.player == null) {
            return;
        }
        float yaw = rotation.getYaw();
        float pitch = Mth.clamp(rotation.getPitch(), -90.0f, 90.0f);
        if (Float.isNaN(yaw) || Float.isNaN(pitch)) {
            return;
        }
        RotationHandler.updateVisualRotation(new Rotation(yaw, pitch));
        Rotation previous = previousVisualRotation == null
                ? new Rotation(mc.player.getYRot(), mc.player.getXRot())
                : previousVisualRotation;
        mc.player.yRotO = previous.getYaw();
        mc.player.xRotO = Mth.clamp(previous.getPitch(), -90.0f, 90.0f);
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
    }

    private static void updateVisualRotation(Rotation rotation) {
        if (currentVisualRotation == null) {
            previousVisualRotation = mc.player == null
                    ? rotation.clone()
                    : new Rotation(mc.player.getYRot(), Mth.clamp(mc.player.getXRot(), -90.0f, 90.0f));
        } else {
            previousVisualRotation = currentVisualRotation.clone();
        }
        currentVisualRotation = rotation.clone();
    }

    private static Rotation offsetRotation(Rotation rotation, float yawDelta, float pitchDelta) {
        return new Rotation(
                rotation.getYaw() + yawDelta,
                Mth.clamp(rotation.getPitch() + pitchDelta, -90.0f, 90.0f));
    }

    private static void clearVisualRotation() {
        previousVisualRotation = null;
        currentVisualRotation = null;
    }

    private static Rotation smoothProviderRotation(RotationProvider provider) {
        return RotationHandler.smoothRotation(
                provider.getRotation(),
                provider.getSmoothMode(),
                provider.getSmoothDurationTicks(),
                provider.getSmoothSteepness(),
                provider.getMaxYawSpeed(),
                provider.getMaxPitchSpeed(),
                provider.getMinStep(),
                provider.getRotationEpsilon(),
                provider.getInterpolationHorizontalSpeedMin(),
                provider.getInterpolationHorizontalSpeedMax(),
                provider.getInterpolationVerticalSpeedMin(),
                provider.getInterpolationVerticalSpeedMax(),
                provider.getInterpolationDirectionChangeFactorMin(),
                provider.getInterpolationDirectionChangeFactorMax(),
                provider.getInterpolationMidpoint(),
                provider.shouldHumanizeRotation(),
                provider.shouldSnapToSensitivity());
    }

    private static Rotation smoothRotation(
            Rotation target,
            SmoothMode smoothMode,
            int smoothDurationTicks,
            double smoothSteepness,
            double maxYawSpeed,
            double maxPitchSpeed,
            double minStep,
            double rotationEpsilon,
            double interpolationHorizontalSpeedMin,
            double interpolationHorizontalSpeedMax,
            double interpolationVerticalSpeedMin,
            double interpolationVerticalSpeedMax,
            double interpolationDirectionChangeFactorMin,
            double interpolationDirectionChangeFactorMax,
            double interpolationMidpoint,
            boolean humanizeRotation,
            boolean snapToSensitivity) {
        return PROVIDER_SMOOTHER.update(
                target,
                smoothMode,
                smoothDurationTicks,
                smoothSteepness,
                maxYawSpeed,
                maxPitchSpeed,
                minStep,
                rotationEpsilon,
                interpolationHorizontalSpeedMin,
                interpolationHorizontalSpeedMax,
                interpolationVerticalSpeedMin,
                interpolationVerticalSpeedMax,
                interpolationDirectionChangeFactorMin,
                interpolationDirectionChangeFactorMax,
                interpolationMidpoint,
                humanizeRotation,
                snapToSensitivity);
    }

    private static void captureResetSnapshot(RotationProvider provider) {
        RotationApplyMode applyMode = provider.getApplyMode();
        if (applyMode == null) {
            applyMode = RotationApplyMode.SILENT;
        }
        resetSnapshot = new RotationResetSnapshot(
                RotationHandler.resolveApplyMode(applyMode, provider.getMovementCorrection()),
                provider.getMovementCorrection(),
                provider.getSmoothMode(),
                provider.getSmoothDurationTicks(),
                provider.getSmoothSteepness(),
                provider.getMaxYawSpeed(),
                provider.getMaxPitchSpeed(),
                provider.getMinStep(),
                provider.getRotationEpsilon(),
                provider.getInterpolationHorizontalSpeedMin(),
                provider.getInterpolationHorizontalSpeedMax(),
                provider.getInterpolationVerticalSpeedMin(),
                provider.getInterpolationVerticalSpeedMax(),
                provider.getInterpolationDirectionChangeFactorMin(),
                provider.getInterpolationDirectionChangeFactorMax(),
                provider.getInterpolationMidpoint(),
                provider.shouldHumanizeRotation(),
                provider.shouldSnapToSensitivity(),
                provider.shouldNormalizeYawForServerPackets(),
                Math.max(0, provider.getTicksUntilReset()),
                Math.max(0.0, provider.getResetThreshold()),
                provider.shouldResetRotation(),
                provider.shouldAffectRayTrace(),
                provider.shouldAffectUseItemRayTrace());
        resetTicksRemaining = resetSnapshot.ticksUntilReset();
    }

    private static void beginResetOrClear() {
        if (RotationHandler.canBeginReset()) {
            Object previousOwner = activeRotationOwner;
            activeProvider = null;
            activeRotationOwner = null;
            resetRotationOwner = previousOwner;
            rotationPhase = RotationPhase.RESET;
            resetTicksRemaining = resetSnapshot.ticksUntilReset();
            resetAwaitingFinalPacket = false;
            resetFinalPacketWritten = false;
            resetFinalPacketWaitTicks = 0;
            resetFinalPacketForced = false;
            if (resetSnapshot.applyMode() != RotationApplyMode.CHANGE_LOOK) {
                RotationHandler.clearVisualRotation();
            }
            isRotating = true;
            return;
        }
        RotationHandler.clearRotationState();
    }

    private static boolean canBeginReset() {
        return resetSnapshot != null
                && resetSnapshot.resetRotation()
                && resetSnapshot.ticksUntilReset() > 0
                && targetRotation != null
                && activeApplyMode != RotationApplyMode.OFF
                && activeRotationOwner != null
                && ROTATION_PROVIDERS.contains(activeRotationOwner)
                && mc.player != null;
    }

    private static boolean updateResetRotation() {
        if (rotationPhase != RotationPhase.RESET
                || resetSnapshot == null
                || resetRotationOwner == null) {
            return false;
        }
        if (RotationHandler.completePendingResetAfterFinalWrite()) {
            return true;
        }
        if (mc.player == null
                || !ROTATION_PROVIDERS.contains(resetRotationOwner)) {
            RotationHandler.clearRotationState();
            return true;
        }
        if (resetAwaitingFinalPacket) {
            resetFinalPacketWaitTicks++;
            if (!resetFinalPacketForced && targetRotation != null) {
                resetFinalPacketForced = true;
                Rotation packetRotation = RotationHandler.toServerPacketRotation(targetRotation);
                ServerboundMovePlayerPacket.Rot finalizationPacket =
                        new ServerboundMovePlayerPacket.Rot(
                        packetRotation.getYaw(),
                        packetRotation.getPitch(),
                        mc.player.onGround());
                resetFinalizationPacket = finalizationPacket;
                PacketUtil.sendQueued(finalizationPacket);
                if (RotationHandler.completePendingResetAfterFinalWrite()) {
                    return true;
                }
            }
            if (resetFinalPacketWaitTicks >= RESET_FINAL_PACKET_TIMEOUT_TICKS) {
                RotationHandler.clearRotationState();
            }
            return true;
        }
        if (resetTicksRemaining <= 0) {
            RotationHandler.clearRotationState();
            return true;
        }

        Rotation resetTarget = new Rotation(mc.player.getYRot(), mc.player.getXRot());
        Rotation smoothed = RotationHandler.smoothRotation(
                resetTarget,
                resetSnapshot.smoothMode(),
                resetSnapshot.smoothDurationTicks(),
                resetSnapshot.smoothSteepness(),
                resetSnapshot.maxYawSpeed(),
                resetSnapshot.maxPitchSpeed(),
                resetSnapshot.minStep(),
                resetSnapshot.rotationEpsilon(),
                resetSnapshot.interpolationHorizontalSpeedMin(),
                resetSnapshot.interpolationHorizontalSpeedMax(),
                resetSnapshot.interpolationVerticalSpeedMin(),
                resetSnapshot.interpolationVerticalSpeedMax(),
                resetSnapshot.interpolationDirectionChangeFactorMin(),
                resetSnapshot.interpolationDirectionChangeFactorMax(),
                resetSnapshot.interpolationMidpoint(),
                resetSnapshot.humanizeRotation(),
                resetSnapshot.snapToSensitivity());
        if (smoothed == null) {
            RotationHandler.clearRotationState();
            return true;
        }

        RotationHandler.applyTargetRotation(
                smoothed,
                resetSnapshot.movementCorrection(),
                resetSnapshot.applyMode());
        resetTicksRemaining--;
        if (RotationHandler.rotationDistance(smoothed, resetTarget) <= resetSnapshot.resetThreshold()
                || resetTicksRemaining <= 0) {
            resetAwaitingFinalPacket = true;
            resetFinalPacketWritten = false;
            resetFinalPacketWaitTicks = 0;
            resetFinalPacketForced = false;
        }
        return true;
    }

    static boolean completePendingResetAfterFinalWrite() {
        if (rotationPhase != RotationPhase.RESET
                || !resetAwaitingFinalPacket
                || !resetFinalPacketWritten) {
            return false;
        }
        RotationHandler.clearRotationState();
        return true;
    }

    public static boolean shouldBypassScaffoldPacketBuffer(Object packet) {
        if (packet == null || packet != resetFinalizationPacket) {
            return false;
        }
        resetFinalizationPacket = null;
        return true;
    }

    private static double rotationDistance(Rotation first, Rotation second) {
        if (first == null || second == null) {
            return Double.MAX_VALUE;
        }
        float yawDiff = Mth.wrapDegrees(first.getYaw() - second.getYaw());
        float pitchDiff = first.getPitch() - second.getPitch();
        return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
    }

    private static void clearResetState() {
        resetSnapshot = null;
        resetRotationOwner = null;
        resetTicksRemaining = 0;
        resetAwaitingFinalPacket = false;
        resetFinalPacketWritten = false;
        resetFinalPacketWaitTicks = 0;
        resetFinalPacketForced = false;
        resetFinalizationPacket = null;
    }

    private static void clearActiveProvider() {
        activeProvider = null;
        activeRotationOwner = null;
        resetRotationOwner = null;
        PROVIDER_SMOOTHER.reset();
        RotationHandler.clearResetState();
    }

    private static void clearRotationState() {
        RotationHandler.clearActiveProvider();
        RotationHandler.clearVisualRotation();
        isRotating = false;
        activeMovementCorrection = MovementCorrection.OFF;
        activeApplyMode = RotationApplyMode.OFF;
        targetRotation = null;
        activeRotationOwner = null;
        resetRotationOwner = null;
        rotationPhase = RotationPhase.IDLE;
    }

    @EventTarget
    public void onWorldChange(WorldChangeEvent worldChangeEvent) {
        prevRotation = null;
        sentRotation = null;
        prevSentRotation = null;
        actualServerRotation = null;
        theoreticalServerRotation = null;
        RotationHandler.clearRotationState();
    }

    @EventTarget(value=0)
    public void onTick(TickEvent tickEvent) {
        TickTimer.tickAll();
    }

    @EventTarget(value=0)
    public void onPacket(PacketEvent packetEvent) {
        Object packet2 = packetEvent.getPacket();
        if (packet2 instanceof ServerboundChatPacket chatPacket) {
            ChatEvent event = new ChatEvent(chatPacket.message());
            if (ZenClient.isReady()) {
                ZenClient.getInstance().getEventBus().call(event);
                if (event.isCancelled()) {
                    packetEvent.setCancelled(true);
                }
            }
        }
    }

    public static void onOutgoingPacketAccepted(Object packet) {
        if (!(packet instanceof ServerboundMovePlayerPacket movePacket)
                || !movePacket.hasRotation()) {
            return;
        }
        theoreticalServerRotation = new Rotation(
                movePacket.getYRot(0.0f),
                movePacket.getXRot(0.0f));
    }

    public static void onFinalPacketWrite(Object packet) {
        if (packet != null && packet == resetFinalizationPacket) {
            resetFinalizationPacket = null;
        }
        if (!(packet instanceof ServerboundMovePlayerPacket movePacket)) {
            return;
        }
        RotationHandler.recordOutgoingMovePacket(movePacket, "final-write");
        if (!movePacket.hasRotation()) {
            return;
        }
        actualServerRotation = new Rotation(
                movePacket.getYRot(0.0f),
                movePacket.getXRot(0.0f));
        if (rotationPhase == RotationPhase.RESET
                && resetAwaitingFinalPacket
                && RotationHandler.sameLogicalRotation(actualServerRotation, targetRotation)) {
            resetFinalPacketWritten = true;
        }
        if (theoreticalServerRotation == null) {
            theoreticalServerRotation = actualServerRotation.clone();
        }
    }

    @EventTarget(value=4)
    public void onTickHigh(TickEvent tickEvent) {
        if (mc.player == null) {
            actualServerRotation = null;
            theoreticalServerRotation = null;
            RotationHandler.clearRotationState();
            return;
        }
        {
            KillAura killAura = KillAura.INSTANCE;
            CrystalAura crystalAura = CrystalAura.INSTANCE;
            AutoMLG autoMLG = AutoMLG.INSTANCE;
            FireballBlink fireballBlink = FireballBlink.INSTANCE;
            AntiTNT antiTNT = AntiTNT.INSTANCE;
            Helper helper = Helper.INSTANCE;
            AntiWeb antiWeb = AntiWeb.INSTANCE;
            AutoWebPlace autoWebPlace = AutoWebPlace.INSTANCE;
            AutoThrow autoThrow = AutoThrow.INSTANCE;
            AntiKB antiKB = AntiKB.INSTANCE;
            MidPearl midPearl = MidPearl.INSTANCE;
            RotationProvider provider = RotationHandler.resolveProvider();
            if (provider != null) {
                activeProvider = provider;
                RotationHandler.captureResetSnapshot(provider);
                Rotation smoothed = RotationHandler.smoothProviderRotation(provider);
                if (smoothed != null) {
                    RotationHandler.setOwnedTargetRotation(
                            provider,
                            smoothed,
                            provider.getMovementCorrection(),
                            provider.getApplyMode());
                } else {
                    RotationHandler.clearRotationState();
                }
            } else if (autoMLG != null && autoMLG.isEnabled() && autoMLG.targetRotation != null) {
                RotationHandler.clearActiveProvider();
                RotationHandler.setOwnedTargetRotation(autoMLG, autoMLG.targetRotation);
                autoMLG.targetRotation = null;
            } else if (crystalAura != null && crystalAura.isEnabled() && CrystalAura.aimRotation != null) {
                RotationHandler.clearActiveProvider();
                RotationHandler.setOwnedTargetRotation(crystalAura, CrystalAura.aimRotation);
            } else if (fireballBlink != null && fireballBlink.isEnabled() && FireballBlink.rotation != null) {
                RotationHandler.clearActiveProvider();
                RotationHandler.setOwnedTargetRotation(fireballBlink, FireballBlink.rotation);
            } else if (midPearl != null && midPearl.isEnabled() && MidPearl.targetRotation != null) {
                RotationHandler.clearActiveProvider();
                RotationHandler.setOwnedTargetRotation(midPearl, MidPearl.targetRotation);
            } else if (antiTNT != null && antiTNT.isEnabled() && AntiTNT.targetRotation != null) {
                RotationHandler.clearActiveProvider();
                RotationHandler.setOwnedTargetRotation(antiTNT, AntiTNT.targetRotation);
            } else if (helper != null && helper.isEnabled() && helper.hasTargetRotation() && Helper.targetRotation != null) {
                RotationHandler.clearActiveProvider();
                RotationHandler.setOwnedTargetRotation(helper, Helper.targetRotation);
            } else if (antiWeb != null && antiWeb.isEnabled() && AntiWeb.currentPhase != AntiWeb.Phase.IDLE && AntiWeb.targetRotation != null) {
                RotationHandler.clearActiveProvider();
                RotationHandler.setOwnedTargetRotation(antiWeb, AntiWeb.targetRotation);
            } else if (autoWebPlace != null && autoWebPlace.isEnabled() && AutoWebPlace.targetRotation != null) {
                RotationHandler.clearActiveProvider();
                RotationHandler.setOwnedTargetRotation(autoWebPlace, AutoWebPlace.targetRotation);
            } else if (autoThrow != null && autoThrow.isEnabled() && autoThrow.targetRotation != null) {
                RotationHandler.clearActiveProvider();
                RotationHandler.setOwnedTargetRotation(autoThrow, autoThrow.targetRotation);
            } else if (killAura != null && killAura.isEnabled() && KillAura.target != null && killAura.rotation != null) {
                RotationHandler.clearActiveProvider();
                RotationHandler.setOwnedTargetRotation(
                        killAura,
                        new Rotation(killAura.rotation.getYaw(), killAura.rotation.getPitch()));
            } else if (antiKB != null && antiKB.isEnabled() && AntiKB.rotation != null) {
                RotationHandler.clearActiveProvider();
                RotationHandler.setOwnedTargetRotation(antiKB, AntiKB.rotation);
            } else if (rotationPhase == RotationPhase.ACTIVE && activeProvider != null) {
                RotationHandler.beginResetOrClear();
                if (rotationPhase == RotationPhase.RESET) {
                    RotationHandler.updateResetRotation();
                }
            } else if (rotationPhase == RotationPhase.RESET) {
                RotationHandler.updateResetRotation();
            } else {
                RotationHandler.clearRotationState();
            }
        }
        if (ZenClient.isReady()) {
            ZenClient.getInstance().getEventBus().call(
                    new RotationResolvedEvent(mc.player.tickCount));
        }
    }

    @EventTarget
    public void onHeadTurn(RotationAnimationEvent e) {
        if (sentRotation != null && prevSentRotation != null && mc.player != null && isRotating) {
            e.setYaw(sentRotation.getYaw());
            e.setLastYaw(prevSentRotation.getYaw());

            e.setPitch(sentRotation.getPitch());
            e.setLastPitch(prevSentRotation.getPitch());
        }
    }

    @EventTarget
    public void onCameraPitch(CameraPitchEvent cameraPitchEvent) {
        if (sentRotation != null && prevSentRotation != null) {
            cameraPitchEvent.setPitch(sentRotation.getPitch());
        }
    }

    @EventTarget(value=4)
    public void onMotion(MotionEvent e) {
        if (e.isPost()) {
            if (mc.player != null && mc.player.tickCount <= 1 && ZenClient.isReady()) {
                ZenClient.getInstance().getEventBus().call(new WorldChangeEvent());
            }
            if (mc.player == null) {
                RotationHandler.clearRotationState();
                return;
            }
            if (prevRotation == null) {
                prevRotation = new Rotation(mc.player.getYRot(), mc.player.getXRot());
            }
            prevSentRotation = sentRotation;
            if (rotationPhase != RotationPhase.IDLE && targetRotation != null && isRotating) {
                Rotation packetRotation = RotationHandler.toServerPacketRotation(targetRotation);
                float yaw = packetRotation.getYaw();
                float pitch = packetRotation.getPitch();
                if (!Float.isNaN(yaw) && !Float.isNaN(pitch)) {
                    e.setYaw(yaw);
                    e.setPitch(pitch);
                    ClientBase.yaw = yaw;
                }
            }
            sentRotation = new Rotation(e.getYaw(), e.getPitch());
            prevRotation = new Rotation(e.getYaw(), e.getPitch());
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent strafeEvent) {
        if (isRotating
                && activeMovementCorrection == MovementCorrection.SILENT
                && targetRotation != null
                && mc.player != null) {
            var corrected = MovementCorrectionUtil.correctSilentInput(
                    DirectionalInput.fromImpulses(
                            strafeEvent.getForward(),
                            strafeEvent.getStrafe()),
                    mc.player.getYRot(),
                    targetRotation.getYaw());
            strafeEvent.setForward(corrected.forwardImpulse());
            strafeEvent.setStrafe(corrected.strafeImpulse());
        }
    }

    @EventTarget
    public void onRayTrace(RayTraceEvent rayTraceEvent) {
        if (targetRotation != null
                && rayTraceEvent.entity == mc.player
                && isRotating
                && RotationHandler.shouldAffectRayTrace()) {
            rayTraceEvent.setYaw(targetRotation.getYaw());
            rayTraceEvent.setPitch(targetRotation.getPitch());
        }
    }

    @EventTarget
    public void onUseItemRayTrace(UseItemRayTraceEvent useItemRayTraceEvent) {
        if (targetRotation != null
                && isRotating
                && RotationHandler.shouldAffectUseItemRayTrace()) {
            useItemRayTraceEvent.setYaw(targetRotation.getYaw());
            useItemRayTraceEvent.setPitch(targetRotation.getPitch());
        }
    }

    private static boolean shouldAffectRayTrace() {
        if (rotationPhase == RotationPhase.RESET && resetSnapshot != null) {
            return resetSnapshot.affectRayTrace();
        }
        return activeProvider == null || activeProvider.shouldAffectRayTrace();
    }

    private static boolean shouldAffectUseItemRayTrace() {
        if (rotationPhase == RotationPhase.RESET && resetSnapshot != null) {
            return resetSnapshot.affectUseItemRayTrace();
        }
        return activeProvider == null || activeProvider.shouldAffectUseItemRayTrace();
    }

    @EventTarget
    public void onRotation(RotationEvent rotationEvent) {
        if (isRotating
                && activeMovementCorrection != MovementCorrection.OFF
                && targetRotation != null) {
            rotationEvent.setYaw(targetRotation.getYaw());
        }
    }

    @EventTarget
    public void onJump(JumpMarkerEvent jumpMarkerEvent) {
        if (isRotating
                && activeMovementCorrection != MovementCorrection.OFF
                && targetRotation != null) {
            jumpMarkerEvent.setYaw(targetRotation.getYaw());
        }
    }

    @EventTarget
    public void onFallFlying(FallFlyingEvent fallFlyingEvent) {
        if (isRotating && targetRotation != null) {
            fallFlyingEvent.setPitch(targetRotation.getPitch());
        }
    }

    static {
        isRotating = false;
        activeMovementCorrection = MovementCorrection.OFF;
        activeApplyMode = RotationApplyMode.OFF;
        rotationPhase = RotationPhase.IDLE;
        resetRotationOwner = null;
        actualServerRotation = null;
        theoreticalServerRotation = null;
        previousVisualRotation = null;
        currentVisualRotation = null;
        resetSnapshot = null;
        resetTicksRemaining = 0;
        outgoingMoveDebugTick = -1;
        outgoingMoveDebugIndex = 0;
    }

    private static void recordOutgoingMovePacket(
            ServerboundMovePlayerPacket packet,
            String source) {
        if (packet == null || mc == null || mc.player == null) {
            return;
        }
        synchronized (OUTGOING_MOVE_DEBUG) {
            int tick = mc.player.tickCount;
            if (outgoingMoveDebugTick != tick) {
                outgoingMoveDebugTick = tick;
                outgoingMoveDebugIndex = 0;
                OUTGOING_MOVE_DEBUG.clear();
            }
            outgoingMoveDebugIndex++;
            String type;
            if (packet.hasPosition() && packet.hasRotation()) {
                type = "PosRot";
            } else if (packet.hasPosition()) {
                type = "Pos";
            } else if (packet.hasRotation()) {
                type = "Rot";
            } else {
                type = "StatusOnly";
            }
            OUTGOING_MOVE_DEBUG.add("#" + outgoingMoveDebugIndex
                    + ":" + type
                    + "@" + source
                    + " pos=" + packet.getX(0.0) + "/" + packet.getY(0.0) + "/" + packet.getZ(0.0)
                    + " rot=" + packet.getYRot(0.0f) + "/" + packet.getXRot(0.0f));
        }
    }

    public enum RotationPhase {
        IDLE,
        ACTIVE,
        RESET
    }

    public enum PlacementRotationSource {
        ACTIVE_OWNER,
        EPHEMERAL_NORMAL,
        EPHEMERAL_ON_TICK,
        CONFLICT,
        UNAVAILABLE
    }

    public record EphemeralPositionRotationCommit(
            Rotation rotation,
            boolean dispatchRequested) {
        public EphemeralPositionRotationCommit {
            rotation = rotation == null ? null : rotation.clone();
        }
    }

    private record RotationResetSnapshot(
            RotationApplyMode applyMode,
            MovementCorrection movementCorrection,
            SmoothMode smoothMode,
            int smoothDurationTicks,
            double smoothSteepness,
            double maxYawSpeed,
            double maxPitchSpeed,
            double minStep,
            double rotationEpsilon,
            double interpolationHorizontalSpeedMin,
            double interpolationHorizontalSpeedMax,
            double interpolationVerticalSpeedMin,
            double interpolationVerticalSpeedMax,
            double interpolationDirectionChangeFactorMin,
            double interpolationDirectionChangeFactorMax,
            double interpolationMidpoint,
            boolean humanizeRotation,
            boolean snapToSensitivity,
            boolean normalizeYawForServerPackets,
            int ticksUntilReset,
            double resetThreshold,
            boolean resetRotation,
            boolean affectRayTrace,
            boolean affectUseItemRayTrace) {
    }
}
