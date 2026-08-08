package shit.zen.platform;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.EquipmentSlot;

/** Small loader boundary shared by the Forge/ASM and Fabric distributions. */
public interface ClientPlatform {
    String loaderId();

    /** Whether the active graphics backend can execute Mizulune's legacy raw-OpenGL renderer. */
    default boolean supportsLegacyOpenGlRendering() {
        return true;
    }

    /** The Forge/Java 17 distribution owns the current MaxHook/JVM native contract. */
    default boolean supportsOfficialId114Native() {
        return true;
    }

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

    /** Loader/version-owned inventory operation used by AutoOffHand. */
    default void swapInventorySlotWithOffhand(int inventorySlot) {
    }

    /** Version-owned slot accessor for the client/server held-slot packets. */
    default int heldSlot(Object packet) { return -1; }

    default boolean isSword(ItemStack stack) { return false; }

    default boolean isPickaxe(ItemStack stack) { return false; }

    default boolean isDigger(ItemStack stack) { return false; }

    default boolean isArmor(ItemStack stack) { return false; }

    default EquipmentSlot armorSlot(ItemStack stack) { return null; }

    default int armorDefense(ItemStack stack) { return 0; }

    default int armorTierScore(ItemStack stack) { return 0; }

    default double attackDamage(ItemStack stack) { return 0.0D; }

    default boolean isBook(Item item) { return false; }

    default boolean isBowlFood(Item item) { return false; }

    /** Fabric/Mojmap is identity; Forge overrides this for production SRG names. */
    default String remapMethod(String ownerInternalName, String name, String descriptor) {
        return name;
    }

    default String remapField(String ownerInternalName, String name) {
        return name;
    }
}
