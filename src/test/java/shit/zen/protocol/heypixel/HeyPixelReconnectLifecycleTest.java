package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

final class HeyPixelReconnectLifecycleTest {
    @Test
    void readyWaitsForInitialId1ExactFinalWrite() throws Exception {
        assertFalse(HeyPixelProtocolRuntime.readySyncOneShotReady(false, false, false));
        assertTrue(HeyPixelProtocolRuntime.readySyncOneShotReady(true, false, false));
        assertFalse(HeyPixelProtocolRuntime.readySyncOneShotReady(true, true, false));
        assertFalse(HeyPixelProtocolRuntime.readySyncOneShotReady(true, false, true));

        ClassNode runtime = readClass(HeyPixelProtocolRuntime.class);
        MethodNode ready = runtime.methods.stream()
            .filter(method -> method.name.equals("tickReadySync"))
            .findFirst()
            .orElseThrow();
        assertTrue(Arrays.stream(ready.instructions.toArray())
            .filter(MethodInsnNode.class::isInstance)
            .map(MethodInsnNode.class::cast)
            .anyMatch(call -> call.owner.equals(Type.getInternalName(HeyPixelProtocolRuntime.class))
                && call.name.equals("readySyncOneShotReady")));
    }

    @Test
    void sameConnectionTargetLossAndReplacementRearmExactlyOnce(@TempDir Path directory)
        throws Exception {
        HeyPixelProtocolRuntime runtime = new HeyPixelProtocolRuntime(null, directory);
        Object lifecycleLock = field(runtime, "id1ContextLock");
        Object connection = new Object();
        HeyPixelProtocolRuntime.Id1TargetIdentity targetA = target("session-a");
        HeyPixelProtocolRuntime.Id1TargetIdentity targetB = target("session-b");
        HeyPixelProtocolRuntime.Id1TargetIdentity targetC = target("session-c");
        AtomicInteger logouts = new AtomicInteger();
        AtomicBoolean logoutHeldLifecycleLock = new AtomicBoolean();
        Id114NativeSink sink = sink(logouts, logoutHeldLifecycleLock, lifecycleLock);

        try {
            runtime.start();
            runtime.observeConnectionLifecycle(connection);
            finishTargetTransition(runtime, recordTarget(runtime, connection, targetA));

            setBoolean(runtime, "initialId1Submitted", true);
            setBoolean(runtime, "readySyncSent", true);
            setField(runtime, "pendingId114LogoutSink", sink);
            runtime.state().setSyncTokenMetadata(SyncTokenMetadata.fromToken("test-sync-token"));
            long generationBeforeLoss = atomicLong(runtime, "id1LifecycleGeneration").get();

            finishTargetTransition(runtime, recordTarget(runtime, connection, null));

            assertFalse(getBoolean(runtime, "initialId1Submitted"));
            assertFalse(getBoolean(runtime, "readySyncSent"));
            assertTrue(runtime.state().syncTokenMetadata().isEmpty());
            assertTrue(atomicLong(runtime, "id1LifecycleGeneration").get()
                > generationBeforeLoss);
            assertEquals(1, logouts.get());
            assertFalse(logoutHeldLifecycleLock.get());
            assertNull(field(runtime, "pendingId114LogoutSink"));

            // A -> null already closed A. The following null -> B observation opens the newly
            // armed lifecycle and must not manufacture a second logout.
            setField(runtime, "pendingId114LogoutSink", sink);
            finishTargetTransition(runtime, recordTarget(runtime, connection, targetB));
            assertEquals(1, logouts.get());

            setBoolean(runtime, "initialId1Submitted", true);
            setBoolean(runtime, "readySyncSent", true);
            finishTargetTransition(runtime, recordTarget(runtime, connection, targetC));
            assertFalse(getBoolean(runtime, "initialId1Submitted"));
            assertFalse(getBoolean(runtime, "readySyncSent"));
            assertEquals(2, logouts.get());
            assertFalse(logoutHeldLifecycleLock.get());
        } finally {
            runtime.onLoggingOut();
            runtime.stop();
        }
    }

    @Test
    void moduleRestartOnTheSameConnectionAndTargetKeepsOneShots(@TempDir Path directory)
        throws Exception {
        HeyPixelProtocolRuntime runtime = new HeyPixelProtocolRuntime(null, directory);
        Object connection = new Object();
        HeyPixelProtocolRuntime.Id1TargetIdentity target = target("session-a");
        try {
            runtime.start();
            runtime.observeConnectionLifecycle(connection);
            finishTargetTransition(runtime, recordTarget(runtime, connection, target));
            setBoolean(runtime, "initialId1Submitted", true);
            setBoolean(runtime, "readySyncSent", true);

            runtime.stop();
            runtime.start();
            runtime.observeConnectionLifecycle(connection);
            finishTargetTransition(runtime, recordTarget(runtime, connection, target));

            assertTrue(getBoolean(runtime, "initialId1Submitted"));
            assertTrue(getBoolean(runtime, "readySyncSent"));
        } finally {
            runtime.onLoggingOut();
            runtime.stop();
        }
    }

    @Test
    void targetTrackerIsInitializedWhenTheFirstResolvePredatesConnectionObservation(
        @TempDir Path directory
    ) throws Exception {
        HeyPixelProtocolRuntime runtime = new HeyPixelProtocolRuntime(null, directory);
        Object connection = new Object();
        HeyPixelProtocolRuntime.Id1TargetIdentity targetA = target("session-a");
        HeyPixelProtocolRuntime.Id1TargetIdentity targetB = target("session-b");
        AtomicInteger logouts = new AtomicInteger();
        Id114NativeSink sink = sink(
            logouts,
            new AtomicBoolean(),
            field(runtime, "id1ContextLock")
        );
        try {
            runtime.start();
            // A configuration/early-packet path can resolve a target before the lifecycle
            // fallback has recorded the connection object.
            finishTargetTransition(runtime, recordTarget(runtime, connection, targetA));

            setBoolean(runtime, "initialId1Submitted", true);
            setBoolean(runtime, "readySyncSent", true);
            setField(runtime, "pendingId114LogoutSink", sink);
            finishTargetTransition(runtime, recordTarget(runtime, connection, targetB));

            assertFalse(getBoolean(runtime, "initialId1Submitted"));
            assertFalse(getBoolean(runtime, "readySyncSent"));
            assertEquals(1, logouts.get());
        } finally {
            runtime.onLoggingOut();
            runtime.stop();
        }
    }

    private static Id114NativeSink sink(
        AtomicInteger logouts,
        AtomicBoolean lockHeld,
        Object lifecycleLock
    ) {
        return new Id114NativeSink() {
            @Override
            public Availability availability() {
                return Availability.ready();
            }

            @Override
            public AcceptResult accept(String transientToken) {
                return AcceptResult.CONFIRMED;
            }

            @Override
            public void logout() {
                lockHeld.compareAndSet(false, Thread.holdsLock(lifecycleLock));
                logouts.incrementAndGet();
            }
        };
    }

    private static HeyPixelProtocolRuntime.Id1TargetIdentity target(String sessionSha256) {
        return new HeyPixelProtocolRuntime.Id1TargetIdentity(
            "127.0.0.1:25565", "pc.bjdmc.net", 25565, true, sessionSha256);
    }

    private static Object recordTarget(
        HeyPixelProtocolRuntime runtime,
        Object connection,
        HeyPixelProtocolRuntime.Id1TargetIdentity target
    ) throws Exception {
        Method method = HeyPixelProtocolRuntime.class.getDeclaredMethod(
            "recordId1TargetLocked", Object.class,
            HeyPixelProtocolRuntime.Id1TargetIdentity.class, boolean.class);
        method.setAccessible(true);
        Object lock = field(runtime, "id1ContextLock");
        synchronized (lock) {
            return method.invoke(runtime, connection, target, true);
        }
    }

    private static void finishTargetTransition(HeyPixelProtocolRuntime runtime, Object transition)
        throws Exception {
        Method method = HeyPixelProtocolRuntime.class.getDeclaredMethod(
            "finishId1TargetTransition", transition.getClass());
        method.setAccessible(true);
        method.invoke(runtime, transition);
    }

    private static AtomicLong atomicLong(Object owner, String name) throws Exception {
        return (AtomicLong) field(owner, name);
    }

    private static boolean getBoolean(Object owner, String name) throws Exception {
        return (boolean) field(owner, name);
    }

    private static void setBoolean(Object owner, String name, boolean value) throws Exception {
        Field field = declaredField(owner, name);
        field.setBoolean(owner, value);
    }

    private static Object field(Object owner, String name) throws Exception {
        return declaredField(owner, name).get(owner);
    }

    private static void setField(Object owner, String name, Object value) throws Exception {
        declaredField(owner, name).set(owner, value);
    }

    private static Field declaredField(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static ClassNode readClass(Class<?> type) throws Exception {
        String resource = type.getName().replace('.', '/') + ".class";
        try (InputStream stream = type.getClassLoader().getResourceAsStream(resource)) {
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }
}
