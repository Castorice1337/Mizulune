package shit.zen.utils.rotation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import shit.zen.patch.EntityPatch;
import shit.zen.utils.game.DirectionalInput;

final class MovementCorrectionUtilTest {
    @Test
    void keepsWorldForwardDirectionAcrossQuarterTurns() {
        DirectionalInput forward = new DirectionalInput(true, false, false, false);

        assertEquals(
                new DirectionalInput(false, false, true, false),
                MovementCorrectionUtil.correctSilentInput(forward, 0.0f, 90.0f));
        assertEquals(
                new DirectionalInput(false, false, false, true),
                MovementCorrectionUtil.correctSilentInput(forward, 0.0f, -90.0f));
        assertEquals(
                new DirectionalInput(false, true, false, false),
                MovementCorrectionUtil.correctSilentInput(forward, 0.0f, 180.0f));
    }

    @Test
    void fortyFiveDegreeDifferenceMapsToDiagonalInput() {
        DirectionalInput corrected = MovementCorrectionUtil.correctSilentInput(
                new DirectionalInput(true, false, false, false),
                0.0f,
                45.0f);

        assertEquals(new DirectionalInput(true, false, true, false), corrected);
    }

    @Test
    void wrappedYawBoundaryDoesNotFlipInput() {
        DirectionalInput corrected = MovementCorrectionUtil.correctSilentInput(
                new DirectionalInput(true, false, false, false),
                179.0f,
                -179.0f);

        assertEquals(new DirectionalInput(true, false, false, false), corrected);
    }

    @Test
    void silentInputAndVelocityYawPreserveCameraWorldDirection() {
        DirectionalInput forward = new DirectionalInput(true, false, false, false);
        for (float serverYaw : new float[]{0.0f, 45.0f, 90.0f, 135.0f, 180.0f, -90.0f}) {
            DirectionalInput corrected = MovementCorrectionUtil.correctSilentInput(
                    forward,
                    0.0f,
                    serverYaw);
            Vec3 worldMovement = EntityPatch.applyRotation(
                    new Vec3(
                            corrected.strafeImpulse(),
                            0.0,
                            corrected.forwardImpulse()),
                    1.0f,
                    serverYaw);

            assertEquals(0.0, worldMovement.x, 1.0E-6);
            assertEquals(1.0, worldMovement.z, 1.0E-6);
        }
    }

    @Test
    void providerCompatibilityMappingPreservesLegacyBehavior() {
        RotationProvider silentProvider = new TestProvider(RotationApplyMode.SILENT, true);
        RotationProvider strictOffProvider = new TestProvider(RotationApplyMode.SILENT, false);
        RotationProvider changeLookProvider = new TestProvider(RotationApplyMode.CHANGE_LOOK, false);

        assertEquals(MovementCorrection.SILENT, silentProvider.getMovementCorrection());
        assertEquals(MovementCorrection.OFF, strictOffProvider.getMovementCorrection());
        assertEquals(MovementCorrection.CHANGE_LOOK, changeLookProvider.getMovementCorrection());
        assertEquals(
                RotationApplyMode.CHANGE_LOOK,
                RotationHandler.resolveApplyMode(
                        RotationApplyMode.SILENT,
                        MovementCorrection.CHANGE_LOOK));
    }

    @Test
    void higherPriorityOwnerWinsAndFallsBackWhenInactive() {
        TestProvider scaffold = new TestProvider(
                RotationApplyMode.SILENT,
                true,
                true,
                50);
        TestProvider clutch = new TestProvider(
                RotationApplyMode.SILENT,
                true,
                true,
                60);

        assertEquals(clutch, RotationHandler.selectProvider(List.of(scaffold, clutch)));
        clutch.active = false;
        assertEquals(scaffold, RotationHandler.selectProvider(List.of(scaffold, clutch)));
    }

    private static final class TestProvider implements RotationProvider {
        private final RotationApplyMode applyMode;
        private final boolean fixMovement;
        private final int priority;
        private boolean active;

        private TestProvider(RotationApplyMode applyMode, boolean fixMovement) {
            this(applyMode, fixMovement, true, 0);
        }

        private TestProvider(
                RotationApplyMode applyMode,
                boolean fixMovement,
                boolean active,
                int priority) {
            this.applyMode = applyMode;
            this.fixMovement = fixMovement;
            this.active = active;
            this.priority = priority;
        }

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public Rotation getRotation() {
            return new Rotation(0.0f, 0.0f);
        }

        @Override
        public boolean isRotationActive() {
            return this.active;
        }

        @Override
        public RotationApplyMode getApplyMode() {
            return this.applyMode;
        }

        @Override
        public boolean shouldFixMovement() {
            return this.fixMovement;
        }

        @Override
        public int getRotationPriority() {
            return this.priority;
        }
    }
}
