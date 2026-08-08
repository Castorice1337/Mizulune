package shit.zen.platform;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Small loader boundary shared by the Forge/ASM and Fabric distributions. */
public interface ClientPlatform {
    String loaderId();

    /** Loader-owned game root used as one of the official environment sources. */
    default Path gameDirectory() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft == null || minecraft.gameDirectory == null
                ? null
                : minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    default void beforeMouseButton(int button, int action, int modifiers) {
    }

    default void afterMouseButton(int button, int action, int modifiers) {
    }

    /** Returns a loader veto/result, or {@code null} when vanilla should continue. */
    default InteractionResult onItemRightClick(Player player, InteractionHand hand) {
        return null;
    }

    default void onPlayerDestroyItem(Player player, ItemStack stack, InteractionHand hand) {
    }

    /** Fabric/Mojmap is identity; Forge overrides this for production SRG names. */
    default String remapMethod(String ownerInternalName, String name, String descriptor) {
        return name;
    }

    default String remapField(String ownerInternalName, String name) {
        return name;
    }
}
