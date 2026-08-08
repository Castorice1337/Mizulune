package shit.zen.protocol.heypixel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import asm.patchify.loader.PatchAgent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class OfficialId114NativeSinkTest {
    @Test
    void packagedMaxHookDoesNotRequireAnOfficialInstallLayout(@TempDir Path directory)
        throws Exception {
        Path maxHook = directory.resolve("portable-MaxHook.dll");
        Files.write(maxHook, "portable-maxhook".getBytes(StandardCharsets.UTF_8));
        Path javaHome = Files.createDirectories(directory.resolve("jdk17"));
        Path jvm = Files.createDirectories(javaHome.resolve("bin").resolve("server"))
            .resolve("jvm.dll");
        Files.write(jvm, "portable-jvm".getBytes(StandardCharsets.UTF_8));
        AtomicReference<String> loadedPath = new AtomicReference<>();

        OfficialId114NativeSink sink = new OfficialId114NativeSink(
            null,
            javaHome,
            sha256(Files.readAllBytes(maxHook)),
            Set.of(sha256(Files.readAllBytes(jvm))),
            loadedPath::set,
            new OfficialId114NativeSink.NativeLoadRegistry(),
            () -> true,
            () -> maxHook
        );

        assertTrue(sink.availability().available());
        assertEquals(maxHook.toRealPath().toString(), loadedPath.get());
    }

    @Test
    void officialInstallMaxHookWinsOverPackagedCopy(@TempDir Path directory)
        throws Exception {
        Fixture fixture = fixture(directory.resolve("official-first"));
        Path packaged = directory.resolve("staged-MaxHook.dll");
        Files.write(packaged, "different-staged-copy".getBytes(StandardCharsets.UTF_8));
        AtomicReference<String> loadedPath = new AtomicReference<>();

        OfficialId114NativeSink sink = new OfficialId114NativeSink(
            fixture.layout(),
            fixture.javaHome(),
            fixture.maxHookHash(),
            Set.of(fixture.jvmHash()),
            loadedPath::set,
            new OfficialId114NativeSink.NativeLoadRegistry(),
            () -> true,
            () -> packaged
        );

        assertTrue(sink.availability().available());
        assertEquals(fixture.maxHook().toRealPath().toString(), loadedPath.get());
    }

    @Test
    void officialHashMismatchDoesNotFallBackToPackagedCopy(@TempDir Path directory)
        throws Exception {
        Fixture fixture = fixture(directory.resolve("official-mismatch"));
        Path packaged = directory.resolve("staged-MaxHook.dll");
        Files.write(packaged, "packaged-match".getBytes(StandardCharsets.UTF_8));
        AtomicInteger loads = new AtomicInteger();

        OfficialId114NativeSink sink = new OfficialId114NativeSink(
            fixture.layout(),
            fixture.javaHome(),
            sha256(Files.readAllBytes(packaged)),
            Set.of(fixture.jvmHash()),
            ignored -> loads.incrementAndGet(),
            new OfficialId114NativeSink.NativeLoadRegistry(),
            () -> true,
            () -> packaged
        );

        assertEquals(Id114NativeSink.Reason.MAXHOOK_HASH_MISMATCH,
            sink.availability().reason());
        assertEquals(0, loads.get());
    }

    @Test
    void explicitPackagedPathOverridesMachineDiscovery(@TempDir Path directory) {
        Path explicit = directory.resolve("MaxHook.dll").toAbsolutePath().normalize();
        String previous = System.getProperty(OfficialId114NativeSink.MAXHOOK_PATH_PROPERTY);
        try {
            System.setProperty(OfficialId114NativeSink.MAXHOOK_PATH_PROPERTY, explicit.toString());
            assertEquals(explicit, OfficialId114NativeSink.locatePackagedMaxHook());
        } finally {
            if (previous == null) {
                System.clearProperty(OfficialId114NativeSink.MAXHOOK_PATH_PROPERTY);
            } else {
                System.setProperty(OfficialId114NativeSink.MAXHOOK_PATH_PROPERTY, previous);
            }
        }
    }

    @Test
    void exactCanonicalIdentityLoadsOnceAndCachesUnverifiedPreflight(@TempDir Path directory)
        throws Exception {
        Fixture fixture = fixture(directory.resolve("one"));
        AtomicInteger loads = new AtomicInteger();
        AtomicReference<String> loadedPath = new AtomicReference<>();
        OfficialId114NativeSink.NativeLoadRegistry registry =
            new OfficialId114NativeSink.NativeLoadRegistry();
        OfficialId114NativeSink sink = fixture.sink(
            (path) -> {
                loads.incrementAndGet();
                loadedPath.set(path);
            },
            registry,
            true,
            fixture.maxHookHash(),
            Set.of(fixture.jvmHash())
        );

        assertTrue(sink.availability().available());
        assertEquals(
            Id114NativeSink.Reason.CALLBACK_READINESS_UNVERIFIED,
            sink.availability().reason()
        );
        assertTrue(sink.availability().available());
        assertTrue(fixture.sink(
            ignored -> loads.incrementAndGet(), registry, true,
            fixture.maxHookHash(), Set.of(fixture.jvmHash())
        ).availability().available());
        assertEquals(1, loads.get());
        assertEquals(fixture.maxHook().toRealPath().toString(), loadedPath.get());
        assertEquals(
            Id114NativeSink.AcceptResult.INVOKED_UNVERIFIED,
            sink.accept("synthetic-id114-test-value")
        );
    }

    @Test
    void lateAttachAndMissingAgentFailBeforeNativeLoad(@TempDir Path directory)
        throws Exception {
        Fixture fixture = fixture(directory.resolve("startup-mode"));
        for (PatchAgent.StartupMode mode : Set.of(
            PatchAgent.StartupMode.AGENTMAIN,
            PatchAgent.StartupMode.NONE
        )) {
            AtomicInteger loads = new AtomicInteger();
            OfficialId114NativeSink sink = new OfficialId114NativeSink(
                fixture.layout(),
                fixture.javaHome(),
                fixture.maxHookHash(),
                Set.of(fixture.jvmHash()),
                ignored -> loads.incrementAndGet(),
                new OfficialId114NativeSink.NativeLoadRegistry(),
                () -> true,
                () -> null,
                () -> mode
            );

            assertEquals(
                mode == PatchAgent.StartupMode.AGENTMAIN
                    ? Id114NativeSink.Reason.LATE_ATTACH_UNSUPPORTED
                    : Id114NativeSink.Reason.PREMAIN_REQUIRED,
                sink.availability().reason()
            );
            assertFalse(sink.availability().available());
            assertEquals(0, loads.get());
        }
    }

    @Test
    void hashAndJvmMismatchFailClosedWithoutLoading(@TempDir Path directory) throws Exception {
        Fixture fixture = fixture(directory.resolve("mismatch"));
        AtomicInteger loads = new AtomicInteger();

        OfficialId114NativeSink maxHookMismatch = fixture.sink(
            ignored -> loads.incrementAndGet(),
            new OfficialId114NativeSink.NativeLoadRegistry(),
            true,
            sha256("different".getBytes(StandardCharsets.UTF_8)),
            Set.of(fixture.jvmHash())
        );
        assertEquals(Id114NativeSink.Reason.MAXHOOK_HASH_MISMATCH,
            maxHookMismatch.availability().reason());

        OfficialId114NativeSink jvmMismatch = fixture.sink(
            ignored -> loads.incrementAndGet(),
            new OfficialId114NativeSink.NativeLoadRegistry(),
            true,
            fixture.maxHookHash(),
            Set.of(sha256("different-jvm".getBytes(StandardCharsets.UTF_8)))
        );
        assertEquals(Id114NativeSink.Reason.JVM_HASH_UNSUPPORTED,
            jvmMismatch.availability().reason());
        assertEquals(0, loads.get());
    }

    @Test
    void platformLoadFailureAndChangedPathRemainTypedAndPathFree(@TempDir Path directory)
        throws Exception {
        Fixture first = fixture(directory.resolve("first"));
        Fixture second = fixture(directory.resolve("second"));

        OfficialId114NativeSink unsupported = first.sink(
            ignored -> { }, new OfficialId114NativeSink.NativeLoadRegistry(), false,
            first.maxHookHash(), Set.of(first.jvmHash()));
        assertEquals(Id114NativeSink.Reason.PLATFORM_UNSUPPORTED,
            unsupported.availability().reason());

        OfficialId114NativeSink failed = first.sink(
            ignored -> { throw new UnsatisfiedLinkError("synthetic"); },
            new OfficialId114NativeSink.NativeLoadRegistry(), true,
            first.maxHookHash(), Set.of(first.jvmHash()));
        assertEquals(Id114NativeSink.Reason.NATIVE_LOAD_FAILED, failed.availability().reason());
        Id114NativeSink.InvocationException failure = assertThrows(
            Id114NativeSink.InvocationException.class,
            () -> failed.accept("synthetic-id114-test-value")
        );
        assertFalse(failure.getMessage().contains(directory.toString()));

        OfficialId114NativeSink.NativeLoadRegistry registry =
            new OfficialId114NativeSink.NativeLoadRegistry();
        assertTrue(first.sink(
            ignored -> { }, registry, true, first.maxHookHash(), Set.of(first.jvmHash())
        ).availability().available());
        assertEquals(Id114NativeSink.Reason.NATIVE_IDENTITY_CHANGED, second.sink(
            ignored -> { }, registry, true, second.maxHookHash(), Set.of(second.jvmHash())
        ).availability().reason());
    }

    private static Fixture fixture(Path root) throws Exception {
        Path installRoot = Files.createDirectories(root.resolve(".minecraft"));
        Path instance = Files.createDirectories(installRoot.resolve("heypixel"));
        Path nativeDirectory = Files.createDirectories(installRoot.resolve("native"));
        Path maxHook = nativeDirectory.resolve("MaxHook.dll");
        Files.write(maxHook, "synthetic-maxhook".getBytes(StandardCharsets.UTF_8));
        Path javaHome = Files.createDirectories(root.resolve("jdk17"));
        Path jvm = Files.createDirectories(javaHome.resolve("bin").resolve("server"))
            .resolve("jvm.dll");
        Files.write(jvm, "synthetic-jvm".getBytes(StandardCharsets.UTF_8));
        return new Fixture(
            HeyPixelInstallLayout.fromPaths(installRoot, instance),
            javaHome,
            maxHook,
            sha256(Files.readAllBytes(maxHook)),
            sha256(Files.readAllBytes(jvm))
        );
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().withUpperCase().formatHex(
            MessageDigest.getInstance("SHA-256").digest(value));
    }

    private record Fixture(
        HeyPixelInstallLayout layout,
        Path javaHome,
        Path maxHook,
        String maxHookHash,
        String jvmHash
    ) {
        OfficialId114NativeSink sink(
            OfficialId114NativeSink.NativeLoader loader,
            OfficialId114NativeSink.NativeLoadRegistry registry,
            boolean platformSupported,
            String expectedMaxHookHash,
            Set<String> allowedJvmHashes
        ) {
            return new OfficialId114NativeSink(
                layout,
                javaHome,
                expectedMaxHookHash,
                allowedJvmHashes,
                loader,
                registry,
                () -> platformSupported
            );
        }
    }
}
