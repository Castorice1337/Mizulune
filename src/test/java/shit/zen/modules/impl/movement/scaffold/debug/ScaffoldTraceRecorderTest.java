package shit.zen.modules.impl.movement.scaffold.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shit.zen.ClientBase;
import shit.zen.modules.impl.movement.Scaffold;
import shit.zen.modules.impl.movement.scaffold.debug.ScaffoldTraceRecorder.Angles;
import shit.zen.modules.impl.movement.scaffold.debug.ScaffoldTraceRecorder.BlockUpdateData;
import shit.zen.modules.impl.movement.scaffold.debug.ScaffoldTraceRecorder.CarriedSlotData;
import shit.zen.modules.impl.movement.scaffold.debug.ScaffoldTraceRecorder.MoveData;
import shit.zen.modules.impl.movement.scaffold.debug.ScaffoldTraceRecorder.Position;
import shit.zen.modules.impl.movement.scaffold.debug.ScaffoldTraceRecorder.SprintData;
import shit.zen.modules.impl.movement.scaffold.debug.ScaffoldTraceRecorder.SwingData;
import shit.zen.modules.impl.movement.scaffold.debug.ScaffoldTraceRecorder.TraceEntry;
import shit.zen.modules.impl.movement.scaffold.debug.ScaffoldTraceRecorder.UseItemOnData;

final class ScaffoldTraceRecorderTest {
    private Scaffold previousScaffold;
    private Minecraft previousMinecraft;

    @BeforeEach
    void setUp() {
        this.previousScaffold = Scaffold.INSTANCE;
        this.previousMinecraft = ClientBase.mc;
        Scaffold.INSTANCE = null;
        ClientBase.mc = null;
        ScaffoldTraceRecorder.resetForTests();
    }

    @AfterEach
    void tearDown() {
        ScaffoldTraceRecorder.resetForTests();
        Scaffold.INSTANCE = this.previousScaffold;
        ClientBase.mc = this.previousMinecraft;
    }

    @Test
    void remainsDisabledByDefault() {
        assertFalse(ScaffoldTraceRecorder.isEnabled());

        ScaffoldTraceRecorder.recordFinalWrite(new ServerboundMovePlayerPacket.StatusOnly(true));

        assertTrue(ScaffoldTraceRecorder.snapshot().isEmpty());
        assertEquals(0L, ScaffoldTraceRecorder.getLastSequence());
    }

    @Test
    void scaffoldDebugSwitchEnablesRecorder() {
        Scaffold scaffold = new Scaffold();
        scaffold.debug.setValue(true);

        assertTrue(ScaffoldTraceRecorder.isEnabled());
        ScaffoldTraceRecorder.recordFinalWrite(new ServerboundMovePlayerPacket.StatusOnly(false));

        TraceEntry entry = ScaffoldTraceRecorder.snapshot().get(0);
        MoveData move = assertInstanceOf(MoveData.class, entry.data());
        assertEquals("StatusOnly", move.subtype());
        assertNull(move.position());
        assertNull(move.rotation());
        assertFalse(move.onGround());
    }

    @Test
    void recordsFinalPacketDetailsAndPacketBoundContextAcrossThreads() throws Exception {
        ScaffoldTraceRecorder.setEnabled(true);
        ServerboundMovePlayerPacket.PosRot movePacket = new ServerboundMovePlayerPacket.PosRot(
                1.25,
                64.5,
                -3.75,
                91.0f,
                -22.5f,
                true);

        ScaffoldTraceRecorder.attachContext(77L, "support=(1,64,-4)", "Scaffold");
        ScaffoldTraceRecorder.captureCurrentContext(movePacket);
        ScaffoldTraceRecorder.attachContext(78L, "support=(2,64,-4)", "OtherOwner");

        Thread finalWriteThread = new Thread(
                () -> ScaffoldTraceRecorder.recordFinalWrite(movePacket),
                "trace-final-write-test");
        finalWriteThread.start();
        finalWriteThread.join();

        Vec3 hitLocation = new Vec3(1.75, 64.875, -3.25);
        BlockPos support = new BlockPos(1, 64, -4);
        ScaffoldTraceRecorder.recordFinalWrite(new ServerboundUseItemOnPacket(
                InteractionHand.MAIN_HAND,
                new BlockHitResult(hitLocation, Direction.UP, support, false),
                19));
        ScaffoldTraceRecorder.recordFinalWrite(new ServerboundSetCarriedItemPacket(5));
        ScaffoldTraceRecorder.recordFinalWrite(new ServerboundSwingPacket(InteractionHand.OFF_HAND));
        ScaffoldTraceRecorder.recordFinalWrite(sprintPacket(
                ServerboundPlayerCommandPacket.Action.START_SPRINTING));
        ScaffoldTraceRecorder.recordFinalWrite(sprintPacket(
                ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));

        List<TraceEntry> entries = ScaffoldTraceRecorder.snapshot();
        assertEquals(6, entries.size());
        for (int index = 0; index < entries.size(); index++) {
            assertEquals(index + 1L, entries.get(index).sequence());
            assertEquals(-1, entries.get(index).tick());
        }

        TraceEntry moveEntry = entries.get(0);
        assertEquals("ServerboundMovePlayerPacket.PosRot", moveEntry.packetType());
        assertEquals(77L, moveEntry.context().frameId());
        assertEquals("support=(1,64,-4)", moveEntry.context().target());
        assertEquals("Scaffold", moveEntry.context().owner());
        MoveData move = assertInstanceOf(MoveData.class, moveEntry.data());
        assertEquals("PosRot", move.subtype());
        assertEquals(new Position(1.25, 64.5, -3.75), move.position());
        assertEquals(new Angles(91.0f, -22.5f), move.rotation());
        assertTrue(move.onGround());

        TraceEntry useEntry = entries.get(1);
        assertEquals(78L, useEntry.context().frameId());
        UseItemOnData use = assertInstanceOf(UseItemOnData.class, useEntry.data());
        assertEquals(InteractionHand.MAIN_HAND, use.hand());
        assertEquals(support, use.block());
        assertEquals(Direction.UP, use.face());
        assertEquals(hitLocation.x, use.hit().x, 0.0);
        assertEquals(hitLocation.y, use.hit().y, 0.0);
        assertEquals(hitLocation.z, use.hit().z, 0.0);
        assertEquals(19, use.sequence());

        assertEquals(5, assertInstanceOf(CarriedSlotData.class, entries.get(2).data()).slot());
        assertEquals(
                InteractionHand.OFF_HAND,
                assertInstanceOf(SwingData.class, entries.get(3).data()).hand());

        SprintData sprintStart = assertInstanceOf(SprintData.class, entries.get(4).data());
        SprintData sprintStop = assertInstanceOf(SprintData.class, entries.get(5).data());
        assertTrue(sprintStart.sprinting());
        assertFalse(sprintStop.sprinting());
        assertEquals(6L, ScaffoldTraceRecorder.getLastSequence());
        assertTrue(moveEntry.toLogLine().contains("seq=1 tick=-1"));
        assertTrue(moveEntry.toLogLine().contains("frame=77 owner=Scaffold"));
        assertTrue(moveEntry.toLogLine().contains("pos=(1.25,64.5,-3.75)"));
        assertTrue(useEntry.toLogLine().contains("sequence=19"));
    }

    @Test
    void delayedPacketKeepsItsOriginalFrameContext() {
        ScaffoldTraceRecorder.setEnabled(true);
        ServerboundMovePlayerPacket packet =
                new ServerboundMovePlayerPacket.StatusOnly(true);

        ScaffoldTraceRecorder.attachPacketContext(packet, 11L, "first", "Scaffold");
        ScaffoldTraceRecorder.attachPacketContext(packet, 12L, "flush", "Scaffold");
        ScaffoldTraceRecorder.recordFinalWrite(packet);

        TraceEntry entry = ScaffoldTraceRecorder.snapshot().get(0);
        assertEquals(11L, entry.context().frameId());
        assertEquals("first", entry.context().target());
    }

    @Test
    void normalTransactionAndFollowingVanillaFlyingKeepThePlacementFrame() {
        ScaffoldTraceRecorder.setEnabled(true);
        BlockHitResult hit = new BlockHitResult(
                new Vec3(0.5, 64.0, 0.5),
                Direction.UP,
                new BlockPos(0, 63, 0),
                false);
        ServerboundUseItemOnPacket use = new ServerboundUseItemOnPacket(
                InteractionHand.MAIN_HAND,
                hit,
                4);
        ServerboundMovePlayerPacket.PosRot target =
                new ServerboundMovePlayerPacket.PosRot(
                        0.5,
                        64.0,
                        0.5,
                        90.0f,
                        75.0f,
                        true);
        ServerboundMovePlayerPacket.PosRot restore =
                new ServerboundMovePlayerPacket.PosRot(
                        0.5,
                        64.0,
                        0.5,
                        15.0f,
                        5.0f,
                        true);
        ServerboundMovePlayerPacket.PosRot vanillaFlying =
                new ServerboundMovePlayerPacket.PosRot(
                        0.55,
                        64.0,
                        0.5,
                        90.0f,
                        75.0f,
                        true);

        ScaffoldTraceRecorder.attachPacketContext(target, 41L, "placement", "Scaffold");
        ScaffoldTraceRecorder.attachPacketContext(use, 41L, "placement", "Scaffold");
        ScaffoldTraceRecorder.attachPacketContext(restore, 41L, "placement", "Scaffold");
        ScaffoldTraceRecorder.attachPacketContext(
                vanillaFlying,
                41L,
                "placement",
                "Scaffold");
        ScaffoldTraceRecorder.recordFinalWrite(target);
        ScaffoldTraceRecorder.recordFinalWrite(use);
        ScaffoldTraceRecorder.recordFinalWrite(restore);
        ScaffoldTraceRecorder.recordFinalWrite(vanillaFlying);

        List<TraceEntry> entries = ScaffoldTraceRecorder.snapshot();
        assertEquals(4, entries.size());
        assertEquals(41L, entries.get(0).context().frameId());
        assertEquals(41L, entries.get(1).context().frameId());
        assertEquals(41L, entries.get(2).context().frameId());
        assertEquals(41L, entries.get(3).context().frameId());
        assertEquals("PosRot", assertInstanceOf(MoveData.class, entries.get(0).data()).subtype());
        assertEquals("PosRot", assertInstanceOf(MoveData.class, entries.get(2).data()).subtype());
        assertEquals("PosRot", assertInstanceOf(MoveData.class, entries.get(3).data()).subtype());
    }

    @Test
    void matchingServerBlockUpdatesDistinguishConfirmationFromAirRollback() {
        ScaffoldTraceRecorder.setEnabled(true);
        BlockPos firstSupport = new BlockPos(1, 63, 2);
        BlockPos secondSupport = new BlockPos(2, 63, 2);
        ServerboundUseItemOnPacket firstUse = new ServerboundUseItemOnPacket(
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        Vec3.atCenterOf(firstSupport),
                        Direction.UP,
                        firstSupport,
                        false),
                21);
        ServerboundUseItemOnPacket secondUse = new ServerboundUseItemOnPacket(
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        Vec3.atCenterOf(secondSupport),
                        Direction.UP,
                        secondSupport,
                        false),
                22);
        ScaffoldTraceRecorder.attachPacketContext(firstUse, 91L, "first", "Scaffold");
        ScaffoldTraceRecorder.attachPacketContext(secondUse, 92L, "second", "Scaffold");
        ScaffoldTraceRecorder.recordFinalWrite(firstUse);
        ScaffoldTraceRecorder.recordFinalWrite(secondUse);

        ScaffoldTraceRecorder.recordBlockUpdate(
                "ClientboundBlockUpdatePacket",
                firstSupport.above(),
                "minecraft:oak_planks",
                false);
        ScaffoldTraceRecorder.recordBlockUpdate(
                "ClientboundBlockUpdatePacket",
                secondSupport.above(),
                "minecraft:air",
                true);

        List<TraceEntry> entries = ScaffoldTraceRecorder.snapshot();
        assertEquals(4, entries.size());
        BlockUpdateData confirmed = assertInstanceOf(BlockUpdateData.class, entries.get(2).data());
        BlockUpdateData rejected = assertInstanceOf(BlockUpdateData.class, entries.get(3).data());
        assertEquals("SERVER_NON_AIR", confirmed.verdict());
        assertEquals(1L, confirmed.outboundTraceSequence());
        assertEquals(21, confirmed.useSequence());
        assertEquals("SERVER_AIR", rejected.verdict());
        assertEquals(2L, rejected.outboundTraceSequence());
        assertEquals(22, rejected.useSequence());
        assertTrue(entries.get(3).toLogLine().contains("verdict=SERVER_AIR"));
    }

    @Test
    void repeatedCellAttemptsKeepSequenceOrderAndSessionClearDropsStaleLinks() {
        ScaffoldTraceRecorder.setEnabled(true);
        BlockPos support = new BlockPos(4, 63, 4);
        for (int sequence : new int[]{31, 32}) {
            ServerboundUseItemOnPacket use = new ServerboundUseItemOnPacket(
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(
                            Vec3.atCenterOf(support),
                            Direction.UP,
                            support,
                            false),
                    sequence);
            ScaffoldTraceRecorder.attachPacketContext(
                    use,
                    sequence,
                    "same-cell",
                    "Scaffold");
            ScaffoldTraceRecorder.recordFinalWrite(use);
        }

        ScaffoldTraceRecorder.recordBlockUpdate(
                "ClientboundBlockUpdatePacket",
                support.above(),
                "minecraft:air",
                true);
        BlockUpdateData first = assertInstanceOf(
                BlockUpdateData.class,
                ScaffoldTraceRecorder.snapshot().get(2).data());
        assertEquals(31, first.useSequence());

        ScaffoldTraceRecorder.clearPendingPlacements();
        ScaffoldTraceRecorder.recordBlockUpdate(
                "ClientboundBlockUpdatePacket",
                support.above(),
                "minecraft:oak_planks",
                false);
        assertEquals(3, ScaffoldTraceRecorder.snapshot().size());
    }

    @Test
    void repeatedCellAttemptsAreBoundedWhenTheServerSendsNoUpdates() {
        ScaffoldTraceRecorder.setEnabled(true);
        BlockPos support = new BlockPos(8, 63, 8);
        for (int sequence = 40; sequence < 50; sequence++) {
            ServerboundUseItemOnPacket use = new ServerboundUseItemOnPacket(
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(
                            Vec3.atCenterOf(support),
                            Direction.UP,
                            support,
                            false),
                    sequence);
            ScaffoldTraceRecorder.attachPacketContext(
                    use,
                    sequence,
                    "bounded-cell",
                    "Scaffold");
            ScaffoldTraceRecorder.recordFinalWrite(use);
        }

        ScaffoldTraceRecorder.recordBlockUpdate(
                "ClientboundBlockUpdatePacket",
                support.above(),
                "minecraft:air",
                true);
        BlockUpdateData update = assertInstanceOf(
                BlockUpdateData.class,
                ScaffoldTraceRecorder.snapshot().get(10).data());
        assertEquals(42, update.useSequence());
    }

    private static ServerboundPlayerCommandPacket sprintPacket(
            ServerboundPlayerCommandPacket.Action action) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(1);
            buffer.writeEnum(action);
            buffer.writeVarInt(0);
            buffer.readerIndex(0);
            return new ServerboundPlayerCommandPacket(buffer);
        } finally {
            buffer.release();
        }
    }
}
