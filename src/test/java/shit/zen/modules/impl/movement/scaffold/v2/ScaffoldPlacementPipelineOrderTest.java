package shit.zen.modules.impl.movement.scaffold.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import shit.zen.utils.game.BlockPlacementTarget;
import shit.zen.utils.game.DirectionalInput;
import shit.zen.utils.rotation.Rotation;
import shit.zen.utils.rotation.RotationHandler;

final class ScaffoldPlacementPipelineOrderTest {
    private static final Object OWNER = new Object();
    private static final BlockPos SUPPORT = new BlockPos(0, 63, 0);
    private static final Vec3 FRAME_POSITION = new Vec3(0.5, 64.0, 0.5);
    private static final Vec3 EYE = new Vec3(0.5, 65.62, 0.5);
    private static final Vec3 HIT_POINT = new Vec3(0.5, 64.0, 0.5);
    private static final Rotation TARGET_ROTATION = new Rotation(90.0f, 75.0f);
    private static final Rotation PLAYER_ROTATION = new Rotation(15.0f, 5.0f);

    @Test
    void normalUsesFramePositionTransactionWhileKeepingProviderActive() {
        RecordingEffects effects = new RecordingEffects(hit(Direction.UP, HIT_POINT));
        ScaffoldPlacementPipeline.Outcome outcome = pipeline(effects).place(
                OWNER,
                frame(InteractionHand.MAIN_HAND, Direction.UP, HIT_POINT),
                ScaffoldPlacementPipeline.RotationTiming.NORMAL,
                TARGET_ROTATION,
                ScaffoldPlacementPipeline.SwingMode.SHOW,
                0.0);

        assertEquals(ScaffoldPlacementPipeline.Status.SUCCESS, outcome.status());
        assertEquals(List.of(
                "raycast",
                "select:MAIN_HAND",
                "posrot:target",
                "use:MAIN_HAND",
                "swing:MAIN_HAND",
                "posrot:player"), effects.actions);
        assertEquals(List.of(FRAME_POSITION, FRAME_POSITION), effects.committedPositions);
        assertEquals(List.of(true, true), effects.committedOnGround);
        assertEquals(1, effects.forcedEphemeralCommits);
        assertEquals(RotationHandler.PlacementRotationSource.EPHEMERAL_NORMAL,
                outcome.rotationSource());
        assertTrue(outcome.detail().contains("PosRot(target)>PosRot(player)"));
    }

    @Test
    void onTickRestoresPlayerRotationAfterUse() {
        RecordingEffects effects = new RecordingEffects(
                hit(Direction.UP, HIT_POINT),
                false,
                PLAYER_ROTATION);
        ScaffoldPlacementPipeline.Outcome outcome = pipeline(effects).place(
                OWNER,
                frame(InteractionHand.MAIN_HAND, Direction.UP, HIT_POINT),
                ScaffoldPlacementPipeline.RotationTiming.ON_TICK,
                TARGET_ROTATION,
                ScaffoldPlacementPipeline.SwingMode.SHOW,
                0.0);

        assertEquals(ScaffoldPlacementPipeline.Status.SUCCESS, outcome.status());
        assertEquals(List.of(
                "raycast",
                "select:MAIN_HAND",
                "posrot:target",
                "use:MAIN_HAND",
                "swing:MAIN_HAND",
                "posrot:player"), effects.actions);
        assertEquals(1, effects.forcedEphemeralCommits);
        assertEquals(List.of(FRAME_POSITION, FRAME_POSITION), effects.committedPositions);
        assertEquals(List.of(true, true), effects.committedOnGround);
        assertTrue(outcome.detail().contains("PosRot(target)>PosRot(player)"));
    }

    @Test
    void newTellyPreUseRunsAfterTargetRotationAndBeforeOffhandPlacement() {
        RecordingEffects effects = new RecordingEffects(hit(Direction.UP, HIT_POINT));
        ScaffoldPlacementPipeline.Outcome outcome = pipeline(effects).place(
                OWNER,
                frame(InteractionHand.OFF_HAND, Direction.UP, HIT_POINT),
                ScaffoldPlacementPipeline.RotationTiming.NORMAL,
                TARGET_ROTATION,
                ScaffoldPlacementPipeline.SwingMode.SHOW,
                0.0,
                new ScaffoldPlacementPipeline.AttemptOptions(true));

        assertEquals(ScaffoldPlacementPipeline.Status.SUCCESS, outcome.status());
        assertEquals(List.of(
                "raycast",
                "select:OFF_HAND",
                "posrot:target",
                "pre-use:MAIN_HAND",
                "use:OFF_HAND",
                "swing:OFF_HAND",
                "posrot:player"), effects.actions);
        assertTrue(outcome.detail().contains("UseItem(main)"));
    }

    @Test
    void newTellyKeepsServerRotationThroughPredictiveInteraction() {
        RecordingEffects effects = new RecordingEffects(hit(Direction.UP, HIT_POINT));
        ScaffoldPlacementPipeline.Outcome outcome = pipeline(effects).place(
                OWNER,
                frame(InteractionHand.MAIN_HAND, Direction.UP, HIT_POINT),
                ScaffoldPlacementPipeline.RotationTiming.NORMAL,
                TARGET_ROTATION,
                ScaffoldPlacementPipeline.SwingMode.SHOW,
                0.0,
                new ScaffoldPlacementPipeline.AttemptOptions(true, true));

        assertEquals(ScaffoldPlacementPipeline.Status.SUCCESS, outcome.status());
        assertEquals(List.of(
                "raycast",
                "select:MAIN_HAND",
                "provider:snap",
                "pre-use:MAIN_HAND",
                "use:MAIN_HAND",
                "swing:MAIN_HAND"), effects.actions);
        assertEquals(List.of(), effects.committedPositions);
        assertEquals(0, effects.forcedEphemeralCommits);
        assertTrue(outcome.detail().contains("PosRot(target:pre-use)>UseItem(main)"));
        assertTrue(outcome.detail().contains("server-rotation-held"));
    }

    @Test
    void newTellyPreUseIsNotCalledWhenStrictTargetFails() {
        RecordingEffects effects = new RecordingEffects(
                new BlockHitResult(HIT_POINT, Direction.NORTH, SUPPORT.east(), false));
        ScaffoldPlacementPipeline.Outcome outcome = pipeline(effects).place(
                OWNER,
                frame(InteractionHand.MAIN_HAND, Direction.UP, HIT_POINT),
                ScaffoldPlacementPipeline.RotationTiming.NORMAL,
                TARGET_ROTATION,
                ScaffoldPlacementPipeline.SwingMode.SHOW,
                0.0,
                new ScaffoldPlacementPipeline.AttemptOptions(true));

        assertEquals(ScaffoldPlacementPipeline.Status.NO_HIT, outcome.status());
        assertEquals(List.of("raycast"), effects.actions);
    }

    @Test
    void attemptedPlacementRestoresRotationWhenUseItemOnReturnsPass() {
        RecordingEffects effects = new RecordingEffects(hit(Direction.UP, HIT_POINT));
        effects.useResult = InteractionResult.PASS;
        ScaffoldPlacementPipeline.Outcome outcome = pipeline(effects).place(
                OWNER,
                frame(InteractionHand.MAIN_HAND, Direction.UP, HIT_POINT),
                ScaffoldPlacementPipeline.RotationTiming.NORMAL,
                TARGET_ROTATION,
                ScaffoldPlacementPipeline.SwingMode.SHOW,
                0.0,
                new ScaffoldPlacementPipeline.AttemptOptions(true));

        assertEquals(ScaffoldPlacementPipeline.Status.PLACE_FAILED, outcome.status());
        assertEquals(List.of(
                "raycast",
                "select:MAIN_HAND",
                "posrot:target",
                "pre-use:MAIN_HAND",
                "use:MAIN_HAND",
                "posrot:player"), effects.actions);
    }

    @Test
    void onTickSkipsTargetPacketWhenServerRotationAlreadyMatches() {
        RecordingEffects effects = new RecordingEffects(
                hit(Direction.UP, HIT_POINT),
                false,
                TARGET_ROTATION);
        ScaffoldPlacementPipeline.Outcome outcome = pipeline(effects).place(
                OWNER,
                frame(InteractionHand.MAIN_HAND, Direction.UP, HIT_POINT),
                ScaffoldPlacementPipeline.RotationTiming.ON_TICK,
                TARGET_ROTATION,
                ScaffoldPlacementPipeline.SwingMode.SHOW,
                0.0);

        assertEquals(ScaffoldPlacementPipeline.Status.SUCCESS, outcome.status());
        assertEquals(List.of(
                "raycast",
                "select:MAIN_HAND",
                "use:MAIN_HAND",
                "swing:MAIN_HAND",
                "posrot:player"), effects.actions);
        assertEquals(0, effects.forcedEphemeralCommits);
        assertEquals(List.of(FRAME_POSITION), effects.committedPositions);
        assertTrue(outcome.detail().contains("target-already-synced>PosRot(player)"));
    }

    @Test
    void onTickSnapActivatesProviderOnlyAfterStrictGate() {
        RecordingEffects effects = new RecordingEffects(hit(Direction.UP, HIT_POINT));
        Rotation continuousCommit = new Rotation(1170.0f, TARGET_ROTATION.getPitch());
        effects.targetCommitOverride = continuousCommit;
        ScaffoldPlacementPipeline.Outcome outcome = pipeline(effects).place(
                OWNER,
                frame(InteractionHand.OFF_HAND, Direction.UP, HIT_POINT),
                ScaffoldPlacementPipeline.RotationTiming.ON_TICK_SNAP,
                TARGET_ROTATION,
                ScaffoldPlacementPipeline.SwingMode.SHOW,
                0.0);

        assertEquals(ScaffoldPlacementPipeline.Status.SUCCESS, outcome.status());
        assertEquals(List.of(
                "raycast",
                "select:OFF_HAND",
                "posrot:target",
                "provider:snap",
                "use:OFF_HAND",
                "swing:OFF_HAND"), effects.actions);
        assertEquals(List.of(FRAME_POSITION), effects.committedPositions);
        assertEquals(List.of(true), effects.committedOnGround);
        assertEquals(continuousCommit.getYaw(), effects.snapRotation.getYaw());
        assertEquals(continuousCommit.getPitch(), effects.snapRotation.getPitch());
    }

    @Test
    void onTickSnapProviderConflictRejectsBeforePositionRotation() {
        RecordingEffects effects = new RecordingEffects(hit(Direction.UP, HIT_POINT));
        effects.snapAvailable = false;

        ScaffoldPlacementPipeline.Outcome outcome = pipeline(effects).place(
                OWNER,
                frame(InteractionHand.MAIN_HAND, Direction.UP, HIT_POINT),
                ScaffoldPlacementPipeline.RotationTiming.ON_TICK_SNAP,
                TARGET_ROTATION,
                ScaffoldPlacementPipeline.SwingMode.SHOW,
                0.0);

        assertEquals(ScaffoldPlacementPipeline.Status.ROTATION_CONFLICT, outcome.status());
        assertEquals(List.of("raycast", "select:MAIN_HAND"), effects.actions);
        assertEquals(List.of(), effects.committedPositions);
    }

    @Test
    void strictMismatchProducesNoSlotRotationOrInteractionEffects() {
        RecordingEffects effects = new RecordingEffects(
                new BlockHitResult(HIT_POINT, Direction.NORTH, SUPPORT.east(), false));

        for (ScaffoldPlacementPipeline.RotationTiming timing
                : ScaffoldPlacementPipeline.RotationTiming.values()) {
            effects.actions.clear();
            ScaffoldPlacementPipeline.Outcome outcome = pipeline(effects).place(
                    OWNER,
                    frame(InteractionHand.MAIN_HAND, Direction.UP, HIT_POINT),
                    timing,
                    TARGET_ROTATION,
                    ScaffoldPlacementPipeline.SwingMode.SHOW,
                    0.0);

            assertEquals(ScaffoldPlacementPipeline.Status.NO_HIT, outcome.status());
            assertEquals(List.of("raycast"), effects.actions, timing.name());
        }
    }

    @Test
    void minDistanceRejectsBeforeLateSlotAndRotation() {
        Vec3 sideHit = new Vec3(0.0, 64.0, EYE.z);
        RecordingEffects effects = new RecordingEffects(hit(Direction.NORTH, sideHit));
        ScaffoldPlacementPipeline.Outcome outcome = pipeline(effects).place(
                OWNER,
                frame(InteractionHand.MAIN_HAND, Direction.NORTH, sideHit),
                ScaffoldPlacementPipeline.RotationTiming.ON_TICK,
                TARGET_ROTATION,
                ScaffoldPlacementPipeline.SwingMode.SHOW,
                0.1);

        assertEquals(ScaffoldPlacementPipeline.Status.NO_HIT, outcome.status());
        assertEquals(List.of("raycast"), effects.actions);
    }

    @Test
    void playerTargetIntersectionRejectsBeforeRaycastSlotAndRotation() {
        RecordingEffects effects = new RecordingEffects(
                hit(Direction.UP, HIT_POINT),
                true);

        for (ScaffoldPlacementPipeline.RotationTiming timing
                : ScaffoldPlacementPipeline.RotationTiming.values()) {
            effects.actions.clear();
            ScaffoldPlacementPipeline.Outcome outcome = pipeline(effects).place(
                    OWNER,
                    frame(InteractionHand.MAIN_HAND, Direction.UP, HIT_POINT),
                    timing,
                    TARGET_ROTATION,
                    ScaffoldPlacementPipeline.SwingMode.SHOW,
                    0.0);

            assertEquals(ScaffoldPlacementPipeline.Status.INVALID_TARGET, outcome.status());
            assertEquals(List.of(), effects.actions, timing.name());
            assertEquals("player-target-intersection packets=none", outcome.detail());
        }
    }

    private static ScaffoldPlacementPipeline pipeline(RecordingEffects effects) {
        return new ScaffoldPlacementPipeline(effects);
    }

    private static ScaffoldTickFrame frame(
            InteractionHand hand,
            Direction face,
            Vec3 hitPoint) {
        BlockPlacementTarget target = new BlockPlacementTarget(
                SUPPORT,
                SUPPORT.relative(face),
                face,
                hitPoint,
                hitPoint.y,
                TARGET_ROTATION);
        return new ScaffoldTickFrame(
                1L,
                20,
                FRAME_POSITION,
                EYE,
                Pose.STANDING,
                DirectionalInput.NONE,
                null,
                null,
                null,
                target,
                TARGET_ROTATION,
                hand,
                hand == InteractionHand.MAIN_HAND ? 4 : -1,
                null,
                63);
    }

    private static BlockHitResult hit(Direction face, Vec3 point) {
        return new BlockHitResult(point, face, SUPPORT, false);
    }

    private static final class RecordingEffects implements ScaffoldPlacementPipeline.Effects {
        private final List<String> actions = new ArrayList<>();
        private final BlockHitResult hit;
        private final boolean intersectsPlayerTarget;
        private Rotation logicalServerRotation;
        private final List<Vec3> committedPositions = new ArrayList<>();
        private final List<Boolean> committedOnGround = new ArrayList<>();
        private boolean snapAvailable = true;
        private int forcedEphemeralCommits;
        private Rotation targetCommitOverride;
        private Rotation snapRotation;
        private InteractionResult useResult = InteractionResult.SUCCESS;

        private RecordingEffects(BlockHitResult hit) {
            this(hit, false, null);
        }

        private RecordingEffects(BlockHitResult hit, boolean intersectsPlayerTarget) {
            this(hit, intersectsPlayerTarget, null);
        }

        private RecordingEffects(
                BlockHitResult hit,
                boolean intersectsPlayerTarget,
                Rotation actualServerRotation) {
            this.hit = hit;
            this.intersectsPlayerTarget = intersectsPlayerTarget;
            this.logicalServerRotation = actualServerRotation;
        }

        @Override
        public boolean hasPlacementContext(ScaffoldTickFrame frame) {
            return true;
        }

        @Override
        public boolean intersectsPlayerTarget(ScaffoldTickFrame frame) {
            return this.intersectsPlayerTarget;
        }

        @Override
        public boolean hasExternalRotationOwner(Object owner) {
            return false;
        }

        @Override
        public Rotation getActiveRotation(Object owner) {
            return TARGET_ROTATION;
        }

        @Override
        public Rotation getPlayerRotation() {
            return PLAYER_ROTATION;
        }

        @Override
        public Vec3 getEyePosition() {
            return EYE;
        }

        @Override
        public BlockHitResult rayTrace(Vec3 eyePosition, Rotation rotation) {
            this.actions.add("raycast");
            return this.hit;
        }

        @Override
        public boolean selectFrameHand(ScaffoldTickFrame frame) {
            this.actions.add("select:" + frame.hand());
            return true;
        }

        @Override
        public boolean canActivateSnapRotation(Object owner) {
            return this.snapAvailable;
        }

        @Override
        public boolean activateSnapRotation(Object owner, Rotation rotation) {
            this.actions.add("provider:snap");
            this.snapRotation = rotation == null ? null : rotation.clone();
            return true;
        }

        @Override
        public boolean isPlayerOnGround() {
            return true;
        }

        @Override
        public RotationHandler.EphemeralPositionRotationCommit commitEphemeralPositionRotation(
                Object owner,
                Vec3 position,
                Rotation rotation,
                boolean onGround,
                boolean forceSend) {
            boolean targetRequest = rotation.getYaw() == TARGET_ROTATION.getYaw()
                    && rotation.getPitch() == TARGET_ROTATION.getPitch();
            Rotation committedRotation = targetRequest && this.targetCommitOverride != null
                    ? this.targetCommitOverride
                    : rotation;
            boolean dispatchRequested = forceSend
                    || !sameRotation(committedRotation, this.logicalServerRotation);
            if (!dispatchRequested) {
                return new RotationHandler.EphemeralPositionRotationCommit(committedRotation, false);
            }
            if (forceSend) {
                this.forcedEphemeralCommits++;
            }
            this.committedPositions.add(position);
            this.committedOnGround.add(onGround);
            this.actions.add(targetRequest
                    ? "posrot:target"
                    : "posrot:player");
            this.logicalServerRotation = committedRotation;
            return new RotationHandler.EphemeralPositionRotationCommit(committedRotation, true);
        }

        private static boolean sameRotation(Rotation first, Rotation second) {
            return first != null
                    && second != null
                    && Float.compare(first.getYaw(), second.getYaw()) == 0
                    && Float.compare(first.getPitch(), second.getPitch()) == 0;
        }

        @Override
        public InteractionResult useItemOn(InteractionHand hand, BlockHitResult hit) {
            this.actions.add("use:" + hand);
            return this.useResult;
        }

        @Override
        public void useItem(InteractionHand hand) {
            this.actions.add("pre-use:" + hand);
        }

        @Override
        public void swing(
                ScaffoldPlacementPipeline.SwingMode swingMode,
                InteractionHand hand) {
            this.actions.add("swing:" + hand);
        }
    }
}
