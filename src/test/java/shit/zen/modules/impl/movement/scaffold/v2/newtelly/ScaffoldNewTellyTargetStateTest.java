package shit.zen.modules.impl.movement.scaffold.v2.newtelly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class ScaffoldNewTellyTargetStateTest {
    @Test
    void landingCapturesBridgeLayerAndUsesFloorForNegativeCoordinates() {
        ScaffoldNewTellyTargetState state = new ScaffoldNewTellyTargetState();

        var tick = state.beginTick(new Vec3(-0.1, 64.0, -1.1), 64, true, false);

        assertEquals(63, tick.bridgeY());
        assertEquals(new BlockPos(-1, 63, -2), tick.currentCell());
    }

    @Test
    void upTellyFreezesPendingAndCommitsRequestedLayerAfterSuccess() {
        ScaffoldNewTellyTargetState state = new ScaffoldNewTellyTargetState();
        state.beginTick(new Vec3(0.5, 64.0, 0.5), 64, true, false);
        var automaticJump = state.beginTick(new Vec3(1.5, 65.2, 0.5), 65, false, false);
        assertEquals(63, state.bridgeY());
        assertEquals(new BlockPos(1, 63, 0), automaticJump.currentCell());
        state.rememberPending(new BlockPos(1, 63, 0));

        var jump = state.beginTick(new Vec3(1.5, 65.2, 0.5), 65, false, true);
        assertEquals(63, jump.bridgeY());
        assertEquals(true, jump.physicalJumpStarted());
        assertEquals(64, state.requestedBridgeY());
        assertEquals(true, state.hasDeferredBridgeTransition());
        assertEquals(new BlockPos(1, 63, 0), state.pendingCell());

        state.onPlacementSuccess(new BlockPos(1, 63, 0));
        var promotedAfterRelease = state.beginTick(
                new Vec3(1.5, 65.2, 0.5), 65, false, false, true);
        assertEquals(64, promotedAfterRelease.bridgeY());
        assertNull(state.requestedBridgeY());
        assertNull(state.pendingCell());

        var held = state.beginTick(
                new Vec3(1.5, 66.2, 0.5), 66, false, true, true);
        assertEquals(65, held.bridgeY());
        assertEquals(true, held.physicalJumpStarted());
    }

    @Test
    void fallingBelowTheBridgeLayerSuspendsScaffoldAndClearsPendingTarget() {
        ScaffoldNewTellyTargetState state = new ScaffoldNewTellyTargetState();
        state.beginTick(new Vec3(0.5, 64.0, 0.5), 64, true, false);
        state.beginTick(new Vec3(0.5, 65.2, 0.5), 65, false, true, true);
        state.beginTick(new Vec3(0.5, 66.2, 0.5), 66, false, true, true);
        state.rememberPending(new BlockPos(0, 65, 0));

        var descending = state.beginTick(new Vec3(0.5, 65.39, 0.5), 65, false, true);

        assertEquals(65, descending.bridgeY());
        assertNull(descending.currentCell());
        assertNull(state.pendingCell());
        assertNull(state.requestedBridgeY());
    }

    @Test
    void airborneStartWaitsForLandingInsteadOfReinitializingARescueLayer() {
        ScaffoldNewTellyTargetState state = new ScaffoldNewTellyTargetState();

        var airborne = state.beginTick(new Vec3(0.5, 60.5, 0.5), 60, false, true);
        assertNull(airborne.bridgeY());
        assertNull(airborne.currentCell());

        var landed = state.beginTick(new Vec3(0.5, 58.0, 0.5), 58, true, true);
        assertEquals(57, landed.bridgeY());
        assertEquals(new BlockPos(0, 57, 0), landed.currentCell());
    }

    @Test
    void pendingTargetSurvivesRotationWindowThenExpires() {
        ScaffoldNewTellyTargetState state = new ScaffoldNewTellyTargetState();
        Vec3 position = new Vec3(0.5, 64.0, 0.5);
        BlockPos pending = new BlockPos(1, 63, 0);
        state.beginTick(position, 64, true, false);
        state.rememberPending(pending);

        for (int age = 1; age <= 6; age++) {
            state.beginTick(position, 64, true, false);
            state.expirePendingAfter(6);
            assertEquals(pending, state.pendingCell());
            assertEquals(age, state.pendingAge());
        }

        state.beginTick(position, 64, true, false);
        state.expirePendingAfter(6);
        assertNull(state.pendingCell());
        assertEquals(0, state.pendingAge());
    }

    @Test
    void freshConfirmationRefreshesPendingAgeBeforeCrossingTheCellBoundary() {
        ScaffoldNewTellyTargetState state = new ScaffoldNewTellyTargetState();
        Vec3 position = new Vec3(0.5, 64.0, 0.5);
        BlockPos pending = new BlockPos(1, 63, 0);
        state.beginTick(position, 64, true, false);

        for (int tick = 0; tick < 10; tick++) {
            state.beginTick(position, 64, true, false);
            state.expirePendingAfter(5);
            state.rememberPending(pending);
            assertEquals(0, state.pendingAge());
        }

        state.beginTick(new Vec3(2.1, 64.0, 0.5), 64, false, false);
        state.expirePendingAfter(5);

        assertEquals(pending, state.pendingCell());
        assertEquals(1, state.pendingAge());
    }

    @Test
    void onlyMatchingPlacementSuccessClearsPendingTarget() {
        ScaffoldNewTellyTargetState state = new ScaffoldNewTellyTargetState();
        BlockPos pending = new BlockPos(1, 63, 0);
        state.rememberPending(pending);

        state.onPlacementSuccess(pending.east());
        assertEquals(pending, state.pendingCell());

        state.onPlacementSuccess(pending);
        assertNull(state.pendingCell());
    }

    @Test
    void pendingTargetCannotFollowThePlayerBeyondTheAdjacentCell() {
        ScaffoldNewTellyTargetState state = new ScaffoldNewTellyTargetState();
        BlockPos pending = new BlockPos(1, 63, 0);
        state.rememberPending(pending);

        state.discardPendingOutside(new BlockPos(2, 63, 1));
        assertEquals(pending, state.pendingCell());

        state.discardPendingOutside(new BlockPos(3, 63, 1));
        assertNull(state.pendingCell());
    }

    @Test
    void deferredPendingMayUseTwoCellBoundButNormalPendingMayNot() {
        ScaffoldNewTellyTargetState normal = new ScaffoldNewTellyTargetState();
        BlockPos pending = new BlockPos(1, 63, 0);
        BlockPos twoCellsAway = new BlockPos(3, 63, 1);
        normal.rememberPending(pending);
        normal.discardPendingOutside(twoCellsAway);
        assertNull(normal.pendingCell());

        ScaffoldNewTellyTargetState deferred = new ScaffoldNewTellyTargetState();
        deferred.beginTick(new Vec3(0.5, 64.0, 0.5), 64, true, false);
        deferred.rememberPending(pending);
        deferred.beginTick(new Vec3(0.5, 65.2, 0.5), 65, false, true);
        deferred.discardPendingOutside(twoCellsAway, 2);
        assertEquals(pending, deferred.pendingCell());
    }

    @Test
    void deferredPendingRejectsTwoByTwoDiagonalStaleTarget() {
        ScaffoldNewTellyTargetState state = new ScaffoldNewTellyTargetState();
        BlockPos pending = new BlockPos(0, 63, 0);
        state.beginTick(new Vec3(0.5, 64.0, 0.5), 64, true, false);
        state.rememberPending(pending);
        state.beginTick(new Vec3(0.5, 65.2, 0.5), 65, false, true);

        state.discardPendingOutside(new BlockPos(2, 63, 2), 2, 3);

        assertNull(state.pendingCell());
        assertEquals(64, state.requestedBridgeY());
        assertEquals(true, state.bridgePromotionBlocked());
    }

    @Test
    void invalidatedDeferredTargetLeavesLastAgeTwoConnectorAvailable() {
        ScaffoldNewTellyTargetState state = new ScaffoldNewTellyTargetState();
        BlockPos anchor = new BlockPos(0, 63, 0);
        BlockPos stalePending = new BlockPos(0, 63, -1);
        state.beginTick(new Vec3(0.5, 64.0, 0.5), 64, true, false);
        state.onPlacementSuccess(anchor);
        state.rememberPending(stalePending);
        state.beginTick(new Vec3(1.2, 65.2, 0.5), 65, false, true);
        var lastConnectorTick = state.beginTick(
                new Vec3(2.2, 65.3, 1.2), 65, false, true);

        state.discardPendingOutside(lastConnectorTick.currentCell(), 2, 3);

        assertNull(state.pendingCell());
        assertEquals(2, state.recentPlacedAge());
        assertEquals(
                new BlockPos(1, 63, 0),
                state.recentPlacementConnectorCell(lastConnectorTick.currentCell(), 2));
    }

    @Test
    void smallDropBelowBridgeTopKeepsCurrentTransactionUntilGraceExpires() {
        ScaffoldNewTellyTargetState state = new ScaffoldNewTellyTargetState();
        BlockPos pending = new BlockPos(0, 63, 1);
        state.beginTick(new Vec3(0.5, 64.0, 0.5), 64, true, false);
        state.rememberPending(pending);

        var edgeDrop = state.beginTick(new Vec3(0.5, 63.51, 0.8), 63, false, true);
        assertEquals(new BlockPos(0, 63, 0), edgeDrop.currentCell());
        assertEquals(pending, state.pendingCell());

        var realFall = state.beginTick(new Vec3(0.5, 63.49, 0.8), 63, false, true);
        assertNull(realFall.currentCell());
        assertNull(state.pendingCell());
    }

    @Test
    void bridgeTopGraceIsBoundedToThreeTicks() {
        ScaffoldNewTellyTargetState state = new ScaffoldNewTellyTargetState();
        state.beginTick(new Vec3(0.5, 64.0, 0.5), 64, true, false);

        for (int tick = 1; tick <= 3; tick++) {
            var grace = state.beginTick(new Vec3(0.5, 63.75, 0.5), 63, false, true);
            assertEquals(new BlockPos(0, 63, 0), grace.currentCell());
            assertEquals(tick, state.bridgeTopGraceTicks());
        }
        var expired = state.beginTick(new Vec3(0.5, 63.75, 0.5), 63, false, true);
        assertNull(expired.currentCell());
    }

    @Test
    void recentPlacementProvidesOnlyBoundedLShapedConnector() {
        ScaffoldNewTellyTargetState state = new ScaffoldNewTellyTargetState();
        BlockPos anchor = new BlockPos(10, 63, 10);
        state.beginTick(new Vec3(10.5, 64.0, 10.5), 64, true, false);
        state.onPlacementSuccess(anchor);

        assertEquals(
                new BlockPos(11, 63, 10),
                state.recentPlacementConnectorCell(new BlockPos(12, 63, 11), 2));
        assertEquals(
                new BlockPos(10, 63, 9),
                state.recentPlacementConnectorCell(new BlockPos(11, 63, 8), 2));
        assertNull(state.recentPlacementConnectorCell(new BlockPos(12, 63, 12), 2));
        assertNull(state.recentPlacementConnectorCell(new BlockPos(12, 62, 11), 2));

        state.beginTick(new Vec3(10.5, 64.0, 10.5), 64, true, false);
        state.beginTick(new Vec3(10.5, 64.0, 10.5), 64, true, false);
        state.beginTick(new Vec3(10.5, 64.0, 10.5), 64, true, false);
        assertNull(state.recentPlacementConnectorCell(new BlockPos(12, 63, 11), 2));
    }

    @Test
    void deferredTransitionFreezesCellAndUsesItsOwnAge() {
        ScaffoldNewTellyTargetState state = new ScaffoldNewTellyTargetState();
        BlockPos frozen = new BlockPos(1, 63, 0);
        state.beginTick(new Vec3(0.5, 64.0, 0.5), 64, true, false);
        state.rememberPending(frozen);

        state.beginTick(new Vec3(1.2, 65.2, 0.5), 65, false, true);
        state.beginTick(new Vec3(1.4, 65.3, 0.5), 65, false, true);
        assertEquals(1, state.pendingAge());

        state.rememberPending(frozen.east());
        assertEquals(frozen, state.pendingCell());
        assertEquals(1, state.pendingAge());
        assertEquals(64, state.requestedBridgeY());
    }

    @Test
    void expiredDeferredTargetKeepsRequestedLayerForLowerSupportRecovery() {
        ScaffoldNewTellyTargetState state = new ScaffoldNewTellyTargetState();
        BlockPos expiredTarget = new BlockPos(1, 63, 0);
        BlockPos recoveryTarget = new BlockPos(2, 63, 0);
        Vec3 raisedPosition = new Vec3(1.2, 65.2, 0.5);

        state.beginTick(new Vec3(0.5, 64.0, 0.5), 64, true, false);
        state.rememberPending(expiredTarget);
        state.beginTick(raisedPosition, 65, false, true);
        for (int age = 1; age <= 6; age++) {
            state.beginTick(raisedPosition, 65, false, true);
            state.expireBridgeTransitionAfter(5);
        }

        assertNull(state.pendingCell());
        assertEquals(64, state.requestedBridgeY());
        assertEquals(true, state.bridgePromotionBlocked());

        state.rememberPending(recoveryTarget);
        state.onPlacementSuccess(recoveryTarget);
        var recoveredAfterRelease = state.beginTick(
                raisedPosition, 65, false, false, true);
        assertEquals(64, recoveredAfterRelease.bridgeY());
        assertNull(state.requestedBridgeY());
        assertEquals(false, state.bridgePromotionBlocked());
    }

    @Test
    void abortedTransitionBlocksUnsupportedPromotionUntilLowerLayerSuccess() {
        ScaffoldNewTellyTargetState state = new ScaffoldNewTellyTargetState();
        BlockPos lowerLayerTarget = new BlockPos(1, 63, 0);
        state.beginTick(new Vec3(0.5, 64.0, 0.5), 64, true, false);
        state.rememberPending(lowerLayerTarget);
        state.beginTick(new Vec3(1.2, 65.2, 0.5), 65, false, true);

        state.clearPending();
        assertEquals(true, state.bridgePromotionBlocked());
        var blocked = state.beginTick(new Vec3(1.3, 65.3, 0.5), 65, false, true);
        assertEquals(63, blocked.bridgeY());

        state.rememberPending(lowerLayerTarget);
        state.onPlacementSuccess(lowerLayerTarget);
        var promoted = state.beginTick(
                new Vec3(1.4, 65.3, 0.5), 65, false, false, true);
        assertEquals(64, promoted.bridgeY());
        assertEquals(false, state.bridgePromotionBlocked());
    }

    @Test
    void diagonalLowerPlacementDoesNotPromoteUntilCardinalSupportExists() {
        ScaffoldNewTellyTargetState state = new ScaffoldNewTellyTargetState();
        BlockPos diagonalTarget = new BlockPos(0, 63, 0);
        BlockPos cardinalTarget = new BlockPos(1, 63, 0);
        Vec3 diagonalPlayerPosition = new Vec3(1.2, 65.2, 1.2);

        state.beginTick(new Vec3(0.5, 64.0, 0.5), 64, true, false);
        state.rememberPending(diagonalTarget);
        state.beginTick(diagonalPlayerPosition, 65, false, true);

        state.onPlacementSuccess(diagonalTarget);
        var unsupported = state.beginTick(
                diagonalPlayerPosition, 65, false, false, false);
        assertEquals(63, unsupported.bridgeY());
        assertEquals(64, state.requestedBridgeY());
        assertNull(state.pendingCell());

        state.rememberPending(cardinalTarget);
        state.onPlacementSuccess(cardinalTarget);
        var supportedAfterRelease = state.beginTick(
                diagonalPlayerPosition, 65, false, false, true);
        assertEquals(64, supportedAfterRelease.bridgeY());
        assertNull(state.requestedBridgeY());
        assertNull(state.pendingCell());
    }

    @Test
    void lowerPlacementCompletedBeforeDiagonalCrossingStillRequiresRequestedLayerTarget() {
        ScaffoldNewTellyTargetState state = new ScaffoldNewTellyTargetState();
        BlockPos lowerTarget = new BlockPos(0, 63, 0);

        state.beginTick(new Vec3(0.5, 64.0, 0.5), 64, true, false);
        state.rememberPending(lowerTarget);
        state.onPlacementSuccess(lowerTarget);

        var unsupportedCrossing = state.beginTick(
                new Vec3(1.2, 65.2, 1.2), 65, false, true, false);

        assertEquals(63, unsupportedCrossing.bridgeY());
        assertEquals(64, state.requestedBridgeY());
        assertNull(state.pendingCell());
    }

    @Test
    void skippedPlayerHeightCanAdvanceOnlyOneBridgeLayerPerTick() {
        ScaffoldNewTellyTargetState state = new ScaffoldNewTellyTargetState();
        state.beginTick(new Vec3(0.5, 64.0, 0.5), 64, true, false);

        var skippedHeight = state.beginTick(
                new Vec3(0.5, 67.2, 0.5), 67, false, true, true);

        assertEquals(64, skippedHeight.bridgeY());
        assertNull(state.requestedBridgeY());
    }

    @Test
    void lateLowerLayerSuccessDoesNotPromoteAboveTheCurrentPlayerHeight() {
        ScaffoldNewTellyTargetState state = new ScaffoldNewTellyTargetState();
        BlockPos lowerLayerTarget = new BlockPos(1, 63, 0);
        state.beginTick(new Vec3(0.5, 64.0, 0.5), 64, true, false);
        state.rememberPending(lowerLayerTarget);
        state.beginTick(new Vec3(1.2, 65.2, 0.5), 65, false, true);

        state.onPlacementSuccess(lowerLayerTarget);
        var descending = state.beginTick(new Vec3(1.2, 64.8, 0.5), 64, false, false);

        assertEquals(63, descending.bridgeY());
        assertNull(state.requestedBridgeY());
    }

}
