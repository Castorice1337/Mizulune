package com.columbina.heypixel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProtocolModConfigTest {
    @Test
    void derivesDefaultsOnlyFromTheSuppliedGameDirectory(@TempDir Path directory)
        throws Exception {
        Path installRoot = Files.createDirectories(directory.resolve("portable-root"));
        Files.createDirectories(installRoot.resolve("mods"));
        Path instance = Files.createDirectories(installRoot.resolve("heypixel"));
        Path javaHome = Files.createDirectories(directory.resolve("jdk17"));
        Path bin = Files.createDirectories(javaHome.resolve("bin"));
        Files.write(bin.resolve("java.exe"), new byte[]{0});

        ProtocolModConfig.Defaults defaults = ProtocolModConfig.detectDefaults(instance);

        assertEquals(installRoot.toAbsolutePath().normalize(), defaults.installRoot());
        assertEquals(instance.toAbsolutePath().normalize(), defaults.instanceDirectory());
        assertEquals(javaHome.toAbsolutePath().normalize(), defaults.officialJavaHome());
    }

    @Test
    void detectsTheOfficialLauncherJdkBesideTheGameTree(@TempDir Path directory)
        throws Exception {
        Path installRoot = Files.createDirectories(
            directory.resolve("MCLDownload").resolve("Game").resolve(".minecraft"));
        Path javaHome = Files.createDirectories(
            directory.resolve("MCLDownload").resolve("ext")
                .resolve("jre-v64").resolve("jdk17"));
        Path bin = Files.createDirectories(javaHome.resolve("bin"));
        Files.write(bin.resolve("javaw.exe"), new byte[]{0});

        assertEquals(javaHome.toAbsolutePath().normalize(),
            ProtocolModConfig.detectOfficialJavaHome(installRoot));
    }

    @Test
    void loadsExplicitExternalOfficialEnvironmentWithoutUsingFabricJavaHome(
        @TempDir Path directory
    ) throws Exception {
        Path installRoot = Files.createDirectories(directory.resolve("official"));
        Path instance = Files.createDirectories(installRoot.resolve("heypixel"));
        Path javaHome = Files.createDirectories(directory.resolve("jdk17"));
        Path bin = Files.createDirectories(javaHome.resolve("bin"));
        Files.write(bin.resolve("java.exe"), new byte[]{0});
        Path protocolDirectory = directory.resolve("protocol");

        String[] keys = {
            "mizulune.heypixel.installRoot",
            "mizulune.heypixel.instanceDir",
            "mizulune.heypixel.userDirectory",
            "mizulune.heypixel.javaHome"
        };
        String[] previous = new String[keys.length];
        for (int index = 0; index < keys.length; index++) {
            previous[index] = System.getProperty(keys[index]);
        }
        try {
            System.setProperty(keys[0], installRoot.toString());
            System.setProperty(keys[1], instance.toString());
            System.setProperty(keys[2], installRoot.toString());
            System.setProperty(keys[3], javaHome.toString());

            ProtocolModConfig config = ProtocolModConfig.load(protocolDirectory, instance);

            assertEquals(installRoot.toAbsolutePath().normalize(), config.installRoot());
            assertEquals(instance.toAbsolutePath().normalize(), config.instanceDirectory());
            assertEquals(installRoot.toAbsolutePath().normalize(), config.officialUserDirectory());
            assertEquals(javaHome.toAbsolutePath().normalize(), config.officialJavaHome());
            assertTrue(config.enabled());
            assertTrue(config.allowLiveSend());
            assertTrue(Files.isRegularFile(protocolDirectory.resolve(ProtocolModConfig.FILE_NAME)));
        } finally {
            for (int index = 0; index < keys.length; index++) {
                if (previous[index] == null) {
                    System.clearProperty(keys[index]);
                } else {
                    System.setProperty(keys[index], previous[index]);
                }
            }
        }
    }
}
