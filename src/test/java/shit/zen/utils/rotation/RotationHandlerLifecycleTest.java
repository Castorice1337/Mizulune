package shit.zen.utils.rotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.WorldChangeEvent;

final class RotationHandlerLifecycleTest {
    @AfterEach
    void restoreIdleState() throws Exception {
        setStatic("rotationPhase", RotationHandler.RotationPhase.IDLE);
        setStatic("activeProvider", null);
        setStatic("activeRotationOwner", null);
        setStatic("resetRotationOwner", null);
        setStatic("actualServerRotation", null);
        setStatic("theoreticalServerRotation", null);
        setStatic("resetAwaitingFinalPacket", false);
        setStatic("resetFinalPacketWritten", false);
        setStatic("resetFinalPacketWaitTicks", 0);
        setStatic("resetFinalPacketForced", false);
        setStatic("resetFinalizationPacket", null);
        RotationHandler.targetRotation = null;
        RotationHandler.isRotating = false;
    }

    @Test
    void idleCurrentRotationDoesNotFallBackToHistoricalRotation() throws Exception {
        setStatic("rotationPhase", RotationHandler.RotationPhase.IDLE);
        setStatic("actualServerRotation", new Rotation(90.0f, 45.0f));
        RotationHandler.targetRotation = new Rotation(30.0f, 60.0f);
        RotationHandler.isRotating = true;

        assertNull(RotationHandler.getCurrentRotation());
    }

    @Test
    void onlyActiveOwnerExposesPlacementRotation() throws Exception {
        Object owner = new Object();
        Object other = new Object();
        Rotation synthetic = new Rotation(135.0f, 70.0f);

        setStatic("rotationPhase", RotationHandler.RotationPhase.ACTIVE);
        setStatic("activeRotationOwner", owner);
        RotationHandler.targetRotation = synthetic;
        RotationHandler.isRotating = true;

        assertEquals(synthetic.getYaw(), RotationHandler.getActiveRotation(owner).getYaw());
        assertTrue(RotationHandler.isActiveRotationOwner(owner));
        assertNull(RotationHandler.getActiveRotation(other));
        assertFalse(RotationHandler.isActiveRotationOwner(other));

        setStatic("rotationPhase", RotationHandler.RotationPhase.RESET);
        setStatic("activeRotationOwner", null);
        setStatic("resetRotationOwner", owner);
        assertNull(RotationHandler.getActiveRotation(owner));
        assertFalse(RotationHandler.isActiveRotationOwner(owner));
    }

    @Test
    void externalOwnerCheckCoversActiveAndResetWithoutIdleFallback() throws Exception {
        Object owner = new Object();
        Object other = new Object();

        setStatic("rotationPhase", RotationHandler.RotationPhase.ACTIVE);
        setStatic("activeRotationOwner", owner);
        assertFalse(RotationHandler.hasExternalRotationOwner(owner));
        assertTrue(RotationHandler.hasExternalRotationOwner(other));

        setStatic("rotationPhase", RotationHandler.RotationPhase.RESET);
        setStatic("resetRotationOwner", owner);
        assertFalse(RotationHandler.hasExternalRotationOwner(owner));
        assertTrue(RotationHandler.hasExternalRotationOwner(other));

        setStatic("rotationPhase", RotationHandler.RotationPhase.IDLE);
        assertFalse(RotationHandler.hasExternalRotationOwner(other));
    }

    @Test
    void finalOutgoingRotationPreservesRawContinuousYaw() {
        RotationHandler.onFinalPacketWrite(
                new ServerboundMovePlayerPacket.Rot(725.0f, 30.0f, true));

        Rotation actual = RotationHandler.getActualServerRotation();
        assertEquals(725.0f, actual.getYaw());
        assertEquals(30.0f, actual.getPitch());
    }

    @Test
    void finalOutgoingPositionRotationPreservesRawContinuousYaw() {
        RotationHandler.onFinalPacketWrite(
                new ServerboundMovePlayerPacket.PosRot(
                        1.25,
                        64.0,
                        -2.5,
                        -725.0f,
                        -30.0f,
                        false));

        Rotation actual = RotationHandler.getActualServerRotation();
        assertEquals(-725.0f, actual.getYaw());
        assertEquals(-30.0f, actual.getPitch());
    }

    @Test
    void acceptedRotationPreservesRawContinuousYaw() {
        RotationHandler.onOutgoingPacketAccepted(
                new ServerboundMovePlayerPacket.PosRot(
                        1.0,
                        64.0,
                        1.0,
                        -1087.0312f,
                        5.0f,
                        true));

        assertEquals(-1087.0312f, RotationHandler.getLogicalServerRotation().getYaw());
    }

    @Test
    void ephemeralYawUsesEquivalentAngleNearestLogicalServerYaw() {
        float targetYaw = RotationHandler.nearestEquivalentYaw(-8.045776f, -1087.0312f);
        float restoreYaw = RotationHandler.nearestEquivalentYaw(-1087.0312f, targetYaw);

        assertEquals(-1088.0458f, targetYaw, 1.0E-4f);
        assertEquals(-1087.0312f, restoreYaw, 1.0E-4f);
        assertTrue(Math.abs(targetYaw - -1087.0312f) < 30.0f);
        assertTrue(Math.abs(restoreYaw - targetYaw) < 30.0f);
    }

    @Test
    void equivalentYawStaysOnNearestBranchAcrossMultipleTurns() {
        float[] references = {-1440.0f, -1080.0f, -720.0f, -360.0f,
                359.0f, 360.0f, 720.0f, 1080.0f, 1440.0f};
        float[] requested = {-179.9f, -7.0312f, 179.9f};

        for (float reference : references) {
            for (float yaw : requested) {
                float continuous = RotationHandler.nearestEquivalentYaw(yaw, reference);
                assertTrue(Math.abs(continuous - reference) <= 180.0f,
                        () -> "reference=" + reference + " requested=" + yaw);
                assertEquals(Mth.wrapDegrees(yaw), Mth.wrapDegrees(continuous), 1.0E-4f);
            }
        }
    }

    @Test
    void onlyOptedInProviderKeepsWireYawOnTheNearestServerBranch() throws Exception {
        TestProvider normalized = new TestProvider(true);
        RotationHandler.registerProvider(normalized);
        try {
            setStatic("actualServerRotation", new Rotation(497.0f, 44.0f));
            assertTrue(RotationHandler.activateSnapRotation(
                    normalized,
                    new Rotation(553.0f, 44.0f)));

            Rotation activePacket = RotationHandler.toServerPacketRotation(
                    new Rotation(553.0f, 44.0f));
            assertEquals(553.0f, activePacket.getYaw(), 1.0E-6f);
            assertEquals(44.0f, activePacket.getPitch(), 1.0E-6f);

            Rotation preActivationPacket = RotationHandler.toServerPacketRotation(
                    normalized,
                    new Rotation(553.0f, 44.0f));
            assertEquals(553.0f, preActivationPacket.getYaw(), 1.0E-6f);

            setStatic("actualServerRotation", new Rotation(552.98f, 44.0f));
            setStatic("activeProvider", null);
            setStatic("activeRotationOwner", null);
            setStatic("resetRotationOwner", normalized);
            setStatic("rotationPhase", RotationHandler.RotationPhase.RESET);
            normalized.setNormalizeYaw(false);
            Rotation resetPacket = RotationHandler.toServerPacketRotation(
                    new Rotation(215.52f, 20.0f));
            Rotation ownedResetPacket = RotationHandler.toServerPacketRotation(
                    normalized,
                    new Rotation(215.52f, 20.0f));
            assertEquals(575.52f, resetPacket.getYaw(), 1.0E-4f);
            assertEquals(575.52f, ownedResetPacket.getYaw(), 1.0E-4f);
        } finally {
            RotationHandler.unregisterProvider(normalized);
        }

        setStatic("rotationPhase", RotationHandler.RotationPhase.ACTIVE);
        setStatic("activeProvider", new TestProvider(false));
        Rotation unchanged = RotationHandler.toServerPacketRotation(
                new Rotation(215.52f, 44.0f));
        assertEquals(215.52f, unchanged.getYaw(), 1.0E-6f);
    }

    @Test
    void resetWaitsForTheNormalizedFinalPacketBeforeReturningIdle() throws Exception {
        Object owner = new Object();
        setStatic("rotationPhase", RotationHandler.RotationPhase.RESET);
        setStatic("resetRotationOwner", owner);
        setStatic("resetAwaitingFinalPacket", true);
        setStatic("resetFinalPacketWritten", false);
        RotationHandler.targetRotation = new Rotation(42.0f, 14.0f);
        RotationHandler.isRotating = true;

        RotationHandler.onFinalPacketWrite(
                new ServerboundMovePlayerPacket.Rot(100.0f, 14.0f, true));
        assertFalse(RotationHandler.completePendingResetAfterFinalWrite());
        assertEquals(RotationHandler.RotationPhase.RESET, RotationHandler.getRotationPhase());

        RotationHandler.onFinalPacketWrite(
                new ServerboundMovePlayerPacket.Rot(402.0f, 14.0f, true));
        assertTrue(RotationHandler.completePendingResetAfterFinalWrite());
        assertEquals(RotationHandler.RotationPhase.IDLE, RotationHandler.getRotationPhase());
        assertNull(RotationHandler.getCurrentRotation());
    }

    @Test
    void scaffoldBufferBypassMarkerIsConsumedExactlyOnce() throws Exception {
        Object packet = new Object();
        setStatic("resetFinalizationPacket", packet);

        assertFalse(RotationHandler.shouldBypassScaffoldPacketBuffer(new Object()));
        assertTrue(RotationHandler.shouldBypassScaffoldPacketBuffer(packet));
        assertFalse(RotationHandler.shouldBypassScaffoldPacketBuffer(packet));
    }

    @Test
    void acceptedBufferedRotationAdvancesLogicalStateBeforeFinalWrite() {
        RotationHandler.onOutgoingPacketAccepted(
                new ServerboundMovePlayerPacket.PosRot(
                        1.0,
                        64.0,
                        1.0,
                        90.0f,
                        70.0f,
                        true));

        assertNull(RotationHandler.getActualServerRotation());
        Rotation logical = RotationHandler.getLogicalServerRotation();
        assertEquals(90.0f, logical.getYaw());
        assertEquals(70.0f, logical.getPitch());
    }

    @Test
    void olderBlinkFinalWriteDoesNotRollBackLogicalQueueTail() {
        ServerboundMovePlayerPacket.PosRot target = new ServerboundMovePlayerPacket.PosRot(
                1.0,
                64.0,
                1.0,
                90.0f,
                70.0f,
                true);
        ServerboundMovePlayerPacket.PosRot restore = new ServerboundMovePlayerPacket.PosRot(
                1.0,
                64.0,
                1.0,
                15.0f,
                5.0f,
                true);

        RotationHandler.onOutgoingPacketAccepted(target);
        RotationHandler.onOutgoingPacketAccepted(restore);
        RotationHandler.onFinalPacketWrite(target);

        assertEquals(90.0f, RotationHandler.getActualServerRotation().getYaw());
        assertEquals(15.0f, RotationHandler.getLogicalServerRotation().getYaw());
    }

    @Test
    void outgoingMoveYawIsNotInflatedBeforeSending() {
        ServerboundMovePlayerPacket.Rot packet =
                new ServerboundMovePlayerPacket.Rot(90.0f, 30.0f, true);

        new RotationHandler().onPacket(new PacketEvent(packet, true));

        assertEquals(90.0f, packet.getYRot(0.0f));
        assertEquals(30.0f, packet.getXRot(0.0f));
    }

    @Test
    void preWritePacketEventDoesNotUpdateActualServerRotation() throws Exception {
        setStatic("actualServerRotation", new Rotation(45.0f, 10.0f));
        PacketEvent outgoing = new PacketEvent(
                new ServerboundMovePlayerPacket.Rot(90.0f, 30.0f, true),
                true);
        new RotationHandler().onPacket(outgoing);

        Rotation actual = RotationHandler.getActualServerRotation();
        assertEquals(45.0f, actual.getYaw());
        assertEquals(10.0f, actual.getPitch());
    }

    @Test
    void unregisterAndWorldChangeClearOwnedLifecycle() throws Exception {
        RotationProvider provider = new TestProvider();
        RotationHandler.registerProvider(provider);
        setStatic("activeProvider", provider);
        setStatic("activeRotationOwner", provider);
        setStatic("rotationPhase", RotationHandler.RotationPhase.ACTIVE);
        RotationHandler.targetRotation = new Rotation(90.0f, 60.0f);
        RotationHandler.isRotating = true;

        RotationHandler.unregisterProvider(provider);

        assertEquals(RotationHandler.RotationPhase.IDLE, RotationHandler.getRotationPhase());
        assertNull(RotationHandler.getCurrentRotation());

        setStatic("actualServerRotation", new Rotation(90.0f, 60.0f));
        new RotationHandler().onWorldChange(new WorldChangeEvent());
        assertNull(RotationHandler.getActualServerRotation());
        assertNull(RotationHandler.getLogicalServerRotation());
        assertEquals(RotationHandler.RotationPhase.IDLE, RotationHandler.getRotationPhase());
    }

    @Test
    void releaseKeepsProviderRegisteredWhileResetOwnsRotation() throws Exception {
        RotationProvider provider = new TestProvider();
        RotationHandler.registerProvider(provider);
        setStatic("activeProvider", null);
        setStatic("activeRotationOwner", null);
        setStatic("resetRotationOwner", provider);
        setStatic("rotationPhase", RotationHandler.RotationPhase.RESET);
        RotationHandler.targetRotation = new Rotation(90.0f, 60.0f);
        RotationHandler.isRotating = true;

        RotationHandler.releaseProvider(provider);

        assertEquals(RotationHandler.RotationPhase.RESET, RotationHandler.getRotationPhase());
        assertEquals(90.0f, RotationHandler.getCurrentRotation().getYaw());
        Field providersField = RotationHandler.class.getDeclaredField("ROTATION_PROVIDERS");
        providersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<RotationProvider> providers = (List<RotationProvider>) providersField.get(null);
        assertTrue(providers.contains(provider));
        RotationHandler.unregisterProvider(provider);
    }

    private static void setStatic(String name, Object value) throws Exception {
        Field field = RotationHandler.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static final class TestProvider implements RotationProvider {
        private boolean normalizeYaw;

        private TestProvider() {
            this(false);
        }

        private TestProvider(boolean normalizeYaw) {
            this.normalizeYaw = normalizeYaw;
        }

        private void setNormalizeYaw(boolean normalizeYaw) {
            this.normalizeYaw = normalizeYaw;
        }

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public Rotation getRotation() {
            return null;
        }

        @Override
        public boolean isRotationActive() {
            return false;
        }

        @Override
        public boolean shouldNormalizeYawForServerPackets() {
            return this.normalizeYaw;
        }
    }
}
