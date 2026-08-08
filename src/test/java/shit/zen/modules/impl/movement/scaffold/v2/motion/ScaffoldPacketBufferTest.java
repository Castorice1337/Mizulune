package shit.zen.modules.impl.movement.scaffold.v2.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.status.ClientboundPongResponsePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class ScaffoldPacketBufferTest {
    private static final Blink.PacketContext GROUND =
            new Blink.PacketContext(true, false, false, false, true);
    private static final Blink.Settings ENABLED = new Blink.Settings(
            true,
            new Blink.TimeRange(100, 100),
            Set.of());

    @Test
    void queuesAndFlushesBufferablePacketsInFifoOrder() {
        List<Packet<?>> sent = new ArrayList<>();
        ScaffoldPacketBuffer buffer = primedBuffer(sent);
        Packet<?> first = new ServerboundMovePlayerPacket.StatusOnly(true);
        Packet<?> second = new ServerboundSetCarriedItemPacket(4);

        assertTrue(buffer.intercept(first, null, ENABLED, GROUND, 2L));
        assertTrue(buffer.intercept(second, null, ENABLED, GROUND, 3L));
        assertEquals(2, buffer.size());

        buffer.flush();

        assertEquals(List.of(first, second), sent);
        assertEquals(0, buffer.size());
        assertFalse(buffer.isFlushing());
    }

    @Test
    void onTickPositionRotationsKeepSamePositionAroundUsePacket() {
        List<Packet<?>> sent = new ArrayList<>();
        ScaffoldPacketBuffer buffer = primedBuffer(sent);
        Vec3 framePosition = new Vec3(1.25, 64.0, -3.5);
        ServerboundMovePlayerPacket.PosRot target = new ServerboundMovePlayerPacket.PosRot(
                framePosition.x,
                framePosition.y,
                framePosition.z,
                90.0f,
                75.0f,
                true);
        ServerboundUseItemOnPacket use = new ServerboundUseItemOnPacket(
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        new Vec3(1.5, 64.0, -3.5),
                        Direction.UP,
                        new BlockPos(1, 63, -4),
                        false),
                3);
        ServerboundMovePlayerPacket.PosRot restore = new ServerboundMovePlayerPacket.PosRot(
                framePosition.x,
                framePosition.y,
                framePosition.z,
                15.0f,
                5.0f,
                true);

        assertTrue(buffer.intercept(target, null, ENABLED, GROUND, 2L));
        assertTrue(buffer.intercept(use, null, ENABLED, GROUND, 3L));
        assertTrue(buffer.intercept(restore, null, ENABLED, GROUND, 4L));
        buffer.flush();

        assertEquals(List.of(target, use, restore), sent);
        assertEquals(target.getX(0.0), restore.getX(0.0));
        assertEquals(target.getY(0.0), restore.getY(0.0));
        assertEquals(target.getZ(0.0), restore.getZ(0.0));
    }

    @Test
    void newTellyPreUseStaysBetweenTargetRotationAndUseItemOnInBlinkFifo() {
        List<Packet<?>> sent = new ArrayList<>();
        ScaffoldPacketBuffer buffer = primedBuffer(sent);
        ServerboundMovePlayerPacket.PosRot target = new ServerboundMovePlayerPacket.PosRot(
                1.25, 64.0, -3.5, 90.0f, 75.0f, true);
        ServerboundUseItemPacket preUse = new ServerboundUseItemPacket(
                InteractionHand.MAIN_HAND, 2);
        ServerboundUseItemOnPacket use = new ServerboundUseItemOnPacket(
                InteractionHand.OFF_HAND,
                new BlockHitResult(
                        new Vec3(1.5, 64.0, -3.5),
                        Direction.UP,
                        new BlockPos(1, 63, -4),
                        false),
                3);

        assertTrue(buffer.intercept(target, null, ENABLED, GROUND, 2L));
        assertTrue(buffer.intercept(preUse, null, ENABLED, GROUND, 3L));
        assertTrue(buffer.intercept(use, null, ENABLED, GROUND, 4L));
        buffer.flush();

        assertEquals(List.of(target, preUse, use), sent);
    }

    @Test
    void disabledPolicyFlushesQueuedPacketsAndLeavesCurrentPacketUnbuffered() {
        List<Packet<?>> sent = new ArrayList<>();
        ScaffoldPacketBuffer buffer = primedBuffer(sent);
        Packet<?> queued = new ServerboundMovePlayerPacket.StatusOnly(true);
        Packet<?> current = new ServerboundMovePlayerPacket.StatusOnly(false);
        buffer.intercept(queued, null, ENABLED, GROUND, 2L);

        assertFalse(buffer.intercept(current, null, Blink.DEFAULTS, GROUND, 3L));
        assertEquals(List.of(queued), sent);
        assertEquals(0, buffer.size());
    }

    @Test
    void discardDropsWorldBoundPacketsWithoutSending() {
        List<Packet<?>> sent = new ArrayList<>();
        ScaffoldPacketBuffer buffer = primedBuffer(sent);
        buffer.intercept(
                new ServerboundMovePlayerPacket.StatusOnly(true),
                null,
                ENABLED,
                GROUND,
                2L);

        buffer.discard();

        assertTrue(sent.isEmpty());
        assertEquals(0, buffer.size());
    }

    @Test
    void onlyScaffoldMovementAndInteractionPacketsAreBufferable() {
        assertTrue(ScaffoldPacketBuffer.isBufferable(
                new ServerboundMovePlayerPacket.StatusOnly(true)));
        assertTrue(ScaffoldPacketBuffer.isBufferable(
                new ServerboundSetCarriedItemPacket(2)));
        assertTrue(ScaffoldPacketBuffer.isBufferable(
                new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 1)));
        assertFalse(ScaffoldPacketBuffer.isBufferable(
                new ClientboundPongResponsePacket(5L)));
    }

    @Test
    void flushPreservesOriginalPacketSendListener() {
        List<PacketSendListener> listeners = new ArrayList<>();
        ScaffoldPacketBuffer buffer = new ScaffoldPacketBuffer(
                (packet, listener) -> listeners.add(listener));
        assertFalse(buffer.intercept(
                new ServerboundMovePlayerPacket.StatusOnly(true),
                null,
                ENABLED,
                GROUND,
                1L));
        buffer.onBlockPlacement(ENABLED);

        PacketSendListener listener = (PacketSendListener) Proxy.newProxyInstance(
                PacketSendListener.class.getClassLoader(),
                new Class<?>[]{PacketSendListener.class},
                (proxy, method, args) -> null);
        assertTrue(buffer.intercept(
                new ServerboundMovePlayerPacket.StatusOnly(true),
                listener,
                ENABLED,
                GROUND,
                2L));

        buffer.flush();

        assertEquals(1, listeners.size());
        assertSame(listener, listeners.get(0));
    }

    private static ScaffoldPacketBuffer primedBuffer(List<Packet<?>> sent) {
        ScaffoldPacketBuffer buffer = new ScaffoldPacketBuffer((packet, listener) -> sent.add(packet));
        assertFalse(buffer.intercept(
                new ServerboundMovePlayerPacket.StatusOnly(true),
                null,
                ENABLED,
                GROUND,
                1L));
        buffer.onBlockPlacement(ENABLED);
        return buffer;
    }
}
