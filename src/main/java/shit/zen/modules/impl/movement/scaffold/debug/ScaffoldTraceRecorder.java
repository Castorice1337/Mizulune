package shit.zen.modules.impl.movement.scaffold.debug;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import shit.zen.ClientBase;
import shit.zen.modules.impl.movement.Scaffold;

public final class ScaffoldTraceRecorder {
    public static final long NO_FRAME_ID = Long.MIN_VALUE;

    private static final int MAX_RECORDS = 4096;
    private static final AtomicBoolean EXPLICIT_ENABLED = new AtomicBoolean();
    private static final AtomicLong GLOBAL_SEQUENCE = new AtomicLong();
    private static final AtomicReference<TraceContext> CURRENT_CONTEXT =
            new AtomicReference<>(TraceContext.EMPTY);
    private static final Map<Packet<?>, TraceContext> PENDING_CONTEXTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final int MAX_PENDING_PLACEMENTS = 512;
    private static final int MAX_PENDING_AGE_TICKS = 40;
    private static final int MAX_PENDING_ATTEMPTS_PER_CELL = 8;
    private static final Map<BlockPos, Deque<PendingPlacement>> PENDING_PLACEMENTS =
            Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Object RECORDS_LOCK = new Object();
    private static final Deque<TraceEntry> RECORDS = new ArrayDeque<>();

    private ScaffoldTraceRecorder() {
    }

    public static void setEnabled(boolean enabled) {
        EXPLICIT_ENABLED.set(enabled);
    }

    public static boolean isEnabled() {
        if (EXPLICIT_ENABLED.get()) {
            return true;
        }
        try {
            Scaffold scaffold = Scaffold.INSTANCE;
            return scaffold != null && Boolean.TRUE.equals(scaffold.debug.getValue());
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void attachContext(long frameId, Object target, Object owner) {
        attachContext(frameId, describeTarget(target), describeOwner(owner));
    }

    public static void attachContext(long frameId, String target, String owner) {
        CURRENT_CONTEXT.set(new TraceContext(frameId, sanitize(target), sanitize(owner)));
    }

    public static void attachPacketContext(Packet<?> packet, long frameId, Object target, Object owner) {
        attachPacketContext(packet, frameId, describeTarget(target), describeOwner(owner));
    }

    public static void attachPacketContext(Packet<?> packet, long frameId, String target, String owner) {
        if (packet == null) {
            return;
        }
        try {
            synchronized (PENDING_CONTEXTS) {
                PENDING_CONTEXTS.putIfAbsent(
                        packet,
                        new TraceContext(frameId, sanitize(target), sanitize(owner)));
            }
        } catch (Throwable ignored) {
        }
    }

    public static void clearContext() {
        CURRENT_CONTEXT.set(TraceContext.EMPTY);
    }

    public static void clearContext(long frameId) {
        while (true) {
            TraceContext current = CURRENT_CONTEXT.get();
            if (current.frameId() != frameId || CURRENT_CONTEXT.compareAndSet(current, TraceContext.EMPTY)) {
                return;
            }
        }
    }

    public static void captureCurrentContext(Packet<?> packet) {
        if (packet == null) {
            return;
        }
        try {
            TraceContext context = CURRENT_CONTEXT.get();
            if (context.isEmpty()) {
                return;
            }
            synchronized (PENDING_CONTEXTS) {
                PENDING_CONTEXTS.putIfAbsent(packet, context);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void recordFinalWrite(Packet<?> packet) {
        TraceContext context = takeContext(packet);
        if (packet == null || !isEnabled()) {
            return;
        }

        try {
            PacketData data = decode(packet);
            TraceEntry entry;
            synchronized (RECORDS_LOCK) {
                entry = new TraceEntry(
                        GLOBAL_SEQUENCE.incrementAndGet(),
                        currentTick(),
                        packetType(packet),
                        context,
                        data);
                while (RECORDS.size() >= MAX_RECORDS) {
                    RECORDS.removeFirst();
                }
                RECORDS.addLast(entry);
            }
            trackPendingPlacement(packet, context, entry);
            if (ClientBase.logger != null) {
                ClientBase.logger.info(entry.toLogLine());
            }
        } catch (Throwable ignored) {
        }
    }

    public static void recordIncoming(Packet<?> packet) {
        if (packet == null || !isEnabled()) {
            return;
        }
        try {
            if (packet instanceof ClientboundBlockUpdatePacket blockUpdate) {
                recordBlockUpdate(
                        packetType(packet),
                        blockUpdate.getPos(),
                        blockUpdate.getBlockState());
            } else if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionUpdate) {
                sectionUpdate.runUpdates((position, state) -> recordBlockUpdate(
                        packetType(packet),
                        position,
                        state));
            }
        } catch (Throwable ignored) {
        }
    }

    public static List<TraceEntry> snapshot() {
        synchronized (RECORDS_LOCK) {
            return List.copyOf(RECORDS);
        }
    }

    public static List<TraceEntry> drain() {
        synchronized (RECORDS_LOCK) {
            List<TraceEntry> drained = new ArrayList<>(RECORDS);
            RECORDS.clear();
            return List.copyOf(drained);
        }
    }

    public static void clearRecords() {
        synchronized (RECORDS_LOCK) {
            RECORDS.clear();
        }
        PENDING_PLACEMENTS.clear();
    }

    public static void clearPendingPlacements() {
        PENDING_PLACEMENTS.clear();
    }

    public static long getLastSequence() {
        return GLOBAL_SEQUENCE.get();
    }

    static void resetForTests() {
        EXPLICIT_ENABLED.set(false);
        GLOBAL_SEQUENCE.set(0L);
        CURRENT_CONTEXT.set(TraceContext.EMPTY);
        PENDING_CONTEXTS.clear();
        clearRecords();
    }

    private static void trackPendingPlacement(
            Packet<?> packet,
            TraceContext context,
            TraceEntry entry) {
        if (!(packet instanceof ServerboundUseItemOnPacket useItemOn)
                || context == null
                || !"Scaffold".equals(context.owner())) {
            return;
        }
        BlockHitResult hit = useItemOn.getHitResult();
        if (hit == null) {
            return;
        }
        BlockPos placedBlock = hit.getBlockPos().relative(hit.getDirection()).immutable();
        synchronized (PENDING_PLACEMENTS) {
            while (PENDING_PLACEMENTS.size() >= MAX_PENDING_PLACEMENTS) {
                BlockPos oldest = PENDING_PLACEMENTS.keySet().iterator().next();
                PENDING_PLACEMENTS.remove(oldest);
            }
            Deque<PendingPlacement> attempts = PENDING_PLACEMENTS
                    .computeIfAbsent(placedBlock, ignored -> new ArrayDeque<>());
            while (!attempts.isEmpty() && isExpired(attempts.peekFirst(), entry.tick())) {
                attempts.removeFirst();
            }
            while (attempts.size() >= MAX_PENDING_ATTEMPTS_PER_CELL) {
                attempts.removeFirst();
            }
            attempts.addLast(new PendingPlacement(
                            entry.sequence(),
                            entry.tick(),
                            useItemOn.getSequence(),
                            context));
        }
    }

    private static void recordBlockUpdate(
            String packetType,
            BlockPos block,
            BlockState state) {
        if (block == null || state == null) {
            return;
        }
        recordBlockUpdate(packetType, block, String.valueOf(state), state.isAir());
    }

    static void recordBlockUpdate(
            String packetType,
            BlockPos block,
            String state,
            boolean air) {
        if (block == null || state == null) {
            return;
        }
        int tick = currentTick();
        PendingPlacement pending;
        synchronized (PENDING_PLACEMENTS) {
            Deque<PendingPlacement> attempts = PENDING_PLACEMENTS.get(block);
            while (attempts != null && !attempts.isEmpty()
                    && isExpired(attempts.peekFirst(), tick)) {
                attempts.removeFirst();
            }
            pending = attempts == null ? null : attempts.pollFirst();
            if (attempts != null && attempts.isEmpty()) {
                PENDING_PLACEMENTS.remove(block);
            }
        }
        if (pending == null) {
            return;
        }
        int ageTicks = tick < 0 || pending.sentTick() < 0
                ? -1
                : Math.max(0, tick - pending.sentTick());
        BlockUpdateData data = new BlockUpdateData(
                block.immutable(),
                state,
                air ? "SERVER_AIR" : "SERVER_NON_AIR",
                pending.outboundTraceSequence(),
                pending.useSequence(),
                ageTicks);
        TraceEntry entry;
        synchronized (RECORDS_LOCK) {
            entry = new TraceEntry(
                    GLOBAL_SEQUENCE.incrementAndGet(),
                    tick,
                    packetType,
                    pending.context(),
                    data);
            while (RECORDS.size() >= MAX_RECORDS) {
                RECORDS.removeFirst();
            }
            RECORDS.addLast(entry);
        }
        if (ClientBase.logger != null) {
            ClientBase.logger.info(entry.toLogLine());
        }
    }

    private static boolean isExpired(PendingPlacement pending, int currentTick) {
        return pending != null
                && pending.sentTick() >= 0
                && currentTick >= 0
                && currentTick - pending.sentTick() > MAX_PENDING_AGE_TICKS;
    }

    private static TraceContext takeContext(Packet<?> packet) {
        if (packet == null) {
            return CURRENT_CONTEXT.get();
        }
        try {
            TraceContext packetContext = PENDING_CONTEXTS.remove(packet);
            return packetContext == null ? CURRENT_CONTEXT.get() : packetContext;
        } catch (Throwable ignored) {
            return CURRENT_CONTEXT.get();
        }
    }

    private static PacketData decode(Packet<?> packet) {
        if (packet instanceof ServerboundMovePlayerPacket move) {
            Position position = move.hasPosition()
                    ? new Position(move.getX(0.0), move.getY(0.0), move.getZ(0.0))
                    : null;
            Angles rotation = move.hasRotation()
                    ? new Angles(move.getYRot(0.0f), move.getXRot(0.0f))
                    : null;
            return new MoveData(moveSubtype(move), position, rotation, move.isOnGround());
        }
        if (packet instanceof ServerboundUseItemOnPacket useItemOn) {
            BlockHitResult hit = useItemOn.getHitResult();
            BlockPos block = hit == null ? null : hit.getBlockPos();
            Vec3 location = hit == null ? null : hit.getLocation();
            return new UseItemOnData(
                    useItemOn.getHand(),
                    block == null ? null : new BlockPos(block.getX(), block.getY(), block.getZ()),
                    hit == null ? null : hit.getDirection(),
                    location == null ? null : new Vec3(location.x, location.y, location.z),
                    useItemOn.getSequence());
        }
        if (packet instanceof ServerboundSetCarriedItemPacket carriedItem) {
            return new CarriedSlotData(carriedItem.getSlot());
        }
        if (packet instanceof ServerboundSwingPacket swing) {
            return new SwingData(swing.getHand());
        }
        if (packet instanceof ServerboundPlayerCommandPacket command
                && (command.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING
                || command.getAction() == ServerboundPlayerCommandPacket.Action.STOP_SPRINTING)) {
            return new SprintData(command.getAction());
        }
        return GenericData.INSTANCE;
    }

    private static String moveSubtype(ServerboundMovePlayerPacket move) {
        if (move.hasPosition()) {
            return move.hasRotation() ? "PosRot" : "Pos";
        }
        return move.hasRotation() ? "Rot" : "StatusOnly";
    }

    private static String packetType(Packet<?> packet) {
        Class<?> type = packet.getClass();
        Class<?> enclosing = type.getEnclosingClass();
        if (enclosing != null && Packet.class.isAssignableFrom(enclosing)) {
            return enclosing.getSimpleName() + "." + type.getSimpleName();
        }
        return type.getSimpleName();
    }

    private static int currentTick() {
        try {
            return ClientBase.mc == null || ClientBase.mc.player == null
                    ? -1
                    : ClientBase.mc.player.tickCount;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static String describeTarget(Object target) {
        return target == null ? null : String.valueOf(target);
    }

    private static String describeOwner(Object owner) {
        if (owner == null) {
            return null;
        }
        if (owner instanceof CharSequence sequence) {
            return sequence.toString();
        }
        String simpleName = owner.getClass().getSimpleName();
        return simpleName.isEmpty() ? owner.getClass().getName() : simpleName;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = value
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replace('"', '\'')
                .trim();
        if (sanitized.isEmpty()) {
            return null;
        }
        return sanitized.length() <= 512 ? sanitized : sanitized.substring(0, 512);
    }

    public record TraceContext(long frameId, String target, String owner) {
        private static final TraceContext EMPTY = new TraceContext(NO_FRAME_ID, null, null);

        public boolean isEmpty() {
            return frameId == NO_FRAME_ID && target == null && owner == null;
        }

        private String format() {
            StringBuilder builder = new StringBuilder();
            if (frameId != NO_FRAME_ID) {
                builder.append(" frame=").append(frameId);
            }
            if (owner != null) {
                builder.append(" owner=").append(owner);
            }
            if (target != null) {
                builder.append(" target={").append(target).append('}');
            }
            return builder.toString();
        }
    }

    public record TraceEntry(
            long sequence,
            int tick,
            String packetType,
            TraceContext context,
            PacketData data) {

        public String toLogLine() {
            return "[ScaffoldNetTrace] seq=" + sequence
                    + " tick=" + tick
                    + " packet=" + packetType
                    + context.format()
                    + data.format();
        }
    }

    public interface PacketData {
        String format();
    }

    public record Position(double x, double y, double z) {
        private String format() {
            return "(" + x + ',' + y + ',' + z + ')';
        }
    }

    public record Angles(float yaw, float pitch) {
        private String format() {
            return "(" + yaw + ',' + pitch + ')';
        }
    }

    public record MoveData(
            String subtype,
            Position position,
            Angles rotation,
            boolean onGround) implements PacketData {

        @Override
        public String format() {
            return " move=" + subtype
                    + (position == null ? "" : " pos=" + position.format())
                    + (rotation == null ? "" : " rot=" + rotation.format())
                    + " onGround=" + onGround;
        }
    }

    public record UseItemOnData(
            InteractionHand hand,
            BlockPos block,
            Direction face,
            Vec3 hit,
            int sequence) implements PacketData {

        @Override
        public String format() {
            return " useItemOn hand=" + hand
                    + " block=" + formatBlock(block)
                    + " face=" + face
                    + " hit=" + formatVec(hit)
                    + " sequence=" + sequence;
        }
    }

    public record CarriedSlotData(int slot) implements PacketData {
        @Override
        public String format() {
            return " carriedSlot=" + slot;
        }
    }

    public record SwingData(InteractionHand hand) implements PacketData {
        @Override
        public String format() {
            return " swing=" + hand;
        }
    }

    public record SprintData(ServerboundPlayerCommandPacket.Action action) implements PacketData {
        public boolean sprinting() {
            return action == ServerboundPlayerCommandPacket.Action.START_SPRINTING;
        }

        @Override
        public String format() {
            return " sprint=" + (sprinting() ? "START" : "STOP");
        }
    }

    public record BlockUpdateData(
            BlockPos block,
            String state,
            String verdict,
            long outboundTraceSequence,
            int useSequence,
            int ageTicks) implements PacketData {

        @Override
        public String format() {
            return " blockUpdate block=" + formatBlock(block)
                    + " state=" + state
                    + " verdict=" + verdict
                    + " outboundTrace=" + outboundTraceSequence
                    + " useSequence=" + useSequence
                    + " ageTicks=" + ageTicks;
        }
    }

    public enum GenericData implements PacketData {
        INSTANCE;

        @Override
        public String format() {
            return "";
        }
    }

    private record PendingPlacement(
            long outboundTraceSequence,
            int sentTick,
            int useSequence,
            TraceContext context) {
    }

    private static String formatBlock(BlockPos block) {
        return block == null ? "null" : "(" + block.getX() + ',' + block.getY() + ',' + block.getZ() + ')';
    }

    private static String formatVec(Vec3 vec) {
        return vec == null ? "null" : "(" + vec.x + ',' + vec.y + ',' + vec.z + ')';
    }
}
