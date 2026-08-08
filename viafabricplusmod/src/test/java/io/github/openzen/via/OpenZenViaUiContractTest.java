package io.github.openzen.via;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Locks the distributed artifact to the original ViaFabricPlus UI. */
final class OpenZenViaUiContractTest {
    @Test
    void originalMultiplayerButtonAndVersionListAreWired() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        String metadata = Files.readString(root.resolve("src/main/resources/fabric.mod.json"),
            StandardCharsets.UTF_8);
        String mixins = Files.readString(root.resolve("src/main/resources/viafabricplus.mixins.json"),
            StandardCharsets.UTF_8);
        String mixin = Files.readString(root.resolve(
            "src/main/java/de/florianmichael/viafabricplus/injection/mixin/base/MixinMultiplayerScreen.java"),
            StandardCharsets.UTF_8);
        String screen = Files.readString(root.resolve(
            "src/main/java/de/florianmichael/viafabricplus/screen/base/ProtocolSelectionScreen.java"),
            StandardCharsets.UTF_8);
        String experimentalSettings = Files.readString(root.resolve(
            "src/main/java/de/florianmichael/viafabricplus/base/settings/groups/ExperimentalSettings.java"),
            StandardCharsets.UTF_8);
        String visualSettings = Files.readString(root.resolve(
            "src/main/java/de/florianmichael/viafabricplus/base/settings/groups/VisualSettings.java"),
            StandardCharsets.UTF_8);

        assertTrue(metadata.contains("\"id\": \"viafabricplus\""));
        assertTrue(metadata.contains("viafabricplus.mixins.json"));
        assertTrue(mixins.contains("base.MixinMultiplayerScreen"));
        assertTrue(mixins.contains("fixes.minecraft.entity.MixinBoatEntity"));
        assertTrue(mixins.contains("fixes.minecraft.entity.MixinEntityModels"));
        assertTrue(mixins.contains("fixes.minecraft.entity.MixinEntityRenderDispatcher"));
        assertTrue(mixins.contains(
            "fixes.viaversion.protocol1_20to1_20_2.MixinEntityPacketRewriter1_20_2"));
        assertTrue(mixin.contains("ProtocolSelectionScreen.INSTANCE.open"));
        assertTrue(screen.contains("VersionEnum.SORTED_VERSIONS"));
        assertTrue(experimentalSettings.contains("emulateBoatMovement"));
        assertTrue(visualSettings.contains("blockHitAnimation"));
        assertTrue(Files.exists(root.resolve(
            "src/main/resources/assets/viafabricplus/textures/boat_1_8.png")));
        assertTrue(Files.exists(root.resolve(
            "src/main/resources/assets/viafabricplus/lang/zh_hk.json")));
        assertFalse(Files.exists(root.resolve(
            "src/main/java/io/github/openzen/via/OpenZenProtocolScreen.java")));
        assertFalse(Files.exists(root.resolve(
            "src/main/java/io/github/openzen/via/mixin/MultiplayerScreenMixin.java")));
    }
}
