package shit.zen.modules.impl.movement.scaffold.v2.newtelly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import shit.zen.modules.impl.movement.scaffold.v2.newtelly.ScaffoldNewTellyPolicy.BlockSlotMode;
import shit.zen.modules.impl.movement.scaffold.v2.newtelly.ScaffoldNewTellyPolicy.JumpMode;
import shit.zen.modules.impl.movement.scaffold.v2.newtelly.ScaffoldNewTellyPolicy.MovementFrame;
import shit.zen.modules.impl.movement.scaffold.v2.newtelly.ScaffoldNewTellyPolicy.RotationNoise;
import shit.zen.modules.impl.movement.scaffold.v2.newtelly.ScaffoldNewTellyPolicy.Settings;
import shit.zen.modules.impl.movement.scaffold.v2.newtelly.ScaffoldNewTellyPolicy.SlotCandidate;
import shit.zen.modules.impl.movement.scaffold.v2.newtelly.ScaffoldNewTellyPolicy.TimingFrame;
import shit.zen.utils.rotation.Rotation;

final class ScaffoldNewTellyPolicyTest {
    @Test
    void screenshotDefaultsMatchTheSelectedSouthsideProfile() {
        Settings settings = Settings.SCREENSHOT_DEFAULTS;

        assertTrue(settings.alwaysUpdateRotation());
        assertEquals(1, settings.placeTick());
        assertEquals(3, settings.rotationTick());
        assertEquals(5, ScaffoldNewTellyPolicy.pendingTargetTicks(settings));
        assertFalse(settings.noUpTelly());
        assertTrue(settings.heyPixelUpTelly());
        assertFalse(settings.safeMode());
        assertFalse(settings.testOnGround());
        assertFalse(settings.fixRotation());
        assertFalse(settings.slowUpTelly());
        assertTrue(settings.duplicateRotPlace());
        assertTrue(settings.interactItemBeforePlace());
        assertEquals(JumpMode.NORMAL, settings.jumpMode());
        assertEquals(BlockSlotMode.FARTHEST, settings.blockSlotMode());
    }

    @Test
    void normalAndParkourJumpModesUseSouthsideGroundGate() {
        ScaffoldNewTellyPolicy policy = new ScaffoldNewTellyPolicy();
        Settings normal = settings(false, false, JumpMode.NORMAL, false, true);

        assertTrue(policy.movement(normal,
                new MovementFrame(1, false, true, true, false, false)).jump());
        assertFalse(policy.movement(normal,
                new MovementFrame(1, true, true, true, false, false)).jump());

        Settings safeWait = settings(true, false, JumpMode.NORMAL, false, true);
        assertFalse(policy.movement(safeWait,
                new MovementFrame(1, false, true, true, false, false)).jump());
        assertTrue(policy.movement(safeWait,
                new MovementFrame(2, false, true, true, false, false)).jump());

        Settings parkour = settings(false, false, JumpMode.PARKOUR, false, true);
        assertFalse(policy.movement(parkour,
                new MovementFrame(1, false, true, true, false, false)).jump());
        assertTrue(policy.movement(parkour,
                new MovementFrame(1, false, true, true, false, true)).jump());

        Settings none = settings(false, false, JumpMode.NONE, false, true);
        assertFalse(policy.movement(none,
                new MovementFrame(1, true, true, true, false, false)).jump());
        assertFalse(policy.movement(none,
                new MovementFrame(1, false, true, true, false, false)).jump());
    }

    @Test
    void safeTestOnGroundSlowAndPlacementWindowsMatchSourceConditions() {
        ScaffoldNewTellyPolicy policy = new ScaffoldNewTellyPolicy();
        Settings settings = settings(true, true, JumpMode.NORMAL, false, true);

        assertEquals(0.2f, policy.movement(settings,
                new MovementFrame(1, true, true, true, false, false)).inputScale());
        assertTrue(policy.canPlace(settings, new TimingFrame(1, 0, true, true)));
        assertFalse(policy.canPlace(settings, new TimingFrame(1, 0, false, false)));
        assertTrue(policy.canPlace(settings, new TimingFrame(0, 1, false, false)));
    }

    @Test
    void heyPixelGroundAndAirStagesUseTheAgreedYawLimiter() {
        ScaffoldNewTellyPolicy policy = new ScaffoldNewTellyPolicy();
        Settings safe = settings(true, false, JumpMode.NORMAL, false, false);
        Rotation target = new Rotation(100.0f, 72.0f);
        Rotation server = new Rotation(0.0f, 0.0f);
        Rotation player = new Rotation(20.0f, 10.0f);

        var ground = policy.rotation(
                safe,
                new TimingFrame(1, 0, true, false),
                target,
                server,
                player,
                false,
                RotationNoise.NONE);
        assertEquals(50.0f, ground.rotation().getYaw(), 1.0E-6f);
        assertEquals(75.5f, ground.rotation().getPitch(), 1.0E-6f);
        assertEquals(2, ground.jumpDelayTicks());

        Settings forcedSettings = settings(true, true, JumpMode.NORMAL, false, false);
        var forcedGround = policy.rotation(
                forcedSettings,
                new TimingFrame(1, 0, true, true),
                target,
                server,
                player,
                false,
                RotationNoise.NONE);
        assertEquals(100.0f, forcedGround.rotation().getYaw(), 1.0E-6f);
        assertEquals(2, forcedGround.jumpDelayTicks());

        Settings airSettings = settings(false, false, JumpMode.NORMAL, false, false);
        var airOne = policy.rotation(
                airSettings,
                new TimingFrame(0, 1, false, false),
                target,
                server,
                player,
                false,
                RotationNoise.NONE);
        assertEquals(80.0f, airOne.rotation().getYaw(), 1.0E-6f);
        assertEquals(72.0f, airOne.rotation().getPitch(), 1.0E-6f);

        var airTwo = policy.rotation(
                airSettings,
                new TimingFrame(0, 2, false, false),
                target,
                server,
                player,
                false,
                RotationNoise.NONE);
        assertEquals(50.0f, airTwo.rotation().getYaw(), 1.0E-6f);
    }

    @Test
    void noUpTellyAndStrictPreviousReuseDoNotInventFallbackAngles() {
        ScaffoldNewTellyPolicy policy = new ScaffoldNewTellyPolicy();
        Settings noUp = settings(false, false, JumpMode.NORMAL, true, false);
        Rotation direct = policy.rotation(
                noUp,
                new TimingFrame(0, 1, true, false),
                new Rotation(90.0f, 70.0f),
                new Rotation(0.0f, 0.0f),
                new Rotation(15.0f, 5.0f),
                false,
                RotationNoise.NONE).rotation();
        assertEquals(90.0f, direct.getYaw(), 1.0E-6f);

        Settings seed = settings(false, false, JumpMode.NORMAL, false, false);
        policy.rotation(
                seed,
                new TimingFrame(0, 3, false, false),
                new Rotation(90.0f, 70.0f),
                new Rotation(0.0f, 0.0f),
                new Rotation(15.0f, 5.0f),
                false,
                RotationNoise.NONE);

        Settings keepStrict = new Settings(
                false, 1, 3, false, true, false, false, false, false,
                false, true, JumpMode.NORMAL, BlockSlotMode.FARTHEST);
        Rotation reused = policy.rotation(
                keepStrict,
                new TimingFrame(0, 3, false, false),
                new Rotation(-90.0f, 60.0f),
                new Rotation(0.0f, 0.0f),
                new Rotation(15.0f, 5.0f),
                true,
                RotationNoise.NONE).rotation();
        assertEquals(90.0f, reused.getYaw(), 1.0E-6f);
    }

    @Test
    void strictPreviousHitAndBoundedGapHoldStabilizeDisabledAlwaysUpdateRotation() {
        ScaffoldNewTellyPolicy policy = new ScaffoldNewTellyPolicy();
        Settings settings = new Settings(
                false, 1, 3, false, true, false, false, false, false,
                false, true, JumpMode.NORMAL, BlockSlotMode.FARTHEST);
        Rotation seeded = policy.rotation(
                settings,
                new TimingFrame(0, 3, false, false),
                new Rotation(90.0f, 70.0f),
                new Rotation(0.0f, 0.0f),
                new Rotation(0.0f, 0.0f),
                false,
                RotationNoise.NONE).rotation();

        var reused = policy.rotation(
                settings,
                new TimingFrame(0, 3, false, false),
                new Rotation(-90.0f, 60.0f),
                new Rotation(90.0f, 70.0f),
                new Rotation(0.0f, 0.0f),
                true,
                RotationNoise.NONE);
        assertEquals(seeded.getYaw(), reused.rotation().getYaw(), 1.0E-6f);
        assertEquals("previous-strict-hit", reused.source());

        var firstGap = policy.holdForMissingTarget(2);
        var secondGap = policy.holdForMissingTarget(2);
        var expired = policy.holdForMissingTarget(2);
        assertEquals(seeded.getYaw(), firstGap.rotation().getYaw(), 1.0E-6f);
        assertEquals("gap-hold-1/2", firstGap.source());
        assertEquals("gap-hold-2/2", secondGap.source());
        assertNull(expired.rotation());
        assertEquals("gap-hold-expired", expired.source());
    }

    @Test
    void gapHoldUsesTheLastIssuedPlayerStageWithoutChangingStrictReuseState() {
        ScaffoldNewTellyPolicy policy = new ScaffoldNewTellyPolicy();
        Settings settings = settings(false, false, JumpMode.NORMAL, false, false);

        var groundPlayer = policy.rotation(
                settings,
                new TimingFrame(2, 0, false, false),
                new Rotation(90.0f, 70.0f),
                new Rotation(0.0f, 0.0f),
                new Rotation(25.0f, 10.0f),
                false,
                RotationNoise.NONE);

        assertEquals("ground-player", groundPlayer.source());
        assertEquals(25.0f, groundPlayer.rotation().getYaw(), 1.0E-6f);
        assertNull(policy.lastRotation());
        assertEquals(25.0f, policy.holdForMissingTarget(1).rotation().getYaw(), 1.0E-6f);
    }

    @Test
    void duplicateJitterAndPitchDifferenceGateAreAppliedBeforePlacement() {
        ScaffoldNewTellyPolicy policy = new ScaffoldNewTellyPolicy();
        Settings settings = Settings.SCREENSHOT_DEFAULTS;
        Rotation jittered = policy.rotation(
                settings,
                new TimingFrame(0, 3, false, false),
                new Rotation(90.0f, 70.0f),
                new Rotation(0.0f, 0.0f),
                new Rotation(0.0f, 0.0f),
                false,
                new RotationNoise(0.0f, 0.0f, 0.0002f, 0.002f, 0.0015f)).rotation();

        assertEquals(89.9998f, jittered.getYaw(), 1.0E-5f);
        assertEquals(69.9965f, jittered.getPitch(), 1.0E-5f);
        assertFalse(policy.blocksDuplicatePlacement(settings, 3.0));
        policy.onPlacementSuccess(3.0);
        assertTrue(policy.blocksDuplicatePlacement(settings, 3.00005));
        assertFalse(policy.blocksDuplicatePlacement(settings, 3.001));
    }

    @Test
    void blockSlotModesPreserveCurrentOrSelectSouthsideOrdering() {
        List<SlotCandidate> candidates = List.of(
                new SlotCandidate(1, 16, true),
                new SlotCandidate(4, 32, true),
                new SlotCandidate(8, 8, true));

        assertEquals(4, ScaffoldNewTellyPolicy.selectHotbarSlot(
                BlockSlotMode.FARTHEST, 4, 1, candidates));
        assertEquals(8, ScaffoldNewTellyPolicy.selectHotbarSlot(
                BlockSlotMode.FARTHEST, 2, 1, candidates));
        assertEquals(4, ScaffoldNewTellyPolicy.selectHotbarSlot(
                BlockSlotMode.MOST_BLOCKS, 4, 1, candidates));
        assertEquals(1, ScaffoldNewTellyPolicy.selectHotbarSlot(
                BlockSlotMode.MOST_BLOCKS,
                1,
                1,
                List.of(new SlotCandidate(1, 32, true), new SlotCandidate(4, 32, true))));
    }

    private static Settings settings(
            boolean safeMode,
            boolean testOnGround,
            JumpMode jumpMode,
            boolean noUpTelly,
            boolean duplicate) {
        return new Settings(
                true,
                1,
                3,
                noUpTelly,
                true,
                safeMode,
                testOnGround,
                false,
                false,
                duplicate,
                true,
                jumpMode,
                BlockSlotMode.FARTHEST);
    }
}
